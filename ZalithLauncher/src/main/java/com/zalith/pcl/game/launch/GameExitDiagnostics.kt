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

package com.zalith.pcl.game.launch

import java.io.File
import java.io.RandomAccessFile

/**
 * 从游戏日志里「猜」游戏为什么退出了。
 *
 * 这里只做一件事：把日志尾部最后一条像样的报错（异常头 + 紧随其后的几行堆栈）捞出来，
 * 直接摆在退出页上。目的是让「启动一会儿就回到启动器主页」这类看不到任何提示的退出，
 * 至少能当场看到一行原因，用户截个图就能定位，不必先分享日志文件再等分析。
 *
 * 不做任何解析/归因推理，捞不到就返回 null，交由调用方退回「请分享日志」的兜底文案。
 */
internal object GameExitDiagnostics {    /** 最多从文件尾部读这么多字节，日志再大也只读一个尾巴 */
    private const val READ_LIMIT = 192 * 1024

    /** 单行最长保留多少字符，超长（比如超长 mods 列表）直接截断 */
    private const val MAX_LINE = 220

    /** 报错行之后最多再带几行（堆栈帧 / Caused by） */
    private const val CONTEXT_LINES = 2

    /**
     * 一眼就能认定是致命错误的特征串。
     * 覆盖三类：Java 层（类/方法/内存）、Mixin/Loader 装配失败、原生崩溃与渲染初始化失败。
     */
    private val FATAL_KEYWORDS = listOf(
        // Java 层
        "UnsupportedClassVersionError",
        "OutOfMemoryError",
        "StackOverflowError",
        "NoClassDefFoundError",
        "ClassNotFoundException",
        "NoSuchMethodError",
        "NoSuchFieldError",
        "IncompatibleClassChangeError",
        "IllegalAccessError",
        "Could not find or load main class",
        // Mixin / 模组加载
        "Mixin apply failed",
        "MixinApplyError",
        "Mixin transformation of",
        "Failed to load mods",
        // 原生崩溃（HotSpot 与 Android 两边）
        "A fatal error has been detected",
        "EXCEPTION_ACCESS_VIOLATION",
        "SIGSEGV",
        "SIGABRT",
        "SIGILL",
        "Fatal signal",
        // 渲染 / 窗口初始化
        "Failed to create window",
        "GLFW error",
        "Couldn't set pixel format",
        "Missing or unsupported mandatory",
        "VK_ERROR",
        "vkCreateInstance",
        "Failed to initialize Vulkan",
        "The game crashed whilst"
    )

    /** 通用报错前缀：作为 [FATAL_KEYWORDS] 之外的宽松匹配 */
    private val ERROR_MARKERS = listOf(
        "Exception:",
        "Exception in thread",
        "Error:",
        "Caused by:",
        "FATAL",
        "CRITICAL"
    )

    /**
     * 日志尾部出现的报错块（最多 [CONTEXT_LINES] 行堆栈）。
     * 找不到任何报错行时返回 null。
     */
    fun findLikelyCause(logFile: File?): String? {
        val lines = readTailLines(logFile)
        if (lines.isEmpty()) return null

        var hit = -1
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            if (line.isBlank()) continue
            if (containsFatal(line) || containsErrorMarker(line)) {
                hit = i
                break
            }
        }
        if (hit < 0) return null

