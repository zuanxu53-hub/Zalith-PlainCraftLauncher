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

package com.zalith.pcl.game.launch.handler

import android.app.Activity
import android.view.KeyEvent
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import com.zalith.pcl.bridge.LoggerBridge.appendInfo
import com.zalith.pcl.bridge.ZLBridge
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.input.EfficientAndroidLWJGLKeycode
import com.zalith.pcl.game.input.LWJGLCharSender
import com.zalith.pcl.game.launch.GameLauncher
import com.zalith.pcl.game.launch.LaunchConfig
import com.zalith.pcl.game.launch.MCOptions
import com.zalith.pcl.game.launch.loadLanguage
import com.zalith.pcl.game.sdl.SdlBridge
import com.zalith.pcl.game.sdl.handleGamepadKeyEvent
import com.zalith.pcl.game.version.installed.GraphicsApi
import com.zalith.pcl.game.version.installed.utils.isLowerVer
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.setting.enums.GamepadInputMode
import com.zalith.pcl.terracotta.Terracotta
import com.zalith.pcl.ui.control.gamepad.isGamepadKeyEvent
import com.zalith.pcl.ui.control.input.TextInputMode
import com.zalith.pcl.ui.screens.game.GameScreen
import com.zalith.pcl.ui.screens.game.elements.LogState
import com.zalith.pcl.ui.screens.game.elements.mutableStateOfLog
import com.zalith.pcl.viewmodel.ErrorViewModel
import com.zalith.pcl.viewmodel.EventViewModel
import com.zalith.pcl.viewmodel.GamepadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libsdl.app.SDLActivity
import org.lwjgl.glfw.CallbackBridge

