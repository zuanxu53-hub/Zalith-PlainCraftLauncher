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

package com.zalith.pcl.game.download.jvm_server

import android.content.Intent
import com.zalith.pcl.components.jre.Jre
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.context.GlobalContext
import com.zalith.pcl.coroutine.TaskLogOutput
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.notification.NoticeProgress
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "GameJVMRunner"

/** 等待互斥进程自行退出的时长，超过后按 pid 强杀 */
private val WAIT_BEFORE_FORCE_KILL: Duration = 15.seconds
/** 强杀后再宽限一轮的时长，仍无法清场则以可见错误结束 */
private val KILL_GRACE: Duration = 5.seconds
/** 等待日志的打印间隔 */
private val WAIT_LOG_INTERVAL: Duration = 2.seconds
/** 单次 JVM 运行等待退出码的总时限 */
private val JVM_EXIT_TIMEOUT: Duration = 15.minutes
/** 看门狗巡检 :jvm 进程生死的间隔 */
private val JVM_WATCHDOG_INTERVAL: Duration = 500.milliseconds
/** 观测到 :jvm 消失后，仍额外等待退出码的宽限期（UDP 包可能还在路上） */
private val JVM_EXIT_CODE_GRACE: Duration = 5.seconds
/** 前台服务启动失败后的最大重试次数 */
private const val SERVICE_START_RETRY_MAX = 4
/** 前台服务启动失败重试的退避基准间隔 */
private val SERVICE_START_RETRY_DELAY: Duration = 500.milliseconds
/** 互斥进程消失后、重新拉起前的冷却时长 */
private val POST_PROCESS_EXIT_COOLDOWN: Duration = 1.seconds

/**
 * 运行一个简易的JVM环境，安装ModLoader，同时在jvm退出时，尝试使用其他的Java环境重试
 * @param logId 记录日志的 tag
 * @param start 刚开始启动会调用的回调
 * @param logOutput 安装 JVM 的实时日志输出
 * @param tried 已经尝试过（并失败）的 Java 运行库，仅用于在全部不可用时给出准确的报错
 */
suspend fun runJvmRetryRuntimes(
    logId: String,
    jvmArgs: String,
    prefixArgs: (Jre) -> String?,
    jre: Jre,
    userHome: String,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    start: () -> Unit = {},
    logOutput: TaskLogOutput? = null,
    tried: List<Jre> = emptyList()
): Unit = withContext(Dispatchers.Default) {
    val attempted = tried + jre

    waitForJvmExclusiveProcessesStopped(logId)

    // 装加载器 / 跑安装器 JAR 之前，再确认一次 Java 到底在不在：
    // 用户可能换了机型、清过数据，或者上一次解压被中断 —— 这里会当场补装内置运行库，
    // 而不是等重试链一路跑完再抛「没有可用的 Java 运行库」。
    // 有能用的运行库时只是几十次 stat，几乎不花时间。
    val readiness = RuntimeProvisioner.ensureFor(GlobalContext, jre.majorVersion, "install jvm:$logId")

    //先确认这个 Java 运行库是不是真的可用（目录存在、release 能解析出 java 版本与架构）。
    //内部运行库没解压好时，原来会照常起 :jvm 服务、然后在启动 JVM 的一瞬抛异常，
    //用户只能看到一句「JVM 退出码 1」；这里提前验一下：能低成本修就修（例如补个缺失的 release），
    //修不好就直接换下一个运行库重试。
    val runtimeError = runCatching {
        RuntimesManager.repairRuntime(jre.jreName)
        RuntimesManager.getRuntimeHome(jre.jreName)
    }.exceptionOrNull()
    if (runtimeError != null) {
        Logger.error(
            TAG,
            "Java runtime ${jre.jreName} is not available (${runtimeError.message}), trying the next one..."
        )
        retryWithNextJre(
            logId = logId,
            jvmArgs = jvmArgs,
            prefixArgs = prefixArgs,
            jre = jre,
            tried = attempted,
            userHome = userHome,
            postSummary = postSummary,
            postProgress = postProgress,
            logOutput = logOutput
        ) {
            IOException(
                buildString {
                    append("没有可用的 Java 运行库：\n")
                    attempted.forEach { tried ->
                        append("· ${tried.jreName} —— ${RuntimesManager.describeRuntimeProblem(tried.jreName)}\n")
                    }
                    // 自动修复也失败时，把「为什么装不上」一并写出来（架构不对 / 空间不足 / 资产读不到）
                    if (!readiness.ok) {
                        append("自动修复结果：${readiness.problem}\n")
                    }
                    append("请到「设置 → Java 运行库」检查，或导入 .xz 格式的运行库。")
                }
            )
        }
        return@withContext
    }

    start()

    val finalArgs = prefixArgs(jre)?.let {
        "$it $jvmArgs"
    } ?: jvmArgs

    val exitCode = startJvmServiceAndWaitExit(
        jvmArgs = finalArgs,
        jreName = jre.jreName,
        userHome = userHome,
        postSummary = postSummary,
        postProgress = postProgress,
        logOutput = logOutput
    )

    if (exitCode != 0) {
        retryWithNextJre(
            logId = logId,
            jvmArgs = jvmArgs,
            prefixArgs = prefixArgs,
            jre = jre,
            tried = attempted,
            userHome = userHome,
            postSummary = postSummary,
            postProgress = postProgress,
            logOutput = logOutput
        ) {
            JvmCrashException(exitCode)
        }
    }
}

