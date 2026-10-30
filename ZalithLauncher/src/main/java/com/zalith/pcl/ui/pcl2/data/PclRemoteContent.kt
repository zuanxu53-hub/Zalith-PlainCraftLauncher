/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「你知道吗 / 回声洞」的**联网文案源**。
 *
 * 官网（pcl-homepage-site）提供 `GET /api/random?type=hint|cave`，只返回审核通过的文案。
 * 这里优先联网抓取，抓不到（未配置 / 断网 / 超时 / 404）就回退到本地内置文案，
 * 保证任何情况下主页都有内容可显示。
 *
 * 站点根地址优先取 [DEFAULT_BASE_URL]（编译期，站点定下来后填这一行即可），
 * 也可以在运行期用 MMKV key `pcl_home_remote_url` 覆盖（后续加设置项用）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zalith.pcl.setting.launcherMMKV
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

private const val LOG_TAG = "PclRemoteContent"

/** 首次进入主页时最多尝试几次（失败后逐步退避），避免一次失败就永远停在占位文案。 */
private const val FETCH_ATTEMPTS = 3

object PclRemoteContent {

    /** MMKV key：运行期覆盖站点根地址用。 */
    private const val KEY_BASE_URL = "pcl_home_remote_url"

    /**
     * 站点根地址（编译期默认值）。
     *
     * 官网正式域名 = `zpcl.craftmc.cn`，证书有效、走 443，不需要带端口。
     * 留空表示还没配置 —— 这时只看 MMKV，两者都空则完全不联网、只用本地文案。
     */
    private const val DEFAULT_BASE_URL = "https://zpcl.craftmc.cn"

    private const val TIMEOUT_MS = 8000

    /** 上次成功拿到的文案，按类型缓存。只在内存里，重启失效。 */
    private val cache = ConcurrentHashMap<String, String>()

    /** 最近一次失败原因，写进日志方便排查（`HTTP 404` / `timeout` / `返回内容为空` 等）。 */
    @Volatile
    var lastError: String? = null
        private set

    /**
     * 上次失败是不是「重试也没用」的那种。
     *
     * `/api/random` 在库里没有审核通过的文案时返回 **404** —— 站点是活的、只是没内容，
     * 再问三次也是同样的答案。断网 / 超时 / 5xx 才值得退避重试。
     */
    @Volatile
    var lastErrorFinal: Boolean = false
        private set

    /** 已缓存的上一条文案；没有就返回 null。 */
    fun cached(type: String): String? = cache[type]

    private fun cacheText(type: String, text: String) {
        cache[type] = text
    }

    /**
     * 站点根地址（去尾斜杠）。空 = 不联网。
     *
     * 顺序是 **MMKV 覆盖值优先，编译期默认值兜底**。
     * 原来写的是 `DEFAULT_BASE_URL.ifBlank { mmkv }` —— 默认值非空时它会直接短路，
     * MMKV 那半边**永远走不到**，注释里承诺的「运行期可覆盖」其实是假的：
     * 站点换域名只能重新发版。这里反过来，覆盖值才真正生效。
     */
    fun baseUrl(): String {
        val override = runCatching { launcherMMKV().getString(KEY_BASE_URL, "").orEmpty() }.getOrDefault("")
        return override.ifBlank { DEFAULT_BASE_URL }.trim().trimEnd('/')
    }

    /**
     * 阻塞式联网随机取一条已审核文案；未配置 / 失败 / 无内容都返回 null。
     * **必须在 IO 线程调用。**
     */
    fun randomBlocking(type: String): String? {
        val base = baseUrl()
        if (base.isEmpty()) {
            lastError = "未配置站点地址"
            lastErrorFinal = true
            return null
        }
        lastErrorFinal = false
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("$base/api/random?type=$type").openConnection() as HttpURLConnection
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "ZalithLauncher/2")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                lastError = "HTTP $code"
                // 4xx：地址错了 / 路由没了 / 库里没内容 —— 都不是等一下就能好的
                lastErrorFinal = code in 400..499
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val text = JSONObject(body).optString("text")
            if (text.isBlank()) {
                lastError = "返回内容为空"
                lastErrorFinal = true
                return null
            }
            lastError = null
            cacheText(type, text)
            text
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /** 协程版：切到 IO 线程联网。 */
    suspend fun random(type: String): String? = withContext(Dispatchers.IO) { randomBlocking(type) }
}

/**
 * 随机文案（本地兜底 + 联网替换）。
 *
 * 首帧优先用**上次成功拿到的文案**（内存缓存），再在后台联网；
 * 拉到审核通过的新文案就替换掉。[reloadToken] 变化会重新拉取（点文字刷新走这条）。
 *
 * 旧实现只请求一次，失败就一直停在占位文案上，表现成「必须点一下才显示内容」；
 * 现在最多重试 [FETCH_ATTEMPTS] 次并逐步退避，失败原因写进日志。
 */
@Composable
fun rememberRemoteText(type: String, reloadToken: Int = 0): String {
    var text by remember(reloadToken, type) {
        mutableStateOf(PclRemoteContent.cached(type) ?: PclHomePresets.randomLocal(type))
    }
    // 三次都失败才算「真的没有内容」。加载中不要提前显示「没有留言」，
    // 否则用户会以为回声洞是空的，复现「每次进来都是没有」的观感。
    var exhausted by remember(reloadToken, type) { mutableStateOf(false) }
    LaunchedEffect(type, reloadToken) {
        exhausted = false
        repeat(FETCH_ATTEMPTS) { attempt ->
            val fetched = PclRemoteContent.random(type)
            if (!fetched.isNullOrBlank()) {
                text = fetched
                return@LaunchedEffect
            }
            Logger.warning(
                LOG_TAG,
                "拉取「$type」文案失败（第 ${attempt + 1}/$FETCH_ATTEMPTS 次）：${PclRemoteContent.lastError}"
            )
            // 404 = 官网在线、只是库里还没审核通过的文案。再问两次答案一样，
            // 直接收手，别让主页白等两次退避。
            if (PclRemoteContent.lastErrorFinal) {
                exhausted = true
                return@LaunchedEffect
            }
            if (attempt < FETCH_ATTEMPTS - 1) delay(500L * (attempt + 1))
        }
        exhausted = true
    }
    if (text.isNotBlank()) return text
    // 回声洞的默认文案已删：只有确认拉不到时才给占位，加载中返回空串（不留假信息）。
    return if (exhausted && type == "cave") "回声洞暂时没有留言，去官网投稿吧～" else ""
}
