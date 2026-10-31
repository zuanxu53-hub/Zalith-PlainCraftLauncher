/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 自定义主页的统一解析入口。
 *
 * PCL 的 `UiCustomType` 有四种主页来源：
 *   0 空白 / 1 读取本地文件 / 2 联网更新 / 3 预设（`UiCustomPreset`）。
 * PCL 里预设要么是内置 XAML（你知道吗 / 回声洞，`PageLaunchRight.xaml.vb:44-58`），
 * 要么是一个远程地址 —— 可能是普通网页（Minecraft 新闻），也可能是 PCL 主页
 * XAML 文件（简单主页 / 主页市场等）。
 *
 * Android 端没有 WPF，这里把所有来源统一解析成两种可渲染形态：
 *   [PclHomeEntry.Web]  → 直接交给 WebView 加载的地址
 *   [PclHomeEntry.Html] → 已经转成 HTML 的内容（WebView loadData）
 *
 * 「PCL 主页文件」（.xaml）由 [PclHomeXaml.convertToHtml] 转换后渲染，
 * 因此本地导入与远程预设都支持 PCL 的主页文件格式。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.lowercase

/** 解析后的主页内容。 */
sealed class PclHomeEntry {
    /** 直接交给 WebView 加载的地址（普通网页 / 本地 html）。 */
    data class Web(val url: String) : PclHomeEntry()

    /** 已经转成 HTML 的内容（PCL 主页 XAML 转换结果 / 内置预设）。 */
    data class Html(val html: String) : PclHomeEntry()
}

object PclHomeLoader {

    private const val CONNECT_TIMEOUT = 5_000
    private const val READ_TIMEOUT = 8_000

    /** PCL 的默认 UA。mcnews.meloong.com 这类 PCL 专用站**只认它**（浏览器 UA 反而 403）。 */
    private const val UA_PCL = "PCL2/2.8.12"

    /** 浏览器 UA，PCL UA 被拒时备用。 */
    private const val UA_BROWSER =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

    /**
     * 解析当前设置对应的主页内容；「空白」或没有任何可用内容时返回 null
     * （调用方退回默认主页）。
     */
    suspend fun resolve(context: Context): PclHomeEntry? = withContext(Dispatchers.IO) {
        when (PclAppearance.homeType) {
            PclHomeType.PRESET -> resolvePreset()
            PclHomeType.LOCAL -> resolveLocal()
            PclHomeType.ONLINE ->
                PclAppearance.homeNet.trim().takeIf { it.isNotBlank() }?.let { resolveOnline(it) }
            else -> null
        }
    }

    // ---------------------------------------------------------------- 预设

    private fun resolvePreset(): PclHomeEntry? {
        val entry = PclHomePresets.of(PclAppearance.homePreset) ?: PclHomePresets.default()
        if (entry.isLocal) {
            // 你知道吗 / 回声洞：PCL 里是内置 XAML + 占位符，这里同样先生成 XAML 片段
            // 再走同一条转换链路，保证视觉一致。文案优先联网抓官网审核内容，失败回退本地。
            val type = if (entry.id == 1) "cave" else "hint"
            val text = PclRemoteContent.randomBlocking(type) ?: PclHomePresets.randomLocal(type)
            // 内置文案已删：官网也没给出内容时，这张卡干脆不画（避免一张空卡片）
            if (text.isBlank()) return null
            val title = if (entry.id == 1) "回声洞" else "你知道吗？"
            val xaml = "<local:MyCard Title=\"$title\">" +
                    "<TextBlock Margin=\"20,4,20,10\" FontSize=\"13.5\" Text=\"${escapeXml(text)}\" TextWrapping=\"Wrap\"/>" +
                    "</local:MyCard>"
            return PclHomeEntry.Html(PclHomeXaml.convertToHtml(xaml))
        }
        val url = entry.url ?: return null
        return downloadAndConvert(url)
    }

    // ---------------------------------------------------------------- 本地文件

    /**
     * 联网主页。
     *
     * 用户常常直接粘 GitHub 仓库首页（PCL 社区主页大多托管在仓库里），
     * 以前这里把 URL 原样丢给 WebView，结果右侧直接变成 GitHub 的 About 页面。
     * 现在识别仓库：从默认分支根目录找 `.xaml` 主页文件，下载并转换；
     * 真正的网页地址仍然走 WebView。
     */
    private fun resolveOnline(url: String): PclHomeEntry {
        val blob = Regex("""^https?://github\.com/([^/]+)/([^/]+)/blob/[^/]+/(.+)$""")
            .find(url)
        if (blob != null) {
            val raw = "https://raw.githubusercontent.com/${blob.groupValues[1]}/${blob.groupValues[2]}/${blob.groupValues[3]}"
            return downloadAndConvert(raw)
        }

        val repo = Regex("""^https?://github\.com/([^/]+)/([^/?#]+)/?$""").find(url)
        if (repo != null) {
            return resolveGitHubRepo(repo.groupValues[1], repo.groupValues[2])
        }
        return downloadAndConvert(url)
    }

