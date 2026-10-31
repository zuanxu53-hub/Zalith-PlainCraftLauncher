/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 版本管理 →「游戏资源」分组下的六个管理页：
 *   存档 / 截图 / 资源包 / 光影包 / 投影原理图 / 服务器
 *
 * 版式照 PCL CE 的 `PageInstanceLeft` + 各 `PageInstanceXxx`：
 *   搜索框（`MySearchBox`）→ 管理按钮卡（打开文件夹 / 刷新 / 下载新 xxx）→ 列表卡。
 * 六个页面的骨架完全一样，差别只在「哪个目录、收哪些文件、每行显示什么、缩略图怎么来」，
 * 因此都挂在同一个 [PclInstanceFilePage] 上；服务器存的是 `servers.dat`（NBT），
 * 走 Zalith 现成的 `AllServers` 读取，单独实现。
 *
 * 「模组」那一页不在本文件里 —— 它有启用/禁用与批量操作，见 `PclInstancePages.kt`
 * 的 `PclInstanceModPage`。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.pages

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalith.pcl.R
import com.zalith.pcl.game.text.MINECRAFT_COLOR_FORMAT
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionFolders
import com.zalith.pcl.game.version.multiplayer.AllServers
import com.zalith.pcl.game.version.multiplayer.ServerData
import com.zalith.pcl.game.version.multiplayer.description.ComponentDescription
import com.zalith.pcl.game.version.multiplayer.description.ComponentDescriptionRoot
import com.zalith.pcl.game.version.multiplayer.description.ServerDescription
import com.zalith.pcl.game.version.multiplayer.description.StringDescription
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclCenteredPage
import com.zalith.pcl.ui.pcl2.PclContent
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSection
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclDualInputDialog
import com.zalith.pcl.ui.pcl2.components.PclEmptyButton
import com.zalith.pcl.ui.pcl2.components.PclEmptyState
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIconButton
import com.zalith.pcl.ui.pcl2.components.PclIconTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.components.PclListItemButton
import com.zalith.pcl.ui.pcl2.components.PclLoadingCard
import com.zalith.pcl.ui.pcl2.components.PclSearchBox
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.data.LocalPclRuntime
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclHairline
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.formatDate
import com.zalith.pcl.utils.network.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/* ============================================================================
 *  存档 / 截图 / 资源包 / 光影包 / 投影原理图
 * ==========================================================================*/

/** 存档：`saves/` 下的每个子文件夹就是一个世界。 */
@Composable
internal fun PclInstanceSavesPage(version: Version) {
    val dir = remember(version) { VersionFolders.SAVES.getDir(version.getGameDir()) }
    val context = LocalContext.current
    PclInstanceFilePage(
        dir = dir,
        searchHint = "搜索存档名称",
        emptyTitle = "还没有存档",
        emptyContent = "存档会在游戏里新建世界后出现在这里。\n你也可以把从别处下载的存档解压进这个文件夹。",
        downloadText = "下载新存档",
        // 下载页左栏：0 原版游戏 / 1 Mod / 2 整合包 / 3 存档 / 4 资源包 / 5 光影包
        downloadIndex = 3,
        accept = { it.isDirectory },
        // 存档目录名就是世界名（`level.dat` 里那个玩家可改的名字我们不解析，保持与列表一致）
        titleOf = { it.name },
        // 世界的封面就是存档目录里的 `icon.png`（MC 存盘时写进去的那张小图）。
        iconOf = { file -> runCatching { File(file, "icon.png") }.getOrNull()?.let { pclDecodeThumb(it) } },
        // 34dp 的图标列放不下「封面」，这里换成自绘的大封面行（56dp 图 / 68dp 行高）
        customRow = { file, thumb, info, onDelete ->
            PclSaveEntry(
                title = file.name,
                info = info,
                thumb = thumb,
                onClick = { openFolder(context, file) },
                onDelete = onDelete,
            )
        },
        deleteTitle = "删除存档",
        importSaveZip = true,
    )
}

/** 截图：`screenshots/` 下的图片，直接画缩略图（PCL 的截图页也是图墙式）。 */
@Composable
internal fun PclInstanceScreenshotPage(version: Version) {
    val dir = remember(version) { VersionFolders.SCREENSHOTS.getDir(version.getGameDir()) }
    PclInstanceFilePage(
        dir = dir,
        searchHint = "搜索截图名称",
        emptyTitle = "还没有截图",
        emptyContent = "在游戏里按 F2 截的图会出现在这里。",
        accept = { it.isFile && it.extension.lowercase() in PCL_IMAGE_EXTENSIONS },
        titleOf = { it.nameWithoutExtension },
        iconOf = { file -> pclDecodeThumb(file) },
        deleteTitle = "删除截图",
    )
}

/** 资源包：`resourcepacks/` 下的 zip 或文件夹。 */
@Composable
internal fun PclInstanceResourcepackPage(version: Version) {
    val dir = remember(version) { VersionFolders.RESOURCE_PACK.getDir(version.getGameDir()) }
    PclInstanceFilePage(
        dir = dir,
        searchHint = "搜索资源包名称",
        emptyTitle = "还没有安装资源包",
        emptyContent = "资源包可以是 zip 文件，也可以是解压后的文件夹。",
        downloadText = "下载新资源包",
        downloadIndex = 4,
        // 光影包目录里常见的 .zip / 文件夹同样适用
        accept = { !it.name.startsWith(".") },
        titleOf = { it.name.removeSuffix(".zip") },
        deleteTitle = "删除资源包",
        importKind = PclSharedResourceKind.RESOURCE_PACK,
    )
}

