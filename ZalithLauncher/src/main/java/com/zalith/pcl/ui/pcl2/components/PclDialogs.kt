/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL 风格的弹出对话框，对应 `Controls/MyMsg/` 下的三个控件：
 *   MyMsgText.xaml    信息 / 确认（正文 + 若干按钮）
 *   MyMsgInput.xaml   单行输入（正文 + 文本框 + 按钮）
 *   MyMsgSelect.xaml  单选（正文 + 单选列表 + 按钮）
 *
 * 三份 XAML 的骨架逐字符一致，只有正文那一行不同，因此这里抽出一个共用的
 * [PclMsgFrame]。所有尺寸与配色都直接取自源码，见 PclDimens 里「对话框」一节的注释。
 *
 * 按钮顺序特别注意：PCL 的 `MyMsgBox(内容, 标题, Button1, Button2)` 中 Button1 是
 * **肯定按钮**，在 XAML 的 PanBtn 里也声明在最前（最左）；`MyMsgText.xaml.vb:36` 里
 * 「存在 Button2 且 Button1 不是红色时，Button1 变为 Highlight」。
 * 也就是说 PCL 是 **[确定(蓝)] [取消]**，而不是常见的 [取消] [确定]。
 * 判据：`FormMain.xaml.vb:239` / `:535` 等处 `MyMsgBox(..., "确定", "取消") = 2 Then Return`，
 * 说明 Button2 才是取消。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zalith.pcl.game.plugin.renderer_v2.data.EnvSettingUnit
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclEase
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** `MyMsg*.xaml` 的 `PanBorder Background="#FBFBFB"`。 */
private val MsgBackground = Color(0xFFFBFBFB)

/** `MyMsg*.xaml` 的 `LabCaption Foreground="#FF5C5C5C"`。 */
private val MsgBodyColor = Color(0xFF5C5C5C)

/** 对话框确认按钮的默认文案，对应 `LangDialogBtnOK`。 */
private const val MSG_BTN_OK = "确定"

/** 对话框取消按钮的默认文案，对应 `LangDialogBtnCancel`。 */
private const val MSG_BTN_CANCEL = "取消"

/**
 * 对话框内容容器 —— **所有 `Dialog` 的内容都套一层它**。
 *
 * Compose 的 `Dialog`（尤其 `usePlatformDefaultWidth = false`）会把**整个窗口的尺寸当作最小约束**
 * 传给内容。直接被量的话，卡片会被强行撑到满屏，而 `PclListItem` 内部有 `fillMaxHeight()`，
 * 于是唯一的条目会占满整屏、标题被顶到正中间，底部的按钮还会被挤出可视区 ——
 * 「选择要下载到的版本」里只有一个版本却占了一整屏就是这么来的。
 *
 * 这里用 `wrapContentSize` 把最小约束清成 0，再把卡片居中。
 *
 * ---------------------------------------------------------------- 弹出动效
 * 进入动画原封不动取自 `MyMsg*.xaml.vb`（三个对话框完全一致）。XAML 里那个
 * `Grid.RenderTransform` 是：
 * ```
 * RenderTransformOrigin = "0,0.5"
 * RotateTransform  Angle = -4      (TransformRotate)
 * TranslateTransform      Y = 40   (TransformPos)
 * Opacity = 0
 * ```
 * 进入时（`:40-46`）：
 * ```
 * AaColor(FrmMain.PanMsg, Background, 深色遮罩, 200)                       ' 遮罩褪色
 * AaOpacity(Me, 1, 120, 60)                                              ' 内容淡入
 * AaDouble(TransformPos.Y, -cur, 300, 60, AniEaseOutBack(Weak))          ' Y: 40 → 0 弹入
 * AaDouble(TransformRotate.Angle, -cur, 300, 60, AniEaseOutFluent(Weak)) ' 旋转: -4° → 0°
 * ```
 * 也就是**从下方 40 像素、带 −4° 倾斜、用回弹缓动弹着飞进来**。
 * 这里以左中（`0, 0.5`）为变换原点复刻同一组参数。
 */
@Composable
internal fun PclDialogHost(
    /** 是否正在播放退出动画（由 [PclDialogContainer] 驱动）。 */
    exiting: Boolean = false,
    /** 退出动画播完时回调 —— 到这一步才真正可以把 Dialog 从组合里拿掉。 */
    onExitFinished: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val startY = with(density) { 40.dp.toPx() }
    val exitY = with(density) { 20.dp.toPx() }
    val yOffset = remember { Animatable(startY) }
    val rotation = remember { Animatable(-4f) }
    val alpha = remember { Animatable(0f) }

    // 进入
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { alpha.animateTo(1f, tween(durationMillis = 120, delayMillis = 60)) }
            launch {
                yOffset.animateTo(
                    0f,
                    tween(durationMillis = 300, delayMillis = 60, easing = PclEase.outBackWeak),
                )
            }
            launch {
                rotation.animateTo(
                    0f,
                    tween(durationMillis = 300, delayMillis = 60, easing = PclEase.outFluentWeak),
                )
            }
        }
    }

    // 退出（`MyMsg*.xaml.vb:58-69`）：
    //   AaOpacity(Me, -Opacity, 80, 20)                                     ' 淡出
    //   AaDouble(TransformPos.Y, 20 - cur, 150, 0, AniEaseOutFluent)        ' Y: 0 → 20 往下沉
    //   AaDouble(TransformRotate.Angle, 6 - cur, 150, 0, AniEaseInFluent)   ' 旋转: 0° → 6°
    //   AaCode(移除, After)
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        coroutineScope {
            launch { alpha.animateTo(0f, tween(durationMillis = 80, delayMillis = 20)) }
            launch { yOffset.animateTo(exitY, tween(durationMillis = 150, easing = PclEase.outFluent)) }
            launch {
                rotation.animateTo(6f, tween(durationMillis = 150, easing = PclEase.inFluentWeak))
            }
        }
        onExitFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationY = yOffset.value
                rotationZ = rotation.value
                this.alpha = alpha.value
            },
        ) {
            content()
        }
    }
}

