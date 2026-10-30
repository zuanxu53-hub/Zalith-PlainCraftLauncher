/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.zalith.pcl.game.renderer

import android.app.Activity
import com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.utils.logging.Logger
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "RendererCompat"

/**
 * 「渲染器：自动选择」的实现。
 *
 * 背景：Sodium / Embeddium / Rubidium / Iris / Oculus 这一代区块渲染模组，
 * 依赖桌面 OpenGL 3.3+ 的核心特性（base-vertex 多重绘制、持久映射缓冲、间接绘制等）。
 * GL4ES 系的翻译层（`libgl4es_*.so`）最高只暴露 GL 3.1，这类模组在上面
 * 会在贴图图集加载完后彻底静默（游戏日志里一堆 `*.png-atlas` 之后没有下文，看起来像卡死）。
 *
 * 但**「换成 Kopper Zink」并不是万能解**：zink 是 Vulkan → 桌面 GL 的翻译层，
 * 它的能力完全取决于设备的 Vulkan 驱动。只报 Vulkan 1.0 / 1.1 的厂商驱动
 * （典型是 Kirin / Mali Bifrost 这类老机型）连 GL 上下文都建不出来，
 * 换过去等于把「能跑的 GLES 包装层」换成「一条路都不剩」。
 *
 * 所以自动选择必须**按设备能力**来定：先看设备真实的 Vulkan 能力（原生枚举扩展与特性），
 * 再看 GLES 版本，最后再看模组需求和渲染器自身支持的 MC 版本范围。
 * 具体规则见 [applyAutoCompat]。
 */
object RendererCompat {
    /**
     * 需要桌面 OpenGL 3.3+ 的区块渲染模组：关键字 → 展示名。
     * 关键字按「文件名分词」匹配，所以 `sodium-fabric-0.5.13+mc1.20.1.jar`、
     * `reeses-sodium-options-...jar` 都能命中。
     */
    private val DESKTOP_GL_MODS: Map<String, String> = linkedMapOf(
        "sodium" to "Sodium",
        "embeddium" to "Embeddium",
        "rubidium" to "Rubidium",
        "iris" to "Iris",
        "oculus" to "Oculus"
    )

    /** 展示顺序固定按 [DESKTOP_GL_MODS] 的声明顺序，避免依赖文件系统枚举顺序。 */
    private val DISPLAY_ORDER: List<String> = DESKTOP_GL_MODS.values.toList()

    private val TOKEN_SPLIT = Regex("[^a-z0-9]+")
    private val MOD_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * 当前渲染器是否属于 GL4ES 翻译层家族。
     *
     * 用渲染器库名判断而不是渲染器类型 —— 用户装的渲染器插件同样可能基于
     * GL4ES（FCL 插件就有 gl4es 版），按库名判断能一并覆盖：
     * 内置的 [com.zalith.pcl.game.renderer.renderers.GL4ESRenderer] 是
     * `libgl4es_114.so`，[com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer]
     * 是 `libng_gl4es.so`，都含 `gl4es`。
     */
    fun isGL4ESFamily(renderer: RendererInterface): Boolean =
        renderer.getRendererLibrary().contains("gl4es", ignoreCase = true)

