package io.hkmario.monologue.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.hkmario.monologue.domain.IntroTimeline
import io.hkmario.monologue.domain.synthesizeIntroJingle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.*

// The logo's own colours (res/drawable/ic_monologue.xml) and the app's ink colour.
private val IntroPaper = Color(0xFFF5EFE4)
private val IntroRecord = Color(0xFF171717)
private val IntroLabel = Color(0xFFA74932)
private val IntroInk = Color(0xFF2E2722)
private val IntroGroove = Color(0xFF35302B)

/**
 * Fixed, seeded irregularities that make the intro look printed and drawn by hand: paper grain and fibres, a record
 * edge and grooves that are not perfectly round, and ink that bleeds from the label. The same every run.
 */
class IntroTexture(seed: Long = 48) {
    private val random = java.util.Random(seed)
    private fun wobble(samples: Int, amount: Float) = FloatArray(samples) { 1f + (random.nextFloat() - 0.5f) * 2 * amount }.let { raw ->
        // Smooth neighbouring samples so the outline undulates instead of jittering.
        FloatArray(samples) { i -> (raw[(i - 1 + samples) % samples] + 2 * raw[i] + raw[(i + 1) % samples]) / 4 }
    }
    /** Grain positions in 0..1 of the screen; dark specks and light specks. */
    val grainDark = List(2600) { Offset(random.nextFloat(), random.nextFloat()) }
    val grainLight = List(1400) { Offset(random.nextFloat(), random.nextFloat()) }
    /** Short paper fibres: start, direction angle and length, in screen fractions. */
    val fibres = List(70) { floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat() * 2 * PI.toFloat(), 0.01f + random.nextFloat() * 0.03f) }
    val recordEdge = wobble(48, 0.005f)
    val labelEdge = wobble(24, 0.008f)
    val grooves = List(3) { wobble(48, 0.008f) }
    /** IntroInk bleed around the label: angle, size (logo units) and how far out it reaches. */
    val bleed = List(10) { floatArrayOf(random.nextFloat() * 2 * PI.toFloat(), 0.12f + random.nextFloat() * 0.18f, 0.01f + random.nextFloat() * 0.025f) }
    /** IntroLabel specks thrown out when the hole is punched: angle and speed. */
    val flecks = List(7) { floatArrayOf(random.nextFloat() * 2 * PI.toFloat(), 0.6f + random.nextFloat() * 0.8f) }

    private var cachedSize = Size.Zero
    private var dark = emptyList<Offset>(); private var light = emptyList<Offset>()
    fun grainAt(size: Size): Pair<List<Offset>, List<Offset>> {
        if(size != cachedSize) {
            cachedSize = size
            dark = grainDark.map { Offset(it.x * size.width, it.y * size.height) }
            light = grainLight.map { Offset(it.x * size.width, it.y * size.height) }
        }
        return dark to light
    }
}

private fun ramp(s: Float, from: Float, to: Float) = ((s - from) / (to - from)).coerceIn(0f, 1f)
private fun easeOut(p: Float) = 1 - (1 - p) * (1 - p) * (1 - p)
private fun easeInOut(p: Float) = if(p < 0.5f) 4 * p * p * p else 1 - (-2 * p + 2).pow(3) / 2

/**
 * A closed outline around [center] whose radius follows [edge] (1 = [radius]), turned by [turn] radians. The few edge
 * samples are blended smoothly over 240 points, so the outline undulates without corners.
 */
