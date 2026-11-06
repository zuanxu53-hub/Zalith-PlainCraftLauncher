/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 「设置 → 插件」：资源包式的文件覆盖插件。
 *
 * 插件是一个普通 APK，里面放两样东西：
 * ```
 * assets/zplugin/plugin.json         清单（名字/版本/目标目录）
 * assets/zplugin/files/<相对路径>…   与目标目录同构的文件树
 * ```
 * 启用后这些文件被覆盖到目标目录，停用时按备份还原。
 * 启动器只把插件当**数据**读，从不加载它的 dex，所以插件没有能力执行代码；
 * 写入侧另有一层黑名单，禁止碰账号与登录凭据（见 `ZlPluginManager`）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zalith.pcl.game.plugin.ZlPlugin
import com.zalith.pcl.game.plugin.ZlPluginManager
import com.zalith.pcl.ui.pcl2.theme.PclUiRenderer
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime
import com.zalith.pcl.ui.pcl2.PclContent
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclHint
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclText
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「设置 → 插件管理」页的内容。
 *
 * 直接挂在设置页的右栏（`PclSetupRight` 的 index 8），不再单独 push 一个二级页 ——
 * 左侧栏已经有入口了，再叠一层返回按钮只会多一次点击。
 */
@Composable
fun PclPluginPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = pclPalette

    var plugins by remember { mutableStateOf<List<ZlPlugin>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ZlPlugin?>(null) }

    fun reload() {
        plugins = runCatching { ZlPluginManager.list(context) }.getOrDefault(emptyList())
    }

    // 自动识别：进页面就扫一遍「下载」文件夹、插件根目录和投递目录，
    // 认出来就直接装上，不需要用户去点选择文件。
    fun scan(announce: Boolean) {
        if (scanning) return
        scanning = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ZlPluginManager.scan(context) }.getOrNull()
            }
            scanning = false
            reload()

            if (result == null) {
                if (announce) PclHintHost.push("自动识别插件失败，换个方式再试。", PclHintTheme.Red)
                return@launch
            }
            val lines = buildList {
                if (result.installed.isNotEmpty()) {
                    add(
                        "已自动安装 ${result.installed.size} 个插件：" +
                                result.installed.joinToString("、") { it.name }
                    )
                }
                result.rejected.take(2).forEach { add(it) }
            }
            when {
                lines.isEmpty() && announce -> PclHintHost.push("没有发现新的插件 APK。", PclHintTheme.Blue)
                lines.isNotEmpty() -> PclHintHost.push(
                    lines.joinToString("\n"),
                    if (result.rejected.isEmpty()) PclHintTheme.Green else PclHintTheme.Red,
                )
            }
        }
    }

    // 首次进入（以及每次切回本页）自动扫一次；静默执行，没东西就不弹提示。
    LaunchedEffect(Unit) {
        reload()
        scan(announce = false)
    }

    // 插件列表一变（装 / 启用 / 停用 / 删除）就把界面皮肤同步过去：
    // 插件的开关就是皮肤开关，不用再点一次「应用」。
    LaunchedEffect(plugins) {
        val theme = withContext(Dispatchers.IO) {
            runCatching { ZlPluginManager.activeTheme(context) }.getOrNull()
        }
        PclUiRuntime.apply(theme?.json, theme?.id.orEmpty())
    }

    // 右栏统一用 PclContent：它自带内边距、滚动与「回顶」信号，
    // 自己再套一层 verticalScroll 会变成嵌套滚动，滚动手感很怪。
    PclContent {
        PclCard(modifier = Modifier.fillMaxWidth(), title = "自动识别") {
            Column {
                PclText(
                    text = "插件和 Minecraft 的资源包一个思路：每个插件是一个**独立 APK**" +
                            "（普通 APK，能装也能只当资源包用），里面放一棵和目标目录同构的文件树，" +
                            "启用后这些文件会覆盖过去。\n" +
                            "比如要改游戏目录下的 `config/1/a.txt`，就在插件里放 " +
                            "`assets/zplugin/files/config/1/a.txt`。",
                    fontSize = 12,
                )
                Spacer(Modifier.height(10.dp))
                PclText(
                    text = "不需要手动选文件。插件 APK 只要在下面任意一个位置，进入本页就会自动认出来并装好：\n" +
                            "· 已经装成应用的插件（最常见：点 APK 装完之后，安装包删了也认）\n" +
                            "· 系统「下载」文件夹 —— 下载完直接回来就行\n" +
                            "· 插件根目录 `plugins/`（散装 .apk）\n" +
                            "· 投递目录 `plugins/incoming/` —— 装完会把原件清掉",
                    fontSize = 12,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    PclButton(
                        text = if (scanning) "正在识别…" else "重新识别",
                        colorState = PclColorState.Highlight,
                        enabled = !scanning,
                        onClick = { scan(announce = true) },
                    )
                    if (scanning) {
                        PclText(text = "正在扫描插件 APK…", fontSize = 12, color = p.gray2)
                    }
                }
                // 「所有文件访问」没开时，下载文件夹与插件目录都读不到 ——
                // 那种情况下只有「已安装应用」那一条能用，得让用户知道，别以为功能坏了。
                if (!hasAllFilesAccess()) {
                    Spacer(Modifier.height(10.dp))
                    PclHint(
                        text = "当前没有「所有文件访问」权限，扫描不到下载文件夹与插件目录；" +
                                "已经装成应用的插件不受影响，照样能识别。" +
                                "想用文件夹那一套，请到系统设置里给本应用开启该权限。",
                        theme = PclHintTheme.Yellow,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PclHint(
                    text = "带 `assets/zplugin/ui.json` 的插件是**界面皮肤**：它不覆盖任何文件，" +
                            "只是把这份参数交给启动器渲染（例如液态玻璃）。同一时间只生效一个皮肤，" +
                            "同时启用多个时取列表里的第一个。\n" +
                            "判断标准只有一个：APK 里有没有 `assets/zplugin/plugin.json`。" +
                            "普通应用 APK 和改了后缀的压缩包不会被认成插件，也不会打扰你。" +
                            "启动器只读它 assets 里的文件，不会加载插件 APK 的代码，也不会执行它。" +
                            "安装时如果插件想写入账号数据库、登录令牌这类路径，整包会被拒绝。",
                    theme = PclHintTheme.Blue,
                )
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        PclCard(modifier = Modifier.fillMaxWidth(), title = "已安装（${plugins.size}）") {
            Column {
                if (plugins.isEmpty()) {
                    PclText(text = "还没有安装任何插件。", fontSize = 12, color = p.gray2)
                } else {
                    plugins.forEachIndexed { index, plugin ->
                        if (index > 0) {
                            Spacer(Modifier.height(10.dp))
                            PluginDivider(p.gray6)
                            Spacer(Modifier.height(10.dp))
                        }
                        PluginRow(
                            plugin = plugin,
                            onToggle = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching {
                                            ZlPluginManager.setEnabled(context, plugin, !plugin.enabled)
                                        }
                                    }
                                    ok.onSuccess {
                                        PclHintHost.push(
                                            if (!plugin.enabled) "已启用「${plugin.name}」。" else "已停用「${plugin.name}」，文件已还原。",
                                            PclHintTheme.Green,
                                        )
                                    }.onFailure {
                                        PclHintHost.push(it.message ?: "操作失败。", PclHintTheme.Red)
                                    }
                                    reload()
                                }
                            },
                            onDelete = { pendingDelete = plugin },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(PclDimens.cardGap))

        PclHint(
            text = "插件目录：`${ZlPluginManager.root(context).absolutePath}`\n" +
                    "每个插件一个子目录，里面是 plugin.apk、解包后的 files/ 与覆盖前的 backup/。" +
                    "停用会按 backup 还原；直接删掉插件目录等于卸载。",
            theme = PclHintTheme.Blue,
        )

        Spacer(Modifier.height(PclDimens.cardGap))
    }

    pendingDelete?.let { plugin ->
        PclConfirmDialog(
            title = "删除插件",
            content = "确定删除「${plugin.name}」吗？\n停用后它覆盖过的文件会还原成插件来之前的样子。",
            confirmText = "删除",
            danger = true,
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        runCatching { ZlPluginManager.remove(context, plugin) }
                    }
                    PclHintHost.push("已删除「${plugin.name}」。", PclHintTheme.Green)
                    reload()
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** 一条插件信息 + 两个操作。 */
@Composable
private fun PluginRow(
    plugin: ZlPlugin,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = pclPalette
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PclText(text = plugin.name, bold = true)
            PclText(
                text = buildString {
                    append(if (plugin.enabled) "已启用" else "已停用")
                    append(" · ")
                    if (plugin.isTheme) {
                        // 皮肤插件：不覆盖文件，只把 ui.json 交给启动器渲染
                        append("界面皮肤 · ")
                        append(plugin.ui.ifBlank { "自定义" })
                        PclUiRuntime.theme?.let { theme ->
                            append(" · ")
                            append(if (theme.renderer == PclUiRenderer.Glass) "玻璃" else "纯色")
                        }
                    } else {
                        append(ZlPluginManager.targetLabel(plugin.target))
                        append(" · ")
                        append("${plugin.fileCount} 个文件")
                    }
                    if (plugin.version.isNotBlank()) {
                        append(" · v")
                        append(plugin.version)
                    }
                },
                fontSize = 12,
                color = if (plugin.enabled) p.color3 else p.gray2,
            )
            if (plugin.description.isNotBlank()) {
                PclText(text = plugin.description, fontSize = 12, color = p.gray2)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PclButton(
                text = if (plugin.enabled) "停用" else "启用",
                colorState = if (plugin.enabled) PclColorState.Normal else PclColorState.Highlight,
                onClick = onToggle,
            )
            PclButton(
                text = "删除",
                colorState = PclColorState.Red,
                onClick = onDelete,
            )
        }
    }
}

/** 有没有「所有文件访问」权限（没有就只能靠「已安装应用」这条路识别插件）。 */
private fun hasAllFilesAccess(): Boolean =
    android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            android.os.Environment.isExternalStorageManager()

/** 细分割线。 */
@Composable
private fun PluginDivider(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color))
}
