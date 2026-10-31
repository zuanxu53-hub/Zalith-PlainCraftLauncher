/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * PCL2 外观设置的后端（对应 `PageSetupUI.xaml` 的 CardBackground / CardMusic /
 * CardLogo 的「图片」分支 / CardCustom）。
 *
 * 取值口径与 PCL 的设置项一一对应（`PageSetupUI.xaml` + `PageSetup/Settings.vb`）：
 *   `UiBackgroundSuit`   0 智能 / 1 居中 / 2 适应 / 3 拉伸 / 4 平铺 / 5 居左上 / 7 居左下 / 9 居右上 / 11 居右下
 *   `UiMusicRandom`      随机播放
 *   `UiMusicAuto`        打开启动器自动开始播放
 *   `UiMusicStart`       游戏启动后自动开始播放，游戏退出后自动暂停
 *   `UiMusicStop`        游戏启动后自动暂停播放，游戏退出后自动开始
 *   `UiMusicVolume`      音量（PCL 是 0..1000，这里存 0..100 的百分比）
 *   `UiCustomType`       0 空白 / 1 读取本地文件 / 2 联网更新 / 3 预设
 *   `UiCustomNet`        联网更新的网址
 *
 * **与 PCL 的差别（Android 平台限制）**：PCL 是让用户往 `PCL/Background` 这类
 * **文件夹**里丢文件，Android 没有稳定的「打开这个文件夹」入口，所以这里改成用系统
 * 文件选择器把文件**导入**到应用私有目录下的同名文件夹，之后行为与 PCL 一致
 * （每次打开随机取一张背景图）。文件夹本身仍然存在，导入进去的内容也仍然生效。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zalith.pcl.setting.launcherMMKV
import com.zalith.pcl.ui.pcl2.PclHintHost
import com.zalith.pcl.ui.pcl2.components.PclHintTheme
import java.io.File
import java.util.Locale

/** PCL `UiBackgroundSuit` 的九个取值（顺序照 XAML 里的下拉框）。 */
enum class PclBackgroundSuit(val value: Int, val label: String) {
    Auto(0, "智能"),
    Tile(4, "平铺"),
    Centered(1, "居中"),
    Stretch(3, "拉伸"),
    TopLeft(5, "居于左上"),
    TopRight(9, "居于右上"),
    BottomLeft(7, "居于左下"),
    BottomRight(11, "居于右下"),
    Adapt(2, "适应"),
    ;

    companion object {
        fun of(value: Int): PclBackgroundSuit =
            entries.firstOrNull { it.value == value } ?: Auto
    }
}

/** `UiCustomType` 的四个取值。 */
object PclHomeType {
    /** 右侧真的什么都不画。 */
    const val EMPTY = 0
    const val LOCAL = 1
    const val ONLINE = 2
    const val PRESET = 3
    /** PCL 默认首页：提示条 + 你知道吗 + 启动日志。 */
    const val DEFAULT = 4

    fun isValid(value: Int): Boolean = value in EMPTY..DEFAULT
}

object PclAppearance {

    /** 认作「背景音乐」的扩展名。列表筛选与导入命名共用这一份。 */
    private val MUSIC_EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a", "aac", "flac", "opus")

    private const val KEY_BG_SUIT = "pcl2.ui.backgroundSuit"
    private const val KEY_BG_IMAGE = "pcl2.ui.backgroundImage"
    private const val KEY_LOGO_IMAGE = "pcl2.ui.logoImage"
    private const val KEY_MUSIC_RANDOM = "pcl2.ui.musicRandom"
    private const val KEY_MUSIC_AUTO = "pcl2.ui.musicAuto"
    private const val KEY_MUSIC_START = "pcl2.ui.musicStart"
    private const val KEY_MUSIC_STOP = "pcl2.ui.musicStop"
    private const val KEY_MUSIC_VOLUME = "pcl2.ui.musicVolume"
    private const val KEY_HOME_TYPE = "pcl2.ui.customType"
    private const val KEY_HOME_TYPE_SPLIT = "pcl2.ui.customType.splitV2"
    private const val KEY_HOME_NET = "pcl2.ui.customNet"
    private const val KEY_HOME_PRESET = "pcl2.ui.customPreset"
    private const val KEY_HOME_LOCAL = "pcl2.ui.customLocal"

    // ------------------------------------------------------------------ 目录