    /** 在仓库根目录里找主页 XAML；找不到就给明确错误，绝不再把 GitHub 页面塞进主页。 */
    private fun resolveGitHubRepo(owner: String, repo: String): PclHomeEntry {
        val api = "https://api.github.com/repos/$owner/$repo/contents/"
        val json = fetchJson(api)
            ?: return PclHomeEntry.Html(
                errorHtml("https://github.com/$owner/$repo", "无法读取仓库文件列表（可能触发了 GitHub 限流）")
            )
        val xamlFiles = runCatching {
            val array = org.json.JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name")
                if (item.optString("type") == "file" && name.endsWith(".xaml", true)) {
                    name to item.optString("download_url")
                } else null
            }
        }.getOrDefault(emptyList())

        if (xamlFiles.isEmpty()) {
            return PclHomeEntry.Html(
                errorHtml(
                    "https://github.com/$owner/$repo",
                    "仓库根目录没有 .xaml 主页文件。请填写主页文件直链，或把 XAML 放到仓库根目录。"
                )
            )
        }
        val preferred = xamlFiles.firstOrNull {
            it.first.equals("homepage.xaml", true) || it.first.equals("index.xaml", true)
        } ?: xamlFiles.first()
        val content = fetch(preferred.second, UA_PCL) ?: fetch(preferred.second, UA_BROWSER)
            ?: return PclHomeEntry.Html(errorHtml(preferred.second, "主页文件下载失败"))
        return PclHomeEntry.Html(PclHomeXaml.convertToHtml(content))
    }

    private fun resolveLocal(): PclHomeEntry? {
        val path = PclAppearance.homeLocalFile ?: return null
        val file = File(path)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull() ?: return null
        // 扩展名是 xaml，或内容看起来是 PCL 主页 XAML（导入时没拿到显示名可能被存成
        // .html，但内容仍是 XAML）→ 都转换成 HTML，避免 WebView 直接打开 XAML 显示乱码。
        val looksLikeXaml = file.extension.equals("xaml", ignoreCase = true) ||
                Regex("<\\s*(local:)?(MyCard|TextBlock|StackPanel|Grid|Page|ScrollViewer)\\b",
                    RegexOption.IGNORE_CASE)
                    .containsMatchIn(text.substring(0, minOf(text.length, 2048)))
        if (looksLikeXaml) {
            return PclHomeEntry.Html(PclHomeXaml.convertToHtml(text))
        }
        // 普通网页文件照旧交给 WebView。
        return PclHomeEntry.Web("file://$path")
    }

    // ---------------------------------------------------------------- 网络

    /**
     * 下载一个远程主页：网页就直接交给 WebView；PCL 主页 XAML 则转换成 HTML。
     *
     * 双 UA 回退：先用 PCL UA（`PCL2/...`——mcnews.meloong.com 这类 PCL 专用站
     * **只认它**，浏览器 UA 反而 403，所以这种站**不能**交给 WebView 直接加载），
     * 被拒再换浏览器 UA。下载成功后按内容分流：像网页（有 `<html`）交给 WebView
     * （要跑 JS），其余当 PCL 主页 XAML 转换。两个 UA 都失败才给错误卡片。
     */
    private fun downloadAndConvert(url: String): PclHomeEntry {
        val content = fetch(url, UA_PCL) ?: fetch(url, UA_BROWSER)
            ?: return PclHomeEntry.Html(errorHtml(url, "下载失败（连接不上或被服务器拒绝）"))

        // 网页（有 <html 标记）直接交给 WebView；其余按 PCL 主页 XAML 转换。
        val looksLikeWeb = Regex("<\\s*html|<!doctype\\s+html", RegexOption.IGNORE_CASE)
            .containsMatchIn(content.substring(0, minOf(content.length, 2048)))
        if (looksLikeWeb) return PclHomeEntry.Web(url)

        return PclHomeEntry.Html(PclHomeXaml.convertToHtml(content))
    }

    /** 用指定 UA 下载文本内容；HTTP 非 2xx 或网络异常都返回 null（由调用方换 UA 重试）。 */
    private fun fetchJson(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", UA_BROWSER)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        conn.inputStream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    private fun fetch(url: String, ua: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", ua)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
        conn.inputStream.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private fun errorHtml(url: String, message: String): String {
        val xaml = "<local:MyCard Title=\"主页加载失败\">" +
                "<TextBlock Margin=\"20,4,20,4\" FontSize=\"13.5\" " +
                "Text=\"无法加载这个主页：${escapeXml(message)}\" TextWrapping=\"Wrap\"/>" +
                "<TextBlock Margin=\"20,2,20,10\" FontSize=\"12\" Foreground=\"{DynamicResource ColorBrush5}\" " +
                "Text=\"${escapeXml(url)}\" TextWrapping=\"Wrap\"/>" +
                "</local:MyCard>"
        return PclHomeXaml.convertToHtml(xaml)
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
