/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 自定义控件在 Compose 中的对应实现。
 * 每个控件的尺寸、圆角、边框宽度、配色状态（普通 / 指向 / 按下 / 选中 / 禁用）
 * 都严格按照 PCL2 的 XAML + 代码还原，未做主观发挥：
 *   MyCard / MyButton / MyTextButton / MyIconButton / MyIconTextButton
 *   MyRadioButton / MyListItem / MyHint / MyCheckBox / MySlider
 *   MyTextBox / MySearchBox / MyLoading / MyExtraButton / MyMsg*
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCirc
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclEase
import com.zalith.pcl.ui.pcl2.theme.PclMotion
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.ui.pcl2.theme.pclHairline
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ============================================================================
 *  交互状态小工具
 * ==========================================================================*/

@Composable
private fun rememberInteraction(): Pair<MutableInteractionSource, Boolean> {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    return source to (hovered || pressed)
}

/**
 * 颜色过渡用的 `AnimationSpec` 缓存。
 *
 * `tween(ms)` 每次调用都会新建一个 spec 对象，而 [animateTo] 是**逐项、逐属性**调用的
 * （一个列表项就有「前景 / 底色 / 边框」三条），列表长一点时每次重组都要新造几百个
 * 短命对象，白白给 GC 添压力。spec 本身不可变，按毫秒数复用即可。
 * 只有主线程访问，无需加锁。
 */
private val pclColorSpecs = HashMap<Int, TweenSpec<Color>>(8)

private fun pclColorSpec(durationMillis: Int): TweenSpec<Color> =
    pclColorSpecs.getOrPut(durationMillis) { tween(durationMillis) }

@Composable
private fun animateTo(target: Color, durationMillis: Int = 100): Color =
    animateColorAsState(targetValue = target, animationSpec = pclColorSpec(durationMillis), label = "pclColor").value

/* ============================================================================
 *  PCL 原式动效（缓动全部走 `PclEase`，即 `ModAnimation.vb` 的 `AniEase*` 公式）
 * ==========================================================================*/

/**
 * `MyButton` 的按下缩放（`MyButton.xaml.vb` `Button_MouseDown` / `Button_MouseUp`）。
 *
 * 源码：
 * ```
 * MouseDown: AaScaleTransform(PanFore, 0.955 - cur, 80,  , AniEaseOutFluent(ExtraStrong))
 *            AaScaleTransform(PanFore, -0.01,         700, , AniEaseOutFluent(Middle))
 * MouseUp:   AaScaleTransform(PanFore, 1 - cur,      300, 10, AniEaseOutFluent(Middle))
 * ```
 * 即：按下先 80ms 急缩到 0.955，再 700ms 缓缓再缩 0.01（到 0.945）；抬起 300ms（延迟 10）回 1。
 *
 * 缩放走 `graphicsLayer`，只影响绘制、不触发布局，每帧只做一次 draw 变换，不会掉帧。
 */
@Composable
fun Modifier.pclPressScale(pressed: Boolean): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(0.955f, tween(80, easing = PclEase.outFluentExtraStrong))
            scale.animateTo(0.945f, tween(700, easing = PclEase.outFluent))
        } else {
            scale.animateTo(1f, tween(300, delayMillis = 10, easing = PclEase.outFluent))
        }
    }
    return this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * `MyIconButton` 的指向缩放（`MyIconButton.xaml.vb` `Button_MouseEnter/MouseLeave`）。
 *
 * 源码指向时叠两条 AaScaleTransform（`1.05` 用 `EaseOutBack(Weak)` 弹起，`-0.05` 用
 * `EaseOutFluent(Strong)` 拉回，各 250ms），净效果是**弹一下再落回 1**；
 * 离开 250ms（`EaseOutFluent`）归 1。
 */
@Composable
fun Modifier.pclHoverBounce(hovered: Boolean, amount: Float = 0.05f): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(hovered) {
        if (hovered) {
            scale.animateTo(1f + amount, tween(250, easing = PclEase.outBackWeak))
            scale.animateTo(1f, tween(250, easing = PclEase.outFluentStrong))
        } else {
            scale.animateTo(1f, tween(250, easing = PclEase.outFluent))
        }
    }
    return this.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * `MyCheckBox` / `MyRadioBox` 的勾选回弹（`AnimationTimeOfCheck = 150`）。
 *
 * 勾选（`MyCheckBox.xaml.vb` `RefreshCheck`）：
 * ```
 * AaScale(ShapeBorder, 12 - W,          150, ,           AniEaseOutFluent)        ' 边框先缩
 * AaScaleTransform(ShapeCheck, 1 - cur, 300, 105,       AniEaseOutBack(Weak))    ' 勾回弹放大
 * AaScale(ShapeBorder, 6,               300, 105,       AniEaseOutBack)          ' 边框再放大
 * ```
 * 取消勾选：
 * ```
 * AaScale(ShapeBorder, 12 - W,         150, ,           AniEaseOutFluent)
 * AaScaleTransform(ShapeCheck, -cur,   135, ,           AniEaseInFluent(Weak))   ' 勾缩到 0
 * AaScale(ShapeBorder, 6,              300, 105,        AniEaseOutBack)
 * ```
 * 这里给出勾标记（`ShapeCheck`）的缩放值 —— 返回值即 `0f…1f` 的勾缩放。
 */
@Composable
fun rememberPclCheckScale(checked: Boolean): State<Float> {
    val scale = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            // `AaScaleTransform(ShapeCheck, 1 - cur, 300, 105, AniEaseOutBack(Weak))`
            // —— 这个 delay 105 是源码里就有的（`AnimationTimeOfCheck * 0.7`），
            //    设计意图是「先让方框缩一下，勾再弹出来」，不是 bug，别删。
            scale.snapTo(0f)
            delay(105)
            scale.animateTo(1f, tween(300, easing = PclEase.outBackWeak))
        } else {
            scale.animateTo(0f, tween(135, easing = PclEase.inFluentWeak))
        }
    }
    return scale.asState()
}

/**
 * 圆点（`MyRadioBox.ShapeDot`）的勾选动画。
 *
 * 源码 `MyRadioBox.xaml.vb:SyncUI`：
 * ```
 * 勾选：If ShapeDot.Opacity < 0.01 Then ShapeDot.Opacity = 1              ' 透明度【直接置 1】，不做动画
 *       AaScale(ShapeDot, 9 - W, 390ms, , AniEaseOutBack(Weak), Absolute) ' 缩放【没有 delay】，点下去立刻开始长
 *       AaOpacity(ShapeDot, 1 - cur, 75ms, 90)                           ' 此时 cur 已是 1，增量 0，这行不会执行
 * 取消：AaScale(ShapeDot, -W, 150ms, , AniEaseInFluent, Absolute)
 *       AaOpacity(ShapeDot, -cur, 75ms, 30)
 * ```
 *
 * ⚠️ 圆点的**缩放没有延迟**。之前误把 `AaOpacity` 的 90ms 延迟也套到了缩放上，
 * 于是点下去之后前 90ms 毫无反应 —— 就是「显示总会慢半拍」的原因。
 * 正确做法：透明度直接置 1，缩放在同一帧就用 `EaseOutBack(Weak)` 开始长（前 30% 就到 92%）。
 *
 * 返回 `State` 而非裸值，让调用方在 `graphicsLayer` 的绘制阶段延迟读取，动画期间不重组。
 */
@Composable
fun rememberPclRadioDotScale(checked: Boolean): Pair<State<Float>, State<Float>> {
    val scale = remember { Animatable(if (checked) 1f else 0f) }
    val alpha = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        if (checked) {
            alpha.snapTo(1f) // 源码：Opacity 直接置 1
            scale.snapTo(0f)
            scale.animateTo(1f, tween(390, easing = PclEase.outBackWeak))
        } else {
            scale.animateTo(0f, tween(150, easing = PclEase.inFluentWeak))
            delay(30)
            // `AaOpacity` 未指定 Ease，默认就是 `AniEaseLinear`
            alpha.animateTo(0f, tween(75, easing = PclEase.linear))
        }
    }
    return scale.asState() to alpha.asState()
}

/* ============================================================================
 *  PCL 按钮
 * ==========================================================================*/

/** `MyButton.ColorState` */
enum class PclColorState { Normal, Highlight, Red }

/**
 * `MyButton` —— PCL 的实心按钮。
 *
 * 尺寸：默认高 35，圆角 3，边框 1。
 * 配色：Normal→ColorBrush1，Highlight→ColorBrush2，Red→ColorBrushRedDark，禁用→Gray4；
 *       指向时统一变为 ColorBrush3 / RedLight；底色 ColorBrushHalfWhite，指向变 ColorBrush7。
 *       文字颜色始终与边框一致（PCL 里 Foreground 绑定到 BorderBrush）。
 */
