package com.tareqsoft.market.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Glass colours of the window, in the order they sit around the arch. */
private val GLASS = listOf(Ruby, Saffron, Emerald, Color(0xFF3E6FB8), Saffron, Ruby, Emerald)

/**
 * A qamariya: the half-round stained-glass window above the windows of
 * Sana'a houses. Coloured glass wedges in a white gypsum lattice, with a soft
 * glow as if lit from behind. Used once, at the top of the home screen.
 */
@Composable
fun QamariyaWindow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension.coerceAtMost(size.width / 2f)
        val center = Offset(size.width / 2f, size.height)
        drawQamariya(center, r.coerceAtMost(size.height))
    }
}

private fun DrawScope.drawQamariya(center: Offset, r: Float) {
    val frame = r * 0.06f
    val lattice = r * 0.022f

    // Light coming through the glass.
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Saffron.copy(alpha = 0.35f), Color.Transparent),
            center = center, radius = r * 1.5f,
        ),
        radius = r * 1.5f, center = center,
    )

    val arc = Size(r * 2, r * 2)
    val topLeft = Offset(center.x - r, center.y - r)
    val n = GLASS.size
    val sweep = 180f / n

    // Outer ring of glass wedges.
    for (i in 0 until n) {
        drawArc(
            color = GLASS[i].copy(alpha = 0.92f),
            startAngle = 180f + i * sweep, sweepAngle = sweep, useCenter = true,
            topLeft = topLeft, size = arc,
        )
    }
    // Inner ring, colours shifted, smaller panes.
    val r2 = r * 0.62f
    for (i in 0 until n) {
        drawArc(
            color = GLASS[(i + 3) % n],
            startAngle = 180f + i * sweep, sweepAngle = sweep, useCenter = true,
            topLeft = Offset(center.x - r2, center.y - r2), size = Size(r2 * 2, r2 * 2),
        )
    }
    // Centre: a small white half-sun.
    val r3 = r * 0.24f
    drawArc(
        color = Color.White, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(center.x - r3, center.y - r3), size = Size(r3 * 2, r3 * 2),
    )

    // Gypsum lattice: radial bars and concentric rings.
    for (i in 0..n) {
        val a = Math.toRadians((180.0 + i * sweep))
        drawLine(
            Color.White,
            start = Offset(center.x + (r3 * cos(a)).toFloat(), center.y + (r3 * sin(a)).toFloat()),
            end = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat()),
            strokeWidth = lattice,
        )
    }
    // Small diamonds where the bars meet the middle ring.
    for (i in 1 until n) {
        val a = Math.toRadians((180.0 + i * sweep))
        val p = Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat())
        val d = r * 0.035f
        drawPath(
            Path().apply {
                moveTo(p.x, p.y - d); lineTo(p.x + d, p.y); lineTo(p.x, p.y + d); lineTo(p.x - d, p.y); close()
            },
            Color.White,
        )
    }
    listOf(r2, r * 0.82f).forEach { rr ->
        drawArc(
            Color.White, 180f, 180f, useCenter = false,
            topLeft = Offset(center.x - rr, center.y - rr), size = Size(rr * 2, rr * 2),
            style = Stroke(width = lattice),
        )
    }
    // Thick gypsum frame.
    drawArc(
        Color.White, 180f, 180f, useCenter = false,
        topLeft = Offset(center.x - r, center.y - r), size = arc,
        style = Stroke(width = frame),
    )
    drawLine(Color.White, Offset(center.x - r - frame / 2, center.y), Offset(center.x + r + frame / 2, center.y), strokeWidth = frame)
}

/**
 * A narrow band of glass diamonds, like the frieze under a qamariya.
 * Sits under the top bar of every inner screen.
 */
@Composable
fun QamariyaBand(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        drawRect(IndigoDeep)
        val step = size.height * 2.2f
        var x = 0f
        var i = 0
        val h = size.height
        while (x < size.width) {
            val c = GLASS[i % GLASS.size]
            drawPath(
                Path().apply {
                    moveTo(x + step / 2, 0f); lineTo(x + step, h / 2); lineTo(x + step / 2, h); lineTo(x, h / 2); close()
                },
                c,
            )
            x += step
            i++
        }
    }
}
