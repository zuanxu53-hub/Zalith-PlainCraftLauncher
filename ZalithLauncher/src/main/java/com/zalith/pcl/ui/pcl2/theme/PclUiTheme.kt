/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「界面皮肤」插件协议 —— 启动器侧的通用解释器。
 *
 * 插件只提供数据，启动器负责画；两边只靠一份 `ui.json` 沟通：
 * ```
 * 插件 APK
 *   assets/zplugin/plugin.json   清单（name / ui / target…）
 *   assets/zplugin/ui.json       皮肤参数（本文件解析的那份）
 * ```
 *
 * `ui.json` 里最关键的字段是 `renderer`，它决定用哪套画法：
 * - `glass` —— 液态玻璃：模糊 + 边缘折射 + 高光（走 Kyant Backdrop）
 * - `flat`  —— 纯实色/半透明换色：只改颜色、圆角，不做模糊（低端机友好）
 *
 * 所以这里**不是只为液态玻璃写的**：任何换色/换圆角/换透明度的皮肤都能用同一份协议，
 * 以后加渲染器只要在 [PclUiRenderer] 里补一档，插件端不用重新编译。
 *
 * 三条硬约束（都是踩出来的）：
 * 1. **只有背景那一层参与录制**（见 `PclShell`）。玻璃控件自己不录，否则互相吸。
 * 2. `lens()` 只支持圆角矩形（CornerBasedShape），别的形状绘制时会抛异常 ——
 *    这里先判断形状，宁可不折射也不能崩。
 * 3. 弹窗是**另一个窗口**，坐标对不上，必须把 [LocalPclUiTheme] 置 null（见 `PclDialogs`）。
 *
 * Android 12 以下没有 RenderEffect，`glass` 会静默退化成 `flat` 的效果。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** 渲染器：决定“表面”怎么画。 */
enum class PclUiRenderer {
    /** 模糊 + 折射 + 高光（需要 Android 12+ 才有真效果）。 */
    Glass,

    /** 纯色 / 半透明换色，不做模糊。 */
    Flat,
}

/** 玻璃渲染器的参数（`renderer = "glass"` 时才用）。 */
data class PclGlassParams(
    /** 高斯模糊半径（dp）。 */
    val blur: Float = 9f,
    /** 边缘折射强度（dp）。0 = 只模糊不折射。 */
    val refraction: Float = 22f,
    /** 折射影响的高度（dp）：离边缘多深开始弯折背景。 */
    val refractionHeight: Float = 12f,
    /** 边缘高光强度（0..1）。 */
    val highlight: Float = 0.62f,
    /** 投影强度（0..1）。 */
    val shadow: Float = 0.10f,
    /** 背景提饱和度，玻璃看起来更透。 */
    val vibrancy: Boolean = true,
    /** 标题栏 / 侧栏这些大面积要不要一起做。 */
    val panel: Boolean = true,
)

/** 描边策略：`none` = 玻璃态一律不画线；`hairline` = 保留 PCL 的细线。 */
enum class PclBorderMode { None, Hairline }

/** 描边参数（`border` 段）。 */
data class PclBorderStyle(
    val mode: PclBorderMode = PclBorderMode.None,
    /** 覆盖描边颜色；null = 用 PCL 原本的边框色。 */
    val color: Color? = null,
    val alpha: Float = 1f,
)

/**
 * 单个「角色」的样式覆盖。
 *
 * 角色是启动器暴露给插件的**接口点**：`card` / `button` / `input` / `item` / `hint` /
 * `pill` / `slider` / `sidebar`。插件可以按角色单独决定「要不要玻璃、什么颜色」，
 * 这样某一块不满意只改 ui.json，不用重新编译启动器。
 */
data class PclRoleStyle(
    /** 这块要不要玻璃；null = 跟随 `renderer`。 */
    val glass: Boolean? = null,
    val color: Color? = null,
    val alpha: Float? = null,
    val corner: Float? = null,
)

/**
 * 一套皮肤。
 *
 * 全部有默认值：插件只写 `{"renderer":"glass"}` 也能用 —— 默认就是液态玻璃的手感。
 */
