/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 皮肤自己的设置（对应 PCL 的 `个性化 → 标题栏 / 功能隐藏` 与 `背景图片 → 叠加彩色背景`）。
 *
 * 取值口径与 PCL 的设置项一一对应（`PageSetupUI.xaml` + `PageSetup/Settings.vb`）：
 *   `UiLogoType`    0 = 无 / 1 = 默认 / 2 = 文本 / 3 = 图片
 *   `UiLogoText`    标题栏文本
 *   `UiLogoLeft`    标题栏居左
 *   `UiBackgroundColorful`  叠加彩色背景（关掉就是 PCL 那个 #F5F5F5 纯色底）
 *   `UiHiddenPage*` 隐藏顶部导航的版块、以及设置/更多的子页面
 *
 * **与 Zalith 的衔接**：PCL 没有的项（背景不透明度 / 背景模糊）直接用 Zalith 的设置
 * （`AllSettings.launcherBackgroundOpacity` / `AllSettings.backgroundBlur`），
 * 所以两边改同一个值；PCL 独有的项存进 Zalith 的 MMKV（不污染 Zalith 的设置界面）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zalith.pcl.setting.launcherMMKV

/** `PageSetupUI.xaml` 的 `UiLogoType`。 */
enum class PclLogoType { None, Default, Text, Picture }

/** 可隐藏的版块 / 子页面的键（与 PCL 的 `UiHiddenPage*` 设置一一对应）。 */
object PclHiddenKeys {
    const val HOME = "launch"
    const val DOWNLOAD = "download"
    const val LINK = "link"
    const val SETUP = "setup"
    const val OTHER = "other"

    // 设置 子页面（UiHiddenSetupLaunch / Link / Ui / System）
    const val SETUP_GAME = "setup.launch"
    const val SETUP_LINK = "setup.link"
    const val SETUP_UI = "setup.ui"
    const val SETUP_SYSTEM = "setup.system"

    // 设置 子页面（Zalith 原生独有，PCL2 没有这四页）
    const val SETUP_CONTROL = "setup.control"
    const val SETUP_GAMEPAD = "setup.gamepad"
    const val SETUP_JAVA = "setup.java"
    const val SETUP_CONTROL_MANAGE = "setup.controlManage"

    // 更多 子页面（UiHiddenOtherHelp / About / Test / Feedback / Vote）
    const val OTHER_HELP = "other.help"
    const val OTHER_ABOUT = "other.about"
    const val OTHER_TOOLS = "other.tools"
    const val OTHER_FEEDBACK = "other.feedback"
    const val OTHER_VOTE = "other.vote"
}

object PclSkinSettings {

    private const val KEY_LOGO_TYPE = "pcl2.ui.logoType"
    private const val KEY_LOGO_TEXT = "pcl2.ui.logoText"
    private const val KEY_LOGO_LEFT = "pcl2.ui.logoLeft"
    private const val KEY_COLORFUL = "pcl2.ui.backgroundColorful"
    private const val KEY_HIDDEN = "pcl2.ui.hidden"
    private const val KEY_UI_SCALE = "pcl2.ui.scale"
    private const val KEY_HOME_PANEL_RIGHT = "pcl2.ui.homePanelRight"

    /** 「界面缩放」的自动档：按窗口尺寸挑一个最接近 PCL 观感的档位。 */
    const val UI_SCALE_AUTO = -1

    /** 自动档的上限。PCL 的窗口是 850×500，放大到窗口里放得下就停。 */
    const val UI_SCALE_MAX = 1.25f

    /** 手动可选档位（百分比）。 */
    val UI_SCALE_PRESETS = listOf(50, 75, 100, 110, 125, 150)

    /**
     * 「临时显示被隐藏的入口」手势：右下角连点次数。
     *
     * 写在一处，外壳的手势与设置页的提示文案都引用它 —— 两边一旦不同步，
     * 用户照着提示点就是没反应。
     */
    const val PEEK_TAP_COUNT = 10

    /** 临时显示保持多久（毫秒）。到期后入口重新藏起来。 */
    const val PEEK_DURATION_MS = 30_000L

    var logoType by mutableStateOf(PclLogoType.Default)
        private set
    var logoText by mutableStateOf("")
        private set
    var logoLeft by mutableStateOf(true)
        private set

    /** `UiBackgroundColorful`：关掉就用 PCL 的 `#F5F5F5` 纯色底。 */
    var colorfulBackground by mutableStateOf(true)
        private set

    /** 被隐藏的版块 / 子页面（见 [PclHiddenKeys]）。 */
    var hidden by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * 「临时显示被隐藏的入口」的截止时刻（`SystemClock.elapsedRealtime()` 口径，0 = 未开启）。
     *
     * `UiHiddenPage*` 勾上之后入口会从界面上消失，而取消勾选的开关本身就在被藏起来的
     * 设置页里 —— 把「设置」勾掉就等于把自己锁在门外。所以右下角连点
     * [PEEK_TAP_COUNT] 次可以**临时**把被隐藏的入口放出来 [PEEK_DURATION_MS]，
     * 趁这段时间进去取消勾选；时间一到自动恢复隐藏（[shouldHide] 立刻按老样子过滤）。
     *
     * 只影响「画不画」，[hidden] 里的勾选状态一个字都不改 —— 时间过了还是隐藏。
     */
    var peekUntil by mutableLongStateOf(0L)
        private set

