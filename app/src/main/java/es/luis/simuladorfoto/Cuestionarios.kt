package es.luis.simuladorfoto

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray

// respuestas[0] es la correcta; se barajan al mostrarlas
data class Pregunta(val id: Int, val tema: String, val texto: String, val respuestas: List<String>, val explicacion: String)

val TEMAS = listOf(
    "Pasos de exposición",
    "Exposiciones equivalentes",
    "Velocidad y movimiento",
    "Diafragma y profundidad de campo",
    "ISO y ruido",
    "Situaciones reales",
    "Corregir la exposición"
)
const val TODOS = "Todos los temas"

fun cargaPreguntas(ctx: Context): List<Pregunta> {
    val arr = JSONArray(ctx.assets.open("preguntas.json").bufferedReader().use { it.readText() })
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val r = o.getJSONArray("r")
        Pregunta(i, o.getString("t"), o.getString("p"), List(r.length()) { r.getString(it) }, o.getString("e"))
    }
}

// Estadísticas guardadas en el móvil
class Progreso(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("cuestionarios", Context.MODE_PRIVATE)
    fun aciertos(tema: String) = prefs.getInt("ok_$tema", 0)
    fun respondidas(tema: String) = prefs.getInt("total_$tema", 0)
    fun vistas(): Set<String> = prefs.getStringSet("vistas", emptySet()) ?: emptySet()
    fun anota(p: Pregunta, acierto: Boolean) {
        val e = prefs.edit()
        for (t in listOf(p.tema, TODOS)) {
            e.putInt("total_$t", respondidas(t) + 1)
            if (acierto) e.putInt("ok_$t", aciertos(t) + 1)
        }
        e.putStringSet("vistas", vistas() + p.id.toString())
        e.apply()
    }
    fun olvidaVistas(ids: Collection<Int>) {
        prefs.edit().putStringSet("vistas", vistas() - ids.map { it.toString() }.toSet()).apply()
    }
    fun borra() = prefs.edit().clear().apply()
}

class Test(val tema: String, val preguntas: List<Pregunta>, val ordenes: List<List<Int>>)

