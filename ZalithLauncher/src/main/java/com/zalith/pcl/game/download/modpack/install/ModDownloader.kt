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

package com.zalith.pcl.game.download.modpack.install

import com.zalith.pcl.R
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.game.download.engine.findHttpCode
import com.zalith.pcl.game.version.download.DownloadFailedException
import com.zalith.pcl.game.version.download.DownloadTask
import com.zalith.pcl.game.version.download.currentDownloadThreads
import com.zalith.pcl.game.version.download.runBatchDownloads
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.utils.file.formatFileSize
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.isInterruptedIOException
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ModDownloader"

/**
 * 这个目标文件是不是「可选内容」（资源包 / 光影包）。
 *
 * 判据是目标文件所在目录名 —— `ModFile.outputFile` 已经是整合包里解出来的
 * 真实落盘路径（`.../<版本名>/resourcepacks/xxx.zip`），比拿 URL 猜可靠得多。
 */
private fun File?.isOptionalAsset(): Boolean {
    if (this == null) return false
    return when (parentFile?.name?.lowercase()) {
        "resourcepacks", "shaderpacks" -> true
        else -> false
    }
}

/**
 * 整合包模组下载器
 * 先并发把延迟解析的模组链接解析出来，再交给批量下载引擎执行
 *
 * 关于「下载失败」的分级：
 * `mods/` 里的东西缺一个整合包可能就跑不起来，失败必须报错让人知道；
 * 但 `resourcepacks/`、`shaderpacks/` 只是外观，作者删了源、CDN 改了直链都很常见，
 * 缺几个完全能进游戏。以前这类文件一失败就整包导入失败（截图里 6 个资源包
 * 全部 404，结果整个整合包白装），现在按「可选内容」处理：跳过、记日志、继续装。
 */
