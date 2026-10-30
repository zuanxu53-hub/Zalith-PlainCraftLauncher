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

package com.zalith.pcl.game.version.installed

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.zalith.pcl.R
import com.zalith.pcl.game.support.touch_controller.VibrationHandler
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.GSON
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.string.getStringNotNull
import java.io.File
import java.io.FileWriter

private const val TAG = "VersionConfig"

@Keep
class VersionConfig(
    @Transient
    private var versionPath: File
) : Parcelable {
    @SerializedName("pinned")
    var pinned: Boolean = false
        private set

    fun setPinnedAndSave(
        value: Boolean,
        applyState: (Boolean) -> Unit
    ) {
        val oldValue = pinned
        applyState(value)

        try {
            this.pinned = value
            saveWithThrowable()
        } catch (e: Exception) {
            this.pinned = oldValue
            applyState(oldValue)
            throw e
        }
    }

    @SerializedName("isolationType")
    var isolationType: SettingState = SettingState.FOLLOW_GLOBAL
        get() = getSettingStateNotNull(field)
    @SerializedName("skipGameIntegrityCheck")
    var skipGameIntegrityCheck: SettingState = SettingState.FOLLOW_GLOBAL
        get() = getSettingStateNotNull(field)
    @SerializedName("javaRuntime")
    var javaRuntime: String = ""
        get() = getStringNotNull(field)
    @SerializedName("jvmArgs")
    var jvmArgs: String = ""
        get() = getStringNotNull(field)
    @SerializedName("gameArgs")
    var gameArgs: String = ""
        get() = getStringNotNull(field)
    @SerializedName("renderer")
    var renderer: String = ""
        get() = getStringNotNull(field)
    @SerializedName("driver")
    var driver: String = ""
        get() = getStringNotNull(field)
    @SerializedName("graphicsApi")
    var graphicsApi: GraphicsApi? = null
    @SerializedName("control")
    var control: String = ""
        get() = getStringNotNull(field)
    @SerializedName("customPath")
    var customPath: String = ""
        get() = getStringNotNull(field)
    @SerializedName("customInfo")
    var customInfo: String = ""
        get() = getStringNotNull(field)
    @SerializedName("versionSummary")
    var versionSummary: String = ""
        get() = getStringNotNull(field)
    @SerializedName("serverIp")
    var serverIp: String = ""
        get() = getStringNotNull(field)

    /**
     * 该版本的登录方式（对应 PCL 的 `版本设置 → 服务器 → 登录方式`）。
     *
     * 存成可空是为了容忍旧配置文件：Gson 反序列化时可能把缺失的字段写成 null，
     * 取值一律用 [VersionLoginType.from] 兜底成「正版登录或离线登录」。
     */
    @SerializedName("loginType")
    var loginType: VersionLoginType? = null
    /** 第三方登录：Authlib 用认证服务器地址，统一通行证用服务器 ID。 */
    @SerializedName("loginAuthServer")
    var loginAuthServer: String = ""
        get() = getStringNotNull(field)
    /** 第三方登录的注册链接（只用于展示 / 一键打开，不参与启动）。 */
    @SerializedName("loginRegisterUrl")
    var loginRegisterUrl: String = ""
        get() = getStringNotNull(field)
    /** 登录页上显示的服务器名称。 */
    @SerializedName("loginServerName")
    var loginServerName: String = ""
        get() = getStringNotNull(field)
    @SerializedName("ramAllocation")
    var ramAllocation: Int = -1
    @SerializedName("touchVibrateDuration")
    var touchVibrateDuration: Int = 100
    @SerializedName("touchVibrateKind")
    var touchVibrateKind: VibrationHandler.VibrateKind? = null

    constructor(
        filePath: File,
        isolationType: SettingState = SettingState.FOLLOW_GLOBAL,
        skipGameIntegrityCheck: SettingState = SettingState.FOLLOW_GLOBAL,
        javaRuntime: String = "",
        jvmArgs: String = "",
        gameArgs: String = "",
        renderer: String = "",
        driver: String = "",
        graphicsApi: GraphicsApi? = null,
        control: String = "",
        customPath: String = "",
        customInfo: String = "",
        versionSummary: String = "",
        serverIp: String = "",
        loginType: VersionLoginType? = null,
        loginAuthServer: String = "",
        loginRegisterUrl: String = "",
        loginServerName: String = "",
        ramAllocation: Int = -1,
        touchVibrateDuration: Int = 100,
        touchVibrateKind: VibrationHandler.VibrateKind? = null,
    ) : this(filePath) {
        this.isolationType = isolationType
        this.skipGameIntegrityCheck = skipGameIntegrityCheck
        this.javaRuntime = javaRuntime
        this.jvmArgs = jvmArgs
        this.gameArgs = gameArgs
        this.renderer = renderer
        this.driver = driver
        this.graphicsApi = graphicsApi
        this.control = control
        this.customPath = customPath
        this.customInfo = customInfo
        this.versionSummary = versionSummary
        this.serverIp = serverIp
        this.loginType = loginType
        this.loginAuthServer = loginAuthServer
        this.loginRegisterUrl = loginRegisterUrl
        this.loginServerName = loginServerName
        this.ramAllocation = ramAllocation
        this.touchVibrateDuration = touchVibrateDuration
        this.touchVibrateKind = touchVibrateKind
    }

    fun copy(): VersionConfig = VersionConfig(
        versionPath,
        getSettingStateNotNull(isolationType),
        getSettingStateNotNull(skipGameIntegrityCheck),
        getStringNotNull(javaRuntime),
        getStringNotNull(jvmArgs),
        getStringNotNull(gameArgs),
        getStringNotNull(renderer),
        getStringNotNull(driver),
        graphicsApi,
        getStringNotNull(control),
        getStringNotNull(customPath),
        getStringNotNull(customInfo),
        getStringNotNull(versionSummary),
        getStringNotNull(serverIp),
        loginType,
        getStringNotNull(loginAuthServer),
        getStringNotNull(loginRegisterUrl),
        getStringNotNull(loginServerName),
        ramAllocation,
        touchVibrateDuration,
        touchVibrateKind,
    )

    fun save() {
        runCatching {
            saveWithThrowable()
        }.onFailure { e ->
            Logger.error(TAG, "An exception occurred while saving the version configuration.", e)
        }
    }

    @Throws(Throwable::class)
    fun saveWithThrowable() {
        val zalithVersionPath = getZalithVersionPath(versionPath)
        val configFile = File(zalithVersionPath, "version.config")
        if (!zalithVersionPath.exists()) zalithVersionPath.mkdirs()

        FileWriter(configFile, false).use {
            val json = GSON.toJson(this)
            it.write(json)
        }
        Logger.info(TAG, "Saved version configuration: $this")
    }

    fun getVersionPath() = versionPath

    fun setVersionPath(versionPath: File) {
        this.versionPath = versionPath
    }

    fun isIsolation(): Boolean = isolationType.toBoolean(AllSettings.versionIsolation.getValue())

    fun skipGameIntegrityCheck(): Boolean = skipGameIntegrityCheck.toBoolean(AllSettings.skipGameIntegrityCheck.getValue())

    private fun SettingState.toBoolean(global: Boolean) = when(getSettingStateNotNull(this)) {
        SettingState.FOLLOW_GLOBAL -> global
        SettingState.ENABLE -> true
        SettingState.DISABLE -> false
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.run {
            writeString(versionPath.absolutePath)
            writeInt(getSettingStateNotNull(isolationType).ordinal)
            writeInt(getSettingStateNotNull(skipGameIntegrityCheck).ordinal)
            writeString(getStringNotNull(javaRuntime))
            writeString(getStringNotNull(jvmArgs))
            writeString(getStringNotNull(gameArgs))
            writeString(getStringNotNull(renderer))
            writeString(getStringNotNull(driver))
            writeInt(graphicsApi?.ordinal ?: -1)
            writeString(getStringNotNull(control))
            writeString(getStringNotNull(customPath))
            writeString(getStringNotNull(customInfo))
            writeString(getStringNotNull(versionSummary))
            writeString(getStringNotNull(serverIp))
            writeInt(loginType?.ordinal ?: 0)
            writeString(getStringNotNull(loginAuthServer))
            writeString(getStringNotNull(loginRegisterUrl))
            writeString(getStringNotNull(loginServerName))
            writeInt(ramAllocation)
            writeInt(touchVibrateDuration)
            writeInt(touchVibrateKind?.ordinal ?: -1)
        }
    }

    companion object CREATOR : Parcelable.Creator<VersionConfig> {
        override fun createFromParcel(parcel: Parcel): VersionConfig {
            val versionPath = File(parcel.readString().orEmpty())
            val isolationType = SettingState.entries.getOrNull(parcel.readInt()) ?: SettingState.FOLLOW_GLOBAL
            val skipGameIntegrityCheck = SettingState.entries.getOrNull(parcel.readInt()) ?: SettingState.FOLLOW_GLOBAL
            val javaRuntime = parcel.readString().orEmpty()
            val jvmArgs = parcel.readString().orEmpty()
            val gameArgs = parcel.readString().orEmpty()
            val renderer = parcel.readString().orEmpty()
            val driver = parcel.readString().orEmpty()
            val graphicsApi = GraphicsApi.entries.getOrNull(parcel.readInt())
            val control = parcel.readString().orEmpty()
            val customPath = parcel.readString().orEmpty()
            val customInfo = parcel.readString().orEmpty()
            val versionSummary = parcel.readString().orEmpty()
            val serverIp = parcel.readString().orEmpty()
            val loginType = VersionLoginType.entries.getOrNull(parcel.readInt()) ?: VersionLoginType.MICROSOFT_OR_OFFLINE
            val loginAuthServer = parcel.readString().orEmpty()
            val loginRegisterUrl = parcel.readString().orEmpty()
            val loginServerName = parcel.readString().orEmpty()
            val ramAllocation = parcel.readInt()
            val touchVibrateDuration = parcel.readInt()
            val touchVibrateKind = VibrationHandler.VibrateKind.entries.getOrNull(parcel.readInt())

            return VersionConfig(
                versionPath,
                isolationType,
                skipGameIntegrityCheck,
                javaRuntime,
                jvmArgs,
                gameArgs,
                renderer,
                driver,
                graphicsApi,
                control,
                customPath,
                customInfo,
                versionSummary,
                serverIp,
                loginType,
                loginAuthServer,
                loginRegisterUrl,
                loginServerName,
                ramAllocation,
                touchVibrateDuration,
                touchVibrateKind,
            )
        }

        override fun newArray(size: Int): Array<VersionConfig?> {
            return arrayOfNulls(size)
        }

        fun parseConfig(versionPath: File): VersionConfig {
            val configFile = File(getZalithVersionPath(versionPath), "version.config")

            return runCatching getConfig@{
                when {
                    configFile.exists() -> {
                        //读取此文件的内容，并解析为VersionConfig
                        val config = GSON.fromJson(configFile.readText(), VersionConfig::class.java)
                        config.setVersionPath(versionPath)
                        config
                    }
                    else -> createNewConfig(versionPath)
                }
            }.onFailure {  e ->
                Logger.error(TAG, "An exception occurred while parsing the version configuration.", e)
            }.getOrElse {
                createNewConfig(versionPath)
            }
        }

        private fun createNewConfig(versionPath: File): VersionConfig {
            val config = VersionConfig(versionPath)
            return config.apply { save() }
        }

        fun createIsolation(versionPath: File): VersionConfig {
            val config = VersionConfig(versionPath)
            config.isolationType = SettingState.ENABLE
            return config
        }
    }
}

