/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 资源详情页（对应 PCL 的 `PageDownloadCompDetail` / `PageDownloadResourceDetail`）。
 *
 * 结构照抄 `PageDownloadCompDetail.xaml` + `.xaml.vb:43-250`：
 *   · `CardIntro`（`Margin="0,0,0,25"`，内容 `StackPanel Margin="22,18,22,19"`）：
 *     资源条目本体（`CanInteraction = False`）+ 一行 `MyButton`（高 35、MinWidth 140、右边距 20）；
 *   · `CardFilter`（`Margin="0,0,0,15"`，内容 `Margin="10,10,0,10"`）：
 *     一排 `MyRadioButton`（`Margin="2,0"`、`ColorType=Highlight`、首项是「全部」，
 *     少于 2 个筛选项时整张卡片隐藏）；
 *   · `PanResults`：每个游戏版本一张可折叠 `MyCard`（`Margin="0,0,0,15"`、
 *     内容 `Margin="20,40,18,0"`），卡片里是该版本下的文件列表；
 *     与所选版本匹配的卡片默认展开，只剩一张卡片时也展开。
 *   · 加载中：`PanLoad` 居中（`PclLoadingCard`）。
 *
 * 版本筛选按 PCL 的 `GetGroupedVersionName` 思路：筛选项少于 9 个时列出每个小版本，
 * 太多时折叠成大版本（1.20.1 → 1.20）；快照单独归到「快照版」。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.pages

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.zalith.pcl.game.download.assets.favorites.FavoriteProjectsRepository
import com.zalith.pcl.game.download.assets.platform.PlatformClasses
import com.zalith.pcl.game.download.assets.platform.PlatformDependencyType
import com.zalith.pcl.game.download.assets.platform.PlatformDisplayLabel
import com.zalith.pcl.game.download.assets.platform.PlatformSearchData
import com.zalith.pcl.game.download.assets.platform.PlatformVersion
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.download.assets.DependencyRequest
import com.zalith.pcl.game.download.assets.downloadDependenciesForVersions
import com.zalith.pcl.game.download.assets.downloadSingleForVersions
import com.zalith.pcl.game.download.assets.platform.getProjectByVersion
import com.zalith.pcl.game.download.assets.platform.getVersions
import com.zalith.pcl.ui.screens.content.download.assets.elements.initAll
import com.zalith.pcl.game.download.assets.utils.ModTranslations
import com.zalith.pcl.game.download.assets.utils.getMcmodTitle
import com.zalith.pcl.game.download.assets.utils.getTranslations
import com.zalith.pcl.game.download.modpack.install.ModpackImporter
import com.zalith.pcl.ui.pcl2.PclContent
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclStrings
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.PclTitledTaskDialog
import com.zalith.pcl.ui.pcl2.startModpackImport
import com.zalith.pcl.ui.pcl2.formatCount
import com.zalith.pcl.ui.pcl2.pclResourceIconFor
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclListItemType
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclLoadingCard
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclResourceItem
import com.zalith.pcl.ui.pcl2.pclVersionText
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.ui.toAndroidString
import com.zalith.pcl.utils.formatDate
import com.zalith.pcl.utils.getTimeAgo
import com.zalith.pcl.utils.network.openLinkInternal
import org.apache.commons.io.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import com.zalith.pcl.utils.file.formatFileSize

/** `PageDownloadResourceDetail` —— 资源详情页。 */
fun pclResourceDetailPage(
    data: PlatformSearchData,
    mcmod: ModTranslations.McMod?,
    classes: PlatformClasses,
): PclSubPage = PclSubPage(
    title = "资源下载 - ${data.platformTitle()}",
    content = { PclResourceDetail(data = data, mcmod = mcmod, classes = classes) },
)

