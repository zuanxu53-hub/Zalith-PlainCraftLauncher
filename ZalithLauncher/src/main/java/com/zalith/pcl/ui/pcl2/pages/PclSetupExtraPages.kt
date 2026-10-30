/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 设置页里 Zalith 原生独有的四张子页面。PCL2 自己的设置页只有
 * 启动 / 联机 / 个性化 / 其他（`Pages/PageSetup/PageSetup{Launch,Link,UI,System}.xaml`），
 * 这四页是原生启动器才有的能力，所以在移植版里用 **PCL 的卡片与列表项** 重做：
 *
 *   控制设置  ← 原生 `ui/screens/content/settings/ControlSettingsScreen.kt`（22 项）
 *   手柄设置  ← 原生 `ui/screens/content/settings/GamepadSettingsScreen.kt`（7 项）
 *   Java 管理 ← 原生 `ui/screens/content/settings/JavaManageScreen.kt`
 *               列表版式取自 PCL2 `PageSetupLaunch.UpdateJavaList()`
 *               ——「一行一个 Java，行尾是打开目录 / 删除两枚小按钮」
 *   控制管理  ← 原生 `ui/screens/content/settings/ControlManageScreen.kt`
 *
 * 功能、数据、落盘全部复用同一套后端，没有只改本地状态、不落盘的“空壳”控件：
 *   AllSettings（全部设置项）、RuntimesManager（Java 运行库）、ControlManager（控制布局）、
 *   GamepadViewModel + remapperMMKV（手柄映射配置）、
 *   EventViewModel（实体键盘按键捕获、控制布局导入与「打开目录」的事件通道，
 *   与原生界面走的是**同一条** Activity 级事件通道）。
 *
 * 与原生界面的差别只在版式：原生是 Material3 的 `SettingsCard`（标题 + 摘要 + 控件），
 * 这里是 PCL 的 `PclCard` + `PclCheckBox` / `PclComboBox` / `PclSlider` / `PclListItem`。
 * 原生每一项的「标题 + 摘要」在这里保留成「PCL 控件 + 其下方的灰色小字」。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.bridge.CursorShape
import com.zalith.pcl.contract.MediaPickerContract
import com.zalith.pcl.context.copyLocalFile
import com.zalith.pcl.context.getFileName
import com.zalith.pcl.game.control.ControlData
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.control.openDocumentStream
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.game.multirt.Runtime
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.setting.enums.GamepadInputMode
import com.zalith.pcl.setting.enums.GestureActionType
import com.zalith.pcl.setting.enums.MouseControlMode
import com.zalith.pcl.setting.unit.AbstractSettingUnit
import com.zalith.pcl.ui.activities.startEditorActivity
import com.zalith.pcl.ui.control.GamepadBindingKeyboard
import com.zalith.pcl.ui.control.gamepad.GamepadMap
import com.zalith.pcl.ui.control.gamepad.JoystickMode
import com.zalith.pcl.ui.control.gamepad.getNameByGamepadEvent
import com.zalith.pcl.ui.control.gamepad.remapperMMKV
import com.zalith.pcl.ui.control.gyroscope.isGyroscopeAvailable
import com.zalith.pcl.ui.control.mouse.MouseHotspotEditorDialog
import com.zalith.pcl.ui.control.mouse.MousePointer
import com.zalith.pcl.ui.control.mouse.getMouseFile
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclInputDialog
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclSlider
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.file.checkExtensionOrThrow
import com.zalith.pcl.utils.image.isImageFile
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.viewmodel.EventViewModel
import com.zalith.pcl.viewmodel.GamepadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/* ============================================================================
 *  通用小部件（与 PclSetupPages.kt 里那套同形；那边是 private，这边另起一份）
 * ==========================================================================*/

/** 设置项标签列宽度，与 `PclSetupPages.LabelWidth` 一致。 */
private val ExtraLabelWidth = 140.dp

/**
 * 控制布局「最近一次导入」的记录。
 *
 * 放文件级单例而不是 `remember`：`nav.push()` 进二级页会让当前 composable 整个离开组合，
 * 回来时 `remember` 全部作废 —— 而用户恰恰是「导入完切走、再切回来看结果」。
 *
 * 这个字段只为一件事存在：**导入没反应时，屏幕上得有东西说明卡在哪一步**
 * （没选到文件 / 读不到流 / 解析失败 / 写盘失败）。否则「列表空白」根本无从定位。
 */
private object PclControlImportLog {
    val text = mutableStateOf<String?>(null)
    fun set(value: String) { text.value = value }
}

/** 控制布局相关操作的日志标签（导入失败原因要能在「首页 → 日志」里看到）。 */
private const val LOG_TAG = "PclSetupExtraPages"

/** 摘要小字用的灰色（PCL 的 `ColorBrushGray3`）。`pclPalette` 本身是 @Composable，所以这里也用取值器。 */
private val summaryColor: Color
    @Composable get() = pclPalette.gray3

/** 设置项一行：`标签（固定宽）…… 控件`。 */
@Composable
private fun ExtraRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(text = label, modifier = Modifier.width(ExtraLabelWidth).padding(end = 20.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** 摘要小字，缩进与控件列对齐。 */
@Composable
private fun ExtraSummary(text: String) {
    PclText(
        text = text,
        modifier = Modifier.padding(start = ExtraLabelWidth + 20.dp, top = 2.dp, bottom = 6.dp),
        color = summaryColor,
        fontSize = 12,
    )
}

/** 复选项 + 灰色说明（原生每一项的 title / summary 对）。 */
@Composable
private fun ExtraCheck(
    text: String,
    unit: AbstractSettingUnit<Boolean>,
    summary: String = "",
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        PclCheckBox(
            text = text,
            checked = unit.state,
            enabled = enabled,
            onCheckedChange = { unit.save(it) },
        )
        if (summary.isNotEmpty()) {
            PclText(
                text = summary,
                modifier = Modifier.padding(top = 2.dp),
                color = summaryColor,
                fontSize = 12,
            )
        }
    }
}

/** 下拉框（标签在左、控件在右，选中即落盘）。 */
@Composable
private fun <T> ExtraCombo(
    label: String,
    items: List<T>,
    selected: T?,
    labelOf: (T) -> String = { it.toString() },
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    ExtraRow(label) {
        PclComboBox(
            items = items,
            selected = selected,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { item -> item?.let(labelOf).orEmpty() },
            onSelect = onSelect,
        )
    }
}

/** 整数滑杆：控件用 0..1 比例，这里换算成实际区间，右侧显示当前值。 */
@Composable
private fun ExtraSlider(
    label: String,
    unit: AbstractSettingUnit<Int>,
    min: Int,
    max: Int,
    suffix: String = "",
    enabled: Boolean = true,
) {
    if (max <= min) return
    val value = unit.state.coerceIn(min, max)
    ExtraRow(label) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclSlider(
                value = ((value - min).toFloat() / (max - min)).coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                enabled = enabled,
                onValueChange = { fraction ->
                    unit.save((min + fraction * (max - min)).roundToInt().coerceIn(min, max))
                },
            )
            PclText(
                text = "$value$suffix",
                modifier = Modifier.width(62.dp).padding(start = 12.dp),
                color = if (enabled) null else summaryColor,
            )
        }
    }
}

