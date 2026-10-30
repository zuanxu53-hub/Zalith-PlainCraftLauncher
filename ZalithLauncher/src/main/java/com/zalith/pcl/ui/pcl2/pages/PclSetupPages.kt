/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 设置页的四张子页面，沿用 PCL2 原始 XAML 的卡片结构与行标签：
 *   PageSetupLaunch.xaml   启动
 *   PageSetupLink.xaml     联机
 *   PageSetupUI.xaml       个性化
 *   PageSetupSystem.xaml   其他（启动器）
 *
 * 与纯外观版的关键区别：本文件的每一个控件都直接读写 Zalith 的真实设置系统
 * （AllSettings / Renderers / DriverPluginManager / RuntimesManager），
 * 不存在只改本地状态、不落盘的“空壳”控件。
 *
 * PCL2 有、而 Zalith 后端并不存在对应能力的卡片（离线皮肤、背景音乐自定义、
 * 主页自定义、功能隐藏、设置导入导出、节点状态等）已经从页面中移除，
 * 以免留下点了没反应的假控件；其余控件全部接上真实设置。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.PaletteStyle
import com.zalith.pcl.game.download.assets.platform.Platform
import com.zalith.pcl.game.plugin.driver.DriverPluginManager
import com.zalith.pcl.game.plugin.renderer_v2.RendererV2Data
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.game.version.installed.GRAPHICS_API_CHOICES
import com.zalith.pcl.game.version.installed.GraphicsApi
import com.zalith.pcl.game.version.installed.forUi
import com.zalith.pcl.game.version.installed.uiLabel
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.data.PclSkinSettings
import com.zalith.pcl.ui.pcl2.data.PclLogoType
import com.zalith.pcl.ui.pcl2.data.PclHiddenKeys
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.zalith.pcl.setting.enums.ActionMenuSide
import com.zalith.pcl.setting.enums.AppLanguage
import com.zalith.pcl.setting.enums.BackgroundBlur
import com.zalith.pcl.setting.enums.DarkMode
import com.zalith.pcl.setting.enums.MirrorSourceType
import com.zalith.pcl.setting.enums.ResolutionRule
import com.zalith.pcl.setting.enums.applyLanguage
import com.zalith.pcl.setting.unit.AbstractSettingUnit
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclExtraTextButton
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclRendererConfigDialog
import com.zalith.pcl.ui.pcl2.components.PclImageLoader
import com.zalith.pcl.ui.pcl2.components.PclRadioBox
import com.zalith.pcl.ui.pcl2.components.PclSlider
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.data.PclAppearance
import com.zalith.pcl.ui.pcl2.data.PclBackgroundSuit
import com.zalith.pcl.ui.pcl2.data.PclHomeType
import com.zalith.pcl.ui.pcl2.data.PclSettings
import com.zalith.pcl.ui.pcl2.pages.pclInstanceVersionPage
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.animation.TransitionAnimationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 设置项一行的标签宽度（原 XAML 为 `SharedSizeGroup="Name"` 自适应列）。 */
// PCL 设置行：标签列 Width="Auto" + SharedSizeGroup="Name1"，标签自身 Margin="0,0,20,0"。
// Compose 没有共享尺寸组，这里用一个固定宽度近似「本页最宽标签」。
private val LabelWidth = 140.dp

/** 从 `content://` 里取显示名（拿不到就返回 null，导入时会用默认文件名）。 */
private fun queryDisplayName(context: Context, uri: Uri): String? =
    runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()

/** 设置项行：标签 + 控件（`Grid ColumnDefinitions Auto / 1*`）。 */
@Composable
private fun SetupRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(text = label, modifier = Modifier.width(LabelWidth).padding(end = 20.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/* ============================================================================
 *  通用设置绑定控件（读写真实设置，落盘到 MMKV）
 * ==========================================================================*/

/** 布尔设置 → 复选框。 */
@Composable
private fun SettingCheck(
    text: String,
    unit: AbstractSettingUnit<Boolean>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PclCheckBox(
        text = text,
        checked = unit.state,
        modifier = modifier,
        enabled = enabled,
        onCheckedChange = { unit.save(it) },
    )
}

/** 字符串设置 → 文本框（本地持有输入态，避免每次按键回写引起的跳光标）。 */
@Composable
private fun SettingTextBox(
    unit: AbstractSettingUnit<String>,
    modifier: Modifier = Modifier,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var text by remember(unit) { mutableStateOf(unit.state) }
    PclTextBox(
        value = text,
        modifier = modifier,
        hint = hint,
        singleLine = singleLine,
        enabled = enabled,
        onValueChange = {
            text = it
            unit.save(it)
        },
    )
}

/** 任意可选值 → 下拉框（选中即落盘）。 */
@Composable
private fun <T> SettingCombo(
    label: String,
    items: List<T>,
    selected: T?,
    modifier: Modifier = Modifier,
    labelOf: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit,
) {
    SetupRow(label) {
        PclComboBox(
            items = items,
            selected = selected,
            modifier = Modifier.fillMaxWidth().then(modifier),
            label = { item -> item?.let(labelOf).orEmpty() },
            onSelect = onSelect,
        )
    }
}

/** 整数设置 → 滑杆（控件使用 0..1 的比例，这里换算成实际区间）。 */
@Composable
private fun SettingSlider(
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (max <= min) return
    val value = unit.state.coerceIn(min, max)
    PclSlider(
        value = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f),
        modifier = modifier,
        enabled = enabled,
        onValueChange = { fraction ->
            unit.save((min + fraction * (max - min)).roundToInt().coerceIn(min, max))
        },
    )
}

/** 读取设备内存：已用 / 总量（MB）。 */
private fun memoryStats(context: Context): Pair<Int, Int> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return 0 to 0
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    val totalMb = (info.totalMem / (1024L * 1024L)).toInt()
    val availMb = (info.availMem / (1024L * 1024L)).toInt()
    return (totalMb - availMb).coerceAtLeast(0) to totalMb
}

/* ============================================================================
 *  PageSetupLaunch —— 启动
 * ==========================================================================*/

