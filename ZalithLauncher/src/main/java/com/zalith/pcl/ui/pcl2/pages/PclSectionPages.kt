/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 四大版块的左栏与右栏装配：
 *   下载 → Pages/PageDownload/PageDownloadLeft.xaml
 *   联机 → Pages/PageLink/PageLinkMain.xaml（PCL 中联机没有左栏，整页显示）
 *   设置 → Pages/PageSetup/PageSetupLeft.xaml
 *   更多 → Pages/PageOther/PageOtherLeft.xaml
 *
 * 清单项的顺序、图标（原样路径）、LogoScale、高度 36、RadioBox 类型、
 * 右侧刷新按钮，以及「社区资源」分组标签的边距与不透明度，全部照抄原 XAML。
 *
 * 下载页的右栏已接入 Zalith 真实后端：
 *   · 「原版游戏」→ MinecraftVersions + 各加载器版本源 + GameInstaller.installGame
 *   · 「社区资源」→ searchAssets + getVersions + downloadSingleForVersions /
 *                    downloadDependenciesForVersions（整合包走 ModpackImporter）
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zalith.pcl.BuildKeys
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TaskStage
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.coroutine.TitledTask
import com.zalith.pcl.game.account.AccountsManager
import com.zalith.pcl.game.addons.modloader.cleanroom.CleanroomVersion
import com.zalith.pcl.game.addons.modloader.cleanroom.CleanroomVersions
import com.zalith.pcl.game.addons.modloader.fabriclike.fabric.FabricAPIVersions
import com.zalith.pcl.game.addons.modloader.fabriclike.fabric.FabricVersion
import com.zalith.pcl.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.zalith.pcl.game.addons.modloader.fabriclike.quilt.QuiltVersion
import com.zalith.pcl.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.zalith.pcl.game.addons.modloader.forgelike.forge.ForgeVersion
import com.zalith.pcl.game.addons.modloader.forgelike.forge.ForgeVersions
import com.zalith.pcl.game.addons.modloader.forgelike.neoforge.NeoForgeVersion
import com.zalith.pcl.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.zalith.pcl.game.addons.modloader.modlike.ModVersion
import com.zalith.pcl.game.addons.modloader.optifine.OptiFineVersion
import com.zalith.pcl.game.addons.modloader.optifine.OptiFineVersions
import com.zalith.pcl.game.download.assets.DependencyRequest
import com.zalith.pcl.game.download.assets.downloadDependenciesForVersions
import com.zalith.pcl.game.download.assets.downloadSingleForVersions
import com.zalith.pcl.game.download.assets.platform.Platform
import com.zalith.pcl.game.download.assets.platform.PlatformClasses
import com.zalith.pcl.game.download.assets.platform.PlatformDependencyType
import com.zalith.pcl.game.download.assets.platform.PlatformSearchData
import com.zalith.pcl.game.download.assets.platform.PlatformSearchFilter
import com.zalith.pcl.game.download.assets.platform.PlatformVersion
import com.zalith.pcl.game.download.assets.platform.getVersions
import com.zalith.pcl.game.download.assets.platform.searchAssets
import com.zalith.pcl.game.download.game.GameDownloadInfo
import com.zalith.pcl.game.download.game.GameInstaller
import com.zalith.pcl.game.download.modpack.install.ModpackImporter
import com.zalith.pcl.game.download.game.GameAlreadyInstalledException
import com.zalith.pcl.game.download.modpack.install.PackNotSupportedException
import com.zalith.pcl.game.download.modpack.install.UnsupportedPackReason
import com.zalith.pcl.game.version.download.DownloadFailedException
import com.zalith.pcl.game.versioninfo.MinecraftVersion
import com.zalith.pcl.game.versioninfo.MinecraftVersions
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclDialogContainer
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclIconTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclLoading
import com.zalith.pcl.ui.pcl2.components.PclLoadingCard
import com.zalith.pcl.ui.pcl2.components.PclProgressBar
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.components.PclTextButton
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.data.PclLeftLabels
import com.zalith.pcl.ui.pcl2.data.PclSkinSettings
import com.zalith.pcl.ui.pcl2.data.PclHiddenKeys
import com.zalith.pcl.ui.pcl2.theme.PclMotion
import com.zalith.pcl.ui.pcl2.theme.PclStaggerIn
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.terracotta.Terracotta
import com.zalith.pcl.terracotta.TerracottaState
import com.zalith.pcl.terracotta.TerracottaVPNService
import com.zalith.pcl.terracotta.fetchNodes
import com.zalith.pcl.terracotta.profile.ProfileKind
import com.zalith.pcl.terracotta.profile.TerracottaProfile
import com.zalith.pcl.viewmodel.EventViewModel
import net.burningtnt.terracotta.TerracottaAndroidAPI
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.screens.content.download.assets.elements.AssetsPage
import com.zalith.pcl.ui.screens.content.download.assets.elements.initAll
import com.zalith.pcl.ui.toAndroidString
import com.zalith.pcl.utils.network.downloadFile
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

// ---- 本轮新增（版本分组列表 / 社区资源列表） ----
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.zalith.pcl.R
import com.zalith.pcl.game.download.assets.platform.PlatformDisplayLabel
import com.zalith.pcl.game.download.assets.platform.PlatformFilterCode
import com.zalith.pcl.game.download.assets.platform.PlatformSortField
import com.zalith.pcl.game.download.assets.platform.curseforge.models.CurseForgeModCategory
import com.zalith.pcl.game.download.assets.platform.curseforge.models.CurseForgeModpackCategory
import com.zalith.pcl.game.download.assets.platform.curseforge.models.CurseForgeResourcePackCategory
import com.zalith.pcl.game.download.assets.platform.curseforge.models.CurseForgeSavesCategory
import com.zalith.pcl.game.download.assets.platform.curseforge.models.CurseForgeShadersCategory
import com.zalith.pcl.game.download.assets.platform.curseforge.models.curseForgeModLoaderFilters
import com.zalith.pcl.game.download.assets.platform.modrinth.models.ModrinthModCategory
import com.zalith.pcl.game.download.assets.platform.modrinth.models.ModrinthModpackCategory
import com.zalith.pcl.game.download.assets.platform.modrinth.models.ModrinthResourcePackCategory
import com.zalith.pcl.game.download.assets.platform.modrinth.models.ModrinthShadersCategory
import com.zalith.pcl.game.download.assets.platform.modrinth.models.modrinthModLoaderFilters
import com.zalith.pcl.game.download.assets.utils.ModTranslations
import com.zalith.pcl.game.download.assets.utils.getMcmodTitle
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.setting.enums.MirrorSourceType
import com.zalith.pcl.ui.pcl2.data.PclRuntime
import com.zalith.pcl.ui.pcl2.pages.pclDownloadManagerPage
import com.zalith.pcl.ui.pcl2.pages.pclDiscardIncompleteVersion
import com.zalith.pcl.ui.pcl2.pages.pclInstallDownloadedSave
import com.zalith.pcl.ui.pcl2.pages.pclResourceDetailPage
import com.zalith.pcl.ui.pcl2.pages.pclUniqueVersionName
import com.zalith.pcl.ui.pcl2.components.PclExtraTextButton
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclInputDialog
import com.zalith.pcl.ui.pcl2.components.PclMsgButton
import com.zalith.pcl.ui.pcl2.components.PclMsgInputDialog
import com.zalith.pcl.ui.pcl2.components.PclResourceItem
import com.zalith.pcl.ui.pcl2.components.rememberPclRowChunks
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.utils.formatDate
import org.apache.commons.io.FileUtils
import com.zalith.pcl.utils.getTimeAgo
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.OffsetDateTime

/* ============================================================================
 *  左栏公共骨架
 * ==========================================================================*/

/** 左栏中的一个条目：列表项或分组标签。 */
private sealed interface PclLeftEntry {
    data class Item(
        val index: Int,
        val title: String,
        val logo: ImageVector,
        /**
         * PCL 源码里给这个条目写的 `LogoScale`（留档用，**实际不生效**）。
         * 真正用的系数由 [pclLeftLogoScale] 按「渲染高度一致」算出来，
         * 否则一排图标会大小不一。
         */
        val logoScale: Float = 1f,
        val buttons: List<PclListItemButton> = emptyList(),
    ) : PclLeftEntry

    data class Label(val text: String) : PclLeftEntry
}

/** 分组标签（`TextBlock Margin="13,20,5,4" Opacity="0.6" FontSize="12"`）。 */
private fun group(text: String) = PclLeftEntry.Label(text)

/** 左栏图标的统一缩放系数。
 *
 * PCL 给每个左栏条目手写了 `LogoScale`（`PageSetupLeft` 是 0.8 / 0.77 / 0.9 / 0.8、
 * 各页还各不相同），那是作者按各个图标自己的观感逐个调出来的经验值。
 * 一旦真的照抄，一排图标就会明显大小不一 —— 窄高形的「启动」三角形偏小、
 * 宽扁形的「联机」wifi 波更扁、方形的「个性化」T 恤最大。
 *
 * 这里改成按「渲染高度一致」归一化：
 *   1. 先做 WPF `Stretch.Uniform` 的等比铺满（图标视口 = 路径包围盒，见 `PclIcons`）；
 *   2. 再把高度统一到可用高度的 90%；
 *   3. 宽度会超出可用宽度时（很扁的图标）以宽度为准，避免被切。
 *
 * 左栏条目固定是「高 36、图标列 34、矢量边距 (6,8,4,8)」→ 可用区 24×20。
 */
private fun pclLeftLogoScale(logo: ImageVector): Float {
    val width = logo.viewportWidth
    val height = logo.viewportHeight
    if (width <= 0f || height <= 0f) return 1f
    val fit = minOf(PCL_LEFT_LOGO_BOX_W / width, PCL_LEFT_LOGO_BOX_H / height)
    val renderedWidth = width * fit
    val renderedHeight = height * fit
    var scale = PCL_LEFT_LOGO_TARGET_H / renderedHeight
    if (renderedWidth * scale > PCL_LEFT_LOGO_BOX_W) scale = PCL_LEFT_LOGO_BOX_W / renderedWidth
    return scale
}

private const val PCL_LEFT_LOGO_BOX_W = 24f
private const val PCL_LEFT_LOGO_BOX_H = 20f
private const val PCL_LEFT_LOGO_TARGET_H = 18f

/**
 * PCL `MyPageLeft` 的内容骨架：`StackPanel Margin="0,12,0,0"`，
 * 条目高 36、RadioBox 类型，可纵向滚动。
 */