        val builder = StringBuilder()
        builder.append(trimLine(lines[hit]))
        var added = 0
        for (i in hit + 1 until lines.size) {
            if (added >= CONTEXT_LINES) break
            val line = lines[i]
            if (line.isBlank()) continue
            //只往下带堆栈帧或 Caused by，避免把后面追加的无关提示也抓进来
            val isStack = line.startsWith("at ") || line.startsWith("\tat ") || line.startsWith("... ")
            if (!isStack && !line.contains("Caused by:")) break
            builder.append('\n').append(trimLine(line))
            added++
        }
        return builder.toString()
    }

    /**
     * 日志末尾 [count] 行非空内容，用于「连报错都没有就直接退出」时的兜底展示。
     */
    fun readLastLines(logFile: File?, count: Int = 6): String? {
        val lines = readTailLines(logFile)
        if (lines.isEmpty()) return null
        return lines.asSequence()
            .filter { it.isNotBlank() }
            .toList()
            .takeLast(count)
            .joinToString("\n") { trimLine(it) }
            .ifBlank { null }
    }

    private fun containsFatal(line: String): Boolean =
        FATAL_KEYWORDS.any { line.contains(it, ignoreCase = true) }

    private fun containsErrorMarker(line: String): Boolean =
        ERROR_MARKERS.any { line.contains(it) }

    private fun trimLine(line: String): String {
        val trimmed = line.trim()
        return if (trimmed.length > MAX_LINE) trimmed.take(MAX_LINE) + "…" else trimmed
    }

    private fun readTailLines(logFile: File?): List<String> {
        if (logFile == null || !logFile.isFile || logFile.length() <= 0L) return emptyList()

        val text = runCatching {
            RandomAccessFile(logFile, "r").use { raf ->
                val length = raf.length()
                val start = (length - READ_LIMIT).coerceAtLeast(0L)
                raf.seek(start)
                val bytes = ByteArray((length - start).toInt())
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrNull() ?: return emptyList()

        val lines = text.split('\n').map { it.trimEnd('\r') }
        //从文件中间开始读时，第一行大概率是被截断的半行，丢掉
        return if (startedMidFile(logFile)) lines.drop(1) else lines
    }

    private fun startedMidFile(logFile: File): Boolean = logFile.length() > READ_LIMIT

    /**
     * 日志尾部有没有「游戏正常关闭」的痕迹。
     *
     * Minecraft 从菜单退出（或存档后退出）时一定会走 `Minecraft.close()`，
     * 日志里会留下 `Stopping!` / `Stopping worker threads` 这一组固定字样；
     * 崩溃 / 加载失败 / 被系统杀掉则**不会**有这些行。
     *
     * 用它来区分「玩家自己退的游戏」和「游戏自己没了」，比单纯看运行时长可靠得多：
     * 玩家进游戏看一眼就退，退出码同样是 0、时间可能还不到 90 秒，但那是正常退出。
     */
    fun looksLikeCleanShutdown(logFile: File?): Boolean {
        val lines = readTailLines(logFile)
        if (lines.isEmpty()) return false
        val text = lines.joinToString("\n")
        return CLEAN_SHUTDOWN_MARKERS.any { text.contains(it, ignoreCase = true) }
    }

    /** Minecraft 正常关闭时日志里必然出现的字样 */
    private val CLEAN_SHUTDOWN_MARKERS = listOf(
        "Stopping!",
        "Stopping worker threads",
        "Stopping singleplayer server",
        "Saving and pausing game"
    )

    /**
     * 根据日志尾部内容给出「下一步怎么处理」的建议类型。
     *
     * 判定顺序是有讲究的：图形后端完全不可用是最致命、也最容易误判成「启动器坏了」的一类，
     * 所以放在最前面；Java 版本、内存这类用户自己能改的放后面。
     * 一条都对不上就返回 [Advice.NONE]，退出页不显示建议区。
     */
    fun findAdvice(logFile: File?): Advice {
        val tail = readTailLines(logFile)
        if (tail.isEmpty()) return Advice.NONE
        //整段尾部一起匹配：一次启动里往往同时出现「OpenGL 建不出窗口」和「Vulkan 版本不够」，
        //单看最后一行只能看到其中一半
        val text = tail.joinToString("\n")

        fun has(vararg keys: String): Boolean = keys.any { text.contains(it, ignoreCase = true) }

        return when {
            //两条渲染路径都堵死：OpenGL 建不出上下文 + Vulkan 能力不足
            has("No supported graphics backend was found") ||
                    (has("Failed to create window with OpenGL context") &&
                            has("does not support Vulkan 1.2", "Device missing capabilities")) ->
                Advice.NO_GRAPHICS_BACKEND

            has("does not support Vulkan 1.2", "Vulkan 1.2") -> Advice.VULKAN_TOO_OLD

            has("Failed to create window context", "Failed to create backend OpenGL") ->
                Advice.OPENGL_CONTEXT_FAILED

            has("UnsupportedClassVersionError", "class file version") -> Advice.JAVA_TOO_OLD

            has("OutOfMemoryError", "Could not reserve enough space", "native memory allocation") ->
                Advice.OUT_OF_MEMORY

            has("Mixin apply failed", "MixinApplyError", "Failed to load mods", "Mod resolution") ->
                Advice.MOD_MISMATCH

            // 渲染器和本机 GPU 厂商不符：Freedreno 要 Adreno（内核驱动 kbase / kgsl），
            // Panfrost 要 Mali。崩溃本身在原生层，但原因非常确定，值得单独给一条建议。
            has("Failed to open kbase", "kbase device", "kgsl device", "FD_OSMESA") ->
                Advice.RENDERER_GPU_MISMATCH

            // SIGILL：CPU 不认识当前 Java 运行时里的指令。典型是 ARMv8.0 的老核心
            // 去跑按 ARMv8.2 编译的 JRE，启动一两秒就崩，且和渲染器无关。
            has("SIGILL", "Illegal instruction") ->
                Advice.JRE_INSTRUCTION_MISMATCH

            has("EXCEPTION_ACCESS_VIOLATION", "SIGSEGV", "SIGABRT", "Fatal signal") ->
                Advice.NATIVE_CRASH

            else -> Advice.NONE
        }
    }

    /** 退出页上「下一步」的几种建议，文案在 `ErrorActivity` 里映射成资源字符串 */
    enum class Advice {
        NONE,
        /** 图形后端整体不可用：OpenGL 建不出上下文，游戏自带的 Vulkan 后端也不满足要求 */
        NO_GRAPHICS_BACKEND,
        /** 设备 Vulkan 版本低于游戏要求 */
        VULKAN_TOO_OLD,
        /** 当前渲染器在本机驱动上建不出 GL 上下文 */
        OPENGL_CONTEXT_FAILED,
        JAVA_TOO_OLD,
        OUT_OF_MEMORY,
        /** 模组与游戏版本不匹配，装不上 */
        MOD_MISMATCH,
        /** 渲染器要求的 GPU 厂商和本机不符（Freedreno 要 Adreno，Panfrost 要 Mali） */
        RENDERER_GPU_MISMATCH,
        /** CPU 不支持当前 Java 运行时的指令集（SIGILL） */
        JRE_INSTRUCTION_MISMATCH,
        /** 原生层崩溃（多半是驱动） */
        NATIVE_CRASH
    }
}
