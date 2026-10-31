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

package com.zalith.pcl.game.versioninfo

import com.google.gson.reflect.TypeToken
import com.zalith.pcl.game.addons.mirror.mapBMCLMirrorUrls
import com.zalith.pcl.game.versioninfo.models.VersionManifest
import com.zalith.pcl.game.versioninfo.models.mapVersion
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.path.URL_MINECRAFT_VERSION_REPOS
import com.zalith.pcl.utils.GSON
import com.zalith.pcl.utils.file.readString
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.fetchStringFromUrl
import com.zalith.pcl.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "MinecraftVersions"

object MinecraftVersions {
    private var manifest: VersionManifest? = null

    private val _allVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val allVersions = _allVersions.asStateFlow()

    /**
     * 刷新Minecraft版本的版本号列表
     * @param force 强制下载更新版本列表
     */
    @Throws(IllegalStateException::class)
    suspend fun refreshVersions(force: Boolean = false) {
        if (!force && _allVersions.value.isNotEmpty()) return

        val vm = if (force) {
            getVersionManifest(force = true)
        } else {
            manifest ?: getVersionManifest(force = false)
        }
        val versions = vm.versions.mapVersion()
        _allVersions.update { versions }
    }

    /**
     * 获取Minecraft版本信息列表
     * @param force 强制下载更新版本列表
     */
    @Throws(IllegalStateException::class)
    suspend fun getVersionManifest(force: Boolean = false): VersionManifest {
        manifest?.takeIf { !force }?.let { return it }

        return withContext(Dispatchers.IO) {
            val localManifestFile = PathManager.FILE_MINECRAFT_VERSIONS
            val isOutdated = !localManifestFile.exists() || !localManifestFile.isFile ||
                    //一天更新一次版本信息列表
                    localManifestFile.lastModified() + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()

            // 先读本地缓存：即使超过了「一天」，它也比一个错误页有用。
            // 旧逻辑只在文件「不过期」时才读，一旦下载失败（镜像返回 200 的 HTML / 空串，
            // Gson 解出 null）就直接报「Version manifest is null」，明明本地有可用清单。
            val cached = readCachedManifest(localManifestFile)

            val downloaded = if (force || isOutdated || cached == null) {
                runCatching { downloadVersionManifest() }
                    .onFailure { e ->
                        Logger.warning(TAG, "Failed to download version manifest, fallback to cache if any", e)
                    }
                    .getOrNull()
            } else {
                null
            }

            val newManifest0 = downloaded ?: cached
                ?: throw IllegalStateException(
                    "无法获取版本清单：所有下载源均未返回有效数据，本地也没有可用缓存"
                )
            mergeUnlistVersions(newManifest0) ?: newManifest0
        }.also { newManifest ->
            manifest = newManifest
        }
    }

    /**
     * 从官方版本仓库获取版本信息。
     *
     * 不能只用 `fetchStringFromUrls`：它把「HTTP 200」当成成功，镜像维护页、限流页
     * 同样是 200，解析出来是 null，然后整个列表就报错。这里逐个源请求并**校验内容**，
     * 只有 `versions` 非空才算成功；写缓存也走临时文件，避免写一半留下坏 JSON。
     */
    private suspend fun downloadVersionManifest(): VersionManifest = withContext(Dispatchers.IO) {
        val urls = URL_MINECRAFT_VERSION_REPOS.mapBMCLMirrorUrls()
        var lastError: Throwable? = null

        for (url in urls) {
            try {
                val rawJson = withRetry("MinecraftVersions", maxRetries = 2) {
                    fetchStringFromUrl(url)
                }
                val manifest = GSON.fromJson(rawJson, VersionManifest::class.java)
                if (manifest == null || manifest.versions.isEmpty()) {
                    throw IllegalStateException("源返回的不是有效版本清单：$url")
                }

                val cache = PathManager.FILE_MINECRAFT_VERSIONS
                val temp = java.io.File(cache.parentFile, "${cache.name}.tmp")
                temp.writeText(rawJson)
                if (!temp.renameTo(cache)) {
                    cache.writeText(rawJson)
                    temp.delete()
                }
                return@withContext manifest
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastError = e
                Logger.warning(TAG, "Version manifest source failed: $url", e)
            }
        }

        throw lastError ?: IllegalStateException("没有可用的版本清单下载源")
    }

    /** 读取本地版本清单；文件不存在、损坏或没有版本条目时返回 null。 */
    private fun readCachedManifest(file: java.io.File): VersionManifest? {
        if (!file.isFile) return null
        return runCatching {
            GSON.fromJson(file.readText(), VersionManifest::class.java)
                ?.takeIf { it.versions.isNotEmpty() }
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to parse cached version manifest", e)
        }.getOrNull()
    }

    /**
     * 尝试从本地合并官方隐藏的版本
     */
    private suspend fun mergeUnlistVersions(
        currentManifest: VersionManifest
    ): VersionManifest? {
        return withContext(Dispatchers.IO) {
            MinecraftVersions::class.java.getResourceAsStream("/assets/game/unlist_versions.json")?.use { input ->
                input.readString()
            }?.let { unlistVersionJson ->
                GSON.fromJson<List<VersionManifest.Version>>(
                    unlistVersionJson,
                    object : TypeToken<List<VersionManifest.Version>>() {}.type
                )
            }?.let { unlistVersions ->
                val versions = currentManifest.versions.toMutableList()
                versions.addAll(unlistVersions)
                versions.sortWith { version, other ->
                    other.releaseTime.compareTo(version.releaseTime)
                }

                currentManifest.copy(
                    versions = versions.toList()
                )
            }
        }
    }
}