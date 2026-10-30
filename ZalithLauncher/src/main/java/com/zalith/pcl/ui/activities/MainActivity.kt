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

package com.zalith.pcl.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.movtery.guide.GuideHost
import com.zalith.pcl.R
import com.zalith.pcl.context.COPY_LABEL_LINK
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.filemanager.FileManagerLauncher
import com.zalith.pcl.filemanager.events.FileManagerEvent
import com.zalith.pcl.filemanager.events.FileManagerEventRegistrar
import com.zalith.pcl.game.control.ControlManager
import com.zalith.pcl.game.path.getVersionsHome
import com.zalith.pcl.game.plugin.PluginLoader
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionsManager
import com.zalith.pcl.notification.NotificationManager
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.path.URL_SUPPORT
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.AndroidStringText
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.base.BaseAppCompatActivity
import com.zalith.pcl.ui.base.ObserveFullScreenSetting
import com.zalith.pcl.ui.buildAppendedText
import com.zalith.pcl.ui.components.SimpleAlertDialog
import com.zalith.pcl.ui.guide.NextTipLabel
import com.zalith.pcl.ui.guide.rememberAppGuides
import com.zalith.pcl.ui.screens.NestedNavKey
import com.zalith.pcl.ui.screens.NormalNavKey
import com.zalith.pcl.ui.screens.content.elements.Background
import com.zalith.pcl.ui.screens.content.elements.LaunchGameOperation
import com.zalith.pcl.ui.screens.content.elements.TitleTaskFlowDialog
import com.zalith.pcl.ui.screens.content.navigateToLogView
import com.zalith.pcl.ui.screens.content.navigateToWeb
import com.zalith.pcl.ui.screens.main.MainScreen
import com.zalith.pcl.ui.screens.main.crashlogs.LogShareMenu
import com.zalith.pcl.ui.screens.main.crashlogs.LogShareMenuOperation
import com.zalith.pcl.ui.screens.main.crashlogs.ShareLinkOperation
import com.zalith.pcl.ui.theme.ZalithLauncherTheme
import com.zalith.pcl.ui.theme.festivals.FestivalEffects
import com.zalith.pcl.ui.theme.festivals.FestivalTapObserver
import com.zalith.pcl.ui.theme.showThemed
import com.zalith.pcl.ui.toAndroidString
import com.zalith.pcl.ui.vulkan_checker.VCOperation
import com.zalith.pcl.ui.vulkan_checker.VulkanChecker
import com.zalith.pcl.upgrade.TooFrequentOperationException
import com.zalith.pcl.utils.compareLangTag
import com.zalith.pcl.utils.copyText
import com.zalith.pcl.utils.festival.getTodayFestivals
import com.zalith.pcl.utils.file.shareFile
import com.zalith.pcl.utils.isChinese
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.openLink
import com.zalith.pcl.utils.network.openLinkInternal
import com.zalith.pcl.utils.string.getMessageOrToString
import com.zalith.pcl.viewmodel.BackgroundViewModel
import com.zalith.pcl.viewmodel.ErrorViewModel
import com.zalith.pcl.viewmodel.EventViewModel
import com.zalith.pcl.viewmodel.LaunchGameViewModel
import com.zalith.pcl.viewmodel.LauncherUpgradeOperation
import com.zalith.pcl.viewmodel.LauncherUpgradeViewModel
import com.zalith.pcl.viewmodel.LogShareViewModel
import com.zalith.pcl.viewmodel.LogsUploadViewModel
import com.zalith.pcl.viewmodel.ModifyVersionOperation
import com.zalith.pcl.viewmodel.ModifyVersionViewModel
import com.zalith.pcl.viewmodel.ModpackConfirmUseMobileDataOperation
import com.zalith.pcl.viewmodel.ModpackImportOperation
import com.zalith.pcl.viewmodel.ModpackImportViewModel
import com.zalith.pcl.viewmodel.ModpackVersionNameOperation
import com.zalith.pcl.viewmodel.ScreenBackStackViewModel
import com.zalith.pcl.viewmodel.VulkanCheckerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = AllSettings.launcherFullScreen.getValue()

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        FestivalTapObserver.observe(event)
        return super.dispatchTouchEvent(event)
    }

    /**
     * 屏幕堆栈管理ViewModel
     */
    private val screenBackStackModel: ScreenBackStackViewModel by viewModels()

    /**
     * 启动游戏ViewModel
     */
    private val launchGameViewModel: LaunchGameViewModel by viewModels()

    /**
     * 错误信息ViewModel
     */
    private val errorViewModel: ErrorViewModel by viewModels()

    /**
     * 与Compose交互的事件ViewModel
     */
    val eventViewModel: EventViewModel by viewModels()

    /**
     * 启动器背景内容管理 ViewModel
     */
    val backgroundViewModel: BackgroundViewModel by viewModels()

    /**
     * 整合包导入 ViewModel
     */
    val modpackImportViewModel: ModpackImportViewModel by viewModels()

    /**
     * 版本修改 ViewModel
     */
    private val modifyVersionViewModel: ModifyVersionViewModel by viewModels()

    /**
     * 启动器更新状态 ViewModel
     */
    val launcherUpgradeViewModel: LauncherUpgradeViewModel by viewModels()

    /**
     * 游戏日志分享菜单 ViewModel
     */
    private val logShareViewModel: LogShareViewModel by viewModels()

    /**
     * 游戏日志上传 ViewModel
     */
    private val logsUploadViewModel: LogsUploadViewModel by viewModels()

    /**
     * Vulkan检测状态 ViewModel
     */
    private val vulkanCheckerViewModel: VulkanCheckerViewModel by viewModels()

    /**
     * 是否开启捕获按键模式
     */
    private var isCaptureKey = false

    /**
     * 文件管理器事件监听
     */
    private var fmEventRegistrar: FileManagerEventRegistrar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //处理外部导入
        val isImporting = handleImportIfNeeded(intent)

        //加载渲染器
        Renderers.init()
        //加载插件
        PluginLoader.loadAllPlugins(this, false)
        refreshData()
        // 每次打开启动器都自检一遍 Java 运行库：已有可用项直接返回，
        // 一个都没有时自动装（某些机型首次安装时解压被打断，Java 列表会是空的）
        lifecycleScope.launch {
            val report = RuntimeProvisioner.ensure(this@MainActivity, RuntimeProvisioner.Mode.REPAIR)
            val message = when {
                report.installed.isNotEmpty() ->
                    "已自动安装内置 Java 运行库：${report.installed.joinToString("、")}"

                !report.hasUsable && report.failed.isNotEmpty() ->
                    "Java 运行库不可用：${report.failed.first().second}"

                // 内置包与本机架构对不上（例如只打包了 arm 的 APK 跑在 x86 设备上）：
                // 不会自己好，必须告诉用户去哪里补
                !report.hasUsable && report.skipped.isNotEmpty() ->
                    "本机架构没有配套的内置 Java，请到「设置 → Java 运行库」导入 .xz 运行库。"

                else -> null
            }
            message?.let {
                Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
            }
        }

        //注册文件管理器事件监听
        fmEventRegistrar = FileManagerEventRegistrar(this, ::onFileManagerEvent).also { it.start() }

        //初始化通知管理（创建渠道）
        NotificationManager.initManager(this)

        //检查更新
        if (!isImporting && launcherUpgradeViewModel.operation == LauncherUpgradeOperation.None) {
            lifecycleScope.launch {
                launcherUpgradeViewModel.checkOnAppStart()
            }
        }

        //错误信息展示
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                errorViewModel.errorEvents.collect { tm ->
                    errorViewModel.showErrorDialog(
                        context = this@MainActivity,
                        tm = tm
                    )
                }
            }
        }

        //事件处理
        lifecycleScope.launch {
            eventViewModel.events.collect { event ->
                when (event) {
                    is EventViewModel.Event.Key.StartKeyCapture -> {
                        Logger.info("CollectEvent", "Start key capture!")
                        isCaptureKey = true
                    }
                    is EventViewModel.Event.Key.StopKeyCapture -> {
                        Logger.info("CollectEvent", "Stop key capture!")
                        isCaptureKey = false
                    }
                    is EventViewModel.Event.OpenLink -> {
                        val url = event.url
                        withContext(Dispatchers.Main) {
                            this@MainActivity.openLink(url)
                        }
                    }
                    is EventViewModel.Event.CheckUpdate -> {
                        checkUpdate()
                    }
                    is EventViewModel.Event.KeepScreen -> {
                        keepScreen(event.on)
                    }
                    is EventViewModel.Event.ImportControls -> {
                        importControlFiles(event.uris)
                    }
                    is EventViewModel.Event.DownloadPlugins -> {
                        showDownloadPlugins(event.link)
                    }
                    is EventViewModel.Event.Launch.Game -> {
                        launchGameViewModel.tryLaunch(event.version)
                    }
                    is EventViewModel.Event.Launch.PlayServer -> {
                        launchGameViewModel.quickPlayServer(event.version, event.address)
                    }
                    is EventViewModel.Event.Launch.PlaySave -> {
                        launchGameViewModel.quickPlaySave(event.version, event.saveName)
                    }
                    is EventViewModel.Event.LogShare.ShareGameLog -> {
                        val file = event.logFile
                        if (file.exists()) {
                            logsUploadViewModel.check(file)
                            logShareViewModel.openMenu(file)
                        }
                    }
                    is EventViewModel.Event.VulkanCheck -> {
                        checkVulkan(event.version)
                    }
                    is EventViewModel.Event.ShowToast -> {
                        Toast.makeText(
                            this@MainActivity,
                            event.text.toAndroidString(this@MainActivity),
                            event.duration
                        ).show()
                    }
                    is EventViewModel.Event.OpenFileManager -> {
                        FileManagerLauncher.launch(
                            context = this@MainActivity,
                            rootPath = event.rootPath,
                            currentPath = event.currentPath,
                            logsDir = PathManager.DIR_LAUNCHER_LOGS.absolutePath
                        )
                    }
                    else -> {
                        //忽略
                    }
                }
            }
        }

        val finishedGame = AllSettings.finishedGame
        val showSponsorship = AllSettings.showSponsorship

        val festivals = getTodayFestivals(
            containsChinese = isChinese(this@MainActivity)
        )

        setContent {
            ZalithLauncherTheme(
                backgroundViewModel = backgroundViewModel,
                festivals = festivals
            ) {
                ObserveFullScreenSetting(AllSettings.launcherFullScreen.state)

                val guides = rememberAppGuides(eventViewModel)
                GuideHost(
                    guides.mainScreen,
                    nextTip = { NextTipLabel(it) }
                ) {
                    Background(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = backgroundViewModel
                    )

                    MainScreen(
                        screenBackStackModel = screenBackStackModel,
                        eventViewModel = eventViewModel,
                        modpackImportViewModel = modpackImportViewModel,
                        modifyVersionViewModel = modifyVersionViewModel,
                        submitError = {
                            errorViewModel.showError(it)
                        }
                    )

                    //节日彩蛋效果层
                    FestivalEffects(
                        modifier = Modifier.fillMaxSize(),
                        festivals = festivals
                    )

                    //启动游戏操作流程
                    LaunchGameOperation(
                        activity = this@MainActivity,
                        eventViewModel = eventViewModel,
                        launchGameViewModel = launchGameViewModel,
                        exitActivity = {
                            this@MainActivity.finish()
                        },
                        ensureVulkanSupported = vulkanCheckerViewModel::ensureSupported,
                        submitError = {
                            errorViewModel.showError(it)
                        },
                        toAccountManageScreen = { menu ->
                            screenBackStackModel.mainScreen.navigateTo(
                                screenKey = NormalNavKey.AccountManager(menu)
                            )
                        },
                        toVersionManageScreen = {
                            screenBackStackModel.mainScreen.removeAndNavigateTo(
                                remove = NestedNavKey.VersionSettings::class,
                                screenKey = NormalNavKey.VersionsManager
                            )
                        },
                        navigateToWeb = { url ->
                            screenBackStackModel.mainScreen.backStack.navigateToWeb(url)
                        },
                        backToMain = {
                            screenBackStackModel.mainScreen.clearWith(NormalNavKey.LauncherMain)
                        },
                        checkIfInWebScreen = {
                            screenBackStackModel.mainScreen.currentKey is NormalNavKey.WebScreen
                        }
                    )

                    //启动游戏流程展示
                    val launchFlow by launchGameViewModel.launchFlow.collectAsStateWithLifecycle()
                    val flow = launchFlow
                    if (flow != null) {
                        val launchTasks by flow.tasksFlow.collectAsStateWithLifecycle()
                        TitleTaskFlowDialog(
                            title = stringResource(R.string.main_launch_game),
                            tasks = launchTasks,
                            onCancel = {
                                launchGameViewModel.cancel()
                            }
                        )
                    }
                }

                //显示赞助支持的小弹窗
                if (!isImporting && finishedGame.state >= 100 && showSponsorship.state) {
                    SimpleAlertDialog(
                        title = stringResource(R.string.about_sponsor),
                        text = stringResource(R.string.game_saponsorship_finished_game, finishedGame.state),
                        dismissText = stringResource(R.string.generic_close),
                        onDismiss = {
                            showSponsorship.save(false)
                        },
                        onConfirm = {
                            showSponsorship.save(false)
                            eventViewModel.sendEvent(
                                EventViewModel.Event.OpenLink(URL_SUPPORT)
                            )
                        }
                    )
                }

                ModpackImportOperation(
                    operation = modpackImportViewModel.importOperation,
                    changeOperation = { modpackImportViewModel.importOperation = it },
                    importer = modpackImportViewModel.importer,
                    onCancel = {
                        modpackImportViewModel.cancel()
                        lifecycleScope.launch {
                            keepScreen(false)
                        }
                    }
                )

                //用户确认版本名称 操作流程
                ModpackVersionNameOperation(
                    operation = modpackImportViewModel.versionNameOperation,
                    onConfirmVersionName = { name ->
                        modpackImportViewModel.confirmVersionName(name)
                    },
                    onCancel = {
                        modpackImportViewModel.cancel()
                    }
                )

                //用户确认使用移动网络 操作流程
                ModpackConfirmUseMobileDataOperation(
                    operation = modpackImportViewModel.confirmMobileDataOperation,
                    onConfirmUse = { use ->
                        modpackImportViewModel.confirmUseMobileData(use)
                    }
                )

                //游戏日志分享菜单
                val logFile = logShareViewModel.currentLogFile
                if (logShareViewModel.showMenu && logFile != null) {
                    LogShareMenu(
                        operation = LogShareMenuOperation.ShowMenu,
                        onChange = { operation ->
                            if (operation == LogShareMenuOperation.None) {
                                logShareViewModel.closeMenu()
                            }
                        },
                        onView = {
                            screenBackStackModel.mainScreen.backStack.navigateToLogView(
                                logPath = logFile.absolutePath
                            )
                            logShareViewModel.closeMenu()
                        },
                        onShare = {
                            shareFile(this@MainActivity, logFile)
                            logShareViewModel.closeMenu()
                        },
                        canUpload = logsUploadViewModel.canUpload,
                        onUpload = {
                            logsUploadViewModel.operation = ShareLinkOperation.Tip
                            logShareViewModel.closeMenu()
                        }
                    )
                }

                ShareLinkOperation(
                    operation = logsUploadViewModel.operation,
                    onChange = { logsUploadViewModel.operation = it },
                    onUploadChancel = { logsUploadViewModel.cancel() },
                    onUpload = {
                        logFile?.let { file ->
                            logsUploadViewModel.upload(file) { link ->
                                openLink(link)
                                copyText(COPY_LABEL_LINK, link, this@MainActivity)
                            }
                        }
                    }
                )

                //检查更新操作流程
                LauncherUpgradeOperation(
                    operation = launcherUpgradeViewModel.operation,
                    onChanged = { launcherUpgradeViewModel.operation = it },
                    onIgnoredClick = { ver ->
                        AllSettings.lastIgnoredVersion.save(ver)
                    },
                    onLinkClick = { eventViewModel.sendEvent(EventViewModel.Event.OpenLink(it)) }
                )

                //版本修改操作流程
                ModifyVersionOperation(
                    operation = modifyVersionViewModel.installOperation,
                    changeOperation = { modifyVersionViewModel.installOperation = it },
                    installer = modifyVersionViewModel.installer,
                    onModify = { payload ->
                        modifyVersionViewModel.modify(this@MainActivity, payload)
                        //任务开始执行，立刻返回主界面，不停留在可能失效的版本设置屏幕
                        screenBackStackModel.mainScreen.clearWith(NormalNavKey.LauncherMain)
                    },
                    onCancel = {
                        modifyVersionViewModel.cancel()
                    }
                )

                val vcOperation by vulkanCheckerViewModel.vcOperation.collectAsStateWithLifecycle()
                VulkanChecker(
                    operation = vcOperation,
                    onChange = {
                        vulkanCheckerViewModel.changeOperation(it)
                    },
                    startCheck = { version ->
                        eventViewModel.sendEvent(
                            EventViewModel.Event.VulkanCheck(version)
                        )
                    },
                    confirmResult = {
                        vulkanCheckerViewModel.resumeCont()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIfNeeded(intent)
        // 重载渲染器
        Renderers.init(true)
        // 重载插件
        PluginLoader.loadAllPlugins(this, true)
    }

    override fun onDestroy() {
        fmEventRegistrar?.stop()
        fmEventRegistrar = null
        super.onDestroy()
    }

    /**
     * 文件管理器文件变更事件处理
     */
    private fun onFileManagerEvent(event: FileManagerEvent) {
        val versionsHome = File(getVersionsHome()).absolutePath
        val touchesVersions = event.changedDirs.any { dir ->
            val normalized = File(dir).absolutePath
            normalized == versionsHome || normalized.startsWith("$versionsHome${File.separator}")
        }
        if (touchesVersions) {
            VersionsManager.refresh("[FileManager] ${event.type.name}")
        }
    }

    /**
     * 检查设备 Vulkan 支持情况
     */
    private suspend fun checkVulkan(version: Version) {
        withContext(Dispatchers.Main) {
            val (result, useTurnip) = vulkanCheckerViewModel.check(version)
            vulkanCheckerViewModel.changeOperation(VCOperation.Result(result, useTurnip))
        }
    }

    /**
     * 检查启动器更新
     */
    private fun checkUpdate() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = launcherUpgradeViewModel.checkManually(
                    onInProgress = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.generic_in_progress), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onIsLatest = {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, getString(R.string.upgrade_is_latest), Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (!success) throw RuntimeException()
            } catch (_: TooFrequentOperationException) {
                //太频繁了
                return@launch
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.upgrade_get_remote_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
        }
    }

    /**
     * 是否保持屏幕不熄屏
     */
    private suspend fun keepScreen(on: Boolean) {
        withContext(Dispatchers.Main) {
            window?.apply {
                if (on) {
                    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * 弹出下载插件的链接提示对话框
     */
    private suspend fun showDownloadPlugins(link: EventViewModel.Event.DownloadPlugins.Links) {
        //匹配当前系统语言可见的网盘链接
        val locale = Locale.getDefault()
        val cloudDrive = link.cloudDrives.sortedByDescending {
            it.language.contains("_")
        }.find { drive ->
            locale.compareLangTag(drive.language)
        }

        withContext(Dispatchers.Main) {
            val builder = MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.plugin_download_title)
                .setMessage(R.string.plugin_download_summary)
                .setPositiveButton("Github") { dialog, _ ->
                    openLinkInternal(link.github)
                    dialog.dismiss()
                }

            cloudDrive?.link?.let { link ->
                builder.setNegativeButton(R.string.upgrade_cloud_drive) { dialog, _ ->
                    openLinkInternal(link)
                    dialog.dismiss()
                }
            }

            builder.showThemed()
        }
    }

    /**
     * 导入控制布局
     */
    private fun importControlFiles(uris: List<Uri>) {
        fun showError(
            title: AndroidStringText = androidText(R.string.control_manage_import_failed),
            message: AndroidStringText
        ) {
            errorViewModel.showError(
                ErrorViewModel.ThrowableMessage(
                    title = title,
                    message = message
                )
            )
        }
        TaskSystem.submitTask(
            Task.runTask(
                dispatcher = Dispatchers.IO,
                task = {
                    var done = false
                    uris.forEach { uri ->
                        val inputStream = contentResolver.openInputStream(uri) ?: run {
                            showError(message = androidText(R.string.multirt_runtime_import_failed_input_stream))
                            return@forEach
                        }
                        ControlManager.importControl(
                            inputStream = inputStream,
                            onSerializationError = {
                                showError(
                                    message = buildAppendedText {
                                        append(R.string.control_manage_import_failed_to_parse)
                                        append("\n")
                                        append(it.getMessageOrToString())
                                    }
                                )
                            },
                            catchedError =  {
                                showError(message = androidText(it.getMessageOrToString()))
                            },
                            onFinished = {
                                done = true
                            }
                        )
                    }
                    ControlManager.refresh()
                    if (done) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.generic_done),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        )
    }

    /**
     * 处理外部导入
     * @return 是否有导入任务正在进行中
     */
    private fun handleImportIfNeeded(intent: Intent?): Boolean {
        if (intent == null) return false

        val type = intent.getStringExtra(EXTRA_IMPORT_TYPE) ?: return false

        val importing = when (type) {
            IMPORT_TYPE_MODPACK -> handleModpackImport(intent)
            IMPORT_TYPE_CONTROLS -> handleControlsImport(intent)
            // `auto` 入口（manifest 声明 */* 的那个）：按文件名后缀分流，
            // 否则原生界面下从「分享」进来的文件会毫无反应
            IMPORT_TYPE_AUTO -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_IMPORT_URI)
                }
                val name = uri?.let { resolveDisplayName(it) }?.lowercase().orEmpty()
                when {
                    name.endsWith(".json") -> handleControlsImport(intent)
                    name.endsWith(".zip") || name.endsWith(".mrpack") ||
                        name.endsWith(".modpack") || name.endsWith(".rar") ||
                        name.endsWith(".7z") -> handleModpackImport(intent)
                    else -> false
                }
            }
            else -> false
        }

        intent.removeExtra(EXTRA_IMPORT_TYPE)
        return importing
    }

    /** 取内容 URI 对应的文件名（`auto` 导入按后缀分流时用）。 */
    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return@runCatching uri.lastPathSegment
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /**
     * @return 是否已经触发了整合包导入程序
     */
    private fun handleModpackImport(intent: Intent): Boolean {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IMPORT_URI)
        }
        if (uri != null) {
            modpackImportViewModel.import(
                context = this@MainActivity,
                uri = uri,
                onStart = {
                    lifecycleScope.launch {
                        keepScreen(true)
                    }
                },
                onStop = {
                    lifecycleScope.launch {
                        keepScreen(false)
                    }
                }
            )
        }
        return uri != null
    }

    /**
     * @return 是否已经触发了控制布局导入程序
     */
    private fun handleControlsImport(intent: Intent): Boolean {
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_IMPORT_URI)
        }
        if (uri != null) {
            importControlFiles(listOf(uri))
        }
        return uri != null
    }

    override fun onResume() {
        super.onResume()
        ControlManager.checkDefaultAndRefresh(this@MainActivity)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isCaptureKey) {
            Logger.info(TAG, "Capture key event: $event")
            eventViewModel.sendEvent(EventViewModel.Event.Key.OnKeyDown(event))
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}