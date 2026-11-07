package es.luis.simuladorfoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RuntimeShader
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

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
                App()
            }
        }
    }
}

// Solo para las pruebas en emulador, que no tiene tarjeta gráfica: sin desenfoque
object Pruebas {
    @Volatile var sinDesenfoque = false
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

// La foto está hecha con un 24 mm: con focales más cortas se ve el mismo encuadre
// (no hay más escena), pero el desenfoque y la velocidad a pulso son los de esa focal.
// Con focales largas se ve una parte pequeña de la foto ampliada y pierde nitidez.
const val FOCAL_FOTO = 24
val FOCALES_INICIALES = listOf(24, 50, 70, 200)
const val FOCAL_MIN = 4
const val FOCAL_MAX = 500f

fun zoomDe(focal: Float) = (focal / FOCAL_FOTO).coerceAtLeast(1f)

// Los botones de focal se pueden cambiar manteniéndolos pulsados; se guardan en el móvil
fun leeFocales(ctx: Context): List<Int> {
    val txt = ctx.getSharedPreferences("simulador", Context.MODE_PRIVATE).getString("focales", null)
    val lista = txt?.split(",")?.mapNotNull { it.toIntOrNull() }
    return if (lista != null && lista.size == FOCALES_INICIALES.size) lista else FOCALES_INICIALES
}

fun guardaFocales(ctx: Context, lista: List<Int>) {
    ctx.getSharedPreferences("simulador", Context.MODE_PRIVATE).edit()
        .putString("focales", lista.joinToString(",")).apply()
}

const val SENSOR_ANCHO = 0.036f   // foto horizontal de formato completo: 36 mm de ancho
const val PULSO = 0.035f          // temblor de la mano (rad/s): da la regla de 1/focal

// ---------- La foto y sus capas ----------

const val FOTO_W = 2576f
const val FOTO_H = 1438f
// Copia reducida de la foto en RGB para el histograma
const val RGB_W = 320
const val RGB_H = 179

// Geometría del suelo de la plaza (medida en la foto con las personas a 3 y 10 m):
// distancia = KH / (fila - HORIZONTE)
const val HORIZONTE = 334f
const val KH = 2173f
const val DISTANCIA_EDIFICIOS = 50f

fun distanciaSuelo(fila: Float): Float =
    if (fila <= HORIZONTE + KH / DISTANCIA_EDIFICIOS) DISTANCIA_EDIFICIOS
    else (KH / (fila - HORIZONTE)).coerceIn(1.5f, DISTANCIA_EDIFICIOS)

fun filaDe(distancia: Float) = HORIZONTE + KH / distancia

// El fondo se reparte en franjas horizontales según la distancia del suelo
val limitesFranjas = listOf(25f, 14f, 8f, 5f, 3.5f, 2.5f).map { filaDe(it) }
val distanciaFranjas = listOf(50f, 18f, 10.5f, 6.3f, 4.2f, 3f, 2.2f)

// Máscara de cada franja: desde su borde superior (difuminado) hasta abajo del todo.
// Se dibujan de lejos a cerca, y cada franja tapa a la anterior, sin dejar rayas.
fun mascaraFranja(i: Int): Array<Pair<Float, Color>> {
    if (i == 0) return arrayOf(0f to Color.White, 1f to Color.White)
    val f = 20f
    val arriba = limitesFranjas[i - 1]
    return arrayOf(
        0f to Color.Transparent,
        (arriba - f) / FOTO_H to Color.Transparent,
        (arriba + f) / FOTO_H to Color.White,
        1f to Color.White
    )
}

// Personas recortadas: posición en la foto, distancia y punto de mira inicial
data class Capa(val archivo: String, val x: Float, val y: Float, val distancia: Float, val mira: Offset)

// De la más lejana a la más cercana (orden de dibujo)
val capas = listOf(
    Capa("p3.webp", 2075f, 200f, 10f, Offset(2145f, 300f)),
    Capa("p2.webp", 1162f, 237f, 3f, Offset(1305f, 330f)),
    Capa("p1.webp", 103f, 79f, 1f, Offset(430f, 330f))
)

class Recursos(private val ctx: Context) {
    private fun bitmap(nombre: String): Bitmap =
        ctx.assets.open(nombre).use { BitmapFactory.decodeStream(it) }

