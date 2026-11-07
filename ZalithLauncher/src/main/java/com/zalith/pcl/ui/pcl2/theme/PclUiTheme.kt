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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
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
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec

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
    /**
     * 高光形态：
     * - `true` = **方向性镜面边**（`HighlightStyle.Default`）：一侧亮、对角渐暗，会随角度变化。
     *   液态玻璃的「液」感主要就来自这条不对称的边 —— 它模拟的是光线在玻璃厚度上被弯折；
     * - `false` = 均匀环境柔光（`Ambient`），看着更干净，但那只是磨砂亚克力，不是液态玻璃。
     */
    val highlightDirectional: Boolean = true,
    /** 方向性高光的角度（度），45 = 左上亮、右下暗。 */
    val highlightAngle: Float = 45f,
    /** 高光边的宽度（dp）。太粗会像描边，0.8~1.6 之间最像玻璃。 */
    val highlightWidth: Float = 1.2f,
    /**
     * **光散射**强度（0..1）。
     *
     * 真实的玻璃不是「清晰 + 模糊」这么简单：光进入介质后会在内部被打散，
     * 在玻璃表面上留下一层极淡的雾（frost / haze），靠近边缘更明显。
     * 缺了这层雾，玻璃看起来像贴了一张模糊的图片，而不是一块材质。
     *
     * 这里在表面色之上叠一层很薄的白，配合 `blur()` 使用。
     * 建议 0.06 ~ 0.16；再高就变成磨砂塑料了。
     */
    val haze: Float = 0f,
    /**
     * **边缘辉光**（光的散射）倍数，配合 [highlightWidth] 用。
     *
     * 镜面边本身是硬的（0.5dp 一条亮线）；真实玻璃的边缘还会把光散出去，
     * 形成一圈由边缘向内衰减的柔光。`Highlight` 的 `blurRadius` 就是这个：
     * 值 = 宽度 × 倍数。1 等于不散，2~4 之间最像玻璃，再大就开始糊成雾。
     */
    val edgeGlow: Float = 2.5f,
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
 * 一个「布局槽位」的覆盖：把这个位置内置的内容换成插件声明的页面。
 *
 * 槽位名是「版块 + 位置」：`home` / `download` / `link` / `setup` / `other`，
 * 每个版块有 `left`（左栏）与 `content`（右侧内容区）两个位置。
 *
 * 只覆盖**内容**，不动骨架：顶栏、侧栏宽度、二级页导航都还是启动器管，
 * 所以插件写坏一页最多是「这一版块变空」，不会让整个界面不可用 ——
 * 停用插件就能恢复。
 */
