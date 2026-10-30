/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * `PageInstanceExport` —— 「导出整合包」页。
 *
 * 版式逐项照 PCL 2.9 的 `Pages/PageInstance/PageInstanceExport.xaml`：
 * ```
 * PanBack（内容区，Margin="25,10,25,65"）
 *   HintOptiFine     MyHint(Yellow)「受 Modrinth 整合包规范限制…不含 OptiFine」
 *   HintJava         MyHint(Yellow/Red)  版本 Java 相关提醒
 *   MyCard Margin="0,15"                  ── 整合包名称 / 整合包版本 同一行
 *     Grid Margin="22,15,25,15" Height="28"  Cols: Auto / 1* / Auto / 0.3*
 *   MyCard Margin="0,0,0,15"              ── 导出内容选项（一长串复选框，子项缩进 30）
 *     BtnOverrideCancel  右上角「重置」
 *     StackPanel Margin="25,38,15,15"
 *   MyCard Title="高级选项" CanSwap=True IsSwapped=True
 *     StackPanel Margin="25,37,23,20"     ── 提示 + 两个总开关 + 三个按钮
 * BtnExport  MyExtraTextButton 底部居中「开始导出」
 * ```
 * 规则（`ExportOption.Rules`）用 PCL 那套 VB `Like` 语法实现：
 * `|` 分隔、`/` 等价 `\`、`*` `?` `[]` 通配、`!` 前缀反选、**靠后的行覆盖靠前的行**，
 * 末尾再追加 PCL 固定的排除项（`*.log`、`*.dat_old` …）。
 *
 * 与 PCL 的差异（见 `buildExportOptions` 与各处的注释）：
 *   · 「PCL 启动器程序 / PCL 个性化内容」没做 —— Zalith 不是 PCL，没法把启动器打进整合包；
 *   · 「版本文件夹中的 Java」没做 —— Zalith 的 `PackExporter` 没有把 Java 塞进整合包的通道；
 *   · 「整合包作者 / 整合包描述」两栏放在**高级选项**里（默认折叠）——
 *     Zalith 的 `ExportInfo` 有这两个字段，PCL 那边没有；
 *   · 「读取配置 / 保存配置」写的是勾选状态（PCL 的配置文件里还含逐文件规则）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.pages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zalith.pcl.game.addons.modloader.ModLoader
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.game.version.export.ExportInfo
import com.zalith.pcl.game.version.export.PackExporter
import com.zalith.pcl.game.version.export.PackType
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.ui.pcl2.PclContent
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclCheckBox
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclExtraTextButton
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclIcons
import com.zalith.pcl.ui.pcl2.components.PclPillButton
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.components.PclTextBox
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* ============================================================================
 *  导出规则（PCL 的 `ExportOption` + `Like` 匹配）
 * ==========================================================================*/

/**
 * PCL 的 `ExportOption`（`PageInstanceExport.xaml.vb:1-17`）。
 *
 * [rules] 为空表示这一项不筛文件（由 [showRules] 决定要不要显示）。
 */
private data class PclExportOption(
    val key: String,
    val title: String,
    val description: String? = null,
    val rules: String? = null,
    val showRules: String? = null,
    val defaultChecked: Boolean = false,
    val requireModLoader: Boolean = false,
    val requireOptiFine: Boolean = false,
    val requireModLoaderOrOptiFine: Boolean = false,
    /** 「游戏本体」那种不可取消的项。 */
    val forcedOn: Boolean = false,
    /** 子项缩进 `Margin="30,0,0,0"`。 */
    val indent: Boolean = false,
    /** 父项 key：父项没勾选就不显示（PCL 的 `Visibility` 绑定）。 */
    val visibleWhen: String? = null,
    /** 经 `IsVisible` 判定后的可见性。 */
    val visible: Boolean = true,
)

/** `StringUtils.LikePatternEscape`：磁盘上的名字里可能带通配符，先转义再拼规则。 */
private fun likeEscape(raw: String): String = buildString {
    for (c in raw) {
        when (c) {
            '*', '?', '[', ']', '#' -> append('[').append(c).append(']')
            else -> append(c)
        }
    }
}

/** VB `Like` 运算符 → 正则（PCL 的规则就是拿 `Like` 比的）。 */
private fun likeToRegex(pattern: String): Regex {
    val sb = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        when (val c = pattern[i]) {
            '*' -> sb.append(".*")
            '?' -> sb.append('.')
            '#' -> sb.append("[0-9]")
            '[' -> {
                val end = pattern.indexOf(']', i + 1)
                if (end < 0) {
                    sb.append("\\[")
                } else {
                    var body = pattern.substring(i + 1, end)
                    if (body.startsWith("!")) body = "^" + body.substring(1)
                    sb.append('[').append(body).append(']')
                    i = end
                }
            }
            else -> {
                if (c in "\\^$.|+(){}") sb.append('\\')
                sb.append(c)
            }
        }
        i++
    }
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

private val likeRegexCache = HashMap<String, Regex>()

private fun likeRegex(pattern: String): Regex = likeRegexCache.getOrPut(pattern) { likeToRegex(pattern) }

/**
 * PCL 的 `StandardizeLines`：去空格、跳过空行与 `#` / `=` 注释行、
 * 把 `/` 换成 `\`、目录规则（以 `\` 结尾）补上 `*`。
 */
private fun standardizeRules(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split('|').mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("=")) return@mapNotNull null
        var rule = trimmed.replace('/', '\\')
        if (rule.endsWith("\\")) rule += "*"
        rule
    }
}

/** 规则按**顺序覆盖**（PCL 的 `For Each Rule In AllRules` 每次都会重写 `ShouldKeep`）。 */
private fun rulesKeep(relativePath: String, rules: List<String>): Boolean {
    var keep = false
    val path = relativePath.replace('/', '\\')
    for (rule in rules) {
        val revert = rule.startsWith("!")
        val pattern = if (revert) rule.substring(1) else rule
        if (likeRegex(pattern).matches(path)) keep = !revert
    }
    return keep
}

/** `GetAllRules` 末尾固定追加的排除项。 */
private const val EXTRA_EXCLUDE_RULES = "*.log|*.dat_old|*.BakaCoreInfo|hmclversion.cfg|log4j2.xml"

/** 导出失败的日志 tag。 */
private const val TAG_EXPORT = "PclExport"

/**
 * 生成导出文件的默认名：`名字-版本.后缀`。
 *
 * 两件事必须在这里做：
 * 1. **后缀跟着导出格式走**。选 Modrinth 格式却给 `.zip`，存出来就是一个披着
 *    zip 皮的 `.mrpack`；发给别人后，按扩展名认格式的启动器会当成普通压缩包，
 *    直接导不进去（「导出文件有问题」有很大一部分是这个）。
 * 2. **剔掉文件名里的非法字符**。`/ \ : * ? " < > |` 在 SAF 的 provider 里
 *    会让 `CreateDocument` 直接抛异常，出口是「导出失败」或一个空文件。
 */
