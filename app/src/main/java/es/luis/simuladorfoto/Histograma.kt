package es.luis.simuladorfoto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.pow

// Histogramas de la parte de la foto que se ve: rojo, verde, azul y luminancia,
// con la exposición, el balance de blancos y el ruido del ISO aplicados como en la imagen.
class Histo(val r: IntArray, val g: IntArray, val b: IntArray, val l: IntArray, val total: Int) {
    // Parte de los píxeles con algún canal quemado o sin nada de luz
    val quemado: Float get() = extremo(253..255)
    val negro: Float get() = extremo(0..2)

    private fun extremo(rango: IntRange): Float {
        if (total == 0) return 0f
        val peor = maxOf(rango.sumOf { r[it] }, rango.sumOf { g[it] }, rango.sumOf { b[it] })
        return peor.toFloat() / total
    }
}

fun histogramas(
    rgb: ByteArray, centro: Offset, zoom: Float, ev: Float,
    wb: Blancos = balances[0], iso: Int = 100
): Histo {
    val ganancias = floatArrayOf(wb.r, wb.g, wb.b)
    val tablas = Array(3) { c -> FloatArray(257) { Revelado.aplica(it.coerceAtMost(255) / 255f, ev, ganancias[c]) * 255f } }
    val hr = IntArray(256)
    val hg = IntArray(256)
    val hb = IntArray(256)
    val hl = IntArray(256)
    val ex = RGB_W / FOTO_W
    val semiW = FOTO_W / zoom / 2 * ex
    val semiH = FOTO_H / zoom / 2 * ex
    val x0 = (centro.x * ex - semiW).toInt().coerceIn(0, RGB_W - 1)
    val x1 = (centro.x * ex + semiW).toInt().coerceIn(x0 + 1, RGB_W)
    val y0 = (centro.y * ex - semiH).toInt().coerceIn(0, RGB_H - 1)
    val y1 = (centro.y * ex + semiH).toInt().coerceIn(y0 + 1, RGB_H)
    // El ruido del ISO ensancha el histograma (unos niveles de desviación por cada paso)
    val ruido = if (iso <= 100) 0f else 1.9f * kotlin.math.sqrt(iso / 100f)
    var semilla = 12345
    fun azar(): Float {
        semilla = semilla * 1103515245 + 12345
        return ((semilla ushr 8) and 0xFFFF) / 65536f
    }
    // Interpola entre valores vecinos con un poco de azar para que no salgan huecos en peine
    fun canal(c: Int, v: Int): Float {
        val t = tablas[c]
        var x = t[v] + azar() * (t[v + 1] - t[v])
        if (ruido > 0f) x += ruido * 2f * (azar() + azar() + azar() - 1.5f)
        return x
    }
    var total = 0
    for (y in y0 until y1) for (x in x0 until x1) {
        val i = (y * RGB_W + x) * 3
        val r = canal(0, rgb[i].toInt() and 0xFF)
        val g = canal(1, rgb[i + 1].toInt() and 0xFF)
        val b = canal(2, rgb[i + 2].toInt() and 0xFF)
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        hr[r.toInt().coerceIn(0, 255)]++
        hg[g.toInt().coerceIn(0, 255)]++
        hb[b.toInt().coerceIn(0, 255)]++
        hl[lum.toInt().coerceIn(0, 255)]++
        total++
    }
    return Histo(hr, hg, hb, hl, total)
}

private val ROJO = Color(0xFFDC3A30)
private val VERDE = Color(0xFF2BB140)
private val AZUL = Color(0xFF3360F0)

// Suaviza un poco la curva; los extremos (lo quemado y lo negro) se dejan como están
private fun suave(h: IntArray): FloatArray = FloatArray(256) { i ->
    if (i == 0 || i == 255) h[i].toFloat()
    else (h[i - 1] + 2f * h[i] + h[i + 1]) / 4f
}

// Altura de referencia: el pico más alto sin contar los extremos, para que una columna
// quemada no aplaste el resto de la gráfica (esa columna se sale por arriba, como en una cámara)
private fun referencia(curvas: List<FloatArray>): Float {
    var m = 1f
    for (c in curvas) for (i in 3..252) if (c[i] > m) m = c[i]
    return m * 1.12f
}

private fun camino(c: FloatArray, ref: Float, zona: Rect): Path = Path().apply {
    moveTo(zona.left, zona.bottom)
    for (i in 0..255) {
        val alto = (c[i] / ref).coerceIn(0f, 1f).pow(0.72f)
        lineTo(zona.left + zona.width * i / 255f, zona.bottom - zona.height * alto)
    }
    lineTo(zona.right, zona.bottom)
    close()
}

