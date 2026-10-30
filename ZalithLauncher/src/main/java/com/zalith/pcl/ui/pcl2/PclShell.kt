/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 主窗口外壳。
 *
 * 结构完全对应 PCL2 的 `FormMain.xaml`：
 *   PanBack（外层 10 边距）
 *    └ BorderForm（圆角 6）
 *       └ PanForm
 *          ├ PanTitle  高 48，横向三段渐变；PCL 描边 Logo；居中 5 个导航胶囊；
 *          │           右侧「最小化」「关闭」；子页面时切换为「返回 + 标题」
 *          └ PanLeft  宽 300 / 200 的白色侧栏（#F1FFFFFF）+ 右侧 4dp 阴影
 *          └ PanMain  右侧内容区（可滚动，带 8dp 的 PCL 滚动条）
 *          └ PanHint  左下角提示条栈
 *          └ 右下角悬浮按钮（回到顶部等）
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zalith.pcl.game.account.AccountsManager
import com.zalith.pcl.ui.pcl2.components.PclPrank
import com.zalith.pcl.ui.pcl2.components.rememberPclPrankModifier
import com.zalith.pcl.ui.pcl2.components.PclBackgroundImageLayer
import com.zalith.pcl.ui.pcl2.components.PclDownloadTaskDialog
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclIconTheme
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclImageLoader
import com.zalith.pcl.ui.pcl2.pages.pclDownloadManagerPage
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclWebOverlay
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.data.PclAppearance
import com.zalith.pcl.ui.pcl2.data.PclLeftLabels
import com.zalith.pcl.ui.pcl2.data.PclThemeState
import com.zalith.pcl.ui.pcl2.data.PclHiddenKeys
import com.zalith.pcl.ui.pcl2.data.PclLoginSettings
import com.zalith.pcl.ui.pcl2.data.PclLogoType
import com.zalith.pcl.ui.pcl2.data.PclRuntime
import com.zalith.pcl.ui.pcl2.data.PclSkinSettings
import com.zalith.pcl.ui.pcl2.data.rememberPclRuntime
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclEase
import com.zalith.pcl.ui.pcl2.theme.PclMotion
import com.zalith.pcl.ui.pcl2.theme.PclPalette
import com.zalith.pcl.ui.pcl2.theme.PclPalettes
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.PclTheme
import com.zalith.pcl.ui.pcl2.theme.pclPalette

/* ============================================================================
 *  顶部导航的 5 个版块
 * ==========================================================================*/

/** PCL2 顶部导航的 5 个版块（对应 `BtnTitleSelect0..4`）。 */
enum class PclSection(val label: String, val icon: ImageVector, val hiddenKey: String) {
    Home(PclStrings.LangTitleHome, PclIcons.navHome, PclHiddenKeys.HOME),
    Download(PclStrings.LangTitleDownload, PclIcons.navDownload, PclHiddenKeys.DOWNLOAD),
    Link(PclStrings.LangTitleLink, PclIcons.navLink, PclHiddenKeys.LINK),
    Setup(PclStrings.LangTitleSetup, PclIcons.navSetup, PclHiddenKeys.SETUP),
    Other(PclStrings.LangTitleOther, PclIcons.navOther, PclHiddenKeys.OTHER),
}

/**
 * 子页面（对应 PCL 的 `PanTitleInner`：标题栏显示返回按钮）。
 *
 * [leftContent] 非空时，子页面自带一个白色左栏
 * （PCL 的「版本管理」「版本选择」等页面就是这种形态）。
 * 宽度**不是固定值**：PCL 的 `PanMain` 第一列是 `Auto`，左栏由最宽条目撑出来，
 * 所以这里用 [leftLabels] 把条目清单交给外壳去算（见 [pclLeftPanelWidth]）；
 * 个别页面在 XAML 里写死了 Min/MaxWidth（`PageSelectLeft`），用 [leftWidth] 直接指定。
 */
class PclSubPage(
    val title: String,
    val leftContent: (@Composable () -> Unit)? = null,
    /** 左栏条目标题（与左栏正文引用同一批常量，见 [PclLeftLabels]）。 */
    val leftLabels: List<String> = emptyList(),
    /** 直接指定左栏宽度，优先级高于 [leftLabels]。 */
    val leftWidth: Dp? = null,
    val content: @Composable () -> Unit,
)

/**
 * PCL2 的页面导航控制器。
 *
 * PCL 的 `FrmMain.PageCurrent / PageCurrentSub` 就在这里对应为
 * [section]（一级版块）、[leftIndex]（左侧列表选中项）、[subPage]（二级页面）。
 */
class PclNavController(initial: PclSection = PclSection.Home) {
    var section by mutableStateOf(initial)
        private set
    var leftIndex by mutableIntStateOf(0)
        private set
    var subPage by mutableStateOf<PclSubPage?>(null)
        private set

    /**
     * 一级版块令牌：只在「换版块 / 进二级页 / 返回」时递增。
     * 左栏的逐个入场动画挂在它上面——点击左栏条目本身不重建左栏，与 PCL 一致。
     */
    var sectionToken by mutableIntStateOf(0)
        private set

    /**
     * 页面令牌：左栏选中项变化也会递增。
     * 右栏内容的入场动画（`MyPageRight.TriggerEnterAnimation`）挂在它上面。
     */
    var pageToken by mutableIntStateOf(0)
        private set

    /** 切换到某个一级版块。 */
    fun go(target: PclSection, index: Int = 0) {
        section = target
        leftIndex = index
        subPage = null
        sectionToken++
        pageToken++
    }

    /** 选中左侧列表的第 [index] 项。 */
    fun select(index: Int) {
        if (leftIndex == index) return
        leftIndex = index
        pageToken++
    }

    /** 进入二级页面。 */
    fun push(page: PclSubPage) {
        subPage = page
        sectionToken++
        pageToken++
    }

    /** 返回上一级。 */
    fun back() {
        subPage = null
        sectionToken++
        pageToken++
    }
}

/** 供各页面获取导航控制器。 */
val LocalPclNav = staticCompositionLocalOf<PclNavController> {
    error("PclNavController 尚未提供，请在 PclMainScreen 内使用")
}

/* ============================================================================
 *  主窗口
 * ==========================================================================*/

/**
 * 「回到顶部」信号：令牌递增时，当前内容区滚动到顶部。
 *
 * PCL 的 `BtnExtraBack` 就是让右侧信息流回到顶部，这里用同一套语义。
 */
object PclScrollSignal {
    var token by mutableIntStateOf(0)
        private set

    /**
     * 当前右栏的滚动状态（由 `PclContent` 持续回写）。
     *
     * PCL 的「回到顶部」悬浮按钮只有在页面已经往下滚了一屏以上时才出现
     * （`FormMain.xaml.vb:1650` 的 `BtnExtraBack_ShowCheck`：`VerticalOffset > Height + 700`）。
     */
    var offset by mutableIntStateOf(0)
        private set
    var viewport by mutableIntStateOf(0)
        private set

    fun updateScroll(offsetPx: Int, viewportPx: Int) {
        offset = offsetPx
        viewport = viewportPx
    }

    fun request() {
        token++
    }
}