private fun wobblyCircle(center: Offset, radius: Float, edge: FloatArray, turn: Float, sweep: Float = 1f): Path {
    val path = Path()
    val full = 240
    val steps = (full * sweep).roundToInt().coerceAtLeast(2)
    for(i in 0..steps) {
        val a = turn + i.toFloat() / full * 2 * PI.toFloat()
        val pos = i.toFloat() / full * edge.size
        val j = pos.toInt(); val f = pos - j
        val blend = (1 - cos(f * PI.toFloat())) / 2
        val r = radius * (edge[j % edge.size] * (1 - blend) + edge[(j + 1) % edge.size] * blend)
        val p = Offset(center.x + r * cos(a), center.y + r * sin(a))
        if(i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    if(sweep >= 1f) path.close()
    return path
}

/**
 * Draws the whole intro frame for [t] in 0..1 (0 s to [IntroTimeline.DURATION]). This is the only function that draws
 * the intro: the screen, the frame checks and every animation frame all call it.
 */
fun DrawScope.renderIntro(t: Float, texture: IntroTexture, text: TextMeasurer) {
    val k = IntroTimeline
    val s = t.coerceIn(0f, 1f) * k.DURATION
    val w = size.width; val h = size.height
    val fade = 1f - easeInOut(ramp(s, k.FADE_START, k.DURATION))
    if(fade <= 0f) return
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = fade })

    // IntroPaper, grain, fibres and a soft vignette.
    drawRect(IntroPaper)
    val paperIn = ramp(s, 0f, k.PAPER_END)
    val (dark, light) = texture.grainAt(size)
    val speck = 1.4f * density
    drawPoints(dark, PointMode.Points, IntroInk.copy(alpha = 0.10f * paperIn), strokeWidth = speck, cap = StrokeCap.Round)
    drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.35f * paperIn), strokeWidth = speck, cap = StrokeCap.Round)
    texture.fibres.forEach { (x, y, a, len) ->
        val start = Offset(x * w, y * h); val l = len * min(w, h)
        drawLine(IntroInk.copy(alpha = 0.05f * paperIn), start, Offset(start.x + l * cos(a), start.y + l * sin(a)), strokeWidth = 0.8f * density, cap = StrokeCap.Round)
    }
    drawRect(Brush.radialGradient(listOf(Color.Transparent, IntroInk.copy(alpha = 0.07f * paperIn)), Offset(w / 2, h / 2), max(w, h) * 0.75f))

    // The logo lives on a 48-unit grid, as in ic_monologue.xml; it lifts and shrinks a little for the wordmark.
    val lift = easeInOut(ramp(s, k.LIFT_START, k.LIFT_END))
    val u = min(w, h) * 0.62f / 48f * (1f - 0.18f * lift)
    val home = Offset(w / 2, h / 2 - lift * h * 0.08f)
    val recordR = 19f * u

    // Drop with gravity, then one small bounce.
    val dropP = ramp(s, k.DROP_START, k.LAND)
    val bounce = ramp(s, k.LAND, k.SETTLE)
    // Falls from high on the screen, never from outside it, so the logo is there from the first frame.
    val fallY = if(s < k.LAND) -(1 - dropP * dropP) * h * 0.3f else -0.06f * recordR * sin(PI.toFloat() * bounce) * (1 - bounce)
    val impact = if(s >= k.LAND) exp(-(s - k.LAND) * 22f) else 0f
    val center = Offset(home.x, home.y + fallY)
    val spin = if(s < k.SETTLE) 0f else { val dt = s - k.SETTLE; val ramp = min(dt, 0.5f); (0.55f * ramp * ramp + 0.55f * 2 * 0.5f * max(0f, dt - 0.5f)) * 2 * PI.toFloat() }
    if(s >= k.DROP_START) {
        // A soft shadow that firms up as the record comes down.
        val near = if(s < k.LAND) dropP * dropP else 1f
        drawCircle(Brush.radialGradient(listOf(IntroInk.copy(alpha = 0.22f * near), Color.Transparent), Offset(home.x, home.y + recordR * 0.06f), recordR * 1.12f), recordR * 1.12f, Offset(home.x, home.y + recordR * 0.06f))
        withTransform({ scale(1 + 0.05f * impact, 1 - 0.05f * impact, Offset(center.x, center.y + recordR)) }) {
            val record = wobblyCircle(center, recordR, texture.recordEdge, spin)
            drawPath(record, IntroRecord)

            // Grooves drawn one ring at a time, like ink following the needle.
            floatArrayOf(16.2f, 13.6f, 11.0f).forEachIndexed { i, r ->
                val p = easeOut(ramp(s, k.GROOVES[i], k.GROOVES[i] + k.GROOVE_DRAW))
                if(p > 0f) {
                    drawPath(wobblyCircle(center, r * u, texture.grooves[i], spin - PI.toFloat() / 2, p), IntroGroove, style = Stroke(0.45f * u, cap = StrokeCap.Round))
                    if(p < 1f) {
                        val a = spin - PI.toFloat() / 2 + p * 2 * PI.toFloat()
                        drawCircle(Color.White.copy(alpha = 0.55f * (1 - p)), 0.5f * u, Offset(center.x + r * u * cos(a), center.y + r * u * sin(a)))
                    }
                }
            }

            // A highlight sweeping across the record right after the punch.
            val sheen = ramp(s, k.PUNCH, k.PUNCH + 0.4f)
            if(sheen > 0f && sheen < 1f) clipPath(record) {
                val x = center.x - recordR * 1.6f + sheen * recordR * 3.2f
                drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), Offset(x - recordR * 0.5f, center.y - recordR), Offset(x + recordR * 0.5f, center.y + recordR)))
            }

            // The label comes down, hits (squash and wobble), and its ink bleeds a little into the paper of the label.
            if(s >= k.STAMP_START) {
                val down = ramp(s, k.STAMP_START, k.STAMP)
                val dt = s - k.STAMP
                val labelScale = if(dt < 0) 1.4f - 0.4f * down * down else 1 - 0.08f * exp(-dt * 18) * cos(dt * 40)
                val labelAlpha = if(dt < 0) down else 1f
                // A hair of print mis-registration, turning with the record so the spin reads.
                val offset = Offset(0.18f * u * cos(spin + 0.7f), 0.18f * u * sin(spin + 0.7f))
                val labelCenter = center + offset
                val spread = ramp(dt, 0f, 0.3f)
                if(dt >= 0) {
                    drawCircle(IntroLabel.copy(alpha = 0.10f * spread), 7f * u * (1 + 0.04f * spread), labelCenter)
                    texture.bleed.forEach { (a, r, reach) ->
                        val d = 7f * u * (1 + reach * spread)
                        drawCircle(IntroLabel.copy(alpha = 0.35f), r * u * spread, Offset(labelCenter.x + d * cos(a + spin), labelCenter.y + d * sin(a + spin)))
                    }
                }
                drawPath(wobblyCircle(labelCenter, 7f * u * labelScale, texture.labelEdge, spin), IntroLabel.copy(alpha = labelAlpha))
            }

            // The spindle hole punches through to the paper; a few label specks fly off.
            if(s >= k.PUNCH) {
                val dt = s - k.PUNCH
                val holeR = 2f * u * easeOut(ramp(dt, 0f, 0.08f)) * (1 + 0.15f * exp(-dt * 12) * sin(dt * 35))
                drawCircle(IntroPaper, holeR, center)
                val fly = ramp(dt, 0f, 0.25f)
                if(fly < 1f) texture.flecks.forEach { (a, speed) ->
                    val d = (2f + speed * 6f * fly) * u
                    drawCircle(IntroLabel.copy(alpha = 1 - fly), 0.35f * u, Offset(center.x + d * cos(a), center.y + d * sin(a)))
                }
            }
        }
    }

    // The wordmark writes in under the lifted record, followed by a hand-drawn terracotta underline.
    val write = easeInOut(ramp(s, k.LIFT_START + 0.1f, k.LIFT_END))
    if(write > 0f) {
        val layout = text.measure("Monologue", TextStyle(fontFamily = FontFamily.Serif, fontSize = (min(w, h) * 0.105f).toSp(), color = IntroInk))
        val top = Offset(w / 2 - layout.size.width / 2f, home.y + recordR + min(w, h) * 0.07f)
        clipRect(top.x - 4, top.y - 8, top.x + layout.size.width * write + 4, top.y + layout.size.height + 8) { drawText(layout, topLeft = top) }
        val line = easeOut(ramp(s, k.LIFT_END - 0.3f, k.LIFT_END + 0.1f))
        if(line > 0f) {
            val y = top.y + layout.size.height + 4 * density
            val path = Path().apply {
                moveTo(top.x, y)
                val steps = 24
                for(i in 1..(steps * line).roundToInt()) { val x = top.x + layout.size.width * i / steps; lineTo(x, y + sin(i * 1.3f) * 0.8f * density) }
            }
            drawPath(path, IntroLabel, style = Stroke(2.2f * density, cap = StrokeCap.Round))
        }
    }
    drawContext.canvas.restore()
}

