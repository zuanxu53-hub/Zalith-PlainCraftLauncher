/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「软玻璃」—— 给**没有 RenderEffect** 的机器（Android 11 / API 30 及以下）用的渲染器。
 *
 * 为什么需要它：
 *   真玻璃的整套效果（`drawBackdrop` + `blur()` + `lens()`）建立在
 *   `RenderEffect` / `RuntimeShader` 上，这两个都是 Android 12 / 13 才有的系统能力。
 *   低于这条线的时候，`pclSurface` 只能退化成「上亮下暗的半透明色块 + 投影」——
 *   那是一块**不透明的白板**，不是玻璃：没有透视、没有反射、没有边缘光，
 *   叠在壁纸上就成了一排白条。
 *
 * 低版本的出路：
 *   既然拿不到系统的模糊，就**自己准备一张模糊过的背景**。
 *   背景层按主题的像素预算重画，再进行真正的高斯卷积。
 *   玻璃块用 BitmapShader 从预模糊底片中取样；画质不再靠缩小冒充模糊。
 *   底片只在背景、尺寸、模糊参数变化时生成，逐控件绘制不重复卷积。
 *
 * 玻璃的「材质感」靠三件事，缺一不可：
 *   ① 边缘的**方向性镜面光**（左上亮 → 右下暗）—— 液体的「液」来自这条不对称的边；
 *   ② 内侧顶部的一层**柔光**（光从上方打进玻璃厚度里）；
 *   ③ 内侧底部的一层**暗边**（玻璃的厚度，让边缘看起来是立体的而不是一张贴纸）。
 *
 * 折射（边缘把背景弯折）在低版本做不出逐像素位移，这里用「整块轻微放大」近似：
 * 玻璃里的背景比外面大一点点，眼睛会读成「透过一块厚玻璃看」。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 一张「整屏模糊背景」。
 *
 * 位图按主题画质降采样，玻璃块靠 [screenSize] 把自己的坐标映射进去。
 */
class PclSoftBackdrop(
    /** 经过高斯卷积的背景。 */
    val image: ImageBitmap,
    /** 生成时对应的窗口像素尺寸。 */
    val screenSize: IntSize,
)

/**
 * 低版本的玻璃表面。
 *
 * @param fill 表面色（通常是白色）；[fillAlpha] 为 0 时不叠色（顶栏那种「全透明玻璃」）
 * @param surface 决定参数怎么缩放：大卡片糊一点、按钮薄一点、面板几乎不折射
 */
