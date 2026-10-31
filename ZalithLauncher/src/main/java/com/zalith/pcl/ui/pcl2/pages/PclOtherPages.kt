/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「更多」版块的右侧页面，对应 PCL2 的：
 *   PageOtherHelp.xaml       帮助（搜索框 + 帮助列表 + 加载卡片）
 *   PageOtherAbout.xaml      关于与鸣谢（关于 / 特别鸣谢 / 法律信息 / 许可与版权声明）
 *   PageOtherTest.xaml       百宝箱（居中单行提示）
 *
 * 卡片结构与文案取自原 XAML 与 zh-CN.xaml。
 * 「关于」与「反馈」两页已经换成 Zalith 的真实信息与真实链接
 * （版本号来自 BuildConfig，链接来自 UrlManager，点击即跳转浏览器），
 * PCL 专有的「新功能投票」在 Zalith 没有后端，改为真实的社区与反馈入口。
 *
 * 「关于」/「特别鸣谢」两卡按 `PageOtherAbout.xaml` 的原始版式重写为
 * 「圆形头像 + 标题/副标题 + 右侧按钮」的列表行；「关于」里的作者依次是
 * **本移植版作者 zuanxu**（`img_avatar_zuanxu`）、**龙腾猫跃**（`img_avatar_ltcat`，
 * PCL2 作者，按钮「打开主页」→ github.com/LTCatt）与 **墨北Mov**
 * （`img_avatar_movtery`，Zalith Launcher 2 作者，按钮「打开主页」→ github.com/MovTery）；
 * 鸣谢对象按 Zalith 实际依赖重排。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.app.Activity
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.BuildConfig
import com.zalith.pcl.R
import com.zalith.pcl.path.URL_BANGBANG93_SPONSOR
import com.zalith.pcl.path.URL_COMMUNITY
import com.zalith.pcl.path.URL_EASYTIER_GITHUB
import com.zalith.pcl.path.URL_FCL
import com.zalith.pcl.path.URL_GITHUB_DRIVER_PLUGINS
import com.zalith.pcl.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import com.zalith.pcl.path.URL_GITHUB_RENDERER_PLUGINS
import com.zalith.pcl.path.URL_HMCL
import com.zalith.pcl.path.URL_LTCATT
import com.zalith.pcl.path.URL_MCIM
import com.zalith.pcl.path.URL_MCMOD
import com.zalith.pcl.path.URL_MOVTERY
import com.zalith.pcl.path.URL_PCL2
import com.zalith.pcl.path.URL_POJAV
import com.zalith.pcl.path.URL_PROJECT
import com.zalith.pcl.path.URL_SUPPORT
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.filemanager.FileManagerLauncher
import com.zalith.pcl.game.download.engine.DownloadEngine
import com.zalith.pcl.game.download.engine.DownloadRequest
import com.zalith.pcl.game.path.GamePathManager
import com.zalith.pcl.path.URL_WEBLATE
import com.zalith.pcl.upgrade.TooFrequentOperationException
import com.zalith.pcl.viewmodel.LauncherUpgradeViewModel
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.pcl2.components.PclBottomSpacer
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclPrank
import com.zalith.pcl.ui.pcl2.components.PclThreeOkDialog
import com.zalith.pcl.ui.pcl2.components.PclEmptyState
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclNoticeDialog
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclSplitLine
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.data.rememberRemoteText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import com.zalith.pcl.utils.network.openLinkInternal

/** 在浏览器里打开一个真实链接。对同包的更新宿主（PclUpgradeHost）开放。 */
internal fun openLink(context: android.content.Context?, url: String) {
    val activity = context as? Activity ?: return
    runCatching {
        activity.openLinkInternal(url)
    }.onFailure {
        PclHintHost.push("无法打开链接：$url", PclHintTheme.Red)
    }
}

/* ============================================================================
 *  帮助
 * ==========================================================================*/

