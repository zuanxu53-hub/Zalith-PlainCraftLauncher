/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 本文件是 Plain Craft Launcher 2 (PCL2) 界面向 Jetpack Compose 的移植实现。
 * 颜色取值与生成算法直接来自 PCL2 的以下原始文件，未做任何主观改动：
 *   - Plain Craft Launcher 2/Application.xaml                （ColorBrush1-8 / 灰阶 / 红 / 白 等资源字典）
 *   - Plain Craft Launcher 2/Modules/ModSecret.vb            （ThemeRefresh：主题色 HSL 生成算法）
 *   - Plain Craft Launcher 2/Modules/Base/ModBase.vb         （MyColor.FromHSL / FromHSL2）
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PCL2 的一整套主题调色板。
 *
 * 命名与 PCL2 资源字典中的键一一对应，方便逐项核对：
 * `ColorBrush1` -> [color1]，`ColorObjectGray3` -> [gray3]，依此类推。
 *
 * 标 `@Immutable` 而不是放任编译器猜：`titleBarGradient` / `bodyGradient` 是 `List<Color>`，
 * 而 `List` 在 Compose 里默认是**不稳定**类型 —— 整个 `PclPalette` 会被判成不稳定，
 * 于是「拿它当参数的 composable」永远无法跳过重组。但这里的两个 List 是 `generate()`
 * 现造出来、之后只读的，整套色板生成后不会再变，标 `@Immutable` 是准确的。
 * 收益：换版块 / 进二级页等重组里，只要色板没变，整棵依赖它的子树都能被跳过。
 */
@Immutable
data class PclPalette(
    val color1: Color,
    val color2: Color,
    val color3: Color,
    val color4: Color,
    val color5: Color,
    val color6: Color,
    val color7: Color,
    val color8: Color,
    val bg0: Color,
    val bg1: Color,
    val gray1: Color,
    val gray2: Color,
    val gray3: Color,
    val gray4: Color,
    val gray5: Color,
    val gray6: Color,
    val gray7: Color,
    val gray8: Color,
    val redBack: Color,
    val redLight: Color,
    val redDark: Color,
    val halfWhite: Color,
    val semiWhite: Color,
    val white: Color,
    val transparent: Color,
    val semiTransparent: Color,
    val sidebarBackground: Color,
    /** 标题栏渐变（PCL 为 hue-Δ / hue / hue+Δ 三段）。 */
    val titleBarGradient: List<Color>,
    /**
     * 窗体默认背景渐变（`ModSecret.ThemeRefreshMain` 中 `UiBackgroundColorful` 开启时使用）：
     * 起点 (0.9, 0) → 终点 (0.1, 1)，三段色为 hsl2(hue-15, sat*0.8, 91) / hsl2(hue, ...) / hsl2(hue+15, ...)。
     */
    val bodyGradient: List<Color>,
) {
    /** MyCard 的不透明底色：`Color.FromArgb(245, 255, 255, 255)` */
    val cardBackground: Color = Color(0xF5FFFFFF)

    /** MyButton 底色：`ColorBrushHalfWhite` = `#55ffffff` */
    val buttonBackground: Color = halfWhite

    /** 卡片阴影默认颜色：`ColorObject1` */
    val cardShadow: Color = color1

    /** 卡片阴影悬浮颜色：`ColorObject4` */
    val cardShadowHover: Color = color4
}

/** 主题调色板。 */
val LocalPclPalette = staticCompositionLocalOf { PclPalettes.blue }

/**
 * PCL2 的取色算法。
 *
 * 完整复刻 `MyColor.FromHSL2`（含每个 30° 色相片区的亮度微调表 `cent`），
 * 因此由同一组 (hue, sat, adjust) 生成的颜色与 PCL2 运行时一致。
 */
object PclColorMath {

    private val cent = doubleArrayOf(
        +0.1, -0.06, -0.3,   // 0, 30, 60
        -0.19, -0.15, -0.24, // 90, 120, 150
        -0.32, -0.09, +0.18, // 180, 210, 240
        +0.05, -0.12, -0.02, // 270, 300, 330
        +0.1, -0.06,         // 最后两位与前两位一致
    )

    private fun hue(v1: Double, v2: Double, vH0: Double): Double {
        var vH = vH0
        if (vH < 0) vH += 1.0
        if (vH > 1) vH -= 1.0
        return when {
            vH < 0.16667 -> v1 + (v2 - v1) * 6 * vH
            vH < 0.5 -> v2
            vH < 0.66667 -> v1 + (v2 - v1) * (4 - vH * 6)
            else -> v1
        }
    }

    /** `MyColor.FromHSL` */
    private fun fromHsl(sH: Double, sS: Double, sL: Double): Color {
        if (sS == 0.0) {
            val v = (sL * 2.55).toInt()
            return Color(v, v, v)
        }
        val h = sH / 360.0
        var s = sS / 100.0
        val l = sL / 100.0
        s = if (l < 0.5) s * l + l else s * (1.0 - l) + l
        val l2 = 2 * l - s
        val r = (255 * hue(l2, s, h + 1.0 / 3)).toInt()
        val g = (255 * hue(l2, s, h)).toInt()
        val b = (255 * hue(l2, s, h - 1.0 / 3)).toInt()
        return Color(r, g, b)
    }

