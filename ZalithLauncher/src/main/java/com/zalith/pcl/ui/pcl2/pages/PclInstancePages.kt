/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 版本相关页面：
 *   Pages/PageSelectLeft.xaml  + PageSelectRight.xaml  —— 版本选择
 *   Pages/PageInstance/PageInstanceLeft.xaml           —— 版本管理左栏
 *   Pages/PageInstance/PageInstanceOverall.xaml        —— 概览
 *   Pages/PageInstance/PageInstanceSetup.xaml          —— 版本独立设置
 *   Pages/PageInstance/PageInstanceMod.xaml            —— Mod 管理
 *   Pages/PageInstance/PageInstanceExport.xaml         —— 导出整合包
 *
 * 这些页面在 PCL 中是「二级页面 + 自带左栏」的形态，这里对应 [PclSubPage.leftContent]。
 * 数据全部取自 Zalith 真实后端：VersionsManager / GamePathManager / VersionConfig /
 * AllModReader / PackExporter / FileManagerLauncher。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.pages

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.FileOutputStream
import com.zalith.pcl.R
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.plugin.driver.DriverPluginManager
import com.zalith.pcl.game.version.download.DownloadMode
import com.zalith.pcl.game.version.download.MinecraftDownloader
import com.zalith.pcl.game.version.installed.VersionLoginType
import com.zalith.pcl.ui.pcl2.data.PclInstanceMeta
import com.zalith.pcl.ui.pcl2.data.LITTLESKIN_AUTH_SERVER
import com.zalith.pcl.ui.pcl2.data.LITTLESKIN_REGISTER_URL
import com.zalith.pcl.ui.pcl2.data.LITTLESKIN_SERVER_NAME
import com.zalith.pcl.ui.pcl2.data.description
import com.zalith.pcl.ui.pcl2.data.displayName
import com.zalith.pcl.ui.pcl2.data.isThirdParty
import com.zalith.pcl.ui.pcl2.data.loginTypeValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zalith.pcl.filemanager.FileManagerLauncher
import com.zalith.pcl.game.addons.modloader.ModLoader
import com.zalith.pcl.game.download.modpack.install.ModpackImporter
import com.zalith.pcl.game.path.GamePathManager
import com.zalith.pcl.game.version.export.ExportInfo
import com.zalith.pcl.game.version.export.PackExporter
import com.zalith.pcl.game.version.export.PackType
import androidx.compose.ui.res.stringResource
import com.zalith.pcl.game.version.export.data.FileSelectionData
import com.zalith.pcl.game.version.export.data.Selected
import com.zalith.pcl.game.version.export.data.buildExportFileTree
import com.zalith.pcl.game.version.export.data.getSelectedFiles
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.game.version.installed.SettingState
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionConfig
import com.zalith.pcl.game.version.installed.GRAPHICS_API_CHOICES
import com.zalith.pcl.game.version.installed.GraphicsApi
import com.zalith.pcl.game.version.installed.forUi
import com.zalith.pcl.game.version.installed.uiLabel
import com.zalith.pcl.utils.platform.bytesToMB
import com.zalith.pcl.utils.platform.getTotalMemory
import com.zalith.pcl.utils.platform.getUsedMemory
import com.zalith.pcl.utils.checkStoragePermissions
import android.Manifest
import androidx.core.app.ActivityCompat
import com.zalith.pcl.ui.pcl2.components.PclActionDialog
import com.zalith.pcl.utils.checkStoragePermissionsForInit
import com.zalith.pcl.utils.getPermissionSettingsAndroid11AndAbove
import com.zalith.pcl.utils.hasStoragePermission
import com.zalith.pcl.game.version.installed.VersionFolders
import com.zalith.pcl.game.version.mod.AllModReader
import com.zalith.pcl.game.version.mod.LocalMod
import com.zalith.pcl.game.version.mod.isEnabled
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclCenteredPage
import com.zalith.pcl.ui.pcl2.PclContent
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSection
import com.zalith.pcl.ui.pcl2.PclStrings
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.PclTitledTaskDialog
import com.zalith.pcl.ui.pcl2.pclAskVersionName
import com.zalith.pcl.ui.pcl2.modpackImportErrorText
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclComboBox
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclEmptyButton
import com.zalith.pcl.ui.pcl2.components.PclEmptyState
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclInputDialog
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclLoading
import com.zalith.pcl.ui.pcl2.components.PclLoadingCard
import com.zalith.pcl.ui.pcl2.components.PclRadioBox
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclSlider
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.components.rememberPclRowChunks
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.data.PclLeftLabels
import com.zalith.pcl.ui.pcl2.data.PclModIcons
import com.zalith.pcl.ui.pcl2.data.PclSettings
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.data.settingStateLabel
import com.zalith.pcl.ui.pcl2.data.settingStateOrder
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclMotion
import com.zalith.pcl.ui.pcl2.theme.PclStaggerIn
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.ui.screens.content.elements.VersionIconImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/* ============================================================================
 *  版本管理
 * ==========================================================================*/

/** 版本管理左栏的选中项（对应 PCL 的 `FrmInstanceLeft.Instance`）。 */
class PclInstanceNav {
    var index by mutableIntStateOf(0)
        private set

    fun select(target: Int) {
        index = target
    }
}

/**
 * 左栏条目下标。
 *
 * PCL CE 的 `PageInstanceLeft` 是一条直线排下来的：
 * 概览 → 设置 →「游戏资源」分组（存档 / 截图 / 模组 / 资源包 / 光影包 / 投影原理图 / 服务器）
 * → 导出。分组标签本身不占下标，所以「模组」从原来的 2 挪到了 4 —— 这里用常量
 * 而不是字面量，免得以后再插条目又要满文件找 `index == 2`。
 */
private object PclInstancePageIndex {
    const val Overview = 0
    const val Setup = 1
    const val Saves = 2
    const val Screenshot = 3
    const val Mod = 4
    const val Resourcepack = 5
    const val Shader = 6
    const val Schematic = 7
    const val Server = 8
    const val Export = 9
}

/**
 * 「版本管理」二级页面（`PageType.InstanceSetup`）。
 * 左栏：概览 / 设置 / 游戏资源 / 导出（对应的 PCL 常量见 [PclLeftLabels.instance]）。
 */
fun pclInstanceVersionPage(): PclSubPage {
    val inav = PclInstanceNav()
    return PclSubPage(
        title = PclStrings.LangPageSetupLaunchGotoVersionSetting,
        leftContent = { PclInstanceLeft(inav) },
        leftLabels = PclLeftLabels.instance,
        content = { PclInstanceRight(inav) },
    )
}

/** 左栏分组标签：`TextBlock Margin="13,18,5,4" Opacity="0.6" FontSize="12"`。 */
@Composable
private fun PclInstanceGroupLabel(text: String) {
    val p = pclPalette
    PclText(
        text = text,
        modifier = Modifier
            .padding(start = 13.dp, top = 18.dp, end = 5.dp, bottom = 4.dp)
            .graphicsLayer(alpha = 0.6f),
        fontSize = 12,
        color = p.color1,
    )
}

/**
 * 左栏的一项：条目（带图标）或分组标签。
 *
 * 分组标签没有图标列、也不可点击，但它同样占一个入场序号，
 * 所以两者放在同一个列表里按顺序 `forEachIndexed`。
 */
private sealed interface PclInstanceLeftItem {
    data class Entry(
        val index: Int,
        val title: String,
        val logo: ImageVector,
        val logoScale: Float = 1f,
    ) : PclInstanceLeftItem

    data class Label(val text: String) : PclInstanceLeftItem
}

/** `PageInstanceLeft` 的条目顺序（PCL CE：概览 / 设置 →「游戏资源」→ 导出）。 */
private val pclInstanceLeftItems: List<PclInstanceLeftItem> = listOf(
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Overview, PclLeftLabels.instanceOverview, PclIcons.instanceOverview),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Setup, PclLeftLabels.instanceSetup, PclIcons.instanceSetup, 0.9f),
    PclInstanceLeftItem.Label(PclLeftLabels.instanceResourceGroup),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Saves, PclLeftLabels.instanceSaves, PclIcons.instanceSaves),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Screenshot, PclLeftLabels.instanceScreenshot, PclIcons.instanceScreenshot),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Mod, PclLeftLabels.instanceMod, PclIcons.instanceMod, 0.97f),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Resourcepack, PclLeftLabels.instanceResourcepack, PclIcons.dlResourcePack, 0.81f),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Shader, PclLeftLabels.instanceShader, PclIcons.dlShader, 1.04f),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Schematic, PclLeftLabels.instanceSchematic, PclIcons.instanceSchematic),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Server, PclLeftLabels.instanceServer, PclIcons.instanceServer),
    PclInstanceLeftItem.Entry(PclInstancePageIndex.Export, PclLeftLabels.instanceExport, PclIcons.instanceExport),
)

