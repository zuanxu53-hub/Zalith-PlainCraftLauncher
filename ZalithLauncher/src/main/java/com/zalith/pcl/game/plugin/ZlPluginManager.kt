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

package com.zalith.pcl.game.plugin

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import com.zalith.pcl.game.path.getGameHome
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.utils.logging.Logger
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "ZlPlugin"

/** 插件 APK 里约定的资源目录前缀（`assets/zplugin/`）。 */
private const val ASSET_ROOT = "assets/zplugin/"
private const val MANIFEST_ENTRY = ASSET_ROOT + "plugin.json"
private const val FILES_PREFIX = ASSET_ROOT + "files/"

/** 目标根：游戏目录（版本、模组、资源包那一套）与启动器数据目录（背景、音乐等）。 */
const val TARGET_MINECRAFT = "minecraft"
const val TARGET_LAUNCHER = "launcher"

/** 皮肤 APK 里放参数的条目名；安装时另存为插件目录下的 `ui.json`。 */
private const val UI_SPEC_ENTRY = ASSET_ROOT + "ui.json"

/** 插件投递目录名（`<插件根>/incoming`）：APK 丢进来，装完自动清掉。 */
private const val INCOMING_DIR = "incoming"

/** 自动识别的「处理过的 APK」记录，避免同一个文件反复安装。 */
private const val SCAN_RECORD = "scan.json"

/**
 * 绝对不允许插件碰的路径片段。
 *
 * 插件是「一堆文件」而不是代码，启动器也从不加载插件 APK 里的 dex，
 * 所以它理论上读不到任何东西；这里再挡一道，是为了防止插件把**覆盖写入**
 * 打到账号数据库 / 凭据文件上 —— 那等于间接篡改正版登录的 id 与令牌。
 *
 * 判据是路径的每一段（小写后）是否包含这些词：
 * - `databases` / `shared_prefs`：安卓存账号与令牌的地方
 * - `accounts` / `account` / `playerdata`：账号数据
 * - `token` / `authlib` / `clientid`：登录凭据相关
 * - `launcher_profiles.json`：原版启动器档案，里面带 accessToken
 */
private val DENIED_SEGMENTS = listOf(
    "databases", "shared_prefs",
    "account", "accounts", "playerdata",
    "token", "authlib", "clientid", "access_token", "launcher_profiles.json",
)

/**
 * 一个已安装的插件。
 *
 * 目录结构（`<外部files>/plugins/<id>/`）：
 * ```
 * plugin.apk     安装时原样保留，方便重新解包/排查
 * meta.json      清单缓存（名字、版本、目标、开关）
 * files/         插件带来的文件树，路径与目标目录同构
 * backup/        覆盖前备份的原文件（停用时还原）
 * applied.json   本次已应用到目标目录的相对路径列表
 * ```
 */
data class ZlPlugin(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    /** [TARGET_MINECRAFT] 或 [TARGET_LAUNCHER] */
    val target: String,
    val fileCount: Int,
    val enabled: Boolean,
    val dir: File,
    /**
     * 皮肤标识（`plugin.json` 的 `ui`，随便写什么名字都行）。
     *
     * 带这个字段的插件是「界面皮肤」：可以不带任何要覆盖的文件，作用是把
     * 插件目录下的 `ui.json` 交给启动器渲染。渲染方式由 `ui.json` 里的 `renderer`
     * 决定（`glass` / `flat` …），启动器不认识的值会退回默认。
     */
    val ui: String = "",
    /** 插件目录下有没有 `ui.json`。 */
    val hasUiSpec: Boolean = false,
) {
    /** 是否界面皮肤插件。 */
    val isTheme: Boolean get() = ui.isNotBlank() || hasUiSpec
}

/** `plugin.json` 的结构；字段都给了默认值，缺省也能装。 */
private data class PluginManifest(
    @SerializedName("name") val name: String = "",
    @SerializedName("version") val version: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("target") val target: String = TARGET_MINECRAFT,
    /** 安装后是否立即启用；默认启用，和「资源包装上就能用」一致。 */
    @SerializedName("enabled") val enabled: Boolean = true,
    /** 界面皮肤标识；给了就说明这是皮肤插件，可以不带任何文件。 */
    @SerializedName("ui") val ui: String = "",
)

