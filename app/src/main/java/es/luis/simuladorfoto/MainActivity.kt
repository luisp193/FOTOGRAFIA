package es.luis.simuladorfoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
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
val focales = listOf(14, 18, 24, 35, 50, 85, 135, 200)

const val SENSOR_ANCHO = 0.024f   // foto vertical de formato completo: 24 mm de ancho
const val ALTURA_CAMARA = 1.5f    // cámara a la altura de los ojos
const val HORIZONTE = 0.3f        // el horizonte queda al 30 % de la altura de la imagen
const val PULSO = 0.035f          // temblor de la mano (rad/s): da la regla de 1/focal
const val ALTURA_PERSONA = 1.75f
const val DISTANCIA_FONDO = 40f   // árboles del fondo

fun textoDiafragma(n: Float): String =
    if (n % 1f == 0f) n.toInt().toString() else n.toString()

// ---------- Escena ----------

// x = desplazamiento lateral en metros (positivo a la derecha)
data class Persona(val distancia: Float, val x: Float, val color: Color)

// De la más lejana a la más cercana (orden de dibujo)
val personas = listOf(
    Persona(10f, 0.1f, Color(0xFF3F51B5)),
    Persona(3f, -0.45f, Color(0xFF2E7D32)),
    Persona(1f, 0.3f, Color(0xFFC62828))
)

// Cámara estenopeica: con la cámara quieta, cambiar la focal es un zoom
// alrededor del centro óptico, como en una cámara real.
class Camara(focalMm: Int, w: Float, h: Float) {
    val k = focalMm / 1000f / SENSOR_ANCHO * w   // píxeles por metro a 1 m de distancia
    val cx = w / 2
    val cy = h * HORIZONTE
    fun x(xm: Float, z: Float) = cx + k * xm / z
    fun y(ym: Float, z: Float) = cy + k * (ALTURA_CAMARA - ym) / z
}

// Radio de desenfoque (círculo de confusión) en píxeles de pantalla
fun desenfoquePx(d: Float, enfoque: Float, n: Float, focalMm: Int, anchoPx: Float): Float {
    val f = focalMm / 1000f
    val c = if (d.isInfinite()) {
        f * f / (n * (enfoque - f))
    } else {
        f * f / n * abs(d - enfoque) / (d * (enfoque - f))
    }
    return c / SENSOR_ANCHO * anchoPx / 2f
}

fun ajusta(c: Color, brillo: Float) = Color(
    (c.red * brillo).coerceIn(0f, 1f),
    (c.green * brillo).coerceIn(0f, 1f),
    (c.blue * brillo).coerceIn(0f, 1f)
)

fun tocaPersona(cam: Camara, p: Persona, pos: Offset): Boolean {
    val s = cam.k / p.distancia
    return abs(pos.x - cam.x(p.x, p.distancia)) <= 0.3f * s &&
        pos.y >= cam.y(ALTURA_PERSONA, p.distancia) &&
        pos.y <= cam.y(0f, p.distancia)
}

// Dibuja una persona con medidas reales en metros
fun DrawScope.dibujaPersona(cam: Camara, p: Persona, brillo: Float) {
    val z = p.distancia
    val s = cam.k / z
    fun px(m: Float) = cam.x(p.x + m, z)
    fun py(m: Float) = cam.y(m, z)
    val piel = ajusta(Color(0xFFF1C27D), brillo)
    val pelo = ajusta(Color(0xFF4E342E), brillo)
    val ropa = ajusta(p.color, brillo)
    val pantalon = ajusta(Color(0xFF37474F), brillo)

    // Piernas
    drawRect(pantalon, topLeft = Offset(px(-0.18f), py(0.9f)), size = Size(0.16f * s, 0.9f * s))
    drawRect(pantalon, topLeft = Offset(px(0.02f), py(0.9f)), size = Size(0.16f * s, 0.9f * s))
    // Brazos
    drawRoundRect(
        ropa, topLeft = Offset(px(-0.3f), py(1.45f)), size = Size(0.09f * s, 0.62f * s),
        cornerRadius = CornerRadius(0.045f * s)
    )
    drawRoundRect(
        ropa, topLeft = Offset(px(0.21f), py(1.45f)), size = Size(0.09f * s, 0.62f * s),
        cornerRadius = CornerRadius(0.045f * s)
    )
    // Tronco
    drawRoundRect(
        ropa, topLeft = Offset(px(-0.2f), py(1.5f)), size = Size(0.4f * s, 0.62f * s),
        cornerRadius = CornerRadius(0.06f * s)
    )
    // Cuello y cabeza
    drawRect(piel, topLeft = Offset(px(-0.05f), py(1.56f)), size = Size(0.1f * s, 0.08f * s))
    drawCircle(piel, radius = 0.11f * s, center = Offset(px(0f), py(1.63f)))
    drawArc(
        pelo, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(px(-0.115f), py(1.755f)), size = Size(0.23f * s, 0.2f * s)
    )
}