    val fondo: ImageBitmap = bitmap("fondo.jpg").asImageBitmap()
    val personas: List<Bitmap> = capas.map { bitmap(it.archivo) }
    val imagenesPersonas: List<ImageBitmap> = personas.map { it.asImageBitmap() }
    val rgb: ByteArray = ctx.assets.open("rgb.bin").use { it.readBytes() }
    val ruido: ImageBitmap = creaRuido()

    // ¿Hay una persona en este punto de la foto? Se mira primero la más cercana.
    fun personaEn(p: Offset): Int? {
        for (i in capas.indices.reversed()) {
            val c = capas[i]
            val b = personas[i]
            val muestras = listOf(Offset.Zero, Offset(12f, 0f), Offset(-12f, 0f), Offset(0f, 12f), Offset(0f, -12f))
            for (m in muestras) {
                val x = (p.x + m.x - c.x).toInt()
                val y = (p.y + m.y - c.y).toInt()
                if (x in 0 until b.width && y in 0 until b.height && (b.getPixel(x, y) ushr 24) > 100) return i
            }
        }
        return null
    }
}

fun creaRuido(): ImageBitmap {
    val n = 256
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) {
        val g = (128 + rnd.nextGaussian() * 45).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// ---------- Cálculos ----------

// Radio de desenfoque (círculo de confusión) en píxeles de pantalla
fun desenfoquePx(d: Float, enfoque: Float, n: Float, focalMm: Float, anchoPx: Float): Float {
    val f = focalMm / 1000f
    val c = f * f / n * abs(d - enfoque) / (d * (enfoque - f))
    return c / SENSOR_ANCHO * anchoPx / 2f
}

fun log2(x: Float) = (ln(x.toDouble()) / ln(2.0)).toFloat()

fun textoEv(ev: Float): String {
    val r = (ev * 10).roundToInt()
    if (r == 0) return "±0,0 EV"
    val signo = if (r > 0) "+" else "-"
    val a = abs(r)
    return "$signo${a / 10},${a % 10} EV"
}

fun textoMetros(d: Float): String =
    if (abs(d - d.roundToInt()) < 0.05f || d >= 10f) "${d.roundToInt()}"
    else String.format(Locale.forLanguageTag("es-ES"), "%.1f", d)

fun textoDiafragma(n: Float): String =
    if (n % 1f == 0f) n.toInt().toString() else n.toString().replace('.', ',')

// Que la parte visible no se salga de la foto
fun limitaCentro(c: Offset, zoom: Float): Offset {
    val sw = FOTO_W / zoom / 2
    val sh = FOTO_H / zoom / 2
    return Offset(c.x.coerceIn(sw, FOTO_W - sw), c.y.coerceIn(sh, FOTO_H - sh))
}

// ---------- Revelado: exposición con la curva de la cámara ----------
// Como en una cámara: la luz se multiplica en lineal y las altas luces se comprimen
// (hombro) antes de quemarse. Se aplica sobre la foto ya desenfocada, igual que el sensor.
const val HOMBRO = 0.85f
const val TECHO = 1.05f

const val AGSL_EXPOSICION = """
uniform shader contenido;
uniform half factor;
half4 main(float2 p) {
    half4 c = contenido.eval(p);
    half3 lin = toLinearSrgb(c.rgb) * factor;
    half3 hombro = 0.85 + 0.2 * (1.0 - exp(-(lin - 0.85) / 0.2));
    lin = mix(lin, hombro, step(half3(0.85), lin));
    return half4(fromLinearSrgb(min(lin, half3(1.0))), c.a);
}
"""

object Revelado {
    private var probado = false
    private var hayCurva = false

    // La curva necesita Android 13; en Android 12 se usa una multiplicación simple
    fun conCurva(): Boolean {
        if (!probado) {
            probado = true
            hayCurva = Build.VERSION.SDK_INT >= 33 && compila()
        }
        return hayCurva
    }

    @RequiresApi(33)
    private fun compilaShader() = RuntimeShader(AGSL_EXPOSICION)

    private fun compila(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) { compilaShader(); true } else false
    } catch (e: Throwable) {
        false
    }

    fun efecto(ev: Float): androidx.compose.ui.graphics.RenderEffect? =
        if (conCurva() && Build.VERSION.SDK_INT >= 33) efecto33(ev) else null

    @RequiresApi(33)
    private fun efecto33(ev: Float): androidx.compose.ui.graphics.RenderEffect {
        val sh = RuntimeShader(AGSL_EXPOSICION)
        sh.setFloatUniform("factor", 2f.pow(ev))
        return android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "contenido").asComposeRenderEffect()
    }

