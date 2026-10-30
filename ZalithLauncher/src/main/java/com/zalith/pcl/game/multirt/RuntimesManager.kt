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

package com.zalith.pcl.game.multirt

import android.system.Os
import com.zalith.pcl.R
import com.zalith.pcl.ZLApplication.Companion.DEVICE_ARCHITECTURE
import com.zalith.pcl.components.jre.Jre
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.utils.device.Architecture
import com.zalith.pcl.utils.file.child
import com.zalith.pcl.utils.file.ensureDirectory
import com.zalith.pcl.utils.file.ensureParentDirectory
import com.zalith.pcl.utils.logging.Logger
import com.zalith.pcl.utils.math.findNearestPositive
import com.zalith.pcl.utils.string.compareVersion
import com.zalith.pcl.utils.string.extractUntilCharacter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "RuntimesManager"

/**
 * [Modified from PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/multirt/MultiRTUtils.java)
 */
object RuntimesManager {
    private val cache = ConcurrentHashMap<String, Runtime>()

    private val RUNTIME_FOLDER = PathManager.DIR_MULTIRT
    private const val JAVA_VERSION_STR: String = "JAVA_VERSION=\""
    private const val OS_ARCH_STR: String = "OS_ARCH=\""

    fun getRuntimes(forceLoad: Boolean = false): List<Runtime> {
        if (!RUNTIME_FOLDER.exists()) {
            Logger.warning(TAG, "Runtime directory not found: ${RUNTIME_FOLDER.absolutePath}")
            return emptyList()
        }

        return RUNTIME_FOLDER.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { loadRuntime(it.name, forceLoad = forceLoad) }
            ?.sortedWith { o1, o2 ->
                val thisVer = o1.versionString ?: o1.name
                -thisVer.compareVersion(o2.versionString ?: o2.name)
            }
            ?: throw IllegalStateException("Failed to access runtime directory")
    }

    fun getExactJreName(majorVersion: Int): String? {
        return getRuntimes().firstOrNull { it.javaVersion == majorVersion }?.name
    }

    fun getNearestJreName(majorVersion: Int): String? {
        return findNearestPositive(majorVersion, getRuntimes()) { it.javaVersion }?.value?.name
    }

    fun forceReload(name: String): Runtime {
        cache.remove(name)
        return loadRuntime(name)
    }

    fun loadRuntime(name: String, forceLoad: Boolean = false): Runtime {
        return cache[name]?.takeIf { !forceLoad } ?: run {
            val runtimeDir = File(RUNTIME_FOLDER, name)
            val releaseFile = File(runtimeDir, "release")

            if (!releaseFile.exists()) return Runtime(name).also { cache[name] = it }

            runCatching {
                val content = releaseFile.readText()
                val javaVersion = content.extractUntilCharacter(JAVA_VERSION_STR, '"')
                val osArch = content.extractUntilCharacter(OS_ARCH_STR, '"')

                if (javaVersion != null && osArch != null) {
                    val versionParts = javaVersion.split('.')
                    val majorVersion = if (versionParts.first() == "1") {
                        versionParts.getOrNull(1)?.toIntOrNull() ?: 0
                    } else {
                        versionParts.first().toIntOrNull() ?: 0
                    }

                    Runtime(
                        name = name,
                        versionString = javaVersion,
                        arch = osArch,
                        javaVersion = majorVersion,
                        isProvidedByLauncher = Jre.entries.any { it.jreName == name },
                        isJDK8 = isJDK8(runtimeDir.absolutePath)
                    )
                } else {
                    Runtime(name)
                }
            }.onFailure { e ->
                Logger.error(TAG, "Failed to load runtime $name", e)
            }.getOrElse {
                Runtime(name)
            }.also { cache[name] = it }
        }
    }

    @Throws(IOException::class)
    suspend fun installRuntime(
        nativeLibDir: String,
        inputStream: InputStream,
        name: String,
        updateProgress: (Int, Array<Any>) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val dest = RUNTIME_FOLDER.child(name)
        try {
            if (dest.exists()) FileUtils.deleteDirectory(dest)
            uncompressTarXZ(inputStream, dest, updateProgress)
            unpack200(nativeLibDir, dest.absolutePath)
            loadRuntime(name).also { runtime ->
                postPrepare(runtime)
            }
        } catch (e: Exception) {
            FileUtils.deleteDirectory(dest)
            throw e
        }
    }