/** `PageOtherHelp` —— 顶部搜索框（Margin 0,0,0,15）+ 帮助条目卡片列表。 */
@Composable
fun PclOtherHelpPage() {
    var keyword by remember { mutableStateOf("") }
    val entries = listOf(
        "如何安装 Minecraft？" to "在「下载 → 原版游戏」中选择游戏版本与加载器，点击「开始下载」。",
        "如何安装 Forge / Fabric？" to "在「下载 → 原版游戏」的安装页里展开对应加载器卡片，选择一个版本后再开始下载。",
        "如何修改游戏内存？" to "「设置 → 启动 → 内存分配」可选择自动或拖动滑杆自定义；这里写入的是 Zalith 的真实设置。",
        "如何更换渲染器？" to "「设置 → 启动 → 渲染器」中选择设备上已安装的渲染器插件。",
        "如何安装 Mod？" to "「下载 → Mod」搜索后下载，或「版本管理 → Mod 管理」查看与启停已安装的 Mod。",
        "联机功能怎么用？" to "在「联机」页创建房间并复制邀请码，好友凭邀请码加入；也可在「设置 → 联机」自定义节点。",
        "启动失败怎么办？" to "首页右侧会显示实时启动日志；也可以在「版本管理 → 设置」里关闭完整性检查后重试。",
    ).filter { keyword.isEmpty() || it.first.contains(keyword) || it.second.contains(keyword) }

    PclContent {
        PclSearchBox(
            value = keyword,
            modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
            hint = PclStrings.LangPageOtherHelpSearch,
            onValueChange = { keyword = it },
        )
        PclHint(text = PclStrings.LangPageOtherHelpSimpleChineseOnly)
        Spacer(Modifier.height(15.dp))
        entries.forEach { (title, body) ->
            PclCard( modifier = Modifier.fillMaxWidth(),title = title) {
                PclText(text = body, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(15.dp))
        }
        if (entries.isEmpty()) {
            PclEmptyState(
                title = PclStrings.LangPageOtherHelpSearch,
                content = "没有找到与「$keyword」相关的帮助内容。",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        PclBottomSpacer()
    }
}

/* ============================================================================
 *  关于
 * ==========================================================================*/

/**
 * 「关于 / 特别鸣谢」里的一行：圆形头像 + 标题 / 副标题 + 右侧按钮。
 *
 * 版式取自 PCL2 `PageOtherAbout.xaml` 的 `Grid`：
 *   `ColumnDefinition Width="40"`  —— 头像列（图片 34dp，左右各 `Margin="3"` 后正好 40）
 *   `ColumnDefinition`（星号）      —— `MyListItem`：Title 13 号、Info 12 号灰字
 *   `ColumnDefinition Width="150"` —— 右侧 35dp 高的 `MyButton`
 * 每行 `RowDefinition Height="45"`。
 *
 * 头像在 PCL2 里用 `EllipseGeometry RadiusX/Y="17"` 裁成圆，
 * 这里等价成 34dp 的 `CircleShape` 裁剪；位图走 `ContentScale.Crop`，
 * 保证非正方形头像也不会被压扁。
 *
 * 行高：PCL2 桌面端一律 45，但手机竖屏的文字列比桌面窄得多，
 * 12 号的副标题很容易折成两行。所以这里写的是 `heightIn(min = 45.dp)` 而不是硬高度：
 * 上下各留 5dp 内边距后内容区下限正好 35dp（= 按钮高），一般行就是 45dp；
 * 副标题真的折行时这一行才长高，避免中文下半截被 45dp 的硬高度裁掉。
 */
@Composable
private fun PclAboutRow(
    icon: Painter,
    title: String,
    info: String,
    buttonText: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val p = pclPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 45.dp)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 15.dp)) {
            PclText(text = title, fontSize = 13)
            PclText(text = info, fontSize = 12, color = p.gray2)
        }
        PclButton(
            text = buttonText,
            colorState = if (highlighted) PclColorState.Highlight else PclColorState.Normal,
            onClick = onClick,
        )
    }
}

/**
 * `PageOtherAbout` —— 关于与鸣谢。
 *
 * 与 PCL2 原版的两点差异（都是有意的）：
 *   · 作者一栏换成**本移植版的作者 zuanxu** 与其头像；
 *   · 其后依次列出**两位上游作者**：龙腾猫跃（PCL2）与墨北Mov（Zalith Launcher 2），
 *     两行的按钮都是「打开主页」，分别跳转各自的 GitHub 个人主页
 *     （`URL_LTCATT` / `URL_MOVTERY`）。
 * 鸣谢对象按 Zalith 实际依赖的上下游重排，按钮全部指向真实可访问的页面
 * （版本号来自 BuildConfig，链接来自 UrlManager）。
 */
