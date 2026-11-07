package es.luis.simuladorfoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras del sistema transparentes con iconos claros (el fondo es negro)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Simulador()
            }
        }
    }
}

// ---------- Valores de la cámara ----------

val velocidades = listOf(
    1 / 4000f, 1 / 2000f, 1 / 1000f, 1 / 500f, 1 / 250f, 1 / 125f,
    1 / 60f, 1 / 30f, 1 / 15f, 1 / 8f, 1 / 4f, 1 / 2f, 1f
)
val etiquetasVel = listOf(
    "1/4000", "1/2000", "1/1000", "1/500", "1/250", "1/125",
    "1/60", "1/30", "1/15", "1/8", "1/4", "1/2", "1"
)
val diafragmas = listOf(1.4f, 2f, 2.8f, 4f, 5.6f, 8f, 11f, 16f, 22f)
val isos = listOf(100, 200, 400, 800, 1600, 3200, 6400, 12800)

const val FOCAL = 0.05f      // objetivo de 50 mm
const val SENSOR = 0.036f    // sensor de 36 mm de ancho (formato completo)
const val VEL_ANDAR = 1.4f   // las personas caminan a 1,4 m/s

fun textoDiafragma(n: Float): String =
    if (n % 1f == 0f) n.toInt().toString() else n.toString()

// ---------- Escena ----------

data class Persona(val distancia: Float, val xRel: Float, val color: Color)

// De la más lejana a la más cercana (orden de dibujo)
val personas = listOf(
    Persona(10f, 0.72f, Color(0xFF3F51B5)),
    Persona(3f, 0.25f, Color(0xFF2E7D32)),
    Persona(1f, 0.55f, Color(0xFFC62828))
)

// Posición de cada persona en pantalla
data class Caja(val cx: Float, val pies: Float, val alto: Float)

fun caja(p: Persona, w: Float, h: Float): Caja {
    val horizonte = h * 0.45f
    val pies = horizonte + (h - horizonte) * (0.95f / p.distancia)
    val alto = h * 0.8f / p.distancia
    return Caja(p.xRel * w, pies, alto)
}

// Radio de desenfoque (círculo de confusión) en píxeles de pantalla
fun desenfoquePx(d: Float, enfoque: Float, n: Float, anchoPx: Float): Float {
    val c = if (d.isInfinite()) {
        FOCAL * FOCAL / (n * (enfoque - FOCAL))
    } else {
        FOCAL * FOCAL / n * abs(d - enfoque) / (d * (enfoque - FOCAL))
    }
    return (c / SENSOR * anchoPx * 0.5f).coerceAtMost(60f)
}

fun ajusta(c: Color, brillo: Float) = Color(
    (c.red * brillo).coerceIn(0f, 1f),
    (c.green * brillo).coerceIn(0f, 1f),
    (c.blue * brillo).coerceIn(0f, 1f)
)

fun DrawScope.dibujaPersona(
    cx: Float, pies: Float, alto: Float, color: Color, alpha: Float, brillo: Float
) {
    val piel = ajusta(Color(0xFFF1C27D), brillo)
    val ropa = ajusta(color, brillo)
    val pantalon = ajusta(Color(0xFF37474F), brillo)
    val top = pies - alto
    val anchoT = alto * 0.26f
    // Cabeza
    drawCircle(color = piel, radius = alto * 0.09f, center = Offset(cx, top + alto * 0.09f), alpha = alpha)
    // Tronco
    drawRoundRect(
        color = ropa,
        topLeft = Offset(cx - anchoT / 2, top + alto * 0.19f),
        size = Size(anchoT, alto * 0.36f),
        cornerRadius = CornerRadius(anchoT * 0.2f),
        alpha = alpha
    )
    // Brazos
    drawRoundRect(
        color = ropa,
        topLeft = Offset(cx - anchoT / 2 - alto * 0.07f, top + alto * 0.2f),
        size = Size(alto * 0.06f, alto * 0.32f),
        cornerRadius = CornerRadius(alto * 0.03f),
        alpha = alpha
    )
    drawRoundRect(
        color = ropa,
        topLeft = Offset(cx + anchoT / 2 + alto * 0.01f, top + alto * 0.2f),
        size = Size(alto * 0.06f, alto * 0.32f),
        cornerRadius = CornerRadius(alto * 0.03f),
        alpha = alpha
    )
    // Piernas
    drawRect(
        color = pantalon,
        topLeft = Offset(cx - anchoT / 2, top + alto * 0.55f),
        size = Size(anchoT * 0.45f, alto * 0.45f),
        alpha = alpha
    )
    drawRect(
        color = pantalon,
        topLeft = Offset(cx + anchoT * 0.05f, top + alto * 0.55f),
        size = Size(anchoT * 0.45f, alto * 0.45f),
        alpha = alpha
    )
}

// ---------- Pantalla ----------