/**
 * PCL 弹窗的**统一容器** —— 所有 `MyMsg*` 系列对话框都套它，负责三件事：
 *
 *   1. 建 `Dialog`（`usePlatformDefaultWidth = false`，尺寸交给内容自己定）；
 *   2. 播进入 / 退出动画（都在 [PclDialogHost] 里）；
 *   3. **把「关闭」拆成两步** —— 内容里调 `requestClose()`（或点外部 / 按返回键）只是**请求**关闭，
 *      退出动画播完之后才真正回调 [onDismiss]，让调用方把 Dialog 从组合里拿掉。
 *
 * 第 3 点是必须的：Compose 的 `Dialog` 一旦从组合树里移除就**瞬间消失**，
 * 不给退出动画任何机会。PCL 那边弹窗是主窗体上的浮层（`FrmMain.PanMsg`），
 * 所以才有「淡出 + 下沉 + 倾斜 6°」的收场；这里靠延迟 dismiss 来还原同样的观感。
 *
 * 用法：把按钮的 `onClick = onDismiss` 改成 `onClick = requestClose`。
 *
 * @param content 收到一个 `requestClose` 回调 —— 内容里**所有**关闭路径都要用它。
 * @param dismissOnOutsideClick 点外部 / 按返回键是否允许关闭。默认 true；
 *        任务进行中的弹窗（如 `PclTitledTaskDialog`）要传 false，以免误触关掉。
 * @param dismissHandler 返回键 / 点外部的**接管**动作。非 null 时代替默认的 `requestClose`
 *        （也就是说**不播退出动画**，由调用方自己改状态把弹窗拿走）。下载进度这类
 *        「状态驱动」的弹窗必须用它，原因见函数体内 `closed` 那条注释。
 */
