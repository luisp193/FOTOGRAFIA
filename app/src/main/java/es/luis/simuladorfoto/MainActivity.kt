package es.luis.simuladorfoto

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
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
            MaterialTheme(colorScheme = Esquema, typography = Tipografia) {
                CompositionLocalProvider(LocalContentColor provides Papel) {
                    App()
                }
            }
        }
    }
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
    val cam = remember { Camara(ctx.applicationContext) }
    var opciones by remember { mutableStateOf(false) }

    val zoom = zoomDe(cam.focal)
    val centroOk = limitaCentro(cam.centro, zoom)
    val hist = remember(centroOk, zoom, cam.ev, cam.blancos, cam.iIso) {
        histogramas(rec.rgb, centroOk, zoom, cam.ev.coerceIn(-10f, 10f), cam.wb, cam.iso)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
    ) {
        val horizontal = maxWidth > maxHeight
        if (horizontal) {
            // En horizontal: la foto a la izquierda y, a la derecha, los tres controles
            // de la exposición siempre a la vista y centrados
            val anchoPanel = if (maxWidth >= 760.dp) 320.dp else 280.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Foto(Modifier.fillMaxWidth().weight(1f), rec, cam, hist)
                    FranjaInfo(cam, Modifier.fillMaxWidth())
                    FilaObjetivo(cam, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp))
                }
                Column(
                    Modifier
                        .width(anchoPanel)
                        .fillMaxHeight()
                        .background(Panel)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BotonLinea("Opciones", Modifier.weight(1f)) { opciones = true }
                        BotonLinea("Cuestionarios", Modifier.weight(1f), onCuestionarios)
                    }
                    FilaModo(cam, Modifier.fillMaxWidth().padding(top = 8.dp))
                    Column(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
                    ) {
                        Diales(cam)
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Cabecera(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    onOpciones = { opciones = true },
                    onCuestionarios = onCuestionarios
                )
                Foto(Modifier.fillMaxWidth().weight(1f), rec, cam, hist)
                FranjaInfo(cam, Modifier.fillMaxWidth())
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Panel)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilaModo(cam, Modifier.fillMaxWidth())
                    FilaObjetivo(cam, Modifier.fillMaxWidth())
                    Diales(cam)
                }
            }
        }
    }
    if (opciones) DialogoOpciones(cam) { opciones = false }
}

// Bajo la foto, como en el visor: a qué distancia se enfoca, qué queda nítido y el exposímetro
@Composable
fun FranjaInfo(cam: Camara, modifier: Modifier) {
    Row(
        modifier
            .background(Color.Black)
            .heightIn(min = 40.dp)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Enfoque: ${textoMetros(cam.enfoque)} m · ${textoNitidez(cam.enfoque, cam.n, cam.focal)}",
            Modifier.weight(1f),
            color = Suave,
            style = letra(12.sp).copy(lineHeight = 15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // Al tocar el exposímetro aparece o se quita el histograma
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { cam.verHistograma = !cam.verHistograma; cam.guardaOpciones() }
                .testTag("exposicion")
                .semantics { stateDescription = textoEv(cam.ev) }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Exposimetro(cam.ev, Modifier.size(84.dp, 18.dp))
            Text(
                textoEv(cam.ev),
                Modifier.padding(start = 8.dp),
                color = if (cam.ev == 0f) Papel else Aviso,
                style = cifra(16.sp, FontWeight.Bold),
                maxLines = 1
            )
        }
    }
}

fun DrawScope.dibujaCapa(
    img: ImageBitmap, x: Float, y: Float, escala: Float,
    ox: Float, oy: Float, s: Float, filtro: ColorFilter?
) {
    drawImage(
        img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset((ox + x * s).roundToInt(), (oy + y * s).roundToInt()),
        dstSize = IntSize((img.width / escala * s).roundToInt(), (img.height / escala * s).roundToInt()),
        colorFilter = filtro,
        filterQuality = FilterQuality.Medium
    )
}

