/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 承载 PCL2 界面的独立 Activity。
 *
 * 该 Activity 与 ZalithLauncher 原有的 Material 界面完全隔离：
 * 只负责把 [PclMainScreen] 铺满屏幕，并按系统栏留出安全边距。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.activities

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.zalith.pcl.components.jre.RuntimeProvisioner
import com.zalith.pcl.game.plugin.PluginLoader
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.ui.base.BaseAppCompatActivity
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.PclMainScreen
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import com.zalith.pcl.ui.pcl2.data.PclImportBridge
import kotlinx.coroutines.launch

/**
 * PCL2 界面入口。
 *
 * 已在 [android.app.Activity] 层面锁定横屏（`sensorLandscape`），
 * 与 PCL2 的 850×500 桌面窗口比例最接近。
 */
class Pcl2Activity : BaseAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 外部分享进来的整合包 / 键位：SplashActivity 按「使用 PCL2 风格界面」分流到这里，
        // 由界面的 `PclExternalImportHost` 接手（整合包走 ModpackImporter、键位走
        // ControlManager）。之前这类请求只会转发给 MainActivity，于是用户长按文件点分享，
        // 界面会突然退回传统 ZL 界面。
        consumeImport(intent)
        // 渲染器与插件：这两步原本只写在 `MainActivity.onCreate` 里。
        // Pcl2Activity 可能被**冷启动直接拉起**（SplashActivity 按「使用 PCL2 风格界面作为
        // 主界面」这个开关直接跳到本界面），此时 MainActivity 根本不会被执行 —— 结果是：
        //   · 设置页的渲染器列表只有 `Renderers.init()` 里那 6 个内置渲染器，
        //     用户装的 FCL 渲染器插件一个都看不到（「不能用插件渲染器」）；
        //   · 就算强行存下了插件的唯一标识，`GameLauncher` 里的
        //     `Renderers.setCurrentRenderer()` 也会因为列表里查不到而回退到第一个内置渲染器。
        // `PluginLoader.loadAllPlugins` 内部有 `isInitialized` 守卫，从 MainActivity 过来时
        // 再调一次会直接返回，不会重复加载。
        Renderers.init()
        PluginLoader.loadAllPlugins(this, false)
        // 账号 / 认证服务器 / 游戏目录都在数据库与设置里，必须显式拉一次。
        // 原生界面是 MainActivity.onCreate 调的 `refreshData()`；PCL2 界面走的是自己的
        // Activity，之前漏了这一步 —— 于是 `AccountsManager.accountsFlow` 一直是空表，
        // 表现为「本次运行里新建的账号看得见，退出应用再进来全没了」
        // （新建时 `suspendSaveAccount` 会顺手 reload 一次，重启后就没人 reload 了）。
        refreshData()
        // ---- 每次打开启动器都自检 Java 运行库 ----------------------------------
        // 某些机型首次安装时会出岔子：解压页被打断（存储不足、进程被杀、用户直接退出），
        // 或者内置包和本机架构对不上（例如 JRE 25 没有 x86 的 bin-x86.tar.xz），
        // 结果「设置 → Java 运行库」里一个都没有，只能手动导入 .xz。
        // 这里在后台补一次：已经有可用运行库时立刻返回，一个都没有才自动装一个。
        lifecycleScope.launch {
            val hadNone = RuntimeProvisioner.currentUsable().isEmpty()
            val report = RuntimeProvisioner.ensure(this@Pcl2Activity, RuntimeProvisioner.Mode.REPAIR)
            when {
                report.installed.isNotEmpty() -> PclHintHost.push(
                    "已自动安装内置 Java 运行库：${report.installed.joinToString("、")}",
                    PclHintTheme.Blue,
                )

                hadNone && !report.hasUsable && report.failed.isNotEmpty() -> PclHintHost.push(
                    "Java 运行库不可用：${report.failed.first().second}",
                    PclHintTheme.Red,
                )

                // 没装、也没报错、还没跳过任何包 —— 说明内置包与本机架构对不上
                // （例如只打包了 arm 的 APK 跑在 x86 设备上）。
                // 这种情况不会自己好了，必须明确告诉用户去哪里补，否则他只看到空列表。
                hadNone && !report.hasUsable && report.skipped.isNotEmpty() -> PclHintHost.push(
                    "本机架构没有配套的内置 Java。请到「设置 → Java 运行库」导入 .xz 运行库。",
                    PclHintTheme.Red,
                )
            }
        }
        setContent {
            // **强制全屏：一点内边距都不留，刘海也不避。**
            //
            // 配合主题里的两条：`android:windowFullscreen=true`（系统栏已隐藏）+
            // `android:windowLayoutInDisplayCutoutMode=shortEdges`（内容允许画进挖孔区），
            // 界面就是物理屏幕的全尺寸，四边不留任何缝。
            //
            // 这个 modifier 上踩过的三个坑，正好是同一条 insets 的三档：
            //   · `WindowInsets.systemBars` —— 手势导航下手指碰到屏幕底部，
            //     那条小白条会临时冒出来，insets 随之在「有 / 无」之间跳，
            //     整页内容跟着上下抽动重排，表现为「点一下小白条画面往下飘 + 闪一下」。
            //   · `systemBarsIgnoringVisibility` —— 治好了抽动，但语义是
            //     「**不管系统栏可不可见，都按它的尺寸算**」，于是留下一条永远存在的
            //     padding，那块区域没人画背景 → 露出窗口底色 → 上下两条黑边。
            //   · `displayCutout` —— 黑边没了，但刘海那一侧仍被让开一条。
            // 用户要的是「我管你刘海屏什么的全部强制全屏」，所以三档全不要。
            PclMainScreen(modifier = Modifier.fillMaxSize())
        }
    }

    /**
     * 界面已经在跑的时候又分享进来一个文件：只会在 `onNewIntent` 里收到。
     *
     * `launchMode="singleTop"` + 转发时带的 `CLEAR_TOP`/`SINGLE_TOP` 会让已有的实例复用，
     * 不处理这个回调的话「第二次分享」会毫无反应。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeImport(intent)
    }

    /** 把 `EXTRA_IMPORT_*` 三个额外参数转成界面层的导入请求。 */
    private fun consumeImport(source: Intent?) {
        if (source == null) return
        val type = source.getStringExtra(EXTRA_IMPORT_TYPE) ?: return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.getParcelableExtra(EXTRA_IMPORT_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            source.getParcelableExtra(EXTRA_IMPORT_URI)
        }
        // 与 MainActivity 一样：取完就抹掉，避免配置变更 / 重建时重复导入
        source.removeExtra(EXTRA_IMPORT_TYPE)
        if (uri == null) return
        PclImportBridge.submit(type, uri)
    }

    override fun onBackPressed() {
        // 让系统返回键也能退出 PCL2 界面
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