@Composable
internal fun PclDialogContainer(
    onDismiss: () -> Unit,
    dismissOnOutsideClick: Boolean = true,
    dismissHandler: (() -> Unit)? = null,
    content: @Composable (requestClose: () -> Unit) -> Unit,
) {
    var exiting by remember { mutableStateOf(false) }
    /**
     * 退出动画播完后**无条件**把窗口从组合里摘掉。
     *
     * 【血泪】以前只靠 `onDismiss` 让调用方清状态间接移除 Dialog，一旦调用方传了空 lambda
     * （或状态没复位），Dialog 就永远留在组合里：`alpha` 已经被退出动画淡到 0，
     * 窗口却是实打实的、可获得焦点的 —— 于是出现一个**看不见但把整屏触摸全吃掉**的窗口。
     * 表现就是用户报的「按下返回后点什么都没反应」，界面看着完全正常。
     * 现在只要退出动画跑完，我们自己就把窗口摘掉，调用方那边再怎么漏都不会再卡。
     */
    var closed by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (!exiting) exiting = true }
    // 【为什么必须显式 provide】和 `PclComboBoxPopup` 是同一类坑（见 PclControls.kt:1851）：
    // `Dialog` 的内容挂在**另一个窗口**的子 composition 里，它拿到的 `LocalDensity`
    // 不保证等于调用点的。而 `PclTheme` 正是把「界面缩放」乘进 density 实现的
    // （`PclTheme.kt:467`），一旦两边不一致，整只弹窗就按另一把尺子渲染 ——
    // 实测某设备上主界面 density ≈ 1.875（界面缩放 75%），弹窗却是原始的 2.5，
    // 于是弹窗整体比周围 UI 大出约 1.33 倍：标题、正文、按钮、留白全部同步放大，
    // 看起来就是「字大了一圈、按钮又粗又方」。
    // 把调用点的 density 带进去，弹窗里的每一个 dp 都与整页 UI 同尺。
    val density = LocalDensity.current
    if (!closed) {
        Dialog(
            onDismissRequest = {
                if (dismissOnOutsideClick) {
                    if (dismissHandler != null) dismissHandler() else requestClose()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            CompositionLocalProvider(LocalDensity provides density) {
                PclDialogHost(
                    exiting = exiting,
                    onExitFinished = {
                        closed = true
                        onDismiss()
                    },
                ) {
                    content(requestClose)
                }
            }
        }
    }
}

/**
 * `MyMsgText` / `MyMsgInput` / `MyMsgSelect` 共用的对话框外框。
 *
 * @param buttons 「肯定按钮在前、取消在后」——这是 PCL 的顺序，别按 Android 习惯反过来。
 */
@Composable
private fun PclMsgFrame(
    title: String,
    modifier: Modifier = Modifier,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = pclPalette
    // 注意：进出动画由外层 [PclDialogContainer] 负责（它包着 [PclDialogHost]），
    // 这里只画 `MyMsg*` 的视觉外框，**不能**再套一层 AniHost —— 套两层等于把 Y/旋转变换叠加两遍。
    Box(
        modifier = modifier
            .padding(PclDimens.msgMargin)
            .widthIn(max = PclDimens.msgMaxWidth)
            // DropShadowEffect Color=ColorObject1 BlurRadius=20 ShadowDepth=4 Opacity=0.8
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(PclDimens.msgCorner),
                ambientColor = p.color1.copy(alpha = 0.8f),
                spotColor = p.color1.copy(alpha = 0.8f),
            )
            .clip(RoundedCornerShape(PclDimens.msgCorner))
            .background(MsgBackground),
    ) {
        // PanMain Margin="22,22,22,23"
        Column(
            modifier = Modifier.padding(
                start = PclDimens.msgPaddingStart,
                top = PclDimens.msgPaddingTop,
                end = PclDimens.msgPaddingEnd,
                bottom = PclDimens.msgPaddingBottom,
            ),
        ) {
            // Row0：LabTitle —— FontSize=23、ColorBrush2、Margin="7,-1,70,9"。
            // 源码里的 -1 上边距是 WPF 的字形微调，Compose 的 padding 不接受负值，故省略。
            Text(
                text = title,
                style = PclTextStyle.base(p).copy(
                    fontSize = PclDimens.msgTitleFontSize.sp,
                    color = p.color2,
                ),
                modifier = Modifier.padding(
                    start = PclDimens.msgTitleStart,
                    end = PclDimens.msgTitleEnd,
                    bottom = PclDimens.msgTitleBottom,
                ),
            )

            // Row1：ShapeLine —— Height=2，Fill 绑定标题的 Foreground，即 ColorBrush2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PclDimens.msgLineHeight)
                    .background(p.color2),
            )

            // Row2：行定义里固定的 Height="13" 间距
            Spacer(Modifier.height(PclDimens.msgLineGap))

            // Row3：正文（各对话框自己决定内容）
            content()

            // Row4：PanBtn —— 右对齐、Margin="150,0,8,0"、按钮间距 12
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = PclDimens.msgButtonEnd),
                horizontalArrangement = Arrangement.spacedBy(PclDimens.msgButtonGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}

/**
 * 对话框里的按钮：`MyButton Padding="5,0"` + `TextPadding="7"`。
 *
 * 两个内边距是**叠加**的，别只取一个：
 *   `Padding="5,0"`    → `MyButton.xaml.vb:52-59`，绑到内层 `PanFore.Padding`，左右各 5；
 *   `TextPadding="7"`  → `MyButton.xaml.vb:28-33`，绑到 `LabText.Padding`，文字四周各 7。
 * 所以横向留白 = 5 + 7 = **12**（普通 `MyButton` 是 13）。
 *
 * ⚠️ 曾经只写了 `Padding`，横向少掉那 7 —— 结果「确定」这种两字按钮被压成近正方形
 * （实测 36×35dp），而 PCL2 里是 52×34dp 的长方形。纵向那 7 由 `heightIn(min = 35dp)`
 * 兜住（PCL 的自然高 = 行高 18 + 7×2 + 边框 2 ≈ 34，两者基本一致），故不必再叠加。
 *
 * 对 `PclDialogs.kt` 之外开放，是为了让 [PclMsgInputDialog] 那种「按钮行由调用方给」的
 * 对话框也能用上完全一致的按钮内边距 —— 它内部渲染的就是这个函数。
 */
@Composable
internal fun RowScope.PclMsgButton(
    text: String,
    colorState: PclColorState = PclColorState.Normal,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    PclButton(
        text = text,
        enabled = enabled,
        colorState = colorState,
        contentPadding = PaddingValues(
            horizontal = PclDimens.msgButtonPaddingH + PclDimens.msgButtonTextPadding,
        ),
        onClick = onClick,
    )
}

/** 对话框正文文本。 */
@Composable
private fun PclMsgBody(text: String, modifier: Modifier = Modifier) {
    val p = pclPalette
    Text(
        text = text,
        style = PclTextStyle.base(p).copy(
            fontSize = PclDimens.msgBodyFontSize.sp,
            lineHeight = PclDimens.msgBodyLineHeight.sp,
            color = MsgBodyColor,
        ),
        modifier = modifier,
    )
}

/**
 * 输入弹窗（`MyMsgInput`）。确认时把当前输入内容交给 [onConfirm]。
 */
@Composable
fun PclInputDialog(
    title: String,
    label: String,
    initial: String = "",
    confirmText: String = MSG_BTN_OK,
    /** 密码框：显示成圆点。 */
    secret: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                // Btn1 = 肯定按钮，在最左；Btn2 = 取消
                PclMsgButton(text = confirmText, colorState = PclColorState.Highlight) {
                    onConfirm(text)
                    requestClose()
                }
                PclMsgButton(text = MSG_BTN_CANCEL, onClick = requestClose)
            },
        ) {
            // Row3：PanText（正文，Margin="0,0,0,7"）+ TextArea（Margin="7,0,7,12"）。
            // 源码给文本框定了 MinWidth=450，手机上放不下，这里改为撑满对话框可用宽度。
            PclMsgBody(
                text = label,
                modifier = Modifier.padding(
                    start = PclDimens.msgBodyStart,
                    end = PclDimens.msgBodyEnd,
                    bottom = PclDimens.msgBodyBottomLoose,
                ),
            )
            PclTextBox(
                value = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgInputStart,
                        top = 0.dp,
                        end = PclDimens.msgInputEnd,
                        bottom = PclDimens.msgInputBottom,
                    ),
                secret = secret,
                onValueChange = { text = it },
            )
        }
    }
}