/**
 * 换下一个 Java 运行库重试；已经没得换了就抛出 [noNextError] 结束
 */
private suspend fun retryWithNextJre(
    logId: String,
    jvmArgs: String,
    prefixArgs: (Jre) -> String?,
    jre: Jre,
    tried: List<Jre>,
    userHome: String,
    postSummary: String?,
    postProgress: NoticeProgress?,
    logOutput: TaskLogOutput?,
    noNextError: () -> Throwable
) {
    val nextJava: Jre? = when (jre) {
        Jre.JRE_8 -> Jre.JRE_17
        Jre.JRE_17 -> Jre.JRE_21
        else -> null
    }

    if (nextJava == null) throw noNextError()

    Logger.info(TAG, "Retry with jre ${nextJava.name}...")
    runJvmRetryRuntimes(
        logId = logId,
        jvmArgs = jvmArgs,
        prefixArgs = prefixArgs,
        jre = nextJava,
        userHome = userHome,
        postSummary = postSummary,
        postProgress = postProgress,
        //重试时不再重复 start()（安装阶段的提示已经在第一次设置好了，与原有行为一致）
        logOutput = logOutput,
        tried = tried
    )
}

/**
 * 等待互斥进程（:jvm、:game）退出后再开跑
 */
private suspend fun waitForJvmExclusiveProcessesStopped(logId: String) {
    val startNanos = System.nanoTime()
    var lastLog = -WAIT_LOG_INTERVAL
    var forceKilled = false
    var sawBlocking = false

    while (true) {
        val blocking = listBlockingProcesses(GlobalContext)
        if (blocking.isEmpty()) {
            if (!sawBlocking) {
                // 从未见过互斥进程则无需等待，直接放行
                return
            }
            // 刚等走过互斥进程，给 system_server 一点处理死亡事件的时间
            delay(POST_PROCESS_EXIT_COOLDOWN)
            if (listBlockingProcesses(GlobalContext).isEmpty()) return
            continue
        }

        sawBlocking = true

        val elapsed = (System.nanoTime() - startNanos).nanoseconds
        when {
            elapsed >= WAIT_BEFORE_FORCE_KILL + KILL_GRACE ->
                throw IOException(
                    "Timed out waiting for exclusive processes to stop: ${blocking.joinToString()}"
                )

            elapsed >= WAIT_BEFORE_FORCE_KILL -> {
                if (!forceKilled) {
                    forceKilled = true
                    Logger.warning(TAG, "$logId Force stopping blocking processes: $blocking")
                }
                stopAllNonMainProcesses(GlobalContext)
            }

            elapsed - lastLog >= WAIT_LOG_INTERVAL -> {
                lastLog = elapsed
                Logger.info(TAG, "$logId Waiting for other processes stop... [$blocking]")
            }
        }
        delay(100L.milliseconds)
    }
}

