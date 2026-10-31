/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 首页（启动页）—— 对应 PCL2 的：
 *   Pages/PageLaunch/PageLaunchLeft.xaml   + .vb
 *   Pages/PageLaunch/PageLaunchRight.xaml  + .vb
 *
 * 布局分支（PanInput / PanLaunching）、控件尺寸、边距、配色与文案均按原文件还原。
 * 数据全部来自 Zalith 的真实后端：已安装版本列表、当前版本、当前账户、启动流程与日志。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import com.zalith.pcl.game.account.wardrobe.validateSkinFile
import com.zalith.pcl.ui.pcl2.data.PclLoginSettings
import com.zalith.pcl.ui.pcl2.data.isThirdParty
import com.zalith.pcl.ui.pcl2.data.loginTypeValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.core.graphics.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zalith.pcl.BuildKeys
import com.zalith.pcl.coroutine.TaskStage
import com.zalith.pcl.coroutine.TitledTask
import com.zalith.pcl.game.account.Account
import com.zalith.pcl.game.account.AccountsManager
import com.zalith.pcl.game.account.getAccountTypeName
import com.zalith.pcl.game.account.isLocalAccount
import com.zalith.pcl.game.account.isMicrosoftAccount
import com.zalith.pcl.game.account.isMicrosoftLogging
import com.zalith.pcl.game.account.microsoftLogin
import com.zalith.pcl.game.account.wardrobe.SkinModelType
import com.zalith.pcl.game.account.wardrobe.getLocalUUIDWithSkinModel
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionLoginType
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclComboBoxItemRow
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclComboBoxPopup
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclLoadingPickaxe
import com.zalith.pcl.ui.pcl2.components.PclInlineHtml
import com.zalith.pcl.ui.pcl2.components.PclInlineWeb
import com.zalith.pcl.ui.pcl2.components.PclInputDialog
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclProgressBar
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextButton
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.data.PclAuthLoginSettings
import com.zalith.pcl.ui.pcl2.data.belongsToAuthServer
import com.zalith.pcl.ui.pcl2.data.loginBaseUrl
import com.zalith.pcl.ui.pcl2.data.loginServerTitle
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.data.PclAppearance
import com.zalith.pcl.ui.pcl2.data.PclHomeEntry
import com.zalith.pcl.ui.pcl2.data.PclHomeLoader
import com.zalith.pcl.ui.pcl2.data.PclHomePresetEntry
import com.zalith.pcl.ui.pcl2.data.PclHomePresets
import com.zalith.pcl.ui.pcl2.data.rememberRemoteText
import com.zalith.pcl.ui.pcl2.data.PclHomeType
import com.zalith.pcl.ui.pcl2.data.PclRuntime
import com.zalith.pcl.ui.pcl2.pages.pclInstanceVersionPage
import com.zalith.pcl.ui.pcl2.pages.pclVersionSelectPage
import com.zalith.pcl.ui.pcl2.theme.PclMotion
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.ui.toAndroidString
import com.zalith.pcl.utils.network.openLinkInternal
import kotlin.math.roundToInt
import java.io.File
import com.zalith.pcl.ui.pcl2.theme.pclHairline

/** 日志卡片最多显示的行数（避免超长日志拖慢界面）。 */
private const val HOME_LOG_LINES = 120

// ---------------------------------------------------------------- 左侧面板

/**
 * `PageLaunchLeft` —— 首页左栏。
 *
 * PCL 里这里有两个互斥分支：
 *  - `PanInput`     未启动：登录方式胶囊 + 皮肤 + 启动按钮 + 版本按钮
 *  - `PanLaunching` 启动中：启动进度与取消按钮
 */
@Composable
fun PclHomeLeft(nav: PclNavController) {
    val runtime = LocalPclRuntime.current
    // PCL 的 PageLaunchLeft 没有统一边距：各区块自带 Margin（PanTypeOne/PanLogin/BtnLaunch 为 20，
    // BtnVersion/BtnMore 左侧 20、右侧 10），因此这里不再套一层统一 padding。
    //
    // PanInput 与 PanLaunching 的切换：PCL 里两个 Grid 各自 Visibility + Opacity 切换，
    // 其中 PanLaunching 从 ScaleTransform(0.8) / Opacity 0 进入（见 PclHomeLaunching 内部）。
    Crossfade(
        targetState = runtime.isLaunching,
        animationSpec = tween(150, easing = PclMotion.easeOutFluent),
        label = "pclHomePan",
    ) { launching ->
        Column(modifier = Modifier.fillMaxHeight()) {
            if (launching) {
                PclHomeLaunching(runtime)
            } else {
                PclHomeInput(nav, runtime)
            }
        }
    }
}

/**
 * `PanLaunching` —— 启动中的进度面板（照 `PageLaunchLeft.xaml:73-165` 还原）。
 *
 * PCL 这里是一屏居中的「启动卡片」：手绘上升箭头（`MyLoading`）→ 标题（正在启动游戏 /
 * 已启动游戏）→ 版本名 → 蓝色渐变进度条 → 两列信息（当前步骤 / 登录方式）→
 * 「你知道吗」小提示框 → 底部「取消」按钮。整块从 `Scale 0.8 / Opacity 0` 进场。
 */