    /**
     * 扫描实例的 mods 目录，返回其中需要桌面 OpenGL 3.3+ 的模组展示名。
     * `xxx.jar.disabled` 这类被禁用的模组不会命中（判据是 `.jar` 结尾）。
     */
    fun scanDesktopGlMods(gameDir: File): List<String> {
        val modsDir = File(gameDir, "mods")
        if (!modsDir.isDirectory) return emptyList()

        val found = linkedSetOf<String>()
        modsDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name.lowercase()
            if (!name.endsWith(".jar")) return@forEach

            val hit = matchByFileName(name) ?: matchByModId(file)
            if (hit != null) found += hit
        }
        // listFiles() 的枚举顺序在 Android / Windows 上都不保证，这里按固定顺序产出
        return found.sortedBy { DISPLAY_ORDER.indexOf(it) }
    }

    /** 按文件名分词匹配，覆盖绝大多数正常命名的整合包。 */
    private fun matchByFileName(fileName: String): String? {
        val tokens = TOKEN_SPLIT.split(fileName)
        return DESKTOP_GL_MODS.entries
            .firstOrNull { (key, _) -> tokens.contains(key) }
            ?.value
    }

    /** 文件名看不出来时（用户改过名），读 jar 内的 fabric / quilt 元数据补充判断。 */
    private fun matchByModId(file: File): String? {
        return try {
            ZipFile(file).use { zip ->
                var hit: String? = null
                for (meta in listOf("fabric.mod.json", "quilt.mod.json")) {
                    val entry = zip.getEntry(meta) ?: continue
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }.lowercase()
                    // 有的元数据把 id 写在别的字段前面，取第一个 "id" 就够了
                    val id = MOD_ID_PATTERN.find(text)?.groupValues?.get(1) ?: continue
                    hit = DESKTOP_GL_MODS.entries
                        .firstOrNull { (key, _) -> id == key || id.startsWith("$key-") }
                        ?.value
                    if (hit != null) break
                }
                hit
            }
        } catch (_: Throwable) {
            // 单个 jar 读不了不影响整体判断
            null
        }
    }

    /**
     * 本次「自动选择」落在哪个渲染器、以及为什么。由 [applyAutoCompat] 写入，
     * 启动日志会把它打成 `Renderer Auto: ...` 一行，排查时不用再猜。
     */
    var lastAutoDecision: String? = null
        private set

    /**
     * 渲染器兼容性检查（只诊断，不改选择）。
     *
     * 这里以前是「自动选择渲染器」：启动前按设备 GLES 版本、Vulkan 能力枚举，
     * 以及整合包里有没有 Sodium / Iris，**在用户不知情的情况下**把渲染器换成
     * Kopper Zink 或 GL4ES。整段逻辑已经删掉，原因有三条：
     *
     * 1. 它是猜的。判据（`reqGlEsVersion`、Vulkan 能力枚举）在厂商驱动上经常不准，
     *    换过去之后 `eglCreateContext` 直接失败，表现就是**进游戏白屏**；
     * 2. 它会悄悄覆盖用户的选择，日志里只有一行，出问题时几乎无法察觉
     *    （26.3 上就是这么挑错渲染器的）；
     * 3. 设置里那个「自动选择」的名字本身也是骗人的 —— 它只是回落到
     *    `Renderers` 列表第一个，并不是真的「按能力挑」。
     *
     * 现在渲染器完全由设置决定（[Version.getRenderer] 把「没设过」解析成默认渲染器），
     * 这里只保留一件事：整合包里装了要求桌面 OpenGL 3.3+ 的模组、而当前渲染器
     * 撑不住时，往日志里写清楚后果。
     *
     * @return 永远是 null —— 不再替换渲染器，保留返回值只为兼容调用方。
     */
    fun applyAutoCompat(version: Version, activity: Activity? = null): RendererInterface? {
        if (Renderers.getRenderers().isEmpty()) return null
        lastAutoDecision = null
        val current = runCatching { Renderers.getCurrentRenderer() }.getOrNull()
        diagnoseExplicitChoice(version, current)
        return null
    }

    /**
     * 诊断：整合包里的模组要求桌面 GL 3.3+，当前渲染器却是 GL4ES 翻译层。
     * 只在「明显不搭」时写一条日志，绝不改动用户的选择。
     */
    private fun diagnoseExplicitChoice(version: Version, current: RendererInterface?) {
        if (current == null) return
        val mods = scanDesktopGlMods(version.getGameDir())
        if (mods.isEmpty()) return

        val modList = mods.joinToString(" / ")
        if (isGL4ESFamily(current) && current !== NGGL4ESRenderer) {
            Logger.warning(
                TAG,
                "检测到需要桌面 OpenGL 3.3+ 的模组（$modList），当前渲染器 ${current.getRendererName()} " +
                        "属于 GL4ES 翻译层、无法支撑，游戏很可能卡在加载界面。请在「设置 → 启动 → 渲染器」中改用 Krypton Wrapper。"
            )
        }
    }
}