/** PCL2 主窗口。把它放进任意 Activity 的 setContent 即可。 */
@Composable
fun PclMainScreen(
    modifier: Modifier = Modifier,
    palette: PclPalette = PclThemeState.palette,
) {
    val nav = remember { PclNavController() }
    val runtime = rememberPclRuntime()
    // 当前版块被「功能隐藏」藏起来时，退回主页。
    // 用 `shouldHide` 而不是 `isHidden`：临时显示期间（右下角连点 10 次）放出来的
    // 入口是可以真的进去的，不该被人一脚踢回主页。
    LaunchedEffect(nav.section, PclSkinSettings.hidden, PclSkinSettings.peekUntil) {
        if (PclSkinSettings.shouldHide(nav.section.hiddenKey)) nav.go(PclSection.Home)
    }

    // ---- 「临时显示被隐藏的入口」的到期回收
    //
    // 连点十次之后所有被隐藏的入口会重新画出来，`PEEK_DURATION_MS` 后自动再藏回去。
    // 到期时如果人正停在某个被隐藏的页面里（多半就是在设置页里取消勾选），
    // 直接收走会把人弹回主页 —— 所以再续一轮，等他离开这一页自然结束。
    LaunchedEffect(PclSkinSettings.peekUntil) {
        val until = PclSkinSettings.peekUntil
        if (until <= 0L) return@LaunchedEffect
        val wait = until - SystemClock.elapsedRealtime()
        if (wait > 0L) delay(wait)
        if (PclSkinSettings.isHidden(nav.section.hiddenKey)) {
            PclSkinSettings.startPeek()
        } else {
            PclSkinSettings.stopPeek()
        }
    }

    // ---- 系统返回键（安卓三大金刚键里的「返回」）
    //
    // PCL 是桌面程序，它自己只有标题栏左上角那个返回箭头；手机上用户按的是系统返回键，
    // 之前这里没人接管，于是**任何一层都能被它一键退出启动器**。
    // 按「从最内层往外退」的顺序接管：
    //   内置浏览器页 → 二级页面 → 左栏选中的第 1..n 项 → 非主页版块 → 交还系统（退出启动器）。
    // 条件全不满足时 `enabled = false`，让系统走它自己的默认行为（关掉 Activity）。
    // 弹窗 / 下拉框 / 提示条这些自带返回处理的层在更内层，会先拿到事件，不用在这里管。
    val canGoBack by remember {
        derivedStateOf {
            runtime.webPage != null || nav.subPage != null ||
                nav.leftIndex != 0 || nav.section != PclSection.Home
        }
    }
    BackHandler(enabled = canGoBack) {
        when {
            runtime.webPage != null -> runtime.closeWebPage()
            nav.subPage != null -> nav.back()
            nav.leftIndex != 0 -> nav.select(0)
            else -> nav.go(PclSection.Home)
        }
    }

    val context = LocalContext.current
    // 背景不透明度：用 Zalith 自己的设置项，改哪边都一样
    val backgroundOpacity = com.zalith.pcl.setting.AllSettings.launcherBackgroundOpacity.state

    // 进界面时从 MMKV 读回皮肤设置（PCL 的 UiLogoType / UiHiddenPage* 等）。
    // 主题功能已移除，色板固定为 PCL2 默认主题，不再需要读回任何主题设置。
    LaunchedEffect(Unit) {
        PclSkinSettings.load(context)
        PclLoginSettings.load(context)
        // 背景图片 / 背景音乐 / 标题图片 / 自定义主页（PageSetupUI 的另外几张卡）
        PclAppearance.load(context)
        // 没记过背景图时，按 PCL 的行为要在 PCL/Background 里随机取一张。
        // 列目录是**磁盘 IO**，放到 IO 线程做：背景图一多，这一步能让「打开启动器」明显卡一下。
        if (PclAppearance.backgroundImage == null) {
            PclAppearance.applyResolvedBackground(
                withContext(Dispatchers.IO) { PclAppearance.resolveBackgroundPath(context) },
            )
        }
        // 账户表来自 Room 数据库，组合时主动拉一次。
        // （`Pcl2Activity` 已经在 onCreate 里调过 `refreshData()`，这里是第二道保险：
        //  只要 PCL 外壳被组合，账号就一定不会是空表。）
        AccountsManager.reloadAccounts()
        // UiMusicAuto：打开启动器自动开始播放
        if (PclAppearance.musicAuto) PclAppearance.startMusic(context)
    }

    // 离开界面（包括启动游戏后关掉启动器）就停掉音乐，别留一个没有界面的播放器在后台响
    DisposableEffect(Unit) {
        onDispose { PclAppearance.stopMusic() }
    }

    // UiMusicStart / UiMusicStop：跟着「游戏启动中」这个状态切换播放。
    // 首次组合不算（那时候的 launching = false 只是初始值），否则会把 UiMusicStop
    // 理解成「一进启动器就放音乐」。
    var musicSawLaunchState by remember { mutableStateOf(false) }
    val musicLaunching = runtime.isLaunching
    LaunchedEffect(musicLaunching) {
        if (!musicSawLaunchState) {
            musicSawLaunchState = true
            return@LaunchedEffect
        }
        if (musicLaunching) {
            if (PclAppearance.musicStart) PclAppearance.startMusic(context)
            if (PclAppearance.musicStop) PclAppearance.pauseMusic()
        } else {
            if (PclAppearance.musicStart) PclAppearance.pauseMusic()
            if (PclAppearance.musicStop) PclAppearance.startMusic(context)
        }
    }

    // 「界面缩放」（PCL 的「界面缩放」/ Windows 显示缩放）：
    // 先量出这个窗口有多大（dp），自动档就按 PCL 的 850×500 把它放大到放得下为止。
    // 关键是这一步必须在 `PclTheme` 之前 —— 量到的必须是**未缩放**的窗口尺寸。
    // 彩蛋期间窗体后面垫一层深色「桌面」—— 窗口缩小后露出来的就是它，
    // 不然缩小的窗体只会显示在原来的窗口底色上，「飘」的感觉出不来。
    val prankActive = PclPrank.active != null
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (prankActive) PclPrank.desktopColor else Color.Transparent),
    ) {
        val uiScale = PclSkinSettings.resolveScale(maxWidth.value, maxHeight.value)
        PclTheme(palette, uiScale) {
            PclMainContent(nav, runtime, backgroundOpacity)
        }

        // 彩蛋的出口。
        //
        // 彩蛋会一直整下去、不自己收场，所以必须留一个「停下」的办法，否则用户只能杀进程。
        // 这一层压在整扇窗口之上、盖满屏幕，所以整活期间点哪儿都是「停」，
        // 不会误触到下面飘着的那些按钮。点完给一句提示（见 `PclPrank.stop()`）。
        if (prankActive) {
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = interaction,
                        onClick = { PclPrank.stop() },
                    ),
            )
        }
    }
}