@Composable
private fun ColumnScope.PclHomeLaunching(runtime: PclRuntime) {
    val p = pclPalette
    val context = LocalContext.current
    val tasks by runtime.launchTasks.collectAsStateWithLifecycle()
    val version by runtime.currentVersion.collectAsStateWithLifecycle()
    val account by runtime.currentAccount.collectAsStateWithLifecycle()

    // 进场动画：PCL 的 PanLaunching 初始 RenderTransform = ScaleTransform(0.8)、Opacity = 0
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enterScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.8f,
        animationSpec = tween(400, easing = PclMotion.easeOutBack),
        label = "pclLaunchScale",
    )
    val enterAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(100, easing = PclMotion.easeOutFluent),
        label = "pclLaunchAlpha",
    )

    // 当前阶段 = 第一个还没完成的子任务；全部完成就是「已启动游戏」。
    val total = tasks.size
    val finishedCount = tasks.count { it.task.stage.value == TaskStage.COMPLETED }
    val allDone = total > 0 && finishedCount == total
    val current = tasks.firstOrNull { it.task.stage.value != TaskStage.COMPLETED }
    val stageName = current?.title?.toAndroidString(context)
        ?: if (allDone) "等待游戏窗口出现" else PclStrings.LangLaunchLeftInit
    val rawProgress = if (total == 0) 0f else finishedCount.toFloat() / total
    val progress by animateFloatAsState(
        targetValue = rawProgress.coerceIn(0f, 1f),
        animationSpec = tween(300, easing = PclMotion.easeOutFluent),
        label = "pclLaunchProgress",
    )
    val method = if (account?.isMicrosoftAccount() == true) "正版验证" else "离线验证"
    // 每次开始启动随机一条小提示（PCL 的 LabLaunchingHint 从提示库里挑）；优先联网抓官网审核文案。
    val tip = rememberRemoteText("hint")

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(horizontal = 20.dp)
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
                alpha = enterAlpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // PCL 的 MyLoading：镐子敲地（与下载界面共用同一份实现）
        PclLoadingPickaxe()

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (allDone) PclStrings.LangLaunchLeftStarted else PclStrings.LangLaunchLeftLaunchingTitle,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = p.color3,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = version?.getVersionName().orEmpty(),
            fontSize = 13.5.sp,
            color = p.color3,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        // 进度条：左段 ColorBrush4 → ColorBrush3 渐变，右段 ColorBrush6 半透明
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .height(4.dp),
        ) {
            Box(
                Modifier
                    .weight(progress.coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(p.color4, p.color3))),
            )
            Box(
                Modifier
                    .weight((1f - progress).coerceAtLeast(0.001f))
                    .fillMaxHeight()
                    .background(p.color6.copy(alpha = 0.6f)),
            )
        }

        // 两列信息（PCL 的 PanLaunchingInfo）
        Spacer(Modifier.height(22.dp))
        LaunchInfoRow(PclStrings.LangLaunchLeftStartingStage, stageName)
        Spacer(Modifier.height(4.dp))
        LaunchInfoRow(PclStrings.LangLaunchLeftStageLogin, method)

        // 「你知道吗」小提示框（灰边框、圆角、标题在框顶）
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .border(1.dp, p.gray5.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
                .padding(horizontal = 11.dp, vertical = 9.dp),
        ) {
            Text(
                text = PclStrings.LangLaunchLeftTip,
                fontSize = 12.5.sp,
                color = p.gray2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            // 内置提示文案已删：官网没给内容时不留空行
            if (tip.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = tip,
                    fontSize = 12.5.sp,
                    color = p.color1,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 底部「取消」（PCL 的 BtnCancel：白底细边框）
        PclButton(
            text = "取消",
            modifier = Modifier.fillMaxWidth().height(35.dp),
            onClick = { runtime.cancelLaunch() },
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** 启动信息的一行：左边灰色标签，右边正文（PCL 的 PanLaunchingInfo 两列）。 */
@Composable
private fun LaunchInfoRow(label: String, value: String) {
    val p = pclPalette
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = p.gray3,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(15.dp))
        Text(
            text = value,
            fontSize = 12.5.sp,
            color = p.color1,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 单个启动阶段的进度行。 */
@Composable
private fun PclLaunchTaskRow(titled: TitledTask, context: android.content.Context) {
    val p = pclPalette
    val progress by titled.task.progress.collectAsState()
    val message by titled.task.message.collectAsState()
    val speed by titled.task.rateBytesPerSec.collectAsState()
    val stage by titled.task.stage.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = titled.title.toAndroidString(context),
                style = PclTextStyle.listTitle(p),
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val speedValue = speed
            if (speedValue != null && speedValue > 0) {
                Text(text = formatSpeed(speedValue), style = PclTextStyle.listInfo(p))
            }
        }
        val msg = message?.toAndroidString(context)
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                style = PclTextStyle.listInfo(p),
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        if (progress >= 0f) {
            PclProgressBar(progress = progress)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(p.color6),
            )
        }
        if (stage == TaskStage.COMPLETED) {
            Text(
                text = "已完成",
                style = PclTextStyle.listInfo(p).copy(color = p.color3),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** `PanInput` —— 未启动时的首页左栏。 */
@Composable
private fun ColumnScope.PclHomeInput(nav: PclNavController, runtime: PclRuntime) {
    val p = pclPalette
    val version by runtime.currentVersion.collectAsStateWithLifecycle()
    val accountState by runtime.currentAccount.collectAsStateWithLifecycle()
    val account = accountState
    val accounts by runtime.accounts.collectAsStateWithLifecycle()

    // ---- 登录方式：跟着**当前版本的「版本设置 → 服务器 → 登录方式」**走
    //
    // 这是用户明确要的口径：版本选了「仅正版登录」，右栏就只剩正版这一种，离线 / 外置
    // 根本不出现；选了第三方（统一通行证 / Authlib），整块换成该服务器的登录面板，
    // 版式见 [PclAuthLoginPanel]。
    val loginType = version?.getVersionConfig()?.loginTypeValue()
        ?: VersionLoginType.MICROSOFT_OR_OFFLINE
    val thirdParty = loginType.isThirdParty()
    val showMicrosoftPill = !thirdParty && loginType != VersionLoginType.OFFLINE_ONLY
    val showOfflinePill = !thirdParty && loginType != VersionLoginType.MICROSOFT_ONLY
    val authBaseUrl = remember(version) { version?.getVersionConfig()?.loginBaseUrl().orEmpty() }
    val authServerName = remember(version) { version?.getVersionConfig()?.loginServerTitle().orEmpty() }
    val authRegisterUrl = remember(version) { version?.getVersionConfig()?.loginRegisterUrl.orEmpty() }
    // PCL 的 PanType 是「正版 / 离线」两枚可切换胶囊（LoginPageType 决定显示哪一块登录页）。
    // 这里多一态「外置登录」，并且**由版本配置决定哪几态可用**。
    var loginPage by remember { mutableStateOf(HomeLoginPage.Microsoft) }
    LaunchedEffect(loginType, authBaseUrl, account?.uniqueUUID) {
        loginPage = when {
            thirdParty -> HomeLoginPage.Auth
            loginType == VersionLoginType.MICROSOFT_ONLY -> HomeLoginPage.Microsoft
            loginType == VersionLoginType.OFFLINE_ONLY -> HomeLoginPage.Offline
            account?.isMicrosoftAccount() == true -> HomeLoginPage.Microsoft
            account?.isLocalAccount() == true -> HomeLoginPage.Offline
            else -> loginPage
        }
    }

    // 离线页输入框的内容（PCL 的 ComboName）：跟随当前的离线账户
    val context = LocalContext.current
    var offlineName by remember { mutableStateOf(PclLoginSettings.offlineName) }
    LaunchedEffect(account?.uniqueUUID) {
        account?.takeIf { it.isLocalAccount() }?.let {
            offlineName = it.username
            PclLoginSettings.applyOfflineName(it.username)
            PclLoginSettings.persist(context)
        }
    }

    // ---- PanType：登录方式胶囊（PageLaunchLeft.xaml:43-57）
    //      外层 Margin="20,22,20,0"，各 MyRadioButton 各 Margin="4,0"，居中排列。
    //
    // 第三方登录不画胶囊：那一块的面板自己有「XXX 登录」标题（PCL 的 `PageLoginAuth`
    // 也是这样），再挂一粒写着「外置登录」的胶囊只是重复。
    if (thirdParty) {
        // 第三方：PCL 不画可点胶囊，改画一枚静态的 `PanTypeOne`（图标 + 认证服务器名）
        PclLoginTypeCapsule(name = authServerName)
    } else {
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showMicrosoftPill) {
                PclPillButton(
                    text = PclStrings.LangLaunchLeftAccountMicrosoft,
                    logo = PclIcons.loginMicrosoft,
                    logoScale = 1f,
                    highlight = true,
                    checked = loginPage == HomeLoginPage.Microsoft,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    onClick = {
                        loginPage = HomeLoginPage.Microsoft
                        // 当前账户不是正版时顺手切到已有的正版账户；没有就留在本页做登录
                        if (account?.isMicrosoftAccount() != true) {
                            accounts.firstOrNull { it.isMicrosoftAccount() }?.let(runtime::switchAccount)
                        }
                    },
                )
            }
            if (showOfflinePill) {
                PclPillButton(
                    text = PclStrings.LangLaunchLeftAccountOffline,
                    logo = PclIcons.loginOffline,
                    logoScale = 0.85f,
                    highlight = true,
                    checked = loginPage == HomeLoginPage.Offline,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    onClick = {
                        loginPage = HomeLoginPage.Offline
                        if (account?.isLocalAccount() != true) {
                            accounts.firstOrNull { it.isLocalAccount() }?.let(runtime::switchAccount)
                        }
                    },
                )
            }
        }
    }

    // ---- PanLogin：登录面板（未登录正版 / 已登录正版 / 离线）
    //      PCL 里是一块 `Margin="20,0"` 的居中区域，内容由登录方式子页面填充。
    //      离线页的输入框内容放在这一层持有：点「启动游戏」时若名字还没落库，
    //      需要先建账户、建完再启动（见下面的 onClick）。
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (loginPage) {
            HomeLoginPage.Microsoft ->
                PclMicrosoftLoginPanel(runtime = runtime, account = account, accounts = accounts)

            HomeLoginPage.Offline -> PclOfflineLoginPanel(
                runtime = runtime,
                account = account,
                accounts = accounts,
                name = offlineName,
                onNameChange = {
                    offlineName = it
                    // PCL 的 ComboName 会记住输入过的名字；这里存下来，重启后还在
                    PclLoginSettings.applyOfflineName(it)
                    PclLoginSettings.persist(context)
                },
            )

            HomeLoginPage.Auth -> PclAuthLoginPanel(
                serverName = authServerName,
                baseUrl = authBaseUrl,
                registerUrl = authRegisterUrl,
                account = account,
                accounts = accounts,
            )
        }
    }
    
    // ---- 启动按钮 + 版本提示
    // PCL 结构：Grid 里 BtnLaunch（Height=54、Margin="20,0"、Padding="30,0,30,15"）
    // 与叠在其底部的 LabVersion（Margin="35,0,35,10"、FontSize=11、Foreground=ColorBrushGray3）。
    // 即版本提示是「画在大按钮内部的下沿」，而不是按钮下方的独立一行。
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(54.dp)) {
        PclButton(
            text = PclStrings.LangLaunchLeftStartInstance,
            enabled = version != null,
            colorState = PclColorState.Highlight,
            height = 54.dp,
            contentPadding = PaddingValues(start = 30.dp, end = 30.dp, bottom = 15.dp),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val name = offlineName.trim()
                val exists = accounts.any {
                    it.isLocalAccount() && it.username.equals(name, ignoreCase = true)
                }
                // 该版本设置了第三方登录时，启动链路会自己去解析 / 登录对应服务器的账户，
                // 这里别依照输入框里的名字顺手建一个离线账户（会凭空多出一个账户）
                if (loginPage == HomeLoginPage.Offline && !thirdParty && name.isNotEmpty() && !exists) {
                    // 名字只在输入框里、还没落库：先把账户建出来再启动
                    runtime.createLocalAccount(name) { runtime.tryLaunch() }
                } else {
                    runtime.tryLaunch()
                }
            },
        )
        Text(
            text = versionHint(version),
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = p.gray3),
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 35.dp, end = 35.dp, bottom = 10.dp),
        )
    }

    // ---- 版本选择 / 版本设置（BtnVersion / BtnMore）：Height=35、Margin="0,10,10,0"
    //
    // PCL 的 PanInput 是 5 列网格：`Auto | 20 | 1* | 1* | 10`。
    //   · 可用宽度 = 面板宽 − 20(列1) − 10(列4) = W − 30，两个 1* 各占 (W−30)/2；
    //   · BtnVersion 在列 2、BtnMore 在列 3，两者都带右边距 10；
    //   · 于是 BtnVersion = [20, 20+(W−30)/2−10]，BtnMore 的右边缘 = W − 20。
    // 而 BtnLaunch 是 `Margin="20,0"` → 也是 [20, W−20]。
    // 换成 Row 表达就是：整行 [20, W−20]、中间间隔 10、两枚各 (W−40−10)/2 = (W−50)/2，
    // 与上式完全等价 —— 右边缘必须和启动按钮对齐（之前写成 end=10，右边多出 10）。
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PclButton(
            text = PclStrings.LangLaunchLeftInstanceChoose,
            modifier = Modifier.weight(1f),
            onClick = { nav.push(pclVersionSelectPage()) },
        )
        PclButton(
            text = PclStrings.LangLaunchLeftInstanceConfig,
            modifier = Modifier.weight(1f),
            enabled = version != null,
            onClick = { nav.push(pclInstanceVersionPage()) },
        )
    }
    Spacer(Modifier.height(20.dp))
}

