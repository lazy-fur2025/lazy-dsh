package io.github.lazy.dsh.feature.launch

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.lazy.dsh.R
import io.github.lazy.dsh.core.designsystem.Dsh
import io.github.lazy.dsh.core.designsystem.LocalReducedMotion
import io.github.lazy.dsh.core.designsystem.Motion
import io.github.lazy.dsh.core.designsystem.modeAccent
import kotlinx.coroutines.delay

/**
 * 冷启动片头, 两个调色版本 (`opus-video/dsh-splash/out/final.mp4` / `final-dark.mp4`,
 * 1080×2372, 2.4 s). Each is graded for its own canvas — light starts on mint (#D1E6C6) and ends
 * on #F5F7FC, dark starts on #0B0C10 and ends on #0B0C10 — so the system splash, the film and the
 * home page all share the same first and last colour.
 *
 * 降低动态效果 still falls back to the quiet variant: the emblem on the canvas with only the halo
 * rings turning (15°/s), which is the calm version of the same beat.
 *
 * The intro is an overlay above the real app, never a gate: the home page is already composed
 * underneath, so the engine and the first frame are never delayed by it. It leaves as soon as
 * [ready] (settings loaded, navigation placed) and at least the intro's own beat have happened.
 */
@Composable
fun LaunchIntro(ready: Boolean, onFinished: () -> Unit) {
    val c = Dsh.colors
    val reduced = LocalReducedMotion.current
    val film = !reduced

    // "The one beat" is done: the film ended, or the quiet version played its fade-in.
    var beatDone by remember { mutableStateOf(!film) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(ready, beatDone) { if (ready && beatDone) leaving = true }
    val fade by animateFloatAsState(
        if (leaving) 0f else 1f,
        tween(if (reduced) 0 else 340, easing = Motion.standard),
        label = "introFade",
    )
    LaunchedEffect(leaving, fade) { if (leaving && fade == 0f) onFinished() }

    val skip = { beatDone = true }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { skip() },
    ) {
        if (film) {
            SplashFilm(dark = c.isDark, onEnd = { beatDone = true })
            // A finished VideoView can blink to black between the last frame and the app; hold the
            // film's own last frame (same 1080×2372 grading) instead.
            if (beatDone) Image(
                painterResource(if (c.isDark) R.drawable.splash_still_dark else R.drawable.splash_still), null,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
            )
        } else {
            QuietIntro(animate = !reduced)
            LaunchedEffect(Unit) { delay(900); beatDone = true }
        }
    }
}

@Composable
private fun SplashFilm(dark: Boolean, onEnd: () -> Unit) {
    val end by rememberUpdatedState(onEnd)
    val raw = if (dark) R.raw.splash_dark else R.raw.splash
    val floor = if (dark) Color(0xFF0B0C10) else Color(0xFFD1E6C6)
    Box(Modifier.fillMaxSize().background(floor)) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/$raw"))
                    setOnPreparedListener { mp ->
                        mp.setVolume(0f, 0f)
                        mp.isLooping = false
                        start()
                    }
                    setOnCompletionListener { end() }
                }
            },
            // 9:19.77 film on a 9:19.5…21 screen: a whisker of overscan hides the letterbox bars.
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.03f; scaleY = 1.03f },
        )
    }
}

/** The calm variant: the emblem on the canvas, halo rings turning at 15°/s (one turn per 24 s). */
@Composable
internal fun QuietIntro(animate: Boolean) {
    val c = Dsh.colors
    var entered by remember { mutableStateOf(!animate) }
    val t by animateFloatAsState(if (entered) 1f else 0f, tween(if (animate) 640 else 0, easing = Motion.enter), label = "quietIn")
    LaunchedEffect(Unit) { entered = true }
    // 15°/s: one turn per 24 s. The only thing that keeps moving once the emblem has landed.
    val spin by rememberInfiniteTransition(label = "quietTurn").animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    val rotation = if (animate) spin else 0f
    val goldAccent = c.modeAccent(io.github.lazy.dsh.core.data.PerformanceMode.Electric)
    val leafAccent = c.modeAccent(io.github.lazy.dsh.core.data.PerformanceMode.Leaf)

    Box(Modifier.fillMaxSize().background(c.canvas), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(250.dp).graphicsLayer {
                alpha = t
                scaleX = 0.92f + 0.08f * t
                scaleY = 0.92f + 0.08f * t
                rotationZ = rotation
            },
        ) {
            val r = size.minDimension / 2f
            // Tight around the emblem, like the film: the ring is ~1.15× the mark, not a big hoop.
            val ring = r * 0.60f
            drawCircle(
                Brush.radialGradient(
                    0f to goldAccent.copy(alpha = if (c.isDark) 0.16f else 0.10f),
                    0.55f to leafAccent.copy(alpha = if (c.isDark) 0.08f else 0.05f),
                    1f to Color.Transparent,
                ),
                r,
            )
            val gold = Color(0xFFF5B324)
            val green = Color(0xFF5CA83C)
            drawCircle(Color.White.copy(alpha = if (c.isDark) 0.16f else 0.55f), ring, style = Stroke(0.7.dp.toPx()))
            drawCircle(c.labelPrimary.copy(alpha = if (c.isDark) 0.10f else 0.06f), ring * 1.12f, style = Stroke(0.5.dp.toPx()))
            // Gold quarter-arc on the left, green on the right: the two halves of the emblem.
            drawArc(gold, -168f, 104f, false, Offset(center.x - ring, center.y - ring), Size(ring * 2, ring * 2),
                style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
            drawArc(green, 16f, 104f, false, Offset(center.x - ring, center.y - ring), Size(ring * 2, ring * 2),
                style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        }
        Image(
            painterResource(R.drawable.brand_emblem), null,
            Modifier.size(132.dp).graphicsLayer {
                alpha = t
                scaleX = 0.90f + 0.10f * t
                scaleY = 0.90f + 0.10f * t
            },
            contentScale = ContentScale.Fit,
        )
    }
}
