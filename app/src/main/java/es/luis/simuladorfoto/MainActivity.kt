package es.luis.simuladorfoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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

// La foto está hecha con un 24 mm; más angular no se puede y más allá de 85 mm se pixela
const val FOCAL_FOTO = 24
val focales = listOf(24, 35, 50, 85)
const val FOCAL_MAX = 85f

const val SENSOR_ANCHO = 0.036f   // foto horizontal de formato completo: 36 mm de ancho
const val PULSO = 0.035f          // temblor de la mano (rad/s): da la regla de 1/focal

// ---------- La foto y sus capas ----------

const val FOTO_W = 2576f
const val FOTO_H = 1438f
const val LUMA_W = 320
const val LUMA_H = 179

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
    val luma: ByteArray = ctx.assets.open("luma.bin").use { it.readBytes() }
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

// Histograma de luminancia de la parte de la foto que se ve
fun histogramaVisible(luma: ByteArray, centro: Offset, zoom: Float): IntArray {
    val h = IntArray(256)
    val ex = LUMA_W / FOTO_W
    val semiW = FOTO_W / zoom / 2 * ex
    val semiH = FOTO_H / zoom / 2 * ex
    val x0 = (centro.x * ex - semiW).toInt().coerceIn(0, LUMA_W - 1)
    val x1 = (centro.x * ex + semiW).toInt().coerceIn(x0 + 1, LUMA_W)
    val y0 = (centro.y * ex - semiH).toInt().coerceIn(0, LUMA_H - 1)
    val y1 = (centro.y * ex + semiH).toInt().coerceIn(y0 + 1, LUMA_H)
    for (y in y0 until y1) for (x in x0 until x1) h[luma[y * LUMA_W + x].toInt() and 0xFF]++
    return h
}

// Aplica la exposición (factor sobre los valores de la imagen) al histograma
fun histogramaExpuesto(base: IntArray, factor: Float): IntArray {
    val h = IntArray(256)
    for (v in 0..255) h[(v * factor).roundToInt().coerceIn(0, 255)] += base[v]
    return h
}

// ---------- Pantalla ----------

