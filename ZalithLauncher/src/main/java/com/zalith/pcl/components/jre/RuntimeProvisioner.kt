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
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.zalith.pcl.components.jre

import android.content.Context
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
private const val TAG = "RuntimeProvisioner"

/**
 * 内置 Java 运行库的「自检 + 自动安装」。
 *
 * 存在的理由（都是实际出过的问题）：
 * 1. 解压页原来要用户点一下「同意」才会开始装。某些机型上这一屏被直接跳过 / 程序被切走，
 *    结果依赖一个都没装，进去以后「设置 → Java 运行库」是空的，只能手动导入 .xz。
 * 2. 解压页把任务丢给协程，**任一任务失败就 `throw`**，父协程随之取消 —— 剩下排队的运行库
 *    全部被取消。存储紧张的机型上常见：装到一半空间不够，最后一个 Java 都没能留下。
 * 3. `AllSettings.javaRuntime` 在解压结束时被无条件写成 `Internal-8`。如果本机架构没有
 *    对应的内置包（例如 JRE 25 不带 x86 的 `bin-x86.tar.xz`），这个值是**指向空气**的，
 *    设置里看着「已选中」，启动游戏时却找不到运行库。
 *
 * 所以这里把「检测 → 自动装 → 校正默认选项」收敛成一个可重复调用的入口：
 * 解压页用 [Mode.FULL] 全量补齐，主界面用 [Mode.REPAIR] 只保证「至少有一个能用的 Java」。
 */
object RuntimeProvisioner {

    /** 自动安装的粒度 */
    enum class Mode {
        /** 补齐本机架构支持的全部内置运行库（进启动器前的解压页用） */
        FULL,

        /**
         * 只保证「至少有一个能用的 Java」。
         *
         * 主界面每次打开都会跑一次检测，这里绝不能顺手重装几百 MB：
         * 已经有可用运行库时直接返回，一个都没有时才挑一个装。
         */
        REPAIR,
    }

    /** 一次检测 / 安装的结果 */
    data class Report(
        /** 当前可用（`release` 能解析出版本、且关键文件齐全）的运行库名 */
        val usable: List<String>,
        /** 本次自动装好的运行库名 */
        val installed: List<String>,
        /** 装失败的：运行库名 to 原因 */
        val failed: List<Pair<String, String>>,
        /** 本机架构没有配套内置包、被跳过的运行库名 */
        val skipped: List<String> = emptyList(),
    ) {
        val hasUsable: Boolean get() = usable.isNotEmpty()
    }

    /**
     * 「装完了能不能真的跑起来」的结论。
     *
     * [Report] 只说「有没有可用运行库」，但启动游戏还要看**版本够不够**：
     * 只有 Java 8 却要跑要求 Java 21 的版本时，`hasUsable` 是 true，游戏照样起不来。
     */
    data class Readiness(
        val report: Report,
        /** 装完之后本机可用的最高 Java 版本（0 = 一个都没有） */
        val javaVersion: Int,
        /** 本次要求的 Java 版本（0 = 只要有一个能用的就行） */
        val required: Int,
    ) {
        val ok: Boolean get() = javaVersion >= required && javaVersion > 0

        /** 不可用时给用户看的准确原因（直接进提示框 / 安装失败信息） */
        val problem: String
            get() = when {
                report.usable.isEmpty() && report.failed.isNotEmpty() ->
                    "内置 Java 运行库安装失败：" +
                            report.failed.joinToString("；") { "${it.first} —— ${it.second}" }

                report.usable.isEmpty() && report.skipped.isNotEmpty() ->
                    "本机架构没有配套的内置 Java（${report.skipped.joinToString()}），" +
                            "请到「设置 → Java 运行库」导入 .xz 格式的运行库"

                report.usable.isEmpty() -> "没有可用的 Java 运行库"

                else -> "这个版本需要 Java $required，本机最高只有 Java $javaVersion" +
                        "（${report.usable.joinToString()}）。请到「设置 → Java 运行库」导入更高版本的运行库"
            }
    }