@Composable
fun Simulador() {
    var iVel by remember { mutableFloatStateOf(5f) }   // 1/125
    var iDia by remember { mutableFloatStateOf(4f) }   // f/5.6
    var iIso by remember { mutableFloatStateOf(0f) }   // ISO 100
    var enfoque by remember { mutableFloatStateOf(3f) }

    val t = velocidades[iVel.roundToInt()]
    val n = diafragmas[iDia.roundToInt()]
    val iso = isos[iIso.roundToInt()]

    // Exposición correcta de referencia: 1/125, f/5.6, ISO 100
    val brillo = ((t / (1 / 125f)) * (iso / 100f) / ((n / 5.6f) * (n / 5.6f))).coerceIn(0.03f, 10f)
    val density = LocalDensity.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
        ) {
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()
            fun aDp(px: Float): Dp = with(density) { px.toDp() }

            // Fondo (a distancia infinita)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(aDp(desenfoquePx(Float.POSITIVE_INFINITY, enfoque, n, w)), BlurredEdgeTreatment.Rectangle)
            ) {
                val horizonte = size.height * 0.45f
                drawRect(color = ajusta(Color(0xFF81D4FA), brillo), size = Size(size.width, horizonte))
                drawRect(
                    color = ajusta(Color(0xFF7CB342), brillo),
                    topLeft = Offset(0f, horizonte),
                    size = Size(size.width, size.height - horizonte)
                )
                for (i in 0 until 6) {
                    val x = size.width * (0.08f + i * 0.17f)
                    drawRect(
                        color = ajusta(Color(0xFF5D4037), brillo),
                        topLeft = Offset(x - 6f, horizonte - 70f),
                        size = Size(12f, 70f)
                    )
                    drawCircle(color = ajusta(Color(0xFF2E7D32), brillo), radius = 45f, center = Offset(x, horizonte - 90f))
                }
                drawCircle(
                    color = ajusta(Color(0xFFFFF59D), brillo),
                    radius = 50f,
                    center = Offset(size.width * 0.85f, size.height * 0.1f)
                )
            }

            // Personas, cada una en su propia capa
            personas.forEach { p ->
                val r = desenfoquePx(p.distancia, enfoque, n, w)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .blur(aDp(r), BlurredEdgeTreatment.Unbounded)
                ) {
                    val c = caja(p, size.width, size.height)
                    // Cuánto se desplaza la persona mientras el obturador está abierto
                    val anchoCampo = SENSOR * p.distancia / FOCAL
                    val desplaz = VEL_ANDAR * t / anchoCampo * size.width
                    if (desplaz < 1.5f) {
                        dibujaPersona(c.cx, c.pies, c.alto, p.color, 1f, brillo)
                    } else {
                        val copias = 12
                        for (k in 0 until copias) {
                            val dx = desplaz * (k / (copias - 1f) - 0.5f)
                            dibujaPersona(c.cx + dx, c.pies, c.alto, p.color, (2.5f / copias).coerceAtMost(1f), brillo)
                        }
                    }
                }
            }

            // Ruido por ISO alto
            Canvas(Modifier.fillMaxSize()) {
                val pasos = ln(iso / 100f) / ln(2f)
                val num = (pasos * pasos * 600).toInt()
                if (num > 0) {
                    val rnd = Random(iso)
                    val pts = List(num) { Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height) }
                    drawPoints(pts.take(num / 2), PointMode.Points, Color.White.copy(alpha = 0.35f), strokeWidth = 3f)
                    drawPoints(pts.drop(num / 2), PointMode.Points, Color.Black.copy(alpha = 0.35f), strokeWidth = 3f)
                }
            }

            // Punto de mira y toques para enfocar
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(w, h) {
                        detectTapGestures(onTap = { pos ->
                            // Se comprueba primero la persona más cercana
                            val tocada = personas.reversed().firstOrNull { p ->
                                val c = caja(p, w, h)
                                abs(pos.x - c.cx) < c.alto * 0.25f && pos.y >= c.pies - c.alto && pos.y <= c.pies
                            }
                            if (tocada != null) enfoque = tocada.distancia
                        })
                    }
            ) {
                val p = personas.first { it.distancia == enfoque }
                val c = caja(p, size.width, size.height)
                val centro = Offset(c.cx, c.pies - c.alto * 0.75f)
                val r = (c.alto * 0.12f).coerceAtLeast(25f)
                drawCircle(color = Color.Red, radius = r, center = centro, style = Stroke(4f))
                drawLine(Color.Red, centro - Offset(r * 1.5f, 0f), centro + Offset(r * 1.5f, 0f), 3f)
                drawLine(Color.Red, centro - Offset(0f, r * 1.5f), centro + Offset(0f, r * 1.5f), 3f)
            }
        }

        // Controles
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Enfoque: ${enfoque.toInt()} m · toca una persona para enfocarla", color = Color.LightGray)
            Text("Velocidad: ${etiquetasVel[iVel.roundToInt()]} s", color = Color.White)
            Slider(
                value = iVel,
                onValueChange = { iVel = it },
                valueRange = 0f..(velocidades.size - 1f),
                steps = velocidades.size - 2
            )
            Text("Diafragma: f/${textoDiafragma(n)}", color = Color.White)
            Slider(
                value = iDia,
                onValueChange = { iDia = it },
                valueRange = 0f..(diafragmas.size - 1f),
                steps = diafragmas.size - 2
            )
            Text("ISO: $iso", color = Color.White)
            Slider(
                value = iIso,
                onValueChange = { iIso = it },
                valueRange = 0f..(isos.size - 1f),
                steps = isos.size - 2
            )
        }
    }
}
