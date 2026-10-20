package dev.plawan.kp.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.plawan.kp.engine.TWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val HM = DateTimeFormatter.ofPattern("HH:mm")
fun fmtTime(t: Instant, z: ZoneId): String = HM.format(t.atZone(z))
fun fmtWindow(w: TWindow, z: ZoneId) = "${fmtTime(w.start, z)}–${fmtTime(w.end, z)}"

// ------------------------------------------------------------------ surfaces

/** Quiet card: hairline border instead of shadow/elevation, generous rounding. */
@Composable
fun KpCard(modifier: Modifier = Modifier, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 3.dp, height = 13.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.secondary, filled: Boolean = false) {
    Box(
        Modifier
            .background(if (filled) color.copy(alpha = 0.92f) else color.copy(alpha = 0.13f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (filled) Color.White else color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).background(color, CircleShape))

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

// ------------------------------------------------------------------ data viz

@Composable
fun ScoreRing(score: Int, modifier: Modifier = Modifier, size: Int = 150) {
    val anim by animateFloatAsState(score / 100f, tween(900), label = "ring")
    val c = scoreColor(score); val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 13.dp.toPx(); val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(Brush.sweepGradient(listOf(c.copy(alpha = .5f), c)), 135f, 270f * anim, false, Offset(inset, inset), arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = c)
            Text("of 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScoreBar(score: Int, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val anim by animateFloatAsState(score / 100f, tween(700), label = "bar")
    Box(modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) {
        Box(Modifier.fillMaxWidth(anim.coerceIn(0.02f, 1f)).fillMaxHeight().background(scoreColor(score), CircleShape))
    }
}

/** 05:00–23:00 strip: green = lucky, red = caution, gold ticks = Moon star/sub pairings, light line = now. */
@Composable
fun DayStrip(lucky: List<TWindow>, avoid: List<TWindow>, pins: List<TWindow>, dayStart: Instant, now: Instant?, from: Int = 5, to: Int = 23) {
    val track = MaterialTheme.colorScheme.surfaceVariant; val line = MaterialTheme.colorScheme.onSurface
    val s0 = dayStart.plusSeconds(from * 3600L); val span = (to - from) * 3600.0
    fun x(t: Instant, w: Float) = (((t.epochSecond - s0.epochSecond) / span).coerceIn(0.0, 1.0) * w).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val h = size.height; val r = CornerRadius(h / 2)
            drawRoundRect(track, size = size, cornerRadius = r)
            fun band(ws: List<TWindow>, c: Color) = ws.forEach { w ->
                val a = x(w.start, size.width); val b = x(w.end, size.width)
                if (b > a) drawRoundRect(c.copy(alpha = .85f), Offset(a, 4.dp.toPx()), Size(b - a, h - 8.dp.toPx()), CornerRadius(6.dp.toPx()))
            }
            band(lucky, KpColors.Good); band(avoid, KpColors.Bad)
            pins.forEach { p -> val a = x(p.start, size.width); drawLine(KpColors.Gold, Offset(a, 0f), Offset(a, h), 3.dp.toPx()) }
            now?.let { if (it.isAfter(s0)) { val a = x(it, size.width); drawLine(line, Offset(a, 0f), Offset(a, h), 2.dp.toPx()) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("05", "09", "13", "17", "21", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun Loading(text: String = "Reading the sky…") = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ nav icons (dependency-free)

enum class KpNav { Today, Life, Chart, Setup }

@Composable
fun KpNavIcon(kind: KpNav, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width; val h = size.height; val sw = 1.8.dp.toPx()
        val stroke = Stroke(sw, cap = StrokeCap.Round)
        when (kind) {
            KpNav.Today -> {
                drawCircle(tint, radius = w * 0.21f, style = stroke)
                for (i in 0 until 8) {
                    val a = i * PI / 4; val r1 = w * 0.33f; val r2 = w * 0.45f
                    drawLine(tint, Offset(w / 2 + (r1 * cos(a)).toFloat(), h / 2 + (r1 * sin(a)).toFloat()),
                        Offset(w / 2 + (r2 * cos(a)).toFloat(), h / 2 + (r2 * sin(a)).toFloat()), sw, StrokeCap.Round)
                }
            }
            KpNav.Life -> {
                val path = Path(); val cx = w / 2; val cy = h / 2; val outer = w * 0.46f; val inner = w * 0.19f
                for (i in 0 until 10) {
                    val ang = -PI / 2 + i * PI / 5; val rad = if (i % 2 == 0) outer else inner
                    val px = cx + (rad * cos(ang)).toFloat(); val py = cy + (rad * sin(ang)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); drawPath(path, tint, style = stroke)
            }
            KpNav.Chart -> {
                drawRoundRect(tint, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), CornerRadius(w * 0.12f), style = stroke)
                val p = Path(); p.moveTo(w / 2, h * 0.12f); p.lineTo(w * 0.88f, h / 2); p.lineTo(w / 2, h * 0.88f); p.lineTo(w * 0.12f, h / 2); p.close()
                drawPath(p, tint, style = stroke)
            }
            KpNav.Setup -> {
                for (i in 0 until 3) {
                    val y = h * (0.25f + i * 0.25f)
                    drawLine(tint, Offset(w * 0.14f, y), Offset(w * 0.86f, y), sw, StrokeCap.Round)
                    val kx = w * listOf(0.38f, 0.62f, 0.44f)[i]
                    drawCircle(tint, radius = w * 0.075f, center = Offset(kx, y))
                }
            }
        }
    }
}

const val DISCLAIMER = "Indications per KP rules, not guarantees. Astrology is not scientifically validated; the astronomy and rule application are exact and testable, the 0–100 score is a transparent heuristic."
