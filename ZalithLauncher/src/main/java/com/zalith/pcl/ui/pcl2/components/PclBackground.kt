/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 背景图片层（PCL `PageSetupUI.xaml` 的 CardBackground 真正生效的那一半）。
 *
 * PCL 的窗体背景由三部分叠出来：
 *   1. 底色：`UiBackgroundColorful` 关掉时是纯色 #F5F5F5，打开时是彩色渐变
 *      （由 `PclShell` 的 `PclMainContent` 画）
 *   2. 背景图片：`PCL/Background` 文件夹里随机取一张，按 `UiBackgroundSuit` 自适应，
 *      按 `UiBackgroundOpacity` 调不透明度
 *   3. 界面本身
 *
 * 本文件负责第 2 步，以及「背景模糊」。
 *
 * 模糊**不再依赖平台能力**：这里自己实现了一次真正的可分离高斯卷积
 * （`PclGaussianBlur`），在 CPU 上把位图预先模糊好再交给 GPU 铺。
 * 原因有两层：
 *   · `Modifier.blur`（`RenderEffect`）要 Android 12+，低版本会**静默失效**；
 *   · 之前的低版本降级是「把图解码得特别小再放大」，那只是双线性插值，
 *     出来的是「马赛克糊」而不是高斯模糊（用户明确要求要真高斯）。
 * 现在所有 Android 版本走同一条管线，观感一致，而且模糊是预算好的位图，
 * 比每帧对一个全屏 layer 做 RenderEffect 更省 GPU。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.media3.ui.AspectRatioFrameLayout
import com.zalith.pcl.ui.components.VideoPlayer
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.data.PclAppearance
import com.zalith.pcl.ui.pcl2.data.PclBackgroundSuit
import com.zalith.pcl.ui.screens.content.elements.whiteOverlayAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

/** 不模糊时解码的最大边长（要够清晰，屏幕再大也够用）。 */
private const val SHARP_MAX_DIM = 2048

/** 模糊档位 0..40 的上下界（与 `AllSettings.backgroundBlur` 的取值范围一致）。 */
private const val BLUR_MAX = 40

/** 拖滑杆时把连续变化合并成一次计算，避免一路拖过去每一档都算一遍。 */
private const val BLUR_DEBOUNCE_MS = 80L

/** 图片解码（带降采样，避免一张手机照片就把内存吃满）。 */
object PclImageLoader {

    /** 解码结果缓存：`路径|最大边长` to 位图（不同降采样档位各存一份）。 */
    private val cache = HashMap<String, ImageBitmap?>()

    /** 只做细粒度加锁，不锁整个方法 —— 见 [invalidate] 里关于锁顺序的说明。 */
    private val lock = Any()