class GameHandler(
    val activity: Activity,
    config: LaunchConfig,
    errorViewModel: ErrorViewModel,
    eventViewModel: EventViewModel,
    private val gamepadViewModel: GamepadViewModel,
    gameLauncher: GameLauncher,
    onExit: (code: Int) -> Unit
) : AbstractHandler(
    type = HandlerType.GAME,
    errorViewModel = errorViewModel,
    eventViewModel = eventViewModel,
    launcher = gameLauncher,
    onExit = onExit
) {
    private val version = config.version
    private val account = config.account

    private val _inputArea = MutableStateFlow<IntRect?>(null)
    override val inputArea = _inputArea.asStateFlow()

    private var isGameRendering = false
    private var showGameInfo by mutableStateOf(true)

    /**
     * 日志展示状态
     */
    private var logState by mutableStateOfLog()

    override suspend fun execute(
        surface: Surface,
        screenSize: IntSize,
        scope: CoroutineScope
    ) {
        ZLBridge.setupBridgeWindow(surface)

        MCOptions.setup(activity, version)

        MCOptions.apply {
            set("fullscreen", "false")
            set("touchscreen", "false")

//            //关闭文本转语音功能
//            set("options.narrator", "0")
//            set("narrator", "0")

            if (version.getVersionInfo()!!.minecraftVersion.isLowerVer("1.13")) {
                //fix: 牢版本按键事件
                //shift + w -> 87 错误的触发了F11，切换全屏
                set("key_key.fullscreen", "0")
                //输入字符@ -> 64 错误的触发了F6，触发“开始/停止直播”
                set("key_key.streamStartStop", "0")
                set("key_key.streamPauseUnpause", "0")
            }

            set("overrideWidth", screenSize.width.toString())
            set("overrideHeight", screenSize.height.toString())

            val graphicsApi = version.getGraphicsApi()
            val graphicsOption = "preferredGraphicsBackend"
            when (graphicsApi) {
                GraphicsApi.DEFAULT, GraphicsApi.DEFAULT_OPENGL -> {
                    /**
                     * 「自动 / OpenGL」的语义是「让游戏走 OpenGL」，不只是「没写过就写一个默认值」。
                     *
                     * 26.2 起游戏自己有 Vulkan 后端，玩家在游戏里点过一次 Vulkan 之后（或者
                     * 某个整合包自带的 options.txt 就是 vulkan），这个键会一直是 `"vulkan"`。
                     * 而 Android 上的画面是启动器用 EGL + OpenGL 桥接出来的，Vulkan 后端拿不到
                     * 可用的窗口表面，典型表现就是**加载一会儿进程自己退出、回到启动器主页**。
                     * 所以这里把残留的 vulkan 改回 opengl；真想用 Vulkan 的玩家可以在
                     * 「实例设置 → 图形 API」里显式选 Vulkan，那条分支不会被覆盖。
                     */
                    val leftover = get(graphicsOption)
                    val leftoverVulkan = leftover?.contains("vulkan", ignoreCase = true) == true
                    if (!containsKey(graphicsOption) || leftoverVulkan) {
                        set(graphicsOption, graphicsApi.option)
                        if (leftoverVulkan) {
                            appendInfo(
                                "options.txt 中的图形后端为 $leftover，已按「自动选择（OpenGL）」改回 ${graphicsApi.option}"
                            )
                        }
                    }
                }
                else -> {
                    set(graphicsOption, graphicsApi.option)
                    if (graphicsApi == GraphicsApi.VULKAN) {
                        // 显式选 Vulkan 的分支不会被上面的「改回 opengl」逻辑碰，
                        // 所以白屏时日志里必须留下能对上的线索，否则只能靠猜。
                        appendInfo(
                            "图形 API 已按设置强制为 Vulkan。" +
                                    "Android 上画面是启动器用 EGL + OpenGL 桥接出来的，" +
                                    "游戏自带的 Vulkan 后端拿不到可用窗口表面，" +
                                    "典型表现是进游戏后白屏或直接退回启动器主页。" +
                                    "如果这一条之后没有画面，请把「图形 API」改成「跟随游戏设定」。"
                        )
                    }
                }
            }

            loadLanguage(version.getVersionInfo()!!.minecraftVersion)
            save()
        }

        super.execute(surface, screenSize, scope)
    }

    override fun onPause() {
    }

    override fun onResume() {
        refreshControls()
        eventViewModel.sendEvent(EventViewModel.Event.Game.OnResume)
    }

    override fun onDestroy() {
        Terracotta.setWaiting(false)
    }

    override fun onGraphicOutput() {
        if (!isGameRendering) {
            isGameRendering = true
            showGameInfo = false
            //游戏已经开始渲染，如果日志状态为渲染前显示，则在这里关闭日志
            if (logState == LogState.SHOW_BEFORE_LOADING) {
                logState = LogState.CLOSE
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldIgnoreKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && (event.flags and KeyEvent.FLAG_CANCELED) != 0) return false

        if (event.isGamepadKeyEvent()) {
            if (AllSettings.gamepadControl.state && gamepadViewModel.checkModePrompt()) {
                //模式选择询问完成前，吞掉所有手柄按键输入
                return false
            }
            if (AllSettings.gamepadControl.state && AllSettings.gamepadInputMode.state == GamepadInputMode.SdlDirect) {
                //SDL 直通模式下，按键原样交给 SDL native
                if (SdlBridge.sdlEnabled) {
                    //让控制布局等注册方感知到手柄使用中
                    gamepadViewModel.notifyActivity()
                    handleGamepadKeyEvent(event)
                    try {
                        SDLActivity.handleKeyEvent(null, event.keyCode, event, null)
                    } catch (_: UnsatisfiedLinkError) {
                        //SDL native 未就绪时忽略
                    }
                }
                return false
            }
            return if (AllSettings.gamepadControl.state) {
                //开启时，提前发送事件，在UI层处理（或重映射）
                gamepadViewModel.sendKeyEvent(event)
                false
            } else {
                //已禁用手柄控制，避免继续向下被当作键盘事件进行处理
                if (AllSettings.showMenuBall.state) {
                    //开启游戏菜单悬浮窗时，完全无响应
                    false
                } else {
                    true
                }
            }
        }
        //已在VMActivity绑定onBackPressedDispatcher，这里不应该继续向下处理
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true

        if ((event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                LWJGLCharSender.sendEnter()
                return false
            }
            //输入法产生的字符类按键事件不能转发给游戏
            if (event.getUnicodeChar() != 0) return true
        }

        EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode).takeIf { it >= 0 }?.let { index ->
            EfficientAndroidLWJGLKeycode.execKey(event, index)
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.ACTION_MULTIPLE,
            KeyEvent.ACTION_UP
                 -> false

            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
                 -> true

            else -> (event.flags and KeyEvent.FLAG_FALLBACK) != KeyEvent.FLAG_FALLBACK
        }
    }

    override fun sendMouseRight(isPressed: Boolean) {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), isPressed)
    }

    @Composable
    override fun ComposableLayout(
        textInputMode: TextInputMode
    ) {
        GameScreen(
            version = version,
            gameHandler = this,
            showGameInfo = showGameInfo,
            onInfoBoxClose = { showGameInfo = false },
            logState = logState,
            onLogStateChange = { logState = it },
            textInputMode = textInputMode,
            isTouchProxyEnabled = version.enableTouchProxy,
            onInputAreaRectUpdated = { _inputArea.value = it },
            getAccountName = { account.username },
            eventViewModel = eventViewModel,
            gamepadViewModel = gamepadViewModel,
        )
    }

    private fun refreshControls() {
        ControlManager.refresh()
    }

    init {
        refreshControls()
    }
}