@Composable
private fun PclLeftPanel(
    nav: PclNavController,
    entries: List<PclLeftEntry>,
) {
    val p = pclPalette
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp),
    ) {
        // MyPageLeft.TriggerShowAnimation 会按 GetAllAnimControls 的顺序逐个入场，
        // 分组标题（TextBlock）也占一个序号，所以这里直接用 forEachIndexed 的下标。
        entries.forEachIndexed { animIndex, entry ->
            when (entry) {
                is PclLeftEntry.Item -> PclStaggerIn(animIndex) {
                    PclListItem(
                        title = entry.title,
                        logo = entry.logo,
                        logoScale = pclLeftLogoScale(entry.logo),
                        type = PclListItemType.RadioBox,
                        checked = nav.leftIndex == entry.index,
                        height = 36.dp,
                        // 四个左栏 XAML 的每个条目都写了 MinPaddingRight="35"
                        minPaddingRight = 35.dp,
                        // `MyListItem.xaml.vb:191` 注册的默认 FontSize 就是 14，
                        // 这里必须与 `pclLeftPanelWidth()` 算宽度时用的字号同源，否则最后几个字会被裁掉。
                        fontSize = PclDimens.leftItemFontSize,
                        buttons = entry.buttons,
                        onClick = { nav.select(entry.index) },
                    )
                }

                is PclLeftEntry.Label -> PclStaggerIn(
                    index = animIndex,
                    // TextBlock 的入场透明度只到 0.6（源码里本来就有的 0.6 Opacity，动画也止于 0.6）
                    labelAlpha = PclMotion.LEFT_ENTER_LABEL_ALPHA,
                ) {
                    Text(
                        text = entry.text,
                        style = PclTextStyle.base(p).copy(fontSize = 12.sp, color = p.color1),
                        modifier = Modifier
                            .padding(start = 13.dp, top = 20.dp, end = 5.dp, bottom = 4.dp)
                            .graphicsLayer(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 刷新按钮（`MyIconButton LogoScale="0.85" ToolTip="刷新"`）。 */
private fun refreshButton(tip: String = "刷新", onClick: () -> Unit = {}) =
    PclListItemButton(logo = PclIcons.refresh, tooltip = tip, logoScale = 0.85f, onClick = onClick)

/** 重置按钮（`MyIconButton LogoScale="0.9" ToolTip="初始化本页设置"`）。 */
private fun resetButton(tip: String, onClick: () -> Unit = {}) =
    PclListItemButton(logo = PclIcons.reset, tooltip = tip, logoScale = 0.9f, onClick = onClick)

/**
 * 「下载页刷新」信号：左栏的刷新按钮递增它，右栏据此重新拉取真实数据。
 * （PCL 的 `PageDownloadLeft.BtnRefresh` 会触发右侧刷新。）
 */
private object PclDownloadRefresh {
    var token by mutableIntStateOf(0)
    fun trigger() {
        token++
    }
}

/* ============================================================================
 *  下载
 * ==========================================================================*/

/**
 * `PageDownloadLeft` ——
 * 0 原版游戏 / 「社区资源」分组标签 / 1 Mod / 2 整合包 / 3 存档 / 4 资源包 / 5 光影包。
 *
 * 注：PCL 第 3 项是「数据包」，Zalith 的资源平台只提供 存档(SAVES)，故此处按真实能力改名。
 */
@Composable
fun PclDownloadLeft(nav: PclNavController) {
    PclLeftPanel(
        nav = nav,
        entries = listOf(
            PclLeftEntry.Item(
                0, PclLeftLabels.downloadGame, PclIcons.dlGame, 1f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
            group(PclStrings.LangDownloadPageLeftResource),
            PclLeftEntry.Item(
                1, PclLeftLabels.downloadMod, PclIcons.instanceMod, 0.97f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
            PclLeftEntry.Item(
                2, PclLeftLabels.downloadModpack, PclIcons.instanceExport, 0.98f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
            PclLeftEntry.Item(
                3, PclLeftLabels.downloadSave, PclIcons.dlDataPack, 0.96f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
            PclLeftEntry.Item(
                4, PclLeftLabels.downloadResourcepack, PclIcons.dlResourcePack, 0.81f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
            PclLeftEntry.Item(
                5, PclLeftLabels.downloadShader, PclIcons.dlShader, 1.04f,
                listOf(refreshButton(onClick = { PclDownloadRefresh.trigger() })),
            ),
        ),
    )
}

/** `PageDownload` 右侧：0 为原版游戏安装页，其余为社区资源页。 */
@Composable
fun PclDownloadRight(nav: PclNavController) {
    when (nav.leftIndex) {
        0 -> PclDownloadInstallPage(nav)
        else -> PclDownloadResourcePage(nav = nav, section = nav.leftIndex)
    }
}

/* ---------------------------------------------------------------- 原版游戏安装 */

/** 加载器版本列表的可观察状态。 */
internal class PclLoaderState<T> {
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var items by mutableStateOf<List<T>>(emptyList())
    var selected by mutableStateOf<T?>(null)

    fun reset() {
        loading = false
        error = null
        items = emptyList()
        selected = null
    }

    suspend fun load(block: suspend () -> List<T>?) {
        reset()
        loading = true
        runCatching { block() }
            .onSuccess { list ->
                items = list.orEmpty()
                // ★ 这里【不能】自动选中首项：PCL 的 SelectedForge / SelectedFabric / SelectedOptiFine ……
                //   全部默认 Nothing（PageDownloadInstall.xaml.vb:363-369），只有用户点选才赋值。
                //   一旦每个加载器都自动选中，开始下载就会把 Forge+NeoForge+Fabric+Quilt+OptiFine+
                //   Cleanroom+FabricAPI 一次性全装上。
                selected = null
            }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
        loading = false
    }
}

/**
 * 安装页里参与「互斥」判定的加载器。
 *
 * PCL 的原表（`PageDownloadInstall.xaml.vb:553-960` 的一串 `LoadXxxGetError`）覆盖的是
 * Forge / NeoForge / Fabric / FabricAPI / OptiFine / LiteLoader / OptiFabric；
 * 本移植把 LiteLoader / OptiFabric 换成了 Quilt 与 Cleanroom，所以这里按同样的模式收进枚举。
 */
internal enum class PclLoaderKind(val displayName: String) {
    Forge("Forge"),
    NeoForge("NeoForge"),
    Fabric("Fabric"),
    Quilt("Quilt"),
    OptiFine("OptiFine"),
    Cleanroom("Cleanroom"),
}

/**
 * 比较两个 Minecraft 版本号（`1.20.4` vs `1.20.5`），返回负 / 零 / 正。
 *
 * 只服务于下面的两个阈值（OptiFine 的 1.13~1.14.3、Fabric 的 1.20.4），它们都是纯数字
 * 三段式，所以按 `.` 切段后逐段比数字；某一段不是数字（快照 `24w14a` 之类）就退回字符串
 * 比较 —— 快照版本本来也不会落在这两个区间里，不会误判。
 */
internal fun compareMcVersion(a: String, b: String): Int {
    val partsA = a.split('.')
    val partsB = b.split('.')
    for (i in 0 until maxOf(partsA.size, partsB.size)) {
        val sa = partsA.getOrNull(i).orEmpty()
        val sb = partsB.getOrNull(i).orEmpty()
        val na = sa.toIntOrNull()
        val nb = sb.toIntOrNull()
        val cmp = if (na != null && nb != null) na.compareTo(nb) else sa.compareTo(sb)
        if (cmp != 0) return cmp
    }
    return 0
}

/**
 * 加载器互斥：返回 `null` 表示 [target] 与当前已选项兼容、可以展开选择；
 * 非 null 是给用户看的原因，卡片会据此禁用。
 *
 * 照着 PCL 的 `LoadForgeGetError` / `LoadNeoForgeGetError` / `LoadFabricGetError` /
 * `LoadOptiFineGetError` 归纳出的规则：
 *
 *   · **模组加载器彼此互斥** —— Forge / NeoForge / Fabric / Quilt / Cleanroom 只能选一个
 *     （Cleanroom 是 1.12.2 上 Forge 的分支，同理不能与 Forge 共存）；
 *   · **OptiFine** 与 NeoForge 不兼容；与 1.13~1.14.3 的 Forge 不兼容
 *     （`LoadOptiFineGetError` 的第二条）；与 1.20.5+ 的 Fabric 不兼容
 *     （`CompareVersion(VanillaName, "1.20.4") > 0`）。
 *
 * 文案直接取 PCL 语言文件 `Resources/Language/zh-CN.xaml` 里的原文。
 *
 * ⚠️ 这里**只禁用、不自动清空**：与 PCL 一样，冲突项由用户自己按卡片右上角的叉清掉，
 * 免得选一个加载器就悄悄把另一个已经选好的版本丢掉。
 */
internal fun pclLoaderConflict(
    target: PclLoaderKind,
    mcVersion: String?,
    selected: Set<PclLoaderKind>,
): String? {
    fun blockedBy(vararg kinds: PclLoaderKind): String? =
        kinds.firstOrNull { it in selected }?.let { "与 ${it.displayName} 不兼容" }

    val mc = mcVersion
    return when (target) {
        PclLoaderKind.Forge -> blockedBy(
            PclLoaderKind.NeoForge, PclLoaderKind.Fabric,
            PclLoaderKind.Quilt, PclLoaderKind.Cleanroom,
        ) ?: if (PclLoaderKind.OptiFine in selected && mc != null &&
            compareMcVersion(mc, "1.13") >= 0 && compareMcVersion(mc, "1.14.3") <= 0
        ) "与 OptiFine 不兼容" else null

        PclLoaderKind.NeoForge -> blockedBy(
            PclLoaderKind.Forge, PclLoaderKind.Fabric,
            PclLoaderKind.Quilt, PclLoaderKind.Cleanroom, PclLoaderKind.OptiFine,
        )

        PclLoaderKind.Fabric -> blockedBy(
            PclLoaderKind.Forge, PclLoaderKind.NeoForge,
            PclLoaderKind.Quilt, PclLoaderKind.Cleanroom,
        ) ?: if (PclLoaderKind.OptiFine in selected && mc != null &&
            compareMcVersion(mc, "1.20.4") > 0
        ) "与 OptiFine 不兼容" else null

        PclLoaderKind.Quilt -> blockedBy(
            PclLoaderKind.Forge, PclLoaderKind.NeoForge,
            PclLoaderKind.Fabric, PclLoaderKind.Cleanroom,
        )

        PclLoaderKind.OptiFine -> blockedBy(
            PclLoaderKind.NeoForge, PclLoaderKind.Quilt, PclLoaderKind.Cleanroom,
        ) ?: if (PclLoaderKind.Forge in selected && mc != null &&
            compareMcVersion(mc, "1.13") >= 0 && compareMcVersion(mc, "1.14.3") <= 0
        ) "与 Forge 不兼容"
        else if (PclLoaderKind.Fabric in selected && mc != null &&
            compareMcVersion(mc, "1.20.4") > 0
        ) "与 Fabric 不兼容" else null

        // Cleanroom 是 1.12.2 的 Forge 分支，和另外几个模组加载器天然无法共存。
        PclLoaderKind.Cleanroom -> blockedBy(
            PclLoaderKind.Forge, PclLoaderKind.NeoForge,
            PclLoaderKind.Fabric, PclLoaderKind.Quilt,
        )
    }
}

/**
 * `PageDownloadInstall` —— 原版游戏安装。
 *
 * PCL 把这一页拆成两个状态（`EnterSelectPage` / `ExitSelectPage`）：
 *   ① 版本列表态（`PanMinecraft`）：「最新版本」卡片 + 正式版 / 预览版 / 远古版 / 愚人节版
 *      四组可折叠卡片（`PageDownloadInstall.xaml.vb:443-540`），点条目即选中该版本；
 *   ② 加载器选择态（`PanSelect`）：返回按钮 + 版本图标 + 版本名输入框 + 各加载器卡片 + 开始下载。
 *
 * 状态切换动画（`EnterSelectPage:84-117` / `ExitSelectPage:134-145`）：
 *   旧面板 70ms 淡出 + 90ms 位移到 ±50，延迟 10ms；
 *   新面板 70ms 淡入 + 160ms 位移（EaseOutFluent），延迟 100ms。
 */
@Composable
private fun PclDownloadInstallPage(nav: PclNavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = LocalPclRuntime.current
    val refreshToken = PclDownloadRefresh.token
    val p = pclPalette
    val density = LocalDensity.current

    // ---- Minecraft 版本列表
    var mcVersions by remember { mutableStateOf<List<MinecraftVersion>>(emptyList()) }
    var mcLoading by remember { mutableStateOf(true) }
    var mcError by remember { mutableStateOf<String?>(null) }

    // ---- 选择状态
    var selectedMc by remember { mutableStateOf<MinecraftVersion?>(null) }
    var customName by remember { mutableStateOf("") }
    // 用户是否手动改过版本名（= PCL 的 `IsSelectNameEdited`）。改过之后就不再被默认名覆盖，
    // 否则用户敲一半、恰好又选了/取消了某个加载器，输入框里的字会被冲掉。
    var customNameEdited by remember { mutableStateOf(false) }
    var inSelectPage by remember { mutableStateOf(false) }

    LaunchedEffect(refreshToken) {
        mcLoading = true
        mcError = null
        runCatching {
            MinecraftVersions.refreshVersions(force = refreshToken > 0 && mcVersions.isEmpty())
        }.onFailure { mcError = it.message ?: it.javaClass.simpleName }
        mcVersions = MinecraftVersions.allVersions.value.sortedByDescending { it.pclReleaseTime() }
        mcLoading = false
    }

    // ---- 分组与「最新版本」置顶项
    val grouped = remember(mcVersions) {
        PclMcGroup.entries
            .map { group -> group to mcVersions.filter { it.pclGroupOf() == group } }
            .filter { it.second.isNotEmpty() }
    }
    val pinned = remember(grouped) {
        val release = grouped.firstOrNull { it.first == PclMcGroup.Release }?.second?.firstOrNull()
        val preview = grouped.firstOrNull { it.first == PclMcGroup.Preview }?.second?.firstOrNull()
        buildList {
            release?.let { add(it to PclStrings.LangDownloadClientReleaseReleaseOn) }
            // 只有预览版比正式版还新时才一起置顶（PageDownloadInstall.xaml.vb:506）
            if (preview != null && (release == null || preview.pclReleaseTime() > release.pclReleaseTime())) {
                add(preview to PclStrings.LangDownloadClientBetaReleaseOn)
            }
        }
    }

    // ---- 各加载器状态
    val forge = remember { PclLoaderState<ForgeVersion>() }
    val neoForge = remember { PclLoaderState<NeoForgeVersion>() }
    val fabric = remember { PclLoaderState<FabricVersion>() }
    val quilt = remember { PclLoaderState<QuiltVersion>() }
    val fabricApi = remember { PclLoaderState<Any>() }
    val optifine = remember { PclLoaderState<OptiFineVersion>() }
    val cleanroom = remember { PclLoaderState<CleanroomVersion>() }

    val mcId = selectedMc?.version?.id

    // ---- 默认版本名（PCL 的 `PageDownloadInstall.GetSelectName`）
    //
    // 【为什么必须带加载器信息】版本名就是 `versions/<名字>/` 的目录名，安装器靠它判断
    // 「这个版本已经装过了」（`GameInstaller.createPathConfig`：`versions/<名字>/<名字>.json` 存在就抛
    // `GameAlreadyInstalledException`）。以前这里默认只填 `26.2`，于是「已装 26.2 原版，
    // 再装 26.2 + Fabric」会被算成同一个版本名，直接提示已经下载过。
    // PCL 的默认名长这样：`26.2-Fabric 0.16.9` / `26.2-Forge_47.2.0`，天然不会撞。
    val defaultName = remember(
        mcId,
        fabric.selected, forge.selected, neoForge.selected,
        quilt.selected, optifine.selected, cleanroom.selected,
    ) {
        mcId?.let { id ->
            pclDefaultVersionName(
                mcId = id,
                fabric = fabric.selected,
                forge = forge.selected,
                neoForge = neoForge.selected,
                quilt = quilt.selected,
                optifine = optifine.selected,
                cleanroom = cleanroom.selected,
            )
        }.orEmpty()
    }

    // 加载器一变，输入框里的名字跟着变（等价于 PCL 的 `SelectNameUpdate`）。
    // 用户自己改过（`customNameEdited`）就停手，别抢人家的输入。
    LaunchedEffect(defaultName) {
        if (defaultName.isNotEmpty() && !customNameEdited) customName = defaultName
    }

    // ---- Fabric API 是否已经自动预选过（见下方「Fabric API 自动预选」）。
    // 声明在刷新效果之前，好在重置时一并复位。
    var autoSelectedFabricApi by remember { mutableStateOf(false) }

    LaunchedEffect(mcId, refreshToken) {
        val id = mcId ?: return@LaunchedEffect
        forge.reset(); neoForge.reset(); fabric.reset()
        quilt.reset(); fabricApi.reset(); optifine.reset(); cleanroom.reset()
        // 换版本或点刷新会把 `fabric.selected` 一起清掉，用户得重新选一次 Fabric；
        // 那时应该重新自动带上 Fabric API，所以这里把标记一并复位
        // （等价于 PCL 的 `ExitSelectPage` → `ClearSelected` 里那句 `AutoSelectedFabricApi = False`）。
        autoSelectedFabricApi = false
        launch { forge.load { ForgeVersions.fetchForgeList(id) } }
        launch { neoForge.load { NeoForgeVersions.fetchNeoForgeList(gameVersion = id) } }
        launch { fabric.load { FabricVersions.fetchFabricLoaderList(id) } }
        launch { quilt.load { QuiltVersions.fetchQuiltLoaderList(id) } }
        launch {
            fabricApi.load {
                // PCL 的 `FabricApi_Loaded` 最后会 `Versions.OrderByDescending(ReleaseDate)`
                // 再取第 0 项当默认值，所以这里的顺序是有语义的，必须显式排。
                FabricAPIVersions.fetchVersionList(id)
                    ?.sortedByDescending { it.version.platformDatePublished() }
                    ?.map { it as Any }
            }
        }
        launch { optifine.load { OptiFineVersions.fetchOptiFineList(gameVersion = id) } }
        launch { cleanroom.load { CleanroomVersions.fetchLoaderList(id) } }
    }

    // ---- Fabric API 自动预选
    //
    // `PageDownloadInstall.xaml.vb:1009-1032`（`FabricApi_Loaded`）：Fabric API 的列表一加载完、
    // 而且已经选了 Fabric，就自动选中排好序后的第 0 项（= 发布时间最新的那个）。这是 PCL 里
    // **唯一**会被自动预选的加载器 —— Forge / Fabric / NeoForge / OptiFine 全部默认 Nothing
    // （见 `PclLoaderState.load` 里的注释），所以别把这个规则推广到别的卡片上。
    //
    // `AutoSelectedFabricApi` 只置一次：用户手动按叉清掉之后不会被重新选上。
    // 离开选择页（`ExitSelectPage`）或换版本/刷新时复位，与源码一致。
    LaunchedEffect(fabric.selected, fabricApi.items) {
        if (autoSelectedFabricApi) return@LaunchedEffect
        if (fabric.selected == null || fabricApi.items.isEmpty()) return@LaunchedEffect
        autoSelectedFabricApi = true
        fabricApi.selected = fabricApi.items.first()
    }

    // ---- 安装任务（全局，切页 / 后台运行都不会中断）
    val installTasks by runtime.installTasks.collectAsStateWithLifecycle()

    // ---- 已选中的加载器集合（互斥判定用）。
    // 读 `state.selected` 本身就是对 State 的订阅，任一项变化都会让这张表重算，
    // 于是「选了 Forge 之后 NeoForge 立刻变灰」是自动的。
    val selectedLoaders = remember(
        forge.selected, neoForge.selected, fabric.selected,
        quilt.selected, optifine.selected, cleanroom.selected,
    ) {
        buildSet {
            if (forge.selected != null) add(PclLoaderKind.Forge)
            if (neoForge.selected != null) add(PclLoaderKind.NeoForge)
            if (fabric.selected != null) add(PclLoaderKind.Fabric)
            if (quilt.selected != null) add(PclLoaderKind.Quilt)
            if (optifine.selected != null) add(PclLoaderKind.OptiFine)
            if (cleanroom.selected != null) add(PclLoaderKind.Cleanroom)
        }
    }

    fun startInstall() {
        val mc = selectedMc
        if (mc == null) {
            PclHintHost.push("请先选择一个游戏版本。", PclHintTheme.Red)
            return
        }
        // 输入框空着 = 用默认名（带加载器后缀的那个）。
        val typed = customName.trim()
        val base = typed.ifEmpty { defaultName.ifEmpty { mc.version.id } }
        // 程序生成的名字撞了就自动让开（`26.2-Fabric 0.16.9 (2)`），别让「再装一份」直接失败；
        // 手动输入的名字则原样交给安装器 —— 用户要的就是那个确切名字，被悄悄改名会更困惑。
        val name = if (customNameEdited) base else pclUniqueVersionName(base)
        val info = GameDownloadInfo(
            gameVersion = mc.version.id,
            customVersionName = name,
            optifine = optifine.selected,
            forge = forge.selected,
            neoforge = neoForge.selected,
            fabric = fabric.selected,
            fabricAPI = fabricApi.selected as? ModVersion,
            quilt = quilt.selected,
            cleanroom = cleanroom.selected,
        )
        runtime.installGame(info)
        // PCL 的做法：任务直接进任务列表，并切到「下载管理」页看进度，而不是弹进度对话框
        nav.push(pclDownloadManagerPage())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PclContent(
            // PageDownloadInstall.xaml：PanInner 的 Margin 是 25,10,25,25；
            // 进入加载器选择页后改成 25,10,25,40（EnterSelectPage:44）。
            contentPadding = PaddingValues(
                start = 25.dp,
                top = 10.dp,
                end = 25.dp,
                bottom = if (inSelectPage) 40.dp else 25.dp,
            ),
        ) {
            AnimatedContent(
                targetState = inSelectPage,
                transitionSpec = {
                    val enterX = with(density) { 40.dp.toPx() }.toInt()
                    val exitX = with(density) { 50.dp.toPx() }.toInt()
                    if (targetState) {
                        (
                            fadeIn(tween(70, delayMillis = 100)) +
                                slideInHorizontally(
                                    tween(160, delayMillis = 100, easing = PclMotion.easeOutFluent),
                                ) { enterX }
                            ).togetherWith(
                            fadeOut(tween(70, delayMillis = 10)) +
                                slideOutHorizontally(
                                    tween(90, delayMillis = 10, easing = PclMotion.easeOutFluent),
                                ) { -exitX },
                        )
                    } else {
                        (
                            fadeIn(tween(70, delayMillis = 100)) +
                                slideInHorizontally(
                                    tween(160, delayMillis = 100, easing = PclMotion.easeOutFluent),
                                ) { -exitX }
                            ).togetherWith(
                            fadeOut(tween(70, delayMillis = 10)) +
                                slideOutHorizontally(
                                    tween(90, delayMillis = 10, easing = PclMotion.easeOutFluent),
                                ) { enterX },
                        )
                    }
                },
                label = "pclDownloadInstallSwap",
            ) { selecting ->
                if (selecting) {
                    // 选了哪个加载器，左上角版本图标就换成它的方块（PCL 的 `GetSelectLogo`，
                    // `PageDownloadInstall.xaml.vb:392-406`）。优先级照原版排：
                    // Fabric > Forge > NeoForge > LiteLoader > OptiFine；
                    // 本移植没有 LiteLoader，那个位置换成了 Quilt（红石块），Cleanroom 排在最后。
                    val headerLogo = when {
                        fabric.selected != null -> R.drawable.pcl_block_fabric
                        forge.selected != null -> R.drawable.pcl_block_anvil
                        neoForge.selected != null -> R.drawable.pcl_block_neoforge
                        quilt.selected != null -> R.drawable.pcl_block_redstone
                        optifine.selected != null -> R.drawable.pcl_block_grass_path
                        cleanroom.selected != null -> R.drawable.pcl_block_redstone_lamp
                        else -> null
                    }
                    PclMcSelectPanel(
                        version = selectedMc,
                        customName = customName,
                        headerLogoRes = headerLogo,
                        onCustomNameChange = {
                            customName = it
                            // 清空就当作「没改过」，让默认名重新接管
                            customNameEdited = it.isNotBlank()
                        },
                        onBack = {
                            // ExitSelectPage：清空已选的加载器、回到版本列表并滚回顶部。
                            // 注意 PCL 不清 VanillaName，所以这里也保留 selectedMc，
                            // 否则退场动画期间那张卡片会闪一下空图标。
                            forge.reset(); neoForge.reset(); fabric.reset(); quilt.reset()
                            fabricApi.reset(); optifine.reset(); cleanroom.reset()
                            // `ClearSelected`（PageDownloadInstall.xaml.vb:901）里会把
                            // AutoSelectedFabricApi 复位，下次进选择页还能自动选一次。
                            autoSelectedFabricApi = false
                            inSelectPage = false
                            PclScrollSignal.request()
                        },
                    ) {
                        if (installTasks.isNotEmpty()) {
                            // 安装是全局任务：弹窗被收起来（后台运行）时在这里也能看到
                            PclHint(
                                text = "游戏正在安装中，可在右下角「下载管理」查看进度。",
                                theme = PclHintTheme.Blue,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        if (fabric.selected != null && fabricApi.selected == null) {
                            PclHint(
                                text = PclStrings.LangDownloadInstallFabricApi,
                                theme = PclHintTheme.Red,
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        PclHint(
                            text = PclStrings.LangDownloadInstallOptiFine,
                            theme = PclHintTheme.Yellow,
                        )
                        Spacer(Modifier.height(12.dp))

                        // 卡片顺序照 PCL：Forge、NeoForge、Fabric、Fabric API（选了 Fabric 才出现）、
                        // OptiFine、LiteLoader；本移植把 LiteLoader / OptiFabric 换成了 Zalith 支持的
                        // Quilt 与 Cleanroom。
                        PclLoaderCard(
                            state = forge,
                            title = PclStrings.LangDownloadPageLeftForge,
                            logoRes = R.drawable.pcl_block_anvil,
                            labelOf = { it.versionName },
                            infoOf = { info ->
                                info.releaseTime.takeIf { it.isNotBlank() }?.let { "发布于 $it" }
                            },
                            disabledReason = pclLoaderConflict(PclLoaderKind.Forge, mcId, selectedLoaders),
                        )
                        PclLoaderCard(
                            state = neoForge,
                            title = PclStrings.LangDownloadPageLeftNeoForge,
                            logoRes = R.drawable.pcl_block_neoforge,
                            labelOf = { it.versionName },
                            infoOf = {
                                if (it.isBeta) PclStrings.LangDownloadTest else PclStrings.LangDownloadStable
                            },
                            disabledReason = pclLoaderConflict(PclLoaderKind.NeoForge, mcId, selectedLoaders),
                        )
                        PclLoaderCard(
                            state = fabric,
                            title = PclStrings.LangDownloadPageLeftFabric,
                            logoRes = R.drawable.pcl_block_fabric,
                            labelOf = { it.version },
                            infoOf = {
                                if (it.stable) PclStrings.LangDownloadStable else PclStrings.LangDownloadTest
                            },
                            disabledReason = pclLoaderConflict(PclLoaderKind.Fabric, mcId, selectedLoaders),
                        )
                        // `PageDownloadInstall.xaml.vb:266` —— Fabric API 卡片只在选了 Fabric 后出现。
                        // 它是依附于 Fabric 的，本身不参与互斥。
                        if (fabric.selected != null) {
                            PclLoaderCard(
                                state = fabricApi,
                                title = PclStrings.LangDownloadPageLeftFabricApi,
                                logoRes = R.drawable.pcl_block_fabric,
                                labelOf = { (it as ModVersion).displayName },
                                infoOf = { null },
                            )
                        }
                        PclLoaderCard(
                            state = quilt,
                            title = "Quilt",
                            logoRes = R.drawable.pcl_block_redstone,
                            labelOf = { it.version },
                            infoOf = {
                                if (it.stable) PclStrings.LangDownloadStable else PclStrings.LangDownloadTest
                            },
                            disabledReason = pclLoaderConflict(PclLoaderKind.Quilt, mcId, selectedLoaders),
                        )
                        PclLoaderCard(
                            state = optifine,
                            title = PclStrings.LangDownloadPageLeftOptiFine,
                            logoRes = R.drawable.pcl_block_grass_path,
                            labelOf = { it.displayName },
                            infoOf = {
                                if (it.isPreview) {
                                    PclStrings.LangDownloadTest
                                } else {
                                    PclStrings.LangDownloadStable
                                }
                            },
                            disabledReason = pclLoaderConflict(PclLoaderKind.OptiFine, mcId, selectedLoaders),
                        )
                        PclLoaderCard(
                            state = cleanroom,
                            title = "Cleanroom (1.12.2)",
                            logoRes = R.drawable.pcl_block_redstone_lamp,
                            labelOf = { it.version },
                            infoOf = { null },
                            disabledReason = pclLoaderConflict(PclLoaderKind.Cleanroom, mcId, selectedLoaders),
                        )

                        PclDownloadSourceCard(runtime)

                        Spacer(Modifier.height(10.dp))
                        PclText(
                            text = "LiteLoader / OptiFabric 在 Zalith 后端没有安装实现，因此未列出；" +
                                "所有加载器版本列表均实时来自官方/镜像源。",
                            fontSize = 12,
                            color = p.gray2,
                        )
                    }                } else {
                    PclMcVersionListView(
                        loading = mcLoading,
                        error = mcError,
                        pinned = pinned,
                        grouped = grouped,
                        onPick = { version ->
                            selectedMc = version
                            // 重新选版本 = 换一次安装目标，之前的改名作废，让默认名接管
                            customNameEdited = false
                            customName = version.version.id
                            inSelectPage = true
                        },
                        onRetry = { PclDownloadRefresh.trigger() },
                    )
                }
            }
        }

        // ---- 底部「开始下载」（BtnStart，MyExtraTextButton，只在选择页显示）
        // `PageDownloadInstall.xaml:161` 只有 `HorizontalAlignment="Center" VerticalAlignment="Bottom"`，
        // 没有写死宽度，胶囊按内容撑宽。
        if (inSelectPage) {
            PclExtraTextButton(
                text = PclStrings.LangDownloadInstallStart,
                logo = PclIcons.resourceDownload,
                logoScale = 0.95f,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                onClick = { startInstall() },
            )
        }
    }
}

/**
 * PCL 对 Minecraft 版本的四个分组（`PageDownloadInstall.xaml.vb:446-490`）。
 *
 * `logoRes` 是这一组版本的方块贴图（`McDownloadListItem` 里的图标）。同组所有行共用，
 * 所以只在卡片上取一次 —— 见 `PclMcVersionGroupCard` 里的说明。
 */
internal enum class PclMcGroup(val title: String, val logoRes: Int) {
    Release(PclStrings.LangDownloadRelease, R.drawable.pcl_block_grass),
    Preview(PclStrings.LangDownloadPreview, R.drawable.pcl_block_command_block),
    Ancient(PclStrings.LangDownloadAncientVersion, R.drawable.pcl_block_cobblestone),
    AprilFool(PclStrings.LangDownloadAprilFool, R.drawable.pcl_block_gold_block),
}

/**
 * 默认版本名（PCL 的 `PageDownloadInstall.GetSelectName`，`PageDownloadInstall.xaml.vb:412-420`）。
 *
 * 版本名在 Zalith 里就是 `versions/<版本名>/` 的目录名，也是「这个版本装没装过」的唯一判据
 * （`GameInstaller` 装之前看 `versions/<名>/<名>.json` 在不在）。所以名字**必须把加载器写进去**：
 * 只写 `26.2` 的话，已经装过原版之后再装「26.2 + Fabric」会被当成同一个版本，直接提示已经下载过。
 *
 * 拼法与 PCL 一致：`VanillaName` + `-Fabric <ver>` + `-Forge_<ver>` + `-NeoForge_<ver>`
 * + `-Quilt_<ver>` + `-OptiFine_<ver>` + `-Cleanroom_<ver>`。
 */
private fun pclDefaultVersionName(
    mcId: String,
    fabric: FabricVersion?,
    forge: ForgeVersion?,
    neoForge: NeoForgeVersion?,
    quilt: QuiltVersion?,
    optifine: OptiFineVersion?,
    cleanroom: CleanroomVersion?,
): String = buildString {
    append(mcId)
    // 版本号里 `+` 后面跟着的是兼容的游戏版本（如 `0.16.9+1.21`），PCL 用 BeforeFirst("+") 砍掉
    fabric?.let { append("-Fabric ").append(it.version.substringBefore('+')) }
    forge?.let { append("-Forge_").append(it.versionName.substringBefore('+')) }
    neoForge?.let { append("-NeoForge_").append(it.versionName.substringBefore('+')) }
    quilt?.let { append("-Quilt_").append(it.version.substringBefore('+')) }
    optifine?.let {
        // PCL：`DisplayName.Replace(VanillaName & " ", "").Replace(" ", "_")`
        append("-OptiFine_")
        append(it.displayName.removePrefix("$mcId ").trim().replace(' ', '_'))
    }
    cleanroom?.let { append("-Cleanroom_").append(it.version.substringBefore('+')) }
}

/** 版本发布时间（PCL 用 `releaseTime` 排序与显示）。 */
internal fun MinecraftVersion.pclReleaseTime(): Instant =
    runCatching { OffsetDateTime.parse(version.releaseTime).toInstant() }.getOrDefault(Instant.EPOCH)

/** `GetLocalTimeFormat`：中文环境下是 `yyyy/M/d HH:mm`。 */
internal fun MinecraftVersion.pclReleaseText(): String =
    formatDate(pclReleaseTime().toEpochMilli(), "yyyy/M/d HH:mm")

/**
 * 分组判定。除按 Mojang 的 type 归类外，还包含 PCL 对「被误标成快照」的正式版的修正：
 * `PageDownloadInstall.xaml.vb:459-466` —— id 以 `1.` 开头且不含
 * combat / rc / experimental / pre 的 snapshot 会被归回正式版。
 */
internal fun MinecraftVersion.pclGroupOf(): PclMcGroup {
    val lower = version.id.lowercase()
    return when (type) {
        MinecraftVersion.Type.Release -> PclMcGroup.Release
        MinecraftVersion.Type.AprilFools -> PclMcGroup.AprilFool
        MinecraftVersion.Type.Snapshot -> {
            val looksLikeRelease = version.id.startsWith("1.") &&
                !lower.contains("combat") &&
                !lower.contains("rc") &&
                !lower.contains("experimental") &&
                !lower.contains("pre")
            if (looksLikeRelease) PclMcGroup.Release else PclMcGroup.Preview
        }
        else -> PclMcGroup.Ancient
    }
}

/** 版本条目图标（`McDownloadListItem` 里取的是 Blocks 目录下的方块贴图）。 */
internal fun MinecraftVersion.pclLogoRes(): Int = pclGroupOf().logoRes

/**
 * 版本列表态（`PanMinecraft`）：最新版本卡片 + 四组折叠卡片。
 *
 * 卡片几何取自 PCL：卡片 `Margin="0,15,0,15"`（最新版本）/ `Margin="0,0,0,15"`（分组），
 * 内容容器 `Margin="20,40,18,0"`（见 [PclVersionStackPadding]），而 `MyCard.StackInstall`
 * 会在末尾补 18 高的占位（`MyCard.vb:170`），所以内容下边距直接取 18。
 *
 * 注意：本函数会被放在 `AnimatedContent` 里，而 `AnimatedContent` 会把内容的所有
 * 直接子项**叠在同一个位置上**（它本来就是靠重叠做进出场动画的），
 * 因此这里必须用一层 `Column` 把所有卡片收成**一个**子项。
 */
@Composable
internal fun PclMcVersionListView(
    loading: Boolean,
    error: String?,
    pinned: List<Pair<MinecraftVersion, String>>,
    grouped: List<Pair<PclMcGroup, List<MinecraftVersion>>>,
    onPick: (MinecraftVersion) -> Unit,
    onRetry: () -> Unit,
) {
    val stackPadding = PclVersionStackPadding

    // ---- 版本号搜索
    //
    // PCL 2.9 的 `PageDownloadInstall` 里 `ComboVersion` 旁边就有个搜索框；版本列表一多
    // （正式版 + 快照加起来一千多条），只靠四个折叠分组翻找基本没法用。
    // 有关键词时不再分组，直接给一张「搜索结果」卡片 —— 跨分组的命中混在一起看更直观。
    var keyword by remember { mutableStateOf("") }
    val query = keyword.trim()
    val matched = remember(query, pinned, grouped) {
        if (query.isEmpty()) {
            emptyList()
        } else {
            (pinned.map { it.first } + grouped.flatMap { it.second })
                .distinctBy { it.version.id }
                .filter { it.version.id.contains(query, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!loading && error == null) {
            PclSearchBox(
                value = keyword,
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                hint = "搜索版本号，例如 1.20.1",
                onValueChange = { keyword = it },
            )
        }
        when {
            loading -> PclLoadingCard(
                text = PclStrings.LangDownloadCompLoadingList,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            error != null -> PclCard(title = PclStrings.LangDownloadLatest, modifier = Modifier.fillMaxWidth()) {
                Column {
                    PclHint(text = "版本列表获取失败：$error", theme = PclHintTheme.Red)
                    Spacer(Modifier.height(10.dp))
                    PclButton(text = PclStrings.LangRefresh, onClick = onRetry)
                }
            }

            pinned.isEmpty() && grouped.isEmpty() -> PclCard(
                title = PclStrings.LangDownloadLatest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PclText(text = "没有获取到任何可安装的版本。", color = pclPalette.gray2)
            }

            else -> {
                if (query.isNotEmpty()) {
                    // ---- 搜索结果（跨分组平铺，命中的行直接按版本号列出）
                    PclCard(
                        title = "搜索结果 (${matched.size})",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                        contentPadding = stackPadding,
                    ) {
                        if (matched.isEmpty()) {
                            PclText(text = "没有版本号包含「$query」的版本。", color = pclPalette.gray2)
                        } else {
                            matched.forEach { version ->
                                key(version.version.id) {
                                    PclMcVersionRow(
                                        version = version,
                                        logo = painterResource(version.pclLogoRes()),
                                        onPick = onPick,
                                    )
                                }
                            }
                        }
                    }
                    return@Column
                }
                // 「最新版本」卡片：默认展开、不折叠（SwapType=2）
                PclCard(
                    title = PclStrings.LangDownloadLatest,
                    modifier = Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 15.dp),
                    contentPadding = stackPadding,
                ) {
                    pinned.forEach { (version, prefix) ->
                        PclMcVersionRow(
                            version = version,
                            // 只有这两行，图标就在这里取
                            logo = painterResource(version.pclLogoRes()),
                            infoPrefix = prefix,
                            onPick = onPick,
                        )
                    }
                }
                // 四组卡片：默认折叠（IsSwapped = True），标题带数量
                grouped.forEach { (group, list) ->
                    key(group) {
                        PclMcVersionGroupCard(group = group, list = list, onPick = onPick)
                    }
                }
            }
        }
    }
}

/** 版本列表卡片的内容边距（`Margin="20,40,18,0"`，`MyCard.StackInstall` 末尾补 18）。 */
internal val PclVersionStackPadding = PaddingValues(
    start = 20.dp,
    top = PclDimens.cardContentTop,
    end = 18.dp,
    bottom = 18.dp,
)

/**
 * 一个版本分组卡片（「正式版 (114)」这类）。
 *
 * 这里有两处专门为「展开不卡」做的处理，缺一个都会回到「点开卡半天才开、开了没动画」：
 *
 * 1. **方块图标在卡片上取一次**（`painterResource` 每次都会重新解析一遍矢量资源），
 *    原来写在行里，833 行的「预览版」一展开就是 833 次解析，比组合本身还贵。
 * 2. **行是分批组合的**（[rememberPclRowChunks]）—— 一帧组合 800 多行 ≈ 200ms，
 *    会把 150ms 的展开动画整个吃掉。首帧只组合够铺满一屏的行，其余逐帧补。
 */
@Composable
private fun PclMcVersionGroupCard(
    group: PclMcGroup,
    list: List<MinecraftVersion>,
    onPick: (MinecraftVersion) -> Unit,
) {
    val collapsed = remember { mutableStateOf(true) }
    val chunks = rememberPclRowChunks(total = list.size, collapsed = collapsed)
    val logo = painterResource(group.logoRes)

    PclCard(
        title = "${group.title} (${list.size})",
        modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
        collapsible = true,
        initiallyCollapsed = true,
        swapLogoRight = true,
        collapsedState = collapsed,
        onToggle = chunks.onToggle,
        contentPadding = PclVersionStackPadding,
    ) {
        for (i in 0 until chunks.shown) {
            val version = list[i]
            PclMcVersionRow(
                version = version,
                logo = logo,
                onPick = onPick,
            )
        }
    }
}

/**
 * 版本条目：`McDownloadListItem` —— 高 42、Clickable、方块图标、Info 是发布时间。
 *
 * [infoPrefix] 只有「最新版本」卡片用（如「最新正式版，」）。
 */
@Composable
private fun PclMcVersionRow(
    version: MinecraftVersion,
    logo: Painter,
    onPick: (MinecraftVersion) -> Unit,
    infoPrefix: String = "",
) {
    // 发布时间要 new 一个 SimpleDateFormat + 解析 ISO 串，比组合这一行本身还贵。
    // 必须放在行内的 remember：写在调用处的话，外层每补一批行就会把**所有**行
    // 的发布时间重算一遍（800 行 × 几十次 = 展开时的第二个卡顿源）。
    val info = remember(version, infoPrefix) { infoPrefix + version.pclReleaseText() }

    PclListItem(
        title = version.version.id,
        info = info,
        type = PclListItemType.Clickable,
        height = 42.dp,
        modifier = Modifier.fillMaxWidth(),
        logoContent = {
            // MyListItem 的图标不是固定尺寸：位图分支的可用区是「图标列 38 − (4+3) 宽、
            // 行高 42 − (5+5) 高」，即 31×32。
            //
            // 铺满方式用 `FillBounds` 而不是默认的 `Fit`：PCL 位图分支是
            // `Canvas.Background = MyBitmap(...)`，WPF 的 ImageBrush 默认 `Stretch=Fill`，
            // 也就是**不管原图长宽比、直接拉满整块画布**（`MyListItem.xaml.vb:238-247`）。
            // 用 `Fit` 的话方图会被压成 31×31，比 PCL 矮 1dp。
            Image(
                painter = logo,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        },
        onClick = { onPick(version) },
    )
}

/**
 * 加载器选择态（`PanSelect`）：
 * 版本卡片（返回按钮 26×26 @13、版本图标 32 @52、版本名输入框 @93）+ 各加载器卡片。
 */
@Composable
private fun PclMcSelectPanel(
    version: MinecraftVersion?,
    customName: String,
    onCustomNameChange: (String) -> Unit,
    onBack: () -> Unit,
    /**
     * 左上角版本图标的覆盖值：选了某个加载器后显示对应加载器的方块
     * （Forge→铁砧、NeoForge、Fabric、Quilt→红石、OptiFine→草径、Cleanroom→红石灯），
     * 与 PCL `PageDownloadInstall` 选完加载器后 `ImgLogo` 跟着变的行为一致；
     * null 表示没选加载器，用版本自己的图标（原版 = 草方块）。
     */
    headerLogoRes: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (version == null) return
    val p = pclPalette

    Column(modifier = Modifier.fillMaxWidth()) {
        // MyCard Height="62" UseAnimation="False"
        PclCard(
            modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(start = 13.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // BtnBack：Margin 13 / 26×26 / LogoScale 0.87 / Theme=Black / Opacity 0.6
                PclIconButton(
                    logo = PclIcons.back,
                    theme = PclIconTheme.Black,
                    size = 26.dp,
                    logoScale = 0.87f,
                    contentDescription = PclStrings.LangDownloadInstallHintBackTip,
                    modifier = Modifier.graphicsLayer(alpha = 0.6f),
                    onClick = onBack,
                )
                Spacer(Modifier.width(13.dp))
                // ImgLogo：Margin="52,0,0,0" Height="32"，即紧接按钮后 13 的间距
                Image(
                    painter = painterResource(headerLogoRes ?: version.pclLogoRes()),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(9.dp))
                // TextSelectName：Margin="93,0,16,0" Padding="-1,0" MinHeight=30 FontSize=15 HasBackground=False
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 30.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = customName,
                        onValueChange = onCustomNameChange,
                        singleLine = true,
                        textStyle = PclTextStyle.base(p).copy(fontSize = 15.sp),
                        cursorBrush = SolidColor(p.color3),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(15.dp))
        content()
    }
}

/** 测速结果的有效期（超过就重新测）。 */
private const val SOURCE_PROBE_VALID_MILLIS = 24L * 60 * 60 * 1000

/**
 * 下载源卡片（**PCL 原版没有这一块，是本移植新增的**）。
 *
 * 原来的「游戏内容下载源 = 自动」只按「是否中国大陆」静态判定：一旦某个源临时变慢，
 * 整批文件都会先在它身上耗完重试才换源，表现出来就是「下载卡住」。
 * 这里把各源的实测速度摊开，并提供一键测速——测完自动写回设置，
 * 之后所有走 `mapBMCLMirrorUrls()` 的下载（版本本体 / 支持库 / 资源文件 / 加载器）都会用最快的那个。
 */
@Composable
private fun PclDownloadSourceCard(runtime: PclRuntime) {
    val p = pclPalette
    val context = LocalContext.current

    // 第一次进到这一页没测过速、或上次测速超过一天，就自动测一次（不需要用户手动点）
    LaunchedEffect(Unit) {
        val stale = runtime.sourceProbeTime <= 0L ||
            System.currentTimeMillis() - runtime.sourceProbeTime > SOURCE_PROBE_VALID_MILLIS
        if (runtime.downloadSource == MirrorSourceType.AUTO &&
            (runtime.measuredFastestSource == MirrorSourceType.AUTO || stale)
        ) {
            runtime.probeDownloadSources()
        }
    }

    val measuredText = when (runtime.measuredFastestSource) {
        MirrorSourceType.MIRROR -> "BMCL API 镜像"
        MirrorSourceType.OFFICIAL -> "官方源"
        MirrorSourceType.AUTO -> "还没测过"
    }
    val statusText = when (runtime.downloadSource) {
        MirrorSourceType.AUTO ->
            if (runtime.measuredFastestSource == MirrorSourceType.AUTO) {
                "自动：还没测过速，暂时按当前网络环境静态判定。"
            } else {
                "自动：已选用实测最快的 $measuredText。"
            }

        MirrorSourceType.OFFICIAL -> "已强制使用官方源（不注入镜像）。"
        MirrorSourceType.MIRROR -> "已强制使用 BMCL API 镜像源。"
    }

    PclCard(
        title = "下载源",
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        contentPadding = PaddingValues(start = 20.dp, top = 40.dp, end = 18.dp, bottom = 15.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PclComboBox(
                    items = MirrorSourceType.entries,
                    selected = runtime.downloadSource,
                    modifier = Modifier.weight(1f),
                    label = { item -> item?.let { context.getString(it.textRes) }.orEmpty() },
                    onSelect = { runtime.chooseDownloadSource(it) },
                )
                Spacer(Modifier.width(10.dp))
                PclButton(
                    text = if (runtime.isProbingSources) "测速中…" else "测速选最快",
                    enabled = !runtime.isProbingSources,
                    onClick = { runtime.probeDownloadSources() },
                )
            }
            Spacer(Modifier.height(8.dp))
            PclText(text = statusText, fontSize = 12, color = p.gray2)
            runtime.sourceSpeeds.forEach { speed ->
                PclText(
                    text = speed.describe(),
                    fontSize = 12,
                    color = if (speed.ok) p.gray2 else p.gray4,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            PclText(
                text = "测速会各拉一次版本清单（约 200 KB）比吞吐与延迟，换个网络后建议重新测。",
                fontSize = 12,
                color = p.gray4,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * 安装页里的加载器卡片。
 *
 * 结构照抄 `PageDownloadInstall.xaml` 里每个 `MyCard`（Forge / NeoForge / Fabric …）：
 *   · `CanSwap="True" IsSwapped="True" SwapLogoRight="True" Height="40" Margin="0,12,0,0"`
 *     —— 默认**折叠**，标题左边是加载器名（`MainTextBlock`），箭头在右边；
 *   · `PanXxxInfo`：`Grid Height="18" Margin="132,11,15,0" VerticalAlignment="Top"`，
 *     折叠时显示「方块贴图（18 高、右边距 7）+ 一行说明」；
 *     没选版本时贴图隐藏、文字是「可以添加」且用 `ColorGray4`；
 *     选了版本时贴图显示、文字换成版本号且用 `ColorGray1`（`ReloadSelected`）。
 *     `SetPanelVisibility(Panel, Card.IsSwapped)` 只在折叠时可见。
 *   · `BtnXxxClear`：`30×30 Margin="0,5,32,0"` 右上角、内嵌 10×10 的叉（`ColorBrushGray1`），
 *     只在选了版本时可见；点它清空选择。
 *   · 展开后的内容 `StackPanel Margin="20,40,18,15"`，是一串 `MyListItem`
 *     （高 42、方块贴图、标题=版本号、副标题=稳定版/测试版/发布于…）。
 *
 * 选中即自动折叠（`Forge_Selected` 里的 `CardForge.IsSwapped = True`）。
 */
@Composable
private fun <T> PclLoaderCard(
    state: PclLoaderState<T>,
    title: String,
    logoRes: Int,
    labelOf: (T) -> String,
    infoOf: (T) -> String?,
    enabled: Boolean = true,
    /**
     * 与已选的其他加载器冲突时的原因；非 null 就禁用这张卡片（不可展开、也不能选），
     * 并把原因显示在原「可以添加」的位置。对应 PCL 的 `CardXxx_PreviewSwap` 里
     * `If LoadXxxGetError() IsNot Nothing Then e.Handled = True`（拦截展开）。
     */
    disabledReason: String? = null,
) {
    val p = pclPalette
    val selected = state.selected
    val collapsedState = remember { mutableStateOf(true) }
    // 展开后逐批组合版本列表：首帧只铺一屏，动画结束后每帧补一批（见 rememberPclRowChunks）。
    val chunks = rememberPclRowChunks(state.items.size, collapsedState)
    // 已选中的卡片不受互斥影响：PCL 的 `ReloadSelected`（:203-221）里，只要
    // SelectedXxx 不为空就无条件显示版本名，冲突提示只在「还没选」时才出现。
    // 这样用户仍然能看见自己选了什么、也还能按叉清掉。
    val blocked = !enabled || (disabledReason != null && selected == null)
    val canExpand = !blocked && state.items.isNotEmpty()

    // 选中后自动折叠（PCL 的 Forge_Selected / Fabric_Selected …）
    LaunchedEffect(selected) { if (selected != null) collapsedState.value = true }
    // 被对方加载器顶掉时强制折叠，避免展开着又点不进去（`ReloadSelected` 里的 IsSwapped = True）
    LaunchedEffect(blocked) { if (blocked) collapsedState.value = true }

    val infoText = when {
        selected != null -> labelOf(selected)
        !enabled -> PclStrings.LangDownloadInstallNeedMc
        disabledReason != null -> disabledReason
        state.loading -> PclStrings.LangDownloadInstallLoadingList
        state.error != null -> state.error ?: PclStrings.LangDownloadInstallNoVersion
        state.items.isEmpty() -> PclStrings.LangDownloadInstallNoVersion
        else -> PclStrings.LangDownloadInstallClickToChose
    }

    PclCard(
        title = title,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        collapsible = canExpand,
        initiallyCollapsed = true,
        collapsedState = collapsedState,
        onToggle = chunks.onToggle,
        swapLogoRight = true,
        contentPadding = PaddingValues(start = 20.dp, top = 40.dp, end = 18.dp, bottom = 15.dp),
        collapsedContent = {
            Row(
                modifier = Modifier
                    .padding(start = 132.dp, top = 11.dp, end = 15.dp)
                    .height(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected != null) {
                    Image(
                        painter = painterResource(logoRes),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = infoText,
                    style = PclTextStyle.base(p).copy(
                        fontSize = 13.sp,
                        color = if (selected != null) p.color1 else p.gray4,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        titleAccessory = {
            if (selected != null) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, end = 32.dp)
                        .size(30.dp)
                        .background(p.semiTransparent)
                        .clickable {
                            state.selected = null
                            collapsedState.value = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PclIcons.close,
                        contentDescription = PclStrings.LangDownloadInstallClear,
                        tint = p.gray1,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                state.loading -> PclLoading(
                    text = PclStrings.LangDownloadInstallLoadingList,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                state.error != null -> {
                    PclHint(text = "获取失败：${state.error}", theme = PclHintTheme.Red)
                    Spacer(Modifier.height(10.dp))
                    PclButton(text = PclStrings.LangRefresh, onClick = { PclDownloadRefresh.trigger() })
                }

                state.items.isEmpty() -> PclText(
                    text = "该游戏版本下没有可用的 $title 版本。",
                    fontSize = 12,
                    color = p.gray2,
                )

                else -> {
                    // Fabric / Quilt 一个 MC 版本下能列出两三百个版本号。
                    // 以前这里把整张表一次性组合，点开卡片要卡住好几秒（所以「点了没反应」）；
                    // 用项目里现成的分批组合：先铺一屏，动画结束后逐帧补。
                    val count = minOf(chunks.shown, state.items.size)
                    for (i in 0 until count) {
                        val item = state.items[i]
                        PclListItem(
                            title = labelOf(item),
                            info = infoOf(item),
                            type = PclListItemType.Clickable,
                            height = 42.dp,
                            modifier = Modifier.fillMaxWidth(),
                            logoContent = {
                                Image(
                                    painter = painterResource(logoRes),
                                    contentDescription = null,
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            onClick = { state.selected = item },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 展示 `TitledTask` 任务流的弹窗（安装 / 导入整合包时使用）。
 */
@Composable
internal fun PclTitledTaskDialog(
    title: String,
    tasks: kotlinx.coroutines.flow.StateFlow<List<TitledTask>>,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = pclPalette
    val context = LocalContext.current
    val list by tasks.collectAsState()

    // 任务进行中：不允许点外部关掉（原来的 `onDismissRequest = { }` 就是这个意思），
    // 但「后台运行」按钮仍然走 requestClose，于是收场时照样有退出动画。
    PclDialogContainer(onDismiss = onDismiss, dismissOnOutsideClick = false) { requestClose ->
        PclCard(
            modifier = Modifier.fillMaxWidth().widthIn(min = 320.dp, max = 560.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Text(
                text = title,
                style = PclTextStyle.cardTitle(p).copy(fontSize = 15.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (list.isEmpty()) {
                Text(
                    text = "准备中…",
                    style = PclTextStyle.base(p),
                    modifier = Modifier.padding(top = 14.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    list.forEach { titled ->
                        val progress by titled.task.progress.collectAsState()
                        val message by titled.task.message.collectAsState()
                        val speed by titled.task.rateBytesPerSec.collectAsState()
                        val stage by titled.task.stage.collectAsState()
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = message?.toAndroidString(context)?.takeIf { it.isNotBlank() }
                                        ?: titled.title.toAndroidString(context),
                                    style = PclTextStyle.listTitle(p),
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                if (speed != null && speed!! > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(text = formatSpeed(speed!!), style = PclTextStyle.listInfo(p))
                                }
                            }
                            if (progress >= 0f && stage != TaskStage.COMPLETED) {
                                PclProgressBar(progress = progress, modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 18.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                PclButton(text = "取消", colorState = PclColorState.Red, onClick = onCancel)
                Spacer(Modifier.width(10.dp))
                PclButton(text = "后台运行", colorState = PclColorState.Highlight, onClick = requestClose)
            }
        }
    }
}

private fun formatSpeed(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB/s".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "%.0f KB/s".format(bytes / 1024.0)
    else -> "$bytes B/s"
}

/* ---------------------------------------------------------------- 社区资源 */

/** 与某个社区资源页分区对应的平台类型与目标文件夹。 */
private fun platformClassesFor(section: Int): PlatformClasses = when (section) {
    1 -> PlatformClasses.MOD
    2 -> PlatformClasses.MOD_PACK
    3 -> PlatformClasses.SAVES
    4 -> PlatformClasses.RESOURCE_PACK
    else -> PlatformClasses.SHADERS
}

/** 每页条目数（`PageResource.PAGE_SIZE`）。 */
private const val PCL_ASSET_PAGE_SIZE = 40

/** 「自定义版本」这个特殊下拉项的显示文本。 */
private const val PCL_VERSION_CUSTOM = "自定义…"

/** 来源候选；`null` 表示「全部」（`LangDownloadCompSearchSourceAll`）。 */
private fun pclSourceChoices(classes: PlatformClasses): List<Platform?> =
    if (classes == PlatformClasses.SAVES) listOf(null, Platform.CURSEFORGE)
    else listOf(null, Platform.CURSEFORGE, Platform.MODRINTH)

/** 某一平台下可用的类别筛选列表（映射与 Zalith 原生搜索页保持一致）。 */
private fun pclCategoryChoices(platform: Platform, classes: PlatformClasses): List<PlatformFilterCode> =
    when (classes) {
        PlatformClasses.MOD ->
            if (platform == Platform.CURSEFORGE) CurseForgeModCategory.entries else ModrinthModCategory.entries

        PlatformClasses.MOD_PACK ->
            if (platform == Platform.CURSEFORGE) CurseForgeModpackCategory.entries else ModrinthModpackCategory.entries

        PlatformClasses.RESOURCE_PACK ->
            if (platform == Platform.CURSEFORGE) CurseForgeResourcePackCategory.entries
            else ModrinthResourcePackCategory.entries

        PlatformClasses.SHADERS ->
            if (platform == Platform.CURSEFORGE) CurseForgeShadersCategory.entries else ModrinthShadersCategory.entries

        PlatformClasses.SAVES -> CurseForgeSavesCategory.entries
    }

/** 某一平台下可用的 Mod 加载器筛选列表（只有 Mod / 整合包才有意义）。 */
private fun pclModLoaderChoices(platform: Platform, classes: PlatformClasses): List<PlatformDisplayLabel> =
    if (classes == PlatformClasses.MOD || classes == PlatformClasses.MOD_PACK) {
        if (platform == Platform.CURSEFORGE) curseForgeModLoaderFilters else modrinthModLoaderFilters
    } else {
        emptyList()
    }

/** `PageResource.xaml` 里「版本」下拉框预设的版本号（原文照抄）。 */
private val PCL_COMMON_GAME_VERSIONS = listOf(
    "26.1", "1.21.11", "1.21.8", "1.21.4", "1.21.1", "1.20.1",
    "1.19.2", "1.18.2", "1.16.5", "1.12.2", "1.7.10",
)

/**
 * 原版 Minecraft 版本号的三种合法写法。
 *
 * 搜索平台的 `gameVersion` 参数只认这类字符串，传实例名（`KPBL`、`1.20.1-Fabric`）
 * 服务端不会报错，但一定搜不到东西 —— 所以筛选列表必须先按这个筛一遍。
 */
private val PLAIN_GAME_VERSION = Regex(
    """^(\d+(\.\d+)+|\d{2}w\d{2}[a-z]|\d+(\.\d+)+-(pre|rc)\d+)$"""
)

/** 是否是标准的原版版本号（正式版 / 快照 / 预发布），用来把自定义实例名挡在筛选列表外。 */
private fun String.isPlainGameVersion(): Boolean = PLAIN_GAME_VERSION.matches(trim())

/**
 * 搜索卡片里「镜像源」的短标签。
 *
 * 设置页那一套文案是「尽量使用官方源」这种长句，塞进半行宽的下拉框会被截断，
 * 这里用短词，语义不变。
 */
private fun mirrorSourceShortLabel(type: MirrorSourceType): String = when (type) {
    MirrorSourceType.AUTO -> "自动"
    MirrorSourceType.OFFICIAL -> "官方"
    MirrorSourceType.MIRROR -> "MCIM 镜像"
}

/**
 * 按平台换算筛选项。
 *
 * Zalith 的请求构造函数对类别 / 加载器用的是安全转换
 * （`toCurseForgeRequest` 的 `as? CurseForgeCategory`、`toModrinthRequest` 的 `as? ModrinthFacet`），
 * 跨平台的筛选项会被静默丢弃。这里先按目标平台自己的列表过滤一次：
 * 类别必须属于该平台，加载器按显示名跨平台对齐（Forge / Fabric / Quilt / NeoForge 两边同名）。
 */
private fun PlatformSearchFilter.pclForPlatform(
    platform: Platform,
    classes: PlatformClasses,
): PlatformSearchFilter {
    val knownCategories = pclCategoryChoices(platform, classes)
    val knownLoaders = pclModLoaderChoices(platform, classes)
    return copy(
        categories = categories.filter { it in knownCategories },
        modloader = modloader?.let { chosen ->
            knownLoaders.firstOrNull { it.getDisplayName() == chosen.getDisplayName() }
        },
    )
}

/** 合并后的搜索结果（来源选「全部」时把两个平台的当前页拼在一起）。 */
private class PclAssetPage(
    val data: List<Pair<PlatformSearchData, ModTranslations.McMod?>>,
    val pageNumber: Int,
    val totalPage: Int,
    val isLastPage: Boolean,
    /** 来源选「全部」时某个平台失败的提示（对应 PCL 的 `HintError`）。 */
    val hint: String?,
)

/**
 * 社区资源页的搜索现场（每个分栏一份）。
 *
 * **为什么需要它**：点进某个模组看详情是 `nav.push(详情页)`，右栏整块被替换，
 * `PclDownloadResourcePage` 因此离开组合，里面所有 `remember` 一起作废 ——
 * 表现就是「搜索关键词、筛选条件、结果列表全没了，返回后要重新搜一遍」。
 * PCL 原版的页面对象是常驻的（WPF 的 `PageResource` 实例不销毁），不存在这个问题。
 *
 * 所以把这几项搬到这个文件级单例里：页面每次组合时从槽位读初值、改动直接写回槽位，
 * 返回时自然接上原来的搜索现场。结果页（[PclAssetPage]）也一起缓存，
 * 返回后不必再发一次请求，直接显示上次那一页。
 */
private class PclAssetSearchSlot {
    // 直接用 `MutableState` 而不是 `by mutableStateOf`：调用点要 `var x by slot.x` 委托到它，
    // 委托目标必须是 `MutableState<T>` 本身，不能是已经拆包出来的 `T`。
    val source = mutableStateOf<Platform?>(null)
    val keyword = mutableStateOf("")
    val gameVersion = mutableStateOf<String?>(null)
    val tag = mutableStateOf<PlatformFilterCode?>(null)
    val sortField = mutableStateOf<PlatformSortField?>(null)
    val modLoader = mutableStateOf<PlatformDisplayLabel?>(null)
    val versionInput = mutableStateOf(false)
    val page = mutableStateOf<PclAssetPage?>(null)
    val pageIndex = mutableIntStateOf(0)

    /** 上次真正发起搜索时的筛选条件；`null` = 这个分栏还没搜过。 */
    val lastSearchedFilters = mutableStateOf<List<Any?>?>(null)

    /**
     * 已经响应过的左栏刷新令牌（普通字段，不参与重组）。
     *
     * `LaunchedEffect(refreshToken)` 在每次重新组合时都会先跑一遍，
     * 不记一下的话「从详情页返回」会被当成点了刷新，又白搜一次。
     * `-1` 表示还没对过号，首次组合只登记、不触发。
     */
    var refreshTokenSeen: Int = -1
}

private object PclAssetSearchMemory {
    private val slots = HashMap<Int, PclAssetSearchSlot>()

    fun of(section: Int): PclAssetSearchSlot = slots.getOrPut(section) { PclAssetSearchSlot() }
}

/** 对单个平台执行一次搜索并把结果包成 [Result]（`searchAssets` 是回调式接口）。 */
private suspend fun pclSearchOnce(
    platform: Platform,
    filter: PlatformSearchFilter,
    classes: PlatformClasses,
    context: android.content.Context,
): Result<AssetsPage> {
    val deferred = CompletableDeferred<Result<AssetsPage>>()
    searchAssets(
        searchPlatform = platform,
        searchFilter = filter,
        platformClasses = classes,
        onSuccess = { result -> deferred.complete(Result.success(result.getAssetsPage(classes))) },
        onError = { state ->
            deferred.complete(
                Result.failure(
                    IllegalStateException(state.message.toAndroidString(context)),
                ),
            )
        },
    )
    return deferred.await()
}

/** 某一个资源分区对应的右下角图标（作为项目图标缺失时的兜底）。 */
internal fun pclResourceIconFor(classes: PlatformClasses): ImageVector = when (classes) {
    PlatformClasses.MOD -> PclIcons.instanceMod
    PlatformClasses.MOD_PACK -> PclIcons.instanceExport
    PlatformClasses.RESOURCE_PACK -> PclIcons.dlResourcePack
    PlatformClasses.SHADERS -> PclIcons.dlShader
    PlatformClasses.SAVES -> PclIcons.dlDataPack
}

/**
 * 底栏左列的「支持版本 / 加载器」。
 *
 * PCL 用 Drop 区间算出「1.16+」这类描述（`ResourceProject.ToResourceItem:455-501`）；
 * Zalith 的搜索结果只提供加载器与游戏版本号列表，这里按「加载器 + 最高版本+」表达
 * （列表里不止一个版本时补 `+`，与 PCL 的区间写法观感一致）。
 */
internal fun PlatformSearchData.pclVersionText(): String {
    val loaders = platformModLoaders().orEmpty().joinToString(" / ") { it.getDisplayName() }
    val versions = platformGameVersions().orEmpty()
    val gamePart = when {
        versions.isEmpty() -> ""
        versions.size == 1 -> versions.first()
        else -> versions.first() + "+"
    }
    return listOf(loaders, gamePart).filter { it.isNotBlank() }.joinToString(" ")
}

/** 下载量文案（`ResourceProject.ToResourceItem:565-567`：亿四舍五入两位、万向下取整）。 */
internal fun formatCount(count: Long): String = when {
    count > 100_000_000 -> String.format(java.util.Locale.US, "%.2f 亿", count / 100_000_000.0)
    count > 100_000 -> String.format(java.util.Locale.US, "%d 万", count / 10_000)
    else -> count.toString()
}

/**
 * 社区资源列表页（`PageResource`）——
 * 搜索卡片（搜索框 / 来源 / 标签 / 排序方式 / 版本 / 加载器）+ 结果列表（`MyResourceItem`）。
 *
 * 真实搜索走 `searchAssets`；下载仍走 `downloadSingleForVersions` / 依赖下载 / 整合包导入。
 */
@Composable
private fun PclDownloadResourcePage(nav: PclNavController, section: Int) {
    val p = pclPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = LocalPclRuntime.current
    val classes = platformClassesFor(section)
    val refreshToken = PclDownloadRefresh.token

    val categoryName = when (section) {
        1 -> PclStrings.LangDownloadPageLeftMods
        2 -> PclStrings.LangDownloadPageLeftModpack
        3 -> "存档"
        4 -> PclStrings.LangDownloadPageLeftResourcepack
        else -> PclStrings.LangDownloadPageLeftShader
    }

    // ---- 筛选状态（默认值与 PCL 搜索卡片一致：全部 / 全部 / 默认 / 任意 / 任意）
    //
    // 不放在 `remember` 里而是存进 [PclAssetSearchMemory]：点开源码里的模组详情是
    // `nav.push`，本页会整块离开组合，`remember` 全部作废 → 搜索关键词被清空。
    // 每个分栏各占一个槽位，切换分栏依然互不干扰。
    val sourceChoices = remember(classes) { pclSourceChoices(classes) }
    val searchSlot = PclAssetSearchMemory.of(section)
    var source by searchSlot.source
    var keyword by searchSlot.keyword
    var gameVersion by searchSlot.gameVersion
    var tag by searchSlot.tag
    var sortField by searchSlot.sortField
    var modLoader by searchSlot.modLoader
    var versionInput by searchSlot.versionInput

    // 标签 / 加载器候选跟着「来源」走；选「全部」时以 CurseForge 的分类为准
    // （下发时按平台换算，见 PlatformSearchFilter.pclForPlatform）。
    val tagPlatform = source ?: Platform.CURSEFORGE
    val tagChoices = remember(tagPlatform, classes) { pclCategoryChoices(tagPlatform, classes) }
    val modLoaderChoices = remember(tagPlatform, classes) { pclModLoaderChoices(tagPlatform, classes) }

    // 版本候选：任意 + 当前已装版本（仅在它是原版版本号时）+ PCL 预设的常用版本。
    //
    // 【为什么不直接把已装版本名塞进来】`currentVersion` 给的是 `versions/<目录名>`，
    // 用户改过名的实例（`KPBL`、`1.20.1-整合包测试`、`通用`）会原样出现在下拉里，
    // 既看不出是什么版本，选了也搜不到东西（平台只认 `1.21.1` 这种原版版本号）。
    // 所以这里只放真正的原版版本号，实例名一律不进筛选列表。
    val installedVersion = runtime.currentVersion.value?.getVersionName()
    val versionChoices = remember(installedVersion) {
        listOf<String?>(null) +
            listOfNotNull(installedVersion?.takeIf { it.isPlainGameVersion() }) +
            PCL_COMMON_GAME_VERSIONS.filter { it != installedVersion }
    }

    // 下载线路（官方 / MCIM 镜像）：直接读写全局「资源平台下载源」，
    // 与 设置 → 其他 → 下载 里那一项是同一个值，改哪边都一样。
    val mirrorSource = AllSettings.assetPlatformSource.state
    // 官方 CurseForge 强制要求 x-api-key；当前构建没配时选「官方」必定 403，
    // 与其让用户对着「(403) 您没有权限访问此资源」猜，不如提前说清楚。
    val officialCfUnavailable = remember { BuildKeys.CURSEFORGE_API.isBlank() }

    // ---- 结果状态
    // loading / error 是纯瞬态，保持 `remember`；结果页与页码跟着槽位走，
    // 返回时直接复用上次的列表，不再白搜一次。
    var loading by remember(classes) { mutableStateOf(false) }
    var error by remember(classes) { mutableStateOf<String?>(null) }
    var page by searchSlot.page
    var pageIndex by searchSlot.pageIndex
    var pendingPick by remember { mutableStateOf<PendingAssetPick?>(null) }
    // 筛选条件变化会自动重搜，连着切几下就会有多个请求同时在路上。
    // 这里用一个递增序号做「后来者居上」：只有最后一次请求允许写回结果与 loading，
    // 否则先发出去的慢请求晚回来，会把新筛选条件的结果覆盖掉。
    var searchSeq by remember(classes) { mutableStateOf(0) }

    fun search(newIndex: Int) {
        // 来源选「全部」时逐个平台各搜一页再合并（PCL 的 Sources 位标志语义）
        val targets = source?.let { listOf(it) } ?: sourceChoices.mapNotNull { it }
        val baseFilter = PlatformSearchFilter(
            searchName = keyword.trim(),
            gameVersion = gameVersion.orEmpty(),
            sortField = sortField ?: PlatformSortField.RELEVANCE,
            categories = listOfNotNull(tag),
            modloader = modLoader,
            index = newIndex,
            limit = PCL_ASSET_PAGE_SIZE,
        )
        val seq = searchSeq + 1
        searchSeq = seq
        pageIndex = newIndex
        loading = true
        error = null
        scope.launch {
            val results = targets.map { platform ->
                platform to pclSearchOnce(
                    platform = platform,
                    filter = baseFilter.pclForPlatform(platform, classes),
                    classes = classes,
                    context = context,
                )
            }
            // 期间用户又改了筛选条件，这次的结果已经过期，直接丢掉
            if (seq != searchSeq) return@launch
            val pages = results.mapNotNull { it.second.getOrNull() }
            val failure = results.firstNotNullOfOrNull { it.second.exceptionOrNull()?.message }
            if (pages.isEmpty()) {
                error = failure ?: "没有获取到结果。"
                page = null
            } else {
                page = PclAssetPage(
                    data = pages.flatMap { it.data },
                    pageNumber = pages.first().pageNumber,
                    totalPage = pages.maxOf { it.totalPage },
                    isLastPage = pages.all { it.isLastPage },
                    hint = if (pages.size < targets.size) failure else null,
                )
            }
            loading = false
        }
    }

    // PCL 的 PageResource 在页面载入时就会自己拉一次列表（`PageResource_Loaded` 里
    // `PageLoaderInit` 会直接启动 Loader），所以进入「模组 / 整合包 / 存档 / 资源包 / 光影包」
    // 这些分栏时不需要先点「搜索」也能看到内容。
    LaunchedEffect(classes) {
        if (page == null && !loading) search(0)
    }

    // 左栏刷新按钮 → 已有结果则重新搜索。
    // `LaunchedEffect` 在页面重新组合时也会先跑一遍，「从模组详情页返回」正好属于这种，
    // 那不是用户点了刷新，所以先拿令牌对一次号（`refreshTokenSeen`）再决定要不要搜。
    LaunchedEffect(refreshToken) {
        val seen = searchSlot.refreshTokenSeen
        searchSlot.refreshTokenSeen = refreshToken
        if (seen >= 0 && seen != refreshToken && page != null) search(pageIndex)
    }

    // ---- 筛选条件一变就重搜（来源 / 标签 / 排序方式 / 版本 / 加载器）
    //
    // 【这里是有意偏离 PCL 原版的一处】原版 `PageResource.xaml.vb` 里，三个下拉
    // （`ComboSearchSource` / `ComboSearchTag` / `ComboSearchLoader`）无论在 xaml 还是
    // vb 侧都没有挂 `SelectionChanged`；版本框的 `TextChanged` 也只用来切换加载器
    // 下拉的可见性（`UpdateSearchLoaderVisibility`，见该文件 245-257 行）。
    // 原版只有三条重搜入口：`BtnSearchRun.Click`、名称/版本框回车（`EnterTrigger`）、
    // 以及点重置。换言之在 PCL 上改完筛选必须再点一次「搜索」。
    // 这里按需求改成「改完即搜」，把这一步省掉。
    //
    // 注意 `keyword`（搜索框文字）不在此列：它是边打边变的，仍按原版走
    // 「点搜索按钮 / 键盘回车」，否则每敲一个字都会发一次请求。
    // 镜像源也在里面：切换线路必须重搜一次，否则看到的是旧线路的结果
    val filterKey = listOf<Any?>(source, tag, sortField, gameVersion, modLoader, mirrorSource)
    // 上次真正发起搜索时的条件，存在槽位里：返回页面时它跟当前条件一致，
    // 于是不会把「从详情页回来」错当成「刚改了筛选」再搜一次。
    // `null` = 这个分栏还没搜过，首次组合只登记、不触发（首屏那次由上面的 `LaunchedEffect(classes)` 负责）。
    var lastSearchedFilters by searchSlot.lastSearchedFilters
    LaunchedEffect(filterKey) {
        val previous = lastSearchedFilters
        lastSearchedFilters = filterKey
        if (previous == null || filterKey == previous) return@LaunchedEffect
        search(0)
    }

    PclContent {
        // ---- 搜索卡片（PageResource.PanAlways）
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PclSearchBox(
                    value = keyword,
                    modifier = Modifier.weight(1f),
                    hint = "搜索$categoryName",
                    onSearch = { search(0) },
                    onValueChange = { keyword = it },
                )
                Spacer(Modifier.width(12.dp))
                PclButton(
                    text = PclStrings.LangDownloadCompSearchRun,
                    colorState = PclColorState.Highlight,
                    onClick = { search(0) },
                )
                Spacer(Modifier.width(8.dp))
                // 刷新按钮（PageResource 顶部的刷新）。放在搜索框这一行，
                // 给下面的「加载器 / 镜像源」腾出并列的位置。
                PclIconButton(
                    logo = PclIcons.refresh,
                    size = 28.dp,
                    logoScale = 0.85f,
                    contentDescription = PclStrings.LangRefresh,
                    onClick = { search(pageIndex) },
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PclFilterRow(label = PclStrings.LangDownloadCompSearchSource, modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = sourceChoices,
                        selected = source,
                        modifier = Modifier.fillMaxWidth(),
                        label = { it?.displayName ?: PclStrings.LangDownloadCompSearchSourceAll },
                        onSelect = {
                            source = it
                            // 平台换了，类别与加载器都作废（与 Zalith 原生搜索页一致）
                            tag = null
                            modLoader = null
                        },
                    )
                }
                Spacer(Modifier.width(12.dp))
                PclFilterRow(label = PclStrings.LangDownloadCompTag, modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = listOf<PlatformFilterCode?>(null) + tagChoices,
                        selected = tag,
                        modifier = Modifier.fillMaxWidth(),
                        label = { item ->
                            item?.let { context.getString(it.getDisplayName()) }
                                ?: PclStrings.LangDownloadCompSearchSourceAll
                        },
                        onSelect = { tag = it },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                PclFilterRow(label = PclStrings.LangDownloadCompSearchSort, modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = listOf<PlatformSortField?>(null) + PlatformSortField.entries,
                        selected = sortField,
                        modifier = Modifier.fillMaxWidth(),
                        label = { item ->
                            item?.let { context.getString(it.getDisplayName()) }
                                ?: PclStrings.LangDownloadCompSearchSortDefault
                        },
                        onSelect = { sortField = it },
                    )
                }
                Spacer(Modifier.width(12.dp))
                PclFilterRow(label = PclStrings.LangDownloadCompSearchVersion, modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = versionChoices + PCL_VERSION_CUSTOM,
                        selected = gameVersion,
                        modifier = Modifier.fillMaxWidth(),
                        label = { item ->
                            if (item == PCL_VERSION_CUSTOM) PCL_VERSION_CUSTOM
                            else item ?: PclStrings.LangDownloadCompSearchAny
                        },
                        onSelect = { item ->
                            if (item == PCL_VERSION_CUSTOM) versionInput = true else gameVersion = item
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PclFilterRow(label = PclStrings.LangDownloadCompSearchLoader, modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = listOf<PlatformDisplayLabel?>(null) + modLoaderChoices,
                        selected = modLoader,
                        modifier = Modifier.fillMaxWidth(),
                        label = { item ->
                            item?.getDisplayName()?.takeIf { it.isNotBlank() }
                                ?: PclStrings.LangDownloadCompSearchAny
                        },
                        onSelect = { modLoader = it },
                    )
                }
                Spacer(Modifier.width(12.dp))
                // 下载线路：官方 / MCIM 镜像切换（与 设置 → 其他 → 下载 的「资源平台下载源」同一项）
                PclFilterRow(label = "镜像源", modifier = Modifier.weight(1f)) {
                    PclComboBox(
                        items = MirrorSourceType.entries.toList(),
                        selected = mirrorSource,
                        modifier = Modifier.fillMaxWidth(),
                        label = { item -> item?.let { mirrorSourceShortLabel(it) } ?: "自动" },
                        onSelect = { AllSettings.assetPlatformSource.save(it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        // 官方 CurseForge 强制要求 x-api-key。本构建没配 key 时选「官方」一定 403，
        // 提前点明，比等用户看到「(403) 您没有权限访问此资源」再自己猜要省事。
        if (officialCfUnavailable && mirrorSource == MirrorSourceType.OFFICIAL &&
            (classes == PlatformClasses.MOD || classes == PlatformClasses.MOD_PACK)
        ) {
            PclHint(
                text = "当前走「官方」线路，但官方 CurseForge 需要 API Key、本版本没有内置，" +
                        "搜索 CurseForge 会返回 403。把上面「镜像源」切到「MCIM 镜像」即可正常搜索。",
                theme = PclHintTheme.Yellow,
            )
            Spacer(Modifier.height(15.dp))
        }

        // ---- 结果（PageResource.PanContent → CardProjects → PanProjects Margin=12）
        when {
            loading -> PclLoadingCard(
                text = PclStrings.LangDownloadCompGettingList.replace("{0}", categoryName),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            error != null -> PclCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PclHint(
                        text = PclStrings.LangDownloadCompLoadFail + "：$error",
                        theme = PclHintTheme.Red,
                    )
                    Spacer(Modifier.height(10.dp))
                    PclButton(text = "重试", onClick = { search(pageIndex) })
                }
            }

            page == null -> PclCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PclText(text = PclStrings.LangDownloadCompSearchTips, color = p.gray2)
                    Spacer(Modifier.height(8.dp))
                    PclText(
                        text = "目标版本：" +
                            (runtime.currentVersion.value?.getVersionName() ?: "未选择"),
                        color = p.gray2,
                        fontSize = 12,
                    )
                }
            }

            page!!.data.isEmpty() -> PclCard(modifier = Modifier.fillMaxWidth()) {
                PclText(text = PclStrings.LangDownloadCompEmptyResult, color = p.gray2)
            }

            else -> {
                page!!.hint?.let { hint ->
                    PclHint(text = hint, theme = PclHintTheme.Yellow)
                    Spacer(Modifier.height(15.dp))
                }
                PclCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    page!!.data.forEach { (data, mcmod) ->
                        val rawTitle = data.platformTitle()
                        val title = mcmod.getMcmodTitle(rawTitle, context)
                        PclResourceItem(
                            title = title,
                            subtitle = rawTitle.takeIf { it.isNotBlank() && it != title },
                            description = data.platformDescription()
                                .replace("\r", "")
                                .replace("\n", ""),
                            tags = data.platformCategories(classes)
                                ?.sortedBy { it.index() }
                                ?.map { context.getString(it.getDisplayName()) }
                                .orEmpty(),
                            iconUrl = data.platformIconUrl(),
                            iconFallback = pclResourceIconFor(classes),
                            versionText = data.pclVersionText(),
                            downloadText = formatCount(data.platformDownloadCount()),
                            updateText = data.platformLastUpdate()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { getTimeAgo(context, it) }
                                ?.takeIf { it.isNotBlank() },
                            sourceText = data.platform().displayName,
                            // PCL：点条目进资源详情页（PageDownloadCompDetail），在那里挑文件下载
                            onClick = {
                                nav.push(pclResourceDetailPage(data = data, mcmod = mcmod, classes = classes))
                            },
                        )
                    }
                }

                // ---- 分页（PageResource.CardPages）
                Spacer(Modifier.height(15.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclButton(
                        text = "上一页",
                        enabled = pageIndex > 0,
                        onClick = { if (pageIndex - PCL_ASSET_PAGE_SIZE >= 0) search(pageIndex - PCL_ASSET_PAGE_SIZE) },
                    )
                    Spacer(Modifier.width(14.dp))
                    PclText(text = "第 ${page!!.pageNumber} / ${page!!.totalPage} 页")
                    Spacer(Modifier.width(14.dp))
                    PclButton(
                        text = "下一页",
                        enabled = !page!!.isLastPage,
                        onClick = { search(pageIndex + PCL_ASSET_PAGE_SIZE) },
                    )
                }
            }
        }
        Spacer(Modifier.height(15.dp))
    }

    // ---- 自定义版本号
    if (versionInput) {
        PclInputDialog(
            title = "自定义版本",
            label = "游戏版本号（例如 1.21.1）",
            initial = gameVersion.orEmpty(),
            onConfirm = {
                gameVersion = it.trim().ifEmpty { null }
                versionInput = false
            },
            onDismiss = { versionInput = false },
        )
    }

    // ---- 选择具体文件后执行真实下载
    pendingPick?.let { pick ->
        PclSelectDialog(
            title = "选择要下载的文件",
            options = pick.candidates.map { it.platformId() to it.pickLabel() },
            current = pick.candidates.firstOrNull()?.platformId(),
            onSelect = { id ->
                val chosen = pick.candidates.firstOrNull { it.platformId() == id }
                    ?: pick.candidates.firstOrNull()
                if (chosen != null) {
                    if (classes == PlatformClasses.MOD_PACK) {
                        startModpackImport(
                            // 同上：导入要活过页面的生命周期，否则下载完的那一刻就被取消；
                            // 任务还会登记进「下载管理」，点「后台运行」后能在那里继续看进度
                            runtime = runtime,
                            version = chosen,
                            onImporter = { imp -> pendingPick = null; modpackImporterHolder = imp },
                        )
                    } else {
                        downloadSingleForVersions(
                            version = chosen,
                            versions = pick.targets,
                            folder = classes.versionFolder.folderName,
                            // 存档：解压 zip 里的 level.dat 所在目录到 saves/，并删掉源压缩包
                            onFileCopied = { file, folder ->
                                if (classes == PlatformClasses.SAVES) {
                                    pclInstallDownloadedSave(zip = file, savesDir = folder)
                                }
                            },
                            onFileCancelled = { file, folder ->
                                if (classes == PlatformClasses.SAVES) {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        FileUtils.deleteQuietly(File(folder, file.nameWithoutExtension))
                                    }
                                }
                            },
                            submitError = { tm -> runtime.submitError(tm) },
                        )
                        val deps = chosen.platformDependencies()
                            .filter { it.type == PlatformDependencyType.REQUIRED }
                            .mapNotNull { dep ->
                                dep.projectId?.let { pid ->
                                    DependencyRequest(
                                        platform = dep.platform,
                                        projectId = pid,
                                        versionId = dep.versionId,
                                        classes = classes,
                                        projectTitle = chosen.platformDisplayName(),
                                    )
                                }
                            }
                        if (deps.isNotEmpty()) {
                            downloadDependenciesForVersions(
                                requests = deps,
                                versions = pick.targets,
                                submitError = { tm -> runtime.submitError(tm) },
                            )
                        }
                        PclHintHost.push(
                            "已提交下载任务：${chosen.platformDisplayName()}",
                            PclHintTheme.Green,
                        )
                    }
                }
                pendingPick = null
            },
            onDismiss = { pendingPick = null },
        )
    }

    modpackImporterHolder?.let { imp ->
        PclTitledTaskDialog(
            title = "正在导入整合包",
            tasks = imp.taskFlow,
            // 这个弹窗的「取消」是红按钮，必须真的把导入任务流停掉。
            // 原来只把 holder 置空 —— 弹窗消失了，后台的安装却在继续跑。
            //
            // 现在走**统一的取消入口**：按 importer 反查出它是哪一条任务，再 `cancelModpackImport(id)`。
            // 那会一并做三件事 —— 停导入器、把这条从任务表里摘掉、让正排在安装锁上的下一条轮到。
            // 只调 `imp.cancel()` 的话任务表不会变，排队者会一直等下去。
            onCancel = {
                runtime.modpackImportIdOf(imp)?.let { runtime.cancelModpackImport(it) }
                    ?: runCatching { imp.cancel() }
                modpackImporterHolder = null
            },
            onDismiss = { modpackImporterHolder = null },
        )
    }
}

/** 筛选行：`标签（固定宽）...控件`。 */
@Composable
private fun PclFilterRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PclText(text = label, modifier = Modifier.width(56.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** 整合包导入器的持有者（全局，供资源页与弹窗共享）。 */
internal var modpackImporterHolder by mutableStateOf<ModpackImporter?>(null)

/** 待选择下载文件的项目。 */
private class PendingAssetPick(
    val candidates: List<PlatformVersion>,
    val targets: List<com.zalith.pcl.game.version.installed.Version>,
)

private fun PlatformVersion.pickLabel(): String = buildString {
    append(platformDisplayName())
    val game = platformGameVersion()
    if (game.isNotEmpty()) {
        append("  [").append(game.joinToString("/")).append("]")
    }
}

/** 拉取项目的可下载文件列表，并交给 [onPick] 让用户确认。 */
private fun installAsset(
    scope: kotlinx.coroutines.CoroutineScope,
    classes: PlatformClasses,
    platform: Platform,
    data: PlatformSearchData,
    targetVersion: com.zalith.pcl.game.version.installed.Version?,
    onPick: (PendingAssetPick) -> Unit,
    onError: (String) -> Unit,
) {
    scope.launch {
        val projectId = data.platformId()
        val raw = runCatching { getVersions(projectID = projectId, platform = platform) }
            .onFailure { onError(it.message ?: it.javaClass.simpleName) }
            .getOrNull()
            .orEmpty()
        if (raw.isEmpty()) {
            onError("该项目没有可下载的文件。")
            return@launch
        }
        val inited = runCatching { raw.initAll(projectId) }.getOrDefault(raw)

        // 优先挑选与当前已装版本匹配的文件，其次取全部
        val targets = listOfNotNull(targetVersion)
        val mc = targetVersion?.getVersionInfo()?.minecraftVersion
        val matched = if (mc.isNullOrBlank()) emptyList()
        else inited.filter { it.platformGameVersion().any { g -> g.equals(mc, true) } }
        val candidates = matched.ifEmpty { inited }

        onPick(PendingAssetPick(candidates = candidates, targets = targets))
    }
}

/**
 * 下载整合包文件后交给 `ModpackImporter` 安装。
 *
 * ⚠️ 下载目录**绝不能**放在 `PathManager.DIR_CACHE_MODPACK_DOWNLOADER`（`cache/temp_modpack`）里面。
 * `ModpackImporter` 任务流的**第一步**就是 `clearTempModPackDir()` —— 为了清掉上一次安装的
 * 残留，它把整个 `temp_modpack` 目录递归删掉。而我们是在它的任务流启动**之前**就把包下进去的，
 * 于是刚下好的文件立刻被自己删掉，紧接着 `ImportModpack.ImportFile` 拿 `file://` 去读就抛
 * `FileNotFoundException`。这就是「整合包无法正常安装」的根因。
 *
 * 原生安装器 `ModPackInstaller` 没这个问题，因为它的顺序是**先清理、后下载**（见
 * `ModPackInstaller.kt:133` 的 `getTaskPhase`）。这里下到一个独立目录，两边互不干扰。
 *
 * 【登记进「下载管理」】下载和安装分属两个引擎，各自不会知道对方的存在。全程用
 * [PclRuntime.trackModpackImport] 把两段登记成**同一条**任务，于是「下载管理」里
 * 从下载到安装是一张连续的卡片，和「下载版本」那张卡完全同构；中途关掉进度弹窗、
 * 切到别的版块，任务都还在那儿，随时能看进度、能取消。
 */
internal fun startModpackImport(
    runtime: com.zalith.pcl.ui.pcl2.data.PclRuntime,
    version: PlatformVersion,
    /** 导入器就绪 / 结束都会回调：就绪时给对象，导入走到终点时回调 null 好让进度弹窗收起来。 */
    onImporter: (ModpackImporter?) -> Unit,
) {
    val context: android.content.Context = runtime.activity
    val title = "安装整合包：${version.platformDisplayName()}"
    // 专用目录，与 `temp_modpack` 平级 —— 导入器清缓存时不会波及这里。
    val outDir = File(PathManager.DIR_CACHE, "pcl2_modpack_import").apply { mkdirs() }
    val output = File(outDir, version.platformFileName())
    // 上一次没清理干净的残留（比如进程被杀），先删掉，免得下载引擎发现同名文件而跳过。
    runCatching { if (output.isFile) output.delete() }
    // 顺手扫掉这个目录里过期的旧包：导入中途被取消的那条路走不到下面的删除。
    // 只删一小时前的，避免误伤另一个正在进行中的导入。
    val staleCutoff = System.currentTimeMillis() - 60L * 60L * 1000L
    runCatching {
        outDir.listFiles()?.forEach { f ->
            if (f.isFile && f.absolutePath != output.absolutePath && f.lastModified() < staleCutoff) {
                f.delete()
            }
        }
    }
    val total = version.platformFileSize().coerceAtLeast(1L)

    val downloadTask = Task.runTask(
        id = "pcl2_modpack_download/${version.platformId()}",
        task = { task ->
            task.updateMessage(androidText("正在下载整合包：${version.platformFileName()}"))
            var done = 0L
            downloadFile(
                url = version.platformDownloadUrl(),
                outputFile = output,
                sha1 = version.platformSha1(),
                sizeCallback = { size ->
                    done += size
                    task.updateProgress((done.toDouble() / total.toDouble()).toFloat())
                },
            )
            task.updateProgress(1f)
        },
    )

    // 先登记再提交：这样「下载管理」在任务开始的第一帧就能看到它，
    // 而不是等下载引擎吐出第一个进度之后才凭空冒出一张卡。
    // 返回的 id 就是这条任务的身份证 —— 后续更新 / 清除 / 取消全都认它，
    // 这样同时跑好几条整合包时谁也动不了谁。
    val importId = runtime.trackModpackImport(title, downloadTask = downloadTask)

    TaskSystem.submitTask(
        downloadTask,
        onEnded = {
            // `onEnded` 在完成 / 失败 / 被取消时**都会**触发，所以不能无脑往下走：
            // 用一个不存在的文件去建导入器，只会在导入阶段抛一个莫名其妙的异常。
            // `stage` 只有在任务体正常 return 后才会变成 COMPLETED，拿它来判定最准。
            if (downloadTask.stage.value != TaskStage.COMPLETED) {
                // 用户从「下载管理」点过取消 → `cancelModpackImport(id)` 已经把它从表里摘掉
                // 并给过提示了，这里再弹一个红色错误框只会让人以为出了故障。
                //
                // 判据必须按 **id** 查。不能再看「全局还有没有整合包任务」—— 现在允许同时跑好几条，
                // 别的整合包还在跑并不代表这一条不是被用户取消的（反过来也一样）。
                val userCancelled = !runtime.isModpackImportTracked(importId)
                runtime.clearModpackImport(importId)
                if (!userCancelled) {
                    runtime.submitErrorText(
                        "整合包下载失败",
                        if (output.isFile && output.length() > 0L) "下载中断，请重试。"
                        else "请检查网络后重试。",
                    )
                }
                return@submitTask
            }
            // 包已经落地，接下来直接交给导入器，不需要用户再点任何东西。
            // 这条提示同时也是「下载完会自动装」的可见凭据 —— 用户切到别的版块时也能看到。
            PclHintHost.push("整合包下载完成，开始自动安装。", PclHintTheme.Blue)
            runCatching {
                val importer = ModpackImporter(
                    context = context,
                    uri = Uri.fromFile(output),
                    scope = runtime.backgroundScope,
                    // 版本名：下载**完成之后**问一次，默认填整合包自己的名字
                    // （清单里的 name；清单没给才退回平台文件显示名 —— 后者对 Modrinth 往往
                    // 就是「1.20.1-1.2.3」这种版本号，装完在版本列表里根本认不出是哪个整合包）。
                    // 这条链可能已经不在下载页上了，所以询问框挂在窗体最外层（`PclNamePromptHost`）。
                    waitForVersionName = { packName ->
                        pclAskVersionName(
                            rawDefault = packName.ifBlank { version.platformDisplayName() },
                            title = "设置版本名称",
                        )
                    },
                    waitForConfirmMobileData = { true },
                )
                // 下载任务此时已经从全局任务列表里移除，把导入器接上（**仍用同一个 id**），
                // 卡片标题不变、下面直接续上安装的各个阶段。
                runtime.trackModpackImport(title, id = importId, importer = importer)
                onImporter(importer)
                // 【排队取安装锁】`ModpackImporter` 的临时环境是写死的共享目录 `cache/temp_modpack`，
                // 而且它开工的第一件事就是把这个目录**整个递归删掉**（清上次残留）——
                // 两个整合包同时进安装阶段时，后者的清理会把前者正在用的文件一并删光，
                // 前者的安装随即失败。引擎层不做隔离，只能在调度层串行：轮到谁谁才开跑。
                // 下载阶段照旧完全并发，互不影响。
                runtime.backgroundScope.launch {
                    runtime.modpackInstallLock.withLock {
                        // 前面那条可能是被**取消**的：`cancelModpackImport()` 会先把它从任务表里摘掉、
                        // 再 `importer.cancel()`，而后者只是 `job.cancel()` —— 协程要等下一个调度点
                        // 才真正停下，此刻它可能还在写 `temp_modpack`。留一小段缓冲等它落停，
                        // 免得本包开工时的 `clearTempModPackDir()` 撞上它的残留写入。
                        delay(400)
                        // 排队期间用户可能已经从「下载管理」把这条取消了。拿到锁后要再确认一次，
                        // 否则「取消了还在装」—— 比不排队更糟。
                        if (!runtime.isModpackImportTracked(importId)) return@withLock
                        importer.startImport(
                            onFinished = { name ->
                                // 先关弹窗再提示：弹窗标题写的是「正在导入」，跑完了不该还挂在那儿
                                onImporter(null)
                                runtime.clearModpackImport(importId)
                                PclHintHost.push("整合包「$name」安装完成。", PclHintTheme.Green)
                                runtime.refreshVersions()
                                runCatching { output.delete() }
                            },
                            // 走到这里只有一种情况：包要求联网、用户选了不用移动数据。
                            onCancelled = {
                                onImporter(null)
                                runtime.clearModpackImport(importId)
                                if (pclDiscardIncompleteVersion(importer.resolvedVersionNameOrNull())) {
                                    runtime.refreshVersions()
                                }
                                runCatching { output.delete() }
                            },
                            onError = { th ->
                                onImporter(null)
                                runtime.clearModpackImport(importId)
                                // 用户点取消时 `TaskFlowExecutor.cancel()` 会把协程取消掉，
                                // 最后落到这里的往往就是一个 CancellationException —— 那是主动取消，
                                // 不是故障，别拿一个红框吓人。
                                if (th !is CancellationException) {
                                    // 半成品版本目录要扫掉，否则它会一直占着这个版本名，
                                    // 下次导入同一个包必然再撞一次。
                                    if (pclDiscardIncompleteVersion(importer.resolvedVersionNameOrNull())) {
                                        runtime.refreshVersions()
                                    }
                                    runtime.submitErrorText("整合包导入失败", modpackImportErrorText(th))
                                }
                                runCatching { output.delete() }
                            },
                        )
                        // 守住锁直到这条从任务表里消失（装完 / 失败 / 被取消都会清）——
                        // **不能等导入器的回调**：`TaskFlowExecutor.cancel()` 是直接 `job.cancel()`，
                        // 那些回调不保证触发，拿它们当结束信号会把安装锁永久占住，
                        // 后面排队的整合包就再也开不了工。
                        runtime.modpackTaskIds.first { importId !in it }
                    }
                }
            }.onFailure {
                runtime.clearModpackImport(importId)
                runtime.submitErrorText("整合包导入失败", modpackImportErrorText(it))
            }
        },
    )
}

/**
 * 把整合包导入过程中抛出的异常翻成用户看得懂的文案。
 *
 * 这几个异常的 `message` 都是**英文 + 机器视角**的，原样丢进错误框等于什么都没说：
 *   · [DownloadFailedException] —— 正文是一串失败文件的**绝对路径**
 *     （`Failed downloads:\n/data/user/0/com.movtery.../binarypatcher-2.1.2-fatjar.jar`），
 *     既不说是哪一步，也不说能怎么办；
 *   · [PackNotSupportedException] —— `The archive is corrupted or failed to extract.`
 *   · [GameAlreadyInstalledException] —— 以前干脆连 message 都没有，界面上只剩一个类名。
 *
 * 这里换成「发生了什么 + 下一步做什么」，网络类错误仍然把文件清单附在末尾供排查
 * （用户要贴给开发者看的就是这一段）。
 */
internal fun modpackImportErrorText(th: Throwable): String = when (th) {
    is DownloadFailedException ->
        "安装这个整合包需要的文件没能全部下载成功，通常是网络中断，或者当前下载源上缺这些文件。\n" +
            "可以换一个下载源再试一次，或者稍后重试。\n\n${th.message.orEmpty()}"

    is PackNotSupportedException -> when (th.reason) {
        UnsupportedPackReason.CorruptedArchive ->
            "这个整合包文件已损坏，或不是能解压的压缩包。请重新下载一份完整的整合包再导入。"

        UnsupportedPackReason.UnsupportedFormat ->
            "无法识别这个整合包的格式。目前认得 CurseForge、Modrinth、MCBBS " +
                "以及 MultiMC / HMCL 导出的整合包。"
    }

    // 正常流程下走不到这里了（导入前会用 `pclUniqueVersionName` 让开已占用的名字），
    // 但如果真撞上，至少别只给一个类名。
    is GameAlreadyInstalledException ->
        "已经有一个叫「${th.versionName}」的版本了，导入没有继续。\n" +
            "在「版本选择」里把它删掉（或改个名字），再重新导入一次。"

    // 未知异常：给一句人话 + 原始信息，末尾附上异常类型方便排查。
    // 以前这里直接 `th.message ?: th.javaClass.simpleName`，用户截图上就是一行
    // 「GameAlreadyInstalledException」—— 既不知道发生了什么，也不知道能做什么。
    else -> buildString {
        append("导入过程中出现了一个意料之外的错误。\n")
        th.message?.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
        append("\n（${th.javaClass.simpleName}）")
    }
}

/* ============================================================================
 *  联机（Zalith 的陶瓦联机 / Terracotta）
 * ==========================================================================*/

private const val TAG_LINK = "PclLink"

/**
 * 加入大厅的等待上限。
 * 陶瓦要扫房主、打洞建链，正常十几秒到一分钟；超过两分钟基本可以认为连不上了。
 */
private const val JOIN_TIMEOUT_MILLIS = 120_000L

/** 联机页的初始化进度（见 `PclLinkRight`）。 */
private enum class PclLinkInit {
    /** `Terracotta.initialize` 还没返回 */
    Initializing,

    /** 初始化完成，可以加入大厅 */
    Ready,

    /** 初始化抛异常，需要用户点「重试初始化」 */
    Failed,
}

/**
 * `PageLinkMain` —— 联机大厅。
 *
 * PCL 的联机大厅是一个在线房间列表；Zalith 的联机能力是内置的陶瓦（Terracotta，底层用
 * EasyTier 打洞组网），所以这里把「加入大厅 / 房间成员」接到真实的 `Terracotta` 状态机上。
 *
 * 【只保留加入，不提供创建】这一页不放「创建房间」入口：大厅由房主在别处开好，
 * 这里只负责用大厅编号加入。状态卡里的「退出大厅」仍然保留，方便随时断开。
 *
 * 【这一页原先完全用不了，原因在这里】`Terracotta.initialize` 以前只在**游戏内**的联机菜单
 * （`TerracottaViewModel.openMenu`）里被调用过，于是这一页的 `Terracotta.initialized`
 * 永远是 false：黄条只会让你「先进入一次游戏」，而两个按钮一点就抛
 * `initialize Terracotta first!`。现在这一页自己完成初始化、VPN 授权和事件处理，不必先进游戏。
 *
 * 【进门先查总开关】陶瓦的总开关是 `AllSettings.enableTerracotta`（设置 → 联机）。
 * 没启用就直接铺开联机页等于偷偷初始化原生库、申请 VPN 权限，所以未启用时只显示占位卡，
 * 并先弹一次提示询问是否启用；启用后走「设置 → 联机」也是同一个开关，一并变成已勾选。
 */
@Composable
fun PclLinkRight(nav: PclNavController) {
    val p = pclPalette
    // 陶瓦总开关。`enableTerracotta` 是 Compose state（`AllSettings` 在 Application 里 init 过），
    // 所以在「设置 → 联机」里勾上 / 取消，这一页会立刻跟着重组，不用自己再存一份。
    val enabled = AllSettings.enableTerracotta.state
    var asking by remember { mutableStateOf(false) }

    // 每次进入联机分栏都查一遍：没启用就把提示弹出来。
    // 只在进入时弹这一次 —— 取消之后不再自己蹦，页面上留着「启用陶瓦联机」按钮可以再点。
    LaunchedEffect(Unit) {
        if (!AllSettings.enableTerracotta.state) asking = true
    }

    if (enabled) {
        // 真正初始化陶瓦（原生库 / VPN 授权）的是 [PclLinkContent]，
        // 所以**未启用时绝不能组合它**，否则等于偷偷把服务拉起来。
        PclLinkContent()
    } else {
        PclContent {
            PclHint(
                text = "陶瓦联机还没启用。启用之后才连得上大厅；启用时会同时打开「设置 → 联机」里的陶瓦联机开关。",
                theme = PclHintTheme.Yellow,
            )
            Spacer(Modifier.height(15.dp))
            PclCard(modifier = Modifier.fillMaxWidth(), title = "联机状态（陶瓦 Terracotta）") {
                Column {
                    PclText(text = "当前状态：未启用", fontSize = 15)
                    Spacer(Modifier.height(8.dp))
                    PclText(
                        text = "陶瓦会在本机建一条虚拟网卡，和好友打洞组网；首次启用时需要授权 VPN。",
                        color = p.gray2,
                        fontSize = 12,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PclButton(
                            text = "启用陶瓦联机",
                            colorState = PclColorState.Highlight,
                            onClick = { asking = true },
                        )
                        Spacer(Modifier.width(14.dp))
                        // 也可以自己跑去「设置 → 联机」勾那一枚开关，效果完全一样（同一个设置单元）
                        PclTextButton(text = "前往联机设置") {
                            nav.go(PclSection.Setup, 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
        }
    }

    if (asking) {
        PclConfirmDialog(
            title = "启用陶瓦联机",
            content = "联机大厅要用陶瓦（Terracotta）打洞组网，现在还没有启用。\n" +
                "启用后会同时打开「设置 → 联机」里的陶瓦联机开关，首次使用还会申请 VPN 权限。",
            confirmText = "启用",
            onConfirm = {
                // 和「设置 → 联机」用的是同一个设置单元：这里存下去，那边那枚开关立刻就是勾上的
                AllSettings.enableTerracotta.save(true)
                PclHintHost.push("已启用陶瓦联机。", PclHintTheme.Green)
            },
            onDismiss = { asking = false },
        )
    }
}

/**
 * 联机页正文（陶瓦已启用）。
 *
 * 进来就会初始化陶瓦并订阅事件通道，所以只允许在 `enableTerracotta` 为 true 时组合。
 */
@Composable
private fun PclLinkContent() {
    val p = pclPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val state by Terracotta.state.collectAsState()
    // 用 Activity 作用域的 EventViewModel —— 与游戏内联机菜单走的是同一条事件通道。
    // 陶瓦是进程级单例，VPN 授权、状态通知这些事件只能有一条通道。
    val eventViewModel: EventViewModel = viewModel()

    var player by remember {
        mutableStateOf(AccountsManager.currentAccountFlow.value?.username ?: "匿名玩家")
    }
    var busy by remember { mutableStateOf(false) }
    // 「正在连接」：从点了【加入】到状态机给出 GuestOK / Exception 之间为 true。
    // 弹窗在这期间不关，改成显示进度文案，避免用户在还没连上时就以为进房间了。
    var joining by remember { mutableStateOf(false) }
    var joinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }

    // 初始化进度。
    // 这一页每次进来都会重新走一遍初始化（见下面的 `LaunchedEffect`），所以要单独记一份状态：
    // 否则「正在初始化」和「初始化完、只是空闲」在界面上长得一模一样，
    // 用户看到「空闲」会以为已经就绪，点加入却报 `initialize Terracotta first!`。
    var initPhase by remember {
        mutableStateOf(if (Terracotta.initialized) PclLinkInit.Ready else PclLinkInit.Initializing)
    }
    var initError by remember { mutableStateOf<String?>(null) }
    // 点「重试」时 +1，重新触发下面那个 `LaunchedEffect`
    var initRetry by remember { mutableIntStateOf(0) }

    // ---- VPN 授权
    // 游戏里那份在 `TerracottaOperation` 里，这一页得自己接一份：陶瓦是个 VpnService，
    // 没有系统授权就起不来，状态会一直卡在「等待授权」。
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 拉起前台服务这一段在任何界面回调里都可能失败（后台限制 / 服务被拦），
            // 失败只记日志，不让它变成未捕获异常把页面带崩
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TerracottaVPNService::class.java)
                        .setAction(TerracottaVPNService.ACTION_START),
                )
            }.onFailure {
                Logger.warning(TAG_LINK, "Cannot start the VPN service", it)
            }
        } else {
            runCatching {
                TerracottaAndroidAPI.getPendingVpnServiceRequest().reject()
                Terracotta.setWaiting(true)
            }.onFailure {
                Logger.warning(TAG_LINK, "Cannot reject the VPN request", it)
            }
            PclHintHost.push(context.getString(R.string.terracotta_permission_vpn), PclHintTheme.Red)
        }
    }

    // ---- 初始化 + 事件处理
    // 先起收集器、后初始化：`initialize` 注册给原生层的回调随时可能发 `RequestVPN`，
    // 那一刻要是还没人订阅，事件会被 SharedFlow 直接丢掉（没有 replay、没有缓冲的订阅者）。
    // `initRetry` 是「重试」按钮的计数器 —— 它一变这个 effect 就重跑，等于重新初始化一遍。
    LaunchedEffect(eventViewModel, initRetry) {
        coroutineScope {
            launch {
                eventViewModel.events
                    .filterIsInstance<EventViewModel.Event.Terracotta>()
                    .collect { event ->
                        when (event) {
                            is EventViewModel.Event.Terracotta.RequestVPN -> withContext(Dispatchers.Main) {
                                // 整段包起来：`vpnLauncher.launch()` 在页面已经离开组合时会抛
                                // `IllegalStateException`（launcher 已注销），`startForegroundService()`
                                // 在后台会被 Android 12+ 拦（`ForegroundServiceStartNotAllowedException`，
                                // 也是 IllegalStateException 的子类）。这两类异常落在事件收集协程里没人接，
                                // 直接就是「应用程序意外崩溃了」。收住之后最坏只是这次 VPN 没起来。
                                runCatching {
                                    val intent = VpnService.prepare(context)
                                    if (intent != null) {
                                        vpnLauncher.launch(intent)
                                    } else {
                                        ContextCompat.startForegroundService(
                                            context,
                                            Intent(context, TerracottaVPNService::class.java)
                                                .setAction(TerracottaVPNService.ACTION_START),
                                        )
                                    }
                                }.onFailure {
                                    Logger.warning(TAG_LINK, "Cannot request the VPN service", it)
                                }
                            }

                            is EventViewModel.Event.Terracotta.VPNUpdateState -> withContext(Dispatchers.Main) {
                                // 与 `TerracottaViewModel` 里的处理保持一致：服务已在前台运行时
                                // 只投递状态文本，避免 startForegroundService 拉起来不及进前台的服务
                                if (TerracottaVPNService.isRunning()) {
                                    context.startService(
                                        Intent(context, TerracottaVPNService::class.java)
                                            .setAction(TerracottaVPNService.ACTION_UPDATE_STATE)
                                            .putExtra(TerracottaVPNService.EXTRA_STATE_TEXT, event.stringRes),
                                    )
                                }
                            }

                            is EventViewModel.Event.Terracotta.StopVPN -> withContext(Dispatchers.Main) {
                                if (TerracottaVPNService.isRunning()) {
                                    context.stopService(Intent(context, TerracottaVPNService::class.java))
                                }
                            }
                        }
                    }
            }
            launch {
                val wasInitialized = Terracotta.initialized
                if (!wasInitialized) {
                    initPhase = PclLinkInit.Initializing
                    initError = null
                }
                var failure: Throwable? = null
                try {
                    // 即使已经初始化过也要再调一次：`initialize` 现在每次都会把事件通道
                    // 指回当前界面（从游戏里退回来时，通道还指着游戏那个已经销毁的界面）。
                    // 放到 IO 线程：原生层要加载 so、建目录，压在主线程上会卡住首帧。
                    withContext(Dispatchers.IO) { Terracotta.initialize(eventViewModel) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    failure = e
                }
                if (failure == null) {
                    // 只在第一次真正初始化时复位状态，免得从游戏里回到这一页把大厅踢掉
                    if (!wasInitialized) runCatching { Terracotta.setWaiting(true) }
                    initPhase = PclLinkInit.Ready
                    // 顺手把节点列表拉下来预缓存（`fetchNodes` 自己有缓存）：
                    // 之后点「加入大厅」就不用再等这一趟网络了。
                    launch(Dispatchers.IO) { runCatching { fetchNodes() } }
                } else {
                    Logger.warning(TAG_LINK, "Terracotta initialize failed", failure)
                    initPhase = PclLinkInit.Failed
                    initError = failure.message ?: failure.javaClass.simpleName
                }
            }
        }
    }

    // ---- 状态派生
    val ready = state as? TerracottaState.Ready
    // 注意绕开 `Waiting.localStringRes()`：源码里它就是 `error("No op.")` 故意不实现的，
    // 直接调会在「模块已初始化、当前空闲」这个最常见的状态下把整页抛崩。
    // 外层再套一层初始化进度：还没初始化完时 `state` 一定是 null，
    // 单看 `state` 会把「正在加载原生库」显示成「空闲」，让人以为已经就绪。
    val statusText = when (initPhase) {
        PclLinkInit.Initializing -> "正在初始化…"
        PclLinkInit.Failed -> "初始化失败"
        PclLinkInit.Ready -> when (ready) {
            null, is TerracottaState.Waiting -> "空闲"
            else -> context.getString(ready.localStringRes())
        }
    }
    val profiles: List<TerracottaProfile> = when (ready) {
        is TerracottaState.HostOK -> ready.profiles.orEmpty()
        is TerracottaState.GuestOK -> ready.profiles.orEmpty()
        else -> emptyList()
    }
    val lobbyCode = (ready as? TerracottaState.HostOK)?.code?.takeIf { it.isNotBlank() }
    val guestUrl = (ready as? TerracottaState.GuestOK)?.url?.takeIf { it.isNotBlank() }

    fun joinRoom(raw: String, onJoined: () -> Unit) {
        val code = raw.trim()
        if (code.isEmpty()) {
            PclHintHost.push("请先填写大厅编号。", PclHintTheme.Red)
            return
        }
        if (busy) return
        busy = true
        joining = true
        scope.launch {
            try {
                // 记下复位前的状态序号：下面判定「连上了」时要求 `GuestOK.index` 比它新，
                // 免得读到上一次大厅遗留下来的 GuestOK。
                val baseIndex = Terracotta.state.value?.index ?: -1
                // ① 先复位：`setGuesting` 要求当前状态是 Waiting，
                //    否则「已经在大厅里」再点一次会抛 "reset state to waiting first!"
                runCatching { Terracotta.setWaiting(true) }
                // 等状态机真的回到 Waiting 再往下走。不等的话，下面等 `GuestOK` 时
                // 会立刻读到上一次大厅留下的旧状态，误判成「已经加进去了」。
                withTimeoutOrNull(2_000) {
                    Terracotta.state.first { it is TerracottaState.Waiting }
                }

                // ② 提交加入请求（节点列表在进页面时已经预取过，这里基本直接命中缓存）
                val nodes = withContext(Dispatchers.Default) {
                    runCatching { fetchNodes().map { it.toString() } }.getOrDefault(emptyList())
                }
                val accepted = Terracotta.setGuesting(code, player.ifBlank { "匿名玩家" }, nodes)
                if (!accepted) {
                    PclHintHost.push(
                        context.getString(R.string.terracotta_status_waiting_guest_prompt_invalid),
                        PclHintTheme.Red,
                    )
                    return@launch
                }

                // ③ 等真正连上。
                // `setGuesting` 只是把请求交给原生层，之后还要 ping 房主、和 EasyTier 打洞建链，
                // 十几秒到一分钟都正常。**只有状态机走到 GuestOK 才算加入成功** ——
                // 以前这里拿到 `accepted == true` 就弹「已加入大厅」，房客那边还没连上，
                // 进游戏自然看不到房间。
                val result = withTimeoutOrNull(JOIN_TIMEOUT_MILLIS) {
                    Terracotta.state.first { s ->
                        s is TerracottaState.Exception ||
                            (s is TerracottaState.GuestOK && s.index > baseIndex)
                    }
                }
                when (result) {
                    is TerracottaState.GuestOK -> {
                        // 交给弹窗走退出动画再关（见上面的 requestClose）
                        joinCode = ""
                        if (joinDialog) onJoined()
                        PclHintHost.push(
                            "已加入大厅，现在可以进入游戏了 —— 打开「多人游戏」就能看到这个房间。",
                            PclHintTheme.Green,
                        )
                    }

                    is TerracottaState.Exception -> {
                        val desc = runCatching {
                            context.getString(result.getEnumType().textRes)
                        }.getOrNull().orEmpty()
                        PclHintHost.push(
                            "加入大厅失败：${desc.ifBlank { "连接被拒绝" }}",
                            PclHintTheme.Red,
                        )
                    }

                    else -> PclHintHost.push(
                        "连接超时：房主可能还没开好大厅，或者网络不通，稍后可以重试。",
                        PclHintTheme.Red,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PclHintHost.push("加入大厅失败：${e.message ?: e.javaClass.simpleName}", PclHintTheme.Red)
            } finally {
                joining = false
                busy = false
            }
        }
    }

    PclContent {
        // PCL 原版的这一条只提「Terracotta 内网穿透」，而页尾又说「由内置的 EasyTier 节点提供」，
        // 两种说法看着像两套东西。其实陶瓦就是基于 EasyTier 的，这里合并成一句。
        PclHint(
            text = "联机由内置的陶瓦（Terracotta，基于 EasyTier）打洞组网，首次使用会申请 VPN 权限。",
            theme = PclHintTheme.Blue,
        )
        Spacer(Modifier.height(15.dp))

        // ---- 联机状态 + 房间成员
        PclCard(modifier = Modifier.fillMaxWidth(), title = "联机状态（陶瓦 Terracotta）") {
            Column {
                PclText(text = "当前状态：$statusText", fontSize = 15)

                // 初始化失败时把原因摊开给用户看，并给一个重试入口 ——
                // 陶瓦的原生库加载失败（缺 so、没存储权限）以前只会闪一下红条，
                // 之后整页永远停在「空闲」，点加入就报 `initialize Terracotta first!`。
                if (initPhase == PclLinkInit.Failed) {
                    Spacer(Modifier.height(8.dp))
                    PclText(
                        text = "陶瓦初始化失败：${initError ?: "未知错误"}",
                        color = p.gray2,
                        fontSize = 12,
                    )
                    Spacer(Modifier.height(8.dp))
                    PclButton(
                        text = "重试初始化",
                        colorState = PclColorState.Highlight,
                        onClick = { initRetry++ },
                    )
                }

                // 房主：大厅编号就是好友加入要用的那串码（PCL 里叫「房间邀请码」）
                lobbyCode?.let { code ->
                    Spacer(Modifier.height(10.dp))
                    PclText(text = "大厅编号：$code", fontSize = 16)
                    Spacer(Modifier.height(8.dp))
                    PclButton(text = "复制编号", colorState = PclColorState.Highlight, onClick = {
                        clipboard.setText(AnnotatedString(code))
                        PclHintHost.push("大厅编号已复制，发给好友即可加入。", PclHintTheme.Green)
                    })
                }

                // 房客：大厅映射到本机的地址
                guestUrl?.let { url ->
                    Spacer(Modifier.height(10.dp))
                    PclText(text = "服务器地址：$url", fontSize = 16)
                    Spacer(Modifier.height(8.dp))
                    PclButton(text = "复制地址", colorState = PclColorState.Highlight, onClick = {
                        clipboard.setText(AnnotatedString(url))
                        PclHintHost.push("服务器地址已复制。", PclHintTheme.Blue)
                    })
                }

                // 房间成员：名字就是建房/加入时填的「玩家名」，本机那一行标成「你」
                if (profiles.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    PclText(text = context.getString(R.string.terracotta_player_list), fontSize = 15)
                    Spacer(Modifier.height(6.dp))
                    profiles.forEach { profile -> PclRoomMemberRow(profile) }
                }

                if (ready == null || ready is TerracottaState.Waiting) {
                    Spacer(Modifier.height(8.dp))
                    PclText(
                        text = "当前没有加入大厅。填好玩家名后，用好友给的大厅编号点「加入房间」。",
                        color = p.gray2,
                        fontSize = 12,
                    )
                } else {
                    Spacer(Modifier.height(12.dp))
                    PclButton(text = "退出大厅", colorState = PclColorState.Red, onClick = {
                        runCatching { Terracotta.setWaiting(true) }
                    })
                }
            }
        }

        Spacer(Modifier.height(15.dp))

        // ---- 玩家名 + 加入
        PclCard(modifier = Modifier.fillMaxWidth(), title = PclStrings.LangLinkMainRoomList) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PclText(text = "玩家名", modifier = Modifier.width(70.dp))
                    PclTextBox(
                        value = player,
                        modifier = Modifier.weight(1f),
                        onValueChange = { player = it },
                    )
                }
                Spacer(Modifier.height(6.dp))
                PclText(
                    text = "这是你在大厅里显示的名字，默认取当前账户名。",
                    color = p.gray2,
                    fontSize = 12,
                )
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    PclButton(
                        text = PclStrings.LangLinkMainJoin,
                        // 初始化没走完之前不给点：`setGuesting` 会检查 `initialized`，
                        // 这时候点下去只能换来一句 `initialize Terracotta first!`
                        enabled = !busy && initPhase == PclLinkInit.Ready,
                        colorState = PclColorState.Highlight,
                        onClick = {
                            // 编号输入搬进弹窗了（见下面「加入大厅」），这里先清空上次的输入
                            joinCode = ""
                            joinDialog = true
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                PclText(
                    text = "联机节点由内置的 EasyTier 提供，可在「设置 → 联机」里配置自定义节点。",
                    color = p.gray2,
                    fontSize = 12,
                )
            }
        }
        Spacer(Modifier.height(15.dp))
    }

    // ---- 「加入大厅」弹窗
    //
    // 编号输入从页面上的输入框挪进了弹窗：一来页面上只留「玩家名 + 加入房间」干净很多，
    // 二来「清除 / 粘贴 / 加入」这三个操作只有弹窗放得下。
    // 注意 PCL 的按钮习惯是「肯定按钮在最左」（`MyMsgInput` 的 Btn1），这里按需求反过来
    // 排在右边，与页面上的「加入房间」保持同一个方向。
    if (joinDialog) {
        PclMsgInputDialog(
            title = "加入大厅",
            // 连接期间把正文换成进度说明：点完【加入】要等十几秒才真的连上，
            // 期间弹窗一直开着，用户才知道自己点到了、还在连。
            lines = if (joining) listOf(
                "正在连接大厅，请稍候…",
                "需要扫描房主并建立隧道，通常十几秒到一分钟，连上后会自动提示。",
            ) else listOf(
                "1、把好友发来的大厅编号填在下面，点【加入】。",
                "2、连上之后再启动游戏，在【多人游戏】→ 局域网里就能看到这个大厅。",
            ),
            value = joinCode,
            hint = "大厅编号",
            onValueChange = { joinCode = it },
            buttons = { requestClose ->
                PclMsgButton(text = "清除", enabled = !joining) { joinCode = "" }
                PclMsgButton(text = "粘贴", enabled = !joining) {
                    val text = clipboard.getText()?.text.orEmpty().trim()
                    if (text.isEmpty()) {
                        PclHintHost.push("剪贴板里没有内容。", PclHintTheme.Blue)
                    } else {
                        joinCode = text
                    }
                }
                // 加入成功后由 joinRoom 回调 requestClose —— 让弹窗走「退出动画」再关，
                // 而不是直接把 joinDialog 置 false（那样是瞬间消失，没有收场动画）。
                PclMsgButton(
                    text = if (joining) "连接中…" else "加入",
                    enabled = !joining,
                    colorState = PclColorState.Highlight,
                ) {
                    joinRoom(joinCode) { requestClose() }
                }
            },
            onDismiss = { joinDialog = false },
        )
    }
}

/**
 * 房间成员的一行（`TerracottaProfile`）。
 *
 * 名字取自传给 `Terracotta.setGuesting` 的那份玩家名，所以自己那一行
 * 显示的就是页面上填的「玩家名」。身份用 `ProfileKind` 区分，`LOCAL` 是本机
 * （房主和房客各自都会有一条），本地化之后正是「你」。
 */
@Composable
private fun PclRoomMemberRow(profile: TerracottaProfile) {
    val p = pclPalette
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(
            text = profile.name?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.terracotta_player_anonymous),
            color = p.color1,
            fontSize = 14,
        )
        Spacer(Modifier.width(10.dp))
        PclText(
            text = context.getString(profile.type.textRes),
            color = if (profile.type == ProfileKind.LOCAL) p.color3 else p.gray2,
            fontSize = 12,
        )
    }
}

/* ============================================================================
 *  设置
 * ==========================================================================*/

/** `PageSetupLeft` —— 启动 / 联机 / 个性化 / 其他，每项带「初始化本页设置」。
 *
 *  后四项（控制设置 / 手柄设置 / Java 管理 / 控制管理）是 PCL2 没有、Zalith 原生
 *  才有的设置页，版式照样用 PCL 的左栏条目，只是图标取自原生 `res/drawable`。 */
@Composable
fun PclSetupLeft(nav: PclNavController) {
    PclLeftPanel(
        nav = nav,
        // `UiHiddenSetup*`：被隐藏的子页面直接不出现在左栏（下标仍是页面编号，不受过滤影响）
        entries = listOf(
            PclLeftEntry.Item(
                0, PclLeftLabels.setupGame, PclIcons.setupLaunch, 0.8f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(0) }),
            ),
            PclLeftEntry.Item(
                1, PclLeftLabels.setupLink, PclIcons.setupLink, 0.77f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(1) }),
            ),
            PclLeftEntry.Item(
                2, PclLeftLabels.setupPersonalize, PclIcons.setupUi, 0.9f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(2) }),
            ),
            PclLeftEntry.Item(
                3, PclLeftLabels.setupLaunch, PclIcons.setupSystem, 0.8f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(3) }),
            ),
            PclLeftEntry.Item(
                4, PclLeftLabels.setupControl, PclIcons.setupControl, 0.85f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(4) }),
            ),
            PclLeftEntry.Item(
                5, PclLeftLabels.setupGamepad, PclIcons.setupGamepad, 0.9f,
                listOf(resetButton(PclStrings.LangPageSetupLeftReset) { resetSettingsPage(5) }),
            ),
            PclLeftEntry.Item(
                6, PclLeftLabels.setupJava, PclIcons.setupJava, 0.85f,
            ),
            PclLeftEntry.Item(
                7, PclLeftLabels.setupControlManage, PclIcons.setupControlManage, 0.85f,
            ),
        ).filterIndexed { index, _ -> !PclSkinSettings.shouldHide(PCL_SETUP_KEYS.getOrElse(index) { "" }) },
    )
}

/** 设置左栏八个子页面对应的 `UiHiddenSetup*` 键（顺序与 `PclSetupLeft` 的条目一致）。 */
private val PCL_SETUP_KEYS = listOf(
    PclHiddenKeys.SETUP_GAME,
    PclHiddenKeys.SETUP_LINK,
    PclHiddenKeys.SETUP_UI,
    PclHiddenKeys.SETUP_SYSTEM,
    PclHiddenKeys.SETUP_CONTROL,
    PclHiddenKeys.SETUP_GAMEPAD,
    PclHiddenKeys.SETUP_JAVA,
    PclHiddenKeys.SETUP_CONTROL_MANAGE,
)

/** 把某一页设置恢复为默认值（真实调用 `AllSettings` 的 `reset()`）。 */
private fun resetSettingsPage(index: Int) {
    runCatching {
        when (index) {
            0 -> com.zalith.pcl.setting.AllSettings.run {
                versionIsolation.reset()
                versionCustomInfo.reset()
                skipGameIntegrityCheck.reset()
                gameFullScreen.reset()
                useSurfaceView.reset()
                sustainedPerformance.reset()
                renderer.reset()
                vulkanDriver.reset()
                graphicsApi.reset()
                resolutionRule.reset()
                resolutionRatio.reset()
                ramAllocation.reset()
                autoPickJavaRuntime.reset()
                javaRuntime.reset()
                jvmArgs.reset()
            }

            1 -> com.zalith.pcl.setting.AllSettings.run {
                enableTerracotta.reset()
                enableTerracottaNodes.reset()
                terracottaNodes.reset()
            }

            2 -> com.zalith.pcl.setting.AllSettings.run {
                launcherColorTheme.reset()
                launcherCustomColor.reset()
                launcherDarkMode.reset()
                launcherLanguage.reset()
                launcherFullScreen.reset()
                launcherFestivalEffects.reset()
                launcherAnimateSpeed.reset()
                launcherAnimateExtent.reset()
                launcherBackgroundOpacity.reset()
                backgroundBlur.reset()
                videoBackgroundVolume.reset()
            }

            3 -> com.zalith.pcl.setting.AllSettings.run {
                gameDownloadSource.reset()
                assetPlatformSource.reset()
                searchModPlatform.reset()
                searchModpackPlatform.reset()
                searchResourcePackPlatform.reset()
                searchShadersPlatform.reset()
                showFPS.reset()
                showMemory.reset()
                showMenuBall.reset()
            }

            // Zalith 原生独有的两页
            4 -> resetControlSettingsPage()
            5 -> resetGamepadSettingsPage()

            // Java 管理 / 控制管理 没有「本页设置」可复位（内容都是列表），左栏也不给复位按钮
            else -> {}
        }
    }
    PclHintHost.push("本页设置已恢复默认值。", PclHintTheme.Blue)
}

/** 「控制设置」的初始化（原生 `ControlSettingsScreen` 对应项）。 */
private fun resetControlSettingsPage() {
    runCatching {
        com.zalith.pcl.setting.AllSettings.run {
            physicalMouseMode.reset()
            physicalKeyImeCode.reset()
            hideMouse.reset()
            mouseSize.reset()
            arrowMouseHotspot.reset()
            linkMouseHotspot.reset()
            iBeamMouseHotspot.reset()
            crossHairMouseHotspot.reset()
            resizeNSMouseHotspot.reset()
            resizeEWMouseHotspot.reset()
            resizeAllMouseHotspot.reset()
            notAllowedMouseHotspot.reset()
            cursorSensitivity.reset()
            mouseCaptureSensitivity.reset()
            mouseControlMode.reset()
            mouseLongPressDelay.reset()
            enableMouseClick.reset()
            gestureControl.reset()
            gestureTapMouseAction.reset()
            gestureLongPressMouseAction.reset()
            gestureLongPressDelay.reset()
            gyroscopeControl.reset()
            gyroscopeSensitivity.reset()
            gyroscopeSampleRate.reset()
            gyroscopeSmoothing.reset()
            gyroscopeSmoothingWindow.reset()
            gyroscopeInvertX.reset()
            gyroscopeInvertY.reset()
        }
    }
    PclHintHost.push("本页设置已恢复默认值。", PclHintTheme.Blue)
}

/** 「手柄设置」的初始化（原生 `GamepadSettingsScreen` 对应项）。 */
private fun resetGamepadSettingsPage() {
    runCatching {
        com.zalith.pcl.setting.AllSettings.run {
            gamepadControl.reset()
            gamepadInputMode.reset()
            gamepadDeadZoneScale.reset()
            joystickControlMode.reset()
            gamepadCursorSensitivity.reset()
            gamepadCameraSensitivity.reset()
            gamepadMappingConfig.reset()
        }
    }
    PclHintHost.push("本页设置已恢复默认值。", PclHintTheme.Blue)
}

/** `PageSetupLeft.PageGet` —— 按左侧选中项切换到八张设置页。
 *
 *  前四张是 PCL2 的设置页；后四张（控制设置 / 手柄设置 / Java 管理 / 控制管理）
 *  是 Zalith 原生独有、在移植版里用 PCL 风格重做的页面。 */
@Composable
fun PclSetupRight(nav: PclNavController) {
    when (nav.leftIndex) {
        0 -> PclSetupLaunchPage()
        1 -> PclSetupLinkPage()
        2 -> PclSetupUiPage()
        3 -> PclSetupSystemPage()
        4 -> PclSetupControlPage()
        5 -> PclSetupGamepadPage()
        6 -> PclSetupJavaPage()
        else -> PclSetupControlManagePage()
    }
}

/* ============================================================================
 *  更多
 * ==========================================================================*/

/** `PageOtherLeft` —— 帮助（带刷新）/ 关于与鸣谢 / 百宝箱 / 反馈 / 新功能投票。 */
@Composable
fun PclOtherLeft(nav: PclNavController) {
    PclLeftPanel(
        nav = nav,
        entries = listOf(
            PclLeftEntry.Item(
                0, PclLeftLabels.otherHelp, PclIcons.otherHelp, 0.97f,
                listOf(refreshButton(PclStrings.LangPageOtherLeftRefresh)),
            ),
            PclLeftEntry.Item(1, PclLeftLabels.otherAbout, PclIcons.otherAbout, 0.98f),
            PclLeftEntry.Item(2, PclLeftLabels.otherTools, PclIcons.otherTools, 0.97f),
            PclLeftEntry.Item(3, PclLeftLabels.otherFeedback, PclIcons.otherFeedback, 0.9f),
            PclLeftEntry.Item(4, PclLeftLabels.otherVoteNew, PclIcons.otherVote, 0.92f),
        ).filterIndexed { index, _ -> !PclSkinSettings.shouldHide(PCL_OTHER_KEYS.getOrElse(index) { "" }) },
    )
}

/** 更多左栏五个子页面对应的 `UiHiddenOther*` 键（顺序与 `PclOtherLeft` 的条目一致）。 */
private val PCL_OTHER_KEYS = listOf(
    PclHiddenKeys.OTHER_HELP,
    PclHiddenKeys.OTHER_ABOUT,
    PclHiddenKeys.OTHER_TOOLS,
    PclHiddenKeys.OTHER_FEEDBACK,
    PclHiddenKeys.OTHER_VOTE,
)

/** `PageOtherLeft.PageGet` —— 帮助 / 关于 / 百宝箱 / 反馈 / 投票。 */
@Composable
fun PclOtherRight(nav: PclNavController) {
    when (nav.leftIndex) {
        0 -> PclOtherHelpPage()
        1 -> PclOtherAboutPage()
        2 -> PclOtherTestPage()
        3 -> PclOtherFeedbackPage()
        else -> PclOtherVotePage()
    }
}

/* ============================================================================
 *  若干通用小部件（供上述页面复用）
 * ==========================================================================*/

/**
 * PCL 设置项的一行：`标签（固定宽）............ 控件`。
 *
 * PCL 里标签列是 `Width="Auto" SharedSizeGroup="Name"`（同一 Grid 内取最宽标签），
 * 标签自身 `Margin="0,0,N,0"`；统计 Pages/PageSetup/ 下 N=20 出现 56 次、N=25 出现 45 次，
 * 且同一页内两者混用。Compose 无法简单复刻共享尺寸组，这里统一为
 * `宽 140 + 右边距 20`，与 PclSetupPages.SetupRow 保持一致（此前两处分别是 150/25 与 140/20）。
 */
@Composable
fun PclSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(
            text = label,
            modifier = Modifier.width(140.dp).padding(end = 20.dp),
        )
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** 纵向留白（占位，避免整页内容贴边）。 */
@Composable
fun PclGap(height: Int) {
    Spacer(Modifier.height(height.dp))
}

/** 供页面直接使用的左栏滚动容器（若某页需要自定义条目）。 */
@Composable
fun PclLeftScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
        content = content,
    )
}

/** 纯文本按钮的便捷包装（PCL 中常见的「返回」等）。 */
@Composable
fun PclLinkText(text: String, onClick: () -> Unit) {
    PclTextButton(text = text, onClick = onClick)
}
