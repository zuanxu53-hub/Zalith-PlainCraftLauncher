/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 的图标全部以内联 SVG Path 数据的形式写在其 XAML 里，本文件把用到的
 * 路径字符串【原样】复制过来，并用 Compose 的 PathParser 还原为 ImageVector，
 * 以保证图形与 PCL2 完全一致（一个控制点都没有改）。
 *
 * 对应来源：FormMain.xaml、Pages 与 Controls 下的各个 xaml。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 把 PCL2（WPF）的 Path Data 字符串解析成 [ImageVector]。
 *
 * WPF 的路径微语言允许在开头写填充规则 `F0` / `F1`，SVG 不允许，
 * 因此这里先把它剥离出来再交给 [PathParser]。
 *
 * **视口（viewport）由路径自身的紧密包围盒决定**，这是关键：
 * PCL 的 `MyListItem` 用的是 `Shapes.Path` + `Stretch=Uniform`
 * （`MyListItem.xaml.vb:250-256`），WPF 的 Uniform 是把**几何自身的包围盒**
 * 等比铺满可用区域，路径数据的绝对坐标大小完全不影响最终尺寸。
 * 如果这里给一个固定 viewport（例如把所有图标都塞进 1024×1024），
 * 那么路径只占视口的一部分、渲染出来就会明显偏小，而且每个图标偏小的
 * 幅度还不一样 —— 这正是「按钮大小不一致」的根因。
 *
 * 另外，个别 PCL 路径含负坐标（例如搜索图标 $x \in [-44.85, 1030.7]$），
 * 用包围盒做视口后需要把坐标平移到视口原点，否则会被裁掉。
 */
private fun pclVector(name: String, data: String): ImageVector {
    var d = data.trim()
    var fillType = PathFillType.NonZero
    if (d.startsWith("F1")) {
        d = d.removePrefix("F1").trim()
    } else if (d.startsWith("F0")) {
        fillType = PathFillType.EvenOdd
        d = d.removePrefix("F0").trim()
    }
    val nodes = runCatching { PathParser().parsePathString(d).toNodes() }.getOrDefault(emptyList())
    val bounds = nodes.tightBounds() ?: Rect(0f, 0f, 1024f, 1024f)
    val viewportWidth = bounds.width.coerceAtLeast(0.01f)
    val viewportHeight = bounds.height.coerceAtLeast(0.01f)

    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = viewportWidth.dp,
        defaultHeight = viewportHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    val draw: ImageVector.Builder.() -> Unit = {
        addPath(
            pathData = nodes,
            pathFillType = fillType,
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
        )
    }
    if (bounds.left != 0f || bounds.top != 0f) {
        builder.addGroup(translationX = -bounds.left, translationY = -bounds.top)
        builder.apply(draw)
        builder.clearGroup()
    } else {
        builder.apply(draw)
    }
    return builder.build()
}

/**
 * 计算一组路径节点的紧密包围盒（等价于 WPF `Geometry.Bounds`）。
 *
 * 直线/二次/三次贝塞尔取精确极值（解导数为零的点），
 * 圆弧（`A` / `a`）先按 SVG 规范 F.6.5 转成圆心参数式，再按 5° 步长采样。
 */