    /** `MyColor.FromHSL2` */
    fun fromHsl2(sH0: Double, sS: Double, sL0: Double): Color {
        if (sS == 0.0) {
            val v = (sL0 * 2.55).toInt()
            return Color(v, v, v)
        }
        val sH = ((sH0 + 3600000.0) % 360.0)
        val center0 = sH / 30.0
        val intCenter = kotlin.math.floor(center0).toInt()
        val center = 50.0 - (
            (1 - center0 + intCenter) * cent[intCenter] + (center0 - intCenter) * cent[intCenter + 1]
            ) * sS
        val sL = if (sL0 < center) (sL0 / center) * 50 else (1 + (sL0 - center) / (100 - center)) * 50
        return fromHsl(sH, sS, sL)
    }

    /**
     * 由主色相生成完整调色板，公式逐行对应 `ModSecret.ThemeRefresh`。
     *
     * @param hue   主色相
     * @param sat   饱和度
     * @param adjust 明度微调
     */
    fun generate(hue: Int, sat: Int, adjust: Int = 0, hueTopbarDelta: Int = 0): PclPalette {
        val c1 = fromHsl2(hue.toDouble(), sat * 0.2, 25 + adjust * 0.3)
        val c2 = fromHsl2(hue.toDouble(), sat.toDouble(), 45 + adjust.toDouble())
        val c3 = fromHsl2(hue.toDouble(), sat.toDouble(), 55 + adjust.toDouble())
        val c4 = fromHsl2(hue.toDouble(), sat.toDouble(), 65 + adjust.toDouble())
        val c5 = fromHsl2(hue.toDouble(), sat.toDouble(), 80 + adjust * 0.4)
        val c6 = fromHsl2(hue.toDouble(), sat.toDouble(), 91 + adjust * 0.1)
        val c7 = fromHsl2(hue.toDouble(), sat.toDouble(), 95.0)
        val c8 = fromHsl2(hue.toDouble(), sat.toDouble(), 97.0)

        // ColorBg0 = Color4 * 0.4 + Color5 * 0.4 + ColorGray4 * 0.2
        val g4 = Color(166, 166, 166)
        val bg0 = Color(
            red = (c4.red * 0.4f + c5.red * 0.4f + g4.red * 0.2f),
            green = (c4.green * 0.4f + c5.green * 0.4f + g4.green * 0.2f),
            blue = (c4.blue * 0.4f + c5.blue * 0.4f + g4.blue * 0.2f),
        )
        val bg1 = c7.copy(alpha = 190f / 255f)

        // 顶部条三段渐变：hue-Δ / hue / hue+Δ，明度 48 / 54 / 48
        val d = hueTopbarDelta
        val gradient = listOf(
            fromHsl2((hue - d).toDouble(), sat.toDouble(), 48 + adjust.toDouble()),
            fromHsl2(hue.toDouble(), sat.toDouble(), 54 + adjust.toDouble()),
            fromHsl2((hue + d).toDouble(), sat.toDouble(), 48 + adjust.toDouble()),
        )

        // 窗体背景三段渐变：hue-15 / hue / hue+15，饱和度 ×0.8，明度 91
        val body = listOf(
            fromHsl2((hue - 15).toDouble(), sat * 0.8, 91.0),
            fromHsl2(hue.toDouble(), sat * 0.8, 91.0),
            fromHsl2((hue + 15).toDouble(), sat * 0.8, 91.0),
        )

        return PclPalette(
            color1 = c1, color2 = c2, color3 = c3, color4 = c4,
            color5 = c5, color6 = c6, color7 = c7, color8 = c8,
            bg0 = bg0, bg1 = bg1,
            gray1 = Color(64, 64, 64),
            gray2 = Color(115, 115, 115),
            gray3 = Color(140, 140, 140),
            gray4 = Color(166, 166, 166),
            gray5 = Color(204, 204, 204),
            gray6 = Color(235, 235, 235),
            gray7 = Color(240, 240, 240),
            gray8 = Color(245, 245, 245),
            redBack = Color(0x80FBDDDD),
            redLight = Color(0xFFFF4C4C),
            redDark = Color(0xFFCE2111),
            halfWhite = Color(0x55FFFFFF),
            semiWhite = Color(0xBBFFFFFF),
            white = Color(0xFFFFFFFF),
            transparent = Color.Transparent,
            semiTransparent = Color(0x01EAF2FE),
            sidebarBackground = Color(0xF1FFFFFF),
            titleBarGradient = gradient,
            bodyGradient = body,
        )
    }
}

object PclPalettes {