    private const val DIR_BACKGROUND = "PCL/Background"
    private const val DIR_LOGO = "PCL/Logo"
    private const val DIR_MUSIC = "PCL/Music"
    private const val DIR_HOME = "PCL/Custom"

    fun backgroundDir(context: Context) = dir(context, DIR_BACKGROUND)
    fun logoDir(context: Context) = dir(context, DIR_LOGO)
    fun musicDir(context: Context) = dir(context, DIR_MUSIC)
    fun homeDir(context: Context) = dir(context, DIR_HOME)

    private fun dir(context: Context, name: String): File =
        File(context.filesDir, name).apply { if (!exists()) mkdirs() }

    // ------------------------------------------------------------------ 背景图片

    /** `UiBackgroundSuit`：背景图片的自适应方式。 */
    var backgroundSuit by mutableStateOf(PclBackgroundSuit.Auto)
        private set

    /** 当前实际使用的背景图片（绝对路径）；为空表示没有背景图片。 */
    var backgroundImage by mutableStateOf<String?>(null)
        private set

    val hasBackground: Boolean get() = backgroundImage?.let { File(it).isFile } == true

    fun applyBackgroundSuit(value: PclBackgroundSuit) {
        backgroundSuit = value
    }

    /**
     * `BtnBackgroundRefresh` —— 从背景文件夹里随机取一张（PCL 的
     * 「随机加载文件夹内的一张背景图片」）。文件夹为空则清掉背景图。
     *
     * 注意：这里面有**磁盘 IO**（列目录）。从 UI 事件里点一下没问题，
     * 但别在打开启动器的主线程路径上直接调 —— 那条路径走
     * [resolveBackgroundPath] + [applyResolvedBackground] 的组合。
     */
    fun refreshBackground(context: Context) {
        backgroundImage = resolveBackgroundPath(context)
    }

    /**
     * 只做「到磁盘上随机挑一张背景图」这件事，**不碰任何状态**，可以在 IO 线程调用。
     *
     * 拆分出来的原因：[load] 以前直接在这里 `listFiles()`，而 [load] 是在打开启动器的
     * `LaunchedEffect`（主线程）里调的 —— 背景图一多，进启动器就会先卡一下。
     */
    fun resolveBackgroundPath(context: Context): String? {
        val files = backgroundDir(context).listFiles()
            ?.filter { it.isFile && it.isPicture() }
            .orEmpty()
        return if (files.isEmpty()) null else files.random().absolutePath
    }

    /** 把 [resolveBackgroundPath]（或任何来源）算出来的路径落到状态上。 */
    fun applyResolvedBackground(path: String?) {
        backgroundImage = path
    }

    /** `BtnBackgroundClear` —— 清空背景图片。 */
    fun clearBackground() {
        backgroundImage = null
    }

    /**
     * 把选中的图片导入背景文件夹，并立刻用它作为背景。
     * @return 是否成功
     */
    fun importBackground(context: Context, uri: Uri, name: String?): Boolean {
        val target = importInto(context, uri, backgroundDir(context), name ?: "background.png")
            ?: return false
        backgroundImage = target.absolutePath
        return true
    }

    // ------------------------------------------------------------------ 标题栏图片

    /** `UiLogoType = 3`（图片）时显示的标题栏图片；为空时退回 PCL 的描边 Logo。 */
    var logoImage by mutableStateOf<String?>(null)
        private set

    val hasLogoImage: Boolean get() = logoImage?.let { File(it).isFile } == true

    fun importLogoImage(context: Context, uri: Uri, name: String?): Boolean {
        val target = importInto(context, uri, logoDir(context), name ?: "logo.png") ?: return false
        logoImage = target.absolutePath
        return true
    }

    fun clearLogoImage() {
        logoImage = null
    }

    // ------------------------------------------------------------------ 背景音乐

    var musicRandom by mutableStateOf(true)
        private set
    var musicAuto by mutableStateOf(false)
        private set
    var musicStart by mutableStateOf(false)
        private set
    var musicStop by mutableStateOf(false)
        private set

    /** `UiMusicVolume`，0..100 的百分比。 */
    var musicVolume by mutableStateOf(70)
        private set

    /** 是否正在播放。 */
    var musicPlaying by mutableStateOf(false)
        private set

    /** 当前播放的文件名。 */
    var musicTitle by mutableStateOf("")
        private set

    private var player: MediaPlayer? = null
    private var playlist: List<File> = emptyList()
    private var index = 0

    fun applyMusicRandom(value: Boolean) {
        musicRandom = value
    }

