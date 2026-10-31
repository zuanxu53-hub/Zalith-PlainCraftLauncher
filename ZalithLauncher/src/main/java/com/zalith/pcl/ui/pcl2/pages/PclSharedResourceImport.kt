/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * Mod / 资源包 / 光影包的本地导入。
 *
 * 两条入口共用这里：
 * · 实例资源页的「从文件导入」；
 * · 从文件管理器分享到启动器后，选择目标实例再导入。
 */

package com.zalith.pcl.ui.pcl2.pages

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zalith.pcl.game.version.installed.Version
import com.zalith.pcl.game.version.installed.VersionFolders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 可直接放进实例目录的资源类型。 */
enum class PclSharedResourceKind(
    val label: String,
    val folder: VersionFolders,
    val extensions: Set<String>,
) {
    MOD("Mod", VersionFolders.MOD, setOf("jar")),
    RESOURCE_PACK("资源包", VersionFolders.RESOURCE_PACK, setOf("zip")),
    SHADER("光影包", VersionFolders.SHADERS, setOf("zip")),
    SCHEMATIC("投影原理图", VersionFolders.NONE, setOf("litematic", "schematic", "nbt"));

    fun dirOf(version: Version): File = if (this == SCHEMATIC) {
        File(version.getGameDir(), "schematics")
    } else {
        folder.getDir(version.getGameDir())
    }
}

/** 按文件名后缀猜资源类型；`.zip` 可能是资源包也可能是光影包，返回 null 让用户选。 */
fun pclSharedKindOf(fileName: String): PclSharedResourceKind? {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "jar" -> PclSharedResourceKind.MOD
        "litematic", "schematic", "nbt" -> PclSharedResourceKind.SCHEMATIC
        else -> null
    }
}

/** 取 content URI 的显示名。 */
fun pclDisplayName(context: Context, uri: Uri): String? = runCatching {
    if (uri.scheme == "file") return@runCatching uri.lastPathSegment
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull() ?: runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()

/**
 * 把一个 content URI 复制进 [dir]。
 *
 * 同名文件不覆盖：自动改成 `name (1).ext`，避免用户重复导入时把原来的文件顶掉。
 * 扩展名不符合 [kind] 时直接返回失败原因。
 */
/**
 * 导入存档 zip：先复制到缓存，再交给现有的 `pclInstallDownloadedSave` 解压到 saves/<name>/。
 * 存档不能像 Mod 那样直接把 zip 丢进目录，游戏只认文件夹。
 */
suspend fun pclImportSaveZip(
    context: Context,
    uri: Uri,
    savesDir: File,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val rawName = pclDisplayName(context, uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "imported_save.zip"
        if (!rawName.endsWith(".zip", ignoreCase = true)) {
            error("存档需要 zip 压缩包（游戏只识别解压后的存档文件夹）。")
        }
        val temp = File(context.cacheDir, rawName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("读不到所选文件，可能是文件管理器没有授予读取权限。")
        if (!temp.isFile || temp.length() <= 0L) {
            temp.delete()
            error("存档压缩包为空或复制失败。")
        }
        try {
            pclInstallDownloadedSave(zip = temp, savesDir = savesDir)
            rawName.removeSuffix(".zip")
        } finally {
            temp.delete()
        }
    }
}

suspend fun pclImportSharedResource(
    context: Context,
    uri: Uri,
    dir: File,
    kind: PclSharedResourceKind,
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val rawName = pclDisplayName(context, uri)
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
            ?: "imported.${kind.extensions.first()}"
        val ext = rawName.substringAfterLast('.', "").lowercase()
        if (ext !in kind.extensions) {
            error("文件类型不对：${kind.label}需要 ${kind.extensions.joinToString(" / ") { ".$it" }}")
        }
        if (!dir.exists() && !dir.mkdirs()) error("无法创建目录：${dir.absolutePath}")

        var target = File(dir, rawName)
        if (target.exists()) {
            val base = rawName.substringBeforeLast('.', rawName)
            var index = 1
            do {
                target = File(dir, "$base ($index).$ext")
                index++
            } while (target.exists())
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("读不到所选文件，可能是文件管理器没有授予读取权限。")
        if (!target.isFile || target.length() <= 0L) error("文件为空或复制失败。")
        // OpenDocument 的授权默认只活到页面销毁；能持久化就存下来，减少跨应用 URI 的权限问题。
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        target
    }
}
