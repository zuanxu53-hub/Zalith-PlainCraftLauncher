/*
 * Zalith Launcher 2 — PCL2 UI port
 *
 * 启动器内置浏览器。
 *
 * PCL2 登录微软账户时用的是**设备码流程**：弹一个 `MyMsgLogin` 对话框显示验证码，
 * 验证地址则交给系统浏览器（`ModBase.OpenWebsite`）。手机上跳到系统浏览器意味着离开
 * 启动器，登录完了还得手动切回来，所以这里改成在启动器**内部**开一个网页层
 * （等价于 PCL 自己的 `MyWebBrowser` 内嵌窗口），关掉它就算退出登录。
 *
 * 同一组件也用来承载 PCL 的「自定义主页 → 联网更新」（`UiCustomType = 2`）。
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zalith.pcl.ui.pcl2.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalith.pcl.ui.pcl2.data.PclSkinSettings
import androidx.compose.ui.viewinterop.AndroidView
import com.zalith.pcl.ui.pcl2.theme.PclDimens
import com.zalith.pcl.ui.pcl2.theme.PclTextStyle
import com.zalith.pcl.ui.pcl2.theme.pclPalette

/** 内置浏览器要打开的一页。 */
data class PclWebPage(
    val url: String,
    /** 顶部显示的名字；为空就显示「内置浏览器」。 */
    val title: String? = null,
)

/**
 * 内置浏览器浮层：铺满整个启动器窗口，上方一条 PCL 风格的工具栏 + 网页本体。
 *
 * 关闭方式：按返回键（网页还能后退时先后退）、点右上角的「关闭」。
 */
@Composable
fun PclWebOverlay(
    page: PclWebPage,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = pclPalette
    val context = LocalContext.current
    var webView by remember(page) { mutableStateOf<WebView?>(null) }
    var currentUrl by remember(page) { mutableStateOf(page.url) }
    var loading by remember(page) { mutableStateOf(true) }
    // 内置浏览器把微软登录页跑挂时只交接一次，避免系统浏览器和内置页互相拉起
    var handedOffToBrowser by remember(page) { mutableStateOf(false) }
    // 微软授权完成页会停在白屏；识别到「已完成」文案后自动关掉这一层
    var autoClosed by remember(page) { mutableStateOf(false) }

    // 有网页时返回键先交给网页历史，退无可退才关掉这一层 ——
    // 与 Zalith 原生界面「退出网页页 = 退出登录」的语义一致。
    BackHandler(enabled = true) {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x77000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(PclDimens.formCorner))
                .background(p.sidebarBackground)
                .border(1.dp, p.color6, RoundedCornerShape(PclDimens.formCorner)),
        ) {
            // ---- 工具栏（PCL 的 MyMsgLogin 标题行风格：标题 15 号 + 右侧按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(p.color7.copy(alpha = 0.62f))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PclText(
                    text = page.title ?: "内置浏览器",
                    color = p.color1,
                    fontSize = 15,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = currentUrl,
                    style = PclTextStyle.base(p).copy(color = p.gray2, fontSize = 11.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                // 网页在 WebView 里跑不动时（微软登录页尤其常见）一键交给系统浏览器
                PclTextButton(text = "浏览器打开") {
                    val url = currentUrl.ifBlank { page.url }
                    if (!openInSystemBrowser(context, url)) {
                        Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
                    }
                }
                Spacer(Modifier.width(14.dp))
                PclTextButton(text = "刷新") { webView?.reload() }
                Spacer(Modifier.width(14.dp))
                PclTextButton(text = "关闭") { onClose() }
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = p.color3,
                    trackColor = p.color7,
                )
            } else {
                Spacer(Modifier.height(3.dp))
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        configureLauncherWebView()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { currentUrl = it }
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { currentUrl = it }
                                loading = false

                                // 微软的设备码验证页在 WebView 里偶发「接力丢参数」：
                                // 页面会跳到 login.live.com/oauth20_authorize.srf?res=error&c=…&uaid=…，
                                // 内容是「我们无法完成你的请求 / invalid_request … client_id」。
                                // 这一页靠 JS + 表单二次提交，WebView 里出问题基本没法自己恢复，
                                // 系统浏览器没有这个毛病 —— 直接把入口页再交给它，用户还能接着登。
                                if (!handedOffToBrowser && url != null && isMicrosoftLoginFailurePage(url)) {
                                    handedOffToBrowser = true
                                    val opened = openInSystemBrowser(context, page.url)
                                    Toast.makeText(
                                        context,
                                        if (opened) "内置浏览器登录失败，已改用系统浏览器打开验证页"
                                        else "内置浏览器登录失败，请点右上角「浏览器打开」重试",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    // 交接成功后把白屏层收掉，别让用户困在这里
                                    if (opened) view?.postDelayed({ onClose() }, 900)
                                }

                                // 微软的设备码授权成功页是一张没有交互的静态页，
                                // 在 WebView 里经常只剩白底。读到「已完成」文案就直接
                                // 关闭浮层；令牌轮询在后台继续，关页不会再取消登录。
                                if (!autoClosed && url != null && isMicrosoftLoginPage(url)) {
                                    view?.evaluateJavascript(
                                        "(document.body && document.body.innerText) || ''"
                                    ) { raw ->
                                        if (!autoClosed && isMicrosoftLoginSuccessText(raw)) {
                                            autoClosed = true
                                            Toast.makeText(
                                                context,
                                                "微软授权已完成，正在返回启动器…",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            view?.postDelayed({ onClose() }, 900)
                                        }
                                    }
                                }
                            }
                        }
                        loadUrl(page.url)
                        webView = this
                    }
                },
            )
        }
    }

    DisposableEffect(page) {
        onDispose {
            webView?.disposeWebView()
            webView = null
        }
    }
}