/** [PclMainScreen] 的主题内层：缩放系数已经由外层算好了。 */
@Composable
private fun PclMainContent(
    nav: PclNavController,
    runtime: PclRuntime,
    backgroundOpacity: Int,
) {
    CompositionLocalProvider(
        LocalPclNav provides nav,
        LocalPclRuntime provides runtime,
    ) {
        // 外部分享进来的整合包 / 键位：在这里接手，别让它们掉回原生界面。
        // 放在 providers 之后、窗体之前 —— 弹窗是独立 window，不参与下面的视觉变换。
        PclExternalImportHost(runtime)
        // 整合包安装前的「设置版本名称」询问框。挂在这里而不是某个页面里：
        // 下载页那条链是**下载完成后**才问的，此时用户可能已经切到别的版块了。
        PclNamePromptHost()
        // 第三方登录（authlib-injector / 统一通行证）的询问框：版本设置了第三方登录、
        // 本地又没有该服务器的账户时，启动那一刻在这里问账号密码。
        PclThirdPartyLoginHost()
        // 启动器更新弹窗。**必须挂在 PCL 外壳上**：上游只有 MainActivity 会渲染更新弹窗，
        // 而本界面跑在 Pcl2Activity —— 挂在页面里就不会弹（「关于」页点检查更新毫无反应）。
        PclUpgradeHost()
        val p = pclPalette
        // 「千万别点」彩蛋：整扇窗体的视觉变换（缩小 / 平移 / 抖动 / 叠加彩虹色）。
        // 放在最外层，`graphicsLayer` 才会把夹在后面的圆角、底色、窗体背景图一起带走。
        val prankModifier = rememberPclPrankModifier()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(prankModifier)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(PclDimens.formCorner))
                // 窗体底色：PCL 的 `UiBackgroundColorful` 关掉时用纯色 #F5F5F5
                // （ModSecret.ThemeRefreshMain 的 else 分支）。
                .background(if (PclSkinSettings.colorfulBackground) Color.Transparent else Color(0xFFF5F5F5))
                // PCL 窗体背景（ModSecret.ThemeRefreshMain 中 UiBackgroundColorful=True 分支）：
                // StartPoint(0.9, 0) → EndPoint(0.1, 1)，三个色标 Offset 分别为 -0.1 / 0.4 / 1.1。
                // Compose 的色标必须落在 0..1，因此把 [-0.1, 1.1] 归一化到 [0, 1]。
                // 「不透明度」用的是 Zalith 的 `launcherBackgroundOpacity`（20..100），
                // 叠在窗体底色上 —— 两边改同一个值。
                // 渐变依赖 size（起点 0.9w、终点 0.1w/1h），所以用 `drawWithCache` 按 size 缓存：
                // 重建只发生在「尺寸变化或重组」，而不是每次重绘都重新构造 Brush 与色标数组。
                .drawWithCache {
                    if (!PclSkinSettings.colorfulBackground) return@drawWithCache onDrawBehind {}
                    val alpha = (backgroundOpacity / 100f).coerceIn(0.2f, 1f)
                    val stops = p.bodyGradient
                    val brush = Brush.linearGradient(
                        colorStops = arrayOf(
                            0f to stops[0].copy(alpha = alpha),
                            ((0.4f + 0.1f) / 1.2f) to stops[1].copy(alpha = alpha),
                            1f to stops[2].copy(alpha = alpha),
                        ),
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.9f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height),
                    )
                    onDrawBehind { drawRect(brush = brush, size = size) }
                },
        ) {
            // ---- 背景图片（PCL 的 CardBackground）
            // 铺在窗体最底下：底色/渐变由上面的 Modifier 画，这一层盖在它们之上、
            // 界面内容之下，和 PCL 把图片放在 BorderForm 背景里的叠法一致。
            PclBackgroundImageLayer(
                modifier = Modifier.matchParentSize(),
                opacityPercent = backgroundOpacity,
            )

            Column(modifier = Modifier.fillMaxSize()) {
                PclTitleBar(nav)
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    PclBody(nav)
                }
            }
            // 左下角提示条栈（PanHint Margin="0,0,0,20"，左对齐）
            PclHintStack(Modifier.align(Alignment.BottomStart).padding(bottom = PclDimens.hintStackBottom))
            // 右下角「连点 10 次」热区：临时显示被「功能隐藏」藏起来的入口。
            // 放在悬浮按钮**之前** —— 角落有按钮时，点按钮不该被计进手势。
            PclHiddenPeekGesture(modifier = Modifier.align(Alignment.BottomEnd))
            // 右下角悬浮按钮（StackPanel Margin="15"）
            PclExtraButtons(
                modifier = Modifier.align(Alignment.BottomEnd).padding(PclDimens.extraButtonStackMargin),
                // PCL 的悬浮下载按钮是切到「下载管理」页（FormMain.xaml.vb:1603），不是弹窗
                onOpenDownloadTasks = { nav.push(pclDownloadManagerPage()) },
            )

            // ---- 内置浏览器层（最上面）
            // 正版登录的设备码验证页就在这一层里打开，关掉它 = 退出登录。
            runtime.webPage?.let { page ->
                PclWebOverlay(
                    page = page,
                    onClose = { runtime.closeWebPage() },
                )
            }
        }

        // 启动器错误信息（对应 Zalith 的错误对话框机制）
        //
        // 以前这里用的是 `PclMessageDialog` —— 一个「标题居中 + 2dp 细线 + 居中确定按钮」的
        // 临时拼装版，和 PCL 的 `MyMsgText` 差得挺明显（PCL 是标题左对齐 23 号、分隔线用标题色、
        // 按钮行右对齐）。现在统一走 `PclNoticeDialog`，它渲染的就是 `MyMsgText` 的骨架。
        runtime.pendingError?.let { tm ->
            val (title, message) = runtime.renderError(tm)
            PclNoticeDialog(
                title = title,
                content = message,
                onDismiss = { runtime.dismissError() },
            )
        }
    }
}

/* ============================================================================
 *  标题栏
 * ==========================================================================*/