    @Throws(IOException::class)
    suspend fun postPrepare(name: String) = withContext(Dispatchers.IO) {
        val dest = RUNTIME_FOLDER.child(name)
        if (!dest.exists()) return@withContext
        val runtime = loadRuntime(name)
        postPrepare(runtime)
    }

    @Throws(IOException::class)
    suspend fun postPrepare(runtime: Runtime) = withContext(Dispatchers.IO) {
        val dest = RUNTIME_FOLDER.child(runtime.name)
        if (!dest.exists()) return@withContext
        var libFolder = "lib"

        val arch = runtime.arch
        if (arch != null && dest.child(libFolder, arch).exists()) {
            libFolder += "/$arch"
        }

        val isJDK8 = isJDK8(dest.absolutePath)
        if (isJDK8) {
            libFolder = "/jre$libFolder"
        }

        val ftIn = dest.child(libFolder, "libfreetype.so.6")
        val ftOut = dest.child(libFolder, "libfreetype.so")
        if (ftIn.exists() && (!ftOut.exists() || ftIn.length() != ftOut.length())) {
            if (!ftIn.renameTo(ftOut)) throw IOException("Failed to rename freetype")
        }

        val ft2In = dest.child(libFolder, "libfreetype.so")
        if (isJDK8 && ft2In.exists()) {
            ft2In.renameTo(ftOut)
        }

        val localXawtLib = File(PathManager.DIR_NATIVE_LIB, "libawt_xawt.so")
        val targetXawtLib = dest.child(libFolder, "libawt_xawt.so")
        if (targetXawtLib.exists()) targetXawtLib.delete()
        FileUtils.copyFile(localXawtLib, targetXawtLib)
    }

    @Throws(IOException::class)
    suspend fun installRuntimeBinPack(
        universalFileInputStream: InputStream,
        platformBinsInputStream: InputStream,
        name: String,
        binPackVersion: String,
        updateProgress: (Int, Array<Any>) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        val dest = RUNTIME_FOLDER.child(name)
        try {
            if (dest.exists()) FileUtils.deleteDirectory(dest)
            installRuntimeNoRemove(universalFileInputStream, dest, updateProgress)
            installRuntimeNoRemove(platformBinsInputStream, dest, updateProgress)

            unpack200(PathManager.DIR_NATIVE_LIB, dest.absolutePath)

            val versionFile = File(dest, "version")
            versionFile.writeText(binPackVersion)

            ensureReleaseFile(dest, name)

            forceReload(name)
        } catch (e: Exception) {
            FileUtils.deleteDirectory(dest)
            throw e
        }
    }

    /**
     * 兜底补一份 `release` 文件。
     *
     * [loadRuntime] 靠 `release` 里的 `JAVA_VERSION` / `OS_ARCH` 判断运行库是否可用，
     * 解压出来的目录里万一没有它（包里没带、或解压不完整），运行库就会被判成「坏的」，
     * 安装加载器会直接以「JVM 退出码 1」失败。
     * 内部运行库的 java 版本在 [Jre] 里是已知的、架构就是本机架构，可以安全地补出来。
     */
    private fun ensureReleaseFile(dest: File, name: String) {
        val releaseFile = File(dest, "release")
        if (releaseFile.isFile && releaseFile.length() > 0L) return

        val majorVersion = Jre.entries.firstOrNull { it.jreName == name }?.majorVersion ?: return

        runCatching {
            releaseFile.writeText(
                buildString {
                    append("JAVA_VERSION=\"")
                    //release 里 java 8 的写法是 1.8.0
                    append(if (majorVersion == 8) "1.8.0" else majorVersion.toString())
                    append("\"\nOS_ARCH=\"")
                    //注意必须是 aarch64 这种写法：getJavaLibDir / getJvmLibDir 会拿它去拼 lib/<arch>
                    append(deviceOsArch())
                    append("\"\n")
                }
            )
            Logger.warning(TAG, "Wrote a fallback release file for runtime $name: ${releaseFile.absolutePath}")
        }.onFailure {
            Logger.warning(TAG, "Failed to write the fallback release file for runtime $name", it)
        }
    }

