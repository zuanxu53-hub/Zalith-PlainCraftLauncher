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
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.pcl.R
import com.zalith.pcl.components.AbstractUnpackTask
import com.zalith.pcl.components.Components
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.components.UnpackComponentsTask
import com.zalith.pcl.components.jre.Jre
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.components.jre.UnpackJnaTask
import com.zalith.pcl.components.jre.UnpackJreTask
import com.zalith.pcl.game.download.engine.DownloadEngine
import com.zalith.pcl.game.download.engine.DownloadRequest
import com.zalith.pcl.game.download.engine.DownloadStats
import com.zalith.pcl.game.download.engine.SegmentedDownloader
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.base.BaseAppCompatActivity
import com.zalith.pcl.ui.pcl2.components.PclConfirmDialog
import com.zalith.pcl.ui.screens.splash.SplashScreen
import com.zalith.pcl.ui.theme.ZalithLauncherTheme
import com.zalith.pcl.ui.theme.backgroundColor
import com.zalith.pcl.ui.theme.onBackgroundColor
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.network.downloadFile
import com.zalith.pcl.utils.network.fetchStringFromUrl
import com.zalith.pcl.viewmodel.SplashBackStackViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "SplashActivity"

const val EXTRA_IMPORT_ACTION = "EXTRA_IMPORT_ACTION"
const val EXTRA_IMPORT_URI    = "EXTRA_IMPORT_URI"
const val EXTRA_IMPORT_TYPE   = "EXTRA_IMPORT_TYPE"

const val IMPORT_TYPE_MODPACK = "modpack"
const val IMPORT_TYPE_CONTROLS = "controls"
/** 「导入到启动器」通用入口：分享方给的是通配类型，具体类型由界面层按文件名后缀判断 */
const val IMPORT_TYPE_AUTO = "auto"
const val IMPORT_TYPE_UNKNOWN = "unknown"

@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
class SplashActivity : BaseAppCompatActivity() {
    private val unpackItems = mutableStateListOf<InstallableItem>()
    private val finishedTaskCount = AtomicInteger(0)

    /** 解压任务只允许启动一次：按钮重复点不能把任务再排一遍 */
    private val taskStarted = AtomicBoolean(false)
    /** 本次解压失败的任务（任务名 to 原因），跑完统一记日志 */
    private val failures = ConcurrentLinkedQueue<Pair<String, String>>()

    private var pendingImportIntent: Intent? = null

    /** 已下载、等待「安装未知应用」授权后继续安装的 MobileGlues APK。 */
    @Volatile
    private var pendingMobileGluesApk: File? = null
    /** 安装界面拉起后是否继续进入主界面。 */
    private var launchAfterMobileGlues = false
    /** 是否正在显示 MobileGlues 询问框（PCL 风格，画在解压页上）。 */
    private var askMobileGlues by mutableStateOf(false)

    private val backStackViewModel: SplashBackStackViewModel by viewModels()