@Composable
fun PclButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colorState: PclColorState = PclColorState.Normal,
    height: Dp = PclDimens.buttonHeight,
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp),
    logo: ImageVector? = null,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed

    // 边框色（= PCL 的 `Foreground`，它绑定到 BorderBrush）：
    //   In=100ms / Out=200ms（MyButton.AnimationColorIn / Out）
    val borderColor = animateTo(
        target = when {
            !enabled -> p.gray4
            active -> if (colorState == PclColorState.Red) p.redLight else p.color3
            colorState == PclColorState.Highlight -> p.color2
            colorState == PclColorState.Red -> p.redDark
            else -> p.color1
        },
        durationMillis = if (active) 100 else 200,
    )
    val backgroundColor = animateTo(
        when {
            !enabled -> p.halfWhite
            active && colorState == PclColorState.Red -> p.redBack
            active -> p.color7
            else -> p.halfWhite
        }
    )

    Row(
        // `MyButton` 的按下缩放套在最外层（对应 PCL 的 PanFore RenderTransform）
        modifier = modifier
            .pclPressScale(pressed)
            .heightIn(min = height)
            .clip(RoundedCornerShape(PclDimens.buttonCorner))
            .background(backgroundColor)
            .border(pclHairline(), borderColor, RoundedCornerShape(PclDimens.buttonCorner))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (logo != null) {
            Icon(logo, contentDescription = null, tint = borderColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = PclTextStyle.button(p).copy(color = borderColor),
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * `MyTextButton` —— 无边框的纯文本按钮。
 */
@Composable
fun PclTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val (interaction, active) = rememberInteraction()
    Text(
        text = text,
        style = PclTextStyle.base(p).copy(color = if (!enabled) p.gray4 else (color ?: p.color3)),
        modifier = modifier
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .graphicsLayer(alpha = if (active) 0.65f else 1f),
    )
}

/** `MyIconButton.Themes` */
enum class PclIconTheme { Color, White, Black, Red, Custom }

/**
 * 按 WPF `Stretch="Uniform"` 的语义把矢量图标安放进 [boxWidth] × [boxHeight] 的盒子。
 *
 * WPF 的 `Shapes.Path` 用的是 `Stretch=Uniform`：几何按**自身包围盒**等比缩放，
 * 缩放到刚好塞进可用区域为止（较长的那条边贴边，较短的那条边留白），
 * 绝对不会做非等比拉伸。`PclIcons` 的视口取的正是路径的紧密包围盒，
 * 因此这里直接用视口宽高比反推绘制尺寸即可。
 *
 * Compose 的 `Icon` 则是把矢量按**各轴独立缩放**铺满给定尺寸的 —— 对非正方形
 * 图标（例如齿轮）会横竖拉伸得不一样。凡是需要跟 PCL 逐像素对齐的地方都必须走这个函数。
 */
@Composable
fun PclUniformIcon(
    imageVector: ImageVector,
    tint: Color,
    boxWidth: Dp,
    boxHeight: Dp,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    contentDescription: String? = null,
) {
    val vw = imageVector.defaultWidth.value
    val vh = imageVector.defaultHeight.value
    if (vw <= 0f || vh <= 0f) return
    val fit = minOf(boxWidth.value / vw, boxHeight.value / vh)
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size((vw * fit).dp, (vh * fit).dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
    )
}

/**
 * `MyIconButton` —— 只有图标的按钮。
 *
 * 配色取自 `MyIconButton.RefreshAnim`：
 *   Color：普通 ColorBrush4，指向 ColorBrush2，禁用 Gray5
 *   White：普通 ColorBrush8，指向时叠一层白色 50/255 的底
 *   Black：普通 rgba(0,0,0,160)，指向 rgba(0,0,0,230)
 *   Red  ：普通 rgba(255,76,76,160)，指向 rgb(255,76,76)
 *   Custom：自定义色 160/255，指向不透明
 *
 * 几何取自 `MyIconButton.xaml`：外层 `Border CornerRadius="1000" Background=SemiTransparent`
 * 里再套一层 `Border CornerRadius="1000"`（`PanBack`，指向时才上白色底），
 * `Path Margin="5" Stretch="Uniform"` —— 所以**图形区恒等于 Height − 10**。
 * `MyListItem` 里批量塞进来的按钮都是 `Height=Width=25`（未显式指定时由
 * `MyListItem.xaml.vb:120-140` 补齐），于是图形区 = 15，再乘各自的 `LogoScale`。
 *
 * ⚠️ 那两个 `CornerRadius="1000"` 只用于**指向时的圆形底色**。WPF 的 `Border`
 * 从不裁剪子元素（`ClipToBounds` 默认 False），所以图标绝不会被圆裁；
 * 这里也必须把 `clip(CircleShape)` 单独加在底色层上，不能套住图标本身。
 */
@Composable
fun PclIconButton(
    logo: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    theme: PclIconTheme = PclIconTheme.Color,
    customColor: Color = Color(0xFF808080),
    size: Dp = 25.dp,
    logoScale: Float = 1f,
    /**
     * 图形本身的绘制边长，即 `MyIconButton` 的 `Path` 可用区域，默认 `size − 10`
     * （`Margin="5"` 四边各 5）。触控区仍由 [size] 单独决定。
     * 个别地方 PCL 用的是别的控件（如 `MySearchBox` 里 Height=24 的关闭按钮），
     * 那些位置在调用处显式传值即可。
     */
    logoSize: Dp = (size - 10.dp).coerceAtLeast(4.dp),
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed

    val tint = animateTo(
        target = when {
            !enabled -> p.gray5
            theme == PclIconTheme.Color -> if (active) p.color2 else p.color4
            theme == PclIconTheme.White -> p.color8
            theme == PclIconTheme.Black -> if (active) Color(0xE6000000) else Color(0xA0000000)
            theme == PclIconTheme.Red -> if (active) Color(0xFFFF4C4C) else Color(0xA0FF4C4C)
            else -> if (active) customColor.copy(alpha = 1f) else customColor.copy(alpha = 160f / 255f)
        },
        // MyIconButton.AnimationColorIn=120 / Out=150
        durationMillis = if (active) 120 else 150,
    )
    val background = if (theme == PclIconTheme.White && active) Color(0x32FFFFFF) else Color.Transparent

    Box(
        modifier = modifier
            .size(size)
            // MyIconButton 的悬停弹跳（250ms EaseOutBack(Weak) 弹起 → EaseOutFluent(Strong) 落回）
            .pclHoverBounce(hovered)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 圆形底色单独一层：只有它需要圆裁，图标本身保持方框（同 WPF Border 的行为）。
        if (background != Color.Transparent) {
            Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(background))
        }
        PclUniformIcon(
            imageVector = logo,
            tint = tint,
            boxWidth = logoSize,
            boxHeight = logoSize,
            scale = logoScale,
            contentDescription = contentDescription,
        )
    }
}

/**
 * `MyRadioButton` —— 标题栏上的胶囊标签（图标 + 文字）。
 *
 * 几何直接来自 `FormMain.xaml` 的 `BtnTitleSelect0..4` + `MyRadioButton.xaml`：
 *   Border：`CornerRadius="13.5"`、`MinHeight/MaxHeight="27"`；
 *   StackPanel 内：`ShapeLogo Margin="12,0,0,0"`、`MaxWidth/MaxHeight="16"`、`Stretch=Uniform`；
 *                 `LabText Margin="8,0,12,0"`。
 *
 * 配色来自 `MyRadioButton.xaml.vb:RefreshColor`：
 *   ColorType=White（标题栏）：正常→白色字/透明底；指向→白字 + `rgba(234,242,254,50)` 底；
 *                            选中→白底 + ColorBrush3 字。
 *   ColorType=Highlight（登录方式）：正常→ColorBrush3 字；指向→ColorBrush3 字 + ColorBrush7 底；
 *                            按下→ColorBrush6 底；选中→ColorBrush3 底 + 白字。
 */
@Composable
fun PclPillButton(
    text: String,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    logo: ImageVector? = null,
    logoScale: Float = 1f,
    highlight: Boolean = false,
    enabled: Boolean = true,
    fontSize: Int = 13,
    /**
     * 无图标时 `LabText.Margin` 的右边距（左边距固定 -2，见 [PCL_PILL_TEXT_SHIFT_NO_LOGO]）。
     *
     * 两个页面写的不一样：Mod 管理页是 8（`PageInstanceMod.xaml.vb:21-24`），
     * 资源详情页是 10（`PageDownloadCompDetail.xaml.vb:126`）。
     */
    textEnd: Dp = PCL_PILL_TEXT_END_NO_LOGO,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val (interaction, active) = rememberInteraction()

    val background = animateTo(
        when {
            !enabled -> Color.Transparent
            highlight -> when {
                checked -> p.color3
                active -> p.color7
                else -> p.semiTransparent
            }
            else -> when {
                checked -> p.white
                active -> Color(0x32EAF2FE) // New MyColor(50, Color8)
                else -> p.semiTransparent
            }
        }
    )
    val contentColor = animateTo(
        when {
            !enabled -> p.gray4
            highlight -> if (checked) p.white else p.color3
            else -> if (checked) p.color3 else p.white
        }
    )

    val hasLogo = logo != null
    Row(
        modifier = modifier
            // PCL 的 `MinHeight/MaxHeight="27"`：这里用 heightIn 表达最小高，
            // 万一字号/行高比 27 大，胶囊就自己长高，而不是把文字下半截裁掉。
            .heightIn(min = PclDimens.navPillHeight)
            .clip(RoundedCornerShape(PclDimens.navPillCorner))
            .background(background)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            // 有图标时按 XAML 的 `ShapeLogo Margin="12,0,0,0"` + `LabText Margin="8,0,12,0"`；
            // 没有图标时（「全部 / 已启用 / 已禁用」这类过滤胶囊）走的是
            // `PageInstanceMod.xaml.vb:21-24` / `PageDownloadCompDetail.xaml.vb:126` 那句
            // 「调整按钮边距」：`LabText.Margin = (-2,0,8|10,0)`。
            // 注意 PCL 的 `ShapeLogo` 即使没有 Logo 数据也仍然占着它自己的 `Margin="12,0,0,0"`，
            // 所以文字的左边距 = 12 − 2 = 10（见 PCL_PILL_TEXT_START_NO_LOGO）。
            .padding(
                start = if (hasLogo) PclDimens.navPillLogoStart else PCL_PILL_TEXT_START_NO_LOGO,
                end = if (hasLogo) PclDimens.navPillTextEnd else textEnd,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (logo != null) {
            Icon(
                imageVector = logo,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(PclDimens.navPillLogoSize)
                    .graphicsLayer(scaleX = logoScale, scaleY = logoScale),
            )
            Spacer(Modifier.width(PclDimens.navPillTextStart))
        }
        Text(
            text = text,
            style = PclTextStyle.base(p).copy(
                color = contentColor,
                fontSize = fontSize.sp,
                lineHeight = PclTextStyle.lineHeight(fontSize),
            ),
            // 与 PclText 一致：字形比行高超一点点时也不裁
            overflow = TextOverflow.Visible,
        )
    }
}

/**
 * 无图标胶囊里文字的左边距。
 *
 * `MyRadioButton.xaml` 的 `StackPanel` 里，`ShapeLogo` 排在 `LabText` 前面，
 * 且**永远带着 `Margin="12,0,0,0"`** —— 没有 Logo 数据时它的宽高是 0，
 * 但这 12 的左边距照样占位。再加上过滤胶囊把 `LabText.Margin` 设成
 * `(-2,0,8,0)` / `(-2,0,10,0)`，文字实际离胶囊左边缘 = 12 − 2 = **10**，
 * 右边距则是 8 或 10 —— 左右基本相等，看上去才是居中的。
 *
 * 踩过的坑：早先把左边距写成 0、再用 `Modifier.offset(-2.dp)` 位移模拟那个负 margin，
 * 结果文字贴着胶囊左边缘、右边空一大截，就是「全部」明显向左偏移的原因。
 */
private val PCL_PILL_TEXT_START_NO_LOGO = 10.dp

/**
 * 过滤胶囊（无图标）的文字右边距默认值：`PageInstanceMod.xaml.vb:21-24`
 * 把 `LabText.Margin` 改成了 `(-2,0,8,0)`。
 * 资源详情页是另一套：`PageDownloadCompDetail.xaml.vb:126` 写的是 `(-2,0,10,0)`，
 * 调用处通过 [PclPillButton] 的 `textEnd` 参数覆盖。
 */
private val PCL_PILL_TEXT_END_NO_LOGO = 8.dp

/**
 * `MyIconTextButton`（`Height="35"` 的用法）—— 带图标的按钮，常用于「安装」「下载」。
 */
@Composable
fun PclIconTextButton(
    text: String,
    logo: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = PclDimens.buttonHeight,
    highlight: Boolean = true,
    leadingPadding: Dp = 0.dp,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val (interaction, active) = rememberInteraction()

    val borderColor = animateTo(
        when {
            !enabled -> p.gray4
            active -> p.color3
            highlight -> p.color2
            else -> p.color1
        }
    )
    val background = animateTo(
        when {
            !enabled -> p.halfWhite
            active -> p.color7
            else -> p.halfWhite
        }
    )

    Row(
        modifier = modifier
            .heightIn(min = height)
            .clip(RoundedCornerShape(PclDimens.buttonCorner))
            .background(background)
            .border(pclHairline(), borderColor, RoundedCornerShape(PclDimens.buttonCorner))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(logo, contentDescription = null, tint = borderColor, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp + leadingPadding))
        Text(
            text = text,
            style = PclTextStyle.button(p).copy(color = borderColor),
            overflow = TextOverflow.Visible,
        )
    }
}

/* ============================================================================
 *  PCL 卡片
 * ==========================================================================*/

/**
 * `MyCard` —— PCL 的白色卡片。
 *
 * 底色 `#F5FFFFFF`，圆角 5，阴影 ColorObject1 @ 0.07 → 指向时 ColorObject4 @ 0.4。
 * 标题 13 号加粗、位置（15, 12）；折叠态高度 40；箭头 10×6，展开时旋转。
 */
@Composable
fun PclCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    collapsible: Boolean = false,
    initiallyCollapsed: Boolean = false,
    swapLogoRight: Boolean = false,
    hasMouseAnimation: Boolean = true,
    contentPadding: PaddingValues? = null,
    /**
     * 折叠状态提升。给出时由外部控制（PCL 里选完加载器版本会自动把卡片折起来，
     * 见 `PageDownloadInstall.xaml.vb:780` 的 `CardForge.IsSwapped = True`）。
     */
    collapsedState: MutableState<Boolean>? = null,
    /**
     * 折叠状态变化后的回调，参数是**变化之后**的「是否展开」。
     *
     * 用于卡片里塞了大列表的场景：展开的那一瞬间就得把列表行数收回首批
     * （见 [rememberPclRowChunks]），否则「关掉再立刻点开」会把上一次没补完的行数
     * 带进新的展开动画里，第一帧又变成重帧。
     */
    onToggle: ((Boolean) -> Unit)? = null,
    /**
     * 只在**折叠**时显示、贴在标题行上的附加信息。
     *
     * 对应 PCL 的 `PanForgeInfo` / `PanNeoForgeInfo` 等：`Grid Height="18"
     * Margin="132,11,15,0" VerticalAlignment="Top"`，里面是方块贴图 + 一行说明
     * （「可以添加」/ 已选版本号）。`SetPanelVisibility(Panel, Card.IsSwapped)`：
     * 显示时从 Y+6 上移到 0 并淡入 150/60ms，隐藏时反向。
     *
     * 传入的内容**不需要**自己 align，它已经被摆到左上角，自行加 padding/尺寸即可。
     */
    collapsedContent: (@Composable () -> Unit)? = null,
    /** 常驻在标题行右上角的内容（PCL 的 `BtnForgeClear` 清除按钮）。 */
    titleAccessory: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = pclPalette
    val slotShift = with(LocalDensity.current) { 6.dp.roundToPx() }
    val (interaction, active) = rememberInteraction()
    var innerCollapsed by remember { mutableStateOf(initiallyCollapsed) }
    val collapsed = collapsedState?.value ?: innerCollapsed
    val toggle = {
        // 先把状态写出去再回调：回调里会读同一个 `collapsedState`，
        // 反过来的话它读到的还是旧值。
        val next = !collapsed
        if (collapsedState != null) collapsedState.value = next else innerCollapsed = next
        onToggle?.invoke(next)
    }

    val shadowColor = animateTo(if (active && hasMouseAnimation) p.color4 else p.color1, 90)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (active && hasMouseAnimation) 0.40f else 0.07f,
        animationSpec = tween(90),
        label = "pclCardShadow",
    )
    val padding = contentPadding ?: if (title != null) {
        // PCL 各页最常用的卡片内容边距：Margin="25,40,25,15"（见 PclDimens 的统计注释）
        PaddingValues(
            start = PclDimens.cardContentStart,
            top = PclDimens.cardContentTop,
            end = PclDimens.cardContentEnd,
            bottom = PclDimens.cardContentBottom,
        )
    } else {
        // 无标题卡片（各页面的小卡片）：Margin="21,16,21,16"
        PaddingValues(
            start = PclDimens.cardContentStartTight,
            top = PclDimens.cardContentBottomTight,
            end = PclDimens.cardContentEndTight,
            bottom = PclDimens.cardContentBottomTight,
        )
    }

    Box(
        modifier = modifier
            // MyCard.vb:99 —— 折叠时 Height = SwapedHeight(40)，展开时 Height = NaN（自适应）。
            // 用 minHeight 表达：展开后内容本就比 40 高，不受影响。
            .then(
                if (title != null) Modifier.heightIn(min = PclDimens.cardCollapsedHeight) else Modifier,
            )
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(PclDimens.cardCorner),
                ambientColor = shadowColor.copy(alpha = shadowAlpha),
                spotColor = shadowColor.copy(alpha = shadowAlpha),
            )
            .clip(RoundedCornerShape(PclDimens.cardCorner))
            .background(p.cardBackground)
            .then(
                if (collapsible) {
                    Modifier.clickable(interactionSource = interaction, indication = null) { toggle() }
                } else {
                    Modifier
                }
            ),
    ) {
        // 内容自带 Margin（25,40,25,15），标题只是「浮」在它上方，不额外占一行——
        // 这一点对应 MyCard.vb 里 MainTextBlock 被 Margin="15,12,0,0" 叠在 MainGrid 上。
        //
        // 折叠时的展开/收起：PCL `MyCard.vb:224` 的
        // `AaHeight(Me, Delta, 150, , AniEaseOutFluent(ExtraStrong))` 在这里被有意改过参数
        // （时长、缓动都换了，理由见 [PclCardHeightDuration] / [PclCardHeightEasing]）；
        // 箭头仍是 `MyCard.vb:304` 的 `AaRotateTransform(MainSwap, ..., 250, ...)`，
        // 现在高度与箭头同为 250ms，两者同步。
        //
        // 多加的一层 `fadeIn`：PCL 原版只有高度动画，内容是被「揭开」的，
        // 在位移被缓动挤到开头之后完全看不出发生过程。配一层整体淡入后，
        // 观感是「先显影、再长满」，展开这件事才立得住。
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(
                animationSpec = tween(PclCardHeightDuration, easing = PclCardHeightEasing),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = tween(PclCardFadeDuration)),
            exit = shrinkVertically(
                animationSpec = tween(PclCardHeightDuration, easing = PclCardHeightEasing),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = tween(PclCardFadeDuration)),
        ) {
            Column(modifier = Modifier.padding(padding), content = content)
        }

        if (title != null) {
            Text(
                text = title,
                // 悬停时前景 ColorBrush1 → ColorBrush2，90ms（MyCard.vb:181 / :192）
                style = PclTextStyle.cardTitle(p).copy(
                    color = animateTo(if (active && hasMouseAnimation) p.color2 else p.color1, 90),
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = PclDimens.cardTitleX, top = PclDimens.cardTitleY),
            )
        }
        // 折叠态才显示的标题行信息（PCL 的 PanXxxInfo：从 Y+6 上移到 0 / 淡入 150、60ms）
        if (collapsedContent != null) {
            AnimatedVisibility(
                visible = collapsed,
                enter = fadeIn(tween(60)) +
                    slideInVertically(tween(150, easing = PclEase.outFluentWeak)) { slotShift },
                exit = fadeOut(tween(60)) + slideOutVertically(tween(60)) { slotShift },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                collapsedContent()
            }
        }
        // 常驻右上角（PCL 的 BtnXxxClear）
        if (titleAccessory != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) { titleAccessory() }
        }
        if (collapsible) {
            val target = if (collapsed) (if (swapLogoRight) 270f else 0f) else 180f
            // 同样在绘制阶段读取，折叠/展开期间不重组
            val rotationState = animateFloatAsState(
                target,
                tween(PclMotion.CARD_SWAP_DURATION, easing = PclEase.outFluentExtraStrong),
                label = "pclCardArrow",
            )
            Icon(
                imageVector = PclIcons.cardSwap,
                contentDescription = null,
                tint = animateTo(if (active && hasMouseAnimation) p.color2 else p.color1, 90),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 17.dp, end = 16.dp)
                    .size(width = 10.dp, height = 6.dp)
                    .graphicsLayer { rotationZ = rotationState.value },
            )
        }
    }
}