/* ============================================================================
 *  控制设置 —— 原生 ControlSettingsScreen
 * ==========================================================================*/

/** 控制设置里 8 种虚拟鼠标指针（顺序与原生一致）。 */
private val pointerShapes = listOf(
    CursorShape.Arrow to "虚拟鼠标指针（默认）",
    CursorShape.Hand to "虚拟鼠标指针（手形）",
    CursorShape.IBeam to "虚拟鼠标指针（输入光标）",
    CursorShape.CrossHair to "虚拟鼠标指针（十字）",
    CursorShape.ResizeNS to "虚拟鼠标指针（上下）",
    CursorShape.ResizeEW to "虚拟鼠标指针（左右）",
    CursorShape.ResizeAll to "虚拟鼠标指针（移动）",
    CursorShape.NotAllowed to "虚拟鼠标指针（禁止）",
)

/**
 * 指针行内的三种操作，是原生 `MousePointerOperation` 的子集。
 *
 * ⚠️ 这里**刻意没有** `Reset` 中间态：本移植的 `PclConfirmDialog` 在点「确定」后
 * 会先调 `onConfirm`、紧接着再调 `onDismiss`，两次状态写入落在同一帧 ——
 * 中间态永远不会被组合到，`when` 里对应的分支一次也不会执行。
 * 重置的活直接在 `onConfirm` 里做。
 */
private sealed interface PointerOp {
    data object None : PointerOp
    data object PreReset : PointerOp
    data object Hotspot : PointerOp
}