@Composable
fun PclOtherAboutPage() {
    val context = LocalContext.current
    // Activity 作用域的更新 ViewModel：与外壳上的 `PclUpgradeHost` 是**同一个实例**，
    // 所以这里检查到新版本时，那个宿主会直接弹出 PCL 风格的更新框
    // （上游只有 MainActivity 渲染更新弹窗，本界面在 Pcl2Activity，之前点完毫无反应）。
    val scope = rememberCoroutineScope()
    val upgradeViewModel: LauncherUpgradeViewModel = viewModel()
    // 手动检查的状态：按钮文字改成「检查中…」，结果用弹窗给出（不再依赖左下角提示条）。
    var checking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    PclContent {
        // ---- 关于
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangPageOtherAboutAbout) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_zuanxu),
                    title = "zuanxu",
                    info = "Zalith-Plain Craft Launcher 的作者",
                    buttonText = "打开主页",
                    highlighted = true,
                ) { openLink(context, URL_PROJECT) }

                // PCL2 原版关于页的作者栏就是这一行（LTCat.jpg + 「赞助 PCL」），
                // 这里按用户要求改成「打开主页」→ 龙腾猫跃本人的 GitHub。
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_ltcat),
                    title = "龙腾猫跃",
                    info = "Plain Craft Launcher 2 的作者！",
                    buttonText = "打开主页",
                ) { openLink(context, URL_LTCATT) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_movtery),
                    title = "墨北Mov",
                    info = "Zalith Launcher 2 的作者！",
                    buttonText = "打开主页",
                ) { openLink(context, URL_MOVTERY) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher),
                    title = "Zalith Launcher 2",
                    info = "Zalith-Plain Craft Launcher 的运行内核 · 版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}",
                    buttonText = "开源代码",
                ) { openLink(context, "https://github.com/ZalithLauncher/ZalithLauncher2") }

                PclAboutRow(
                    icon = painterResource(R.drawable.ic_launcher_logo),
                    title = "Zalith-Plain Craft Launcher",
                    info = "本启动器：基于 Zalith Launcher 2 与 PCL2 界面打造的 Minecraft Java 版启动器",
                    buttonText = if (checking) "检查中…" else "检查更新",
                ) {
                    // 检查更新必须**当场给出可见结果**：以前只往左下角推一条提示条，
                    // 而「已是最新」「网络失败」这类结果一闪就没了，用户点完的感受就是
                    // 「点了没反应，连个弹窗都没有」。现在统一弹 PCL 提示框。
                    if (checking) return@PclAboutRow
                    scope.launch {
                        checking = true
                        try {
                            val finished = withTimeoutOrNull(90_000) {
                                upgradeViewModel.checkManually(
                                    onChecked = { data ->
                                        notice = when {
                                            data == null ->
                                                "获取更新失败，请检查网络后重试。\n\n" +
                                                    "（如果你在中国大陆，GitHub 的接口经常连不上，换个时间段再试通常就好了。）"
                                            BuildConfig.VERSION_CODE >= data.code ->
                                                "当前已是最新版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）。"
                                            // 有新版本：operation 已由 ViewModel 设好，交给 PclUpgradeHost 弹框
                                            else -> null
                                        }
                                    },
                                )
                            }
                            if (finished == null) {
                                notice = "检查更新超时了，请检查网络后重试。"
                            }
                        } catch (_: TooFrequentOperationException) {
                            notice = "检查太频繁了，请等几秒再试。"
                        } catch (_: Exception) {
                            notice = "获取更新失败，请检查网络后重试。"
                        } finally {
                            checking = false
                        }
                    }
                }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_pcl2),
                    title = "Plain Craft Launcher 2",
                    info = "Zalith-Plain Craft Launcher 界面的移植原型：版式、配色与文案均取自 PCL2",
                    buttonText = "开源代码",
                ) { openLink(context, URL_PCL2) }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 特别鸣谢
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangPageOtherAboutThanks) {
            Column(modifier = Modifier.fillMaxWidth()) {
                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_bangbang93),
                    title = "bangbang93",
                    info = "提供 BMCLAPI 镜像源和 Forge 安装工具",
                    buttonText = "赞助镜像源",
                ) { openLink(context, URL_BANGBANG93_SPONSOR) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_platform_mcmod),
                    title = "MC 百科",
                    info = "提供了 Mod 名称的中文翻译和更多相关信息！",
                    buttonText = "打开百科",
                ) { openLink(context, URL_MCMOD) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_avatar_mcim),
                    title = "z0z0r4",
                    info = "提供了 MCIM 社区资源镜像源和帮助库图床！",
                    buttonText = "MCIM 主页",
                ) { openLink(context, URL_MCIM) }

                PclAboutRow(
                    icon = painterResource(R.drawable.ic_public),
                    title = "EasyTier",
                    info = "提供了联机模块！",
                    buttonText = "GitHub 页面",
                ) { openLink(context, URL_EASYTIER_GITHUB) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_fcl),
                    title = "Fold Craft Launcher",
                    info = "Android 端界面与交互框架的重要参考",
                    buttonText = "GitHub 页面",
                ) { openLink(context, URL_FCL) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_hmcl),
                    title = "Hello Minecraft! Launcher",
                    info = "启动流程与版本管理的重要参考",
                    buttonText = "GitHub 页面",
                ) { openLink(context, URL_HMCL) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_launcher_pojav),
                    title = "PojavLauncher",
                    info = "在 Android 上运行 Minecraft Java 版的技术先驱",
                    buttonText = "GitHub 页面",
                ) { openLink(context, URL_POJAV) }

                PclAboutRow(
                    icon = painterResource(R.drawable.img_platform_weblate),
                    title = "Weblate",
                    info = "为本启动器提供在线的多语言翻译协作",
                    buttonText = "参与翻译",
                ) { openLink(context, URL_WEBLATE) }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 许可与版权声明
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = PclStrings.LangPageOtherAboutLicenseCopyright,
            collapsible = true,
            initiallyCollapsed = true,
        ) {
            Column {
                PclText(
                    text = "Plain Craft Launcher 2 以 GNU General Public License v3.0 发布，" +
                        "Zalith Launcher 2 亦以 GPL-3.0 发布，本界面移植同样遵循该许可。",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    PclButton(text = "许可原文", onClick = { openLink(context, "$URL_PROJECT/blob/main/LICENSE") })
                    PclButton(text = "项目仓库", onClick = { openLink(context, URL_PROJECT) })
                }
            }
        }

        PclBottomSpacer()
    }

    // 手动检查更新的结果（已最新 / 失败 / 太频繁 / 超时）。
    // 放在 `PclContent` 之后：Dialog 是独立窗口，不参与页面滚动布局。
    notice?.let { text ->
        PclNoticeDialog(
            title = "检查更新",
            content = text,
            onDismiss = { notice = null },
        )
    }
}

