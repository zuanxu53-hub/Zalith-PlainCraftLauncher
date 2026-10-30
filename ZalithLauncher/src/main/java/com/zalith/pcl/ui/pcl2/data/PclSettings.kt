/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 设置读取辅助：把 Zalith 设置系统里那些「不在 MMKV 里、需要现算」的可选项
 * （渲染器列表、Java 运行库列表、内存上限）统一暴露出来，供 PCL2 外观的设置页使用。
 *
 * 所有数值都来自 Zalith 的真实实现，没有硬编码的示例数据。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.game.renderer.RendererInterface
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.game.renderer.renderers.FreedrenoRenderer
import com.zalith.pcl.game.renderer.renderers.GL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.KopperZinkRenderer
import com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.PanfrostRenderer
import com.zalith.pcl.game.renderer.renderers.VirGLRenderer
import com.zalith.pcl.game.version.installed.SettingState
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.platform.getMaxMemoryForSettings

/** `Renderers.init()` 注册的那批内置渲染器，只在渲染器表意外为空时当兜底用。 */
private val PCL_BUILTIN_RENDERERS: List<RendererInterface> = listOf(
    NGGL4ESRenderer,
    GL4ESRenderer,
    KopperZinkRenderer,
    VirGLRenderer,
    FreedrenoRenderer,
    PanfrostRenderer,
)

object PclSettings {

    /**
     * 可用渲染器：[唯一标识] to [显示名]。
     *
     * 这里**不能静默吞异常**：原来用 `runCatching{}.getOrDefault(emptyList())`，
     * 一旦拿不到列表，版本设置的「选择渲染器」对话框里就只剩「跟随全局设置」一项，
     * 看起来像是「只能选全局那个」。空表时强制重建一次内置渲染器再取，仍失败才留空。
     */
    fun rendererOptions(): List<Pair<String, String>> {
        val loaded = try {
            Renderers.init()
            if (Renderers.getRenderers().isEmpty()) Renderers.init(reset = true)
            Renderers.getRenderers()
        } catch (t: Throwable) {
            Logger.warning("PclSettings", "读取渲染器列表失败：${t.message}")
            emptyList()
        }
        // 兜底：`Renderers` 万一没装起来（初始化半途抛异常 / 被别处清空），
        // 直接把内置那几个渲染器对象列出来 —— 它们的唯一标识和 `Renderers.init()`
        // 里注册的完全一致，选下去照样能生效。总比给用户一条空下拉强。
        val list = loaded.ifEmpty {
            runCatching { PCL_BUILTIN_RENDERERS }.getOrDefault(emptyList())
        }
        if (loaded.isEmpty()) {
            Logger.warning("PclSettings", "渲染器表为空，已回退到内置渲染器列表")
        }
        return list.map { it.getUniqueIdentifier() to it.getRendererName() }
    }

    /** 当前渲染器唯一标识；未设置时返回空串。 */
    fun currentRendererId(): String = runCatching {
        Renderers.init()
        if (Renderers.isCurrentRendererValid()) {
            Renderers.getCurrentRenderer().getUniqueIdentifier()
        } else {
            ""
        }
    }.getOrDefault("")

    /**
     * 默认渲染器唯一标识。
     *
     * 以前设置里有一项叫「自动选择」（存空串），实际行为是
     * `Renderers.setCurrentRenderer("")` → 落到列表第一个渲染器。也就是说
     * **「自动选择」这个名字是骗人的：它并不是按设备能力挑，只是用第一个**。
     * 真正的按能力挑选另有一处（`RendererCompat.applyAutoCompat`），还会在启动时
     * 悄悄把用户选的渲染器换掉 —— 26.3 上就是它挑错导致白屏。
     *
     * 现在下拉里不再有这两项：直接显示即将生效的那个渲染器，名字和实际行为一致。
     */
    fun defaultRendererId(): String = runCatching {
        Renderers.init()
        if (Renderers.getRenderers().isEmpty()) Renderers.init(reset = true)
        Renderers.getRenderers().firstOrNull()?.getUniqueIdentifier()
    }.getOrNull() ?: NGGL4ESRenderer.getUniqueIdentifier()

    /**
     * 设备上已安装的 Java 运行库：[名称] to [显示名]。
     *
     * 该调用会扫描运行库目录，请在协程里调用。
     */
    fun javaRuntimeOptions(): List<Pair<String, String>> = runCatching {
        RuntimesManager.getRuntimes().map { runtime ->
            val label = buildString {
                append(runtime.name)
                runtime.versionString?.let { append(" (Java ").append(it).append(")") }
                runtime.arch?.let { append(" · ").append(it) }
            }
            runtime.name to label
        }
    }.getOrDefault(emptyList())

    /** 本机可分配内存上限（MB）。 */
    fun memoryLimitMb(context: Context): Int =
        runCatching { getMaxMemoryForSettings(context) }.getOrDefault(4096)

    /** 把内存分配从「MB」换算成滑块的 0..1 比例。 */
    fun ramToFraction(ramMb: Int, limitMb: Int): Float =
        if (limitMb <= 0) 0f else (ramMb.toFloat() / limitMb).coerceIn(0f, 1f)

    /** 把滑块比例换算回 MB，并向上取整到 64MB 一档（与 PCL 的内存滑块手感一致）。 */
    fun fractionToRam(fraction: Float, limitMb: Int): Int {
        val raw = (limitMb * fraction).toInt()
        return ((raw + 63) / 64) * 64
    }
}

/** `SettingState` 的本地化名称（跟随全局 / 启用 / 禁用）。 */
@Composable
fun settingStateLabel(state: SettingState): String = stringResource(state.textRes)

/** `SettingState` 的三个选项，顺序固定为 跟随全局 / 启用 / 禁用。 */
val settingStateOrder: List<SettingState> = listOf(
    SettingState.FOLLOW_GLOBAL,
    SettingState.ENABLE,
    SettingState.DISABLE,
)