data class PclSlotOverride(
    /** 左栏换成哪个插件页面（`pages.json` 的 id）。 */
    val left: String? = null,
    /** 右侧内容区换成哪个插件页面。 */
    val content: String? = null,
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
    /**
     * 角色素材（`ui.json` 的 `assets` 段）：用图片顶掉某个角色的表面。
     *
     * key 是角色名（`card` / `button` / `input` / `pill` …），外加一个特殊 key
     * `background`（窗体背景图，由 `PclShell` 的背景层消费）。
     * 有素材的角色在 [pclSurface] 里直接贴图，不再走玻璃或纯色。
     */
    val assets: Map<String, PclAssetSpec> = emptyMap(),
    /**
     * 布局槽位覆盖（`ui.json` 的 `layout` 段）。
     *
     * key 是版块名，value 指定该版块的左栏 / 内容区用哪个插件页面。
     * 空表 = 全用启动器内置页面。
     */
    val layout: Map<String, PclSlotOverride> = emptyMap(),
    /** 描边策略，默认 `none`（玻璃态不该有硬边）。 */
    val border: PclBorderStyle = PclBorderStyle(),
    /** true = 玻璃态下把色板换成中性灰阶（白色主题）。 */
    val neutralPalette: Boolean = false,
    /** true = 滑条用移植来的液态实现（会与页面滚动抢手势，默认关）。 */
    val sliderLiquid: Boolean = false,
    /** 按钮文字是否强制居中；PCL 原本就是居中，玻璃外壳容易丢，默认开。 */
    val buttonTextCenter: Boolean = true,
    /**
     * 玻璃的覆盖范围：
     * - `cards`（默认）只给卡片 / 标题栏 / 侧栏上玻璃，按钮、输入框、列表保持 PCL 原样；
     * - `all` 连按钮与输入框一起玻璃化。
     *
     * 默认保守是有代价的：全量玻璃化会与 PCL 自带的状态色、描边、禁用态打架，
     * 出现「深色块、文字看不清、控件像消失」这类问题，而卡片这一层最安全。
     */
    val glassAllControls: Boolean = false,
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
    @SerializedName("highlightStyle") val highlightStyle: String? = null,
    @SerializedName("highlightAngle") val highlightAngle: Float? = null,
    @SerializedName("highlightWidth") val highlightWidth: Float? = null,
    @SerializedName("haze") val haze: Float? = null,
    @SerializedName("edgeGlow") val edgeGlow: Float? = null,
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

private data class LayoutSlotJson(
    @SerializedName("left") val left: String? = null,
    @SerializedName("content") val content: String? = null,
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
    @SerializedName("layout") val layout: Map<String, LayoutSlotJson>? = null,
    /** 素材：值可以是字符串（只给路径）或对象（路径 + 九宫格切片）。 */
    @SerializedName("assets") val assets: Map<String, com.google.gson.JsonElement>? = null,
    @SerializedName("border") val border: BorderJson? = null,
    @SerializedName("palette") val palette: String? = null,
    @SerializedName("slider") val slider: SliderJson? = null,
    @SerializedName("button") val button: ButtonJson? = null,
    @SerializedName("scope") val scope: String? = null,
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
        assets = raw.assets?.mapNotNull { (key, value) ->
            val slot = key.trim().lowercase().ifBlank { null } ?: return@mapNotNull null
            val spec = runCatching {
                when {
                    value.isJsonPrimitive -> value.asString.trim().ifBlank { null }
                        ?.let { PclAssetSpec(src = it) }
                    value.isJsonObject -> {
                        val obj = value.asJsonObject
                        val src = obj.get("src")?.takeIf { it.isJsonPrimitive }
                            ?.asString?.trim()?.ifBlank { null } ?: return@runCatching null
                        val slice = obj.get("slice")?.takeIf { it.isJsonPrimitive }
                            ?.asInt ?: 0
                        PclAssetSpec(src = src, slice = slice.coerceIn(0, 512))
                    }
                    else -> null
                }
            }.getOrNull()
            spec?.let { slot to it }
        }?.toMap().orEmpty(),
        layout = raw.layout?.mapNotNull { (key, value) ->
            val slot = key.trim().lowercase().ifBlank { null } ?: return@mapNotNull null
            val override = PclSlotOverride(
                left = value.left?.trim()?.ifBlank { null },
                content = value.content?.trim()?.ifBlank { null },
            )
            if (override.left == null && override.content == null) null else slot to override
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
        glassAllControls = raw.scope?.trim()?.lowercase() == "all",
        glass = PclGlassParams(
            blur = raw.glass?.blur?.coerceIn(0f, 40f) ?: def.glass.blur,
            refraction = raw.glass?.refraction?.coerceIn(0f, 80f) ?: def.glass.refraction,
            refractionHeight = raw.glass?.refractionHeight?.coerceIn(0f, 60f)
                ?: def.glass.refractionHeight,
            highlightDirectional = raw.glass?.highlightStyle?.trim()?.lowercase() != "ambient",
            highlightAngle = raw.glass?.highlightAngle ?: 45f,
            highlightWidth = (raw.glass?.highlightWidth ?: 1.2f).coerceIn(0f, 4f),
            haze = (raw.glass?.haze ?: 0f).coerceIn(0f, 0.5f),
            edgeGlow = (raw.glass?.edgeGlow ?: 2.5f).coerceIn(1f, 8f),
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

/**
 * 当前 composition 的内容原点在**屏幕**上的位置。
 *
 * 主窗口是全屏的，原点就是 (0,0)，用不上这个。
 * 但弹窗（`Dialog`）是一个**尺寸包裹内容、居中显示**的独立窗口：
 * 它的 `positionInRoot` 从自己窗口的左上角算起，而软底片是整屏的，
 * 不补这个偏移，弹窗里的玻璃就会取到屏幕左上角那块背景（错位）。
 */
val LocalPclScreenOffset = staticCompositionLocalOf<androidx.compose.ui.geometry.Offset> {
    androidx.compose.ui.geometry.Offset.Zero
}

/** 当前是否装了皮肤插件。 */
val pclUiThemed: Boolean
    @Composable get() = LocalPclUiTheme.current != null

/** 这台机器能不能真的做模糊（RenderEffect 要 Android 12 / API 31）。 */
val pclGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 折射（lens）要 RuntimeShader，**Android 13 / API 33** 才有。 */
val pclRefractionSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 一句人话的诊断，显示在插件管理页上。
 *
 * 玻璃「看着没生效」通常有三种原因，靠猜永远猜不中：
 * 1. 系统低于 Android 12 —— 没有 RenderEffect，模糊做不了；
 * 2. 系统是 12 —— 有模糊但没有 RuntimeShader，折射做不了；
 * 3. 背景是纯色/没壁纸 —— 没有东西可折，糊出来自然是一片白。
 */
fun pclGlassDiagnostics(theme: PclUiTheme?): String {
    if (theme == null) return "未启用皮肤"
    return buildString {
        append("渲染器 ").append(if (theme.renderer == PclUiRenderer.Glass) "glass" else "flat")
        append(" · 系统 API ").append(Build.VERSION.SDK_INT)
        append(" · 模糊 ").append(if (pclGlassSupported) "可用" else "不可用（需 Android 12）")
        append(" · 折射 ").append(if (pclRefractionSupported) "可用" else "不可用（需 Android 13）")
    }
}

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

    /**
     * 低版本用的「模糊背景底片」。
     *
     * Android 12+ 走系统的 `RenderEffect`，这张图用不上（null）；
     * 低版本没有那套接口，只能自己准备一张缩小过的背景图给玻璃采样。
     * 由 `PclShell` 在壁纸 / 窗口尺寸变化时重建。
     */
    var soft by mutableStateOf<PclSoftBackdrop?>(null)

    /**
     * 当前启用的插件声明的全部页面。
     *
     * 由 `PclShell` 在启动时读一次、插件增删时刷新。放全局是因为
     * 「布局槽位」要在渲染版块时按 id 找页面，而那个位置拿不到插件目录。
     */
    var pages by mutableStateOf<List<PclPluginPageSpec>>(emptyList())

    /** 当前皮肤插件的目录（`ui.json` 里的素材路径相对于它）。 */
    var assetDir by mutableStateOf<java.io.File?>(null)

    /** 按 id 找插件页面；找不到返回 null（宁可退回内置界面，也不显示空白）。 */
    fun pageById(id: String?): PclPluginPageSpec? =
        id?.let { key -> pages.firstOrNull { it.id == key } }

    /** 由插件目录里的 `ui.json` 文本刷新一次。必须在 IO 线程调用。 */
    fun apply(json: String?, id: String = "") {
        theme = json?.let { parsePclUiTheme(it, id) }
    }

    fun clear() {
        theme = null
        soft = null
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

    // 插件给这个角色准备了图片：直接贴图，玻璃与纯色都不再参与。
    // 放在最前面是因为素材是「最终形态」——再叠玻璃只会在图上糊一层，把素材弄脏。
    theme.assets[role]?.let { asset ->
        if (PclAssetLoader.resolve(asset) != null) {
            return this.pclImageSurface(asset, shape)
        }
    }

    val roleStyle = theme.roles[role]
    val color = roleStyle?.color ?: colorOverride ?: theme.cardColor
    // 透明度优先级：roles 里写的 > 传进来的颜色自带的 > cardAlpha。
    // 第三条是为了让调用方能按状态给不同厚度（比如胶囊的选中/未选中/禁用），
    // 又不用在 ui.json 里写死一个值。
    val alpha = (
        (roleStyle?.alpha ?: colorOverride?.alpha?.takeIf { it < 1f } ?: theme.cardAlpha)
            * alphaScale
        ).coerceIn(0f, 1f)
    // 角色显式写了 `glass` 就按它；没写就退回 `scope` 那个总开关。
    // 这样插件可以精确到「按钮玻璃、输入框不动」。
    val wantGlass = roleStyle?.glass ?: theme.glassAllControls

    if (!wantGlass) {
        // 这个角色不玻璃 → **原样填色**，连透明度都不动。
        // 之前这里还会套一层 roleAlpha/cardAlpha，结果「关掉玻璃」的列表项其实还是被
        // 压成半透明，露出底下深色背景 → 看着像深灰块。
        return this.background(color, shape)
    }
    if (theme.renderer == PclUiRenderer.Flat) {
        return this.background(color.copy(alpha = alpha), shape)
    }

    // 系统太老（没有 RenderEffect）或拿不到底片：走不了 `drawBackdrop`。
    //
    // 这里以前是「上亮下暗的半透明色块 + 6dp 投影」—— 那是**一块白板**，
    // 在壁纸上就是一排白条，跟玻璃没关系（用户一眼就看出来了）。
    // 现在优先用自绘的「软玻璃」：拿一张预模糊的背景图按屏幕坐标采样，
    // 再补上镜面边、内侧柔光、底部暗边。只有连这张图都没有时才退到极淡的一层色。
    if (backdrop == null || !pclGlassSupported) {
        val soft = PclUiRuntime.soft
        if (soft != null) {
            return this.pclSoftGlass(
                theme = theme,
                soft = soft,
                shape = shape,
                surface = surface,
                fill = color,
                fillAlpha = alpha,
            )
        }
        return this.pclFadedGlass(
            shape = shape,
            fill = color,
            fillAlpha = (alpha * 0.55f).coerceIn(0f, 1f),
            rimAlpha = (theme.glass.highlight * 0.55f).coerceIn(0f, 1f),
        )
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
        // 高光：这是液态玻璃和「磨砂玻璃」的分界线。
        //
        // 之前为了修「黑边」把它换成了 Ambient（均匀柔光），结果边框是没了，
        // 玻璃也没了 —— 均匀柔光看起来就是「没有边」。
        // 真正的镜面边是**方向性**的：左上亮、对角渐暗，宽度只有 1dp 左右。
        // 它不像描边那样只画一圈，而是参与亮度叠加（BlendMode.Plus），
        // 所以背景越亮它越亮、背景越暗它反而收进去，不会变成硬线。
        highlight = {
            val a = glass.highlight.coerceIn(0f, 1f)
            if (glass.highlightDirectional) {
                Highlight(
                    width = glass.highlightWidth.dp,
                    // 边缘的散射：blurRadius = 宽度 × 倍数。
                    // 没有它，边缘就是一条硬亮线；有了它，光才「散」得出去。
                    blurRadius = (glass.highlightWidth * glass.edgeGlow).dp,
                    alpha = a,
                    style = HighlightStyle.Default(angle = glass.highlightAngle),
                )
            } else {
                Highlight(
                    width = 0.dp,
                    alpha = a * 0.4f,
                    style = HighlightStyle.Ambient(intensity = a * 0.5f),
                )
            }
        },
        // **不加内阴影**。
        // 源码 `drawBackdrop` 的 innerShadow 默认就是 null —— 它根本不画。
        // 而 InnerShadow 做的事是「贴着形状边缘往里糊一圈暗色」，radius 18dp 意味着
        // 从边缘往里 18dp 都是暗的，在浅色界面上就是一圈灰黑框。
        // 之前我从 #2A3A55@0.55 压到 #9AA6B4@0.14，那只是把黑框调淡，本质还是在画一个
        // 源码没有的东西。现在直接去掉。
        shadow = {
            Shadow(
                radius = 26.dp,
                color = Color(0xFF4A5666),
                alpha = (glass.shadow.coerceIn(0f, 1f) * 0.6f),
            )
        },
        onDrawSurface = {
            // 光散射：先铺一层极淡的白雾，再叠表面色。
            // 顺序不能反 —— 雾是「介质里的散射光」，应该在颜色之下、背景之上。
            val haze = theme.glass.haze
            if (haze > 0f) drawRect(Color.White.copy(alpha = haze))
            if (alpha > 0f) drawRect(color.copy(alpha = alpha))
        },
    )
}

/**
 * **拟态玻璃**：给「独立窗口」用的玻璃表面。
 *
 * 弹窗（`Dialog`）和下拉菜单（`Popup`）都跑在**另一个 Window** 里，
 * `drawBackdrop` 取不到主窗口录制的底片（坐标体系都不一样），
 * 所以真模糊在这里是做不出来的。
 *
 * 那就把玻璃最要紧的两件事手工画出来：
 *   ① **0.5dp 的方向性镜面边**（上亮下暗）—— 玻璃之所以像玻璃，靠的就是这条边；
 *   ② 轻微的投影，让它从底下的内容上浮起来。
 *
 * 填充用半透明白，叠在弹窗的 scrim 上会透出一点底下的颜色，接近磨砂玻璃的观感。
 * 这不是真玻璃，但比一块 `#FBFBFB` 实色板要像得多。
 */
fun Modifier.pclFauxGlass(
    theme: PclUiTheme?,
    shape: Shape,
    fillColor: Color = Color.White,
    fillAlpha: Float = 0.86f,
    shadowRadius: Dp = 16.dp,
    rimAlpha: Float = 0.95f,
): Modifier {
    // 没装皮肤：保持 PCL 原本的实色
    if (theme == null) return this.background(fillColor.copy(alpha = 1f), shape)
    if (theme.renderer != PclUiRenderer.Glass) {
        return this.background(fillColor.copy(alpha = theme.cardAlpha), shape)
    }

    // 镜面边：上亮下暗，这就是 HighlightStyle.Default(45°) 的手工版。
    // 用 `Modifier.border(width, brush, shape)` 而不是自己 drawOutline ——
    // 它对任意 Shape 都成立，Capsule / RoundedCornerShape 都吃。
    val rim = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = rimAlpha),
            Color.White.copy(alpha = rimAlpha * 0.35f),
            Color.White.copy(alpha = rimAlpha * 0.06f),
        ),
    )

    return this
        .shadow(elevation = shadowRadius, shape = shape, clip = false)
        .background(fillColor.copy(alpha = fillAlpha), shape)
        .border(width = 0.5.dp, brush = rim, shape = shape)
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
    // 什么时候画边框：
    //   · 没装皮肤 → 照 PCL 原样画；
    //   · 这个角色**没有**用玻璃（比如勾选框、标题栏胶囊）→ 也照 PCL 原样画，
    //     否则「取消了边框 + 又没玻璃」，控件就只剩一块糊掉的底色，看着像坏了；
    //   · 用了玻璃 → 才听 `border.mode`。
    val drawBorder = theme == null ||
            theme.border.mode == PclBorderMode.Hairline ||
            !theme.glassEnabledFor(role)
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

/** 某个角色当前要不要玻璃（`roles` 里写了就听它的，否则听 `scope`）。 */
fun PclUiTheme?.glassEnabledFor(role: String): Boolean {
    val t = this ?: return false
    t.roles[role]?.glass?.let { return it }
    return t.glassAllControls
}

/** 某个角色的不透明度覆盖（没写就返回 null）。 */
fun PclUiTheme?.roleAlpha(role: String): Float? = this?.roles?.get(role)?.alpha

/** 皮肤覆盖的按钮圆角。 */
fun PclUiTheme?.buttonCornerOr(default: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    this?.buttonCorner?.dp ?: default