private fun packFileName(name: String, version: String, suffix: String): String {
    val bad = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
    fun clean(raw: String, fallback: String): String = raw
        .replace(bad, "_")
        .trim()
        .trimEnd('.')
        .ifBlank { fallback }

    return "${clean(name, "modpack")}-${clean(version, "1.0.0")}.$suffix"
}

/** 把当前勾选的选项翻译成规则列表（顺序 = 界面顺序，与 PCL 的 `GetAllOptions` 一致）。 */
private fun collectRules(options: List<PclExportOption>, checked: Map<String, Boolean>): List<String> {
    val lines = ArrayList<String>()
    for (option in options) {
        if (!option.visible) continue
        if (option.visibleWhen != null && checked[option.visibleWhen] != true) continue
        if (!option.forcedOn && checked[option.key] != true) continue
        lines += standardizeRules(option.rules ?: continue)
    }
    lines += standardizeRules(EXTRA_EXCLUDE_RULES)
    return lines
}

/** PCL 在搜索时跳过的目录：根目录下的资源/版本/库，以及几个缓存夹。 */
private val SKIP_ROOT_FOLDERS = setOf("assets", "versions", "libraries")
private val SKIP_ANY_FOLDERS = setOf("structureCacheV1", ".fabric", ".git", "avatar-cache", "cosmetic-cache")