/**
 * 单输入框 + 实时说明 + 确认校验（PCL 风格）。
 *
 * 与 [PclInputDialog] 的区别是「输入有语义」：输入过程中可以在下面显示实时说明
 * （[describe]，例如识别出来的是哪种联机邀请码），点确定时还能再校验一次
 * （[validate] 返回非空 → 显示红字并且**不关闭**）。
 *
 * 联机房客填邀请码就是这种场景 —— 原来的 `SimpleEditDialog` 是 Material 圆角框，
 * 在 PCL 界面里弹出来格格不入。
 */
@Composable
fun PclInputDialogValidate(
    title: String,
    label: String,
    initial: String = "",
    hint: String = "",
    confirmText: String = MSG_BTN_OK,
    /** 实时说明；返回 null / 空串表示不显示。 */
    describe: ((String) -> String?)? = null,
    /** 点确定时的校验；返回 null 表示通过，否则是要显示的错误文字（此时弹窗不关闭）。 */
    validate: (String) -> String? = { null },
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = pclPalette
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                PclMsgButton(text = confirmText, colorState = PclColorState.Highlight) {
                    val message = validate(text)
                    if (message == null) {
                        onConfirm(text)
                        requestClose()
                    } else {
                        error = message
                    }
                }
                PclMsgButton(text = MSG_BTN_CANCEL, onClick = requestClose)
            },
        ) {
            PclMsgBody(
                text = label,
                modifier = Modifier.padding(
                    start = PclDimens.msgBodyStart,
                    end = PclDimens.msgBodyEnd,
                    bottom = PclDimens.msgBodyBottomLoose,
                ),
            )
            PclTextBox(
                value = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgInputStart,
                        top = 0.dp,
                        end = PclDimens.msgInputEnd,
                        bottom = PclDimens.msgInputBottom,
                    ),
                hint = hint,
                onValueChange = {
                    text = it
                    // 用户开始改就先把上一次的红字收掉，别一边打一边看着旧错误
                    if (error != null) error = null
                },
            )
            val tip = error ?: describe?.invoke(text)?.takeIf { it.isNotBlank() }
            if (tip != null) {
                Text(
                    text = tip,
                    style = PclTextStyle.base(p).copy(
                        fontSize = 12.5.sp,
                        color = if (error != null) p.redDark else p.gray2,
                    ),
                    modifier = Modifier.padding(
                        start = PclDimens.msgInputStart,
                        end = PclDimens.msgInputEnd,
                        bottom = PclDimens.msgInputBottom,
                    ),
                )
            }
        }
    }
}

/**
 * 双输入框弹窗 —— `MyMsgInput` 的变体，一次收集两项内容。
 *
 * PCL CE 的「添加服务器」（`PageInstanceServer`）就是这个版式：先填服务器地址、再填名称。
 * 外框、正文、输入框全部复用 [PclMsgFrame] / [PclMsgBody] / [PclTextBox]，视觉上与
 * [PclInputDialog] 完全一致，只是中间多一组「标签 + 输入框」。
 *
 * @param hint1 / [hint2] 输入框的占位提示（PCL 的 `TextArea.HintText`）
 */
@Composable
fun PclDualInputDialog(
    title: String,
    label1: String,
    initial1: String = "",
    hint1: String = "",
    label2: String,
    initial2: String = "",
    hint2: String = "",
    confirmText: String = MSG_BTN_OK,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var first by remember { mutableStateOf(initial1) }
    var second by remember { mutableStateOf(initial2) }

    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                PclMsgButton(text = confirmText, colorState = PclColorState.Highlight) {
                    onConfirm(first, second)
                    requestClose()
                }
                PclMsgButton(text = MSG_BTN_CANCEL, onClick = requestClose)
            },
        ) {
            PclMsgBody(
                text = label1,
                modifier = Modifier.padding(
                    start = PclDimens.msgBodyStart,
                    end = PclDimens.msgBodyEnd,
                    bottom = PclDimens.msgBodyBottomLoose,
                ),
            )
            PclTextBox(
                value = first,
                hint = hint1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgInputStart,
                        top = 0.dp,
                        end = PclDimens.msgInputEnd,
                        bottom = 0.dp,
                    ),
                onValueChange = { first = it },
            )
            Spacer(Modifier.height(PclDimens.msgBodyBottomLoose))
            PclMsgBody(
                text = label2,
                modifier = Modifier.padding(
                    start = PclDimens.msgBodyStart,
                    end = PclDimens.msgBodyEnd,
                    bottom = PclDimens.msgBodyBottomLoose,
                ),
            )
            PclTextBox(
                value = second,
                hint = hint2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgInputStart,
                        top = 0.dp,
                        end = PclDimens.msgInputEnd,
                        bottom = PclDimens.msgInputBottom,
                    ),
                onValueChange = { second = it },
            )
        }
    }
}