    // Lo mismo que hace la pantalla, para un valor sRGB de 0 a 1
    fun aplica(v: Float, ev: Float): Float {
        if (!conCurva()) return (v * 2f.pow(ev / 2.2f)).coerceAtMost(1f)
        var l = aLineal(v) * 2f.pow(ev)
        if (l > HOMBRO) l = HOMBRO + (TECHO - HOMBRO) * (1f - kotlin.math.exp(-(l - HOMBRO) / (TECHO - HOMBRO)))
        return aSrgb(l.coerceAtMost(1f))
    }

    private fun aLineal(x: Float) = if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
    private fun aSrgb(x: Float) = if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
}

// Histograma de luminancia de la parte de la foto que se ve, con la exposición aplicada
// a cada canal (rojo, verde y azul) como en la imagen, y quemando cada uno en 255.
fun histogramaVisible(rgb: ByteArray, centro: Offset, zoom: Float, ev: Float): IntArray {
    val tabla = FloatArray(257) { Revelado.aplica(it.coerceAtMost(255) / 255f, ev) * 255f }
    val h = IntArray(256)
    val ex = RGB_W / FOTO_W
    val semiW = FOTO_W / zoom / 2 * ex
    val semiH = FOTO_H / zoom / 2 * ex
    val x0 = (centro.x * ex - semiW).toInt().coerceIn(0, RGB_W - 1)
    val x1 = (centro.x * ex + semiW).toInt().coerceIn(x0 + 1, RGB_W)
    val y0 = (centro.y * ex - semiH).toInt().coerceIn(0, RGB_H - 1)
    val y1 = (centro.y * ex + semiH).toInt().coerceIn(y0 + 1, RGB_H)
    var semilla = 12345
    // Interpola entre valores vecinos con un poco de azar para que no salgan huecos en peine
    fun canal(v: Int): Float {
        semilla = semilla * 1103515245 + 12345
        val f = ((semilla ushr 8) and 0xFFFF) / 65536f
        return tabla[v] + f * (tabla[v + 1] - tabla[v])
    }
    for (y in y0 until y1) for (x in x0 until x1) {
        val i = (y * RGB_W + x) * 3
        val r = canal(rgb[i].toInt() and 0xFF)
        val g = canal(rgb[i + 1].toInt() and 0xFF)
        val b = canal(rgb[i + 2].toInt() and 0xFF)
        val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
        h[lum.toInt().coerceIn(0, 255)]++
    }
    return h
}

// ---------- Pantalla ----------

@Composable
fun App() {
    var verCuestionarios by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // El simulador sigue debajo para no perder sus ajustes al volver
        Simulador(onCuestionarios = { verCuestionarios = true })
        if (verCuestionarios) Cuestionarios(onSalir = { verCuestionarios = false })
    }
}