@Composable
fun PclSetupLaunchPage() {
    val context = LocalContext.current
    val nav = LocalPclNav.current

    PclContent {
        // ---- 启动选项（全部为 AllSettings 中的真实条目）
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchOptions) {
            Column {
                SettingCombo(
                    label = PclStrings.LangPageSetupLaunchLaunchVersionIsolation,
                    items = listOf(true, false),
                    selected = AllSettings.versionIsolation.state,
                    labelOf = { if (it) "开启版本隔离" else "关闭版本隔离" },
                    onSelect = { AllSettings.versionIsolation.save(it) },
                )
                SetupRow(PclStrings.LangPageSetupLaunchLaunchCustomInfo) {
                    SettingTextBox(AllSettings.versionCustomInfo, Modifier.fillMaxWidth())
                }
                SettingCheck("不检查游戏完整性（跳过文件校验，启动更快）", AllSettings.skipGameIntegrityCheck)
                SettingCheck("游戏页面全屏化", AllSettings.gameFullScreen)
                SettingCheck("使用 SurfaceView 渲染", AllSettings.useSurfaceView)
                SettingCheck("持续性能模式", AllSettings.sustainedPerformance)
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 渲染器（Zalith 的后端能力）
        PclRenderingCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 内存分配
        PclMemoryCard(context)

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- Java 运行环境
        PclJavaCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 高级选项（IsSwapped=True → 默认折叠）
        PclAdvancedCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 跳转版本独立设置
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PclExtraTextButton(
                text = PclStrings.LangPageSetupLaunchGotoVersionSetting,
                logo = PclIcons.arrowRight,
                logoScale = 0.9f,
                onClick = { nav.push(pclInstanceVersionPage()) },
            )
        }
        Spacer(Modifier.height(PclDimens.cardGap))
        PclHint(
            text = "以上设置项均直接读写 Zalith 的真实配置，修改后立即保存并生效。",
            theme = PclHintTheme.Blue,
        )
        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/** 渲染器 / Vulkan 驱动 / 图形 API / 分辨率。 */
@Composable
private fun PclRenderingCard() {
    val rendererOptions = remember { PclSettings.rendererOptions() }
    // 默认渲染器 = `Renderers` 列表第一个，也就是「没选过」时真正生效的那个。
    // 以前这里有一项叫「自动选择」（存空串），名字听着像「按设备挑」，
    // 实际只是回落到列表第一个；真正的按能力挑选是启动时另一处逻辑干的，
    // 会把用户的选择悄悄换掉（26.3 上就是这样挑错、白屏的）。
    // 现在下拉里只列真实渲染器，默认项显示的就是即将生效的那个。
    val defaultRendererId = remember { PclSettings.defaultRendererId() }
    val rendererIds = remember(rendererOptions, defaultRendererId) {
        val ids = rendererOptions.map { it.first }
        when {
            ids.isEmpty() -> listOf(defaultRendererId)
            defaultRendererId.isEmpty() || defaultRendererId in ids -> ids
            else -> listOf(defaultRendererId) + ids
        }
    }
    val rendererNames = rendererOptions.toMap()
    val selectedRendererId = AllSettings.renderer.state.ifEmpty { defaultRendererId }

    val driverOptions = remember {
        runCatching { DriverPluginManager.getDriverList().map { it.id to it.name } }
            .getOrDefault(emptyList())
    }
    val currentDriver = AllSettings.vulkanDriver.state
    val driverIds = (listOf(currentDriver) + driverOptions.map { it.first }).distinct()
    val driverNames = driverOptions.toMap() + (currentDriver to
        (driverOptions.firstOrNull { it.first == currentDriver }?.second ?: currentDriver))

    // 新一代渲染器插件（MobileGlues 这类）会带一组可配置环境变量。
    // 原生界面在「全局渲染器」右侧有枚齿轮，点开就是 `RendererV2ConfigDialog`；
    // PCL 界面之前完全没有这个入口，装了插件也够不着 MOBILEGL_BACKEND_TYPE 之类的开关。
    val currentRendererId = AllSettings.renderer.state
    val rendererEnvUnits = remember(currentRendererId) {
        runCatching {
            Renderers.getRenderers()
                .filterIsInstance<RendererV2Data>()
                .firstOrNull { it.getUniqueIdentifier() == currentRendererId }
                ?.env
                ?.getConfigurableUnits()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
    var showRendererConfig by remember { mutableStateOf(false) }

    PclCard( modifier = Modifier.fillMaxWidth(),title = "渲染器") {
        Column {
            SetupRow("渲染器") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclComboBox(
                        items = rendererIds,
                        selected = selectedRendererId,
                        modifier = Modifier.weight(1f),
                        // PclComboBox 的 label 收到的是可空项（列表里允许出现 null），
                        // 所以这里要先兜住 null 再查名字。
                        label = { id -> id?.let { rendererNames[it] ?: it }.orEmpty() },
                        onSelect = { AllSettings.renderer.save(it) },
                    )
                    // 只有「选中新一代渲染器插件 + 它确实有可配置项」时才出现，和原生一致
                    if (rendererEnvUnits != null) {
                        Spacer(Modifier.width(10.dp))
                        PclIconButton(
                            logo = PclIcons.setupGear,
                            size = 30.dp,
                            logoScale = 0.95f,
                            contentDescription = "渲染器配置",
                            onClick = { showRendererConfig = true },
                        )
                    }
                }
            }
            SettingCombo(
                label = "Vulkan 驱动",
                items = driverIds,
                selected = currentDriver,
                labelOf = { driverNames[it] ?: it },
                onSelect = { AllSettings.vulkanDriver.save(it) },
            )
            // 图形 API 的标签与可选列表统一来自 `uiLabel` / `GRAPHICS_API_CHOICES`：
            // 原来这里手写一套 when、实例设置页又用 displayName 兜一套，
            // 结果就是那个页面里出现两个一模一样的「OpenGL」。
            SettingCombo(
                label = "图形 API",
                items = GRAPHICS_API_CHOICES,
                selected = AllSettings.graphicsApi.state.forUi,
                labelOf = { it.uiLabel },
                onSelect = { AllSettings.graphicsApi.save(it) },
            )
            SettingCombo(
                label = "分辨率规则",
                items = ResolutionRule.entries.toList(),
                selected = AllSettings.resolutionRule.state,
                labelOf = { if (it == ResolutionRule.PERCENTAGE) "按屏幕百分比缩放" else "自定义宽高" },
                onSelect = { AllSettings.resolutionRule.save(it) },
            )
            SetupRow("分辨率（%）") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingSlider(
                        unit = AllSettings.resolutionRatio,
                        min = 25,
                        max = 300,
                        modifier = Modifier.weight(1f),
                    )
                    PclText(
                        text = "${AllSettings.resolutionRatio.state}%",
                        modifier = Modifier.width(56.dp).padding(start = 12.dp),
                    )
                }
            }
            if (AllSettings.resolutionRule.state == ResolutionRule.CUSTOM) {
                SetupRow("自定义宽度") {
                    SettingSlider(AllSettings.customResolutionWidth, 320, 7680, Modifier.fillMaxWidth())
                }
                SetupRow("自定义高度") {
                    SettingSlider(AllSettings.customResolutionHeight, 240, 4320, Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(6.dp))
            SettingCheck("着色器日志输出（调试用）", AllSettings.dumpShaders)
            SettingCheck("Zink 垂直同步", AllSettings.vsyncInZink)
            SettingCheck("使用系统 Vulkan 驱动（Zink）", AllSettings.zinkPreferSystemDriver)
            Spacer(Modifier.height(4.dp))
            PclHint(
                text = "渲染器与驱动列表来自设备上实际安装的插件，未安装插件时仅显示内置 Turnip 驱动。",
                theme = PclHintTheme.Blue,
            )
            PclHint(
                text = "渲染器只按上面选定的那个启动，不会再在启动时自动替换。" +
                        "整合包里装了 Sodium、Iris 这类需要桌面 OpenGL 3.3+ 的模组时，" +
                        "请手动选 Krypton Wrapper（Koopers Zink / 原生 GLES）；" +
                        "选了 GL4ES 系的渲染器会卡在加载界面且不报错。",
                theme = PclHintTheme.Blue,
            )
        }
    }

    // 渲染器配置弹窗（环境变量）。放在卡片外层：`PclDialogContainer` 自带 Dialog 层级，
    // 塞进 `Column` 里也不会被卡片裁剪，但放外层更清楚。
    if (showRendererConfig && rendererEnvUnits != null) {
        PclRendererConfigDialog(
            units = rendererEnvUnits,
            onDismiss = { showRendererConfig = false },
        )
    }
}

/** 内存分配：自动 / 自定义 + 真实内存条。 */
@Composable
private fun PclMemoryCard(context: Context) {
    val p = pclPalette
    val limitMb = remember { PclSettings.memoryLimitMb(context) }
    val setting = AllSettings.ramAllocation
    val ramAuto = setting.state == null
    val ramMb = setting.state ?: (limitMb / 2)
    val fraction = PclSettings.ramToFraction(ramMb, limitMb)
    val (usedMb, totalMb) = remember { memoryStats(context) }

    PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchMem) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PclRadioBox(
                    text = PclStrings.LangPageSetupLaunchLaunchMemAutoAdjust,
                    checked = ramAuto,
                    modifier = Modifier.width(140.dp),
                    onCheckedChange = { if (it) setting.save(null) },
                )
                PclRadioBox(
                    text = PclStrings.LangPageSetupLaunchLaunchMemCustom,
                    checked = !ramAuto,
                    onCheckedChange = { if (it) setting.save(ramMb) },
                )
            }
            PclSlider(
                value = if (ramAuto) 0.5f else fraction,
                enabled = !ramAuto,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                onValueChange = { f -> setting.save(PclSettings.fractionToRam(f, limitMb)) },
            )

            Spacer(Modifier.height(11.dp))
            val usedRatio = if (totalMb > 0) usedMb.toFloat() / totalMb else 0f
            val gameRatio = if (totalMb > 0) ramMb.toFloat() / totalMb else 0f
            PclRamBar(
                usedRatio = usedRatio.coerceIn(0f, 1f),
                gameRatio = gameRatio.coerceIn(0f, 1f - usedRatio.coerceIn(0f, 1f)),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    PclText(
                        text = PclStrings.LangPageSetupLaunchLaunchMemUsedMem,
                        fontSize = 11,
                        color = p.color1.copy(alpha = 0.7f),
                    )
                    PclText(text = "%.1f GB / %.1f GB".format(usedMb / 1024f, totalMb / 1024f), fontSize = 16)
                }
                Column {
                    PclText(
                        text = PclStrings.LangPageSetupLaunchLaunchMemMemForGame,
                        fontSize = 11,
                        color = p.color1.copy(alpha = 0.7f),
                    )
                    PclText(
                        text = if (ramAuto) "自动（约 %.2f GB）".format(ramMb / 1024f)
                        else "%.2f GB".format(ramMb / 1024f),
                        fontSize = 16,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            PclHint(
                text = "本机可为启动器分配的内存上限为 $limitMb MB。设为「自动」时由 Zalith 按设备情况决定。",
                theme = PclHintTheme.Blue,
            )
        }
    }
}