/**
 * 卡片展开/收起的高度时长。
 *
 * PCL 原版是 `MyCard.vb:224` 的 `AaHeight(Me, Delta, 150, , AniEaseOutFluent(ExtraStrong))`，
 * 但那个组合在手机上**看不出展开过程**：ExtraStrong 是 `1-(1-t)^5`，前 20% 的时间就走完
 * 67% 的位移、前 60ms 走完 92%，而版本分组卡片一展开就是 800 多 dp 的位移
 * —— 全部挤在开头一瞬，观感就是「没有动画」（用户第 93 轮的原话）。
 *
 * 所以这里有意偏离原版：时长取箭头旋转的 [PclMotion.CARD_SWAP_DURATION]（250ms），
 * 让高度和箭头同步收尾；缓动降到 Middle 档（见 [PclCardHeightEasing]），
 * 使中段也留得住运动。
 */
private const val PclCardHeightDuration = 250

/**
 * 展开/收起时内容整体的淡入淡出时长。
 *
 * 比高度动画早 70ms 收尾：内容先「显影」再「长满」，两层错开一点比同时结束更耐看。
 */
private const val PclCardFadeDuration = 180

/**
 * 展开/收起高度动画的缓动。
 *
 * 原版是 `AniEaseOutFluent(ExtraStrong)`，位移几乎全挤在开头；
 * 降到 Middle 档（`1-(1-t)^3`）后，250ms 里的运动才铺得开。
 */
private val PclCardHeightEasing = PclEase.outFluent

/* ============================================================================
 *  折叠卡片里的大列表：分批组合
 * ==========================================================================*/

/**
 * 首帧先组合多少行。
 *
 * 20 行 × 42dp = 840dp，比任何手机上「卡片能露出来的高度」都高，
 * 所以第一批就够铺满屏幕；再多的行第一帧也看不见，组合了纯属浪费。
 */
private const val PclChunkFirstRows = 20

/** 之后每帧补多少行。 */
private const val PclChunkStepRows = 20

/**
 * 展开动画走完之后再开始补行：[PclCardHeightDuration]（250）+ 60ms 余量。
 *
 * **不能**在动画途中补。`expandVertically` 的显示高度是按**当前测量到的内容高度**
 * 逐帧算的，动画途中内容突然长高，卡片高度会在半路跳一下。
 */
private const val PclChunkSettleMillis = 310L

/**
 * 折叠卡片里的大列表「分批组合」状态，由 [rememberPclRowChunks] 创建。
 *
 * 用法：
 * ```
 * val collapsed = remember { mutableStateOf(true) }
 * val chunks = rememberPclRowChunks(list.size, collapsed)
 * PclCard(..., collapsedState = collapsed, onToggle = chunks.onToggle) {
 *     for (i in 0 until chunks.shown) { Row(list[i]) }
 * }
 * ```
 */
@Stable
class PclRowChunks internal constructor(
    internal val total: Int,
    private val firstChunk: Int,
    private val step: Int,
) {
    /** 已经组合到第几行。在卡片内容里读它，出现它就会补行。 */
    var shown by mutableStateOf(minOf(total, firstChunk))
        private set

    internal fun reset() { shown = minOf(total, firstChunk) }

    internal fun advance() { shown = minOf(total, shown + step) }

    /** 接到 `PclCard(onToggle = ...)`：展开的瞬间就把行数收回首批。 */
    val onToggle: (Boolean) -> Unit = { expanded -> if (expanded) reset() }
}