    /** 本机架构在 `release` 的 `OS_ARCH` 里的写法（不是 Android 的 ABI 名） */
    private fun deviceOsArch(): String = when (DEVICE_ARCHITECTURE) {
        Architecture.ARCH_ARM64 -> "aarch64"
        Architecture.ARCH_ARM -> "arm"
        Architecture.ARCH_X86_64 -> "amd64"
        Architecture.ARCH_X86 -> "i386"
        else -> "unknown"
    }

    fun loadInternalRuntimeVersion(name: String): String? {
        val versionFile = RUNTIME_FOLDER.child(name, "version")
        try {
            return if (versionFile.exists()) {
                versionFile.readText()
            } else {
                null
            }
        } catch (_: IOException) {
            return null
        }
    }

    @Throws(IOException::class)
    fun removeRuntime(name: String) {
        val dest: File = RUNTIME_FOLDER.child(name).takeIf { it.exists() } ?: return
        FileUtils.deleteDirectory(dest)
        cache.remove(name)
    }

    fun getRuntimeHome(name: String): File {
        val dest = RUNTIME_FOLDER.child(name)
        if (!dest.exists() || forceReload(name).versionString == null) {
            throw RuntimeException("Selected runtime is broken!")
        }

        return dest
    }

    /**
     * 运行库是不是真的可用：目录存在，且 `release` 能解析出 java 版本、架构与本机一致。
     *
     * 只比对 `version` 标记是不够的 —— 标记文件在、但解压不完整（缺 `release`）时，
     * 运行库会被永远判定成「已安装」，而实际一用就废：
     * 安装加载器会以「JVM 退出码 1」告终，本质是 `getRuntimeHome` 抛了
     * `Selected runtime is broken!`。
     */
    fun isRuntimeUsable(name: String): Boolean = runCatching {
        val dest = RUNTIME_FOLDER.child(name)
        dest.exists() && forceReload(name).isCompatible() && findMissingRuntimeFiles(dest).isEmpty()
    }.getOrDefault(false)

    /**
     * 运行库目录里缺了哪些**起来就活不了**的文件。
     *
     * 只看 `release` 是不够的：解压被中途打断（存储满、进程被杀、包本身损坏）时，
     * `release` 往往已经落盘，`bin/java`、`libjli.so`、`libjvm.so` 却没写进去。
     * 这种目录会被判成「已安装、且版本架构都对」，一直用到真正拉起 JVM 才炸 ——
     * 原生层 `dlopen("libjli.so")` 失败，回传的退出码是 **-1**，
     * 界面上只会显示「JVM 以异常代码 -1 退出」，谁也看不出是运行库残缺。
     *
     * 这里按名字递归找一遍：不同版本布局不一样（JRE 8 是 `lib/<arch>/jli/libjli.so`，
     * JRE 9+ 是 `lib/libjli.so`），按目录结构猜容易再次猜错，直接按文件名找最稳。
     */
    private fun findMissingRuntimeFiles(dest: File): List<String> {
        if (!dest.exists()) return listOf("运行库目录")

        val missing = mutableListOf<String>()
        if (!File(dest, "bin/java").exists()) missing += "bin/java"

        val soNames = runCatching {
            FileUtils.listFiles(dest, arrayOf("so"), true).map { it.name }.toHashSet()
        }.getOrDefault(emptySet<String>())
        if ("libjli.so" !in soNames) missing += "libjli.so"
        if ("libjvm.so" !in soNames) missing += "libjvm.so"
        return missing
    }

    /**
     * 尝试低成本修好一个「标记对得上、但实际不可用」的运行库。
     *
     * 目前能处理的只有「缺 release 文件」这一种：内部运行库的 java 版本与架构都是已知的，
     * 直接补上就能用，不必把几百 MB 的运行库整个重新解压。
     * @return 修复（或本来就）可用则为 true
     */
    fun repairRuntime(name: String): Boolean {
        if (isRuntimeUsable(name)) return true

        val dest = RUNTIME_FOLDER.child(name)
        if (dest.exists()) {
            ensureReleaseFile(dest, name)
            forceReload(name)
        }

        return isRuntimeUsable(name)
    }