    /**
     * 安装互斥。
     *
     * 解压页（[com.zalith.pcl.ui.activities.SplashActivity]）与主界面的兜底检测可能同时被触发，
     * 两边都往同一个目录解压几百 MB 会把对方写出来的文件删掉（[RuntimesManager.installRuntimeBinPack]
     * 一开始就 `deleteDirectory(dest)`）。用一把锁串起来，后到的那边直接读现状返回。
     */
    private val mutex = Mutex()

    /**
     * 检测并（必要时）自动安装。
     *
     * 必须在主线程之外调用：会读 assets、解压 tar.xz、扫描运行库目录。
     */
    suspend fun ensure(
        context: Context,
        mode: Mode = Mode.REPAIR,
        minJavaVersion: Int = 0,
    ): Report = mutex.withLock {
        try {
            withContext(Dispatchers.IO) { installMissing(context, mode, minJavaVersion) }
        } catch (e: Throwable) {
            Logger.error(TAG, "Runtime provisioning failed", e)
            Report(
                usable = currentUsable(),
                installed = emptyList(),
                failed = listOf("内置运行库" to (e.message ?: "未知错误"))
            )
        } finally {
            runCatching { ensureDefaultSelection() }
        }
    }

    /**
     * JVM 相关流程（安装游戏 / 安装加载器 / 启动游戏）开跑前的自检。
     *
     * 「检测是否存在，没有就自动修复」就是这一个调用：
     * - 已经有达标（≥ [required]）的运行库 → 只做几十次 stat，立刻返回；
     * - 一个都没有（首次安装、换机型、解压被中断）→ 自动解压内置包；
     * - 装完再验一次版本够不够，不够就把原因写进 [Readiness.problem]。
     *
     * 必须在主线程之外调用（内部有 IO，但仍然建议 `withContext(Dispatchers.IO)` 包一层，
     * 因为 [currentUsable] 会读运行库目录）。
     */
    suspend fun ensureFor(context: Context, required: Int, reason: String): Readiness {
        val before = currentUsable()
        val report = ensure(context, Mode.REPAIR, required)
        val versions = javaVersions()
        val readiness = Readiness(
            report = report,
            javaVersion = report.usable.maxOfOrNull { versions[it] ?: 0 } ?: 0,
            required = required,
        )
        Logger.info(
            TAG,
            "[$reason] java ready=${readiness.ok} (required=$required, available=${readiness.javaVersion}, " +
                    "before=$before, installed=${report.installed}, " +
                    "failed=${report.failed.map { it.first }}, skipped=${report.skipped})"
        )
        return readiness
    }

    /** 运行库名 → Java 主版本号（一次目录扫描，别在循环里逐个查） */
    private fun javaVersions(): MutableMap<String, Int> = runCatching {
        RuntimesManager.getRuntimes().associateTo(mutableMapOf<String, Int>()) { it.name to it.javaVersion }
    }.getOrDefault(mutableMapOf())

    /**
     * 把 `javaRuntime` 校正到一个**真实存在**的运行库上。
     *
     * 原来解压结束时无条件写 `Internal-8`：本机没有这个包时，设置里显示「已选中」，
     * 实际 `RuntimesManager.getRuntimeHome` 直接抛 `Selected runtime is broken!`。
     */
    fun ensureDefaultSelection() {
        val runtimes = runCatching { RuntimesManager.getRuntimes(forceLoad = true) }
            .getOrDefault(emptyList())
            .filter { it.versionString != null && RuntimesManager.isRuntimeUsable(it.name) }
        if (runtimes.isEmpty()) return

        val names = runtimes.map { it.name }
        val current = AllSettings.javaRuntime.getValue()
        if (current.isNotBlank() && current in names) return

        // 优先内置 Java 8：它覆盖面最广（1.7 ~ 1.12 都能跑），也是原来写死的默认值
        val pick = runtimes.firstOrNull { it.name == Jre.JRE_8.jreName }?.name
            ?: runtimes.minByOrNull { it.javaVersion }?.name
            ?: return

        AllSettings.javaRuntime.save(pick)
        Logger.info(TAG, "Default java runtime corrected: '$current' -> '$pick'")
    }

