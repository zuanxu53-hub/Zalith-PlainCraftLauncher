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

package com.zalith.pcl.game.version.export

import android.content.Context
import android.net.Uri
import com.zalith.pcl.R
import com.zalith.pcl.context.writeLocalFile
import com.zalith.pcl.coroutine.TaskFlowExecutor
import com.zalith.pcl.coroutine.TitledTask
import com.zalith.pcl.coroutine.addTask
import com.zalith.pcl.coroutine.buildPhase
import com.zalith.pcl.game.version.export.platform.CurseForgePackExporter
import com.zalith.pcl.game.version.export.platform.MCBBSPackExporter
import com.zalith.pcl.game.version.export.platform.ModrinthPackExporter
import com.zalith.pcl.game.version.export.platform.MultiMCPackExporter
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.utils.file.zipDirectory
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "PackExporter"

/**
 * 整合包导出器
 * @param exportInfo 要导出的整合包的必要信息
 * @param scope 在有生命周期管理的scope中执行安装任务
 */
class PackExporter(
    val context: Context,
    val exportInfo: ExportInfo,
    private val scope: CoroutineScope,
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val taskFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val exporter: AbstractExporter = when (exportInfo.packType) {
        PackType.MCBBS -> MCBBSPackExporter()
        PackType.Modrinth -> ModrinthPackExporter()
        PackType.CurseForge -> CurseForgePackExporter()
        PackType.MultiMC -> MultiMCPackExporter()
    }

    /**
     * 开始导出整合包
     */
    fun startExport(
        outputUri: Uri,
        version: Version,
        isRunning: () -> Unit = {},
        onFinished: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            isRunning()
            return //正在运行中，拒绝导出
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhases(outputUri, version)
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onFinished()
            },
            onError = { e ->
                onError(e)
            }
        )
    }

    private suspend fun getTaskPhases(
        outputUri: Uri,
        version: Version
    ) = withContext(Dispatchers.IO) {
        val exportCachePath = PathManager.DIR_CACHE_MODPACK_EXPORTER
        val tempPath = File(exportCachePath, "temp")
        // 缓存里的中间文件名必须清洗：`exportInfo.name` 是用户手填的，
        // 带 `/`、`:`、`*` 这类字符（「正式版/1.0」这种写法很常见）时，
        // `FileOutputStream` 会抛 `FileNotFoundException: ... (Not a directory)`，
        // 出口是「导出失败」，而且提示里完全看不出是名字的问题。
        val pack = File(exportCachePath, "${cacheFileName(exportInfo.name)}.${exporter.fileSuffix}")

        listOf(
            buildPhase {
                //清除上一次导出的缓存
                addTask(
                    id = "ExportModpack.Cleanup",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModPackDir()
                    tempPath.createDirAndLog()
                }

                with(exporter) {
                    buildTasks(
                        context = context,
                        version = version,
                        info = exportInfo,
                        tempPath = tempPath
                    )
                }

                addTask(
                    id = "ExportModpack.Pack",
                    title = androidText(R.string.versions_export_task_generate_pack),
                    icon = R.drawable.ic_build_outlined
                ) {
                    zipDirectory(
                        sourceDir = tempPath,
                        outputZipFile = pack,
                        preserveFileTime = false
                    )

                    context.writeLocalFile(
                        inputFile = pack,
                        outputUri = outputUri,
                        mimeType = "application/*"
                    )
                }

                addTask(
                    id = "ExportModpack.Cleanup_Finished",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModPackDir()
                }
            }
        )
    }

    /**
     * 清理临时整合包导出目录
     */
    private suspend fun clearTempModPackDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MODPACK_EXPORTER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary modpack export directory cleared.")
        }
    }

    /**
     * 取消整合包导入
     */
    fun cancel() {
        taskExecutor.cancel()
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}

/**
 * 落盘用的文件名：把文件系统不认的字符换成下划线。
 *
 * 只清洗**中间产物 / 保存用的名字**；写进 `manifest.json` / `modrinth.index.json`
 * 的 `name` 字段仍然是用户原文，不受影响。
 */
private fun cacheFileName(raw: String): String {
    val cleaned = raw
        .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_")
        .trim()
        .trimEnd('.')
    return cleaned.ifBlank { "modpack" }
}