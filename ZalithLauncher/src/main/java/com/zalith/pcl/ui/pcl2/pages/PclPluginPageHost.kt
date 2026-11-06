/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 插件自定义页面的**渲染器**：把 `pages.json` 里的积木翻译成 PCL 的原生控件。
 *
 * 刻意全部用现成的 `PclCard` / `PclButton` / `PclSwitchItem` / `PclTextStyle`，
 * 而不是自己画一套 —— 这样插件页面和内置页面**长得一模一样**，
 * 换皮肤、换缩放、换语言都自动跟着走，不需要插件操心。
 */

package com.zalith.pcl.ui.pcl2.pages

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalith.pcl.ui.pcl2.LocalPclNav
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclSection
import com.zalith.pcl.ui.pcl2.PclSubPage
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclSwitchItem
import com.zalith.pcl.ui.pcl2.data.PclPluginAction
import com.zalith.pcl.ui.pcl2.data.PclPluginBlock
import com.zalith.pcl.ui.pcl2.data.PclPluginPageSpec
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.PclUiRuntime
import com.zalith.pcl.ui.pcl2.theme.pclPalette

/**
 * 把一个插件页面包装成启动器的二级页面。
 *
 * @param all 同一份 `pages.json` 里的**全部**页面 —— `{"type":"page"}` 动作要靠它找目标。
 */
fun pclPluginSubPage(spec: PclPluginPageSpec): PclSubPage = PclSubPage(
    title = spec.title,
    leftLabels = spec.leftLabels,
    leftContent = if (spec.leftLabels.isEmpty()) null else ({ PclPluginLeft(spec.leftLabels) }),
    content = { PclPluginPageInline(spec) },
)

@Composable
private fun PclPluginLeft(labels: List<String>) {
    val p = pclPalette
    Column(modifier = Modifier.fillMaxSize().padding(top = 6.dp)) {
        labels.forEach {
            Text(
                text = it,
                style = PclTextStyle.listTitle(p),
                modifier = Modifier.padding(vertical = 9.dp),
            )
        }
    }
}

/**
 * 把一页积木渲染到当前容器里。
 *
 * 两个地方用它：二级页（[pclPluginSubPage]）和**布局槽位**（插件把某个版块的
 * 左栏 / 内容区换成自己的页面，见 `PclUiTheme.layout`）。
 * 页面清单从 [PclUiRuntime.pages] 取 —— 那里是全局加载好的，不用每次读文件。
 */
@Composable
fun PclPluginPageInline(spec: PclPluginPageSpec) {
    val all = PclUiRuntime.pages
    val nav = LocalPclNav.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        spec.blocks.forEach { PclPluginBlockView(it, all, nav::push, nav::go) }
    }
}

@Composable
private fun PclPluginBlockView(
    block: PclPluginBlock,
    all: List<PclPluginPageSpec>,
    onPush: (PclSubPage) -> Unit,
    onSection: (PclSection) -> Unit,
) {
    val p = pclPalette
    when (block) {
        is PclPluginBlock.Title -> Text(
            text = block.text,
            style = PclTextStyle.listTitle(p).copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.color1,
            ),
        )

        is PclPluginBlock.Text -> Text(
            text = block.text,
            style = PclTextStyle.base(p).copy(color = if (block.muted) p.gray2 else p.color1),
        )

        is PclPluginBlock.Card -> PclCard(
            title = block.title,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                block.blocks.forEach { PclPluginBlockView(it, all, onPush, onSection) }
            }
        }

        is PclPluginBlock.Row -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            block.blocks.forEach { child ->
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    PclPluginBlockView(child, all, onPush, onSection)
                }
            }
        }

        is PclPluginBlock.Button -> PclButton(
            text = block.text,
            onClick = { runPclPluginAction(block.action, all, onPush, onSection) },
        )

        is PclPluginBlock.Switch -> {
            // 状态只活在这一次页面停留期间：插件没有落盘能力，
            // 需要记住的偏好应该让插件自己写进它自己的目录（files/ 下）。
            var checked by remember(block.text) { mutableStateOf(block.checked) }
            PclSwitchItem(
                title = block.text,
                checked = checked,
                modifier = Modifier.fillMaxWidth(),
                onCheckedChange = {
                    checked = it
                    runPclPluginAction(block.action, all, onPush, onSection)
                },
            )
        }

        PclPluginBlock.Divider -> Box(
            modifier = Modifier.fillMaxWidth().height(1.dp).background(p.color6),
        )

        is PclPluginBlock.Space -> Spacer(Modifier.height(block.height.dp))
    }
}

private fun runPclPluginAction(
    action: PclPluginAction?,
    all: List<PclPluginPageSpec>,
    onPush: (PclSubPage) -> Unit,
    onSection: (PclSection) -> Unit,
) {
    when (action) {
        null -> Unit
        is PclPluginAction.Hint -> PclHintHost.push(action.text, PclHintTheme.Blue)
        is PclPluginAction.Page -> all.firstOrNull { it.id == action.id }
            ?.let { onPush(pclPluginSubPage(it)) }
        is PclPluginAction.Section -> when (action.value.lowercase()) {
            "home" -> onSection(PclSection.Home)
            "download" -> onSection(PclSection.Download)
            "link" -> onSection(PclSection.Link)
            "setup" -> onSection(PclSection.Setup)
            "other" -> onSection(PclSection.Other)
            else -> Unit
        }
    }
}