    /**
     * 说明一个运行库为什么不可用（用于把原因写进报错信息给用户看）
     */
    fun describeRuntimeProblem(name: String): String = runCatching {
        val dest = RUNTIME_FOLDER.child(name)
        when {
            !dest.exists() -> "目录不存在（可能从未解压成功）"

            !File(dest, "release").isFile -> "缺少 release 文件（解压不完整）"

            else -> {
                val runtime = forceReload(name)
                val missing = findMissingRuntimeFiles(dest)
                when {
                    missing.isNotEmpty() ->
                        "解压不完整，缺少 ${missing.joinToString("、")}（需要重新解压）"

                    runtime.versionString == null -> "release 里解析不出 JAVA_VERSION"
                    runtime.arch == null -> "release 里解析不出 OS_ARCH"
                    !runtime.isCompatible() -> "架构不匹配（release 里是 ${runtime.arch}）"
                    else -> "可用"
                }
            }
        }
    }.getOrElse { "检查运行库时出错：${it.message}" }

    /**
     * Unpacks all .pack files into .jar Serves only for java 8, as java 9 brought project jigsaw
     * @param nativeLibraryDir The native lib path, required to execute the unpack200 binary
     * @param runtimePath The path to the runtime to walk into
     */
    private suspend fun unpack200(
        nativeLibraryDir: String,
        runtimePath: String
    ) = withContext(Dispatchers.Default) {
            val basePath = File(runtimePath)
            val files: Collection<File> = FileUtils.listFiles(basePath, arrayOf("pack"), true)

            val workDir = File(nativeLibraryDir)
            val processBuilder = ProcessBuilder().directory(workDir)

            files.forEach { jarFile ->
                ensureActive()
                runCatching {
                    val destPath = jarFile.absolutePath.replace(".pack", "")
                    processBuilder.command(
                        "./libunpack200.so",
                        "-r",
                        jarFile.absolutePath,
                        destPath
                    ).start().apply {
                        waitFor()
                    }
                }.onFailure { e ->
                    if (e is IOException) {
                        Logger.error(TAG, "Failed to unpack the runtime!", e)
                    } else throw e
                }
            }
        }

    @Throws(IOException::class)
    private suspend fun installRuntimeNoRemove(
        inputStream: InputStream,
        dest: File,
        updateProgress: (Int, Array<Any>) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        uncompressTarXZ(inputStream, dest, updateProgress)
        inputStream.close()
    }

    @Throws(IOException::class)
    private suspend fun uncompressTarXZ(
        inputStream: InputStream,
        dest: File,
        updateProgress: (Int, Array<Any>) -> Unit = { _, _ -> }
    ) = withContext(Dispatchers.IO) {
        dest.ensureDirectory()
        val buffer = ByteArray(8192)

        TarArchiveInputStream(XZCompressorInputStream(inputStream)).use { tarIn ->
            generateSequence { tarIn.nextEntry }.forEach { tarEntry ->
                ensureActive()
                val tarEntryName = tarEntry.name
                updateProgress(R.string.generic_unpacking, arrayOf(tarEntryName))

                val destPath = File(dest, tarEntryName).ensureParentDirectory()

                when {
                    tarEntry.isSymbolicLink -> try {
                        if (destPath.exists()) destPath.delete()
                        Os.symlink(tarEntry.linkName, destPath.absolutePath)
                    } catch (e: Throwable) {
                        Logger.warning(TAG, "Failed to create symbolic link: ${destPath.absolutePath} -> ${tarEntry.linkName} (${e.message})")
                    }

                    tarEntry.isDirectory -> destPath.ensureDirectory()
                    !destPath.exists() || destPath.length() != tarEntry.size ->
                        FileOutputStream(destPath).use { os ->
                            IOUtils.copyLarge(tarIn, os, buffer)
                        }
                }
            }
        }
    }

    fun isJDK8(runtimeDir: String): Boolean {
        return File(runtimeDir, "jre").exists() && File(runtimeDir, "bin/javac").exists()
    }
}