/**
 * 让 `PclCard` 里的大列表分批组合 —— 解决「点开卡半天才开、开了又看不到动画」。
 *
 * 根因：`AnimatedVisibility` 展开时会把**整个列表一次性组合**。800 多行的一帧要
 * 200ms 上下，而高度动画只有 150ms，于是动画的每一帧都被这一帧吞掉 ——
 * 表现出来就是「慢半拍，而且没有动画」，连续点开/关时每一下都在付这个成本。
 *
 * 做法：首帧只组合够铺满一屏的行（[PclChunkFirstRows]），动画因此能完整跑完；
 * 动画结束（[PclChunkSettleMillis]）后逐帧补 [PclChunkStepRows] 行，
 * 之后新增的行都在屏幕之外，看起来就是列表自己往下「填」出来。
 *
 * 补完的总成本没变，只是被摊到了几十帧里，不再压在动画那一帧上。
 *
 * 行数很少（≤ 首批）的卡片完全不受影响：一次组合完，没有任何多余的重组。
 */
@Composable
fun rememberPclRowChunks(
    total: Int,
    collapsed: State<Boolean>,
    firstChunk: Int = PclChunkFirstRows,
    step: Int = PclChunkStepRows,
    settleMillis: Long = PclChunkSettleMillis,
): PclRowChunks {
    // 列表换了一批（重新拉取 / 搜索过滤）就重新从首批开始
    val chunks = remember(total, firstChunk, step) { PclRowChunks(total, firstChunk, step) }
    LaunchedEffect(chunks, collapsed.value) {
        if (collapsed.value) return@LaunchedEffect
        delay(settleMillis)
        while (chunks.shown < chunks.total) {
            // 一帧补一批：不占满当前帧的话，下一批会和这一批挤在同一帧里
            withFrameNanos { }
            chunks.advance()
        }
    }
    return chunks
}

/* ============================================================================
 *  PCL 提示条
 * ==========================================================================*/

/** `MyHint.Themes`（页内提示条只有这三种；`Green` 供左下角提示条使用） */
enum class PclHintTheme { Blue, Red, Yellow, Green }

/**
 * `MyHint` —— 左侧 3dp 色条 + 圆角 2 的提示条。
 *
 * 三套配色逐项取自 `MyHint.xaml.vb:UpdateUI`：
 *   Blue  ：底 #d9ecff，条 #1172D4，字 #0F64B8
 *   Red   ：底 #ffdddf，条 #d82929，字 #bf0b0b
 *   Yellow：底 #ffebd7，条 #f57a00，字 #d86c00
 * 文字 `Padding="12,9"`、`LineHeight="16"`；关闭按钮 20×20、右边距 8。
 */
@Composable
fun PclHint(
    text: String,
    modifier: Modifier = Modifier,
    theme: PclHintTheme = PclHintTheme.Red,
    onClose: (() -> Unit)? = null,
) {
    val background: Color
    val border: Color
    val textColor: Color
    when (theme) {
        PclHintTheme.Blue -> {
            background = Color(0xFFD9ECFF); border = Color(0xFF1172D4); textColor = Color(0xFF0F64B8)
        }
        PclHintTheme.Red -> {
            background = Color(0xFFFFDDDF); border = Color(0xFFD82929); textColor = Color(0xFFBF0B0B)
        }
        PclHintTheme.Yellow -> {
            background = Color(0xFFFFEBD7); border = Color(0xFFF57A00); textColor = Color(0xFFD86C00)
        }
        // MyHint 本身没有 Green 主题（PCL 的绿色只用在左下角提示条上），
        // 万一页内误用就退回蓝色，避免出现 PCL 里不存在的配色
        PclHintTheme.Green -> {
            background = Color(0xFFD9ECFF); border = Color(0xFF1172D4); textColor = Color(0xFF0F64B8)
        }
    }
    val p = pclPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .background(background)
            .drawBehind {
                drawRect(color = border, size = Size(3.dp.toPx(), size.height))
            }
            .padding(start = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = PclTextStyle.hint(p),
            color = textColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 9.dp, bottom = 9.dp),
        )
        if (onClose != null) {
            PclIconButton(
                logo = PclIcons.close,
                theme = PclIconTheme.Black,
                size = 20.dp,
                logoScale = 0.7f,
                modifier = Modifier.padding(end = 8.dp),
                onClick = onClose,
            )
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

/* ============================================================================
 *  PCL 列表项
 * ==========================================================================*/

/** `MyListItem.CheckType` */
enum class PclListItemType { None, Clickable, RadioBox, CheckBox }

/** 列表项右侧的小图标按钮（对应 `MyListItem.Buttons`）。 */
/**
 * 列表项右侧的浮层按钮（`MyListItem` 的 `ButtonStack` 里的一项）。
 *
 * 标 `@Immutable`：[onClick] 是 lambda，Compose 默认会把含函数类型的类判成不稳定，
 * 而这个类是「构造好就不再改」的纯数据，标注准确 —— 这样它所在的列表项才有机会被跳过重组。
 */
@Immutable
data class PclListItemButton(
    val logo: ImageVector,
    val tooltip: String? = null,
    /**
     * `MyIconButton.LogoScale`。PCL 的默认值就是 1（`MyIconButton.xaml.vb:21`），
     * 只有源码里显式写了 LogoScale 的地方才该是别的值
     * （版本列表三个按钮 1.1 / 1.15、左栏刷新 0.85、重置 0.9 …）。
     */
    val logoScale: Float = 1f,
    val onClick: () -> Unit,
)

/**
 * `MyListItem` —— PCL 的列表项。
 *
 * 列宽与 PCL 完全一致：勾选条 6（None/Clickable 时为 2，高 <40 时 4）、
 * 图标列 34（高 ≥40 时 38）、图标内边距 (8,8,6,8)（高 <40 时为 (6,8,4,8)）。
 *
 * 两种状态互不相同（务必按源码区分）：
 *   · **指向**（`RefreshColor`）：浮出 `RectBack` —— 底色 ColorBrush7 @ alpha 190、边框 1px ColorBrush6；
 *     圆角 `If(IsScaleAnimationEnabled OrElse Height > 40, 6, 0)`；只改背景，**不改前景**。
 *   · **选中**（`Loaded` / `SetChecked`）：只把前景改成 ColorBrush3（高 <40）或 ColorBrush2（高 ≥40），
 *     并显示左侧 5dp 主题色竖条（圆角 2、Margin(-1,6,0,6)）；**不画背景、不画边框**。
 */
@Composable
fun PclListItem(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    logo: ImageVector? = null,
    logoScale: Float = 1f,
    /**
     * 自定义图标内容（如版本自带的 `VersionIconImage`）。给出时优先于 [logo]，
     * 并按 PCL 的位图分支使用 `Margin="4,5,3,5"`（矢量是 6/8/4/8 或 8/8/6/8）。
     */
    logoContent: (@Composable BoxScope.() -> Unit)? = null,
    /** 标题后缀，以淡灰 `#D7D7D7` 12 号紧随标题（`McInstance.ToListItem` 的自定义描述）。 */
    titleSuffix: String? = null,
    type: PclListItemType = PclListItemType.None,
    checked: Boolean = false,
    height: Dp = 42.dp,
    fontSize: Int = PclDimens.listItemFontSize,
    infoColor: Color? = null,
    buttons: List<PclListItemButton> = emptyList(),
    /** `MyListItem.MinPaddingRight`，默认 4；左栏条目在 XAML 里显式写成 35。 */
    minPaddingRight: Dp = 4.dp,
    /**
     * 禁用态：内容整体灰化、点不中、行内按钮也一并禁用。
     *
     * `MyListItem` 自己没有禁用样式（PCL 从没把列表项整条禁用过），所以配色沿用
     * `MyCheckBox` / `MyIconButton` 的禁用约定 —— 即 `ColorBrushGray5`（`#CCCCCC`）那一族：
     * 标题降到 [PclPalette.gray2]、副标题与图标降到 `ColorBrushGray5`。
     */
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val p = pclPalette
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = enabled && (hovered || pressed)

    val compact = height < 40.dp

    // MyListItem.xaml.vb:590 / :604 —— 按下时整项缩到 0.98（`Time*0.9 = 108ms`，EaseOutFluent），
    // 松开时 `Time*3 = 540ms` 回到 1。
    //
    // 性能：动画值不给 `by` 直接读，而是在 `graphicsLayer` 的**绘制阶段**延迟读取，
    // 于是缩放期间列表项**不重组**、只重绘；用 `derivedStateOf` 把「是否挂着图层」
    // 压缩成一个布尔，只在「开始缩放 / 回到 1」两个时刻各重组一次。
    val itemScaleState = animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(if (pressed) 108 else 540, easing = PclEase.outFluent),
        label = "pclListItemScale",
    )
    val scaling by remember { derivedStateOf { itemScaleState.value != 1f } }

    // MyListItem.xaml.vb:640-645（Loaded）与 :453（SetChecked 动画）——
    // 前景色只由「选中」决定：选中时高 <40 用 ColorBrush3、高 ≥40 用 ColorBrush2，否则 ColorBrush1。
    // 关键：PCL 的选中**不画任何背景/边框**，只有左侧 5px 竖条 + 前景色变化。
    // （RefreshColor 只改 RectBack 与 ButtonStack，从不改 Foreground。）
    val isChecked = (type == PclListItemType.RadioBox || type == PclListItemType.CheckBox) && checked
    val contentColor = animateTo(
        when {
            !enabled -> p.gray2
            isChecked -> if (compact) p.color3 else p.color2
            else -> p.color1
        },
        200,
    )

    // MyListItem.xaml.vb:584 / :620 / :604 —— RectBack（指向浮层）只在鼠标指向（触屏为按下）时出现：
    //   底色 ColorBg1 = MyColor(190, Color7)，即 ColorBrush7 @ alpha 190/255（**半透明**，不是实色）；
    //   边框 1px ColorBrush6。空闲时 RectBack 的 Background 是 ColorBrush7、Opacity = 0（不可见）。
    val background = animateTo(if (active) p.color7.copy(alpha = 190f / 255f) else Color.Transparent, 120)
    val borderColor = animateTo(if (active) p.color6 else Color.Transparent, 120)

    val checkColumnWidth: Dp = when (type) {
        PclListItemType.None, PclListItemType.Clickable -> if (compact) 4.dp else 2.dp
        else -> 6.dp
    }
    val hasLogo = logo != null || logoContent != null
    val logoColumnWidth: Dp = (if (hasLogo) 34.dp else 0.dp) + (if (compact) 0.dp else 4.dp)
    // PathLogo.Margin：位图固定 (4,5,3,5)；矢量高<40 → (6,8,4,8)，否则 (8,8,6,8)
    val logoPadding = when {
        logoContent != null -> PaddingValues(start = 4.dp, top = 5.dp, end = 3.dp, bottom = 5.dp)
        compact -> PaddingValues(start = 6.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
        else -> PaddingValues(start = 8.dp, top = 8.dp, end = 6.dp, bottom = 8.dp)
    }

    val clickable = onClick != null || type == PclListItemType.Clickable || type == PclListItemType.RadioBox ||
        type == PclListItemType.CheckBox
    // MyListItem.xaml.vb:21 —— RectBack.CornerRadius = If(IsScaleAnimationEnabled OrElse Height > 40, 6, 0)。
    // 左栏条目（高 36）在 XAML 里显式写了 IsScaleAnimationEnabled="False"，因此圆角为 0；
    // 高 ≥40 的列表项（如资源列表 48）一律为 6。
    val corner = if (compact) 0.dp else 6.dp

    // MyListItem.xaml.vb:617 / :627 —— ColumnPaddingRight =
    //   max(MinPaddingRight, 5 + Buttons.Count * 25)。它就是标题右侧必须让出的宽度，
    //   按钮本身是 ColumnSpan=10 的浮层（右对齐、Margin=(0,0,5,0)，每个宽 25），不占列宽。
    val rightReserve = maxOf(minPaddingRight, (5 + buttons.size * 25).dp)

    Box(
        modifier = modifier
            .heightIn(min = height)
            .then(
                if (scaling) {
                    Modifier.graphicsLayer {
                        scaleX = itemScaleState.value
                        scaleY = itemScaleState.value
                    }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(corner))
            .background(background)
            .border(pclHairline(), borderColor, RoundedCornerShape(corner))
            .then(
                if (clickable) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                    ) { onClick?.invoke() }
                } else {
                    Modifier
                }
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 勾选条：RectCheck Width=5、圆角 2、色 ColorBrush3；
            // Margin=(-1,6,0,6)、VerticalAlignment=Stretch → 紧贴左边缘、高度 = 项高 − 12。
            //
            // 这里给列盒写**显式高度**而不是 `fillMaxHeight()`：列表处在纵向滚动容器里，
            // 高度上限是 Infinity，`fillMaxHeight()` 拿不到有意义的父高度，
            // 列盒会塌成内容高度（实测约 29dp），连带着下面的图标盒一起变小。
            Box(
                modifier = Modifier.width(checkColumnWidth).height(height),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (type == PclListItemType.RadioBox || type == PclListItemType.CheckBox) {
                    // MyListItem.xaml.vb:447-459 —— 勾选时竖条分两段伸长：
                    //   AaHeight(RectCheck, Delta*0.4, 200, AniEaseOutFluent(Weak))
                    //   AaHeight(RectCheck, Delta*0.6, 300, AniEaseOutBack(Weak))    ' 带点回弹
                    //   同时 AaOpacity(RectCheck, 1 - cur, 30)
                    // 取消时：AaHeight(RectCheck, -H, 120, AniEaseInFluent(Weak))。
                    val barAnim = remember { Animatable(0f) }
                    LaunchedEffect(checked, compact) {
                        val full = (height - 12.dp).value
                        if (checked) {
                            val delta = full - barAnim.value
                            barAnim.animateTo(
                                barAnim.value + delta * 0.4f,
                                tween(200, easing = PclEase.outFluentWeak),
                            )
                            barAnim.animateTo(full, tween(300, easing = PclEase.outBackWeak))
                        } else {
                            barAnim.animateTo(0f, tween(120, easing = PclEase.inFluentWeak))
                        }
                    }
                    val barHeight = barAnim.value.dp
                    if (barHeight > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (enabled) p.color3 else p.gray5),
                        )
                    }
                }
            }

            // 图标：填充 34 宽的图标列（去掉 left/right margin 后 31 宽）。
            // 高 42 时可用区 = (38−4−3) × (42−5−5) = 31 × 32 —— PCL 里 `PathLogo.Margin="4,5,3,5"`
            // 叠在 `RowSpan=4` 的整行上，得到的正是这块区域。
            //
            // 高度必须**写死成项高**（不能用 `fillMaxHeight()`）：列表在纵向滚动容器里，
            // 传入的高度上限是 Infinity，`fillMaxHeight()` 会退化成内容高度，
            // 图标盒随之塌到约 20dp，方块贴图比 PCL 小一整圈（「版本图标大小不协调」就是这个）。
            // 容器有确定高宽，`Icon`/`Image` 才能按 ContentScale 铺满（等价于 WPF 的
            // `Stretch.Uniform` / 位图的 `Stretch.Fill`）；再叠 PCL 的 LogoScale。
            Box(
                modifier = Modifier
                    .width(logoColumnWidth)
                    .height(height)
                    .padding(logoPadding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    logoContent != null -> logoContent()
                    logo != null -> {
                        // `PathLogo` 是 `Stretch=Uniform`：等比装进「图标列 − Margin」这块矩形，
                        // 较长边贴边、较短边留白，不能按各轴拉伸（否则齿轮之类的图标会被压扁/拉长）。
                        val ld = LocalLayoutDirection.current
                        PclUniformIcon(
                            imageVector = logo,
                            tint = contentColor,
                            boxWidth = logoColumnWidth -
                                logoPadding.calculateStartPadding(ld) -
                                logoPadding.calculateEndPadding(ld),
                            boxHeight = height -
                                logoPadding.calculateTopPadding() -
                                logoPadding.calculateBottomPadding(),
                            scale = logoScale,
                        )
                    }
                }
            }

            // 标题 + 副标题：LabTitle.Margin = (4,0,0, 高<40 ? 0 : 2)，右侧让出 ColumnPaddingRight
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = rightReserve, bottom = if (compact) 0.dp else 2.dp),
            ) {
                Text(
                    text = if (titleSuffix.isNullOrEmpty()) {
                        AnnotatedString(title)
                    } else {
                        // 对应 McInstance.ToListItem 的第二个 Run：Foreground=MyColor(215,215,215)、FontSize=12
                        buildAnnotatedString {
                            append(title)
                            withStyle(SpanStyle(color = Color(0xFFD7D7D7), fontSize = 12.sp)) {
                                append(titleSuffix)
                            }
                        }
                    },
                    style = PclTextStyle.listTitle(p).copy(
                        fontSize = fontSize.sp,
                        lineHeight = PclTextStyle.lineHeight(fontSize),
                        color = contentColor,
                    ),
                    maxLines = 1,
                )
                if (!info.isNullOrEmpty()) {
                    Text(
                        text = info,
                        style = PclTextStyle.listInfo(p).copy(
                            color = if (enabled) infoColor ?: p.gray2 else p.gray5,
                        ),
                        maxLines = 1,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }

        // 右侧按钮浮层：`ButtonStack`（右对齐、Margin=(0,0,5,0)、每项 25 宽）。
        // 源码里默认为 Opacity=0，仅在鼠标指向时淡入；触屏没有 hover 会永远点不到，
        // 因此这里保持常显（唯一一处为了可用性而偏离原样，其余几何完全一致）。
        if (buttons.isNotEmpty()) {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons.forEach { btn ->
                    PclIconButton(
                        logo = btn.logo,
                        size = 25.dp,
                        enabled = enabled,
                        logoScale = btn.logoScale,
                        contentDescription = btn.tooltip,
                        onClick = btn.onClick,
                    )
                }
            }
        }
    }
}