/** 启动按钮下方的版本提示（PCL 的 `LabVersion`）。 */
private fun versionHint(version: Version?): String {
    if (version == null) return "尚未安装任何游戏版本"
    val info = version.getVersionInfo()?.getInfoString()
    return if (info.isNullOrBlank()) version.getVersionName() else "$info（${version.getVersionName()}）"
}

// ---------------------------------------------------------------- 登录面板

/** 微软账号相关的三个外链（`PageLoginMs.xaml` / `PageLoginMsSkin.xaml` 原文）。 */
private const val URL_MS_BUY = "https://www.xbox.com/games/store/minecraft-java-bedrock-edition-for-pc/9nxp44l49shj"
private const val URL_MS_SITE = "https://www.minecraft.net"
private const val URL_MS_PROFILE = "https://www.minecraft.net/zh-hans/msaprofile/mygames/editprofile"
private const val URL_MS_PASSWORD = "https://account.live.com/password/Change"

/**
 * 启动真实的微软设备码登录（`game/account/AccountUtils.kt:microsoftLogin`）。
 *
 * 设备码流程不依赖内置网页页：验证码会复制到剪贴板并弹提示，验证地址交给
 * **启动器内置的浏览器层**（`PclWebOverlay`）打开，应用侧持续轮询直到拿到令牌。
 *
 * `checkIfInWebScreen` 跟着「内置浏览器是否还开着」走 —— 用户关掉那一层就当作
 * 放弃登录（`getTokenResponse` 里连续两次返回「已退出」会中止轮询），这与 Zalith
 * 原生界面 `currentKey is NormalNavKey.WebScreen` 的判断口径一致。
 */
private fun startMicrosoftLogin(runtime: PclRuntime, context: android.content.Context) {
    // 本仓库不内置微软应用的 client id（见 MicrosoftAuthenticator.fetchDeviceCodeResponse）。
    // 没配置就直说，别让用户对着「登录进行中…」等一个注定 400 的请求。
    if (BuildKeys.OAUTH_CLIENT_ID.isBlank()) {
        PclHintHost.push(
            "未配置正版登录 Client ID，无法登录正版账户（离线登录不受影响）。",
            PclHintTheme.Red,
        )
        return
    }
    if (isMicrosoftLogging()) {
        PclHintHost.push("正版登录正在进行中，请在启动器内的验证页面完成登录。", PclHintTheme.Yellow)
        return
    }
    microsoftLogin(
        context = runtime.activity,
        toWeb = { url ->
            // 微软的设备码登录页在 WebView 里经常在授权完成后停在白屏，刷新还会把
            // 一次性会话刷成「我们无法满足你的需求」。设备码流程本身与浏览器无关
            // （应用在后台轮询令牌），所以优先交给系统浏览器；没有浏览器才退回内置页。
            val opened = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            }.getOrDefault(false)
            if (opened) {
                PclHintHost.push(
                    "验证码已复制。请在浏览器中完成登录，授权后回到启动器即可，无需手动关闭页面。",
                    PclHintTheme.Blue,
                )
            } else {
                runtime.openWebPage(url, "Microsoft 账户登录")
                PclHintHost.push("没有可用的浏览器，已改用启动器内登录页。", PclHintTheme.Yellow)
            }
        },
        backToMain = { runtime.closeWebPage() },
        // 关闭页面不再等同于放弃登录：设备码已经发出，用户很可能是在微软的
        // 「已完成」白屏上点了关闭，此时令牌轮询还在进行，关页就取消会把一次
        // 已经成功的授权直接作废。真正的取消交给任务管理器。
        checkIfInWebScreen = { true },
        updateOperation = { },
        showToast = { text, _ ->
            PclHintHost.push(text.toAndroidString(context), PclHintTheme.Blue)
        },
        submitError = { th ->
            runtime.submitErrorText("登录失败", th.message.toAndroidString(context))
        },
        onSuccess = { PclHintHost.push("正版账户登录成功。", PclHintTheme.Green) },
    )
}

/** 首页头像的默认皮肤资源（原样取自 PCL2 的 `images/Skins/`）。 */
private const val PCL_SKIN_STEVE = "pcl_skin_steve.png"
private const val PCL_SKIN_ALEX = "pcl_skin_alex.png"

/** PCL 的 MySkin 是 64 的外框装 56 的脸图（`MySkin.xaml` 里 ImgFore 56 / ImgBack 48）。 */
private const val PCL_SKIN_FACE_RATIO = 0.875f

/** 与 Zalith `AccountElements.getSkinAvatar` 一致：关闭位图过滤，保留像素风。 */
private val pclHeadPaint = Paint().apply { isFilterBitmap = false }

/**
 * 按 UUID 判定默认皮肤模型。
 *
 * 规则与 PCL 的 `ModMinecraft.McSkinSex` 逐字一致：取 UUID 第 8/16/24/32 个十六进制位
 * （即四个分组各自的末位）做异或，偶数 → 粗臂 Steve、奇数 → 细臂 Alex。
 * Zalith 自己的 `getLocalUUIDWithSkinModel` 也用同一套奇偶约定（`SkinModelType.targetParity`）。
 */
private fun pclDefaultSkinModel(uuid: String): SkinModelType {
    val value = uuid.replace("-", "")
    if (value.length != 32) return SkinModelType.STEVE
    val nibbles = listOf(7, 15, 23, 31).map { Character.digit(value[it], 16) }
    if (nibbles.any { it < 0 }) return SkinModelType.STEVE
    return if (nibbles.reduce { a, b -> a xor b } % 2 == 1) SkinModelType.ALEX else SkinModelType.STEVE
}

/**
 * 从皮肤贴图裁出头像（脸层 + 头发层）。
 *
 * 几何与 Zalith `AccountElements.getSkinAvatar` 完全一致：脸层取 (8,8,8,8) 并内缩约 1/18，
 * 头发层取 (40,8,8,8) 覆盖整块，因此看起来比脸略大（PCL 那边是 48/56 的效果）。
 */
private fun cropSkinHead(skin: Bitmap, pixelSize: Int): Bitmap {
    val faceOffset = (pixelSize / 18.0).roundToInt().toFloat()
    val scaleFactor = skin.width / 64.0f
    val faceSize = (8 * scaleFactor).roundToInt()
    val faceEndY = faceSize * 2
    val hatSrcX = (40 * scaleFactor).roundToInt()
    val innerEnd = pixelSize - faceOffset

    val avatar = createBitmap(pixelSize, pixelSize)
    val canvas = Canvas(avatar)
    canvas.drawBitmap(
        skin,
        Rect(faceSize, faceSize, faceEndY, faceEndY),
        RectF(faceOffset, faceOffset, innerEnd, innerEnd),
        pclHeadPaint,
    )
    canvas.drawBitmap(
        skin,
        Rect(hatSrcX, faceSize, hatSrcX + faceSize, faceEndY),
        RectF(0f, 0f, pixelSize.toFloat(), pixelSize.toFloat()),
        pclHeadPaint,
    )
    return avatar
}