/** Plays the intro jingle in step with the picture; silent until the viewer unmutes it. */
private class IntroSound {
    private val rate = 44100
    private var track: AudioTrack? = null
    private suspend fun prepared(): AudioTrack = track ?: withContext(Dispatchers.Default) {
        val pcm = synthesizeIntroJingle(rate).let { f -> ShortArray(f.size) { (f[it] * Short.MAX_VALUE).toInt().toShort() } }
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            .also { it.write(pcm, 0, pcm.size) }
    }.also { track = it }
    suspend fun play(fromSeconds: Float) {
        val t = prepared()
        runCatching { t.pause(); t.playbackHeadPosition = (fromSeconds * rate).toInt().coerceIn(0, (IntroTimeline.DURATION * rate).toInt() - 1); t.play() }
            .onFailure { android.util.Log.w("IntroSound", "jingle could not play", it) }
    }
    fun mute() { runCatching { track?.pause() } }
    fun release() { runCatching { track?.release() }; track = null }
}

/**
 * The 3-second startup intro over the app: the record drops, the grooves draw, the label stamps and the hole punches,
 * then "Monologue" writes in and everything fades into the app. Tap anywhere (or Back) to skip. The sound starts muted.
 */
@Composable fun IntroScreen(start: Boolean = true, onDone: () -> Unit) {
    val texture = remember { IntroTexture() }
    val measurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    var muted by remember { mutableStateOf(true) }
    val sound = remember { IntroSound() }
    val done by rememberUpdatedState(onDone)
    // The clock starts once the app behind has been built ([start]) and one more frame has passed, so no motion is lost
    // to that work; until then the first frame holds.
    LaunchedEffect(start) {
        if(!start) return@LaunchedEffect
        withFrameNanos { }
        progress.animateTo(1f, tween((IntroTimeline.DURATION * 1000).toInt(), easing = LinearEasing)); done()
    }
    LaunchedEffect(muted) { if(muted) sound.mute() else sound.play(progress.value * IntroTimeline.DURATION) }
    DisposableEffect(Unit) { onDispose { sound.release() } }
    BackHandler { done() }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { done() } }) {
        Canvas(Modifier.fillMaxSize()) { renderIntro(progress.value, texture, measurer) }
        // Hidden again with the picture as it fades, so the button never sits on top of the app.
        if(progress.value < IntroTimeline.FADE_START / IntroTimeline.DURATION)
            IconButton({ muted = !muted }, Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp)) {
                Icon(if(muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, if(muted) "開啟聲音" else "靜音", tint = IntroInk.copy(alpha = 0.6f))
            }
    }
}
