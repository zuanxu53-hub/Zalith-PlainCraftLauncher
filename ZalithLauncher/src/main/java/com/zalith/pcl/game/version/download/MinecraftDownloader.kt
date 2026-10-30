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

package com.zalith.pcl.game.version.download

import android.content.Context
import com.zalith.pcl.R
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TitledTask
import com.zalith.pcl.game.path.getGameHome
import com.zalith.pcl.game.versioninfo.models.GameManifest
import com.zalith.pcl.game.versioninfo.models.VersionManifest
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.utils.file.formatFileSize
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.string.getMessageOrToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "MinecraftDownloader"

/** 单个安装流程的最大并发下载连接数（兜底默认值；实际值取设置里的「最大线程数」） */
const val DEFAULT_DOWNLOAD_THREADS = 8

/**
 * 当前生效的最大下载线程数（设置 → 下载 → 最大线程数）。
 *
 * 下载器在构造时读一次，所以改完设置**下一次下载**就生效，不需要重启。
 * 限制在 1..128：[AllSettings.downloadThreads] 本身有范围，这里再钳一次是防止
 * 设置文件被外部改坏（例如写进 0）导致信号量永远拿不到许可、下载直接卡死。
 */
val currentDownloadThreads: Int
    get() = AllSettings.downloadThreads.getValue().coerceIn(1, 128)

/**
 * Minecraft 安装器：装配版本 JSON、client jar、assets 与 libraries 的下载任务，
 * 并交由批量下载引擎执行
 * 分块并发、自动换源、失败整轮重试
 */
