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

package com.zalith.pcl.terracotta

import com.google.gson.JsonParseException
import com.zalith.pcl.path.GLOBAL_CLIENT
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.isChinaMainland
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.safeBodyAsJson
import com.zalith.pcl.utils.string.isNotEmptyOrBlank
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.net.URISyntaxException

private const val TAG = "TerracottaNodeList"

private const val NODE_LIST_URL = "https://terracotta.glavo.site/nodes"

@Serializable
private data class TerracottaNode(
    @SerialName("url")
    val url: String? = null,
    @SerialName("region")
    val region: String? = null
) {
    fun validate() {
        requireNotNull(url, { "TerracottaNode.url cannot be null" })
        try {
            URI(url)
        } catch (e: URISyntaxException) {
            throw JsonParseException("Invalid URL: $url", e)
        }
    }
}

@Volatile
private var nodeList: List<URI>? = null

private val fetchMutex = Mutex()

/**
 * [Reference HMCL](https://github.com/HMCL-dev/HMCL/blob/a70b8d7/HMCL/src/main/java/org/jackhuang/hmcl/terracotta/TerracottaNodeList.java)
 */
suspend fun fetchNodes(): List<URI> {
    nodeList?.let { return it }

    return fetchMutex.withLock {
        nodeList?.let { return it }

        if (AllSettings.enableTerracottaNodes.getValue()) {
            // 使用自定义的 EasyTier 服务器节点
            val url = AllSettings.terracottaNodes.getValue()
            if (url.isNotEmptyOrBlank()) {
                val custom = sanitizeNode(URI(url))
                if (custom != null) {
                    val tempList = listOf(custom)
                    nodeList = tempList
                    return tempList
                }
                Logger.warning(TAG, "Ignoring invalid custom Terracotta node: $url")
                // 自定义节点不是 tcp:// / udp:// / https:// 这类绝对地址时，
                // 继续走默认节点。以前这里会把 "U/XXXX-..." 这种房间码原样交给
                // EasyTier，配置里出现 uri = "U/..."，直接 TOML 报 relative URL 崩溃。
            }
            // 为空则继续使用默认的节点逻辑
        }

        withContext(Dispatchers.IO) {
            fetchNodesFromRemote()
        }.also { result ->
            nodeList = result
        }
    }
}

private suspend fun fetchNodesFromRemote(): List<URI> {
    return runCatching {
        val nodes = GLOBAL_CLIENT
            .get(NODE_LIST_URL)
            .safeBodyAsJson<List<TerracottaNode?>?>()

        if (nodes.isNullOrEmpty()) {
            Logger.info(TAG, "No available Terracotta nodes found")
            return emptyList()
        }

        val result = nodes
            .asSequence()
            .mapNotNull { node ->
                if (node == null) return@mapNotNull null
                parseNode(node)
            }
            .toList()

        result
    }.onFailure {
        Logger.warning(TAG, "Failed to fetch terracotta node list", it)
    }.getOrDefault(emptyList())
}

private fun parseNode(
    node: TerracottaNode
): URI? {
    return try {
        node.validate()

        if (!shouldUseNode(node.region)) {
            return null
        }

        sanitizeNode(URI.create(node.url))

    } catch (e: Exception) {
        Logger.warning(TAG, "Invalid terracotta node: $node", e)
        null
    }
}

/**
 * 只接受 EasyTier 能识别的绝对节点地址。
 *
 * `URI("U/XXXX-...")` 是合法 URI，但属于**相对**地址；传给 EasyTier 后会被写进
 * `[[peer]] uri = "U/..."`，TOML 直接报 `relative URL without a base` 并崩掉。
 */
private fun sanitizeNode(uri: URI): URI? {
    val scheme = uri.scheme?.lowercase() ?: return null
    if (!uri.isAbsolute) return null
    val allowed = setOf("tcp", "udp", "ws", "wss", "http", "https", "wg")
    if (scheme !in allowed) return null
    if (uri.host.isNullOrBlank()) return null
    return uri
}

private fun shouldUseNode(
    region: String?
): Boolean {
    if (region.isNullOrBlank()) return true

    //仅限中国大陆地区使用
    val isMainLand = isChinaMainland()
    return isMainLand && region.equals("CN", ignoreCase = true)
}
