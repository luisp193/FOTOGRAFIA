package es.luis.simuladorfoto

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestosTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun antes() {
            Pruebas.sinDesenfoque = true
        }
    }

    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    // El emulador no tiene tarjeta gráfica y el desenfoque tarda: la captura no debe tumbar la prueba
    private fun captura(nombre: String) {
        try {
            val img = regla.onRoot().captureToImage().asAndroidBitmap()
            TestStorage().openOutputFile("$nombre.png").use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) {
            println("CAPTURA $nombre no disponible: ${e.message}")
        }
    }

    private fun textoObjetivo(): String {
        val nodos = regla.onAllNodesWithText("Objetivo:", substring = true).fetchSemanticsNodes()
        return nodos.joinToString { n ->
            n.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.Text) { emptyList() }
                .joinToString { it.text }
        }
    }

    @Test
    fun pellizcarAmplia() {
        captura("1_inicio")
        val antes = textoObjetivo()
        regla.onNodeWithTag("foto").performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f), end0 = center - Offset(300f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(300f, 0f),
                durationMillis = 800
            )
        }
        regla.waitUntil(timeoutMillis = 60_000) { textoObjetivo() != antes }
        val despues = textoObjetivo()
        captura("2_tras_pellizco")
        println("OBJETIVO antes=[$antes] despues=[$despues]")
        assertNotEquals("El pellizco no cambia el objetivo", antes, despues)
    }

    private fun encuadre(): String =
        regla.onNodeWithTag("foto").fetchSemanticsNode().config
            .getOrElse(androidx.compose.ui.semantics.SemanticsProperties.StateDescription) { "" }

    @Test
    fun arrastrarConUnDedo() {
        regla.onNodeWithText("50 mm").performClick()
        regla.waitUntil(timeoutMillis = 30_000) { textoObjetivo().contains("50 mm") }
        val antes = encuadre()
        // arrastrar empezando sobre el hombre de 1 m: no debe enfocarlo, solo mover la foto
        regla.onNodeWithTag("foto").performTouchInput {
            swipe(center + Offset(-250f, 0f), center + Offset(150f, 0f), durationMillis = 600)
        }
        regla.waitUntil(timeoutMillis = 30_000) { encuadre() != antes }
        println("ENCUADRE antes=[$antes] despues=[${encuadre()}]")
        assertNotEquals("Arrastrar con un dedo no mueve la foto", antes, encuadre())
        assertTrue("El zoom ha cambiado al arrastrar", textoObjetivo().contains("50 mm"))
        assertEquals("Arrastrar no debe cambiar el enfoque", 1,
            regla.onAllNodesWithText("Enfoque: 3 m", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun histogramaQuemado() {
        // La curva de la cámara tiene que compilar en Android 13 o superior
        assertTrue("La curva de exposición no compila", Revelado.conCurva())
        val ctx = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val rgb = ctx.assets.open("rgb.bin").use { it.readBytes() }
        val centro = Offset(FOTO_W / 2, FOTO_H / 2)
        fun derecha(ev: Float): Float {
            val h = histogramaVisible(rgb, centro, 1f, ev)
            return (252..255).sumOf { h[it] }.toFloat() / h.sum()
        }
        println("HISTOGRAMA derecha a 0 EV=${derecha(0f)} a +1=${derecha(1f)} a +2,9=${derecha(2.9f)}")
        assertTrue("A 0 EV no debería haber casi nada quemado", derecha(0f) < 0.05f)
        assertTrue("A +2,9 EV casi todo debería estar a la derecha", derecha(2.9f) > 0.6f)
    }

    @Test
    fun cuestionario() {
        regla.onNodeWithText("Cuestionarios").performClick()
        captura("4_temas")
        regla.onNodeWithText("Velocidad y movimiento").performClick()
        captura("5_pregunta")
        // responde la primera opción y comprueba que sale la explicación
        regla.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        regla.onNodeWithText("Pregunta 1 de 10", substring = true).assertExists()
        val botones = regla.onAllNodes(hasClickAction())
        botones[botones.fetchSemanticsNodes().size - 1].performClick()
        captura("6_respuesta")
        regla.onNodeWithText("Siguiente").assertExists()
    }

    @Test
    fun tocarEnfoca() {
        // Tocar al hombre de la izquierda (primer plano, 1 m)
        regla.onNodeWithTag("foto").performTouchInput {
            // la foto ocupa el centro de la zona; el hombre tiene la cara en (430, 330) de la foto
            val dw = minOf(width.toFloat(), height * FOTO_W / FOTO_H)
            val dh = dw * FOTO_H / FOTO_W
            click(Offset((width - dw) / 2 + 430f / FOTO_W * dw, (height - dh) / 2 + 330f / FOTO_H * dh))
        }
        captura("3_enfoque_1m")
        val enfoque = regla.onAllNodesWithText("Enfoque: 1 m", substring = true).fetchSemanticsNodes().size
        assertEquals("No enfoca a 1 m al tocar al hombre", 1, enfoque)
    }
}