/* ============================================================================
 *  百宝箱
 *
 *  ⚠️ PCL2 的**开源版把这一页整个掏空了**：`PageOtherTest.xaml` 只剩一句居中提示，
 *  `.xaml.vb` 里每个函数都只 `Hint(GetLang("LangPageOtherTestNoUtility"))`
 *  （「为便于维护，开源内容中不包含百宝箱功能……」）。所以下面的实现**不是**照抄源码，
 *  而是按 PCL2 实际界面复刻版式，行为在 Android 上重新落地：
 *
 *    · 今日人品 / 千万别点 —— 纯本地彩蛋，随机池写在本文件下方；
 *    · 内存优化 —— Android 上对应「触发 GC 并回收堆」，并把**实际**释放量报出来；
 *    · 清理游戏垃圾 —— 只清 .minecraft 里公认的垃圾（崩溃报告 / 轮转日志 / hs_err 转储），
 *      删除前先把条数与体积列出来让用户确认，`latest.log` 永远保留；
 *    · 回声洞 —— PCL 是向自己的服务端取随机留言，Zalith 没有这个后端，改为内置留言池；
 *    · 下载自定义文件 —— 走 Zalith 自己的多线程下载引擎（`DownloadEngine`），
 *      提交后同样会出现在「下载管理」里。
 * ==========================================================================*/

