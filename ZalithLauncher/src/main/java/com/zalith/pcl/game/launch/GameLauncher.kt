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

package com.zalith.pcl.game.launch

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import android.widget.Toast
import androidx.annotation.Keep
import androidx.compose.ui.unit.IntSize
import com.zalith.pcl.BuildConfig
import com.zalith.pcl.R
import com.zalith.pcl.ZLApplication
import com.zalith.pcl.bridge.LoggerBridge.appendInfo
import com.zalith.pcl.bridge.LoggerBridge.appendTitle
import com.zalith.pcl.bridge.ZLBridge
import com.zalith.pcl.context.readAssetFile
import com.zalith.pcl.game.account.Account
import com.zalith.pcl.game.account.AccountType
import com.zalith.pcl.game.account.offline.OfflineYggdrasilServer
import com.zalith.pcl.game.addons.modloader.ModLoader
import com.zalith.pcl.game.download.game.parseLibraryComponents
import com.zalith.pcl.game.multirt.Runtime
import com.zalith.pcl.game.multirt.RuntimesManager
import com.zalith.pcl.game.plugin.Plugin
import com.zalith.pcl.game.plugin.driver.DriverPluginManager
import com.zalith.pcl.game.plugin.renderer.RendererPluginManager
import com.zalith.pcl.game.renderer.RendererCompat
import com.zalith.pcl.game.renderer.Renderers
import com.zalith.pcl.game.renderer.renderers.GL4ESRenderer
import com.zalith.pcl.game.renderer.renderers.NGGL4ESRenderer
import com.zalith.pcl.game.support.touch_controller.ControllerProxy
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionInfoParser
import com.zalith.pcl.game.versioninfo.models.GameManifest
import com.zalith.pcl.path.LibPath
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.GSON
import com.zalith.pcl.utils.device.Architecture
import com.zalith.pcl.utils.file.child
import com.zalith.pcl.utils.file.ensureDirectorySilently
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.string.isBiggerTo
import com.zalith.pcl.utils.string.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext

private const val TAG = "GameLauncher"

@Keep
@Parcelize
class LaunchConfig(
    val version: Version,
    val account: Account,
): Parcelable