data class PclUiTheme(
    /** 皮肤标识（用于显示，如 `liquid_glass`）。 */
    val id: String = "",
    /** 皮肤名字（给设置页显示）。 */
    val name: String = "",
    val renderer: PclUiRenderer = PclUiRenderer.Glass,
    /** 卡片底色。 */
    val cardColor: Color = Color.White,
    /** 卡片底色的不透明度（0..1）。 */
    val cardAlpha: Float = 0.26f,
    /** 侧栏底色；null = 沿用调色板原色。 */
    val sidebarColor: Color? = null,
    /** 侧栏底色不透明度；null = 不透明。 */
    val sidebarAlpha: Float? = null,
    /** 卡片圆角（dp）；null = 用 PCL 原本的圆角。 */
    val corner: Float? = null,
    /** 按钮圆角（dp）；null = 用 PCL 原本的圆角。 */
    val buttonCorner: Float? = null,
    val glass: PclGlassParams = PclGlassParams(),
    /** 角色级覆盖（见 [PclRoleStyle]）。 */
    val roles: Map<String, PclRoleStyle> = emptyMap(),
    /** 描边策略，默认 `none`（玻璃态不该有硬边）。 */
    val border: PclBorderStyle = PclBorderStyle(),
    /** true = 玻璃态下把色板换成中性灰阶（白色主题）。 */
    val neutralPalette: Boolean = false,
    /** true = 滑条用移植来的液态实现（会与页面滚动抢手势，默认关）。 */
    val sliderLiquid: Boolean = false,
    /** 按钮文字是否强制居中；PCL 原本就是居中，玻璃外壳容易丢，默认开。 */
    val buttonTextCenter: Boolean = true,
)

/* ---------------------------------------------------------------- 解析 */

private data class SideJson(
    @SerializedName("color") val color: String? = null,
    @SerializedName("alpha") val alpha: Float? = null,
)

private data class GlassJson(
    @SerializedName("blur") val blur: Float? = null,
    @SerializedName("refraction") val refraction: Float? = null,
    @SerializedName("refractionHeight") val refractionHeight: Float? = null,
    @SerializedName("highlight") val highlight: Float? = null,
    @SerializedName("shadow") val shadow: Float? = null,
    @SerializedName("vibrancy") val vibrancy: Boolean? = null,
    @SerializedName("panel") val panel: Boolean? = null,
)

/** `ui.json` 的原始结构；字段全部可缺省，缺省即用默认值。 */
private data class RoleJson(
    @SerializedName("glass") val glass: Boolean? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("alpha") val alpha: Float? = null,
    @SerializedName("corner") val corner: Float? = null,
)

private data class BorderJson(
    @SerializedName("mode") val mode: String? = null,
    @SerializedName("color") val color: String? = null,
    @SerializedName("alpha") val alpha: Float? = null,
)

private data class SliderJson(
    @SerializedName("liquid") val liquid: Boolean? = null,
)

private data class ButtonJson(
    @SerializedName("textAlign") val textAlign: String? = null,
)

private data class ThemeJson(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("renderer") val renderer: String? = null,
    @SerializedName("card") val card: SideJson? = null,
    @SerializedName("sidebar") val sidebar: SideJson? = null,
    @SerializedName("corner") val corner: Float? = null,
    @SerializedName("buttonCorner") val buttonCorner: Float? = null,
    @SerializedName("glass") val glass: GlassJson? = null,
    @SerializedName("roles") val roles: Map<String, RoleJson>? = null,
    @SerializedName("border") val border: BorderJson? = null,
    @SerializedName("palette") val palette: String? = null,
    @SerializedName("slider") val slider: SliderJson? = null,
    @SerializedName("button") val button: ButtonJson? = null,
)

private val themeGson: Gson = Gson()

/**
 * 解析一份 `ui.json`。
 *
 * 解析失败时**不是**丢掉皮肤，而是退回默认参数：插件写错一个字段不该让界面报错。
 */