/* ============================================================================
 *  PCL 复选框 / 设置开关
 * ==========================================================================*/

/**
 * `MyCheckBox` —— 18×18 方框（边框 1.1、圆角 3、底色 `#55ffffff`）+ 对勾 + 文字。
 *
 * 边框色取自 `MyCheckBox.xaml.vb:SyncUI`：未选中 ColorBrush1、选中 ColorBrush2、
 * 禁用 ColorBrushGray5；文字 `FontSize=13`、`Margin="26,0,0,0"`。
 */
@Composable
fun PclCheckBox(
    text: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val p = pclPalette
    val (interaction, _) = rememberInteraction()
    // 边框色：勾选态变更 150ms（MyCheckBox.AnimationTimeOfCheck）
    val borderColor = animateTo(
        target = when {
            !enabled -> p.gray5
            checked -> p.color2
            else -> p.color1
        },
        durationMillis = 150,
    )
    // 勾标记：150ms 缩 + 300ms(delay105) `AniEaseOutBack(Weak)` 回弹
    val checkScale = rememberPclCheckScale(checked)

    Row(
        modifier = modifier
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(PclDimens.checkBoxSize)
                .clip(RoundedCornerShape(PclDimens.checkBoxCorner))
                .background(p.halfWhite)
                .border(PclDimens.checkBoxBorder, borderColor, RoundedCornerShape(PclDimens.checkBoxCorner)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PclIcons.check,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier
                    .size(PclDimens.checkBoxLogo)
                    .graphicsLayer {
                        scaleX = checkScale.value
                        scaleY = checkScale.value
                    },
            )
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(PclDimens.settingTextStart - PclDimens.checkBoxSize))
            Text(
                text = text,
                style = PclTextStyle.base(p).copy(color = if (enabled) p.color1 else p.gray5),
                overflow = TextOverflow.Visible,
            )
        }
    }
}

/**
 * `MyRadioBox` —— PCL 的单选圆框。
 *
 * `18×18` 圆形（边框 1.1、底色 `#55ffffff`）、选中时 9 号实心圆点；
 * 边框色：未选中 ColorBrush1、选中 ColorBrush2、禁用 Gray5；
 * 文字 `FontSize=13`、`Margin="26,0,0,0"`。
 */
@Composable
fun PclRadioBox(
    text: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val p = pclPalette
    val (interaction, _) = rememberInteraction()
    // 边框色：勾选态变更 150ms（MyRadioBox.AnimationTimeOfCheck）
    val borderColor = animateTo(
        target = when {
            !enabled -> p.gray5
            checked -> p.color2
            else -> p.color1
        },
        durationMillis = 150,
    )
    // 圆点：390ms `AniEaseOutBack(Weak)` 回弹 + 75ms 淡入（delay 90）
    val (dotScale, dotAlpha) = rememberPclRadioDotScale(checked)

    Row(
        modifier = modifier
            .height(PclDimens.checkBoxSize + 4.dp)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) {
                onCheckedChange(true)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(PclDimens.radioBoxSize), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(PclDimens.radioBoxSize)
                    .clip(CircleShape)
                    .background(p.halfWhite)
                    .border(PclDimens.radioBoxBorder, borderColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(PclDimens.radioBoxDot)
                        .graphicsLayer {
                            scaleX = dotScale.value
                            scaleY = dotScale.value
                            alpha = dotAlpha.value
                        }
                        .clip(CircleShape)
                        .background(borderColor),
                )
            }
        }
        if (text.isNotEmpty()) {
            Spacer(Modifier.width(PclDimens.settingTextStart - PclDimens.radioBoxSize))
            Text(
                text = text,
                style = PclTextStyle.base(p).copy(color = if (enabled) p.color1 else p.gray5),
                maxLines = 1,
            )
        }
    }
}

/**
 * `MyExtraTextButton` —— 悬浮胶囊按钮（「开始下载」「版本独立设置」）。
 *
 * 几何取自 `MyExtraTextButton.xaml`：
 *   外层 `Height="52"`、`Margin="20"`；内层 `PanScale Margin="0,10,0,0"` → 实体胶囊高 42；
 *   圆角 = 外层高度 × 0.4 = 20.8；
 *   内容 `StackPanel Margin="20,0"`，图标 `Path Margin="2,12,0,12"`（→ 高 18）、
 *   文字 `FontSize=16`、`Margin="12,0,0,0.8"`、颜色 ColorBrush8；底色 ColorBrush3。
 */