// Elige primero preguntas que todavía no has visto
fun nuevoTest(todas: List<Pregunta>, tema: String, n: Int, progreso: Progreso): Test {
    val delTema = if (tema == TODOS) todas else todas.filter { it.tema == tema }
    val vistas = progreso.vistas()
    var nuevas = delTema.filter { it.id.toString() !in vistas }.shuffled()
    if (nuevas.size < n) {
        // ya las has visto casi todas: se vuelve a empezar el tema
        progreso.olvidaVistas(delTema.map { it.id })
        nuevas = nuevas + delTema.filter { it !in nuevas }.shuffled()
    }
    val elegidas = nuevas.take(n)
    return Test(tema, elegidas, elegidas.map { p -> p.respuestas.indices.shuffled() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Cuestionarios(onSalir: () -> Unit) {
    val ctx = LocalContext.current
    val todas = remember { cargaPreguntas(ctx.applicationContext) }
    val progreso = remember { Progreso(ctx.applicationContext) }
    var test by remember { mutableStateOf<Test?>(null) }
    var cuantas by remember { mutableIntStateOf(10) }
    var refresco by remember { mutableIntStateOf(0) }

    BackHandler { if (test != null) { test = null; refresco++ } else onSalir() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            // Tapa por completo el simulador que queda debajo
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (test != null) { test = null; refresco++ } else onSalir() }) {
                Text(if (test != null) "← Temas" else "← Simulador")
            }
            Spacer(Modifier.weight(1f))
            Text("Cuestionarios", style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        val t = test
        if (t == null) {
            ListaTemas(todas, progreso, cuantas, { cuantas = it }, refresco) { tema ->
                test = nuevoTest(todas, tema, cuantas, progreso)
            }
        } else {
            PantallaTest(t, progreso,
                onOtro = { test = nuevoTest(todas, t.tema, t.preguntas.size, progreso) },
                onTemas = { test = null; refresco++ })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaTemas(
    todas: List<Pregunta>,
    progreso: Progreso,
    cuantas: Int,
    onCuantas: (Int) -> Unit,
    refresco: Int,
    onEmpezar: (String) -> Unit
) {
    var borrado by remember { mutableIntStateOf(0) }
    val clave = refresco + borrado   // vuelve a leer las estadísticas al volver de un test
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            "${todas.size} preguntas de 3 respuestas. Elige un tema y cuántas preguntas quieres.",
            color = Color.LightGray, style = MaterialTheme.typography.bodyMedium
        )
        Row(
            Modifier.padding(vertical = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Preguntas por test", color = Color.White)
            listOf(10, 20, 50).forEach { n ->
                FilterChip(selected = cuantas == n, onClick = { onCuantas(n) }, label = { Text("$n") })
            }
        }
        (listOf(TODOS) + TEMAS).forEach { tema ->
            val total = if (tema == TODOS) todas.size else todas.count { it.tema == tema }
            val hechas = remember(clave) { progreso.respondidas(tema) }
            val ok = remember(clave) { progreso.aciertos(tema) }
            Card(
                onClick = { onEmpezar(tema) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(tema, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "$total preguntas" + if (hechas > 0) " · aciertos ${ok * 100 / hechas} % de $hechas respondidas" else "",
                        color = Color.Gray, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        TextButton(onClick = { progreso.borra(); borrado++ }, modifier = Modifier.padding(top = 8.dp)) {
            Text("Borrar estadísticas", color = Color.Gray)
        }
    }
}

@Composable
fun PantallaTest(test: Test, progreso: Progreso, onOtro: () -> Unit, onTemas: () -> Unit) {
    var indice by remember(test) { mutableIntStateOf(0) }
    var elegida by remember(test) { mutableStateOf<Int?>(null) }
    var aciertos by remember(test) { mutableIntStateOf(0) }
    val n = test.preguntas.size

    if (indice >= n) {
        val pct = aciertos * 100 / n
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Has acertado $aciertos de $n", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("$pct %", style = MaterialTheme.typography.displayMedium, color = colorNota(pct), fontWeight = FontWeight.Bold)
            Text(
                when {
                    pct == 100 -> "¡Perfecto! Dominas este tema."
                    pct >= 80 -> "Muy bien. Ya casi lo tienes."
                    pct >= 50 -> "Bien encaminado. Repasa las explicaciones de los fallos."
                    else -> "Toca practicar: prueba en el simulador lo que has fallado."
                },
                color = Color.LightGray, modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(onClick = onOtro, modifier = Modifier.fillMaxWidth()) { Text("Otro test del mismo tema") }
            OutlinedButton(onClick = onTemas, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Elegir otro tema") }
        }
        return
    }

    val p = test.preguntas[indice]
    val orden = test.ordenes[indice]
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Pregunta ${indice + 1} de $n · aciertos $aciertos", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { indice / n.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
        Text(p.tema, color = Color(0xFF90CAF9), style = MaterialTheme.typography.labelMedium)
        Text(
            p.texto, color = Color.White, style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        orden.forEach { r ->
            val respondida = elegida != null
            val correcta = r == 0
            val colores = when {
                respondida && correcta -> ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1B5E20), contentColor = Color.White)
                respondida && r == elegida -> ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
                else -> ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            }
            OutlinedButton(
                onClick = {
                    if (elegida == null) {
                        elegida = r
                        if (correcta) aciertos++
                        progreso.anota(p, correcta)
                    }
                },
                colors = colores,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    (if (respondida && correcta) "✓ " else if (respondida && r == elegida) "✗ " else "") + p.respuestas[r],
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            }
        }
        val e = elegida
        if (e != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF242424))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (e == 0) "¡Correcto!" else "No es correcto",
                        color = if (e == 0) Color(0xFF81C784) else Color(0xFFE57373),
                        fontWeight = FontWeight.Bold
                    )
                    Text(p.explicacion, color = Color.LightGray, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Button(
                onClick = { indice++; elegida = null },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text(if (indice + 1 < n) "Siguiente" else "Ver resultado") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

fun colorNota(pct: Int) = when {
    pct >= 80 -> Color(0xFF81C784)
    pct >= 50 -> Color(0xFFFFD54F)
    else -> Color(0xFFE57373)
}
