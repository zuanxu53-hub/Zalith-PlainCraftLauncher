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

import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import com.zalith.pcl.context.GlobalContext
import com.zalith.pcl.coroutine.MutableTransitionStateFlow
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.viewmodel.EventViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.burningtnt.terracotta.TerracottaAndroidAPI
import java.io.IOException
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Terracotta"

/**
 * 原生层给一个 VPN 请求预留的等待时间是 **30 秒**（`TerracottaAndroidAPI.onVpnServiceStateChanged`），
 * 超时就会在原生回调里抛 `IllegalStateException`，异常顺着 JNI 冒出去直接终结进程 ——
 * 界面上看到的就是「应用程序意外崩溃了」，栈顶固定是 `onVpnServiceStateChanged`。
 *
 * 这里留 12 秒的余量自己兜一次底：界面还活着的时候正常路径通常 1 秒内就建立隧道了，
 * 12 秒还没人认领基本就说明事件丢了 / 界面已经销毁 / 授权框被晾在一边没人管。
 */
private const val VPN_REQUEST_FALLBACK_DELAY = 12_000L

/**
 * [Reference FCL](https://github.com/FCL-Team/FoldCraftLauncher/blob/52f0542/FCL/src/main/java/com/tungsten/fcl/terracotta/Terracotta.java)
 */
object Terracotta {
    const val TERRACOTTA_USER_NOTICE_VERSION = 1

    enum class Mode {
        /** 房主模式 */
        Host,
        /** 房客模式 */
        Guest
    }

    var initialized = false
        private set

    private var eventViewModel: EventViewModel? = null

    private var notificationJob: Job? = null
    private var metadata: TerracottaAndroidAPI.Metadata? = null

    /** 无人认领 VPN 请求时的兜底任务，见 [scheduleVpnFallback] */
    private var vpnFallbackJob: Job? = null

    var mode: Mode? = null
        private set

    private val _state = MutableTransitionStateFlow<TerracottaState.Ready?>(null)
    val state = _state.stateFlow
    val stateChanges = _state.changes

    private val stateRef = AtomicReference<TerracottaState.Ready?>(null)

    /**
     * 状态轮询与前台通知用的**进程级**作用域。
     *
     * 它们原先跑在调用方传进来的 `viewModelScope` / `lifecycleScope` 上，而陶瓦是个进程级单例
     * （`initialized` 一旦为真，后面的 `initialize` 就只剩 `return`）：于是「谁先初始化，
     * 循环就挂在谁的界面上」。界面一销毁循环就没了，之后换到别的界面（PCL2 联机页 ↔ 游戏内菜单）
     * 状态将永远停在旧值。挪到进程级之后，两个界面谁先谁后都不会互相打断。
     */
    private val runtimeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 初始化陶瓦联机。
     *
     * 与上面同一个道理，**每次调用都会刷新 [eventViewModel]**：陶瓦状态是进程级共享的，
     * 但 `EventViewModel` 是每个 Activity 一份。不刷新的话，VPN 授权请求会被投递给一个
     * 已经销毁的界面，表现就是「点了创建房间没有任何反应」。
     */
    fun initialize(eventViewModel: EventViewModel) {
        this.eventViewModel = eventViewModel
        if (initialized) return

        metadata = TerracottaAndroidAPI.initialize(
            PathManager.DIR_TERRACOTTA.absolutePath,
            PathManager.FILE_TERRACOTTA_LOG.absolutePath
        ) {
            // ⚠️ 这个回调是**原生线程**上执行的（JNI 反调进 Java）。
            // 任何异常从这里冒回去都会被 JVM 当成未捕获异常，直接走
            // `ZLApplication` 的崩溃处理 → 弹「应用程序意外崩溃了」→ 杀进程。
            // 所以下面做的所有事都必须自己吞异常，一个都不许漏出去。
            handleVpnServiceRequest()
        }

        runtimeScope.launch(Dispatchers.Default) {
            // 轮询失败（原生层 panic、状态 JSON 解析出错）原本会顺着 `runtimeScope` 的
            // 未捕获异常处理器把整个启动器带崩。这里收住、降频重试 —— 状态机卡在旧值
            // 顶多是界面不动，闪退则是直接打断用户。
            var lastFailureAt = 0L
            while (true) {
                try {
                    val current = stateRef.get()
                    val index = current?.index ?: -1

                    val stateJson = TerracottaAndroidAPI.getState()
                    val obj = TerracottaState.TerracottaStateGson.fromJson<TerracottaState.Ready>(
                        stateJson,
                        object : TypeToken<TerracottaState.Ready>() {}.type
                    ) ?: throw JsonParseException("Json object cannot be null.")

                    if (obj.index > index) {
                        compareAndSet(current, obj)
                    }

                    delay(1L.milliseconds)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    val now = System.currentTimeMillis()
                    if (now - lastFailureAt > 30_000L) {
                        lastFailureAt = now
                        Logger.warning(TAG, "Cannot poll the Terracotta state", t)
                    }
                    delay(500L)
                }
            }
        }

        initialized = true
    }

    fun setWaiting(manual: Boolean) {
        if (!initialized) return

        // 回到等待大厅说明隧道已经用不上了，兜底任务跟着取消，免得回头又拉起一个服务
        vpnFallbackJob?.cancel()
        vpnFallbackJob = null

        stopNotificationJob()
        if (manual) this.eventViewModel?.sendEvent(EventViewModel.Event.Terracotta.StopVPN)
        TerracottaAndroidAPI.setWaiting()
    }

    fun setScanning(room: String?, player: String?, extraNodes: List<String>?) {
        checkInitialized()
        if (_state.value !is TerracottaState.Waiting)
            throw Exception("reset state to waiting first!")

        mode = Mode.Host
        TerracottaAndroidAPI.setScanning(room, player, extraNodes)
    }