/**
 * `MyMsgInput` 的另一种按钮布局：正文是若干行固定说明 + 一个输入框，**按钮行由调用方给**。
 *
 * 联机页的「加入大厅」要「清除 / 粘贴 / 加入」三个按钮，`PclInputDialog` 的
 * 「确定 / 取消」两个装不下，所以单独开一个。外框、正文、输入框的尺寸都复用同一套
 * `PclMsgFrame` / `PclMsgBody` / `PclTextBox`，视觉上与其它对话框完全一致，
 * 只是把 `PanBtn` 那一行开放出去。
 *
 * @param lines 正文各行。PCL 的 `MyMsgInput` 正文是一个整体，行与行之间靠换行；
 *   这里拆成列表是为了让「1、…」「2、…」这种分条说明各自成段，行距更好控制。
 */
@Composable
fun PclMsgInputDialog(
    title: String,
    lines: List<String>,
    value: String,
    hint: String = "",
    onValueChange: (String) -> Unit,
    buttons: @Composable RowScope.(requestClose: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(title = title, buttons = { buttons(requestClose) }) {
            // Row3：PanText（正文）+ TextArea（Margin="7,0,7,12"）
            lines.forEach { line ->
                PclMsgBody(
                    text = line,
                    modifier = Modifier.padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
                )
            }
            PclTextBox(
                value = value,
                hint = hint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgInputStart,
                        top = 0.dp,
                        end = PclDimens.msgInputEnd,
                        bottom = PclDimens.msgInputBottom,
                    ),
                onValueChange = onValueChange,
            )
        }
    }
}

/**
 * 确认弹窗（双按钮的 `MyMsgText`）。[danger] 为真时肯定按钮用红色（删除类操作），
 * 对应 `MyMsgText.xaml.vb:18` 的 `Btn1.ColorType = Red`。
 *
 * ⚠️【重要】点「确定」时是 **先调 [onConfirm]、紧接着再调 [onDismiss]**（两按钮共用同一条
 * 关窗路径）。因此**不要**在 [onConfirm] 里把一个状态切成「中间态」、再指望别处按那个状态
 * 去干活 —— 两次写入发生在同一帧，组合永远看不到中间态，对应的分支一次都不会执行。
 * 该做的事直接在 [onConfirm] 里做完（异步的话就 `scope.launch { ... }`）。
 */
@Composable
fun PclConfirmDialog(
    title: String,
    content: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = MSG_BTN_OK,
    danger: Boolean = false,
    /** 第二个按钮的文案，默认「取消」。上游 `SimpleAlertDialog` 有「忽略 / 刷新 / 关闭」这类自定义。 */
    dismissText: String = MSG_BTN_CANCEL,
    /** 点遮罩 / 返回键是否算取消。上游 `SimpleAlertDialog(dismissByDialog = false)` 的强制用法靠它。 */
    dismissOnOutsideClick: Boolean = true,
) {
    PclDialogContainer(onDismiss = onDismiss, dismissOnOutsideClick = dismissOnOutsideClick) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                PclMsgButton(
                    text = confirmText,
                    colorState = if (danger) PclColorState.Red else PclColorState.Highlight,
                ) {
                    onConfirm()
                    requestClose()
                }
                PclMsgButton(text = dismissText, onClick = requestClose)
            },
        ) {
            // Row3：PanCaption —— 容器 Padding="7,0,15,0"、Margin="0,0,0,17"
            PclMsgBody(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
            )
        }
    }
}

/**
 * 两个**自定义文案**按钮的弹窗（PCL 的 `MyMsgBox` 自定义 Button1 / Button2）。
 *
 * [PclConfirmDialog] 把两个按钮写死成「确定 / 取消」，凡是需要「忽略 / 授权」这类
 * 业务文案的地方（比如存储权限引导）就只能自己拼。源码里这类对话框本来就是
 * 调用方给两个按钮的文字，所以单独开一个通用版本。
 *
 * 声明顺序 = 界面顺序（[PclMsgFrame] 的按钮行从左往右排）：**主按钮放最后**，
 * 与 PCL「左边次要、右边主要」的排法一致。
 */
@Composable
fun PclActionDialog(
    title: String,
    content: String,
    secondaryText: String,
    onSecondary: () -> Unit,
    primaryText: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
) {
    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                PclMsgButton(text = secondaryText) {
                    onSecondary()
                    requestClose()
                }
                PclMsgButton(text = primaryText, colorState = PclColorState.Highlight) {
                    onPrimary()
                    requestClose()
                }
            },
        ) {
            PclMsgBody(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
            )
        }
    }
}

/**
 * 「三个确定」弹窗 —— 专供百宝箱的「千万别点」彩蛋。
 *
 * 这是 PCL2 那个彩蛋的**已知结构**：点「千万别点」之后弹出的对话框里并排三枚
 * **全部写着「确定」**的按钮，随便点哪个都会接着放恶搞画面
 * （见 [com.zalith.pcl.ui.pcl2.components.PclPrankOverlay]）。
 *
 * 三枚按钮刻意做成**完全一样**（都是 Normal 配色、都是「确定」），
 * 中间那枚不做高亮 —— 一高亮就等于给了「正确答案」，笑点就没了。
 */
@Composable
fun PclThreeOkDialog(
    title: String,
    content: String,
    onChosen: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                repeat(3) { index ->
                    PclMsgButton(text = MSG_BTN_OK) {
                        onChosen(index)
                        requestClose()
                    }
                }
            },
        ) {
            PclMsgBody(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
            )
        }
    }
}

