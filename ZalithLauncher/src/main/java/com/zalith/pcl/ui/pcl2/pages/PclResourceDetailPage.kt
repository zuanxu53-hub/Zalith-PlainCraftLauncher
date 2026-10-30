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
import androidx.compose.runtime.key
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
import com.zalith.pcl.game.download.assets.platform.Platform
import com.zalith.pcl.game.download.assets.platform.PlatformClasses
import com.zalith.pcl.game.download.assets.platform.PlatformDependencyType
import com.zalith.pcl.game.download.assets.platform.PlatformDisplayLabel
import com.zalith.pcl.game.download.assets.platform.PlatformFilterCode
import com.zalith.pcl.game.download.assets.platform.PlatformProject
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
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclStrings
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.PclTitledTaskDialog
import com.zalith.pcl.ui.pcl2.startModpackImport
import com.zalith.pcl.ui.pcl2.formatCount
import com.zalith.pcl.ui.pcl2.pclResourceIconFor
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
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
    /**
     * 进来后要直接定位到的文件版本 Id。
     *
     * 从「必要前置资源」点进来时就带上这个 —— 例如在 Iris 的页面点前置 Sodium，
     * 要打开的是 Sodium 的项目页，但**当前选中的版本**应该是 Iris 这个文件所依赖的那一版
     * （Modrinth 的依赖里带 `version_id`），而不是 Sodium 的最新版。
     */
    focusVersionId: String? = null,
): PclSubPage = PclSubPage(
    title = "资源下载 - ${data.platformTitle()}",
    content = {
        PclResourceDetail(
            data = data,
            mcmod = mcmod,
            classes = classes,
            focusVersionId = focusVersionId,
        )
    },
)