@Composable
fun Simulador(onCuestionarios: () -> Unit) {
    val ctx = LocalContext.current
    val rec = remember { Recursos(ctx.applicationContext) }

    var iVel by remember { mutableFloatStateOf(5f) }   // 1/125
    var iDia by remember { mutableFloatStateOf(4f) }   // f/5.6
    var iIso by remember { mutableFloatStateOf(0f) }   // ISO 100
    var focal by remember { mutableFloatStateOf(FOCAL_FOTO.toFloat()) }
    var enfoque by remember { mutableFloatStateOf(3f) }
    var mira by remember { mutableStateOf(capas[1].mira) }
    var centro by remember { mutableStateOf(Offset(FOTO_W / 2, FOTO_H / 2)) }
    var verHistograma by remember { mutableStateOf(false) }
    var tripode by remember { mutableStateOf(false) }

    val t = velocidades[iVel.roundToInt()]
    val n = diafragmas[iDia.roundToInt()]
    val iso = isos[iIso.roundToInt()]
    val zoom = zoomDe(focal)
    var botonesFocal by remember { mutableStateOf(leeFocales(ctx)) }

    // Exposición correcta de referencia: 1/125, f/5.6, ISO 100
    val ev = log2((t / (1 / 125f)) * (iso / 100f) / ((n / 5.6f) * (n / 5.6f))).coerceIn(-10f, 10f)
    // La foto está en sRGB: un paso de luz multiplica sus valores por unos 2^(1/2,2)
    // Con Android 13 o superior la exposición la aplica la curva de la cámara sobre la foto entera;
    // en Android 12, una multiplicación simple en cada capa
    val factor = 2f.pow(ev / 2.2f)
    val filtro = if (Revelado.conCurva()) null
        else ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(factor, factor, factor, 1f) })

    val centroOk = limitaCentro(centro, zoom)

    val hist = remember(centroOk, zoom, ev) { histogramaVisible(rec.rgb, centroOk, zoom, ev) }

    val controles: @Composable (Modifier) -> Unit = { mod ->
        Controles(
            mod, focal, { focal = it; centro = mira }, botonesFocal,
            { i, f ->
                botonesFocal = botonesFocal.toMutableList().also { it[i] = f }
                guardaFocales(ctx, botonesFocal)
                focal = f.toFloat()
                centro = mira
            },
            enfoque, iVel, { iVel = it }, iDia, { iDia = it }, iIso, { iIso = it },
            tripode, { tripode = it }, onCuestionarios
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
    ) {
        val horizontal = maxWidth > maxHeight
        val foto: @Composable (Modifier) -> Unit = { mod ->
            Foto(
                mod, rec, focal, zoom, centroOk,
                { f, c -> focal = f; centro = limitaCentro(c, zoomDe(f)) },
                enfoque, n, t, filtro, iso, mira,
                onEnfoca = { d, p -> enfoque = d; mira = p },
                ev, hist, verHistograma, { verHistograma = it }, tripode
            )
        }
        if (horizontal) {
            Row(Modifier.fillMaxSize()) {
                foto(Modifier.weight(1f).fillMaxHeight())
                controles(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                foto(Modifier.fillMaxWidth().weight(1f))
                controles(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun Foto(
    modifier: Modifier,
    rec: Recursos,
    focal: Float,
    zoom: Float,
    centro: Offset,
    onEncuadre: (Float, Offset) -> Unit,
    enfoque: Float,
    n: Float,
    t: Float,
    filtro: ColorFilter?,
    iso: Int,
    mira: Offset,
    onEnfoca: (Float, Offset) -> Unit,
    ev: Float,
    hist: IntArray,
    verHistograma: Boolean,
    onVerHistograma: (Boolean) -> Unit,
    tripode: Boolean
) {
    val density = LocalDensity.current
    // Valores actuales para el gesto, que no se reinicia al mover o ampliar la foto
    val centroActual by rememberUpdatedState(centro)
    val focalActual by rememberUpdatedState(focal)
    // Un dedo: toque rápido para enfocar, pulsar y arrastrar para mover la foto.
    // Dos dedos: pellizcar para el zoom.
    // Se recoge en toda la zona de la foto, también en las franjas negras, para que quepan los dos dedos.
    val gestos = Modifier
        .testTag("foto")
        .pointerInput(Unit) {
            awaitEachGesture {
                val zonaW = size.width.toFloat()
                val zonaH = size.height.toFloat()
                val dw = minOf(zonaW, zonaH * FOTO_W / FOTO_H)
                val medio = Offset(zonaW / 2, zonaH / 2)
                fun escala(f: Float) = dw * zoomDe(f) / FOTO_W
                val primero = awaitFirstDown(requireUnconsumed = false)
                val inicio = primero.position
                var dosDedos = false
                var arrastrando = false
                var f = focalActual
                var c = limitaCentro(centroActual, zoomDe(f))
                while (true) {
                    val evento = awaitPointerEvent()
                    val pulsados = evento.changes.count { it.pressed }
                    if (pulsados >= 2) {
                        dosDedos = true
                        val centroide = evento.calculateCentroid(useCurrent = true)
                        if (centroide != Offset.Unspecified) {
                            val pan = evento.calculatePan()
                            val cambio = evento.calculateZoom()
                            // El punto de la foto que estaba bajo los dedos sigue bajo los dedos
                            val punto = c + (centroide - pan - medio) / escala(f)
                            // el pellizco va de 24 a 500 mm (más angular no cambia el encuadre)
                            f = (maxOf(f, FOCAL_FOTO.toFloat()) * cambio).coerceIn(FOCAL_FOTO.toFloat(), FOCAL_MAX)
                            c = limitaCentro(punto - (centroide - medio) / escala(f), zoomDe(f))
                            onEncuadre(f, c)
                        }
                        evento.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (pulsados == 1) {
                        // Un dedo: si se mueve más que un toque, arrastra la foto
                        val dedo = evento.changes.first { it.pressed }
                        if (!arrastrando && (dedo.position - inicio).getDistance() > viewConfiguration.touchSlop) {
                            arrastrando = true
                        }
                        if (arrastrando) {
                            val mov = dedo.position - dedo.previousPosition
                            if (mov != Offset.Zero) {
                                f = focalActual
                                c = limitaCentro(c - mov / escala(f), zoomDe(f))
                                onEncuadre(f, c)
                            }
                            dedo.consume()
                        }
                    }
                    if (pulsados == 0) {
                        val ultimo = evento.changes.firstOrNull { it.id == primero.id }
                        val fin = ultimo?.position ?: inicio
                        val tocado = ultimo?.isConsumed == true   // lo ha usado otro botón (la exposición)
                        if (!dosDedos && !arrastrando && !tocado && (fin - inicio).getDistance() < viewConfiguration.touchSlop) {
                            val fa = focalActual
                            val p = limitaCentro(centroActual, zoomDe(fa)) + (fin - medio) / escala(fa)
                            if (p.x in 0f..FOTO_W && p.y in 0f..FOTO_H) {
                                val persona = rec.personaEn(p)
                                val d = if (persona != null) capas[persona].distancia else distanciaSuelo(p.y)
                                onEnfoca(d, p)
                            }
                        }
                        break
                    }
                }
            }
        }
    val encuadre = "Encuadre ${centro.x.roundToInt()} ${centro.y.roundToInt()}"
    BoxWithConstraints(
        modifier.then(gestos).semantics { stateDescription = encuadre },
        contentAlignment = Alignment.Center
    ) {
        // Rectángulo donde cabe la foto entera
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val dw = minOf(maxW, maxH * FOTO_W / FOTO_H)
        val dh = dw * FOTO_H / FOTO_W
        val s = dw * zoom / FOTO_W            // píxeles de pantalla por píxel de la foto
        val ox = dw / 2 - centro.x * s
        val oy = dh / 2 - centro.y * s
        fun aPantalla(p: Offset) = Offset(ox + p.x * s, oy + p.y * s)
        fun aDp(px: Float): Dp = if (Pruebas.sinDesenfoque) 0.dp else with(density) { px.coerceIn(0f, 80f).toDp() }

        // Trepidación: trazo que deja el temblor de la mano mientras el obturador está abierto
        val k = focal / 1000f / SENSOR_ANCHO * dw
        // Con trípode la cámara no tiembla
        val temblor = if (tripode) 0f else k * PULSO * t
        fun radioX(dof: Float) = aDp(hypot(dof, temblor * 0.45f))
        fun radioY(dof: Float) = aDp(hypot(dof, temblor * 0.2f))

        fun DrawScope.dibuja(img: ImageBitmap, x: Float, y: Float) {
            drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset((ox + x * s).roundToInt(), (oy + y * s).roundToInt()),
                dstSize = IntSize((img.width * s).roundToInt(), (img.height * s).roundToInt()),
                colorFilter = filtro
            )
        }

        Box(
            Modifier
                .size(with(density) { dw.toDp() }, with(density) { dh.toDp() })
                .clipToBounds()
        ) {
          val revelado = remember(ev) { Revelado.efecto(ev) }
          Box(Modifier.fillMaxSize().graphicsLayer { renderEffect = revelado }) {
            // Fondo por franjas de distancia, cada una con su desenfoque
            distanciaFranjas.forEachIndexed { i, d ->
              if (!Pruebas.sinDesenfoque || i == 0) {
                val dof = desenfoquePx(d, enfoque, n, focal, dw)
                val mascara = Brush.verticalGradient(
                    *mascaraFranja(i), startY = oy, endY = oy + FOTO_H * s
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .blur(radioX(dof), radioY(dof), BlurredEdgeTreatment.Rectangle)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    dibuja(rec.fondo, 0f, 0f)
                    drawRect(brush = mascara, blendMode = BlendMode.DstIn)
                }
              }
            }

            // Personas, de la más lejana a la más cercana
            capas.forEachIndexed { i, c ->
                val dof = desenfoquePx(c.distancia, enfoque, n, focal, dw)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .blur(radioX(dof), radioY(dof), BlurredEdgeTreatment.Rectangle)
                ) {
                    dibuja(rec.imagenesPersonas[i], c.x, c.y)
                }
            }

            // Grano por ISO alto
            Canvas(Modifier.fillMaxSize()) {
                val pasos = log2(iso / 100f)
                if (pasos > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(rec.ruido, TileMode.Repeated, TileMode.Repeated)),
                        alpha = (pasos * 0.09f).coerceAtMost(0.65f),
                        blendMode = BlendMode.Overlay
                    )
                }
            }
          }

            // Punto de mira (los gestos se recogen en toda la zona de la foto, más abajo)
            Canvas(Modifier.fillMaxSize()) {
                val c = aPantalla(mira)
                val r = 22.dp.toPx()
                drawCircle(Color.Red, radius = r, center = c, style = Stroke(3.dp.toPx()))
                drawLine(Color.Red, c - Offset(r * 1.5f, 0f), c + Offset(r * 1.5f, 0f), 2.dp.toPx())
                drawLine(Color.Red, c - Offset(0f, r * 1.5f), c + Offset(0f, r * 1.5f), 2.dp.toPx())
            }

            // Exposición en la esquina; al tocarla sale el histograma y al tocar este vuelve
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
            ) {
                if (verHistograma) {
                    val ancho = with(density) { (dw * 0.38f).toDp() }.coerceIn(140.dp, 240.dp)
                    Histograma(
                        hist, textoEv(ev),
                        Modifier
                            .size(ancho, ancho * 0.55f)
                            .clickable { onVerHistograma(false) }
                    )
                } else {
                    Text(
                        textoEv(ev),
                        Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .clickable { onVerHistograma(true) }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Histograma(hist: IntArray, ev: String, modifier: Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 64 columnas; altura en raíz cuadrada para que se vea lo que no está quemado
            // aunque la columna de la derecha sea enorme
            val columnas = 64
            val alturas = FloatArray(columnas) { c ->
                var suma = 0
                for (v in c * 4 until c * 4 + 4) suma += hist[v]
                kotlin.math.sqrt(suma.toFloat())
            }
            val maximo = (alturas.maxOrNull() ?: 1f).coerceAtLeast(1f)
            for (q in 1..3) {
                val x = size.width * q / 4
                drawLine(Color.White.copy(alpha = 0.2f), Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val ancho = size.width / columnas
            val camino = Path().apply {
                moveTo(0f, size.height)
                for (c in 0 until columnas) {
                    val y = size.height * (1f - alturas[c] / maximo)
                    lineTo(c * ancho, y)
                    lineTo((c + 1) * ancho, y)
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(camino, Color.White.copy(alpha = 0.85f))
        }
        Text(
            ev,
            Modifier.align(Alignment.TopStart).padding(start = 6.dp),
            color = Color(0xFFFFD54F),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
fun DialogoFocal(inicial: Int, onAceptar: (Int) -> Unit, onCancelar: () -> Unit) {
    var texto by remember { mutableStateOf(inicial.toString()) }
    val valor = texto.toIntOrNull()
    val valido = valor != null && valor in FOCAL_MIN..FOCAL_MAX.toInt()
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text("Distancia focal") },
        text = {
            Column {
                Text("Escribe la focal para este botón, de $FOCAL_MIN a ${FOCAL_MAX.toInt()} mm.")
                OutlinedTextField(
                    value = texto,
                    onValueChange = { nuevo -> texto = nuevo.filter { it.isDigit() }.take(3) },
                    label = { Text("mm") },
                    singleLine = true,
                    isError = !valido,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(top = 8.dp).testTag("campoFocal")
                )
                if (!valido) {
                    Text("Tiene que estar entre $FOCAL_MIN y ${FOCAL_MAX.toInt()}", color = Color(0xFFE57373),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valor != null && valido) onAceptar(valor) }, enabled = valido) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Controles(
    modifier: Modifier,
    focal: Float,
    onFocal: (Float) -> Unit,
    botonesFocal: List<Int>,
    onCambiaBoton: (Int, Int) -> Unit,
    enfoque: Float,
    iVel: Float,
    onVel: (Float) -> Unit,
    iDia: Float,
    onDia: (Float) -> Unit,
    iIso: Float,
    onIso: (Float) -> Unit,
    tripode: Boolean,
    onTripode: (Boolean) -> Unit,
    onCuestionarios: () -> Unit
) {
    val t = velocidades[iVel.roundToInt()]
    val n = diafragmas[iDia.roundToInt()]
    val iso = isos[iIso.roundToInt()]

    // Velocidad mínima a pulso según la regla de 1/focal
    val segura = velocidades.filter { it <= 1f / focal }.maxOrNull() ?: velocidades.first()
    val etiquetaSegura = etiquetasVel[velocidades.indexOf(segura)]
    val movida = !tripode && t > 1f / focal
    val mm = focal.roundToInt()
    val vibra = LocalHapticFeedback.current
    var editando by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Enfoque: ${textoMetros(enfoque)} m · toca la foto para enfocar" +
                    " · arrastra para mover · pellizca para el zoom",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onCuestionarios, modifier = Modifier.padding(start = 8.dp)) {
                Text("Cuestionarios")
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Objetivo: $mm mm", color = Color.White)
            botonesFocal.forEachIndexed { i, f ->
                FilterChip(
                    selected = abs(focal - f) < 0.5f,
                    onClick = { onFocal(f.toFloat()) },
                    label = { Text("$f mm") },
                    // Mantener pulsado 3 s para escribir otra focal
                    modifier = Modifier.pointerInput(i) {
                        awaitEachGesture {
                            val abajo = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val resultado = withTimeoutOrNull(3000L) {
                                var r = "soltado"
                                while (true) {
                                    val e = awaitPointerEvent(PointerEventPass.Initial)
                                    val dedo = e.changes.firstOrNull { it.id == abajo.id }
                                    if (dedo == null || !dedo.pressed) break
                                    if ((dedo.position - abajo.position).getDistance() > viewConfiguration.touchSlop) {
                                        r = "movido"
                                        break
                                    }
                                }
                                r
                            }
                            if (resultado == null) {
                                vibra.performHapticFeedback(HapticFeedbackType.LongPress)
                                editando = i
                            }
                        }
                    }
                )
            }
        }
        Text(
            "Mantén pulsado un objetivo 3 s para poner otra focal (de $FOCAL_MIN a ${FOCAL_MAX.toInt()} mm)",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        if (focal < FOCAL_FOTO) {
            Text(
                "Más angular que la foto (24 mm): se ve el mismo encuadre, pero el desenfoque y la velocidad a pulso son los de $mm mm",
                color = Color(0xFFFFB300),
                style = MaterialTheme.typography.bodySmall
            )
        }
        val idx = editando
        if (idx != null) {
            DialogoFocal(
                inicial = botonesFocal[idx],
                onAceptar = { v -> onCambiaBoton(idx, v); editando = null },
                onCancelar = { editando = null }
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Velocidad: ${etiquetasVel[iVel.roundToInt()]} s", color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("Trípode", color = Color.White, modifier = Modifier.padding(end = 8.dp))
            Switch(checked = tripode, onCheckedChange = onTripode)
        }
        Text(
            if (tripode) "Con trípode no sale movida por el pulso, a ninguna velocidad"
            else if (movida) "⚠ Foto movida por el pulso: con $mm mm usa $etiquetaSegura s o más rápida"
            else "A pulso con $mm mm: $etiquetaSegura s o más rápida",
            color = if (movida) Color(0xFFFFB300) else Color.Gray,
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = iVel,
            onValueChange = onVel,
            valueRange = 0f..(velocidades.size - 1f),
            steps = velocidades.size - 2
        )
        Text("Diafragma: f/${textoDiafragma(n)}", color = Color.White)
        Slider(
            value = iDia,
            onValueChange = onDia,
            valueRange = 0f..(diafragmas.size - 1f),
            steps = diafragmas.size - 2
        )
        Text("ISO: $iso", color = Color.White)
        Slider(
            value = iIso,
            onValueChange = onIso,
            valueRange = 0f..(isos.size - 1f),
            steps = isos.size - 2
        )
    }
}