class GameLauncher(
    private val activity: Activity,
    config: LaunchConfig,
    onExit: (code: Int, isSignal: Boolean) -> Unit,
    openPath: (folder: File) -> Unit
) : Launcher(onExit, openPath) {
    private lateinit var gameManifest: GameManifest
    private var jnaDir: File? = null
    private val offlineServer = OfflineYggdrasilServer(0)

    private val version = config.version
    private val usingAccount = if (version.offlineAccountLogin) {
        //使用临时离线账号启动游戏
        config.account.copy(
            accountType = AccountType.LOCAL.tag
        )
    } else {
        config.account
    }

    override fun exit() {
        offlineServer.stop()
    }

    override suspend fun launch(screenSize: IntSize): Int {
        // 每次启动都按「版本级设置 ?: 全局设置」重新解析一次渲染器，
        // 这样用户在设置里改完渲染器后立刻生效（原来只在渲染器尚未解析时才设置，
        // 从 PCL2 界面进入的第二次启动会一直沿用上一次的结果）。
        Renderers.setCurrentRenderer(version.getRenderer())

        // 渲染器兼容性自动适配：Sodium / Iris 这类模组依赖桌面 OpenGL 3.3+，
        // GL4ES 翻译层会直接卡在加载界面，这里在用户没手动指定渲染器时自动换用 Kopper Zink。
        withContext(Dispatchers.IO) { RendererCompat.applyAutoCompat(version, activity) }

        val manifest = GSON.fromJson(File(version.getVersionPath(), "${version.getVersionName()}.json").readText(), GameManifest::class.java)
        val clientJar = manifest.inheritsFrom?.let { inheritsFrom ->
            //FIXME: 依赖的是一个原版ID的版本，但这个版本可能是用户自行安装的，只是版本名称与ID一致，不保证客户端真的是对应版本
            version.getInheritedClientJar(inheritsFrom)
        } ?: version.getClientJar()

        gameManifest = version.launchManifest?.let { json ->
            runCatching {
                GSON.fromJson(json, GameManifest::class.java)
            }.onFailure {
                Logger.warning(TAG, "Failed to parse the carried launch manifest", it)
            }.getOrNull()
        } ?: VersionInfoParser(version)
            .setManifest(manifest)
            .setInheriting()
            .build()

        //jna
        jnaDir = gameManifest.libraries?.find { library ->
            library.name.startsWith("net.java.dev.jna:jna:")
        }?.let { library ->
            parseLibraryComponents(library.name).version
        }?.let { jnaVersion ->
            File(LibPath.JNA, jnaVersion)
        }?.takeIf { it.exists() }

        CallbackBridge.nativeSetUseInputStackQueue(gameManifest.arguments != null)
        //Cleanroom 的 LWJGLXX 桥需要字符事件补全暂存的按键事件，见 CallbackBridge.setControlKeyCharPairing
        CallbackBridge.setControlKeyCharPairing(
            version.getVersionInfo()?.loaderInfos?.any { info ->
                info.loader == ModLoader.CLEANROOM
            } == true
        )

        val customArgs = version.getJvmArgs().takeIf { it.isNotBlank() } ?: AllSettings.jvmArgs.getValue()
        val javaRuntime = getRuntime()

        printLauncherInfo(
            javaArguments = customArgs.takeIf { it.isNotEmpty() } ?: "NONE",
            javaRuntime = javaRuntime,
        )

        initLwjglComponent(activity, detectLwjglVersion(gameManifest))

        return launchGame(
            screenSize = screenSize,
            clientJar = clientJar,
            javaRuntime = javaRuntime,
            customArgs = customArgs,
        )
    }

    override fun MutableMap<String, String>.putJavaArgs() {
        val versionInfo = version.getVersionInfo()
        //Fix Forge 1.7.2
        val is172 = (versionInfo?.minecraftVersion ?: "0.0").isEqualTo("1.7.2")
        if (is172 && versionInfo?.hasLoader(ModLoader.FORGE) == true) {
            Logger.debug(TAG, "Is Forge 1.7.2, use the patched sorting method.")
            put("sort.patch", "true")
        }

        //Jna
        jnaDir?.let { dir ->
            val dirPath = dir.absolutePath
            put("jna.boot.library.path", dirPath) //覆盖父类添加的jna路径
        }
    }

    override fun chdir(): String {
        return version.getGameDir().absolutePath
    }

    override fun getMinecraftPath(): String = version.getGameHome()

    override fun getLogFile(): File = version.getLatestLog()

    override fun initEnv(screenSize: IntSize): MutableMap<String, String> {
        val envMap = super.initEnv(screenSize)

        envMap["DRIVER_PATH"] = DriverPluginManager.getDriver(version.getDriver()).path

        checkAndUsedJSPH(envMap, runtime)
        version.getVersionInfo()?.loaderInfos?.forEach { info ->
            info.getLoaderEnvKey()?.let { loaderKey ->
                envMap[loaderKey] = "1"
            }
        }
        if (Renderers.isCurrentRendererValid()) {
            setRendererEnv(envMap)
        }
        envMap["ZALITH_VERSION_CODE"] = BuildConfig.VERSION_CODE.toString()

        //lwjgl3ify 实例：注入 XDG 数据目录兜底，避免其向不存在的 ~/.local/share
        //写桌面快捷方式时抛出异常杀死 RFB 主线程，导致游戏静默退出
        if (File(version.getGameDir(), "config/lwjgl3ify.cfg").isFile()) {
            val xdgDataHome = File(version.getGameDir(), ".local/share")
            if (!xdgDataHome.isDirectory) xdgDataHome.mkdirs()
            envMap["XDG_DATA_HOME"] = xdgDataHome.absolutePath
        }
        return envMap
    }

    override fun dlopenEngine() {
        super.dlopenEngine()
        appendTitle("DLOPEN Renderer")

        //声音引擎加载后，dlopen渲染器的库
        RendererPluginManager.selectedRendererPlugin?.let { renderer ->
            val libs by renderer.getDlopenLibrary()
            libs.forEach { libPath ->
                ZLBridge.dlopen(libPath)
            }
        }

        val rendererLib = getRendererLibrary() ?: return
        if (!ZLBridge.dlopen(rendererLib) && !ZLBridge.dlopen(findInLdLibPath(rendererLib))) {
            Logger.error(TAG, "Failed to load renderer $rendererLib")
        }
    }

    override fun progressFinalUserArgs(args: MutableList<String>, ramAllocation: Int) {
        super.progressFinalUserArgs(args, version.getRamAllocation(activity))
        if (Renderers.isCurrentRendererValid()) {
            args.add("-Dorg.lwjgl.opengl.libname=${getRendererLibrary()}")
        }
    }

    private suspend fun launchGame(
        screenSize: IntSize,
        clientJar: File,
        javaRuntime: String,
        customArgs: String
    ): Int {
        val runtime = RuntimesManager.forceReload(javaRuntime)

        val gameDirPath = version.getGameDir()

        disableSplash(gameDirPath)

        //初始化运行环境
        this.runtime = runtime
        val launchArgs = LaunchArgs(
            runtimeLibraryPath = getRuntimeLibraryPath(),
            account = usingAccount,
            offlineServer = offlineServer,
            gameDirPath = gameDirPath,
            version = version,
            clientJar = clientJar,
            gameManifest = gameManifest,
            lwjglVersion = lwjglVersion,
            runtime = runtime,
            readAssetsFile = { path -> activity.readAssetFile(path) },
            getCacioJavaArgs = { isJava8 ->
                getCacioJavaArgs(screenSize, isJava8)
            }
        ).getAllArgs()

        tryStartTouchProxy()

        return launchJvm(
            context = activity,
            jvmArgs = launchArgs,
            userHome = version.getGameHome(),
            userArgs = customArgs,
            screenSize = screenSize
        )
    }

    override fun getRuntimeLibraryPath(): String {
        val parent = super.getRuntimeLibraryPath()
        return jnaDir?.absolutePath?.let { dirPath ->
            "$parent:$dirPath"
        } ?: parent
    }

    private fun tryStartTouchProxy() {
        if (version.enableTouchProxy) {
            ControllerProxy.startProxy(
                context = activity,
                vibrateDuration = version.getTouchVibrateDuration(),
                vibrateKind = version.getTouchVibrateKind(),
            )
        }
    }

    /**
     * 设备对外暴露的 Vulkan 版本，写进启动日志。
     *
     * MC 26.2 起游戏自带 Vulkan 后端，硬性要求 **Vulkan 1.2 + 一批扩展/特性**
     * （VK_KHR_synchronization2、VK_EXT_vertex_attribute_divisor、VK_KHR_dynamic_rendering、
     * VK_KHR_push_descriptor、timelineSemaphore、shaderDrawParameters 等）。
     * 设备只报得到 1.0 / 1.1 时这条路必然走不通，游戏退回 OpenGL；
     * 而 Kopper Zink 这类渲染器本身也是跑在 Vulkan 上的，老驱动上常常连 GL 上下文都建不出来。
     * 把版本号直接写进日志，出问题时一眼就能看出卡在哪一层。
     */
    private fun describeVulkanSupport(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return "not supported (API < 24)"
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)) {
            return "not supported"
        }
        //feature 的 version 字段就是 Vulkan 版本号：major << 22 | minor << 12 | patch
        val raw = pm.systemAvailableFeatures
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?.version
            ?: return "unknown version"
        val major = (raw shr 22) and 0x7F
        val minor = (raw shr 12) and 0x3FF
        val patch = raw and 0xFFF
        val text = "$major.$minor.$patch"
        return if (major > 1 || (major == 1 && minor >= 2)) {
            text
        } else {
            "$text (below 1.2, which Minecraft 26.2+ requires for its Vulkan backend)"
        }
    }

    private fun printLauncherInfo(
        javaArguments: String,
        javaRuntime: String,
    ) {
        var mcInfo = version.getVersionName()
        version.getVersionInfo()?.let { info -> mcInfo = info.getInfoString() }
        val renderer = Renderers.getCurrentRenderer()

        appendTitle("Launch Minecraft")
        appendInfo("Launcher version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendInfo("Architecture: ${Architecture.archAsString(ZLApplication.DEVICE_ARCHITECTURE)}")
        appendInfo("Device model: ${Build.MANUFACTURER}, ${Build.MODEL}")
        appendInfo("API version: ${Build.VERSION.SDK_INT}")
        appendInfo("Vulkan: ${describeVulkanSupport(activity)}")
        // 标一下这个渲染器是「用户点过名」还是「默认落到它头上」的：
        // 出问题时日志里能一眼看出是不是「我根本没选，是它自己上的」。
        // 「自动选择」已经删掉了，空值现在等价于默认渲染器（见 `Version.getRenderer`）。
        val explicitRenderer = version.getVersionConfig().renderer
            .ifEmpty { AllSettings.renderer.getValue() }
        val usingDefault = explicitRenderer.isEmpty()
        val rendererLabel = if (usingDefault) {
            "${renderer.getRendererName()} (默认)"
        } else {
            renderer.getRendererName()
        }
        appendInfo("Renderer: $rendererLabel")
        if (usingDefault) {
            appendInfo("Renderer Default: 没有手动指定渲染器，本次使用默认的 ${renderer.getRendererName()}")
        }
        renderer.getRendererSummary()?.let { summary ->
            appendInfo("Renderer Summary: $summary")
        }
        appendInfo("Selected Minecraft version: ${version.getVersionName()}")
        appendInfo("Minecraft Info: $mcInfo")
        appendInfo("Game Path: ${version.getGameDir().absolutePath} (Isolation: ${version.isIsolation()})")
        appendInfo("Custom Java arguments: $javaArguments")
        appendInfo("Java Runtime: $javaRuntime")
        appendInfo("Account: ${usingAccount.username} (${usingAccount.accountType})")
    }

    /**
     * 决定本次启动用哪个 Java 运行库。
     *
     * 最低版本要求来自「游戏清单里的 javaVersion」或加载器自己的要求
     * （Babric 17、Cleanroom 0.5+ 要 25 / 0.4.4 要 21，MC 26.2 起清单里写的 25）。
     *
     * 挑选规则（完全按「设备上实际装了什么」来定）：
     * 1. **玩家手动指定过运行库（版本独立设置或全局设置）→ 一律按他的选择启动，绝不替换**。
     *    版本低于游戏要求也只写日志 + 一条提示，不替他改 —— 很多人是「故意用低版本 Java」
     *    （比如某些整合包在 25 上有问题、或者只想试一下），自动换成高版本反而破坏了排查条件。
     * 2. 「自动选择」时才在已安装运行库里挑 **≥ 要求的最低版本**（不是最高版本）。
     *    高版本不是越新越好：老版本 Minecraft（1.12 及更早那批）跑在 17+ 上会因为
     *    LWJGL / 反射限制直接崩，1.16 也会偶发问题，所以「够用就好」比「最新最好」稳。
     * 3. 一个达标都没有：用当前已装的最高版本启动，把「需要 Java N」明确写进日志与提示，
     *    不去伪造一个不存在的运行库。
     *
     * 以前这里只调 `getNearestJreName`，它既不检查架构（32 位设备上可能挑到 arm64 包，
     * 一启动就 `Exec format error`），也不区分「自动选择」和「用户手动指定」，
     * 所以手动指定了过旧版本时只能靠 Toast 提示、自动档却可能挑到过高版本。
     *
     * 手动选择唯一的例外是**这个运行库在本机根本执行不了**（已被卸载，或架构与本机不一致）——
     * 那种情况下「尊重选择」等于让游戏必然 `Exec format error`，只能临时换一个并说明原因。
     */
    private fun getRuntime(): String {
        val loaderInfo = version.getVersionInfo()?.primaryLoader
        val requiredJavaVersion = when (loaderInfo?.loader) {
            ModLoader.BABRIC -> 17 //Babric 推荐使用 17
            ModLoader.CLEANROOM -> {
                if (loaderInfo.version.isBiggerTo("0.4.4-alpha")) {
                    25 //0.5.0-alpha 及以上要求使用 25
                } else {
                    21 //0.4.4-alpha 及以下要求使用 21
                }
            }
            else -> gameManifest.javaVersion?.majorVersion ?: 8
        }

        // 版本独立设置优先；没设置才跟随全局（两者都为空 = 设置里的「自动选择」）
        val versionRuntime = version.getJavaRuntime()
        val selected = versionRuntime.takeIf { it.isNotEmpty() }
            ?: AllSettings.javaRuntime.getValue()
        // 什么才算「玩家自行选择」：
        //   · 实例设置里点名了某个运行库（实例级优先于全局的「自动选择」开关）；
        //   · 或者关掉了「自动选择合适的 Java 环境」，并在全局设置里选了具体运行库。
        // 反过来，**开着自动选择开关时一律按自动走** —— 这个开关默认就是开的，
        // 而 javaRuntime 里很可能残留着上一次手选运行库的名字（勾回自动时 UI 不会清空它）。
        // 只看 selected 是否为空的话，这种残留会被误判成手动指定，自动选择等于失效。
        val manual = versionRuntime.isNotEmpty() ||
                (!AllSettings.autoPickJavaRuntime.getValue() && selected.isNotEmpty())

        // 设备上真正可用的运行库：架构必须与本机一致，且能读到版本号
        val installed = runCatching { RuntimesManager.getRuntimes() }
            .onFailure { Logger.warning(TAG, "读取 Java 运行库列表失败", it) }
            .getOrDefault(emptyList())
            .filter { it.javaVersion > 0 && it.isCompatible() }

        // 1. 玩家自己选的运行库：直接用，版本不够也不替换
        if (manual) {
            val picked = installed.firstOrNull { it.name == selected }
                ?: RuntimesManager.loadRuntime(selected)
            if (picked.javaVersion > 0 && picked.isCompatible()) {
                if (picked.javaVersion < requiredJavaVersion) {
                    Logger.warning(
                        TAG,
                        "按手动选择使用 Java ${picked.javaVersion}（$selected）启动，" +
                                "低于游戏要求的 Java $requiredJavaVersion —— 不做自动替换。"
                    )
                    appendInfo(
                        "Java: $selected（Java ${picked.javaVersion}，低于游戏要求的 Java $requiredJavaVersion，" +
                                "按手动选择启动）"
                    )
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            activity.getString(
                                R.string.game_runtime_manual_low,
                                picked.javaVersion,
                                requiredJavaVersion
                            ),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return selected
            }
        }

        // 2. 「自动选择」，或手动选的运行库已经不存在 / 架构不符：在已安装里挑「≥ 要求的最低版本」
        val best = installed
            .filter { it.javaVersion >= requiredJavaVersion }
            .minByOrNull { it.javaVersion }

        if (best != null) {
            if (best.name != selected) {
                Logger.warning(
                    TAG,
                    "本次启动使用 Java ${best.javaVersion}（${best.name}）：游戏要求 Java $requiredJavaVersion" +
                            if (!manual) {
                                "，「自动选择」按已安装运行库挑选。"
                            } else {
                                "，所选的 $selected 在本机不可用（未安装或架构不符），本次临时改用该运行库（未改动你的设置）。"
                            }
                )
                if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.game_runtime_selected_unusable, best.name),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            return best.name
        }

        // 3. 一个达标的都没有：明说缺什么，仍然按现有最高版本启动，让游戏自己报错
        val fallback = installed.maxByOrNull { it.javaVersion }
        Logger.warning(
            TAG,
            "没有任何运行库满足 Java $requiredJavaVersion 的要求" +
                    (fallback?.let { "，当前最高为 Java ${it.javaVersion}（${it.name}）" } ?: "，且没有可用的运行库")
        )
        activity.runOnUiThread {
            Toast.makeText(
                activity,
                activity.getString(R.string.game_runtime_missing_required, requiredJavaVersion),
                Toast.LENGTH_LONG
            ).show()
        }
        return fallback?.name ?: selected
    }

    /**
     * 禁用Forge的启动屏幕
     * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/a6f3fc0/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java#L372-L391)
     */
    private fun disableSplash(dir: File) {
        File(dir, "config").let { configDir ->
            if (configDir.ensureDirectorySilently()) {
                val forgeSplashFile = configDir.child("splash.properties")
                runCatching {
                    var forgeSplashContent = "enabled=true"
                    if (forgeSplashFile.exists()) {
                        forgeSplashContent = forgeSplashFile.readText()
                    }
                    if (forgeSplashContent.contains("enabled=true")) {
                        forgeSplashFile.writeText(
                            forgeSplashContent.replace("enabled=true", "enabled=false")
                        )
                    }
                }.onFailure {
                    Logger.warning(TAG, "Could not disable Forge 1.12.2 and below splash screen!", it)
                }
            } else {
                Logger.warning(TAG, "Failed to create the configuration directory")
            }
        }
    }
}

private fun checkAndUsedJSPH(envMap: MutableMap<String, String>, runtime: Runtime) {
    if (runtime.javaVersion < 11) return //onUseJSPH
    val dir = File(PathManager.DIR_NATIVE_LIB).takeIf { it.isDirectory } ?: return
    val jsphHome = if (runtime.javaVersion == 17) "libjsph17" else "libjsph21"
    dir.listFiles { _, name -> name.startsWith(jsphHome) }?.takeIf { it.isNotEmpty() }?.let {
        val libName = "${PathManager.DIR_NATIVE_LIB}/$jsphHome.so"
        envMap["JSP"] = libName
    }
}

private fun setRendererEnv(envMap: MutableMap<String, String>) {
    val renderer = Renderers.getCurrentRenderer()
    val rendererId = renderer.getRendererId()

    // SDL 环境变量
    envMap["SDL_OPENGL_LIBRARY"] = rendererId

    if (rendererId.startsWith("opengles2")) {
        envMap["LIBGL_ES"] = "2"
        envMap["LIBGL_MIPMAP"] = "3"
        envMap["LIBGL_NOERROR"] = "1"
        envMap["LIBGL_NOINTOVLHACK"] = "1"
        envMap["LIBGL_NORMALIZE"] = "1"
    }

    envMap += renderer.getRendererEnv().value

    renderer.getRendererEGL()?.let { eglName ->
        envMap["POJAVEXEC_EGL"] = eglName

        // 指定 SDL EGL
        val nativeLibPath = if (renderer is Plugin) {
            renderer.getNativeLibPath()
        } else {
            PathManager.DIR_NATIVE_LIB
        }
        envMap["SDL_EGL_LIBRARY"] = "$nativeLibPath/$eglName"
    }

    envMap["POJAV_RENDERER"] = rendererId

    if (RendererPluginManager.selectedRendererPlugin != null) return

    if (renderer != GL4ESRenderer && renderer != NGGL4ESRenderer) {
        envMap["MESA_LOADER_DRIVER_OVERRIDE"] = "zink"
        envMap["MESA_GLSL_CACHE_DIR"] = PathManager.DIR_CACHE.absolutePath
        envMap["MESA_GL_VERSION_OVERRIDE"] = "4.6"
        envMap["MESA_GLSL_VERSION_OVERRIDE"] = "460"
        envMap["force_glsl_extensions_warn"] = "true"
        envMap["allow_higher_compat_version"] = "true"
        envMap["allow_glsl_extension_directive_midshader"] = "true"
        envMap["LIB_MESA_NAME"] = getRendererLibrary() ?: "null"
    }

    if (!envMap.containsKey("LIBGL_ES")) {
        val glesMajor = getDetectedVersion()
        Logger.info(TAG, "GLES version detected: $glesMajor")

        envMap["LIBGL_ES"] = if (glesMajor < 3) {
            //fallback to 2 since it's the minimum for the entire app
            "2"
        } else if (rendererId.startsWith("opengles")) {
            rendererId.replace("opengles", "").replace("_5", "")
        } else {
            // TODO if can: other backends such as Vulkan.
            // Sure, they should provide GLES 3 support.
            "3"
        }
    }
}

private fun getRendererLibrary(): String? {
    return if (!Renderers.isCurrentRendererValid()) null
    else Renderers.getCurrentRenderer().getRendererLibrary()
}

/**
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/98947f2/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/JREUtils.java#L505-L516)
 */
private fun hasExtension(extensions: String, name: String): Boolean {
    var start = extensions.indexOf(name)
    while (start >= 0) {
        // check that we didn't find a prefix of a longer extension name
        val end = start + name.length
        if (end == extensions.length || extensions[end] == ' ') {
            return true
        }
        start = extensions.indexOf(name, end)
    }
    return false
}

private const val EGL_OPENGL_ES_BIT: Int = 0x0001
private const val EGL_OPENGL_ES2_BIT: Int = 0x0004
private const val EGL_OPENGL_ES3_BIT_KHR: Int = 0x0040

private fun getDetectedVersion(): Int {
    val egl = EGLContext.getEGL() as EGL10
    val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
    val numConfigs = IntArray(1)
    if (egl.eglInitialize(display, null)) {
        try {
            val checkES3: Boolean = hasExtension(egl.eglQueryString(display, EGL10.EGL_EXTENSIONS), "EGL_KHR_create_context")
            if (egl.eglGetConfigs(display, null, 0, numConfigs)) {
                val configs = arrayOfNulls<EGLConfig>(
                    numConfigs[0]
                )
                if (egl.eglGetConfigs(display, configs, numConfigs[0], numConfigs)) {
                    var highestEsVersion = 0
                    val value = IntArray(1)
                    for (i in 0..<numConfigs[0]) {
                        if (egl.eglGetConfigAttrib(
                                display, configs[i],
                                EGL10.EGL_RENDERABLE_TYPE, value
                            )
                        ) {
                            if (checkES3 && ((value[0] and EGL_OPENGL_ES3_BIT_KHR) == EGL_OPENGL_ES3_BIT_KHR)) {
                                if (highestEsVersion < 3) highestEsVersion = 3
                            } else if ((value[0] and EGL_OPENGL_ES2_BIT) == EGL_OPENGL_ES2_BIT) {
                                if (highestEsVersion < 2) highestEsVersion = 2
                            } else if ((value[0] and EGL_OPENGL_ES_BIT) == EGL_OPENGL_ES_BIT) {
                                if (highestEsVersion < 1) highestEsVersion = 1
                            }
                        } else {
                            Logger.warning(TAG,
                                ("Getting config attribute with "
                                        + "EGL10#eglGetConfigAttrib failed "
                                        + "(" + i + "/" + numConfigs[0] + "): "
                                        + egl.eglGetError())
                            )
                        }
                    }
                    return highestEsVersion
                } else {
                    Logger.error(TAG,
                        "Getting configs with EGL10#eglGetConfigs failed: "
                                + egl.eglGetError()
                    )
                    return -1
                }
            } else {
                Logger.error(TAG,
                    "Getting number of configs with EGL10#eglGetConfigs failed: "
                            + egl.eglGetError()
                )
                return -2
            }
        } finally {
            egl.eglTerminate(display)
        }
    } else {
        Logger.error(TAG, "Couldn't initialize EGL.")
        return -3
    }
}