@Composable
private fun PclTitleBar(nav: PclNavController) {
    val p = pclPalette
    val sub = nav.subPage
    val activity = LocalContext.current as? android.app.Activity
    // 标题栏三段渐变：Brush 只在调色板真正变化时重建一次，
    // 而不是每次重组都新造一个（标题栏所在的外壳重组频率并不低）。
    val titleBrush = remember(p.titleBarGradient) { Brush.horizontalGradient(p.titleBarGradient) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PclDimens.titleBarHeight)
            .background(titleBrush),
    ) {
        if (sub == null) {
            // PCL 描边 Logo / 自定义文本（`UiLogoType` + `UiLogoText` + `UiLogoLeft`）：
            // ShapeTitleLogo Width=39、Margin="19,15.5,0,15.5"（→ 高 17）；
            // 文本用 `LabTitleLogo`（FontSize=17、白色、Margin="18,0,0,0"）。
            val logoAlign = if (PclSkinSettings.logoLeft) Alignment.CenterStart else Alignment.Center
            when (PclSkinSettings.logoType) {
                PclLogoType.None -> Unit
                PclLogoType.Text -> Text(
                    text = PclSkinSettings.logoText.ifBlank { "PCL" },
                    style = PclTextStyle.base(p).copy(
                        fontSize = 17.sp,
                        lineHeight = PclTextStyle.lineHeight(17),
                        color = Color.White,
                    ),
                    modifier = Modifier.align(logoAlign).padding(start = PclDimens.titleLogoStart),
                )
                PclLogoType.Picture -> {
                    // `UiLogoType = 3`（图片）：用「个性化 → 标题栏」里选的图片。
                    // 解码走 IO 线程（图像解码是重活，放在组合里会卡住首帧），完成前退回描边 Logo。
                    val logoPath = PclAppearance.logoImage
                    val logoBitmap = produceState<ImageBitmap?>(initialValue = null, logoPath) {
                        value = withContext(Dispatchers.IO) {
                            PclImageLoader.load(logoPath, maxDim = 512)
                        }
                    }.value
                    if (logoBitmap != null) {
                        Image(
                            bitmap = logoBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .align(logoAlign)
                                .padding(start = PclDimens.titleLogoStart)
                                .height(PclDimens.titleLogoHeight)
                                .widthIn(max = 140.dp),
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = PclIcons.titleLogo,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(logoAlign)
                                .padding(start = PclDimens.titleLogoStart)
                                .size(width = PclDimens.titleLogoWidth, height = PclDimens.titleLogoHeight),
                        )
                    }
                }
                PclLogoType.Default -> androidx.compose.material3.Icon(
                    imageVector = PclIcons.titleLogo,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(logoAlign)
                        .padding(start = PclDimens.titleLogoStart)
                        .size(width = PclDimens.titleLogoWidth, height = PclDimens.titleLogoHeight),
                )
            }

            // 居中导航（PanTitleSelect，Grid 1*/Auto/1*；自身 Margin="13,0"）
            // `UiHiddenPage*` 隐藏的版块直接不出现在这一排里。
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = PclDimens.navBarSideMargin),
                horizontalArrangement = Arrangement.spacedBy(PclDimens.navPillSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclSection.entries.filter { !PclSkinSettings.shouldHide(it.hiddenKey) }.forEach { section ->
                    PclPillButton(
                        text = section.label,
                        checked = nav.section == section,
                        logo = section.icon,
                        // `MyRadioButton` 的 `ShapeLogo` 是 `MaxWidth/MaxHeight="16"` 的**正方形**框，
                        // WPF 的 `Stretch=Uniform` 已经把每个图标的**长边**统一铺到 16，
                        // 所以这里不再叠 PCL 写的 `LogoScale`（首页 0.9 / 下载 0.9 / 联机 1.05 /
                        // 设置 1.1 / 更多 0.93 —— 逐图标手调的经验值，照抄会让一排按钮一大一小）。
                        logoScale = 1f,
                        onClick = { nav.go(section) },
                    )
                }
            }
        } else {
            // 子页面标题栏（PanTitleInner）：返回键 Margin="12,0,0,0"、标题 Margin="47,1,60,0"、字号 15
            Row(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = PclDimens.titleInnerBackStart),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclIconButton(
                    logo = PclIcons.back,
                    theme = PclIconTheme.White,
                    size = PclDimens.titleButtonSize,
                    logoScale = 0.87f,
                    contentDescription = "返回",
                    onClick = { nav.back() },
                )
                Spacer(Modifier.width(PclDimens.titleInnerTitleStart - PclDimens.titleInnerBackStart - PclDimens.titleButtonSize))
                PclText(
                    text = sub.title,
                    color = p.color8,
                    fontSize = 15,
                )
            }
        }

        // 窗口按钮：BtnTitleMin Margin="0,0,44,0"、BtnTitleClose Margin="0,0,12,0"，均 28×28
        // → 关闭键右边缘距右边 12，最小化键右边缘距右边 44，两者间距 4
        Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
            PclIconButton(
                logo = PclIcons.minimize,
                theme = PclIconTheme.White,
                size = PclDimens.titleButtonSize,
                logoScale = 0.72f,
                contentDescription = "最小化",
                onClick = { activity?.moveTaskToBack(true) },
            )
            Spacer(Modifier.width(PclDimens.titleButtonGap))
            PclIconButton(
                logo = PclIcons.close,
                theme = PclIconTheme.White,
                size = PclDimens.titleButtonSize,
                logoScale = 0.72f,
                contentDescription = "关闭",
                onClick = { activity?.finish() },
                modifier = Modifier.padding(end = PclDimens.titleButtonMarginEnd),
            )
        }
    }
}

/* ============================================================================
 *  左栏宽度
 * ==========================================================================*/

/**
 * `MyListItem` 里「除标题文字以外」的固定宽度之和。
 *
 * 逐项对应 `PclListItem` 的实际布局（不要照抄 XAML 的数字，要以**渲染代码**为准）：
 * ```
 * 勾选列 checkColumnWidth（type = RadioBox → 6）   PclControls.kt:761-764
 * + 图标列 logoColumnWidth（有图标 34，高 < 40 不再加 4）  PclControls.kt:765
 * + 标题左边距 Column.padding(start = 4)            PclControls.kt:856
 * + 右侧让位 rightReserve（左栏统一 MinPaddingRight = 35）  PclControls.kt:783
 *   = 6 + 34 + 4 + 35 = 79
 * ```
 *
 * 这里曾经写 77（把勾选列当成 4、图标列当成 34 就直接相加了），比实际少 2dp；
 * 再加上宽度是按 13 号字量的、而条目实际按 14 号字排版（少 4dp），
 * 两者叠加正好让 4 个字的「原版游戏」差最后一个字放不下 —— 显示成「原版游」。
 */
private val PCL_LEFT_ITEM_FIXED_WIDTH = 79.dp

/**
 * 左栏宽度。
 *
 * PCL 里左栏**没有固定宽度**：`FormMain.xaml` 的 `PanMain` 第一列是 `Auto`，`MyPageLeft`
 * 自身不设宽，于是宽度由「最宽条目」撑出来 —— 就是上面那 77 + 标题实测宽度。
 * 全项目唯一写死宽度的是启动页（`PageLaunchLeft.xaml:8` 的 `Width="300"`）。
 *
 * 这里自己量文字而不用 Compose 的 `IntrinsicSize`：左栏外面套着滚动容器，
 * 而 `Modifier.verticalScroll` 在外层参与固有测量时行为不稳，直接量更可控。
 * 标题字体取 PCL 的全局默认 13（`Application.xaml:131/138/284/324` 的一串 Setter）。
 * 末尾 +1dp 是防浮点取整：量出来的宽度若比实际排版差一点点，最后两个字会被裁掉。
 */
@Composable
fun pclLeftPanelWidth(
    labels: List<String>,
    /**
     * 条目标题**实际渲染**用的字号。
     *
     * 必须与 `PclListItem(fontSize = ...)` 传的是同一个值。这里曾经写死 13、
     * 而左栏条目走的是 `MyListItem` 的默认值 14（`MyListItem.xaml.vb:191`），
     * 于是 4 个字的「原版游戏」算出来比实际排版窄了 4dp，
     * 最后一个字被 `maxLines = 1` 裁掉 —— 显示成「原版游」。
     */
    fontSize: Int = PclDimens.leftItemFontSize,
): Dp {
    val p = pclPalette
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // `listTitle` 是 @Composable，必须在组合里取；取出来的 TextStyle 是 data class，可当 key。
    val style = PclTextStyle.listTitle(p).copy(fontSize = fontSize.sp)
    // 量文字宽度是**测量级**开销（TextMeasurer 会走一遍排版），
    // 而 PclBody 的重组并不罕见（版块切换、左栏宽度动画、缩放变化……）。
    // 条目清单和字号没变时，结果就一定是同一个值，直接 remember 住。
    return remember(labels, style, density, measurer) {
        val widest = labels.maxOfOrNull { measurer.measure(it, style).size.width } ?: 0
        with(density) {
            // widest 是 **px**（TextMeasurer 的返回值），这里按 px 留 2px：dp↔px 要取整，
            // 只留 1dp 在 density > 1 的屏幕上反而更不稳。
            // 再补 4dp 余量：中文回退字形的实际宽度偶尔比 TextMeasurer 的估算略宽，
            // 多出来的只是标题右侧的空白，肉眼看不出来，但能保证最后一个字永远不被裁。
            PCL_LEFT_ITEM_FIXED_WIDTH + (widest + 2).toDp() + 4.dp
        }
    }
}

