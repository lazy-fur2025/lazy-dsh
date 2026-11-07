package io.github.lazy.dsh.core.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Pages that are not part of the app's own flows (GitHub releases, a provider's privacy policy, …)
 * open in the user's browser as its own task; the app no longer has an in-app Platform WebView.
 */
object Browser {
    /** Opens [url] (http/https) in the default browser; false when nothing could handle it. */
    fun openExternal(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { context.startActivity(intent); true } catch (e: ActivityNotFoundException) {
            Log.w("Browser", "no browser for $url", e); false
        }
    }
}