/** `meta.json`：清单 + 开关状态。 */
private data class PluginMeta(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val target: String = TARGET_MINECRAFT,
    val fileCount: Int = 0,
    val enabled: Boolean = false,
    val ui: String = "",
)

/** `applied.json`：启用时记下动了哪些文件，停用时才好精确还原。 */
private data class AppliedRecord(
    /** 相对目标根的路径（`/` 分隔）。 */
    val path: String = "",
    /** 覆盖前目标里已经有这个文件（true 表示停用时要还原，false 表示要删掉）。 */
    val existed: Boolean = false,
)

/**
 * 自动识别的去重记录。
 *
 * 用「路径 + 大小 + 修改时间」当指纹：文件名不变但内容换了（升级），指纹会变，就会被重新安装。
 */
private data class ScanRecord(
    val path: String = "",
    val size: Long = 0L,
    val modified: Long = 0L,
)

/** 插件提供的界面皮肤：id + 名字 + `ui.json` 原文。 */
data class ZlPluginTheme(
    val id: String,
    val name: String,
    val json: String,
)

/** 一次自动识别的结果。 */
data class PluginScanResult(
    /** 本次新装上（或升级）的插件。 */
    val installed: List<ZlPlugin> = emptyList(),
    /** 认出来是插件但没装成的，元素形如「xxx.apk：原因」。 */
    val rejected: List<String> = emptyList(),
)

/**
 * 资源包式插件管理器。
 *
 * 玩法与 MC 的资源包一样：插件 APK 里放一棵**与目标目录同构的文件树**，
 * 启用后这些文件被覆盖到目标目录，停用则还原成插件来之前的样子。
 * 例如想改游戏目录下 `config/1/x.txt`，就在插件里放
 * `assets/zplugin/files/config/1/x.txt`。
 *
 * 插件必须是**独立 APK**（能被 PackageManager 读出包名，纯压缩包会被拒绝）：
 * 启动器只读它 assets 里的文件，从不加载它的 dex、不反射、不执行任何东西。因此它没有能力读取正版登录信息；写入侧另有
 * [DENIED_SEGMENTS] 黑名单兜底。
 */
object ZlPluginManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** 插件根目录。 */
    fun root(context: Context): File = File(PathManager.DIR_FILES_EXTERNAL, "plugins")

    /** 目标根目录。 */
    fun targetRoot(target: String): File = when (target) {
        TARGET_LAUNCHER -> PathManager.DIR_FILES_EXTERNAL
        else -> File(getGameHome())
    }

    fun targetLabel(target: String): String = when (target) {
        TARGET_LAUNCHER -> "启动器目录"
        else -> "游戏目录"
    }

    /**
     * 当前生效的界面皮肤（第一个启用中的皮肤插件）。
     *
     * 返回的是**插件给的原文**，怎么解释交给界面层（`theme/PclUiTheme.kt`）——
     * 这样启动器不必认识每一种皮肤，插件也不必为了加一种风格重新编译。
     */
    fun activeTheme(context: Context): ZlPluginTheme? {
        val plugin = list(context).firstOrNull { it.enabled && it.isTheme } ?: return null
        val spec = File(plugin.dir, "ui.json")
        val json = runCatching { if (spec.isFile) spec.readText() else null }.getOrNull()
        return ZlPluginTheme(
            id = plugin.ui.ifBlank { plugin.id },
            name = plugin.name,
            // 只写了 plugin.json 里的 ui、没带 ui.json 时，给一份空参数 —— 界面用默认值
            json = json?.ifBlank { null } ?: "{}",
        )
    }

    /** 列出已安装插件（按名字排序）。 */
    fun list(context: Context): List<ZlPlugin> {
        val root = root(context)
        if (!root.isDirectory) return emptyList()
        return root.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readMeta(it) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    /** 插件投递目录：APK 丢进来，进入插件页被自动装上后原件就删掉。 */
    fun incomingDir(context: Context): File =
        File(root(context), INCOMING_DIR).apply { mkdirs() }

    /**
     * 自动识别插件。
     *
     * 依次扫这三个位置里的 `.apk`（都只扫一层，不递归）：
     * 1. `<插件根>/incoming/` —— 投递目录，装上之后原件删掉
     * 2. `<插件根>/` —— 散装 APK，装完留在原地
     * 3. 系统「下载」文件夹 —— 用户下载插件后直接回来就能看到
     *
     * 判断标准只有一个：APK 里有没有 `assets/zplugin/plugin.json`。
     * 普通应用 APK、随便改后缀的压缩包都不算插件，会被静默跳过（不骚扰用户）。
     * 处理过的文件记在 `scan.json` 里（路径 + 大小 + 修改时间），不会反复安装。
     *
     * 必须在 IO 线程调用。
     */
    fun scan(context: Context): PluginScanResult {
        val root = root(context).apply { mkdirs() }
        val incoming = incomingDir(context)
        val sources = listOfNotNull(
            incoming,
            root,
            downloadDir(),
        )

        val records = readScanRecords(root).toMutableList()
        val installed = mutableListOf<ZlPlugin>()
        val rejected = mutableListOf<String>()

        sources.forEachIndexed { sourceIndex, dir ->
            if (!dir.isDirectory) return@forEachIndexed
            val apks = dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()

            apks.forEach { apk ->
                val size = apk.length()
                val modified = apk.lastModified()
                if (size <= 0L) return@forEach
                if (records.any { it.path == apk.absolutePath && it.size == size && it.modified == modified }) {
                    return@forEach
                }
                // 不是插件就当作普通 APK 忽略；顺手记一笔，免得每次进页面都把同一个 APK 再拆一遍
                if (!looksLikePlugin(apk)) {
                    records += ScanRecord(apk.absolutePath, size, modified)
                    return@forEach
                }

                runCatching { install(context, apk) }
                    .onSuccess { plugin ->
                        installed += plugin
                        records += ScanRecord(apk.absolutePath, size, modified)
                        Logger.info(TAG, "自动识别到插件 ${plugin.name}（${apk.name}）")
                        // 投递目录里的原件装完即删，避免下次重复处理
                        if (sourceIndex == 0) runCatching { apk.delete() }
                    }
                    .onFailure { e ->
                        // 认出来了但装不上（清单坏了、路径越界…）：记录指纹，避免每次进页面都报同一件事
                        records += ScanRecord(apk.absolutePath, size, modified)
                        rejected += "${apk.name}：${e.message ?: "安装失败"}"
                        Logger.warning(TAG, "自动识别插件失败：${apk.name}", e)
                    }
            }
        }

        writeScanRecords(root, records.takeLast(300))
        return PluginScanResult(installed = installed, rejected = rejected)
    }

    /** 系统「下载」文件夹；拿不到（或没权限）就返回 null。 */
    private fun downloadDir(): File? = runCatching {
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .takeIf { it.isDirectory }
    }.getOrNull()

    /** APK 里带插件清单才算插件；这一步只是「认」，装不装得成由 [install] 判断。 */
    private fun looksLikePlugin(apk: File): Boolean = runCatching {
        ZipFile(apk).use { zip -> zip.getEntry(MANIFEST_ENTRY) != null }
    }.getOrDefault(false)

    private fun readScanRecords(root: File): List<ScanRecord> {
        val file = File(root, SCAN_RECORD)
        if (!file.isFile) return emptyList()
        return runCatching {
            gson.fromJson(file.readText(), Array<ScanRecord>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeScanRecords(root: File, records: List<ScanRecord>) {
        runCatching {
            File(root, SCAN_RECORD).writeText(gson.toJson(records))
        }.onFailure { Logger.warning(TAG, "写入自动识别记录失败", it) }
    }

    /**
     * 安装（或升级）一个插件 APK（来自文件选择器 / 分享意图的 `content://`）。
     *
     * @throws IllegalStateException 格式不对、路径越界、命中黑名单时抛出，[message] 直接给用户看
     */
    fun install(context: Context, uri: Uri): ZlPlugin {
        val root = root(context).apply { mkdirs() }
        val tempApk = File(root, "install.tmp").apply { runCatching { delete() } }

        // ① 先把 APK 落到自己的目录：content:// 的授权是临时的，后面还要反复读
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("读不到这个文件，换一个再试。")
        input.use { ins -> tempApk.outputStream().use { out -> ins.copyTo(out) } }

        return installTemp(context, tempApk)
    }

    /**
     * 安装（或升级）一个插件 APK（来自自动识别扫到的普通文件）。
     *
     * 只是把文件复制成临时副本，后续流程与 [install] 完全一致。
     */
    fun install(context: Context, apk: File): ZlPlugin {
        val root = root(context).apply { mkdirs() }
        val tempApk = File(root, "install.tmp").apply { runCatching { delete() } }
        apk.copyTo(tempApk, overwrite = true)
        return installTemp(context, tempApk)
    }

    /** 真正的安装流程；[tempApk] 是本管理器目录下的临时副本，结束时会被移走或删掉。 */
    private fun installTemp(context: Context, tempApk: File): ZlPlugin {
        val root = root(context).apply { mkdirs() }

        val archive = runCatching { ZipFile(tempApk) }.getOrElse {
            tempApk.delete()
            throw IllegalStateException("这个文件不是有效的 APK。")
        }

        // 解包结果要带出 use 作用域，供下面构建返回值
        var installedMeta: PluginMeta? = null
        var installedDir: File? = null
        var autoEnable = true

        // ⓪ 必须是真正的 APK：能读出包名才认。纯 zip / 改后缀的文件在这里就被挡住，
        //    这样「插件就是一个独立 APK」这条规则由加载器自己保证，而不是靠用户自觉。
        val pkgInfo = runCatching {
            context.packageManager.getPackageArchiveInfo(tempApk.absolutePath, 0)
        }.getOrNull() ?: run {
            tempApk.delete()
            throw IllegalStateException(
                "这个文件不是有效的 APK（读不到包信息）。\n" +
                        "插件必须是独立的 .apk 文件，普通压缩包不行。"
            )
        }

        archive.use { zip ->
            val manifestEntry = findEntry(zip, MANIFEST_ENTRY)
                ?: throw IllegalStateException(
                    "这个 APK 里没有插件清单（$MANIFEST_ENTRY）。\n" +
                            "插件包需要在 assets/zplugin/ 下放 plugin.json 与 files/ 目录。"
                )

            val manifest = runCatching {
                gson.fromJson(
                    zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() },
                    PluginManifest::class.java,
                )
            }.getOrNull() ?: throw IllegalStateException("plugin.json 解析失败，检查一下 JSON 格式。")

            val target = manifest.target.ifBlank { TARGET_MINECRAFT }
            if (target != TARGET_MINECRAFT && target != TARGET_LAUNCHER) {
                throw IllegalStateException("plugin.json 里的 target 只能是 minecraft 或 launcher，现在是「$target」。")
            }

            // ② 先把整棵文件树过一遍安全校验，任何一条不合规就整体拒绝
            val entries = zip.entries().toList().filter {
                !it.isDirectory && it.normalizedEntryName().startsWith(FILES_PREFIX)
            }
            // 皮肤插件只负责「打开一套界面」，本来就没有要覆盖的文件，所以放行
            val hasSpec = findEntry(zip, UI_SPEC_ENTRY) != null
            if (entries.isEmpty() && manifest.ui.isBlank() && !hasSpec) {
                throw IllegalStateException("这个插件里没有文件（${FILES_PREFIX} 下是空的）。")
            }
            entries.forEach { checkRelative(it.normalizedEntryName().removePrefix(FILES_PREFIX)) }

            // ③ id 用 APK 的包名：重装同一个插件是升级，不会变成两份
            val pkgName = pkgInfo.packageName
            val id = sanitizeId(pkgName)
            val dir = File(root, id).apply { mkdirs() }

            // 覆盖安装前先把旧版本停用，避免「文件已经换成新的、备份却还是旧的」
            readMeta(dir)?.let { old ->
                if (old.enabled) runCatching { revert(old) }
            }

            tempApk.copyTo(File(dir, "plugin.apk"), overwrite = true)
            tempApk.delete()

            // ④ 解包文件树
            val filesDir = File(dir, "files").apply {
                deleteRecursively()
                mkdirs()
            }
            entries.forEach { entry ->
                val rel = entry.normalizedEntryName().removePrefix(FILES_PREFIX)
                val out = File(filesDir, rel)
                out.parentFile?.mkdirs()
                zip.getInputStream(entry).use { ins ->
                    out.outputStream().use { os -> ins.copyTo(os) }
                }
            }

            val meta = PluginMeta(
                id = id,
                name = manifest.name.ifBlank { pkgName ?: id },
                version = manifest.version,
                author = manifest.author,
                description = manifest.description,
                target = target,
                fileCount = entries.size,
                enabled = false, // 启用由下面统一处理
                ui = manifest.ui.trim().lowercase(),
            )

            // 皮肤的渲染参数：原样存一份到插件目录，界面那边直接读这个文件
            findEntry(zip, UI_SPEC_ENTRY)?.let { specEntry ->
                runCatching {
                    zip.getInputStream(specEntry).bufferedReader().use { reader ->
                        File(dir, "ui.json").writeText(reader.readText())
                    }
                }.onFailure { Logger.warning(TAG, "保存 ui.json 失败", it) }
            }
            writeMeta(dir, meta)
            installedMeta = meta
            installedDir = dir
            autoEnable = manifest.enabled
            Logger.info(TAG, "已安装插件 ${meta.name}（$id），${meta.fileCount} 个文件，目标 $target")
        }

        val meta = installedMeta ?: throw IllegalStateException("安装失败，插件清单没有生成。")
        val dir = installedDir ?: throw IllegalStateException("安装失败，插件目录没有创建。")
        var plugin = ZlPlugin(
            id = meta.id,
            name = meta.name,
            version = meta.version,
            author = meta.author,
            description = meta.description,
            target = meta.target,
            fileCount = meta.fileCount,
            enabled = false,
            dir = dir,
            ui = meta.ui,
            hasUiSpec = File(dir, "ui.json").isFile,
        )

        // ⑤ 默认启用：与「资源包放进去就生效」一致；启用失败不影响安装本身
        if (autoEnable) {
            runCatching { setEnabled(context, plugin, true) }
                .onFailure { Logger.warning(TAG, "插件安装后自动启用失败", it) }
            plugin = plugin.copy(enabled = true)
        }
        return plugin
    }

    /** 条目名统一成分隔符为正斜杠的形式（Windows 上 aapt2 打的包可能是反斜杠）。 */
    private fun java.util.zip.ZipEntry.normalizedEntryName(): String = name.replace('\\', '/')

    /** 按约定路径找条目，正反斜杠都认。 */
    private fun findEntry(zip: ZipFile, path: String): java.util.zip.ZipEntry? {
        zip.getEntry(path)?.let { return it }
        zip.getEntry(path.replace('/', '\\'))?.let { return it }
        return zip.entries().asSequence().firstOrNull { it.normalizedEntryName() == path }
    }

    private fun readMeta(dir: File): ZlPlugin? {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.isFile) return null
        val meta = runCatching {
            gson.fromJson(metaFile.readText(), PluginMeta::class.java)
        }.getOrNull() ?: return null
        val filesDir = File(dir, "files")
        return ZlPlugin(
            id = meta.id.ifBlank { dir.name },
            name = meta.name.ifBlank { dir.name },
            version = meta.version,
            author = meta.author,
            description = meta.description,
            target = meta.target.ifBlank { TARGET_MINECRAFT },
            fileCount = meta.fileCount.takeIf { it > 0 }
                ?: filesDir.walkTopDown().count { it.isFile }.coerceAtLeast(0),
            enabled = meta.enabled,
            dir = dir,
            ui = meta.ui,
            hasUiSpec = File(dir, "ui.json").isFile,
        )
    }

    private fun writeMeta(dir: File, meta: PluginMeta) {
        File(dir, "meta.json").writeText(gson.toJson(meta))
    }

    private fun sanitizeId(raw: String): String =
        raw.lowercase().map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
            .ifBlank { "plugin" }
            .take(64)

    /**
     * 相对路径校验：不许绝对路径、不许 `..`、不许命中凭据黑名单。
     *
     * 这一步不能只做一次：`File(root, "../x")` 在 Java 里是会真的跳出 root 的。
     */
    private fun checkRelative(rel: String) {
        val normalized = rel.replace('\\', '/')
        if (normalized.isBlank()) throw IllegalStateException("插件里有空路径。")
        if (normalized.startsWith("/")) throw IllegalStateException("插件里出现了绝对路径：$normalized")
        val segments = normalized.split('/')
        if (segments.any { it == ".." || it == "." }) {
            throw IllegalStateException("插件里出现了越界路径：$normalized")
        }
        val denied = segments.firstOrNull { seg ->
            val lower = seg.lowercase()
            DENIED_SEGMENTS.any { lower.contains(it) }
        }
        if (denied != null) {
            throw IllegalStateException(
                "插件想写入「$normalized」，这个路径涉及账号或登录凭据，已拒绝安装。\n" +
                        "插件不允许读写正版登录相关的数据。"
            )
        }
    }

    /** 解析成目标目录里的真实文件，并再次确认没有跳出根目录。 */
    private fun resolveIn(root: File, rel: String): File {
        checkRelative(rel)
        val file = File(root, rel)
        val rootPath = root.canonicalPath + File.separator
        if (!file.canonicalPath.startsWith(rootPath)) {
            throw IllegalStateException("插件路径越界：$rel")
        }
        return file
    }

    /**
     * 启用 / 停用插件。
     *
     * 启用 = 把 `files/` 覆盖到目标目录（覆盖前备份原文件）；
     * 停用 = 按备份还原，插件新加的文件直接删掉。
     */
    fun setEnabled(context: Context, plugin: ZlPlugin, enabled: Boolean) {
        val meta = readMeta(plugin.dir) ?: plugin
        if (enabled == meta.enabled) return

        if (enabled) apply(plugin) else revert(plugin)

        val metaFile = File(plugin.dir, "meta.json")
        val json = runCatching { gson.fromJson(metaFile.readText(), PluginMeta::class.java) }.getOrNull()
        if (json != null) {
            writeMeta(plugin.dir, json.copy(enabled = enabled))
        }
        Logger.info(TAG, "插件 ${plugin.name} 已${if (enabled) "启用" else "停用"}")
    }

    private fun apply(plugin: ZlPlugin) {
        val root = targetRoot(plugin.target)
        if (!root.isDirectory) root.mkdirs()
        val filesDir = File(plugin.dir, "files")
        val backupDir = File(plugin.dir, "backup").apply {
            deleteRecursively()
            mkdirs()
        }

        val applied = mutableListOf<AppliedRecord>()
        if (!filesDir.isDirectory) {
            // 皮肤插件没有 files/，记一条空清单就够
            File(plugin.dir, "applied.json").writeText(gson.toJson(applied))
            return
        }
        filesDir.walkTopDown().filter { it.isFile }.forEach { src ->
            val rel = src.relativeTo(filesDir).path.replace('\\', '/')
            val dest = resolveIn(root, rel)
            val existed = dest.isFile
            if (existed) {
                val bak = File(backupDir, rel)
                bak.parentFile?.mkdirs()
                dest.copyTo(bak, overwrite = true)
            }
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            applied += AppliedRecord(path = rel, existed = existed)
        }
        File(plugin.dir, "applied.json").writeText(gson.toJson(applied))
        Logger.info(TAG, "插件 ${plugin.name} 覆盖了 ${applied.size} 个文件到 ${root.absolutePath}")
    }

    private fun revert(plugin: ZlPlugin) {
        val root = targetRoot(plugin.target)
        val appliedFile = File(plugin.dir, "applied.json")
        val applied = runCatching {
            gson.fromJson(appliedFile.readText(), Array<AppliedRecord>::class.java)?.toList()
        }.getOrNull().orEmpty()

        applied.forEach { record ->
            val dest = resolveIn(root, record.path)
            if (record.existed) {
                val bak = File(File(plugin.dir, "backup"), record.path)
                if (bak.isFile) {
                    dest.parentFile?.mkdirs()
                    bak.copyTo(dest, overwrite = true)
                }
            } else {
                runCatching { if (dest.isFile) dest.delete() }
            }
        }
        runCatching { appliedFile.delete() }
    }

    /** 卸载插件：先停用（还原文件），再删目录。 */
    fun remove(context: Context, plugin: ZlPlugin) {
        if (plugin.enabled) revert(plugin)
        plugin.dir.deleteRecursively()
        Logger.info(TAG, "已删除插件 ${plugin.name}")
    }
}