/** `MySkin` —— 64×64 的皮肤头像（底色半透明、`ColorObject2 @0.2` 阴影，脸图 56）。 */
@Composable
private fun PclSkinAvatar(
    account: Account?,
    fallbackName: String = "",
    size: Dp = 64.dp,
    /** 换完皮肤后传一个新值，强制重新读一次皮肤文件。 */
    refreshKey: Any? = Unit,
    /** 非空时整块头像可点（离线账户用它来换皮肤）。 */
    onClick: (() -> Unit)? = null,
) {
    val p = pclPalette
    val context = LocalContext.current
    val density = LocalDensity.current
    val refreshWardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()

    // 有皮肤文件就用真皮肤；没有就按 UUID 的奇偶在 Steve / Alex 里挑一个默认皮肤
    // （PCL `ModMinecraft.McSkinSex`：`(uuid[7]^uuid[15]^uuid[23]^uuid[31]) % 2`，
    //   0 → 粗臂 Steve、1 → 细臂 Alex；Zalith 生成离线 profileId 时用的是同一套规则）。
    // `fallbackName` 用于「还没建账户、只在输入框里敲了名字」时也能按该名字预览默认皮肤。
    val headBitmap = remember(account, fallbackName, refreshWardrobe, size, density, refreshKey) {
        // PCL 的 MySkin 是 64 外框装 56 的脸图
        val pixelSize = with(density) { (size * PCL_SKIN_FACE_RATIO).roundToPx() }
        fun fromFile(file: File): Bitmap? = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)?.let { cropSkinHead(it, pixelSize) }
        }.getOrNull()

        fun fromAsset(asset: String): Bitmap? = runCatching {
            context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
                ?.let { cropSkinHead(it, pixelSize) }
        }.getOrNull()

        val skinFile = account?.getSkinFile()
        (skinFile?.takeIf { it.exists() }?.let(::fromFile)) ?: run {
            val model = account?.skinModelType?.takeIf { it != SkinModelType.NONE }
                ?: pclDefaultSkinModel(
                    account?.profileId
                        ?: getLocalUUIDWithSkinModel(fallbackName.trim(), SkinModelType.NONE),
                )
            fromAsset(
                if (model == SkinModelType.ALEX) PCL_SKIN_ALEX else PCL_SKIN_STEVE,
            )
        }
    }

    Box(
        modifier = Modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(0.dp),
                ambientColor = p.color2.copy(alpha = 0.2f),
                spotColor = p.color2.copy(alpha = 0.2f),
            )
            .background(p.semiTransparent),
        contentAlignment = Alignment.Center,
    ) {
        if (headBitmap != null) {
            Image(
                bitmap = headBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size * PCL_SKIN_FACE_RATIO),
            )
        } else {
            Text(
                text = "?",
                style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, color = p.color4),
            )
        }
    }
}

/**
 * `PageLoginMs`（未登录）/ `PageLoginMsSkin`（已登录）—— 正版登录面板。
 *
 * PCL 在 `PageLaunchLeft.xaml.vb:225-235` 里按「当前账户是不是正版」往 `PanLogin`
 * 塞 `PageLoginMs` 或 `PageLoginMsSkin`，两者互斥，这里保持同一分支。
 */
@Composable
private fun PclMicrosoftLoginPanel(
    runtime: PclRuntime,
    account: Account?,
    accounts: List<Account>,
) {
    val p = pclPalette
    val context = LocalContext.current
    val msAccounts = accounts.filter { it.isMicrosoftAccount() }
    val current = account?.takeIf { it.isMicrosoftAccount() }

    // PCL 的「切换账号」按钮（`PageLoginMsSkin.BtnExit_Click`）实际干的是**退出登录**：
    // 把 `CacheMsV2*` 一串设置清空，然后 `RefreshPage` 回到未登录页（`PageLoginMs`，
    // 带账户下拉框的那个）。Zalith 的账户是多条记录、没有「只缓存一个」的概念，
    // 所以这里保留同一语义：进「挑账户」状态 = 显示未登录页的那个下拉框。
    // 挑完账户、或选了「添加新账号」并登录成功，就自动回到头像面板。
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(current?.uniqueUUID) {
        // 当前账户换了（含登录了新的一个）→ 收起挑账户状态
        if (current != null) picking = false
    }

    if (current != null && !picking) {
        PclMicrosoftAccountPanel(
            runtime = runtime,
            current = current,
            msAccounts = msAccounts,
            onSwitch = { picking = true },
        )
        return
    }

    // ---- PanEmpty（`PageLoginMs.xaml`）：40 宽的人像图标 + 25 行高 + [账户下拉框 + 登录按钮]
    //      + 30 行高 + 购买/官网链接（Opacisty 0.3、间隔 30）
    Icon(
        imageVector = PclIcons.personFill,
        contentDescription = null,
        tint = p.color2.copy(alpha = 0.7f),
        modifier = Modifier.height(40.dp).width(40.dp),
    )
    Spacer(Modifier.height(25.dp))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // `ComboAccounts`：第 0 项恒为「添加新账号」，后面是已存的正版账户、
        // 每条都能就地删除（`ComboBoxItemTemplateWithDelete`）。
        PclComboBox(
            items = msAccounts,
            selected = current,
            modifier = Modifier.weight(1f),
            label = { it?.username.orEmpty() },
            leadingItem = PclStrings.LangPageLoginMsAddAccount,
            onLeadingClick = { startMicrosoftLogin(runtime, context) },
            onDelete = { runtime.deleteAccount(it) },
            onSelect = { runtime.switchAccount(it) },
        )
        Spacer(Modifier.width(10.dp))
        PclButton(
            text = PclStrings.LangPageLoginMsLogin,
            colorState = PclColorState.Highlight,
            height = 28.dp,
            modifier = Modifier.width(60.dp),
            onClick = { startMicrosoftLogin(runtime, context) },
        )
    }
    Spacer(Modifier.height(30.dp))
    // PCL：`Grid Grid.Row="4" Margin="2,30,2,0" HorizontalAlignment="Center"`、
    // 中间一列宽 30 → 两个按钮间隔 30 且整体居中。
    Row(
        modifier = Modifier.graphicsLayer(alpha = 0.3f),
        horizontalArrangement = Arrangement.spacedBy(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclTextButton(text = PclStrings.LangPageLoginMsBuy) {
            runtime.activity.openLinkInternal(URL_MS_BUY)
        }
        Spacer(Modifier.width(0.dp))
        PclTextButton(text = PclStrings.LangPageLoginMsWebsite) {
            runtime.activity.openLinkInternal(URL_MS_SITE)
        }
    }
}

/** `PageLoginMsSkin.xaml` 里 Grid 的行高合计：75 + Auto(min 30) + 13 + 30 + 19。 */
private val PCL_MS_SKIN_GRID_HEIGHT = 167.dp

/**
 * `PageLoginMsSkin` —— 已登录正版账户面板。
 *
 * 原文（`PageLoginMsSkin.xaml`）是一块 `Margin="0,60,0,5"` 的 5 行网格：
 *
 * ```
 * Row 0  Height=75            皮肤位（64×64 的 MySkin 叠在上面，居中贴顶）
 * Row 1  Height=Auto MinHeight=30   TextName  FontSize=17、Margin="8,0"、居中、CharacterEllipsis
 * Row 2  Height=13            空行
 * Row 3  Height=30            PanButtons：圆角 5、底色 ColorBrush8、Padding="10,3,9,3"，
 *                             内含 3 枚 Height=24 的 MyIconButton（皮肤 / 修改信息 / 切换账号），
 *                             彼此 Margin="0,0.5,9,0" → 间隔 9
 * Row 4  Height=19            空行
 * ```
 *
 * `PanData` 整块铺 `ColorBrushSemiTransparent`；`Skin` 带 `IsHitTestVisible="False"`
 * （不吃点击），`PanButtons` 初始 `Opacity=0`、靠 `PanData.MouseEnter` 淡入 ——
 * 悬停是鼠标独有的交互，触屏没有对应事件，所以这里恒定可见。
 */