private fun List<PathNode>.tightBounds(): Rect? {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE

    val acc: (Float, Float) -> Unit = { x, y ->
        if (x.isFinite() && y.isFinite()) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }
    }

    var cx = 0f
    var cy = 0f
    // 上一个三次 / 二次曲线的第二控制点，供 S / T（反射）指令使用；null 表示没有
    var lastCubic: Pair<Float, Float>? = null
    var lastQuad: Pair<Float, Float>? = null

    for (node in this) {
        when (node) {
            is PathNode.MoveTo -> {
                cx = node.x; cy = node.y; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.RelativeMoveTo -> {
                cx += node.dx; cy += node.dy; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.LineTo -> {
                cx = node.x; cy = node.y; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.RelativeLineTo -> {
                cx += node.dx; cy += node.dy; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.HorizontalTo -> {
                cx = node.x; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.RelativeHorizontalTo -> {
                cx += node.dx; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.VerticalTo -> {
                cy = node.y; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.RelativeVerticalTo -> {
                cy += node.dy; acc(cx, cy); lastCubic = null; lastQuad = null
            }

            is PathNode.CurveTo -> {
                accCubic(cx, cy, node.x1, node.y1, node.x2, node.y2, node.x3, node.y3, acc)
                lastCubic = node.x2 to node.y2; lastQuad = null
                cx = node.x3; cy = node.y3
            }

            is PathNode.RelativeCurveTo -> {
                val x1 = cx + node.dx1; val y1 = cy + node.dy1
                val x2 = cx + node.dx2; val y2 = cy + node.dy2
                val x3 = cx + node.dx3; val y3 = cy + node.dy3
                accCubic(cx, cy, x1, y1, x2, y2, x3, y3, acc)
                lastCubic = x2 to y2; lastQuad = null
                cx = x3; cy = y3
            }

            is PathNode.ReflectiveCurveTo -> {
                val x1 = lastCubic?.let { 2f * cx - it.first } ?: cx
                val y1 = lastCubic?.let { 2f * cy - it.second } ?: cy
                accCubic(cx, cy, x1, y1, node.x1, node.y1, node.x2, node.y2, acc)
                lastCubic = node.x1 to node.y1; lastQuad = null
                cx = node.x2; cy = node.y2
            }

            is PathNode.RelativeReflectiveCurveTo -> {
                val x1 = lastCubic?.let { 2f * cx - it.first } ?: cx
                val y1 = lastCubic?.let { 2f * cy - it.second } ?: cy
                val x2 = cx + node.dx1; val y2 = cy + node.dy1
                val x3 = cx + node.dx2; val y3 = cy + node.dy2
                accCubic(cx, cy, x1, y1, x2, y2, x3, y3, acc)
                lastCubic = x2 to y2; lastQuad = null
                cx = x3; cy = y3
            }

            is PathNode.QuadTo -> {
                accQuad(cx, cy, node.x1, node.y1, node.x2, node.y2, acc)
                lastQuad = node.x1 to node.y1; lastCubic = null
                cx = node.x2; cy = node.y2
            }

            is PathNode.RelativeQuadTo -> {
                val x1 = cx + node.dx1; val y1 = cy + node.dy1
                val x2 = cx + node.dx2; val y2 = cy + node.dy2
                accQuad(cx, cy, x1, y1, x2, y2, acc)
                lastQuad = x1 to y1; lastCubic = null
                cx = x2; cy = y2
            }

            is PathNode.ReflectiveQuadTo -> {
                val x1 = lastQuad?.let { 2f * cx - it.first } ?: cx
                val y1 = lastQuad?.let { 2f * cy - it.second } ?: cy
                accQuad(cx, cy, x1, y1, node.x, node.y, acc)
                lastQuad = x1 to y1; lastCubic = null
                cx = node.x; cy = node.y
            }

            is PathNode.RelativeReflectiveQuadTo -> {
                val x1 = lastQuad?.let { 2f * cx - it.first } ?: cx
                val y1 = lastQuad?.let { 2f * cy - it.second } ?: cy
                val x2 = cx + node.dx; val y2 = cy + node.dy
                accQuad(cx, cy, x1, y1, x2, y2, acc)
                lastQuad = x1 to y1; lastCubic = null
                cx = x2; cy = y2
            }

            is PathNode.ArcTo -> {
                accArc(
                    cx, cy,
                    node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta,
                    node.isMoreThanHalf, node.isPositiveArc,
                    node.arcStartX, node.arcStartY,
                    acc,
                )
                lastCubic = null; lastQuad = null
                cx = node.arcStartX; cy = node.arcStartY
            }

            is PathNode.RelativeArcTo -> {
                val ex = cx + node.arcStartDx
                val ey = cy + node.arcStartDy
                accArc(
                    cx, cy,
                    node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta,
                    node.isMoreThanHalf, node.isPositiveArc,
                    ex, ey,
                    acc,
                )
                lastCubic = null; lastQuad = null
                cx = ex; cy = ey
            }

            else -> {
                // PathNode.Close：不做任何事（包围盒不因闭合而改变）
            }
        }
    }
    if (minX > maxX || minY > maxY) return null
    return Rect(minX, minY, maxX, maxY)
}

/** 三次贝塞尔：端点 + 两个方向上的解析极值点 + 采样兜底。 */
private fun accCubic(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float,
    acc: (Float, Float) -> Unit,
) {
    acc(x0, y0)
    acc(x3, y3)
    for (t in cubicExtrema(x0, x1, x2, x3)) acc(cubicAt(x0, x1, x2, x3, t), cubicAt(y0, y1, y2, y3, t))
    for (t in cubicExtrema(y0, y1, y2, y3)) acc(cubicAt(x0, x1, x2, x3, t), cubicAt(y0, y1, y2, y3, t))
    for (i in 1 until CURVE_SAMPLES) {
        val t = i.toFloat() / CURVE_SAMPLES
        acc(cubicAt(x0, x1, x2, x3, t), cubicAt(y0, y1, y2, y3, t))
    }
}

/** 二次贝塞尔：端点 + 解析极值点 + 采样兜底。 */
private fun accQuad(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    acc: (Float, Float) -> Unit,
) {
    acc(x0, y0)
    acc(x2, y2)
    for (t in quadExtrema(x0, x1, x2)) acc(quadAt(x0, x1, x2, t), quadAt(y0, y1, y2, t))
    for (t in quadExtrema(y0, y1, y2)) acc(quadAt(x0, x1, x2, t), quadAt(y0, y1, y2, t))
    for (i in 1 until CURVE_SAMPLES) {
        val t = i.toFloat() / CURVE_SAMPLES
        acc(quadAt(x0, x1, x2, t), quadAt(y0, y1, y2, t))
    }
}

/**
 * 圆弧：按 SVG 规范 F.6.5 把「端点参数式」转成「圆心参数式」后采样。
 *
 * @param thetaDeg x 轴旋转角（度）
 */
private fun accArc(
    x0: Float, y0: Float,
    rxIn: Float, ryIn: Float, thetaDeg: Float,
    largeArc: Boolean, sweep: Boolean,
    x1: Float, y1: Float,
    acc: (Float, Float) -> Unit,
) {
    acc(x0, y0)
    acc(x1, y1)
    if (rxIn == 0f || ryIn == 0f) return

    var rx = abs(rxIn).toDouble()
    var ry = abs(ryIn).toDouble()
    val phi = Math.toRadians(thetaDeg.toDouble())
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)

    val dx2 = (x0 - x1) / 2.0
    val dy2 = (y0 - y1) / 2.0
    val x1p = cosPhi * dx2 + sinPhi * dy2
    val y1p = -sinPhi * dx2 + cosPhi * dy2

    var rx2 = rx * rx
    var ry2 = ry * ry
    val lambda = x1p * x1p / rx2 + y1p * y1p / ry2
    if (lambda > 1.0) {
        val s = sqrt(lambda)
        rx *= s
        ry *= s
        rx2 = rx * rx
        ry2 = ry * ry
    }

    val den = rx2 * y1p * y1p + ry2 * x1p * x1p
    var coef = if (den <= 0.0) 0.0 else sqrt(max(0.0, (rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p) / den))
    if (largeArc == sweep) coef = -coef

    val cxp = coef * rx * y1p / ry
    val cyp = -coef * ry * x1p / rx
    val cx = cosPhi * cxp - sinPhi * cyp + (x0 + x1) / 2.0
    val cy = sinPhi * cxp + cosPhi * cyp + (y0 + y1) / 2.0

    val ux = (x1p - cxp) / rx
    val uy = (y1p - cyp) / ry
    val vx = (-x1p - cxp) / rx
    val vy = (-y1p - cyp) / ry

    val theta1 = atan2(uy, ux)
    var delta = atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    if (!sweep && delta > 0) delta -= 2 * PI
    if (sweep && delta < 0) delta += 2 * PI

    // 5° 一步，误差远小于 1 个视口单位
    val steps = max(8, ceil(abs(delta) / (PI / 36.0)).toInt())
    for (i in 0..steps) {
        val t = theta1 + delta * i / steps
        val px = cx + rx * cosPhi * cos(t) - ry * sinPhi * sin(t)
        val py = cy + rx * sinPhi * cos(t) + ry * cosPhi * sin(t)
        acc(px.toFloat(), py.toFloat())
    }
}

/**
 * 三次贝塞尔在 (0,1) 内的参数极值点。
 *
 * ⚠️ 这里必须用 Double 计算，而且退化判定必须用**相对**阈值。
 *
 * 对称圆弧（`x0 == x3` 且 `x1 == x2`，圆角图标里到处都是）在数学上 `a == 0`，
 * 用 Float 逐项累加却会残留约 ±1 ULP —— 134.54 这样的量级下就是 1.5e-5，
 * 轻松越过原先 `1e-6` 的**绝对**阈值，于是走进求根公式、除以一个极小的 a，
 * 得到 ±2e7 这种假根，真正的极值点 t = 0.5 反倒被丢掉。
 *
 * 后果是包围盒某一边偏窄：`MySearchBox` 的放大镜实测宽 888.59 而高 1023.18，
 * `Stretch="Uniform"` 会按这个错误长宽比缩放，图标既变形、又被 Compose
 * `Icon` 的 `clipToBounds` 把多出来的那一条边切掉（「放大镜被切了一节」）。
 */
private fun cubicExtrema(p0: Float, p1: Float, p2: Float, p3: Float): List<Float> {
    val x0 = p0.toDouble()
    val x1 = p1.toDouble()
    val x2 = p2.toDouble()
    val x3 = p3.toDouble()
    // 导数 = 3(a·t² + b·t + c)
    val a = -x0 + 3.0 * x1 - 3.0 * x2 + x3
    val b = 2.0 * (x0 - 2.0 * x1 + x2)
    val c = x1 - x0
    val scale = maxOf(abs(a), abs(b), abs(c), 1.0)

    val out = ArrayList<Float>(2)
    if (abs(a) <= 1e-12 * scale) {
        // 退化成一次方程（对称圆弧就是这种情形）
        if (abs(b) > 1e-12 * scale) {
            val t = -c / b
            if (t > 0.0 && t < 1.0) out.add(t.toFloat())
        }
        return out
    }
    val disc = b * b - 4.0 * a * c
    if (disc < 0.0) return out
    val s = sqrt(disc)
    // 数值稳定的两根：先算 q 再取 q/a 与 c/q，绕开 (-b ± s) 的相消
    val q = -0.5 * (b + if (b >= 0.0) s else -s)
    val roots = doubleArrayOf(q / a, if (q == 0.0) Double.NaN else c / q)
    for (t in roots) {
        if (t.isFinite() && t > 0.0 && t < 1.0) out.add(t.toFloat())
    }
    return out
}

/** 二次贝塞尔在 (0,1) 内的参数极值点。同样用 Double + 相对阈值。 */
private fun quadExtrema(p0: Float, p1: Float, p2: Float): List<Float> {
    val x0 = p0.toDouble()
    val x1 = p1.toDouble()
    val x2 = p2.toDouble()
    val den = x0 - 2.0 * x1 + x2
    val scale = maxOf(abs(den), abs(x0 - x1), 1.0)
    if (abs(den) <= 1e-12 * scale) return emptyList()
    val t = (x0 - x1) / den
    return if (t > 0.0 && t < 1.0) listOf(t.toFloat()) else emptyList()
}

/**
 * 曲线采样兜底的点数。
 *
 * 解析极值本身已经算对了，但这条链上任何一次浮点退化都可能再漏掉一个极值点，
 * 而「包围盒偏小」的代价是图标被整体缩放 + 裁边，很难靠截图逐个排查。
 * 采样点都落在曲线上，只会把包围盒撑到更接近真值、永远不会超出真值，所以是安全的保险。
 * 1/32 分段的矢高误差约 r·(1 - cos(1.4°)) ≈ 0.0003r，可以忽略。
 */
private const val CURVE_SAMPLES = 32

private fun cubicAt(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val u = 1f - t
    return u * u * u * p0 + 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t * p3
}

private fun quadAt(p0: Float, p1: Float, p2: Float, t: Float): Float {
    val u = 1f - t
    return u * u * p0 + 2f * u * t * p1 + t * t * p2
}

/** 描边型矢量（PCL 标题栏 logo 用的是 Stroke 而非 Fill）。 */
private fun pclStrokeVector(
    name: String,
    data: String,
    viewportWidth: Float,
    viewportHeight: Float,
    strokeWidth: Float,
): ImageVector {
    val d = data.trim().removePrefix("F1").trim().removePrefix("F0").trim()
    val nodes = runCatching { PathParser().parsePathString(d).toNodes() }.getOrDefault(emptyList())
    return ImageVector.Builder(
        name = name,
        defaultWidth = viewportWidth.dp,
        defaultHeight = viewportHeight.dp,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    ).apply {
        addPath(
            pathData = nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
        )
    }.build()
}

/**
 * 同 [pclStrokeVector]，但**视口由路径自身的紧密包围盒推导**（并把坐标平移到视口原点）。
 *
 * 用于几何坐标不落在视口原点的图元 —— 例如 `MyLoading.xaml` 的镐子，
 * 它的坐标跨 `x∈[65.1, 963.6]`、`y∈[49.5, 948.0]`，若直接以固定视口渲染会被整块裁掉。
 *
 * [strokeWidth] 是**最终渲染**的线条宽度（dp），内部会按包围盒高度换算成视口单位
 * （PCL 的 `StrokeThickness` 按设备像素计，不随 `Stretch=Uniform` 缩放）。
 *
 * @param renderHeight 该图元最终绘制的高度（dp），用来换算线宽
 */
private fun pclStrokeVectorAuto(
    name: String,
    data: String,
    strokeWidth: Float,
    renderHeight: Float,
    renderWidth: Float? = null,
): ImageVector {
    val d = data.trim().removePrefix("F1").trim().removePrefix("F0").trim()
    val nodes = runCatching { PathParser().parsePathString(d).toNodes() }.getOrDefault(emptyList())
    val bounds = nodes.tightBounds() ?: Rect(0f, 0f, 1024f, 1024f)
    val vw = bounds.width.coerceAtLeast(0.01f)
    val vh = bounds.height.coerceAtLeast(0.01f)
    // 渲染尺寸按 WPF `Stretch=Uniform`：给定高度时宽度按包围盒长宽比推得
    val outH = renderHeight
    val outW = renderWidth ?: (renderHeight * vw / vh)
    val viewportStroke = strokeWidth * (vh / outH)
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = outW.dp,
        defaultHeight = outH.dp,
        viewportWidth = vw,
        viewportHeight = vh,
    )
    val draw: ImageVector.Builder.() -> Unit = {
        addPath(
            pathData = nodes,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = viewportStroke,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Bevel,
        )
    }
    if (bounds.left != 0f || bounds.top != 0f) {
        builder.addGroup(translationX = -bounds.left, translationY = -bounds.top)
        builder.apply(draw)
        builder.clearGroup()
    } else {
        builder.apply(draw)
    }
    return builder.build()
}

/**
 * PCL2 全部图标。
 *
 * 每个图标都标注了它在 PCL2 里的出处，方便逐项比对。
 */
object PclIcons {

    /** 视口由路径自身的紧密包围盒推导（见 [pclVector]），因此不再需要手写尺寸。 */
    private fun v(name: String, d: String) = lazy { pclVector(name, d) }

    // ---------------------------------------------------------------- 标题栏

    /** `FormMain.xaml` → ShapeTitleLogo（PCL 三个字母的描边轮廓）。 */
    val titleLogo by lazy {
        pclStrokeVector(
            "PclTitleLogo",
            "M26,29 v-25 h6 a7,7 180 0 1 0,14 h-6 M62.5,6.5 a10,11.5 180 1 0 0,18 M71,2.5 v24.5 h13.5",
            viewportWidth = 85f,
            viewportHeight = 29f,
            strokeWidth = 2.2f,
        )
    }

    /** 标题栏「关闭」，`MyIconButton BtnTitleClose`。 */
    val close by v(
        "PclClose",
        "F1 M2,0 L0,2 8,10 0,18 2,20 10,12 18,20 20,18 12,10 20,2 18,0 10,8 2,0Z",
    )

    // ---------------------------------------------------------------- 加载动画
    // `MyLoading.xaml` 的四个图元。镐子是描边图（StrokeThickness=2、Height=35），
    // 三个小件是填充图（PathLeft / PathRight 的碎屑、PathError 的叉）。

    /**
     * `MyLoading.xaml` → `PathPickaxe`（镐子，描边 2、`Height=35`）。
     *
     * 坐标跨 `x∈[65.1,963.6]`、`y∈[49.5,948.0]`，所以要走自动包围盒版本。
     * 它是 `MyLoading` 那段循环动画的旋转对象（`AaRotateTransform(PathPickaxe, ...)`）。
     */
    val loadingPickaxe by lazy {
        pclStrokeVectorAuto(
            "PclLoadingPickaxe",
            "M 963.6 858.2 410.816 305.504 " +
                    "C 508.116 213.304 609.204 196.8 711.104 128.6 " +
                    "837.11367 49.573762 879.34045 50.334062 751.5 49.5 " +
                    "611.3 52 471.8 96.2 353.3 182.4 " +
                    "309.8 155.7 252.1 161.2 214.5 198.9 " +
                    "176.9 236.6 171.3 294.2 198 337.7 " +
                    "111.8 456.3 67.6 595.8 65.1 735.9 " +
                    "63.315254 883.82034 65.077966 837.29308 144.2 695.488 " +
                    "212.4 593.588 228.888 492.4 321.088 395.2 " +
                    "L 873.9 948 c 0.60001 0.59999 1.6 0.6 2.2 0 " +
                    "l 87.5 -87.5 c 0.6 -0.7 0.6 -1.6 0 -2.3 z",
            strokeWidth = 2f,
            renderHeight = 35f,
        )
    }

    /** `MyLoading.xaml` → `PathLeft` / `PathRight` 的碎屑小三角（`W=3 H=5`）。 */
    val loadingFragment by v(
        "PclLoadingFragment",
        "M0,0L2,0L1,4",
    )

    /** `MyLoading.xaml` → `PathError` 的叉（`W=17 H=17`）。 */
    val loadingError by v(
        "PclLoadingError",
        "F1 M2,0 L0,2 8,10 0,18 2,20 10,12 18,20 20,18 12,10 20,2 18,0 10,8 2,0Z",
    )

    /** 标题栏「最小化」，`MyIconButton BtnTitleMin`。 */
    val minimize by v(
        "PclMinimize",
        "F1 M0,0 h15 v2 h-15 v-2 Z",
    )

    /** 内页「返回」，`MyIconButton BtnTitleInner`。 */
    val back by v(
        "PclBack",
        "M1097 584 250 584 562 896C591 925 591 972 562 1001 533 1030 487 1030 458 1001L21 565C6 550-0 531 0 511L0 511 0 511C-0 492 6 472 21 457L458 21C487-7 533-7 562 21 591 50 591 97 562 126L250 438 1097 438C1137 438 1170 471 1170 511 1170 551 1137 584 1097 584L1097 584Z",
    )

    /** 指向右的箭头，`PageSetupLaunch.BtnSwitch`（版本独立设置）。 */
    val arrowRight by v(
        "PclArrowRight",
        "M73 584L920 584 608 896C579 925 579 972 608 1001 637 1030 683 1030 712 1001L1149 565C1164 550 1170 531 1170 511 1170 492 1164 472 1149 457L712 21C683-7 637-7 608 21 579 50 579 97 608 126L920 438 73 438C33 438 0 471 0 511 0 551 33 584 73 584Z",
    )

    // ---------------------------------------------------------------- 顶部导航

    /** `BtnTitleSelect0` 主页。 */
    val navHome by v(
        "PclNavHome",
        "M52.1,164.5c-1.4,0-3.1-0.5-4.2-1.3c-2.6-1.7-4-4.2-4-7V43.8c0-2.9,1.6-5.8,4.1-7c1.2-0.8,2.7-1.2,4.1-1.2c1.5,0,2.9,0.4,4.2,1.2L153.1,93c0,0,0.1,0,0.1,0.1c2.6,1.7,4,4.2,4,7c0,3-1.7,5.8-4.2,7.1l-96.8,56.2C55.1,164,53.5,164.5,52.1,164.5z M60.4,142.1l72.1-42.1L60.4,58.2V142.1z",
    )

    /** `BtnTitleSelect1` 下载。 */
    val navDownload by v(
        "PclNavDownload",
        "M955 610h-59c-15 0-29 13-29 29v196c0 15-13 29-29 29h-649c-15 0-29-13-29-29v-196c0-15-13-29-29-29h-59c-15 0-29 13-29 29V905c0 43 35 78 78 78h787c43 0 78-35 78-78V640c0-15-13-29-29-29zM492 740c11 11 29 11 41 0l265-265c11-11 11-29 0-41l-41-41c-11-11-29-11-41 0l-110 110c-11 11-33 3-33-13V68C571 53 555 39 541 39h-59c-15 0-29 13-29 29v417c0 17-21 25-33 13l-110-110c-11-11-29-11-41 0L226 433c-11 11-11 29 0 41L492 740z",
    )

    /** `BtnTitleSelect2` 联机。 */
    val navLink by v(
        "PclNavLink",
        "M512 817c-48.601 0-88-39.399-88-88s39.399-88 88-88 88 39.399 88 88-39.399 88-88 88zM237.671 565.74C308.335 474.58 397.369 429 504.774 429c118.433 0 214.225 55.421 287.377 166.264l-53.407 30.369c-13.84 7.87-31.362 4.367-41.114-8.219-50.291-64.911-114.577-97.367-192.856-97.367-86.851 0-156.835 40.318-209.95 120.953l-47.995-28.02c-15.263-8.91-20.412-28.507-11.502-43.77a32 32 0 0 1 2.344-3.47zM107.691 419.47C205.24 278.491 337.805 208 505.379 208c178.77 0 317.694 80.224 416.772 240.672l-56.54 31.73c-13.686 7.68-30.922 4.303-40.697-7.975C735.581 360.213 629.07 303.009 505.38 300.815 373.997 298.485 261.637 362.88 168.3 494l-50.116-28.505c-15.362-8.738-20.732-28.275-11.994-43.637a32 32 0 0 1 1.5-2.387z",
    )

    /** `BtnTitleSelect3` 设置。 */
    val navSetup by v(
        "PclNavSetup",
        "M940.4 463.7L773.3 174.2c-17.3-30-49.2-48.4-83.8-48.4H340.2c-34.6 0-66.5 18.5-83.8 48.4L89.2 463.7c-17.3 30-17.3 66.9 0 96.8L256.4 850c17.3 30 49.2 48.4 83.8 48.4h349.2c34.6 0 66.5-18.5 83.8-48.4l167.2-289.5c17.3-29.9 17.3-66.8 0-96.8z m-94.6 96.8L725.9 768.1c-17.3 30-49.2 48.4-83.8 48.4H387.5c-34.6 0-66.5-18.5-83.8-48.4L183.9 560.5c-17.3-30-17.3-66.9 0-96.8l119.8-207.5c17.3-30 49.2-48.4 83.8-48.4h254.6c34.6 0 66.5 18.5 83.8 48.4l119.8 207.5c17.3 30 17.3 66.9 0.1 96.8z M522.3 321.2c-2.5-0.1-5-0.2-7.5-0.2-119.9 0-214 110.3-186.3 235 15.8 70.9 71.5 126.6 142.4 142.4 17.5 3.9 34.7 5.4 51.4 4.7 102.1-3.9 183.6-87.9 183.6-191 0.1-103-81.5-187-183.6-190.9z m68.6 269.1c-18.5 18-43 28.9-68.6 30.7l-6 0.3c-30.2 0.4-58.6-11.4-79.7-33-19.5-20.1-30.7-47-30.9-75-0.3-29.6 11.1-57.4 32-78.3 20.6-20.6 48-32 77.2-32 2.5 0 5 0.1 7.5 0.3 26.7 1.8 51.5 13.2 70.5 32.5 19.6 20 30.8 46.9 31.2 74.9 0.2 30.2-11.5 58.6-33.2 79.6z",
    )

    /** `BtnTitleSelect4` 更多。 */
    val navOther by v(
        "PclNavOther",
        "M364 0h-273C40 0 0 40 0 91v273C0 414 40 455 91 455h273C414 455 455 414 455 364V91C455 40 414 0 364 0zM341 341H113V113h227v227zM933 0h-273C609 0 568 40 568 91v273c0 50 40 91 91 91h273C983 455 1024 414 1024 364V91c0-50-40-91-90-91zM910 341h-227V113h227v227zM364 568h-273C40 568 0 609 0 659v273c0 50 40 91 91 91h273C414 1024 455 983 455 932v-273C455 609 414 568 364 568zM341 910H113v-227h227v227zM933 568h-273c-50 0-91 40-91 91v273c0 50 40 91 91 91h273c50 0 90-40 90-91v-273c0-50-40-90-90-90zM910 910h-227v-227h227v227z",
    )

    // ---------------------------------------------------------------- 版本管理左栏

    /** `PageInstanceLeft.ItemOverall` 概览。 */
    val instanceOverview by v(
        "PclInstanceOverview",
        "M12 0l-11 6v12.131l11 5.869 11-5.869v-12.066l-11-6.065zm-1 21.2l-6.664-3.555 4.201-2.801c1.08-.719-.066-2.359-1.243-1.575l-4.294 2.862v-7.901l8 4.363v8.607zm-6.867-14.63l6.867-3.746v4.426c0 1.323 2 1.324 2 0v-4.415l6.91 3.811-7.905 4.218-7.872-4.294zm8.867 6.03l8-4.269v7.8l-4.263-2.842c-1.181-.785-2.323.855-1.245 1.574l4.172 2.781-6.664 3.556v-8.6z",
    )

    /** `PageInstanceLeft.ItemSetup` 设置（齿轮）。 */
    val instanceSetup by v(
        "PclInstanceSetup",
        "M1000 241c-2-5-4-11-6-17-0-6-4-12-8-17-7-9-18-15-31-15-6 0-13 1-19 5l-15 15-17 17-21 21L749 381c-14 14-38 14-52 0l-54-54c-14-14-14-38 0-52l132-132 14-14 20-20 16-16c3-5 5-11 5-19 0-12-5-24-16-32-5-4-11-6-18-7-5-2-10-4-16-5-36-12-76-19-117-19-7 0-14 0-22 0-188 11-337 168-337 360 0 16 0 32 3 48L56 663c-73 74-73 193 0 267l33 33c73 73 193 73 267 0l240-241c21 4 42 5 64 5 190 0 346-146 360-333 0-8 0-17 0-27 0-44-6-86-21-125zM658 639c-28 0-54-4-79-11-4 7-10 14-16 21l-258 258c-43 43-115 43-158 0l-29-29c-43-44-43-115 0-158l212-212 46-46c5-5 12-11 19-15-8-26-12-54-12-83v-1c0-152 123-275 276-275 18 0 37 1 55 5L597 207c-52 52-52 137 0 189l29 29c52 52 137 52 189 0L930 312c2 16 4 33 4 50 0 152-123 276-276 276z",
    )

    /** `PageInstanceLeft.ItemMod` / `ItemModDisabled` Mod 管理。 */
    val instanceMod by v(
        "PclInstanceMod",
        "M789.504 912.896h-195.072a35.328 35.328 0 0 1-34.816-35.328v-77.312a39.936 39.936 0 0 0-40.448-39.936H481.28a40.448 40.448 0 0 0-40.448 39.936v77.312a34.816 34.816 0 0 1-34.816 35.328H204.8a41.984 41.984 0 0 1-40.448-42.496v-200.192a35.328 35.328 0 0 1 35.328-35.328h72.704a39.936 39.936 0 0 0 39.936-39.936v-37.888a39.936 39.936 0 0 0-39.936-40.448H199.68a35.328 35.328 0 0 1-35.328-35.328V287.744A41.984 41.984 0 0 1 204.8 245.76h176.64v-32.768a102.4 102.4 0 0 1 102.4-102.4h33.792a102.4 102.4 0 0 1 102.4 102.4v32.768h170.496a41.984 41.984 0 0 1 41.984 41.984V460.8h28.672a102.4 102.4 0 0 1 102.4 102.4v33.792a102.4 102.4 0 0 1-102.4 102.4h-28.672V870.4a41.984 41.984 0 0 1-43.008 42.496z m-159.744-70.144h131.584V665.6a34.304 34.304 0 0 1 34.816-34.816h64a31.744 31.744 0 0 0 31.744-31.744V563.2a31.744 31.744 0 0 0-31.744-31.744h-64a34.816 34.816 0 0 1-34.816-35.328V316.416h-177.152a35.328 35.328 0 0 1-35.328-35.328V212.992a31.744 31.744 0 0 0-31.744-31.744h-33.792a31.744 31.744 0 0 0-31.744 31.744v68.096a35.328 35.328 0 0 1-34.816 35.328H234.496v130.048h37.888a110.592 110.592 0 0 1 110.08 110.592v37.888a110.592 110.592 0 0 1-110.08 110.592h-37.888v137.216h136.192v-42.496a110.592 110.592 0 0 1 110.592-110.08h37.888a110.592 110.592 0 0 1 110.592 110.08z",
    )

    /** `PageInstanceLeft.ItemExport` 导出。 */
    val instanceExport by v(
        "PclInstanceExport",
        "M511 995a128 128 0 0 1-57-13L70 791a126 126 0 0 1-70-113V311a126 126 0 0 1 15-60V248c1-2 3-5 5-8a127 127 0 0 1 49-42L454 13a128 128 0 0 1 112 0l383 190a126 126 0 0 1 72 113v360a126 126 0 0 1-70 115L568 984c-17 7-37 11-57 11z m42-470v370l360-178c14-7 23-21 23-38v-335L554 524zM85 330v347a42 42 0 0 0 23 38l360 178V523L85 330zM135 260l375 189 137-65L286 188 135 260z m245-118l363 197 150-71-365-180a42 42 0 0 0-37 0l-111 53z",
    )

    // ------------------------------------------------------------ 「游戏资源」分组（第 116 轮）
    //
    // PCL CE 在 `PageInstanceLeft` 里多出一组「游戏资源」：存档 / 截图 / 模组 / 资源包 /
    // 光影包 / 投影原理图 / 服务器。模组与资源包、光影包继续沿用下载页那三枚图标
    // （`instanceMod` / `dlResourcePack` / `dlShader`），剩下的四枚在这里补：
    // 前三个直接取原生 `res/drawable` 的同款 Material Symbols 路径，
    // 最后一个「投影原理图」原生没有现成资源，按同样的 960 视口手写了一个等轴立方体。

    /** 左栏「游戏资源 → 存档」。原生 `ic_public`（地球：Minecraft 把存档叫「世界」）。 */
    val instanceSaves by v(
        "PclInstanceSaves",
        "M324,848.5Q251,817 197,763t-85.5,-127Q80,563 80,480t31.5,-156Q143,251 197,197t127,-85.5Q397,80 480,80t156,31.5Q709,143 763,197t85.5,127Q880,397 880,480t-31.5,156Q817,709 763,763t-127,85.5Q563,880 480,880t-156,-31.5ZM440,798v-78q-33,0 -56.5,-23.5T360,640v-40L168,408q-3,18 -5.5,36t-2.5,36q0,121 79.5,212T440,798ZM716,696q41,-45 62.5,-100.5T800,480q0,-98 -54.5,-179T600,184v16q0,33 -23.5,56.5T520,280h-80v80q0,17 -11.5,28.5T400,400h-80v80h240q17,0 28.5,11.5T600,520v120h40q26,0 47,15.5t29,40.5Z",
    )

    /** 左栏「游戏资源 → 截图」。原生 `ic_image_outlined`。 */
    val instanceScreenshot by v(
        "PclInstanceScreenshot",
        "M200,840q-33,0 -56.5,-23.5T120,760v-560q0,-33 23.5,-56.5T200,120h560q33,0 56.5,23.5T840,200v560q0,33 -23.5,56.5T760,840L200,840ZM200,760h560v-560L200,200v560ZM240,680h480L570,480 450,640l-90,-120 -120,160ZM200,760v-560,560Z",
    )

    /**
     * 左栏「游戏资源 → 投影原理图」。
     *
     * 原生工程里没有立方体类图标，这里按 `ic_public` 同样的 960 视口手写一个等轴立方体：
     * 顶面是一个菱形，左右两个侧面各是一块平行四边形，三块之间留 20 个单位的缝，
     * 于是纯填充渲染出来仍然能看清三条棱（不是一坨实心）。
     */
    val instanceSchematic by v(
        "PclInstanceSchematic",
        "M480,140 750,295 480,450 210,295Z M210,325 460,468 460,750 210,607Z M500,468 750,325 750,607 500,750Z",
    )

    /** 左栏「游戏资源 → 服务器」。原生 `ic_dns_outlined`（两排机架）。 */
    val instanceServer by v(
        "PclInstanceServer",
        "M300,240q-25,0 -42.5,17.5T240,300q0,25 17.5,42.5T300,360q25,0 42.5,-17.5T360,300q0,-25 -17.5,-42.5T300,240ZM300,640q-25,0 -42.5,17.5T240,700q0,25 17.5,42.5T300,760q25,0 42.5,-17.5T360,700q0,-25 -17.5,-42.5T300,640ZM160,120h640q17,0 28.5,11.5T840,160v280q0,17 -11.5,28.5T800,480L160,480q-17,0 -28.5,-11.5T120,440v-280q0,-17 11.5,-28.5T160,120ZM200,200v200h560v-200L200,200ZM160,520h640q17,0 28.5,11.5T840,560v280q0,17 -11.5,28.5T800,880L160,880q-17,0 -28.5,-11.5T120,840v-280q0,-17 11.5,-28.5T160,520ZM200,600v200h560v-200L200,600ZM200,200v200,-200ZM200,600v200,-200Z",
    )

    // ---------------------------------------------------------------- 版本列表图标按钮
    // 全部来自 `Modules/Base/ModBase.vb` 的 Logo.IconButton*，用于 MyIconButton（宽 16 见方）。

    /** `Logo.IconButtonLikeFill` —— 已收藏（实心）。 */
    val likeFill by v(
        "PclLikeFill",
        "M700.856 155.543c-74.769 0-144.295 72.696-190.046 127.26-45.737-54.576-115.247-127.26-190.056-127.26-134.79 0-244.443 105.78-244.443 235.799 0 77.57 39.278 131.988 70.845 175.713C238.908 694.053 469.62 852.094 479.39 858.757c9.41 6.414 20.424 9.629 31.401 9.629 11.006 0 21.998-3.215 31.398-9.63 9.782-6.662 240.514-164.703 332.238-291.701 31.587-43.724 70.874-98.143 70.874-175.713-0.001-130.02-109.656-235.8-244.445-235.8z m0 0",
    )

    /** `Logo.IconButtonLikeLine` —— 未收藏（空心）。 */
    val likeLine by v(
        "PclLikeLine",
        "M512 896a42.666667 42.666667 0 0 1-30.293333-12.373333l-331.52-331.946667a224.426667 224.426667 0 0 1 0-315.733333 223.573333 223.573333 0 0 1 315.733333 0L512 282.026667l46.08-46.08a223.573333 223.573333 0 0 1 315.733333 0 224.426667 224.426667 0 0 1 0 315.733333l-331.52 331.946667A42.666667 42.666667 0 0 1 512 896zM308.053333 256a136.533333 136.533333 0 0 0-97.28 40.106667 138.24 138.24 0 0 0 0 194.986666L512 792.746667l301.226667-301.653334a138.24 138.24 0 0 0 0-194.986666 141.653333 141.653333 0 0 0-194.56 0l-76.373334 76.8a42.666667 42.666667 0 0 1-60.586666 0L405.333333 296.106667A136.533333 136.533333 0 0 0 308.053333 256z",
    )

    /** `Logo.IconButtonOpen` —— 打开文件夹。 */
    val openFolder by v(
        "PclOpenFolder",
        "M889.018182 418.909091H884.363636V316.509091a93.090909 93.090909 0 0 0-99.607272-89.832727h-302.545455l-93.090909-76.334546A46.545455 46.545455 0 0 0 358.865455 139.636364H146.152727A93.090909 93.090909 0 0 0 46.545455 229.469091V837.818182a46.545455 46.545455 0 0 0 46.545454 46.545454 46.545455 46.545455 0 0 0 16.756364-3.258181 109.381818 109.381818 0 0 0 25.134545 3.258181h586.472727a85.178182 85.178182 0 0 0 87.04-63.301818l163.374546-302.545454a46.545455 46.545455 0 0 0 5.585454-21.876364A82.385455 82.385455 0 0 0 889.018182 418.909091z m-744.727273-186.181818h198.283636l93.09091 76.334545a46.545455 46.545455 0 0 0 29.323636 10.705455h319.301818a12.101818 12.101818 0 0 1 6.516364 0V418.909091H302.545455a85.178182 85.178182 0 0 0-87.04 63.301818L139.636364 622.778182V232.727273a19.549091 19.549091 0 0 1 6.516363 0z m578.094546 552.029091a27.461818 27.461818 0 0 0-2.792728 6.516363H154.530909l147.083636-272.290909a27.461818 27.461818 0 0 0 2.792728-6.981818h565.061818z",
    )

    /** 版本选择页「新建 .minecraft 文件夹」的文件夹图标（PCL `PageSelectLeft.xaml.vb:95` 原样搬运）。 */
    val selectCreateFolder by v(
        "PclSelectCreateFolder",
        "M103.331925 384.978025l25.805736 0L129.137661 161.847132c0-18.313088 14.905478-33.718963 33.718963-33.718963l0.969071 0 253.006318 0c10.82044 0 20.218484 4.797259 26.500561 12.257162l117.579929 126.753869 297.819966 0c18.297738 0 33.736359 15.179724 33.736359 33.977859l0 0.952698 0 82.909292 25.547863 0c18.538215 0 34.187637 15.179724 34.187637 33.977859 0 2.163269-0.469698 3.617387-0.469698 5.539156l-54.437843 432.971086c-1.210571 10.382465-7.007601 19.056008-14.968923 24.352641-6.249331 5.765307-14.680351 9.624195-23.595394 9.624195l-0.969071 0-694.906773 0c-9.155521 0-17.344017-3.858888-23.626094-9.155521-8.67252-5.765307-14.453177-14.939247-15.389502-25.758664L69.597613 423.040922c-2.165316-18.313088 10.868535-35.414581 29.665647-38.062897L103.331925 384.978025 103.331925 384.978025zM196.576609 384.978025 196.576609 384.978025l627.938546 0 0-49.625234L546.461371 335.352791l0 0c-9.400091 0-18.329461-4.117784-25.048489-11.110035L402.363486 196.067514 196.576609 196.067514 196.576609 384.978025 196.576609 384.978025zM879.469767 452.916347 879.469767 452.916347l-20.267603 0-0.469698 0-0.969071 0-694.906773 0-0.984421 0-20.218484 0 45.781696 366.728382 646.218888 0L879.469767 452.916347 879.469767 452.916347z",
    )

    /** 版本选择页「添加已有文件夹」的圆圈加号（`PageSelectLeft.xaml.vb:103`）。 */
    val selectAddExistFolder by v(
        "PclSelectAddExistFolder",
        "M512.277 954.412c-118.89 0-230.659-46.078-314.73-129.73S67.12 629.666 67.12 511.222s46.327-229.744 130.398-313.427 195.82-129.73 314.73-129.73 230.659 46.078 314.72 129.73S957.397 392.81 957.397 511.183 911.078 740.96 826.97 824.642s-195.8 129.77-314.692 129.77z m0-822.784c-101.972 0-197.809 39.494-269.865 111.222s-111.7 166.997-111.7 268.373 39.653 196.695 111.67 268.335S410.246 890.78 512.248 890.78s197.809-39.484 269.865-111.222 111.7-166.998 111.67-268.374c-0.03-101.375-39.654-196.665-111.67-268.303S614.22 131.628 512.277 131.628z m222.585 347.8H544.073V288.64c-0.76-17.561-15.613-31.18-33.173-30.419-16.495 0.714-29.704 13.924-30.419 30.419v190.787H289.703c-17.56 0.761-31.179 15.614-30.419 33.174 0.715 16.494 13.924 29.703 30.42 30.418H480.48v190.788c0.761 17.56 15.614 31.179 33.174 30.419 16.494-0.715 29.703-13.925 30.418-30.42V543.02h190.788c17.56 0.762 32.413-12.857 33.173-30.418 0.762-17.561-12.858-32.414-30.419-33.174a31.683 31.683 0 0 0-2.753 0z",
    )

    /** 版本选择页「导入整合包」的圆圈包裹（`PageSelectLeft.xaml.vb:110`）。 */
    val selectAddModpack by v(
        "PclSelectAddModpack",
        "M512 40.96C249.344 40.96 35.84 252.416 35.84 512s213.504 471.04 476.16 471.04c103.424 0 202.752-33.28 286.72-96.256l1.536-1.536c5.12-5.632 7.68-12.8 7.68-19.968 0-16.896-13.824-30.208-30.72-30.208-7.68 0-15.36 2.56-20.992 7.68h-0.512c-71.68 52.224-155.648 79.36-243.712 79.36-227.328 0-412.16-182.784-412.16-407.552 0-224.768 184.832-407.552 412.16-407.552s412.16 182.784 412.16 407.552c0 68.608-15.872 132.608-46.592 190.464-0.512 1.024-1.024 2.048-1.024 3.072-0.512 2.048-1.536 4.608-1.536 8.192 0 16.896 13.824 30.208 30.72 30.208 12.288 0 23.04-7.168 28.16-18.432 35.84-68.608 53.76-141.312 53.76-216.064 0.512-259.584-212.992-471.04-475.648-471.04z M812.032 483.328c-31.744-20.992-71.68 1.536-78.848 6.144-1.024 0.512-104.448 61.44-128 74.752-8.192 4.608-22.528-0.512-27.136-4.096-31.232-36.352-54.272-70.656-68.608-102.4-13.312-29.184 0.512-41.472 3.072-43.52 7.168-4.608 114.688-68.608 143.36-83.456 24.064-12.288 40.96-25.088 46.08-45.056 3.072-13.312 0-27.136-9.216-39.936-22.016-31.744-172.544-84.992-311.296-3.584-157.184 91.648-152.064 242.688-150.528 292.352v9.216c0 18.944-12.8 37.376-14.848 40.448l-20.992 21.504c-6.144 6.144-9.216 13.824-9.216 22.528 0 8.704 3.584 16.384 9.728 22.528 12.8 12.288 32.768 11.776 45.056-0.512l22.528-23.552 0.512-0.512c3.072-3.584 30.208-38.4 30.208-81.92l-0.512-11.264c-1.536-44.544-5.632-162.816 119.296-235.52 88.064-51.2 173.056-32.256 208.896-19.968-36.864 19.456-143.36 83.456-144.896 84.48-22.016 14.336-55.808 58.88-26.112 122.88 17.408 37.376 43.52 76.8 80.896 120.32 14.336 17.408 62.976 37.376 103.424 15.36 24.576-13.312 125.44-73.216 130.048-75.776 2.048-1.024 4.608-2.56 7.68-3.584 0 2.56-0.512 6.144-1.024 10.752-5.632 35.84-35.328 155.136-191.488 181.76-49.664 8.704-89.6 3.584-121.856-0.512h-0.512c-37.888-4.608-73.216-9.216-101.888 14.336-31.232 26.112-40.96 34.304-35.84 54.272 3.584 14.336 16.384 24.064 30.72 24.064 2.56 0 5.12-0.512 7.68-1.024 6.656-1.536 12.8-5.632 16.896-10.752 2.048-2.048 7.68-6.656 20.992-18.432 6.656-5.632 25.088-3.584 52.736 0 34.816 4.608 81.92 10.24 141.312 0.512 157.184-26.624 228.864-138.752 243.2-234.496 7.68-38.912 0-64.512-21.504-78.336z",
    )

    /** `Logo.IconButtonDelete` —— 垃圾桶（版本列表的删除按钮用的就是这个，不是叉）。 */
    val deleteSolid by v(
        "PclDeleteSolid",
        "M520.192 0C408.43 0 317.44 82.87 313.563 186.734H52.736c-29.038 0-52.663 21.943-52.663 49.079s23.625 49.152 52.663 49.152h58.075v550.473c0 103.35 75.118 187.757 167.717 187.757h472.43c92.599 0 167.716-83.894 167.716-187.757V285.477h52.59c29.038 0 52.59-21.943 52.663-49.08-0.073-27.135-23.625-49.151-52.663-49.151H726.235C723.237 83.017 631.955 0 520.192 0zM404.846 177.957c3.803-50.03 50.176-89.015 107.447-89.015 57.197 0 103.57 38.985 106.788 89.015H404.92zM284.379 933.669c-33.353 0-69.997-39.351-69.997-95.525v-549.01H833.39v549.522c0 56.247-36.645 95.525-69.998 95.525H284.379v-0.512z M357.23 800.695a48.274 48.274 0 0 0 47.616-49.006V471.7a48.274 48.274 0 0 0-47.543-49.08 48.274 48.274 0 0 0-47.69 49.006V751.69c0 27.282 20.846 49.006 47.617 49.006z m166.62 0a48.274 48.274 0 0 0 47.688-49.006V471.7a48.274 48.274 0 0 0-47.689-49.08 48.274 48.274 0 0 0-47.543 49.006V751.69c0 27.282 21.431 49.006 47.543 49.006z m142.92 0a48.274 48.274 0 0 0 47.543-49.006V471.7a48.274 48.274 0 0 0-47.543-49.08 48.274 48.274 0 0 0-47.616 49.006V751.69c0 27.282 20.773 49.006 47.543 49.006z",
    )

    /** `Logo.IconButtonSetup` —— 齿轮（版本列表的「设置」按钮；与 instanceSetup 不是同一条路径）。 */
    val setupGear by v(
        "PclSetupGear",
        "M651.946667 1001.813333c-22.186667 0-42.666667-10.24-61.44-27.306666-23.893333-23.893333-49.493333-35.84-75.093334-35.84-29.013333 0-56.32 11.946667-73.386666 30.72v3.413333c-17.066667 17.066667-42.666667 27.306667-66.56 27.306667h-6.826667c-6.826667 0-11.946667-1.706667-15.36-1.706667l-6.826667-1.706667c-64.853333-20.48-121.173333-54.613333-168.96-98.986666-29.013333-23.893333-37.546667-63.146667-25.6-95.573334 8.533333-23.893333 5.12-51.2-10.24-75.093333-15.36-27.306667-34.133333-40.96-59.733333-47.786667h-1.706667l-5.12-1.706666c-35.84-8.533333-61.44-34.133333-66.56-69.973334C1.706667 575.146667 0 537.6 0 512c0-32.426667 3.413333-63.146667 8.533333-93.866667v-6.826666l3.413334-8.533334c10.24-23.893333 23.893333-40.96 44.373333-51.2 5.12-3.413333 11.946667-6.826667 20.48-8.533333 27.306667-8.533333 51.2-25.6 63.146667-44.373333 13.653333-23.893333 17.066667-52.906667 10.24-81.92-11.946667-34.133333 0-71.68 30.72-93.866667 44.373333-37.546667 97.28-68.266667 158.72-93.866667l3.413333-1.706666c44.373333-13.653333 75.093333 3.413333 92.16 20.48 23.893333 23.893333 49.493333 35.84 75.093333 35.84 30.72 0 56.32-10.24 71.68-30.72l3.413334-3.413334c27.306667-27.306667 63.146667-35.84 93.866666-22.186666 63.146667 22.186667 117.76 54.613333 165.546667 97.28 29.013333 23.893333 37.546667 63.146667 25.6 95.573333-8.533333 23.893333-5.12 51.2 10.24 75.093333 15.36 27.306667 34.133333 40.96 59.733333 47.786667h1.706667l5.12 1.706667c35.84 8.533333 61.44 34.133333 66.56 71.68 6.826667 30.72 10.24 63.146667 11.946667 93.866666v3.413334c0 32.426667-3.413333 63.146667-8.533334 93.866666v6.826667l-3.413333 8.533333c-10.24 23.893333-23.893333 40.96-44.373333 51.2-5.12 3.413333-11.946667 6.826667-20.48 8.533334-27.306667 8.533333-51.2 25.6-63.146667 46.08-13.653333 23.893333-17.066667 52.906667-10.24 81.92 11.946667 35.84-1.706667 75.093333-30.72 95.573333-44.373333 35.84-95.573333 66.56-157.013333 92.16-15.36 3.413333-27.306667 3.413333-35.84 3.413333z m3.413333-83.626666z m1.706667 0zM517.12 853.333333c47.786667 0 93.866667 20.48 134.826667 59.733334 1.706667 1.706667 3.413333 1.706667 3.413333 3.413333 52.906667-22.186667 97.28-49.493333 136.533333-80.213333l1.706667-1.706667v-3.413333c-13.653333-52.906667-8.533333-104.106667 17.066667-148.48 23.893333-39.253333 64.853333-69.973333 114.346666-85.333334 1.706667 0 3.413333-1.706667 6.826667-6.826666 5.12-25.6 8.533333-51.2 8.533333-78.506667-1.706667-29.013333-3.413333-56.32-10.24-81.92v-5.12h-1.706666c-51.2-11.946667-90.453333-39.253333-119.466667-87.04-27.306667-44.373333-34.133333-100.693333-17.066667-148.48l-1.706666-1.706667h-3.413334c-39.253333-35.84-85.333333-63.146667-136.533333-80.213333H648.533333s-1.706667 1.706667-3.413333 1.706667c-32.426667 39.253333-80.213333 59.733333-136.533333 59.733333-47.786667 0-93.866667-20.48-134.826667-59.733333l-1.706667-1.706667h-1.706666c-54.613333 22.186667-98.986667 49.493333-136.533334 80.213333l-1.706666 1.706667v3.413333c13.653333 52.906667 8.533333 104.106667-17.066667 148.48-23.893333 39.253333-64.853333 69.973333-114.346667 85.333334-1.706667 0-3.413333 1.706667-6.826666 6.826666-6.826667 25.6-8.533333 51.2-8.533334 78.506667 0 30.72 3.413333 58.026667 6.826667 76.8l1.706667 5.12h1.706666c51.2 11.946667 90.453333 39.253333 119.466667 87.04 27.306667 44.373333 34.133333 100.693333 17.066667 148.48l1.706666 1.706667 1.706667 1.706666c37.546667 35.84 83.626667 63.146667 134.826667 80.213334 1.706667 0 3.413333 0 3.413333 1.706666h1.706667s1.706667 0 5.12-1.706666c34.133333-37.546667 81.92-59.733333 136.533333-59.733334z m-6.826667-146.773333c-110.933333 0-199.68-85.333333-199.68-196.266667 0-109.226667 87.04-196.266667 199.68-196.266666s199.68 85.333333 199.68 196.266666c-1.706667 109.226667-88.746667 196.266667-199.68 196.266667z m0-307.2c-63.146667 0-114.346667 49.493333-114.346666 110.933333 0 63.146667 49.493333 110.933333 114.346666 110.933334 30.72 0 59.733333-11.946667 80.213334-32.426667 20.48-20.48 32.426667-49.493333 32.426666-78.506667 0-63.146667-49.493333-110.933333-112.64-110.933333z",
    )

    // ---------------------------------------------------------------- 下载左栏

    /** `PageDownloadLeft.ItemGame` 游戏 / Minecraft。 */
    val dlGame by v(
        "PclDlGame",
        "M445 545c18 0 33 14 33 33v149a182 182 0 1 1-182-182z m282 0a182 182 0 1 1-182 182v-149c0-18 14-33 33-33zM412 611H296a116 116 0 1 0 116 116V611z m315 0H611v116a116 116 0 1 0 116-116zM296 113a182 182 0 0 1 182 182v149a33 33 0 0 1-33 33H296a182 182 0 0 1 0-365z m431 0a182 182 0 0 1 0 365h-149a33 33 0 0 1-33-33V296A182 182 0 0 1 727 113zM296 180a116 116 0 0 0 0 232H412V296c0-62-48-112-109-115z m431 0a116 116 0 0 0-116 116V412h116c62 0 112-48 115-109l0-6a116 116 0 0 0-116-116z",
    )

    /** `PageDownloadLeft` 资源包。 */
    val dlResourcePack by v(
        "PclDlResourcePack",
        "M884.4 130.6H140.6c-41.9 0-76 34.1-76 76v613.3c0 41.9 34.1 76 76 76h743.8c41.9 0 76-34.1 76-76V206.6c0-41.9-34.1-76-76-76z m-743.8 72h743.8c2.2 0 4 1.8 4 4v371.3L796 461.7c-21.7-27.3-54.1-43.7-88.8-45-34.7-1.3-68.1 12.7-91.7 38.3L454 630c-2.8 3-7.2 3.5-10.5 1.3l-103-70.3c-31.5-21.5-72.8-22.5-105.3-2.7l-98.6 60.1V206.6c0-2.2 1.8-4 4-4z m743.8 621.3H140.6c-2.2 0-4-1.8-4-4V702.7l136.1-83c8.4-5.1 19.1-4.8 27.2 0.7l103 70.3c15.9 10.8 35.1 15.6 54.2 13.4 19-2.2 36.7-11.2 49.8-25.3l161.6-175c9.5-10.3 22.3-15.7 36.2-15.1 13.9 0.5 26.3 6.8 35.1 17.8l148.7 187v126.3c-0.1 2.3-1.9 4.1-4.1 4.1z M231.8 400.6a69.6 69.6 0 1 0 98.4-98.4 69.6 69.6 0 1 0-98.4 98.4Z",
    )

    /** `PageDownloadLeft` 光影。 */
    val dlShader by v(
        "PclDlShader",
        "M512 0c25 0 42 17 42 42v85c0 25-17 42-42 42s-42-17-42-42V42c0-25 17-42 42-42zM512 853c25 0 42 17 42 42v85c0 25-17 42-42 42s-42-17-42-42v-85c0-25 17-42 42-42zM209 149c-17-17-42-17-59 0-17 17-17 42 0 59l59 59c17 17 42 17 59 0 17-17 17-42 0-59L209 149zM755 755c17-17 42-17 59 0l59 59c17 17 17 42 0 59s-42 17-59 0l-59-59c-17-17-17-46 0-59zM42 469c-25 0-42 17-42 42s17 42 42 42h85c25 0 42-17 42-42s-17-42-42-42H42zM853 512c0-25 17-42 42-42h85c25 0 42 17 42 42s-17 42-42 42h-85c-25 0-42-17-42-42zM149 814c-17 17-17 42 0 59 17 17 42 17 59 0l59-59c17-17 17-42 0-59-17-17-42-17-59 0l-59 59zM755 268c-17-17-17-42 0-59l59-59c17-17 42-17 59 0s17 42 0 59l-59 59c-17 17-46 17-59 0z M512 213c-166 0-298 132-298 298s132 298 298 298 298-132 298-298-132-298-298-298z m0 512c-119 0-213-93-213-213s93-213 213-213 213 93 213 213-93 213-213 213z",
    )

    /** `PageDownloadLeft.ItemDataPack` 数据包（PCL 复用游戏方块图标）。 */
    val dlDataPack: ImageVector get() = dlGame

    // ---------------------------------------------------------------- 其他左栏

    /** `PageOtherLeft.ItemHelp` 帮助。 */
    val otherHelp by v(
        "PclOtherHelp",
        "M520.6 620.3c-11.3-0.6-20.1-4-26.9-10.5-6.6-6.3-8.1-11-10.1-20.8-1.9-9.5-1.5-16.7-1-24.9l0.3-5.3c0.5-9.9 3.5-19.6 8.8-28.1 5.7-9 12.5-17.2 20.8-25.1 8.6-8 17.7-15.7 27-22.9 9.4-7.2 18.7-14.8 27.7-22.7 9-7.9 16.2-15.8 22.1-24.3 6.2-9 9.3-18.8 9.3-29.4 1.2-20.3-6.5-37.7-22.8-51.3-16-13.3-37.4-20-63.5-20-13.7 0-26.3 3.3-37.4 10-10.9 6.5-20.2 14.5-27.7 23.8-7.5 9.2-13.5 19-17.9 29.2-4.5 10.6-6.7 19.4-6.7 26.8 0 9.2-3.3 16-10.1 20.8-6.9 4.8-14.6 7.3-22.8 7.3h-1.3c-9-0.3-17-3.3-24.6-9.2-7.3-5.6-11.1-14.6-11.6-27.5-0.6-11.5 1.9-26.1 7.2-43.5 5.4-17.4 14.8-34.4 28.1-50.5 13.4-16.3 31.2-30.4 52.8-41.8 21.6-11.4 49.2-17.2 81.9-17.2 26.4 0 49.6 3.9 69.1 11.7 19.4 7.7 35.4 18.2 47.5 31.1l1.6 1.7c11.8 12.6 20.3 21.7 29.4 44.4 11.6 28.9 7.1 52.1 5.5 58.5-5.8 22.6-12.6 37.7-22.7 50.4-11.7 14.6-24.9 28.2-39.2 40.4-14 11.7-39.2 33.3-39.2 33.3-13.3 11.4-19.4 22.1-18.6 32.9V587c0.5 8.7-2.4 16.1-8.8 22.8-6.2 6.4-14.8 10-26.2 10.5zM519 766.1c-13 0-23.6-4.2-32.2-12.9-8.7-8.7-13-19-13-31.4 0-12.9 4.2-23.5 12.9-32.2 8.7-8.7 19.2-12.9 32.2-12.9 13 0 23.5 4.2 32.2 12.9s12.9 19.2 12.9 32.2c0 12.4-4.2 22.7-12.9 31.4-8.6 8.6-19.2 12.9-32.1 12.9z M515 928.3c-228.1 0-413.7-185.6-413.7-413.7S286.9 100.9 515 100.9s413.7 185.6 413.7 413.7c0.1 228.1-185.5 413.7-413.7 413.7z m0-747.4c-184 0-333.7 149.7-333.7 333.7S331 848.3 515 848.3s333.7-149.7 333.7-333.7S699 180.9 515 180.9z",
    )

    /** `PageOtherLeft.ItemAbout` 关于。 */
    val otherAbout by v(
        "PclOtherAbout",
        "M149.883623 873.911618c47.094581 47.094581 101.765247 83.95121 162.783444 109.75085 63.065787 26.618676 130.226755 40.337532 199.230553 40.337532 69.003799 0 136.164767-13.514097 199.230554-40.337532 61.018196-25.79964 115.688862-62.656269 162.783444-109.75085s83.95121-101.765247 109.75085-162.783444c26.618676-63.065787 40.337532-130.226755 40.337532-199.230554 0-69.003799-13.514097-136.164767-40.337532-199.230553-25.79964-61.018196-62.656269-115.688862-109.75085-162.783444-46.889822-46.889822-101.765247-83.95121-162.783444-109.75085C648.062388 13.514097 580.90142 0 511.89762 0s-136.164767 13.514097-199.230553 40.337532c-61.018196 25.79964-115.688862 62.656269-162.783444 109.75085s-83.95121 101.765247-109.75085 162.783444C13.514097 375.732853 0 442.893821 0 511.89762s13.514097 136.164767 40.337532 199.230554c25.594881 61.018196 62.656269 115.688862 109.546091 162.783444zM97.670066 511.89762c0-228.511098 185.921216-414.227554 414.227554-414.227554 228.511098 0 414.227554 185.921216 414.227555 414.227554S740.408718 926.125175 511.89762 926.125175c-228.511098 0-414.227554-185.716457-414.227554-414.227555z M539.130574 244.277544c-8.190362-4.709458-17.609278-7.371326-27.232954-7.371325-9.623675 0-19.042591 2.457109-27.232953 7.371325-16.790242 9.623675-27.232953 27.847231-27.232953 47.299341 0 30.09958 24.366327 54.465907 54.465906 54.465906 30.09958 0 54.465907-24.366327 54.465907-54.465906 0-19.45211-10.442711-37.470906-27.232953-47.299341zM566.363527 732.218356v-284.615077c0-30.09958-24.366327-54.465907-54.465907-54.465906-30.09958 0-54.465907 24.366327-54.465906 54.465906v284.615077c0 30.09958 24.366327 54.465907 54.465906 54.465907 30.09958 0 54.465907-24.366327 54.465907-54.465907z",
    )

    /** `PageOtherLeft.ItemTest` 工具。 */
    val otherTools by v(
        "PclOtherTools",
        "M511 995a128 128 0 0 1-57-13L70 791a126 126 0 0 1-70-113V311a126 126 0 0 1 15-60V248c1-2 3-5 5-8a127 127 0 0 1 49-42L454 13a128 128 0 0 1 112 0l383 190a126 126 0 0 1 72 113v360a126 126 0 0 1-70 115L568 984c-17 7-37 11-57 11z m42-470v370l360-178c14-7 23-21 23-38v-335L554 524zM85 330v347a42 42 0 0 0 23 38l360 178V523L85 330zM135 260l375 189 137-65L286 188 135 260z m245-118l363 197 150-71-365-180a42 42 0 0 0-37 0l-111 53z",
    )

    /** `PageOtherLeft.ItemFeedback` 反馈。 */
    val otherFeedback by v(
        "PclOtherFeedback",
        "M613.717333 64.426667l3.413334 3.242666 331.861333 331.861334a85.333333 85.333333 0 0 1 3.2 117.269333l-3.2 3.413333L573.162667 896H960a21.333333 21.333333 0 0 1 21.333333 21.333333v42.666667a21.333333 21.333333 0 0 1-21.333333 21.333333H64a21.333333 21.333333 0 0 1-21.333333-21.333333v-42.666667a21.333333 21.333333 0 0 1 21.333333-21.333333h295.338667L74.069333 610.709333a85.333333 85.333333 0 0 1-3.221333-117.269333l3.2-3.413333 422.4-422.357334a85.333333 85.333333 0 0 1 117.269333-3.221333z m-20.629333 99.882666l-21.418667 321.493334a85.333333 85.333333 0 0 1-74.944 79.04l-4.522666 0.426666-321.493334 21.44 295.552 295.530667 422.4-422.378667L593.066667 164.309333z m-86.442667 13.824L184.533333 500.266667l301.994667-20.117334 20.117333-301.994666z",
    )

    /** `PageOtherLeft.ItemVote` 投票。 */
    val otherVote by v(
        "PclOtherVote",
        "M979 1024H45c-24.9 0-45-20.1-45-45s20.1-45 45-45h934c24.9 0 45 20.1 45 45s-20.1 45-45 45zM135 887C60.6 887 0 826.4 0 752V334c0-74.4 60.6-135 135-135s135 60.6 135 135v418c0 74.4-60.6 135-135 135z m0-598c-24.8 0-45 20.2-45 45v418c0 24.8 20.2 45 45 45s45-20.2 45-45V334c0-24.8-20.2-45-45-45zM512 887c-74.4 0-135-60.6-135-135V532c0-74.4 60.6-135 135-135s135 60.6 135 135v220c0 74.4-60.6 135-135 135z m0-400c-24.8 0-45 20.2-45 45v220c0 24.8 20.2 45 45 45s45-20.2 45-45V532c0-24.8-20.2-45-45-45zM889 887c-74.4 0-135-60.6-135-135V135C754 60.6 814.6 0 889 0s135 60.6 135 135v617c0 74.4-60.6 135-135 135z m0-797c-24.8 0-45 20.2-45 45v617c0 24.8 20.2 45 45 45s45-20.2 45-45V135c0-24.8-20.2-45-45-45z",
    )

    // ---------------------------------------------------------------- 设置左栏

    /** `PageSetupLeft.ItemLaunch` 游戏。 */
    val setupLaunch by v(
        "PclSetupLaunch",
        "M52.1,164.5c-1.4,0-3.1-0.5-4.2-1.3c-2.6-1.7-4-4.2-4-7V43.8c0-2.9,1.6-5.8,4.1-7c1.2-0.8,2.7-1.2,4.1-1.2c1.5,0,2.9,0.4,4.2,1.2L153.1,93c0,0,0.1,0,0.1,0.1c2.6,1.7,4,4.2,4,7c0,3-1.7,5.8-4.2,7.1l-96.8,56.2C55.1,164,53.5,164.5,52.1,164.5z M60.4,142.1l72.1-42.1L60.4,58.2V142.1z",
    )

    /** `PageSetupLeft.ItemLink` 联机。 */
    val setupLink = navLink

    /** `PageSetupLeft.ItemUI` 个性化。 */
    val setupUi by v(
        "PclSetupUi",
        "M764.0 0.0a215.4 215.4 0 0 0-76.2 13.9l-9.0 3.6c-49.1 20.7-88.5 30.5-115.4 30.5-27.0 0-66.4-9.8-116.6-31.0a217.5 217.5 0 0 0-72.1-16.7l-9.1-0.3-3.8-0.0-8.6 0.3-4.2 0.2A160.7 160.7 0 0 0 250.4 45.3l-219.3 212.4a102.3 102.3 0 0 0-11.2 134.2l106.3 144.5 3.3 4.3a102.4 102.4 0 0 0 112.8 31.6l5.6-2.1V883.2a140.7 140.7 0 0 0 140.7 140.7h372.1l5.6-0.1a140.7 140.7 0 0 0 135.1-140.6l-0.0-318.6 1.1 0.7a102.3 102.3 0 0 0 141.9-44.2l70.9-144.3a102.3 102.3 0 0 0-20.6-118.6L875.8 45.3A160.7 160.7 0 0 0 764.0 0.0z m-357.3 111.4C468.4 137.6 520.5 150.6 563.1 150.6c42.4 0 94.5-12.9 156.1-38.9A112.5 112.5 0 0 1 764.0 102.4c15.1 0 29.6 5.8 40.5 16.4L1023.9 331.4l-70.9 144.3-93.0-65.6a38.3 38.3 0 0 0-60.5 31.3V883.2a38.3 38.3 0 0 1-38.3 38.3H388.8a38.3 38.3 0 0 1-38.3-38.3V445.1a38.3 38.3 0 0 0-61.6-30.4l-80.0 61.2L102.3 331.3l219.3-212.4a58.3 58.3 0 0 1 35.5-16.2L362.1 102.4l6.3 0.1a115.1 115.1 0 0 1 38.2 8.8z",
    )

    /** `PageSetupLeft.ItemSystem` 启动器。 */
    val setupSystem = navOther

    // ---------------------------------------------------------------- 通用

    /** 刷新（`PageDownloadLeft` / `PageOtherLeft` 的 Refresh 按钮）。 */
    val refresh by v(
        "PclRefresh",
        "M512.0 838.3c-80.2 0-153.4-29.3-210.2-77.4l75.5-75.5c11.5-11.5 25.8-22.0 25.8-37.0a27.2 27.2 0 0 0-27.1-27.1H104.0c-27.1 0-27.1 23.9-27.1 27.1v271.9a27.1 27.1 0 0 0 27.1 27.1c15.0 0 27.8-16.6 42.5-31.2l77.9-77.9c76.6 67.7 177.1 108.9 287.4 108.9 221.7 0 404.5-166.0 431.2-380.6h-109.8c-25.9 154.2-159.7 271.9-321.3 271.9zM919.9 76.6c-15.0 0-27.8 16.6-42.5 31.3L799.5 185.8c-76.5-67.7-177.1-108.9-287.4-108.9-221.8 0-404.5 166.1-431.3 380.6H190.6c25.9-154.2 159.7-271.9 321.4-271.9 80.2 0 153.4 29.3 210.1 77.4l-75.5 75.5c-11.6 11.5-25.8 22.0-25.8 37.1a27.2 27.2 0 0 0 27.1 27.1h271.9c27.1 0 27.1-23.9 27.1-27.1V103.8a27.1 27.1 0 0 0-27.1-27.1z",
    )

    /** 重置（`PageSetupLeft` 右侧的 Reset 按钮）。 */
    val reset by v(
        "PclReset",
        "M530 0c287 0 521 229 521 511s-233 511-521 511c-233 0-436-151-500-368a63 63 0 0 1 44-79 65 65 0 0 1 80 43c48 162 200 276 375 276 215 0 390-171 390-383s-174-383-390-383c-103 0-199 39-270 106l21-5a63 63 0 0 1 33 123l-157 42a65 65 0 0 1-90-42l-49-183a65 65 0 1 1 126-33l6 26A524 524 0 0 1 530 0z",
    )

    /** `MyCheckBox.ShapeCheck` 对勾。 */
    val check by v(
        "PclCheck",
        "M0,6L1.5,4.5 4.5,7.5 10.5,1.5 12,3 4.5,10.5 0,6z",
    )

    /** `MyCard.MainSwap` 折叠箭头（朝下，展开态旋转 180° 朝上）。 */
    val cardSwap by v(
        "PclCardSwap",
        "M2,4 l-2,2 10,10 10,-10 -2,-2 -8,8 -8,-8 z",
    )

    /** `MyComboBox` 下拉箭头（描边）。 */
    val comboArrow by lazy {
        pclStrokeVector(
            "PclComboArrow",
            "M1,1L4.5,4.5L8,1",
            viewportWidth = 9f,
            viewportHeight = 5.5f,
            strokeWidth = 1.5f,
        )
    }

    /** 右键菜单 / 删除用的叉（`ComboBoxItemTemplateWithDelete`）。 */
    val delete by v(
        "PclDelete",
        "F1 M 26.9166,22.1667L 37.9999,33.25L 49.0832,22.1668L 53.8332,26.9168L 42.7499,38L 53.8332,49.0834L 49.0833,53.8334L 37.9999,42.75L 26.9166,53.8334L 22.1666,49.0833L 33.25,38L 22.1667,26.9167L 26.9166,22.1667 Z",
    )

    // ------------------------------------------------------- Mod 管理页底部操作条
    // 全部取自 PageInstanceMod.xaml 里 CardSelect 的四个 MyIconTextButton（LogoScale 已随用随写）。

    /** `BtnSelectEnable` 「启用」。 */
    val selectEnable by v(
        "PclSelectEnable",
        "M512 0a512 512 0 1 0 512 512A512 512 0 0 0 512 0z m0 921.6a409.6 409.6 0 1 1 409.6-409.6 409.6 409.6 0 0 1-409.6 409.6z M716.8 339.968l-256 253.44L328.192 460.8A51.2 51.2 0 0 0 256 532.992l168.448 168.96a51.2 51.2 0 0 0 72.704 0l289.28-289.792A51.2 51.2 0 0 0 716.8 339.968z",
    )

    /** `BtnSelectDisable` 「禁用」。 */
    val selectDisable by v(
        "PclSelectDisable",
        "M508 990.4c-261.6 0-474.4-212-474.4-474.4S246.4 41.6 508 41.6s474.4 212 474.4 474.4S769.6 990.4 508 990.4zM508 136.8c-209.6 0-379.2 169.6-379.2 379.2 0 209.6 169.6 379.2 379.2 379.2s379.2-169.6 379.2-379.2C887.2 306.4 717.6 136.8 508 136.8zM697.6 563.2 318.4 563.2c-26.4 0-47.2-21.6-47.2-47.2 0-26.4 21.6-47.2 47.2-47.2l379.2 0c26.4 0 47.2 21.6 47.2 47.2C744.8 542.4 724 563.2 697.6 563.2z",
    )

    /** `BtnSelectCancel` 「取消选择」。 */
    val selectCancel by v(
        "PclSelectCancel",
        "M867.648 951.296 512 595.648l-355.648 355.648c-11.52 11.52-30.272 11.52-41.856 0L72.64 909.44c-11.52-11.52-11.52-30.272 0-41.856L428.352 512 72.64 156.352c-11.52-11.52-11.52-30.272 0-41.856l41.856-41.856c11.52-11.52 30.272-11.52 41.856 0L512 428.288l355.648-355.648c11.52-11.52 30.272-11.52 41.856 0l41.856 41.856c11.52 11.52 11.52 30.272 0 41.856L595.648 512l355.648 355.648c11.52 11.52 11.52 30.272 0 41.856l-41.856 41.856C897.984 962.88 879.168 962.88 867.648 951.296L867.648 951.296z",
    )

    // ---------------------------------------------------------------- 悬浮按钮

    /** `BtnExtraBack` 回到顶部。 */
    val extraBackToTop by v(
        "PclExtraBackToTop",
        "M858.496 188.9024 173.1072 188.9024c-30.2848 0-54.8352-24.5504-54.8352-54.8352L118.272 106.6496c0-30.2848 24.5504-54.8352 54.8352-54.8352l685.3888 0c30.2848 0 54.8352 24.5504 54.8352 54.8352l0 27.4176C913.3312 164.352 888.7808 188.9024 858.496 188.9024L858.496 188.9024zM150.6048 550.8608c0 0 300.0064-240.3584 303.0272-243.328 13.9776-13.5936 31.1808-21.8624 48.8192-24.7552 1.7152-0.3072 3.4304-0.5888 5.1456-0.768 2.7392-0.3072 5.4528-0.3584 8.192-0.3328 2.7392-0.0256 5.4272 0.0256 8.1664 0.3328 1.7408 0.1792 3.4304 0.4864 5.1456 0.768 17.664 2.8928 34.8672 11.1616 48.8192 24.7552 3.0464 2.944 303.0016 243.328 303.0016 243.328 32.384 31.5136 29.6192 63.9744-2.7392 95.5136-32.3328 31.5392-75.648 2.9696-108.0064-28.544l-185.8816-147.1232 0 485.8368c0 30.3104-24.5248 54.8608-54.8352 54.8608l-27.392 0c-30.2848 0-54.8352-24.5504-54.8352-54.8608L447.232 470.7072l-185.8304 147.0976c-32.3584 31.5392-75.6992 60.1344-108.032 28.5696C121.0368 614.8352 118.272 582.3744 150.6048 550.8608L150.6048 550.8608zM150.6048 550.8608",
    )

    /** `BtnExtraShutdown` 关闭 Minecraft。 */
    val extraShutdown by v(
        "PclExtraShutdown",
        "M802.152 143.682c31.236 22.212 59.004 47.379 83.297 75.486 24.297 28.116 45.12 58.481 62.472 91.105 17.356 32.624 30.543 67.159 39.569 103.6 9.023 36.442 13.533 73.403 13.533 110.887 0 64.556-12.32 125.116-36.96 181.69-24.643 56.57-57.963 105.854-99.956 147.847-41.994 41.998-91.278 75.313-147.851 99.955-56.57 24.642-117.134 36.966-181.684 36.966-63.86 0-124.08-12.324-180.649-36.966-56.575-24.642-106.032-57.957-148.37-99.955-42.344-41.993-75.662-91.277-99.956-147.846C81.3 649.876 69.155 589.315 69.155 524.76c0-36.788 4.337-72.884 13.016-108.283 8.678-35.4 20.996-69.065 36.96-100.999 15.969-31.926 35.75-61.774 59.35-89.537 23.6-27.768 49.976-52.757 79.133-74.969 15.272-11.107 31.755-15.271 49.456-12.497 17.697 2.782 32.1 11.455 43.21 26.03 11.108 14.583 15.27 30.891 12.496 48.94-2.781 18.047-11.454 32.622-26.03 43.732-43.732 31.927-77.225 71.145-100.48 117.653-23.247 46.507-34.873 96.483-34.873 149.93 0 45.812 8.673 89.027 26.03 129.632 17.352 40.605 41.124 76.003 71.317 106.198 30.2 30.2 65.599 54.145 106.204 71.846 40.607 17.697 83.814 26.549 129.631 26.549 45.811 0 89.02-8.852 129.626-26.549 40.606-17.701 76.01-41.647 106.203-71.846 30.195-30.195 54.14-65.593 71.841-106.198 17.702-40.607 26.549-83.82 26.549-129.631 0-54.14-12.49-105.334-37.478-153.577-24.994-48.24-60.048-87.977-105.162-119.213-15.272-10.412-24.468-24.643-27.592-42.69-3.125-18.049 0.518-34.709 10.93-49.983 10.412-14.575 24.643-23.427 42.69-26.547 18.047-3.12 34.698 0.519 49.97 10.93zM534.57 527.888c-18.05 0-33.495-6.421-46.337-19.264-12.841-12.841-19.263-28.287-19.263-46.335V66.635c0-18.048 6.42-33.666 19.263-46.854C501.076 6.593 516.521 0 534.571 0c18.738 0 34.53 6.593 47.371 19.781 12.842 13.188 19.263 28.806 19.263 46.854v395.654c0 18.047-6.42 33.493-19.263 46.335-12.842 12.843-28.633 19.264-47.371 19.264z",
    )

    /** `MySearchBox` 放大镜（含负坐标，由包围盒自动平移）。 */
    val search by lazy {
        pclVector(
            "PclSearch",
            "M675.9168 242.816C556.3008 123.2 362.4448 123.2 242.8288 242.816 123.2128 362.432 123.2128 556.288 242.8288 675.904 362.4448 795.52 556.3008 795.52 675.9168 675.904 795.5328 556.288 795.5328 362.432 675.9168 242.816L675.9168 242.816ZM1000.7168 892.48C1030.6048 922.368 1030.6048 970.816 1000.7168 1000.704 970.8288 1030.656 922.3808 1030.656 892.4288 1000.704L730.0608 838.336C728.1408 836.416 727.4368 833.92 725.7728 831.872 546.3168 960.448 295.7568 945.408 134.5408 784.192-44.8512 604.8-44.8512 313.92 134.5408 134.528 313.9328-44.864 604.8128-44.864 784.2048 134.528 945.4208 295.744 960.4608 546.304 831.8848 725.76 833.9328 727.424 836.4288 728.128 838.3488 730.048L1000.7168 892.48Z",
        )
    }

    /** 正版登录（微软）图标，`PageLaunchLeft.RadioLoginType5`。 */
    val loginMicrosoft by v(
        "PclLoginMicrosoft",
        "M511.488256 95.184408c35.310345 22.516742 95.184408 55.78011 167.34033 84.437781 75.738131 29.681159 148.405797 40.93953 191.392304 45.033483v353.615193c0 73.691154-50.662669 164.781609-136.123938 244.101949C649.65917 901.181409 558.568716 942.12094 512 942.12094c-46.568716 0-137.65917-40.93953-222.096952-119.748126C204.441779 742.54073 153.77911 651.450275 153.77911 577.247376v-353.103448c42.474763-4.093953 116.165917-15.352324 191.904048-45.545227 75.226387-30.192904 133.565217-63.456272 165.805098-83.414293M512 0c-4.093953 0-8.187906 1.535232-11.258371 3.582209l-14.84058 10.234882c-1.023488 0.511744-67.550225 47.592204-170.410794 88.531735-100.813593 39.916042-198.556722 41.963018-199.58021 41.963018l-25.075462 0.511744c-10.746627 0.511744-18.934533 8.187906-18.934533 18.422789v414.000999c0 216.97951 286.064968 446.24088 440.09995 446.24088s440.09995-229.261369 440.09995-445.729136V163.758121c0-10.234883-8.69965-18.422789-18.934533-18.422789l-24.563718-0.511744c-1.023488 0-98.766617-2.046977-199.58021-41.963018-103.372314-40.93953-170.410795-88.01999-170.922538-88.531734L523.258371 3.582209c-3.070465-2.558721-7.164418-3.582209-11.258371-3.582209z M743.308346 410.930535l-260.477761 260.477761c-15.864068 15.864068-41.963018 15.864068-57.827087 0l-144.823588-144.823588c-15.864068-15.864068-15.864068-41.963018 0-57.827087 8.187906-8.187906 18.422789-11.770115 29.169415-11.770115 10.234883 0 20.981509 4.093953 29.169416 11.770115l115.654173 115.654173L685.993003 352.591704c15.864068-15.864068 41.963018-15.864068 57.827087 0 15.352324 16.375812 15.352324 42.474763-0.511744 58.338831z",
    )

    /** 离线登录图标，`PageLaunchLeft.RadioLoginType0`。 */
    val loginOffline by v(
        "PclLoginOffline",
        "M533.293176 788.841412a60.235294 60.235294 0 1 1 85.202824 85.202823l-42.616471 42.586353c-129.355294 129.385412-339.124706 129.385412-468.510117 0-129.385412-129.385412-129.385412-339.124706 0-468.510117l42.586353-42.616471a60.235294 60.235294 0 1 1 85.202823 85.202824l-42.61647 42.586352a210.823529 210.823529 0 1 0 298.164706 298.164706l42.586352-42.61647z m255.548236-255.548236l42.61647-42.586352a210.823529 210.823529 0 1 0-298.164706-298.164706l-42.586352 42.61647a60.235294 60.235294 0 1 1-85.202824-85.202823l42.616471-42.586353c129.355294-129.385412 339.124706-129.385412 468.510117 0 129.385412 129.385412 129.385412 339.1247 0 468.510117l-42.61647 42.61647a60.235294 60.235294 0 1 1-85.202824-85.202823z",
    )

    /**
     * `Logo.IconButtonCard` —— 统一通行证 / Authlib-Injector 两种登录方式的图标
     * （`PageLaunchLeft.xaml.vb` 的 `Case 3` / `Case 4` 都把它塞进 `PathTypeOne`）。
     *
     * 图形是「人物 + 三行列表 + 外框」。PCL 原版的外置登录就是这一枚，
     * 不是 Material 那个「方框加箭头」的 login 图标。
     */
    val loginAuth by v(
        "PclLoginAuth",
        "M834.5 684.1c-31.2-70.4-98.9-120.9-179.1-127.3 63.5-8.5 112.6-63 112.6-128.8 " +
            "0-71.8-58.2-130-130-130s-130 58.2-130 130c0 65.9 49 120.3 112.6 128.8-80.2 6.4-148 57-179.1 127.3 " +
            "-8.7 19.7 6 42 27.6 42 12.1 0 22.7-7.5 27.7-18.5 24.3-53.9 78.5-91.5 141.3-91.5s117 37.6 141.3 91.5 " +
            "c5 11.1 15.6 18.5 27.7 18.5 21.4 0 36.1-22.3 27.4-42z" +
            "M567.9 427.9c0-38.6 31.4-70 70-70s70 31.4 70 70-31.4 70-70 70-70-31.4-70-70z" +
            "M460.3 347.9H216.9c-16.6 0-30 13.4-30 30s13.4 30 30 30h243.3c16.6 0 30-13.4 30-30 " +
            "0.1-16.5-13.4-30-29.9-30z" +
            "M367.4 459.6H216.9c-16.6 0-30 13.4-30 30s13.4 30 30 30h150.4c16.6 0 30-13.4 30-30 " +
            "0.1-16.6-13.4-30-29.9-30z" +
            "M297.4 571.2H217c-16.6 0-30 13.4-30 30s13.4 30 30 30h80.4c16.6 0 30-13.4 30-30 " +
            "0-16.5-13.5-30-30-30z" +
            "M900 236v552H124V236h776m0-60H124c-33.1 0-60 26.9-60 60v552c0 33.1 26.9 60 60 60h776 " +
            "c33.1 0 60-26.9 60-60V236c0-33.1-26.9-60-60-60z",
    )

    /** `PageLoginMs.PanEmpty` 的「未登录正版」人像图标（40 宽、ColorBrush2 @ 0.7）。 */
    val personFill by v(
        "PclPersonFill",
        "M660.338 528.065c63.61-46.825 105.131-121.964 105.131-206.83 0-141.7-115.29-256.987-256.997-256.987-141.706 0-256.998 115.288-256.998 256.987 0 85.901 42.52 161.887 107.456 208.562-152.1 59.92-260.185 207.961-260.185 381.077 0 21.276 17.253 38.53 38.53 38.53 21.278 0 38.53-17.254 38.53-38.53 0-183.426 149.232-332.671 332.667-332.671 1.589 0 3.113-0.207 4.694-0.244 0.8 0.056 1.553 0.244 2.362 0.244 183.434 0 332.664 149.245 332.664 332.671 0 21.276 17.255 38.53 38.533 38.53 21.277 0 38.53-17.254 38.53-38.53 0-174.885-110.354-324.13-264.917-382.809z m-331.803-206.83c0-99.22 80.72-179.927 179.935-179.927s179.937 80.708 179.937 179.927c0 99.203-80.721 179.91-179.937 179.91s-179.935-80.708-179.935-179.91z",
    )

    // ------------------------------------------------ 下载管理（PageSpeedRight）

    /** `PageSpeedRight` 分任务「已完成」的对勾（Height=16、Width=15）。 */
    val speedCheck by v(
        "PclSpeedCheck",
        "F1 M 23.7501,33.25L 34.8334,44.3333L 52.2499,22.1668L 56.9999,26.9168L 34.8334,53.8333L 19.0001,38L 23.7501,33.25 Z",
    )

    /** `PageSpeedRight` 分任务「失败」的叉（15×15）。 */
    val speedFailed by v(
        "PclSpeedFailed",
        "F1 M2.5,0 L0,2.5 7.5,10 0,17.5 2.5,20 10,12.5 17.5,20 20,17.5 12.5,10 20,2.5 17.5,0 10,7.5 2.5,0Z",
    )

    /** `PageSpeedRight` 任务卡片右上角的关闭（20×20，Height=20、LogoScale=1.1）。 */
    val speedCancel by v(
        "PclSpeedCancel",
        "F1 M2,0 L0,2 8,10 0,18 2,20 10,12 18,20 20,18 12,10 20,2 18,0 10,8 2,0Z",
    )

    // ------------------------------------------------ 社区资源列表项（MyResourceItem）

    /** `MyResourceItem.PathVersion` 适用于的版本（立方体）。 */
    val resourceVersion by v(
        "PclResourceVersion",
        "M940.4 463.7L773.3 174.2c-17.3-30-49.2-48.4-83.8-48.4H340.2c-34.6 0-66.5 18.5-83.8 48.4L89.2 463.7c-17.3 30-17.3 66.9 0 96.8L256.4 850c17.3 30 49.2 48.4 83.8 48.4h349.2c34.6 0 66.5-18.5 83.8-48.4l167.2-289.5c17.3-29.9 17.3-66.8 0-96.8z m-94.6 96.8L725.9 768.1c-17.3 30-49.2 48.4-83.8 48.4H387.5c-34.6 0-66.5-18.5-83.8-48.4L183.9 560.5c-17.3-30-17.3-66.9 0-96.8l119.8-207.5c17.3-30 49.2-48.4 83.8-48.4h254.6c34.6 0 66.5 18.5 83.8 48.4l119.8 207.5c17.3 30 17.3 66.9 0.1 96.8z M522.3 321.2c-2.5-0.1-5-0.2-7.5-0.2-119.9 0-214 110.3-186.3 235 15.8 70.9 71.5 126.6 142.4 142.4 17.5 3.9 34.7 5.4 51.4 4.7 102.1-3.9 183.6-87.9 183.6-191 0.1-103-81.5-187-183.6-190.9z m68.6 269.1c-18.5 18-43 28.9-68.6 30.7l-6 0.3c-30.2 0.4-58.6-11.4-79.7-33-19.5-20.1-30.7-47-30.9-75-0.3-29.6 11.1-57.4 32-78.3 20.6-20.6 48-32 77.2-32 2.5 0 5 0.1 7.5 0.3 26.7 1.8 51.5 13.2 70.5 32.5 19.6 20 30.8 46.9 31.2 74.9 0.2 30.2-11.5 58.6-33.2 79.6z",
    )

    /** `MyResourceItem.PathDownload` 下载量。 */
    val resourceDownload by v(
        "PclResourceDownload",
        "M955 610h-59c-15 0-29 13-29 29v196c0 15-13 29-29 29h-649c-15 0-29-13-29-29v-196c0-15-13-29-29-29h-59c-15 0-29 13-29 29V905c0 43 35 78 78 78h787c43 0 78-35 78-78V640c0-15-13-29-29-29zM492 740c11 11 29 11 41 0l265-265c11-11 11-29 0-41l-41-41c-11-11-29-11-41 0l-110 110c-11 11-33 3-33-13V68C571 53 555 39 541 39h-59c-15 0-29 13-29 29v417c0 17-21 25-33 13l-110-110c-11-11-29-11-41 0L226 433c-11 11-11 29 0 41L492 740z",
    )

    /** `MyResourceItem.PathTime` 更新时间。 */
    val resourceTime by v(
        "PclResourceTime",
        "M810 403l-39 39c-10 10-28 10-39 0l-162-162v412c0 15-12 28-28 28h-56c-15 0-28-12-28-28v-412l-162 162c-10 10-28 10-39 0l-39-39c-10-10-10-28 0-39l278-278c10-10 28-10 39 0l278 278c10 10 10 28-0 39zM568 946h-112v-112h112v112z",
    )

    // ------------------------------------------------- 正版登录（PageLoginMsSkin）

    /** `PageLoginMsSkin.xaml` → `BtnSkin`（衣架：皮肤与披风）。 */
    val loginMsSkin by v(
        "PclLoginMsSkin",
        "M764.0003 0.076794a215.433442 215.433442 0 0 0-76.282279 13.950954l-9.06172 3.686123c-49.148314 20.734445-88.518161 30.53851-115.498538 30.53851-27.031573 0-66.478214-9.855261-116.676049-31.050471a217.583681 217.583681 0 0 0-72.186586-16.766743l-9.138515-0.307177-3.839712-0.051196-8.626553 0.307177-4.223683 0.281579A160.755943 160.755943 0 0 0 250.477214 45.385396l-219.32435 212.438467a102.392321 102.392321 0 0 0-11.263156 134.210734l106.360023 144.552359 3.378947 4.351674a102.469115 102.469115 0 0 0 112.810739 31.690423l5.657176-2.175837V883.210559a140.789441 140.789441 0 0 0 140.789441 140.789441h372.170487l5.657175-0.102392a140.789441 140.789441 0 0 0 135.132266-140.687049l-0.025599-318.6705 1.177512 0.716747a102.392321 102.392321 0 0 0 141.941355-44.233483l70.906682-144.39877a102.392321 102.392321 0 0 0-20.657651-118.647101L875.86391 45.385396A160.730345 160.730345 0 0 0 764.0003 0.076794z m-357.349199 111.454041C468.444867 137.615279 520.562558 150.644702 563.157763 150.644702c42.492813 0 94.584906-12.978227 156.199485-38.985876A112.580356 112.580356 0 0 1 764.0003 102.469115c15.154063 0 29.693773 5.887558 40.598555 16.433967L1023.923206 331.495138l-70.906682 144.39877-93.023423-65.684674a38.39712 38.39712 0 0 0-60.53946 31.383247V883.210559a38.39712 38.39712 0 0 1-38.39712 38.39712H388.886034a38.39712 38.39712 0 0 1-38.397121-38.39712V445.176212a38.39712 38.39712 0 0 0-61.691373-30.487314l-80.070795 61.20501L102.392321 331.341549l219.32435-212.438467a58.363623 58.363623 0 0 1 35.581332-16.229183L362.110442 102.469115l6.322726 0.179186a115.191361 115.191361 0 0 1 38.217933 8.882534z",
    )

    /** `PageLoginMsSkin.xaml` → `BtnEdit`（铅笔：修改信息）。 */
    val loginMsEdit by v(
        "PclLoginMsEdit",
        "M462.336 924.891429L73.142857 950.857143l25.965714-389.193143 467.017143-467.017143a73.398857 73.398857 0 0 1 103.789715 0l259.437714 259.437714a73.398857 73.398857 0 0 1 0 103.789715l-467.017143 467.017143z m155.684571-778.349715L202.861714 561.664l259.474286 259.474286L877.458286 405.942857 618.057143 146.541714zM151.003429 873.033143l233.508571-25.965714-207.579429-207.579429-25.965714 233.545143z m544.841142-492.982857l51.894858 51.894857-233.508572 233.508571-51.931428-51.894857 233.545142-233.508571z",
    )

    /** `PageLoginMsSkin.xaml` → `BtnExit`（双向箭头：切换账号）。 */
    val loginMsSwitch by v(
        "PclLoginMsSwitch",
        "M512 0A512 512 0 1 0 512 512 512 512 0 0 0 512 0z m0 941.407086A429.407086 429.407086 0 1 1 941.407086 512 429.407086 429.407086 0 0 1 512 941.407086z m235.139657-368.64H264.045714a43.505371 43.505371 0 0 0-31.246628 72.060343l159.305143 166.765714a41.281829 41.281829 0 1 0 59.626057-56.788114l-94.997943-99.474286H747.227429a41.311086 41.311086 0 1 0 0-82.622172zM262.670629 450.940343H745.764571a43.505371 43.505371 0 0 0 31.246629-72.352914l-159.305143-166.765715a41.281829 41.281829 0 1 0-59.713828 57.022172l94.968685 99.474285H262.670629a41.281829 41.281829 0 0 0 0 82.563658z",
    )

    /** `MyResourceItem.PathSource` 来源平台。 */
    val resourceSource by v(
        "PclResourceSource",
        "M511 143c-203 0-369 165-369 369 0 203 165 369 369 369s369-165 369-369c0-203-165-369-369-369z m0 673c-29 0-68-59-89-159l178 0c-20 100-59 159-89 159z m-98-224c-2-24-3-51-3-79 0-29 1-57 4-83l197 0c2 25 4 53 4 83 0 28-1 54-3 79l-198-0z m98-383c29 0 67 58 88 156l-177-0c20-98 59-155 88-155z m164 221l128 0c7 26 11 54 11 82 0 27-3 54-10 80l-129-0c2-26 3-52 3-79 0-28-1-56-4-83z m101-64l-110-0c-7-37-16-72-28-102-4-11-9-22-15-33 66 25 121 74 155 135z m-376-135c-5 10-10 21-15 33-12 30-21 64-28 102l-110-0c34-60 89-109 154-134z m-181 199l128 0c-2 27-3 54-3 83 0 27 1 53 3 79l-127-0v6c-8-27-12-55-12-85-0-29 3-57 11-83z m24 227l112 0c7 38 16 74 29 105 4 11 9 22 15 33-67-26-122-76-156-139zM621 795c5-10 10-21 15-33 12-30 22-66 29-105l111 0c-34 62-89 111-156 137z",
    )

    // ------------------------------------------- 设置：控制 / 手柄 / Java / 控制管理
    //
    // PCL2 本身只有 启动 / 联机 / 个性化 / 其他 四张设置页，这四个版块是 Zalith 原生
    // 才有的能力，所以图标不能取自 PCL 源码 —— 这里直接取 Zalith 原生 `res/drawable`
    // 里的 Material 图标 pathData（同样是 960 视口、纯填充），路径一个点都没改。

    /** 设置 → 控制设置。原生 `ic_mouse_filled`（鼠标）。 */
    val setupControl by v(
        "PclSetupControl",
        "M480,880q-117,0 -198.5,-81.5T200,600v-160h560v160q0,117 -81.5,198.5T480,880ZM200,360q0,-105 68,-183.5T440,83v277L200,360ZM520,360v-277q104,15 172,93.5T760,360L520,360Z",
    )

    /** 设置 → 手柄设置。原生 `ic_sports_esports_outlined`（手柄）。 */
    val setupGamepad by v(
        "PclSetupGamepad",
        "M182,760q-51,0 -79,-35.5T82,638l42,-300q9,-60 53.5,-99T282,200h396q60,0 104.5,39t53.5,99l42,300q7,51 -21,86.5T778,760q-21,0 -39,-7.5T706,730l-90,-90L344,640l-90,90q-15,15 -33,22.5t-39,7.5ZM198,674 L312,560h336l114,114q2,2 16,6 11,0 17.5,-6.5T800,656l-44,-308q-4,-29 -26,-48.5T678,280L282,280q-30,0 -52,19.5T204,348l-44,308q-2,11 4.5,17.5T182,680q2,0 16,-6ZM708.5,508.5Q720,497 720,480t-11.5,-28.5Q697,440 680,440t-28.5,11.5Q640,463 640,480t11.5,28.5Q663,520 680,520t28.5,-11.5ZM628.5,388.5Q640,377 640,360t-11.5,-28.5Q617,320 600,320t-28.5,11.5Q560,343 560,360t11.5,28.5Q583,400 600,400t28.5,-11.5ZM480,480ZM310,450v40q0,13 8.5,21.5T340,520q13,0 21.5,-8.5T370,490v-40h40q13,0 21.5,-8.5T440,420q0,-13 -8.5,-21.5T410,390h-40v-40q0,-13 -8.5,-21.5T340,320q-13,0 -21.5,8.5T310,350v40h-40q-13,0 -21.5,8.5T240,420q0,13 8.5,21.5T270,450h40Z",
    )

    /** 设置 → Java 管理。原生 `ic_java`（Java 咖啡杯）。 */
    val setupJava by v(
        "PclSetupJava",
        "m10.64,0.07c1.27,2.99 -4.55,4.83 -5.09,7.32 -0.49,2.29 3.45,4.98 3.45,4.98 -0.6,-0.95 -1.04,-1.75 -1.64,-3.22C6.35,6.65 13.56,4.4 10.64,0.07ZM14.48,4.23s-5.09,0.32 -5.34,3.65c-0.12,1.48 1.34,2.26 1.39,3.34 0.04,0.88 -0.88,1.61 -0.88,1.61 0,0 1.64,-0.3 2.15,-1.64 0.57,-1.49 -1.1,-2.51 -0.93,-3.7 0.16,-1.14 3.62,-3.25 3.62,-3.25h0ZM15.72,11.89c-0.53,-0.02 -1.13,0.17 -1.67,0.57 1.05,-0.23 1.95,0.43 1.95,1.19 0,1.7 -2.43,3.31 -2.43,3.31 0,0 3.76,-0.42 3.76,-3.22 0,-1.16 -0.72,-1.8 -1.61,-1.84h0ZM5.74,11.92c-1.31,0.05 -3.93,0.26 -3.93,1.27 0,1.41 6.1,1.52 10.46,0.65 0,0 1.19,-0.83 1.5,-1.13 -2.86,0.59 -9.39,0.69 -9.39,0.17 0,-0.47 2.09,-0.96 2.09,-0.96 0,0 -0.3,-0.01 -0.73,0h0ZM5.34,14.52c-0.72,0 -1.78,0.56 -1.78,1.1 0,1.1 5.4,1.94 9.39,0.34l-1.39,-0.85c-2.7,0.88 -7.7,0.59 -6.22,-0.59h0ZM6.02,16.98c-0.98,0 -1.61,0.62 -1.61,1.07 0,1.4 5.85,1.54 8.17,0.11l-1.47,-0.96c-1.73,0.75 -6.09,0.86 -5.09,-0.23h0ZM2.74,18.25c-1.6,-0.03 -2.63,0.69 -2.63,1.3 0,3.23 16.37,3.08 16.37,-0.23 0,-0.55 -0.65,-0.81 -0.88,-0.93 1.34,3.16 -13.37,2.91 -13.37,1.05 0,-0.42 1.09,-0.85 2.09,-0.65l-0.85,-0.48c-0.24,-0.04 -0.49,-0.05 -0.74,-0.06h0ZM18.21,20.43c-2.49,2.41 -8.78,3.27 -15.13,1.78 6.34,2.65 15.09,1.18 15.13,-1.78h0Z",
    )

    /** 设置 → 插件管理。Material `extension`（拼图块）。 */
    val setupPlugin by v(
        "PclSetupPlugin",
        "M20.5,11H19V7c0-1.1-0.9-2-2-2h-4V3.5C13,2.12 11.88,1 10.5,1S8,2.12 8,3.5V5H4C2.9,5 2.01,5.9 2.01,7v3.8H3.5C4.88,10.8 6,11.92 6,13.3s-1.12,2.5-2.5,2.5H2V19c0,1.1 0.9,2 2,2h3.8v-1.5c0-1.38 1.12-2.5 2.5-2.5s2.5,1.12 2.5,2.5V21H17c1.1,0 2-0.9 2-2v-4h1.5c1.38,0 2.5-1.12 2.5-2.5S21.88,11 20.5,11z",
    )

    /** 设置 → 控制管理。原生 `ic_dashboard_outlined`（布局面板）。 */
    val setupControlManage by v(
        "PclSetupControlManage",
        "M520,360v-240h320v240L520,360ZM120,520v-400h320v400L120,520ZM520,840v-400h320v400L520,840ZM120,840v-240h320v240L120,840ZM200,440h160v-240L200,200v240ZM600,760h160v-240L600,520v240ZM600,280h160v-80L600,200v80ZM200,760h160v-80L200,680v80Z",
    )

    // ------------------------------------------- 列表行内小按钮（同样取自原生 drawable）

    /** 上移一项（`MyIconButton` 用）。原生 `ic_arrow_upward`。 */
    val moveUp by v(
        "PclMoveUp",
        "M440,800v-487L216,537l-56,-57 320,-320 320,320 -56,57 -224,-224v487h-80Z",
    )

    /** 下移一项（`MyIconButton` 用）。原生 `ic_arrow_downward`。 */
    val moveDown by v(
        "PclMoveDown",
        "M440,160v487L216,423l-56,57 320,320 320,-320 -56,-57 -224,224v-487h-80Z",
    )

    /** 更换图片。原生 `ic_image_outlined`。 */
    val replaceImage by v(
        "PclReplaceImage",
        "M200,840q-33,0 -56.5,-23.5T120,760v-560q0,-33 23.5,-56.5T200,120h560q33,0 56.5,23.5T840,200v560q0,33 -23.5,56.5T760,840L200,840ZM200,760h560v-560L200,200v560ZM240,680h480L570,480 450,640l-90,-120 -120,160ZM200,760v-560,560Z",
    )

    /** 设置命中点。原生 `ic_highlight_mouse_cursor`。 */
    val hotspot by v(
        "PclHotspot",
        "M516,878q-9,2 -18,2h-18q-83,0 -156,-31.5T197,763q-54,-54 -85.5,-127T80,480q0,-83 31.5,-156T197,197q54,-54 127,-85.5T480,80q83,0 156,31.5T763,197q54,54 85.5,127T880,480v18q0,9 -2,18l-78,-24v-12q0,-134 -93,-227t-227,-93q-134,0 -227,93t-93,227q0,134 93,227t227,93h12l24,78ZM821,900L650,729 600,880 480,480l400,120 -151,50 171,171 -79,79Z",
    )

    /** 重置该项（PCL 的 `reset` 是左栏那枚循环箭头，这里用 Material 的 `restart_alt` 以区分上下文）。 */
    val restartAlt by v(
        "PclRestartAlt",
        "M440,838q-121,-15 -200.5,-105.5T160,520q0,-66 26,-126.5T260,288l57,57q-38,34 -57.5,79T240,520q0,88 56,155.5T440,758v80ZM520,838v-80q87,-16 143.5,-83T720,520q0,-100 -70,-170t-170,-70h-3l44,44 -56,56 -140,-140 140,-140 56,56 -44,44h3q134,0 227,93t93,227q0,121 -79.5,211.5T520,838Z",
    )

    /** 复制一份。原生 `ic_file_copy_filled`。 */
    val fileCopy by v(
        "PclFileCopy",
        "M160,920q-33,0 -56.5,-23.5T80,840v-560h80v560h440v80L160,920ZM320,760q-33,0 -56.5,-23.5T240,680v-560q0,-33 23.5,-56.5T320,40h280l240,240v400q0,33 -23.5,56.5T760,760L320,760ZM560,320h200L560,120v200Z",
    )

    /** 新建。原生 `ic_add_box_outlined`。 */
    val addBox by v(
        "PclAddBox",
        "M440,680h80v-160h160v-80L520,440v-160h-80v160L280,440v80h160v160ZM200,840q-33,0 -56.5,-23.5T120,760v-560q0,-33 23.5,-56.5T200,120h560q33,0 56.5,23.5T840,200v560q0,33 -23.5,56.5T760,840L200,840ZM200,760h560v-560L200,200v560ZM200,200v560,-560Z",
    )

    /**
     * 「启动并进入服务器」的播放三角 —— 服务器列表行尾那个小三角。
     *
     * PCL2 的服务器列表并没有单独的播放图标（它靠双击条目进服），
     * 这里按 Material 的 `play_arrow` 轮廓画一个，视觉上与 PCL 的图标风格一致。
     */
    val playTriangle by v(
        "PclPlayTriangle",
        "M320,176L832,512L320,848Z",
    )
}
