/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「皮肤素材」—— 主题协议的第三层：让插件用**图片**换掉控件的表面。
 *
 * 前两层分别是：`ui.json` 的配色/玻璃参数（换材质）、`pages.json` 的积木（加内容）。
 * 这一层解决的是「按钮 / 卡片长得不像 PCL」的需求 —— 插件直接给一张图，
 * 启动器把它当作该角色的表面。
 *
 * 素材放在插件 APK 的 `assets/zplugin/res/`，安装时原样复制到 `<插件目录>/res/`，
 * `ui.json` 里用**相对路径**引用：
 * ```json
 * "assets": {
 *   "background": "res/bg.webp",
 *   "titleLogo":  "res/logo.png",
 *   "card":   { "src": "res/card.png", "slice": 20 },
 *   "button": { "src": "res/btn.png",  "slice": 24 }
 * }
 * ```
 *
 * 为什么需要 `slice`（九宫格）：
 *   按钮和卡片的宽高是**动态**的（文字长度、屏幕宽度都会变），
 *   一张位图直接拉伸会把圆角和描边拉花。九宫格把图切成 3×3，
 *   只拉伸中间的行/列，四个角保持原样 —— 这是所有 UI 框架处理这类素材的标准做法。
 *   `slice` 的单位是**源图像素**，取「圆角半径 + 描边宽度」那一圈就够了。
 *
 * 安全：路径必须落在插件目录内（`canonicalFile` 前缀校验），`..` 出不去。
 */

package com.zalith.pcl.ui.pcl2.theme

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import java.io.File

/** 一份素材：相对插件目录的路径 + 九宫格切片。 */
data class PclAssetSpec(
    /** 相对插件目录的路径，一般是 `res/xxx.png`。 */
    val src: String,
    /**
     * 九宫格切片宽度（**源图像素**）。0 = 整张图直接拉伸。
     *
     * 取「圆角 + 描边」那一圈的厚度即可；给大了中间可拉伸的区域会变窄，
     * 控件比两倍切片还小时会退回纯拉伸。
     */
    val slice: Int = 0,
)

/**
 * 素材解码缓存。
 *
 * 不复用 `PclImageLoader`：那个是给背景图用的 LRU（只留 3 张），
 * 而素材是「按钮 / 卡片 / 图标」这种**每帧都要用**的小图，
 * 几张一起用就会互相挤掉，然后每帧解码一次 —— 那会直接掉帧。
 * 素材总量很小（通常 < 10 张），全留着的代价反而更低。
 */
internal object PclAssetLoader {
    private val cache = HashMap<String, ImageBitmap?>()
    private val lock = Any()

    /** 把 `ui.json` 里的相对路径解析成真实文件，并挡住越界路径。 */
    fun resolve(spec: PclAssetSpec?): File? {
        if (spec == null) return null
        val dir = PclUiRuntime.assetDir ?: return null
        val base = runCatching { dir.canonicalFile }.getOrNull() ?: return null
        val target = runCatching { File(base, spec.src).canonicalFile }.getOrNull() ?: return null
        if (!target.path.startsWith(base.path + File.separator)) return null
        return target.takeIf { it.isFile }
    }

    fun load(spec: PclAssetSpec?): ImageBitmap? {
        if (spec == null) return null

        val file = resolve(spec) ?: return null
        // 带上修改时间：插件升级后图片换了，缓存会自动失效
        val key = file.absolutePath + "|" + file.lastModified()
        synchronized(lock) { if (cache.containsKey(key)) return cache[key] }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }.getOrNull()
        synchronized(lock) { cache[key] = bitmap }
        return bitmap
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }
}

/**
 * 用素材画表面。图片缺失 / 解码失败时**什么都不画**，调用方应保留原来的降级路径。
 *
 * @param shape 会把图片裁成这个形状，素材本身不需要带圆角
 */
fun Modifier.pclImageSurface(image: ImageBitmap, spec: PclAssetSpec, shape: Shape): Modifier = composed {

    drawWithCache {
        val bmp = image.asAndroidBitmap()
        val outline = shape.createOutline(size, layoutDirection, this)
        val clip = Path().apply { addOutline(outline) }.asAndroidPath()
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        // 切片不能超过图的三分之一，否则中间没有可拉伸的部分了
        val slice = spec.slice.coerceIn(0, minOf(bmp.width, bmp.height) / 3)

        onDrawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@onDrawBehind
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.clipPath(clip)

            if (slice <= 0) {
                canvas.drawBitmap(bmp, null, RectF(0f, 0f, w, h), paint)
            } else {
                val s = slice.toFloat()
                val sw = bmp.width.toFloat()
                val sh = bmp.height.toFloat()
                // 控件比两倍切片还小时，把切片压到一半，让中间段退化为 0（纯拉伸）
                val left = minOf(s, w / 2f)
                val right = maxOf(w - s, w / 2f)
                val top = minOf(s, h / 2f)
                val bottom = maxOf(h - s, h / 2f)
                val xs = floatArrayOf(0f, s, sw - s, sw)
                val ys = floatArrayOf(0f, s, sh - s, sh)
                val xd = floatArrayOf(0f, left, right, w)
                val yd = floatArrayOf(0f, top, bottom, h)
                for (col in 0..2) {
                    for (row in 0..2) {
                        val dst = RectF(xd[col], yd[row], xd[col + 1], yd[row + 1])
                        if (dst.width() <= 0f || dst.height() <= 0f) continue
                        // 源矩形必须是 Rect（整型）：Canvas 只有 (Rect, RectF) 这个重载
                        val src = Rect(
                            xs[col].toInt().coerceIn(0, bmp.width),
                            ys[row].toInt().coerceIn(0, bmp.height),
                            xs[col + 1].toInt().coerceIn(0, bmp.width),
                            ys[row + 1].toInt().coerceIn(0, bmp.height),
                        )
                        if (src.width() <= 0 || src.height() <= 0) continue
                        canvas.drawBitmap(bmp, src, dst, paint)
                    }
                }
            }
            canvas.restore()
        }
    }
}