/**
 * 版本级「登录方式」（对应 PCL 的 `VersionSetup → 服务器 → 登录方式`）。
 *
 * 只决定**启动时用哪一类账户**，账号本身仍由 `AccountsManager` 管理：
 *   · [MICROSOFT_OR_OFFLINE] 跟随当前账户（PCL 的默认项）
 *   · [MICROSOFT_ONLY] / [OFFLINE_ONLY] 强制用正版 / 离线账户
 *   · [UNIFIED_PASSPORT] 统一通行证（Nide8），[loginAuthServer] 存服务器 ID
 *   · [AUTHLIB] Authlib Injector / LittleSkin，[loginAuthServer] 存认证服务器地址
 */
enum class VersionLoginType(val tag: String) {
    @SerializedName("ONLINE_OR_OFFLINE")
    MICROSOFT_OR_OFFLINE("OnlineOrOffline"),

    @SerializedName("ONLINE")
    MICROSOFT_ONLY("Online"),

    @SerializedName("OFFLINE")
    OFFLINE_ONLY("Offline"),

    @SerializedName("UNIFIED_PASSPORT")
    UNIFIED_PASSPORT("Nide8"),

    @SerializedName("AUTHLIB")
    AUTHLIB("Authlib");

    companion object {
        /** 旧配置里没有这一项（反序列化成 null）时按 PCL 默认值处理。 */
        fun from(type: VersionLoginType?): VersionLoginType = type ?: MICROSOFT_OR_OFFLINE
    }
}