@Composable
private fun PclMicrosoftAccountPanel(
    runtime: PclRuntime,
    current: Account,
    msAccounts: List<Account>,
    onSwitch: () -> Unit,
) {
    val p = pclPalette
    val context = LocalContext.current

    // ---- 皮肤（`BtnSkin` 的「修改皮肤 / 刷新」两项；Zalith 的皮肤存在 `Account.getSkinFile()`）
    var skinToken by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val skinPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "pcl_ms_skin.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching "读取不了所选图片"
                    if (!validateSkinFile(temp)) return@runCatching "文件不是有效的皮肤"
                    val skinFile = current.getSkinFile()
                    skinFile.parentFile?.mkdirs()
                    temp.copyTo(skinFile, overwrite = true)
                    null
                }.getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (error == null) {
                skinToken++
                AccountsManager.refreshWardrobe()
                PclHintHost.push("皮肤已更换。", PclHintTheme.Green)
            } else {
                PclHintHost.push("换皮肤失败：$error", PclHintTheme.Red)
            }
        }
    }

    var skinMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, bottom = 5.dp)
            .height(PCL_MS_SKIN_GRID_HEIGHT),
    ) {
        // PanData：半透明底 + 五行网格
        Box(modifier = Modifier.fillMaxSize().background(p.semiTransparent))

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(75.dp))

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.username,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = p.color1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(13.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(p.color8)
                        .padding(start = 10.dp, top = 3.dp, end = 9.dp, bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclIconButton(
                        logo = PclIcons.loginMsSkin,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.08f,
                        contentDescription = "皮肤与披风",
                        onClick = { skinMenu = true },
                    )
                    PclIconButton(
                        logo = PclIcons.loginMsEdit,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.1f,
                        contentDescription = "修改信息",
                        onClick = { editMenu = true },
                    )
                    PclIconButton(
                        logo = PclIcons.loginMsSwitch,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.15f,
                        contentDescription = "切换账号",
                        onClick = onSwitch,
                    )
                }

                // `BtnSkin` 的右键菜单（`PageLoginMsSkin.xaml` 的 ContextMenu）
                DropdownMenu(
                    expanded = skinMenu,
                    onDismissRequest = { skinMenu = false },
                    modifier = Modifier.background(p.white),
                ) {
                    DropdownMenuItem(
                        text = { Text("修改皮肤", style = PclTextStyle.base(p)) },
                        onClick = { skinMenu = false; skinPicker.launch("image/png") },
                    )
                    DropdownMenuItem(
                        text = { Text("刷新皮肤", style = PclTextStyle.base(p)) },
                        onClick = {
                            skinMenu = false
                            skinToken++
                            AccountsManager.refreshWardrobe()
                            PclHintHost.push("已刷新皮肤。", PclHintTheme.Blue)
                        },
                    )
                }

                // `BtnEdit` 的右键菜单：修改密码 / 修改玩家名
                DropdownMenu(
                    expanded = editMenu,
                    onDismissRequest = { editMenu = false },
                    modifier = Modifier.background(p.white),
                ) {
                    DropdownMenuItem(
                        text = { Text("修改密码", style = PclTextStyle.base(p)) },
                        onClick = {
                            editMenu = false
                            runtime.activity.openLinkInternal(URL_MS_PASSWORD)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("修改玩家名", style = PclTextStyle.base(p)) },
                        onClick = {
                            editMenu = false
                            runtime.activity.openLinkInternal(URL_MS_PROFILE)
                        },
                    )
                }
            }

            Spacer(Modifier.height(19.dp))
        }

        // Skin：64×64，居中贴顶（`IsHitTestVisible="False"` → 不吃点击）
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            PclSkinAvatar(account = current, size = 64.dp, refreshKey = skinToken)
        }
    }
}

/** 离线账户下拉项；`account` 为 null 表示「新建离线账户…」。 */
private class OfflineAccountOption(val account: Account?, val label: String)

/**
 * `MyComboBox IsEditable="True"`（`PageLoginLegacy.ComboName`）——
 * 可以直接敲玩家名的输入框，右侧箭头点开列出已有离线账户。
 *
 * 几何照抄 `MyComboBox`：高 28、圆角 3、边框 1px ColorBrushBg0、底色半透明白、
 * 内容 `Margin="8.5,21.5"`、箭头 9×5.5。
 */
