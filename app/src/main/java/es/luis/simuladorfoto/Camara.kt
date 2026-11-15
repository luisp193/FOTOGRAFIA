package es.luis.simuladorfoto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

// Solo para las pruebas en emulador, que no tiene tarjeta gráfica: sin desenfoque
object Pruebas {
    var sinDesenfoque by mutableStateOf(false)
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

// Exposición correcta de referencia: 1/125 s, f/5,6, ISO 100
const val I_VEL_REF = 5
const val I_DIA_REF = 4
const val I_ISO_REF = 0

// Cada posición de velocidad, diafragma o ISO es un paso de luz exacto
fun pasosEv(iVel: Int, iDia: Int, iIso: Int): Int =
    (iVel - I_VEL_REF) - (iDia - I_DIA_REF) + (iIso - I_ISO_REF)

// Lo que elegiría la cámara para dejar la exposición en 0
fun velAuto(iDia: Int, iIso: Int): Int =
    (I_VEL_REF + (iDia - I_DIA_REF) - (iIso - I_ISO_REF)).coerceIn(0, velocidades.size - 1)

fun diaAuto(iVel: Int, iIso: Int): Int =
    (I_DIA_REF + (iVel - I_VEL_REF) + (iIso - I_ISO_REF)).coerceIn(0, diafragmas.size - 1)

enum class Modo(val letra: String, val nombre: String) {
    M("M", "Manual"),
    A("A", "Prioridad de diafragma"),
    S("S", "Prioridad de velocidad")
}

// Balance de blancos: la plaza tiene luz de día, así que los demás ajustes tiñen la foto
data class Blancos(val nombre: String, val r: Float, val g: Float, val b: Float)

val balances = listOf(
    Blancos("Luz de día", 1f, 1f, 1f),
    Blancos("Nublado", 1.10f, 1f, 0.88f),
    Blancos("Sombra", 1.20f, 1f, 0.78f),
    Blancos("Tungsteno", 0.66f, 0.92f, 1.50f),
    Blancos("Fluorescente", 0.84f, 0.94f, 1.22f)
)

enum class TipoHisto(val nombre: String) {
    RGB("RGB"),
    LUZ("Luminancia"),
    CANALES("Canales")
}

// La foto está hecha con un 24 mm, así que el zoom va de 24 a 200 mm.
// A 200 mm se ve una parte pequeña de la foto ampliada y pierde algo de nitidez.
const val FOCAL_FOTO = 24
val FOCALES_INICIALES = listOf(24, 50, 70, 200)
const val FOCAL_MIN = 24
const val FOCAL_MAX = 200f

fun zoomDe(focal: Float) = (focal / FOCAL_FOTO).coerceAtLeast(1f)

// Los botones de focal se pueden cambiar manteniéndolos pulsados; se guardan en el móvil
fun leeFocales(ctx: Context): List<Int> {
    val txt = ctx.getSharedPreferences("simulador", Context.MODE_PRIVATE).getString("focales", null)
    val lista = txt?.split(",")?.mapNotNull { it.toIntOrNull() }
    return if (lista != null && lista.size == FOCALES_INICIALES.size) lista.map { it.coerceIn(FOCAL_MIN, FOCAL_MAX.toInt()) }
    else FOCALES_INICIALES
}

fun guardaFocales(ctx: Context, lista: List<Int>) {
    ctx.getSharedPreferences("simulador", Context.MODE_PRIVATE).edit()
        .putString("focales", lista.joinToString(",")).apply()
}

const val SENSOR_ANCHO = 0.036f   // foto horizontal de formato completo: 36 mm de ancho
const val PULSO = 0.035f          // temblor de la mano (rad/s): da la regla de 1/focal
const val CIRCULO_CONFUSION = 0.00003f   // 0,03 mm: lo que se considera nítido en formato completo

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

// Para Android 12 (sin el desenfoque de objetivo): el fondo se reparte en franjas
// horizontales según la distancia del suelo
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
// escala: píxeles de la imagen por cada píxel de la foto original (las capas están ampliadas
// con superresolución para que no se pixelen con el tele)
data class Capa(val archivo: String, val x: Float, val y: Float, val distancia: Float, val mira: Offset, val escala: Float)

// De la más lejana a la más cercana (orden de dibujo)
val capas = listOf(
    Capa("p3.webp", 2075f, 200f, 10f, Offset(2145f, 300f), 4f),
    Capa("p2.webp", 1162f, 237f, 3f, Offset(1305f, 330f), 4f),
    Capa("p1.webp", 103f, 79f, 1f, Offset(430f, 330f), 2f)
)

// El fondo está al doble de resolución que la foto original
const val ESCALA_FONDO = 2f

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
                val x = ((p.x + m.x - c.x) * c.escala).toInt()
                val y = ((p.y + m.y - c.y) * c.escala).toInt()
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

// ---------- Estado de la cámara ----------

@Stable
class Camara(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("simulador", Context.MODE_PRIVATE)

    var modo by mutableStateOf(Modo.M)
    // Lo que ha puesto el usuario; en los modos A y S la cámara calcula el otro valor
    var iVelManual by mutableIntStateOf(I_VEL_REF)
    var iDiaManual by mutableIntStateOf(I_DIA_REF)
    var iIso by mutableIntStateOf(I_ISO_REF)
    var focal by mutableFloatStateOf(FOCAL_FOTO.toFloat())
    var enfoque by mutableFloatStateOf(3f)
    var mira by mutableStateOf(capas[1].mira)
    var centro by mutableStateOf(Offset(FOTO_W / 2, FOTO_H / 2))
    var tripode by mutableStateOf(false)
    var blancos by mutableIntStateOf(0)
    var botonesFocal by mutableStateOf(leeFocales(ctx))

    // Opciones que se guardan en el móvil
    var rejilla by mutableStateOf(prefs.getBoolean("rejilla", false))
    var avisos by mutableStateOf(prefs.getBoolean("avisos", false))
    var verHistograma by mutableStateOf(prefs.getBoolean("histograma", false))
    var tipoHisto by mutableStateOf(
        TipoHisto.entries[prefs.getInt("tipoHisto", 0).coerceIn(0, TipoHisto.entries.size - 1)]
    )

    val iVel: Int get() = if (modo == Modo.A) velAuto(iDiaManual, iIso) else iVelManual
    val iDia: Int get() = if (modo == Modo.S) diaAuto(iVelManual, iIso) else iDiaManual
    val t: Float get() = velocidades[iVel]
    val n: Float get() = diafragmas[iDia]
    val iso: Int get() = isos[iIso]
    val ev: Float get() = pasosEv(iVel, iDia, iIso).toFloat()
    val wb: Blancos get() = balances[blancos]

    // Sin trípode, la foto sale movida si la velocidad es más lenta que 1/focal
    val movida: Boolean get() = !tripode && t > 1f / focal

    fun cambiaModo(nuevo: Modo) {
        // Al cambiar de modo se conserva lo que había en pantalla
        val v = iVel
        val d = iDia
        iVelManual = v
        iDiaManual = d
        modo = nuevo
    }

    fun cambiaBoton(i: Int, f: Int) {
        botonesFocal = botonesFocal.toMutableList().also { it[i] = f }
        guardaFocales(ctx, botonesFocal)
        ponFocal(f.toFloat())
    }

    fun ponFocal(f: Float) {
        focal = f
        centro = mira
    }

    fun guardaOpciones() {
        prefs.edit()
            .putBoolean("rejilla", rejilla)
            .putBoolean("avisos", avisos)
            .putBoolean("histograma", verHistograma)
            .putInt("tipoHisto", tipoHisto.ordinal)
            .apply()
    }

    fun restablece() {
        modo = Modo.M
        iVelManual = I_VEL_REF
        iDiaManual = I_DIA_REF
        iIso = I_ISO_REF
        focal = FOCAL_FOTO.toFloat()
        enfoque = capas[1].distancia
        mira = capas[1].mira
        centro = Offset(FOTO_W / 2, FOTO_H / 2)
        tripode = false
        blancos = 0
    }
}

// ---------- Cálculos ----------

// Radio del círculo de confusión en píxeles de pantalla
fun desenfoquePx(d: Float, enfoque: Float, n: Float, focalMm: Float, anchoPx: Float): Float {
    val f = focalMm / 1000f
    val c = f * f / n * abs(d - enfoque) / (d * (enfoque - f))
    return c / SENSOR_ANCHO * anchoPx / 2f
}

// Límites de la zona nítida (profundidad de campo); el lejano puede ser infinito
fun zonaNitida(enfoque: Float, n: Float, focalMm: Float): Pair<Float, Float> {
    val f = focalMm / 1000f
    val hiperfocal = f * f / (n * CIRCULO_CONFUSION) + f
    val cerca = enfoque * (hiperfocal - f) / (hiperfocal + enfoque - 2 * f)
    val lejos = if (enfoque >= hiperfocal) Float.POSITIVE_INFINITY
        else enfoque * (hiperfocal - f) / (hiperfocal - enfoque)
    return cerca to lejos
}

private fun metros(d: Float): String =
    if (d >= 10f) "${d.roundToInt()}"
    else String.format(Locale.forLanguageTag("es-ES"), "%.1f", d)

fun textoNitidez(enfoque: Float, n: Float, focalMm: Float): String {
    val (cerca, lejos) = zonaNitida(enfoque, n, focalMm)
    if (lejos.isInfinite()) return "nítido desde ${metros(cerca)} m hasta el infinito"
    val fondo = lejos - cerca
    if (fondo < 0.01f) return "menos de 1 cm nítido"
    if (fondo < 0.2f) return "solo ${(fondo * 100).roundToInt()} cm nítidos"
    return "nítido de ${metros(cerca)} a ${metros(lejos)} m"
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
