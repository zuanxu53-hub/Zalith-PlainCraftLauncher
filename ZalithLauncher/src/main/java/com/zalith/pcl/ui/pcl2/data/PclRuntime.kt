/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 界面 ↔ ZalithLauncher 真实后端的桥接层。
 *
 * 本文件不画任何 UI，只负责把 Zalith 现有的服务（版本扫描、账户系统、启动流程、
 * 任务系统、日志桥）适配成 Compose 可直接消费的 StateFlow / 动作函数，
 * 使 PCL2 外观的页面具备与原生界面完全一致的真实功能。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.zalith.pcl.bridge.LoggerBridge
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.coroutine.Task
import com.zalith.pcl.coroutine.TaskStage
import com.zalith.pcl.coroutine.TaskSystem
import com.zalith.pcl.coroutine.TitledTask
import com.zalith.pcl.game.account.Account
import com.zalith.pcl.game.account.AccountsManager
import com.zalith.pcl.game.account.localLogin
import com.zalith.pcl.game.account.isLocalAccount
import com.zalith.pcl.game.account.isMicrosoftAccount
import com.zalith.pcl.game.version.installed.VersionLoginType
import com.zalith.pcl.game.addons.mirror.SourceSpeedResult
import com.zalith.pcl.game.addons.mirror.probeAndApplyFastestSource
import com.zalith.pcl.game.download.engine.BatchDownloader
import com.zalith.pcl.game.download.engine.BatchProgress
import com.zalith.pcl.game.download.game.GameDownloadInfo
import com.zalith.pcl.game.download.game.GameInstaller
import com.zalith.pcl.game.download.modpack.install.ModpackImporter
import com.zalith.pcl.game.launch.GameLaunchFlow
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionsManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.setting.enums.MirrorSourceType
import com.zalith.pcl.ui.androidText
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.components.PclWebPage
import com.zalith.pcl.ui.pcl2.PclThirdPartyLogin
import com.zalith.pcl.ui.screens.content.elements.QuickPlay
import com.zalith.pcl.ui.toAndroidString
import com.zalith.pcl.viewmodel.ErrorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "Pcl2Runtime"

/** 日志卡片最多保留的行数。 */
private const val LOG_LIMIT = 600

/** 启动前检查的结果。 */
enum class PclLaunchCheck {
    /** 可以启动 */
    Ready,

    /** 没有任何已安装版本 */
    NoVersion,

    /** 当前版本文件夹不完整（缺少 json 或目录） */
    InvalidVersion,

    /** 没有当前账户 */
    NoAccount,

    /** 已经在启动了 */
    AlreadyLaunching,
}

/**
 * PCL2 界面运行时。
 *
 * 所有数据都来自 Zalith 的真实服务，页面上看到的版本、账户、日志、进度
 * 全部是设备上的真实状态。
 */