/** 光影包：`shaderpacks/` 下的 zip 或文件夹。 */
@Composable
internal fun PclInstanceShaderPage(version: Version) {
    val dir = remember(version) { VersionFolders.SHADERS.getDir(version.getGameDir()) }
    PclInstanceFilePage(
        dir = dir,
        searchHint = "搜索光影包名称",
        emptyTitle = "还没有安装光影包",
        emptyContent = "光影包需要 Iris 或 OptiFine 才能生效，装好后在游戏里选择。",
        downloadText = "下载新光影包",
        downloadIndex = 5,
        accept = { !it.name.startsWith(".") },
        titleOf = { it.name.removeSuffix(".zip") },
        deleteTitle = "删除光影包",
        importKind = PclSharedResourceKind.SHADER,
    )
}

/**
 * 投影原理图：`schematics/` 下的 `.litematic` / `.schematic` / `.nbt`。
 *
 * 目录是 Litematica / WorldEdit 之类的 Mod 自己建的，原版没有，
 * 所以「还没有」是常态，别把它当成错误。
 *
 * 但「目录空」有两种完全不同的原因：**装了投影 Mod 只是还没保存过**，
 * 和**压根没装投影 Mod**（那这个文件夹永远不会自己出现东西）。
 * 后者光写「还没有投影原理图」等于让用户对着一个空页面猜，所以这里先扫一遍
 * `mods/`，没扫到就把空态换成「您尚未安装投影 Mod」并给一个去下载 Mod 的入口。
 */
@Composable
internal fun PclInstanceSchematicPage(version: Version) {
    val dir = remember(version) { File(version.getGameDir(), "schematics") }
    val context = LocalContext.current
    val nav = LocalPclNav.current

    // null = 还没扫完（先按「已装」处理，别闪一下「尚未安装」）
    var projectionMod by remember(version) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(version) {
        projectionMod = withContext(Dispatchers.IO) { pclHasProjectionMod(version) }
    }

    PclInstanceFilePage(
        dir = dir,
        searchHint = "搜索原理图名称",
        emptyTitle = "还没有投影原理图",
        emptyContent = "投影原理图一般由 Litematica、WorldEdit 等 Mod 保存到 schematics 文件夹。",
        accept = { it.isFile && it.extension.lowercase() in PCL_SCHEMATIC_EXTENSIONS },
        titleOf = { it.nameWithoutExtension },
        deleteTitle = "删除原理图",
        importKind = PclSharedResourceKind.SCHEMATIC,
        emptyStateOverride = if (projectionMod == false) {
            {
                PclEmptyState(
                    title = "您尚未安装投影 Mod",
                    content = "这个实例里没有检测到 Litematica、WorldEdit、Schematica 或 Axiom。\n" +
                            "投影原理图由它们保存到 schematics 文件夹 —— 先装一个投影 Mod 再回来。",
                    modifier = Modifier.padding(40.dp),
                    buttonText = "下载 Mod",
                    onButtonClick = { nav.go(PclSection.Download, 1) },
                    contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 0.dp),
                    buttonsTopPadding = 20.dp,
                    moreButtons = listOf(PclEmptyButton("打开文件夹") { openFolder(context, dir) }),
                )
            }
        } else {
            null
        },
    )
}

/* ============================================================================
 *  通用文件列表页
 * ==========================================================================*/

private val PCL_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")
private val PCL_SCHEMATIC_EXTENSIONS = setOf("litematic", "schematic", "nbt")

/** 能产生投影原理图的 Mod，按 jar 文件名里会出现的关键字（小写）匹配。 */
private val PCL_PROJECTION_MOD_KEYWORDS = listOf("litematica", "worldedit", "schematica", "axiom")

/**
 * 这个实例装了投影类 Mod 没有。
 *
 * 只按 `mods/` 里的**文件名**判断，不解压 jar：这类 Mod 的 jar 名一定带自己的名字
 * （`litematica-fabric-1.20.1-0.15.3.jar`、`worldedit-mod-7.2.15.jar`），
 * 为了给一句提示去解析几百个 jar 的元数据不值得。
 *
 * 没有 Mod 加载器的实例直接判 false —— 原版 Minecraft 根本加载不了 Mod，
 * 里面就算躺着 jar 也不会生效。
 */
private fun pclHasProjectionMod(version: Version): Boolean {
    val hasLoader = version.getVersionInfo()?.loaderInfos?.any { it.loader.isLoader } == true
    if (!hasLoader) return false
    val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
    val names = modsDir.listFiles()?.map { it.name.lowercase() }.orEmpty()
    return names.any { name -> PCL_PROJECTION_MOD_KEYWORDS.any { name.contains(it) } }
}

/**
 * 列表行左侧的小封面。
 *
 * 有图就 `Crop` 铺满并切 4dp 圆角（Crop 是硬裁，不切圆角就是一块死方块）；
 * 没图才退到 PCL 的 `NoIcon.png` —— 调用方不给 [iconOf] 时压根不会有这一列。
 */