    /**
     * 读缓存 / 解码。**会阻塞当前线程**，不要在组合阶段直接调用。
     *
     * 解码本身在锁外做：一张大图解码要几十毫秒，持锁会让并发的 `load`（比如右上角
     * logo 与背景图同时加载）互相干等。
     */
    fun load(path: String?, maxDim: Int = SHARP_MAX_DIM): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val key = "$path|$maxDim"
        synchronized(lock) {
            if (cache.containsKey(key)) return cache[key]
        }
        val bitmap = decode(path, maxDim)
        synchronized(lock) { cache[key] = bitmap }
        return bitmap
    }

    /**
     * 清空缓存。背景图换了 / 模糊档位变了之后由设置页调用。
     * 顺便把模糊结果缓存一起清掉 —— 否则换了一张图，用户看到的还是上一张的模糊图。
     *
     * ⚠️ 两次清理**不能嵌在同一个锁里**：`load` 里是「先拿本地图缓存锁、再解码」，
     * 而 `PclGaussianBlur.load` 是「先拿模糊缓存锁、再调 `PclImageLoader.load`」。
     * 如果这里持着图缓存的锁去拿模糊缓存的锁，就和那条路径构成 ABBA 死锁。
     */
    fun invalidate() {
        synchronized(lock) { cache.clear() }
        PclGaussianBlur.clear()
    }

    private fun decode(path: String, maxDim: Int): ImageBitmap? = runCatching {
        val source = decodeBitmap(path, maxDim) ?: return@runCatching null
        source.asImageBitmap()
    }.getOrNull()

    /** 按「最长边不超过 [maxDim]」解码（用 2 的幂做 `inSampleSize`，不再额外缩放）。 */
    internal fun decodeBitmap(path: String, maxDim: Int): Bitmap? {
        if (!File(path).isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/**
 * 背景模糊：**自己实现的高斯模糊**（可分离卷积，整数定点权重）。
 *
 * 管线：解码到「工作分辨率」→ 水平卷积 → 垂直卷积 → 交给 Compose 放大铺满。
 * 工作分辨率随模糊强度收缩（越模糊越不需要细节），既是性能优化，也顺带起到了
 * 「先低通再卷积」的作用 —— 这正是高质量高斯模糊的常规做法
 * （RenderScript `ScriptIntrinsicBlur` 内部也是先降采样再模糊）。
 *
 * 所有计算都在调用方给的线程上同步执行，请放在 `Dispatchers.Default` 里调用。
 * 结果按 `路径|档位` 缓存，拖动滑杆来回试不会重复算。
 */
object PclGaussianBlur {

    /** 访问顺序 LRU，最多留 3 张（一张 512×512 的位图 ≈ 1 MB，留多了没必要）。 */
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?): Boolean =
            size > 3
    }

    private val lock = Any()

    fun load(path: String?, blur: Int): ImageBitmap? {
        if (path.isNullOrBlank()) return null
        val level = blur.coerceIn(0, BLUR_MAX)
        // 不模糊时直接借图缓存；注意这行**必须在拿 [lock] 之前**返回，
        // 否则就会和 `PclImageLoader.invalidate` 构成 ABBA 死锁（见那边的注释）。
        if (level == 0) return PclImageLoader.load(path, SHARP_MAX_DIM)
        val key = "$path|$level"
        synchronized(lock) {
            if (cache.containsKey(key)) return cache[key]
        }
        // 卷积是纯 CPU 活儿，放在锁外跑，别把设置页的其它背景请求堵住
        val result = runCatching { blurFile(path, level) }.getOrNull()
        synchronized(lock) { cache[key] = result }
        return result
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    /**
     * 解码 → 缩放 → 高斯模糊。返回的位图最长边是 [workMaxDimFor] 算出来的工作尺寸，
     * 由 Compose 放大铺满（已经模糊过的图放大是平滑的，不会露像素）。
     */
    private fun blurFile(path: String, blur: Int): ImageBitmap? {
        val workMaxDim = workMaxDimFor(blur)
        val source = PclImageLoader.decodeBitmap(path, workMaxDim) ?: return null

        // `inSampleSize` 只能取 2 的幂，可能比目标还大一圈；再用双线性缩到精确尺寸。
        // 这次缩放本身也是低通，与后面的卷积一起构成完整的模糊核。
        val scale = workMaxDim.toFloat() / max(source.width, source.height).toFloat()
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = if (width == source.width && height == source.height) {
            source
        } else {
            Bitmap.createScaledBitmap(source, width, height, true)
        }

        val sigma = sigmaFor(blur, workMaxDim)
        val kernel = gaussianKernel(sigma)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()

        convolve(pixels, width, height, kernel)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out.asImageBitmap()
    }

    /**
     * 工作分辨率：模糊越强，工作位图越小。
     * 768 → 256（blur 0..40 线性插值）。256 边长配 sigma≈7.7 已经是「几乎看不出内容」
     * 的程度了，再大只是白烧 CPU。
     */
    private fun workMaxDimFor(blur: Int): Int {
        val t = (blur.toFloat() / BLUR_MAX).coerceIn(0f, 1f)
        return (768f + (256f - 768f) * t).roundToInt()
    }

    /**
     * 高斯核的标准差（单位：工作位图像素）。
     *
     * 取「工作位图最长边的 3%」作为最强档（blur=40）的 sigma。
     * 一个经验值：sigma 到画面宽度的 3% 时，内容还留得住大色块，但细节已经化开 ——
     * 再往上加就只剩色斑了。0.6 的基础量保证 blur=1 也有一点点软化，
     * 不至于「开了跟没开一样」。
     */
    private fun sigmaFor(blur: Int, workMaxDim: Int): Float {
        val t = (blur.toFloat() / BLUR_MAX).coerceIn(0f, 1f)
        return workMaxDim * 0.03f * t + 0.6f
    }

    /**
     * 一维高斯核，归一化后转成 16 位定点整数（`sum == 65536`）。
     *
     * 用定点而不是浮点：卷积是 O(像素×核长) 的热循环，每个像素做 4 次浮点乘加
     * 在手机上明显更慢；定点的累加是全整数运算，JIT 后快很多，而 8 位色深根本
     * 吃不出精度差。舍入误差补回中心那一格，保证总权重精确等于 65536，
     * 否则整张图会整体偏暗或偏亮。
     */
    internal fun gaussianKernel(sigma: Float): IntArray {
        val radius = ceil(sigma * 3f).toInt().coerceIn(1, 64)
        val size = radius * 2 + 1
        val twoSigmaSq = 2f * sigma * sigma
        val raw = FloatArray(size) { i ->
            val x = (i - radius).toFloat()
            exp(-(x * x) / twoSigmaSq)
        }
        val total = raw.sum()
        var acc = 0
        val kernel = IntArray(size)
        for (i in 0 until size) {
            val v = (raw[i] / total * 65536f).roundToInt()
            kernel[i] = v
            acc += v
        }
        kernel[radius] += 65536 - acc
        return kernel
    }

    /**
     * 可分离高斯卷积：先横后纵，两趟各 `2r+1` 次乘加，
     * 比二维核的 `(2r+1)²` 省一个数量级。
     *
     * 边缘处理用**钳位复制**（越界的取样索引夹到边界像素），
     * 这样模糊后四周不会出现发黑的透明边，也就不需要画的时候再往外「扩边」。
     * alpha 通道一起参与卷积（背景图基本都是不透明的，不做预乘也不会出问题）。
     */
    internal fun convolve(pixels: IntArray, width: Int, height: Int, kernel: IntArray) {
        val radius = kernel.size / 2
        val temp = IntArray(pixels.size)
        val lastX = width - 1
        val lastY = height - 1

        // 横向：读的是同一行的连续像素，天然对缓存友好
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                for (k in -radius..radius) {
                    val p = pixels[row + (x + k).coerceIn(0, lastX)]
                    val w = kernel[k + radius]
                    a += (p ushr 24 and 0xFF) * w
                    r += (p ushr 16 and 0xFF) * w
                    g += (p ushr 8 and 0xFF) * w
                    b += (p and 0xFF) * w
                }
                temp[row + x] = ((a shr 16) shl 24) or
                    ((r shr 16) shl 16) or
                    ((g shr 16) shl 8) or
                    (b shr 16)
            }
        }

        // 纵向：如果直接按列取 `temp[(y + k) * width + x]`，内层循环每读一次就跳一整行
        // （512 宽时步长 2 KB），相当于每步都换一条 cache line —— 手机上这一趟能比横向慢好几倍。
        // 改成「一列一个累加缓冲、逐行喂」，每次读整行都是顺序访问，累加在寄存器友好的 IntArray 上。
        val accA = IntArray(width)
        val accR = IntArray(width)
        val accG = IntArray(width)
        val accB = IntArray(width)
        for (y in 0 until height) {
            accA.fill(0)
            accR.fill(0)
            accG.fill(0)
            accB.fill(0)
            for (k in -radius..radius) {
                val rowBase = (y + k).coerceIn(0, lastY) * width
                val w = kernel[k + radius]
                for (x in 0 until width) {
                    val p = temp[rowBase + x]
                    accA[x] += (p ushr 24 and 0xFF) * w
                    accR[x] += (p ushr 16 and 0xFF) * w
                    accG[x] += (p ushr 8 and 0xFF) * w
                    accB[x] += (p and 0xFF) * w
                }
            }
            val out = y * width
            for (x in 0 until width) {
                pixels[out + x] = ((accA[x] shr 16) shl 24) or
                    ((accR[x] shr 16) shl 16) or
                    ((accG[x] shr 16) shl 8) or
                    (accB[x] shr 16)
            }
        }
    }
}