// ---------- Pantalla ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Simulador() {
    var iVel by remember { mutableFloatStateOf(5f) }   // 1/125
    var iDia by remember { mutableFloatStateOf(4f) }   // f/5.6
    var iIso by remember { mutableFloatStateOf(0f) }   // ISO 100
    var focal by remember { mutableIntStateOf(24) }
    var enfoque by remember { mutableFloatStateOf(3f) }

    val t = velocidades[iVel.roundToInt()]
    val n = diafragmas[iDia.roundToInt()]
    val iso = isos[iIso.roundToInt()]

    // Exposición correcta de referencia: 1/125, f/5.6, ISO 100
    val brillo = ((t / (1 / 125f)) * (iso / 100f) / ((n / 5.6f) * (n / 5.6f))).coerceIn(0.03f, 10f)

    // Velocidad mínima a pulso según la regla de 1/focal
    val segura = velocidades.filter { it <= 1f / focal }.maxOrNull() ?: velocidades.first()
    val etiquetaSegura = etiquetasVel[velocidades.indexOf(segura)]
    val movida = t > 1f / focal

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
            val cam = Camara(focal, w, h)
            fun aDp(px: Float): Dp = with(density) { px.coerceIn(0f, 80f).toDp() }

            // Trepidación: trazo que deja el temblor de la mano mientras el obturador está abierto
            val temblor = cam.k * PULSO * t
            fun radioX(dof: Float) = aDp(hypot(dof, temblor * 0.45f))
            fun radioY(dof: Float) = aDp(hypot(dof, temblor * 0.2f))

            // Fondo: cielo, suelo, sol y árboles a 40 m
            val dofFondo = desenfoquePx(DISTANCIA_FONDO, enfoque, n, focal, w)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(radioX(dofFondo), radioY(dofFondo), BlurredEdgeTreatment.Rectangle)
            ) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(ajusta(Color(0xFF4FA3E0), brillo), ajusta(Color(0xFFB3E5FC), brillo)),
                        startY = 0f, endY = cam.cy
                    ),
                    size = Size(size.width, cam.cy)
                )
                drawRect(
                    ajusta(Color(0xFF7CB342), brillo),
                    topLeft = Offset(0f, cam.cy),
                    size = Size(size.width, size.height - cam.cy)
                )
                drawCircle(
                    ajusta(Color(0xFFFFF59D), brillo),
                    radius = cam.k * 0.04f,
                    center = Offset(cam.cx + cam.k * 0.35f, cam.cy - cam.k * 0.2f)
                )
                val z = DISTANCIA_FONDO
                val s = cam.k / z
                for (i in -8..8) {
                    val xm = i * 7.5f + if (i % 2 == 0) 1.5f else -1f
                    val alto = if (i % 3 == 0) 1.15f else 1f
                    drawRect(
                        ajusta(Color(0xFF5D4037), brillo),
                        topLeft = Offset(cam.x(xm - 0.25f, z), cam.y(3f * alto, z)),
                        size = Size(0.5f * s, 3f * alto * s)
                    )
                    drawCircle(
                        ajusta(Color(0xFF2E7D32), brillo),
                        radius = 1.8f * alto * s,
                        center = Offset(cam.x(xm, z), cam.y(4.4f * alto, z))
                    )
                }
            }

            // Personas (quietas), cada una en su capa con su desenfoque
            personas.forEach { p ->
                val dof = desenfoquePx(p.distancia, enfoque, n, focal, w)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .blur(radioX(dof), radioY(dof), BlurredEdgeTreatment.Unbounded)
                ) {
                    dibujaPersona(cam, p, brillo)
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
                    .pointerInput(w, h, focal) {
                        detectTapGestures(onTap = { pos ->
                            // Se comprueba primero la persona más cercana
                            val tocada = personas.reversed().firstOrNull { tocaPersona(cam, it, pos) }
                            if (tocada != null) enfoque = tocada.distancia
                        })
                    }
            ) {
                val p = personas.first { it.distancia == enfoque }
                val centro = Offset(cam.x(p.x, p.distancia), cam.y(1.35f, p.distancia))
                val r = (cam.k / p.distancia * 0.12f).coerceIn(25f, 110f)
                drawCircle(Color.Red, radius = r, center = centro, style = Stroke(4f))
                drawLine(Color.Red, centro - Offset(r * 1.5f, 0f), centro + Offset(r * 1.5f, 0f), 3f)
                drawLine(Color.Red, centro - Offset(0f, r * 1.5f), centro + Offset(0f, r * 1.5f), 3f)
            }
        }

        // Controles
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                "Enfoque: ${enfoque.toInt()} m · toca una persona para enfocarla",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Objetivo", color = Color.White)
                focales.forEach { f ->
                    FilterChip(
                        selected = f == focal,
                        onClick = { focal = f },
                        label = { Text("$f mm") }
                    )
                }
            }
            Text("Velocidad: ${etiquetasVel[iVel.roundToInt()]} s", color = Color.White)
            Text(
                if (movida) "⚠ Foto movida por el pulso: con $focal mm usa $etiquetaSegura s o más rápida"
                else "A pulso con $focal mm: $etiquetaSegura s o más rápida",
                color = if (movida) Color(0xFFFFB300) else Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
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