class ModDownloader(
    val mods: List<ModFile>,
    private val maxDownloadThreads: Int = currentDownloadThreads
) {
    suspend fun startDownload(task: Task) {
        task.updateMessage(null)

        val resolvedFailures = AtomicInteger(0)
        val missingMods = AtomicInteger(0)
        val missedAssets = AtomicInteger(0)
        val skippedMods = ConcurrentHashMap.newKeySet<String>()
        // 被跳过的可选资源，结束时汇总成一条日志，方便事后手动补
        val skippedAssets = ConcurrentHashMap.newKeySet<String>()
        // 链接解析失败的模组名，结束时写日志方便手动补
        val unresolvedMods = ConcurrentHashMap.newKeySet<String>()
        val tasks = prepareAll(resolvedFailures, missingMods, missedAssets, skippedAssets, unresolvedMods)

        try {
            runBatch(task, tasks, R.string.download_modpack_download_mods, missingMods, missedAssets, skippedMods, skippedAssets)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloadFailedException) {
            // 第一轮有文件下载失败
            // 等整批结束后再对未完成的文件重试一轮
            Logger.error(TAG, "Some mods failed to download, retrying failed mods", e)
            runBatch(task, tasks, R.string.download_modpack_download_mods_retry, missingMods, missedAssets, skippedMods, skippedAssets)
        }

        if (missedAssets.get() > 0) {
            Logger.warning(
                TAG,
                "已跳过 ${missedAssets.get()} 个下载失败的资源包/光影包（不影响进入游戏）：" +
                        skippedAssets.joinToString("、")
            )
        }

        // 少量模组解析失败不应该让整包白装：整合包经常有个别源站下架/限流的文件。
        // 失败过半才说明这个包整体有问题，此时仍然中止。
        val failedCount = resolvedFailures.get()
        if (failedCount > 0) {
            val fatal = failedCount * 2 >= mods.size
            Logger.warning(
                TAG,
                "无法解析 $failedCount/${mods.size} 个模组的下载链接：" +
                        unresolvedMods.joinToString("、") +
                        if (fatal) "（超过一半，中止安装）" else "（已跳过，继续安装）"
            )
            if (fatal) {
                throw DownloadFailedException("$failedCount mods failed to resolve download links")
            }
        }

        val missing = missingMods.get()
        if (missing > 0) {
            val fatal = missing * 2 >= mods.size
            Logger.warning(
                TAG,
                "有 $missing/${mods.size} 个模组下载失败：" +
                        skippedMods.joinToString("、") { File(it).name } +
                        if (fatal) "（超过一半，中止安装）" else "（已跳过，继续安装）"
            )
            if (fatal) {
                throw DownloadFailedException("$missing mods failed to download")
            }
        }

        task.updateProgress(1f)
        task.updateMessage(null)
    }

    /** 执行一轮批量下载，本地已校验可复用的文件由引擎自动跳过 */
    private suspend fun runBatch(
        task: Task,
        tasks: List<DownloadTask>,
        messageRes: Int,
        missingMods: AtomicInteger,
        missedAssets: AtomicInteger,
        skippedMods: MutableSet<String>,
        skippedAssets: MutableSet<String>
    ) {
        task.runBatchDownloads(
            tasks = tasks,
            maxConnections = maxDownloadThreads,
            retryRounds = 1,
            onSnapshot = { snapshot ->
                task.updateSpeed(snapshot.speedBytesPerSec)
                task.updateMessage(
                    androidText(
                        messageRes,
                        snapshot.downloadedFiles + missingMods.get(),
                        snapshot.totalFiles + missingMods.get(),
                        formatFileSize(snapshot.downloadedBytes)
                    )
                )
            },
            acceptFailure = { downloadTask, error ->
                val code = error.findHttpCode()
                // 404（源端下架）跳过；资源包 / 光影包无论什么原因失败都跳过 ——
                // 少几个资源包不该让整个整合包装不上
                // 和 Zalith 一样：下载失败不整包中止，先跳过并记录；
                // 失败过半才说明包整体不可用，由 startDownload 统一判定。
                val optional = downloadTask.targetFile.isOptionalAsset()
                // 跨轮次按目标文件判重，避免同一个文件在重试轮被重复计数
                val path = downloadTask.targetFile.absolutePath
                if (skippedMods.add(path)) {
                    if (optional) {
                        missedAssets.incrementAndGet()
                        skippedAssets.add(downloadTask.targetFile.name)
                    } else {
                        missingMods.incrementAndGet()
                    }
                }
                true
            }
        )
    }

    /** 并发解析全部模组的下载信息，解析失败的记入失败计数 */
    private suspend fun prepareAll(
        resolvedFailures: AtomicInteger,
        missingMods: AtomicInteger,
        missedAssets: AtomicInteger,
        skippedAssets: MutableSet<String>,
        unresolvedMods: MutableSet<String>,
    ): List<DownloadTask> =
        coroutineScope {
            val semaphore = Semaphore(maxDownloadThreads)
            mods.map { mod ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            prepare(mod)
                        } catch (_: CancellationException) {
                            throw CancellationException("mod link resolving cancelled")
                        } catch (_: FileNotFoundException) {
                            //已在源端下架（404）的模组会被跳过而不是终止安装
                            countMissing(mod, missingMods, missedAssets, skippedAssets)
                            null
                        } catch (_: NotFoundException) {
                            //已在源端下架（404）的模组会被跳过而不是终止安装
                            countMissing(mod, missingMods, missedAssets, skippedAssets)
                            null
                        } catch (e: Exception) {
                            if (e.isInterruptedIOException()) throw CancellationException("cancelled", e)
                            Logger.error(TAG, "Failed to resolve mod links: ${mod.outputFile?.name}", e)
                            // 链接解析不出来也一样：资源包/光影包失联不算致命
                            if (mod.outputFile.isOptionalAsset()) {
                                countOptional(mod, missedAssets, skippedAssets)
                            } else {
                                mod.outputFile?.name?.let { unresolvedMods.add(it) }
                                resolvedFailures.incrementAndGet()
                            }
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

    /**
     * 记一个「下不到」的文件：资源包 / 光影包算可选，`mods/` 里的算缺失。
     *
     * 两者分开计数，是因为它们对整合包的后果完全不同 ——
     * 少一个模组可能直接进不去游戏，少一个资源包只是外观不对。
     */
    private fun countMissing(
        mod: ModFile,
        missingMods: AtomicInteger,
        missedAssets: AtomicInteger,
        skippedAssets: MutableSet<String>,
    ) {
        if (mod.outputFile.isOptionalAsset()) {
            countOptional(mod, missedAssets, skippedAssets)
        } else {
            missingMods.incrementAndGet()
        }
    }

    private fun countOptional(
        mod: ModFile,
        missedAssets: AtomicInteger,
        skippedAssets: MutableSet<String>,
    ) {
        missedAssets.incrementAndGet()
        mod.outputFile?.name?.let { skippedAssets.add(it) }
    }

    /**
     * 解析单个模组的下载信息（CurseForge 包常只有 projectID/fileID，必须现场请求）
     * 只有已经拿到直链的 ModFile 的 getFile 才是 null。
     */
    private suspend fun prepare(mod: ModFile): DownloadTask {
        val file = mod.getFile?.invoke() ?: mod
        val urls = file.downloadUrls ?: throw FileNotFoundException("No download url for ${file.outputFile}")
        val output = file.outputFile ?: throw FileNotFoundException("No target file for mod")

        return DownloadTask(
            urls = urls,
            verifyIntegrity = true,
            targetFile = output,
            sha1 = file.sha1
        )
    }
}
