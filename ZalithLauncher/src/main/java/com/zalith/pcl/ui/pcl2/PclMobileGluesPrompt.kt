/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 首次启动（自动解压完成后）检查 MobileGlues 渲染器插件是否安装。
 * 它必须作为独立 APK 插件存在，启动器不能把渲染库直接编进来，所以这里只负责
 * 发现缺失并询问，用户同意后打开官方发布页；装完系统会广播，下次进渲染器列表即可选到。
 */

package com.zalith.pcl.ui.pcl2

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog

private const val MOBILE_GLUES_PACKAGE = "com.fcl.plugin.mobileglues"
private const val MOBILE_GLUES_RELEASES =
    "https://github.com/MobileGL-Dev/MobileGlues-release/releases"

private fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

@Composable
fun PclMobileGluesPromptHost() {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("pcl2.mobileglues", Context.MODE_PRIVATE)
        if (prefs.getBoolean("prompted", false)) return@LaunchedEffect
        if (isPackageInstalled(context, MOBILE_GLUES_PACKAGE)) return@LaunchedEffect
        visible = true
    }

    if (visible) {
        PclConfirmDialog(
            title = "未检测到 MobileGlues 渲染器",
            content = "MobileGlues 是独立 APK 渲染器插件，启动器不能把它的原生渲染库直接打包进来。\n\n" +
                "安装官方插件后，它才会出现在「设置 → 控制设置 → 渲染器」列表里。" +
                "是否现在前往官方发布页下载安装？",
            confirmText = "前往下载",
            dismissText = "暂不",
            onConfirm = {
                visible = false
                context.getSharedPreferences("pcl2.mobileglues", Context.MODE_PRIVATE)
                    .edit().putBoolean("prompted", true).apply()
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(MOBILE_GLUES_RELEASES))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            },
            onDismiss = {
                visible = false
                context.getSharedPreferences("pcl2.mobileglues", Context.MODE_PRIVATE)
                    .edit().putBoolean("prompted", true).apply()
            },
        )
    }
}
