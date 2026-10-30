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

package com.zalith.pcl.components.jre

import android.content.Context
import android.content.res.AssetManager
import com.zalith.pcl.ZLApplication
import com.zalith.pcl.components.AbstractUnpackTask
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.utils.device.Architecture
import com.zalith.pcl.utils.file.readString
import com.zalith.pcl.utils.logging.Logger
import java.io.InputStream

private const val TAG = "UnpackJreTask"

/** 每个内置运行环境都必带的主体包名（与分架构的 `bin-<abi>.tar.xz` 配套）。 */
private const val UNIVERSAL_PACK = "universal.tar.xz"

class UnpackJreTask(
    private val context: Context,
    private val jre: Jre
) : AbstractUnpackTask() {
    private lateinit var assetManager: AssetManager
    private lateinit var launcherRuntimeVersion: String
    private var isCheckFailed: Boolean = false

    init {
        runCatching {
            assetManager = context.assets
            if (!isJreArchSupported()) {
                //当前设备架构不支持使用这个环境
                isCheckFailed = true
                return@runCatching
            }
            launcherRuntimeVersion = assetManager.open(jre.jrePath + "/version").readString()
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to init jre version. assetsPath=${jre.jrePath}/version", e)
            isCheckFailed = true
        }
    }

    fun isCheckFailed() = isCheckFailed

    override fun checkState(): InstallableItem.State {
        if (isCheckFailed) return InstallableItem.State.NOT_EXISTS

        return runCatching {
            val installedRuntimeVersion = RuntimesManager.loadInternalRuntimeVersion(jre.jreName)
            when {
                //未安装该环境
                installedRuntimeVersion == null -> InstallableItem.State.NOT_STARTED

                launcherRuntimeVersion != installedRuntimeVersion -> InstallableItem.State.PENDING

                //版本标记对得上，但运行库本身是废的（上次解压不完整，例如缺 release）→
                //先试着补好（补 release 即可，不用重解压），补不好再要求重新解压。
                //否则它会一直被当成「已安装」，而安装加载器 / 启动游戏全都用不了
                !RuntimesManager.repairRuntime(jre.jreName) -> {
                    Logger.warning(
                        TAG,
                        "Internal runtime ${jre.jreName} is not usable " +
                                "(${RuntimesManager.describeRuntimeProblem(jre.jreName)}), re-unpacking it."
                    )
                    InstallableItem.State.NOT_STARTED
                }

                else -> InstallableItem.State.FINISHED
            }
        }.onFailure { e ->
            Logger.error("CheckJre", "An exception occurred while detecting the Java Runtime.", e)
        }.getOrElse {
            //检查失败，要求重新进行安装
            InstallableItem.State.NOT_STARTED
        }
    }

    private fun isJreArchSupported(): Boolean {
        return runCatching {
            val allPacks = assetManager.list(jre.jrePath) ?: return@runCatching false
            //检查是否包含符合当前设备架构的环境
            val runtime = getRuntimeByArch()
            val supported = allPacks.contains(runtime).also {
                Logger.info(TAG, "Device requires environment: ${jre.jrePath}/$runtime, contains = $it")
            }
            // 架构包在、但 universal 主体不在 —— 这不是「本机不支持」，是**安装包残缺**。
            // 这里只记日志（返回值仍按架构判定，免得把正常的架构筛选搅乱），
            // 真正的报错在 run() 里，会给出「请重新下载完整安装包」的提示。
            if (supported && !allPacks.contains(UNIVERSAL_PACK)) {
                Logger.error(
                    TAG,
                    "内置运行环境 ${jre.jrePath} 缺少 $UNIVERSAL_PACK，只有：${allPacks.joinToString()}。" +
                            "当前安装包不完整，解压会在 run() 阶段失败。"
                )
            }
            supported
        }.getOrElse { e ->
            Logger.warning(TAG, "Failed to list assets directory", e)
            false
        }
    }

    private fun getRuntimeByArch() = "bin-" + Architecture.archAsString(ZLApplication.DEVICE_ARCHITECTURE) + ".tar.xz"

    /**
     * 打开一个必需的 asset。
     *
     * 原来直接 `assetManager.open(...)`：文件一旦不在（残缺安装包、打包漏了、存储损坏），
     * 抛出的是裸的 `FileNotFoundException: runtimes/jre-21/universal.tar.xz`，
     * 冒到 `SplashActivity` 变成 `SplashException` → 整个应用带着崩溃页退出，
     * 用户从中得不到任何可行动的信息。
     *
     * 这里把它转成一条说得清的 [IllegalStateException]，并把该目录**实际有哪些条目**
     * 一起写进去 —— 崩溃页会原样显示，用户截图反馈就能直接定位是「哪一类残缺」。
     */
    private fun openAsset(fileName: String): InputStream {
        val path = jre.jrePath + "/" + fileName
        return try {
            assetManager.open(path)
        } catch (e: Exception) {
            val packs = runCatching {
                assetManager.list(jre.jrePath)?.joinToString().orEmpty()
            }.getOrDefault("")
            Logger.error(TAG, "无法读取内置运行环境资产 $path（目录内实际条目：$packs）", e)
            throw IllegalStateException(
                "内置 Java 运行环境文件缺失：$path\n" +
                        "当前安装包可能不完整，请从官网重新下载安装包后覆盖安装。\n" +
                        "该目录实际内容：[${packs.ifBlank { "空" }}]",
                e
            )
        }
    }

    override suspend fun run() {
        runCatching {
            RuntimesManager.installRuntimeBinPack(
                universalFileInputStream = openAsset(UNIVERSAL_PACK),
                platformBinsInputStream = openAsset(getRuntimeByArch()),
                name = jre.jreName,
                binPackVersion = launcherRuntimeVersion,
                updateProgress = { textRes, textArgs ->
                    updateMessage(context.getString(textRes, *textArgs))
                }
            )
            RuntimesManager.postPrepare(jre.jreName)
        }.onFailure {
            Logger.error(TAG, "Internal JRE unpack failed", it)
        }.getOrThrow()
    }
}