/**
 * 内嵌网页（无工具栏）：给「自定义主页 → 联网更新」用。
 *
 * [transparent] 打开后网页不刷自己的底色，启动器的「自定义背景」能从底下透上来
 * （PCL 的 `PanCustom` 也是把网页当透明层铺在背景上的）。
 */
@Composable
fun PclInlineWeb(
    url: String,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
) {
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    configureLauncherWebView(transparent)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url2: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url2, favicon)
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url2: String?) {
                            super.onPageFinished(view, url2)
                            loading = false
                            // 透明 WebView 只是让「画布底色」透明，网页自己的
                            // `body{background:#fff}` 仍然会铺满 —— 主页网站基本都写了这一条，
                            // 所以要顺手把它也清掉，否则用户只看到半透明的白。
                            if (transparent) view?.clearPageBackground()
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
        )
        if (loading) {
            PclText(text = "正在加载自定义主页…", color = pclPalette.gray2)
        }
    }

    DisposableEffect(url) {
        onDispose {
            webView?.disposeWebView()
            webView = null
        }
    }
}

private fun WebView.disposeWebView() {
    runCatching {
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
    }
}

/**
 * 渲染已经转成 HTML 的主页内容（PCL 主页 XAML 的转换结果 / 内置预设）。
 *
 * 用 `loadDataWithBaseURL` 而不是 `loadUrl`：HTML 是转换器现生成的字符串，
 * 没有 URL 可加载；baseURL 给 `https://localhost/`，让页面里的相对链接
 * 与脚本按普通网页处理，同时保持与 [PclInlineWeb] 一致的加载动画。
 */
@Composable
fun PclInlineHtml(
    html: String,
    modifier: Modifier = Modifier,
    /** 主页里的内部按钮（启动游戏 / 下载文件 / 刷新主页…）回传给宿主处理。 */
    onEvent: ((type: String, data: String) -> Unit)? = null,
    /** 见 [PclInlineWeb]：转换出来的主页 `body` 本来就是透明的，WebView 画布也必须透明。 */
    transparent: Boolean = false,
) {
    // 「界面缩放」在 Compose 里是靠乘 density 实现的，WebView 是原生 View 吃不到，
    // 所以这里把缩放系数用 CSS `zoom` 注进页面，让字号、卡片、图片**整体**一起缩放。
    val config = LocalConfiguration.current
    val scale = PclSkinSettings.resolveScale(
        config.screenWidthDp.toFloat(),
        config.screenHeightDp.toFloat(),
    )
    val scaledHtml = remember(html, scale) {
        if (kotlin.math.abs(scale - 1f) < 0.01f) {
            html
        } else {
            val zoom = String.format(java.util.Locale.ROOT, "%.3f", scale)
            html.replace("</style>", "body{zoom:$zoom;}</style>")
        }
    }

    var webView by remember(scaledHtml) { mutableStateOf<WebView?>(null) }
    var loading by remember(scaledHtml) { mutableStateOf(true) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    configureLauncherWebView(transparent)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url2: String?) {
                            super.onPageFinished(view, url2)
                            loading = false
                        }

                        /**
                         * 主页里的按钮/链接统一在这里分流：
                         * - `pclhome://event?...` → PCL 的内部事件（启动游戏 / 下载文件 / 刷新主页），
                         *   解析后回传给 Compose；
                         * - `http(s)` → 系统浏览器打开，**不把主页本身导航走**。
                         *
                         * 两个签名都要 override：API 24+ 只会调用带 [WebResourceRequest] 的新签名，
                         * 只写旧的（deprecated）那个的话，真机上点击会直接落到 WebView 自己的导航里。
                         */
                        private fun handle(url: String?, view: WebView?): Boolean {
                            if (url.isNullOrBlank()) return false
                            if (url.startsWith("pclhome://", ignoreCase = true)) {
                                val uri = Uri.parse(url)
                                onEvent?.invoke(
                                    uri.getQueryParameter("type").orEmpty(),
                                    uri.getQueryParameter("data").orEmpty(),
                                )
                                return true
                            }
                            if (url.startsWith("http://", ignoreCase = true) ||
                                url.startsWith("https://", ignoreCase = true)
                            ) {
                                runCatching {
                                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                                return true
                            }
                            return false
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean = handle(request?.url?.toString(), view)

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                            handle(url, view)
                    }
                    loadDataWithBaseURL("https://localhost/", scaledHtml, "text/html", "utf-8", null)
                    webView = this
                }
            },
        )
        if (loading) {
            PclText(text = "正在加载自定义主页…", color = pclPalette.gray2)
        }
    }

    DisposableEffect(scaledHtml) {
        onDispose {
            webView?.disposeWebView()
            webView = null
        }
    }
}