/** 按规则挑出要导出的文件（等价于 PCL 的 `SearchFolder` + `ShouldKeep`）。 */
private fun selectExportFiles(root: File, rules: List<String>): List<File> {
    val result = ArrayList<File>()
    fun walk(dir: File, relative: String, isRoot: Boolean) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val name = child.name
            if (child.isDirectory) {
                if (isRoot && name in SKIP_ROOT_FOLDERS) continue
                if (name in SKIP_ANY_FOLDERS) continue
                walk(child, if (relative.isEmpty()) name else "$relative/$name", false)
            } else {
                val path = if (relative.isEmpty()) name else "$relative/$name"
                if (rulesKeep(path, rules)) result += child
            }
        }
    }
    walk(root, "", true)
    return result
}

/* ============================================================================
 *  选项清单（照 XAML 的顺序与规则）
 * ==========================================================================*/

private val SUB_OPTION_BLACKLIST = listOf("Quark Programmer Art.zip", "+ EuphoriaPatches_", "PCL2 Skin.zip")

/** `ReloadSubOptions`：把真实目录下的压缩包与子文件夹列成子项。 */
private fun buildSubOptions(
    keyPrefix: String,
    gameDir: File,
    folder: String,
    acceptArchive: Boolean,
    acceptFolder: Boolean,
): List<PclExportOption> {
    val dir = File(gameDir, folder)
    if (!dir.isDirectory) return emptyList()
    val result = ArrayList<PclExportOption>()
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    if (acceptArchive) {
        dir.listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".zip", true) || it.name.endsWith(".rar", true)) }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                if (SUB_OPTION_BLACKLIST.any { file.name.contains(it) }) return@forEach
                result += PclExportOption(
                    key = "$keyPrefix:${file.name}",
                    title = file.name,
                    rules = likeEscape("$folder/${file.name}"),
                    defaultChecked = true,
                    indent = true,
                )
            }
    }
    if (acceptFolder) {
        dir.listFiles()
            ?.filter { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
            ?.sortedByDescending { it.lastModified() }
            ?.forEach { sub ->
                if (SUB_OPTION_BLACKLIST.any { sub.name.contains(it) }) return@forEach
                result += PclExportOption(
                    key = "$keyPrefix:${sub.name}",
                    title = sub.name,
                    // 「单人游戏存档」的子项在 PCL 里显示最后修改时间
                    description = if (folder == "saves") dateFormat.format(Date(sub.lastModified())) else null,
                    rules = likeEscape("$folder/${sub.name}/"),
                    defaultChecked = true,
                    indent = true,
                )
            }
    }
    return result
}

/**
 * PCL `IsVisible` 里的粗查：
 *   1. 先只拿前两级的文件名/文件夹名去比对规则；
 *   2. 对三级以上的规则（如 `config/tacz/custom/`）把通配符裁掉后按真实路径查一次
 *      —— PCL 的 `Rule.Trim("*?")` 之后 `Split("\").Count >= 3` 那一支。
 */
private fun roughlyHasEntry(gameDir: File, rules: String): Boolean {
    val entries = ArrayList<String>()
    gameDir.listFiles()?.forEach { first ->
        entries += first.name
        if (first.isDirectory) {
            entries += "${first.name}\\"
            first.listFiles()?.forEach { second ->
                entries += "${first.name}\\${second.name}"
                if (second.isDirectory) entries += "${first.name}\\${second.name}\\"
            }
        }
    }
    val positive = standardizeRules(rules).filterNot { it.startsWith("!") }
    return positive.any { rule ->
        entries.any { likeRegex(rule).matches(it) } || run {
            val stripped = rule.trim { it == '*' || it == '?' }
            if (stripped.split('\\').size < 3) return@run false
            val target = File(gameDir, stripped.replace('\\', '/'))
            if (stripped.endsWith("\\")) {
                target.isDirectory && target.listFiles()?.isNotEmpty() == true
            } else {
                target.exists()
            }
        }
    }
}

/**
 * 生成整页的选项清单（顺序与 PCL 的 XAML 完全一致，靠后的规则覆盖靠前的）。
 *
 * 没做的三项见文件头的差异说明。
 */