@Composable
private fun BoxScope.PclFileCover(thumb: ImageBitmap?) {
    if (thumb != null) {
        Image(
            bitmap = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
        )
    } else {
        Image(
            painter = androidx.compose.ui.res.painterResource(R.drawable.pcl_noicon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 存档专用的一行：左侧 56dp 大封面 + 世界名 + 「时间 · 大小」+ 删除。
 *
 * 为什么不直接用 [PclListItem]：它的图标列宽写死在 34dp（PCL 的 `PathLogo.Margin` 口径），
 * 而存档的 `icon.png` 是这张卡片上唯一有信息量的东西 —— 塞进 31dp 就只是块糊掉的小方块，
 * 看着根本不像封面。这里自绘一行，封面 56dp、行高 68dp，整行点击进存档文件夹。
 *
 * 悬停态刻意与 `PclListItem` 对齐（ColorBrush7 @ alpha 190 底 + 1px ColorBrush6 边 + 6dp 圆角），
 * 和别的列表摆在一起看不出是两套控件。
 */
@Composable
private fun PclSaveEntry(
    title: String,
    info: String,
    thumb: ImageBitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = pclPalette
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed

    val background by animateColorAsState(
        targetValue = if (active) p.color7.copy(alpha = 190f / 255f) else Color.Transparent,
        animationSpec = tween(120),
        label = "pclSaveRowBackground",
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) p.color6 else Color.Transparent,
        animationSpec = tween(120),
        label = "pclSaveRowBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(pclHairline(), borderColor, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(start = 4.dp, top = 6.dp, end = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(p.gray8)
                .border(1.dp, p.gray6, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
                )
            } else {
                // 没有 icon.png 的存档（很多下载来的地图都没有）才退到占位图，别把灰立方体放大
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.pcl_noicon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = PclTextStyle.listTitle(p).copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = info,
                style = PclTextStyle.listInfo(p).copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(6.dp))
        PclIconButton(
            logo = PclIcons.delete,
            contentDescription = "删除存档",
            size = 25.dp,
        ) { onDelete() }
    }
}

/**
 * 「游戏资源」五个页面共用的骨架。
 *
 * 与 PCL 的顺序一致：搜索框（吸顶）→ 管理按钮卡 → 列表卡。
 * 空态是 `PanEmpty` 那张居中卡片（[PclEmptyState]）：一个「下载新的」主按钮 +
 * 「打开文件夹」次按钮 —— 目录都不存在时也能把用户领到正确的位置。
 *
 * @param accept 目录里哪些条目算这一页的资源
 * @param titleOf 行标题
 * @param iconOf 缩略图（会在 IO 线程里逐条调用）。**给 null 表示这一页不显示封面列** ——
 *   存档这类「封面只是一张通用小方块」的页面就别摆图标了，直接文字两行更干净；
 *   给了函数但某一条返回 null 时仍画 PCL 的 `NoIcon.png` 占位。
 * @param customRow 自定义一行。给了就不用 [PclListItem]（存档页要用比 34dp 图标列大得多的封面，
 *   而 `PclListItem` 的图标列宽是写死的 34dp，塞不进 56dp 的图）。
 * @param emptyStateOverride 替换默认空态卡片的内容（外层 [PclCenteredPage] 仍由本页提供）。
 *   投影原理图页用它区分「装了 Mod 但还没保存过」和「压根没装投影 Mod」。
 * @param downloadIndex 下载页左栏下标；null 表示这页没有「下载新的」入口
 */
@Composable
private fun PclInstanceFilePage(
    dir: File,
    searchHint: String,
    emptyTitle: String,
    emptyContent: String,
    accept: (File) -> Boolean,
    titleOf: (File) -> String = { it.name },
    iconOf: ((File) -> ImageBitmap?)? = null,
    customRow: (@Composable (file: File, thumb: ImageBitmap?, info: String, onDelete: () -> Unit) -> Unit)? = null,
    emptyStateOverride: (@Composable () -> Unit)? = null,
    downloadText: String? = null,
    downloadIndex: Int? = null,
    deleteTitle: String = "删除文件",
    /** 非空时显示「从文件导入」，并按这个类型校验扩展名。 */
    importKind: PclSharedResourceKind? = null,
    /** 存档页：导入 zip 后解压成 `saves/<世界名>/`。 */
    importSaveZip: Boolean = false,
) {
    val p = pclPalette
    val context = LocalContext.current
    val nav = LocalPclNav.current
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    /** 每行的大小 + 修改时间；key = 绝对路径。 */
    var infos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    /** 缩略图；key = 绝对路径。 */
    var icons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var token by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<File?>(null) }

    // 从文件导入：直接选本机文件复制进当前实例对应目录，和「打开文件夹后手工拖进去」等价。
    val canImport = importKind != null || importSaveZip
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || !canImport) return@rememberLauncherForActivityResult
        scope.launch {
            val result = if (importSaveZip) {
                pclImportSaveZip(context, uri, dir)
            } else {
                pclImportSharedResource(context, uri, dir, importKind!!)
                    .map { it.name }
            }
            result.onSuccess { name ->
                PclHintHost.push("已导入「$name」。", PclHintTheme.Green)
                token++
            }.onFailure {
                PclHintHost.push("导入失败：${it.message ?: it.javaClass.simpleName}", PclHintTheme.Red)
            }
        }
    }

    LaunchedEffect(dir, token) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                // 新的排在前面：截图 / 存档页最常看的就是最近的那几个
                dir.listFiles()?.filter(accept)?.sortedByDescending { it.lastModified() }
            }.getOrNull().orEmpty()
        }
        files = loaded
        // 大小要递归统计文件夹、缩略图要解码 —— 一律在 IO 线程算完再进状态
        infos = withContext(Dispatchers.IO) {
            loaded.associate { it.absolutePath to pclFileSummary(it) }
        }
        icons = withContext(Dispatchers.IO) {
            buildMap {
                if (iconOf != null) {
                    loaded.forEach { file -> iconOf.invoke(file)?.let { put(file.absolutePath, it) } }
                }
            }
        }
        loading = false
    }

    val visible = remember(files, keyword) {
        if (keyword.isBlank()) files else files.filter { titleOf(it).contains(keyword, true) }
    }

    // 目录为空 / 还在读：PCL 会把整页换成一张居中卡片
    if (files.isEmpty()) {
        PclCenteredPage {
            if (loading) {
                PclCard(
                    modifier = Modifier.padding(horizontal = 40.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    PclLoadingCard(text = "正在读取文件列表")
                }
            } else if (emptyStateOverride != null) {
                emptyStateOverride()
            } else {
                PclEmptyState(
                    title = emptyTitle,
                    content = emptyContent,
                    modifier = Modifier.padding(40.dp),
                    // 有下载入口时把「下载新的」放主按钮位，否则主按钮就是「打开文件夹」
                    buttonText = downloadText ?: "打开文件夹",
                    onButtonClick = {
                        if (downloadIndex != null && downloadText != null) {
                            nav.go(PclSection.Download, downloadIndex)
                        } else {
                            openFolder(context, dir)
                        }
                    },
                    contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 0.dp),
                    buttonsTopPadding = 20.dp,
                    moreButtons = buildList {
                        if (canImport) {
                            add(PclEmptyButton(if (importSaveZip) "导入存档" else "从文件导入") {
                                importPicker.launch(arrayOf("*/*"))
                            })
                        }
                        if (downloadIndex != null && downloadText != null) {
                            add(PclEmptyButton("打开文件夹") { openFolder(context, dir) })
                        }
                    },
                )
            }
        }
        return
    }

    PclContent(
        contentPadding = PaddingValues(start = 25.dp, top = 10.dp, end = 25.dp, bottom = 10.dp),
    ) {
        PclSearchBox(
            value = keyword,
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            hint = searchHint,
            onValueChange = { keyword = it },
        )

        // ---- 管理按钮卡（PageInstanceMod 的 PanManage 同款：Grid Margin="15,15,0,15" Height="35"）
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(15.dp, 15.dp, 0.dp, 15.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                PclManageButton("打开文件夹", highlight = true) { openFolder(context, dir) }
                if (canImport) {
                    PclManageButton(if (importSaveZip) "导入存档" else "从文件导入") {
                        importPicker.launch(arrayOf("*/*"))
                    }
                }
                PclManageButton("刷新") { token++ }
                if (downloadIndex != null && downloadText != null) {
                    PclManageButton(downloadText) { nav.go(PclSection.Download, downloadIndex) }
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))

        // ---- 列表卡
        PclCard(
            modifier = Modifier.fillMaxWidth(),
            title = " ",
            contentPadding = PaddingValues(start = 15.dp, top = 13.dp, end = 18.dp, bottom = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclText(
                    text = if (keyword.isBlank()) "共 ${visible.size} 个" else "搜索结果 ${visible.size} 个",
                    fontSize = 12,
                    color = p.gray2,
                )
            }
            Spacer(Modifier.height(7.dp))

            if (visible.isEmpty()) {
                PclText(
                    text = "没有符合当前搜索条件的条目。",
                    color = p.gray2,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            } else {
                // 这一页要不要封面列，由调用方给不给 iconOf 决定
                val hasCover = iconOf != null
                visible.forEach { file ->
                    val info = infos[file.absolutePath].orEmpty()
                    val thumb = icons[file.absolutePath]
                    if (customRow != null) {
                        customRow(file, thumb, info) { deleting = file }
                        Spacer(Modifier.height(2.dp))
                    } else {
                        val cover: (@Composable BoxScope.() -> Unit)? = if (hasCover) {
                            { PclFileCover(thumb) }
                        } else {
                            null
                        }
                        PclListItem(
                            title = titleOf(file),
                            info = info,
                            logoContent = cover,
                            height = 44.dp,
                            buttons = listOf(
                                PclListItemButton(PclIcons.delete, "删除") { deleting = file },
                            ),
                            // 目录（存档 / 解压后的资源包）直接进去；文件则打开它所在的文件夹
                            onClick = { openFolder(context, if (file.isDirectory) file else dir) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(PclDimens.cardGap))
    }

    deleting?.let { file ->
        PclConfirmDialog(
            title = deleteTitle,
            content = "确定要删除「${titleOf(file)}」吗？此操作不可恢复。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        }.getOrDefault(false)
                    }
                    if (ok) {
                        PclHintHost.push("已删除「${titleOf(file)}」。", PclHintTheme.Green)
                        token++
                    } else {
                        PclHintHost.push("删除失败，文件可能正在被占用。", PclHintTheme.Red)
                    }
                }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/* ============================================================================
 *  服务器（servers.dat）
 * ==========================================================================*/

/**
 * 服务器列表。数据来自 `servers.dat`（NBT），读取/回写都用 Zalith 现成的
 * [AllServers]，删一条就是 `removeServer` + `save` —— 正好是 M 原版的行为，
 * 不需要另写一套 NBT 解析。
 *
 * 「添加服务器」就是往 `servers.dat` 里塞一条 [ServerData] 再 `save`。
 * 添加完 / 进入页面时会逐条 [ServerData.load]（M 的 status 查询），
 * 拿到的延迟、在线人数、封面图正好是用户要的三样东西。
 */
@Composable
internal fun PclInstanceServerPage(version: Version) {
    val p = pclPalette
    val nav = LocalPclNav.current
    val runtime = LocalPclRuntime.current
    val scope = rememberCoroutineScope()
    val gameDir = remember(version) { version.getGameDir() }
    val dataFile = remember(gameDir) { File(gameDir, "servers.dat") }

    var holder by remember { mutableStateOf<AllServers?>(null) }
    var servers by remember { mutableStateOf<List<ServerData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var token by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<ServerData?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServerData?>(null) }

    LaunchedEffect(dataFile, token) {
        loading = true
        val all = AllServers()
        withContext(Dispatchers.IO) { all.loadServers(dataFile) }
        holder = all
        val list = all.serverList.toList()
        servers = list
        loading = false

        // Ping 一定要挂在这条 effect 里，**不能**另开一条 `LaunchedEffect(servers)`：
        // `ServerData.equals` 把 `icon` 也算进相等性，而 Ping 成功会把远端 favicon 回填到
        // `icon` 上 —— 列表内容于是「变了」，以它为 key 的 effect 会重启，再 Ping 一轮，无限循环。
        //
        // `ServerData.load` 内部自己切 IO、再回主线程写 `operation`，这里只管并发发出去；
        // 失败只是那一行显示「无法连接」，不影响其它服务器。
        list.forEach { server ->
            launch {
                // `AllServers.save` 收的是**目录**（它自己拼 servers.dat），别传 dataFile
                server.load { holder?.let { h -> scope.launch { h.save(gameDir) } } }
            }
        }
    }

    if (servers.isEmpty()) {
        PclCenteredPage {
            if (loading) {
                PclCard(
                    modifier = Modifier.padding(horizontal = 40.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    PclLoadingCard(text = "正在读取服务器列表")
                }
            } else {
                PclEmptyState(
                    title = "服务器列表为空",
                    content = if (dataFile.exists()) {
                        "这个实例的 servers.dat 里还没有任何服务器。\n点下面的「添加服务器」填上地址就能加进来。"
                    } else {
                        "这个实例还没有 servers.dat。\n点下面的「添加服务器」填上地址，添加时会自动生成。"
                    },
                    modifier = Modifier.padding(40.dp),
                    buttonText = "添加服务器",
                    onButtonClick = { adding = true },
                    contentPadding = PaddingValues(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 0.dp),
                    buttonsTopPadding = 20.dp,
                    moreButtons = listOf(
                        PclEmptyButton("返回联机页") { nav.go(PclSection.Link) },
                    ),
                )
            }
        }
    } else {
        PclContent(
            contentPadding = PaddingValues(start = 25.dp, top = 10.dp, end = 25.dp, bottom = 10.dp),
        ) {
            PclCard(
                modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
                contentPadding = PaddingValues(15.dp, 15.dp, 0.dp, 15.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    PclManageButton("添加服务器", highlight = true) { adding = true }
                    PclManageButton("刷新") { token++ }
                    PclManageButton("转到联机") { nav.go(PclSection.Link) }
                }
            }
            Spacer(Modifier.height(PclDimens.cardGap))

            PclCard(
                modifier = Modifier.fillMaxWidth(),
                title = " ",
                contentPadding = PaddingValues(start = 10.dp, top = 13.dp, end = 10.dp, bottom = 22.dp),
            ) {
                PclText(text = "共 ${servers.size} 个服务器", fontSize = 12, color = p.gray2)
                Spacer(Modifier.height(7.dp))
                servers.forEach { server ->
                    PclServerEntry(
                        server = server,
                        onRefresh = { scope.launch { server.load { } } },
                        onJoin = {
                            // 小三角 = 启动这个实例并直接进服（PCL 的服务器列表双击同款效果）
                            if (runtime.playServer(version, server.originIp)) {
                                PclHintHost.push(
                                    "正在启动「${version.getVersionName()}」，进入后自动连接 ${server.originIp}。",
                                    PclHintTheme.Green,
                                )
                            }
                        },
                        onEdit = { editing = server },
                        onDelete = { deleting = server },
                    )
                }
            }
            Spacer(Modifier.height(PclDimens.cardGap))
        }
    }

    // ---- 添加服务器（地址 + 名称，PCL 的 PageInstanceServer「添加服务器」）
    if (adding) {
        PclDualInputDialog(
            title = "添加服务器",
            label1 = "服务器地址",
            hint1 = "例如 mc.example.com 或 1.2.3.4:25565",
            label2 = "服务器名称",
            hint2 = "留空就用服务器地址",
            confirmText = "添加",
            onConfirm = { address, name ->
                // 弹窗点完「添加」会立刻关窗，所以真正干活要自己 launch 出去
                scope.launch {
                    val failure = pclAddServer(address, name, servers, holder, gameDir, dataFile)
                    if (failure == null) {
                        PclHintHost.push("已添加服务器，正在获取延迟与在线人数…", PclHintTheme.Green)
                        token++
                    } else {
                        PclHintHost.push(failure, PclHintTheme.Red)
                    }
                }
            },
            onDismiss = { adding = false },
        )
    }

    // ---- 修改服务器（地址 + 名称，PCL 服务器列表的「编辑」）
    editing?.let { server ->
        PclDualInputDialog(
            title = "修改服务器",
            label1 = "服务器地址",
            initial1 = server.originIp,
            hint1 = "例如 mc.example.com 或 1.2.3.4:25565",
            label2 = "服务器名称",
            initial2 = server.name,
            hint2 = "留空就用服务器地址",
            confirmText = "保存",
            onConfirm = { address, name ->
                scope.launch {
                    val failure = pclEditServer(server, address, name, servers, holder, gameDir, dataFile)
                    if (failure == null) {
                        PclHintHost.push("已保存服务器信息，正在重新获取延迟与在线人数…", PclHintTheme.Green)
                        token++
                    } else {
                        PclHintHost.push(failure, PclHintTheme.Red)
                    }
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { server ->
        PclConfirmDialog(
            title = "删除服务器",
            content = "确定要从列表里移除「${server.name.ifBlank { server.originIp }}」吗？" +
                "这只是删掉 servers.dat 里的这一条，不会影响游戏本体。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            val all = holder ?: return@runCatching false
                            all.removeServer(server)
                            // 同「添加」：save 收目录，且失败不抛异常，只能回读确认
                            all.save(gameDir)
                            val check = AllServers().also { it.loadServers(dataFile) }
                            check.serverList.none { it.originIp.equals(server.originIp, ignoreCase = true) }
                        }.getOrDefault(false)
                    }
                    if (ok) {
                        PclHintHost.push("已移除服务器「${server.name.ifBlank { server.originIp }}」。", PclHintTheme.Green)
                        token++
                    } else {
                        PclHintHost.push("写入 servers.dat 失败。", PclHintTheme.Red)
                    }
                }
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

/* ============================================================================
 *  服务器行 / 添加服务器
 * ==========================================================================*/

/**
 * 一行服务器 —— 版式照你给的那张服务器列表截图（也是 PCL CE 的 `ItemServer`）：
 *
 * ```
 *  ┌──────┬──────────────────────────────┬───────────┬──────────┐
 *  │ 封面 │ 名称                          │ 100/200 ▂▄▆ │  ▶ ✎ 🗑  │
 *  │      │ MOTD（保留原版颜色，最多两行）  │  45 ms     │          │
 *  │      │ 地址                          │            │          │
 *  └──────┴──────────────────────────────┴───────────┴──────────┘
 * ```
 *
 * 行尾的小三角（[PclIcons.playTriangle]）= 启动这个实例并**直接进服**；
 * ✎ 改地址 / 名称；🗑 删除。点整行照旧是重新 Ping。
 *
 * `ServerData.operation` / `refreshUI` 都是 snapshot 状态（见 `ServerData.kt`），
 * 在组合里读一下，Ping 结果回填时这一行就自动刷新；封面是 `servers.dat` 里内嵌的
 * PNG（base64），Ping 拿到新 favicon 时 `refreshUI` 会自增，于是这里按它重算缩略图。
 */
@Composable
private fun PclServerEntry(
    server: ServerData,
    onRefresh: () -> Unit,
    onJoin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = pclPalette
    val operation = server.operation
    val iconVersion = server.refreshUI
    val icon = remember(server, iconVersion) { pclDecodeIcon(server.icon) }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = hovered || pressed
    // 与 PclListItem 的指向底色一致：ColorBrush7 @ alpha 190
    val background by animateColorAsState(
        targetValue = if (active) p.color7.copy(alpha = 190f / 255f) else Color.Transparent,
        animationSpec = tween(120),
        label = "pclServerRowBack",
    )

    val loaded = (operation as? ServerData.Operation.Loaded)?.result
    val players = loaded?.status?.players
    val pingMs = loaded?.pingMs

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(interactionSource = interaction, indication = null, onClick = onRefresh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(start = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- 封面
            // 圆角 6（和整行悬停底色的圆角一致）。原来的 3 在 38dp 上几乎看不出来，
            // 服务器图标看着就是方块；灰底 + 灰边保留 —— 有些服的 favicon 主体是白的，
            // 没有这圈边会直接溶进白色卡片里。
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.gray8)
                    .border(1.dp, p.gray6, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.pcl_noicon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(Modifier.width(9.dp))

            // ---- 名称 / MOTD / 地址
            Column(modifier = Modifier.weight(1f).padding(vertical = 7.dp)) {
                Text(
                    text = server.name.ifBlank { server.originIp },
                    style = PclTextStyle.listTitle(p).copy(
                        fontSize = 13.sp,
                        lineHeight = PclTextStyle.lineHeight(13),
                        color = p.color1,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                PclServerMotd(
                    description = loaded?.status?.description,
                    fallback = when (operation) {
                        is ServerData.Operation.Loading -> "正在获取服务器信息…"
                        is ServerData.Operation.Failed -> "无法连接，点这一行重试"
                        is ServerData.Operation.Loaded -> null
                    },
                    fallbackColor = if (operation is ServerData.Operation.Failed) p.redDark else p.gray2,
                    maxLines = 2,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = server.originIp,
                    style = PclTextStyle.listInfo(p).copy(fontSize = 10.sp, color = p.gray2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(6.dp))

            // ---- 在线人数 + 信号条 + 延迟
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PclText(
                        text = players?.let { "${it.online}/${it.max}" } ?: "--/--",
                        fontSize = 11,
                        color = p.gray2,
                    )
                    Spacer(Modifier.width(4.dp))
                    PclServerSignal(
                        strength = pclSignalStrength(pingMs),
                        activeColor = if (pingMs != null) p.color3 else p.gray5,
                        idleColor = p.gray6,
                    )
                }
                Spacer(Modifier.height(3.dp))
                PclText(
                    text = pingMs?.let { "$it ms" } ?: "—",
                    fontSize = 10,
                    color = p.gray2,
                )
            }

            Spacer(Modifier.width(2.dp))

            // ---- 行尾：小三角（启动进服）/ 修改 / 删除
            PclIconButton(
                logo = PclIcons.playTriangle,
                size = 30.dp,
                logoSize = 14.dp,
                theme = PclIconTheme.Custom,
                customColor = p.color3,
                contentDescription = "启动并进入服务器",
                onClick = onJoin,
            )
            PclIconButton(
                logo = PclIcons.loginMsEdit,
                size = 25.dp,
                logoScale = 0.85f,
                contentDescription = "修改服务器地址与名称",
                onClick = onEdit,
            )
            PclIconButton(
                logo = PclIcons.delete,
                size = 25.dp,
                logoScale = 0.9f,
                contentDescription = "删除服务器",
                onClick = onDelete,
            )
        }
    }
}

/**
 * MOTD：Minecraft 的 `description` 是富文本组件（带颜色 / 加粗 / `§` 代码），
 * 这里按原版语义还原成 [AnnotatedString]。
 *
 * **默认色用 [PclPalette.gray1]**（而不是原版的白）：PCL 的卡片是浅色的，
 * 组件里没写颜色时若按白色渲染就等于看不见。
 */
@Composable
private fun PclServerMotd(
    description: ServerDescription?,
    fallback: String?,
    fallbackColor: Color,
    maxLines: Int,
) {
    val p = pclPalette
    val text = remember(description, p.gray1) { pclMotdText(description, p.gray1) }
    when {
        text.isNotEmpty() -> Text(
            text = text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        fallback != null -> Text(
            text = fallback,
            style = PclTextStyle.listInfo(p).copy(fontSize = 10.sp, color = fallbackColor),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 信号条：4 根递增的柱子，按延迟点亮。
 *
 * 阈值照 PCL / 原版的血条习惯：<100ms 满格，<200ms 三格，<400ms 两格，其余一格。
 */
@Composable
private fun PclServerSignal(
    strength: Int,
    activeColor: Color,
    idleColor: Color,
) {
    Canvas(modifier = Modifier.size(width = 14.dp, height = 11.dp)) {
        val bars = 4
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val height = size.height * (0.4f + 0.6f * i / (bars - 1))
            drawRoundRect(
                color = if (i < strength) activeColor else idleColor,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth * 0.4f, barWidth * 0.4f),
            )
        }
    }
}

/** 延迟 → 信号格数。 */
private fun pclSignalStrength(pingMs: Long?): Int = when {
    pingMs == null -> 0
    pingMs < 100 -> 4
    pingMs < 200 -> 3
    pingMs < 400 -> 2
    else -> 1
}

/* ---- MOTD 富文本 -------------------------------------------------------- */

/**
 * [ServerDescription] → [AnnotatedString]。
 *
 * `ServerData` 的 `description` 可能是三选一（见 `StatusResponse.kt`）：
 * 组件根 / 单个组件 / 纯字符串（后者省事，直接写 `§a浅绿` 这种代码）。
 */
private fun pclMotdText(description: ServerDescription?, default: Color): AnnotatedString {
    if (description == null) return AnnotatedString("")
    return buildAnnotatedString { appendMotd(description, default) }
}

private fun AnnotatedString.Builder.appendMotd(desc: ServerDescription, default: Color) {
    when (desc) {
        is ComponentDescriptionRoot -> desc.values.forEach { appendMotd(it, default) }
        is ComponentDescription -> {
            val style = SpanStyle(
                color = pclReadableColor(desc.color?.foreground ?: default, default),
                fontWeight = if (desc.bold == true) FontWeight.Bold else null,
                fontStyle = if (desc.italic == true) FontStyle.Italic else null,
                textDecoration = when {
                    desc.underlined == true && desc.strikethrough == true ->
                        TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                    desc.underlined == true -> TextDecoration.Underline
                    desc.strikethrough == true -> TextDecoration.LineThrough
                    else -> null
                },
            )
            appendStyledRun(desc.text, style, default)
            desc.extra.forEach { appendMotd(it, default) }
        }
        is StringDescription -> appendStyledRun(
            text = desc.value,
            base = SpanStyle(color = default),
            default = default,
        )
    }
}

/**
 * 追加一段带 `§` 格式代码的文本。颜色 / 粗斜体 / 下划线都按原版 `Formatting` 处理，
 * `§r` 回到这一段的基础样式。
 */
private fun AnnotatedString.Builder.appendStyledRun(
    text: String,
    base: SpanStyle,
    default: Color,
) {
    if (text.isEmpty()) return
    if (!text.contains('§')) {
        withStyle(base) { append(text) }
        return
    }

    var current = base
    val buffer = StringBuilder()
    var index = 0
    while (index < text.length) {
        val c = text[index]
        if (c == '§' && index + 1 < text.length) {
            if (buffer.isNotEmpty()) {
                withStyle(current) { append(buffer.toString()) }
                buffer.clear()
            }
            val code = text[index + 1].lowercaseChar()
            current = when {
                MINECRAFT_COLOR_FORMAT.containsKey(code) -> base.copy(
                    color = pclReadableColor(MINECRAFT_COLOR_FORMAT.getValue(code).foreground, default),
                )
                code == 'r' -> base
                code == 'l' -> current.copy(fontWeight = FontWeight.Bold)
                code == 'o' -> current.copy(fontStyle = FontStyle.Italic)
                code == 'n' -> current.copy(textDecoration = TextDecoration.Underline)
                code == 'm' -> current.copy(textDecoration = TextDecoration.LineThrough)
                else -> current
            }
            index += 2
        } else {
            buffer.append(c)
            index++
        }
    }
    if (buffer.isNotEmpty()) withStyle(current) { append(buffer.toString()) }
}

/**
 * PCL 的卡片底色是浅色，原版的纯白（`§f` / `white`）压上去等于看不见，
 * 因此把接近纯白的颜色换成默认文字色；其它颜色原样保留。
 */
private fun pclReadableColor(color: Color, default: Color): Color = when {
    color.red > 0.92f && color.green > 0.92f && color.blue > 0.92f -> default
    else -> color
}

/**
 * 修改一条服务器。
 *
 * [ServerData] 的 `name` / `originIp` 都是 `var`，而 `AllServers` 内部持有的正是同一个
 * 实例，所以直接改字段再存盘即可（不需要先删后加）。
 *
 * 地址变了就把本地缓存的 favicon 清掉 —— 那是旧服务器的图，留着会张冠李戴；
 * 调用方随后 `token++` 重新读盘，会重新 Ping 并取回新图。
 *
 * @return 失败时返回给人看的原因；成功返回 null。
 */
private suspend fun pclEditServer(
    server: ServerData,
    address: String,
    name: String,
    servers: List<ServerData>,
    holder: AllServers?,
    gameDir: File,
    dataFile: File,
): String? {
    val ip = address.trim()
    if (ip.isEmpty()) return "服务器地址不能为空。"
    if (runCatching { ServerAddress.parse(ip) }.isFailure) {
        return "服务器地址格式不对，示例：mc.example.com 或 1.2.3.4:25565。"
    }
    if (servers.any { it !== server && it.originIp.equals(ip, ignoreCase = true) }) {
        return "列表里已经有 $ip 了。"
    }

    val newName = name.trim().ifBlank { ip }
    val ipChanged = !server.originIp.equals(ip, ignoreCase = true)

    val saved = withContext(Dispatchers.IO) {
        runCatching {
            val all = holder ?: AllServers().also { it.loadServers(dataFile) }
            server.name = newName
            server.originIp = ip
            if (ipChanged) server.icon = null
            // save 收目录，不是 servers.dat 文件
            all.save(gameDir)
            // 回读校验（save 内部吞异常，只能这样确认真写进去了）
            val check = AllServers().also { it.loadServers(dataFile) }
            check.serverList.any { it.originIp.equals(ip, ignoreCase = true) && it.name == newName }
        }.getOrDefault(false)
    }
    return if (saved) null else "写入 servers.dat 失败，请确认游戏目录可写。"
}

/**
 * 往 `servers.dat` 里加一条服务器。
 *
 * @return 失败时返回给人看的原因；成功返回 null。
 *
 * `holder` 为空只可能发生在「页面还没读过文件」这一瞬间，此时自己新建一个
 * [AllServers] 先把磁盘上的内容读进来，免得把用户原有的服务器整表覆盖掉。
 *
 * ⚠️ `AllServers.save` 内部把异常全吞了、还会**静默失败**，所以写完必须回读一次
 * `servers.dat` 确认这一条真的在里面 —— 否则就是第 117 轮那个「提示添加成功、
 * 列表里什么都没有」。
 */
private suspend fun pclAddServer(
    address: String,
    name: String,
    servers: List<ServerData>,
    holder: AllServers?,
    gameDir: File,
    dataFile: File,
): String? {
    val ip = address.trim()
    if (ip.isEmpty()) return "服务器地址不能为空。"

    val invalid = runCatching { ServerAddress.parse(ip) }.exceptionOrNull()
    if (invalid != null) {
        return "服务器地址格式不对，示例：mc.example.com 或 1.2.3.4:25565。"
    }
    if (servers.any { it.originIp.equals(ip, ignoreCase = true) }) {
        return "列表里已经有 $ip 了。"
    }

    val saved = withContext(Dispatchers.IO) {
        runCatching {
            val all = holder ?: AllServers().also { it.loadServers(dataFile) }
            all.addServer(ServerData(name = name.trim().ifBlank { ip }, originIp = ip))
            // save 收目录，不是 servers.dat 文件
            all.save(gameDir)
            // 回读校验：真的写进磁盘了吗？
            val check = AllServers().also { it.loadServers(dataFile) }
            check.serverList.any { it.originIp.equals(ip, ignoreCase = true) }
        }.getOrDefault(false)
    }
    return if (saved) null else "写入 servers.dat 失败，请确认游戏目录可写。"
}

/** `servers.dat` 里的封面是 64×64 的 PNG 字节流，直接解出来当行图标。 */
private fun pclDecodeIcon(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.isEmpty()) return null
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/* ============================================================================
 *  小工具
 * ==========================================================================*/

/** 一行的副标题：`2026/10/17 12:30 · 12.3 MB`。 */
private fun pclFileSummary(file: File): String {
    val size = if (file.isDirectory) pclFolderSize(file) else file.length()
    return "${formatDate(file.lastModified(), "yyyy/M/d HH:mm")} · ${pclFormatSize(size)}"
}

/** 递归统计文件夹大小；只用于显示，超过 [limit] 个文件就停，避免大存档卡住列表。 */
private fun pclFolderSize(dir: File, limit: Int = 4096): Long {
    var total = 0L
    var count = 0
    val stack = ArrayDeque<File>()
    stack.addLast(dir)
    while (stack.isNotEmpty() && count < limit) {
        val current = stack.removeLast()
        val children = current.listFiles() ?: continue
        for (child in children) {
            count++
            if (count > limit) break
            if (child.isDirectory) stack.addLast(child) else total += child.length()
        }
    }
    return total
}

/** 文件大小 → `1.2 MB` / `345 KB`。 */
private fun pclFormatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1024.0 / 1024 / 1024)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

/** 解码缩略图（`inSampleSize` 直接按目标边长给，别把 4K 截图整张读进内存）。 */
private fun pclDecodeThumb(file: File): ImageBitmap? = runCatching {
    if (!file.isFile || file.length() <= 0L) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > PCL_THUMB_MAX || bounds.outHeight / sample > PCL_THUMB_MAX) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}.getOrNull()

/** 缩略图的解码上限（边长）。存档封面 56dp，按三倍屏留余量（56 × 3 ≈ 168）。 */
private const val PCL_THUMB_MAX = 192