/* ============================================================================
 *  窗体主体：左侧栏 + 内容区
 * ==========================================================================*/

@Composable
private fun PclBody(nav: PclNavController) {
    val p = pclPalette
    val sub = nav.subPage

    // 侧栏宽度：PCL 里由「最宽条目」决定（见 pclLeftPanelWidth），
    // 只有启动页写死 300（PageLaunchLeft.xaml:8）、联机页没有侧栏。
    val rawWidth: Dp = when {
        sub != null -> when {
            sub.leftContent == null -> 0.dp
            sub.leftWidth != null -> sub.leftWidth
            sub.leftLabels.isNotEmpty() -> pclLeftPanelWidth(sub.leftLabels)
            else -> PclDimens.leftPanelNarrowWidth
        }
        nav.section == PclSection.Home -> PclDimens.leftPanelWidth
        nav.section == PclSection.Link -> 0.dp
        nav.section == PclSection.Download -> pclLeftPanelWidth(PclLeftLabels.download)
        nav.section == PclSection.Setup -> pclLeftPanelWidth(PclLeftLabels.setup)
        else -> pclLeftPanelWidth(PclLeftLabels.other)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 自适应：手机上把侧栏压到屏宽的 42% 以内，避免内容区被挤没
        val maxLeft = maxWidth * 0.42f

        // 左栏**内容**那一列的宽度：瞬时取值，不做动画。
        //
        // PCL 里内容在 `PanMainLeft`（`FormMain.xaml:154-172` 的 `PanMain`，第一列 `Auto`），
        // 列宽由内容撑出来、**从来没有动画**。有 180ms 宽度动画的是 `PanLeft` 里那个
        // 纯背景矩形 `RectLeftBackground`（`FormMain.xaml.vb:1533-1555`）—— 背景可以慢慢滑，
        // 内容列宽一直是一步到位的。
        //
        // 这里原来把动画做在了这一列上，等于让动画值参与布局。宽度是 `Row` 的布局参数，
        // 动画期间每帧都在变，于是：
        //   ① `PclBody` 每帧重组，而右栏内容是 `sub.content()` 这种字段 invoke，
        //      编译器不会给它生成可跳过的组 → **整页内容每帧重新组合一遍**；
        //   ② 内容区的宽度约束每帧都在变 → **整页文字每帧重新换行、整页每帧重新测量与绘制**。
        // 「每次切页帧率骤降」就是这么来的：一次切换要白白付 180ms × 每帧的整页开销。
        val leftWidth = if (rawWidth > maxLeft) maxLeft else rawWidth

        // PCL 的 `RectLeftBackground`：白色侧栏底色，180ms 缓动到新宽度。
        // 关键是**不在组合里用 `by` 读它** —— 只在绘制阶段读，于是它既不触发重组、
        // 也不参与布局，每帧的成本只是重画一个矩形，和原版同量级。
        val leftBgWidth = animateDpAsState(
            targetValue = leftWidth,
            animationSpec = tween(180, easing = PclEase.outFluentExtraStrong),
            label = "pclLeftBgWidth",
        )
        val leftBarShadow = remember {
            Brush.horizontalGradient(listOf(Color(0x0A000000), Color.Transparent))
        }
        val leftBarShadowWidth = with(LocalDensity.current) { 4.dp.toPx() }

        // 「启动面板位置」只在启动页（且没进二级页）生效，别的版块左栏仍然在左边。
        val homePanelOnRight =
            sub == null && nav.section == PclSection.Home && PclSkinSettings.homePanelOnRight

        Box(modifier = Modifier.fillMaxSize()) {
            // 白色侧栏底 + 右侧 4dp 阴影（`RectLeftBackground` / `RectLeftShadow`）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val w = leftBgWidth.value.toPx()
                        if (w <= 0f) return@drawBehind
                        if (homePanelOnRight) {
                            // 面板挪到右边：底色条贴在右侧，阴影落在它的左边
                            val x = size.width - w
                            drawRect(color = p.sidebarBackground, topLeft = Offset(x, 0f), size = Size(w, size.height))
                            drawRect(
                                brush = leftBarShadow,
                                topLeft = Offset(x - leftBarShadowWidth, 0f),
                                size = Size(leftBarShadowWidth, size.height),
                            )
                        } else {
                            drawRect(color = p.sidebarBackground, size = Size(w, size.height))
                            drawRect(
                                brush = leftBarShadow,
                                topLeft = Offset(w, 0f),
                                size = Size(leftBarShadowWidth, size.height),
                            )
                        }
                    },
            )
            Row(modifier = Modifier.fillMaxSize()) {
                // 启动页可以把「启动面板」挪到右边（设置 → 个性化 → 启动面板位置）：
                // 只换两块的**位置**，宽度与内容都不变，所以下面用同一段 lambda 组装两次。
                val panelFirst = homePanelOnRight.not()
                if (panelFirst) {
                    // 左栏内容所在列（宽度瞬时，与 PCL 的 `PanMainLeft` 一致）
                    Box(modifier = Modifier.width(leftWidth).fillMaxHeight()) {
                        // MyPageLeft.TriggerShowAnimation：左栏整页重建时才逐个入场，
                        // 因此把左栏内容按 sectionToken 分组；点击左栏条目不会重建它（与 PCL 一致）。
                        key(nav.sectionToken) {
                            if (sub != null) {
                                sub.leftContent?.invoke()
                            } else {
                                when (nav.section) {
                                    PclSection.Home -> PclHomeLeft(nav)
                                    PclSection.Download -> PclDownloadLeft(nav)
                                    PclSection.Setup -> PclSetupLeft(nav)
                                    PclSection.Other -> PclOtherLeft(nav)
                                    PclSection.Link -> Unit // 联机页无左栏
                                }
                            }
                        }
                    }

                    // 右侧内容
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (sub != null) {
                            sub.content()
                        } else {
                            when (nav.section) {
                                PclSection.Home -> PclHomeRight(nav)
                                PclSection.Download -> PclDownloadRight(nav)
                                PclSection.Link -> PclLinkRight(nav)
                                PclSection.Setup -> PclSetupRight(nav)
                                PclSection.Other -> PclOtherRight(nav)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PclHomeRight(nav)
                    }
                    Box(modifier = Modifier.width(leftWidth).fillMaxHeight()) {
                        key(nav.sectionToken) { PclHomeLeft(nav) }
                    }
                }
            }
        }
    }
}

/* ============================================================================
 *  内容区滚动容器（带 PCL 滚动条）
 * ==========================================================================*/

/**
 * PCL 右侧信息流的滚动容器：内容外边距 `25,25,25,10`，滚动条宽 8、圆角 2。
 */