    /** PCL 里 `UiMusicStart` / `UiMusicStop` 互斥。 */
    fun applyMusicStart(value: Boolean) {
        musicStart = value
        if (value) musicStop = false
    }

    fun applyMusicStop(value: Boolean) {
        musicStop = value
        if (value) musicStart = false
    }

    fun applyMusicAuto(value: Boolean) {
        musicAuto = value
    }

    fun applyMusicVolume(value: Int) {
        musicVolume = value.coerceIn(0, 100)
        player?.let { runCatching { it.setVolume(f(), f()) } }
    }

    private fun f(): Float = musicVolume / 100f

    /** 音乐文件夹里的所有曲目（按文件名排序）。 */
    fun musicFiles(context: Context): List<File> =
        musicDir(context).listFiles()
            ?.filter { it.isFile && it.isMusic() }
            ?.sortedBy { it.name.lowercase(Locale.ROOT) }
            .orEmpty()

    fun importMusic(context: Context, uris: List<Uri>): Int {
        val dir = musicDir(context)
        var count = 0
        uris.forEach { uri ->
            if (importInto(context, uri, dir, pclMusicName(context, uri)) != null) count++
        }
        if (count > 0) {
            // 导入即播：PCL 里用户往 `PCL/Music` 里丢完文件、点一次「刷新背景音乐」也就开播了，
            // Android 上少了「丢文件」这一步，导入完什么都不发生就会让人以为没导进去。
            // 正在播的时候不要打断当前这首，只把新歌补进队列。
            playlist = musicFiles(context)
            if (!musicPlaying && playlist.isNotEmpty()) {
                index = if (musicRandom && playlist.size > 1) playlist.indices.random() else 0
                playCurrent(context)
            } else if (index !in playlist.indices) {
                index = 0
            }
        }
        return count
    }

    /** `BtnMusicRefresh` —— 重读播放列表并从第一首开始播放。 */
    fun refreshMusic(context: Context, play: Boolean = true) {
        stopMusic()
        playlist = musicFiles(context)
        index = if (musicRandom && playlist.size > 1) playlist.indices.random() else 0
        if (play && playlist.isNotEmpty()) playCurrent(context)
    }