@Composable
private fun PclAccountNameField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    accounts: List<Account>,
    onPick: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(p.halfWhite)
                .border(pclHairline(), p.bg0, RoundedCornerShape(3.dp))
                .padding(start = 8.5.dp, end = 4.dp)
                .onGloballyPositioned { anchorSize = it.size },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = PclStrings.LangPageLoginLegacyID,
                        style = PclTextStyle.base(p).copy(color = p.gray4),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = PclTextStyle.base(p),
                    cursorBrush = SolidColor(p.color3),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onCommit(value)
                            focusManager.clearFocus()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        // 失焦即「填完了」：PCL 是在启动时才取用输入的内容，
                        // 这里改成收起键盘/点别处就落库，保证点「启动游戏」时账户已就绪。
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                hadFocus = true
                            } else if (hadFocus) {
                                hadFocus = false
                                onCommit(value)
                            }
                        },
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 20.dp, height = 28.dp)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PclIcons.comboArrow,
                    contentDescription = null,
                    tint = p.bg0,
                    modifier = Modifier.size(width = 9.dp, height = 5.5.dp),
                )
            }
        }

        // 下拉层用 PCL 的 `MyComboBox` 弹层形状（等宽、白底、圆角 3、同色边框），
        // 而不是 Material 的 DropdownMenu —— 后者圆角、阴影、宽度都不一样。
        if (expanded) {
            PclComboBoxPopup(
                anchorSize = anchorSize,
                borderColor = p.bg0,
                onDismiss = { expanded = false },
            ) {
                if (accounts.isEmpty()) {
                    PclComboBoxItemRow(
                        text = PclStrings.LangAccountNoLocal,
                        selected = false,
                        foreground = p.bg0,
                        enabled = true,
                        onClick = { expanded = false },
                    )
                } else {
                    accounts.forEach { item ->
                        PclComboBoxItemRow(
                            text = item.username,
                            selected = false,
                            foreground = p.bg0,
                            enabled = true,
                            onClick = {
                                expanded = false
                                onPick(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * `PageLaunchLeft.xaml` 的 `PanTypeOne` —— 版本把登录方式定死成一种时（仅正版 / 仅离线 /
 * 统一通行证 / Authlib-Injector），PCL 不画那两枚可点的胶囊，而是显示一枚**静态**胶囊：
 *
 * ```
 * Border Background=ColorBrushSemiTransparent CornerRadius=13 MinHeight=27 MaxHeight=27
 *   Path   16×16（MaxHeight/MaxWidth=16，Scale 1.05）、Margin="12,0,0,0"、ColorBrush3
 *   Text   FontSize=14、Margin="15,0,12,0"、ColorBrush3
 * ```
 *
 * 外置登录的文字就是认证服务器的名字（`VersionServerAuthName`），没填才退回「第三方登录」
 * —— 注意 PCL 这里**没有**「登录」二字，只有服务器名。
 */
@Composable
private fun PclLoginTypeCapsule(name: String) {
    val p = pclPalette
    Row(
        // PanType 的 Margin="20,22,20,0"
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .height(27.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(p.semiTransparent)
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = PclIcons.loginAuth,
                contentDescription = null,
                tint = p.color3,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = 1.05f
                        scaleY = 1.05f
                    },
            )
            Spacer(Modifier.width(15.dp))
            PclText(
                text = name.ifBlank { "第三方登录" },
                color = p.color3,
                fontSize = 14,
            )
        }
    }
}

/** 右栏当前显示哪一块登录页（可用项由版本的「登录方式」决定）。 */
private enum class HomeLoginPage { Microsoft, Offline, Auth }

/**
 * `PageLoginAuth` —— 外置登录（authlib-injector / LittleSkin / 统一通行证）面板。
 *
 * 版式照 PCL 的启动页右栏：顶部居中「{服务器名} 登录」，中间留白（PCL 那里是角色皮肤
 * 预览，没登录时就是空的），底部是 `邮箱 / 密码 / 记住密码 / 注册账号` 四件套。
 *
 * 没有「登录」按钮 —— 和 PCL 一致：填好之后直接按底下那个**启动游戏**，
 * 启动链路（`PclRuntime.prepareAccount` → `PclThirdPartyLogin.ask`）会拿这里填的凭据
 * 去认证服务器换 token 再启动，所以面板只需要把凭据交出去（见 [PclAuthLoginSettings]）。
 */
@Composable
private fun PclAuthLoginPanel(
    serverName: String,
    baseUrl: String,
    registerUrl: String,
    account: Account?,
    accounts: List<Account>,
) {
    val p = pclPalette
    val context = LocalContext.current
    val runtime = LocalPclRuntime.current

    if (baseUrl.isBlank()) {
        // 版本里还没填认证服务器：给一句提示，别摆一个登不进去的输入框
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PclHint(
                text = "该版本还没有填认证服务器。\n" +
                        "「版本设置 → 服务器 → 认证服务器」填好之后再回来登录。",
                theme = PclHintTheme.Blue,
            )
        }
        return
    }

    val saved = remember(baseUrl) { PclAuthLoginSettings.load(context, baseUrl) }
    val loggedIn = account?.takeIf { it.belongsToAuthServer(baseUrl) }
    var email by remember(baseUrl) {
        mutableStateOf(saved.email.ifEmpty { loggedIn?.otherAccount.orEmpty() })
    }
    var password by remember(baseUrl) { mutableStateOf(saved.password) }
    var remember by remember(baseUrl) { mutableStateOf(saved.remember) }
    var showHistory by remember { mutableStateOf(false) }

    // 输入即同步给启动链路（内存那份），勾了「记住密码」才写盘
    LaunchedEffect(email, password, remember) {
        PclAuthLoginSettings.save(context, baseUrl, PclAuthLoginSettings.Saved(email, password, remember))
    }

    // 「邮箱」下拉里的历史账号：这台服务器上已登录过的账户 + 记住的那个
    val history = remember(accounts, baseUrl, saved.email) {
        buildList {
            accounts.filter { it.belongsToAuthServer(baseUrl) }
                .mapNotNull { it.otherAccount?.takeIf { value -> value.isNotBlank() } }
                .forEach { add(it) }
            if (saved.email.isNotBlank()) add(saved.email)
        }.distinct()
    }

    // 「切换账号」：和正版面板的 picking 同一个语义 —— 先回到输入框，登录成功再自动收起
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn?.uniqueUUID) {
        if (loggedIn != null) picking = false
    }

    // 标题不在这里画：PCL 把它画成了 `PanTypeOne` 那枚静态胶囊（见 [PclLoginTypeCapsule]），
    // 位置在登录面板**上面**、和正版 / 离线那两枚胶囊同一行，所以由调用方负责。
    Column(modifier = Modifier.fillMaxSize()) {
        if (loggedIn != null && !picking) {
            // ---- 已登录：整块换成和正版一样的「皮肤头像 + 角色名 + 按钮行」。
            //      PCL 的 `PageLoginAuth` 只画输入框，没有已登录样式（它把这件事交给
            //      `PageLoginMsSkin` 那一套去了），这里是照正版面板补的。
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PclAuthAccountPanel(
                    serverName = serverName,
                    baseUrl = baseUrl,
                    registerUrl = registerUrl,
                    current = loggedIn,
                    onSwitch = { picking = true },
                )
            }
            return@Column
        }

        // ---- 未登录（或刚点了「切换账号」）：中间留白 + 账号密码
        Box(modifier = Modifier.weight(1f).fillMaxWidth())

        // ---- 邮箱（PCL 的可编辑 ComboBox：手动输入 + 右侧下拉选历史账号）
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclText(text = "邮箱", modifier = Modifier.width(36.dp))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                PclTextBox(
                    value = email,
                    modifier = Modifier.fillMaxWidth(),
                    onValueChange = { email = it },
                )
                if (history.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable { showHistory = true }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = PclIcons.comboArrow,
                            contentDescription = "选择已登录的账号",
                            tint = p.color3,
                            modifier = Modifier.size(9.dp, 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(9.dp))

        // ---- 密码
        Row(verticalAlignment = Alignment.CenterVertically) {
            PclText(text = "密码", modifier = Modifier.width(36.dp))
            Spacer(Modifier.width(8.dp))
            PclTextBox(
                value = password,
                modifier = Modifier.weight(1f),
                secret = true,
                onValueChange = { password = it },
            )
        }
        Spacer(Modifier.height(11.dp))

        // ---- 记住密码 / 注册账号（缩在输入框那一侧，对应 PCL 的 CheckRemember + BtnLink）
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PclCheckBox(
                text = "记住密码",
                checked = remember,
                onCheckedChange = { remember = it },
            )
            Spacer(Modifier.weight(1f))
            if (registerUrl.isNotBlank()) {
                PclTextButton(text = "注册账号") {
                    runtime.openWebPage(registerUrl, "$serverName 注册账号")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showHistory) {
        PclSelectDialog(
            title = "选择账号",
            options = history.map { it to it },
            current = email,
            onSelect = {
                email = it
                showHistory = false
            },
            onDismiss = { showHistory = false },
        )
    }
}

/**
 * 外置账户已登录面板 —— 版式与 [PclMicrosoftAccountPanel] **完全一致**：
 *
 * ```
 * Row 0  Height=75             64×64 皮肤头像（居中贴顶）
 * Row 1  Height=Auto MinHeight=30   角色名，17sp，居中、超长省略
 * Row 2  Height=13             空行
 * Row 3  Height=30             PanButtons：圆角 5、底色 ColorBrush8，3 枚 24dp 图标按钮
 * Row 4  Height=19             空行
 * ```
 *
 * 三枚按钮的含义换成外置登录那一套：**皮肤**（本地换皮 / 重新从服务器拉）·
 * **服务器网站**（注册页 / 账号中心）· **切换账号**。几何常量直接复用
 * [PCL_MS_SKIN_GRID_HEIGHT]，改一处两边一起动 —— 这两个面板并排切换时不能有位移。
 */
@Composable
private fun PclAuthAccountPanel(
    serverName: String,
    baseUrl: String,
    registerUrl: String,
    current: Account,
    onSwitch: () -> Unit,
) {
    val p = pclPalette
    val context = LocalContext.current
    val runtime = LocalPclRuntime.current
    val scope = rememberCoroutineScope()

    var skinToken by remember { mutableStateOf(0) }
    var skinMenu by remember { mutableStateOf(false) }
    var editMenu by remember { mutableStateOf(false) }

    // 「修改皮肤」：外置账户的真皮肤由认证服务器给（`downloadYggdrasil`），
    // 这里选的文件只覆盖本地那一份，语义和正版面板的「修改皮肤」相同。
    val skinPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(context.cacheDir, "pcl_auth_skin.png")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching "读取不了所选图片"
                    if (!validateSkinFile(temp)) return@runCatching "文件不是有效的皮肤"
                    val skinFile = current.getSkinFile()
                    skinFile.parentFile?.mkdirs()
                    temp.copyTo(skinFile, overwrite = true)
                    null
                }.getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (error == null) {
                skinToken++
                AccountsManager.refreshWardrobe()
                PclHintHost.push("皮肤已更换。", PclHintTheme.Green)
            } else {
                PclHintHost.push("换皮肤失败：$error", PclHintTheme.Red)
            }
        }
    }

    // 老账户 / 上次拉皮肤失败：打开面板时补拉一次，别让头像一直停在默认 Steve。
    LaunchedEffect(current.uniqueUUID) {
        if (!current.getSkinFile().exists()) {
            withContext(Dispatchers.IO) { runCatching { current.downloadYggdrasil() } }
            skinToken++
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().height(PCL_MS_SKIN_GRID_HEIGHT),
    ) {
        // PanData：半透明底 + 五行网格
        Box(modifier = Modifier.fillMaxSize().background(p.semiTransparent))

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(75.dp))

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 30.dp).padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.username,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = p.color1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(13.dp))

            Box(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(p.color8)
                        .padding(start = 10.dp, top = 3.dp, end = 9.dp, bottom = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclIconButton(
                        logo = PclIcons.loginMsSkin,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.08f,
                        contentDescription = "皮肤与披风",
                        onClick = { skinMenu = true },
                    )
                    PclIconButton(
                        logo = PclIcons.loginMsEdit,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.1f,
                        contentDescription = "服务器网站",
                        onClick = { editMenu = true },
                    )
                    PclIconButton(
                        logo = PclIcons.loginMsSwitch,
                        size = 24.dp,
                        logoSize = 14.dp,
                        logoScale = 1.15f,
                        contentDescription = "切换账号",
                        onClick = onSwitch,
                    )
                }

                DropdownMenu(
                    expanded = skinMenu,
                    onDismissRequest = { skinMenu = false },
                    modifier = Modifier.background(p.white),
                ) {
                    DropdownMenuItem(
                        text = { Text("修改皮肤", style = PclTextStyle.base(p)) },
                        onClick = { skinMenu = false; skinPicker.launch("image/png") },
                    )
                    DropdownMenuItem(
                        text = { Text("刷新皮肤", style = PclTextStyle.base(p)) },
                        onClick = {
                            skinMenu = false
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching { current.downloadYggdrasil() }
                                }
                                skinToken++
                                AccountsManager.refreshWardrobe()
                                PclHintHost.push("已刷新皮肤。", PclHintTheme.Blue)
                            }
                        },
                    )
                }

                // 外置账户没有「修改密码」这类统一路径：跳服务器自己的网站就对了
                DropdownMenu(
                    expanded = editMenu,
                    onDismissRequest = { editMenu = false },
                    modifier = Modifier.background(p.white),
                ) {
                    DropdownMenuItem(
                        text = { Text("$serverName 网站", style = PclTextStyle.base(p)) },
                        onClick = {
                            editMenu = false
                            runtime.openWebPage(
                                registerUrl.ifBlank { baseUrl },
                                "$serverName 网站",
                            )
                        },
                    )
                    if (registerUrl.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("注册新账号", style = PclTextStyle.base(p)) },
                            onClick = {
                                editMenu = false
                                runtime.openWebPage(registerUrl, "$serverName 注册账号")
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(19.dp))
        }

        // Skin：64×64，居中贴顶（和正版面板同一层）
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            PclSkinAvatar(account = current, size = 64.dp, refreshKey = skinToken)
        }
    }
}