    /**
     * 当前**真的能用**的运行库（不安装，只读）。
     *
     * 注意不能只看 `release` 能不能解析：解压被截断的目录往往 `release` 已落盘、
     * `bin/java` / `libjli.so` 却没写进去，那种目录会被算成「已安装」，
     * 于是自动修复直接跳过，一直用到拉起 JVM 才以 -1 崩掉。
     */
    fun currentUsable(): List<String> = runCatching {
        RuntimesManager.getRuntimes()
            .filter { it.versionString != null && RuntimesManager.isRuntimeUsable(it.name) }
            .map { it.name }
    }.getOrDefault(emptyList())

    private suspend fun installMissing(context: Context, mode: Mode, minJavaVersion: Int): Report {
        val usable = currentUsable().toMutableList()
        val installed = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        val skipped = mutableListOf<String>()
        val versions = javaVersions()

        fun meetsRequirement(): Boolean =
            usable.any { (versions[it] ?: 0) >= minJavaVersion }

        // 先按 majorVersion 升序：Java 8 覆盖的版本最多，先保证它在。
        // REPAIR 时把「设置里正选着的那个」提到最前 —— 它多半就是坏掉的那个
        // （解压被中断），先修它能省下几百 MB 的无谓解压。
        val preferred = AllSettings.javaRuntime.getValue()
        val order = if (mode == Mode.REPAIR) {
            Jre.entries.sortedWith(compareBy({ it.jreName != preferred }, { it.majorVersion }))
        } else {
            Jre.entries.sortedBy { it.majorVersion }
        }

        for (jre in order) {
            if (mode == Mode.REPAIR) {
                // 已经有一个达到要求的运行库就收工
                if (meetsRequirement()) break
                // 低于要求的装了也没用，几百 MB 白花
                if (jre.majorVersion < minJavaVersion) continue
            }

            val task = runCatching { UnpackJreTask(context, jre) }
                .onFailure { Logger.warning(TAG, "Failed to create unpack task for ${jre.jreName}", it) }
                .getOrNull()
                ?: run {
                    failed += jre.jreName to "无法读取内置运行库资产"
                    continue
                }

            if (task.isCheckFailed()) {
                // 本机架构没有配套的 `bin-<abi>.tar.xz`（例如 JRE 25 不带 x86），不是故障
                skipped += jre.jreName
                continue
            }

            val state = runCatching { task.checkState() }.getOrDefault(InstallableItem.State.NOT_STARTED)
            if (state == InstallableItem.State.FINISHED) {
                if (jre.jreName !in usable) usable += jre.jreName
                versions[jre.jreName] = jre.majorVersion
                continue
            }

            Logger.info(TAG, "Auto installing runtime ${jre.jreName} (state=$state, mode=$mode)")
            runCatching { withContext(Dispatchers.IO) { task.run() } }
                .onFailure { e ->
                    val reason = e.message ?: e::class.java.simpleName
                    Logger.error(TAG, "Auto install failed for ${jre.jreName}: $reason", e)
                    failed += jre.jreName to reason
                }
                .onSuccess {
                    // 装完必须**再验一次**：`run()` 不抛异常不等于装好了，
                    // 解压被截断时目录会「看着在、其实缺 bin/java / libjli.so」
                    val after = runCatching { task.checkState() }
                        .getOrDefault(InstallableItem.State.NOT_STARTED)
                    if (after == InstallableItem.State.FINISHED) {
                        if (jre.jreName !in usable) usable += jre.jreName
                        // versions 是进循环前扫的快照，装完必须补上，
                        // 否则 meetsRequirement() 看不到刚装好的那个（会继续多装一个）
                        versions[jre.jreName] = jre.majorVersion
                        installed += jre.jreName
                    } else {
                        failed += jre.jreName to
                                "解压后仍不可用（${RuntimesManager.describeRuntimeProblem(jre.jreName)}）"
                    }
                }
        }

        Logger.info(
            TAG,
            "Provisioning done (mode=$mode): usable=$usable, installed=$installed, " +
                    "failed=$failed, skipped=$skipped"
        )
        return Report(usable = usable, installed = installed, failed = failed, skipped = skipped)
    }
}
