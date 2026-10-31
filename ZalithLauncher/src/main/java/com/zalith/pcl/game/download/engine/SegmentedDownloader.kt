/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 单文件「分段并行」下载器。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.game.download.engine

import com.zalith.pcl.path.URL_USER_AGENT
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "SegmentedDownloader"

/**
 * 把**一个大文件**切成若干段、用多条连接同时拉，最后合并校验。
 *
 * ---- 为什么需要它
 *
 * [Fetcher] 是「一个文件一条连接」的流式下载：文件级并发（[BatchDownloader] 的
 * `maxConnections`，默认 64）已经足够，但**单个文件**的速度被一条 TCP 连接锁死。
 * 几百 MB 的整合包 / client jar 在这种情形下跑到几百 KB/s 很常见，而同一台机器
 * 用浏览器多线程下同一个文件能跑满带宽 —— 差别就是连接数。
 *
 * ---- 为什么放在 FileDownloader 前面而不是改 Fetcher
 *
 * [Fetcher] 承担了换源、续传握手、gzip 解码、错误语义（404 要能被
 * [findHttpCode] 认出来）等一堆细节，动它的风险远大于收益。这里做成**可选前置**：
 *   · 条件不满足（文件小、不支持 Range、没有强校验器）→ 返回 `false`，原样走 Fetcher；
 *   · 中途任何一步不对 → 删掉临时文件、把已上报的字节扣回去，再走 Fetcher 重下。
 * 也就是说这条路**只可能更快，不可能把原本能下的文件弄失败**。
 *
 * ---- 几个必须守住的细节
 *
 * 1. **`Accept-Encoding: identity`**。Fetcher 平时发 `gzip`，压缩流的 Range 是按
 *    压缩后字节算的，多段拼接必然错位。分段请求一律要 identity。
 * 2. **必须探测到强校验器**（ETag / Last-Modified）。没有它就无法在下载中途确认
 *    「服务器上的文件没变过」，多段可能拼出个四不像。宁可不分段。
 * 3. **进度要能退**。分段失败回退时，已计入的字节必须原子地扣回去，
 *    否则进度会先冲到 100% 再重下，看起来像卡死（[DownloadStats.addBytes] 是
 *    AtomicLong 累加，传负数即可）。
 * 4. 临时文件与目标同目录，最后 `renameTo` 才是原子的；跨目录会退化成复制。
 */
object SegmentedDownloader {

    /**
     * 低于这个大小不分段。
     *
     * assets 里绝大多数文件是几百 KB，分段省下的时间还不够多付几次握手 + 慢启动，
     * 而多出来的请求会把整批几千个文件的握手开销翻几倍 —— 那就是负优化。
     */
    const val MIN_TOTAL_SIZE = 8L * 1024 * 1024

    /** 单段最小长度：段太小（比如 200KB 切 8 段）每条连接都在慢启动，反而更慢 */
    private const val MIN_SEGMENT_SIZE = 2L * 1024 * 1024

    /** 单个文件最多几条连接。再多对 CDN 不友好，收益也基本到顶 */
    const val MAX_SEGMENTS = 8

    /**
     * 全进程**同时**在做分段下载的文件数上限。
     *
     * 批量下载的 `maxConnections` 是 64 —— 如果这 64 个作业里有十几个大文件、
     * 每个再开 8 段，峰值就是上百个并发 socket。手机上足以把网络栈/内存压垮，
     * 表现出来是「下载变快了一下，然后整个应用卡住」。所以这里卡一道全局配额：
     * 配额用满时**立刻返回 false 让这个文件走单连接**，绝不排队等待 ——
     * 排队会让一批文件互相等，比慢一点糟糕得多。
     */
    private const val MAX_PARALLEL_FILES = 6

    private val activeSegmentedFiles = AtomicInteger(0)

    private const val TIMEOUT_MILLIS = 15_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_REDIRECTS = 8

    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

