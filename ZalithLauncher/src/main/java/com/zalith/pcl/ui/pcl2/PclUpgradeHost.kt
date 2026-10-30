/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 启动器更新的全局弹窗宿主。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zalith.pcl.BuildConfig
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.pcl2.components.PclSelectDialog
import com.zalith.pcl.ui.pcl2.components.PclUpgradeDialog
import com.zalith.pcl.ui.pcl2.components.PclUpgradeDownloadDialog
import com.zalith.pcl.ui.pcl2.components.PclUpgradeFilesDialog
import com.zalith.pcl.ui.pcl2.components.pclMarkdownToPlain
import com.zalith.pcl.ui.pcl2.data.PclUpdateSources
import com.zalith.pcl.upgrade.RemoteData
import com.zalith.pcl.upgrade.findCurrentBody
import com.zalith.pcl.upgrade.getCurrentCouldDrive
import com.zalith.pcl.viewmodel.LauncherUpgradeOperation
import com.zalith.pcl.viewmodel.LauncherUpgradeViewModel
import java.util.Locale

/**
 * 启动器更新弹窗（PCL 界面）。
 *
 * ---- 为什么必须单独挂一个宿主
 *
 * 上游只有 `MainActivity` 会渲染 [LauncherUpgradeOperation]，而 PCL 界面跑在
 * `Pcl2Activity` —— 于是「关于」页点「检查更新」时，`LauncherUpgradeViewModel` 确实
 * 抓到了新版本、也确实把 `operation` 设成了 `Upgrade`，但**没有任何地方去画它**，
 * 表现就是「点了完全没反应，连个弹窗都没有」。
 *
 * 这里挂在 `PclMainContent` 上（与 `PclNamePromptHost` 同一层），所以：
 *   · 每次进入应用自动检查（[LauncherUpgradeViewModel.checkOnAppStart]）→ 本宿主弹出更新框；
 *   · 「关于」页手动检查 → 同一个框。
 *
 * 用的是 **Activity 作用域的同一个 ViewModel 实例**（`viewModel()` 默认取
 * `LocalViewModelStoreOwner`，与「关于」页里拿到的是同一个），不需要任何额外转发。
 */