@Composable
fun PclExtraTextButton(
    text: String,
    logo: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    logoScale: Float = 1f,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val (interaction, active) = rememberInteraction()
    val background = animateTo(
        when {
            !enabled -> p.gray4
            active -> p.color4
            else -> p.color3
        }
    )
    Box(modifier = modifier.height(52.dp).padding(top = 10.dp)) {
        Row(
            // 胶囊**按内容撑宽**，不铺满：PCL 的 `MyExtraTextButton` 自身只有 `Margin="20"`，
            // 宽度由调用方的 `HorizontalAlignment`（Center/Bottom）决定，
            // 所以「开始下载」「开始导出」「版本独立设置」都是短胶囊。
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(20.8.dp))
                .background(background)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = logo,
                contentDescription = null,
                tint = p.color8,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer(scaleX = logoScale, scaleY = logoScale),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = PclTextStyle.base(p).copy(color = p.color8, fontSize = 16.sp),
                overflow = TextOverflow.Visible,
            )
        }
    }
}

/** PCL 设置项外观的开关行：`标题 (+ 说明) ............ [复选框]`。 */
@Composable
fun PclSwitchItem(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val p = pclPalette
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = PclTextStyle.base(p).copy(color = if (enabled) p.color1 else p.gray4))
            if (!description.isNullOrEmpty()) {
                Text(
                    text = description,
                    style = PclTextStyle.listInfo(p).copy(color = p.gray2),
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        PclCheckBox(text = "", checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/* ============================================================================
 *  PCL 滑块
 * ==========================================================================*/

/**
 * `MySlider` —— 1px 底轨（ColorBrushBg0 @ 0.3）、2px 前轨（主题色）、
 * 10dp 圆点（ColorBrushBg0 填充 + 1.2dp 主题色描边）。支持点击与拖动。
 */
@Composable
fun PclSlider(
    value: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
    /**
     * 松手回调。PCL 的滑杆是「拖动中连续生效」，这里额外给一个「这一轮拖动结束」的信号 ——
     * 有些调用处要拿它当「可以关掉弹窗了」的时机（如编辑控件时改完尺寸就退出）。
     * 不传则完全不触发。
     */
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val p = pclPalette
    val activeColor = if (enabled) p.color2 else p.gray4

    // MySlider.xaml.vb:87 / :103 —— 手柄按下的缩放：
    //   按下 `AaScaleTransform(ShapeDot, 1.3 - cur, 40ms, AniEaseOutFluent)`（快速放大到 1.3）
    //   松开 `AaScaleTransform(ShapeDot, 1 - cur, 200ms, AniEaseOutFluent)`
    var dragging by remember { mutableStateOf(false) }
    // 同样延迟到 draw 阶段读取：拖动期间只重绘、不重组
    val dotScaleState = animateFloatAsState(
        targetValue = if (dragging) 1.3f else 1f,
        animationSpec = tween(if (dragging) 40 else 200, easing = PclEase.outFluent),
        label = "pclSliderDot",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(16.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (!change.pressed) {
                            // 手指抬起：本轮拖动结束
                            if (dragging) dragging = false
                            onValueChangeFinished?.invoke()
                            continue
                        }
                        if (!dragging) dragging = true
                        val fraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onValueChange(fraction)
                        change.consume()
                    }
                }
            },
    ) {
        val cy = size.height / 2f
        val v = value.coerceIn(0f, 1f)
        drawLine(
            color = p.bg0.copy(alpha = 0.3f),
            start = Offset(0f, cy),
            end = Offset(size.width, cy),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = activeColor,
            start = Offset(0f, cy),
            end = Offset(size.width * v, cy),
            strokeWidth = 2.dp.toPx(),
        )
        val dot = 5.dp.toPx() * dotScaleState.value
        drawCircle(color = p.bg0, radius = dot, center = Offset(size.width * v, cy))
        drawCircle(
            color = activeColor,
            radius = dot,
            center = Offset(size.width * v, cy),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }
}

/* ============================================================================
 *  PCL 文本框 / 搜索框
 * ==========================================================================*/

/** `MyTextBox` —— 高 28 起，边框 1、圆角 3、底色半透明白、边框色 ColorBrushBg0。 */
@Composable
fun PclTextBox(
    value: String,
    modifier: Modifier = Modifier,
    hint: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    fontSize: Int = 13,
    /** 密码框：显示成圆点，实际内容不变。 */
    secret: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val p = pclPalette
    Box(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(p.halfWhite)
            .border(pclHairline(), p.bg0, RoundedCornerShape(3.dp))
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && hint.isNotEmpty()) {
            Text(text = hint, style = PclTextStyle.base(p).copy(color = p.gray4, fontSize = fontSize.sp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = PclTextStyle.base(p).copy(fontSize = fontSize.sp),
            cursorBrush = SolidColor(p.color3),
            visualTransformation = if (secret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * `MySearchBox` 的放大镜边长。
 *
 * PCL 原文是 `Path Width="14" Height="14" Margin="14,0,0,0" Stretch="Uniform"`，
 * 于是图形区恒为 14×14、左边界恒在 14，文字从 32 开始（`MyTextBox Padding="32,0,40,0"`）。
 *
 * 注意这个图标**不能**用 `Modifier.fillMaxSize()` 交给父盒决定大小：
 * 它的视口宽高是 1023.136 × 1023.184（紧密包围盒），一旦父盒不是正方形，
 * Compose 会按各轴独立缩放把它拉变形，环带左右两半的落点就不同，
 * 看上去就像放大镜被切掉了一截。这里显式给出方盒子 + `Stretch=Uniform` 语义。
 */
private val PCL_SEARCH_ICON = 14.dp

/** 搜索框里图标与文字的分界线（PCL 的 `MyTextBox Padding="32,0,40,0"`）。 */
private val PCL_SEARCH_TEXT_START = 32.dp

/**
 * `MySearchBox` —— 高 40 的卡片式搜索框，左侧放大镜、右侧清空。
 *
 * [onSearch] 对应 PCL 的 `EnterTrigger`：在搜索框里按回车就开搜
 * （`PageResource.xaml.vb:228-230`，`TextSearchName.KeyDown` 判 `Key.Enter`）。
 * Android 上没有回车键，改绑输入法的搜索键（放大镜）。不传则不绑，
 * 行为与改动前一致。
 */
@Composable
fun PclSearchBox(
    value: String,
    modifier: Modifier = Modifier,
    hint: String = "搜索",
    onSearch: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    val p = pclPalette
    PclCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
            // `Path` 在 `MySearchBox` 的 Grid 里是左对齐、垂直居中（Height=14 显式指定），
            // 这里用 CenterStart + start padding 精确复刻它的位置。
            PclUniformIcon(
                imageVector = PclIcons.search,
                tint = p.color1,
                boxWidth = PCL_SEARCH_ICON,
                boxHeight = PCL_SEARCH_ICON,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp),
            )
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    style = PclTextStyle.base(p).copy(color = p.gray4, fontSize = 13.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = PCL_SEARCH_TEXT_START, end = 40.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                // PCL 的 `EnterTrigger`：搜索框里回车即开搜。Android 用输入法的搜索键替代。
                keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
                keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
                textStyle = PclTextStyle.base(p).copy(fontSize = 13.5.sp, lineHeight = PclTextStyle.lineHeight(13),),
                cursorBrush = SolidColor(p.color3),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = PCL_SEARCH_TEXT_START, end = 40.dp),
            )
            if (value.isNotEmpty()) {
                // PCL：`MyIconButton Width=24 Height=24 LogoScale=0.75`，
                // `MyIconButton` 的图形区 = Height − 10 = 14 → 实际图形 14×0.75 = 10.5
                PclIconButton(
                    logo = PclIcons.close,
                    theme = PclIconTheme.Black,
                    size = 24.dp,
                    logoSize = 14.dp,
                    logoScale = 0.75f,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    onClick = { onValueChange("") },
                )
            }
        }
    }
}

/* ============================================================================
 *  PCL 下拉框
 * ==========================================================================*/

/**
 * `MyComboBox` —— 模板见 `Application.xaml:376-424`，配色见 `MyComboBox.vb:76-100`。
 *
 * 收起态（`Border CornerRadius="3" BorderThickness="1"`，`Brush = TemplateBinding Foreground`）：
 *   · 正常：边框 + 箭头 + 文字 = `ColorBrushBg0`（**#96c0f9**）、底 = `ColorBrushHalfWhite`（#55ffffff）
 *   · 指向：`ColorBrush4` / `ColorBrush7`
 *   · 按下或展开：`ColorBrush3` / `ColorBrush7`
 *   · 禁用：`ColorBrushGray5` / `ColorBrushGray6`
 *   文字左边距 8.5、右边距 21.5（给箭头让位）；箭头 `Width="15"` 右对齐、
 *   描边 1.5、`RenderTransformOrigin="0.3,0.5"`，展开时 180° 旋转、200ms CircleEase EaseOut。
 *
 * 展开态（`Popup Placement="Bottom" VerticalOffset="-1.5" PopupAnimation="Fade"`）：
 *   · **VerticalOffset = -1.5**：下拉层与触发器底边重叠 1.5px，边框连成一体；
 *   · 白色底 + 圆角 3 + **与触发器同色的 1px 边框**（`dropDownBorder`），`MaxHeight=320` 且可滚动；
 *   · `Width="{TemplateBinding Width}"`，而 PCL 各页的 ComboBox 都没写 Width（=NaN），
 *     `MyComboBox_DropDownOpened` 会在展开时把 Width 临时设成 `ActualWidth`
 *     → **弹层宽度 = 触发器宽度**（收起后还原 NaN）。所以这里也必须跟触发器等宽。
 *
 * 条目（`MyComboBoxItem`，`Application.xaml:487-500` + `MyComboBoxItem.vb`）：
 *   模板 `Padding="6,4"`；选中底色 `ColorBrush6`、指向 `ColorBrush8`、正常透明、禁用透明度 0.4。
 */
@Composable
fun <T> PclComboBox(
    items: List<T>,
    selected: T?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 28.dp,
    /**
     * 把一条数据渲染成显示文字。
     *
     * 参数是**可空**的：`items` 里允许出现 `null` 项（例如资源下载页的筛选框，
     * 第一项 `null` 就代表「全部」），收起态在没选中任何具体项时也要把这一项渲染出来。
     */
    label: (T?) -> String = { it?.toString().orEmpty() },
    /**
     * 下拉列表最上面那一条**固定项**。
     *
     * PCL 的 `ComboAccounts`（`PageLoginMs.xaml.vb:11`）第 0 项永远是
     * `LangPageLoginMsAddAccount`（「添加正版账户」），下面才是已保存的账户；
     * `SelectedIndex = 0` 就代表「不走任何已存账户，重新登一个」。
     */
    leadingItem: String? = null,
    /** 点 [leadingItem] 时触发（PCL 里等价于 `ComboAccounts.SelectedIndex = 0`）。 */
    onLeadingClick: (() -> Unit)? = null,
    /**
     * 非空时，每条右侧带一枚删除按钮 —— 对应 PCL 的 `ComboBoxItemTemplateWithDelete`。
     * PCL 的删除会直接摘掉该条账户记录。
     */
    onDelete: ((T) -> Unit)? = null,
    onSelect: (T) -> Unit,
) {
    val p = pclPalette
    val density = LocalDensity.current
    var expanded by remember { mutableStateOf(false) }
    val (interaction, active) = rememberInteraction()
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    // RefreshColor：边框（Foreground）与底色（Background）按状态切换。
    //
    // 【与源码唯一的一处有意偏差】PCL 的常态前景是 `ColorBrushBg0`（约 #73ABE3）。
    // 那个颜色是画在**桌面端偏蓝的窗体底色**上的，淡一点没关系；本移植把设置项放进
    // 近白的卡片（`ColorBg = #F5FFFFFF`）里，同一支淡蓝在手机的小屏、细边框下
    // 只剩一层灰，用户反馈「下拉栏颜色太淡了」。
    // 因此把整条状态阶梯**整体下移一档**：常态用 PCL 的「按下色」ColorBrush3，
    // 指向/展开再深一档用 ColorBrush2。上下深浅的顺序语义与源码完全一致
    // （越深入地交互越深），只是起点抬到了能看清的位置。
    val borderColor = animateTo(
        when {
            !enabled -> p.gray5
            expanded || active -> p.color2
            else -> p.color3
        }
    )
    val background = animateTo(
        when {
            !enabled -> p.gray6
            expanded || active -> p.color7
            else -> p.halfWhite
        }
    )
    // ToggleButton 里箭头被 IsChecked 驱动旋转 180°（200ms、CircleEase EaseOut）
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200, easing = EaseOutCirc),
        label = "pclComboArrow",
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(RoundedCornerShape(3.dp))
                .background(background)
                .border(pclHairline(), borderColor, RoundedCornerShape(3.dp))
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = null,
                ) { expanded = !expanded }
                .onGloballyPositioned { anchorSize = it.size },
        ) {
            // ContentPresenter Margin="8.5,0,21.5,0"
            //
            // 收起态显示什么：
            //   1. 有选中项 → 渲染该项；
            //   2. 没选中但给了 `leadingItem` → 显示它（PCL 的 `ComboAccounts`
            //      默认 `SelectedIndex = 0`，触发器上写的就是「添加新账号」）；
            //   3. 否则 → `label(null)`。资源下载页那排筛选框属于这一类：它们的
            //      `items` 第一项就是 `null`（「全部」），`selected = null` 正是选中了它，
            //      所以收起态必须把 `label(null)` 显示出来，不能留空。
            Text(
                text = when {
                    selected != null -> label(selected)
                    leadingItem != null -> leadingItem
                    else -> label(null)
                },
                style = PclTextStyle.base(p).copy(fontSize = 13.sp, lineHeight = PclTextStyle.lineHeight(13), color = borderColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.5.dp, end = 21.5.dp),
            )
            // ToggleButton 里的 Path：Width=15、右对齐、Margin 右 1.5
            Icon(
                imageVector = PclIcons.comboArrow,
                contentDescription = null,
                tint = borderColor,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.5.dp)
                    .size(width = 9.dp, height = 5.5.dp)
                    .rotate(arrowRotation),
            )
        }

        if (expanded) {
            PclComboBoxPopup(
                anchorSize = anchorSize,
                borderColor = borderColor,
                onDismiss = { expanded = false },
            ) {
                if (leadingItem != null) {
                    PclComboBoxItemRow(
                        text = leadingItem,
                        selected = false,
                        foreground = borderColor,
                        enabled = enabled,
                        onClick = {
                            expanded = false
                            onLeadingClick?.invoke()
                        },
                    )
                }
                items.forEach { item ->
                    PclComboBoxItemRow(
                        text = label(item),
                        selected = item == selected,
                        foreground = borderColor,
                        enabled = enabled,
                        trailing = onDelete?.let { del -> { del(item) } },
                        onClick = {
                            expanded = false
                            onSelect(item)
                        },
                    )
                }
            }
        }
    }
}