/**
 * 把链接交给系统浏览器（Chrome / Edge / 厂商自带）。
 *
 * @return 是否真的拉起了某个浏览器（没有浏览器时 `startActivity` 会抛
 *         `ActivityNotFoundException`，这里吞掉并返回 false）
 */
private fun openInSystemBrowser(context: Context, url: String): Boolean {
    if (url.isBlank()) return false
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.getOrDefault(false)
}

/**
 * 是不是微软登录的「失败页」。
 *
 * `login.live.com/oauth20_authorize.srf` 少参数时会重定向到自己并带上
 * `res=error&c=<错误码>&uaid=<会话>`，页面内容是
 * 「我们无法完成你的请求 / invalid_request: The provided request must include a 'client_id'」。
 * 正常登录流程根本不会出现这一页，所以见到就说明这一步已经废了。
 */
private fun isMicrosoftLoginFailurePage(url: String): Boolean =
    url.contains("login.live.com/oauth20_authorize.srf") && url.contains("res=error")

/** 当前页是否属于微软登录域（只有这些页面才需要读「授权完成」文案）。 */
private fun isMicrosoftLoginPage(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    return host.endsWith("microsoftonline.com") ||
        host.endsWith("login.live.com") ||
        host.endsWith("microsoft.com")
}

/** 微软设备码授权完成页的文案（中英文）。 */
private fun isMicrosoftLoginSuccessText(raw: String): Boolean {
    val text = raw.replace("\\u0027", "'").lowercase()
    return text.contains("you're all set") ||
        text.contains("you’re all set") ||
        text.contains("you can close this window") ||
        text.contains("你可以关闭此窗口") ||
        text.contains("你可以关闭这个窗口") ||
        text.contains("已完成登录")
}

/**
 * 把网页自己的底色清掉，让启动器的自定义背景透上来。
 *
 * 只对内嵌主页用：网页（尤其是 mcnews 这类站点）几乎都会给 `html/body` 写死
 * 一个白底，光把 WebView 画布设成透明是不够的。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun WebView.clearPageBackground() {
    runCatching {
        evaluateJavascript(
            "(function(){" +
                "var s=document.createElement('style');" +
                "s.setAttribute('data-zalith','1');" +
                "s.textContent='html,body{background-color:transparent !important;background-image:none !important;}';" +
                "(document.head||document.documentElement).appendChild(s);" +
                "})()",
            null,
        )
    }
}

/**
 * 启动器内置浏览器的统一设置。
 *
 * 两个要点：
 * - **去掉 UA 里的 `; wv`**：微软账户的登录页会识别 WebView 并拒绝服务
 *   （「此浏览器或应用可能不安全」），去掉这个标记后按普通 Chrome 处理。
 * - **允许第三方 Cookie**：`login.microsoftonline.com` → `login.live.com` 是跨站跳转，
 *   Android 的 WebView 从 API 30 起默认拦截第三方 Cookie，不开会卡在登录页。
 */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun WebView.configureLauncherWebView(transparent: Boolean = false) {
    // 主页那一层必须透明：WebView 默认把画布刷成白色，用户设的「自定义背景」
    // （`PCL/Background` 里的图 / 彩色背景）就被整块盖住，看上去像背景变白了。
    setBackgroundColor(
        if (transparent) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE
    )
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        userAgentString = userAgentString.replace("; wv", "")
        // 「自定义主页 → 读取本地文件」是 file:// 加载，需要放开文件访问，
        // 否则页面里的相对资源（css / 图片）会被拦掉。
        allowFileAccess = true
        allowContentAccess = true
        allowFileAccessFromFileURLs = true
        allowUniversalAccessFromFileURLs = true
    }
    runCatching {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@configureLauncherWebView, true)
        }
    }
}