class MinecraftDownloader(
    private val context: Context,
    private val version: String,
    private val customName: String = version,
    private val gameHome: String = getGameHome(),
    private val downloader: BaseMinecraftDownloader = BaseMinecraftDownloader(gameHome),
    private val mode: DownloadMode = DownloadMode.DOWNLOAD,
    private val onCompletion: suspend (Task) -> Unit = {},
    private val onError: (message: String) -> Unit = {},
    private val onThrowable: ((throwable: Throwable) -> Unit)? = null,
    private val maxDownloadThreads: Int = currentDownloadThreads
) {
    private var allDownloadTasks = mutableListOf<DownloadTask>()

    /** 分阶段下载时缓存的版本清单：Json 任务解析一次，支持库/资源任务直接复用 */
    private var phasedManifest: GameManifest? = null

    private fun <R> getTaskMessage(
        download: R,
        verify: R
    ): R =
        when (mode) {
            DownloadMode.DOWNLOAD -> download
            DownloadMode.VERIFY_AND_REPAIR -> verify
        }

    /**
     * 自定义 client 目录 ->client<-/versions/..
     */
    fun getDownloadTask(
        clientName: String = this.customName,
        clientVersionsDir: File = downloader.versionsTarget
    ): Task {
        return Task.runTask(
            id = DOWNLOADER_TAG,
            dispatcher = Dispatchers.Default,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(
                    getTaskMessage(androidText(R.string.minecraft_download_stat_download_task), null)
                )
                if (mode == DownloadMode.DOWNLOAD) {
                    progressNewDownloadTasks(clientName, clientVersionsDir)
                } else {
                    val jsonFile = downloader.getVersionJsonPath(customName).takeIf { it.canRead() } ?: throw IOException("Version $customName JSON file is unreadable.")
                    val jsonText = jsonFile.readText()
                    val gameManifest = jsonText.parseTo(GameManifest::class.java)
                    progressDownloadTasks(gameManifest, clientName)
                }

                if (allDownloadTasks.isNotEmpty()) {
                    task.runBatchDownloads(
                        tasks = allDownloadTasks,
                        maxConnections = maxDownloadThreads,
                        retryRounds = 1,
                        onSnapshot = { snapshot ->
                            task.updateSpeed(snapshot.speedBytesPerSec)
                            task.updateMessage(androidText(
                                getTaskMessage(R.string.minecraft_download_downloading_game_files, R.string.minecraft_download_verifying_and_repairing_files),
                                snapshot.downloadedFiles, snapshot.totalFiles,
                                formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                            ))
                        }
                    )
                }
                //清除任务信息
                task.updateProgress(1f)
                task.updateMessage(null)

                onCompletion(task)
            },
            onError = { e ->
                Logger.error(TAG, "Failed to download Minecraft!", e)
                if (onThrowable != null) {
                    onThrowable(e)
                } else {
                    val message = when(e) {
                        is CancellationException -> return@runTask
                        is FileNotFoundException -> context.getString(R.string.minecraft_download_failed_notfound)
                        else -> e.getMessageOrToString()
                    }
                    onError(message)
                }
            }
        )
    }

    /**
     * 仅将 Jar、Json 文件安装到自定义版本目录中
     */
    private suspend fun progressNewDownloadTasks(
        clientName: String,
        clientVersionsDir: File
    ) {
        val gameManifest = downloader.findVersion(this.version)?.let {
            downloader.createVersionJson(it, clientName, clientVersionsDir)
        } ?: throw IllegalArgumentException("Version not found: $version")

        commonScheduleDownloads(gameManifest, null, clientName, clientVersionsDir)
    }

    /**
     * PCL2 版式的分阶段原版下载任务：三个独立任务行
     *   1. 下载原版 Json 文件（版本 Json + client jar）
     *   2. 下载原版支持库文件（libraries）
     *   3. 下载原版资源文件（assets）
     *
     * 三行加入**同一个阶段**后由执行器顺序运行（见 TaskFlowExecutor.executePhases
     * 的 for 循环），所以后两行可以直接依赖第一行解析出的 manifest。
     * 校验修复（VERIFY_AND_REPAIR）不适用：它没有「先下 Json」的顺序约束，继续走单任务。
     */
    fun getPhasedDownloadTasks(
        clientName: String = this.customName,
        clientVersionsDir: File = downloader.versionsTarget
    ): List<TitledTask> {
        phasedManifest = null

        // ---- 1. 版本 Json 文件 + client jar
        val jsonTask = Task.runTask(
            id = "Download.Vanilla.Json",
            dispatcher = Dispatchers.Default,
            task = { task ->
                task.updateProgress(-1f)
                val gameManifest = downloader.findVersion(version)?.let {
                    downloader.createVersionJson(it, clientName, clientVersionsDir)
                } ?: throw IllegalArgumentException("Version not found: $version")
                phasedManifest = gameManifest

                val jarTasks = mutableListOf<DownloadTask>()
                downloader.loadClientJarDownload(
                    gameManifest = gameManifest,
                    clientName = clientName,
                    mcFolder = clientVersionsDir,
                    scheduleDownload = { urls, hash, targetFile, size ->
                        jarTasks.add(
                            DownloadTask(
                                urls = urls, verifyIntegrity = true, targetFile = targetFile,
                                sha1 = hash, size = size
                            )
                        )
                    },
                    scheduleCopy = {}
                )
                if (jarTasks.isNotEmpty()) {
                    task.runBatchDownloads(
                        tasks = jarTasks,
                        maxConnections = maxDownloadThreads,
                        retryRounds = 1,
                        onSnapshot = { snapshot ->
                            task.updateSpeed(snapshot.speedBytesPerSec)
                            task.updateMessage(androidText(
                                R.string.minecraft_download_downloading_game_files,
                                snapshot.downloadedFiles, snapshot.totalFiles,
                                formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                            ))
                        }
                    )
                }
                task.updateProgress(1f)
                task.updateMessage(null)
            }
        )

        // ---- 2. 支持库文件
        val libraryTask = Task.runTask(
            id = "Download.Vanilla.Libraries",
            dispatcher = Dispatchers.Default,
            task = { task ->
                val gameManifest = phasedManifest ?: throw IllegalStateException("Version manifest is not ready")
                val libTasks = mutableListOf<DownloadTask>()
                downloader.loadLibraryDownloads(gameManifest) { urls, hash, targetFile, size, isDownloadable ->
                    libTasks.add(
                        DownloadTask(
                            urls = urls, verifyIntegrity = true, targetFile = targetFile,
                            sha1 = hash, size = size, isDownloadable = isDownloadable
                        )
                    )
                }
                if (libTasks.isNotEmpty()) {
                    task.runBatchDownloads(
                        tasks = libTasks,
                        maxConnections = maxDownloadThreads,
                        retryRounds = 1,
                        onSnapshot = { snapshot ->
                            task.updateSpeed(snapshot.speedBytesPerSec)
                            task.updateMessage(androidText(
                                R.string.minecraft_download_downloading_game_files,
                                snapshot.downloadedFiles, snapshot.totalFiles,
                                formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                            ))
                        }
                    )
                }
                task.updateProgress(1f)
                task.updateMessage(null)
            }
        )

        // ---- 3. 资源文件
        val assetsTask = Task.runTask(
            id = "Download.Vanilla.Assets",
            dispatcher = Dispatchers.Default,
            task = { task ->
                val gameManifest = phasedManifest ?: throw IllegalStateException("Version manifest is not ready")
                val assetIndex = downloader.createAssetIndex(downloader.assetIndexTarget, gameManifest)
                val assetTasks = mutableListOf<DownloadTask>()
                downloader.loadAssetsDownload(assetIndex) { urls, hash, targetFile, size ->
                    assetTasks.add(
                        DownloadTask(
                            urls = urls, verifyIntegrity = true, targetFile = targetFile,
                            sha1 = hash, size = size
                        )
                    )
                }
                if (assetTasks.isNotEmpty()) {
                    task.runBatchDownloads(
                        tasks = assetTasks,
                        maxConnections = maxDownloadThreads,
                        retryRounds = 1,
                        onSnapshot = { snapshot ->
                            task.updateSpeed(snapshot.speedBytesPerSec)
                            task.updateMessage(androidText(
                                R.string.minecraft_download_downloading_game_files,
                                snapshot.downloadedFiles, snapshot.totalFiles,
                                formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                            ))
                        }
                    )
                }
                task.updateProgress(1f)
                task.updateMessage(null)
            }
        )

        return listOf(
            TitledTask(title = androidText(R.string.download_game_install_vanilla_json), task = jsonTask),
            TitledTask(title = androidText(R.string.download_game_install_vanilla_libraries), task = libraryTask),
            TitledTask(title = androidText(R.string.download_game_install_vanilla_assets), task = assetsTask),
        )
    }

    private suspend fun progressDownloadTasks(
        gameManifest: GameManifest,
        clientName: String,
        clientVersionsDir: File = downloader.versionsTarget
    ) {
        val inheritsFrom = downloader.takeIf {
            gameManifest.inheritsFrom != null
        }?.findVersion(gameManifest.inheritsFrom)

        //优先尝试解析原版
        inheritsFrom?.let {
            downloader.createVersionJson(it)
        }?.let { gameManifest1 ->
            progressDownloadTasks(gameManifest1, gameManifest.inheritsFrom)
        }

        commonScheduleDownloads(
            gameManifest = gameManifest,
            inheritsFrom = inheritsFrom,
            clientName = clientName,
            clientVersionsDir = clientVersionsDir
        )
    }

    private suspend fun commonScheduleDownloads(
        gameManifest: GameManifest,
        inheritsFrom: VersionManifest.Version? = null,
        clientName: String,
        clientVersionsDir: File
    ) {
        val assetsIndex = downloader.createAssetIndex(downloader.assetIndexTarget, gameManifest)

        downloader.loadClientJarDownload(
            gameManifest = gameManifest,
            clientName = clientName,
            mcFolder = clientVersionsDir,
            scheduleDownload = { urls, hash, targetFile, size ->
                scheduleDownload(urls, hash, targetFile, size)
            },
            scheduleCopy = { targetFile ->
                inheritsFrom?.let { inheritsFrom ->
                    val inheritsJar = downloader.getVersionJarPath(inheritsFrom.id)

                    allDownloadTasks.find {
                        it.targetFile.absolutePath == inheritsJar.absolutePath
                    }?.let { task ->
                        task.fileDownloadedTask = {
                            if (!targetFile.exists() && inheritsJar.exists()) {
                                inheritsJar.copyTo(targetFile, overwrite = true)
                                Logger.info(TAG, "Copied ${inheritsJar.absolutePath} to ${targetFile.absolutePath}")
                            }
                        }
                    }
                }
            }
        )
        downloader.loadAssetsDownload(assetsIndex) { urls, hash, targetFile, size ->
            scheduleDownload(urls, hash, targetFile, size)
        }
        downloader.loadLibraryDownloads(gameManifest) { urls, hash, targetFile, size, isDownloadable ->
            scheduleDownload(urls, hash, targetFile, size, isDownloadable)
        }
    }

    /**
     * 提交计划下载
     */
    private fun scheduleDownload(urls: List<String>, sha1: String?, targetFile: File, size: Long, isDownloadable: Boolean = true) {
        allDownloadTasks.add(
            DownloadTask(
                urls = urls,
                verifyIntegrity = true,
                targetFile = targetFile,
                sha1 = sha1,
                size = size,
                isDownloadable = isDownloadable
            )
        )
    }
}
