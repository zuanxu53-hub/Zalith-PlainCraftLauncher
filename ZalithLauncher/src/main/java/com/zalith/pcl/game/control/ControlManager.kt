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

package com.zalith.pcl.game.control

import android.content.Context
import com.movtery.layer_controller.data.lang.createTranslatable
import com.movtery.layer_controller.layout.ControlLayout
import com.movtery.layer_controller.layout.EmptyControlLayout
import com.movtery.layer_controller.layout.EmptyLayoutInfo
import com.movtery.layer_controller.layout.loadLayoutFromFile
import com.movtery.layer_controller.layout.loadLayoutFromFileUncheck
import com.movtery.layer_controller.layout.loadLayoutFromString
import com.movtery.layer_controller.observable.ObservableControlLayout
import com.movtery.layer_controller.utils.newRandomFileName
import com.movtery.layer_controller.layout.patchLayoutJson
import com.movtery.layer_controller.utils.saveToFile
import com.zalith.pcl.context.copyAssetFile
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.utils.file.readString
import com.zalith.pcl.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.InputStream

private const val TAG = "ControlManager"

/**
 * 控制布局管理者
 */
object ControlManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _dataList = MutableStateFlow<List<ControlData>>(emptyList())
    val dataList = _dataList.asStateFlow()

    private var currentJob: Job? = null

    private val _selectedLayout = MutableStateFlow<ControlData?>(null)
    /** 当前选择的控制布局 */
    val selectedLayout = _selectedLayout.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** 是否正在刷新控制布局 */
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * 获取一个新的布局文件文件，名称随机
     */
    private fun getNewRandomFile() = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")

    /**
     * 检查当前是否不存在控制布局，不存在则解压一份默认控制布局
     * @param context 访问assets的上下文
     */
    fun checkDefaultAndRefresh(context: Context) {
        scope.launch(Dispatchers.IO) {
            val files = (PathManager.DIR_CONTROL_LAYOUTS.listFiles() ?: emptyArray())
                .filter { file ->
                    file.isFile && file.exists() && file.extension.equals("json", true)
                }
            if (files.isEmpty()) {
                unpackDefaultControl(context)
            }
            refresh()
        }
    }

    fun refresh() {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.update { true }
            try {
                val dir = PathManager.DIR_CONTROL_LAYOUTS
                if (!dir.exists() && !dir.mkdirs()) {
                    Logger.warning(TAG, "Failed to create control layout directory: ${dir.absolutePath}")
                }
                val files = dir.listFiles() ?: emptyArray()

                // 【别再「先清空再逐个解析」】原来第一行就是 `_dataList.update { emptyList() }`，
                // 然后才顺着 listFiles 往下走。只要有一个文件让 `ObservableControlLayout(...)`
                // 的构造抛异常（它不在任何 try 里），这个协程就死在中途：
                //   · 列表永远停在刚清空的空表 —— 连原来那份正常显示的默认布局也一起没了
                //   · `isRefreshing` 永远为 true —— 页面一直显示「正在读取控制布局…」
                // 用户看到的就是「导入完布局，这一页什么都不显示了」。而且下次进来还这样
                // （坏文件还在目录里），除非手动去文件夹里删掉。
                // 现在改成：整张表先算好再一次性替换，单个文件三层兜底，绝不影响别人。
                val list = files.mapNotNull { file ->
                    if (!(file.isFile && file.exists() && file.extension.equals("json", true))) return@mapNotNull null
                    buildControlData(file)
                }
                _dataList.update {
                    list.sortedBy {
                        if (it.isSupport) it.controlLayout.info.name.default
                        else it.file.name
                    }
                }
                checkSettings()
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to refresh control layouts", e)
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    /**
     * 把一个布局文件读成列表里的一行。
     *
     * **任何异常都不允许漏到 `refresh()` 外面**（漏出去就是整页空白，见上面的注释）。
     * 两层兜底：
     * 1. 解析失败（版本过新 / 不是合法 JSON / 根本不是这种格式）→ 用 `EmptyControlLayout`
     *    占位并标 `isSupport = false`，列表里显示成「不支持或无法读取，只能删除」。
     * 2. 解析出来了但 `ObservableControlLayout` 构造不出来（图层里有本版本不认识的控件）→
     *    同样退回占位行，而不是把这个文件从列表里抹掉。
     */
    private fun buildControlData(file: File): ControlData {
        var isSupport = true
        val layout: ControlLayout = try {
            loadLayoutFromFile(file)
        } catch (_: IllegalArgumentException) {
            isSupport = false
            runCatching {
                loadLayoutFromFileUncheck(file)
            }.onFailure { e ->
                Logger.warning(TAG, "Failed to load control layout! file = $file", e)
            }.getOrNull() ?: EmptyControlLayout
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to load control layout! file = $file", e)
            isSupport = false
            EmptyControlLayout
        }

        return runCatching {
            ControlData(
                file = file,
                controlLayout = ObservableControlLayout(layout),
                isSupport = isSupport
            )
        }.getOrElse { e ->
            Logger.warning(TAG, "Failed to observe control layout! file = $file", e)
            ControlData(
                file = file,
                controlLayout = ObservableControlLayout(EmptyControlLayout),
                isSupport = false
            )
        }
    }

    /**
     * 检查并更新设置
     */
    private fun checkSettings() {
        val setting = AllSettings.controlLayout.getValue()

        val layout = _dataList.value.find { it.file.name == setting && it.isSupport }
            ?: dataList.value.firstOrNull { it.isSupport }
                ?.also { AllSettings.controlLayout.save(it.file.name) }

        if (layout == null) {
            AllSettings.controlLayout.reset()
        }

        _selectedLayout.update { layout }
    }

    /**
     * 确保控制布局目录里至少有一份可读布局，并返回它。
     *
     * 游戏内「编辑布局」必须有具体文件才能保存。PCL2 界面冷启动时不会经过
     * `MainActivity.onResume`，而那里原本是唯一会自动解压默认布局的地方；
     * 一旦用户没进过布局设置页，目录就是空的，`currentControlFile` 也为 null。
     */
    suspend fun ensureDefaultControl(context: Context): File? = withContext(Dispatchers.IO) {
        val dir = PathManager.DIR_CONTROL_LAYOUTS
        if (!dir.exists() && !dir.mkdirs()) {
            Logger.warning(TAG, "Failed to create control layout directory: ${dir.absolutePath}")
            return@withContext null
        }
        dir.listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("json", true) }
            ?.let { return@withContext it }

        val file = getNewRandomFile()
        runCatching {
            context.copyAssetFile(fileName = "default_layout.json", output = file, overwrite = false)
        }.onFailure { e ->
            Logger.warning(TAG, "Failed to unpack default control layout", e)
        }
        file.takeIf { it.isFile && it.length() > 0 }
    }

    /**
     * 解压默认控制布局
     */
    private suspend fun unpackDefaultControl(
        context: Context
    ) = withContext(Dispatchers.IO) {
        try {
            val file = getNewRandomFile()
            context.copyAssetFile(fileName = "default_layout.json", output = file, overwrite = false)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to unpack default control layout", e)
        }
    }

    /**
     * 选择控制布局
     */
    fun selectControl(data: ControlData) {
        if (!data.file.exists() || !data.isSupport) return
        AllSettings.controlLayout.save(data.file.name)
        _selectedLayout.update { data }
    }

    /**
     * 在协程内删除控制布局
     */
    fun deleteControl(data: ControlData) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) return@launch
            FileUtils.deleteQuietly(data.file)
            refresh()
        }
    }

    /**
     * 在协程内保存控制布局的数据
     */
    fun saveControl(
        data: ControlData,
        submitError: (Exception) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) {
                refresh()
                return@launch
            }
            val layout = data.controlLayout.pack()
            try {
                layout.saveToFile(data.file)
            } catch (e: Exception) {
                submitError(e)
//                FileUtils.deleteQuietly(data.file)
            }
            refresh()
        }
    }

    /**
     * 新建一份空白控制布局。
     *
     * 对应原生 `ControlManageScreen` 的「新建」：`EmptyControlLayout` 换掉 info 的
     * 名称/作者/版本号之后直接存盘（`ControlViewModel.createNew`）。
     */
    fun createEmptyControl(name: String, submitError: (Exception) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val file = getNewRandomFile()
            try {
                EmptyControlLayout.copy(
                    info = EmptyLayoutInfo.copy(
                        name = createTranslatable(default = name),
                        author = createTranslatable(default = ""),
                        versionName = ""
                    )
                ).saveToFile(file)
            } catch (e: Exception) {
                submitError(e)
                FileUtils.deleteQuietly(file)
            }
            refresh()
        }
    }

    /**
     * 复制一份已有的控制布局。
     *
     * 对应原生 `ControlViewModel.copyNew`：`ObservableControlLayout.pack()` 出来原样另存。
     */
    fun duplicateControl(data: ControlData, submitError: (Exception) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val file = getNewRandomFile()
            try {
                data.controlLayout.pack().saveToFile(file)
            } catch (e: Exception) {
                submitError(e)
                FileUtils.deleteQuietly(file)
            }
            refresh()
        }
    }

    /**
     * 导入一份控制布局。
     *
     * 【顺序很重要：先落盘，再解析】
     * 导入的本质是**把这份文件放进控制布局目录**（用户分享的、选中的就是一个文件），
     * 解析只决定它能不能被正常显示，不该决定它能不能留下。
     * 以前的写法是「解析成功才 `saveToFile`」，格式差一点点就什么都不剩 —— 用户看到的
     * 是「导入了，列表里还是只有 default」，连失败的那个文件都找不到，无从排查。
     *
     * 现在的顺序：
     * 1. 原样 `copyTo` 进目录（一个字节都不改）；
     * 2. 清掉 BOM / 零宽空格（Windows 记事本另存过的 json 必带，不清必定解析失败）；
     * 3. 能解析 → 按标准格式重写一遍（补齐的字段真正落盘）；解析不了 → 文件照样留在
     *    目录里，列表里占一行「不支持或无法读取」，可删、可导出。
     */
    suspend fun importControl(
        inputStream: InputStream,
        onSerializationError: (Exception) -> Unit,
        catchedError: (Exception) -> Unit,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = getNewRandomFile()

        // ① 原样落盘。
        //    **必须带超时**：SAF 的流由文件管理器提供，个别 provider 会一直不给数据，
        //    `copyTo` 就永远阻塞在 IO 线程上 —— 界面上永远停在「正在导入键位布局…」，
        //    连失败提示都出不来。20 秒足够拷完任何正常大小的布局 json。
        val copied = try {
            withTimeoutOrNull(20_000) {
                file.parentFile?.mkdirs()
                inputStream.use { stream ->
                    file.outputStream().use { out -> stream.copyTo(out) }
                }
                true
            }
        } catch (e: Exception) {
            Logger.warning(TAG, "控制布局写盘失败", e)
            null
        }
        if (copied == null) {
            FileUtils.deleteQuietly(file)
            catchedError(
                IllegalStateException("读取这个文件失败或超时（20 秒），可能是文件太大，或者文件管理器没有正常返回数据。")
            )
            return@withContext
        }

        // ② 清理隐形垃圾。`loadLayoutFromFile` 读的时候会原样带着 BOM，
        //    kotlinx 直接抛 `Unexpected JSON token at offset 0`。
        val text = try {
            file.readText()
        } catch (e: Exception) {
            Logger.warning(TAG, "控制布局读出失败（可能不是 UTF-8 文本）", e)
            catchedError(e)
            return@withContext
        }
        val cleaned = text.stripJsonNoise()
        if (cleaned != text) {
            runCatching { file.writeText(cleaned) }
        }

        // ③ 再谈能不能解析
        try {
            val layout = parseLayoutLeniently(cleaned)
            // 解析成功就按标准格式重写一遍：补齐过的字段要真正落到磁盘，
            // 否则列表重新扫的时候读的还是原始那份，又变回「不支持」。
            layout.saveToFile(file)
            Logger.info(TAG, "控制布局导入成功：${file.name}")
            onFinished()
        } catch (e: SerializationException) {
            Logger.warning(TAG, "控制布局不是可识别的 JSON 格式", e)
            // 原始异常是一句英文（`The file does not contain the key "editorVersion".`），
            // 用户看不出问题在哪；这里换成能对号入座的说法，并强调文件已经存下来了。
            val message = when {
                e.message?.contains("editorVersion") == true ->
                    "文件已保存，但读不出来：这份 json 缺少 editorVersion 字段，不是本启动器的控制布局格式。"
                e.message?.contains("Unexpected JSON token", ignoreCase = true) == true ->
                    "文件已保存，但读不出来：不是合法的 JSON，内容可能已损坏（比如从网页复制时丢了括号）。"
                else -> "文件已保存，但读不出来：${e.message}"
            }
            onSerializationError(SerializationException(message, e))
        } catch (e: IllegalArgumentException) {
            // `loadLayoutFromString` 在文件的 editorVersion 比本启动器新时抛这个。
            // 注意 SerializationException 也继承自 IllegalArgumentException，必须排在它后面。
            Logger.warning(TAG, "控制布局版本过新", e)
            catchedError(
                IllegalArgumentException(
                    "文件已保存，但这个布局来自更新版本的启动器，需要先升级启动器才能打开。", e
                )
            )
        } catch (e: Exception) {
            Logger.warning(TAG, "控制布局解析失败", e)
            catchedError(e)
        }
    }

    /**
     * 先按标准格式解析，失败再补齐缺字段重试一次。
     *
     * `ControlLayout` 只有 `info` 和 `editorVersion` 没有默认值，从别处下载的布局
     * 往往就缺这一个 `editorVersion` —— 报错是英文的 `The file does not contain
     * the key "editorVersion".`，用户看着就像「格式不兼容，废了」。补上再试，
     * 大部分还能正常打开。
     */
    private fun parseLayoutLeniently(jsonString: String): ControlLayout {
        val first = runCatching { loadLayoutFromString(jsonString) }
        first.getOrNull()?.let { return it }

        val patched = runCatching { patchLayoutJson(jsonString) }.getOrNull()
        if (patched != null && patched != jsonString) {
            Logger.info(TAG, "布局缺少部分字段，补齐后重试解析")
            runCatching { loadLayoutFromString(patched) }.getOrNull()?.let { return it }
        }
        throw first.exceptionOrNull() ?: SerializationException("无法把这个文件读成控制布局")
    }

}

