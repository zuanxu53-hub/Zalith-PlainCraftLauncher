package com.zalith.pcl.ui.pcl2

import com.zalith.pcl.ui.pcl2.theme.PclGlassQuality
import com.zalith.pcl.ui.pcl2.theme.PclSoftBlur
import com.zalith.pcl.ui.pcl2.theme.PclSoftBlurPlan
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PclSoftBlurTest {
    @Test fun highQualityKeepsDetailBeforeBlurring() {
        val plan = PclSoftBlur.plan(1920, 1080, PclGlassQuality.High, 24f)
        assertEquals(768, plan.width)
        assertEquals(432, plan.height)
        assertEquals(9.6f, plan.sigma, 0.001f)
    }

    @Test fun largeAndRotatedWindowsKeepTheirAspectRatio() {
        for (quality in PclGlassQuality.entries) {
            val landscape = PclSoftBlur.plan(3840, 2160, quality, 18f)
            val portrait = PclSoftBlur.plan(2160, 3840, quality, 18f)
            assertEquals(landscape.width, portrait.height)
            assertEquals(landscape.height, portrait.width)
            assertTrue(abs(landscape.width.toFloat() / landscape.height - 3840f / 2160f) < 0.01f)
            assertTrue(maxOf(landscape.width, landscape.height) <= 768)
        }
    }

    @Test fun blurParameterChangesSpreadAtEveryQuality() {
        for (quality in PclGlassQuality.entries) {
            val light = PclSoftBlur.plan(1080, 640, quality, 8f)
            val strong = PclSoftBlur.plan(1080, 640, quality, 24f)
            assertEquals(light.width, strong.width)
            assertEquals(light.height, strong.height)
            assertEquals(light.sigma * 3f, strong.sigma, 0.001f)
        }
    }

    @Test fun zeroBlurPreservesPixelsExactly() {
        val plan = PclSoftBlur.plan(64, 32, PclGlassQuality.High, 0f)
        val pixels = IntArray(plan.width * plan.height) { if (it % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        val expected = pixels.copyOf()
        PclSoftBlur.blurPixels(pixels, plan)
        assertArrayEquals(expected, pixels)
    }

    @Test fun hardWallpaperEdgeBecomesSmoothInsteadOfPixelBlocks() {
        val plan = PclSoftBlur.plan(256, 128, PclGlassQuality.High, 18f)
        val pixels = IntArray(plan.width * plan.height) { index ->
            if (index % plan.width < plan.width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        PclSoftBlur.blurPixels(pixels, plan)
        val row = (0 until plan.width).map { pixels[plan.height / 2 * plan.width + it] and 255 }
        assertTrue("应当有连续过渡，不能仅放大两个色块", row.count { it in 1..254 } > 30)
        assertTrue("邻像素不能留下马赛克台阶", row.zipWithNext().maxOf { abs(it.second - it.first) } < 16)
        assertTrue("边缘必须单调平滑", row.zipWithNext().all { it.second >= it.first })
        assertTrue("模糊后仍保持不透明，不能出现黑边", pixels.all { it ushr 24 == 255 })
    }

    @Test fun uniformWallpaperDoesNotChangeBrightness() {
        val plan = PclSoftBlurPlan(64, 32, 4f)
        val pixels = IntArray(plan.width * plan.height) { 0xFFABCDEF.toInt() }
        PclSoftBlur.blurPixels(pixels, plan)
        assertTrue(pixels.all { it == 0xFFABCDEF.toInt() })
    }

    @Test fun tinyWindowStillHasValidDimensions() {
        for (quality in PclGlassQuality.entries) {
            val plan = PclSoftBlur.plan(1, 1, quality, 18f)
            assertEquals(1, plan.width)
            assertEquals(1, plan.height)
            val pixels = intArrayOf(0xFF224466.toInt())
            PclSoftBlur.blurPixels(pixels, plan)
            assertEquals(0xFF224466.toInt(), pixels[0])
        }
    }
}