/**
 * `PageLoginLegacy` —— 离线登录面板：64 头像 + 可直接输入玩家名的输入框。
 *
 * PCL 的输入框是可编辑 ComboBox：敲什么名字，头像就按那个名字的 UUID 预览默认皮肤，
 * 名字直到启动时才被取用。Zalith 必须先有 `Account` 才能启动，
 * 所以这里在「输入完成」（回车 / 收起键盘 / 点别处）时落库：
 * 已有同名账户就切过去，没有就用这个名字新建一个离线账户。
 */
@Composable
private fun PclOfflineLoginPanel(
    runtime: PclRuntime,
    account: Account?,
    accounts: List<Account>,
    name: String,
    onNameChange: (String) -> Unit,
) {
    val p = pclPalette
    val localAccounts = accounts.filter { it.isLocalAccount() }
    val current = account?.takeIf { it.isLocalAccount() }

    val typed = name.trim()
    val matched = localAccounts.firstOrNull { it.username.equals(typed, ignoreCase = true) }

    // ---- 换皮肤（PCL 的离线页点头像就是换皮肤；Zalith 的皮肤存在 `Account.getSkinFile()`）
    var skinToken by remember { mutableStateOf(0) }
    val skinScope = rememberCoroutineScope()
    val skinContext = LocalContext.current
    // 这次选图到底写给哪个账户。之所以存下来而不是在回调里现算 `matched`：
    // 一是账户可能是刚刚才建出来的（见下面的 onClick），二是 `matched` 是每次重组重算的
    // 普通值，回调不保证拿到最新一帧的那个。`skinPickTarget` 是 remember 出来的 state，
    // 闭包拿到的始终是同一个 MutableState，读出来一定是最新值。
    var skinPickTarget by remember { mutableStateOf<Account?>(null) }
    val skinPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = skinPickTarget
        skinPickTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        skinScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val temp = File(skinContext.cacheDir, "pcl_offline_skin.png")
                    skinContext.contentResolver.openInputStream(uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching "读取不了所选图片"
                    if (!validateSkinFile(temp)) return@runCatching "文件不是有效的皮肤"
                    val skinFile = target.getSkinFile()
                    skinFile.parentFile?.mkdirs()
                    temp.copyTo(skinFile, overwrite = true)
                    null
                }.getOrElse { it.message ?: it.javaClass.simpleName }
            }
            if (result == null) {
                skinToken++
                AccountsManager.refreshWardrobe()
                PclHintHost.push("皮肤已更换。", PclHintTheme.Green)
            } else {
                PclHintHost.push("换皮肤失败：$result", PclHintTheme.Red)
            }
        }
    }
    PclSkinAvatar(
        account = matched,
        fallbackName = typed,
        refreshKey = skinToken,
        onClick = {
            val target = matched ?: current
            when {
                target != null -> {
                    skinPickTarget = target
                    skinPicker.launch("image/png")
                }
                // 账户还没建出来时，就直接用输入框里的名字现建一个，建完接着拉起选图。
                //
                // 【有意去掉的限制】原先这里弹的是「先输入玩家名并启动一次游戏，才能给这个
                // 离线账户换皮肤。」——而这句话在 PCL2 源码里根本不存在（全仓库搜不到），
                // 是移植时自己加的。皮肤文件只是 `DIR_ACCOUNT_SKIN/<uniqueUUID>.png`，
                // 跟有没有启动过游戏毫无关系；PCL 的离线皮肤也是纯本地文件
                // （`PageLaunchLeft.xaml.vb` 的 `SkinLegacyLoad`，`LaunchSkinType` 里的自定义
                // 皮肤就是一份 `CustomSkin.png`）。所以这里不再拦人。
                typed.isEmpty() ->
                    PclHintHost.push("请先输入玩家名，再点头像换皮肤。", PclHintTheme.Blue)
                !typed.matches(Regex("^[0-9A-Za-z_]+$")) ->
                    PclHintHost.push(PclStrings.LangPageLoginLegacySpecialCharacters, PclHintTheme.Red)
                else -> runtime.createLocalAccount(typed) {
                    // `suspendSaveAccount` 收尾会 `suspendReloadAccounts()`，所以这一刻
                    // `accountsFlow.value` 里已经有这个新账户了，直接取出来用。
                    AccountsManager.accountsFlow.value
                        .firstOrNull { it.isLocalAccount() && it.username.equals(typed, ignoreCase = true) }
                        ?.let { created ->
                            skinPickTarget = created
                            skinPicker.launch("image/png")
                        }
                }
            }
        },
    )
    Spacer(Modifier.height(15.dp))

    PclAccountNameField(
        value = name,
        onValueChange = onNameChange,
        onCommit = { raw ->
            val value = raw.trim()
            if (value.isNotEmpty()) {
                val exist = localAccounts.firstOrNull { it.username.equals(value, ignoreCase = true) }
                if (exist != null) {
                    if (exist.uniqueUUID != current?.uniqueUUID) runtime.switchAccount(exist)
                } else {
                    runtime.createLocalAccount(value)
                }
            }
        },
        accounts = localAccounts,
        onPick = { runtime.switchAccount(it) },
        modifier = Modifier.fillMaxWidth(),
    )

    // 非法字符提示（PageLoginLegacy.HintChinese：只允许英文、数字、下划线）
    if (name.isNotEmpty() && !name.matches(Regex("^[0-9A-Za-z_]*$"))) {
        Spacer(Modifier.height(10.dp))
        PclHint(
            text = PclStrings.LangPageLoginLegacySpecialCharacters,
            theme = PclHintTheme.Red,
        )
    }
}

// ---------------------------------------------------------------- 右侧面板