    /** 用 startActivityForResult 等安装器真正返回，避免 onResume 被误触发后盖掉安装页。 */
    private val installerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        if (launchAfterMobileGlues) {
            launchAfterMobileGlues = false
            launchMain()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        initUnpackItems()
        checkAllTask()

        val prefs = getSharedPreferences("pcl2.mobileglues", MODE_PRIVATE)
        val mgInstalled = runCatching {
            packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
            true
        }.getOrDefault(false)
        askMobileGlues = !mgInstalled && !prefs.getBoolean("splash_asked_v2", false)

        if (!askMobileGlues && checkTasksToMain()) return
        setContent {
            ZalithLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    SplashScreen(
                        startAllTask = { startAllTask() },
                        unpackItems = unpackItems,
                        screenViewModel = backStackViewModel,
                    )
                }
            }
            if (askMobileGlues) {
                PclConfirmDialog(
                    title = "是否安装 MobileGlues 渲染器？",
                    content = "MobileGlues 可以提升部分机型的游戏渲染性能。\n\n" +
                        "选择「安装」后，它会加入「需要安装的内容」，点「同意并安装」时" +
                        "自动从 GitHub 下载并调起系统安装器。",
                    confirmText = "安装",
                    dismissText = "不安装",
                    dismissOnOutsideClick = false,
                    onConfirm = { resolveMobileGluesChoice(true) },
                    onDismiss = { resolveMobileGluesChoice(false) },
                )
            }
        }
    }

    private fun resolveMobileGluesChoice(install: Boolean) {
        getSharedPreferences("pcl2.mobileglues", MODE_PRIVATE)
            .edit()
            .putBoolean("splash_asked_v2", true)
            .putBoolean("prompted", true)
            .apply()
        askMobileGlues = false
        if (install) addMobileGluesItem()
        if (checkTasksToMain()) return
    }

    /** 询问结束（或无需询问）后再检查解压状态、挂载界面。 */
    private fun startSplashUi() {
        checkAllTask()

        if (checkTasksToMain()) {
            return
        }

        setContent {
            ZalithLauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor(),
                    contentColor = onBackgroundColor()
                ) {
                    SplashScreen(
                        startAllTask = { startAllTask() },
                        unpackItems = unpackItems,
                        screenViewModel = backStackViewModel,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        //若依赖未完成，暂存外部导入，待依赖完成后处理
        if (!areAllTasksFinished()) {
            if (isImportIntent(intent) && !isLauncherIntent(intent)) {
                pendingImportIntent = intent
                Logger.info(TAG, "Import intent received but dependencies are not ready, deferring.")
            }
            return
        }

        if (isImportIntent(intent) && !isLauncherIntent(intent)) {
            handleImportIntent(intent)
            finish()
        }
    }

    private fun initUnpackItems() {
        Components.entries.forEach { component ->
            val task = UnpackComponentsTask(this@SplashActivity, component)
            if (!task.isCheckFailed()) {
                unpackItems.add(
                    InstallableItem(
                        component.displayName,
                        getString(component.summary),
                        task
                    )
                )
            }
        }
        Jre.entries.forEach { jre ->
            val task = UnpackJreTask(this@SplashActivity, jre)
            if (!task.isCheckFailed()) {
                unpackItems.add(
                    InstallableItem(
                        jre.jreName,
                        getString(jre.summary),
                        task
                    )
                )
            }
        }
        val jnaTask = UnpackJnaTask(this@SplashActivity)
        if (!jnaTask.isCheckFailed()) {
            unpackItems.add(
                InstallableItem(
                    "JNA",
                    getString(R.string.unpack_screen_jna),
                    jnaTask
                )
            )
        }
        unpackItems.sort()
    }

    private fun checkAllTask() {
        //检查应用 assets 目录
        listAssetsPath("runtimes").forEach { filePath ->
            Logger.info(TAG, "The launcher contains the runtime environment: $filePath")
        }

        unpackItems.forEach { item ->
            val state = item.task.checkState()
            item.updateState(state)
            if (state == InstallableItem.State.FINISHED) {
                finishedTaskCount.incrementAndGet()
            }
        }
    }

    private fun listAssetsPath(root: String): List<String> {
        return buildList {
            val rootFiles = runCatching {
                assets.list(root)?.takeIf { it.isNotEmpty() }
            }.getOrNull()
            if (rootFiles != null) {
                rootFiles.forEach { child ->
                    val childPath = "$root/$child"
                    val childFiles = runCatching {
                        assets.list(childPath)?.takeIf { it.isNotEmpty() }
                    }.getOrNull()

                    if (childFiles != null) {
                        addAll(listAssetsPath(childPath))
                    } else {
                        add(childPath)
                    }
                }
            } else {
                add(root)
            }
        }
    }

    private fun startAllTask() {
        // 界面上只有一个「同意并安装」按钮，重复点也只跑一次
        if (!taskStarted.compareAndSet(false, true)) return

        lifecycleScope.launch {
            val jobs = unpackItems
                .filter {
                    val state = it.state.value
                    state == InstallableItem.State.NOT_STARTED ||
                    state == InstallableItem.State.PENDING
                }
                .map { item ->
                    launch(Dispatchers.IO) {
                        item.updateState(InstallableItem.State.RUNNING)
                        // **单项失败绝不能往外抛。**
                        // 原来这里是 `onFailure { throw SplashException(it) }`：异常落到子协程，
                        // 父协程随之取消 —— 排队的兄弟任务全被打断。存储紧张的机型上
                        // （解压四个运行库要 1GB 以上）装到一半空间不足，结果一个 Java 都没留下，
                        // 用户看到的就是「首次安装后 Java 列表是空的」。
                        val ok = runCatching { item.task.run() }
                            .onFailure { e ->
                                val reason = e.message ?: e::class.java.simpleName
                                Logger.error(TAG, "Failed to unpack ${item.name}: $reason", e)
                                failures.add(item.name to reason)
                            }
                            .isSuccess
                        // 失败退回「未安装」：界面上如实显示没装上，而不是假装完成
                        item.updateState(
                            if (ok) InstallableItem.State.FINISHED else InstallableItem.State.NOT_STARTED
                        )
                        if (ok) finishedTaskCount.incrementAndGet()
                    }
                }
            jobs.joinAll()
        }.invokeOnCompletion {
            // 不再无条件写 `Internal-8`：本机架构没有这个内置包时（例如 JRE 25 不带 x86 的
            // `bin-x86.tar.xz`），那个值是「指向空气」的，设置里看着已选中、启动却找不到运行库。
            runCatching { RuntimeProvisioner.ensureDefaultSelection() }
                .onFailure { Logger.warning(TAG, "Failed to correct the default java runtime", it) }

            if (failures.isNotEmpty()) {
                Logger.error(
                    TAG,
                    "Some dependencies failed to unpack: " +
                            failures.joinToString { "${it.first}（${it.second}）" }
                )
            }

            // MobileGlues 下载完先调系统安装器，安装界面拉起后再进主界面。
            val apk = pendingMobileGluesApk
            if (apk != null && apk.isFile) {
                launchAfterMobileGlues = true
                installMobileGluesApk(apk)
            } else {
                finishSplash()
            }
        }
    }

    private fun finishSplash() {
        val import = pendingImportIntent
            ?.takeIf { isImportIntent(it) && !isLauncherIntent(it) }
            ?: intent.takeIf { isImportIntent(it) && !isLauncherIntent(it) }

        if (import != null && handleImportIntent(import)) {
            finish()
        } else {
            swapToMain()
        }
    }

    private fun checkTasksToMain(): Boolean {
        if (!areAllTasksFinished()) return false

        Logger.info(TAG, "All content that needs to be extracted is already the latest version!")

        if (isImportIntent(intent) && !isLauncherIntent(intent)) {
            val success = handleImportIntent(intent)
            if (success) {
                finish()
                return true
            }
        }

        swapToMain()
        return true
    }

    private fun swapToMain() {
        // MobileGlues 是否安装已经在列表出现之前问过了，这里不再弹第二次。
        launchMain()
    }

    /**
     * 在「需要安装的内容」出现之前询问是否安装 MobileGlues。
     *
     * 选「是」就把一项加入解压列表，随「同意并安装」一起下载并调起安装器；
     * 选「否」列表保持原样。两个选择都只问一次。
     */
    private fun maybeAskMobileGlues(onDone: () -> Unit) {
        val prefs = getSharedPreferences("pcl2.mobileglues", MODE_PRIVATE)
        val installed = runCatching {
            packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
            true
        }.getOrDefault(false)
        if (installed || prefs.getBoolean("splash_asked_v2", false)) {
            onDone()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("是否安装 MobileGlues 渲染器？")
            .setMessage(
                "MobileGlues 可以提升部分机型的游戏渲染性能。\n\n" +
                    "选择「安装」后，它会出现在下面的「需要安装的内容」列表里，" +
                    "点「同意并安装」时自动下载并调起系统安装器。"
            )
            .setCancelable(false)
            .setPositiveButton("安装") { _, _ ->
                prefs.edit().putBoolean("splash_asked_v2", true).putBoolean("prompted", true).apply()
                addMobileGluesItem()
                onDone()
            }
            .setNegativeButton("不安装") { _, _ ->
                prefs.edit().putBoolean("splash_asked_v2", true).putBoolean("prompted", true).apply()
                onDone()
            }
            .show()
    }

    private fun addMobileGluesItem() {
        val task = MobileGluesInstallTask(this@SplashActivity) { apk ->
            pendingMobileGluesApk = apk
        }
        unpackItems.add(
            InstallableItem(
                name = "MobileGlues",
                summary = "为 Minecraft 提供 OpenGL ES 渲染支持（官方独立插件）",
                task = task,
            )
        )
        unpackItems.sort()
    }

    /**
     * 缺失 MobileGlues 时弹询问。返回 true 表示已经弹窗，主界面会在用户选择后启动。
     *
     * 插件本身必须由系统安装器安装，这里只负责发现缺失并给官方下载入口。
     */
    private fun promptMobileGluesIfNeeded(): Boolean {
        val prefs = getSharedPreferences("pcl2.mobileglues", MODE_PRIVATE)
        // 用独立键：之前版本可能在主界面弹过一次（旧键 prompted），
        // 不能让那次的「暂不」把这次解压页的询问也吞掉。
        if (prefs.getBoolean("splash_prompted", false)) return false
        val installed = runCatching {
            packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
            true
        }.getOrDefault(false)
        if (installed) return false

        prefs.edit().putBoolean("splash_prompted", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("未检测到 MobileGlues 渲染器")
            .setMessage(
                "MobileGlues 是独立 APK 渲染器插件，不能直接打进启动器。\n\n" +
                    "安装官方插件后，它会出现在「设置 → 控制设置 → 渲染器」列表里。" +
                    "是否现在前往官方发布页下载安装？"
            )
            .setCancelable(false)
            .setPositiveButton("下载安装") { _, _ -> downloadMobileGlues() }
            .setNegativeButton("暂不") { _, _ -> launchMain() }
            .show()
        return true
    }

    /**
     * 从 GitHub API 找最新 release 的 .apk 资产，直接下载后调起系统安装器。
     * 失败回退到 release 页面，至少不让用户卡在启动页。
     */
    private fun downloadMobileGlues() {
        val progress = MaterialAlertDialogBuilder(this)
            .setTitle("正在下载 MobileGlues")
            .setMessage("正在获取最新版本…")
            .setCancelable(false)
            .create()
        progress.show()

        lifecycleScope.launch {
            val result = runCatching {
                val json = org.json.JSONObject(
                    fetchStringFromUrl(
                        "https://api.github.com/repos/MobileGL-Dev/MobileGlues-release/releases/latest"
                    )
                )
                val assets = json.optJSONArray("assets") ?: error("发布页没有资产列表")
                var apkUrl: String? = null
                var apkName: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
                val url = apkUrl ?: error("最新版本里没有找到 APK")
                val dir = File(cacheDir, "mobileglues").apply { mkdirs() }
                val target = File(dir, apkName ?: "MobileGlues.apk")
                runCatching { if (target.exists()) target.delete() }
                downloadFile(url = url, outputFile = target)
                if (!target.isFile || target.length() <= 0L) error("下载的文件为空")
                target
            }

            progress.dismiss()
            result.onSuccess { apk ->
                launchAfterMobileGlues = true
                installMobileGluesApk(apk)
            }.onFailure { e ->
                Logger.warning(TAG, "MobileGlues 下载失败", e)
                MaterialAlertDialogBuilder(this@SplashActivity)
                    .setTitle("下载失败")
                    .setMessage("${e.message ?: e.javaClass.simpleName}\n\n可以改从官方发布页手动下载。")
                    .setCancelable(false)
                    .setPositiveButton("打开网页") { _, _ ->
                        openMobileGluesPage()
                        launchMain()
                    }
                    .setNegativeButton("关闭") { _, _ -> launchMain() }
                    .show()
            }
        }
    }

    private fun installMobileGluesApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingMobileGluesApk = file
            MaterialAlertDialogBuilder(this)
                .setTitle("需要安装权限")
                .setMessage("请允许本应用安装未知应用，授权后返回会自动继续安装 MobileGlues。")
                .setPositiveButton("去授权") { _, _ ->
                    runCatching {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:$packageName"),
                            )
                        )
                    }
                }
                .setNegativeButton("取消") { _, _ ->
                    pendingMobileGluesApk = null
                    launchMain()
                }
                .setCancelable(false)
                .show()
            return
        }

        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            pendingMobileGluesApk = null
            installerLauncher.launch(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure { e ->
            Logger.warning(TAG, "无法调起 MobileGlues 安装器", e)
            Toast.makeText(
                this,
                "无法打开安装程序：${e.message ?: e.javaClass.simpleName}",
                Toast.LENGTH_LONG,
            ).show()
            launchMain()
        }
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingMobileGluesApk
        if (apk != null) {
            // 从「安装未知应用」授权页回来：权限已给就继续安装
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                packageManager.canRequestPackageInstalls()
            ) {
                installMobileGluesApk(apk)
            }
            return
        }
        // 安装器返回由 installerLauncher 处理；onResume 只负责授权页回来后的续装。
    }

    private fun openMobileGluesPage() {
        runCatching {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/MobileGL-Dev/MobileGlues-release/releases"),
                )
            )
        }
    }

    private fun launchMain() {
        //根据设置决定进入 PCL2 风格界面还是原生 Material 界面
        val target = if (AllSettings.usePcl2Ui.getValue()) {
            Pcl2Activity::class.java
        } else {
            MainActivity::class.java
        }
        val forward = Intent(this, target).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(forward)
        finish()
    }



    private fun handleImportIntent(source: Intent): Boolean {
        if (!isImportIntent(source)) return false

        val uri: Uri? = when (source.action) {
            Intent.ACTION_SEND -> {
                source.clipData?.getItemAt(0)?.uri
                    ?: source.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> source.data
            else -> null
        }

        if (uri == null) {
            Logger.warning(TAG, "No valid import Uri found")
            return false
        } else {
            try {
                //可持久化访问授权
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                Logger.warning(TAG, "No persistable permission granted for $uri")
            }
        }

        val forward = Intent(this, importTarget()).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            val importType = resolveImportType(source)
            // QQ / 微信 / MT 管理器分享的是对方 FileProvider 的临时 URI，
            // 转发到 Pcl2Activity 后授权经常已经失效，整合包会直接报 Permission Denial。
            // 所有分享文件都先落到启动器缓存，后面只读自己的 file:// URI。
            val forceJson = importType == IMPORT_TYPE_CONTROLS ||
                (importType == IMPORT_TYPE_AUTO &&
                    source.type?.contains("json", ignoreCase = true) == true)
            val forwardUri = if (uri.scheme == "content") {
                if (importType == IMPORT_TYPE_MODPACK) {
                    Toast.makeText(this@SplashActivity, "正在读取分享的整合包…", Toast.LENGTH_SHORT).show()
                }
                cacheSharedFile(uri, forceJson = forceJson)
            } else {
                uri
            }

            putExtra(EXTRA_IMPORT_ACTION, source.action)
            putExtra(EXTRA_IMPORT_URI, forwardUri)
            putExtra(EXTRA_IMPORT_TYPE, importType)
        }

        startActivity(forward)
        return true
    }

    /**
     * 把分享进来的小文件复制到应用缓存，返回自己的 file:// URI。
     *
     * 分享方的 content URI 往往只带一次性读取授权；跨 Activity 后 provider 已经拒绝访问。
     * 复制失败时退回原 URI，至少保留原来的错误路径。
     */
    private fun cacheSharedFile(uri: Uri, forceJson: Boolean = false): Uri {
        return runCatching {
            val name = runCatching {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "shared_${System.currentTimeMillis()}"

            val safeName = name.replace(Regex("[^0-9A-Za-z._\\-\\u4e00-\\u9fa5]"), "_")
                .let {
                    when {
                        forceJson && !it.endsWith(".json", true) -> "$it.json"
                        !it.contains('.') -> "$it.zip"
                        else -> it
                    }
                }
            val dir = File(cacheDir, "share_import").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}_$safeName")
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching uri
            if (target.length() > 0L) Uri.fromFile(target) else uri
        }.getOrDefault(uri)
    }

    /**
     * 导入请求该交给哪个界面。
     *
     * 原来这里**写死了 `MainActivity`** —— 于是「长按整合包 / 键位 → 分享 → 启动器」
     * 会无视「使用 PCL2 风格界面」这个开关，直接把人踹回传统 Material 界面。
     * 现在跟着启动分流走：PCL2 界面开着就交给 [Pcl2Activity]（由它的
     * `PclExternalImportHost` 接手导入），否则仍走原生界面。
     */
    private fun importTarget(): Class<out BaseAppCompatActivity> =
        if (AllSettings.usePcl2Ui.getValue()) Pcl2Activity::class.java else MainActivity::class.java

    /**
     * 根据 AndroidManifest 内为 activity-alias 配置的 meta-data 来判断导入类型
     */
    private fun resolveImportType(intent: Intent): String {
        val comp = intent.component ?: return IMPORT_TYPE_UNKNOWN
        val info = packageManager.getActivityInfo(comp, PackageManager.GET_META_DATA)
        return info.metaData?.getString("import_type") ?: IMPORT_TYPE_UNKNOWN
    }

    private fun isLauncherIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        return intent.action == Intent.ACTION_MAIN &&
                intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
    }

    private fun isImportIntent(intent: Intent?): Boolean {
        val comp = intent?.component ?: return false
        val info = packageManager.getActivityInfo(comp, PackageManager.GET_META_DATA)
        return info.metaData?.getString("import_type") != null
    }

    private fun areAllTasksFinished(): Boolean {
        return finishedTaskCount.get() >= unpackItems.size
    }
}