@Composable
fun Foto(modifier: Modifier, rec: Recursos, cam: Camara, hist: Histo) {
    val density = LocalDensity.current
    // Mientras se mueve o se amplía la foto, el desenfoque se calcula con menos muestras
    var moviendo by remember { mutableStateOf(false) }
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
                fun encuadra(f: Float, c: Offset) {
                    cam.focal = f
                    cam.centro = limitaCentro(c, zoomDe(f))
                }
                val primero = awaitFirstDown(requireUnconsumed = false)
                val inicio = primero.position
                var dosDedos = false
                var arrastrando = false
                var f = cam.focal
                var c = limitaCentro(cam.centro, zoomDe(f))
                while (true) {
                    val evento = awaitPointerEvent()
                    val pulsados = evento.changes.count { it.pressed }
                    if (pulsados >= 2) {
                        dosDedos = true
                        moviendo = true
                        val centroide = evento.calculateCentroid(useCurrent = true)
                        if (centroide != Offset.Unspecified) {
                            val pan = evento.calculatePan()
                            val cambio = evento.calculateZoom()
                            // El punto de la foto que estaba bajo los dedos sigue bajo los dedos
                            val punto = c + (centroide - pan - medio) / escala(f)
                            // el pellizco va de 24 a 200 mm
                            f = (maxOf(f, FOCAL_FOTO.toFloat()) * cambio).coerceIn(FOCAL_FOTO.toFloat(), FOCAL_MAX)
                            c = limitaCentro(punto - (centroide - medio) / escala(f), zoomDe(f))
                            encuadra(f, c)
                        }
                        evento.changes.forEach { if (it.positionChanged()) it.consume() }
                    } else if (pulsados == 1) {
                        // Un dedo: si se mueve más que un toque, arrastra la foto
                        val dedo = evento.changes.first { it.pressed }
                        if (!arrastrando && (dedo.position - inicio).getDistance() > viewConfiguration.touchSlop) {
                            arrastrando = true
                            moviendo = true
                        }
                        if (arrastrando) {
                            val mov = dedo.position - dedo.previousPosition
                            if (mov != Offset.Zero) {
                                f = cam.focal
                                c = limitaCentro(c - mov / escala(f), zoomDe(f))
                                encuadra(f, c)
                            }
                            dedo.consume()
                        }
                    }
                    if (pulsados == 0) {
                        moviendo = false
                        val ultimo = evento.changes.firstOrNull { it.id == primero.id }
                        val fin = ultimo?.position ?: inicio
                        val tocado = ultimo?.isConsumed == true   // lo ha usado otro botón (el histograma)
                        if (!dosDedos && !arrastrando && !tocado && (fin - inicio).getDistance() < viewConfiguration.touchSlop) {
                            val fa = cam.focal
                            val p = limitaCentro(cam.centro, zoomDe(fa)) + (fin - medio) / escala(fa)
                            if (p.x in 0f..FOTO_W && p.y in 0f..FOTO_H) {
                                val persona = rec.personaEn(p)
                                cam.enfoque = if (persona != null) capas[persona].distancia else distanciaSuelo(p.y)
                                cam.mira = p
                            }
                        }
                        break
                    }
                }
            }
        }

    val focal = cam.focal
    val zoom = zoomDe(focal)
    val centro = limitaCentro(cam.centro, zoom)
    val enfoque = cam.enfoque
    val n = cam.n
    val ev = cam.ev.coerceIn(-10f, 10f)
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

        // Trepidación: trazo que deja el temblor de la mano mientras el obturador está abierto.
        // Con trípode la cámara no tiembla.
        val temblor = if (cam.tripode) 0f else focal / 1000f / SENSOR_ANCHO * dw * PULSO * cam.t
        val trepX = (temblor * 0.45f).coerceAtMost(80f)
        val trepY = (temblor * 0.2f).coerceAtMost(36f)

        val conCurva = Revelado.conCurva()
        val desenfoqueReal = Bokeh.disponible() && !Pruebas.sinDesenfoque
        // Sin la curva (Android 12) la exposición es una multiplicación simple en cada capa:
        // la foto está en sRGB y un paso de luz multiplica sus valores por unos 2^(1/2,2)
        val filtro = if (conCurva) null else {
            val wb = cam.wb
            fun g(x: Float) = (2f.pow(ev) * x).pow(1f / 2.2f)
            ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(g(wb.r), g(wb.g), g(wb.b), 1f) })
        }

        Box(
            Modifier
                .size(with(density) { dw.toDp() }, with(density) { dh.toDp() })
                .clipToBounds()
        ) {
            val celda = dw / 1300f
            val revelado = remember(ev, cam.blancos, cam.iIso, cam.avisos, celda) {
                Revelado.efecto(ev, cam.wb, cam.iso, celda, cam.avisos)
            }
            Box(Modifier.fillMaxSize().graphicsLayer { renderEffect = revelado }) {
                if (desenfoqueReal && Build.VERSION.SDK_INT >= 33) {
                    EscenaObjetivo(rec, ox, oy, s, dw, dh, focal, n, enfoque, trepX, trepY, moviendo)
                } else {
                    EscenaClasica(rec, ox, oy, s, dw, focal, n, enfoque, trepX, trepY, filtro)
                }
                if (!conCurva) {
                    // Grano por ISO alto (en Android 13 o superior lo pone el revelado)
                    Canvas(Modifier.fillMaxSize()) {
                        val pasos = log2(cam.iso / 100f)
                        if (pasos > 0f) {
                            drawRect(
                                brush = ShaderBrush(ImageShader(rec.ruido, TileMode.Repeated, TileMode.Repeated)),
                                alpha = (pasos * 0.09f).coerceAtMost(0.65f),
                                blendMode = BlendMode.Overlay
                            )
                        }
                    }
                }
            }

            // Rejilla de tercios
            if (cam.rejilla) {
                Canvas(Modifier.fillMaxSize()) {
                    val linea = Color.White.copy(alpha = 0.45f)
                    val sombra = Color.Black.copy(alpha = 0.25f)
                    for (q in 1..2) {
                        val x = size.width * q / 3
                        val y = size.height * q / 3
                        drawLine(sombra, Offset(x + 1f, 0f), Offset(x + 1f, size.height), 1f)
                        drawLine(linea, Offset(x, 0f), Offset(x, size.height), 1f)
                        drawLine(sombra, Offset(0f, y + 1f), Offset(size.width, y + 1f), 1f)
                        drawLine(linea, Offset(0f, y), Offset(size.width, y), 1f)
                    }
                }
            }

            // Punto de mira: se cierra sobre el punto al enfocar
            val cierre = remember { Animatable(1f) }
            LaunchedEffect(cam.mira) {
                cierre.snapTo(1.45f)
                cierre.animateTo(1f, tween(220))
            }
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(ox + cam.mira.x * s, oy + cam.mira.y * s)
                val r = 20.dp.toPx() * cierre.value
                val borde = Color.Black.copy(alpha = 0.35f)
                drawCircle(borde, radius = r, center = c, style = Stroke(4.5.dp.toPx()))
                drawCircle(Mira, radius = r, center = c, style = Stroke(2.5.dp.toPx()))
                // cuatro marcas que salen del círculo y un punto en el centro
                val a = r * 0.72f
                val b = r * 1.5f
                for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
                    drawLine(Mira, c + Offset(dx * a, dy * a), c + Offset(dx * b, dy * b), 2.dp.toPx())
                }
                drawCircle(Mira, radius = 2.dp.toPx(), center = c)
            }

            // Avisos sobre la foto
            Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (cam.movida) Rotulo("Movida por el pulso", Aviso)
                if (cam.tripode) Rotulo("Trípode", Papel)
                if (cam.blancos != 0) Rotulo("Blancos: ${cam.wb.nombre}", Papel)
            }

            // Histograma en la esquina; al tocarlo cambia de tipo de gráfica
            if (cam.verHistograma) {
                val ancho = with(density) { (dw * 0.42f).toDp() }.coerceIn(150.dp, 280.dp)
                val alto = if (cam.tipoHisto == TipoHisto.CANALES) ancho * 0.8f else ancho * 0.6f
                val maxAlto = with(density) { dh.toDp() } - 16.dp
                Histograma(
                    hist, cam.tipoHisto, textoEv(cam.ev),
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(ancho, minOf(alto, maxAlto))
                        .testTag("histograma")
                        .clickable {
                            val tipos = TipoHisto.entries
                            cam.tipoHisto = tipos[(cam.tipoHisto.ordinal + 1) % tipos.size]
                            cam.guardaOpciones()
                        }
                )
            }
        }
    }
}

