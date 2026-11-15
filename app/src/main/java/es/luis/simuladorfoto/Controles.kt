package es.luis.simuladorfoto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

// ---------- Aspecto ----------
// La misma idea que la web: carta gris, tinta clara y el rojo del punto de mira

val Fondo = Color(0xFF0A0A0A)
val Panel = Color(0xFF161615)
val Linea = Color(0xFF343432)
val Marca = Color(0xFF6C6C68)
val Papel = Color(0xFFF1F1EE)
val Suave = Color(0xFFA3A39E)
val Mira = Color(0xFFE53935)
val Aviso = Color(0xFFFFB300)

val Esquema = darkColorScheme(
    primary = Papel,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2B2B29),
    onPrimaryContainer = Papel,
    secondary = Papel,
    onSecondary = Color.Black,
    secondaryContainer = Papel,
    onSecondaryContainer = Color.Black,
    tertiary = Mira,
    onTertiary = Color.White,
    background = Fondo,
    onBackground = Papel,
    surface = Panel,
    onSurface = Papel,
    surfaceVariant = Color(0xFF262625),
    onSurfaceVariant = Color(0xFFBDBDB8),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF181817),
    surfaceContainer = Color(0xFF1C1C1B),
    surfaceContainerHigh = Color(0xFF222221),
    surfaceContainerHighest = Color(0xFF2B2B29),
    outline = Color(0xFF62625E),
    outlineVariant = Linea,
    error = Color(0xFFFF6B60),
    onError = Color.Black
)

// Archivo es una fuente variable: la misma sirve estrecha (cifras de la cámara),
// normal (textos) y ancha (título)
@OptIn(ExperimentalTextApi::class)
private fun archivo(peso: Int, ancho: Float) = Font(
    R.font.archivo,
    weight = FontWeight(peso),
    variationSettings = FontVariation.Settings(FontVariation.weight(peso), FontVariation.width(ancho))
)

private val pesos = listOf(400, 500, 600, 700, 800)
val Archivo = FontFamily(pesos.map { archivo(it, 100f) })
val ArchivoEstrecha = FontFamily(pesos.map { archivo(it, 80f) })
val ArchivoAncha = FontFamily(pesos.map { archivo(it, 125f) })

val Tipografia: Typography = Typography().let { b ->
    fun TextStyle.a() = copy(fontFamily = Archivo)
    Typography(
        displayLarge = b.displayLarge.a(), displayMedium = b.displayMedium.a(), displaySmall = b.displaySmall.a(),
        headlineLarge = b.headlineLarge.a(), headlineMedium = b.headlineMedium.a(), headlineSmall = b.headlineSmall.a(),
        titleLarge = b.titleLarge.a(), titleMedium = b.titleMedium.a(), titleSmall = b.titleSmall.a(),
        bodyLarge = b.bodyLarge.a(), bodyMedium = b.bodyMedium.a(), bodySmall = b.bodySmall.a(),
        labelLarge = b.labelLarge.a(), labelMedium = b.labelMedium.a(), labelSmall = b.labelSmall.a()
    )
}

// Cifras de cámara: estrechas y todas del mismo ancho, para que no bailen al cambiar
fun cifra(tam: TextUnit, peso: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = ArchivoEstrecha, fontWeight = peso, fontSize = tam, fontFeatureSettings = "tnum"
)

fun letra(tam: TextUnit, peso: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Archivo, fontWeight = peso, fontSize = tam
)

// ---------- Piezas ----------

@Composable
fun Pastilla(texto: String, activa: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(forma)
            .background(if (activa) Papel else Color.Transparent)
            .border(1.dp, if (activa) Papel else Linea, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            color = if (activa) Color.Black else Papel,
            style = cifra(15.sp, if (activa) FontWeight.Bold else FontWeight.Medium),
            maxLines = 1
        )
    }
}

@Composable
fun BotonLinea(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(8.dp)
    Box(
        modifier
            .heightIn(min = 36.dp)
            .clip(forma)
            .background(Color(0xFF222221))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, color = Papel, style = letra(14.sp, FontWeight.SemiBold), maxLines = 1)
    }
}