@Composable
private fun PclResourceDetail(
    data: PlatformSearchData,
    mcmod: ModTranslations.McMod?,
    classes: PlatformClasses,
    focusVersionId: String? = null,
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
    /**
     * 点了下载、但这条文件带**必装前置**（非 null 时先弹「需要前置资源」提示）。
     *
     * 前置其实是自动装的（[startResourceDownload] 里会把 REQUIRED 依赖一并提交），
     * 但这个过程完全在后台，用户看到的只有「已提交下载任务」。
     * 于是很常见的困惑是：装了 Iris 却发现没生效 —— 因为它要 Sodium，
     * 而玩家根本不知道有这回事。这里把它提前讲清楚。
     */
    var pendingDeps by remember { mutableStateOf<PendingDependencies?>(null) }

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

    /**
     * 从「必要前置资源」点进来时要定位的那个文件（`focusVersionId` 对应的条目）。
     *
     * 文件列表是异步拉的，所以这里跟 [files] 一起算 —— 列表回来之前是 null，回来之后才有着落。
     */
    val focusFile = remember(files, focusVersionId) {
        focusVersionId?.takeIf { it.isNotBlank() }
            ?.let { id -> files.firstOrNull { it.platformId() == id } }
    }

    // 定位成功给一句提示：卡片被顶到最前、条目还挂着「适配版本」，但用户刚跳进来未必反应过来
    LaunchedEffect(focusFile) {
        focusFile?.let {
            PclHintHost.push("已定位到适配版本：${it.platformDisplayName()}", PclHintTheme.Green)
        }
    }

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
    val grouped = remember(visibleFiles, versionFilter, installedVersion, focusFile) {
        val map = LinkedHashMap<String, MutableList<PlatformVersion>>()
        visibleFiles.forEach { file ->
            val versions = file.platformGameVersion().toList().ifEmpty { listOf("未知版本") }
            val keys = versions.filter {
                versionFilter == null || it == versionFilter || it.pclBigVersion() == versionFilter
            }.ifEmpty { versions }
            keys.forEach { key -> map.getOrPut(key) { mutableListOf() }.add(file) }
        }
        val entries = map.entries
            .sortedWith { a, b -> PclVersionDescComparator.compare(a.key, b.key) }
            .map { it.key to it.value }
        if (focusFile == null) {
            entries
        } else {
            // 定位目标所在的那张卡片顶到最前：列表按版本号降序排，目标很可能排在十几张开外，
            // 不挪上来用户还得自己翻。
            entries.sortedBy { (_, list) -> if (list.any { it === focusFile }) 0 else 1 }
        }
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
     * [download] 的后半段：确定「装到哪个版本」并真正提交下载。
     *
     * 注意这是**局部函数**，必须写在 [download] 前面 —— Kotlin 不允许先调用后定义。
     */
    fun proceedDownload(version: PlatformVersion) {
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

    /**
     * 点「下载 / 安装」。
     *
     * 整合包：PCL 是让用户输入一个新的实例名（`MyMsgBoxInput`），不涉及目标版本。
     * 其余资源（Mod / 资源包 / 光影包 / 数据包）：PCL 会先确定「装到哪个版本」——
     * 见 `PageDownloadCompDetail.xaml.vb:420` 那段：优先用**当前选中的版本**作为默认位置，
     * 当前版本不合适时才退回到最近改动过的合适版本，最后才让用户自己挑。
     * 这里就是把这件事显式做成一个选择框：默认选中当前版本，玩家也可以改。
     *
     * 另外：**带必装前置的文件会先弹一句提示**（见 [pendingDeps]），确认后才继续往下走。
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
        // 必装前置先讲清楚再下。整合包不走这一步（它有自己的一整套依赖树）。
        val required = version.platformDependencies()
            .filter { it.type == PlatformDependencyType.REQUIRED }
            .filter { it.projectId != null }
        if (required.isNotEmpty()) {
            val prompt = PendingDependencies(version = version, dependencies = required, names = null)
            pendingDeps = prompt
            // 依赖数据里只有 projectId，没有名字。这里顺手把名字查出来，查不到就退回 id，
            // 不让「提示框还没算出名字」挡住用户点下载。
            scope.launch {
                val names = required.map { dep ->
                    val pid = dep.projectId.orEmpty()
                    runCatching { getProjectByVersion(pid, dep.platform, printLog = false).platformTitle() }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "项目 $pid"
                }
                // 用户可能已经关掉 / 换了另一个文件，别把新提示覆盖掉
                pendingDeps = pendingDeps
                    ?.takeIf { it.version === version }
                    ?.copy(names = names)
            }
            return
        }
        proceedDownload(version)
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
                        // 从「前置资源」跳进来定位的那个文件：卡片必须默认展开，否则目标根本看不见
                        (focusFile != null && list.any { it === focusFile }) ||
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
                                classes = classes,
                                installLabel = if (classes == PlatformClasses.MOD_PACK) "安装" else "下载",
                                highlighted = focusFile != null && file === focusFile,
                                onInstall = { download(file) },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 必装前置提示（点下载时先讲清楚，别让用户装完发现不生效）
    pendingDeps?.let { prompt ->
        val names = prompt.names
        // 名字还在查（或查不到）时用项目 id 顶上，不让提示框一直空着
        val listed = names?.takeIf { it.isNotEmpty() }
            ?: prompt.dependencies.map { "项目 ${it.projectId}" }
        PclConfirmDialog(
            title = "需要前置资源",
            content = buildString {
                append("「")
                append(prompt.version.platformDisplayName())
                append("」需要 ")
                append(listed.size)
                append(" 项必装前置资源，会随它一起下载并装进同一个版本：\n\n")
                listed.forEach { append("·  ").append(it).append('\n') }
                append("\n装完若没有生效，先确认前置的版本、加载器和游戏版本是否一致。")
                if (names == null) append("\n\n（前置名称还在获取中，「继续下载」不受影响）")
            },
            confirmText = "继续下载",
            // 先把状态清掉再往下走：按钮回调之外，退出动画结束时 onDismiss 还会被调一次，
            // 那时 pendingDeps 已是 null，不会把刚发起的流程搅乱。
            onConfirm = {
                pendingDeps = null
                proceedDownload(prompt.version)
            },
            onDismiss = { pendingDeps = null },
        )
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
    classes: PlatformClasses,
    installLabel: String,
    highlighted: Boolean = false,
    onInstall: () -> Unit,
) {
    val loaders = file.platformLoaders().joinToString(" / ") { it.getDisplayName() }
    // 必装前置的条数直接标在信息行里（截图里 PCL 的「1 项前置」就是这个位置）。
    // 只在 > 0 时出现，不占位置也不影响别的资源。
    val requiredCount = file.platformDependencies()
        .count { it.type == PlatformDependencyType.REQUIRED && it.projectId != null }
    val info = listOfNotNull(
        loaders.takeIf { it.isNotBlank() },
        "$requiredCount 项前置".takeIf { requiredCount > 0 },
        formatFileSize(file.platformFileSize()),
        formatDate(file.platformDatePublished().toEpochMilli(), "yyyy/M/d"),
        file.platformFileName().takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    Column(modifier = Modifier.fillMaxWidth()) {
        PclListItem(
            title = file.platformDisplayName(),
            info = info,
            type = PclListItemType.Clickable,
            height = 42.dp,
            modifier = Modifier.fillMaxWidth(),
            // 从「前置资源」跳进来时，被定位的那一版挂个小尾巴，一眼能认出「就是它」
            titleSuffix = "适配版本".takeIf { highlighted },
            buttons = listOf(
                PclListItemButton(
                    logo = PclIcons.resourceDownload,
                    tooltip = installLabel,
                    onClick = onInstall,
                ),
            ),
            onClick = onInstall,
        )
        // 展开卡片后，紧跟着这个文件把它的必装前置列出来：
        // 以前只有点下载时弹一次提示，关掉就没了；《Iris 要 Sodium》这种关系用户想回头看就看不到。
        PclRequiredDependencyBlock(file = file, classes = classes)
    }
}

/**
 * 「必要前置资源（N）」—— 某个文件所依赖的必装前置清单。
 *
 * 条目直接可点：点进去打开的是那个前置**项目**的详情页，并且带上它适配的那个版本 Id
 * （[PlatformVersion.PlatformDependency.versionId]），进去后会自动停在对应版本上。
 * 这正是玩家最容易卡住的地方 —— 从 Sodium 页面只能看到一串最新版，
 * 不知道 Iris 到底要哪一版。
 */
@Composable
private fun PclRequiredDependencyBlock(
    file: PlatformVersion,
    classes: PlatformClasses,
) {
    val p = pclPalette
    val nav = LocalPclNav.current
    val deps = remember(file) {
        file.platformDependencies().filter {
            it.type == PlatformDependencyType.REQUIRED && it.projectId != null
        }
    }
    if (deps.isEmpty()) return

    // projectId -> 项目详情。依赖数据里只有 Id，名称/图标/下载量都得再查一次；
    // 查到一个就更新一次，不让整块等着最后一个请求回来。
    var projects by remember(file) { mutableStateOf<Map<String, PlatformProject>>(emptyMap()) }
    LaunchedEffect(file) {
        val loaded = LinkedHashMap<String, PlatformProject>()
        projects = loaded
        deps.forEach { dep ->
            val pid = dep.projectId ?: return@forEach
            runCatching { getProjectByVersion(pid, dep.platform, printLog = false) }
                .getOrNull()
                ?.let {
                    loaded[pid] = it
                    projects = LinkedHashMap(loaded)
                }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
    ) {
        PclText(text = "必要前置资源（${deps.size}）", bold = true)
        Spacer(Modifier.height(4.dp))
        deps.forEach { dep ->
            val pid = dep.projectId.orEmpty()
            val project = projects[pid]
            key(pid) {
                PclResourceItem(
                    // 还没查到就先用项目 Id 顶着，别让条目空着或者消失
                    title = project?.platformTitle()?.takeIf { it.isNotBlank() } ?: "项目 $pid",
                    description = project?.platformSummary().orEmpty(),
                    iconUrl = project?.platformIconUrl(),
                    iconFallback = pclResourceIconFor(classes),
                    downloadText = project?.let { formatCount(it.platformDownloadCount()) },
                    sourceText = dep.platform.displayName,
                    onClick = {
                        nav.push(
                            pclResourceDetailPage(
                                data = PclProjectRefData(
                                    platformValue = dep.platform,
                                    projectId = pid,
                                    project = project,
                                ),
                                mcmod = null,
                                classes = classes,
                                // 依赖里指定了具体版本就定位过去；只说了「需要这个项目」就正常打开
                                focusVersionId = dep.versionId,
                            )
                        )
                    },
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * 把 [PlatformProject]（项目详情）当成 [PlatformSearchData]（搜索结果）交给详情页。
 *
 * 详情页要的是搜索结果的形状，而跳转时手上只有项目详情 —— 两边字段几乎一一对应，
 * 差在 [PlatformProject.platformSummary] 是可空的、[PlatformSearchData.platformDescription] 不是。
 * [project] 为空（详情还没拉到、或者拉失败）时仍然允许跳转：进去之后详情页自己会再请求一次。
 */
private class PclProjectRefData(
    private val platformValue: Platform,
    private val projectId: String,
    private val project: PlatformProject?,
) : PlatformSearchData {
    override fun platform(): Platform = project?.platform() ?: platformValue

    override fun platformId(): String =
        project?.platformId()?.takeIf { it.isNotBlank() } ?: projectId

    override fun platformTitle(): String =
        project?.platformTitle()?.takeIf { it.isNotBlank() } ?: projectId

    override fun platformDescription(): String = project?.platformSummary().orEmpty()

    override fun platformAuthor(): String = project?.platformAuthor().orEmpty()

    override fun platformIconUrl(): String? = project?.platformIconUrl()

    override fun platformDownloadCount(): Long = project?.platformDownloadCount() ?: 0L

    override fun platformFollows(): Long? = project?.platformFollows()

    override fun platformModLoaders(): List<PlatformDisplayLabel>? = project?.platformModLoaders()

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? =
        project?.platformCategories(classes)
}

/**
 * 点下载时遇到的必装前置。
 *
 * [names] 是异步查出来的显示名（`null` = 还在查，先用项目 id 顶上）；
 * 依赖数据本身只有 `projectId`，没有名字，所以要额外请求一次。
 */
private data class PendingDependencies(
    val version: PlatformVersion,
    val dependencies: List<PlatformVersion.PlatformDependency>,
    val names: List<String>?,
)

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