@Composable
private fun Rotulo(texto: String, color: Color) {
    Text(
        texto,
        Modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = color,
        style = cifra(12.sp, FontWeight.Bold),
        maxLines = 1
    )
}

// Android 13 o superior: desenfoque de objetivo (discos) calculado en la tarjeta gráfica.
// El fondo es una sola capa en la que el desenfoque cambia de forma continua con la distancia
// del suelo; cada persona es otra capa con el desenfoque de su distancia.
@RequiresApi(33)
@Composable
fun EscenaObjetivo(
    rec: Recursos, ox: Float, oy: Float, s: Float, dw: Float, dh: Float,
    focal: Float, n: Float, enfoque: Float, trepX: Float, trepY: Float, rapido: Boolean
) {
    // El desenfoque no pasa de este radio (con el tele y el diafragma abierto sería enorme)
    val tope = dw * 0.06f
    val f = focal / 1000f
    // Radio en píxeles de un punto en el infinito
    val k = f * f / n / (enfoque - f) / SENSOR_ANCHO * dw / 2f

    val efectoFondo = remember(oy, s, dw, dh, k, enfoque, tope, trepX, trepY, rapido) {
        Bokeh.suelo(k, enfoque, tope, oy, s, dw, dh, trepX, trepY, rapido)?.asComposeRenderEffect()
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = efectoFondo; clip = true }) {
        dibujaCapa(rec.fondo, 0f, 0f, ESCALA_FONDO, ox, oy, s, null)
    }

    // Personas, de la más lejana a la más cercana. Cada capa ocupa solo el rectángulo
    // de la persona más el margen que necesita su desenfoque.
    capas.forEachIndexed { i, c ->
        val img = rec.imagenesPersonas[i]
        val radio = minOf(desenfoquePx(c.distancia, enfoque, n, focal, dw), tope)
        val margen = ceil(radio * 1.3f + trepX * 2f + 2f)
        val x0 = floor(ox + c.x * s - margen).coerceAtLeast(0f).toInt()
        val y0 = floor(oy + c.y * s - margen).coerceAtLeast(0f).toInt()
        val x1 = ceil(ox + (c.x + img.width / c.escala) * s + margen).coerceAtMost(dw).toInt()
        val y1 = ceil(oy + (c.y + img.height / c.escala) * s + margen).coerceAtMost(dh).toInt()
        if (x1 > x0 && y1 > y0) {
            val w = x1 - x0
            val h = y1 - y0
            val efecto = remember(radio, w, h, trepX, trepY, rapido) {
                Bokeh.fijo(radio, w.toFloat(), h.toFloat(), trepX, trepY, rapido)?.asComposeRenderEffect()
            }
            Spacer(
                Modifier
                    .offset { IntOffset(x0, y0) }
                    .layout { medible, _ ->
                        val p = medible.measure(Constraints.fixed(w, h))
                        layout(w, h) { p.place(0, 0) }
                    }
                    .graphicsLayer { renderEffect = efecto; clip = true }
                    .drawBehind {
                        translate(-x0.toFloat(), -y0.toFloat()) {
                            dibujaCapa(img, c.x, c.y, c.escala, ox, oy, s, null)
                        }
                    }
            )
        }
    }
}