    /**
     * PCL2 默认主题（蓝色）。
     *
     * 关键：PCL2 **运行时**并不是直接用 `Application.xaml` 里写的那组十六进制值，
     * 而是在启动时由 `FormMain.xaml.vb:277 → ThemeRefresh(Settings("UiLauncherTheme"))`
     * → `ModSecret.ThemeRefresh` 现算一遍，并覆盖资源字典里的 `ColorBrush1..8`。
     *
     * 默认参数为 `ColorHue = 210`、`ColorSat = 85`、`ColorLightAdjust = 0`、
     * `ColorHueTopbarDelta = 0`（见 `ModSecret.vb` 字段声明），因此统一走
     * [PclColorMath.generate] 生成，等价于 PCL2 启动后的真实色板——
     * 包括顶部条那条**三段渐变**（而不是 `Application.xaml` 里的单色占位）。
     *
     * 附：`Application.xaml` 的静态设计值为
     * #343D4A / #0B5BCB / #1370F3 / #4890F5 / #96C0F9 / #D5E6FD / #E0EAFD / #EAF2FE，
     * 但它们在运行时会被覆盖，故不作为最终显示值。
     */
    val blue: PclPalette = PclColorMath.generate(hue = 210, sat = 85, adjust = 0, hueTopbarDelta = 0)

    /**
     * 液态玻璃皮肤专用：**饱和度归零**的中性色板。
     *
     * 玻璃要无色才透得干净；带绿/蓝调时整块卡片都会被染色，观感从玻璃变成塑料板。
     * 玻璃态下换成这组灰阶，只保留明度层次，颜色交给壁纸。
     */
    val liquidGlass: PclPalette = PclColorMath.generate(hue = 210, sat = 0, adjust = 2)

    /** `Application.xaml` 中随发行版声明的静态设计色板（留作对照）。 */
    val blueDesignTokens = PclPalette(
        color1 = Color(0xFF343D4A),
        color2 = Color(0xFF0B5BCB),
        color3 = Color(0xFF1370F3),
        color4 = Color(0xFF4890F5),
        color5 = Color(0xFF96C0F9),
        color6 = Color(0xFFD5E6FD),
        color7 = Color(0xFFE0EAFD),
        color8 = Color(0xFFEAF2FE),
        bg0 = Color(0xFF96C0F9),
        bg1 = Color(0xBEE0EAFD),
        gray1 = Color(0xFF404040),
        gray2 = Color(0xFF737373),
        gray3 = Color(0xFF8C8C8C),
        gray4 = Color(0xFFA6A6A6),
        gray5 = Color(0xFFCCCCCC),
        gray6 = Color(0xFFEBEBEB),
        gray7 = Color(0xFFF0F0F0),
        gray8 = Color(0xFFF5F5F5),
        redBack = Color(0x80FBDDDD),
        redLight = Color(0xFFFF4C4C),
        redDark = Color(0xFFCE2111),
        halfWhite = Color(0x55FFFFFF),
        semiWhite = Color(0xBBFFFFFF),
        white = Color(0xFFFFFFFF),
        transparent = Color.Transparent,
        semiTransparent = Color(0x01EAF2FE),
        sidebarBackground = Color(0xF1FFFFFF),
        // 默认主题 ColorHueTopbarDelta = 0，因此三段同色
        titleBarGradient = listOf(Color(0xFF4890F5), Color(0xFF4890F5), Color(0xFF4890F5)),
        // 默认窗体背景：hsl2(195/210/225, 68, 91)
        bodyGradient = listOf(Color(0xFFCAEAF5), Color(0xFFD3E5F7), Color(0xFFDBE2F8)),
    )

    /**
     * PCL2 内置主题的 (名称, 色相, 饱和度, 明度微调) 表。
     *
     * 取值与 PCL2 `页面设置 → 个性化 → 启动器主题` 中的主题一一对应；
     * 通过 [PclColorMath.generate] 还原为完整色板。
     */
    val builtInThemes: List<PclThemeEntry> = listOf(
        PclThemeEntry("蔚蓝", 210, 85, 0),
        PclThemeEntry("浅蓝", 205, 70, 12),
        PclThemeEntry("深蓝", 214, 90, -10),
        PclThemeEntry("青空", 195, 80, 0),
        PclThemeEntry("紫罗兰", 275, 70, 0),
        PclThemeEntry("薄荷", 165, 65, 0),
        PclThemeEntry("祖母绿", 150, 70, -6),
        PclThemeEntry("暖橙", 28, 85, 0),
        PclThemeEntry("琥珀", 45, 85, -4),
        PclThemeEntry("落日", 12, 80, 0),
        PclThemeEntry("樱粉", 335, 75, 4),
        PclThemeEntry("石板灰", 210, 8, 0),
    )
}

/** 一个 PCL2 主题条目。 */
data class PclThemeEntry(
    val name: String,
    val hue: Int,
    val sat: Int,
    val adjust: Int,
) {
    /** 按 PCL2 的算法生成的完整色板。 */
    val palette: PclPalette by lazy { PclColorMath.generate(hue, sat, adjust) }
}