class PclRuntime(
    val activity: Activity,
    private val scope: CoroutineScope,
) {
    /**
     * 与 Activity 同生命周期的协程作用域。
     *
     * 后台流程（游戏安装、整合包导入…）必须挂在这上面，**不要**用页面的
     * `rememberCoroutineScope()` —— 后者在离开组合（切版块、关子页面）时就被取消了。
     *
     * 这条正是「整合包下载完了却没有自动安装」的来源：下载任务本身跑在
     * `TaskSystem` 的进程级 scope 上，一定会跑到终点、一定会触发 `onEnded`；
     * 但 `onEnded` 里拉起的导入器如果挂在页面 scope 上，页面一没，
     * 刚刚才建立的导入协程立刻被掐掉，看起来就是「下载完没动静」。
     */
    val backgroundScope: CoroutineScope get() = scope

    // ------------------------------------------------------------------ 版本

    val versions: StateFlow<List<Version>> = VersionsManager.versions
    val currentVersion: StateFlow<Version?> = VersionsManager.currentVersion
    val isRefreshingVersions: StateFlow<Boolean> = VersionsManager.isRefreshing

    // ------------------------------------------------------------------ 账户

    val accounts: StateFlow<List<Account>> = AccountsManager.accountsFlow
    val currentAccount: StateFlow<Account?> = AccountsManager.currentAccountFlow

    // ------------------------------------------------------------------ 启动

    private val launchFlow = GameLaunchFlow(scope)

    /** 启动过程中的任务（账号校验 / 模组扫描 / 文件校验 / 启动）。 */
    val launchTasks: StateFlow<List<TitledTask>> = launchFlow.tasksFlow

    /** 全局下载任务（下载页提交的任务都会出现在这里）。 */
    val downloadTasks: StateFlow<List<Task>> = TaskSystem.tasksFlow

    // ------------------------------------------------------------------ 游戏安装

    /**
     * 当前正在进行的游戏安装器。
     *
     * 安装跑在 Activity 的 `lifecycleScope` 上（而不是页面的 `rememberCoroutineScope`），
     * 所以切到别的版块、或者把进度弹窗收起来（后台运行）都不会中断安装；
     * 同时它会被「下载管理」列为一条任务，随时能看到进度。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val gameInstallerFlow = MutableStateFlow<GameInstaller?>(null)

    /** 游戏安装任务（供「下载管理」显示）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val installTasks: StateFlow<List<TitledTask>> = gameInstallerFlow
        .flatMapLatest { it?.tasksFlow ?: flowOf(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 安装任务的显示名（下载管理页的卡片标题）。 */
    var installTitle by mutableStateOf("")
        private set

    /**
     * 当前批量下载的进度快照（总进度 / 下载速度 / 剩余文件）。
     *
     * 来自下载引擎 `BatchDownloader.latestProgress`：下载管理页左栏用它显示剩余文件数。
     */
    val downloadProgress: StateFlow<BatchProgress?> = BatchDownloader.latestProgress

    /** 是否已有安装任务在跑。 */
    val isInstalling: Boolean get() = gameInstallerFlow.value != null

    /**
     * 当前这条任务是不是「修改 / 重置实例」。
     *
     * 【为什么必须区分】取消安装时 `GameInstaller.cancelInstall()` 默认会把**目标版本目录整棵删掉**
     * （`clearTarget = true`），那对一次新安装是「别留半成品」，对一次重置就是**把用户的实例连存档一起删了**。
     * 重置本来就只往临时目录里写、最后才替换 json/jar，所以取消时清目标目录没有任何意义。
     */
    private var installIsModify by mutableStateOf(false)

    /**
     * 开始安装游戏（全局任务）。
     * @param onFinished 安装结束（成功 / 失败 / 已存在都会回调），参数是安装结果描述
     */
    fun installGame(info: GameDownloadInfo, onFinished: (String) -> Unit = {}) {
        if (gameInstallerFlow.value != null) {
            hint("已有安装任务正在进行，请稍候。", PclHintTheme.Yellow)
            return
        }
        val installer = GameInstaller(activity, info, scope)
        installIsModify = false
        gameInstallerFlow.value = installer
        installTitle = "${info.customVersionName} 安装"
        installer.installGame(
            isRunning = {
                gameInstallerFlow.value = null
                hint("上一个安装任务还在收尾，请稍等一下再试。", PclHintTheme.Yellow)
                onFinished("")
            },
            onInstalled = { version ->
                gameInstallerFlow.value = null
                refreshVersions()
                hint("版本「$version」安装完成。", PclHintTheme.Green)
                onFinished(version)
            },
            onError = { th ->
                gameInstallerFlow.value = null
                // 弹窗只显示 message，定位不到崩溃行；完整堆栈写进日志方便排查。
                Logger.error(TAG, "安装失败", th)
                pendingError = ErrorViewModel.ThrowableMessage(
                    title = androidText("安装失败"),
                    message = androidText(th.message ?: th.javaClass.simpleName),
                )
                onFinished("")
            },
            onGameAlreadyInstalled = {
                gameInstallerFlow.value = null
                hint("该版本已经安装过了。", PclHintTheme.Blue)
                onFinished("")
            },
        )
    }

    /**
     * 修改（重置）一个已安装实例：按 [info] 描述的 Minecraft 版本 + 加载器组合原地重建它。
     *
     * 与 [installGame] 的区别只有一处：安装器走的是 `GameInstaller.modifyVersion()`，
     * 也就是**允许目标版本已经存在**（`createPathConfig(checkVersionExists = false)`），
     * 最终只替换该版本的 json/jar，存档、Mod、资源包这些用户数据一个都不碰。
     *
     * 任务同样挂在 Activity 的 scope 上并进 [installTasks]，因此
     * 「下载管理」里看得到进度，切版块也不会把它掐断。
     *
     * @param version 要修改的实例（决定目标游戏目录与版本目录）
     * @param info 修改后的组合；`customVersionName` 必须是该实例现有的名字
     */
    fun modifyGame(version: Version, info: GameDownloadInfo, onFinished: (Boolean) -> Unit = {}) {
        if (gameInstallerFlow.value != null) {
            hint("已有安装任务正在进行，请稍候。", PclHintTheme.Yellow)
            onFinished(false)
            return
        }
        val installer = GameInstaller(
            context = activity,
            info = info,
            scope = scope,
            // 实例可能不在默认游戏目录下（自定义游戏目录 / 版本隔离），
            // 必须按实例自己的 gameHome 去建目录，否则文件会被装到别处去。
            targetGameFolder = File(version.getGameHome()),
        )
        installIsModify = true
        gameInstallerFlow.value = installer
        installTitle = "${info.customVersionName} 重置"
        installer.modifyVersion(
            // 安装器内部拒绝受理时（任务流还占着）必须**说出来并释放槽位**：
            // 否则界面会停在「正在重置」、按钮再也点不动，用户看到的就是「没反应」。
            isRunning = {
                gameInstallerFlow.value = null
                hint("上一个安装任务还在收尾，请稍等一下再试。", PclHintTheme.Yellow)
                onFinished(false)
            },
            onModified = {
                gameInstallerFlow.value = null
                refreshVersions()
                hint("实例「${info.customVersionName}」已重置完成。", PclHintTheme.Green)
                onFinished(true)
            },
            onError = { th ->
                gameInstallerFlow.value = null
                Logger.error(TAG, "重置失败", th)
                pendingError = ErrorViewModel.ThrowableMessage(
                    title = androidText("重置失败"),
                    message = androidText(th.message ?: th.javaClass.simpleName),
                )
                onFinished(false)
            },
        )
    }

    /** 取消当前的游戏安装。 */
    fun cancelInstall() {
        val installer = gameInstallerFlow.value ?: return
        // 重置任务取消时**不能**清目标目录 —— 那正是用户正在用的实例
        installer.cancelInstall(clearTarget = !installIsModify)
        gameInstallerFlow.value = null
        hint("已取消安装。", PclHintTheme.Yellow)
    }

    // ------------------------------------------------------------------ 整合包导入

    /**
     * 当前正在进行的整合包任务。
     *
     * 【为什么单独开一条槽】一条整合包任务的完整链路横跨两个引擎：
     *   · **下载**走全局任务系统（`TaskSystem`），包落到本地；
     *   · **安装**走 `ModpackImporter` 自己的任务流（解压 → 读描述文件 → 补依赖 → 拷贝）。
     * 以前只有前半截会出现在「下载管理」里，后半截只活在一个点开就关得掉的进度弹窗里 ——
     * 弹窗一关（或者从列表页误触退出），安装仍在跑，但界面上再也找不到它。
     * 这里把两半合成一条常驻任务，让它和「下载版本」一样在下载管理里全程可见、可取消。
     *
     * 【为什么是一张表而不是一个槽】以前这里是**单个** `MutableStateFlow<ModpackImportTask?>`，
     * 于是「一边下载 A、一边导入 B」时 B 直接把 A 覆盖掉：A 的卡片凭空消失（下载其实还在跑），
     * 更要命的是 A 走到终点时会调一次 `clearModpackImport()`，把**当时槽里那条**（也就是 B）
     * 一起清掉 —— 表现出来就是「导入一个整合包会打断另一个」。改成按 id 索引的表之后，
     * 每条任务各自认领自己的行、各自的取消按钮，互不干扰。
     *
     * @param id           这条任务的唯一标识，后续更新 / 清除 / 取消都必须带上它
     * @param title        卡片标题
     * @param downloadTask 下载阶段的任务（本地导入整合包时为 null）
     * @param importer     安装阶段的导入器（下载尚未完成时为 null）
     */
    data class ModpackImportTask(
        val id: Int,
        val title: String,
        val downloadTask: Task? = null,
        val importer: ModpackImporter? = null,
    )

    /**
     * 「下载管理」里一条整合包任务的完整展示数据。
     *
     * @param waiting 下载已经完成、安装还没吐出第一个阶段（可能正排在别的整合包后面等安装锁），
     *                此时 [rows] 是空的 —— 卡片靠这个标志显示一句「等待中」，而不是空着。
     */
    data class ModpackImportEntry(
        val id: Int,
        val title: String,
        val rows: List<TitledTask>,
        val waiting: Boolean = false,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val modpackFlow = MutableStateFlow<List<ModpackImportTask>>(emptyList())
    private var modpackIdSeed = 0

    /**
     * 全部整合包任务，每条一张卡（供「下载管理」逐条渲染）。
     *
     * 每条的 [ModpackImportEntry.rows] = 「下载整合包」那一行 + 该导入器任务流当前吐出的各阶段。
     * 用 `combine` 把每条的流并起来，任何一条有新阶段都会让整张列表刷新。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val modpackEntries: StateFlow<List<ModpackImportEntry>> = modpackFlow
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    list.map { task ->
                        val installFlow = task.importer?.taskFlow ?: flowOf(emptyList())
                        installFlow.map { installRows ->
                            ModpackImportEntry(
                                id = task.id,
                                title = task.title,
                                rows = buildList {
                                    task.downloadTask?.let {
                                        add(TitledTask(title = androidText("下载整合包"), task = it))
                                    }
                                    addAll(installRows)
                                },
                                waiting = task.importer != null && installRows.isEmpty(),
                            )
                        }
                    }
                ) { it.toList() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 全部整合包任务的下载半截 —— 左栏统计要把它们从「普通下载」里剔掉，免得算两遍。 */
    val modpackDownloadTasks: StateFlow<List<Task>> = modpackFlow
        .map { list -> list.mapNotNull { it.downloadTask } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 全部整合包任务当前的行（下载行 + 安装行），悬浮按钮的角标用它计数。 */
    val modpackTaskList: StateFlow<List<Task>> = modpackEntries
        .map { entries -> entries.flatMap { entry -> entry.rows.map { it.task } } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 是否还有整合包任务在跑（下载或安装任一段）。 */
    val isImportingModpack: StateFlow<Boolean> = modpackFlow
        .map { it.isNotEmpty() }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * 当前登记在册的整合包任务 id 集合。
     *
     * 排队等安装权的任务靠它感知「前面那条结束了」—— 因为**所有**出口
     * （装完 / 失败 / 被取消）都会走一次 `clearModpackImport(id)`，这是唯一一个一定会发生的信号。
     * 反过来，靠 `ModpackImporter` 的回调是不可靠的：`TaskFlowExecutor.cancel()` 只是
     * `job.cancel()`（`TaskFlowExecutor.kt:186`），回调不保证触发，拿它当结束信号会把
     * 安装锁永久占住，后面排队的整合包就再也开不了工。
     */
    val modpackTaskIds: StateFlow<Set<Int>> = modpackFlow
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** 这条整合包任务是否还登记在册（用来判断「用户是否已经把它取消了」）。 */
    fun isModpackImportTracked(id: Int): Boolean = modpackFlow.value.any { it.id == id }

    /** 按导入器反查它属于哪条任务 —— 给「进度弹窗的取消」这种手上只有 importer 的地方用。 */
    fun modpackImportIdOf(importer: ModpackImporter): Int? =
        modpackFlow.value.firstOrNull { it.importer === importer }?.id

    /**
     * 登记一条整合包任务，返回它的 id。
     *
     * 下载开始时登记下载任务；下载完成后再带上导入器登记一次 —— 此时**必须传回同一个 [id]**，
     * 否则会凭空多出一张卡。只传变化的那一半，另一半自动沿用，两段衔接处不会出现空白。
     */
    fun trackModpackImport(
        title: String,
        id: Int? = null,
        downloadTask: Task? = null,
        importer: ModpackImporter? = null,
    ): Int {
        val list = modpackFlow.value
        val targetId = id ?: ++modpackIdSeed
        val previous = list.find { it.id == targetId }
        val updated = ModpackImportTask(
            id = targetId,
            title = title.ifBlank { previous?.title.orEmpty() }.ifBlank { "安装整合包" },
            downloadTask = downloadTask ?: previous?.downloadTask,
            importer = importer ?: previous?.importer,
        )
        modpackFlow.value = list.filterNot { it.id == targetId } + updated
        return targetId
    }

    /**
     * 注销整合包任务（导入已经走到终点时调用）。
     * @param id 只清这一条；传 null 清掉全部
     */
    fun clearModpackImport(id: Int? = null) {
        modpackFlow.value = if (id == null) {
            emptyList()
        } else {
            modpackFlow.value.filterNot { it.id == id }
        }
    }

    /** 取消一条整合包任务：下载和安装一起停，别的整合包任务不受影响。 */
    fun cancelModpackImport(id: Int) {
        val list = modpackFlow.value
        val current = list.find { it.id == id } ?: return
        modpackFlow.value = list.filterNot { it.id == id }
        // 已经跑完的下载任务不需要再取消（它已经从任务列表里移除了）
        current.downloadTask?.let { if (it.stage.value != TaskStage.COMPLETED) TaskSystem.cancelTask(it) }
        runCatching { current.importer?.cancel() }
    }

    /**
     * 整合包**安装阶段**的全局互斥锁。
     *
     * 为什么必须有：`ModpackImporter` 用的临时环境是**写死的共享目录** `cache/temp_modpack`
     * （`PathManager.DIR_CACHE_MODPACK_DOWNLOADER`，见 `ModpackImporter.kt:120`），
     * 而它进入任务流的第一件事就是把这个目录**整个递归删掉**（清上一次安装的残留，
     * `ModpackImporter.kt:138` / `:231`）。两个整合包同时进安装阶段时，后者的这次清理会把
     * 前者正在用的临时文件一并删光，前者的安装随即失败 —— 这就是「导入一个会打断另一个」
     * 里最硬的那一层。引擎层不做目录隔离，只能在调度层串行：同一时刻只允许一个整合包在装。
     *
     * 下载阶段**完全并发**，互不影响（每个包下到自己的文件名，见 `startModpackImport`）。
     */
    val modpackInstallLock: Mutex = Mutex()

    /** 串行化离线账户的「查重 + 创建」，避免回车与失焦重复提交建出两个同名账户。 */
    private val localAccountCreateLock: Mutex = Mutex()

    // ------------------------------------------------------------------ 下载源

    /** 当前的游戏内容下载源设置。 */
    var downloadSource by mutableStateOf(AllSettings.gameDownloadSource.getValue())
        private set

    /** 上次测速选出的最快源（[MirrorSourceType.AUTO] = 还没测过）。 */
    var measuredFastestSource by mutableStateOf(AllSettings.gameDownloadFastestSource.getValue())
        private set

    /** 上次测速时间（毫秒时间戳）。 */
    var sourceProbeTime by mutableStateOf(AllSettings.gameDownloadProbeTime.getValue())
        private set

    /** 上次测速的逐源结果。 */
    var sourceSpeeds by mutableStateOf<List<SourceSpeedResult>>(emptyList())
        private set

    /** 是否正在测速。 */
    var isProbingSources by mutableStateOf(false)
        private set

    fun chooseDownloadSource(type: MirrorSourceType) {
        AllSettings.gameDownloadSource.save(type)
        downloadSource = type
    }

    /**
     * 给各个下载源测速，并把实测最快的源记为「自动」档的选择。
     * 之后所有走 `mapBMCLMirrorUrls()` 的下载（版本本体、支持库、资源文件、加载器）都会用它。
     */
    fun probeDownloadSources() {
        if (isProbingSources) return
        isProbingSources = true
        scope.launch {
            val results = runCatching { probeAndApplyFastestSource() }
                .getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                isProbingSources = false
                sourceSpeeds = results
                sourceProbeTime = AllSettings.gameDownloadProbeTime.getValue()
                measuredFastestSource = AllSettings.gameDownloadFastestSource.getValue()
                val best = results.filter { it.ok }.maxByOrNull { it.bytesPerSec }
                if (best != null) {
                    hint(
                        "测速完成：已选用 ${best.describe()}",
                        PclHintTheme.Green,
                    )
                } else {
                    hint(
                        "测速失败：所有下载源都不可用，已保持原有选择。",
                        PclHintTheme.Red,
                    )
                }
            }
        }
    }

    /** 是否正在启动。 */
    var isLaunching by mutableStateOf(false)
        private set

    /** 待展示的错误（对应 Zalith 的错误对话框）。 */
    var pendingError by mutableStateOf<ErrorViewModel.ThrowableMessage?>(null)
        private set

    // ------------------------------------------------------------------ 日志

    private val logBuffer = ArrayDeque<String>()
    private val _logLines = MutableStateFlow<List<String>>(emptyList())

    /** 启动日志（最近 [LOG_LIMIT] 行）。 */
    val logLines: StateFlow<List<String>> = _logLines.asStateFlow()

    private var logListener: LoggerBridge.EventLogListener? = null

    init {
        // 进入 PCL2 界面时刷新一次版本列表（Zalith 内部会去重，重复调用无副作用）
        VersionsManager.refresh(TAG)
    }

    // ================================================================== 启动检查

    /**
     * 启动前检查。与 Zalith 原生界面的 `LaunchGameOperation` 语义一致，
     * 只是把结果收敛成 PCL 风格的少量分支。
     */
    fun checkLaunch(): PclLaunchCheck {
        if (isLaunching) return PclLaunchCheck.AlreadyLaunching
        val version = currentVersion.value ?: return PclLaunchCheck.NoVersion
        if (!version.isValid()) return PclLaunchCheck.InvalidVersion
        // 第三方登录的版本允许「暂时没有账户」：真正启动的那一刻会问账号密码当场登
        val thirdParty = version.getVersionConfig().loginTypeValue().isThirdParty()
        if (currentAccount.value == null && !thirdParty) return PclLaunchCheck.NoAccount
        return PclLaunchCheck.Ready
    }

    /**
     * 检查并启动。检查不通过时通过左下角提示条给出原因（对应 PCL 的 Hint）。
     */
    fun tryLaunch() {
        when (checkLaunch()) {
            PclLaunchCheck.Ready -> {
                val version = currentVersion.value ?: return
                scope.launch {
                    // 按「版本设置 → 服务器 → 登录方式」把账户调整到位（必要时当场登录）
                    if (prepareAccount(version)) launch()
                }
            }
            PclLaunchCheck.NoVersion ->
                hint("没有可用的游戏版本，请先在「下载」页安装一个版本。", PclHintTheme.Red)
            PclLaunchCheck.InvalidVersion ->
                hint("当前版本文件不完整，请在「下载」页重新安装该版本。", PclHintTheme.Red)
            PclLaunchCheck.NoAccount ->
                hint("尚未登录任何账户，请先在「更多 → 账号」中登录。", PclHintTheme.Red)
            PclLaunchCheck.AlreadyLaunching ->
                hint("游戏已经在启动中了，请稍候。", PclHintTheme.Blue)
            null -> Unit
        }
    }

    /**
     * 按「版本设置 → 服务器 → 登录方式」把当前账户调整到位。
     *
     * PCL 的这五项在手机上的落点就是「启动时用哪一类账户」：
     *   · 正版或离线 —— 跟随当前账户，什么都不做
     *   · 仅正版 / 仅离线 —— 当前账户不是那种就自动切到已有的那种账户，没有就拦下来说明
     *   · 统一通行证 / Authlib —— 必须是对应认证服务器上的账户；本地没有就当场问账号密码
     *
     * @return true 表示可以继续启动
     */
    private suspend fun prepareAccount(version: Version): Boolean {
        val config = version.getVersionConfig()
        val type = config.loginTypeValue()
        val current = currentAccount.value
        val all = accounts.value

        return when (type) {
            VersionLoginType.MICROSOFT_OR_OFFLINE -> true

            VersionLoginType.MICROSOFT_ONLY -> {
                if (current?.isMicrosoftAccount() == true) {
                    true
                } else {
                    val target = all.firstOrNull { it.isMicrosoftAccount() }
                    if (target == null) {
                        hint("该版本设置为「仅正版登录」，请先在「更多 → 账号」里登录微软账户。", PclHintTheme.Red)
                        false
                    } else {
                        switchAccount(target)
                        true
                    }
                }
            }

            VersionLoginType.OFFLINE_ONLY -> {
                if (current?.isLocalAccount() == true) {
                    true
                } else {
                    val target = all.firstOrNull { it.isLocalAccount() }
                    if (target == null) {
                        hint("该版本设置为「仅离线登录」，请先在「更多 → 账号」里创建离线账户。", PclHintTheme.Red)
                        false
                    } else {
                        switchAccount(target)
                        true
                    }
                }
            }

            VersionLoginType.UNIFIED_PASSPORT, VersionLoginType.AUTHLIB -> {
                val baseUrl = config.loginBaseUrl()
                if (baseUrl.isBlank()) {
                    hint(
                        if (type == VersionLoginType.UNIFIED_PASSPORT) {
                            "该版本设置为统一通行证登录，请先在「版本设置 → 服务器」里填写服务器 ID。"
                        } else {
                            "该版本设置为第三方登录，请先在「版本设置 → 服务器」里填写认证服务器地址。"
                        },
                        PclHintTheme.Red,
                    )
                    return false
                }
                if (current != null && current.belongsToAuthServer(baseUrl)) {
                    return true
                }
                all.firstOrNull { it.belongsToAuthServer(baseUrl) }?.let { target ->
                    switchAccount(target)
                    return true
                }
                // 本地没有这一台的账户：当场问账号密码（登录成功后已经是当前账户）
                val account = PclThirdPartyLogin.ask(
                    context = activity,
                    serverName = config.loginServerTitle(),
                    baseUrl = baseUrl,
                    registerUrl = config.loginRegisterUrl,
                )
                if (account == null) {
                    hint("已取消第三方登录，本次没有启动游戏。", PclHintTheme.Blue)
                    return false
                }
                hint(
                    "已登录「${account.username}」" +
                            "（${account.accountType ?: config.loginServerTitle()}）",
                    PclHintTheme.Green,
                )
                true
            }
        }
    }

    /** 直接启动当前版本（调用方需先自行检查）。 */
    fun launch() {
        val version = currentVersion.value ?: return
        launchVersion(version, quickPlay = null)
    }

    /**
     * 服务器列表行尾的小三角：启动指定版本并**直接进入该服务器**。
     *
     * 实现上就是给 `version.quickPlaySingle` 塞一个 [QuickPlay.Server]，
     * `LaunchArgs` 会据此追加 `--quickPlayMultiplayer`（1.20+）
     * 或旧版那对 `--server / --port`（见 `LaunchArgs.kt:128-157`）。
     *
     * 这里不要求版本已经是「当前版本」：实例页左栏可以管理任意实例，
     * 顺手把它设成当前版本，免得回首页还是别的版本。
     *
     * @return 真的提交了启动任务才返回 true（检查没通过时只弹提示）
     */
    fun playServer(version: Version, address: String): Boolean {
        when {
            isLaunching -> {
                hint("游戏已经在启动中了，请稍候。", PclHintTheme.Blue)
                return false
            }
            !version.isValid() -> {
                hint("版本「${version.getVersionName()}」文件不完整，无法启动。", PclHintTheme.Red)
                return false
            }
            currentAccount.value == null -> {
                hint("尚未登录任何账户，请先在「更多 → 账号」中登录。", PclHintTheme.Red)
                return false
            }
        }
        if (currentVersion.value?.getVersionName() != version.getVersionName()) {
            VersionsManager.saveVersion(version)
        }
        launchVersion(version, QuickPlay.Server(address))
        return true
    }

    /**
     * @param quickPlay 非空时这次启动会直接进入对应的存档 / 服务器
     *   （`LaunchArgs` 读的就是 `version.quickPlaySingle`，所以启动前写、启动后清）。
     */
    private fun launchVersion(version: Version, quickPlay: QuickPlay?) {
        if (AccountsManager.currentAccountFlow.value == null) return
        if (isLaunching) return

        // 每次都重写：上一次「进服务器」的残留绝不能带进这一次普通启动
        version.quickPlaySingle = quickPlay

        clearLog()
        appendLocalLog("▷ 准备启动 ${version.getVersionName()}")
        if (quickPlay is QuickPlay.Server) {
            appendLocalLog("▷ 启动后自动进入服务器 ${quickPlay.serverAddress}")
        }
        isLaunching = true
        //概览页的「启动次数」（PCL 存在 Setup.ini 的 LaunchCount 里，内核没有这个概念）
        com.zalith.pcl.ui.pcl2.data.PclInstanceMeta.increaseStartCount(version.getVersionName())

        launchFlow.launch(
            context = activity,
            version = version,
            exitActivity = { activity.finish() },
            submitError = { tm -> pendingError = tm },
            onReloginRequired = { acc ->
                pendingError = ErrorViewModel.ThrowableMessage(
                    title = androidText("账号需要重新登录"),
                    message = androidText("账号「${acc.username}」的登录凭据已被服务器拒绝，请在「更多 → 账号」中重新登录。"),
                )
            },
            onRefreshFailed = { acc, th ->
                pendingError = ErrorViewModel.ThrowableMessage(
                    title = androidText("账号刷新失败"),
                    message = androidText("账号「${acc.username}」刷新失败：${th.message ?: th.javaClass.simpleName}"),
                )
            },
            isRunning = { hint("已有启动任务正在进行。", PclHintTheme.Blue) },
            onComplete = {
                // 「直接进服务器」只对这一次启动有效，用完立刻清掉
                version.quickPlaySingle = null
                isLaunching = false
            },
        )
        // launch 在内部遇到异常分支时不会回调 onComplete，这里补一个兜底
        scope.launch {
            withContext(Dispatchers.Default) {
                kotlinx.coroutines.delay(1500)
            }
            if (isLaunching && launchTasks.value.isEmpty()) isLaunching = false
        }
    }

    /** 取消启动。 */
    fun cancelLaunch() {
        launchFlow.cancel()
        isLaunching = false
        appendLocalLog("▷ 已取消启动")
    }

    // ================================================================== 版本操作

    /** 切换当前版本。 */
    fun selectVersion(version: Version) {
        VersionsManager.saveVersion(version)
        hint("已切换到版本「${version.getVersionName()}」", PclHintTheme.Blue)
    }

    fun refreshVersions() {
        VersionsManager.refresh(TAG)
    }

    fun renameVersion(version: Version, newName: String) {
        VersionsManager.renameVersion(version, newName)
        hint("已重命名为「$newName」", PclHintTheme.Green)
    }

    fun deleteVersion(version: Version) {
        VersionsManager.deleteVersion(version)
        hint("已删除版本「${version.getVersionName()}」", PclHintTheme.Green)
    }

    fun copyVersion(version: Version, newName: String) {
        VersionsManager.copyVersion(version, newName, true)
        hint("已复制为「$newName」", PclHintTheme.Green)
    }

    fun togglePinned(version: Version) {
        version.setPinnedAndSave(!version.pinnedState)
    }

    // ================================================================== 账户操作

    /**
     * 创建并切换到离线账户；[onCreated] 在账户落库并成为当前账户后回调。
     *
     * 名称框会在「回车」和「失焦」时各提交一次，两次几乎同时到达。以前两个协程都先看到
     * 「还没有这个账户」，然后各自用随机 UUID 存了一条 —— 输入一次名字就多出两个账户。
     * 这里用互斥锁串行化「查重 + 落库」，第二个调用会发现刚创建的账户并直接切换过去。
     */
    fun createLocalAccount(userName: String, onCreated: () -> Unit = {}) {
        val name = userName.trim()
        if (name.isEmpty()) {
            hint("请输入玩家名。", PclHintTheme.Red)
            return
        }
        scope.launch {
            localAccountCreateLock.withLock {
                val existing = AccountsManager.accountsFlow.value.firstOrNull {
                    it.isLocalAccount() && it.username.equals(name, ignoreCase = true)
                }
                if (existing != null) {
                    AccountsManager.setCurrentAccount(existing)
                    withContext(Dispatchers.Main) { onCreated() }
                    return@withLock
                }

                val account = Account(
                    username = name,
                    accountType = com.zalith.pcl.game.account.AccountType.LOCAL.tag,
                )
                AccountsManager.suspendSaveAccount(account)
                withContext(Dispatchers.Main) {
                    hint("已创建离线账户「$name」", PclHintTheme.Green)
                    onCreated()
                }
            }
        }
    }

    fun switchAccount(account: Account) {
        AccountsManager.setCurrentAccount(account)
        hint("已切换到账户「${account.username}」", PclHintTheme.Blue)
    }

    fun deleteAccount(account: Account) {
        AccountsManager.deleteAccount(account)
    }

    fun refreshAccount(account: Account) {
        AccountsManager.refreshAccount(
            context = activity,
            account = account,
            onFailed = { th ->
                pendingError = ErrorViewModel.ThrowableMessage(
                    title = androidText("刷新失败"),
                    message = androidText(th.message ?: th.javaClass.simpleName),
                )
            },
        )
    }

    // ================================================================== 内置浏览器

    /**
     * 当前在内置浏览器里打开的一页（对应 PCL 的 `MyWebBrowser` 窗口）。
     *
     * 「正版登录」的设备码验证页走的就是它 —— 不再跳到系统浏览器，
     * 关掉这一层即视为退出登录（`checkIfInWebScreen`）。
     */
    var webPage by mutableStateOf<PclWebPage?>(null)
        private set

    fun openWebPage(url: String, title: String? = null) {
        if (url.isBlank()) return
        webPage = PclWebPage(url, title)
    }

    fun closeWebPage() {
        webPage = null
    }

    // ================================================================== 日志

    /** 开始捕获 JVM 日志（在界面可见时调用）。 */
    fun startLogCapture() {
        if (logListener != null) return
        runCatching {
            val listener = LoggerBridge.EventLogListener { text ->
                synchronized(logBuffer) {
                    logBuffer.addLast(text)
                    while (logBuffer.size > LOG_LIMIT) logBuffer.removeFirst()
                    _logLines.value = logBuffer.toList()
                }
            }
            logListener = listener
            LoggerBridge.setListener(listener)
        }
    }

    /** 停止捕获。 */
    fun stopLogCapture() {
        logListener?.let {
            runCatching { LoggerBridge.setListener(null) }
        }
        logListener = null
    }

    /** 清空日志。 */
    fun clearLog() {
        synchronized(logBuffer) {
            logBuffer.clear()
            _logLines.value = emptyList()
        }
    }

    /** 往日志里写一行本界面自己的记录（PCL 也会这么往日志里追加启动器信息）。 */
    fun appendLocalLog(line: String) {
        synchronized(logBuffer) {
            logBuffer.addLast(line)
            while (logBuffer.size > LOG_LIMIT) logBuffer.removeFirst()
            _logLines.value = logBuffer.toList()
        }
    }

    /**
     * 读取上一次启动游戏留下的日志文件的最后 [lines] 行。
     * 对应 PCL 首页在未启动时展示上次启动日志的行为。
     */
    suspend fun readLatestLog(version: Version?, lines: Int = 200): List<String> {
        if (version == null) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val file: File = version.getLatestLog()
                if (!file.exists()) return@runCatching emptyList<String>()
                val all = file.readLines()
                all.takeLast(lines)
            }.getOrDefault(emptyList())
        }
    }

    // ================================================================== 错误 / 提示

    fun dismissError() {
        pendingError = null
    }

    /** 提交一个错误框（供下载 / 安装等流程复用 Zalith 原有的错误对话框）。 */
    fun submitError(tm: ErrorViewModel.ThrowableMessage) {
        pendingError = tm
    }

    /** 用纯文本提交一个错误框。 */
    fun submitErrorText(title: String, message: String) {
        pendingError = ErrorViewModel.ThrowableMessage(
            title = androidText(title),
            message = androidText(message),
        )
    }

    private fun hint(text: String, theme: PclHintTheme) {
        com.zalith.pcl.ui.pcl2.PclHintHost.push(text, theme)
    }

    /** 把错误信息转换成可以显示的纯文本。 */
    fun renderError(tm: ErrorViewModel.ThrowableMessage): Pair<String, String> =
        tm.title.toAndroidString(activity) to tm.message.toAndroidString(activity)
}

/** 供页面获取运行时。 */
val LocalPclRuntime = staticCompositionLocalOf<PclRuntime> {
    error("PclRuntime 尚未提供，请在 PclMainScreen 内使用")
}

/**
 * 在 Activity 内创建并记住 [PclRuntime]。
 *
 * 顺带在组合进入时开始捕获日志、离开时停止。
 */
@Composable
fun rememberPclRuntime(): PclRuntime {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val runtime = remember(context) {
        PclRuntime(
            activity = context as Activity,
            scope = lifecycleOwner.lifecycleScope,
        )
    }
    DisposableEffect(runtime) {
        runtime.startLogCapture()
        onDispose { runtime.stopLogCapture() }
    }
    return runtime
}
