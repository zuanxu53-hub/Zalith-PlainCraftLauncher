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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.jakewharton.processphoenix.ProcessPhoenix
import com.zalith.pcl.R
import com.zalith.pcl.context.COPY_LABEL_LINK
import com.zalith.pcl.path.PathManager
import com.zalith.pcl.game.launch.GameExitDiagnostics
import com.zalith.pcl.setting.AllSettings
import com.zalith.pcl.ui.base.BaseAppCompatActivity
import com.zalith.pcl.ui.pcl2.data.PclThemeState
import com.zalith.pcl.ui.pcl2.theme.PclTheme
import com.zalith.pcl.ui.screens.main.ErrorScreen
import com.zalith.pcl.ui.screens.main.PclCrashBody
import com.zalith.pcl.ui.screens.main.crashlogs.ShareLinkOperation
import com.zalith.pcl.ui.theme.ZalithLauncherTheme
import com.zalith.pcl.ui.theme.backgroundColor
import com.zalith.pcl.ui.theme.onBackgroundColor
import com.zalith.pcl.utils.copyText
import com.zalith.pcl.utils.file.shareFile
import com.zalith.pcl.utils.getParcelableSafely
import com.zalith.pcl.utils.getSerializableSafely
import com.zalith.pcl.utils.network.openLink
import com.zalith.pcl.utils.string.throwableToString
import com.zalith.pcl.viewmodel.LogsUploadViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.parcelize.Parcelize
import java.io.File

private const val BUNDLE_EXIT_TYPE = "BUNDLE_EXIT_TYPE"
private const val BUNDLE_THROWABLE = "BUNDLE_THROWABLE"
private const val BUNDLE_JVM_CRASH = "BUNDLE_JVM_CRASH"
private const val BUNDLE_CAN_RESTART = "BUNDLE_CAN_RESTART"
private const val EXIT_JVM = "EXIT_JVM"
private const val EXIT_LAUNCHER = "EXIT_LAUNCHER"

fun showExitMessage(
    context: Context,
    code: Int,
    isSignal: Boolean,
    logPath: String,
    /**
     * 游戏「退出码为 0、但启动后很快就结束」时的运行时长（秒）。
     * 传大于等于 0 的值表示这是「没玩成」的退出，退出页会换成对应的说明文案。
     */
    earlyExitSeconds: Int = -1
) {
    val intent = Intent(context, ErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_EXIT_TYPE, EXIT_JVM)
        putExtra(BUNDLE_JVM_CRASH, JvmCrash(code, isSignal, logPath, earlyExitSeconds))
    }
    context.startActivity(intent)
}

@Parcelize
private data class JvmCrash(
    val code: Int,
    val isSignal: Boolean,
    val logPath: String,
    /** 见 [showExitMessage] 的同名参数，-1 表示常规退出 */
    val earlyExitSeconds: Int = -1
): Parcelable

@AndroidEntryPoint
class ErrorActivity : BaseAppCompatActivity() {

    /**
     * 游戏崩溃日志上传逻辑管理 ViewModel
     */
    private val viewModel: LogsUploadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras ?: return runFinish()
        extras.classLoader = javaClass.classLoader

        val exitType = extras.getString(BUNDLE_EXIT_TYPE, EXIT_LAUNCHER)

        //游戏「退出码 0 但启动后很快就结束」：文案换成「没玩成」的说法，见 VMActivity.EARLY_EXIT_SECONDS
        var earlyExitSeconds = -1

        val errorMessage = when (exitType) {
            EXIT_JVM -> {
                val jvmCrash = extras.getParcelableSafely(BUNDLE_JVM_CRASH, JvmCrash::class.java) ?: return runFinish()
                earlyExitSeconds = jvmCrash.earlyExitSeconds
                val earlyExit = earlyExitSeconds >= 0
                val message = when {
                    earlyExit -> getString(R.string.crash_early_exit_message, earlyExitSeconds)
                    jvmCrash.isSignal -> getString(R.string.crash_singnal_message, jvmCrash.code)
                    else -> getString(R.string.crash_exit_message, jvmCrash.code)
                }
                val messageBody = getString(
                    if (earlyExit) R.string.crash_early_exit_note else R.string.crash_exit_note
                )
                ErrorMessage(
                    message = message,
                    messageBody = messageBody,
                    crashType = CrashType.GAME_CRASH,
                    logFile = File(jvmCrash.logPath).also { file ->
                        //检查日志文件是否适合上传
                        viewModel.check(file)
                    }
                )
            }
            else -> {
                val throwable = extras.getSerializableSafely(BUNDLE_THROWABLE, Throwable::class.java) ?: return runFinish()
                val message = getString(R.string.crash_launcher_message)
                val messageBody = throwableToString(throwable)
                ErrorMessage(
                    message = message,
                    messageBody = messageBody,
                    crashType = CrashType.LAUNCHER_CRASH,
                    logFile = PathManager.FILE_CRASH_REPORT
                )
            }
        }