@Composable
fun PclContent(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = PclDimens.contentSide,
        top = PclDimens.contentTop,
        end = PclDimens.contentSide,
        bottom = PclDimens.contentBottom,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberScrollState()
    val scrollTopToken = PclScrollSignal.token
    LaunchedEffect(scrollTopToken) {
        if (scrollTopToken > 0) state.animateScrollTo(0)
    }
    // 视口高度：只在尺寸真正变化时写一次。
    var viewportPx by remember { mutableIntStateOf(0) }

    // 回写滚动位置：悬浮的「回到顶部」按钮靠它决定是否显示。
    //
    // 性能：这里**不能**把 `state.value` 写成 `LaunchedEffect` 的 key。那样每滚一帧都会
    // 「取消协程 + 重启协程」；更要命的是 `state.value` 是在 `PclContent` 自己的组合作用域里
    // 被读的，读它等于让 **整个 PclContent 每帧重组** —— 而 PclContent 下面挂着整页内容
    // （全是 Column，没有任何懒加载），于是「手指一滑 = 整页重新组合一遍」。
    // 这是滚动卡顿的第一大来源。改用 `snapshotFlow` 订阅：协程只起一次，
    // 值的变化只在协程里被消费，完全不触碰组合。
    LaunchedEffect(state) {
        snapshotFlow { state.value to viewportPx }
            .collect { (value, viewport) -> PclScrollSignal.updateScroll(value, viewport) }
    }

    val enter = rememberPclPageEnter()

    Box(
        modifier = modifier.fillMaxSize().onSizeChanged { viewportPx = it.height },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // `graphicsLayer` **必须在 `verticalScroll` 之外**。
                //
                // 它在最内层时作用于 Column 的内容节点，而这个节点的尺寸是
                // **整页内容全高**（`verticalScroll` 给子项的是无限高度约束）——
                // 页面一长就是上万像素。`alpha < 1` 的每一帧都要按节点尺寸做一次离屏合成，
                // 100ms 的淡入里等于每帧合成一张上万像素高的图层。
                //
                // 放到滚动容器外之后，图层尺寸退回**视口**大小，合成量降一个数量级；
                // 位移仍会让整页（含裁剪框）一起从上方 -16 滑入，视觉与原来一致。
                //
                // 另外，这里的两个值都是 **`State` 在绘制阶段读**（见 [rememberPclPageEnter]），
                // 所以入场动画的 350ms 里一次重组都不会发生，只是每帧改一次图层参数。
                .graphicsLayer {
                    alpha = enter.fade.value
                    translationY = PclMotion.pageEnterOffsetY.toPx() * (1f - enter.move.value)
                }
                .verticalScroll(state)
                .padding(contentPadding),
            content = content,
        )
        PclScrollBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .padding(1.dp)
                .graphicsLayer {
                    translationX = PclMotion.scrollBarEnterOffsetX.toPx() * (1f - enter.bar.value)
                },
        )
    }
}

/**
 * `MyPageRight` 的「内容居中、不滚动」版。
 *
 * `PageInstanceModDisabled` 这类整页提示卡的根节点在原版里同样是 `MyPageRight`
 * （`PageInstanceModDisabled.xaml` 第一行），只是里面没有 `MyScrollViewer`：
 * 卡片自己 `HorizontalAlignment/VerticalAlignment=Center` 居中，
 * 所以既没有滚动条、也不做滚动条入场动画，但**页面入场动画是有的**
 * （`MyPageRight.vb:392 TriggerEnterAnimation`：内容从上方 -16 滑入 + 淡入）。
 *
 * 之前这张提示卡用的是裸 `Box`，进出这一页完全没有过渡，
 * 和别的页面比起来就像「啪」一下跳出来 —— 这里补齐与 [PclContent] 同一套入场动画。
 */
@Composable
fun PclCenteredPage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val enter = rememberPclPageEnter()
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enter.fade.value
                translationY = PclMotion.pageEnterOffsetY.toPx() * (1f - enter.move.value)
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * `MyPageRight.TriggerEnterAnimation` 的页面入场动画（`Controls/MyPageRight.vb:392`）：
 * 淡入 100ms（EaseOutFluent）+ 位移 -16 → 0，350ms（EaseOutBack，收尾带轻微过冲）；
 * 滚动条另有 TranslateTransform(10, 0) → 0 的 350ms 动画。
 *
 * 返回的是 **`State`，故意不在组合里读 `value`**：调用方只在 `Modifier.graphicsLayer {}`
 * 里读，读取落在图层阶段，动画每帧只改图层参数，**不触发重组**。
 * （原来用 `by` 在组合里读，350ms 里每一帧都把整页内容重新组合一遍 ——
 *  这也是切页时掉帧的一部分。）
 */
@Composable
private fun rememberPclPageEnter(): PclPageEnter {
    val pageToken = LocalPclNav.current.pageToken
    var entered by remember(pageToken) { mutableStateOf(false) }
    LaunchedEffect(pageToken) { entered = true }

    val target = if (entered) 1f else 0f
    // 注意：`animateFloatAsState` 是 @Composable，不能塞进 `remember {}` 里调用，
    // 这里就直接调、把三个 State 包成一个小对象返回（本函数只在 entered 翻转时重组一次）。
    return PclPageEnter(
        move = animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = PclMotion.PAGE_ENTER_DURATION,
                easing = PclMotion.easeOutBack,
            ),
            label = "pclPageEnter",
        ),
        fade = animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = PclMotion.PAGE_ENTER_FADE_DURATION,
                easing = PclMotion.easeOutFluent,
            ),
            label = "pclPageFade",
        ),
        bar = animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = PclMotion.SCROLLBAR_ENTER_DURATION,
                easing = PclMotion.easeOutFluent,
            ),
            label = "pclScrollBarEnter",
        ),
    )
}

/** [rememberPclPageEnter] 的三条入场动画当前值，仅供 `graphicsLayer` 在绘制阶段读。 */
private class PclPageEnter(
    val move: State<Float>,
    val fade: State<Float>,
    val bar: State<Float>,
)