@Composable
fun PclUpgradeHost() {
    val context = LocalContext.current
    val upgradeViewModel: LauncherUpgradeViewModel = viewModel()

    // 每次进入都查一次（ViewModel 内部对同一个实例只放行一次，不会因重组重复请求）。
    LaunchedEffect(Unit) {
        upgradeViewModel.checkOnAppStart()
    }

    // 预热官网的网盘入口：这样「发现新版本」弹出时按钮已经是最终形态，
    // 不会先三按钮、零点几秒后变两行（看起来像闪一下）。
    LaunchedEffect(Unit) {
        PclUpdateSources.fetch()
    }

    // 从「安装未知应用」授权页回来时继续安装。系统设置页是另一个 Activity，
    // 我们这边只会收到 ON_RESUME，所以只能靠生命周期回调来接。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val state = PclLauncherUpdate.state
                if (state is PclLauncherUpdateState.NeedPermission && PclLauncherUpdate.hasPendingInstall) {
                    PclLauncherUpdate.retryInstall(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    when (val operation = upgradeViewModel.operation) {
        LauncherUpgradeOperation.None -> {}

        is LauncherUpgradeOperation.Upgrade -> {
            val data = operation.data
            val body = remember(data) {
                data.findCurrentBody(Locale.getDefault()) ?: data.defaultBody
            }
            // 官网「下载方式」里配了 123 云盘就多给一个按钮。首帧先用缓存（断网也有），
            // 联网拉到新地址就替换 —— 后台改链接不用重新发版。
            var cloudDrive by remember(data) { mutableStateOf(PclUpdateSources.cachedSource()) }
            LaunchedEffect(data) {
                PclUpdateSources.fetch()?.let { cloudDrive = it }
            }
            // 按钮回调里已经把 operation 推到下一步（或清空）了，退出动画结束时的
            // `onLater` 就不能再写一次 —— 否则会把刚设置的 SelectApk 覆盖成 None，
            // 「立即更新」点了什么都不发生（与 PclConfirmDialog 那条注释同一个坑）。
            //
            // 以前的判据是一个 `navigated` 标志，但它只在按钮里置 true、弹窗被复用时不会复位，
            // 于是「按返回 / 稍后」有可能清不掉 operation，弹窗停在 exiting 状态不走。
            // 现在直接比对象：**只有 operation 还停在本弹窗这一档时才清空**，语义天然正确。
            PclUpgradeDialog(
                versionText = "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）" +
                    "  →  最新版本 ${data.version}（${data.code}）",
                bodyText = pclMarkdownToPlain(body.markdown),
                onDownload = {
                    val files = data.files
                    when {
                        // 资产没取到（GitHub API 不通 / 还没上传）→ 退回网盘
                        files.isEmpty() -> {
                            val drive = data.currentCloudDrive()
                            upgradeViewModel.operation = if (drive == null) {
                                LauncherUpgradeOperation.None
                            } else {
                                LauncherUpgradeOperation.OpenCloudDrive(drive)
                            }
                        }
                        files.size == 1 -> {
                            upgradeViewModel.operation = LauncherUpgradeOperation.None
                            PclLauncherUpdate.start(context, data.version, files.first())
                        }
                        else -> upgradeViewModel.operation = LauncherUpgradeOperation.SelectApk(data)
                    }
                },
                onIgnore = {
                    AllSettings.lastIgnoredVersion.save(data.code)
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
                onLater = {
                    if (upgradeViewModel.operation === operation) {
                        upgradeViewModel.operation = LauncherUpgradeOperation.None
                    }
                },
                cloudButtonText = cloudDrive?.name,
                // 「立即更新」是直接装；网盘是给下不动 / 想留一份的人的备选入口，
                // 点了就用系统浏览器打开，安装包在网盘里自己取。
                onCloudButton = {
                    val target = cloudDrive
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                    if (target != null) openLink(context, target.url)
                },
            )
        }

        is LauncherUpgradeOperation.SelectApk -> {
            val data = operation.data
            val deviceArch = remember { currentLauncherArch() }
            // 选了架构这一步同样给网盘入口：有人点到这儿才发现 GitHub 下不动。
            var cloudDrive by remember(data) { mutableStateOf(PclUpdateSources.cachedSource()) }
            LaunchedEffect(data) {
                PclUpdateSources.fetch()?.let { cloudDrive = it }
            }
            // 当前设备架构排在最前面，并默认打勾：绝大多数人直接点第一项就是对的。
            // 没有对应架构时退回 universal。
            val ordered = remember(data, deviceArch) {
                data.files.sortedBy { if (it.arch == deviceArch) 0 else 1 }
            }
            val defaultFile = remember(ordered, deviceArch) {
                ordered.firstOrNull { it.arch == deviceArch }?.fileName
                    ?: ordered.firstOrNull { it.arch == RemoteData.RemoteFile.Arch.ALL }?.fileName
            }
            PclUpgradeFilesDialog(
                title = "选择要下载的安装包（${data.version}）",
                // 只显示「版本 · 架构」，不显示 GitHub 的长链接
                options = ordered.map { it.fileName to "${data.version} · ${it.arch.abiText()}" },
                current = defaultFile,
                onSelect = { fileName ->
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                    ordered.firstOrNull { it.fileName == fileName }?.let {
                        PclLauncherUpdate.start(context, data.version, it)
                    }
                },
                onDismiss = { upgradeViewModel.operation = LauncherUpgradeOperation.None },
                extraButtonText = cloudDrive?.name,
                onExtraButton = {
                    val target = cloudDrive
                    if (target != null) openLink(context, target.url)
                },
            )
        }

        is LauncherUpgradeOperation.OpenCloudDrive -> {
            val drive = operation.cloudDrive
            val options = if (drive.links.isEmpty()) {
                listOf("默认链接" to drive.link)
            } else {
                drive.links.map { it.name to it.link }
            }
            PclSelectDialog(
                title = "选择网盘下载",
                options = options,
                current = null,
                onSelect = { link ->
                    openLink(context, link)
                    upgradeViewModel.operation = LauncherUpgradeOperation.None
                },
                onDismiss = { upgradeViewModel.operation = LauncherUpgradeOperation.None },
            )
        }
    }

    // ---- 下载 / 安装过程（与上面的「检查更新」各阶段互斥，但独立渲染更省心）
    when (val update = PclLauncherUpdate.state) {
        PclLauncherUpdateState.Idle -> {}

        is PclLauncherUpdateState.Downloading -> {
            val total = update.total
            val progress = if (total > 0L) {
                (update.downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            PclUpgradeDownloadDialog(
                title = "正在下载启动器更新",
                versionText = "${update.version} · ${update.archText}",
                detailText = buildString {
                    append(formatUpdateSize(update.downloaded))
                    if (total > 0L) {
                        append(" / ").append(formatUpdateSize(total))
                        append("（").append((progress * 100).toInt()).append("%）")
                    }
                    if (update.speed > 0L) {
                        append(" · ").append(formatUpdateSize(update.speed)).append("/s")
                    }
                    append("\n下载完成后会自动打开安装界面。")
                },
                progress = progress,
                onCancel = { PclLauncherUpdate.cancel() },
            )
        }

        is PclLauncherUpdateState.Failed -> {
            PclConfirmDialog(
                title = "更新下载失败",
                content = "${update.version} · ${update.archText}\n\n${update.message}\n\n" +
                    "网络不稳时重试通常就好了；也可以点「取消」后到 GitHub Releases 页手动下载。",
                confirmText = "重试",
                dismissText = "取消",
                danger = true,
                onConfirm = { PclLauncherUpdate.retry(context) },
                onDismiss = { PclLauncherUpdate.dismiss() },
            )
        }

        is PclLauncherUpdateState.NeedPermission -> {
            PclConfirmDialog(
                title = "需要安装权限",
                content = "安装包已经下载好了（${update.version} · ${update.archText}）。\n\n" +
                    "系统要求先允许本应用「安装未知应用」，授权后会自动继续安装。",
                confirmText = "去授权",
                dismissText = "取消",
                onConfirm = { PclLauncherUpdate.openInstallSettings(context) },
                onDismiss = { PclLauncherUpdate.dismiss() },
            )
        }
    }
}

/** 当前语言对应的网盘；没配多网盘时退回 [RemoteData.defaultCloudDrive]。 */
private fun RemoteData.currentCloudDrive(): RemoteData.CloudDrive? {
    return getCurrentCouldDrive(Locale.getDefault()) ?: defaultCloudDrive
}
