/*
 * 移植自 Kyant 的 AndroidLiquidGlass（Apache-2.0）
 * https://github.com/Kyant0/AndroidLiquidGlass
 * 参照：app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidButton.kt
 *
 * 与原文的差别只有「几何」：原文写死 Capsule + 48dp 高、蓝色 tint；
 * 这里把形状 / 高度 / 颜色 / 边框做成参数，好套进 PCL 的 35dp 方角按钮。
 * 液态那部分 —— pressProgress 驱动的整体缩放、朝手指方向的形变、
 * 以及跟着手指走的镜面高光（InteractiveHighlight）—— 与源码一致。
 */

package com.zalith.pcl.ui.pcl2.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 会「流动」的按钮表面。
 *
 * @param tint 按钮状态色（普通 / 高亮 / 危险），以 [tintAlpha] 叠在玻璃上，
 *             这样 PCL 的三种按钮配色仍然分得出来。
 * @param border 边框颜色与粗细（PCL 的 `MyButton` 有一圈 1dp 描边）
 */
@Composable
fun PclLiquidButtonSurface(
    backdrop: Backdrop,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 35.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp),
    tint: Color = Color.White,
    tintAlpha: Float = 0.22f,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    highlightAlpha: Float = 0.62f,
    blurRadius: Dp = 5.dp,
    refractionHeight: Dp = 8.dp,
    refraction: Dp = 16.dp,
    shadowAlpha: Float = 0.10f,
    /** 文字是否居中（PCL 的按钮本来就是居中，玻璃外壳容易丢）。 */
    textCenter: Boolean = true,
    vibrancyEnabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    if (vibrancyEnabled) vibrancy()
                    blur(blurRadius.toPx())
                    if (shape is CornerBasedShape && refraction > 0.dp) {
                        lens(refractionHeight.toPx(), refraction.toPx())
                    }
                },
                // 环境高光，不画 1dp 硬边（浅底上那圈会被当成描边）
                highlight = {
                    Highlight(
                        width = 0.dp,
                        alpha = highlightAlpha * 0.4f,
                        style = com.kyant.backdrop.highlight.HighlightStyle.Ambient(
                            intensity = highlightAlpha * 0.5f,
                        ),
                    )
                },
                innerShadow = {
                    InnerShadow(radius = 16.dp, color = Color(0xFF9AA6B4), alpha = 0.14f)
                },
                shadow = {
                    Shadow(radius = 18.dp, color = Color(0xFF23324A), alpha = shadowAlpha)
                },
                onDrawSurface = {
                    if (tintAlpha > 0f) drawRect(tint.copy(alpha = tintAlpha))
                },
                // 原文的「按下去会朝手指方向鼓起来」全靠这一段 graphicsLayer
                layerBlock = if (enabled) {
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
                        scaleX = scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                        scaleY = scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
            )
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .border(borderWidth, borderColor, shape)
            .heightIn(min = minHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        // 必须显式居中：PCL 的 MyButton 文字是居中的，少了这一行就变成左对齐
        horizontalArrangement = if (textCenter) Arrangement.Center else Arrangement.Start,
        content = content,
    )
}