    /**
     * `BtnMusicClear` —— 清空背景音乐。
     *
     * PCL 原版是把 `PCL/Musics` 整个目录删掉再建回来（`BtnMusicClear_Click`），所以「清空」
     * 意味着**磁盘上真的没有了**。这里原来只清了内存里的 `playlist` / `musicTitle`，文件一个没删：
     * 点完按钮界面看着像清空了，可只要再点「开始播放」「下一首」或「刷新背景音乐」，
     * [startMusic] / [next] 里的 `playlist = musicFiles(context)` 又会把整个文件夹读回来 ——
     * 表现就是「清空按钮失效」。所以这里必须连文件一起删。
     *
     * @return 实际删掉的文件数（删失败的不影响其余文件，也不抛异常）。
     */
    fun clearMusic(context: Context): Int {
        stopMusic()
        playlist = emptyList()
        index = 0
        musicTitle = ""
        val files = musicDir(context).listFiles()?.filter { it.isFile }.orEmpty()
        var deleted = 0
        files.forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) deleted++
        }
        return deleted
    }

    /** 播放 / 暂停切换（设置页的播放按钮）。 */
    fun toggleMusic(context: Context) {
        if (musicPlaying) pauseMusic() else startMusic(context)
    }

    fun startMusic(context: Context) {
        if (playlist.isEmpty()) playlist = musicFiles(context)
        if (playlist.isEmpty()) {
            PclHintHost.push("背景音乐文件夹是空的，先导入几首歌吧。", PclHintTheme.Yellow)
            return
        }
        playCurrent(context)
    }

    fun pauseMusic() {
        player?.let { runCatching { if (it.isPlaying) it.pause() } }
        musicPlaying = false
    }

    fun stopMusic() {
        player?.let { runCatching { it.stop(); it.release() } }
        player = null
        musicPlaying = false
    }

    private fun playCurrent(context: Context) {
        val file = playlist.getOrNull(index) ?: return
        stopMusic()
        val mp = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                isLooping = false
                setVolume(f(), f())
                setOnCompletionListener { next(context, auto = true) }
                // `prepare()` 是**同步**的（读文件头、建缓冲），调用它的线程会被挡住。
                // 而 `startMusic` 是在打开启动器的 `LaunchedEffect`（主线程）上直接调的，
                // 音乐文件稍大就会卡住界面。改用异步准备，准备好再 `start`。
                // 同步版本的失败靠异常回旋，异步版本的失败走 `setOnErrorListener`，
                // 两个回调都在主线程（MediaPlayer 建在主线程），可以直接改状态。
                setOnPreparedListener { mp -> runCatching { mp.start() } }
                setOnErrorListener { _, _, _ ->
                    musicPlaying = false
                    PclHintHost.push("这首音乐播放失败了：${file.name}", PclHintTheme.Red)
                    true
                }
                prepareAsync()
            }
        }.getOrNull() ?: run {
            PclHintHost.push("这首音乐播放失败了：${file.name}", PclHintTheme.Red)
            return
        }
        player = mp
        musicPlaying = true
        musicTitle = file.name
    }

    /** 下一首（[auto] = true 表示上一首播完了）。 */
    fun next(context: Context, auto: Boolean = false) {
        if (playlist.isEmpty()) playlist = musicFiles(context)
        if (playlist.isEmpty()) return
        index = if (musicRandom && playlist.size > 1) {
            // 随机的下一首不重复当前这首
            var next = playlist.indices.random()
            while (next == index) next = playlist.indices.random()
            next
        } else {
            (index + 1) % playlist.size
        }
        if (auto || musicPlaying) playCurrent(context) else musicTitle = playlist[index].name
    }

    // ------------------------------------------------------------------ 主页

    /** `UiCustomType`。 */
    var homeType by mutableStateOf(PclHomeType.DEFAULT)
        private set

    /** `UiCustomNet`。 */
    var homeNet by mutableStateOf("")
        private set

    /** 「读取本地文件」时导入进来的 html（绝对路径）。 */
    var homeLocalFile by mutableStateOf<String?>(null)
        private set

    /** 「预设」选中的预设 id（对应 [PclHomePresets.entries]，PCL 的 `UiCustomPreset`）。 */
    var homePreset by mutableStateOf(PclHomePresets.default().id)
        private set

    fun applyHomeType(value: Int) {
        homeType = value
    }

    fun applyHomeNet(value: String) {
        homeNet = value
    }

    fun applyHomePreset(value: Int) {
        homePreset = value
    }

    fun importHomeFile(context: Context, uri: Uri, name: String?): Boolean {
        val target = importInto(context, uri, homeDir(context), name ?: "Custom.html") ?: return false
        homeLocalFile = target.absolutePath
        return true
    }

    /**
     * 当前生效的自定义主页地址；没有就返回 null。
     *
     * 「联网更新」用网址，「读取本地文件」用 `file://` 指向导入进来的 html。
     */
    val homeUrl: String?
        get() = when (homeType) {
            PclHomeType.ONLINE -> homeNet.trim().ifBlank { null }
            PclHomeType.LOCAL -> homeLocalFile
                ?.takeIf { File(it).isFile }
                ?.let { "file://$it" }
            else -> null
        }

    // ------------------------------------------------------------------ 持久化

    fun load(context: Context) {
        val mmkv = launcherMMKV()
        backgroundSuit = PclBackgroundSuit.of(mmkv.getInt(KEY_BG_SUIT, 0))
        backgroundImage = mmkv.getString(KEY_BG_IMAGE, null)?.takeIf { it.isNotBlank() }
        logoImage = mmkv.getString(KEY_LOGO_IMAGE, null)?.takeIf { it.isNotBlank() }
        musicRandom = mmkv.getBoolean(KEY_MUSIC_RANDOM, true)
        musicAuto = mmkv.getBoolean(KEY_MUSIC_AUTO, false)
        musicStart = mmkv.getBoolean(KEY_MUSIC_START, false)
        musicStop = mmkv.getBoolean(KEY_MUSIC_STOP, false)
        musicVolume = mmkv.getInt(KEY_MUSIC_VOLUME, 70)
        // 旧版的「空白」实际上是默认信息流。首次升级时把旧的 0 迁到「默认」，
        // 之后用户再主动选「侧边什么也没有」才会保存新的 0。
        val storedHomeType = mmkv.getInt(KEY_HOME_TYPE, PclHomeType.DEFAULT)
        homeType = if (!mmkv.getBoolean(KEY_HOME_TYPE_SPLIT, false)) {
            val migrated = if (storedHomeType == PclHomeType.EMPTY) PclHomeType.DEFAULT else storedHomeType
            mmkv.putBoolean(KEY_HOME_TYPE_SPLIT, true)
            migrated
        } else {
            storedHomeType
        }
        if (!PclHomeType.isValid(homeType)) homeType = PclHomeType.DEFAULT
        homeNet = mmkv.getString(KEY_HOME_NET, "").orEmpty()
        homePreset = mmkv.getInt(KEY_HOME_PRESET, PclHomePresets.default().id)
            .let { id -> PclHomePresets.of(id)?.id ?: PclHomePresets.default().id }
        homeLocalFile = mmkv.getString(KEY_HOME_LOCAL, null)?.takeIf { it.isNotBlank() }
        // 没有记录背景图时，按 PCL 的行为在打开时随机取一张。
        // 这一步要在磁盘上列目录，所以**不在这里做** —— 由调用方放到 IO 线程：
        //   PclAppearance.applyResolvedBackground(withContext(Dispatchers.IO) {
        //       PclAppearance.resolveBackgroundPath(ctx)
        //   })
    }

    fun persist(context: Context) {
        launcherMMKV().apply {
            putInt(KEY_BG_SUIT, backgroundSuit.value)
            putString(KEY_BG_IMAGE, backgroundImage ?: "")
            putString(KEY_LOGO_IMAGE, logoImage ?: "")
            putBoolean(KEY_MUSIC_RANDOM, musicRandom)
            putBoolean(KEY_MUSIC_AUTO, musicAuto)
            putBoolean(KEY_MUSIC_START, musicStart)
            putBoolean(KEY_MUSIC_STOP, musicStop)
            putInt(KEY_MUSIC_VOLUME, musicVolume)
            putInt(KEY_HOME_TYPE, homeType)
            putString(KEY_HOME_NET, homeNet)
            putInt(KEY_HOME_PRESET, homePreset)
            putString(KEY_HOME_LOCAL, homeLocalFile ?: "")
        }
    }

    // ------------------------------------------------------------------ 工具

    private fun File.isPicture(): Boolean =
        extension.lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    private fun File.isMusic(): Boolean = extension.lowercase(Locale.ROOT) in MUSIC_EXTENSIONS

    /**
     * 音乐文件落地时该叫什么名字。
     *
     * **这是「导入成功却一首都不播」的根因**：系统音乐选择器给的是
     * `content://media/external/audio/media/1234` 这种 URI，`lastPathSegment` 拿到的就是
     * 一串数字 ID，**没有扩展名**。以前直接拿它当文件名，落地成 `1234`，
     * 而 [musicFiles] 是按扩展名筛歌的 → 文件在文件夹里躺着，但列表永远是空的。
     *
     * 取值顺序：`OpenableColumns.DISPLAY_NAME`（真实文件名）→ `lastPathSegment`
     * → 按 MIME 补扩展名 → 兜底 `music.mp3`。
     */
    private fun pclMusicName(context: Context, uri: Uri): String {
        val queried = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        val raw = queried?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "music"
        val name = raw.substringAfterLast('/').ifBlank { "music" }

        // 已经有扩展名（而且是我们认的音乐格式）就直接用
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext in MUSIC_EXTENSIONS) return name

        // 没有 / 不认识：按 MIME 猜一个。不猜的话文件永远不会被当成音乐。
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            ?.substringAfter('/', "")?.substringBefore(';')?.lowercase(Locale.ROOT)
        val guessed = when (mime) {
            "mpeg", "mp3" -> "mp3"
            "ogg", "opus", "flac", "aac", "wav", "x-wav", "wave", "m4a" -> if (mime == "x-wav" || mime == "wave") "wav" else mime
            "mp4", "x-m4a" -> "m4a"
            "x-flac" -> "flac"
            else -> "mp3"
        }
        return "$name.$guessed"
    }

    /** 把 `content://` 里的文件复制进目录（同名时加序号），返回落地的文件。 */
    private fun importInto(context: Context, uri: Uri, destDir: File, name: String): File? {
        return runCatching {
            destDir.mkdirs()
            var target = File(destDir, name.substringAfterLast('/').ifBlank { "file" })
            var seq = 1
            while (target.exists()) {
                val base = target.nameWithoutExtension
                val ext = target.extension
                target = File(destDir, if (ext.isBlank()) "$base ($seq)" else "$base ($seq).$ext")
                seq++
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("无法读取所选文件")
            target
        }.getOrNull()
    }
}