suspend fun startJvmServiceAndWaitExit(
    jvmArgs: String,
    jreName: String? = null,
    userHome: String? = null,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    logOutput: TaskLogOutput? = null,
): Int = withContext(Dispatchers.IO) {
    val doneSignal = CompletableDeferred<Unit>()
    //看门狗记录的失败原因；非空即表示这一轮是以失败结束的
    val watchdogFailure = AtomicReference<Throwable?>(null)
    var watchdogJob: kotlinx.coroutines.Job? = null

    val tailerJob = logOutput?.let {
        ProcessLogTailer(LATEST_PROCESS_LOG_FILE, it).launch(this)
    }

    try {
        // 先起接收端再拉起服务
        // JVM 秒退时退出码不会因 socket 尚未绑定而丢失
        JVMSocketServer.start { receiveMsg ->
            Logger.info(TAG, "receive msg: $receiveMsg, stopping server...")
            if (!doneSignal.isCompleted) {
                doneSignal.complete(Unit)
            }
            JVMSocketServer.stop()
        }

        //看门狗：JVM 到底有没有真的跑起来，唯一可靠的信号是 :jvm 子进程的生死。
        //若前台服务被系统拒绝（例如通知渠道缺失 → Bad notification for startForeground），
        //:jvm 会在启动后极短时间内崩溃、且**不会**回传退出码，主进程原本要白等满 15 分钟
        //（用户看到的就是「安装卡住」）。这里改成「观测到进程出现、随后消失且没有退出码」立即失败。
        //只观测到过 :jvm 才判定失败，避免个别 ROM 不上报子进程导致误判。
        watchdogJob = launch {
            var sawProcess = false
            while (isActive) {
                delay(JVM_WATCHDOG_INTERVAL)
                if (doneSignal.isCompleted) return@launch

                if (isJvmProcessAlive()) {
                    sawProcess = true
                    continue
                }
                if (!sawProcess) continue

                //进程已消失，但退出码可能还在路上
                delay(JVM_EXIT_CODE_GRACE)
                if (doneSignal.isCompleted || JVMSocketServer.receiveMsg != null) return@launch

                Logger.error(TAG, "The :jvm process died without sending an exit code, aborting the run.")
                watchdogFailure.set(
                    IOException("JVM 进程意外退出且没有回传退出码（通常是前台服务被系统拒绝）")
                )
                JVMSocketServer.stop()
                if (!doneSignal.isCompleted) doneSignal.complete(Unit)
                return@launch
            }
        }

        var attempt = 0
        while (true) {
            try {
                startJvmService(
                    context = GlobalContext,
                    jvmArgs = jvmArgs,
                    userHome = userHome,
                    jreName = jreName,
                    postSummary = postSummary,
                    postProgress = postProgress
                )
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                attempt++
                if (attempt > SERVICE_START_RETRY_MAX) {
                    Logger.error(TAG, "Failed to start JvmService after $attempt attempts", e)
                    throw e
                }
                val retryDelay = SERVICE_START_RETRY_DELAY * (1 shl (attempt - 1))
                Logger.warning(
                    TAG,
                    "Failed to start JvmService (attempt $attempt/$SERVICE_START_RETRY_MAX): " +
                        "${e.message}. Retrying in $retryDelay..."
                )
                delay(retryDelay)
            }
        }

        if (withTimeoutOrNull(JVM_EXIT_TIMEOUT) { doneSignal.await() } == null) {
            // 超时，停掉服务与接收端，让安装以可见错误结束而不是永久挂起
            Logger.error(TAG, "Timed out ($JVM_EXIT_TIMEOUT) waiting for JVM exit code, stopping service...")
            runCatching {
                GlobalContext.stopService(Intent(GlobalContext, JvmService::class.java))
            }
            throw IOException("Timed out waiting for the JVM process to exit.")
        }

        //看门狗判定失败（JVM 进程已消失却没有退出码）时优先抛出，方便用户直接看到原因
        watchdogFailure.get()?.let { throw it }
    } finally {
        // 无论成败、取消还是超时都收掉接收端与看门狗
        // 单例状态跨轮残留会毒化下一次运行
        watchdogJob?.cancel()
        JVMSocketServer.stop()
        tailerJob?.cancel()
    }

    val code = JVMSocketServer.receiveMsg?.toIntOrNull()
    Logger.info(TAG, "receive exit code: ${code ?: "unknown, default 0"}")
    code ?: 0
}

/**
 * `:jvm` 子进程是否还在运行（安装用的 JVM 就跑在这个进程里）
 */
private fun isJvmProcessAlive(): Boolean {
    val mainProcessName = GlobalContext.packageName
    return listBlockingProcesses(GlobalContext).any { it.startsWith("$mainProcessName:jvm") }
}