    fun setGuesting(room: String, player: String?, extraNodes: List<String>?): Boolean {
        checkInitialized()
        if (_state.value !is TerracottaState.Waiting)
            throw Exception("reset state to waiting first!")

        mode = Mode.Guest
        return TerracottaAndroidAPI.setGuesting(room, player, extraNodes)
    }

    fun parseRoomCode(room: String?): TerracottaAndroidAPI.RoomType? {
        if (!initialized || room == null) return null
        return TerracottaAndroidAPI.parseRoomCode(room)
    }

    fun getMetadata(): TerracottaAndroidAPI.Metadata =
        metadata ?: TerracottaAndroidAPI.Metadata("unknown", 0, "unknown")

    fun collectLogs(): String? {
        if (!initialized) return null
        return try {
            TerracottaAndroidAPI.collectLogs().use { reader ->
                val writer = StringWriter()
                val buf = CharArray(4096)
                var n: Int
                while (reader.read(buf).also { n = it } != -1) {
                    writer.write(buf, 0, n)
                }
                writer.toString()
            }
        } catch (e: IOException) {
            e.message?.let { Logger.warning(TAG, it) }
            "Failed to collect logs: ${e.message}"
        }
    }

    @Deprecated("This API is exposed for debug purpose.")
    fun testNativePanic() {
        if (!initialized) return
        TerracottaAndroidAPI.panic()
    }

    /**
     * 原生层要一条隧道时走这里。
     *
     * **这个方法跑在原生线程上**，抛出去的东西会顺着 JNI 变成「未捕获异常」，
     * 直接把启动器带崩（崩溃栈顶永远是 `onVpnServiceStateChanged`）。所以：
     * 一不许抛，二必须保证这条请求最终有人认领 —— 原生层只等 30 秒。
     */
    private fun handleVpnServiceRequest() {
        // 留一条痕迹：这个回调一旦出问题就是闪退，日志里至少要知道「请求来过、什么时候来的」
        Logger.info(TAG, "VPN service request received")

        try {
            runtimeScope.startNotificationJob()
        } catch (t: Throwable) {
            Logger.warning(TAG, "Cannot start the notification job", t)
        }

        // 这里必须用字段而不是 initialize 的入参：回调是原生层以后才调的，
        // 中间界面很可能已经换过一轮，用入参就会把请求发给老界面的 ViewModel。
        val target = eventViewModel
        if (target == null) {
            Logger.warning(TAG, "VPN request received but no event channel is attached yet")
        } else {
            try {
                target.sendEvent(EventViewModel.Event.Terracotta.RequestVPN)
            } catch (t: Throwable) {
                // 事件投递失败（ViewModel 已销毁之类）不能连累原生回调，下面还有兜底
                Logger.warning(TAG, "Cannot dispatch the VPN request", t)
            }
        }

        scheduleVpnFallback()
    }

    /**
     * 兜底：万一界面上没人认领这条 VPN 请求（事件丢了、界面已经销毁、授权框被晾在一边），
     * 到点由我们自己把服务拉起来，绝不让它挂到原生层的 30 秒超时上。
     */
    private fun scheduleVpnFallback() {
        vpnFallbackJob?.cancel()
        vpnFallbackJob = runtimeScope.launch {
            delay(VPN_REQUEST_FALLBACK_DELAY)

            // 请求还在挂着才会抛，抛了说明已经有人建立隧道/干脆拒掉了，无需插手
            val pending = try {
                TerracottaAndroidAPI.getPendingVpnServiceRequest()
            } catch (_: IllegalStateException) {
                null
            }
            if (pending == null) return@launch

            val context = try {
                GlobalContext
            } catch (t: Throwable) {
                Logger.warning(TAG, "Cannot access the application context for the VPN fallback", t)
                return@launch
            }

            // 还没授权时只能靠系统弹窗；后台起不了界面，硬来只会再抛一次，交给用户自己点
            val consentIntent = try {
                VpnService.prepare(context)
            } catch (t: Throwable) {
                Logger.warning(TAG, "Cannot query the VPN consent state", t)
                return@launch
            }
            if (consentIntent != null) {
                Logger.warning(TAG, "VPN request is still pending while consent is missing; skip the fallback")
                return@launch
            }

            Logger.warning(TAG, "VPN request is still pending after $VPN_REQUEST_FALLBACK_DELAY ms; starting the service from the fallback")
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TerracottaVPNService::class.java)
                        .setAction(TerracottaVPNService.ACTION_START),
                )
            } catch (t: Throwable) {
                // 后台启动前台服务在 Android 12+ 会被拦，拦了就只能等原生层超时
                Logger.warning(TAG, "The VPN fallback service start was rejected", t)
            }
        }
    }

    private fun checkInitialized() {
        if (!initialized) throw Exception("initialize Terracotta first!")
    }

    private fun compareAndSet(previous: TerracottaState.Ready?, next: TerracottaState.Ready) {
        if (stateRef.compareAndSet(previous, next)) {
            _state.set(next)
        }
    }

    private fun CoroutineScope.startNotificationJob() {
        notificationJob?.cancel()
        notificationJob = launch(Dispatchers.Default) {
            state.collect { state ->
                if (state != null && state !is TerracottaState.Waiting) {
                    val stringRes = state.localStringRes()
                    eventViewModel?.sendEvent(EventViewModel.Event.Terracotta.VPNUpdateState(stringRes))
                }
            }
        }
    }

    private fun stopNotificationJob() {
        notificationJob?.cancel()
        notificationJob = null
    }
}
