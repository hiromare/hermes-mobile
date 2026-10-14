package com.m57.hermescontrol.ui.bots.screen

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.toJsonElement
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val SCREEN_VIRTUAL_ROOT = "mobile-bot-screen/"

/**
 * Serve only packaged assets under a virtual path of the configured dashboard origin.
 * The display WebSocket rejects foreign Origin headers, so appassets.androidplatform.net
 * cannot act as the viewer origin. Intercept all WebView resource requests instead of
 * fetching HTML/JS from the network; no server changes or Origin-policy exemptions.
 */
internal object BotScreenAssets {
    fun pageUrl(endpoint: ServerEndpoint): String = endpoint.resolve("${SCREEN_VIRTUAL_ROOT}index.html").toString()

    fun localAssetPath(
        url: String,
        endpoint: ServerEndpoint,
    ): String? {
        val root = endpoint.resolve(SCREEN_VIRTUAL_ROOT).toString()
        if (!url.startsWith(root)) return null
        val relative = url.removePrefix(root)
        if (!Regex("[A-Za-z0-9_./-]+").matches(relative)) return null
        if (relative.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return "bot_screen/$relative"
    }
}

@Composable
fun BotScreenView(
    profile: String,
    onBack: () -> Unit,
) {
    HermesScaffold(
        title = { Text("Bot Screen · $profile") },
        navigationIcon = NavIcon.Back(onBack),
        drawerGesturesEnabled = false,
    ) { padding ->
        BotScreenPanel(profile, Modifier.fillMaxSize().padding(padding))
    }
}

/** Inline viewer; can be docked above the real ChatScreen transcript and composer. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BotScreenPanel(
    profile: String,
    modifier: Modifier = Modifier,
    header: @Composable (String, BotScreenControls) -> Unit = { name, controls ->
        BotScreenHeader(profile = name, controls = controls)
    },
) {
    val context = LocalContext.current
    val displayEndpoint = remember(profile) { AuthManager.endpointForBuild() }
    val displayPageUrl = remember(displayEndpoint) { BotScreenAssets.pageUrl(displayEndpoint) }
    val scope = rememberCoroutineScope()
    var status by remember(profile) { mutableStateOf<BotScreenProtocol.Status?>(null) }
    var viewerId by remember(profile) { mutableStateOf<String?>(null) }
    var socketUrl by remember(profile) { mutableStateOf<String?>(null) }
    var ownsControl by remember(profile) { mutableStateOf(false) }
    var busy by remember(profile) { mutableStateOf(false) }
    var error by remember(profile) { mutableStateOf<String?>(null) }
    var webView by remember(profile) { mutableStateOf<WebView?>(null) }
    var viewerReady by remember(profile) { mutableStateOf(false) }
    var initialObserveAttempted by remember(profile) { mutableStateOf(false) }

    suspend fun rpc(
        method: String,
        params: Map<String, Any> = emptyMap(),
    ): Any? =
        HermesWsClient
            .request(
                method,
                params + ("profile" to profile),
                timeoutMs = 30_000L,
                suppressErrorEvent = true,
            ).await()

    fun setControl(enabled: Boolean) {
        webView
            ?.takeIf {
                viewerReady
            }?.evaluateJavascript("window.hermesSetControl(${if (enabled) "true" else "false"})", null)
    }

    suspend fun refreshStatus() {
        val newStatus = BotScreenProtocol.parseStatus(rpc("display.status"))
        status = newStatus
        // A second viewer's Take over revokes our lease. Never allow stale touches.
        if (ownsControl && !newStatus.lease.heldBy(viewerId)) {
            ownsControl = false
            setControl(false)
        }
    }

    suspend fun observe() {
        // Tickets are single-use, valid for 30 seconds, and minted per connected WS client.
        val previousId = viewerId
        val result = rpc("display.observe", previousId?.let { mapOf("viewer_id" to it) } ?: emptyMap())
        val observation = BotScreenProtocol.parseObservation(result)
        if (previousId != null && previousId != observation.viewerId && ownsControl) {
            ownsControl = false // Reconnect lost the previous client's minted viewer identity.
            setControl(false)
        }
        viewerId = observation.viewerId
        socketUrl = BotScreenProtocol.displaySocketUrl(AuthManager.endpointForBuild(), observation)
    }

    fun act(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                block()
            } catch (e: Exception) {
                error = e.message ?: "Display request failed"
            } finally {
                busy = false
            }
        }
    }

    // Keep the main WS connected while the pane is open. Preserve a human's lease
    // across transient reconnects, but hand it back when this pane is closed.
    DisposableEffect(profile) {
        HermesWsClient.acquireExternalActivityConnectionLease()
        HermesWsClient.connect()
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        onDispose {
            val heldViewer = viewerId?.takeIf { ownsControl }
            webView?.evaluateJavascript("window.hermesDisconnectScreen()", null)
            if (heldViewer != null) {
                cleanupScope.launch {
                    runCatching { rpc("display.lease.release", mapOf("viewer_id" to heldViewer)) }
                    HermesWsClient.releaseExternalActivityConnectionLease()
                }
            } else {
                HermesWsClient.releaseExternalActivityConnectionLease()
            }
        }
    }

    LaunchedEffect(profile) {
        while (isActive) {
            try {
                refreshStatus()
                if (status?.running == true && !initialObserveAttempted) {
                    initialObserveAttempted = true
                    observe()
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to open Bot Screen"
            }
            delay(3_000L)
        }
    }

    // Loading is gated on page-ready: no ticket can be spent before the local JS module exists.
    LaunchedEffect(socketUrl, viewerReady, webView) {
        val url = socketUrl ?: return@LaunchedEffect
        if (!viewerReady) return@LaunchedEffect
        webView?.evaluateJavascript("window.hermesConnectScreen(${JSONObject.quote(url)})", null)
        setControl(ownsControl)
    }

    val current = status
    val controls =
        BotScreenControls(
            running = current?.running == true,
            loading = current == null,
            busy = busy,
            ownsControl = ownsControl,
            viewerOccupied = current?.lease?.holder == "human" && !ownsControl,
            error = error,
            unavailable =
                when {
                    current == null -> null
                    !current.supported -> "Bot Screen unavailable"
                    !current.installed -> current.installCommand ?: current.missing.joinToString()
                    !current.running -> current.blocker ?: "Bot Screen stopped"
                    else -> null
                },
            onReconnect = { act { observe() } },
            onStart = {
                act {
                    status = BotScreenProtocol.parseStatus(rpc("display.start"))
                    observe()
                }
            },
            onToggleControl = {
                act {
                    val id = viewerId ?: kotlin.error("No viewer ID")
                    if (ownsControl) {
                        val response = rpc("display.lease.release", mapOf("viewer_id" to id)).toJsonObject()
                        val lease = BotScreenProtocol.parseLease(response["lease"])
                        ownsControl = lease.heldBy(id)
                        setControl(false)
                    } else {
                        val lease =
                            BotScreenProtocol.parseLease(
                                rpc("display.lease.acquire", mapOf("viewer_id" to id)).toJsonObject()["lease"],
                            )
                        ownsControl = lease.heldBy(id)
                        setControl(ownsControl)
                    }
                    refreshStatus()
                }
            },
        )
    Column(modifier = modifier.fillMaxWidth()) {
        header(profile, controls)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surface)) {
            if (current?.running == true) {
                AndroidView(
                    factory = {
                        WebView(context).apply {
                            setBackgroundColor(Color.rgb(28, 28, 32))
                            settings.javaScriptEnabled = true
                            settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                            settings.domStorageEnabled = false
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.setSupportMultipleWindows(false)
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            webViewClient =
                                object : WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): WebResourceResponse? {
                                        val path =
                                            request?.url?.toString()?.let {
                                                BotScreenAssets.localAssetPath(
                                                    it,
                                                    displayEndpoint,
                                                )
                                            }
                                                ?: return WebResourceResponse(
                                                    "text/plain",
                                                    "UTF-8",
                                                    403,
                                                    "Forbidden",
                                                    emptyMap(),
                                                    ByteArrayInputStream(ByteArray(0)),
                                                )
                                        val mime =
                                            when {
                                                path.endsWith(".html") -> "text/html"
                                                path.endsWith(".js") -> "text/javascript"
                                                path.endsWith(".css") -> "text/css"
                                                else -> "application/octet-stream"
                                            }
                                        return try {
                                            WebResourceResponse(mime, "UTF-8", context.assets.open(path))
                                        } catch (_: Exception) {
                                            WebResourceResponse(
                                                "text/plain",
                                                "UTF-8",
                                                404,
                                                "Not Found",
                                                emptyMap(),
                                                ByteArrayInputStream(ByteArray(0)),
                                            )
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean = request?.url?.toString() != displayPageUrl

                                    override fun onPageFinished(
                                        view: WebView?,
                                        url: String?,
                                    ) {
                                        if (url == displayPageUrl) {
                                            viewerReady = true
                                        }
                                    }
                                }
                            webView = this
                            loadUrl(displayPageUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag("bot_screen_viewer"),
                )
            } else {
                if (current == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag("bot_screen_loading"))
                } else {
                    Text(
                        controls.unavailable ?: "Screen not running",
                        modifier = Modifier.align(Alignment.Center).padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun Any?.toJsonObject(): kotlinx.serialization.json.JsonObject =
    this.toJsonElement() as? kotlinx.serialization.json.JsonObject
        ?: kotlin.error("Unexpected screen response")