private fun buildExportOptions(
    gameDir: File,
    modable: Boolean,
    hasOptiFine: Boolean,
    versionDisplayName: String,
): List<PclExportOption> {
    val options = ArrayList<PclExportOption>()

    // 「游戏本体」：不可取消，且不参与规则（整合包本身就是它）
    options += PclExportOption(
        key = "basic",
        title = "游戏本体",
        description = versionDisplayName,
        forcedOn = true,
    )
    options += PclExportOption(
        key = "options",
        title = "游戏本体设置",
        description = "键位、音量、视频设置等",
        rules = "options.txt|configureddefaults/",
        defaultChecked = true,
    )
    options += PclExportOption(
        key = "profile",
        title = "游戏本体个人信息",
        description = "命令历史、已保存的快捷栏",
        rules = "hotbar.nbt|command_history.txt",
    )
    options += PclExportOption(
        key = "optifine",
        title = "OptiFine 设置",
        rules = "optionsof.txt|optionsshaders.txt",
        defaultChecked = true,
        requireOptiFine = true,
    )
    options += PclExportOption(
        key = "mod",
        title = "Mod",
        description = "模组",
        rules = "mods/|!mods/*.disabled|!mods/*.old|!mods/.connector/|coremods/|lib/",
        defaultChecked = true,
        requireModLoader = true,
    )
    options += PclExportOption(
        key = "modDisabled",
        title = "已禁用的 Mod",
        rules = "mods/*.disabled|mods/*.old",
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "modAddons",
        title = "整合包重要数据",
        description = "脚本文件、内置资源包、数据包等",
        rules = "hotai/|bansoukou/|addons/|multiblocked/|modpack-update-checker/|global_packs/|" +
                "global_resource_packs/|global_data_packs/|optional_data_packs/|moonlight-global-datapacks/|" +
                "maps/|icon.png|mods-resourcepacks/|matmos/|resource_assorts/|resource_assorts.json|" +
                "patchouli_books/|datapacks/|kubejs*/|!kubejs*/probe/|!kubejs*/exported/|" +
                "!kubejs*/jsconfig.json|!kubejs*/README.txt|openloader/|worldshape/|resources/|scripts/|" +
                "structures/|fontfiles/|oresources/|packmenu/|craftpresence/|pointblanks/|template*/|" +
                "!template*/playerdata/|!template*/stats/",
        defaultChecked = true,
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "modSettings",
        title = "Mod 设置",
        rules = "config/|!config/jei/world/|!config/worldedit/|config/worldedit/worldedit.properties|" +
                "!config/spark/|config/spark/config.json|defaultconfigs/|journeymap/config/|journeymap/server/|" +
                "TrashSlotSaveState.json|customfov.txt|gg.essential.mod/|essential/|!essential/*/|" +
                "!essential/*.jar*|!essential/screenshot-checksum-caches.json|" +
                "!essential/microsoft_accounts.json|paragliderSettings.nbt|local/client_config.json|" +
                "local/ftbl.json|local/client/sidebar_buttons.json|local/client/ftbutilities.cfg|" +
                "local/client/ftblib.cfg|local/client/xencraft.cfg|liteloader.properties|default_reference.xml|" +
                "CustomSkinLoader/CustomSkinLoader.json|!config/tacz/custom",
        defaultChecked = true,
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "tacz",
        title = "TaCZ 枪包",
        rules = "tacz/|config/tacz/custom/",
        defaultChecked = true,
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "paintings",
        title = "已上传的沉浸画",
        rules = "immersive_paintings/",
        defaultChecked = true,
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "mapRecord",
        title = "已绘制的地图",
        description = "地图类 Mod 为现有的存档、服务器记录的地图、路标点等",
        rules = "journeymap/data/|xaero/|XaeroWaypoints/|XaeroWorldMap/",
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "jei",
        title = "JEI 个人信息",
        description = "物品收藏夹等",
        rules = "config/jei/world/",
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "emi",
        title = "EMI 个人信息",
        description = "物品收藏夹、默认配方、合成历史记录等",
        rules = "emi.json",
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "patchouli",
        title = "帕秋莉手册个人信息",
        description = "教程书的已读记录、书签、阅读历史记录等",
        rules = "patchouli_data.json",
        indent = true,
        visibleWhen = "mod",
    )
    options += PclExportOption(
        key = "resourcepacks",
        title = "资源包",
        description = "纹理包/材质包",
        showRules = "resourcepacks/|texturepacks/",
        defaultChecked = true,
    )
    options += buildSubOptions("resourcepacks", gameDir, "resourcepacks", true, true)
    options += buildSubOptions("texturepacks", gameDir, "texturepacks", true, true)
    options += PclExportOption(
        key = "shaderpacks",
        title = "光影包",
        showRules = "shaderpacks/",
        defaultChecked = true,
        requireModLoaderOrOptiFine = true,
    )
    options += buildSubOptions("shaderpacks", gameDir, "shaderpacks", true, true)
    options += PclExportOption(key = "screenshot", title = "截图", rules = "screenshots/")
    options += PclExportOption(
        key = "schematics",
        title = "导出的结构",
        description = "schematics 文件夹",
        rules = "schematics/",
    )
    options += PclExportOption(
        key = "replay",
        title = "录像回放",
        description = "Replay Mod 的录像文件",
        rules = "replay_recordings/|replay_videos/",
        requireModLoader = true,
    )
    options += PclExportOption(
        key = "saves",
        title = "单人游戏存档",
        description = "世界/地图",
        showRules = "saves/",
    )
    options += buildSubOptions("saves", gameDir, "saves", false, true)
    options += PclExportOption(
        key = "license",
        title = "协议",
        description = "Licence 文件",
        rules = "LICEN*",
        defaultChecked = true,
    )
    options += PclExportOption(key = "servers", title = "多人游戏服务器列表", rules = "servers.dat")

    // 可见性（PCL 的 IsVisible）：先看 RequireXxx，再**不分父子**地用 `Rules ?: ShowRules`
    // 粗查磁盘上是否真的有对应的文件/文件夹 —— 子项（如「TaCZ 枪包」）也一样要查，
    // 否则装了什么模组都没有的整合包里也会列出一长串 Mod 专属项。
    // 勾选 = DefaultChecked && Pass；显示与否 = Pass（父项没勾时再由 visibleWhen 收起来）。
    return options.map { option ->
        val pass = when {
            option.requireOptiFine && !hasOptiFine -> false
            option.requireModLoader && !modable -> false
            option.requireModLoaderOrOptiFine && !modable && !hasOptiFine -> false
            option.forcedOn -> true
            else -> {
                val probe = option.rules ?: option.showRules
                if (probe == null) true else roughlyHasEntry(gameDir, probe)
            }
        }
        option.copy(visible = pass, defaultChecked = option.defaultChecked && pass)
    }
}

