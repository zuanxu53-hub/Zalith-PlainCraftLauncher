/*
 * 移植自 Kyant 的 AndroidLiquidGlass（Apache-2.0）
 *
 * 原仓库用 `expect/actual` 提供 awaitFrame（skiko 走另一套实现）；
 * 本工程只有 Android 端，直接落成实际实现。
 */

package com.zalith.pcl.ui.pcl2.glass

import kotlinx.coroutines.android.awaitFrame as androidAwaitFrame

/** 等到下一帧（动画里用来逐帧推进）。 */
suspend fun awaitFrame() {
    androidAwaitFrame()
}