fun parsePclUiTheme(json: String, fallbackId: String = ""): PclUiTheme {
    val raw = runCatching { themeGson.fromJson(json, ThemeJson::class.java) }.getOrNull()
        ?: return PclUiTheme(id = fallbackId)
    val def = PclUiTheme()
    val renderer = when (raw.renderer?.trim()?.lowercase()) {
        "flat" -> PclUiRenderer.Flat
        "glass" -> PclUiRenderer.Glass
        else -> def.renderer
    }
    return PclUiTheme(
        id = raw.id?.trim()?.ifBlank { null } ?: fallbackId,
        name = raw.name?.trim().orEmpty(),
        renderer = renderer,
        cardColor = raw.card?.color?.let(::parseHexColor) ?: def.cardColor,
        cardAlpha = raw.card?.alpha?.coerceIn(0f, 1f) ?: def.cardAlpha,
        sidebarColor = raw.sidebar?.color?.let(::parseHexColor),
        sidebarAlpha = raw.sidebar?.alpha?.coerceIn(0f, 1f),
        corner = raw.corner?.takeIf { it >= 0f },
        buttonCorner = raw.buttonCorner?.takeIf { it >= 0f },
        roles = raw.roles?.mapNotNull { (key, value) ->
            val style = PclRoleStyle(
                glass = value.glass,
                color = value.color?.let(::parseHexColor),
                alpha = value.alpha?.coerceIn(0f, 1f),
                corner = value.corner?.takeIf { it >= 0f },
            )
            key.trim().lowercase().ifBlank { null }?.let { it to style }
        }?.toMap().orEmpty(),
        border = PclBorderStyle(
            mode = when (raw.border?.mode?.trim()?.lowercase()) {
                "hairline", "line", "stroke" -> PclBorderMode.Hairline
                else -> PclBorderMode.None
            },
            color = raw.border?.color?.let(::parseHexColor),
            alpha = raw.border?.alpha?.coerceIn(0f, 1f) ?: 1f,
        ),
        neutralPalette = raw.palette?.trim()?.lowercase() == "neutral",
        sliderLiquid = raw.slider?.liquid ?: false,
        buttonTextCenter = raw.button?.textAlign?.trim()?.lowercase() != "start",
        glass = PclGlassParams(
            blur = raw.glass?.blur?.coerceIn(0f, 40f) ?: def.glass.blur,
            refraction = raw.glass?.refraction?.coerceIn(0f, 80f) ?: def.glass.refraction,
            refractionHeight = raw.glass?.refractionHeight?.coerceIn(0f, 60f)
                ?: def.glass.refractionHeight,
            highlight = raw.glass?.highlight?.coerceIn(0f, 1f) ?: def.glass.highlight,
            shadow = raw.glass?.shadow?.coerceIn(0f, 1f) ?: def.glass.shadow,
            vibrancy = raw.glass?.vibrancy ?: def.glass.vibrancy,
            panel = raw.glass?.panel ?: def.glass.panel,
        ),
    )
}

/** `#RRGGBB` / `#AARRGGBB`（第 5 位起是 alpha 的写法也认）。 */
private fun parseHexColor(text: String): Color? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 && hex.length != 8) return null
    val value = hex.toLongOrNull(16) ?: return null
    return if (hex.length == 6) Color(0xFF000000L or value) else Color(value)
}

/* ---------------------------------------------------------------- 状态 */

/**
 * 当前页面的皮肤；null = 没装皮肤插件，控件照旧走 PCL 的实色。
 *
 * 由 `PclShell` 按插件状态填（[PclUiRuntime]），弹窗里会被置 null。
 */
val LocalPclUiTheme = staticCompositionLocalOf<PclUiTheme?> { null }

/**
 * 当前窗口的「背景底片」。
 *
 * 只有 `renderer = glass` 时才会录制；控件把它传给 [pclSurface] 取模糊与折射。
 * 弹窗里必须置 null（另一个窗口，坐标对不上）。
 */
val LocalPclBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** 当前是否装了皮肤插件。 */
val pclUiThemed: Boolean
    @Composable get() = LocalPclUiTheme.current != null

/** 这台机器能不能真的做模糊/折射（RenderEffect 要 Android 12+）。 */
val pclGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * 全局皮肤状态。
 *
 * 为什么不直接读插件：读 `ui.json` 是文件 IO，不能放进组合里每帧跑；
 * 而且「装了 / 停用了」要能主动触发重组，所以用一个可观察的变量兜一层。
 */
object PclUiRuntime {
    /** 当前生效的皮肤；null = 未启用。 */
    var theme by mutableStateOf<PclUiTheme?>(null)
        private set

    /** 由插件目录里的 `ui.json` 文本刷新一次。必须在 IO 线程调用。 */
    fun apply(json: String?, id: String = "") {
        theme = json?.let { parsePclUiTheme(it, id) }
    }

    fun clear() {
        theme = null
    }
}

/* ---------------------------------------------------------------- 绘制 */

/** 表面类型，决定玻璃参数按哪个比例缩放。 */
enum class PclSurface {
    /** 大卡片：糊一点、折射厚一点。 */
    Card,

    /** 按钮 / 小控件：薄一些，折射别太夸张，否则字会跟着变形。 */
    Control,

    /** 侧栏 / 标题栏这种大面积：几乎不折射，只要一层雾。 */
    Panel,
}

/**
 * 把主题里的「表面」画在 [shape] 范围内。
 *
 * `flat` 渲染器只填一层半透明色；`glass` 渲染器走 backdrop（拿不到底片时也自动退化成 flat）。
 *
 * @param backdrop 背景底片；null 时不做模糊
 * @param fallbackColor 没装皮肤（theme == null）时用 PCL 原本的颜色
 */
