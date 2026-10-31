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
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zalith.pcl.R
import com.zalith.pcl.components.Components
import com.zalith.pcl.components.InstallableItem
import com.zalith.pcl.components.UnpackComponentsTask
import com.zalith.pcl.components.jre.Jre
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.components.jre.UnpackJnaTask
import com.zalith.pcl.components.jre.UnpackJreTask
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.base.BaseAppCompatActivity
import com.zalith.pcl.ui.screens.splash.SplashScreen
import com.zalith.pcl.ui.theme.ZalithLauncherTheme
import com.zalith.pcl.ui.theme.backgroundColor
import com.zalith.pcl.ui.theme.onBackgroundColor
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.viewmodel.SplashBackStackViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
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
    private val unpackItems: MutableList<InstallableItem> = ArrayList()
    private val finishedTaskCount = AtomicInteger(0)

    /** 解压任务只允许启动一次：按钮重复点不能把任务再排一遍 */
    private val taskStarted = AtomicBoolean(false)
    /** 本次解压失败的任务（任务名 to 原因），跑完统一记日志 */
    private val failures = ConcurrentLinkedQueue<Pair<String, String>>()

    private var pendingImportIntent: Intent? = null

    private val backStackViewModel: SplashBackStackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        initUnpackItems()
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
            finishSplash()
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
        // 自动解压已经结束，在进入主界面之前做一次 MobileGlues 检查：
        // 用户此时还停在「需要安装的内容」这一屏，询问不会被主界面盖住。
        if (promptMobileGluesIfNeeded()) return
        launchMain()
    }

    /**
     * 缺失 MobileGlues 时弹询问。返回 true 表示已经弹窗，主界面会在用户选择后启动。
     *
     * 插件本身必须由系统安装器安装，这里只负责发现缺失并给官方下载入口。
     */
    private fun promptMobileGluesIfNeeded(): Boolean {
        val prefs = getSharedPreferences("pcl2.mobileglues", MODE_PRIVATE)
        if (prefs.getBoolean("prompted", false)) return false
        val installed = runCatching {
            packageManager.getPackageInfo("com.fcl.plugin.mobileglues", 0)
            true
        }.getOrDefault(false)
        if (installed) return false

        prefs.edit().putBoolean("prompted", true).apply()
        MaterialAlertDialogBuilder(this)
            .setTitle("未检测到 MobileGlues 渲染器")
            .setMessage(
                "MobileGlues 是独立 APK 渲染器插件，不能直接打进启动器。\n\n" +
                    "安装官方插件后，它会出现在「设置 → 控制设置 → 渲染器」列表里。" +
                    "是否现在前往官方发布页下载安装？"
            )
            .setCancelable(false)
            .setPositiveButton("前往下载") { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/MobileGL-Dev/MobileGlues-release/releases")
                        )
                    )
                }
                launchMain()
            }
            .setNegativeButton("暂不") { _, _ -> launchMain() }
            .show()
        return true
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
            // QQ / 微信这类分享方给的是自己的 FileProvider URI，授权只在分享瞬间有效，
            // 转到 Pcl2Activity 后经常已经读不到（「找不到布局文件」）。
            // 布局文件很小，先在启动页复制到应用缓存，再把 file:// URI 往后传。
            val forwardUri = if (
                importType == IMPORT_TYPE_CONTROLS ||
                (importType == IMPORT_TYPE_AUTO &&
                    source.type?.contains("json", ignoreCase = true) == true)
            ) {
                cacheSharedFile(uri)
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
    private fun cacheSharedFile(uri: Uri): Uri {
        return runCatching {
            val name = runCatching {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull()?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: "shared_${System.currentTimeMillis()}.json"

            val safeName = name.replace(Regex("[^0-9A-Za-z._\\-\\u4e00-\\u9fa5]"), "_")
                .let { if (it.endsWith(".json", true)) it else "$it.json" }
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