// Android 12 (y pruebas en emulador): desenfoque gaussiano del sistema, con el fondo
// repartido en franjas de distancia
@Composable
fun EscenaClasica(
    rec: Recursos, ox: Float, oy: Float, s: Float, dw: Float,
    focal: Float, n: Float, enfoque: Float, trepX: Float, trepY: Float, filtro: ColorFilter?
) {
    val density = LocalDensity.current
    val sin = Pruebas.sinDesenfoque
    fun aDp(px: Float): Dp = if (sin) 0.dp else with(density) { px.coerceIn(0f, 80f).toDp() }
    fun radioX(dof: Float) = aDp(hypot(dof, trepX))
    fun radioY(dof: Float) = aDp(hypot(dof, trepY))

    distanciaFranjas.forEachIndexed { i, d ->
        if (!sin || i == 0) {
            val dof = desenfoquePx(d, enfoque, n, focal, dw)
            val mascara = Brush.verticalGradient(*mascaraFranja(i), startY = oy, endY = oy + FOTO_H * s)
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(radioX(dof), radioY(dof), BlurredEdgeTreatment.Rectangle)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            ) {
                dibujaCapa(rec.fondo, 0f, 0f, ESCALA_FONDO, ox, oy, s, filtro)
                drawRect(brush = mascara, blendMode = BlendMode.DstIn)
            }
        }
    }
    capas.forEachIndexed { i, c ->
        val dof = desenfoquePx(c.distancia, enfoque, n, focal, dw)
        Canvas(
            Modifier
                .fillMaxSize()
                .blur(radioX(dof), radioY(dof), BlurredEdgeTreatment.Rectangle)
        ) {
            dibujaCapa(rec.imagenesPersonas[i], c.x, c.y, c.escala, ox, oy, s, filtro)
        }
    }
}
