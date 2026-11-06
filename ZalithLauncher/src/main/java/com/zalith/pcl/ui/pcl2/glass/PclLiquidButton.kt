/*
 * 移植自 Kyant 的 AndroidLiquidGlass（Apache-2.0）
 * https://github.com/Kyant0/AndroidLiquidGlass
 * 参照：app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt
 *
 * **本文件严格照抄源码的绘制链路，一个参数都不加。**
 *
 * 源码里 `drawBackdrop` 其实**只传了 effects 和 layerBlock**，`highlight` / `shadow` /
 * `innerShadow` 全部走库默认值：
 *
 *   highlight   = Highlight.Default → width 0.5dp、blurRadius 0.25dp、
 *                 style HighlightStyle.Default(angle = 45°, falloff = 1)
 *   shadow      = Shadow.Default
 *   innerShadow = null（源码**不用**内阴影）
 *
 * 这三点是关键：
 *   · 镜面边只有 0.5dp，靠 `BlendMode.Plus` 做**亮度叠加** —— 它不是描边，
 *     所以背景越亮它越亮、背景越暗它收进去，不会变成硬边框；
 *   · `HighlightStyle.Default` 是**方向性**的（一侧亮、对角渐暗），不是均匀柔光；
 *   · 没有内阴影，边缘不会有深色环。
 *
 * 之前为了修「黑边」把 highlight 换成 Ambient、又加了 #2A3A55 的 InnerShadow，
 * 等于把液态玻璃最核心的这两样全删了 —— 那不是调整，是删掉。
 *
 * 与原文的唯一差别是「几何」：原文写死 `Capsule()` + 48dp 高；
 * 这里把 shape / 高度 / 内容做成参数，好套进 PCL 的按钮。
 * 尺寸相关的三个数值（blur 2dp、lens 高 12dp、折射 24dp）按高度等比缩放。
 */

package com.zalith.pcl.ui.pcl2.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** 源码 LiquidButton 的高度，用来把 2dp / 12dp / 24dp 这三个数值等比换算。 */
private val SOURCE_HEIGHT = 48.dp

/**
 * 会「流动」的按钮表面 —— 源码 `LiquidButton` 的绘制链路，参数一个不改。
 *
 * @param surfaceColor 玻璃表面叠色。PCL 的按钮底色本身很浅（高亮态也是白底 + 彩字），
 *                     所以给一层低透明度白就够了；给多了会变成实心块。
 * @param textCenter   文字是否居中（PCL 的 `MyButton` 本来就居中）。
 */
@Composable
fun PclLiquidButtonSurface(
    backdrop: Backdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 35.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp),
    surfaceColor: Color = Color.White.copy(alpha = 0.20f),
    textCenter: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()

    // 源码同一段：高光跟着手指走、按下会鼓起来，全靠这个对象
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    // 源码的 2dp / 12dp / 24dp 是按 48dp 高调的；PCL 按钮只有 35dp，等比缩才不会糊成一团
    val k = (minHeight / SOURCE_HEIGHT).coerceIn(0.55f, 1.35f)

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // —— 以下三行与源码完全一致（只加了等比系数 k）——
                    vibrancy()
                    blur(2f.dp.toPx() * k)
                    lens(12f.dp.toPx() * k, 24f.dp.toPx() * k)
                },
                // ===== 这里**故意什么都不传** =====
                // highlight / shadow / innerShadow 全部走库默认值：
                //   0.5dp 方向性镜面边 + 默认投影 + 无内阴影。
                // 之前在这里塞 Ambient 高光和 #2A3A55 内阴影，就是「不像液态玻璃」的根源。
                layerBlock = if (enabled) {
                    // 源码原文：pressProgress 驱动的整体缩放 + 朝手指方向的 tanh 形变
                    {
                        val width = size.width
                        val height = size.height.coerceAtLeast(1f)

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                    (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                    (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (surfaceColor.alpha > 0f) drawRect(surfaceColor)
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .heightIn(min = minHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        // 必须显式居中：PCL 的 MyButton 文字是居中的，少了这一行就变成左对齐
        horizontalArrangement = if (textCenter) {
            Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        } else {
            Arrangement.spacedBy(8.dp, Alignment.Start)
        },
        content = content,
    )
}