/**
 * `MyComboBox` 的展开弹层（`Application.xaml:376-424` 里 `Popup` 那一段）。
 *
 * 单独抽出来，是为了让「触发器不是 [PclComboBox]」的地方也能用同一套形状 ——
 * 例如离线登录页那个可输入的 `ComboName`（`MyComboBox IsEditable="True"`）。
 *
 * PCL 的关键几何：
 * · `Popup Placement="Bottom" VerticalOffset="-1.5"` —— 弹层与触发器底边重叠 1.5px，
 *   两段边框连成一体；
 * · `Width="{TemplateBinding Width}"`，而各页 ComboBox 都没写 Width（=NaN），
 *   `MyComboBox_DropDownOpened` 会在展开时把 Width 临时设成 `ActualWidth`
 *   → **弹层宽度 = 触发器宽度**（否则在手机上会铺满整个弹窗空间）；
 * · 白色底 + 圆角 3 + **与触发器同色的 1px 边框**，`MaxHeight=320` 且可滚动。
 */
@Composable
internal fun PclComboBoxPopup(
    anchorSize: IntSize,
    borderColor: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val overlap = with(density) { 1.5.dp.roundToPx() }
    // 触发器宽度先按**调用点**的 density 换算成 dp，弹层里再用同一个 density 还原成 px。
    // 一进一出必须用同一把尺子，见下面 `CompositionLocalProvider` 的说明。
    val anchorWidth = with(density) { anchorSize.width.toDp() }
    Popup(
        alignment = Alignment.TopStart,
        // offset 是**像素**，直接由 anchor 的像素高算出，不经过 dp，不受 density 影响。
        offset = IntOffset(0, (anchorSize.height - overlap).coerceAtLeast(0)),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // 【为什么必须显式 provide】Popup 的内容挂在**另一个窗口**的子 composition 里，
        // 它拿到的 `LocalDensity` 不保证等于调用点的。而 `PclTheme` 是把「界面缩放」乘进
        // density 实现的（`PclTheme.kt:451`）。两边一旦不一致，弹层就会用另一把尺子渲染：
        //   · 宽度这条「px → dp → px」的往返会错位，弹层宽出触发器一大截（「界面缩放改成
        //     75% 后下拉栏变得又长又宽」就是这个）；
        //   · 行高与字号也按别处的密度走，「界面缩放」对弹层形同虚设。
        // 把调用点的 density 带进去，弹层的每一个 dp 都和触发器、和整页 UI 同尺。
        CompositionLocalProvider(LocalDensity provides density) {
            Box(
                modifier = Modifier
                    .width(anchorWidth)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
                    .border(pclHairline(), borderColor, RoundedCornerShape(3.dp)),
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    content = content,
                )
            }
        }
    }
}

/**
 * `MyComboBoxItem`（`Application.xaml:487-500`）—— `Border Padding="6,4"` + 四态底色。
 *
 * [trailing] 不为空时，在条目右侧加一枚删除按钮 —— 对应 PCL 的
 * `ComboBoxItemTemplateWithDelete`（`PageLoginMs.xaml.vb:16-20`）：
 * 微软账户下拉框里每一条都能直接删掉。
 */
@Composable
internal fun PclComboBoxItemRow(
    text: String,
    selected: Boolean,
    foreground: Color,
    enabled: Boolean,
    trailing: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val p = pclPalette
    val (interaction, active) = rememberInteraction()
    // AnimationTimeIn = 100 / AnimationTimeOut = 300
    val background = animateTo(
        when {
            selected -> p.color6
            active -> p.color8
            else -> Color.Transparent
        },
        if (active || selected) 100 else 300,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .graphicsLayer(alpha = if (enabled) 1f else 0.4f)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = PclTextStyle.base(p).copy(fontSize = 13.sp, lineHeight = PclTextStyle.lineHeight(13), color = foreground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            // PCL 的 `MyListItem` 按钮列：`MyIconButton Height=24 LogoScale=0.75`
            PclIconButton(
                logo = PclIcons.close,
                theme = PclIconTheme.Black,
                size = 24.dp,
                logoScale = 0.75f,
                contentDescription = "删除",
                enabled = enabled,
                onClick = trailing,
            )
        }
    }
}

/* ============================================================================
 *  PCL 加载中
 * ==========================================================================*/

/**
 * `MyLoading` —— PCL 的镐子挥动加载动画 + 16 号文案。
 *
 * 动效原封不动取自 `MyLoading.xaml.vb:180-205` 的循环（源码注释自己写着
 * 「这坨循环代码也是老屎坑了，救救.jpg」），几何取自 `MyLoading.xaml`：
 *
 * ```
 * Grid 60×47（PanBack 为半透明底）：
 *   PathPickaxe  Height=35  Margin(10,6)  StrokeThickness=2
 *                RotateTransform Angle=55 CenterX=30 CenterY=30
 *   PathLeft     W=3 H=5  Margin(7,41)   RotateTransform -45
 *   PathRight    W=3 H=5  Margin(14,41)  RotateTransform  45
 *   Rectangle    25×2      VerticalAlignment=Bottom
 * ```
 *
 * 循环（一轮 ≈ 350 + 900 + 900 = 2150ms）：
 * ```
 * 1) AaRotateTransform(PathPickaxe, -20 - Angle, 350, 250, AniEaseInBack(Weak))   ' 55° → -20° 抬起
 * 2) AaRotateTransform(PathPickaxe, +50, 900, , AniEaseOutFluent, After:=True)    ' -20° → 30° 挥下
 * 3) AaRotateTransform(PathPickaxe, +25, 900, , AniEaseOutElastic(Weak))          ' 30° → 55° 弹性回位
 *    同时：AaOpacity(PathLeft, -1, 100, 50) + AaX(-5) / AaY(-6) 180ms（右侧对称 +5/-6）
 *    再 AaCode(重播)
 * ```
 * 也就是「抬起 → 抡下 → 弹性回位」，同时左右各飞出一粒碎屑。
 */
@Composable
fun PclLoading(
    text: String,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    Column(
        modifier = modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 17.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PclLoadingPickaxe()
        Spacer(Modifier.height(10.dp))
        Text(text = text, style = PclTextStyle.base(p).copy(fontSize = 16.sp, lineHeight = PclTextStyle.lineHeight(16),), textAlign = TextAlign.Center)
    }
}

/**
 * 只要 `MyLoading` 的**镐子挥动动画**（60×47 的盒子：镐子 + 左右碎屑 + 底部横条），不带文案。
 *
 * 启动界面（`PclHomeLaunching`）要和下载界面用**同一份**动画，所以把它从 [PclLoading]
 * 里抽出来。几何与时间轴见上面 [PclLoading] 的说明。
 */