        val logFile = errorMessage.logFile
        val canRestart: Boolean = extras.getBoolean(BUNDLE_CAN_RESTART, true)
        val logExists = logFile.exists() && logFile.isFile

        /**
         * 从日志尾部捞出「疑似原因」直接摆在页面上。
         *
         * 以前退出页只有「去看日志 / 分享日志」的说明，用户很难判断到底哪一行是原因；
         * 尤其是「启动一会儿就回到启动器主页」这种连崩溃弹窗都没有的退出，更容易一头雾水。
         * 这里只读日志尾部（[GameExitDiagnostics] 内部限制读取量），不会拖慢退出页。
         */
        val suspectedCause = if (exitType == EXIT_JVM && logExists) {
            GameExitDiagnostics.findLikelyCause(logFile)
                //连报错行都没有：至少把日志末尾几行摆出来，通常能看出卡在哪一步
                ?: if (earlyExitSeconds >= 0) GameExitDiagnostics.readLastLines(logFile, 6) else null
        } else {
            null
        }

        /**
         * 「下一步怎么处理」。日志里的报错对普通玩家来说只是天书，
         * 所以这里按报错类型给一句能直接照做的建议（换渲染器 / 换 Java / 调小内存 / 禁用模组）。
         */
        val adviceRes = if (exitType == EXIT_JVM && logExists) {
            adviceTextRes(GameExitDiagnostics.findAdvice(logFile))
        } else {
            null
        }