// Título con el punto de mira del icono
@Composable
fun Cabecera(modifier: Modifier, onOpciones: () -> Unit, onCuestionarios: () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(18.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.width * 0.3f
            drawCircle(Mira, r, c, style = Stroke(1.6.dp.toPx()))
            drawLine(Mira, c - Offset(size.width / 2, 0f), c + Offset(size.width / 2, 0f), 1.4.dp.toPx())
            drawLine(Mira, c - Offset(0f, size.height / 2), c + Offset(0f, size.height / 2), 1.4.dp.toPx())
        }
        Text(
            "Simulador Foto",
            Modifier.padding(start = 8.dp).weight(1f),
            color = Papel,
            style = TextStyle(fontFamily = ArchivoAncha, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BotonLinea("Opciones", onClick = onOpciones)
        Spacer(Modifier.width(8.dp))
        BotonLinea("Cuestionarios", onClick = onCuestionarios)
    }
}

// Modo de la cámara (M, A, S) y trípode
@Composable
fun FilaModo(cam: Camara, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(8.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(forma).border(1.dp, Linea, forma)) {
            Modo.entries.forEach { m ->
                val activo = cam.modo == m
                Box(
                    Modifier
                        .size(40.dp, 36.dp)
                        .background(if (activo) Papel else Color.Transparent)
                        .clickable { cam.cambiaModo(m) }
                        .testTag("modo${m.letra}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        m.letra,
                        color = if (activo) Color.Black else Papel,
                        style = cifra(16.sp, FontWeight.Bold)
                    )
                }
            }
        }
        Text(
            cam.modo.nombre,
            Modifier.weight(1f).padding(horizontal = 10.dp),
            color = Suave,
            style = letra(12.sp).copy(lineHeight = 14.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Pastilla("Trípode", cam.tripode) { cam.tripode = !cam.tripode }
    }
}

// Objetivo: focal actual y botones (mantener pulsado 3 s para escribir otra focal)
@Composable
fun FilaObjetivo(cam: Camara, modifier: Modifier = Modifier) {
    val vibra = LocalHapticFeedback.current
    var editando by remember { mutableStateOf<Int?>(null) }
    val mm = cam.focal.roundToInt()
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Suave, fontFamily = Archivo, fontSize = 13.sp)) { append("Objetivo: ") }
                append("$mm mm")
            },
            Modifier.padding(end = 4.dp),
            color = Papel,
            style = cifra(18.sp, FontWeight.Bold),
            maxLines = 1
        )
        cam.botonesFocal.forEachIndexed { i, f ->
            Pastilla(
                "$f mm",
                abs(cam.focal - f) < 0.5f,
                Modifier.pointerInput(i) {
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
            ) { cam.ponFocal(f.toFloat()) }
        }
    }
    val idx = editando
    if (idx != null) {
        DialogoFocal(
            inicial = cam.botonesFocal[idx],
            onAceptar = { v -> cam.cambiaBoton(idx, v); editando = null },
            onCancelar = { editando = null }
        )
    }
}

// Los tres controles de la exposición
@Composable
fun Diales(cam: Camara) {
    // Velocidad mínima a pulso según la regla de 1/focal
    val segura = velocidades.filter { it <= 1f / cam.focal }.maxOrNull() ?: velocidades.first()
    val etiquetaSegura = etiquetasVel[velocidades.indexOf(segura)]
    val velAutomatica = cam.modo == Modo.A
    val diaAutomatico = cam.modo == Modo.S
    // En automático, si la cámara se queda sin valores la exposición ya no sale a 0
    val sinMargen = cam.ev != 0f

    Dial(
        titulo = "Velocidad",
        valor = "${etiquetasVel[cam.iVel]} s",
        nota = when {
            velAutomatica && sinMargen -> "No hay velocidad que valga"
            cam.tripode -> "Con trípode no sale movida"
            cam.movida -> "Movida: usa $etiquetaSegura o más rápida"
            else -> "A pulso hasta $etiquetaSegura"
        },
        notaAviso = cam.movida || (velAutomatica && sinMargen),
        etiquetas = etiquetasVel,
        indice = cam.iVel,
        auto = velAutomatica,
        etiqueta = "dialVelocidad"
    ) { cam.iVelManual = it }
    Dial(
        titulo = "Diafragma",
        valor = "f/${textoDiafragma(cam.n)}",
        nota = if (diaAutomatico && sinMargen) "No hay diafragma que valga" else "",
        notaAviso = true,
        etiquetas = diafragmas.map { textoDiafragma(it) },
        indice = cam.iDia,
        auto = diaAutomatico,
        etiqueta = "dialDiafragma"
    ) { cam.iDiaManual = it }
    Dial(
        titulo = "ISO",
        valor = "${cam.iso}",
        nota = "",
        notaAviso = false,
        etiquetas = isos.map { it.toString() },
        indice = cam.iIso,
        auto = false,
        etiqueta = "dialIso"
    ) { cam.iIso = it }
}