/**
 * 单按钮的提示弹窗。源码里 Button2 为空时会被 `Visibility.Collapsed` 收起，
 * 且 `MyMsgText.xaml.vb:36` 只在「存在第二个按钮」时才把 Button1 变 Highlight，
 * 所以单按钮时应当保持 Normal 配色。
 *
 * 正文放在一个**限高 + 可滚动**的容器里（PCL 的 `MyMsgText` 正文本来就在 `ScrollViewer`
 * 里面）：启动器的错误框会附上整段安装日志，不限高的话按钮会被顶到屏幕外面去。
 */
@Composable
fun PclNoticeDialog(
    title: String,
    content: String,
    confirmText: String = MSG_BTN_OK,
    dismissOnOutsideClick: Boolean = true,
    onDismiss: () -> Unit,
) {
    PclDialogContainer(onDismiss = onDismiss, dismissOnOutsideClick = dismissOnOutsideClick) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                PclMsgButton(text = confirmText, onClick = requestClose)
            },
        ) {
            PclMsgBody(
                text = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    )
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * 单选项弹窗（`MyMsgSelect`）。条目是 `MyListItem Type=RadioBox MinHeight=24`，
 * 无 Logo 时图标列宽 4、标题下边距 2 —— 与 PclListItem 在 height ≥40 时的分支一致
 * （源码里 Height 未设置，WPF 中 `NaN < 40` 为 False，因此走的就是「高 ≥40」这一支）。
 *
 * [extraButtonText] / [onExtraButton] 是可选的**附加动作**（例如启动器更新里的
 * 「123 云盘」）：给了就在「取消」左边多画一个按钮，点它先执行动作再关框。
 * 默认不传 —— 其它调用点行为完全不变。
 */
@Composable
fun PclSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    current: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    extraButtonText: String? = null,
    onExtraButton: (() -> Unit)? = null,
) {
    val p = pclPalette
    val extraText = extraButtonText?.takeIf { it.isNotBlank() && onExtraButton != null }
    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = title,
            buttons = {
                if (extraText != null) {
                    PclMsgButton(text = extraText) {
                        onExtraButton?.invoke()
                        requestClose()
                    }
                }
                PclMsgButton(text = MSG_BTN_CANCEL, onClick = requestClose)
            },
        ) {
            // Row3：PanSelection（StackPanel）—— 容器 Padding="7,0,15,0"、Margin="0,0,0,17"
            //
            // 【为什么是 LazyColumn 而不是 Column + verticalScroll】
            // 这个弹窗最常用来选「模组加载器版本」，Fabric / Quilt 一个 MC 版本下能列出
            // 两三百条。Column 会把这三百个 `PclListItem` **一次性全部组合**：每一项自己
            // 都挂着按压缩放动画、悬停状态、颜色过渡和一条勾选竖条的 `LaunchedEffect`，
            // 于是点开弹窗的那一帧要干掉上千个状态对象 —— 低端机上主线程直接卡住好几秒，
            // 期间点任何地方都没反应（用户看到的就是「选了没反应 / 点了没动静」）。
            // LazyColumn 只组合可视区那几条，滚动时再逐条补，弹窗立刻就能响应。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // 条目多了就限高 + 滚动，别把对话框撑出屏幕（按钮必须始终看得见）
                    .heightIn(max = 260.dp)
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
            ) {
                if (options.isEmpty()) {
                    item {
                        Text(text = "没有可选项。", style = PclTextStyle.listInfo(p))
                    }
                } else {
                    items(options.size) { index ->
                        val (id, label) = options[index]
                        PclListItem(
                            title = label,
                            modifier = Modifier.fillMaxWidth(),
                            type = PclListItemType.RadioBox,
                            checked = id == current,
                            height = 40.dp,
                            onClick = {
                                onSelect(id)
                                requestClose()
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ============================================================================
 *  启动器更新（「关于」页的检查更新）
 * ==========================================================================*/

/**
 * 把更新说明的 Markdown 压成纯文本。
 *
 * PCL 的更新框正文是 `MyMsgText` 里的普通 `Text`，不认 Markdown 语法；
 * 直接塞原文的话用户会看到一堆 `**`、`###`、`` ` ``。这里只做去标记、不改变文字，
 * 保证 `latest_version_md.json` 里那份说明在弹窗里依然可读。
 */
internal fun pclMarkdownToPlain(md: String): String {
    var s = md
    s = s.replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")                        // 图片
    s = s.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")                     // 链接 → 文字
    s = s.replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")     // 标题井号
    s = s.replace(Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE), "")          // 引用
    s = s.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "· ")        // 无序列表
    s = s.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")                            // 粗体
    s = s.replace(Regex("__(.+?)__"), "$1")
    s = s.replace("`", "")
    return s.trim()
}

/**
 * 发现新版本（PCL 风格的 `MyMsgText`）。
 *
 * 上游的 `UpgradeDialog` 是 Material 的圆角卡片 —— PCL 界面里弹出来就是「不像 PCL」，
 * 所以这里用同一套 `PclMsgFrame` 重画：标题栏 + 版本号 + 说明正文（限高滚动）+ 按钮行。
 *
 * 按钮顺序按 PCL 的习惯**主要按钮在最左**：立即更新 / 忽略此版本 / 稍后。
 * 正文必须限高可滚动：更新说明有好几十行，不限高会把按钮顶到屏幕外。
 *
 * 官网配了「123 云盘」时（[cloudButtonText] 非空）会多出一个网盘按钮。四个按钮塞一行
 * 在窄屏上会把最左边的「立即更新」挤出屏幕 —— 那是主按钮，绝不能看不见 ——
 * 所以有网盘按钮时改成两行：上面「立即更新 / 网盘」，下面「忽略此版本 / 稍后」。
 */
@Composable
fun PclUpgradeDialog(
    versionText: String,
    bodyText: String,
    onDownload: () -> Unit,
    onIgnore: () -> Unit,
    onLater: () -> Unit,
    cloudButtonText: String? = null,
    onCloudButton: (() -> Unit)? = null,
) {
    val p = pclPalette
    val cloudText = cloudButtonText?.takeIf { it.isNotBlank() && onCloudButton != null }
    PclDialogContainer(onDismiss = onLater) { requestClose ->
        PclMsgFrame(
            title = "发现新版本",
            buttons = {
                if (cloudText == null) {
                    PclMsgButton(text = "立即更新", colorState = PclColorState.Highlight) {
                        onDownload()
                        requestClose()
                    }
                    PclMsgButton(text = "忽略此版本") {
                        onIgnore()
                        requestClose()
                    }
                    PclMsgButton(text = "稍后", onClick = requestClose)
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(PclDimens.msgButtonGap),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PclDimens.msgButtonGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PclMsgButton(text = "立即更新", colorState = PclColorState.Highlight) {
                                onDownload()
                                requestClose()
                            }
                            PclMsgButton(text = cloudText) {
                                onCloudButton?.invoke()
                                requestClose()
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(PclDimens.msgButtonGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PclMsgButton(text = "忽略此版本") {
                                onIgnore()
                                requestClose()
                            }
                            PclMsgButton(text = "稍后", onClick = requestClose)
                        }
                    }
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    )
                    .heightIn(max = 250.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = versionText,
                    style = PclTextStyle.base(p).copy(
                        fontSize = PclDimens.msgBodyFontSize.sp,
                        color = p.color2,
                    ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = bodyText,
                    style = PclTextStyle.base(p).copy(
                        fontSize = PclDimens.msgBodyFontSize.sp,
                        lineHeight = PclDimens.msgBodyLineHeight.sp,
                        color = MsgBodyColor,
                    ),
                )
            }
        }
    }
}

/**
 * 选择要下载哪一个安装包（多架构）。
 *
 * 直接复用 [PclSelectDialog]（`MyMsgSelect`），但**列表里只写版本与架构** ——
 * 之前这里把 `fileName to uri` 顺序传反了，弹窗里列出来的是五条一模一样的
 * `https://github.com/...`，选中后拿到的还是文件名，于是「选完什么都没发生」。
 *
 * 默认选中当前设备架构那一条（[current] 传文件名，PclSelectDialog 会打上勾）。
 */
@Composable
fun PclUpgradeFilesDialog(
    title: String,
    options: List<Pair<String, String>>,
    current: String?,
    onSelect: (fileName: String) -> Unit,
    onDismiss: () -> Unit,
    extraButtonText: String? = null,
    onExtraButton: (() -> Unit)? = null,
) {
    PclSelectDialog(
        title = title,
        options = options,
        current = current,
        onSelect = onSelect,
        onDismiss = onDismiss,
        extraButtonText = extraButtonText,
        onExtraButton = onExtraButton,
    )
}

/**
 * 正在下载启动器安装包（PCL 风格的 `MyMsgProgress`）。
 *
 * 进度条用 [PclProgressBar]（4dp、圆角 2、主题色），下面是「已下载 / 总大小 · 速度」，
 * 与游戏资源下载页的观感一致。总大小未知时 `progress < 0`，进度条停在 0 不动。
 */
@Composable
fun PclUpgradeDownloadDialog(
    title: String,
    versionText: String,
    detailText: String,
    progress: Float,
    onCancel: () -> Unit,
) {
    val p = pclPalette
    PclDialogContainer(
        // 这个弹窗完全由 `PclLauncherUpdate.state` 驱动：状态一变回 Idle，
        // 宿主那边就不渲染它了，所以这里没有需要清理的调用方状态。
        onDismiss = {},
        // 按返回键 = 取消下载。**只改状态、不走退出动画**。
        // 以前走的是默认的 requestClose()：退出动画把 alpha 淡到 0，而 onDismiss 是空 lambda，
        // 没人把窗口摘掉 —— 剩下一个透明却吞掉全部触摸的窗口，就是「按返回后点什么都没反应」。
        dismissHandler = { onCancel() },
    ) { _ ->
        PclMsgFrame(
            title = title,
            buttons = {
                // 同理：直接取消，不 requestClose。取消导致 state 变 Idle，弹窗自然消失。
                PclMsgButton(text = "取消下载", onClick = onCancel)
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    ),
            ) {
                Text(
                    text = versionText,
                    style = PclTextStyle.base(p).copy(
                        fontSize = PclDimens.msgBodyFontSize.sp,
                        color = MsgBodyColor,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                PclProgressBar(progress = progress.coerceAtLeast(0f))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = detailText,
                    style = PclTextStyle.base(p).copy(
                        fontSize = PclDimens.msgBodyFontSize.sp,
                        color = p.color2,
                    ),
                )
            }
        }
    }
}

/* ============================================================================
 *  渲染器配置（新一代渲染器插件的环境变量）
 * ==========================================================================*/

/**
 * 「渲染器配置」弹窗（PCL 风格）。
 *
 * 原生界面在「设置 → 渲染设置 → 全局渲染器」右侧有一枚齿轮，点开就是这个配置
 * （`RendererV2ConfigDialog`）。PCL 界面此前没有入口 —— 用户装了 MobileGlues 这类
 * 新一代渲染器插件后，MOBILEGL_BACKEND_TYPE 之类的开关在 PCL 界面里完全够不着。
 *
 * 三类配置项按插件给的 [EnvSettingUnit] 原样渲染：
 *   · `Selectable`   —— 可选勾选 + 下拉选值（`MOBILEGL_BACKEND_TYPE`）
 *   · `Toggleable`   —— 开关（`MOBILEGL_DISABLE_TIMERQUERY`，开 = 写入 envValue）
 *   · `Customizable` —— 文本框（自由填写）
 * 变量名用等宽思路单行显示、超长省略；摘要走灰色小字，和 PCL 的说明行一致。
 */
@Composable
fun PclRendererConfigDialog(
    units: List<EnvSettingUnit>,
    onDismiss: () -> Unit,
) {
    val p = pclPalette
    PclDialogContainer(onDismiss = onDismiss) { requestClose ->
        PclMsgFrame(
            title = "渲染器配置",
            buttons = {
                PclMsgButton(text = "关闭", onClick = requestClose)
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = PclDimens.msgBodyStart,
                        end = PclDimens.msgBodyEnd,
                        bottom = PclDimens.msgBodyBottomTight,
                    )
                    // 配置项可能很多，限高滚动，别把「关闭」按钮顶出屏幕
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (units.isEmpty()) {
                    Text(
                        text = "当前渲染器没有可配置的环境变量。",
                        style = PclTextStyle.base(p).copy(
                            fontSize = PclDimens.msgBodyFontSize.sp,
                            color = MsgBodyColor,
                        ),
                    )
                }
                units.forEachIndexed { index, unit ->
                    if (index > 0) {
                        Spacer(Modifier.height(14.dp))
                        // 分隔线：PCL 里同一张卡片内的条目之间就靠一条淡线分开
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(p.color6),
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    when (unit) {
                        is EnvSettingUnit.Selectable -> PclSelectableEnvItem(unit)
                        is EnvSettingUnit.Toggleable -> PclToggleableEnvItem(unit)
                        is EnvSettingUnit.Customizable -> PclCustomizableEnvItem(unit)
                    }
                }
            }
        }
    }
}

/** 环境变量名：`环境变量：MOBILEGL_BACKEND_TYPE`。 */
@Composable
private fun PclEnvKeyText(key: String) {
    val p = pclPalette
    Text(
        text = "环境变量：$key",
        style = PclTextStyle.base(p).copy(
            fontSize = PclDimens.msgBodyFontSize.sp,
            color = p.color1,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 插件给的说明（灰色小字）。 */
@Composable
private fun PclEnvSummaryText(summary: String?) {
    if (summary.isNullOrBlank()) return
    val p = pclPalette
    Spacer(Modifier.height(3.dp))
    Text(
        text = summary,
        style = PclTextStyle.base(p).copy(
            fontSize = 12.5.sp,
            color = p.gray2,
        ),
    )
}

@Composable
private fun PclSelectableEnvItem(unit: EnvSettingUnit.Selectable) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (unit.rawEnv.check != null) {
            // 插件声明了 check：可以整体关掉这条环境变量
            PclCheckBox(
                text = "环境变量：${unit.rawEnv.key}",
                checked = unit.isEnabled,
                modifier = Modifier.fillMaxWidth(),
                onCheckedChange = { unit.saveCheck(it) },
            )
        } else {
            PclEnvKeyText(unit.rawEnv.key)
        }
        PclEnvSummaryText(unit.summary)
        Spacer(Modifier.height(6.dp))
        PclComboBox(
            items = unit.values,
            selected = unit.state,
            // 关掉的时候下拉置灰，避免「关了还能改」的困惑
            enabled = unit.isEnabled,
            modifier = Modifier.fillMaxWidth(),
            label = { it.orEmpty() },
            onSelect = { if (it != unit.state) unit.save(it) },
        )
    }
}

@Composable
private fun PclToggleableEnvItem(unit: EnvSettingUnit.Toggleable) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PclSwitchItem(
            title = "环境变量：${unit.rawEnv.key}",
            checked = unit.isEnabled,
            description = unit.summary,
            modifier = Modifier.fillMaxWidth(),
            onCheckedChange = { checked -> unit.save(if (checked) unit.envValue else "") },
        )
    }
}

@Composable
private fun PclCustomizableEnvItem(unit: EnvSettingUnit.Customizable) {
    var text by remember(unit) { mutableStateOf(unit.state) }
    Column(modifier = Modifier.fillMaxWidth()) {
        PclEnvKeyText(unit.rawEnv.key)
        PclEnvSummaryText(unit.summary)
        Spacer(Modifier.height(6.dp))
        PclTextBox(
            value = text,
            modifier = Modifier.fillMaxWidth(),
            hint = unit.defaultValue.ifEmpty { "留空表示不设置" },
            onValueChange = {
                text = it
                unit.save(it)
            },
        )
    }
}