@Composable
private fun PclResourceDetail(
    data: PlatformSearchData,
    mcmod: ModTranslations.McMod?,
    classes: PlatformClasses,
) {
    val p = pclPalette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val runtime = LocalPclRuntime.current
    val clipboard = LocalClipboardManager.current
    val platform = data.platform()

    val rawTitle = data.platformTitle()
    val title = mcmod.getMcmodTitle(rawTitle, context)

    var files by remember(data) { mutableStateOf<List<PlatformVersion>>(emptyList()) }
    var loading by remember(data) { mutableStateOf(true) }
    var error by remember(data) { mutableStateOf<String?>(null) }
    var projectUrl by remember(data) { mutableStateOf<String?>(null) }
    var favorite by remember(data) {
        mutableStateOf(FavoriteProjectsRepository.isFavorite(platform, data.platformId()))
    }
    var versionFilter by remember(data) { mutableStateOf<String?>(null) }
    var loaderFilter by remember(data) { mutableStateOf<PlatformDisplayLabel?>(null) }
    var importer by remember { mutableStateOf<ModpackImporter?>(null) }
    /** 点了下载、但还没确定「装到哪个版本」的资源（非 null 时弹选择框）。 */
    var pendingPick by remember { mutableStateOf<PlatformVersion?>(null) }

    LaunchedEffect(data) {
        loading = true
        error = null
        runCatching { getVersions(projectID = data.platformId(), platform = platform) }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
            .getOrNull()
            ?.let { list ->
                files = runCatching { list.initAll(data.platformId()) }.getOrDefault(list)
            }
        loading = false
        // 平台工程链接（用于「转到 xxx」按钮）
        projectUrl = runCatching {
            getProjectByVersion(data.platformId(), platform).platformUrls(classes).projectUrl
        }.getOrNull()
    }

    val installedVersion = runtime.currentVersion.value?.getVersionInfo()?.minecraftVersion

    // 版本筛选项：先列小版本，太多就折叠成大版本（PCL 的 GroupedDrop 思路）
    val versionChoices = remember(files) {
        val all = files.flatMap { it.platformGameVersion().toList() }.distinct()
        val sorted = all.sortedWith(PclVersionDescComparator)
        if (sorted.size < 9) sorted else sorted.map { it.pclBigVersion() }.distinct()
    }
    val loaderChoices = remember(files) {
        files.flatMap { it.platformLoaders() }.distinctBy { it.getDisplayName() }
    }

    val visibleFiles = remember(files, versionFilter, loaderFilter) {
        files.filter { file ->
            val versionOk = versionFilter == null ||
                file.platformGameVersion().any { it == versionFilter || it.pclBigVersion() == versionFilter }
            val filter = loaderFilter
            val loaderOk = filter == null ||
                file.platformLoaders().any { it.getDisplayName() == filter.getDisplayName() }
            versionOk && loaderOk
        }
    }

    // 按游戏版本归组；与筛选/当前已装版本匹配的卡片默认展开
    val grouped = remember(visibleFiles, versionFilter, installedVersion) {
        val map = LinkedHashMap<String, MutableList<PlatformVersion>>()
        visibleFiles.forEach { file ->
            val versions = file.platformGameVersion().toList().ifEmpty { listOf("未知版本") }
            val keys = versions.filter {
                versionFilter == null || it == versionFilter || it.pclBigVersion() == versionFilter
            }.ifEmpty { versions }
            keys.forEach { key -> map.getOrPut(key) { mutableListOf() }.add(file) }
        }
        map.entries
            .sortedWith { a, b -> PclVersionDescComparator.compare(a.key, b.key) }
            .map { it.key to it.value }
    }

    /** 真正提交下载：把资源装进 [targets] 里的每个版本。 */
    fun startResourceDownload(version: PlatformVersion, targets: List<Version>) {
        if (targets.isEmpty()) return
        downloadSingleForVersions(
            version = version,
            versions = targets,
            folder = classes.versionFolder.folderName,
            // 存档：解压 zip 里 level.dat 所在目录到 saves/，并删掉源压缩包
            // （与原生 DownloadSavesScreen 的 onFileCopied 行为一致）
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
        val deps = version.platformDependencies()
            .filter { it.type == PlatformDependencyType.REQUIRED }
            .mapNotNull { dep ->
                dep.projectId?.let { pid ->
                    DependencyRequest(
                        platform = dep.platform,
                        projectId = pid,
                        versionId = dep.versionId,
                        classes = classes,
                        projectTitle = version.platformDisplayName(),
                    )
                }
            }
        if (deps.isNotEmpty()) {
            downloadDependenciesForVersions(
                requests = deps,
                versions = targets,
                submitError = { tm -> runtime.submitError(tm) },
            )
        }
        val where = targets.joinToString("、") { it.getVersionName() }
        PclHintHost.push("已提交下载任务：${version.platformDisplayName()} → $where", PclHintTheme.Green)
    }

    /**
     * 点「下载 / 安装」。
     *
     * 整合包：PCL 是让用户输入一个新的实例名（`MyMsgBoxInput`），不涉及目标版本。
     * 其余资源（Mod / 资源包 / 光影包 / 数据包）：PCL 会先确定「装到哪个版本」——
     * 见 `PageDownloadCompDetail.xaml.vb:420` 那段：优先用**当前选中的版本**作为默认位置，
     * 当前版本不合适时才退回到最近改动过的合适版本，最后才让用户自己挑。
     * 这里就是把这件事显式做成一个选择框：默认选中当前版本，玩家也可以改。
     */
    fun download(version: PlatformVersion) {
        if (classes == PlatformClasses.MOD_PACK) {
            startModpackImport(
                // 内部用 `runtime.backgroundScope`：下载可能要好几分钟，用户中途切走版块是常事，
                // 挂在页面 scope 上的话导入会在建立瞬间就被取消 →「下载完没自动安装」。
                // 同时任务会被登记进「下载管理」，关掉进度弹窗后进度依然在那儿。
                runtime = runtime,
                version = version,
                // 传 null 表示导入已结束，弹窗该收起来了
                onImporter = { importer = it },
            )
            return
        }
        val all = runtime.versions.value
        if (all.isEmpty()) {
            // 一个版本都没扫到：能退到「当前版本」就退，连当前版本都没有才报错。
            val only = runtime.currentVersion.value
            if (only == null) {
                runtime.submitErrorText("没有可用的游戏版本", "先去「下载」里装一个游戏版本，再回来下载这个资源。")
            } else {
                startResourceDownload(version, listOf(only))
            }
        } else {
            // 【一律弹框，不看版本个数】默认选中当前版本。
            //
            // 这里原来是 `if (all.size <= 1) 直接装` —— 意思是「只有一个版本就没得挑」。
            // 结果就是：**只装了一个版本的用户，永远看不到这个确认框**，
            // 点「下载」后资源被静默写进那唯一的版本里，界面上既没有选择也没有二次确认
            // （用户原话：「下载模组或者其他东西时，选择下载版本的界面全不见了」）。
            // 选择框的价值不只是"挑"，更是"让你看见这东西要装到哪儿"，所以个数不是跳过它的理由。
            pendingPick = version
        }
    }

    PclContent {
        // ---- CardIntro
        PclCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 25.dp),
            contentPadding = PaddingValues(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 19.dp),
        ) {
            // 资源本体（MyResourceItem，不可交互）
            PclLocalResourceIntro(data = data, classes = classes, title = title, rawTitle = rawTitle)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclButton(
                    text = "转到 ${platform.displayName}",
                    colorState = PclColorState.Highlight,
                    height = 35.dp,
                    enabled = projectUrl != null,
                    onClick = { projectUrl?.let { runtime.activity.openLinkInternal(it) } },
                )
                // 链接必须按「当前这一栏是哪类资源」来拼：
                // mcmod.cn 的 `class/<id>` 与 `modpack/<id>` 是**两套互不相通的编号**，
                // 同一个数字在两边指向完全不同的东西（整合包 248 = 难以置信的优化，
                // 而 class/248 = 矿物追踪）。以前这里写死了 `class/`，于是整合包详情
                // 点「转到 MC 百科」全都会跳到同号的某个 Mod 上。
                mcmod?.let { m ->
                    val mcmodUrl = classes.getTranslations().getMcmodUrl(m)
                    if (mcmodUrl.isNotBlank()) {
                        PclButton(
                            text = "转到 MC 百科",
                            height = 35.dp,
                            onClick = { runtime.activity.openLinkInternal(mcmodUrl) },
                        )
                    }
                }
                PclButton(
                    text = "复制名称",
                    height = 35.dp,
                    onClick = {
                        clipboard.setText(AnnotatedString(rawTitle))
                        PclHintHost.push("已复制名称。", PclHintTheme.Green)
                    },
                )
                PclButton(
                    text = "复制链接",
                    height = 35.dp,
                    enabled = projectUrl != null,
                    onClick = {
                        projectUrl?.let {
                            clipboard.setText(AnnotatedString(it))
                            PclHintHost.push("已复制链接。", PclHintTheme.Green)
                        }
                    },
                )
                PclButton(
                    text = if (favorite) "已收藏" else "收藏",
                    colorState = if (favorite) PclColorState.Highlight else PclColorState.Normal,
                    height = 35.dp,
                    onClick = {
                        FavoriteProjectsRepository.toggle(data, classes)
                        favorite = !favorite
                    },
                )
            }
        }

        when {
            loading -> PclLoadingCard(
                text = PclStrings.LangDownloadCompLoadingList,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            error != null -> PclCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PclText(text = "获取文件列表失败：$error", color = p.gray2)
                    Spacer(Modifier.height(10.dp))
                    PclButton(text = "刷新", onClick = { error = null; loading = true })
                }
            }

            files.isEmpty() -> PclCard(modifier = Modifier.fillMaxWidth()) {
                PclText(text = "该项目没有可下载的文件。", color = p.gray2)
            }

            else -> {
                // ---- CardFilter：版本筛选 + 加载器筛选
                if (versionChoices.size >= 2 || loaderChoices.size >= 2) {
                    PclCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                        // `PageDownloadCompDetail.xaml:25`：`StackPanel PanFilter Margin="10,10,0,10"`
                        contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 0.dp, bottom = 10.dp),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (versionChoices.size >= 2) {
                                PclFilterChips(
                                    items = versionChoices,
                                    selected = versionFilter,
                                    onSelect = { versionFilter = it },
                                )
                            }
                            if (loaderChoices.size >= 2) {
                                Spacer(Modifier.height(6.dp))
                                PclFilterChips(
                                    items = loaderChoices.map { it.getDisplayName() },
                                    selected = loaderFilter?.getDisplayName(),
                                    onSelect = { name ->
                                        loaderFilter = loaderChoices.firstOrNull { it.getDisplayName() == name }
                                    },
                                )
                            }
                        }
                    }
                }

                // ---- PanResults：每个游戏版本一张可折叠卡片
                grouped.forEachIndexed { index, (version, list) ->
                    val expand = (installedVersion != null && version.equals(installedVersion, true)) ||
                        (versionFilter != null && (version == versionFilter || version.pclBigVersion() == versionFilter)) ||
                        grouped.size == 1 ||
                        index == 0
                    PclCard(
                        title = "$version (${list.size})",
                        modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                        collapsible = true,
                        initiallyCollapsed = !expand,
                        swapLogoRight = true,
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            top = PclDimens.cardContentTop,
                            end = 18.dp,
                            bottom = 18.dp,
                        ),
                    ) {
                        list.forEach { file ->
                            PclResourceFileRow(
                                file = file,
                                installLabel = if (classes == PlatformClasses.MOD_PACK) "安装" else "下载",
                                onInstall = { download(file) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 选择「装到哪个版本」（默认选中当前版本）
    pendingPick?.let { pending ->
        val all = runtime.versions.value
        PclSelectDialog(
            title = "选择要下载到的版本",
            // 名字为空的（加载失败的版本）不列出来，避免出现一排点不动的空条目
            options = all.mapNotNull { v ->
                v.getVersionName().takeIf { it.isNotBlank() }
                    ?.let { v.getVersionPath().absolutePath to it }
            }.distinctBy { it.first },
            // 默认值 = 当前选中的版本（PCL `PageDownloadCompDetail.xaml.vb:420` 的默认位置）
            current = runtime.currentVersion.value?.getVersionPath()?.absolutePath,
            onSelect = { id ->
                all.firstOrNull { it.getVersionPath().absolutePath == id }
                    ?.let { startResourceDownload(pending, listOf(it)) }
            },
            onDismiss = { pendingPick = null },
        )
    }

    importer?.let { imp ->
        PclTitledTaskDialog(
            title = "正在导入整合包",
            tasks = imp.taskFlow,
            // 「取消」是红按钮，必须真的停掉任务流；原来只把弹窗收起来，安装还在后台跑
            onCancel = { runCatching { imp.cancel() }; importer = null },
            onDismiss = { importer = null },
        )
    }
}

/** 详情页顶部的资源本体（对应 `MyResourceItem` + `CanInteraction = False`）。 */
@Composable
private fun PclLocalResourceIntro(
    data: PlatformSearchData,
    classes: PlatformClasses,
    title: String,
    rawTitle: String,
) {
    val context = LocalContext.current
    val tags = remember(data, classes) {
        data.platformCategories(classes)
            ?.sortedBy { it.index() }
            ?.map { context.getString(it.getDisplayName()) }
            .orEmpty()
    }
    PclResourceItem(
        title = title,
        subtitle = rawTitle.takeIf { it.isNotBlank() && it != title },
        description = data.platformDescription().replace("\r", "").replace("\n", ""),
        tags = tags,
        iconUrl = data.platformIconUrl(),
        iconFallback = pclResourceIconFor(classes),
        versionText = data.pclVersionText(),
        downloadText = formatCount(data.platformDownloadCount()),
        updateText = data.platformLastUpdate()
            ?.takeIf { it.isNotBlank() }
            ?.let { getTimeAgo(context, it) }
            ?.takeIf { it.isNotBlank() },
        sourceText = data.platform().displayName,
        onClick = null,
    )
}

/** 一行筛选胶囊：`MyRadioButton Margin="2,0" ColorType=Highlight`，首项是「全部」。 */
@Composable
private fun PclFilterChips(
    items: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    // PCL 的 `PanFilter` 里只有胶囊，没有「××筛选：」这样的标签
    // （`PageDownloadCompDetail.xaml:25` 的 StackPanel 里由代码动态塞 MyRadioButton）。
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        PclPillButton(
            text = "全部",
            checked = selected == null,
            highlight = true,
            modifier = Modifier.padding(horizontal = 2.dp),
            // `PageDownloadCompDetail.xaml.vb:126`：LabText.Margin = (-2,0,10,0)
            textEnd = 10.dp,
            onClick = { onSelect(null) },
        )
        items.forEach { item ->
            PclPillButton(
                text = item,
                checked = selected == item,
                highlight = true,
                modifier = Modifier.padding(horizontal = 2.dp),
                textEnd = 10.dp,
                onClick = { onSelect(item) },
            )
        }
    }
}

/** 一个可下载文件（PCL 的 `ResourceVersion.ToListItem`）。 */
@Composable
private fun PclResourceFileRow(
    file: PlatformVersion,
    installLabel: String,
    onInstall: () -> Unit,
) {
    val loaders = file.platformLoaders().joinToString(" / ") { it.getDisplayName() }
    val info = listOfNotNull(
        loaders.takeIf { it.isNotBlank() },
        formatFileSize(file.platformFileSize()),
        formatDate(file.platformDatePublished().toEpochMilli(), "yyyy/M/d"),
        file.platformFileName().takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    PclListItem(
        title = file.platformDisplayName(),
        info = info,
        type = PclListItemType.Clickable,
        height = 42.dp,
        modifier = Modifier.fillMaxWidth(),
        buttons = listOf(
            PclListItemButton(
                logo = PclIcons.resourceDownload,
                tooltip = installLabel,
                onClick = onInstall,
            ),
        ),
        onClick = onInstall,
    )
}

/** 版本号降序比较：数字段逐段比较，没有点号的（快照）排在最后。 */
private val PclVersionDescComparator = Comparator<String> { a, b ->
    val aNums = versionNumbers(a)
    val bNums = versionNumbers(b)
    val aSnapshot = !a.contains(".")
    val bSnapshot = !b.contains(".")
    when {
        aSnapshot != bSnapshot -> if (aSnapshot) 1 else -1
        else -> {
            var result = 0
            val size = maxOf(aNums.size, bNums.size)
            for (i in 0 until size) {
                val x = aNums.getOrElse(i) { 0 }
                val y = bNums.getOrElse(i) { 0 }
                if (x != y) {
                    result = y - x
                    break
                }
            }
            if (result == 0) b.compareTo(a) else result
        }
    }
}

private fun versionNumbers(value: String): List<Int> =
    Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.toList()

/**
 * 大版本号（PCL 的 `GetGroupedVersionName` 近似）：`1.20.1` → `1.20`，快照保持原样。
 */
private fun String.pclBigVersion(): String {
    if (!contains(".")) return this
    val parts = split(".")
    return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else this
}
