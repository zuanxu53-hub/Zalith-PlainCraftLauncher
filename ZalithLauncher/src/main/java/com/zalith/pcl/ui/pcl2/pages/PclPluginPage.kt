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

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSubPage
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

/** 「插件」页。 */
fun pclPluginPage(): PclSubPage = PclSubPage(
    title = "插件",
    content = { PclPluginContent() },
)

@Composable
private fun PclPluginContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = pclPalette

    var plugins by remember { mutableStateOf<List<ZlPlugin>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ZlPlugin?>(null) }

    fun reload() {
        plugins = runCatching { ZlPluginManager.list(context) }.getOrDefault(emptyList())
    }

    LaunchedEffect(Unit) { reload() }

    // 选 APK：不同文件管理器给的 mime 五花八门，直接收 */*，真正的格式判断由管理器做
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (busy) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ZlPluginManager.install(context, uri) }
            }
            busy = false
            result.onSuccess { plugin ->
                PclHintHost.push(
                    "已安装「${plugin.name}」${plugin.version.ifBlank { "" }}，${plugin.fileCount} 个文件已覆盖到" +
                            ZlPluginManager.targetLabel(plugin.target) + "。",
                    PclHintTheme.Green,
                )
                reload()
            }.onFailure { e ->
                PclHintHost.push(e.message ?: "插件安装失败。", PclHintTheme.Red)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
    ) {
        PclCard(modifier = Modifier.fillMaxWidth(), title = "安装插件") {
            Column {
                PclText(
                    text = "插件和 Minecraft 的资源包一个思路：一个插件就是一个普通 APK，" +
                            "里面放一棵和目标目录同构的文件树，启用后这些文件会覆盖过去。\n" +
                            "比如要改游戏目录下的 `config/1/a.txt`，就在插件里放 " +
                            "`assets/zplugin/files/config/1/a.txt`。",
                    fontSize = 12,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    PclButton(
                        text = if (busy) "正在安装…" else "选择插件 APK",
                        colorState = PclColorState.Highlight,
                        enabled = !busy,
                        onClick = { picker.launch(arrayOf("*/*")) },
                    )
                    PclButton(text = "刷新列表", enabled = !busy, onClick = { reload() })
                }
                Spacer(Modifier.height(10.dp))
                PclHint(
                    text = "插件只是文件，启动器不会加载插件 APK 里的代码，也不会执行它。" +
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
                    append(ZlPluginManager.targetLabel(plugin.target))
                    append(" · ")
                    append("${plugin.fileCount} 个文件")
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

/** 细分割线。 */
@Composable
private fun PluginDivider(color: Color) {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color))
}