/** `PageLaunchRight` —— 右侧信息流：提示条 + 自定义主页 + 启动日志卡片。 */
@Composable
fun PclHomeRight(nav: PclNavController) {
    // PanCustom（自定义主页）：PCL 在 `UiCustomType` 为 预设/本地/联网 时把启动页
    // 右侧整块换成自定义主页，左边的账户 / 版本面板照旧。
    val context = LocalContext.current
    val runtime = LocalPclRuntime.current

    // 「侧边什么也没有」：整块右侧不画任何东西（连提示条和启动日志也不画）。
    if (PclAppearance.homeType == PclHomeType.EMPTY) return

    var reloadToken by remember { mutableStateOf(0) }
    // 主页里 `EventType="弹出窗口"` 的标题 / 内容（PclConfirmDialog 展示）。
    var homeDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 「你知道吗？/ 回声洞」是 PCL 的内置预设（`PageLaunchRight.xaml.vb` 里 Case 0/1 的
    // XAML 字面量）。这两张卡片**直接用 Compose 复刻**，完全不经过 XAML 转换器 ——
    // 转换器只用来啃别人的主页文件，内置预设没必要冒那个风险。
    if (PclAppearance.homeType == PclHomeType.PRESET) {
        val preset = PclHomePresets.of(PclAppearance.homePreset)
        if (preset != null && preset.isLocal) {
            PclLocalPresetHome(preset = preset, reloadToken = reloadToken, onRefresh = { reloadToken++ })
            return
        }
    }

    // 远程预设 / 本地文件 / 联网更新：统一由 [PclHomeLoader] 解析成「网址」或「已转换的
    // HTML」；远程 PCL 主页 XAML 会在 IO 线程下载并转换，所以这里异步等待。
    var homeEntry by remember { mutableStateOf<PclHomeEntry?>(null) }
    // 「默认」直接用下面的内置信息流，不要先闪一下「正在加载自定义主页」。
    var resolved by remember {
        mutableStateOf(PclAppearance.homeType == PclHomeType.DEFAULT)
    }

    LaunchedEffect(
        PclAppearance.homeType,
        PclAppearance.homePreset,
        PclAppearance.homeNet,
        PclAppearance.homeLocalFile,
        reloadToken,
    ) {
        if (PclAppearance.homeType == PclHomeType.DEFAULT ||
            PclAppearance.homeType == PclHomeType.EMPTY
        ) {
            homeEntry = null
            resolved = true
            return@LaunchedEffect
        }
        resolved = false
        homeEntry = PclHomeLoader.resolve(context)
        resolved = true
    }

    // 正在解析（预设里的远程 PCL 主页 XAML 要联网下载 + 转换，可能要点时间），
    // 给一个明确的加载态，而不是先闪一下默认主页再跳回来。
    if (!resolved) {
        PclContent {
            PclCard(modifier = Modifier.fillMaxWidth(), title = "自定义主页") {
                PclText(text = "正在加载自定义主页…", color = pclPalette.gray2)
            }
            com.zalith.pcl.ui.pcl2.components.PclBottomSpacer()
        }
        return
    }

    if (homeEntry != null) {
        PclCustomHomePage(
            entry = homeEntry!!,
            reloadToken = reloadToken,
            onEvent = { type, data ->
                // 主页里的内部按钮（PCL 的 EventType）：能在手机上做的就做，做不了的给明确提示。
                when {
                    type.contains("刷新") -> reloadToken++
                    // 教程：`EventType="弹出窗口"`，EventData 格式是「标题|内容」。
                    type.contains("弹出窗口") -> {
                        val parts = data.split("|", limit = 2)
                        homeDialog = parts[0].ifBlank { "提示" } to (parts.getOrNull(1) ?: "")
                    }
                    // 教程：`EventType="复制文本"`，把 EventData 放进剪贴板。
                    type.contains("复制文本") -> {
                        runCatching {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("PCL 主页", data))
                        }.onSuccess {
                            PclHintHost.push("已复制到剪贴板。", PclHintTheme.Green)
                        }.onFailure {
                            PclHintHost.push("复制失败。", PclHintTheme.Red)
                        }
                    }
                    type.contains("启动游戏") -> {
                        // EventData 是版本名（如 "26.4-snapshot-5"）。找到它、切为当前版本、直接启动。
                        val target = runtime.versions.value
                            .firstOrNull { it.getVersionName() == data }
                        if (target == null) {
                            PclHintHost.push(
                                "没有找到版本「$data」，请先在「下载」页安装该版本。",
                                PclHintTheme.Red,
                            )
                        } else {
                            if (runtime.currentVersion.value?.getVersionName() != data) {
                                runtime.selectVersion(target)
                            }
                            runtime.tryLaunch()
                        }
                    }
                    type.contains("下载") && data.startsWith("http", true) ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(data),
                                )
                            )
                        }.onFailure {
                            PclHintHost.push("无法打开下载链接。", PclHintTheme.Red)
                        }
                    type.contains("打开网页") && data.startsWith("http", true) ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(data),
                                )
                            )
                        }
                    type.isNotBlank() ->
                        PclHintHost.push("主页动作「$type」暂不支持。", PclHintTheme.Blue)
                }
            },
        )
        // 「弹出窗口」事件：用 PCL 的确认对话框显示标题 + 内容。
        homeDialog?.let { (title, content) ->
            PclConfirmDialog(
                title = title,
                content = content,
                onConfirm = { homeDialog = null },
                onDismiss = { homeDialog = null },
            )
        }
        return
    }

    val p = pclPalette
    val version by runtime.currentVersion.collectAsStateWithLifecycle()
    val isLaunching = runtime.isLaunching
    val liveLog by runtime.logLines.collectAsStateWithLifecycle()

    // 未启动时展示上一次启动留下的日志文件内容
    var fileLog by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(version, isLaunching) {
        fileLog = if (isLaunching) emptyList() else runtime.readLatestLog(version)
    }
    val shownLines = (if (liveLog.isNotEmpty()) liveLog else fileLog).takeLast(HOME_LOG_LINES)

    PclContent {
        // PanHint（PCL 首页的固定提示条）
        var hintClosed by remember { mutableStateOf(false) }
        if (!hintClosed) {
            PclHint(
                text = "Minecraft 已安装的版本会显示在左侧；未登录时可以先用「离线账户」启动。",
                theme = PclHintTheme.Blue,
                onClose = { hintClosed = true },
            )
            Spacer(Modifier.height(15.dp))
        }

        // PanCustom（自定义主页）
        // 「你知道吗」的内置文案已删除：只显示官网审核通过的文案，拉不到就整张卡不画。
        val homeHint = rememberRemoteText("hint")
        if (homeHint.isNotBlank()) {
            PclCard(modifier = Modifier.fillMaxWidth(),
                title = "你知道吗？",
                contentPadding = PaddingValues(start = 25.dp, top = 40.dp, end = 23.dp, bottom = 15.dp),
            ) {
                PclText(text = homeHint)
            }
            Spacer(Modifier.height(15.dp))
        }

        // PanLog（启动日志卡片）
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangLaunchRightLog,
            contentPadding = PaddingValues(start = 20.dp, top = 38.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclText(
                        text = when {
                            isLaunching -> "启动中…（下方为 JVM 实时日志）"
                            shownLines.isEmpty() -> "暂无日志，启动一次游戏后这里会显示日志内容。"
                            else -> "上次启动日志（${shownLines.size} 行）"
                        },
                        color = p.gray3,
                        fontSize = 12,
                        modifier = Modifier.weight(1f),
                    )
                    PclTextButton(
                        text = "清空",
                        onClick = {
                            runtime.clearLog()
                            // 只清 buffer 是不够的：liveLog 一空，shownLines 立刻回退到
                            // fileLog（磁盘里上次启动的日志），看起来像「点了没反应」。
                            fileLog = emptyList()
                        },
                    )
                }
                shownLines.forEach { line ->
                    Text(
                        text = line,
                        style = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = p.color1,
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                }
            }
        }
        com.zalith.pcl.ui.pcl2.components.PclBottomSpacer()
    }
}

private fun formatSpeed(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
    else -> "$bytes B/s"
}

/**
 * PCL 的内置主页预设「你知道吗？/ 回声洞」的 Compose 原生实现。
 *
 * 对应 `PageLaunchRight.xaml.vb:44-58` 里 Case 0 / Case 1 的两段 XAML 字面量：
 * 一张 `MyCard`（标题 + 正文 `TextBlock`），右上角一个刷新按钮（`MyIconButton`）。
 * 这里直接用 [PclCard] / [PclText] / [PclTextButton] 复刻，**不经过 XAML 转换器** ——
 * 内置内容本来就有现成的 Compose 控件可用，没必要让通用转换器去冒险。
 */
@Composable
private fun PclLocalPresetHome(
    preset: PclHomePresetEntry,
    reloadToken: Int,
    onRefresh: () -> Unit,
) {
    val isEcho = preset.id == 1
    // 每次刷新换一条随机内容（对应 PCL 的 {hint} / {cave} 占位符随机替换）；
    // 优先联网抓官网已审核文案，抓不到回退本地。
    val text = rememberRemoteText(if (isEcho) "cave" else "hint", reloadToken)

    // 原来的顶部条（「自定义主页 / 刷新主页」）已整行删除，卡片右上角的「刷新」按钮也去掉了；
    // 换一条的方式改成**直接点正文文字**，少两层视觉噪音。
    PclContent {
        // 内置文案已删：官网没审核内容时「你知道吗」不再画空卡（回声洞有占位文案）
        if (text.isNotBlank()) {
            PclCard(
                modifier = Modifier.fillMaxWidth(),
                title = if (isEcho) "回声洞" else "你知道吗？",
                contentPadding = PaddingValues(start = 25.dp, top = 40.dp, end = 23.dp, bottom = 15.dp),
            ) {
                PclText(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRefresh() },
                )
            }
        }
        com.zalith.pcl.ui.pcl2.components.PclBottomSpacer()
    }
}

/**
 * `PageLaunchRight.PanCustom` —— 自定义主页。
 *
 * PCL 这里是内嵌的 WebBrowser 控件；Android 上用同一个内置 WebView 组件承载，
 * 顶部补一行「刷新主页」按钮（对应 `BtnCustomRefresh`）。
 * 「网址」直接加载；「HTML」（PCL 主页 XAML 的转换结果 / 内置预设）用 loadData 渲染。
 */
@Composable
private fun PclCustomHomePage(
    entry: PclHomeEntry,
    reloadToken: Int,
    onEvent: ((type: String, data: String) -> Unit)? = null,
) {
    // 换 key 才能让 WebView 真的重建（AndroidView 的 factory 只会跑一次）：
    // reloadToken 变化（主页里的「刷新」按钮）或内容变化都会触发重建。
    key(entry, reloadToken) {
        when (entry) {
            is PclHomeEntry.Web -> PclInlineWeb(
                url = entry.url,
                modifier = Modifier.fillMaxSize(),
                // 主页必须在「自定义背景」之上当透明层用，否则整块被刷成白色
                transparent = true,
            )
            is PclHomeEntry.Html -> PclInlineHtml(
                html = entry.html,
                modifier = Modifier.fillMaxSize(),
                onEvent = onEvent,
                transparent = true,
            )
        }
    }
}