fun Modifier.pclSoftGlass(
    theme: PclUiTheme,
    soft: PclSoftBackdrop,
    shape: Shape,
    surface: PclSurface,
    fill: Color,
    fillAlpha: Float,
): Modifier = composed {
    // 位置的「槽」：onGloballyPositioned 写、绘制时读。
    // 用 FloatArray 而不是 State —— 位置每帧都可能变（滚动），
    // 走 State 会把整棵子树重组一遍，这里只要重绘就够。
    val pos = remember { FloatArray(2) }
    // 弹窗那类独立窗口的内容原点不在屏幕左上角，取底片时要补上这段偏移。
    val screenOffset = LocalPclScreenOffset.current

    val g = theme.glass
    val bendScale = when (surface) {
        PclSurface.Card -> 1f
        PclSurface.Control -> 0.55f
        PclSurface.Panel -> 0.3f
    }
    // 折射近似：整块放大 1.4%~2.4%，面板几乎不放大（大面积放大看着会晕）
    val bend = 1f + (g.refraction / 1600f) * bendScale
    val rimAlpha = g.highlight.coerceIn(0f, 1f)
    val rimWidth = g.highlightWidth.dp
    val glow = g.edgeGlow.coerceIn(1f, 8f)
    val haze = g.haze.coerceIn(0f, 0.5f)

    this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            pos[0] = p.x
            pos[1] = p.y
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val androidClip = clip.asAndroidPath()

            val src = soft.image.asAndroidBitmap()
            // 位图一个像素 = 屏幕上多少个像素
            val scaleX = soft.screenSize.width.toFloat() / src.width.toFloat()
            val scaleY = soft.screenSize.height.toFloat() / src.height.toFloat()

            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val glassPaint = Paint().apply {
                isAntiAlias = true
                isFilterBitmap = true
                this.shader = shader
            }
            val matrix = Matrix()

            // ① 镜面边：沿描边做一次 45° 的线性渐变 —— 左上白、右下透明。
            //    这是「液态玻璃」和「磨砂塑料」的分界线，宁可淡也不能没有。
            val rimShader = LinearGradient(
                0f, 0f, size.width, size.height,
                intArrayOf(
                    Color.White.copy(alpha = rimAlpha).toArgb(),
                    Color.White.copy(alpha = rimAlpha * 0.42f).toArgb(),
                    Color.White.copy(alpha = rimAlpha * 0.04f).toArgb(),
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP,
            )
            val rimPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = rimWidth.toPx()
                this.shader = rimShader
            }
            // ② 边缘散射：同一条渐变，画得更宽更淡，让光「散」出去而不是一条硬线
            val glowPaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = (rimWidth * glow).toPx()
                alpha = (rimAlpha * 90).toInt().coerceIn(0, 255)
                this.shader = rimShader
            }
            // ③ 内侧顶部柔光：光从上方打进玻璃厚度。
            //    高度必须**贴着边缘**（玻璃的厚度是固定的，不该随卡片变高），
            //    之前按 55% 高度铺，等于把整块玻璃的上半截刷白 —— 那又变回白条了。
            //    终点必须是「同色的透明」（White@0），用 Transparent（透明黑）会插出一片灰。
            val innerEdge = minOf(size.height * 0.34f, 18.dp.toPx())
            val topPaint = Paint().apply {
                isAntiAlias = true
                this.shader = LinearGradient(
                    0f, 0f, 0f, innerEdge,
                    Color.White.copy(alpha = (0.14f * rimAlpha).coerceIn(0f, 0.26f)).toArgb(),
                    Color.White.copy(alpha = 0f).toArgb(),
                    Shader.TileMode.CLAMP,
                )
            }
            // ④ 内侧底部暗边：玻璃的厚度，让边缘看起来是立体的而不是一张贴纸。
            //    只给卡片/控件画；面板（顶栏、侧栏）要的是「一整块连续玻璃」，
            //    底部暗边会在它贴着内容区的地方留下一条深色横线（正是用户截图里那根线）。
            val lowerEdge = minOf(size.height * 0.30f, 14.dp.toPx())
            val drawThicknessEdges = surface != PclSurface.Panel
            val bottomPaint = Paint().apply {
                isAntiAlias = true
                this.shader = LinearGradient(
                    0f, size.height, 0f, size.height - lowerEdge,
                    Color.Black.copy(alpha = 0.07f).toArgb(),
                    Color.Black.copy(alpha = 0f).toArgb(),
                    Shader.TileMode.CLAMP,
                )
            }
            val tintPaint = Paint().apply { isAntiAlias = true }
            val tintArgb = fill.copy(alpha = 1f).toArgb()

            onDrawBehind {
                var px = pos[0] + screenOffset.x
                var py = pos[1] + screenOffset.y
                if (!px.isFinite()) px = 0f
                if (!py.isFinite()) py = 0f

                // 先映射至窗口并移到控件原点，再绕控件自身中心折射。
                // 两轴单独还原整数采样尺寸，避免横竖屏高度累积偏差。
                matrix.setScale(scaleX, scaleY)
                matrix.postTranslate(-px, -py)
                matrix.postScale(bend, bend, size.width / 2f, size.height / 2f)
                shader.setLocalMatrix(matrix)

                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.clipPath(androidClip)
                canvas.drawRect(0f, 0f, size.width, size.height, glassPaint)

                // 光散射的雾：极淡的白，模拟介质内部的散射光
                if (haze > 0f) {
                    tintPaint.color = Color.White.toArgb()
                    tintPaint.alpha = (haze.coerceIn(0f, 1f) * 255).toInt()
                    canvas.drawRect(0f, 0f, size.width, size.height, tintPaint)
                }
                if (fillAlpha > 0f) {
                    tintPaint.color = tintArgb
                    tintPaint.alpha = (fillAlpha.coerceIn(0f, 1f) * 255).toInt()
                    canvas.drawRect(0f, 0f, size.width, size.height, tintPaint)
                }
                if (drawThicknessEdges) {
                    canvas.drawRect(0f, 0f, size.width, size.height, topPaint)
                    canvas.drawRect(0f, 0f, size.width, size.height, bottomPaint)
                }
                canvas.restore()

                // 边缘光画在裁剪之外：stroke 有一半落在形状外面，裁掉就只剩半条线
                if (rimAlpha > 0.01f) {
                    canvas.drawPath(androidClip, glowPaint)
                    canvas.drawPath(androidClip, rimPaint)
                }
            }
        }
}

/** 没有底片可用时的最后退路：淡到几乎看不见的一层色 + 一条亮边，至少不像白板。 */
fun Modifier.pclFadedGlass(
    shape: Shape,
    fill: Color,
    fillAlpha: Float,
    rimAlpha: Float,
): Modifier = composed {
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }
        val androidClip = clip.asAndroidPath()
        val fillPaint = Paint().apply { isAntiAlias = true }
        val rimPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 1.dp.toPx()
            shader = LinearGradient(
                0f, 0f, size.width, size.height,
                intArrayOf(
                    Color.White.copy(alpha = rimAlpha).toArgb(),
                    Color.White.copy(alpha = rimAlpha * 0.30f).toArgb(),
                    Color.White.copy(alpha = rimAlpha * 0.05f).toArgb(),
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        onDrawBehind {
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.clipPath(androidClip)
            fillPaint.color = fill.copy(alpha = fillAlpha.coerceIn(0f, 1f)).toArgb()
            canvas.drawRect(0f, 0f, size.width, size.height, fillPaint)
            canvas.restore()
            if (rimAlpha > 0.01f) canvas.drawPath(androidClip, rimPaint)
        }
    }
}