    /**
     * 「界面缩放」：`UI_SCALE_AUTO` 或百分比（100 / 110 / 125 / 150）。
     *
     * PCL 是桌面程序，Windows 的显示缩放会让「字与框一起放大」（WPF 按 DIP 整体缩放）；
     * 手机上则是靠 density 表达同样的东西。这里把系数乘进 `PclTheme` 的 density，
     * 于是整套界面（连同字号）等比放大 —— 字变小到看不清笔画时，直接调大这一档就行。
     */
    var uiScale by mutableStateOf(UI_SCALE_AUTO)
        private set

    /**
     * 启动页（主页）的「启动面板」放在左边还是右边。
     *
     * PCL 是桌面程序，启动面板固定在最左一列；手机上单手拿横屏时，最左边那列
     * 往往正好被握持的手挡住，所以给一个「挪到右边」的开关 —— 换边只是把
     * 左栏与内容区的**位置**对调，两块内容本身一模一样（登录 / 启动按钮仍在面板里）。
     */
    var homePanelOnRight by mutableStateOf(false)
        private set

    /** 用户勾的原始状态（设置页的复选框看这个）。 */
    fun isHidden(key: String): Boolean = key in hidden

    /**
     * 界面渲染用：勾了隐藏、**且**当前不在「临时显示」窗口里，才真的不画。
     *
     * 三处生效点（顶部导航、设置左栏、更多左栏）与「当前版块被藏就退回主页」
     * 都用这个，而不是 [isHidden] —— 否则连点十次放出来的入口点不进去。
     */
    fun shouldHide(key: String): Boolean = key in hidden && !isPeeking()

    fun isPeeking(): Boolean = peekUntil > SystemClock.elapsedRealtime()

    /** 被隐藏的入口数量（提示文案用）。 */
    val hiddenCount: Int get() = hidden.size

    /** 开始（或续上一轮）临时显示。 */
    fun startPeek(durationMs: Long = PEEK_DURATION_MS) {
        peekUntil = SystemClock.elapsedRealtime() + durationMs
    }

    fun stopPeek() {
        peekUntil = 0L
    }

    /** 设置顶层「主页」是不可隐藏的（隐藏了就没有可去的地方了）。 */
    fun setHidden(key: String, value: Boolean) {
        hidden = if (value) hidden + key else hidden - key
    }

    fun applyLogoType(value: PclLogoType) {
        logoType = value
    }

    fun applyLogoText(value: String) {
        logoText = value
    }

    fun applyLogoLeft(value: Boolean) {
        logoLeft = value
    }

    fun applyColorful(value: Boolean) {
        colorfulBackground = value
    }

    fun applyUiScale(value: Int) {
        uiScale = value
    }

    fun applyHomePanelOnRight(value: Boolean) {
        homePanelOnRight = value
    }

    /**
     * 实际生效的缩放系数。
     *
     * 「自动」固定按 75% 显示：不同机型不再按窗口尺寸算出大小不一的界面，
     * 75% 下控件与字号都还能看清，同时能在一屏里放下更多内容。
     */
    fun resolveScale(windowWidthDp: Float, windowHeightDp: Float): Float {
        if (uiScale != UI_SCALE_AUTO) return uiScale / 100f
        return 0.75f
    }

    fun load(context: Context) {
        val mmkv = launcherMMKV()
        logoType = PclLogoType.entries.getOrNull(mmkv.getInt(KEY_LOGO_TYPE, PclLogoType.Default.ordinal))
            ?: PclLogoType.Default
        logoText = mmkv.getString(KEY_LOGO_TEXT, "").orEmpty()
        logoLeft = mmkv.getBoolean(KEY_LOGO_LEFT, true)
        colorfulBackground = mmkv.getBoolean(KEY_COLORFUL, true)
        hidden = mmkv.getString(KEY_HIDDEN, "").orEmpty()
            .split('|')
            .filter { it.isNotBlank() }
            .toSet()
        uiScale = mmkv.getInt(KEY_UI_SCALE, UI_SCALE_AUTO)
        homePanelOnRight = mmkv.getBoolean(KEY_HOME_PANEL_RIGHT, false)
    }

    fun persist(context: Context) {
        launcherMMKV().apply {
            putInt(KEY_LOGO_TYPE, logoType.ordinal)
            putString(KEY_LOGO_TEXT, logoText)
            putBoolean(KEY_LOGO_LEFT, logoLeft)
            putBoolean(KEY_COLORFUL, colorfulBackground)
            putString(KEY_HIDDEN, hidden.joinToString("|"))
            putInt(KEY_UI_SCALE, uiScale)
            putBoolean(KEY_HOME_PANEL_RIGHT, homePanelOnRight)
        }
    }
}


/**
 * 离线登录的用户名（PCL 的 `ComboName` 会记住上次输入的名字）。
 *
 * Zalith 只在「启动游戏」时才把名字落成账户，所以在没建账户之前名字是悬空的 ——
 * 这里单独存一份，退出应用再打开还在。
 */
object PclLoginSettings {
    private const val KEY_OFFLINE_NAME = "pcl2.login.offlineName"

    var offlineName by mutableStateOf("")
        private set

    fun applyOfflineName(value: String) {
        offlineName = value
    }

    fun load(context: Context) {
        offlineName = launcherMMKV().getString(KEY_OFFLINE_NAME, "").orEmpty()
    }

    fun persist(context: Context) {
        launcherMMKV().putString(KEY_OFFLINE_NAME, offlineName)
    }
}