/**
 * 去掉 JSON 文本里的「隐形垃圾」。
 *
 * 最常见的两种：
 *   · **UTF-8 BOM**（`EF BB BF`）—— 用 Windows 记事本 / 某些下载站另存过的文件都带，
 *     `IOUtils.toString(UTF_8)` 会把它保留成 `\uFEFF`，kotlinx.serialization 直接抛
 *     「Unexpected JSON token at offset 0」，表现就是「这份布局导不进来」，而且报错
 *     里一个中文字符都没有，用户根本看不出是 BOM 的问题。
 *   · **零宽空格 / 首尾空白** —— 复制粘贴拼出来的文件常见。
 */
private fun String.stripJsonNoise(): String =
    trimStart('\uFEFF', '\u200B').trim()

/**
 * 打开 SAF 文档 Uri 的输入流。
 *
 * `openInputStream` 对少数第三方文件管理器（以及没有给 MIME 提示的 provider）
 * 会返回 null 甚至抛异常，这时退回 `openFileDescriptor` 拿原始 fd —— 两条都失败
 * 才真的读不到。
 */
fun openDocumentStream(context: Context, uri: android.net.Uri): InputStream? {
    runCatching { context.contentResolver.openInputStream(uri) }
        .getOrNull()
        ?.let { return it }
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.let {
            java.io.FileInputStream(it.fileDescriptor)
        }
    }.getOrNull()
}