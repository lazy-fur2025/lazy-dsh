package io.github.lazy.dsh.core.platform

/**
 * A one-line bubble beside the floating ball, for things that are not an agent turn: a class
 * reminder, a heads-up that has no conversation behind it. The ball service attaches itself while it
 * runs, so anything that has something to say (TimetableReminders) only needs this object and never
 * depends on the service.
 */
object BallNotice {
    /** Null while the ball is not running: nobody to show it to. */
    @Volatile private var sink: ((String) -> Unit)? = null

    /** Called by the ball service when it starts; the callback runs on the main thread. */
    fun attach(show: (String) -> Unit) { sink = show }

    fun detach() { sink = null }

    /** Shows [text] beside the ball for a few seconds. A no-op when the ball is off. */
    fun show(text: String) {
        if (text.isBlank()) return
        val s = sink
        android.util.Log.i("BallNotice", if (s == null) "show ignored (ball not running): $text" else "show: $text")
        s?.invoke(text)
    }

    val isAttached: Boolean get() = sink != null
}
