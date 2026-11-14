package io.github.lazy.dsh.feature.interactive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.interactive.InteractiveArtifacts
import io.github.lazy.dsh.core.interactive.InteractiveArtifact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded, directly usable chat scene. Shares the full-screen viewer's network/file sandbox. */
@Composable
fun InlineInteractive(id: String, revision: String?, modifier: Modifier = Modifier) {
    key(id, revision) { InlineScene(id, modifier) }
}

private sealed interface SceneLoad {
    data object Loading : SceneLoad
    data class Loaded(val page: InteractiveArtifact?) : SceneLoad
}

@Composable
private fun InlineScene(id: String, modifier: Modifier) {
    val context = LocalContext.current
    val state by produceState<SceneLoad>(SceneLoad.Loading, id) {
        value = SceneLoad.Loaded(withContext(Dispatchers.IO) { InteractiveArtifacts.read(context, id) })
    }
    val bounds = modifier.fillMaxWidth().height(360.dp)
    if (state == SceneLoad.Loading) {
        Box(bounds, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val page = (state as SceneLoad.Loaded).page
    if (page == null) {
        Box(bounds, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.interactive_error))
        }
        return
    }
    AndroidView(
        factory = { sandboxedWebView(it) },
        modifier = bounds,
        onRelease = { web -> web.tag = null; web.stopLoading(); web.destroy() },
        update = { web ->
            val key = page.id to page.updatedAt
            if (web.tag != key) {
                web.tag = key
                web.post {
                    if (web.tag == key && web.width > 0 && web.height > 0) {
                        web.loadDataWithBaseURL(BASE_URL, withViewportUnits(page.html, web, context), "text/html", "utf-8", null)
                    } else if (web.tag == key) {
                        web.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                            override fun onLayoutChange(v: android.view.View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                                if (r <= l || b <= t) return
                                web.removeOnLayoutChangeListener(this)
                                if (web.tag == key) web.loadDataWithBaseURL(BASE_URL, withViewportUnits(page.html, web, context), "text/html", "utf-8", null)
                            }
                        })
                    }
                }
            }
        },
    )
}