@Composable
fun Dial(
    titulo: String,
    valor: String,
    nota: String,
    notaAviso: Boolean,
    etiquetas: List<String>,
    indice: Int,
    auto: Boolean,
    etiqueta: String,
    modifier: Modifier = Modifier,
    onIndice: (Int) -> Unit
) {
    Column(modifier.fillMaxWidth().testTag(etiqueta).semantics { stateDescription = valor }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(titulo, color = Suave, style = letra(13.sp, FontWeight.Medium), maxLines = 1)
            if (auto) {
                Text(
                    "AUTO",
                    Modifier
                        .padding(start = 6.dp)
                        .background(Color(0xFF2B2B29), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                    color = Papel,
                    style = cifra(11.sp, FontWeight.Bold)
                )
            }
            Text(
                nota,
                Modifier.weight(1f).padding(horizontal = 8.dp),
                color = if (notaAviso) Aviso else Marca,
                style = letra(11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Text(valor, color = Papel, style = cifra(22.sp, FontWeight.Bold), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Paso("−", !auto && indice > 0) { onIndice(indice - 1) }
            Escala(etiquetas, indice, !auto, Modifier.weight(1f), onIndice)
            Paso("+", !auto && indice < etiquetas.size - 1) { onIndice(indice + 1) }
        }
    }
}

@Composable
private fun Paso(signo: String, activo: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .padding(top = 1.dp)
            .size(30.dp, 26.dp)
            .clip(forma)
            .border(1.dp, Linea, forma)
            .clickable(enabled = activo, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(signo, color = if (activo) Papel else Marca, style = cifra(17.sp, FontWeight.Bold))
    }
}

// Escala con una marca por cada paso de luz; se toca o se arrastra para elegir
@Composable
private fun Escala(
    etiquetas: List<String>,
    indice: Int,
    activo: Boolean,
    modifier: Modifier,
    onIndice: (Int) -> Unit
) {
    val medidor = rememberTextMeasurer()
    val avisa by rememberUpdatedState(onIndice)
    val indiceActual by rememberUpdatedState(indice)
    val vibra = LocalHapticFeedback.current
    val n = etiquetas.size
    val estilo = cifra(10.sp, FontWeight.Medium)
    Canvas(
        modifier
            .height(40.dp)
            .pointerInput(n, activo) {
                if (!activo) return@pointerInput
                val margen = 14.dp.toPx()
                fun indiceEn(x: Float): Int =
                    ((x - margen) / (size.width - 2 * margen) * (n - 1)).roundToInt().coerceIn(0, n - 1)
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    var ultimo = indiceActual
                    val primero = indiceEn(abajo.position.x)
                    if (primero != ultimo) {
                        ultimo = primero
                        vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        avisa(primero)
                    }
                    abajo.consume()
                    while (true) {
                        val e = awaitPointerEvent()
                        val dedo = e.changes.firstOrNull { it.id == abajo.id } ?: break
                        if (!dedo.pressed) break
                        val i = indiceEn(dedo.position.x)
                        if (i != ultimo) {
                            ultimo = i
                            vibra.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            avisa(i)
                        }
                        dedo.consume()
                    }
                }
            }
    ) {
        val margen = 14.dp.toPx()
        val paso = (size.width - 2 * margen) / (n - 1)
        val eje = 13.dp.toPx()
        drawLine(Linea, Offset(margen, eje), Offset(size.width - margen, eje), 2.dp.toPx())
        for (i in 0 until n) {
            val x = margen + i * paso
            drawLine(Marca, Offset(x, eje - 4.dp.toPx()), Offset(x, eje + 4.dp.toPx()), 1.5.dp.toPx())
        }
        // Índice
        val xi = margen + indice * paso
        drawCircle(if (activo) Papel else Marca, 9.dp.toPx(), Offset(xi, eje))
        drawCircle(Panel, 3.dp.toPx(), Offset(xi, eje))
        // Números: los que quepan sin pisarse
        val medidas = etiquetas.map { medidor.measure(it, estilo) }
        val anchoMax = medidas.maxOf { it.size.width }
        val cada = ceil((anchoMax + 6.dp.toPx()) / paso).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            if (i % cada != 0) continue
            val m = medidas[i]
            val x = (margen + i * paso - m.size.width / 2f).coerceIn(0f, size.width - m.size.width)
            drawText(m, color = if (i == indice) Papel else Marca, topLeft = Offset(x, 26.dp.toPx()))
        }
    }
}

// ---------- Diálogos ----------

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
                    Text(
                        "Tiene que estar entre $FOCAL_MIN y ${FOCAL_MAX.toInt()}", color = Color(0xFFE57373),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valor != null && valido) onAceptar(valor) }, enabled = valido) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } }
    )
}