@Composable
fun PclLoadingPickaxe(modifier: Modifier = Modifier) {
    val p = pclPalette

    // 镐子角度（度）与碎屑进度
    val angle = remember { Animatable(55f) }
    val fragAlpha = remember { Animatable(0f) }
    val fragShift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(250) // 首轮延迟（源码 `AaRotateTransform(..., 250, EaseInBack)` 的 delay）
        while (true) {
            // 1) 抬起：55° → -20°
            angle.animateTo(-20f, tween(350, easing = PclEase.inBackWeak))
            // 2) 挥下：-20° → +30°（+50）
            angle.animateTo(30f, tween(900, easing = PclEase.outFluent))
            // 3) 弹性回位（+25 → 55°）与碎屑飞出同时进行
            coroutineScope {
                launch {
                    fragAlpha.snapTo(1f)
                    fragShift.snapTo(0f)
                    delay(50)
                    fragAlpha.animateTo(0f, tween(100))
                }
                launch { fragShift.animateTo(1f, tween(180, easing = PclEase.outFluent)) }
                launch { angle.animateTo(55f, tween(900, easing = PclEase.outElasticWeak)) }
            }
        }
    }

    // Grid 60×47：镐子 + 左右碎屑 + 底部横条
    Box(modifier = modifier.size(60.dp, 47.dp)) {
        // 镐子绕自身的 (30,30) 旋转（相对 35×35 的盒子 = 0.857）
        Icon(
            imageVector = PclIcons.loadingPickaxe,
            contentDescription = null,
            tint = p.color3,
            modifier = Modifier
                .offset(x = 10.dp, y = 6.dp)
                .size(35.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(30f / 35f, 30f / 35f)
                    rotationZ = angle.value
                },
        )
        // 左侧碎屑：Margin(7,41)，RotateTransform -45，飞出 (-5,-6)
        val fragTint = p.color3
        Icon(
            imageVector = PclIcons.loadingFragment,
            contentDescription = null,
            tint = fragTint,
            modifier = Modifier
                .offset(
                    x = 7.dp + (-5).dp * fragShift.value,
                    y = 41.dp + (-6).dp * fragShift.value,
                )
                .size(3.dp, 5.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationZ = -45f
                    alpha = fragAlpha.value
                },
        )
        // 右侧碎屑：Margin(14,41)，RotateTransform 45，飞出 (+5,-6)
        Icon(
            imageVector = PclIcons.loadingFragment,
            contentDescription = null,
            tint = fragTint,
            modifier = Modifier
                .offset(
                    x = 14.dp + 5.dp * fragShift.value,
                    y = 41.dp + (-6).dp * fragShift.value,
                )
                .size(3.dp, 5.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    rotationZ = 45f
                    alpha = fragAlpha.value
                },
        )
        // 底部横条 Rectangle 25×2
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(25.dp, 2.dp)
                .background(p.color3),
        )
    }
}

/**
 * 居中的加载卡片（PCL 各页的 `PanLoad`）。
 *
 * XAML 原文：`MyCard HorizontalAlignment="Center" VerticalAlignment="Center" Margin="40,50"`
 * 内含 `MyLoading Margin="20,20,20,17"` —— 卡片宽度**贴合内容**并水平居中，不是铺满整行。
 * 因此调用方需要把它放进 Column/Box 并自行居中（例如 `Modifier.align(Alignment.CenterHorizontally)`）。
 */
@Composable
fun PclLoadingCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    PclCard(
        modifier = modifier.padding(horizontal = 40.dp, vertical = 50.dp),
        contentPadding = PaddingValues(0.dp),
    ) {
        PclLoading(text = text)
    }
}

/* ============================================================================
 *  文本与分隔线
 * ==========================================================================*/

/** PCL 正文说明文本（13 号，可换行）。 */
@Composable
fun PclText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    fontSize: Int = 13,
    bold: Boolean = false,
    textAlign: TextAlign? = null,
) {
    val p = pclPalette
    Text(
        text = text,
        style = PclTextStyle.base(p).copy(
            color = color ?: p.color1,
            fontSize = fontSize.sp,
            // 行高按字号比例给：行高不足时 Compose 会把中文的下半截裁掉（见 PclTextStyle）
            lineHeight = PclTextStyle.lineHeight(fontSize),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        ),
        textAlign = textAlign,
        // 再兜一层：万一有字形比行高还高，也不要裁（PCL 的 WPF 同样不裁）
        overflow = TextOverflow.Visible,
        modifier = modifier,
    )
}

/**
 * PCL 的进度条：4dp 高、圆角 2，底轨 ColorBrush6、前景主题色。
 */
@Composable
fun PclProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    val value = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(p.color6),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(value)
                .clip(RoundedCornerShape(2.dp))
                .background(p.color3),
        )
    }
}

/** PCL 的分隔线（`PageSpeedLeft` 的 Split 样式：两端渐隐的主题色细线）。 */
@Composable
fun PclSplitLine(modifier: Modifier = Modifier) {
    val p = pclPalette
    Canvas(modifier = modifier.fillMaxWidth().height(2.dp)) {
        val brush = Brush.horizontalGradient(listOf(p.bg1, p.color3, p.color3, p.bg1))
        drawLine(
            brush = brush,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
        )
    }
}

/** `PanEmpty` 里的一个按钮：`Height="35"`、`MinWidth="140"`、`Padding="13,0"`。 */
data class PclEmptyButton(
    val text: String,
    val highlight: Boolean = false,
    /** `PageInstanceMod` 的按钮行里每个按钮都写了 `Margin="0,0,17,0"`；单个按钮的页则没有。 */
    val marginEnd: Dp = PCL_EMPTY_BUTTON_MARGIN_END,
    val onClick: () -> Unit,
)

/** `PanEmpty` 按钮之间的间隔（`Margin="0,0,17,0"`）。 */
private val PCL_EMPTY_BUTTON_MARGIN_END = 17.dp

/** `PanEmpty` 的按钮：`MinWidth="140"`、`Margin="0,10,0,0"`（`PageSelectRight` 单个按钮的那种）。 */
@Composable
fun PclEmptyStateButton(
    text: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    PclButton(
        text = text,
        colorState = if (highlight) PclColorState.Highlight else PclColorState.Normal,
        contentPadding = PaddingValues(horizontal = 13.dp),
        modifier = modifier.widthIn(min = PCL_EMPTY_BUTTON_MIN_WIDTH),
        onClick = onClick,
    )
}

/** `PanEmpty` 的按钮最小宽度。 */
private val PCL_EMPTY_BUTTON_MIN_WIDTH = 140.dp

/**
 * 空状态占位卡片。
 *
 * 尺寸与间距全部照 PCL 的 `PanEmpty`：
 * ```
 * MyCard  HorizontalAlignment=Center VerticalAlignment=Center Margin=40   ← 居中，宽度由内容撑出来
 *   StackPanel Margin="20,17"
 *     TextBlock  Margin="0,0,0,9"  FontSize=19  ColorBrush3  居中       ← 标题
 *     Rectangle  Height="2"  Fill=ColorBrush3  HorizontalAlignment=Stretch  ← 主题色细线
 *     TextBlock  Margin="10,15,10,5"（PageSelectRight）/ "10,10,10,0"（PageInstanceMod）
 *     MyButton   MinWidth="140" Height="35" Padding="13,0"
 * ```
 *
 * **关键**：卡片宽度是「最宽的那个孩子」撑出来的（标题 / 说明 / 按钮行），
 * 那条 `Stretch` 的细线再拉到同一个宽度 —— 所以细线不会比文字长出一大截。
 * WPF 那边 `StackPanel` 自己就取最宽孩子；Compose 里这里**不依赖**固有测量
 * （空状态常常嵌在滚动容器里，那条路不稳），而是用 [rememberTextMeasurer] 把
 * 标题 / 说明 / 按钮行三者的宽度量出来取最大值，再交给 `widthIn(max=…)` 当上限。
 * 调用方负责把这张卡片居中摆放（PCL 是 `HorizontalAlignment/VerticalAlignment=Center`）。
 */
@Composable
fun PclEmptyState(
    title: String,
    content: String,
    modifier: Modifier = Modifier,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    /** `PanEmpty` 里说明文字的外边距。 */
    contentPadding: PaddingValues = PaddingValues(start = 10.dp, top = 15.dp, end = 10.dp, bottom = 5.dp),
    /** 按钮行与说明文字的间距（`PageSelectRight` 是 5+10，`PageInstanceMod` 是 0+20）。 */
    buttonsTopPadding: Dp = 10.dp,
    /** 除首个按钮外还要并排的按钮（`PageInstanceMod` 的空状态是三个）。 */
    moreButtons: List<PclEmptyButton> = emptyList(),
) {
    val p = pclPalette
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = PclTextStyle.base(p).copy(
        fontSize = 19.sp,
        // 19sp 的字身比 base 的 13sp 行高更高，必须一起放大，否则会被裁（见 PclTextStyle）
        lineHeight = PclTextStyle.lineHeight(19),
        color = p.color3,
        textAlign = TextAlign.Center,
    )
    val tipStyle = PclTextStyle.base(p)
    val buttonStyle = PclTextStyle.button(p)

    val buttons = buildList {
        if (buttonText != null && onButtonClick != null) {
            // `PageSelectRight` 的单个按钮是 `Margin="0,10,0,0"`（右侧不留）；
            // `PageInstanceMod` 的三个按钮则**每个**都写 `Margin="0,0,17,0"`（含最后一个），
            // 所以并排时按钮之间的间隔处处都是 17 —— 别把第一个的右间距写成 0。
            add(
                PclEmptyButton(
                    buttonText,
                    highlight = true,
                    marginEnd = if (moreButtons.isEmpty()) 0.dp else PCL_EMPTY_BUTTON_MARGIN_END,
                    onClick = onButtonClick,
                )
            )
        }
        addAll(moreButtons)
    }
    // 三个孩子各自的「期望宽度」，取最大者就是卡片的正文宽度
    val layoutDirection = LocalLayoutDirection.current
    val tipSidePadding = contentPadding.calculateStartPadding(layoutDirection) +
            contentPadding.calculateEndPadding(layoutDirection)
    val contentWidth = with(density) {
        fun textPx(text: String, style: TextStyle) = measurer.measure(text, style).size.width.toDp()
        val titleW = textPx(title, titleStyle)
        val tipW = textPx(content, tipStyle) + tipSidePadding
        val rowW = buttons.fold(0.dp) { acc, btn ->
            // MyButton：Padding="13,0"、MinWidth="140"
            acc + maxOf(PCL_EMPTY_BUTTON_MIN_WIDTH, textPx(btn.text, buttonStyle) + 26.dp) + btn.marginEnd
        }
        maxOf(titleW, tipW, rowW)
    }

    PclCard(
        modifier = modifier,
        // StackPanel Margin="20,17"
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 17.dp),
    ) {
        Column(modifier = Modifier.widthIn(max = contentWidth)) {
            Text(
                text = title,
                style = titleStyle,
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(p.color3))
            Text(
                text = content,
                style = tipStyle,
                modifier = Modifier.padding(contentPadding),
            )
            if (buttons.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = buttonsTopPadding),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    buttons.forEach { btn ->
                        PclEmptyStateButton(
                            text = btn.text,
                            highlight = btn.highlight,
                            modifier = Modifier.padding(end = btn.marginEnd),
                            onClick = btn.onClick,
                        )
                    }
                }
            }
        }
    }
}

/** 撑满剩余空间的占位（等价于 PCL 里 `Height = 18` 的底部留白）。 */
@Composable
fun PclBottomSpacer(height: Dp = 18.dp) {
    Spacer(Modifier.height(height))
}

/** 让 Box 充满：便于在自适应布局里写 `Modifier.pclFill()`。 */
fun Modifier.pclFill(): Modifier = this.fillMaxSize()