        setContent {
            ZalithLauncherTheme {
                ShareLinkOperation(
                    operation = viewModel.operation,
                    onChange = { viewModel.operation = it },
                    onUploadChancel = { viewModel.cancel() },
                    onUpload = {
                        viewModel.upload(logFile) { link ->
                            openLink(link)
                            copyText(COPY_LABEL_LINK, link, this@ErrorActivity)
                        }
                    }
                )

                //界面风格：PCL2 界面下退出页也走 PCL 的一套窗体 / 卡片 / 按钮。
                //PclTheme 只注入调色板并钉住 density/fontScale（PCL 的尺寸全是固定 DIP），
                //Material 主题仍由外层的 ZalithLauncherTheme 提供 —— 原生分支还在用。
                val usePcl2Ui = AllSettings.usePcl2Ui.state
                val errorPage: @Composable () -> Unit = {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (usePcl2Ui) Color.Transparent else backgroundColor(),
                        contentColor = onBackgroundColor()
                    ) {
                        ErrorScreen(
                            crashType = errorMessage.crashType,
                            shareLogs = logExists,
                            canUpload = viewModel.canUpload,
                            canRestart = canRestart,
                            onShareLogsClick = {
                                if (logExists) {
                                    shareFile(this@ErrorActivity, logFile)
                                }
                            },
                            onUploadClick = {
                                viewModel.operation = ShareLinkOperation.Tip
                            },
                            onRestartClick = {
                                // 带上「崩溃前所在的启动器界面」的 Intent，把用户送回原来的地方。
                                // 见 `restartTarget()` 的说明：**一定要带 Intent**，
                                // 不带的话 `triggerRebirth` 会去查 manifest 的 LAUNCHER，
                                // 命中导入用的 activity-alias 时会掉进原生的 MainActivity。
                                ProcessPhoenix.triggerRebirth(this@ErrorActivity, restartTarget())
                            },
                            onExitClick = { finish() },
                            onOrientationChanged = {
                                this@ErrorActivity.requestedOrientation = it
                            },
                        ) {
                            if (usePcl2Ui) {
                                PclCrashBody(
                                    message = errorMessage.message,
                                    messageBody = errorMessage.messageBody,
                                    reasonTitle = getString(R.string.crash_exit_reason),
                                    suspectedCause = suspectedCause,
                                    adviceTitle = getString(R.string.crash_exit_advice_title),
                                    advice = adviceRes?.let { getString(it) }
                                )
                            } else {
                                Text(
                                    text = errorMessage.message,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = errorMessage.messageBody,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                suspectedCause?.let { cause ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = getString(R.string.crash_exit_reason),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = cause,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace
                                            )
                                        )
                                    }
                                }
                                adviceRes?.let { res ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = getString(R.string.crash_exit_advice_title),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = getString(res),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (usePcl2Ui) {
                    PclTheme(palette = PclThemeState.palette) { errorPage() }
                } else {
                    errorPage()
                }
            }
        }
    }

    /**
     * 崩溃后「重启」要回到的界面。**永远不返回 null** —— 一定要带 Intent 重启。
     *
     * 【为什么绝不能退化成不带 Intent 的 `ProcessPhoenix.triggerRebirth(context)`】
     * 那个重载会去查 manifest 的 launcher activity，而本项目曾经注册了多个 LAUNCHER：
     * `SplashActivity`、`Pcl2Activity`，外加导入整合包 / 导入控制布局的两个 activity-alias。
     * 一旦命中导入用的 alias，`SplashActivity` 会把它当成「导入整合包」处理，
     * `handleImportIntent` 直接 `startActivity(MainActivity)` —— 用户看到的就是
     * 「游戏崩溃后点重启，回到了传统 ZL 界面」。
     * （第 98 轮起桌面只留 `SplashActivity` 一个 LAUNCHER，这条歧义已基本消失，
     *  但「明确带 Intent 重启」的做法更稳，继续保留。）
     *
     * 目标来源有两层：
     *   1. [AllSettings.lastForegroundScreen]（`ZLApplication` 只记 `MainActivity` / `Pcl2Activity`
     *      这两个启动器根界面，子进程里的 `VMActivity` 不会再污染它）；
     *   2. 兜底用用户自己选的启动器界面（`AllSettings.usePcl2Ui`）——
     *      记录缺失、或是崩在了某个非启动器界面上时，回用户选的那个准没错。
     *
     * `SplashActivity` 不作为目标：它自己会重新分流一次，且会先跑依赖检查（解压页），
     * 那不是用户想看到的「重启回原处」。
     */
    private fun restartTarget(): Intent {
        val preferred = if (runCatching { AllSettings.usePcl2Ui.getValue() }.getOrDefault(true)) {
            Pcl2Activity::class.java
        } else {
            MainActivity::class.java
        }
        val name = runCatching { AllSettings.lastForegroundScreen.getValue() }.getOrNull()
        val recorded = RESTARTABLE_SCREENS.firstOrNull { it.name == name }
        val clazz = when {
            recorded == null -> preferred
            recorded == SplashActivity::class.java -> preferred
            else -> recorded
        }
        return Intent(this, clazz).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 「下一步建议」文案。返回 null 表示这条日志还没被归纳出明确的处理办法。
     *
     * 每一条都是**用户当场能操作**的动作：换渲染器、换 Java、调小内存、禁用模组。
     * 宁可少说也不要说错方向，所以没命中就不显示建议区。
     */
    @StringRes
    private fun adviceTextRes(advice: GameExitDiagnostics.Advice): Int? = when (advice) {
        GameExitDiagnostics.Advice.NONE -> null
        GameExitDiagnostics.Advice.NO_GRAPHICS_BACKEND -> R.string.crash_advice_no_graphics_backend
        GameExitDiagnostics.Advice.VULKAN_TOO_OLD -> R.string.crash_advice_vulkan_too_old
        GameExitDiagnostics.Advice.OPENGL_CONTEXT_FAILED -> R.string.crash_advice_opengl_context_failed
        GameExitDiagnostics.Advice.JAVA_TOO_OLD -> R.string.crash_advice_java_too_old
        GameExitDiagnostics.Advice.OUT_OF_MEMORY -> R.string.crash_advice_out_of_memory
        GameExitDiagnostics.Advice.MOD_MISMATCH -> R.string.crash_advice_mod_mismatch
        GameExitDiagnostics.Advice.NATIVE_CRASH -> R.string.crash_advice_native_crash
        GameExitDiagnostics.Advice.RENDERER_GPU_MISMATCH -> R.string.crash_advice_renderer_gpu_mismatch
        GameExitDiagnostics.Advice.JRE_INSTRUCTION_MISMATCH -> R.string.crash_advice_jre_instruction_mismatch
    }

    private data class ErrorMessage(        val message: String,
        val messageBody: String,
        val crashType: CrashType,
        val logFile: File
    )

    private companion object {
        /** 允许作为崩溃重启目标的界面。 */
        val RESTARTABLE_SCREENS = listOf(
            MainActivity::class.java,
            Pcl2Activity::class.java,
            SplashActivity::class.java,
        )
    }
}

/**
 * 崩溃类型
 */
enum class CrashType(val textRes: Int) {
    /**
     * 启动器崩溃
     */
    LAUNCHER_CRASH(R.string.crash_type_launcher),

    /**
     * 游戏运行崩溃
     */
    GAME_CRASH(R.string.crash_type_game)
}

/**
 * 启动软件崩溃信息页面
 */
fun showLauncherCrash(context: Context, throwable: Throwable, canRestart: Boolean = true) {
    val intent = Intent(context, ErrorActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(BUNDLE_EXIT_TYPE, EXIT_LAUNCHER)
        putExtra(BUNDLE_THROWABLE, throwable)
        putExtra(BUNDLE_CAN_RESTART, canRestart)
    }
    context.startActivity(intent)
}