enum class SettingState(val textRes: Int) {
    @SerializedName("FOLLOW_GLOBAL")
    FOLLOW_GLOBAL(R.string.generic_follow_global),
    @SerializedName("ENABLE")
    ENABLE(R.string.generic_enable),
    @SerializedName("DISABLE")
    DISABLE(R.string.generic_disable)
}

enum class GraphicsApi(
    val displayName: String,
    val option: String
) {
    /** 默认使用游戏设定 */
    @SerializedName("DEFAULT")
    DEFAULT("", "\"default\""),
    /** 强制切换到OpenGL，覆盖游戏原有设定 */
    @SerializedName("OPENGL")
    OPENGL("OpenGL", "\"opengl\""),
    /** 默认切换到OpenGL，如果游戏有设定过则不覆盖 */
    @SerializedName("DEFAULT_OPENGL")
    DEFAULT_OPENGL(OPENGL.displayName, OPENGL.option),
    /** 强制切换到Vulkan，覆盖游戏原有设定 */
    @SerializedName("VULKAN")
    VULKAN("Vulkan", "\"vulkan\"")
}

/**
 * 界面上的显示名。
 *
 * `displayName` 不能直接拿来当标签：`DEFAULT_OPENGL` 的 displayName 就是 `OPENGL` 的
 * 「OpenGL」，两者在设置里并排出现时就是两个一模一样的「OpenGL」，用户根本没法选
 * （截图里那两个 OpenGL 就是这么来的）。而 `DEFAULT` 的 displayName 是空串，
 * 实例设置页自己兜了一句「默认（游戏设定）」，全局设置页又写成「跟随游戏设定」，
 * 同一个东西两套说法。
 *
 * 统一从这里取标签，两个页面就不会再各写一套、各跑偏一套。
 */