/** `MyScrollBar` —— 8dp 宽，底色 #01eaf2fe，指示块为主题色、圆角 2、内缩 2。 */
@Composable
private fun PclScrollBar(
    state: ScrollState,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    androidx.compose.foundation.Canvas(modifier = modifier.width(PclDimens.scrollBarWidth)) {
        // 滚动状态在**绘制阶段**读（`Canvas` 的 draw lambda 里），不是在组合阶段。
        // 于是滚动只会让这一层重绘（invalidate），不会让 PclContent / 整页内容重组；
        // 顺带也省掉了「滚动条位置作为参数逐帧传入」导致的参数变化。
        val scrollMax = state.maxValue
        if (scrollMax <= 0) return@Canvas
        val scrollValue = state.value
        val viewport = size.height
        val content = viewport + scrollMax
        if (content <= 0f) return@Canvas
        val thumbHeight = (viewport * viewport / content).coerceAtLeast(24.dp.toPx())
        val maxOffset = (viewport - thumbHeight).coerceAtLeast(0f)
        val offset = maxOffset * (scrollValue.toFloat() / scrollMax)
        // 轨道
        drawRect(color = p.semiTransparent, size = size)
        // 指示块（外框 + 内块，内缩 2）
        drawRoundRect(
            color = p.color4,
            topLeft = androidx.compose.ui.geometry.Offset(2.dp.toPx(), offset + 2.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                (size.width - 4.dp.toPx()).coerceAtLeast(1f),
                (thumbHeight - 4.dp.toPx()).coerceAtLeast(1f),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        )
    }
}

/* ============================================================================
 *  左下角提示条 / 右下角悬浮按钮
 * ==========================================================================*/

/** 供页面推送提示条（对应 PCL 的 `Hint` 机制）。 */
object PclHintHost {
    /** 一条提示（用自增 id 标识，避免按序号删除时错删）。 */
    class Hint(
        val id: Long,
        val text: String,
        val theme: com.zalith.pcl.ui.pcl2.components.PclHintTheme,
    )

    private var nextId = 0L
    private val hints = mutableStateOf<List<Hint>>(emptyList())

    /** PCL 的 `HintTick`：`PanHint.Children.Count >= 20` 时新提示直接忽略 */
    private const val MAX_HINTS = 20

    val list: List<Hint> get() = hints.value

    fun push(text: String, theme: com.zalith.pcl.ui.pcl2.components.PclHintTheme) {
        val current = hints.value

        // PCL 的 HintTick 会先查重：同一条文字已经在屏上时**不再新加一条**，
        // 而是让已有那条重新播一遍动画、并把消失计时重置。
        // 这里用「换一个 id」实现同样的效果 —— 动画与计时都是按 id 做 key 的。
        val duplicated = current.firstOrNull { it.text == text }
        if (duplicated != null) {
            hints.value = current.map { hint ->
                if (hint.id == duplicated.id) Hint(nextId++, text, theme) else hint
            }
            return
        }

        if (current.size >= MAX_HINTS) return
        hints.value = current + Hint(nextId++, text, theme)
    }

    /** 提示条自动消失（PCL 的提示条没有关闭按钮，到点自己走）。 */
    fun remove(id: Long) {
        hints.value = hints.value.filterNot { it.id == id }
    }

    fun clear() {
        hints.value = emptyList()
    }
}

/**
 * 左下角提示条栈（`FormMain.PanHint`，`Margin="0,0,0,20"`、左对齐）。
 *
 * 样式照抄 `ModMain.vb:61-130`：
 *   · 高 26、`CornerRadius=(0,6,6,0)`（左边两角是直角，贴住窗口左边）、
 *     文字 13 号白色、`Margin="33,5,8,5"`、单行省略；
 *   · 底色是**竖向渐变**（`LinearGradientBrush(..., 90)`），三套配色都带 215/255 的不透明度：
 *       蓝 rgb(37,155,252) → rgb(10,142,252)
 *       绿 rgb(33,177,33)  → rgb(29,160,29)
 *       红 rgb(255,53,11)  → rgb(255,43,0)（PCL 的 `Case Else`，所以黄色也归到红）
 *   · 同一条文字重复弹出时不会叠成两条，而是让已有那条重新播动画（见 [PclHintHost.push]）；
 *   · 显示时长 = `(800 + 文字长度.Clamp(5,23) * 180) ms`，没有关闭按钮。
 */
@Composable
private fun PclHintStack(modifier: Modifier = Modifier) {
    val items = PclHintHost.list
    if (items.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items.forEach { hint ->
            PclHintToast(
                id = hint.id,
                text = hint.text,
                theme = hint.theme,
                onTimeout = { PclHintHost.remove(hint.id) },
            )
        }
    }
}

/** 一条提示条（自动消失）。 */
@Composable
private fun PclHintToast(
    id: Long,
    text: String,
    theme: com.zalith.pcl.ui.pcl2.components.PclHintTheme,
    onTimeout: () -> Unit,
) {
    // 渐变颜色（alpha 215/255），**顺序必须写成具名参数**：
    // PCL 的 `New MyColor(215, 37, 155, 252)` 是「透明度在前」的 (a,r,g,b)，
    // 直接搬进 Compose 的 Color(r,g,b,a) 会把透明度当成红色分量
    // —— 整条提示条会变成一层 13% 不透明的洋红，白字几乎看不见。
    val (top, bottom) = when (theme) {
        com.zalith.pcl.ui.pcl2.components.PclHintTheme.Green ->
            Color(red = 33, green = 177, blue = 33, alpha = 215) to
                    Color(red = 29, green = 160, blue = 29, alpha = 215)

        com.zalith.pcl.ui.pcl2.components.PclHintTheme.Red,
        com.zalith.pcl.ui.pcl2.components.PclHintTheme.Yellow ->
            Color(red = 255, green = 53, blue = 11, alpha = 215) to
                    Color(red = 255, green = 43, blue = 0, alpha = 215)

        else ->
            Color(red = 37, green = 155, blue = 252, alpha = 215) to
                    Color(red = 10, green = 142, blue = 252, alpha = 215)
    }

    // 入场：X 30 → 20（200ms）+ 淡入 100ms，缓动走 PCL 原式 `AniEaseOutFluent`（`1-(1-t)^3`）。
    // key 用 id 而不是文字：同一条文字重复弹出时 id 会换新，动画与计时都要跟着重来。
    var entered by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { entered = true }
    val offsetX by animateFloatAsState(
        targetValue = if (entered) 20f else 30f,
        animationSpec = tween(200, easing = PclEase.outFluent),
        label = "pclHintOffset",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(100),
        label = "pclHintAlpha",
    )

    // ---- 退场（`Modules/ModMain.vb` 的那组 `Hint Hide` 动画）：
    //   AaX(Control, -50, 200, Delay, AniEaseInFluent)          ' 再往左滑 50 出屏
    //   AaOpacity(Control, -1, 150, Delay)                      ' 淡出
    //   AaCode(Tag(0) = False, Delay)
    //   AaHeight(Control, -26, 100, AniEaseOutFluent, After)    ' 高度收起
    //   AaCode(移除, After)
    // 因此**不能**一到时间就直接 remove —— 得先把动画播完再摘掉。
    var exiting by remember(id) { mutableStateOf(false) }
    val exitSlide = remember { Animatable(0f) }   // 0 → 1（左移 50dp）
    val exitFade = remember { Animatable(1f) }    // 1 → 0
    val collapse = remember { Animatable(0f) }    // 0 → 1（高度 26 → 0）

    // 自动消失：PCL 的时长公式
    LaunchedEffect(id) {
        delay((800 + text.length.coerceIn(5, 23) * 180).toLong())
        exiting = true
    }
    LaunchedEffect(exiting) {
        if (!exiting) return@LaunchedEffect
        coroutineScope {
            launch { exitSlide.animateTo(1f, tween(200, easing = PclEase.inFluentWeak)) }
            launch { exitFade.animateTo(0f, tween(150)) }
        }
        // X 先到位（200ms），再收起高度（100ms），最后才交给宿主移除
        collapse.animateTo(1f, tween(100, easing = PclEase.outFluent))
        onTimeout()
    }

    Box(
        modifier = Modifier
            .offset(x = (-20).dp + offsetX.dp - (50f * exitSlide.value).dp)
            .height(26.dp * (1f - collapse.value))
            .graphicsLayer(alpha = alpha * exitFade.value)
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 6.dp, bottomEnd = 6.dp, bottomStart = 0.dp))
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Start,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 33.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        )
    }
}