/**
 * 动态背景层（视频）。
 *
 * 复用 [VideoPlayer]（ExoPlayer + TextureView 版式的 PlayerView）：
 * 循环、静音、`RESIZE_MODE_ZOOM` 铺满，切到后台由播放器自己暂停。
 *
 * 为什么不用 SurfaceView：SurfaceView 画在窗口 surface **下面**，
 * 启动器的渐变底和界面内容会把它整块盖住，等于看不见；
 * TextureView 参与正常的视图合成，才能当「垫在最底下」的背景。
 *
 * 视频不做模糊：逐帧高斯模糊代价太高，而且动态背景本来就是给「看得见」用的。
 */
@Composable
fun PclBackgroundVideoLayer(
    modifier: Modifier = Modifier,
    opacityPercent: Int,
) {
    val path = PclAppearance.backgroundVideo
    if (path.isNullOrBlank() || !File(path).isFile) return

    val alpha = (opacityPercent / 100f).coerceIn(0.05f, 1f)

    VideoPlayer(
        videoUri = Uri.fromFile(File(path)),
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        autoPlay = true,
        loop = true,
        muted = true,
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        // 换了视频要重新 setMediaItem，用路径当 key
        refreshTrigger = path,
    )
}

/**
 * 背景图片层。铺在窗体最底下，界面内容都盖在它上面。
 *
 * @param opacityPercent 不透明度（20..100），用的是 Zalith 的 `launcherBackgroundOpacity`
 */