fun Modifier.pclSurface(
    theme: PclUiTheme?,
    backdrop: Backdrop?,
    shape: Shape,
    surface: PclSurface = PclSurface.Card,
    fallbackColor: Color = Color.White,
    colorOverride: Color? = null,
    alphaScale: Float = 1f,
    /**
     * 角色名（`card` / `button` / `input` / `item` / `hint` / `pill` / `slider`）。
     *
     * 插件可以在 ui.json 的 `roles` 里按角色单独指定「要不要玻璃、什么颜色」——
     * 这是启动器开放给插件的接口点，改外观不用改启动器代码。
     */
    role: String = "",
): Modifier {
    if (theme == null) return this.background(fallbackColor, shape)

    val roleStyle = theme.roles[role]
    val color = roleStyle?.color ?: colorOverride ?: theme.cardColor
    val alpha = ((roleStyle?.alpha ?: theme.cardAlpha) * alphaScale).coerceIn(0f, 1f)
    // 角色显式关掉玻璃（或整体渲染器是 flat / 老系统 / 无底片）→ 老老实实填色
    val wantGlass = roleStyle?.glass ?: true

    if (!wantGlass || theme.renderer == PclUiRenderer.Flat || backdrop == null || !pclGlassSupported) {
        return this.background(color.copy(alpha = alpha), shape)
    }

    val glass = theme.glass
    val (blurScale, bounceScale) = when (surface) {
        PclSurface.Card -> 1f to 1f
        PclSurface.Control -> 0.55f to 0.55f
        PclSurface.Panel -> 1.35f to 0.35f
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (glass.vibrancy) vibrancy()
            blur((glass.blur * blurScale).dp.toPx())
            // lens 只吃圆角矩形，别的形状会抛异常
            if (shape is CornerBasedShape && glass.refraction > 0f) {
                lens(
                    (glass.refractionHeight * bounceScale).dp.toPx(),
                    (glass.refraction * bounceScale).dp.toPx(),
                )
            }
        },
        // 高光用「环境光」而不是默认的 1dp 亮边：
        // 那圈 1dp 在浅色底上就是一道硬边框，玻璃立刻变塑料壳。
        highlight = {
            Highlight(
                width = 0.dp,
                alpha = glass.highlight.coerceIn(0f, 1f) * 0.4f,
                style = HighlightStyle.Ambient(intensity = glass.highlight.coerceIn(0f, 1f) * 0.5f),
            )
        },
        // 内阴影原来写的是 #2A3A55 @0.55 —— 在浅色背景上就是一圈黑边（截图里 版本选择 那圈）。
        // 压成极淡的冷灰，只留一点体积感。
        innerShadow = {
            InnerShadow(radius = 18.dp, color = Color(0xFF9AA6B4), alpha = 0.14f)
        },
        shadow = {
            Shadow(
                radius = 26.dp,
                color = Color(0xFF4A5666),
                alpha = (glass.shadow.coerceIn(0f, 1f) * 0.6f),
            )
        },
        onDrawSurface = {
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
        },
    )
}

/** 皮肤表面 + PCL 描边：**装了皮肤就不画描边**（玻璃自带柔光边缘，再叠一圈线就成了塑料壳）。 */
fun Modifier.pclSurfaceBordered(
    theme: PclUiTheme?,
    backdrop: Backdrop?,
    shape: Shape,
    surface: PclSurface,
    fillColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    alphaScale: Float = 1f,
    role: String = "",
): Modifier {
    val base = pclSurface(
        theme = theme,
        backdrop = backdrop,
        shape = shape,
        surface = surface,
        fallbackColor = fillColor,
        colorOverride = fillColor,
        alphaScale = alphaScale,
        role = role,
    )
    // 装了皮肤时由插件说了算：`border.mode = none` 就一条线都不画
    val drawBorder = theme == null || theme.border.mode == PclBorderMode.Hairline
    if (!drawBorder) return base
    val color = theme?.border?.color?.copy(alpha = theme.border.alpha) ?: borderColor
    return base.border(borderWidth, color, shape)
}

/** 皮肤给侧栏用的底色：没装皮肤或没写 sidebar 就返回 null（调用方用原色）。 */
fun PclUiTheme?.sidebarTint(): Color? {
    val theme = this ?: return null
    val base = theme.sidebarColor ?: return null
    val alpha = theme.sidebarAlpha ?: return base
    return base.copy(alpha = alpha.coerceIn(0f, 1f))
}

/** 皮肤覆盖的圆角；没写就用调用方给的默认值。 */
fun PclUiTheme?.cornerOr(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    this?.corner?.dp ?: default

/** 皮肤覆盖的按钮圆角。 */
fun PclUiTheme?.buttonCornerOr(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    this?.buttonCorner?.dp ?: default
