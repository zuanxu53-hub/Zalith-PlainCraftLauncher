/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.zalith.pcl.bridge

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import androidx.annotation.Keep
import androidx.core.net.toUri
import com.zalith.pcl.BuildKeys
import com.zalith.pcl.context.GlobalContext
import com.zalith.pcl.filemanager.FileManagerLauncher
import com.zalith.pcl.game.launch.Launcher
import com.zalith.pcl.utils.file.shareFile
import com.zalith.pcl.utils.killProgress
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.openLink
import java.io.File

private const val TAG = "ZLNativeInvoker"

@Keep
object ZLNativeInvoker {
    @JvmStatic
    var staticLauncher: Launcher? = null

    @Keep
    @JvmStatic
    fun openLink(link: String) {
        (GlobalContext as? Activity)?.let { activity ->
            Logger.info(TAG, "collect link: $link")
            activity.runOnUiThread {
                // 本地路径也走这里：游戏里的「打开资源包文件夹 / 打开截图文件夹」
                // 在 Linux 上是 `xdg-open <路径>`，被 `java_exec_hooks.c` 拦下来后
                // 原样丢进 openLink。以前只有 `file:` 开头的才算本地路径，
                // 裸的 `/storage/emulated/0/...` 会被当成网址交给 ACTION_VIEW，
                // 没有 scheme 的 Uri 没有任何应用能接 → 表现就是「点了没反应」。
                val localPath = localPathOf(link)
                if (localPath != null) {
                    Logger.info(TAG, "open link: $localPath")

                    val file = File(localPath)
                    if (file.isDirectory || link.endsWith('/')) {
                        //可能是一个目录，创建并发起浏览目录请求
                        file.mkdirs()
                        // 原生界面在跑时交给它的文件管理器；PCL2 界面 / 后台时
                        // staticLauncher 是 null，直接开启动器自己的文件管理器兜底
                        val opened = staticLauncher?.let {
                            runCatching { it.openPath(file) }.isSuccess
                        } == true
                        if (!opened) {
                            runCatching { FileManagerLauncher.launch(activity, file.absolutePath) }
                        }
                    } else {
                        shareFile(activity, file)
                        Logger.info(TAG, "In-game Share File: ${file.absolutePath}")
                    }
                } else {
                    activity.openLink(link, "*/*")
                }
            }
        }
    }

    /**
     * 这个字符串是不是一个本地路径（`file:` URI 或者裸的绝对路径）。
     *
     * 只有「确实存在」的绝对路径才算 —— 否则 `/` 开头的网址会被误判成文件。
     */
    private fun localPathOf(link: String): String? {
        if (link.startsWith("file:")) return formatFilePath(link)
        if (link.startsWith("/") && File(link).exists()) return link
        return null
    }

    /**
     * 格式化文件路径
     */
    private fun formatFilePath(input: String): String? {
        return try {
            val uri = input.toUri()
            if (uri.scheme == "file") {
                uri.path
            } else {
                null
            }
        } catch (_: Exception) {
            when {
                input.startsWith("file:") -> {
                    input
                        .replace(Regex("^file:/+"), "/")
                        .replace("%20", " ")
                }
                else -> null
            }
        }
    }

    @Keep
    @JvmStatic
    fun querySystemClipboard() {
        (GlobalContext as? Activity)?.let { activity ->
            activity.runOnUiThread {
                val clipData = (activity.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.primaryClip ?: run {
                    ZLBridge.clipboardReceived(null, null)
                    return@runOnUiThread
                }
                val clipItemText = clipData.getItemAt(0).text ?: run {
                    ZLBridge.clipboardReceived(null, null)
                    return@runOnUiThread
                }
                ZLBridge.clipboardReceived(clipItemText.toString(), "plain")
            }
        }
    }

    @Keep
    @JvmStatic
    fun putClipboardData(data: String, mimeType: String) {
        (GlobalContext as? Activity)?.let { activity ->
            activity.runOnUiThread {
                val clipData = when (mimeType) {
                    "text/plain" -> ClipData.newPlainText(BuildKeys.LAUNCHER_IDENTIFIER, data)
                    "text/html" -> ClipData.newHtmlText(BuildKeys.LAUNCHER_IDENTIFIER, data, data)
                    else -> null
                }
                clipData?.let {
                    (activity.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(it)
                }
            }
        }
    }

    @Keep
    @JvmStatic
    fun jvmExit(exitCode: Int, isSignal: Boolean) {
        staticLauncher?.exit()
        staticLauncher?.onExit?.invoke(exitCode, isSignal)
        staticLauncher = null
        killProgress()
    }
}