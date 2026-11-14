package io.github.lazy.dsh.feature.interactive

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.data.PerformanceMode
import io.github.lazy.dsh.core.data.SettingsRepository
import io.github.lazy.dsh.core.data.ThemeMode
import io.github.lazy.dsh.core.designsystem.BtnVariant
import io.github.lazy.dsh.core.designsystem.DButton
import io.github.lazy.dsh.core.designsystem.DTopBar
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.DshTheme
import io.github.lazy.dsh.core.designsystem.EmptyNote
import io.github.lazy.dsh.core.designsystem.IconBtn
import io.github.lazy.dsh.core.interactive.InteractiveArtifacts
import io.github.lazy.dsh.core.interactive.ViewportUnits
import io.github.lazy.dsh.core.platform.AppLanguage
import javax.inject.Inject

/**
 * The sandboxed window for one AI-drawn scene. It is its own activity (not a nav destination) so a
 * tool call can open it without reaching into the conversation's back stack; closing it returns
 * exactly where the user was.
 */
@AndroidEntryPoint
class InteractiveActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    private var artifactId by mutableStateOf<String?>(null)
    private var reloadRequest by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        artifactId = intent.getStringExtra(InteractiveArtifacts.EXTRA_ID)
        setContent {
            val s by settings.settings.collectAsStateWithLifecycle(null)
            DshTheme(
                s?.theme ?: ThemeMode.System,
                brand = s?.performance ?: PerformanceMode.Default,
                frost = s?.glassBlur ?: true,
                reduceTransparency = s?.reduceTransparency ?: false,
            ) {
                val id = artifactId
                if (id == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    InteractivePage(id, reloadRequest) { finish() }
                }
            }
        }
    }

    /** A second call for the same window (e.g. the model updated the drawing) reloads in place. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(InteractiveArtifacts.EXTRA_ID)?.takeIf { it != artifactId }?.let { artifactId = it }
        reloadRequest++
    }
}

@Composable
private fun InteractivePage(id: String, reloadRequest: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val c = Dsh.colors
    var pageKey by remember(id) { mutableIntStateOf(0) }
    var viewportReady by remember(id) { mutableStateOf(false) }
    val artifact = remember(id, reloadRequest, pageKey) { InteractiveArtifacts.read(context, id) }
    val web = remember { sandboxedWebView(context) }

    DisposableEffect(web) {
        onDispose {
            web.stopLoading()
            web.destroy()
        }
    }
    LaunchedEffect(viewportReady, artifact?.html, pageKey) {
        // Load only after the view has a real height: `vh`/`%` units are resolved on the first
        // layout, and a WebView loaded at 0×0 keeps those values broken for the whole page.
        if (viewportReady) artifact?.let { html ->
            web.post {
                val prepared = withViewportUnits(html.html, web, context)
                web.loadDataWithBaseURL(BASE_URL, prepared, "text/html", "utf-8", null)
                // Some WebViews still cache a zero-height viewport from the first frame; a second
                // load after layout resolves vh/% against the real window.
                web.postDelayed({
                    if (!web.isAttachedToWindow || web.height <= 0) return@postDelayed
                    web.loadDataWithBaseURL(BASE_URL, prepared, "text/html", "utf-8", null)
                }, 180)
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DTopBar(
            title = artifact?.title ?: stringResource(R.string.interactive_default_title),
            onBack = onClose,
            glass = true,
            actions = {
                IconBtn(
                    R.drawable.dsw_refresh_outline, { pageKey++ },
                    contentDescription = stringResource(R.string.interactive_reload),
                )
                IconBtn(
                    R.drawable.dsw_close_outline, onClose,
                    contentDescription = stringResource(R.string.close),
                )
            },
        )
        Box(
            Modifier.weight(1f).fillMaxWidth().imePadding().navigationBarsPadding()
                .background(c.canvas)
                .onSizeChanged { if (it.height > 0) viewportReady = true },
        ) {
            if (artifact == null) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyNote(stringResource(R.string.interactive_error), Modifier.padding(top = 48.dp), art = R.drawable.brand_paw)
                    DButton(stringResource(R.string.close), onClose, variant = BtnVariant.Ghost)
                }
            } else {
                AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/**
 * JavaScript is the point of the window, but nothing else is: no network, no files, no content
 * providers, no new windows. A scene that tries to reach a CDN or a local file simply stays blank,
 * which the tool description warns the model about.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun sandboxedWebView(context: android.content.Context): WebView = WebView(context).apply {
    setBackgroundColor(Color.TRANSPARENT)
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
    settings.blockNetworkImage = true
    settings.cacheMode = WebSettings.LOAD_NO_CACHE
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.mediaPlaybackRequiresUserGesture = true
    settings.javaScriptCanOpenWindowsAutomatically = false
    settings.setSupportZoom(false)
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
            !isInternal(request?.url?.toString())
    }
}

internal const val BASE_URL = "https://dsh.local/"

/**
 * Load [html] with `vh`/`vw` baked to the current viewport: some WebViews otherwise keep the
 * first (0×0) layout viewport and every `vh` becomes 0, which collapses AI-written sheets.
 */
internal fun withViewportUnits(html: String, web: WebView, context: android.content.Context): String {
    val density = context.resources.displayMetrics.density.takeIf { it > 0 } ?: 1f
    val width = (web.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels) / density
    val height = (web.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels) / density
    return ViewportUnits.rewrite(html, width.toInt(), height.toInt())
}

/** Same-document navigation is fine; anything off the page (including http/https) is refused. */
private fun isInternal(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    val lower = url.lowercase()
    return lower.startsWith("about:") || lower.startsWith("data:") || lower.startsWith("blob:") ||
        lower.startsWith("javascript:") || lower.startsWith(BASE_URL) ||
        Uri.parse(url).scheme == null
}