    private val CONTENT_RANGE = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""")

    /**
     * 尝试分段并行下载。
     *
     * @param expectedSize 清单里声明的大小；小于 [MIN_TOTAL_SIZE] 时**一次请求都不发**直接返回
     *        （这是它不拖慢小文件下载的关键 —— 判定完全靠本地数字）。
     * @return true 表示文件已就位且校验通过；false 表示没走成，调用方应回退到 [Fetcher]
     */
    suspend fun downloadOrNull(
        urls: List<String>,
        targetFile: File,
        sha1: String?,
        expectedSize: Long,
        onBytes: (Long) -> Unit,
        /** 分段上限；MobileGlues 这类固定 10 MB 左右的单文件可以显式开到 64。 */
        maxSegments: Int = MAX_SEGMENTS,
        /** 单段最小长度；配合 maxSegments 让 11 MB 的文件也能切出足够多的连接。 */
        minSegmentSize: Long = MIN_SEGMENT_SIZE,
    ): Boolean {
        if (expectedSize < minSegmentSize) return false

        if (activeSegmentedFiles.incrementAndGet() > MAX_PARALLEL_FILES) {
            activeSegmentedFiles.decrementAndGet()
            return false
        }

        try {
            for (candidate in urls.distinct()) {
                val url = try {
                    URL(candidate)
                } catch (e: Exception) {
                    continue
                }
                if (!url.protocol.equals("http", ignoreCase = true) &&
                    !url.protocol.equals("https", ignoreCase = true)
                ) {
                    continue
                }

                var reported = 0L
                var succeeded = false
                try {
                    val result = attempt(
                        url = url,
                        targetFile = targetFile,
                        sha1 = sha1,
                        maxSegments = maxSegments,
                        minSegmentSize = minSegmentSize,
                    ) { delta ->
                        reported += delta
                        onBytes(delta)
                    }
                    if (result) {
                        succeeded = true
                        return true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.warning(TAG, "Segmented download gave up: $candidate", e)
                } finally {
                    // 已经报给统计的字节要扣回去：接下来 Fetcher 会把这个文件从头下一遍，
                    // 不扣的话总进度会先虚高再回落（下载页看起来就是「100% 又变回 60%」）。
                    if (!succeeded && reported != 0L) {
                        onBytes(-reported)
                    }
                }
            }
            return false
        } finally {
            activeSegmentedFiles.decrementAndGet()
        }
    }

    /** 单个候选源的一次分段下载；false = 这个源不适合分段（不是错误） */
    private suspend fun attempt(
        url: URL,
        targetFile: File,
        sha1: String?,
        maxSegments: Int,
        minSegmentSize: Long,
        report: (Long) -> Unit,
    ): Boolean {
        val probe = probe(url) ?: return false
        val total = probe.total
        if (total < MIN_TOTAL_SIZE) return false

        val parent = targetFile.absoluteFile.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false

        val segmentCount = (total / minSegmentSize).toInt().coerceIn(2, maxSegments)
        val temp = File(parent, "${targetFile.name}.zpart")
        runCatching { if (temp.exists()) temp.delete() }

        Logger.info(TAG, "Segmented download: ${targetFile.name}, $total bytes / $segmentCount segments")

        try {
            RandomAccessFile(temp, "rw").use { raf ->
                raf.setLength(total)
                raf.channel.use { channel ->
                    coroutineScope {
                        (0 until segmentCount).map { index ->
                            // 段边界按乘法分配，最后一段吃掉除不尽的余数
                            val start = total * index / segmentCount
                            val endInclusive = total * (index + 1) / segmentCount - 1
                            async {
                                runInterruptible(Dispatchers.IO) {
                                    fetchSegment(url, channel, start, endInclusive, probe.validator, report)
                                }
                            }
                        }.awaitAll()
                    }
                    channel.force(true)
                }
            }

            if (temp.length() != total) {
                throw IOException("Size mismatch after merge: ${temp.length()} != $total")
            }
            if (sha1 != null) {
                val actual = runInterruptible(Dispatchers.IO) { sha1Of(temp) }
                if (!actual.equals(sha1, ignoreCase = true)) {
                    throw IOException("SHA-1 mismatch: $actual != $sha1")
                }
            }
            if (targetFile.exists() && !targetFile.delete()) {
                throw IOException("Cannot replace existing file: $targetFile")
            }
            if (!temp.renameTo(targetFile)) {
                throw IOException("Cannot move temp file to $targetFile")
            }
            return true
        } catch (e: Throwable) {
            runCatching { temp.delete() }
            throw e
        }
    }

    /** 探测：是否支持 Range、总大小、强校验器。任何一项不满足都返回 null（= 不分段） */
    private fun probe(url: URL): Probe? {
        var current = url
        var redirects = 0
        while (true) {
            val connection = open(current)
            try {
                connection.setRequestProperty("Range", "bytes=0-0")
                // 探测也必须 identity：协商出 gzip 就说明这条链路会重编码，分段不可用
                connection.setRequestProperty("Accept-Encoding", "identity")
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location") ?: return null
                    if (redirects++ >= MAX_REDIRECTS) return null
                    current = URL(current, location)
                    continue
                }
                // 只有 206 才证明服务器真的按区间给数据；200 说明它无视 Range，直接整份返回
                if (code != 206) {
                    Logger.info(TAG, "Range not supported (HTTP $code): $current")
                    return null
                }
                val encoding = connection.getHeaderField("Content-Encoding")
                if (encoding != null && !encoding.equals("identity", ignoreCase = true)) {
                    return null
                }
                val total = parseContentRange(connection.getHeaderField("Content-Range"), 3)
                    ?: return null
                val validator = strongValidatorOf(connection) ?: return null
                return Probe(total, validator)
            } catch (e: Exception) {
                return null
            } finally {
                runCatching { connection.disconnect() }
            }
        }
    }

    /** 拉取 [start, endInclusive] 这一段，直接写进 [channel] 的对应位置（阻塞，靠线程中断取消） */
    private fun fetchSegment(
        url: URL,
        channel: FileChannel,
        start: Long,
        endInclusive: Long,
        validator: String,
        report: (Long) -> Unit,
    ) {
        var current = url
        var redirects = 0
        while (true) {
            val connection = open(current)
            try {
                connection.setRequestProperty("Range", "bytes=$start-$endInclusive")
                connection.setRequestProperty("Accept-Encoding", "identity")
                // 下载期间文件若被换掉，服务器会返回 200 整份；下面的 206 断言会当场拦住
                connection.setRequestProperty("If-Range", validator)
                val code = connection.responseCode
                if (code in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Redirect without Location: $current")
                    if (redirects++ >= MAX_REDIRECTS) throw IOException("Too many redirects: $url")
                    current = URL(current, location)
                    continue
                }
                if (code != 206) {
                    throw IOException("Segment $start-$endInclusive: unexpected HTTP $code")
                }
                val rangeStart = parseContentRange(connection.getHeaderField("Content-Range"), 1)
                if (rangeStart != null && rangeStart != start) {
                    throw IOException("Segment $start-$endInclusive: Content-Range starts at $rangeStart")
                }

                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var position = start
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedIOException("Cancelled")
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        val slice = ByteBuffer.wrap(buffer, 0, read)
                        var offset = position
                        while (slice.hasRemaining()) {
                            // 绝对位置写：多个段共用一个 channel 也是线程安全的
                            offset += channel.write(slice, offset)
                        }
                        position += read
                        report(read.toLong())
                    }
                    val expected = endInclusive - start + 1
                    if (position - start != expected) {
                        throw IOException(
                            "Segment $start-$endInclusive: received ${position - start} bytes, expected $expected"
                        )
                    }
                }
                return
            } finally {
                runCatching { connection.disconnect() }
            }
        }
    }

    private fun open(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", URL_USER_AGENT)
        return connection
    }

    /**
     * 强校验器：`If-Range` 只认「强」ETag 或 Last-Modified。
     * 弱 ETag（`W/"..."`）按 RFC 不能用于 If-Range，给了也可能被服务器忽略 —— 那就干脆不分段。
     */
    private fun strongValidatorOf(connection: HttpURLConnection): String? {
        val etag = connection.getHeaderField("ETag")
        if (etag != null && !etag.startsWith("W/", ignoreCase = true)) return etag
        return connection.getHeaderField("Last-Modified")
    }

    private fun parseContentRange(value: String?, group: Int): Long? {
        val match = CONTENT_RANGE.find(value ?: return null) ?: return null
        return match.groupValues[group].toLongOrNull()
    }

    private fun sha1Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedIOException("Cancelled")
                }
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private class Probe(val total: Long, val validator: String)
}