@Composable
fun Simulador() {
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
    val zoom = focal / FOCAL_FOTO.toFloat()

    // Exposición correcta de referencia: 1/125, f/5.6, ISO 100
    val ev = log2((t / (1 / 125f)) * (iso / 100f) / ((n / 5.6f) * (n / 5.6f))).coerceIn(-10f, 10f)
    // La foto está en sRGB: un paso de luz multiplica sus valores por unos 2^(1/2,2)
    val factor = 2f.pow(ev / 2.2f)
    val filtro = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(factor, factor, factor, 1f) })

    val centroOk = limitaCentro(centro, zoom)

    val base = remember(centroOk, zoom) { histogramaVisible(rec.luma, centroOk, zoom) }
    val hist = remember(base, factor) { histogramaExpuesto(base, factor) }

    val controles: @Composable (Modifier) -> Unit = { mod ->
        Controles(
            mod, focal, { focal = it; centro = mira }, enfoque, iVel, { iVel = it }, iDia, { iDia = it }, iIso, { iIso = it },
            tripode, { tripode = it }
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
                { f, c -> focal = f; centro = limitaCentro(c, f / FOCAL_FOTO) },
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
    filtro: ColorFilter,
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
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // Rectángulo donde cabe la foto entera
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val dw = minOf(maxW, maxH * FOTO_W / FOTO_H)
        val dh = dw * FOTO_H / FOTO_W
        val s = dw * zoom / FOTO_W            // píxeles de pantalla por píxel de la foto
        val ox = dw / 2 - centro.x * s
        val oy = dh / 2 - centro.y * s
        fun aPantalla(p: Offset) = Offset(ox + p.x * s, oy + p.y * s)
        fun aDp(px: Float): Dp = with(density) { px.coerceIn(0f, 80f).toDp() }
        // Valores actuales para el gesto, que no se reinicia al mover o ampliar la foto
        val centroActual by rememberUpdatedState(centro)
        val focalActual by rememberUpdatedState(focal)
        fun escala(f: Float) = dw * (f / FOCAL_FOTO) / FOTO_W

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
            // Fondo por franjas de distancia, cada una con su desenfoque
            distanciaFranjas.forEachIndexed { i, d ->
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

            // Punto de mira. Un dedo: tocar para enfocar. Dos dedos: pellizcar para el zoom y arrastrar para mover.
            Canvas(
                Modifier
                    .fillMaxSize()
                    .testTag("foto")
                    .pointerInput(dw, dh) {
                        awaitEachGesture {
                            val primero = awaitFirstDown(requireUnconsumed = false)
                            val inicio = primero.position
                            var dosDedos = false
                            var f = focalActual
                            var c = limitaCentro(centroActual, f / FOCAL_FOTO)
                            val medio = Offset(dw / 2, dh / 2)
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
                                        f = (f * cambio).coerceIn(FOCAL_FOTO.toFloat(), FOCAL_MAX)
                                        c = limitaCentro(punto - (centroide - medio) / escala(f), f / FOCAL_FOTO)
                                        onEncuadre(f, c)
                                    }
                                    evento.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                                if (pulsados == 0) {
                                    val fin = evento.changes.firstOrNull { it.id == primero.id }?.position ?: inicio
                                    if (!dosDedos && (fin - inicio).getDistance() < viewConfiguration.touchSlop) {
                                        val fa = focalActual
                                        val sa = escala(fa)
                                        val actual = limitaCentro(centroActual, fa / FOCAL_FOTO)
                                        val p = Offset(
                                            (fin.x - (dw / 2 - actual.x * sa)) / sa,
                                            (fin.y - (dh / 2 - actual.y * sa)) / sa
                                        )
                                        val persona = rec.personaEn(p)
                                        val d = if (persona != null) capas[persona].distancia else distanciaSuelo(p.y)
                                        onEnfoca(d, p)
                                    }
                                    break
                                }
                            }
                        }
                    }
            ) {
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
            val total = hist.sum().coerceAtLeast(1)
            val maxRef = (1..254).maxOf { hist[it] }.coerceAtLeast(1)
            for (q in 1..3) {
                val x = size.width * q / 4
                drawLine(Color.White.copy(alpha = 0.2f), Offset(x, 0f), Offset(x, size.height), 1f)
            }
            val camino = Path().apply {
                moveTo(0f, size.height)
                for (i in 0..255) {
                    val x = i / 255f * size.width
                    val y = size.height * (1f - (hist[i].toFloat() / maxRef).coerceAtMost(1f))
                    lineTo(x, y)
                }
                lineTo(size.width, size.height)
                close()
            }
            drawPath(camino, Color.White.copy(alpha = 0.85f))
            // Avisos de luces quemadas (rojo) y sombras empastadas (azul)
            if (hist[255].toFloat() / total > 0.005f) {
                drawRect(Color(0xFFE53935), topLeft = Offset(size.width - 4.dp.toPx(), 0f), size = Size(4.dp.toPx(), size.height))
            }
            if (hist[0].toFloat() / total > 0.005f) {
                drawRect(Color(0xFF42A5F5), topLeft = Offset.Zero, size = Size(4.dp.toPx(), size.height))
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Controles(
    modifier: Modifier,
    focal: Float,
    onFocal: (Float) -> Unit,
    enfoque: Float,
    iVel: Float,
    onVel: (Float) -> Unit,
    iDia: Float,
    onDia: (Float) -> Unit,
    iIso: Float,
    onIso: (Float) -> Unit,
    tripode: Boolean,
    onTripode: (Boolean) -> Unit
) {
    val t = velocidades[iVel.roundToInt()]
    val n = diafragmas[iDia.roundToInt()]
    val iso = isos[iIso.roundToInt()]

    // Velocidad mínima a pulso según la regla de 1/focal
    val segura = velocidades.filter { it <= 1f / focal }.maxOrNull() ?: velocidades.first()
    val etiquetaSegura = etiquetasVel[velocidades.indexOf(segura)]
    val movida = !tripode && t > 1f / focal
    val mm = focal.roundToInt()

    Column(
        modifier
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            "Enfoque: ${textoMetros(enfoque)} m · toca la foto para enfocar" +
                " · dos dedos para el zoom y mover",
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
            Text("Objetivo: $mm mm", color = Color.White)
            focales.forEach { f ->
                FilterChip(
                    selected = abs(focal - f) < 0.5f,
                    onClick = { onFocal(f.toFloat()) },
                    label = { Text("$f mm") }
                )
            }
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