/**
 * 列表里的 MobileGlues 项：从 GitHub 最新 release 下载 APK 到缓存，
 * 下载完成后由 SplashActivity 调起系统安装器。
 */
private class MobileGluesInstallTask(
    context: android.content.Context,
    private val onDownloaded: (File) -> Unit,
) : AbstractUnpackTask() {
    private val appContext = context.applicationContext

    override fun checkState(): InstallableItem.State =
        if (runCatching {
                appContext.packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
                true
            }.getOrDefault(false)
        ) {
            InstallableItem.State.FINISHED
        } else {
            InstallableItem.State.NOT_STARTED
        }

    override suspend fun run() {
        updateMessage("正在获取最新版本…")
        val json = org.json.JSONObject(
            fetchStringFromUrl(
                "https://api.github.com/repos/MobileGL-Dev/MobileGlues-release/releases/latest"
            )
        )
        val assets = json.optJSONArray("assets") ?: error("发布页没有资产列表")
        var apkUrl: String? = null
        var apkName: String? = null
        var apkSize = -1L
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkName = name
                apkUrl = asset.optString("browser_download_url")
                apkSize = asset.optLong("size", -1L)
                break
            }
        }
        val url = apkUrl ?: error("最新版本里没有找到 APK")
        val dir = File(appContext.cacheDir, "mobileglues").apply { mkdirs() }
        val target = File(dir, apkName ?: "MobileGlues.apk")
        runCatching { if (target.exists()) target.delete() }

        val stats = DownloadStats()
        coroutineScope {
            val progress = launch {
                while (isActive) {
                    val done = stats.downloadedBytes
                    val pct = if (apkSize > 0) {
                        (done * 100 / apkSize).coerceIn(0, 100)
                    } else {
                        -1
                    }
                    updateMessage(
                        if (pct >= 0) {
                            "正在下载 ${apkName ?: "MobileGlues.apk"}… $pct%（${done / 1048576} / ${apkSize / 1048576} MB，64 线程）"
                        } else {
                            "正在下载 ${apkName ?: "MobileGlues.apk"}… ${done / 1048576} MB"
                        }
                    )
                    delay(200)
                }
            }
            try {
                // GitHub 的 APK 只有十来 MB，默认最少 2 MB 一段、最多 8 段，速度上不去。
                // 这里单独按 64 段下载；服务器不支持 Range 时自动回退到普通下载。
                val segmented = apkSize > 0L && SegmentedDownloader.downloadOrNull(
                    urls = listOf(url),
                    targetFile = target,
                    sha1 = null,
                    expectedSize = apkSize,
                    onBytes = { delta -> stats.addBytes(delta) },
                    maxSegments = 64,
                    minSegmentSize = 128L * 1024,
                )
                if (!segmented) {
                    DownloadEngine.download(
                        request = DownloadRequest(
                            urls = listOf(url),
                            targetFile = target,
                            expectedSize = apkSize,
                        ),
                        stats = stats,
                    )
                }
            } finally {
                progress.cancelAndJoin()
            }
        }
        if (!target.isFile || target.length() <= 0L) error("下载的文件为空")
        updateMessage(null)
        onDownloaded(target)
    }
}