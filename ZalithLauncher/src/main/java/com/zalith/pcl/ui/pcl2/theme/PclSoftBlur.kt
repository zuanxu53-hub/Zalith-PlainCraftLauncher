package com.zalith.pcl.ui.pcl2.theme

import com.zalith.pcl.ui.pcl2.components.PclGaussianBlur
import kotlin.math.roundToInt

/** 工作图保持窗口比例；画质控制像素预算，blur 控制真实卷积强度。 */
internal data class PclSoftBlurPlan(val width: Int, val height: Int, val sigma: Float)

internal object PclSoftBlur {
    fun plan(width: Int, height: Int, quality: PclGlassQuality, blurPx: Float): PclSoftBlurPlan {
        require(width > 0 && height > 0)
        val (divisor, maxDimension) = when (quality) {
            PclGlassQuality.Low -> 4f to 384
            PclGlassQuality.Normal -> 3f to 512
            PclGlassQuality.High -> 2f to 768
        }
        // 两个方向共用缩放比例，不能分别钳位后把宽屏底片挤成方形。
        val scale = minOf(1f / divisor, maxDimension.toFloat() / maxOf(width, height))
        val workWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val workHeight = (height * scale).roundToInt().coerceAtLeast(1)
        val sigma = if (blurPx.isFinite() && blurPx > 0f) {
            (blurPx * scale).coerceIn(0.35f, 21f)
        } else 0f
        return PclSoftBlurPlan(workWidth, workHeight, sigma)
    }

    fun blurPixels(pixels: IntArray, plan: PclSoftBlurPlan) {
        require(pixels.size == plan.width * plan.height)
        if (plan.sigma <= 0f) return
        PclGaussianBlur.convolve(
            pixels, plan.width, plan.height, PclGaussianBlur.gaussianKernel(plan.sigma),
        )
    }
}