@Composable
fun PclSetupControlPage() {
    val context = LocalContext.current
    val eventViewModel: EventViewModel = viewModel()
    val gyroscopeReady = remember { isGyroscopeAvailable(context) }

    // ---- 实体键盘「呼出输入法」的按键捕获。
    // 走 Activity 级事件通道：MainActivity 收到 StartKeyCapture 后把硬件按键
    // 通过 OnKeyDown 发回来 —— 与原生 `PhysicalKeyImeTrigger` 完全同一条路径。
    var capturing by remember { mutableStateOf(false) }
    if (capturing) {
        LaunchedEffect(Unit) {
            eventViewModel.sendEvent(EventViewModel.Event.Key.StartKeyCapture)
            eventViewModel.events
                .filterIsInstance<EventViewModel.Event.Key.OnKeyDown>()
                .collect { event ->
                    capturing = false
                    AllSettings.physicalKeyImeCode.save(event.key.keyCode)
                }
        }
        LaunchedEffect(Unit) {
            // 兜底：15 s 内没按下任何键就自动退出捕获，别让「正在捕获」永远挂着。
            delay(15_000)
            if (capturing) capturing = false
        }
        DisposableEffect(Unit) {
            onDispose { eventViewModel.sendEvent(EventViewModel.Event.Key.StopKeyCapture) }
        }
    }

    PclContent {
        // ---- 实体鼠标
        PclCard(modifier = Modifier.fillMaxWidth(), title = "实体鼠标") {
            Column {
                ExtraCheck(
                    text = "实体鼠标控制",
                    unit = AllSettings.physicalMouseMode,
                    summary = "连接实体鼠标时，是否禁用虚拟鼠标指针，直接使用系统鼠标指针进行操控。",
                )
                Spacer(Modifier.height(8.dp))
                ExtraRow("呼出输入法按键") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val code = AllSettings.physicalKeyImeCode.state
                        PclButton(
                            text = when {
                                capturing -> "请按下要绑定的按键…"
                                code == null -> "点击绑定按键"
                                else -> "已绑定按键：${keyCodeName(code)}"
                            },
                            colorState = if (capturing) PclColorState.Highlight else PclColorState.Normal,
                            onClick = { capturing = !capturing },
                        )
                        if (code != null && !capturing) {
                            Spacer(Modifier.width(8.dp))
                            PclButton(
                                text = "清除",
                                colorState = PclColorState.Red,
                                onClick = { AllSettings.physicalKeyImeCode.save(null) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "绑定后，游戏内按下该按键即可呼出输入法。" +
                            "若设备把实体鼠标右键识别为“返回”、中键识别为“回到桌面”，" +
                            "可进入系统设置 > 开发者选项开启“禁用权限监控”，大概率能解决。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 虚拟鼠标指针
        PclCard(modifier = Modifier.fillMaxWidth(), title = "虚拟鼠标指针") {
            Column {
                ExtraSlider(
                    label = "虚拟鼠标大小（Dp）",
                    unit = AllSettings.mouseSize,
                    min = 5,
                    max = 50,
                )
                Spacer(Modifier.height(4.dp))
                PclHint(
                    text = "点击任意一项可以把它替换成自己的图片；「设置热点」用来指定图片上" +
                            "真正代表指针位置的那一点；「重置」恢复成启动器自带的图片。",
                    theme = PclHintTheme.Blue,
                )
                Spacer(Modifier.height(8.dp))
                pointerShapes.forEach { (shape, title) ->
                    PclPointerRow(title = title, shape = shape)
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 虚拟鼠标
        PclCard(modifier = Modifier.fillMaxWidth(), title = "虚拟鼠标") {
            Column {
                ExtraCheck(
                    text = "隐藏虚拟鼠标",
                    unit = AllSettings.hideMouse,
                    summary = "一些场景下您可能希望隐藏虚拟鼠标；该设置不会影响实体鼠标控制、滑动控制。",
                )
                ExtraCheck(
                    text = "触控板式操作",
                    unit = AllSettings.enableMouseClick,
                    summary = "点击屏幕视为鼠标左键，长按视为长按左键操作；" +
                            "该设置不会影响实体鼠标控制、点击控制。",
                )
                Spacer(Modifier.height(6.dp))
                ExtraCombo(
                    label = "虚拟鼠标控制模式",
                    items = MouseControlMode.entries.toList(),
                    selected = AllSettings.mouseControlMode.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.mouseControlMode.save(it) },
                )
                ExtraSummary("选择虚拟鼠标的操作方式：滑动屏幕移动光标，或点击屏幕指定位置。")
                ExtraSlider(
                    label = "虚拟鼠标灵敏度",
                    unit = AllSettings.cursorSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "抓获鼠标滑动灵敏度",
                    unit = AllSettings.mouseCaptureSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "虚拟鼠标长按触发的延迟",
                    unit = AllSettings.mouseLongPressDelay,
                    min = 100,
                    max = 1000,
                    suffix = " ms",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 手势
        PclCard(modifier = Modifier.fillMaxWidth(), title = "手势") {
            Column {
                ExtraCheck(
                    text = "手势控制",
                    unit = AllSettings.gestureControl,
                    summary = "支持通过点击、长按以及双指滑动滚动等手势在游戏中进行交互，类似基岩版。",
                )
                Spacer(Modifier.height(6.dp))
                ExtraCombo(
                    label = "点击触发的操作类型",
                    items = GestureActionType.entries.toList(),
                    selected = AllSettings.gestureTapMouseAction.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.gestureTapMouseAction.save(it) },
                )
                ExtraSummary("选择在轻触屏幕时应触发的鼠标按键操作。")
                ExtraCombo(
                    label = "长按触发的操作类型",
                    items = GestureActionType.entries.toList(),
                    selected = AllSettings.gestureLongPressMouseAction.state,
                    labelOf = { context.getString(it.nameRes) },
                    onSelect = { AllSettings.gestureLongPressMouseAction.save(it) },
                )
                ExtraSummary("选择在长按屏幕时应触发的鼠标按键操作。")
                ExtraSlider(
                    label = "手势长按触发的延迟",
                    unit = AllSettings.gestureLongPressDelay,
                    min = 100,
                    max = 1000,
                    suffix = " ms",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 陀螺仪
        PclCard(modifier = Modifier.fillMaxWidth(), title = "陀螺仪") {
            Column {
                ExtraCheck(
                    text = "陀螺仪控制",
                    unit = AllSettings.gyroscopeControl,
                    summary = "在游戏中通过转动设备来控制视角。",
                    enabled = gyroscopeReady,
                )
                if (!gyroscopeReady) {
                    Spacer(Modifier.height(6.dp))
                    PclHint(text = "未在当前设备上检测到陀螺仪，本项设置不会生效。", theme = PclHintTheme.Red)
                }
                val on = gyroscopeReady && AllSettings.gyroscopeControl.state
                Spacer(Modifier.height(6.dp))
                ExtraSlider(
                    label = "陀螺仪控制灵敏度",
                    unit = AllSettings.gyroscopeSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                    enabled = on,
                )
                ExtraSlider(
                    label = "陀螺仪采样率",
                    unit = AllSettings.gyroscopeSampleRate,
                    min = 5,
                    max = 50,
                    enabled = on,
                )
                ExtraCheck(
                    text = "陀螺仪数值平滑",
                    unit = AllSettings.gyroscopeSmoothing,
                    summary = "平滑陀螺仪监测到的数值，减少抖动，但会增加延迟。",
                    enabled = on,
                )
                ExtraSlider(
                    label = "平滑处理窗口大小",
                    unit = AllSettings.gyroscopeSmoothingWindow,
                    min = 2,
                    max = 10,
                    enabled = on && AllSettings.gyroscopeSmoothing.state,
                )
                ExtraCheck(
                    text = "反转 X 轴",
                    unit = AllSettings.gyroscopeInvertX,
                    summary = "启用后，左右转动设备会反向控制视角。",
                    enabled = on,
                )
                ExtraCheck(
                    text = "反转 Y 轴",
                    unit = AllSettings.gyroscopeInvertY,
                    summary = "启用后，上下转动设备会反向控制视角。",
                    enabled = on,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }
}

/** 一枚虚拟鼠标指针：预览图 + 更换图片 / 设置热点 / 重置。 */
@Composable
private fun PclPointerRow(title: String, shape: CursorShape) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = getMouseFile(shape)

    var refresh by remember { mutableIntStateOf(0) }
    var exists by remember { mutableStateOf(false) }
    LaunchedEffect(file, refresh) {
        exists = withContext(Dispatchers.IO) { file.exists() && file.length() > 0L }
    }

    var op by remember { mutableStateOf<PointerOp>(PointerOp.None) }

    val picker = rememberLauncherForActivityResult(
        contract = MediaPickerContract(allowImages = true, allowVideos = false),
    ) { result ->
        val uri = result?.firstOrNull() ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { context.copyLocalFile(uri, file) }
                if (!withContext(Dispatchers.IO) { file.isImageFile() }) {
                    error("所选文件不是可识别的图片。")
                }
            }.onSuccess {
                refresh++
            }.onFailure { th ->
                withContext(Dispatchers.IO) { file.delete() }
                PclHintHost.push("替换指针图片失败：${th.message ?: "未知错误"}", PclHintTheme.Red)
            }
        }
    }

    PclListItem(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        info = if (exists) "已使用自定义图片" else "启动器自带图片",
        logoContent = {
            Box(modifier = Modifier.align(Alignment.Center)) {
                MousePointer(
                    mouseSize = 22.dp,
                    cursorShape = shape,
                    mouseFile = file,
                    centerIcon = true,
                    triggerRefresh = refresh,
                )
            }
        },
        type = PclListItemType.Clickable,
        height = 48.dp,
        buttons = buildList {
            add(
                PclListItemButton(
                    logo = PclIcons.replaceImage,
                    tooltip = "更换图片",
                    onClick = { picker.launch(Unit) },
                )
            )
            add(
                PclListItemButton(
                    logo = PclIcons.hotspot,
                    tooltip = "设置热点",
                    onClick = { op = PointerOp.Hotspot },
                )
            )
            if (exists) {
                add(
                    PclListItemButton(
                        logo = PclIcons.restartAlt,
                        tooltip = "重置为自带图片",
                        onClick = { op = PointerOp.PreReset },
                    )
                )
            }
        },
        onClick = { picker.launch(Unit) },
    )

    when (op) {
        PointerOp.None -> {}

        PointerOp.PreReset -> PclConfirmDialog(
            title = "重置",
            content = "您确定要重置虚拟鼠标指针的图片吗？",
            // ⚠️ 不能照抄原生的写法。
            //
            // 原生 `MousePointerOperation` 是「确定 → 切成中间态 Reset → 由 Reset 分支
            // 的 `LaunchedEffect` 干活」；它用的是 `SimpleAlertDialog`，那个的 onConfirm
            // 之后不会自动再调 onDismiss。
            //
            // 本移植的 `PclConfirmDialog` 点「确定」时会 **先 onConfirm、紧接着再 onDismiss**
            // （见 `PclDialogs.kt` 里 `PclMsgButton` 的回调）。于是 `op = Reset` 和
            // `op = None` 落在同一帧里，组合永远看不到 `Reset`，那个分支一次都不会执行 ——
            // 表现就是用户报的「替换图标后按下重置没有反应」。
            //
            // 直接在 onConfirm 里把活干完，不再经过中间态。
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { file.delete() }
                    refresh++
                }
            },
            onDismiss = { op = PointerOp.None },
        )

        PointerOp.Hotspot -> MouseHotspotEditorDialog(
            hotspot = hotspotUnitOf(shape),
            cursorShape = shape,
            onClose = { op = PointerOp.None },
        )
    }
}

/** 与原生 `getMouseHotspot` 同一张表：形状 → 热点设置项。 */
@Composable
private fun hotspotUnitOf(shape: CursorShape) = when (shape) {
    CursorShape.Arrow -> AllSettings.arrowMouseHotspot
    CursorShape.IBeam -> AllSettings.iBeamMouseHotspot
    CursorShape.Hand -> AllSettings.linkMouseHotspot
    CursorShape.CrossHair -> AllSettings.crossHairMouseHotspot
    CursorShape.ResizeNS -> AllSettings.resizeNSMouseHotspot
    CursorShape.ResizeEW -> AllSettings.resizeEWMouseHotspot
    CursorShape.ResizeAll -> AllSettings.resizeAllMouseHotspot
    CursorShape.NotAllowed -> AllSettings.notAllowedMouseHotspot
}

/** Android 键值 → 人类可读的按键名。 */
private fun keyCodeName(code: Int): String {
    val raw = android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    return when (raw) {
        "VOLUME_UP" -> "音量键（上）"
        "VOLUME_DOWN" -> "音量键（下）"
        "MENU" -> "菜单键"
        "BACK" -> "返回键"
        "SPACE" -> "空格"
        "ENTER" -> "回车"
        "TAB" -> "Tab"
        else -> raw.replace('_', ' ')
    }
}

/* ============================================================================
 *  手柄设置 —— 原生 GamepadSettingsScreen
 * ==========================================================================*/

@Composable
fun PclSetupGamepadPage() {
    val context = LocalContext.current
    val gamepadViewModel: GamepadViewModel = viewModel()

    var configToken by remember { mutableIntStateOf(0) }
    var newConfigDialog by remember { mutableStateOf(false) }
    var deleteConfigTarget by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    // 逐键绑定。`GamepadViewModel.currentMapping` 与 `GamepadMappingList.findByMap()` 读的都是
    // **普通字段、不是 Compose 状态**（映射表内部是一个普通 MutableMap），改完之后框架不会知道，
    // 所以每次保存/重置都要自己递增一个 token 触发重算 —— 原生也是这么干的（`refreshed = refreshed.not()`）。
    var bindToken by remember { mutableIntStateOf(0) }
    // 映射分「游戏内」与「菜单内」两套（`GamepadMapping.targetsInGame / targetsInMenu`），原生用两枚 CheckChip 切换。
    var editKeyInGame by remember { mutableStateOf(true) }
    // 非空时弹出逐键绑定键盘（对应原生的 `BindKeyOperation.OnBind`）。
    var bindingTarget by remember { mutableStateOf<GamepadMap?>(null) }

    // `getAllConfigKeys()` 读的是 GamepadViewModel 内部那份 MMKV 缓存，
    // 必须先 `reloadAllMappings()` 才是最新的；而它**不是 Compose 状态**，
    // 所以「先落进一个 state，再渲染」—— 不能写成 `remember(token){ getAllConfigKeys() }`，
    // 那样首帧拿到的是空列表、后面 reload 完了也不会重算。
    var configs by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(configToken) {
        gamepadViewModel.reloadAllMappings()
        configs = gamepadViewModel.getAllConfigKeys()
        // 换配置后 `currentMapping` 指向了另一份映射表，逐键列表必须重算 ——
        // 而 `reloadAllMappings()` 不是状态写入，框架不会自己感知，得由这个 token 顶上。
        bindToken++
    }
    val current = AllSettings.gamepadMappingConfig.state

    // 原生语义：手柄被关闭、或改成 SDL 直通时，映射相关的项一律不可用。
    val remapEnabled = AllSettings.gamepadControl.state &&
            AllSettings.gamepadInputMode.state == GamepadInputMode.Mapped

    PclContent {
        // ---- 手柄控制
        PclCard(modifier = Modifier.fillMaxWidth(), title = "手柄控制") {
            Column {
                ExtraCheck(
                    text = "启用手柄控制",
                    unit = AllSettings.gamepadControl,
                    summary = "若您的其他外设被误识别为手柄，请关闭此选项。",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 输入模式
        PclCard(modifier = Modifier.fillMaxWidth(), title = "输入模式") {
            Column {
                ExtraCombo(
                    label = "手柄输入模式",
                    items = GamepadInputMode.entries.toList(),
                    selected = AllSettings.gamepadInputMode.state,
                    labelOf = { context.getString(it.titleRes) },
                    onSelect = { AllSettings.gamepadInputMode.save(it) },
                )
                ExtraSummary(context.getString(AllSettings.gamepadInputMode.state.summaryRes))
                PclHint(
                    text = "切换输入模式后需要重新启动游戏才会生效。" +
                            "映射模式适用于不支持手柄的游戏；SDL 直通把原始手柄输入直接交给游戏" +
                            "（Minecraft 26.3+ 原生手柄），此时按键映射不再生效。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 摇杆
        PclCard(modifier = Modifier.fillMaxWidth(), title = "摇杆") {
            Column {
                ExtraCombo(
                    label = "摇杆操作模式",
                    items = JoystickMode.entries.toList(),
                    selected = AllSettings.joystickControlMode.state,
                    labelOf = { context.getString(it.titleRes) },
                    onSelect = { AllSettings.joystickControlMode.save(it) },
                )
                ExtraSummary(context.getString(AllSettings.joystickControlMode.state.summaryRes))
                ExtraSlider(
                    label = "手柄死区缩放",
                    unit = AllSettings.gamepadDeadZoneScale,
                    min = 50,
                    max = 200,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "摇杆指针灵敏度",
                    unit = AllSettings.gamepadCursorSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
                ExtraSlider(
                    label = "摇杆视角灵敏度",
                    unit = AllSettings.gamepadCameraSensitivity,
                    min = 25,
                    max = 300,
                    suffix = "%",
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 按键映射
        PclCard(modifier = Modifier.fillMaxWidth(), title = "按键映射") {
            Column {
                ExtraCombo(
                    label = "手柄映射配置",
                    items = configs,
                    selected = current.takeIf { it in configs },
                    labelOf = { it },
                    enabled = remapEnabled,
                    onSelect = {
                        AllSettings.gamepadMappingConfig.save(it)
                        gamepadViewModel.reloadAllMappings()
                        configToken++
                    },
                )
                if (configs.isEmpty()) {
                    ExtraSummary("还没有创建任何映射配置，请先新建一份。")
                }
                ExtraRow("管理配置") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PclButton(
                            text = "新建配置",
                            enabled = remapEnabled,
                            onClick = { newConfigDialog = true },
                        )
                        PclButton(
                            text = "删除当前配置",
                            enabled = remapEnabled && current in configs,
                            colorState = PclColorState.Red,
                            onClick = { deleteConfigTarget = current },
                        )
                    }
                }
                ExtraRow("按键映射") {
                    PclButton(
                        text = "重置手柄按键映射",
                        enabled = remapEnabled,
                        colorState = PclColorState.Red,
                        onClick = { confirmReset = true },
                    )
                }
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = "按键映射可帮助更好地适配您的手柄：启动器会请您依次按下手柄的关键按键，" +
                            "把不同品牌手柄的键值对齐成同一套。若先前设置不正确，" +
                            "可重置后重新启动游戏再绑定一次。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 按键绑定
        // 对应原生 `GamepadSettingsScreen` 里 `BindKeyOperation` 那一段：先选「游戏内 / 菜单内」，
        // 然后一个手柄按键一行，点开 `GamepadBindingKeyboard` 挑要触发的键盘/鼠标操作。
        // 手柄按键 → 键盘按键的映射表存在 `GamepadMappingList` 里（`targetsInGame` / `targetsInMenu`，
        // 落盘在 keyMappingListMMKV），和原生读写的完全是同一份数据。
        PclCard(modifier = Modifier.fillMaxWidth(), title = "按键绑定") {
            Column {
                ExtraRow("作用范围") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PclPillButton(
                            text = "游戏内",
                            checked = editKeyInGame,
                            // `highlight = true` 不能省：`PclPillButton` 默认那套配色
                            // （白字 + 透明底）对应 `MyRadioButton.ColorType=White`，是给顶部**蓝色标题栏**
                            // 上的胶囊用的；照搬到白卡片里就是白字白底 —— 「菜单内」会整块看不见。
                            // PCL 里所有「浅色内容区里多选一」的胶囊都写 ColorType=Highlight
                            // （登录方式选择、实例列表筛选、资源详情页），这里同例。
                            highlight = true,
                            enabled = remapEnabled,
                            onClick = { editKeyInGame = true },
                        )
                        PclPillButton(
                            text = "菜单内",
                            checked = editKeyInGame.not(),
                            highlight = true,
                            enabled = remapEnabled,
                            onClick = { editKeyInGame = false },
                        )
                    }
                }
                ExtraSummary(
                    "点一行即可指定这个手柄按键要触发哪些键盘 / 鼠标操作；" +
                            "行尾的重置按钮把这一项恢复成默认按键。"
                )
                Spacer(Modifier.height(4.dp))

                GamepadMap.entries.forEach { item ->
                    // `findByMap()` 的结果不会自己刷新（映射表内部是普通 MutableMap），
                    // 因此必须拿 `bindToken` / `editKeyInGame` 当 key 重算。
                    val codes = remember(bindToken, editKeyInGame, item) {
                        gamepadViewModel.currentMapping
                            ?.findByMap(item, inGame = editKeyInGame)
                            ?.toList()
                            ?: emptyList()
                    }
                    // `getNameByGamepadEvent` 是 @Composable（要用 stringResource），
                    // 不能塞进 `joinToString` 这类非 inline lambda，只能显式循环。
                    val names = StringBuilder()
                    for (code in codes) {
                        if (names.isNotEmpty()) names.append("、")
                        names.append(getNameByGamepadEvent(code))
                    }

                    PclListItem(
                        title = item.pclKeyDisplayName,
                        info = if (names.isEmpty()) "未绑定任何键值" else "已绑定：$names",
                        logoContent = {
                            Image(
                                modifier = Modifier.fillMaxSize(),
                                painter = painterResource(item.getIconRes()),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                            )
                        },
                        type = PclListItemType.Clickable,
                        height = 42.dp,
                        buttons = listOf(
                            PclListItemButton(
                                logo = PclIcons.reset,
                                tooltip = "重置",
                                // 与左栏「初始化本页设置」的刷新按钮同一个 LogoScale（PCL 源码里 0.9）。
                                logoScale = 0.9f,
                                onClick = {
                                    gamepadViewModel.currentMapping?.resetMapping(item, editKeyInGame)
                                    bindToken++
                                },
                            ),
                        ),
                        onClick = if (remapEnabled) ({ bindingTarget = item }) else null,
                    )
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    if (newConfigDialog) {
        PclInputDialog(
            title = "创建手柄映射配置",
            label = "配置名称",
            onConfirm = { name ->
                gamepadViewModel.createNewConfig(
                    name = name,
                    onContainsConfig = { PclHintHost.push("已有同名称的配置。", PclHintTheme.Red) },
                    onFinished = { configToken++ },
                )
            },
            onDismiss = { newConfigDialog = false },
        )
    }

    deleteConfigTarget?.let { name ->
        PclConfirmDialog(
            title = "删除配置",
            content = "您确定要删除配置「$name」吗？",
            danger = true,
            onConfirm = {
                gamepadViewModel.deleteConfig(name) { configToken++ }
                deleteConfigTarget = null
            },
            onDismiss = { deleteConfigTarget = null },
        )
    }

    if (confirmReset) {
        PclConfirmDialog(
            title = "重置手柄按键映射",
            content = "已绑定的手柄按键映射会被全部清除，之后重新进入游戏时" +
                    "启动器会再引导您绑定一次。是否继续？",
            danger = true,
            onConfirm = {
                remapperMMKV().clearAll()
                PclHintHost.push("已重置所有手柄按键映射。", PclHintTheme.Blue)
                confirmReset = false
            },
            onDismiss = { confirmReset = false },
        )
    }

    bindingTarget?.let { item ->
        // 与原生 `BindKeyOperation.OnBind` 完全同构：
        // 打开时把当前已绑定的键拷成一份本地可变清单，之后每次增删都**立刻**落盘
        // （`saveMapping()` 内部会 `save()` 到 MMKV），关掉键盘不需要再确认。
        val mapping = gamepadViewModel.currentMapping
        val selectedKeys = remember(item, editKeyInGame) {
            (
                    mapping?.findByMap(item, inGame = editKeyInGame)?.toList()
                            ?: emptyList()
                    ).toMutableList()
        }

        GamepadBindingKeyboard(
            selectedKeys = selectedKeys,
            onKeyAdd = { key ->
                selectedKeys.add(key)
                mapping?.saveMapping(item, selectedKeys.toSet(), editKeyInGame)
                bindToken++
            },
            onKeyRemove = { key ->
                selectedKeys.remove(key)
                mapping?.saveMapping(item, selectedKeys.toSet(), editKeyInGame)
                bindToken++
            },
            onDismissRequest = { bindingTarget = null },
        )
    }
}

/**
 * 手柄按键的中文名。
 *
 * 原生界面只用图标表示手柄按键（`GamepadMap.getIconRes()`），没有文字；
 * 而 PCL 的列表项是「文字为主、图标只是前缀」，所以这里补一份名字。
 */
private val GamepadMap.pclKeyDisplayName: String
    get() = when (this) {
        GamepadMap.ButtonA -> "A 键"
        GamepadMap.ButtonB -> "B 键"
        GamepadMap.ButtonX -> "X 键"
        GamepadMap.ButtonY -> "Y 键"
        GamepadMap.ButtonStart -> "Start（菜单）键"
        GamepadMap.ButtonSelect -> "Select（视图）键"
        GamepadMap.ButtonLB -> "LB 左肩键"
        GamepadMap.ButtonRB -> "RB 右肩键"
        GamepadMap.ButtonLT -> "LT 左扳机"
        GamepadMap.ButtonRT -> "RT 右扳机"
        GamepadMap.DPadUp -> "方向键 上"
        GamepadMap.DPadLeft -> "方向键 左"
        GamepadMap.DPadDown -> "方向键 下"
        GamepadMap.DPadRight -> "方向键 右"
        GamepadMap.StickLeftClick -> "左摇杆 按下"
        GamepadMap.StickRightClick -> "右摇杆 按下"
    }

/* ============================================================================
 *  Java 管理 —— 原生 JavaManageScreen（列表版式取自 PCL2 的 Java 选择列表）
 * ==========================================================================*/

@Composable
fun PclSetupJavaPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventViewModel: EventViewModel = viewModel()

    var runtimes by remember { mutableStateOf<List<Runtime>>(emptyList()) }
    var token by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<Runtime?>(null) }
    var loading by remember { mutableStateOf(true) }
    var installing by remember { mutableStateOf(false) }

    LaunchedEffect(token) {
        loading = true
        runtimes = withContext(Dispatchers.IO) {
            runCatching { RuntimesManager.getRuntimes(forceLoad = true) }.getOrDefault(emptyList())
        }
        // 列表是空的：先自动补一次内置运行库。
        // 有些机型首次安装时解压页被打断，进到这里就是「一个 Java 都没有」——
        // 原来只能让用户自己去导入 .xz，等于玩不了游戏。
        if (runtimes.isEmpty()) {
            val report = RuntimeProvisioner.ensure(context, RuntimeProvisioner.Mode.REPAIR)
            if (report.installed.isNotEmpty()) {
                runtimes = withContext(Dispatchers.IO) {
                    runCatching { RuntimesManager.getRuntimes(forceLoad = true) }
                        .getOrDefault(emptyList())
                }
                PclHintHost.push(
                    "已自动安装内置 Java 运行库：${report.installed.joinToString("、")}",
                    PclHintTheme.Blue,
                )
            }
        }
        loading = false
    }

    // 选中的运行库已经不在了（被删掉 / 本机根本没有那个内置包）：改指到第一个真实可用的，
    // 否则设置里显示的「已选中」是个指向空气的名字，启动游戏时才报错
    LaunchedEffect(runtimes) {
        val current = AllSettings.javaRuntime.getValue()
        if (runtimes.isEmpty()) return@LaunchedEffect
        if (current.isNotBlank() && runtimes.any { it.name == current }) return@LaunchedEffect
        runtimes.minByOrNull { it.javaVersion }?.let {
            AllSettings.javaRuntime.save(it.name)
        }
    }

    // 导入 .xz 运行库：与原生 `progressRuntimeUri` 同一条链路
    // （checkExtensionOrThrow → installRuntime → 失败提示 → 完成后刷新）。
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.getFileName(uri)
        if (name.isNullOrBlank()) {
            PclHintHost.push("无法读取所选文件的文件名。", PclHintTheme.Red)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching { name.checkExtensionOrThrow(listOf("xz")) }.onFailure {
                PclHintHost.push("请选择 .xz 格式的 Java 运行库。", PclHintTheme.Red)
                return@launch
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选文件。")
                    RuntimesManager.installRuntime(
                        nativeLibDir = PathManager.DIR_NATIVE_LIB,
                        inputStream = stream,
                        name = name,
                    )
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        token++
                        PclHintHost.push("运行库「$name」导入完成。", PclHintTheme.Blue)
                    }
                }.onFailure { th ->
                    withContext(Dispatchers.Main) {
                        PclHintHost.push("导入失败：${th.message ?: "未知错误"}", PclHintTheme.Red)
                    }
                }
            }
        }
    }

    val selected = AllSettings.javaRuntime.state
    // 勾上「自动选择合适的 Java 环境」之后，下面那张运行库列表就只是给用户看现状的：
    // 既然要自动挑，还让人手动选一个就是自相矛盾（最后到底用了哪个谁也说不清）。
    // 所以自动选择开启时把整张列表灰掉并禁止点选 —— 取消勾选即恢复可点。
    val autoPick = AllSettings.autoPickJavaRuntime.state

    PclContent {
        // ---- Java 选择策略
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Java 选择") {
            Column {
                ExtraCheck(
                    text = "自动选择合适的 Java 环境",
                    unit = AllSettings.autoPickJavaRuntime,
                    summary = "开启后启动游戏时会按游戏所需的 Java 版本自动挑选；" +
                            "关闭后使用下方列表中选中的运行库。",
                )
                Spacer(Modifier.height(6.dp))
                PclHint(
                    text = if (autoPick) {
                        "已开启自动选择，下方运行库列表仅供查看；取消勾选后可手动指定。"
                    } else {
                        "当前选中：" +
                                (runtimes.firstOrNull { it.name == selected }?.name
                                        ?: "未指定（按自动选择处理）")
                    },
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 运行库列表
        PclCard(modifier = Modifier.fillMaxWidth(), title = "Java 运行库") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PclButton(
                        text = "刷新",
                        logo = PclIcons.refresh,
                        onClick = { token++ },
                    )
                    PclButton(
                        text = "导入运行库（.xz）",
                        logo = PclIcons.addBox,
                        // 用 `*/*`：.xz 的 MIME 各机型给得不一样（application/x-xz /
                        // application/octet-stream 都有），限死类型会直接看不到文件。
                        onClick = { importPicker.launch("*/*") },
                    )
                }
                Spacer(Modifier.height(8.dp))

                when {
                    loading -> PclText(text = "正在扫描运行库…", color = summaryColor, fontSize = 12)

                    runtimes.isEmpty() -> Column {
                        PclText(
                            text = "没有找到任何 Java 运行库。点下面的按钮自动安装内置运行库，" +
                                    "也可以自己导入 .xz 格式的运行库。",
                            color = summaryColor,
                            fontSize = 12,
                        )
                        Spacer(Modifier.height(8.dp))
                        PclButton(
                            text = if (installing) "正在安装…" else "安装内置运行库",
                            logo = PclIcons.addBox,
                            enabled = !installing,
                            onClick = {
                                installing = true
                                scope.launch {
                                    val report = RuntimeProvisioner.ensure(
                                        context,
                                        RuntimeProvisioner.Mode.REPAIR,
                                    )
                                    installing = false
                                    when {
                                        report.installed.isNotEmpty() -> {
                                            token++
                                            PclHintHost.push(
                                                "已安装：${report.installed.joinToString("、")}",
                                                PclHintTheme.Blue,
                                            )
                                        }

                                        report.failed.isNotEmpty() -> PclHintHost.push(
                                            "安装失败：${report.failed.first().second}",
                                            PclHintTheme.Red,
                                        )

                                        else -> PclHintHost.push(
                                            "没有可安装的内置运行库（本机架构没有配套的包），可导入 .xz。",
                                            PclHintTheme.Yellow,
                                        )
                                    }
                                }
                            },
                        )
                    }

                    else -> runtimes.forEach { runtime ->
                        PclJavaRow(
                            runtime = runtime,
                            checked = runtime.name == selected,
                            enabled = !autoPick,
                            onSelect = { AllSettings.javaRuntime.save(runtime.name) },
                            onOpenFolder = {
                                eventViewModel.sendEvent(
                                    EventViewModel.Event.OpenFileManager(
                                        rootPath = PathManager.DIR_MULTIRT.absolutePath,
                                        currentPath = File(PathManager.DIR_MULTIRT, runtime.name).absolutePath,
                                    )
                                )
                            },
                            onDelete = { deleteTarget = runtime },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                PclHint(
                    text = "「启动器自带」的运行库由 Zalith 自动下载，删除后下次启动游戏会重新下载；" +
                            "自己导入的运行库可以放心删除。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    deleteTarget?.let { runtime ->
        PclConfirmDialog(
            title = "删除运行库",
            content = "确定要删除「${runtime.name}」吗？",
            danger = true,
            onConfirm = {
                val name = runtime.name
                deleteTarget = null
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { RuntimesManager.removeRuntime(name) } }
                        .onSuccess { token++ }
                        .onFailure { th ->
                            PclHintHost.push("删除失败：${th.message ?: "未知错误"}", PclHintTheme.Red)
                        }
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** 一行 Java 运行库：版本信息 + 打开目录 / 删除。 */
@Composable
private fun PclJavaRow(
    runtime: Runtime,
    checked: Boolean,
    /** 开启「自动选择」时整行灰掉、点不中（单选、行内按钮一并失效）。 */
    enabled: Boolean,
    onSelect: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
) {
    PclListItem(
        title = runtime.name,
        modifier = Modifier.fillMaxWidth(),
        info = runtimeInfo(runtime),
        logo = PclIcons.setupJava,
        logoScale = 0.8f,
        type = PclListItemType.RadioBox,
        checked = checked,
        height = 50.dp,
        enabled = enabled,
        buttons = listOf(
            PclListItemButton(
                logo = PclIcons.openFolder,
                tooltip = "打开所在目录",
                onClick = onOpenFolder,
            ),
            PclListItemButton(
                logo = PclIcons.delete,
                tooltip = "删除这个运行库",
                onClick = onDelete,
            ),
        ),
        onClick = onSelect,
    )
}

/** 运行库的一行描述，对应原生 `JavaRuntimeItem` 里的那几个标签。 */
private fun runtimeInfo(runtime: Runtime): String = buildString {
    if (runtime.versionString == null) {
        append("版本信息损坏，启动时不会使用")
    } else {
        append(runtime.versionString)
        runtime.arch?.let { append(" · ").append(it) }
        if (runtime.javaVersion != 0) append(" · Java ").append(runtime.javaVersion)
        if (!runtime.isCompatible()) append(" · 与本机架构不匹配")
    }
    if (runtime.isProvidedByLauncher) append(" · 启动器自带")
}

/* ============================================================================
 *  控制管理 —— 原生 ControlManageScreen
 * ==========================================================================*/

@Composable
fun PclSetupControlManagePage() {
    val context = LocalContext.current
    val dataList by ControlManager.dataList.collectAsStateWithLifecycle()
    val isRefreshing by ControlManager.isRefreshing.collectAsStateWithLifecycle()

    var createDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ControlData?>(null) }
    /**
     * 导入失败的具体原因。
     *
     * 【别只推左下角 hint】「缺 editorVersion」「JSON 损坏」「版本过新」这些是用户唯一
     * 能拿去排查的线索，hint 三秒就没了，观感上跟「点了没反应」一模一样 ——
     * 第 213 轮就是栽在这儿。改成必须点掉的提示框。
     */
    var importError by remember { mutableStateOf<String?>(null) }
    // 最近一次导入的结果：放在文件级单例里，切走再回来也还在
    val lastImportResult by PclControlImportLog.text

    // 原生界面进入这一页时会先确保至少有一份默认布局（`checkDefaultAndRefresh`），
    // 否则全新安装的用户会看到一份空列表、没得选。
    LaunchedEffect(Unit) { ControlManager.checkDefaultAndRefresh(context) }

    val selectedName = AllSettings.controlLayout.state

    // 【不能走 `Event.ImportControls`】那是 Activity 级事件通道，只有 `MainActivity` 在
    // `lifecycleScope` 里收 —— PCL 界面跑在 `Pcl2Activity` 里，事件发出去没人接，
    // 表现就是「选完 json 文件，列表里什么都没多出来」。
    // 这里直接调 `ControlManager.importControl`（和 `PclExternalImport` 的外部导入同一条路），
    // 导完自己 refresh 一次列表。
    val importScope = rememberCoroutineScope()
    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) {
            // 【别再静默 return】选择器没给回 uri（用户点了取消 / 某些文件管理器返回空）
            // 和「导入失败」在界面上长得一模一样，必须能区分开
            PclControlImportLog.set("没有拿到文件：文件选择器没有返回可读取的地址。")
            return@rememberLauncherForActivityResult
        }
        PclControlImportLog.set("已选中 ${uris.size} 个文件，正在导入…")
        importScope.launch {
            var imported = 0
            val failures = mutableListOf<String>()
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    // 提示里带上文件名：一次选好几个文件时，不然根本不知道是哪个出的问题
                    val displayName = context.getFileName(uri) ?: "所选文件"
                    runCatching {
                        val stream = openDocumentStream(context, uri)
                            ?: error("读不到这个文件，可能是文件管理器没给读取权限。")
                        var parseError: String? = null
                        stream.use {
                            ControlManager.importControl(
                                inputStream = it,
                                // `ControlManager` 的消息里已经写明「文件已保存，但读不出来：…」，
                                // 直接透传，别再套一层
                                onSerializationError = { e ->
                                    parseError = e.message ?: "文件已保存，但读不出来。"
                                },
                                catchedError = { e ->
                                    parseError = "写盘失败：${e.message}"
                                },
                            )
                        }
                        parseError?.let { message -> error(message) }
                        imported++
                    }.onFailure {
                        val reason = it.message ?: it.javaClass.simpleName
                        failures += "「$displayName」$reason"
                        Logger.warning(LOG_TAG, "控制布局导入失败：$displayName / $uri", it)
                    }
                }
                // 列表要重新扫一遍：`dataList` 是 ControlManager 里的 StateFlow，
                // 不 refresh 的话导进来的那份永远不出现在这一页
                ControlManager.refresh()
            }
            // `refresh()` 内部是异步的，等它把表算完再报「当前列表里到底有几份」
            delay(150)
            val names = ControlManager.dataList.value.map { it.file.name }
            val listInfo =
                if (names.isEmpty()) "列表仍为空"
                else "列表现有 ${names.size} 份：${names.joinToString("、")}"
            if (imported > 0) {
                PclControlImportLog.set("已导入 $imported 个 —— $listInfo")
                PclHintHost.push(
                    if (imported == 1) "控制布局已导入。" else "已导入 $imported 个控制布局。",
                    PclHintTheme.Green,
                )
            } else {
                val reason = failures.firstOrNull() ?: "无法读取所选文件。"
                // 一个都没能正常用上 —— 原因必须让人看见，不能三秒后自己消失
                PclControlImportLog.set("$reason —— $listInfo")
                importError = reason
            }
        }
    }

    PclContent {
        PclCard(modifier = Modifier.fillMaxWidth(), title = "控制布局") {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PclButton(
                        text = "刷新",
                        logo = PclIcons.refresh,
                        onClick = { ControlManager.refresh() },
                    )
                    PclButton(
                        text = "导入布局（.json）",
                        logo = PclIcons.addBox,
                        onClick = {
                            // 【只给一个 `*/*`】以前传的是
                            // `arrayOf("application/json", "*/*")`，部分系统的文件选择器会拿
                            // EXTRA_MIME_TYPES 去过滤，结果选完之后一个 uri 都不返回 ——
                            // 回调里 uris 为空，直接 return，表现就是「点了完全没反应」。
                            // 给通配就一定选得到任何文件，后缀对不对交给导入流程自己判断。
                            runCatching { importPicker.launch(arrayOf("*/*")) }
                                .onFailure {
                                    val why = "打不开文件选择器：${it.message}"
                                    PclControlImportLog.set(why)
                                    PclHintHost.push(why, PclHintTheme.Red)
                                }
                        },
                    )
                    PclButton(
                        text = "新建空白布局",
                        logo = PclIcons.selectCreateFolder,
                        onClick = { createDialog = true },
                    )
                }
                Spacer(Modifier.height(8.dp))

                when {
                    isRefreshing && dataList.isEmpty() ->
                        PclText(text = "正在读取控制布局…", color = summaryColor, fontSize = 12)

                    dataList.isEmpty() ->
                        PclText(text = "没有可用的控制布局。", color = summaryColor, fontSize = 12)

                    else -> dataList.forEach { data ->
                        PclControlRow(
                            data = data,
                            checked = data.file.name == selectedName,
                            onSelect = { ControlManager.selectControl(data) },
                            onEdit = { startEditorActivity(context, data.file) },
                            onDuplicate = {
                                ControlManager.duplicateControl(data) { th ->
                                    PclHintHost.push("复制失败：${th.message ?: "未知错误"}", PclHintTheme.Red)
                                }
                            },
                            onDelete = { deleteTarget = data },
                        )
                    }
                }

                lastImportResult?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    PclHint(
                        text = message,
                        // 失败留下的是黄条，成功是绿条 —— 扫一眼就知道上一次到底怎么了
                        theme = if (message.startsWith("已导入")) PclHintTheme.Green
                        else PclHintTheme.Yellow,
                    )
                }

                Spacer(Modifier.height(8.dp))
                PclHint(
                    text = "布局文件保存在：${PathManager.DIR_CONTROL_LAYOUTS.absolutePath}\n" +
                            "点一行即可把它设为当前布局；「编辑布局」会打开可视化编辑器，" +
                            "调整按键的位置与大小。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))
        PclBottomSpacer()
    }

    if (createDialog) {
        PclInputDialog(
            title = "新建控制布局",
            label = "布局名称",
            onConfirm = { name ->
                ControlManager.createEmptyControl(name) { th ->
                    PclHintHost.push("新建失败：${th.message ?: "未知错误"}", PclHintTheme.Red)
                }
            },
            onDismiss = { createDialog = false },
        )
    }

    deleteTarget?.let { data ->
        val title = if (data.isSupport) data.controlLayout.info.name.default else data.file.name
        PclConfirmDialog(
            title = "删除控制布局",
            content = "确定要删除「$title」吗？",
            danger = true,
            onConfirm = {
                ControlManager.deleteControl(data)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    importError?.let { message ->
        PclNoticeDialog(
            title = "控制布局导入未完成",
            content = message,
            onDismiss = { importError = null },
        )
    }
}

/** 一行控制布局：名称 / 作者信息 + 编辑 / 复制 / 删除。 */
@Composable
private fun PclControlRow(
    data: ControlData,
    checked: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = if (data.isSupport) data.controlLayout.info.name.default else data.file.name
    val info = buildString {
        if (!data.isSupport) {
            // 这类行有两种来源：布局版本比本启动器新，或者文件本身损坏。
            // 以前损坏的文件会被 refresh 直接丢掉（用户只看到「导入完了但列表没变化」），
            // 现在保留成一行，至少能删。
            append("不支持或无法读取，只能删除")
        } else {
            val author = data.controlLayout.info.author.default
            if (author.isNotEmpty()) append("作者：").append(author)
            if (data.controlLayout.info.versionName.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("版本：").append(data.controlLayout.info.versionName)
            }
            if (isEmpty()) append(data.file.name)
        }
    }

    PclListItem(
        title = title,
        modifier = Modifier.fillMaxWidth(),
        info = info,
        logo = PclIcons.setupControlManage,
        logoScale = 0.85f,
        type = PclListItemType.RadioBox,
        checked = checked,
        height = 50.dp,
        buttons = buildList {
            if (data.isSupport) {
                add(
                    PclListItemButton(
                        logo = PclIcons.loginMsEdit,
                        tooltip = "编辑布局",
                        onClick = onEdit,
                    )
                )
                add(
                    PclListItemButton(
                        logo = PclIcons.fileCopy,
                        tooltip = "复制一份",
                        onClick = onDuplicate,
                    )
                )
            }
            add(
                PclListItemButton(
                    logo = PclIcons.delete,
                    tooltip = "删除这份布局",
                    onClick = onDelete,
                )
            )
        },
        onClick = { if (data.isSupport) onSelect() },
    )
}