/**
 * 右下角的「连点 10 次」隐形热区：**临时**显示被「功能隐藏」勾掉的那些入口。
 *
 * 为什么需要这个：整块开关能把「设置」自己藏掉，而恢复它的开关就在设置页里 ——
 * 勾完等于把自己锁在门外。所以换个路子：勾选状态一个字不改，只是把「画不画」的
 * 判断临时放开 [PclSkinSettings.PEEK_DURATION_MS]，人趁这段时间进设置取消勾选，
 * 时间一到入口自动重新藏好。
 *
 * 命中后：
 *   · 一个被隐藏的入口都没有时，直接说明「当前没有隐藏功能」，不做无意义的闪烁。
 *   · 有的话，顺带把用户带到「设置 → 个性化」——“功能隐藏”就在那页底部；
 *     就算「设置」自己被藏了，顶栏那一条正在临时显示，照样点得进去。
 *   · 左下角给一条提示，写清「临时」「多久」「怎么提前收起」。
 */
@Composable
private fun PclHiddenPeekGesture(modifier: Modifier = Modifier) {
    val nav = LocalPclNav.current
    val interaction = remember { MutableInteractionSource() }
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            // 44dp：吸附在右下角，够手指连点，又不会挡到大片内容
            .size(PEEK_HOTSPOT)
            .clickable(interactionSource = interaction, indication = null) {
                val now = SystemClock.elapsedRealtime()
                // 两次点击隔太久就重新计数 —— 否则「一天里点了 10 次」也会触发
                taps = if (now - lastTapAt > PEEK_TAP_WINDOW_MS) 1 else taps + 1
                lastTapAt = now
                if (taps < PclSkinSettings.PEEK_TAP_COUNT) return@clickable
                taps = 0

                val hiddenCount = PclSkinSettings.hiddenCount
                if (hiddenCount == 0) {
                    PclHintHost.push("当前没有被隐藏的功能。", PclHintTheme.Blue)
                    return@clickable
                }

                // 已经在临时显示里，再点十次就当「提前收起」—— 免得人不知道怎么关掉
                if (PclSkinSettings.isPeeking()) {
                    PclSkinSettings.stopPeek()
                    PclHintHost.push(
                        "已收起临时显示，$hiddenCount 个入口重新隐藏。",
                        PclHintTheme.Blue,
                    )
                    return@clickable
                }

                PclSkinSettings.startPeek()
                PclHintHost.push(
                    "已临时显示 $hiddenCount 个被隐藏的入口，" +
                            "${PclSkinSettings.PEEK_DURATION_MS / 1000} 秒后自动隐藏。" +
                            "去「设置 → 个性化 → 功能隐藏」取消勾选就能永久恢复；" +
                            "再连点右下角 ${PclSkinSettings.PEEK_TAP_COUNT} 次可提前收起。",
                    PclHintTheme.Green,
                )
                // 「功能隐藏」在「设置 → 个性化」（左栏第 3 项）底部
                nav.go(PclSection.Setup, 2)
            }
    )
}

/** 「临时显示」手势：单次计数窗口 / 热区大小（连点次数见 `PclSkinSettings`）。 */
private const val PEEK_TAP_WINDOW_MS = 1_500L
private val PEEK_HOTSPOT = 44.dp

/** 右下角悬浮按钮（`MyExtraButton`：40×40 圆形、内缩 10、主题色底 + 10px 模糊阴影、白图标 16）。 */
@Composable
private fun PclExtraButtons(
    modifier: Modifier = Modifier,
    onOpenDownloadTasks: () -> Unit,
) {
    val p = pclPalette
    val runtime = LocalPclRuntime.current
    val nav = LocalPclNav.current
    val density = LocalDensity.current
    val downloadTasks by runtime.downloadTasks.collectAsStateWithLifecycle()
    val installTasks by runtime.installTasks.collectAsStateWithLifecycle()
    val modpackTasks by runtime.modpackTaskList.collectAsStateWithLifecycle()
    val modpackActive by runtime.isImportingModpack.collectAsStateWithLifecycle()
    val modpackDownloads by runtime.modpackDownloadTasks.collectAsStateWithLifecycle()
    // 角标要把「游戏安装」「整合包安装」也算进去，否则点了「后台运行」之后看不出还有任务在跑。
    // 整合包的行（下载段 + 安装段）由 `modpackTaskList` 一并给出：它的下载任务已经在其中，
    // 所以这里要把它从普通下载里按 id 集合整批剔掉，免得同一次下载数两遍。
    // 刚登记、行还没吐出来的那一瞬（`modpackActive` 为真但列表还空）补一位，避免角标闪 0。
    val modpackDownloadIds = modpackDownloads.map { it.id }.toSet()
    val taskCount = downloadTasks.count { it.id !in modpackDownloadIds } +
        installTasks.size + maxOf(modpackTasks.size, if (modpackActive) 1 else 0)

    // PCL 的悬浮按钮都是「用到才出现」（FormMain.xaml.vb 的 ShowCheck）：
    //   · 回到顶部：页面往下滚过一屏之后才出现（首次显示还多留 700px 的迟滞量，这里按 dp 折算）
    //   · 下载管理：有下载/安装任务、且当前不在下载管理页时出现
    //   （PCL 的第三个按钮是「关闭 Minecraft」，只在游戏运行中显示；
    //     Zalith 没有暴露游戏进程状态，所以这里不做那个按钮，原来那个「刷新版本列表」
    //     是自造的、没有「适用条件」，已移除。）
    // 悬浮按钮的显隐只看滚动位置，但**不能**让它跟着每一帧滚动重组。
    // 用 `derivedStateOf` 把「是否越过阈值」压成一个布尔：阈值内的每帧变化只在快照层重算，
    // 只有**跨过阈值那一刻**才通知组合 —— 重组次数从「每帧一次」降到「每次跨越一次」。
    val hysteresis = with(density) { 200.dp.toPx() }
    var backShown by remember { mutableStateOf(false) }
    val shouldShowBack by remember(hysteresis) {
        derivedStateOf {
            PclScrollSignal.offset > PclScrollSignal.viewport + if (backShown) 0f else hysteresis
        }
    }
    LaunchedEffect(shouldShowBack) { backShown = shouldShowBack }
    val showDownload = taskCount > 0 && nav.subPage?.title != PclStrings.LangPageNameDownloadManagement

    val buttons = buildList {
        if (shouldShowBack) add(Triple("回到顶部", PclIcons.extraBackToTop, { PclScrollSignal.request() }))
        if (showDownload) add(Triple("下载管理", PclIcons.navDownload, onOpenDownloadTasks))
    }
    if (buttons.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PclDimens.extraButtonGap)) {
        buttons.forEach { (tip, icon, action) ->
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(PclDimens.extraButtonSize)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(p.color3)
                        .clickable { action() },
                    contentAlignment = Alignment.Center,
                ) {
                    // Path Margin="12" → 图标区 40−12×2 = 16
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = tip,
                        tint = p.color8,
                        modifier = Modifier.size(16.dp),
                    )
                }
                // 有下载/安装任务时在「下载管理」上显示数量角标
                if (tip == "下载管理" && taskCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(p.gray1)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = taskCount.toString(),
                            style = PclTextStyle.base(p).copy(
                                fontSize = 11.sp,
                                color = p.color8,
                            ),
                        )
                    }
                }
            }
        }
    }
}
