/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.zalith.pcl.ui.screens.splash

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zalith.pcl.BuildKeys
import com.zalith.pcl.R
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.base.BaseScreen
import com.zalith.pcl.ui.components.BackgroundCard
import com.zalith.pcl.ui.components.MarqueeText
import com.zalith.pcl.ui.components.ScalingActionButton
import com.zalith.pcl.ui.pcl2.components.PclButton
import com.zalith.pcl.ui.pcl2.components.PclCard
import com.zalith.pcl.ui.pcl2.components.PclColorState
import com.zalith.pcl.ui.pcl2.components.PclListItem
import com.zalith.pcl.ui.pcl2.data.PclThemeState
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.PclTheme
import com.zalith.pcl.ui.pcl2.theme.pclPalette
import com.zalith.pcl.ui.screens.NormalNavKey
import com.zalith.pcl.ui.theme.itemColor
import com.zalith.pcl.ui.theme.onItemColor
import com.zalith.pcl.utils.animation.getAnimateTween
import com.zalith.pcl.utils.animation.swapAnimateDpAsState
import com.zalith.pcl.viewmodel.SplashBackStackViewModel

@Composable
fun UnpackScreen(
    items: List<InstallableItem>,
    screenViewModel: SplashBackStackViewModel,
    onAgreeClick: () -> Unit = {}
) {
    if (AllSettings.usePcl2Ui.state) {
        // z-pcl：解压依赖页也按 PCL2 样式渲染（这是进启动器前的第一屏，
        // 不做的话用户会先看到一屏原生 Material 再「跳」进 PCL 界面）
        PclUnpackContent(items = items, onAgreeClick = onAgreeClick)
    } else {
        BaseScreen(
            screenKey = NormalNavKey.UnpackDeps,
            currentKey = screenViewModel.splashScreen.currentKey
        ) { isVisible ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                UnpackTaskList(
                    isVisible = isVisible,
                    items = items,
                    modifier = Modifier
                        .weight(7f)
                        .fillMaxHeight()
                )

                ActionMenu(
                    isVisible = isVisible,
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight(),
                    onAgreeClick = onAgreeClick
                )
            }
        }
    }
}

/**
 * PCL2 样式的解压依赖页。
 *
 * 布局与原生版一致（左列表 + 右说明/按钮），但全部换成 PCL 控件与配色：
 * 底色 #F5F5F5、PclCard 列表、PclListItem 行、右下角 Highlight 描边按钮。
 */
@Composable
private fun PclUnpackContent(
    items: List<InstallableItem>,
    onAgreeClick: () -> Unit,
) {
    PclTheme(palette = PclThemeState.palette) {
        val p = pclPalette
        // 只有用户点了「同意并安装」之后按钮才变成「正在安装」并置灰。
        // 这一屏**不再自动开始**：装什么、占多少空间由用户点这一下决定。
        var clicked by remember { mutableStateOf(false) }
        val installing = clicked

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
        ) {
            // 顶栏：居中启动器名（与原生 SplashScreen 的 TopBar 对位）
            Box(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = BuildKeys.LAUNCHER_NAME, style = PclTextStyle.base(p))
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PclCard(
                    title = stringResource(R.string.splash_screen_title),
                    modifier = Modifier
                        .weight(7f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                    ) {
                        items.forEach { item ->
                            PclUnpackItemRow(item = item)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (installing) {
                                stringResource(R.string.splash_screen_installing)
                            } else {
                                stringResource(R.string.splash_screen_unpack_desc)
                            },
                            style = PclTextStyle.base(p)
                        )
                    }

                    PclButton(
                        text = stringResource(R.string.splash_screen_agree),
                        enabled = !installing,
                        colorState = PclColorState.Highlight,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            clicked = true
                            onAgreeClick()
                        }
                    )
                }
            }
        }
    }
}

/** 单个解压项：PCL 列表行，标题 = 组件名，副标题 = 说明 / 实时任务消息，状态跟在标题后。 */
@Composable
private fun PclUnpackItemRow(item: InstallableItem) {
    val state by item.state.collectAsStateWithLifecycle()
    val message by item.task.taskMessage.collectAsStateWithLifecycle()
    val p = pclPalette

    val info = when (state) {
        InstallableItem.State.RUNNING -> message ?: stringResource(R.string.generic_unpacking, item.name)
        else -> item.summary
    }
    val suffix = when (state) {
        InstallableItem.State.PENDING -> stringResource(R.string.unpack_state_pending)
        InstallableItem.State.RUNNING -> stringResource(R.string.unpack_state_running)
        InstallableItem.State.FINISHED -> stringResource(R.string.unpack_state_finished)
        else -> null
    }

    PclListItem(
        title = item.name,
        info = info,
        titleSuffix = suffix,
        onClick = null,
        logoContent = {
            when (state) {
                InstallableItem.State.RUNNING -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                InstallableItem.State.FINISHED -> Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = p.color3
                )
                else -> Icon(
                    painter = painterResource(R.drawable.ic_folder_zip_outlined),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = p.gray3
                )
            }
        }
    )
}

@Composable
private fun ActionMenu(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    onAgreeClick: () -> Unit = {},
) {
    var clicked by remember { mutableStateOf(false) }
    val installing = clicked

    val xOffset by swapAnimateDpAsState(
        targetValue = 40.dp,
        swapIn = isVisible,
        isHorizontal = true
    )

    Column(
        modifier = modifier.offset { IntOffset(x = xOffset.roundToPx(), y = 0) },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(state = rememberScrollState())
                .weight(1f)
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = if (installing) {
                    stringResource(R.string.splash_screen_installing)
                } else {
                    stringResource(R.string.splash_screen_unpack_desc)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        ScalingActionButton(
            enabled = !installing,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                clicked = true
                onAgreeClick()
            }
        ) {
            MarqueeText(text = stringResource(R.string.splash_screen_agree))
        }
    }
}

@Composable
private fun UnpackTaskList(
    isVisible: Boolean,
    items: List<InstallableItem>,
    modifier: Modifier = Modifier,
) {
    val yOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )

    BackgroundCard(
        modifier = modifier.offset { IntOffset(x = 0, y = yOffset.roundToPx()) },
        influencedByBackground = false,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        val scrollState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nonInteractiveScrollbar(
                    state = scrollState.scrollIndicatorState!!,
                    orientation = Orientation.Vertical,
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            state = scrollState,
        ) {
            items(items) { item ->
                TaskItem(
                    item = item,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TaskItem(
    item: InstallableItem,
    modifier: Modifier = Modifier
) {
    val state by item.state.collectAsStateWithLifecycle()
    val message by item.task.taskMessage.collectAsStateWithLifecycle()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = itemColor(),
        contentColor = onItemColor(),
    ) {
        Row {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .animateContentSize(animationSpec = getAnimateTween())
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium
                )
                item.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (state == InstallableItem.State.RUNNING) {
                    message?.let { taskMessage ->
                        Text(
                            text = taskMessage,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }

            val iconModifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(PaddingValues(horizontal = 12.dp, vertical = 8.dp))
                .size(18.dp)
            when (state) {
                InstallableItem.State.NOT_STARTED -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_folder_zip_outlined),
                        contentDescription = null
                    )
                }
                InstallableItem.State.PENDING -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_update),
                        contentDescription = null
                    )
                }
                InstallableItem.State.RUNNING -> {
                    CircularProgressIndicator(
                        modifier = iconModifier,
                        strokeWidth = 2.dp
                    )
                }
                InstallableItem.State.FINISHED -> {
                    Icon(
                        modifier = iconModifier,
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null
                    )
                }
                else -> {}
            }
        }
    }
}