private fun DrawScope.rejillaHisto(zona: Rect) {
    val linea = Color.White.copy(alpha = 0.14f)
    for (q in 1..3) {
        val x = zona.left + zona.width * q / 4
        drawLine(linea, Offset(x, zona.top), Offset(x, zona.bottom), 1f)
    }
    drawRect(Color.White.copy(alpha = 0.28f), zona.topLeft, zona.size, style = Stroke(1f))
}

@Composable
fun Histograma(h: Histo, tipo: TipoHisto, ev: String, modifier: Modifier) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(forma)
            .background(Color.Black.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), forma)
            .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 8.dp)
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // capa propia: los tres colores se suman entre sí (donde coinciden sale blanco)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val r = suave(h.r)
            val g = suave(h.g)
            val b = suave(h.b)
            val l = suave(h.l)
            val todo = Rect(Offset.Zero, size)
            when (tipo) {
                TipoHisto.RGB -> {
                    val ref = referencia(listOf(r, g, b))
                    drawPath(camino(r, ref, todo), ROJO, blendMode = BlendMode.Plus)
                    drawPath(camino(g, ref, todo), VERDE, blendMode = BlendMode.Plus)
                    drawPath(camino(b, ref, todo), AZUL, blendMode = BlendMode.Plus)
                    rejillaHisto(todo)
                }
                TipoHisto.LUZ -> {
                    val ref = referencia(listOf(l))
                    drawPath(camino(l, ref, todo), Color(0xFFD8D8D4))
                    rejillaHisto(todo)
                }
                TipoHisto.CANALES -> {
                    val ref = referencia(listOf(r, g, b))
                    val hueco = 4.dp.toPx()
                    val alto = (size.height - 2 * hueco) / 3
                    listOf(r to ROJO, g to VERDE, b to AZUL).forEachIndexed { i, (curva, color) ->
                        val zona = Rect(Offset(0f, i * (alto + hueco)), Size(size.width, alto))
                        drawPath(camino(curva, ref, zona), color)
                        rejillaHisto(zona)
                    }
                }
            }
        }
        // Cabecera: exposición, tipo de gráfica y avisos de sombras y luces recortadas
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = -18.dp.toPx() }) {
            Text(
                ev,
                Modifier.align(Alignment.TopStart).padding(start = 14.dp),
                color = if (ev.startsWith("±")) Papel else Aviso,
                style = cifra(12.sp, FontWeight.Bold)
            )
            Text(
                tipo.nombre,
                Modifier.align(Alignment.TopCenter),
                color = Suave,
                style = cifra(12.sp, FontWeight.Medium)
            )
            Canvas(Modifier.fillMaxSize()) {
                val t = 9.dp.toPx()
                // Triángulo izquierdo: sombras sin detalle. Derecho: luces quemadas.
                val izq = Path().apply { moveTo(0f, 3f); lineTo(t, 3f); lineTo(0f, 3f + t); close() }
                val der = Path().apply { moveTo(size.width, 3f); lineTo(size.width - t, 3f); lineTo(size.width, 3f + t); close() }
                val apagado = Color.White.copy(alpha = 0.22f)
                drawPath(izq, if (h.negro > 0.005f) Color(0xFF4A8CFF) else apagado)
                drawPath(der, if (h.quemado > 0.005f) Mira else apagado)
            }
        }
    }
}

// Exposímetro como el del visor: escala de -3 a +3 pasos con un índice
@Composable
fun Exposimetro(ev: Float, modifier: Modifier) {
    Canvas(modifier) {
        val centro = size.width / 2
        val paso = (size.width - 10.dp.toPx()) / 6
        val grosor = 1.5.dp.toPx()
        for (i in -3..3) {
            val x = centro + i * paso
            val alto = if (i == 0) 8.dp.toPx() else 5.dp.toPx()
            drawLine(if (i == 0) Papel else Suave, Offset(x, 0f), Offset(x, alto), grosor)
        }
        for (i in -3..2) {
            val x = centro + (i + 0.5f) * paso
            drawLine(Suave.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, 2.5.dp.toPx()), grosor)
        }
        val color = if (ev == 0f) Papel else Aviso
        val x = centro + ev.coerceIn(-3f, 3f) * paso
        val arriba = 10.dp.toPx()
        val abajo = size.height
        val medio = 4.dp.toPx()
        val indice = Path().apply {
            if (abs(ev) > 3f) {
                // fuera de la escala: flecha hacia el lado que se sale
                val punta = if (ev > 0) medio else -medio
                moveTo(x - punta, arriba); lineTo(x + punta, (arriba + abajo) / 2); lineTo(x - punta, abajo)
            } else {
                moveTo(x, arriba); lineTo(x + medio, abajo); lineTo(x - medio, abajo)
            }
            close()
        }
        drawPath(indice, color)
    }
}