val GraphicsApi.uiLabel: String
    get() = when (this) {
        GraphicsApi.DEFAULT -> "跟随游戏设定"
        // 与 DEFAULT 行为一致（只在 options.txt 没写过、或残留 vulkan 时才落 opengl），
        // 所以界面上不单列，存量配置沿用这个标签
        GraphicsApi.DEFAULT_OPENGL -> "跟随游戏设定"
        GraphicsApi.OPENGL -> "强制 OpenGL"
        GraphicsApi.VULKAN -> "Vulkan"
    }

/**
 * 界面上真正可选的那几项。
 *
 * `DEFAULT_OPENGL` 不列出来：它和 `DEFAULT` 在 `GameHandler` 里走的是同一个分支、
 * 写出 `options.txt` 的值也一样，摆两条只会让人以为有什么不同。
 *
 * **枚举本身不能删值** —— `VersionConfig.writeToParcel` 写的是 `ordinal`，
 * 删一项会让所有存量配置的图形 API 整体错位。
 */
val GRAPHICS_API_CHOICES: List<GraphicsApi> = listOf(
    GraphicsApi.DEFAULT,
    GraphicsApi.OPENGL,
    GraphicsApi.VULKAN,
)

/** 把存量配置里的 `DEFAULT_OPENGL` 归一到界面上那一项，避免下拉选不中。 */
val GraphicsApi.forUi: GraphicsApi
    get() = if (this == GraphicsApi.DEFAULT_OPENGL) GraphicsApi.DEFAULT else this

private fun getSettingStateNotNull(type: SettingState?) = type ?: SettingState.FOLLOW_GLOBAL