@Composable
fun PclOtherTestPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 通用小提示框（标题 to 正文）
    var notice by remember { mutableStateOf<Pair<String, String>?>(null) }
    // 待确认的垃圾清理结果
    var rubbish by remember { mutableStateOf<RubbishScan?>(null) }
    // 待确认的存储清理结果
    var storage by remember { mutableStateOf<StorageScan?>(null) }
    // 「千万别点」：0 无 / 1 三按钮弹窗 / 2 恶搞画面
    var prankStage by remember { mutableIntStateOf(0) }
    // 回声洞：进页面就联网抓一条（旧实现只给了占位文案，没有首帧请求，
    // 必须点一下才去抓，表现成「每次进来都是没有留言」）。点击整块换一条。
    var echoReload by remember { mutableIntStateOf(0) }
    val echoText = rememberRemoteText("cave", echoReload)

    // ---- 下载自定义文件的表单
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(defaultDownloadFolder().absolutePath) }
    var fileName by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val path = treeUriToPath(uri)) {
            null -> PclHintHost.push("无法识别所选文件夹的路径，请换一个位置重试。", PclHintTheme.Red)
            else -> folder = path
        }
    }

    PclContent {
        // ---------------------------------------------------------------- 百宝箱
        PclCard(modifier = Modifier.fillMaxWidth(), title = "百宝箱") {
            Column {
                // 横排一行；窄屏时允许横向滑动，避免挤成一团
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PclButton(text = "今日人品", onClick = { notice = todayFortune() })
                    PclButton(text = "清理存储", onClick = {
                        scope.launch {
                            val scan = withContext(Dispatchers.IO) { scanStorage(context) }
                            if (scan.totalBytes <= 0L) {
                                notice = "清理存储" to "启动器的缓存目录已经是空的，没有可清理的内容。"
                            } else {
                                storage = scan
                            }
                        }
                    })
                    PclButton(text = "清理游戏垃圾", onClick = {
                        scope.launch {
                            val scan = withContext(Dispatchers.IO) {
                                scanRubbish(File(GamePathManager.currentPath.value))
                            }
                            if (scan.files.isEmpty()) {
                                notice = "清理游戏垃圾" to "这个游戏目录已经很干净了，没有可清理的垃圾文件。"
                            } else {
                                rubbish = scan
                            }
                        }
                    })
                    PclButton(
                        text = "千万别点",
                        colorState = PclColorState.Red,
                        onClick = { prankStage = 1 },
                    )
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---------------------------------------------------------------- 回声洞
        PclCard(modifier = Modifier.fillMaxWidth(), title = "回声洞") {
            // 整块可点：点一次从官网联网换一条（拿不到就保持现状）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { echoReload++ },
            ) {
                Text(
                    text = echoText,
                    style = PclTextStyle.base(pclPalette),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---------------------------------------------------------------- 下载自定义文件
        PclCard(modifier = Modifier.fillMaxWidth(), title = "下载自定义文件") {
            Column {
                PclHint(
                    text = "使用 PCL 的高速多线程下载引擎下载任意文件。请注意，部分网站" +
                            "（例如百度网盘）可能会报错（403）已禁止，无法正常下载。",
                    theme = PclHintTheme.Blue,
                )
                Spacer(Modifier.height(12.dp))

                PclFormRow("下载地址") {
                    PclTextBox(
                        value = url,
                        modifier = Modifier.fillMaxWidth(),
                        hint = "https://example.com/file.zip",
                        onValueChange = { url = it },
                    )
                }
                PclFormRow("保存到") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PclTextBox(
                            value = folder,
                            modifier = Modifier.weight(1f),
                            onValueChange = { folder = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        PclButton(text = "选择", onClick = { folderPicker.launch(null) })
                    }
                }
                PclFormRow("文件名") {
                    PclTextBox(
                        value = fileName,
                        modifier = Modifier.fillMaxWidth(),
                        hint = "留空则按下载地址自动推断",
                        onValueChange = { fileName = it },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PclButton(
                        text = "开始下载",
                        colorState = PclColorState.Highlight,
                        onClick = {
                            val request = buildCustomDownload(url = url, folder = folder, fileName = fileName)
                            if (request == null) {
                                PclHintHost.push("请先填写下载地址与文件名。", PclHintTheme.Red)
                            } else {
                                startCustomDownload(request)
                            }
                        },
                    )
                    Spacer(Modifier.width(10.dp))
                    PclButton(
                        text = "打开文件夹",
                        onClick = {
                            runCatching {
                                File(folder).takeIf { !it.exists() }?.mkdirs()
                                FileManagerLauncher.launch(context, rootPath = folder)
                            }.onFailure {
                                PclHintHost.push("无法打开文件夹：${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
                            }
                        },
                    )
                }
            }
        }
        PclBottomSpacer()
    }

    notice?.let { (title, body) ->
        PclNoticeDialog(title = title, content = body, onDismiss = { notice = null })
    }

    rubbish?.let { scan ->
        PclConfirmDialog(
            title = "清理游戏垃圾",
            content = buildString {
                append("将在「${GamePathManager.currentPath.value}」中删除 ")
                append("${scan.files.size} 个文件，共 ${formatBytes(scan.totalBytes)}。\n\n")
                append("会清掉的：崩溃报告、轮转日志（保留 latest.log）、hs_err / replay 崩溃转储。\n")
                append("Mod、存档、资源包等**不受影响**。")
            },
            danger = true,
            onConfirm = {
                // 注意：`PclConfirmDialog` 在 onConfirm 之后还会紧接着调一次 onDismiss，
                // 所以这里不能再切「中间态」等别处去干活（那样会被同帧覆盖掉），
                // 该做的事直接做完。
                scope.launch {
                    val (count, bytes) = withContext(Dispatchers.IO) { deleteRubbish(scan.files) }
                    PclHintHost.push("已清理 $count 个文件，释放 ${formatBytes(bytes)}。", PclHintTheme.Green)
                }
            },
            onDismiss = { rubbish = null },
        )
    }

    storage?.let { scan ->
        PclConfirmDialog(
            title = "清理存储",
            content = buildString {
                append("将清空启动器的缓存目录，共 ${formatBytes(scan.totalBytes)}（${scan.targets.size} 项）。\n\n")
                append("清掉的是：下载与安装留下的临时目录、图片与图标缓存、外部缓存目录。\n")
                append("游戏、存档、Mod、账号与设置都**不受影响**。")
            },
            danger = true,
            onConfirm = {
                // 同 `rubbish`：不能在这里切中间态，直接干活。
                scope.launch {
                    val (count, bytes) = withContext(Dispatchers.IO) { deleteStorage(scan.targets) }
                    PclHintHost.push("已清理 $count 项，释放 ${formatBytes(bytes)}。", PclHintTheme.Green)
                }
            },
            onDismiss = { storage = null },
        )
    }

    if (prankStage == 1) {
        // PCL2 那个彩蛋的已知结构：弹出的对话框里并排三枚全部写着「确定」的按钮，
        // 随便按哪一枚都会**整扇窗口**开始整活（四种效果随机抽一个，见 `PclPrank`）。
        PclThreeOkDialog(
            title = "千万别点",
            content = "都说了千万别点，你怎么还是点了呢？\n\n" +
                    "下面这三个按钮一模一样，随便挑一个按吧。",
            onChosen = {
                // 先把弹窗关掉，再放彩蛋 —— 窗口飘起来的时候不该还挂着一个对话框
                prankStage = 0
                PclPrank.triggerRandom()
            },
            // 按下返回键关窗会走 onDismiss；加个判定，别把已经关掉的状态又拉回来。
            onDismiss = { if (prankStage == 1) prankStage = 0 },
        )
    }
}

/** 表单行：左标签 + 右控件，对齐 PCL 设置页的 `Margin="0,0,0,10"` 布局。 */
@Composable
private fun PclFormRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PclTextStyle.base(pclPalette),
            maxLines = 1,
            modifier = Modifier.width(72.dp),
        )
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/* ------------------------------------------------------------------ 百宝箱的数据与工具 */

/** 默认下载目录：启动器自己的外部目录（无需任何存储权限即可写）。 */
private fun defaultDownloadFolder(): File = File(PathManager.DIR_FILES_EXTERNAL, "MyDownload")

/** 待清理的垃圾文件集合。 */
private data class RubbishScan(val files: List<File>, val totalBytes: Long)

/**
 * 扫描 .minecraft 里公认的垃圾。
 *
 * 只认这几类，且**只删文件、不递归删目录**：
 *   · `crash-reports` 目录下的全部文件
 *   · `logs` 目录下除 `latest.log` 之外的轮转日志
 *   · 根目录的 `hs_err_pid` / `replay_pid` 开头的崩溃转储
 */
private fun scanRubbish(gameDir: File): RubbishScan {
    if (!gameDir.isDirectory) return RubbishScan(emptyList(), 0L)
    val out = mutableListOf<File>()

    File(gameDir, "crash-reports").listFiles()
        ?.filterTo(out) { it.isFile }

    File(gameDir, "logs").listFiles()
        ?.filterTo(out) { it.isFile && it.name != "latest.log" }

    gameDir.listFiles()
        ?.filterTo(out) {
            it.isFile && (it.name.startsWith("hs_err_pid") || it.name.startsWith("replay_pid"))
        }

    return RubbishScan(out, out.sumOf { it.length() })
}

/** 删除扫描结果，返回「实际删掉的条数 to 实际释放的字节」。 */
private fun deleteRubbish(files: List<File>): Pair<Int, Long> {
    var count = 0
    var bytes = 0L
    files.forEach { file ->
        val size = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) {
            count++
            bytes += size
        }
    }
    return count to bytes
}

/** 待清理的存储缓存。 */
private data class StorageScan(val targets: List<File>, val totalBytes: Long)

/**
 * 扫描启动器的缓存占用。
 *
 * 覆盖两处：应用私有缓存目录（`context.cacheDir`，里面装着下载/安装的临时目录、
 * 图片缓存 `images`、图标缓存 `app_icons`、联网主页缓存 `remote_homepage`）
 * 与外部缓存目录。
 *
 * ⚠️ **正在跑任务时跳过 `temp_` 开头的目录**：`temp_game` / `temp_modpack` 等是下载与
 * 安装引擎的工作区，边下边删会把任务弄炸。任务列表来自 `TaskSystem.tasksFlow`。
 */
private fun scanStorage(context: Context): StorageScan {
    val busy = TaskSystem.tasksFlow.value.isNotEmpty()
    val targets = mutableListOf<File>()

    context.cacheDir.listFiles()?.forEach { child ->
        if (busy && child.name.startsWith("temp_")) return@forEach
        targets += child
    }
    context.externalCacheDir?.listFiles()?.forEach { targets += it }

    return StorageScan(targets, targets.sumOf { sizeOf(it) })
}

/** 递归求体积（目录则累加子项）。 */
private fun sizeOf(file: File): Long =
    if (file.isDirectory) (file.listFiles()?.sumOf { sizeOf(it) } ?: 0L) else file.length()

/** 删除扫描结果，返回「实际删掉的项数 to 实际释放的字节」。 */
private fun deleteStorage(targets: List<File>): Pair<Int, Long> {
    // 删除前**再确认一次**任务状态：从扫描到用户点确定之间可能刚好有下载起来了
    val busy = TaskSystem.tasksFlow.value.isNotEmpty()
    var count = 0
    var bytes = 0L
    targets.forEach { file ->
        if (busy && file.name.startsWith("temp_")) return@forEach
        val size = sizeOf(file)
        if (runCatching { file.deleteRecursively() }.getOrDefault(false)) {
            count++
            bytes += size
        }
    }
    return count to bytes
}

/** 今日人品：同一天必然得到同一个结果（按日期做种子）。 */
private fun todayFortune(): Pair<String, String> {
    val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    val seed = day.hashCode().toLong()
    val luck = (kotlin.math.abs(seed) % 101L).toInt()
    val comment = when {
        luck >= 95 -> "鸿运当头！今天开新存档吧，世界会善待你的。"
        luck >= 80 -> "相当不错的一天，适合去挖矿或者建个大工程。"
        luck >= 60 -> "运势平稳偏好，日常游玩不会有太大意外。"
        luck >= 40 -> "普普通通的一天，注意别把房子建在岩浆边上。"
        luck >= 20 -> "运势一般偏低，出发前记得备份存档。"
        luck >= 5 -> "今天还是别去末地了，在家整理箱子吧。"
        else -> "宜：睡觉。忌：开新档、进下界、徒手跳崖。"
    }
    return "今日人品" to "今日人品：$luck\n\n$comment"
}

/** 把下载表单整理成一个下载请求；信息不全时返回 null。 */
private fun buildCustomDownload(url: String, folder: String, fileName: String): CustomDownload? {
    val rawUrl = url.trim()
    if (rawUrl.isEmpty()) return null
    val dir = File(folder.trim().ifEmpty { defaultDownloadFolder().absolutePath })
    val name = fileName.trim().ifEmpty {
        rawUrl.substringAfterLast('/').substringBefore('?').substringBefore('#')
    }
    if (name.isEmpty()) return null
    return CustomDownload(url = rawUrl, target = File(dir, name))
}

private data class CustomDownload(val url: String, val target: File)

/** 用 HEAD 跟随重定向探测文件大小；拿不到返回 -1。 */
private suspend fun probeRemoteSize(url: String): Long = withContext(Dispatchers.IO) {
    runCatching {
        var current = java.net.URL(url)
        repeat(8) {
            val connection = (current.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: return@runCatching -1L
                    current = java.net.URL(current, location)
                    return@repeat
                }
                if (code !in 200..299) return@runCatching -1L
                val length = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                return@runCatching length
            } finally {
                runCatching { connection.disconnect() }
            }
        }
        -1L
    }.getOrDefault(-1L)
}

/**
 * 提交下载任务。走 Zalith 自己的引擎（`DownloadEngine`），
 * 与「下载管理」共用同一条任务流，所以提交后在那里也能看到进度、也能取消。
 */
private fun startCustomDownload(request: CustomDownload) {
    runCatching {
        request.target.parentFile?.takeIf { !it.exists() }?.mkdirs()
        val task = Task.runTask(
            dispatcher = Dispatchers.IO,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(androidText("正在连接 ${request.target.name}…"))

                // 先探测 Content-Length，能拿到就显示真实百分比，并让引擎按大小决定是否分段。
                // HEAD 被网站拒绝时退回原来的不确定进度，下载本身不受影响。
                val total = probeRemoteSize(request.url)
                if (total > 0L) task.updateProgress(0f)

                var downloaded = 0L
                DownloadEngine.download(
                    request = DownloadRequest(
                        urls = listOf(request.url),
                        targetFile = request.target,
                        expectedSize = total,
                    ),
                ) { delta ->
                    downloaded += delta
                    if (total > 0L) {
                        task.updateProgress((downloaded.toFloat() / total).coerceIn(0f, 1f))
                        task.updateMessage(
                            androidText(
                                "下载 ${request.target.name}（${formatBytes(downloaded)} / ${formatBytes(total)}）"
                            )
                        )
                    } else {
                        task.updateMessage(
                            androidText("下载 ${request.target.name}（${formatBytes(downloaded)}）")
                        )
                    }
                }
                task.updateMessage(androidText("已下载 ${request.target.name}（${formatBytes(downloaded)}）"))
                task.updateProgress(1f)
            },
        )
        TaskSystem.submitTask(task, onEnded = {
            PclHintHost.push("下载结束：${request.target.absolutePath}", PclHintTheme.Green)
        })
        PclHintHost.push("已提交下载任务，进度见「下载管理」。", PclHintTheme.Blue)
    }.onFailure {
        PclHintHost.push("无法开始下载：${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
    }
}

/** `DocumentsContract` 的树 URI → 真实路径（与版本管理页里那份逻辑一致）。 */
private fun treeUriToPath(uri: android.net.Uri): String? = runCatching {
    val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
    val parts = docId.split(":", limit = 2)
    val volume = parts[0]
    val relative = parts.getOrNull(1).orEmpty().trim('/')
    val root = when {
        volume.startsWith("raw:") -> return@runCatching "/" + volume.removePrefix("raw:").trim('/')
        volume == "primary" -> "/storage/emulated/0"
        else -> "/storage/$volume"
    }
    if (relative.isEmpty()) root else "$root/$relative"
}.getOrNull()

/** 人类可读的字节数。 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

/* ============================================================================
 *  反馈
 * ==========================================================================*/

/** `PageOtherFeedback` —— 真实的反馈与支持入口，点击即打开对应页面。 */
@Composable
fun PclOtherFeedbackPage() {
    val p = pclPalette
    val context = LocalContext.current
    val channels = listOf(
        Triple("GitHub Issues", "提交 Bug 报告与功能建议（本启动器仓库）", "$URL_PROJECT/issues"),
        Triple("渲染器插件", "获取可安装的渲染器插件（FCL 插件格式）", URL_GITHUB_RENDERER_PLUGINS),
        Triple("驱动插件", "获取 Turnip 等 Vulkan 驱动插件", URL_GITHUB_DRIVER_PLUGINS),
        Triple("原生库插件", "获取额外的原生库插件", URL_GITHUB_NATIVE_LIB_PLUGINS),
    )
    PclContent {
        PclHint(
            text = "反馈前建议先查看启动日志（首页右侧，或「设置 → 启动 → 高级选项」中的日志设置）。",
            theme = PclHintTheme.Blue,
        )
        Spacer(Modifier.height(15.dp))
        PclCard( modifier = Modifier.fillMaxWidth(),title = "反馈与资源渠道") {
            Column {
                channels.forEach { (name, info, url) ->
                    PclListItem(
                        title = name,
                        info = info,
                        logo = PclIcons.otherFeedback,
                        logoScale = 0.85f,
                        height = 48.dp,
                        infoColor = p.gray2,
                        buttons = listOf(
                            com.zalith.pcl.ui.pcl2.components.PclListItemButton(
                                logo = PclIcons.arrowRight,
                                tooltip = "打开",
                            ) { openLink(context, url) },
                        ),
                        onClick = { openLink(context, url) },
                    )
                }
            }
        }
        PclBottomSpacer()
    }
}

/* ============================================================================
 *  投票 → Zalith 没有投票后端，改为真实的社区入口
 * ==========================================================================*/

/** 原名 `PageOtherVote`；Zalith 无投票功能，这里展示真实的社区入口。 */
@Composable
fun PclOtherVotePage() {
    val p = pclPalette
    val context = LocalContext.current
    val entries = listOf(
        Triple("项目仓库", "查看进展、提交 Issue 与 Pull Request", URL_PROJECT),
        Triple("贡献者列表", "查看为本启动器做出贡献的开发者", "$URL_PROJECT/graphs/contributors"),
    )
    PclContent {
        PclHint(
            text = "本启动器没有内置的「新功能投票」系统，功能建议请通过 GitHub Issues 提交。",
            theme = PclHintTheme.Yellow,
        )
        Spacer(Modifier.height(15.dp))
        PclCard( modifier = Modifier.fillMaxWidth(),title = "社区") {
            Column {
                entries.forEach { (name, info, url) ->
                    PclListItem(
                        title = name,
                        info = info,
                        logo = PclIcons.otherVote,
                        logoScale = 0.9f,
                        height = 48.dp,
                        infoColor = p.gray2,
                        buttons = listOf(
                            com.zalith.pcl.ui.pcl2.components.PclListItemButton(
                                logo = PclIcons.arrowRight,
                                tooltip = "打开",
                            ) { openLink(context, url) },
                        ),
                        onClick = { openLink(context, url) },
                    )
                }
            }
        }
        PclBottomSpacer()
    }
}