/** Java 运行环境。 */
@Composable
private fun PclJavaCard() {
    var runtimes by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(Unit) {
        runtimes = withContext(Dispatchers.IO) { PclSettings.javaRuntimeOptions() }
    }
    val ids = listOf("") + runtimes.map { it.first }
    val names = mapOf("" to PclStrings.LangPageSetupLaunchLaunchJavaAutoChoice) +
        runtimes.associate { it.first to it.second }

    PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLaunchLaunchJava) {
        Column {
            SettingCheck("自动选择合适的 Java 环境", AllSettings.autoPickJavaRuntime)
            SettingCombo(
                label = "Java 环境",
                items = ids,
                selected = AllSettings.javaRuntime.state,
                labelOf = { names[it] ?: it.ifEmpty { PclStrings.LangPageSetupLaunchLaunchJavaAutoChoice } },
                onSelect = { AllSettings.javaRuntime.save(it) },
            )
            if (runtimes.isEmpty()) {
                Spacer(Modifier.height(4.dp))
                PclHint(
                    text = "尚未在设备上检测到任何 Java 运行库，可在此页之外下载安装后再回到这里选择。",
                    theme = PclHintTheme.Yellow,
                )
            }
        }
    }
}

/** 高级选项：JVM 参数 / 日志。 */
@Composable
private fun PclAdvancedCard() {
    PclCard(modifier = Modifier.fillMaxWidth(),
        title = PclStrings.LangPageSetupLaunchLaunchAdvanceOptions,
        collapsible = true,
        initiallyCollapsed = true,
    ) {
        Column {
            SetupRow(PclStrings.LangPageSetupLaunchLaunchAdvanceOptionsJVMHead) {
                SettingTextBox(AllSettings.jvmArgs, Modifier.fillMaxWidth(), singleLine = false)
            }
            SetupRow("日志字体大小") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingSlider(AllSettings.logTextSize, 5, 20, Modifier.weight(1f))
                    PclText(
                        text = "${AllSettings.logTextSize.state}",
                        modifier = Modifier.width(40.dp).padding(start = 12.dp),
                    )
                }
            }
            SetupRow("日志缓冲刷新间隔（毫秒）") {
                SettingSlider(AllSettings.logBufferFlushInterval, 100, 1000, Modifier.fillMaxWidth())
            }
            SettingCheck("启动游戏时自动展示日志（直到游戏开始渲染）", AllSettings.showLogAutomatic)
            Spacer(Modifier.height(5.dp))
            PclHint(
                text = "JVM 参数会原样追加到游戏启动命令行末尾，请谨慎填写。",
                theme = PclHintTheme.Yellow,
            )
        }
    }
}