/* ============================================================================
 *  页面
 * ==========================================================================*/

@Composable
fun PclInstanceExportPage(version: Version) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val info = remember(version) { version.getVersionInfo() }
    val modable = info?.primaryLoader?.loader?.isLoader == true
    val hasOptiFine = remember(version) {
        (info?.primaryLoader?.loader == ModLoader.OPTIFINE) ||
                version.getVersionName().contains("optifine", true)
    }

    var packName by remember(version) { mutableStateOf("") }
    var packVersion by remember(version) { mutableStateOf("") }
    // Zalith 的 ExportInfo 需要作者与描述（PCL 没有这两栏，因此放进高级选项）
    var author by remember(version) { mutableStateOf("") }
    var summary by remember(version) { mutableStateOf("") }
    /** `CheckAdvancedInclude`：打包资源文件（勾选 = 不联网取远程资源） */
    var advancedEmbed by remember(version) { mutableStateOf(false) }
    /** `CheckAdvancedModrinth`：Modrinth 上传模式 */
    var advancedModrinth by remember(version) { mutableStateOf(false) }

    var options by remember(version) { mutableStateOf<List<PclExportOption>>(emptyList()) }
    val checked = remember(version) { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(version) {
        val built = withContext(Dispatchers.IO) {
            buildExportOptions(version.getGameDir(), modable, hasOptiFine, version.getVersionName())
        }
        checked.clear()
        built.forEach { option ->
            checked[option.key] = if (option.forcedOn) true else option.defaultChecked
        }
        options = built
    }

    // ---- 版本 Java 设置提醒（PCL 的 HintJava / BtnExport.IsEnabled）
    val javaRuntimeId = remember(version) { version.getVersionConfig().javaRuntime }
    val javaUsable = remember(javaRuntimeId) {
        javaRuntimeId.isBlank() || RuntimesManager.isRuntimeUsable(javaRuntimeId)
    }

    var running by remember(version) { mutableStateOf(false) }

    fun doExport(uri: Uri) {
        if (running) return
        running = true
        scope.launch {
            // 准备阶段（扫文件、建 ExportInfo）也可能抛异常。不兜住的话 `running` 会永远停在
            // true，按钮从此点不动，而且没有任何提示 —— 看起来就是「点了没反应」。
            try {
                val name = packName.ifBlank { version.getVersionName() }
                val ver = packVersion.ifBlank { "1.0.0" }
                val rules = collectRules(options, checked)
                val files = withContext(Dispatchers.IO) { selectExportFiles(version.getGameDir(), rules) }
                if (files.isEmpty()) {
                    running = false
                    PclHintHost.push("没有匹配到任何要导出的文件，请检查「导出内容」的勾选。", PclHintTheme.Red)
                    return@launch
                }
                val loader = info?.primaryLoader?.let { ExportInfo.LoaderVersion(it.loader, it.version) }
                val exportInfo = ExportInfo(
                    gamePath = version.getGameDir(),
                    name = name,
                    summary = summary.ifBlank { null },
                    author = author,
                    version = ver,
                    mcVersion = info?.minecraftVersion ?: "",
                    loader = loader,
                    selectedFiles = files,
                    minMemory = 1024,
                    maxMemory = version.getRamAllocation(context),
                    packType = if (advancedModrinth) PackType.Modrinth else PackType.CurseForge,
                    // 「打包资源文件」= 不再联网取远程资源（对应 PCL 的同名开关）
                    packModrinth = !advancedEmbed,
                    packCurseForge = !advancedEmbed,
                )
                val exporter = PackExporter(context = context, exportInfo = exportInfo, scope = scope)
                exporter.startExport(
                    outputUri = uri,
                    version = version,
                    onFinished = {
                        running = false
                        // 提示里带完整文件名：用户选保存位置时未必补了扩展名，靠它确认存成了什么文件
                        val actualSuffix = if (advancedModrinth) "mrpack" else "zip"
                        PclHintHost.push(
                            "整合包已导出：${packFileName(name, ver, actualSuffix)}",
                            PclHintTheme.Green,
                        )
                    },
                    onError = { th ->
                        running = false
                        Logger.warning(TAG_EXPORT, "Export modpack failed", th)
                        PclHintHost.push(
                            "导出失败：${th.message ?: th.javaClass.simpleName}",
                            PclHintTheme.Red,
                        )
                    },
                )
            } catch (t: Throwable) {
                running = false
                Logger.warning(TAG_EXPORT, "Prepare modpack export failed", t)
                PclHintHost.push(
                    "导出失败：${t.message ?: t.javaClass.simpleName}",
                    PclHintTheme.Red,
                )
            }
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { doExport(it) } }

    // ---- 「保存配置 / 读取配置」
    val saveConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = JSONObject().apply {
                put("name", packName)
                put("version", packVersion)
                put("author", author)
                put("summary", summary)
                put("embedAssets", advancedEmbed)
                put("modrinth", advancedModrinth)
                put("options", JSONObject().apply { checked.forEach { (k, v) -> put(k, v) } })
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toString().toByteArray()) }
                    true
                }.getOrDefault(false)
            }
            if (ok) PclHintHost.push("已保存配置文件。", PclHintTheme.Green)
            else PclHintHost.push("保存配置失败。", PclHintTheme.Red)
        }
    }
    val readConfig = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
                    .getOrNull()
            }
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (json == null) {
                PclHintHost.push("读取配置失败。", PclHintTheme.Red)
                return@launch
            }
            packName = json.optString("name", packName)
            packVersion = json.optString("version", packVersion)
            author = json.optString("author", author)
            summary = json.optString("summary", summary)
            advancedEmbed = json.optBoolean("embedAssets", advancedEmbed)
            advancedModrinth = json.optBoolean("modrinth", advancedModrinth)
            json.optJSONObject("options")?.let { obj ->
                obj.keys().forEach { key -> checked[key] = obj.optBoolean(key) }
            }
            PclHintHost.push("已读取配置文件。", PclHintTheme.Green)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // StackPanel Margin="25,10,25,65" —— 底部留白给悬浮的「开始导出」
        PclContent(contentPadding = PaddingValues(start = 25.dp, top = 10.dp, end = 25.dp, bottom = 65.dp)) {
            if (hasOptiFine) {
                PclHint(
                    text = "受 Modrinth 整合包规范限制，导出的整合包中不会包含 OptiFine。",
                    modifier = Modifier.padding(top = 15.dp),
                    theme = PclHintTheme.Yellow,
                )
            }
            if (javaRuntimeId.isNotBlank()) {
                PclHint(
                    text = if (javaUsable) {
                        "该版本设置要求「使用指定的 Java」，这在导出后很可能会失效。建议修改为「使用版本文件夹中的 Java」。"
                    } else {
                        "该版本设置要求「使用指定的 Java」，但该 Java 当前不可用，导出的整合包可能无法启动。"
                    },
                    modifier = Modifier.padding(top = 15.dp),
                    theme = if (javaUsable) PclHintTheme.Yellow else PclHintTheme.Red,
                )
            }

            // ---- 整合包名称 / 整合包版本（同一行：Auto / 1* / Auto / 0.3*）
            PclCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                contentPadding = PaddingValues(start = 22.dp, top = 15.dp, end = 25.dp, bottom = 15.dp),
            ) {
                Row(
                    // PCL 是 `Grid Height="28"`；这里用 heightIn 而不是 height —— 万一行高被撑大也不会裁字
                    modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PclText(text = "整合包名称", modifier = Modifier.padding(end = 20.dp))
                    PclTextBox(
                        value = packName,
                        modifier = Modifier.weight(1f),
                        hint = version.getVersionName(),
                        onValueChange = { packName = it },
                    )
                    PclText(text = "整合包版本", modifier = Modifier.padding(start = 30.dp, end = 20.dp))
                    PclTextBox(
                        value = packVersion,
                        modifier = Modifier.weight(0.3f),
                        hint = "1.0.0",
                        onValueChange = { packVersion = it },
                    )
                }
            }

            // ---- 导出内容（PanOptions Margin="25,38,15,15"，右上角一个「重置」）
            PclCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                // PanOptions Margin="25,38,15,15"（上边距 38 是给右上角的「重置」让位）
                contentPadding = PaddingValues(start = 25.dp, top = 38.dp, end = 15.dp, bottom = 15.dp),
                titleAccessory = {
                    PclPillButton(
                        text = "重置",
                        logo = PclIcons.reset,
                        logoScale = 0.9f,
                        modifier = Modifier.padding(top = 4.dp, end = 5.dp),
                        onClick = {
                            options.forEach { option ->
                                checked[option.key] = if (option.forcedOn) true else option.defaultChecked
                            }
                        },
                    )
                },
            ) {
                options.forEach { option ->
                    if (!option.visible) return@forEach
                    if (option.visibleWhen != null && checked[option.visibleWhen] != true) return@forEach
                    key(option.key) {
                        PclExportOptionRow(
                            option = option,
                            checked = checked[option.key] == true,
                            onCheckedChange = { checked[option.key] = it },
                        )
                    }
                }
            }

            // ---- 高级选项（PCL：CanSwap=True IsSwapped=True → 默认折叠）
            PclCard(
                title = "高级选项",
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                collapsible = true,
                initiallyCollapsed = true,
                contentPadding = PaddingValues(start = 25.dp, top = 37.dp, end = 23.dp, bottom = 20.dp),
            ) {
                PclHint(
                    text = "不勾选「打包资源文件」时，导入方要联网把每个模组、资源包重新下一遍 ——" +
                            "作者删过源、CDN 换过直链的那些文件会在导入时报「Failed downloads」。" +
                            "想把整合包发给别人、又不想让对方卡在下载上，就勾上它。",
                    theme = PclHintTheme.Yellow,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                PclCheckBox(
                    text = "打包资源文件，以避免在导入时下载",
                    checked = advancedEmbed,
                    onCheckedChange = { advancedEmbed = it },
                )
                PclCheckBox(
                    text = "Modrinth 上传模式",
                    checked = advancedModrinth,
                    onCheckedChange = { advancedModrinth = it },
                )
                PclHint(
                    text = "勾选「Modrinth 上传模式」时导出 .mrpack，否则导出 .zip 压缩包。",
                    theme = PclHintTheme.Yellow,
                    modifier = Modifier.padding(start = 26.dp, top = 2.dp, bottom = 2.dp),
                )
                PclHint(
                    text = "「保存配置 / 读取配置」保存的是本页的勾选状态与填写的名称，方便下次复用。",
                    theme = PclHintTheme.Blue,
                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                )

                // Zalith 的导出器需要作者与描述，PCL 那边没有这两项，放在这里
                Spacer(Modifier.height(12.dp))
                PclExportLabeledText("整合包作者", author) { author = it }
                Spacer(Modifier.height(8.dp))
                PclExportLabeledText("整合包描述", summary) { summary = it }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // PCL 是三个 MinWidth=140 的按钮；手机上等分更稳，因此改成等宽
                    PclButton(
                        text = "读取配置",
                        colorState = PclColorState.Highlight,
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { readConfig.launch("*/*") },
                    )
                    PclButton(
                        text = "保存配置",
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { saveConfig.launch("PCL-Export.json") },
                    )
                    PclButton(
                        text = "整合包制作指南",
                        contentPadding = PaddingValues(horizontal = 13.dp),
                        modifier = Modifier.weight(1f),
                        onClick = { PclHintHost.push("整合包制作请参考 PCL 官方指南。", PclHintTheme.Blue) },
                    )
                }
            }

            Spacer(Modifier.height(PclDimens.cardGap))
        }

        // BtnExport：底部居中的悬浮胶囊（MyExtraTextButton，自身 Margin="20"）
        PclExtraTextButton(
            text = "开始导出",
            logo = PclIcons.instanceExport,
            enabled = javaUsable && !running,
            logoScale = 1.1f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            onClick = {
                // 默认文件名必须跟着导出格式走：选 Modrinth 却给 `.zip`，
                // 存出来的是一个披着 zip 皮的 mrpack，发给别人之后
                // 「按扩展名认格式」的启动器会当成普通压缩包处理，直接导不进去。
                createDocument.launch(
                    packFileName(
                        packName.ifBlank { version.getVersionName() },
                        packVersion.ifBlank { "1.0.0" },
                        if (advancedModrinth) "mrpack" else "zip",
                    ),
                )
            },
        )
    }
}

/** 选项行：`MyCheckBox Height="26"` + 「标题 + 灰色说明」，子项缩进 30。 */
@Composable
private fun PclExportOptionRow(
    option: PclExportOption,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val p = pclPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (option.indent) 30.dp else 0.dp)
            // PCL 是 `MyCheckBox Height="26"`。这里用 heightIn：字号被系统放大时行会跟着长，
            // 若写死 26dp 就会把字的下半截裁掉。
            .heightIn(min = 26.dp)
            .clickable(enabled = !option.forcedOn) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclCheckBox(
            text = "",
            checked = option.forcedOn || checked,
            enabled = !option.forcedOn,
            onCheckedChange = { onCheckedChange(it) },
        )
        PclText(text = option.title)
        option.description?.let { desc -> PclText(text = "   $desc", color = p.gray5) }
    }
}

/** 高级选项里的「标签 + 输入框」一行。 */
@Composable
private fun PclExportLabeledText(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PclText(text = label, modifier = Modifier.width(90.dp))
        Box(modifier = Modifier.weight(1f)) {
            PclTextBox(
                value = value,
                modifier = Modifier.fillMaxWidth(),
                onValueChange = onValueChange,
            )
        }
    }
}