/** `PageInstanceLeft` —— 概览 / 设置 / 游戏资源（存档…服务器）/ 导出。 */
@Composable
private fun PclInstanceLeft(inav: PclInstanceNav) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp),
    ) {
        // MyPageLeft.TriggerShowAnimation：按顺序逐个入场
        pclInstanceLeftItems.forEachIndexed { animIndex, item ->
            when (item) {
                is PclInstanceLeftItem.Label -> PclStaggerIn(
                    index = animIndex,
                    labelAlpha = PclMotion.LEFT_ENTER_LABEL_ALPHA,
                ) { PclInstanceGroupLabel(item.text) }

                is PclInstanceLeftItem.Entry -> PclStaggerIn(animIndex) {
                    PclListItem(
                        title = item.title,
                        logo = item.logo,
                        logoScale = item.logoScale,
                        type = PclListItemType.RadioBox,
                        checked = inav.index == item.index,
                        height = 36.dp,
                        fontSize = PclDimens.leftItemFontSize,
                        // PageInstanceLeft.xaml 每个条目都写了 MinPaddingRight="35"
                        minPaddingRight = 35.dp,
                        onClick = { inav.select(item.index) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** `PageInstanceLeft.PageGet` —— 按左栏选中项切换右侧内容。 */
@Composable
private fun PclInstanceRight(inav: PclInstanceNav) {
    val runtime = LocalPclRuntime.current
    val version by runtime.currentVersion.collectAsStateWithLifecycle()

    if (version == null) {
        PclContent {
            PclEmptyState(
                title = PclStrings.LangLaunchLeftNoAvailableInstance,
                content = "请先在「版本选择」里选择一个版本，或到「下载」页安装一个新版本。",
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(PclDimens.cardGap))
        }
        return
    }

    val v = version ?: return
    when (inav.index) {
        PclInstancePageIndex.Overview -> PclInstanceOverallPage(v)
        PclInstancePageIndex.Setup -> PclInstanceSetupPage(v)
        PclInstancePageIndex.Saves -> PclInstanceSavesPage(v)
        PclInstancePageIndex.Screenshot -> PclInstanceScreenshotPage(v)
        PclInstancePageIndex.Mod -> PclInstanceModPage(v)
        PclInstancePageIndex.Resourcepack -> PclInstanceResourcepackPage(v)
        PclInstancePageIndex.Shader -> PclInstanceShaderPage(v)
        PclInstancePageIndex.Schematic -> PclInstanceSchematicPage(v)
        PclInstancePageIndex.Server -> PclInstanceServerPage(v)
        else -> PclInstanceExportPage(v)
    }
}

/**
 * 打开一个目录（走 Zalith 自带的文件管理器）。
 *
 * 原来只是一句 `runCatching { launch(...) }`，于是：
 *   · 目录还不存在（存档 / 资源包 / 光影包目录在「没装过任何东西」时本来就不存在），
 *     文件管理器打开一个不存在的路径会直接抛异常，被 runCatching 吞掉 ——
 *     用户看到的就是「点了没反应」；
 *   · 真正出错时也没有任何提示。
 * 所以这里先补建目录，再兜住异常并给出可见的反馈。
 */
internal fun openFolder(context: android.content.Context, folder: File) {
    if (!folder.exists()) {
        runCatching { folder.mkdirs() }
    }
    if (!folder.exists()) {
        PclHintHost.push("目录不存在，也无法创建：${folder.absolutePath}", PclHintTheme.Red)
        return
    }
    runCatching {
        FileManagerLauncher.launch(context, rootPath = folder.absolutePath)
    }.onFailure { th ->
        PclHintHost.push(
            "打开文件夹失败：${th.message ?: th.javaClass.simpleName}\n${folder.absolutePath}",
            PclHintTheme.Red,
        )
    }
}

/**
 * `PageInstanceOverall` —— 版本概览。
 *
 * 版式照 PCL 2.9 的「实例设置 · 概览」：五张卡片依次是
 * ① 实例显示卡（无标题，`MyCard Margin="0,15"` + `Grid Margin="10,7"`）
 * ② 实例信息（启动次数 / Minecraft，图标 + 两行文字）
 * ③ 个性化（图标 / 分类 两个组合框 + 修改实例名 / 修改实例描述 / 加入收藏夹）
 * ④ 快捷方式（实例文件夹 / 存档文件夹）
 * ⑤ 高级管理（导出启动脚本 / 测试游戏 / 补全文件 ；重置 / 删除实例 / 修补核心）
 *
 * 三张带标题卡片的正文都是 `StackPanel Margin="25,40,25,15"`，按钮行是
 * `Grid Margin="0,2,0,7" Height="35"`、每行 3 个 `MinWidth=140 Padding="13,0"` 且右边距 20。
 *
 * 每一项的实际动作都接在 Zalith 的真实能力上（见各按钮处的注释）；
 * 其中「分类 / 启动次数 / 图标选择」内核没有对应字段，由 [PclInstanceMeta] 单独记录。
 */
@Composable
private fun PclInstanceOverallPage(version: Version) {
    val p = pclPalette
    val runtime = LocalPclRuntime.current
    val context = LocalContext.current

    var renaming by remember { mutableStateOf(false) }
    var editingDesc by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var iconPicker by remember { mutableStateOf(false) }
    // 图标 / 分类 / 启动次数改动后用来触发重读
    var refreshKey by remember { mutableIntStateOf(0) }

    val info = version.getVersionInfo()
    val versionName = version.getVersionName()
    val customInfo = remember(version, refreshKey) { version.getVersionConfig().customInfo }
    val startCount = remember(version, refreshKey) { PclInstanceMeta.startCount(versionName) }
    val category = remember(version, refreshKey) { PclInstanceMeta.categoryOf(versionName) }
    val iconChoice = remember(version, refreshKey) { PclInstanceMeta.iconChoiceOf(versionName) }

    // 「自定义…」用系统图片选择器（PCL 的 Tag="PCL\Logo.png" 对应内核的 VersionIcon.png）
    val customIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val target = version.getVersionIconFile()
            val ok = runCatching {
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("无法读取所选图片")
                true
            }.getOrElse { false }
            if (ok) {
                PclInstanceMeta.setIconChoice(versionName, PclInstanceMeta.ICON_CUSTOM)
                refreshKey++
                PclHintHost.push("已更新实例图标。", PclHintTheme.Green)
            } else {
                PclHintHost.push("图标设置失败。", PclHintTheme.Red)
            }
        }
    }

    PclContent {
        // ① 实例显示卡
        PclCard(
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            contentPadding = PaddingValues(10.dp, 7.dp, 10.dp, 7.dp),
        ) {
            PclListItem(
                title = versionName,
                info = customInfo.ifBlank { info?.getInfoString() ?: versionName },
                type = PclListItemType.None,
                // 与 `PclVersionRow` 同理：图标盒是确定尺寸的 Box，撑满整盒 + ContentScale.Fit
                // 才等价于 `PathLogo` 的 `Stretch=Uniform`；不给尺寸时加载中/失败会塌成 0 高。
                logoContent = { VersionIconImage(version = version, modifier = Modifier.fillMaxSize(), refreshKey = refreshKey) },
                onClick = { openFolder(context, version.getVersionPath()) },
            )
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ② 实例信息
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = "实例信息",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclInstanceInfoItem(
                    icon = { PclIconBox(PclIcons.instanceOverview) },
                    title = "启动次数",
                    info = "已启动 $startCount 次",
                )
                PclInstanceInfoItem(
                    icon = { PclIconBox(painterResource(R.drawable.pcl_block_grass)) },
                    title = "Minecraft",
                    info = info?.minecraftVersion ?: versionName,
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ③ 个性化
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = "个性化",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            PclInstanceSettingRow("图标") {
                PclComboBox(
                    items = PCL_OVERALL_ICONS,
                    selected = PCL_OVERALL_ICONS.firstOrNull { it.second == iconChoice }
                        ?: PCL_OVERALL_ICONS.first(),
                    label = { it?.first.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { option ->
                        when (option.second) {
                            PclInstanceMeta.ICON_AUTO -> {
                                version.getVersionIconFile().delete()
                                PclInstanceMeta.setIconChoice(versionName, PclInstanceMeta.ICON_AUTO)
                                refreshKey++
                            }

                            PclInstanceMeta.ICON_CUSTOM -> iconPicker = true

                            else -> {
                                val resId = option.third
                                val ok = resId != null && copyDrawableToFile(
                                    context = context,
                                    resId = resId,
                                    target = version.getVersionIconFile(),
                                )
                                if (ok) {
                                    PclInstanceMeta.setIconChoice(versionName, option.second)
                                    refreshKey++
                                    PclHintHost.push("已更新实例图标。", PclHintTheme.Green)
                                } else {
                                    PclHintHost.push("图标设置失败。", PclHintTheme.Red)
                                }
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclInstanceSettingRow("分类") {
                PclComboBox(
                    items = PCL_OVERALL_CATEGORIES,
                    selected = PCL_OVERALL_CATEGORIES.first { it.second == category },
                    label = { it?.first.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { option ->
                        PclInstanceMeta.setCategory(versionName, option.second)
                        refreshKey++
                        runtime.refreshVersions()
                    },
                )
            }
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PclOverallButton("修改实例名") { renaming = true }
                PclOverallButton("修改实例描述") { editingDesc = true }
                PclOverallButton(if (version.pinnedState) "从收藏夹中移除" else "加入收藏夹") {
                    runtime.togglePinned(version)
                    refreshKey++
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ④ 快捷方式
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = "快捷方式",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PclOverallButton("实例文件夹") { openFolder(context, version.getVersionPath()) }
                PclOverallButton("存档文件夹") {
                    openFolder(context, VersionFolders.SAVES.getDir(version.getGameDir()))
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ⑤ 高级管理
        PclCard(modifier = Modifier.fillMaxWidth(),
            title = "高级管理",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PclOverallButton("导出启动脚本") { exportLaunchArgs(context, version) }
                PclOverallButton("测试游戏") {
                    runtime.selectVersion(version)
                    runtime.tryLaunch()
                }
                PclOverallButton("补全文件") { verifyAndRepair(context, version) }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PclOverallButton("重置") {
                    resetVersionConfig(version)
                    PclHintHost.push("已把「$versionName」的设置恢复为默认值。", PclHintTheme.Green)
                }
                PclOverallButton("删除实例", danger = true) { deleting = true }
                // 修补核心同样走「校验并修复」：它会重新校验并下载所有缺失/损坏的文件（含核心 jar）
                PclOverallButton("修补核心") { verifyAndRepair(context, version) }
            }
            Spacer(Modifier.height(7.dp))
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }

    if (renaming) {
        PclInputDialog(
            title = "重命名版本",
            label = "新的版本名（不能包含 \\ / : * ? \" < > |）",
            initial = versionName,
            onConfirm = { name ->
                if (name.isNotBlank() && name != versionName) runtime.renameVersion(version, name)
            },
            onDismiss = { renaming = false },
        )
    }
    if (editingDesc) {
        PclInputDialog(
            title = "更改描述",
            label = "修改版本的描述文本，留空则使用默认描述",
            initial = customInfo,
            onConfirm = { text ->
                runCatching {
                    val config = version.getVersionConfig()
                    config.customInfo = text.trim()
                    config.save()
                }.onSuccess {
                    refreshKey++
                    PclHintHost.push("描述已更新。", PclHintTheme.Green)
                }.onFailure {
                    runtime.submitErrorText("保存描述失败", it.message ?: it.javaClass.simpleName)
                }
            },
            onDismiss = { editingDesc = false },
        )
    }
    if (iconPicker) {
        // 只有「自定义…」需要选文件；方块图标直接写内置图片
        customIconPicker.launch("image/*")
        iconPicker = false
    }
    if (deleting) {
        PclConfirmDialog(
            title = "删除版本",
            content = "确定要删除「$versionName」吗？该版本的存档、Mod 与配置都会一并删除，且无法恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = { runtime.deleteVersion(version) },
            onDismiss = { deleting = false },
        )
    }
}

/** 图标组合框的候选项：`(显示名, 键, 内置图片资源)`；`键` 用 [PclInstanceMeta] 里的常量。 */
private val PCL_OVERALL_ICONS: List<Triple<String, String, Int?>> = listOf(
    Triple("自动", PclInstanceMeta.ICON_AUTO, null),
    Triple("自定义…", PclInstanceMeta.ICON_CUSTOM, null),
    Triple("圆石", "res:cobblestone", R.drawable.pcl_block_cobblestone),
    Triple("命令方块", "res:command_block", R.drawable.pcl_block_command_block),
    Triple("金块", "res:gold_block", R.drawable.pcl_block_gold_block),
    Triple("草方块", "res:grass", R.drawable.pcl_block_grass),
    Triple("土径", "res:grass_path", R.drawable.pcl_block_grass_path),
    Triple("铁砧", "res:anvil", R.drawable.pcl_block_anvil),
    Triple("红石块", "res:redstone", R.drawable.pcl_block_redstone),
    Triple("红石灯（开）", "res:redstone_lamp", R.drawable.pcl_block_redstone_lamp),
    Triple("鸡蛋", "res:egg", R.drawable.pcl_block_egg),
    Triple("布料（Fabric）", "res:fabric", R.drawable.pcl_block_fabric),
    Triple("狐狸（NeoForge）", "res:neoforge", R.drawable.pcl_block_neoforge),
)

/** 分类组合框的候选项：`(显示名, 键)`，顺序与 PCL 一致。 */
private val PCL_OVERALL_CATEGORIES: List<Pair<String, String>> = listOf(
    "自动" to PclInstanceMeta.CATEGORY_AUTO,
    "从版本列表中隐藏" to PclInstanceMeta.CATEGORY_HIDDEN,
    "可安装 Mod 的版本" to PclInstanceMeta.CATEGORY_MOD,
    "常规版本" to PclInstanceMeta.CATEGORY_REGULAR,
    "不常用版本" to PclInstanceMeta.CATEGORY_UNCOMMON,
    "愚人节版本" to PclInstanceMeta.CATEGORY_FOOL,
)

/** 实例信息里的一项：图标 + 标题 + 说明（PCL 用两块 34 高的图标配两行文字）。 */
@Composable
private fun PclInstanceInfoItem(
    icon: @Composable () -> Unit,
    title: String,
    info: String,
) {
    val p = pclPalette
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(10.dp))
        Column {
            PclText(text = title, fontSize = 14)
            PclText(text = info, fontSize = 12, color = p.gray2)
        }
    }
}

/** 34×34 的图标盒子。 */
@Composable
private fun PclIconBox(icon: Painter) {
    Image(
        painter = icon,
        contentDescription = null,
        modifier = Modifier.size(34.dp),
    )
}

/** 34×34 的矢量图标盒子。 */
@Composable
private fun PclIconBox(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = pclPalette.color1,
        modifier = Modifier.size(34.dp),
    )
}

/** 个性化卡片里的一行「标签 + 控件」：标签列固定 40 + 25 间距（对应 PCL 的
 *  `ColumnDefinition Width="Auto" SharedSizeGroup="Name"` + `Margin="0,0,25,0"`）。 */
@Composable
private fun PclInstanceSettingRow(label: String, control: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(40.dp)) { PclText(text = label) }
        Spacer(Modifier.width(25.dp))
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/** PCL 概览页的按钮：`MinWidth=140 Padding="13,0" Margin="0,0,20,0"`。 */
@Composable
private fun PclOverallButton(
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    PclButton(
        text = text,
        colorState = if (danger) PclColorState.Red else PclColorState.Normal,
        contentPadding = PaddingValues(horizontal = 13.dp),
        modifier = Modifier.widthIn(min = 140.dp),
        onClick = onClick,
    )
}

/** 把内置图片（`res/drawable-nodpi/pcl_block_*.png`）写成实例图标。 */
private fun copyDrawableToFile(context: android.content.Context, resId: Int, target: File): Boolean =
    runCatching {
        target.parentFile?.mkdirs()
        context.resources.openRawResource(resId).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)

/**
 * 「导出启动脚本」。
 *
 * PCL 是导出一个 .bat；Zalith 的启动命令由内核在启动时用 `LaunchArgs` 现场拼装
 * （构造它需要账号、内置登录服务端、assets 读取器等 11 个参数），界面侧无法离线复现整条命令。
 * 这里导出的是这个版本**真实生效的启动配置**（Java / 内存 / 自定义参数 / 渲染器 / 隔离），
 * 完整命令行可在「启动日志」里看到，出问题时对照排查足够。
 */
private fun exportLaunchArgs(context: android.content.Context, version: Version) {
    runCatching {
        val config = version.getVersionConfig()
        val file = File(version.getVersionPath(), "启动参数.txt")
        file.writeText(
            buildString {
                appendLine("版本：${version.getVersionName()}")
                appendLine("Minecraft：${version.getVersionInfo()?.minecraftVersion ?: "-"}")
                appendLine("加载器：${version.getVersionInfo()?.primaryLoader?.let { "${it.loader.displayName} ${it.version}" } ?: "原版"}")
                appendLine("游戏目录：${version.getGameHome()}")
                appendLine("版本目录：${version.getVersionPath().absolutePath}")
                appendLine("版本隔离：${if (version.isIsolation()) "开启" else "关闭"}")
                appendLine("跳过文件校验：${if (version.skipGameIntegrityCheck()) "是" else "否"}")
                appendLine("Java 运行库：${config.javaRuntime.ifBlank { "跟随全局设置" }}")
                appendLine("内存分配：${if (config.ramAllocation > 0) "${config.ramAllocation} MB" else "跟随全局设置"}")
                appendLine("渲染器：${config.renderer.ifBlank { "跟随全局设置" }}")
                appendLine("自定义 JVM 参数：${config.jvmArgs.ifBlank { "（空）" }}")
                appendLine("自定义游戏参数：${config.gameArgs.ifBlank { "（空）" }}")
                appendLine()
                appendLine("注：Zalith 的实际启动命令由内核在启动时生成，")
                appendLine("    完整内容见启动日志（概览页的日志或「更多 → 帮助」里的日志入口）。")
            }
        )
        PclHintHost.push("启动参数已导出到：${file.absolutePath}", PclHintTheme.Green)
    }.onFailure {
        PclHintHost.push("导出启动脚本失败：${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
    }
}

/**
 * 「补全文件 / 修补核心」：走内核的「校验并修复」下载器
 * （`DownloadMode.VERIFY_AND_REPAIR`，与启动前那次校验是同一个实现），
 * 它会重新校验并下载所有缺失或损坏的文件，包括 Minecraft 核心 jar。
 */
private fun verifyAndRepair(context: android.content.Context, version: Version) {
    runCatching {
        val task = MinecraftDownloader(
            context = context,
            version = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName(),
            customName = version.getVersionName(),
            gameHome = version.getGameHome(),
            mode = DownloadMode.VERIFY_AND_REPAIR,
            onError = { message ->
                PclHintHost.push("校验失败：$message", PclHintTheme.Red)
            },
        ).getDownloadTask()
        TaskSystem.submitTask(task)
        PclHintHost.push("已提交校验任务，进度见「下载管理」。", PclHintTheme.Blue)
    }.onFailure {
        PclHintHost.push("无法启动校验：${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
    }
}

/**
 * 「重置」：把该版本的独立设置恢复成默认值
 * （对应 PCL 概览页的重置，清掉 `PCL\Setup.ini` 里的版本设置）。
 */
private fun resetVersionConfig(version: Version) {
    runCatching {
        version.getVersionConfig().apply {
            isolationType = SettingState.FOLLOW_GLOBAL
            skipGameIntegrityCheck = SettingState.FOLLOW_GLOBAL
            javaRuntime = ""
            jvmArgs = ""
            gameArgs = ""
            renderer = ""
            driver = ""
            graphicsApi = null
            control = ""
            customPath = ""
            ramAllocation = -1
            serverIp = ""
        }.save()
    }
}

/**
 * `PageInstanceSetup` —— 版本独立设置（版式照 PCL 2.9 的「实例设置 · 设置」）。
 *
 * 四张卡片：启动选项 / 游戏内存 / 服务器 / 高级启动选项（默认折叠），末尾是 PCL 的那句提示
 * 与「→ 全局设置」按钮。全部读写 Zalith 的 `VersionConfig`（版本目录下的 version.config），
 * 「跟随全局设置」= `SettingState.FOLLOW_GLOBAL` 或字段留空。
 *
 * 内核没有对应字段、因此这里没有放的行（PCL 有）：
 *   · 游戏窗口标题 / 默认窗口标题 —— Zalith 没有版本级的窗口标题设置；
 *   · 限制验证方式 —— Zalith 没有版本级的验证方式开关；
 *   · 游戏内存的「自动配置」—— Zalith 的自动内存是全局开关，版本级只有「跟随全局 / 自定义」。
 */
@Composable
private fun PclInstanceSetupPage(version: Version) {
    val p = pclPalette
    val context = LocalContext.current
    val nav = LocalPclNav.current
    val config: VersionConfig = version.getVersionConfig()

    // 用一个 token 触发重算，保证修改后界面立刻反映
    var token by remember { mutableIntStateOf(0) }
    LaunchedEffect(token) { }

    // ---- 游戏内存（PCL：跟随全局设置 / 自定义 + 滑条 + 已使用内存/游戏分配）
    val memoryLimit = remember { PclSettings.memoryLimitMb(context) }
    var ramCustom by remember { mutableStateOf(config.ramAllocation >= 256) }
    var ramFraction by remember {
        mutableStateOf(PclSettings.ramToFraction(version.getRamAllocation(context), memoryLimit))
    }
    var totalMb by remember { mutableStateOf(0.0) }
    var usedMb by remember { mutableStateOf(0.0) }
    LaunchedEffect(token) {
        withContext(Dispatchers.IO) {
            runCatching {
                totalMb = getTotalMemory(context).bytesToMB()
                usedMb = getUsedMemory(context).bytesToMB()
            }
        }
    }

    // ---- 渲染器 / Java / 控制布局
    //
    // 渲染器列表只是 `Renderers` 单例里的内存表（不碰磁盘），所以**同步**取：
    // 原来放在 IO 协程里，第一帧 `rendererOptions` 还是空表，下拉里就只剩「跟随全局设置」。
    val rendererOptions = remember(version) { PclSettings.rendererOptions() }
    // 驱动列表同样是内存表（`PluginLoader` 启动时把插件扫好），同步取即可。
    //
    // 版本设置的「驱动」以前是个自由文本框：要单独设置就得手打 `Driver` 的 id，
    // 而 id 是插件包名 / 内置标识，谁也记不住，实际等于「只能跟随全局」。
    // 这里取一次列表给下拉用，和「渲染器」那行保持同一种交互。
    val driverOptions = remember(version) {
        runCatching { DriverPluginManager.getDriverList().map { it.id to it.name } }
            .getOrDefault(emptyList())
    }
    var javaOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var controlOptions by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val controlData by com.zalith.pcl.game.control.ControlManager.dataList
        .collectAsStateWithLifecycle()
    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            javaOptions = PclSettings.javaRuntimeOptions()
        }
    }
    LaunchedEffect(controlData) {
        controlOptions = controlData.filter { it.isSupport }.map { data ->
            data.file.name to data.controlLayout.info.name.default
        }
    }

    // SettingState 的显示名要按当前语言取，而组合框的 label lambda 不是 @Composable，
    // 所以先把三个名字算好（map 是内联的，可以在可组合上下文里调用 settingStateLabel）
    val stateLabels = settingStateOrder.map { settingStateLabel(it) }
    fun stateLabel(state: SettingState): String {
        val index = settingStateOrder.indexOf(state)
        return stateLabels.getOrElse(index) { stateLabels.first() }
    }

    // 控制布局列表必须自己拉一次。
    //
    // `ControlManager.dataList` 是个空表起步的 StateFlow，只有 `checkDefaultAndRefresh()`
    // 会解压一份默认布局再 `refresh()`。原生界面在 `MainActivity` 里调过一次，
    // PCL 界面只有「设置 → 控制布局」那一页调 —— 冷启动直接进 PCL 界面、又没进过那一页时，
    // 这里拿到的永远是空表，于是版本设置里「控制布局」下拉只剩「跟随全局设置」。
    LaunchedEffect(Unit) { ControlManager.checkDefaultAndRefresh(context) }

    fun saveToken() {
        config.save()
        token++
    }

    PclContent {
        // ---- 修改此实例
        //
        // PCL CE 的「版本设置」最上面就是这样一条：扳手图标 +「修改」+ 右侧一枚循环箭头，
        // 点进去是「修改实例」页（重选 Minecraft 版本与模组加载器，然后「开始重置」）。
        // 这里把整条做成一个可点的列表项，右侧那枚按钮和整条行点击走同一个入口。
        PclCard(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            PclListItem(
                title = "修改",
                info = "重新选择 Minecraft 版本与模组加载器",
                logo = PclIcons.instanceSetup,
                logoScale = 0.9f,
                height = 46.dp,
                buttons = listOf(
                    PclListItemButton(
                        logo = PclIcons.restartAlt,
                        tooltip = "修改此实例",
                        logoScale = 0.9f,
                    ) { nav.push(pclInstanceModifyPage(version)) },
                ),
                onClick = { nav.push(pclInstanceModifyPage(version)) },
            )
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 启动选项
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = "启动选项",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            PclSetupRow("实例隔离") {
                PclComboBox(
                    items = settingStateOrder,
                    selected = config.isolationType,
                    label = { item -> item?.let { s -> stateLabel(s) }.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.isolationType = it; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("自定义信息") {
                PclTextBox(
                    value = config.customInfo,
                    modifier = Modifier.fillMaxWidth(),
                    hint = "跟随全局设置",
                    onValueChange = { config.customInfo = it; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("游戏 Java") {
                // 同样直接列出全部 Java（空串 = 跟随全局），别再走「先展开一条、再点开弹窗」。
                val items = listOf("" to "跟随全局设置") + javaOptions
                PclComboBox(
                    items = items,
                    selected = items.firstOrNull { it.first == config.javaRuntime } ?: items.first(),
                    label = { it?.second.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.javaRuntime = it.first; saveToken() },
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 游戏内存
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = "游戏内存",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            Column {
                PclRadioBox(
                    text = PclStrings.LangPageVersionSetupUseGlobal,
                    checked = !ramCustom,
                    onCheckedChange = {
                        ramCustom = false
                        config.ramAllocation = -1
                        saveToken()
                    },
                )
                PclRadioBox(
                    text = PclStrings.LangPageSetupLaunchLaunchMemCustom,
                    checked = ramCustom,
                    onCheckedChange = {
                        ramCustom = true
                        config.ramAllocation = PclSettings.fractionToRam(ramFraction, memoryLimit)
                        saveToken()
                    },
                )
                PclSlider(
                    value = ramFraction,
                    enabled = ramCustom,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onValueChange = {
                        ramFraction = it
                        config.ramAllocation = PclSettings.fractionToRam(it, memoryLimit)
                        saveToken()
                    },
                )
                Spacer(Modifier.height(6.dp))
                // PCL：左「已使用内存 x / y」、右「游戏分配 z」，标签在上、数值在下
                val allocatedMb = if (ramCustom) {
                    PclSettings.fractionToRam(ramFraction, memoryLimit).toDouble()
                } else {
                    version.getRamAllocation(context).toDouble()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        PclText(
                            text = PclStrings.LangPageSetupLaunchLaunchMemUsedMem,
                            fontSize = 12,
                            color = p.gray2,
                        )
                        PclText(text = "%.1f GiB / %.1f GiB".format(usedMb / 1024.0, totalMb / 1024.0), fontSize = 15)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PclText(
                            text = PclStrings.LangPageSetupLaunchLaunchMemMemForGame,
                            fontSize = 12,
                            color = p.gray2,
                        )
                        PclText(text = "%.1f GiB".format(allocatedMb / 1024.0), fontSize = 15)
                    }
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 服务器（`PageInstanceSetup.xaml` 的「服务器」卡）
        //
        // 「登录方式」是整个启动器里唯一一处在**版本级**决定「启动时用哪一类账户」的开关：
        // 选了第三方登录，启动时就必须用对应认证服务器上的账户 —— 本地没有这一台的账户
        // 时，`PclThirdPartyLogin` 会在那一瞬间问账号密码并当场登录。
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = "服务器",
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            var loginType by remember(version) { mutableStateOf(config.loginTypeValue()) }
            PclSetupRow("登录方式") {
                PclComboBox(
                    items = VersionLoginType.entries,
                    selected = loginType,
                    label = { it?.displayName().orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { selected ->
                        loginType = selected
                        config.loginType = selected
                        saveToken()
                    },
                )
            }

            if (loginType.isThirdParty()) {
                val isUnifiedPassport = loginType == VersionLoginType.UNIFIED_PASSPORT
                Spacer(Modifier.height(9.dp))
                PclSetupRow(if (isUnifiedPassport) "服务器 ID" else "认证服务器") {
                    PclTextBox(
                        value = config.loginAuthServer,
                        modifier = Modifier.fillMaxWidth(),
                        hint = if (isUnifiedPassport) {
                            "服主后台 → 集成说明 中提供的服务器 ID"
                        } else {
                            "Yggdrasil API 认证服务器的完整地址"
                        },
                        onValueChange = { config.loginAuthServer = it; saveToken() },
                    )
                }
                if (!isUnifiedPassport) {
                    Spacer(Modifier.height(9.dp))
                    PclSetupRow("注册链接") {
                        PclTextBox(
                            value = config.loginRegisterUrl,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "注册账号的网页地址，如 https://xxxxx.xxx/auth/register",
                            onValueChange = { config.loginRegisterUrl = it; saveToken() },
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    PclSetupRow("服务器名称") {
                        PclTextBox(
                            value = config.loginServerName,
                            modifier = Modifier.fillMaxWidth(),
                            hint = "显示在登录页面的服务器名称",
                            onValueChange = { config.loginServerName = it; saveToken() },
                        )
                    }
                }
            }

            // PCL 的顺序：三行认证信息 → 「自动进入服务器」→ 按钮（`PageInstanceSetup.xaml` 里
            // 按钮在 Grid 外面，所以永远排在这些行后面）。
            Spacer(Modifier.height(9.dp))
            PclSetupRow("自动进入服务器") {
                PclTextBox(
                    value = config.serverIp,
                    modifier = Modifier.fillMaxWidth(),
                    hint = "例如 mc.example.com:25565，留空则直接进主界面",
                    onValueChange = { config.serverIp = it; saveToken() },
                )
            }

            if (loginType == VersionLoginType.AUTHLIB) {
                // PCL 的 `BtnServerAuthLittle`：Height=35、MinWidth=150、靠左、ColorType=Highlight。
                // 注意别用 fillMaxWidth —— 它在 PCL 里是个小按钮，拉满整行会显得整张卡都散了。
                Spacer(Modifier.height(12.dp))
                PclButton(
                    text = "设置为 LittleSkin",
                    height = 35.dp,
                    colorState = PclColorState.Highlight,
                    modifier = Modifier.widthIn(min = 150.dp),
                    onClick = {
                        config.loginAuthServer = LITTLESKIN_AUTH_SERVER
                        config.loginRegisterUrl = LITTLESKIN_REGISTER_URL
                        config.loginServerName = LITTLESKIN_SERVER_NAME
                        saveToken()
                        PclHintHost.push(
                            "已填入 LittleSkin 的认证服务器，启动该版本时会用 LittleSkin 账户登录。",
                            PclHintTheme.Green,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 高级启动选项（PCL 默认折叠）
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = "高级启动选项",
            collapsible = true,
            initiallyCollapsed = true,
            contentPadding = PaddingValues(25.dp, 40.dp, 25.dp, 15.dp),
        ) {
            PclSetupRow(PclStrings.LangPageSetupLaunchLaunchSkipCheck) {
                PclComboBox(
                    items = settingStateOrder,
                    selected = config.skipGameIntegrityCheck,
                    label = { item -> item?.let { s -> stateLabel(s) }.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.skipGameIntegrityCheck = it; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("渲染器") {
                // 下拉里**直接列出全部渲染器**（空串那项 = 跟随全局）。
                //
                // 这里原来是「单条目下拉 + 点它再弹选择框」：下拉永远只显示当前那一项，
                // 用户展开后只看到一条「跟随全局设置」，自然认为「只能选全局的」。
                // PCL 的版本设置本来就是一颗普通 ComboBox，这里对齐，去掉中间那一步。
                val globalName = rendererOptions
                    .firstOrNull { it.first == PclSettings.currentRendererId() }
                    ?.second
                val followLabel = globalName?.let { "跟随全局设置（$it）" } ?: "跟随全局设置"
                val items = listOf("" to followLabel) + rendererOptions
                PclComboBox(
                    items = items,
                    selected = items.firstOrNull { it.first == config.renderer } ?: items.first(),
                    label = { it?.second.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.renderer = it.first; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("图形 API") {
                // 标签统一走 `uiLabel`。原来这里用的是 `displayName`，
                // 而 `DEFAULT_OPENGL` 的 displayName 就是 `OPENGL` 的「OpenGL」，
                // 于是下拉里并排出现两个一模一样的「OpenGL」，谁也说不清该选哪个。
                val globalApi = AllSettings.graphicsApi.state.forUi
                val items = listOf(null to "跟随全局设置（${globalApi.uiLabel}）") +
                        GRAPHICS_API_CHOICES.map { it to it.uiLabel }
                val selected = config.graphicsApi?.forUi
                PclComboBox(
                    items = items,
                    selected = items.firstOrNull { it.first == selected } ?: items.first(),
                    label = { it?.second.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { option ->
                        config.graphicsApi = option.first
                        saveToken()
                        if (option.first == GraphicsApi.VULKAN) {
                            // Android 上的画面由启动器用 EGL + OpenGL 桥出来，游戏自己的
                            // Vulkan 后端拿不到可用的窗口表面 —— 选它就是白屏 / 闪退回启动器。
                            // 26.2 起才有的选项，坑很深，选完必须说清楚。
                            PclHintHost.push(
                                "Vulkan 在 Android 上会白屏：画面是启动器用 OpenGL 桥出来的，" +
                                        "游戏自己的 Vulkan 后端拿不到窗口。建议改回「跟随游戏设定」。",
                                PclHintTheme.Red,
                            )
                        }
                    },
                )
            }
            // 提示条放在 `PclSetupRow` **外面**：它的 content 只是 Row 里一个 weight(1f) 的 Box，
            // 塞两个可组合进去会直接叠在一起（Box 默认叠放），看起来像文字压在下拉框上。
            // 放外面还有个好处：它一直挂着，不像 `PclHintHost` 三秒就消失 ——
            // 白屏这种事得让用户随时能看见原因。
            if (config.graphicsApi == GraphicsApi.VULKAN) {
                PclHint(
                    text = "Android 上选 Vulkan 会白屏：画面是启动器用 EGL + OpenGL 桥出来的，" +
                            "游戏自带的 Vulkan 后端拿不到窗口表面。26.2 起游戏虽然有 Vulkan 后端，" +
                            "但在手机上走这条路目前进不去，请改回「跟随游戏设定」。",
                    theme = PclHintTheme.Red,
                    modifier = Modifier.padding(start = 110.dp, top = 4.dp, end = 10.dp),
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("控制布局") {
                // 配置里存的是**布局文件名**（`Version.getControlPath()` 拿它去
                // `DIR_CONTROL_LAYOUTS` 底下拼路径），所以选项 id 用 `file.name`、显示名用布局名。
                // 原来这里是 `onSelect = { config.control = it.second }` + 按显示名比对，
                // 存下去的是「用户看到的中文名」而不是文件名，进游戏根本找不到布局。
                val items = listOf("" to "跟随全局设置") + controlOptions
                PclComboBox(
                    items = items,
                    selected = items.firstOrNull { it.first == config.control } ?: items.first(),
                    label = { it?.second.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.control = it.first; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("驱动") {
                // 空串 = 跟随全局设置（`Version.getDriver()` 就是这么回落的：
                // `versionConfig.driver.getValueOrDefault(AllSettings.vulkanDriver.getValue())`）。
                //
                // 以前这里是自由文本框，要单独设置必须手打驱动 id（插件包名），
                // 记不住就只能留空 —— 等于「只能跟随全局」。现在改成下拉：
                // 第一项是跟随全局并显示全局当前选的是哪个驱动，后面列全部已装驱动。
                val globalDriverId = AllSettings.vulkanDriver.state
                val globalDriverName = driverOptions
                    .firstOrNull { it.first == globalDriverId }?.second
                    ?: globalDriverId
                val items = buildList {
                    add("" to "跟随全局设置（$globalDriverName）")
                    addAll(driverOptions)
                    // 存着的 id 可能来自已卸载的驱动插件：补一条「未安装」进去。
                    // 否则下拉会回落到第一项、显示成「跟随全局」，用户以为设置丢了。
                    if (config.driver.isNotBlank() && driverOptions.none { it.first == config.driver }) {
                        add(config.driver to "${config.driver}（未安装）")
                    }
                }
                PclComboBox(
                    items = items,
                    selected = items.firstOrNull { it.first == config.driver } ?: items.first(),
                    label = { it?.second.orEmpty() },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { config.driver = it.first; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("自定义游戏路径") {
                PclTextBox(
                    value = config.customPath,
                    modifier = Modifier.fillMaxWidth(),
                    hint = "跟随全局设置",
                    onValueChange = { config.customPath = it; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("自定义 JVM 参数") {
                PclTextBox(
                    value = config.jvmArgs,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    hint = "留空则使用全局设置",
                    onValueChange = { config.jvmArgs = it; saveToken() },
                )
            }
            Spacer(Modifier.height(9.dp))
            PclSetupRow("自定义游戏参数") {
                PclTextBox(
                    value = config.gameArgs,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    hint = "留空则使用全局设置",
                    onValueChange = { config.gameArgs = it; saveToken() },
                )
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // PCL 页面底部那句提示 + 「→ 全局设置」（PCL 里是右下角的悬浮按钮）
        PclText(
            text = "跳过校验可以加快启动，但游戏文件缺失时不会自动修复。",
            fontSize = 12,
            color = p.gray2,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            PclButton(
                text = "→ 全局设置",
                colorState = PclColorState.Highlight,
                onClick = { nav.go(PclSection.Setup) },
            )
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }
}

/** 版本设置页里的一行「标签 + 控件」：标签列固定宽（PCL 用 SharedSizeGroup 对齐两列）。 */
@Composable
private fun PclSetupRow(
    label: String,
    control: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(110.dp)) { PclText(text = label) }
        Box(modifier = Modifier.weight(1f)) { control() }
    }
}

/**
 * `PageInstanceMod` —— Mod 管理（版式照 PCL 2.9）。
 *
 * 结构：搜索框（`搜索 Mod 名称 / 描述 / 标签`）→ 管理按钮卡（打开文件夹 / 从文件安装 /
 * 下载新 Mod / 全选）→ 列表卡（全部 / 已启用 / 已禁用 三枚过滤胶囊 + Mod 列表）。
 * Mod 数据全部来自 Zalith 的 `AllModReader`，启用/禁用就是 PCL 那套 `.disabled` 重命名。
 *
 * 与 PCL 的差异：PCL 的「可更新」过滤需要模组平台接口，Zalith 的 `LocalMod` 没有远端更新信息，
 * 因此这里只放「全部 / 已启用 / 已禁用」三枚胶囊。
 */
@Composable
private fun PclInstanceModPage(version: Version) {
    val p = pclPalette
    val context = LocalContext.current
    val nav = LocalPclNav.current
    val scope = rememberCoroutineScope()
    val modsDir = remember(version) { VersionFolders.MOD.getDir(version.getGameDir()) }

    // 实例自己没装 Mod 加载器时，PCL 并不会显示一个空列表，而是整页换成
    // `PageInstanceModDisabled` 那张提示卡（`PageInstanceLeft.xaml.vb:9-16` 会顺手把左栏的
    // Mod 管理条目也换成禁用版 —— 两条条目标题图标完全一样，所以左栏不必动）。
    // 否则用户会看到「尚未安装 Mod」，然后去点「从文件安装」，装完却根本加载不了。
    if (!version.hasModLoader()) {
        PclInstanceModUnavailablePage()
        return
    }

    var keyword by remember { mutableStateOf("") }
    var mods by remember { mutableStateOf<List<LocalMod>>(emptyList()) }
    /** 每个 Mod 自带图标（从 jar 里读出来的），key = 文件绝对路径。 */
    var modIcons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var token by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<LocalMod?>(null) }
    /** 0 全部 / 1 已启用 / 2 已禁用 */
    var filter by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(version, token) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching { AllModReader(modsDir).readAllLocals() }.getOrDefault(emptyList())
        }
        mods = loaded
        // 图标要从 jar 里解出来，跟着列表一起在 IO 线程读；
        // PclModIcons 内部按「路径 + 大小 + 修改时间」缓存，重复刷新不会反复解压。
        modIcons = withContext(Dispatchers.IO) { PclModIcons.loadAll(loaded) }
        loading = false
    }

    // 「从文件安装」：选一个 Mod 文件拷进 mods 目录（PCL 的拖入安装的等价入口）
    val installPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)?.name
                }.getOrNull()
            } ?: "mod.jar"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    modsDir.mkdirs()
                    val target = File(modsDir, if (name.endsWith(".jar")) name else "$name.jar")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选文件")
                    true
                }.getOrDefault(false)
            }
            if (ok) {
                PclHintHost.push("已安装「$name」。", PclHintTheme.Green)
                token++
            } else {
                PclHintHost.push("安装失败。", PclHintTheme.Red)
            }
        }
    }

    // PageInstanceMod.xaml.vb:133-180 —— 计数与列表都以「搜索结果」为基准：
    // 搜索时顶部胶囊显示的是命中集合里的数量，而不是全部 Mod 的数量。
    val searching = keyword.isNotBlank()
    val searched = if (searching) {
        mods.filter { mod ->
            mod.name.contains(keyword, true) ||
                    (mod.description?.contains(keyword, true) == true) ||
                    mod.id.contains(keyword, true)
        }
    } else {
        mods
    }
    val anyCount = searched.size
    val enabledCount = searched.count { it.file.isEnabled() }
    val disabledCount = anyCount - enabledCount
    val visible = searched.filter { mod ->
        when (filter) {
            1 -> mod.file.isEnabled()
            2 -> !mod.file.isEnabled()
            else -> true
        }
    }

    fun applyMods(block: (LocalMod) -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { selected.forEach { path -> mods.firstOrNull { it.file.absolutePath == path }?.let(block) } }
            selected = emptySet()
            token++
        }
    }

    // PageInstanceMod.xaml.vb:76-86 —— 列表为空（或还在读）时 PCL 会把整个 PanBack
    // （搜索框 + 管理按钮卡 + 列表卡）Collapsed 掉，只留一张居中的卡片。
    if (mods.isEmpty()) {
        // 同样是 `MyPageRight` 里的居中卡片（PanLoad / PanEmpty），
        // 用带入场动画的居中页，免得加载态和空态都是「啪」一下出现。
        PclCenteredPage {
            if (loading) {
                // PanLoad：MyCard + 居中 MyLoading（Margin="20,20,20,17"）
                PclCard(modifier = Modifier.padding(horizontal = 40.dp), contentPadding = PaddingValues(0.dp)) {
                    PclLoading(text = "正在加载 Mod 列表")
                }
            } else {
                // PanEmpty：MyCard（Center/Center，Margin=40）+ 标题 + 细线 + 说明 + 三个按钮
                PclEmptyState(
                    title = "尚未安装 Mod",
                    content = "你可以下载新的 Mod，或者从已经下载好的文件安装 Mod。",
                    modifier = Modifier.padding(40.dp),
                    buttonText = "下载新 Mod",
                    onButtonClick = { nav.go(PclSection.Download) },
                    // PageInstanceMod 的说明是 Margin="10,10,10,0"，按钮行 Margin="15,20,0,5"
                    contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 0.dp),
                    buttonsTopPadding = 20.dp,
                    moreButtons = listOf(
                        PclEmptyButton("从文件安装") { installPicker.launch("application/java-archive") },
                        PclEmptyButton("打开文件夹") { openFolder(context, modsDir) },
                    ),
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
    PclContent(
        // PageInstanceMod.xaml:16 —— PanMain Margin="25,10,25,10"（上边距是 10，不是 25）
        contentPadding = PaddingValues(start = 25.dp, top = 10.dp, end = 25.dp, bottom = 10.dp),
    ) {
        PclSearchBox(
            value = keyword,
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            hint = "搜索 Mod 名称 / 描述 / 标签",
            onValueChange = { keyword = it },
        )

        // ---- 管理按钮卡
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            // PanManage 内层 Grid Margin="15,15,0,15" Height="35"
            contentPadding = PaddingValues(15.dp, 15.dp, 0.dp, 15.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                PclManageButton("打开文件夹", highlight = true) { openFolder(context, modsDir) }
                PclManageButton("从文件安装") { installPicker.launch("application/java-archive") }
                PclManageButton("下载新 Mod") { nav.go(PclSection.Download) }
                // PageInstanceMod.xaml.vb:270 —— 「全选」的文字永不变化，点一下在
                // 「全选中」与「全不选」之间切换（ChangeAllSelected(SelectedMods.Count < 列表数)），
                // 选中后的操作都放在底部悬浮条上。
                PclManageButton("全选", highlight = selected.isNotEmpty()) {
                    val selectAll = selected.size < visible.size
                    selected = if (selectAll) visible.map { it.file.absolutePath }.toSet() else emptySet()
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 列表卡（PCL 的标题栏是一条空标题）
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = " ",
            contentPadding = PaddingValues(start = 15.dp, top = 13.dp, end = 18.dp, bottom = 22.dp),
        ) {
            // PanFilter：Margin="15,13,0,0" Height="28"，胶囊之间 Margin="2,0"（→ 4）
            Row(
                modifier = Modifier.height(28.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // PageInstanceMod.xaml.vb:174-180 —— 文案是「{名称} ({数量})」，半角括号。
                //
                // 与原版的差异：**三枚胶囊常驻**。原版是「数量为 0 就收起」
                // （`Visibility = If(Filter = 已启用 OrElse (已启用数 > 0 AndAlso < 总数), …)`，
                // 已禁用同理），但这样一来「全部 Mod 都启用着」的机器上根本没有入口去切到
                // 「已禁用」这个视图 —— 用户看不到那两枚胶囊，更没法确认某个 Mod 到底禁用没。
                // 计数为 0 的胶囊点进去只会看到一句「没有符合当前筛选条件的 Mod」，无害，
                // 于是改成常驻，同时保留原版「当前筛选一定可见」的那半条规则。
                val pills = buildList {
                    add((if (searching) "搜索结果" else "全部") to 0)
                    add("已启用" to 1)
                    add("已禁用" to 2)
                }
                pills.forEach { (label, index) ->
                    PclPillButton(
                        text = "$label (${if (index == 0) anyCount else if (index == 1) enabledCount else disabledCount})",
                        checked = filter == index,
                        highlight = true,
                        onClick = { filter = index },
                    )
                }
            }
            // PanList：Margin="20,48,18,22" → 胶囊行（13+28）之后还有 7
            Spacer(Modifier.height(7.dp))

            when {
                visible.isEmpty() -> PclText(
                    text = "没有符合当前筛选条件的 Mod。",
                    color = p.gray2,
                    modifier = Modifier.padding(vertical = 6.dp),
                )

                else -> visible.forEach { mod ->
                    val enabled = mod.file.isEnabled()
                    val icon = modIcons[mod.file.absolutePath]
                    // MyLocalModItem：Height="44"；条目一直被当作可勾选项
                    // （PCL 没有「批量模式」——点一下条目就是勾选，底部栏随选中数出现）。
                    PclListItem(
                        title = mod.name,
                        info = buildString {
                            mod.version?.let { append(it) }
                            if (mod.id.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(mod.id)
                            }
                            mod.description?.take(70)?.takeIf { it.isNotBlank() }?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                            if (!enabled) append(" · 已禁用")
                        },
                        // MyLocalModItem 的主 Logo：有就画模组自带的图标，
                        // 没有就退回 PCL 的 `Icons/NoIcon.png` 占位；
                        // 禁用状态在右下角叠一个 `Icons/Disabled.png`（PCL 的 ImgState）。
                        logoContent = {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (icon != null) {
                                    Image(
                                        bitmap = icon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(R.drawable.pcl_noicon),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                if (!enabled) {
                                    Image(
                                        painter = painterResource(R.drawable.pcl_mod_disabled),
                                        contentDescription = "已禁用",
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp),
                                    )
                                }
                            }
                        },
                        type = PclListItemType.CheckBox,
                        checked = mod.file.absolutePath in selected,
                        height = 44.dp,
                        buttons = listOf(
                            PclListItemButton(
                                // PageInstanceMod.xaml.vb:123 —— 图标跟着状态走：
                                //   `Logo = If(Entry.IsEnabled, Logo.IconButtonStop, Logo.IconButtonCheck)`，
                                // 即**启用中显示「停用」（圆圈横线）**、**已禁用显示对勾**。
                                // 原来恒用 reset（循环箭头），光看图标分不出这一项现在是什么状态。
                                logo = if (enabled) PclIcons.selectDisable else PclIcons.selectEnable,
                                tooltip = if (enabled) "禁用" else "启用",
                            ) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        runCatching { if (enabled) mod.disable() else mod.enable() }
                                    }
                                    token++
                                }
                            },
                            PclListItemButton(PclIcons.delete, "删除") { deleting = mod },
                        ),
                        onClick = {
                            val path = mod.file.absolutePath
                            selected = if (path in selected) selected - path else selected + path
                        },
                    )
                }
            }
        }
        // PageInstanceMod.xaml.vb:207 —— 底部栏出现时列表卡的下边距要给到 95
        Spacer(Modifier.height(if (selected.isEmpty()) PclDimens.cardGap else 95.dp))
    }

    // PCL 的 CardSelect：底部居中的悬浮卡（已选择 N 个文件 + 操作胶囊）。
    // 它和内容区是兄弟节点，因此这里用 Box 叠在滚动区之上（对应 XAML 里的 Grid 兄弟）。
    PclModSelectBar(
        selected = visible.count { it.file.absolutePath in selected },
        hasEnabled = visible.any { it.file.absolutePath in selected && it.file.isEnabled() },
        hasDisabled = visible.any { it.file.absolutePath in selected && !it.file.isEnabled() },
        onEnable = { applyMods { runCatching { it.enable() } } },
        onDisable = { applyMods { runCatching { it.disable() } } },
        onDelete = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    selected.forEach { path ->
                        mods.firstOrNull { it.file.absolutePath == path }?.file?.delete()
                    }
                }
                PclHintHost.push("已删除所选 Mod。", PclHintTheme.Green)
                selected = emptySet()
                    token++
            }
        },
        onCancel = {
            selected = emptySet()
        },
    )
    }

    deleting?.let { mod ->
        PclConfirmDialog(
            title = "删除 Mod",
            content = "确定要删除「${mod.name}」吗？此操作不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { mod.file.delete() } }
                    token++
                }
            },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * 实例没有 Mod 加载器时的 Mod 管理页 —— PCL 的 `PageInstanceModDisabled`（独立一整页）。
 *
 * ```
 * MyPageRight
 *   MyCard  HorizontalAlignment=Center VerticalAlignment=Center Margin=40
 *     Grid Margin="20,17"
 *       TextBlock  居中  FontSize=19  Foreground=ColorBrush3  Margin="0,0,0,9"   ← 标题
 *       Rectangle  Height=2  Fill=ColorBrush3                                   ← 主题色细线
 *       TextBlock  Margin="10,15,10,5"  TextWrapping=Wrap                       ← 正文
 *       MyButton   高 35  MinWidth=140  Margin="10,10,10,0"  Padding="13,0"
 *          「转到下载页面」（Highlight，Column 1）/「版本选择」（Column 2）
 * ```
 *
 * 这张卡的版式与 `PanEmpty` 一模一样（同样的 `20,17` 内边距、19 号 ColorBrush3 标题、
 * 2dp 细线、MinWidth 140 的按钮、正文 `10,15,10,5`、按钮行上距 10），
 * 所以直接复用 [PclEmptyState]，不再单独拼一遍。
 */
@Composable
private fun PclInstanceModUnavailablePage() {
    val nav = LocalPclNav.current
    // 原版这一页的根节点是 `MyPageRight`（`PageInstanceModDisabled.xaml` 第一行），
    // 卡片自己 Center 居中、页面没有 ScrollViewer —— 所以用「居中不滚动」的版本，
    // 它带的是与其它页面同一套入场动画（从上方 -16 滑入 + 淡入）。
    PclCenteredPage {
        PclEmptyState(
            // 文案取自 PCL 的 LangPageVersionModDisabledTitle / Content / Btn*，
            // 只把「版本」换成 z-pcl 里一贯的「实例」（与截图里那份一致）。
            title = "该实例不可使用模组",
            content = "你需要先安装 Forge、Fabric 等模组加载器才能使用模组，" +
                    "请在下载页面安装这些实例。\n" +
                    "如果你已经安装过了模组加载器，那么你很可能选择了错误的实例，" +
                    "请点击实例选择按钮切换实例。",
            modifier = Modifier.padding(40.dp),
            buttonText = "转到下载页面",
            // PageInstanceModDisabled.xaml.vb:4 —— 跳 Download 的安装子页，
            // 也就是本文件里「下载新 Mod」走的同一条路。
            onButtonClick = { nav.go(PclSection.Download) },
            moreButtons = listOf(
                // PageInstanceModDisabled.xaml.vb:8 —— 原版先锚定 Launch 再进版本选择，
                // 是为了让「选定版本」只回退一层。这里是 push，返回即回到这张卡，效果相同。
                PclEmptyButton("实例选择") { nav.push(pclVersionSelectPage()) },
            ),
        )
    }
}

/**
 * 实例是否装了 Mod 加载器（PCL 的 `McInstance.Modable`，`ModMinecraft.vb:303`）。
 *
 * 原版判定是 `HasFabric OrElse HasForgelike OrElse HasLiteLoader OrElse DisplayType = API`：
 * Fabric 系（Fabric / Quilt / Legacy Fabric / Babric）、Forge 系（Forge / NeoForge / Cleanroom）
 * 与 LiteLoader。落到 Zalith 上正好就是 `ModLoader.isLoader` 为真的那几个 ——
 * `OPTIFINE` 和一堆 `*_API` 的 `isLoader` 都是 false，与原版把它们排除在外一致。
 */
private fun Version.hasModLoader(): Boolean =
    getVersionInfo()?.loaderInfos?.any { it.loader.isLoader } == true

/**
 * `PageInstanceMod.CardSelect` —— 选中若干 Mod 之后从底部浮出的操作条。
 *
 * ```
 * MyCard  HorizontalAlignment=Center VerticalAlignment=Bottom Margin="25,25,25,0"
 *   TextBlock LabSelect  Margin="9"  居中  Foreground=ColorBrush2  「已选择 {0} 个文件」
 *   StackPanel  Margin="5,28,5,5"  →  更新 / 启用 / 禁用 / 删除 / 取消选择（MyIconTextButton，27 高的胶囊）
 * ```
 *
 * 差异：「更新」这一项没有做 —— `LocalMod` 里没有远端更新信息（PCL 的 `HasUpdate` 来自模组平台）。
 */
@Composable
private fun PclModSelectBar(
    selected: Int,
    hasEnabled: Boolean,
    hasDisabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val p = pclPalette
    val shown = selected > 0
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "pclModSelectBar")
    if (appear <= 0.01f) return

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        PclCard(
            modifier = Modifier
                .padding(start = 25.dp, end = 25.dp, bottom = 25.dp)
                .graphicsLayer {
                    alpha = appear
                    translationY = (1f - appear) * (-10).dp.toPx()
                },
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
        ) {
            PclText(
                text = "已选择 $selected 个文件",
                color = p.color2,
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 5.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                PclPillButton(text = "启用", logo = PclIcons.selectEnable, logoScale = 1.05f, highlight = true, enabled = hasDisabled, onClick = onEnable)
                Spacer(Modifier.width(8.dp))
                PclPillButton(text = "禁用", logo = PclIcons.selectDisable, highlight = true, enabled = hasEnabled, onClick = onDisable)
                Spacer(Modifier.width(8.dp))
                PclPillButton(text = "删除", logo = PclIcons.delete, logoScale = 0.96f, highlight = true, onClick = onDelete)
                Spacer(Modifier.width(8.dp))
                PclPillButton(text = "取消选择", logo = PclIcons.selectCancel, logoScale = 0.8f, highlight = true, onClick = onCancel)
            }
        }
    }
}

/** Mod 管理页的按钮：PCL `MinWidth="110"`、`Padding="13,0"`、右间距 15。 */
@Composable
internal fun PclManageButton(
    text: String,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    PclButton(
        text = text,
        colorState = if (highlight) PclColorState.Highlight else PclColorState.Normal,
        contentPadding = PaddingValues(horizontal = 13.dp),
        modifier = Modifier.widthIn(min = 110.dp),
        onClick = onClick,
    )
}


/* ============================================================================
 *  版本选择（PageSelectLeft + PageSelectRight）
 * ==========================================================================*/

/**
 * 「版本选择」二级页面（`PageType.InstanceSelect`）。
 *
 * 这里曾经有一个 `PclSelectNav`（左栏选中项的 index），但它是一段死代码：
 * 左栏点击时写入 `index`，右栏却从不读它 —— 右栏的版本列表来自
 * `VersionsManager.versions`，本身就跟着「当前游戏目录」全局变化。
 * 留着它只会让人误以为「切换目录」是由那个 index 驱动的，故删除。
 * 选中态另有依据：`checked = path.path == GamePathManager.currentPath`（路径比较）。
 */
fun pclVersionSelectPage(): PclSubPage {
    return PclSubPage(
        title = PclStrings.LangLaunchLeftInstanceChoose,
        leftContent = { PclSelectLeft() },
        // PageSelectLeft.xaml:12 —— 左栏是 MyScrollViewer MinWidth="250" MaxWidth="300"，
        // 不按内容算；版本文件夹的名字再长也只到 300。
        leftWidth = 250.dp,
        content = { PclSelectRight() },
    )
}

/** 左栏分组标题：`Margin="13,18,5,4" Opacity="0.6" FontSize="12"`。 */
@Composable
private fun PclSelectGroupLabel(text: String) {
    PclText(
        text = text,
        modifier = Modifier
            .padding(start = 13.dp, top = 18.dp, end = 5.dp, bottom = 4.dp)
            .graphicsLayer(alpha = 0.6f),
        fontSize = 12,
    )
}

/**
 * `PageSelectLeft` —— 版本文件夹列表。
 *
 * 结构逐项取自 `PageSelectLeft.xaml` + `.xaml.vb:29-115`：
 * ```
 * 标题 LangSelectFolderList    Margin="13,18,5,4" Opacity="0.6" FontSize="12"
 * 文件夹条目                   Height="40" Type=RadioBox MinPaddingRight="30"
 *                             ★ 不设 Logo（所以没有 34 宽的图标列）
 *                             Buttons = [ IconButtonSetup LogoScale=1.1 ] → 右键菜单（重命名/打开/刷新/删除）
 * 标题 LangSelectAddFolder     Margin="13,18,5,4" Opacity="0.6" FontSize="12"
 * 新建 .minecraft 文件夹         Height="34" Type=Clickable LogoScale=0.9（.minecraft 不存在时才出现）
 * 添加已有文件夹                Height="34" Type=Clickable
 * 导入整合包                   Height="34" Type=Clickable
 * 末尾 10 高占位
 * ```
 *
 * Android 侧的对应关系：
 *   · 「新建 .minecraft 文件夹」→ `GamePathManager.saveDefaultPath()`，
 *     显示条件用「默认目录还没登记」代替 PCL 的 `If Not DirectoryUtils.Exists(...)`；
 *   · 「添加已有文件夹」→ 系统目录选择器（SAF）拿到路径后先问显示名再 `addNewPath()`，
 *     与原生界面 `GamePathOperation.AddNewPath` + `NameEditPathDialog` 是同一套行为；
 *   · 「导入整合包」→ 本地压缩包交给 `ModpackImporter`，与下载页那条导入链同一个实现。
 *
 * ⚠️ 关于「外部存储权限」（这一页曾经因此整片失效）：
 *
 * `GamePathManager.saveCurrentPath()` 的第一行就是
 * `if (canHandlePermission && !hasStoragePermission) throw IllegalStateException(...)`，
 * 而自定义游戏目录都在外部存储上，没开「管理设备所有文件」时这个异常**必然**抛出。
 * 原先这里写成 `runCatching { saveCurrentPath(path.id) }` —— 空手接住了异常，
 * 于是点击**完全没有任何反应**（不切换、不报错、选中态不动），看起来就是
 * 「自定义目录不能选择与切换」。更糟的是 `reloadPath()` 里有同一个判断，
 * 每次刷新都会把当前目录重置回默认值，所以自定义目录连「切过去」都保不住。
 *
 * 原生界面（`VersionsManageScreen.kt:385-400`）的做法是**点之前先走一遍权限引导**：
 * 默认目录直接 `saveDefaultPath()`（不需要权限），自定义目录先
 * `checkStoragePermissions(hasPermission = { saveCurrentPath(id) })`。这里照搬。
 */
@Composable
private fun PclSelectLeft() {
    val context = LocalContext.current
    val runtime = LocalPclRuntime.current
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val paths by GamePathManager.gamePathData.collectAsStateWithLifecycle()
    // ⚠️ 这里拿的是**当前游戏目录的路径字符串**（`GamePathManager._currentPath`），
    // 不是目录的 id。原来的变量名写成 `currentPathId` 并且拿去和 `path.id` 比较，
    // 于是 "default"/UUID 永远不可能等于 "/storage/emulated/0/.minecraft" ——
    // 所有目录条目恒为未选中，用户看到的「选不了、切不了」有一半就是这个。
    // 原生 `VersionsManageScreen.kt:383` 用的也是路径比较：`currentPath == pathItem.path`。
    val currentPath by GamePathManager.currentPath.collectAsStateWithLifecycle()
    // 待删除的游戏目录（确认弹窗用）
    var deletingPath by remember { mutableStateOf<com.zalith.pcl.game.path.GamePath?>(null) }

    // PCL 风格的存储权限引导。
    // 原生 `checkStoragePermissions()` 弹的是 MaterialAlertDialog —— 大圆角、绿字按钮、
    // 与这一页的 PCL 控件完全不搭（第 114 轮用户反馈）。改成自己的 PclActionDialog（忽略 / 授权）。
    var storagePromptMessage by remember { mutableStateOf<String?>(null) }
    var storageIgnoreAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** 权限提示文案：Android 11 起要的是「管理设备所有文件」，更低版本是普通存储权限。 */
    fun storagePermissionMessage(sdk30: Int, legacy: Int): String =
        context.getString(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) sdk30 else legacy)

    /** 去系统里开权限：11+ 跳「所有文件访问」设置页，10 及以下申请运行时权限。 */
    fun openStoragePermissionSettings() {
        val act = activity ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { act.startActivity(getPermissionSettingsAndroid11AndAbove(act)) }
                .onFailure {
                    PclHintHost.push(
                        "打不开权限设置页，请手动到系统设置 → 应用 → Zalith-Plain Craft Launcher 里开启「管理设备所有文件」。",
                        PclHintTheme.Red,
                    )
                }
        } else {
            ActivityCompat.requestPermissions(
                act,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
                0,
            )
        }
    }

    /**
     * 动外部存储之前先过这一关。
     *
     * @return true = 已经有权限，调用方接着干活；false = 已经弹了引导框，这次什么都不做。
     * @param onIgnore 用户点「忽略」时执行（一般是把 `pendingSwitch` 之类的待办清掉）。
     */
    fun ensureStoragePermission(message: String, onIgnore: () -> Unit = {}): Boolean {
        // 用户可能在系统设置里刚授过权，每次现查一遍，别用启动时缓存的布尔值
        checkStoragePermissionsForInit(context)
        if (hasStoragePermission) return true
        storagePromptMessage = message
        storageIgnoreAction = onIgnore
        return false
    }

    // 是不是「默认目录」。
    // 默认目录在 `PathManager.DIR_FILES_EXTERNAL` 下，属于应用自己的数据目录，
    // 不需要「管理设备所有文件」权限就能读写；其它目录都在外部存储上，需要。
    // 除了按 id 判，也顺手按路径判一次 —— 免得出现「id 不是 default 但路径就是默认目录」
    // 这种登记方式时走错分支。
    fun isDefaultFolder(path: com.zalith.pcl.game.path.GamePath): Boolean =
        path.id == GamePathManager.DEFAULT_ID || path.path == GamePathManager.getDefaultPath()

    // 真正执行切换。
    //
    // 默认目录走 `saveDefaultPath()`（原生同款，不需要任何权限）；其余目录走
    // `saveCurrentPath(id)`，它内部会顺手 `VersionsManager.refresh()` 刷新右栏。
    // 失败时**必须**说话：原来静默吞异常，用户只能看到「点了没用」。
    fun switchTo(path: com.zalith.pcl.game.path.GamePath) {
        runCatching {
            if (isDefaultFolder(path)) {
                GamePathManager.saveDefaultPath()
            } else {
                GamePathManager.saveCurrentPath(path.id)
            }
        }.onFailure { th ->
            // `saveCurrentPath` 只有两种失败：`IllegalStateException`（没权限）与
            // `IllegalArgumentException`（列表里找不到这个 id）。前者给个人话版本。
            val reason = if (th is IllegalStateException) {
                "没有外部存储权限，请在系统设置里为启动器开启「管理设备所有文件」。"
            } else {
                th.message ?: th.javaClass.simpleName
            }
            PclHintHost.push("切换游戏目录失败：$reason", PclHintTheme.Red)
        }
    }

    // 被引导去系统设置开权限的那个目标目录。
    // Android 11+ 的「管理设备所有文件」是跳设置页（`startActivityForResult`），
    // `checkStoragePermissions` 没有处理返回值，所以用户授权回来之后不会自动续上。
    // 把目标记下来，在这一页重新可见时补一次 —— 否则用户会以为「点了授权还是没反应」。
    // 用户点「忽略」时会在 onDialogCancel 里清掉，不会偷偷切过去。
    var pendingSwitch by remember { mutableStateOf<com.zalith.pcl.game.path.GamePath?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val target = pendingSwitch ?: return@LifecycleEventObserver
            pendingSwitch = null
            // 这里**直接**尝试切换，不再走一次 `checkStoragePermissions`：
            // `saveCurrentPath` 自带权限检查，拿到了就切、没拿到就抛，
            // 由 `switchTo` 给一句人话提示。若再走一遍权限引导，用户没开权限时
            // 每次回到这一页都会被弹一次对话框，非常烦。
            switchTo(target)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 「添加已有文件夹」：选完目录先问显示名（对应原生界面的 NameEditPathDialog）
    var pendingPickedPath by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            when (val realPath = treeUriToPath(uri)) {
                null -> PclHintHost.push("无法识别所选文件夹的路径，请换一个位置重试。", PclHintTheme.Red)
                else -> if (GamePathManager.containsPath(realPath)) {
                    PclHintHost.push("该文件夹已经在列表里了。", PclHintTheme.Blue)
                } else {
                    pendingPickedPath = realPath
                }
            }
        }
    }

    /** 点「添加已有文件夹」也是同一个门槛：没权限时 `addNewPath` 存下来的目录会被 `reloadPath` 丢弃。 */
    fun pickFolder() {
        if (activity == null) {
            folderPicker.launch(null)
        } else {
            val message = storagePermissionMessage(
                R.string.versions_manage_game_path_storage_permissions_sdk30,
                R.string.versions_manage_game_path_storage_permissions,
            )
            if (ensureStoragePermission(message)) {
                folderPicker.launch(null)
            }
        }
    }

    // 「导入整合包」：本地压缩包 → ModpackImporter
    var importer by remember { mutableStateOf<ModpackImporter?>(null) }
    val modpackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val newImporter = ModpackImporter(
                    context = context,
                    uri = uri,
                    // 与下载页那条链保持一致：导入挂在 Activity 上，切版块不会把它掐掉
                    scope = runtime.backgroundScope,
                    // PCL 的规矩：装整合包之前先问版本名，默认填整合包自己的名字。
                    // `pclAskVersionName` 负责「默认名先避让重名 → 用户可改 → 改完再兜一次
                    // sanitize/去重」，三条整合包链路共用同一套规则。
                    waitForVersionName = { packName ->
                        pclAskVersionName(packName, title = "设置版本名称")
                    },
                    waitForConfirmMobileData = { true },
                )
                importer = newImporter
                // 这条链没有下载阶段，直接就是安装：登记后「下载管理」里同样能看到各阶段，
                // 关掉进度弹窗、离开这一页都不会让它从界面上消失。
                val importId = runtime.trackModpackImport("导入整合包", importer = newImporter)
                // 与下载页那条链同规矩：`ModpackImporter` 共用写死的 `cache/temp_modpack`
                // 且开工前会把它整个删掉，两个整合包同时装必然互相踩 —— 这里在安装锁上排队。
                runtime.backgroundScope.launch {
                    runtime.modpackInstallLock.withLock {
                        // 见 `startModpackImport`：前面那条可能是被取消的，`job.cancel()` 之后
                        // 它还要一个调度点才真停，缓冲一下免得撞上它正在写的 `temp_modpack`。
                        delay(400)
                        // 排队期间可能已经被用户从「下载管理」取消了，拿到锁后再确认一次。
                        if (!runtime.isModpackImportTracked(importId)) return@withLock
                        newImporter.startImport(
                            onFinished = { name ->
                                importer = null
                                runtime.clearModpackImport(importId)
                                PclHintHost.push("整合包「$name」安装完成。", PclHintTheme.Green)
                                runtime.refreshVersions()
                            },
                            onCancelled = {
                                importer = null
                                runtime.clearModpackImport(importId)
                                if (pclDiscardIncompleteVersion(newImporter.resolvedVersionNameOrNull())) {
                                    runtime.refreshVersions()
                                }
                            },
                            onError = { th ->
                                importer = null
                                runtime.clearModpackImport(importId)
                                // 主动取消会以 CancellationException 落到这里，那不是故障。
                                if (th !is CancellationException) {
                                    // 装到一半失败时把这次建出来的版本目录扫掉：`GameInstaller`
                                    // 只在 `cancelInstall()` 里清目标目录，任务流因异常结束时是不清的，
                                    // 留下的半成品会一直占着那个版本名（正是「导入永远失败」的来源之一）。
                                    if (pclDiscardIncompleteVersion(newImporter.resolvedVersionNameOrNull())) {
                                        runtime.refreshVersions()
                                    }
                                    runtime.submitErrorText("整合包导入失败", modpackImportErrorText(th))
                                }
                            },
                        )
                        // 结束信号看任务表（所有出口都会清），不等导入器的回调 —— 见
                        // `PclRuntime.modpackTaskIds` 上的说明。
                        runtime.modpackTaskIds.first { importId !in it }
                    }
                }
            }.onFailure { th ->
                importer = null
                runtime.clearModpackImport()
                runtime.submitErrorText("整合包导入失败", modpackImportErrorText(th))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // MyPageLeft.TriggerShowAnimation：按顺序逐个入场。
        // 序号与 PCL 的 GetAllAnimControls 顺序一致（分组标题也占一位）。
        // 注意 PclSelectGroupLabel 自己已经带了 0.6 的 Opacity，这里 labelAlpha 传 1，避免叠乘。
        PclStaggerIn(0) { PclSelectGroupLabel(PclStrings.LangSelectFolderList) }

        paths.forEachIndexed { index, path ->
            PclStaggerIn(1 + index) {
                PclListItem(
                    title = path.title.ifBlank { "游戏目录" },
                    info = path.path,
                    type = PclListItemType.RadioBox,
                    checked = path.path == currentPath,
                    height = 40.dp,
                    minPaddingRight = 30.dp,
                    buttons = listOf(
                        PclListItemButton(
                            logo = PclIcons.setupGear,
                            tooltip = PclStrings.LangSelectBtnOpenFolder,
                            logoScale = 1.1f,
                        ) { openFolder(context, File(path.path)) },
                        // PCL 的目录条目右键菜单里有「删除」
                        PclListItemButton(
                            logo = PclIcons.delete,
                            tooltip = PclStrings.LangSelectBtnDeleteFolder,
                            logoScale = 0.96f,
                        ) { deletingPath = path },
                    ),
                    onClick = {
                        // 默认目录不需要外部存储权限，直接切（原生 `VersionsManageScreen:387` 同款）；
                        // 其余目录在外部存储上，先过一遍权限引导，授权之后再执行切换。
                        if (isDefaultFolder(path) || activity == null) {
                            switchTo(path)
                        } else {
                            pendingSwitch = path
                            val message = storagePermissionMessage(
                                R.string.versions_manage_game_storage_permissions_sdk30,
                                R.string.versions_manage_game_storage_permissions,
                            )
                            // 有权限就当场切；没有就由下面的弹窗引导，用户授权回来由 ON_RESUME 续上
                            if (ensureStoragePermission(message) { pendingSwitch = null }) {
                                pendingSwitch = null
                                switchTo(path)
                            }
                        }
                    },
                )
            }
        }

        if (paths.isEmpty()) {
            // PCL 在这种情况下是抛异常写日志（`LangSelectNoMCFolder`），界面上不留文案；
            // 手机上没有任何目录时列表会空得莫名其妙，因此把 PCL 这条文案显示出来。
            PclText(
                text = PclStrings.LangSelectNoMCFolder,
                color = pclPalette.gray2,
                modifier = Modifier.padding(12.dp),
            )
        }

        val base = 1 + paths.size
        Spacer(Modifier.height(10.dp))
        PclStaggerIn(base) {
            PclSelectGroupLabel(PclStrings.LangSelectAddFolder)
        }

        // PCL：`.minecraft` 不存在时才显示「新建 .minecraft 文件夹」（这里=默认目录还没登记）
        var nextIndex = base + 1
        if (!GamePathManager.containsId(GamePathManager.DEFAULT_ID)) {
            PclStaggerIn(nextIndex++) {
                PclListItem(
                    title = PclStrings.LangSelectCreateFolder,
                    logo = PclIcons.selectCreateFolder,
                    logoScale = 0.9f,
                    type = PclListItemType.Clickable,
                    height = 34.dp,
                    onClick = {
                        runCatching { GamePathManager.saveDefaultPath() }
                            .onSuccess { PclHintHost.push("已创建默认游戏文件夹并切换过去。", PclHintTheme.Green) }
                            .onFailure {
                                runtime.submitErrorText("创建失败", it.message ?: it.javaClass.simpleName)
                            }
                    },
                )
            }
        }

        PclStaggerIn(nextIndex++) {
            PclListItem(
                title = PclStrings.LangSelectAddExistFolder,
                logo = PclIcons.selectAddExistFolder,
                type = PclListItemType.Clickable,
                height = 34.dp,
                onClick = { pickFolder() },
            )
        }

        PclStaggerIn(nextIndex) {
            PclListItem(
                title = PclStrings.LangSelectAddModpack,
                logo = PclIcons.selectAddModpack,
                type = PclListItemType.Clickable,
                height = 34.dp,
                onClick = { modpackPicker.launch("application/zip") },
            )
        }
        Spacer(Modifier.height(10.dp))
    }

    // 添加文件夹：问显示名（PCL 的 LangSelectDialogInputNameTitle）
    pendingPickedPath?.let { path ->
        PclInputDialog(
            title = "添加文件夹",
            label = "文件夹显示名称",
            initial = path.trimEnd('/').substringAfterLast('/'),
            onConfirm = { name ->
                val title = name.ifBlank { path.trimEnd('/').substringAfterLast('/') }
                runCatching { GamePathManager.addNewPath(title = title, path = path) }
                    .onSuccess { PclHintHost.push("已添加文件夹「$title」", PclHintTheme.Green) }
                    .onFailure { runtime.submitErrorText("添加文件夹失败", it.message ?: it.javaClass.simpleName) }
                pendingPickedPath = null
            },
            onDismiss = { pendingPickedPath = null },
        )
    }

    // 导入整合包的进度（与下载页共用同一个弹窗组件）
    importer?.let { imp ->
        PclTitledTaskDialog(
            title = "正在导入整合包",
            tasks = imp.taskFlow,
            // 「取消」是红按钮，必须真的停掉任务流；原来只把弹窗收起来，安装还在后台跑
            onCancel = { runCatching { imp.cancel() }; importer = null },
            onDismiss = { importer = null },
        )
    }

    // 删除已添加的游戏目录（只从列表移除，不动磁盘）
    deletingPath?.let { target ->
        PclConfirmDialog(
            title = PclStrings.LangSelectDeleteFolderTitle,
            content = PclStrings.LangSelectDeleteFolderContent.replace(
                "%1",
                target.title.ifBlank { target.path },
            ),
            confirmText = PclStrings.LangSelectDeleteFolderConfirm,
            danger = true,
            onConfirm = {
                runCatching {
                    // 删的正好是当前目录时先切回默认目录，免得留一个指向已删目录的选中项
                    if (target.path == currentPath) GamePathManager.saveDefaultPath()
                    GamePathManager.removePath(target)
                }
                PclHintHost.push(PclStrings.LangSelectDeleteFolderHint, PclHintTheme.Green)
                deletingPath = null
            },
            onDismiss = { deletingPath = null },
        )
    }

    // 存储权限引导（PCL 样式）。替换掉原生 `checkStoragePermissions` 那套 MaterialAlertDialog。
    storagePromptMessage?.let { message ->
        fun finishPrompt() {
            storagePromptMessage = null
            storageIgnoreAction = null
        }
        PclActionDialog(
            title = "警告",
            content = message,
            secondaryText = "忽略",
            onSecondary = {
                val ignore = storageIgnoreAction
                finishPrompt()
                ignore?.invoke()
            },
            primaryText = "授权",
            onPrimary = {
                finishPrompt()
                openStoragePermissionSettings()
            },
            // 点遮罩 / 返回键也算「忽略」：待办不清掉的话，下次回到这一页会自己执行
            onDismiss = {
                val ignore = storageIgnoreAction
                finishPrompt()
                ignore?.invoke()
            },
        )
    }
}

/**
 * 把 SAF 选中的目录转成真实文件路径。
 *
 * Zalith 的游戏目录（`GamePath`）存的是文件系统路径而不是 Uri，所以必须换算一次；
 * `DocumentsContract.getTreeDocumentId` 对主存储返回 `primary:相对路径`，
 * 对 SD 卡等其它卷返回 `卷ID:相对路径`。
 */
private fun treeUriToPath(uri: Uri): String? = runCatching {
    val docId = DocumentsContract.getTreeDocumentId(uri)
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

/* ------------------------------------------------------------------ 版本列表分组 */

/**
 * 版本列表的卡片分组。
 *
 * PCL 的 `McInstanceCardType` 共 11 种，卡片顺序见 `ModMinecraft.vb:1566`：
 * `Star, API, OriginalLike, Rubbish, Fool, Error, Hidden`。
 *
 * 「不常用版本 / 愚人节版本 / 隐藏的版本」依赖 `McInstanceState`（旧版本、快照、愚人节）
 * 以及用户手工写进 `Setup.ini` 的 `DisplayType`；Zalith 的 `VersionInfo` 只提供
 * MC 版本号与加载器信息，拿不到这些判据，所以这里只还原能真实判定的四类，不臆造分组。
 */
private enum class PclVersionCard(val label: String, val collapsedByDefault: Boolean = false) {
    /** `LangSelectVersionTypeFavorites`。 */
    Star(PclStrings.LangSelectVersionTypeFavorites),

    /**
     * `LangSelectVersionTypeModAbility`。
     * `ModMinecraft.vb:1482` 把所有 `Forge / NeoForge / LiteLoader / Fabric` 一并归到这张卡；
     * 单独的「Forge 版本」等卡片只在用户手工指定 `DisplayType` 时才会出现。
     */
    Modded(PclStrings.LangSelectVersionTypeModAbility),

    /** `LangSelectVersionTypeRegular`。 */
    Vanilla(PclStrings.LangSelectVersionTypeRegular),

    /** `LangSelectVersionTypeNotCommonlyUsed`，默认折叠（`PageSelectRight.xaml.vb:82`）。 */
    Uncommon(PclStrings.LangSelectVersionTypeNotCommonlyUsed, collapsedByDefault = true),

    /** `LangSelectVersionTypeAprilFool`，默认折叠（同上）。 */
    AprilFool(PclStrings.LangSelectVersionTypeAprilFool, collapsedByDefault = true),

    /** `LangSelectVersionTypeError`，默认折叠（`PageSelectRight.xaml.vb:82`）。 */
    Error(PclStrings.LangSelectVersionTypeError, collapsedByDefault = true),

    /** `LangSelectVersionTypeHidden` —— 概览页「分类」里手工设为「从版本列表中隐藏」的版本。 */
    Hidden(PclStrings.LangSelectVersionTypeHidden);

    /** `PageSelectRight.xaml.vb:75` —— 收藏夹不显示数量，其余卡片追加 `" (n)"`。 */
    fun cardTitle(count: Int): String = if (this == Star) label else "$label ($count)"
}

/**
 * 判定一个版本属于哪张卡片。
 * 顺序照抄 `ModMinecraft.vb:1469-1482`：**先挑收藏，再挑错误**（收藏的坏版本仍进收藏夹）。
 */
private fun Version.pclCardOf(): PclVersionCard {
    // 概览页「分类」里手工指定的分组优先（对应 PCL 的 McInstance.DisplayType）
    when (PclInstanceMeta.categoryOf(getVersionName())) {
        PclInstanceMeta.CATEGORY_HIDDEN -> return PclVersionCard.Hidden
        PclInstanceMeta.CATEGORY_MOD -> return PclVersionCard.Modded
        PclInstanceMeta.CATEGORY_REGULAR -> return PclVersionCard.Vanilla
        PclInstanceMeta.CATEGORY_UNCOMMON -> return PclVersionCard.Uncommon
        PclInstanceMeta.CATEGORY_FOOL -> return PclVersionCard.AprilFool
    }

    return when {
        pinnedState -> PclVersionCard.Star
        !isValid() -> PclVersionCard.Error
        else -> {
            val info = getVersionInfo()
            val modded = info != null && ModLoader.entries.any { it.isLoader && info.hasLoader(it) }
            if (modded) PclVersionCard.Modded else PclVersionCard.Vanilla
        }
    }
}

/** 单个版本条目：`McInstance.ToListItem`（ModMinecraft.vb:902）+ `McInstanceListContent`（PageSelectRight.xaml.vb:116）。 */
@Composable
private fun PclVersionRow(
    version: Version,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val info = version.getVersionInfo()
    val valid = version.isValid()
    // ToListItem：设置了自定义描述时，标题后以内联 Run 追加 "  |  " & VersionDisplayName()
    // （Foreground=MyColor(215,215,215)、FontSize=12）
    val customInfo = version.getCustomInfo()
    val titleSuffix = if (customInfo.isNotBlank() && info != null) {
        "  |  ${info.getInfoString()}"
    } else {
        null
    }

    PclListItem(
        title = version.getVersionName(),
        titleSuffix = titleSuffix,
        info = info?.getInfoString() ?: "未知版本",
        // ToListItem 的 Logo：版本自带图标（VersionIconImage 读 VersionIcon.png，缺失时回退加载器图标）
        // 用 fillMaxSize 而不是 fillMaxWidth：图标盒（PclListItem 里宽 34、高 = 项高）是**确定尺寸**的，
        // 只给宽度的话高度得由图片自身的固有比例算，加载中/加载失败时那个高度会是 0 —— 图标整块消失。
        // 撑满整盒 + ContentScale.Fit 等价于 PCL 里 `PathLogo` 的 `Stretch=Uniform`（也等价于原生界面的 Modifier.size）。
        logoContent = { VersionIconImage(version, Modifier.fillMaxSize()) },
        // ★ Type = Clickable，不是 RadioBox —— 所以没有左侧蓝条，选中也不变蓝
        type = PclListItemType.Clickable,
        height = 42.dp,
        buttons = buildList {
            add(
                PclListItemButton(
                    logo = if (version.pinnedState) PclIcons.likeFill else PclIcons.likeLine,
                    tooltip = if (version.pinnedState) {
                        PclStrings.LangSelectBtnCancelFavorite
                    } else {
                        PclStrings.LangSelectBtnFavorite
                    },
                    logoScale = 1.1f,
                    onClick = onTogglePin,
                ),
            )
            add(
                PclListItemButton(
                    logo = PclIcons.deleteSolid,
                    tooltip = PclStrings.LangSelectDelete,
                    logoScale = 1.1f,
                    onClick = onDelete,
                ),
            )
            // State = Error 时第三个按钮换成「打开文件夹」（LogoScale 1.15）
            if (valid) {
                add(
                    PclListItemButton(
                        logo = PclIcons.setupGear,
                        tooltip = PclStrings.LangSelectBtnSet,
                        logoScale = 1.1f,
                        onClick = onOpenSettings,
                    ),
                )
            } else {
                add(
                    PclListItemButton(
                        logo = PclIcons.openFolder,
                        tooltip = PclStrings.LangSelectBtnOpenFolder,
                        logoScale = 1.15f,
                        onClick = onOpenFolder,
                    ),
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * `PageSelectRight` —— 按类型分组的多张可折叠版本卡片。
 *
 * 布局取自 `PageSelectRight.xaml` + `.xaml.vb:28-91`：
 * ```
 * StackPanel PanMain Margin="25,25,25,10"          ← PclContent 的默认外边距
 * └ MyCard  Title="{名称} ({数量})"  Margin="0,0,0,15"  SwapType=0
 *    └ StackPanel Margin="20,40,18,0"              ← 内容容器，顶边距就是 MyCard.SwapedHeight(=40)
 * ```
 * 「不常用版本 / 愚人节版本 / 错误的版本」三张卡默认折叠；若只剩一张卡且它是折叠的则展开。
 *
 * 注：PCL 这个页面本身没有搜索框，这里保留搜索框作为手机上的必要补充（唯一一处增补）。
 */
@Composable
private fun PclSelectRight() {
    val nav = LocalPclNav.current
    val runtime = LocalPclRuntime.current
    val context = LocalContext.current
    val versions by runtime.versions.collectAsStateWithLifecycle()
    val refreshing by runtime.isRefreshingVersions.collectAsStateWithLifecycle()

    var keyword by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Version?>(null) }
    var renaming by remember { mutableStateOf<Version?>(null) }

    val grouped = remember(versions, keyword) {
        versions
            .filter { v ->
                keyword.isBlank() ||
                    v.getVersionName().contains(keyword, true) ||
                    (v.getVersionInfo()?.minecraftVersion?.contains(keyword, true) == true)
            }
            .groupBy { it.pclCardOf() }
    }
    val cards = PclVersionCard.entries.filter { grouped[it]?.isNotEmpty() == true }
    // PageSelectRight.xaml.vb:90 —— 若只剩一张卡片且它是折叠的，则强制展开
    val onlyCard = cards.singleOrNull()

    PclContent {
        PclSearchBox(
            value = keyword,
            modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
            hint = PclStrings.LangDownloadCompSearch,
            onValueChange = { keyword = it },
        )

        when {
            refreshing && versions.isEmpty() -> PclLoadingCard(
                text = PclStrings.LangDownloadCompLoadingList,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // `PageSelectRight.PanEmpty`：整页居中（水平居中交给这里的 align）
            cards.isEmpty() -> PclEmptyState(
                title = PclStrings.LangSelectNoAvailableVersion,
                content = if (keyword.isBlank()) {
                    PclStrings.LangSelectNoAvailableVersionTip
                } else {
                    "没有与「$keyword」匹配的版本。"
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                buttonText = PclStrings.LangSelectNoAvailableVersionDownloadGame,
                onButtonClick = { nav.go(PclSection.Download) },
            )

            else -> cards.forEach { card ->
                key(card) {
                    val items = grouped.getValue(card)
                    // 折叠状态提升到外面：分批组合要知道卡片当前是不是展开的。
                    // 初值仍与 `initiallyCollapsed` 一致（只在第一次组合时求值）。
                    val collapsed = remember(card) {
                        mutableStateOf(card.collapsedByDefault && onlyCard != card)
                    }
                    // 老版本 / 错误版本攒多了也会有上百行，和下载页的分组卡片一样要分批组合
                    val chunks = rememberPclRowChunks(total = items.size, collapsed = collapsed)
                    PclCard(
                        title = card.cardTitle(items.size),
                        modifier = Modifier.fillMaxWidth().padding(bottom = PclDimens.cardGap),
                        collapsible = true,
                        initiallyCollapsed = card.collapsedByDefault && onlyCard != card,
                        collapsedState = collapsed,
                        onToggle = chunks.onToggle,
                        // MyCard.SwapedHeight = 40；内容 StackPanel Margin="20,40,18,0"
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = 40.dp,
                            end = 18.dp,
                            bottom = 0.dp,
                        ),
                    ) {
                        for (i in 0 until chunks.shown) {
                            val version = items[i]
                            PclVersionRow(
                                version = version,
                                onClick = {
                                    if (version.isValid()) {
                                        // PageSelectRight.Item_Click：选中后返回上一页
                                        runtime.selectVersion(version)
                                        nav.back()
                                    } else {
                                        openFolder(context, version.getVersionPath())
                                    }
                                },
                                onTogglePin = { runtime.togglePinned(version) },
                                onDelete = { deleting = version },
                                onOpenSettings = {
                                    // PageInstanceLeft.Instance = Instance; PageChange(InstanceSetup, 0)
                                    runtime.selectVersion(version)
                                    nav.push(pclInstanceVersionPage())
                                },
                                onOpenFolder = { openFolder(context, version.getVersionPath()) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }

    renaming?.let { version ->
        PclInputDialog(
            title = "重命名版本",
            label = "新的版本名",
            initial = version.getVersionName(),
            onConfirm = { name ->
                if (name.isNotBlank()) runtime.renameVersion(version, name)
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { version ->
        PclConfirmDialog(
            title = "删除版本",
            content = "确定要删除「${version.getVersionName()}」吗？操作不可恢复。",
            confirmText = PclStrings.LangDialogBtnDelete,
            danger = true,
            onConfirm = { runtime.deleteVersion(version) },
            onDismiss = { deleting = null },
        )
    }
}