/** `PanRamDisplay` —— 已用（ColorBrush3→ColorBrush2 渐变）/ 游戏（ColorBrush3 @0.5）/ 空闲（ColorBrush6 @0.7）。 */
@Composable
private fun PclRamBar(usedRatio: Float, gameRatio: Float) {
    val p = pclPalette
    Row(modifier = Modifier.fillMaxWidth().height(8.dp)) {
        Box(
            modifier = Modifier
                .weight(usedRatio.coerceAtLeast(0.01f))
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(p.color3, p.color2))),
        )
        Box(
            modifier = Modifier
                .weight(gameRatio.coerceAtLeast(0.01f))
                .height(4.dp)
                .background(p.color3.copy(alpha = 0.5f)),
        )
        Box(
            modifier = Modifier
                .weight((1f - usedRatio - gameRatio).coerceAtLeast(0.01f))
                .height(4.dp)
                .background(p.color6.copy(alpha = 0.7f)),
        )
    }
}

/* ============================================================================
 *  PageSetupLink —— 联机（Zalith 的陶瓦联机）
 * ==========================================================================*/

@Composable
fun PclSetupLinkPage() {
    PclContent {
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupLinkConnectionOptions) {
            Column {
                SettingCheck("启用陶瓦联机（Terracotta）", AllSettings.enableTerracotta)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "陶瓦联机可在没有公网 IP 的情况下与好友一起游玩，由 Zalith 内置的联机模块提供。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageSetupLinkAdvancedSettings,
            collapsible = true,
        ) {
            Column {
                SettingCheck("使用自定义 EasyTier 服务器节点", AllSettings.enableTerracottaNodes)
                SetupRow(PclStrings.LangPageSetupLinkCustomPeer) {
                    SettingTextBox(
                        unit = AllSettings.terracottaNodes,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = AllSettings.enableTerracottaNodes.state,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PclHint(
                    text = "多个节点请用英文逗号或分号分隔；留空则使用默认公共节点。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/* ============================================================================
 *  PageSetupUI —— 个性化
 * ==========================================================================*/

/**
 * 「功能隐藏」里的一项：勾上就藏起来（存 [PclSkinSettings]）。
 *
 * 勾/取消都当场给一条提示：勾上之后界面上少的是一整个入口，用户很可能
 * 既不知道「为什么不见了」，也不知道「怎么找回来」—— 提示里把恢复手势一并写清楚。
 */
@Composable
private fun PclSkinHiddenCheck(key: String, label: String) {
    val context = LocalContext.current
    PclCheckBox(
        text = label,
        checked = PclSkinSettings.isHidden(key),
        onCheckedChange = { checked ->
            PclSkinSettings.setHidden(key, checked)
            PclSkinSettings.persist(context)
            if (checked) {
                PclHintHost.push(
                    "「$label」已隐藏。连点右下角 ${PclSkinSettings.PEEK_TAP_COUNT} 次" +
                            "可临时显示被隐藏的入口，" +
                            "去「设置 → 个性化 → 功能隐藏」取消勾选即可恢复。",
                    PclHintTheme.Yellow,
                )
            } else {
                PclHintHost.push("「$label」已恢复显示。", PclHintTheme.Green)
            }
        },
    )
}

/** 一行「标签 + 滑杆 + 数值」，直接读写 Zalith 的整数设置项。 */
@Composable
private fun SettingSliderRow(
    label: String,
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
) {
    SetupRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclSlider(
                value = ((unit.state - min).toFloat() / (max - min)).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                onValueChange = { unit.save(min + ((max - min) * it).roundToInt()) },
            )
            PclText(
                text = "${unit.state}",
                modifier = Modifier.width(48.dp).padding(start = 12.dp),
                fontSize = 12,
                color = pclPalette.gray2,
            )
        }
    }
}

@Composable
fun PclSetupUiPage() {
    val context = LocalContext.current

    // 「清空背景音乐」的二次确认（PCL 原版也是先弹一个警告框再删）。
    var showClearMusic by remember { mutableStateOf(false) }

    // 「选择背景图片」：PCL 是往文件夹里丢图片（`BtnBackgroundOpen` 打开文件夹），
    // Android 上换成系统图片选择器，选完复制进应用目录的 `PCL/Background`。
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importBackground(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclImageLoader.invalidate()
            PclImageLoader.load(PclAppearance.backgroundImage)
            PclAppearance.persist(context)
            PclHintHost.push("背景图片已设置。", PclHintTheme.Green)
        } else {
            PclHintHost.push("背景图片导入失败。", PclHintTheme.Red)
        }
    }

    // 「更改图片」（标题栏）：`UiLogoType = 3`
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importLogoImage(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclImageLoader.invalidate()
            PclImageLoader.load(PclAppearance.logoImage)
            PclAppearance.persist(context)
            PclHintHost.push("标题栏图片已设置。", PclHintTheme.Green)
        } else {
            PclHintHost.push("标题栏图片导入失败。", PclHintTheme.Red)
        }
    }

    // 「导入音乐」：PCL 的「打开文件夹」，Android 上换成可多选的音乐选择器
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        val count = PclAppearance.importMusic(context, uris)
        if (count > 0) {
            PclAppearance.persist(context)
            // 导入 + 开播都在 importMusic 里做完了（正在播时不打断当前这首），
            // 这里只按实际结果给一句说明 —— 免得再出现「提示导入成功、实际一首没进列表」。
            val total = PclAppearance.musicFiles(context).size
            when {
                PclAppearance.musicPlaying ->
                    PclHintHost.push(
                        "已导入 $count 首背景音乐，正在播放「${PclAppearance.musicTitle}」。",
                        PclHintTheme.Green,
                    )
                total > 0 ->
                    PclHintHost.push("已导入 $count 首背景音乐，已开始播放。", PclHintTheme.Green)
                else ->
                    PclHintHost.push(
                        "已导入 $count 个文件，但都不是可识别的音频格式" +
                                "（支持 mp3 / ogg / wav / m4a / aac / flac / opus）。",
                        PclHintTheme.Red,
                    )
            }
        } else {
            PclHintHost.push("音乐导入失败。", PclHintTheme.Red)
        }
    }

    // 「读取本地文件」的主页：选一个 .html 导入到 `PCL/Custom`
    val homePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = PclAppearance.importHomeFile(context, uri, queryDisplayName(context, uri))
        if (ok) {
            PclAppearance.persist(context)
            PclHintHost.push("本地主页已导入。", PclHintTheme.Green)
        } else {
            PclHintHost.push("本地主页导入失败。", PclHintTheme.Red)
        }
    }

    PclContent {
        // ---- 基础（PCL `PageSetupUI.xaml:16` 的 CardLauncher，Title 是 `LangSetTheme` =「基础」）
        PclCard(modifier = Modifier.fillMaxWidth(), title = "基础") {
            Column {
                // ---- 界面缩放
                // PCL 是桌面程序，字号跟着 Windows 的显示缩放走；Android 上没有这个入口，
                // 所以这里补一档：整张界面（含字号）等比放大，字小到看不清笔画时直接调它。
                SetupRow("界面缩放") {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PclRadioBox(
                            text = "自动",
                            checked = PclSkinSettings.uiScale == PclSkinSettings.UI_SCALE_AUTO,
                            onCheckedChange = {
                                PclSkinSettings.applyUiScale(PclSkinSettings.UI_SCALE_AUTO)
                                PclSkinSettings.persist(context)
                            },
                        )
                        PclSkinSettings.UI_SCALE_PRESETS.forEach { percent ->
                            PclRadioBox(
                                text = "$percent%",
                                checked = PclSkinSettings.uiScale == percent,
                                onCheckedChange = {
                                    PclSkinSettings.applyUiScale(percent)
                                    PclSkinSettings.persist(context)
                                },
                            )
                        }
                    }
                }
                PclHint(
                    text = "「界面缩放」是照着 PCL 在 Windows 显示缩放下的行为做的：" +
                            "整个界面（字号、控件、间距）一起等比放大，不会像系统「字体大小」那样" +
                            "只放大文字、把固定高的容器撑破。自动档会按窗口大小挑一档；" +
                            "50% / 75% 是给「窗口本来就小、想把界面压得更紧凑」的情况留的。",
                    theme = PclHintTheme.Blue,
                )
                PclHint(
                    text = "下面几张卡（背景图片 / 背景音乐 / 标题栏 / 主页 / 功能隐藏）都是真的在生效；" +
                            "深色模式、语言、动画这类 Zalith 原生外观设置请到 Zalith 自己的设置里改。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 背景图片（PCL `PageSetupUI.xaml:284` 的 CardBackground）
        // PCL 是让用户往 `PCL/Background` 文件夹里丢图片；Android 没有稳定的「打开该文件夹」
        // 入口，所以改成用系统文件选择器把图片**导入**到应用私有的同名文件夹，
        // 之后的行为与 PCL 一致：每次打开启动器随机取一张，用「自适应方式」铺开。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetBackgroundPic) {
            Column {
                SetupRow(PclStrings.LangSetBackgroundPicAdaptive) {
                    PclComboBox(
                        items = PclBackgroundSuit.entries,
                        selected = PclAppearance.backgroundSuit,
                        modifier = Modifier.fillMaxWidth(),
                        label = { it?.label.orEmpty() },
                        onSelect = {
                            PclAppearance.applyBackgroundSuit(it)
                            PclAppearance.persist(context)
                        },
                    )
                }
                SettingSliderRow(
                    label = PclStrings.LangSetBackgroundPicOpacity,
                    unit = AllSettings.launcherBackgroundOpacity,
                    min = 20, max = 100,
                )
                SettingSliderRow(
                    label = PclStrings.LangSetBackgroundPicBlur,
                    unit = AllSettings.backgroundBlur,
                    min = 0, max = 40,
                )
                Spacer(Modifier.height(6.dp))
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundPicColorfulBack,
                    checked = PclSkinSettings.colorfulBackground,
                    onCheckedChange = {
                        PclSkinSettings.applyColorful(it)
                        PclSkinSettings.persist(context)
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PclButton(
                        text = "选择背景图片",
                        colorState = PclColorState.Highlight,
                        onClick = { backgroundPicker.launch("image/*") },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundPicRefresh,
                        onClick = {
                            PclAppearance.refreshBackground(context)
                            PclAppearance.persist(context)
                            PclImageLoader.invalidate()
                        },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundPicEmpty,
                        colorState = PclColorState.Red,
                        onClick = {
                            PclAppearance.clearBackground()
                            PclAppearance.persist(context)
                            PclImageLoader.invalidate()
                        },
                    )
                }
                PclHint(
                    text = "「不透明度」与「背景模糊」用的是 Zalith 的同名设置项（`launcherBackgroundOpacity` / " +
                            "`backgroundBlur`），两边改的是同一个值。" +
                            "背景模糊是启动器自己实现的高斯模糊（分层卷积，CPU 预先算好再交给 GPU 放大），" +
                            "所以不依赖 Android 12 的实时模糊，各版本表现一致。" +
                            "「填充文件夹」这套 PCL 的做法在 Android 上换成了「选择背景图片」：" +
                            "图片会被复制进应用目录的 `PCL/Background` 里，之后「刷新背景图片」会随机抽一张。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 背景音乐（PCL `PageSetupUI.xaml:404` 的 CardMusic）
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetBackgroundMusicTitle) {
            Column {
                SetupRow(PclStrings.LangSetBackgroundMusicVolume) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PclSlider(
                            value = PclAppearance.musicVolume / 100f,
                            modifier = Modifier.weight(1f),
                            onValueChange = {
                                PclAppearance.applyMusicVolume((it * 100).roundToInt())
                                PclAppearance.persist(context)
                            },
                        )
                        PclText(
                            text = "${PclAppearance.musicVolume}",
                            modifier = Modifier.width(48.dp).padding(start = 12.dp),
                            fontSize = 12,
                            color = pclPalette.gray2,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayRandom,
                    checked = PclAppearance.musicRandom,
                    onCheckedChange = {
                        PclAppearance.applyMusicRandom(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayOnStart,
                    checked = PclAppearance.musicAuto,
                    onCheckedChange = {
                        PclAppearance.applyMusicAuto(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayDuringGaming,
                    checked = PclAppearance.musicStart,
                    onCheckedChange = {
                        PclAppearance.applyMusicStart(it)
                        PclAppearance.persist(context)
                    },
                )
                PclCheckBox(
                    text = PclStrings.LangSetBackgroundMusicPlayDuringLaunching,
                    checked = PclAppearance.musicStop,
                    onCheckedChange = {
                        PclAppearance.applyMusicStop(it)
                        PclAppearance.persist(context)
                    },
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PclButton(
                        text = "导入音乐",
                        colorState = PclColorState.Highlight,
                        onClick = { musicPicker.launch("audio/*") },
                    )
                    PclButton(
                        text = if (PclAppearance.musicPlaying) "暂停播放" else "开始播放",
                        onClick = { PclAppearance.toggleMusic(context) },
                    )
                    PclButton(text = "下一首") { PclAppearance.next(context, auto = true) }
                    PclButton(
                        text = PclStrings.LangSetBackgroundMusicRefresh,
                        onClick = {
                            PclAppearance.refreshMusic(context)
                            PclAppearance.persist(context)
                        },
                    )
                    PclButton(
                        text = PclStrings.LangSetBackgroundMusicEmpty,
                        colorState = PclColorState.Red,
                        onClick = { showClearMusic = true },
                    )
                }
                PclHint(
                    text = "同背景图片：PCL 的做法是往 `PCL/Music` 文件夹里丢音乐，Android 上换成" +
                            "「导入音乐」（可多选），导入后行为一致 —— 随机播放、音量、自动播放都可用。" +
                            "当前状态：" + if (PclAppearance.musicPlaying) {
                        "正在播放「${PclAppearance.musicTitle}」"
                    } else if (PclAppearance.musicTitle.isNotBlank()) {
                        "已暂停「${PclAppearance.musicTitle}」"
                    } else {
                        "未在播放"
                    },
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 标题栏（PCL `PageSetupUI.xaml:484` 的 CardLogo）
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetTitle) {
            Column {
                SetupRow(PclStrings.LangSetThemeTitle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        listOf(
                            PclLogoType.None to PclStrings.LangSetNone,
                            PclLogoType.Default to PclStrings.LangSetDefault,
                            PclLogoType.Text to PclStrings.LangSetText,
                        ).forEach { (type, label) ->
                            PclRadioBox(
                                text = label,
                                checked = PclSkinSettings.logoType == type,
                                onCheckedChange = {
                                    PclSkinSettings.applyLogoType(type)
                                    PclSkinSettings.persist(context)
                                },
                            )
                        }
                        PclRadioBox(
                            text = PclStrings.LangSetPicture,
                            checked = PclSkinSettings.logoType == PclLogoType.Picture,
                            onCheckedChange = {
                                PclSkinSettings.applyLogoType(PclLogoType.Picture)
                                PclSkinSettings.persist(context)
                            },
                        )
                    }
                }
                if (PclSkinSettings.logoType == PclLogoType.Text) {
                    SetupRow(PclStrings.LangSetTitleContent) {
                        PclTextBox(
                            value = PclSkinSettings.logoText,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "PCL",
                            onValueChange = {
                                PclSkinSettings.applyLogoText(it)
                                PclSkinSettings.persist(context)
                            },
                        )
                    }
                }
                SetupRow("") {
                    PclCheckBox(
                        text = PclStrings.LangSetTitleLeft,
                        checked = PclSkinSettings.logoLeft,
                        onCheckedChange = {
                            PclSkinSettings.applyLogoLeft(it)
                            PclSkinSettings.persist(context)
                        },
                    )
                }
                // PanLogoChange：`UiLogoType = 3` 时出现的「更改图片 / 清空图片」
                if (PclSkinSettings.logoType == PclLogoType.Picture) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(15.dp),
                    ) {
                        PclButton(
                            text = PclStrings.LangSetTitlePicChange,
                            colorState = PclColorState.Highlight,
                            onClick = { logoPicker.launch("image/*") },
                        )
                        PclButton(
                            text = PclStrings.LangSetTitlePicEmpty,
                            colorState = PclColorState.Red,
                            onClick = {
                                PclAppearance.clearLogoImage()
                                PclAppearance.persist(context)
                                PclImageLoader.invalidate()
                            },
                        )
                    }
                }
                PclHint(
                    text = "「无 / 默认 / 文本」直接生效；「图片」会用到下面的「更改图片」选进来的那张图" +
                            "（没选图时先退回 PCL 的描边 Logo）。PCL 的「标题栏居左」在手机上取决于屏幕宽度，" +
                            "所以这里只在标题栏有位置时才看得出来。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 主页（PCL `PageSetupUI.xaml:569` 的 CardCustom）
        // PCL 的「主页」= 用自定义内容替换启动页右侧整块（左边账户 / 版本面板照旧）。
        // 这里支持「联网更新」（输入网址）与「读取本地文件」（导入一个 html）；
        // 「预设」要拉 PCL 官方的主页市场列表，本移植版没有那份数据，所以先置灰。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetHomePageTitle) {
            Column {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageEmpty,
                        checked = PclAppearance.homeType == PclHomeType.EMPTY,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.EMPTY)
                            PclAppearance.persist(context)
                        },
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePagePreset,
                        checked = false,
                        enabled = false,
                        onCheckedChange = {},
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageLocal,
                        checked = PclAppearance.homeType == PclHomeType.LOCAL,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.LOCAL)
                            PclAppearance.persist(context)
                        },
                    )
                    PclRadioBox(
                        text = PclStrings.LangSetHomePageOnline,
                        checked = PclAppearance.homeType == PclHomeType.ONLINE,
                        onCheckedChange = {
                            PclAppearance.applyHomeType(PclHomeType.ONLINE)
                            PclAppearance.persist(context)
                        },
                    )
                }
                if (PclAppearance.homeType == PclHomeType.ONLINE) {
                    Spacer(Modifier.height(10.dp))
                    SetupRow(PclStrings.LangSetHomePageDownload) {
                        PclTextBox(
                            value = PclAppearance.homeNet,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "https://",
                            onValueChange = {
                                PclAppearance.applyHomeNet(it)
                                PclAppearance.persist(context)
                            },
                        )
                    }
                }
                if (PclAppearance.homeType == PclHomeType.LOCAL) {
                    Spacer(Modifier.height(10.dp))
                    PclButton(
                        text = "选择本地主页文件",
                        colorState = PclColorState.Highlight,
                        onClick = { homePicker.launch("*/*") },
                    )
                    if (PclAppearance.homeLocalFile != null) {
                        Spacer(Modifier.height(6.dp))
                        PclText(
                            text = "当前：${PclAppearance.homeLocalFile?.substringAfterLast('/')}",
                            color = pclPalette.gray2,
                            fontSize = 12,
                        )
                    }
                }
                if (PclAppearance.homeUrl != null) {
                    PclHint(
                        text = PclStrings.LangSetHomePageCustomWarn,
                        theme = PclHintTheme.Yellow,
                    )
                }
                PclHint(
                    text = "设定后，「主页」版块的右侧会换成这个自定义主页（左边的账户与版本面板不变），" +
                            "顶部有一条「刷新主页」。「预设」要拉 PCL 官方的主页市场列表，" +
                            "本移植版没有那份数据，所以先置灰。",
                    theme = PclHintTheme.Blue,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 功能隐藏（PCL `PageSetupUI.xaml:723` 的 CardSwitch）
        //
        // 勾上之后对应入口会从界面上消失，而取消勾选的开关就在这里 —— 于是右下角
        // 留了个「连点 10 次」的手势，把这些入口**临时**放出来（见 `PclHiddenPeekGesture`）。
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangSetFuncHidden) {
            Column {
                PclSkinHiddenCheck(key = PclHiddenKeys.DOWNLOAD, label = PclStrings.LangSetFuncHiddenDownload)
                PclSkinHiddenCheck(key = PclHiddenKeys.LINK, label = PclStrings.LangSetFuncHiddenLink)
                PclSkinHiddenCheck(key = PclHiddenKeys.SETUP, label = PclStrings.LangSetFuncHiddenSettings)
                PclSkinHiddenCheck(key = PclHiddenKeys.OTHER, label = PclStrings.LangSetFuncHiddenMore)
                Spacer(Modifier.height(6.dp))
                PclText(
                    text = PclStrings.LangSetFuncHiddenSettingsSubPage,
                    color = pclPalette.gray2,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_GAME, PclStrings.LangSetFuncHiddenSettingsGame)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_LINK, PclStrings.LangSetFuncHiddenSettingsLink)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_UI, PclStrings.LangSetFuncHiddenSettingsPersonalize)
                PclSkinHiddenCheck(PclHiddenKeys.SETUP_SYSTEM, PclStrings.LangSetFuncHiddenSettingsLaunch)
                Spacer(Modifier.height(6.dp))
                PclText(
                    text = PclStrings.LangSetFuncHiddenMoreSubPage,
                    color = pclPalette.gray2,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_HELP, PclStrings.LangSetFuncHiddenMoreHelp)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_ABOUT, PclStrings.LangSetFuncHiddenMoreAbout)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_TOOLS, PclStrings.LangSetFuncHiddenMoreTool)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_FEEDBACK, PclStrings.LangSetFuncHiddenMoreFeedback)
                PclSkinHiddenCheck(PclHiddenKeys.OTHER_VOTE, PclStrings.LangSetFuncHiddenMoreVote)
                PclHint(
                    text = "勾上就把对应入口藏起来（顶部导航的那一排、以及设置/更多的子页面）。" +
                            "「主页面」不允许隐藏，否则就没地方可去了。",
                    theme = PclHintTheme.Blue,
                )
                PclHint(
                    text = "勾掉之后入口就从界面上消失了。想临时找回来：在右下角连点 " +
                            "${PclSkinSettings.PEEK_TAP_COUNT} 次，被隐藏的入口会全部重新出现 " +
                            "${PclSkinSettings.PEEK_DURATION_MS / 1000} 秒，趁这段时间回来取消勾选即可；" +
                            "勾选状态不会因为这次临时显示被改动。",
                    theme = PclHintTheme.Yellow,
                )
            }
        }
        PclBottomSpacer()
    }

    // 清空背景音乐：真的把 `PCL/Music` 里的音频文件删掉（PCL 是删整个 Musics 目录）。
    if (showClearMusic) {
        PclConfirmDialog(
            title = "清空背景音乐",
            content = "确定要删除所有背景音乐吗？这些文件会从背景音乐文件夹里一并删掉，无法恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                val deleted = PclAppearance.clearMusic(context)
                PclAppearance.persist(context)
                PclHintHost.push(
                    if (deleted > 0) {
                        "已删除 $deleted 首背景音乐。"
                    } else {
                        "背景音乐文件夹里没有可删除的文件。"
                    },
                    if (deleted > 0) PclHintTheme.Green else PclHintTheme.Yellow,
                )
            },
            onDismiss = { showClearMusic = false },
        )
    }
}

