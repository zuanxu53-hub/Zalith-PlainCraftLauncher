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

import com.zalith.pcl.utils.GpuFamily
import com.zalith.pcl.utils.gpuDisplayName
import com.zalith.pcl.utils.gpuFamily
import com.zalith.pcl.utils.logging.Logger

private const val TAG = "RendererGpuCompat"

/**
 * 渲染器与 GPU 的匹配检查。
 *
 * 起因：华为 / 麒麟这类 Mali 机选了 `Freedreno (Adreno)`，启动后
 * `libOSMesa` 打不开 Adreno 的内核驱动 `kbase`（Mali 上根本没有这个设备），
 * 直接 SIGSEGV；反过来 Mali 专用驱动也一样。这类崩溃发生在原生层，
 * 日志里只有一堆十六进制地址，用户完全看不出是渲染器选错了。
 *
 * 所以在启动前先拦一道：厂商对不上就明确说清楚，别让它崩。
 */
object RendererGpuCompat {

    /** Freedreno 只认高通的 Adreno —— 它走的是 Adreno 的内核驱动 `kgsl` / `kbase`。 */
    private const val ID_FREEDRENO = "gallium_freedreno"

    /** Panfrost 只认 ARM 的 Mali。 */
    private const val ID_PANFROST = "gallium_panfrost"

    /**
     * 把渲染器的唯一标识（存进设置里的那个）换成渲染器 id（`gallium_*`）。
     *
     * 设置里存的是 `getUniqueIdentifier()`，空串表示「没选过」，
     * 此时真正生效的是渲染器列表里的第一个，和 [Renderers.getDefaultRendererId] 一致。
     */
    private fun resolveRendererId(uniqueIdentifier: String): String? {
        val list = runCatching { Renderers.getRenderers() }.getOrDefault(emptyList())
        if (list.isEmpty()) return null
        val target = if (uniqueIdentifier.isEmpty()) {
            list.first()
        } else {
            list.firstOrNull { it.getUniqueIdentifier() == uniqueIdentifier } ?: list.first()
        }
        return target.getRendererId()
    }

    /**
     * 检查这个渲染器能不能在当前设备上跑。
     *
     * @param uniqueIdentifier 设置里存的渲染器唯一标识（空串 = 没选过）
     * @return null 表示没意见；否则是给用户看的说明，调用方负责展示
     */
    fun warningFor(uniqueIdentifier: String): String? {
        val rendererId = resolveRendererId(uniqueIdentifier) ?: return null
        if (rendererId != ID_FREEDRENO && rendererId != ID_PANFROST) return null

        val family = gpuFamily()
        val gpu = gpuDisplayName()

        // 检测不到厂商时不下结论：可能是 EGL 建不起来（老机器、驱动异常），
        // 那种情况贸然拦住反而挡住本来能跑的组合。
        if (family == GpuFamily.Unknown) {
            Logger.warning(TAG, "无法识别 GPU 厂商，跳过渲染器兼容检查（renderer=$rendererId）")
            return null
        }

        return when {
            rendererId == ID_FREEDRENO && family != GpuFamily.Qualcomm ->
                "「Freedreno (Adreno)」只能用在高通 Adreno 显卡上，当前检测到的是 $gpu。" +
                        "继续启动会在原生层直接崩溃（打不开 Adreno 内核驱动 kbase）。\n\n" +
                        "请到「设置 → 渲染器」换成 Panfrost (Mali)，或者 GL4ES / Krypton 这类通用渲染器。"

            rendererId == ID_PANFROST && family != GpuFamily.Mali ->
                "「Panfrost (Mali)」只能用在 ARM Mali 显卡上，当前检测到的是 $gpu。" +
                        "继续启动会在原生层崩溃。\n\n" +
                        "请到「设置 → 渲染器」换成 Freedreno (Adreno)，或者 GL4ES / Krypton 这类通用渲染器。"

            else -> null
        }
    }
}