@Composable
private fun Apartado(titulo: String) {
    Text(
        titulo,
        Modifier.padding(top = 14.dp, bottom = 6.dp),
        color = Papel,
        style = letra(15.sp, FontWeight.Bold)
    )
}

@Composable
private fun Interruptor(titulo: String, detalle: String, valor: Boolean, activo: Boolean = true, onCambio: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(titulo, color = Papel, style = letra(14.sp, FontWeight.Medium))
            if (detalle.isNotEmpty()) Text(detalle, color = Suave, style = letra(12.sp))
        }
        Switch(checked = valor, onCheckedChange = onCambio, enabled = activo)
    }
}

@Composable
fun DialogoOpciones(cam: Camara, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Opciones") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Apartado("Balance de blancos")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    balances.forEachIndexed { i, b ->
                        Pastilla(b.nombre, cam.blancos == i) { cam.blancos = i }
                    }
                }
                Text(
                    "La plaza está con luz de día. Con otro ajuste la foto sale teñida, como cuando la cámara se equivoca de luz.",
                    Modifier.padding(top = 6.dp),
                    color = Suave, style = letra(12.sp)
                )

                Apartado("Histograma")
                Interruptor("Mostrar el histograma", "También se abre tocando el exposímetro.", cam.verHistograma) {
                    cam.verHistograma = it; cam.guardaOpciones()
                }
                Row(
                    Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TipoHisto.entries.forEach { t ->
                        Pastilla(t.nombre, cam.tipoHisto == t) { cam.tipoHisto = t; cam.guardaOpciones() }
                    }
                }

                Apartado("Ayudas en la foto")
                Interruptor("Rejilla de tercios", "Para componer el encuadre.", cam.rejilla) {
                    cam.rejilla = it; cam.guardaOpciones()
                }
                Interruptor(
                    "Avisar de luces quemadas",
                    if (Revelado.conCurva()) "Rayas rojas donde se quema la luz y azules donde la sombra queda negra."
                    else "Necesita Android 13 o superior.",
                    cam.avisos && Revelado.conCurva(),
                    activo = Revelado.conCurva()
                ) { cam.avisos = it; cam.guardaOpciones() }

                Apartado("Cómo se usa")
                Text(
                    "Toca la foto para enfocar a una persona o al suelo. Arrastra con un dedo para mover el encuadre " +
                        "y pellizca con dos para el zoom. Mantén pulsado 3 s un botón de objetivo para escribir otra focal.\n\n" +
                        "M: tú eliges todo. A: tú eliges el diafragma y la cámara pone la velocidad. " +
                        "S: tú eliges la velocidad y la cámara pone el diafragma.",
                    color = Suave, style = letra(13.sp)
                )
                TextButton(
                    onClick = { cam.restablece(); onCerrar() },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("Restablecer la cámara") }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } }
    )
}