/* ============================================================================
 *  PageSetupSystem —— 其他（启动器）
 * ==========================================================================*/

@Composable
fun PclSetupSystemPage() {
    val context = LocalContext.current

    PclContent {
        // ---- 界面风格（PCL2 / 原生）
        PclCard( modifier = Modifier.fillMaxWidth(),title = "界面风格") {
            Column {
                SettingCheck("使用 PCL2 风格界面作为启动器主界面", AllSettings.usePcl2Ui)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "关闭后重启启动器，主界面会切换回 Zalith 原生界面；" +
                            "之后可以在原生界面的「设置 → 启动器 → 切换到 PCL2 界面」随时回到这里。" +
                            "（桌面只保留一个图标，按这个开关决定进哪个界面。）",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 下载源
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupSystemDownload) {
            Column {
                SettingCombo(
                    label = "游戏内容下载源",
                    items = MirrorSourceType.entries.toList(),
                    selected = AllSettings.gameDownloadSource.state,
                    labelOf = { context.getString(it.textRes) },
                    onSelect = { AllSettings.gameDownloadSource.save(it) },
                )
                SettingCombo(
                    label = "资源平台下载源",
                    items = MirrorSourceType.entries.toList(),
                    selected = AllSettings.assetPlatformSource.state,
                    labelOf = { context.getString(it.textRes) },
                    onSelect = { AllSettings.assetPlatformSource.save(it) },
                )
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "\u201c自动\u201d会按当前网络环境选择初始下载源，并在失败时自适应换源。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- Mod 与资源搜索平台
        PclCard( modifier = Modifier.fillMaxWidth(),title = PclStrings.LangPageSetupSystemDownloadModTitle) {
            Column {
                SettingCombo(
                    label = "搜索 Mod 的平台",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchModPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchModPlatform.save(it) },
                )
                SettingCombo(
                    label = "搜索整合包的平台",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchModpackPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchModpackPlatform.save(it) },
                )
                SettingCombo(
                    label = "搜索资源包的平台",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchResourcePackPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchResourcePackPlatform.save(it) },
                )
                SettingCombo(
                    label = "搜索光影的平台",
                    items = Platform.entries.toList(),
                    selected = AllSettings.searchShadersPlatform.state,
                    labelOf = { it.displayName },
                    onSelect = { AllSettings.searchShadersPlatform.save(it) },
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 游戏内悬浮窗
        PclCard( modifier = Modifier.fillMaxWidth(),title = "游戏内悬浮窗") {
            Column {
                SettingCheck("显示实时帧率（FPS）", AllSettings.showFPS)
                SettingCheck("显示内存占用", AllSettings.showMemory)
                SettingCheck("显示菜单悬浮球", AllSettings.showMenuBall)
                SetupRow("悬浮球不透明度（%）") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SettingSlider(AllSettings.menuBallOpacity, 20, 100, Modifier.weight(1f))
                        PclText(
                            text = "${AllSettings.menuBallOpacity.state}%",
                            modifier = Modifier.width(52.dp).padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 启动器信息（真实数据）
        PclLauncherInfoCard()

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 调试选项（IsSwapped=True → 默认折叠）
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageSetupSystemDebug,
            collapsible = true,
            initiallyCollapsed = true,
        ) {
            Column {
                SettingCombo(
                    label = "主界面操作菜单位置",
                    items = ActionMenuSide.entries.toList(),
                    selected = AllSettings.launcherActionMenuSide.state,
                    labelOf = { if (it == ActionMenuSide.START) "阅读方向起始侧" else "阅读方向末尾侧" },
                    onSelect = { AllSettings.launcherActionMenuSide.save(it) },
                )
                SettingCheck("打开启动器时自动展开任务菜单", AllSettings.launcherTaskMenuExpanded)
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "已禁用的原生库插件：${AllSettings.disableNativeLibPlugins.state.size} 个。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/** 启动器 / 设备真实信息。 */
@Composable
private fun PclLauncherInfoCard() {
    val context = LocalContext.current
    val p = pclPalette
    val runtime = LocalPclRuntime.current
    val versions by runtime.versions.collectAsStateWithLifecycle()
    val accounts by runtime.accounts.collectAsStateWithLifecycle()
    val current by runtime.currentVersion.collectAsStateWithLifecycle()
    val (usedMb, totalMb) = remember { memoryStats(context) }

    val info = listOf(
        "已安装版本" to "${versions.size} 个",
        "已登录账户" to "${accounts.size} 个",
        "当前版本" to (current?.getVersionName() ?: "未选择"),
        "设备内存" to "%.1f GB / %.1f GB".format(usedMb / 1024f, totalMb / 1024f),
        "设备架构" to (System.getProperty("os.arch") ?: "未知"),
    )

    PclCard( modifier = Modifier.fillMaxWidth(),title = "启动器信息") {
        Column {
            info.forEach { (label, value) ->
                SetupRow(label) {
                    PclText(text = value, fontSize = 13, color = p.color1)
                }
            }
            Spacer(Modifier.height(8.dp))
            PclHint(
                text = "以上信息读取自当前设备与已安装的游戏版本。",
                theme = PclHintTheme.Blue,
            )
        }
    }
}