@Composable
fun PclBackgroundImageLayer(
    modifier: Modifier = Modifier,
    opacityPercent: Int,
) {
    val path = PclAppearance.backgroundImage
    val suit = PclAppearance.backgroundSuit
    val blur = AllSettings.backgroundBlur.state

    // 位图不随档位一起清空：换图 / 调档期间继续显示上一张，直到新的算好再替换。
    // （先置 null 的话，拖动滑杆时背景会一黑一闪 —— 那比"晚半帧才变"难看得多。）
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path, blur) {
        val target = path
        if (target.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        // 拖滑杆会连续改 blur；等手停下来再算，避免每档都跑一次卷积
        if (blur > 0) delay(BLUR_DEBOUNCE_MS)
        val loaded = withContext(Dispatchers.Default) {
            if (blur > 0) {
                PclGaussianBlur.load(target, blur)
            } else {
                // 不模糊时解码要放到 IO 里（大图解码是纯 IO + 内存操作）
                withContext(Dispatchers.IO) { PclImageLoader.load(target, SHARP_MAX_DIM) }
            }
        }
        if (loaded != null) bitmap = loaded
    }
    val bmp = bitmap ?: return

    val alpha = (opacityPercent / 100f).coerceIn(0.05f, 1f)
    // 白色提亮层（毛玻璃的"雾"），与原生 BackgroundGlass 同一套公式
    val frostAlpha = if (blur > 0) whiteOverlayAlpha(blur) else 0f

    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .drawWithCache {
                // 平铺用无限重复的着色器，比在 draw 里循环贴图快得多
                val tileShader = ImageShader(bmp, TileMode.Repeated, TileMode.Repeated)
                onDrawBehind {
                    drawPclBackground(bmp, tileShader, suit)
                    if (frostAlpha > 0f) {
                        drawRect(color = Color.White.copy(alpha = frostAlpha))
                    }
                }
            },
    )
}

/**
 * 按 `UiBackgroundSuit` 铺开背景图片。
 *
 * 模糊现在是**烘进位图里**的（见 [PclGaussianBlur]），卷积用的是钳位边缘，
 * 所以画的时候不需要再往外扩一圈来遮透明豁口。
 */
private fun DrawScope.drawPclBackground(
    image: ImageBitmap,
    tileShader: Shader,
    suit: PclBackgroundSuit,
) {
    val w = size.width
    val h = size.height
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (w <= 0f || h <= 0f || iw <= 0f || ih <= 0f) return

    fun place(dstW: Float, dstH: Float, alignX: Float, alignY: Float) {
        val left = (w - dstW) * alignX
        val top = (h - dstH) * alignY
        drawImage(
            image = image,
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(dstW.toInt().coerceAtLeast(1), dstH.toInt().coerceAtLeast(1)),
        )
    }

    when (suit) {
        PclBackgroundSuit.Tile ->
            drawRect(brush = ShaderBrush(tileShader), size = size)

        PclBackgroundSuit.Stretch ->
            place(w, h, 0.5f, 0.5f)

        PclBackgroundSuit.Adapt -> {
            val s = maxOf(w / iw, h / ih)
            place(iw * s, ih * s, 0.5f, 0.5f)
        }

        // 智能：与「适应」一致（等比放大铺满、裁掉超出部分）。
        // PCL 原版的 `UiBackgroundSuit = 0`（智能）是「图片比窗口小时平铺、否则适应」，
        // 但平铺在宽屏手机上观感很差，用户要求按「适应」处理。
        PclBackgroundSuit.Auto -> {
            val s = maxOf(w / iw, h / ih)
            place(iw * s, ih * s, 0.5f, 0.5f)
        }

        PclBackgroundSuit.Centered -> place(iw, ih, 0.5f, 0.5f)
        PclBackgroundSuit.TopLeft -> place(iw, ih, 0f, 0f)
        PclBackgroundSuit.TopRight -> place(iw, ih, 1f, 0f)
        PclBackgroundSuit.BottomLeft -> place(iw, ih, 0f, 1f)
        PclBackgroundSuit.BottomRight -> place(iw, ih, 1f, 1f)
    }
}
