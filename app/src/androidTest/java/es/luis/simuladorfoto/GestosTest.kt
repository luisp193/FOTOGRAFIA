package es.luis.simuladorfoto

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performMultiTouchInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestosTest {
    @get:Rule
    val regla = createAndroidComposeRule<MainActivity>()

    private fun captura(nombre: String) {
        regla.waitForIdle()
        val img = regla.onRoot().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("$nombre.png").use { img.compress(Bitmap.CompressFormat.PNG, 100, it) }
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
        regla.onNodeWithTag("foto").performMultiTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f), end0 = center - Offset(300f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(300f, 0f),
                durationMillis = 800
            )
        }
        captura("2_tras_pellizco")
        val despues = textoObjetivo()
        println("OBJETIVO antes=[$antes] despues=[$despues]")
        assertNotEquals("El pellizco no cambia el objetivo", antes, despues)
    }

    @Test
    fun tocarEnfoca() {
        // Tocar al hombre de la izquierda (primer plano, 1 m)
        regla.onNodeWithTag("foto").performTouchInput {
            click(Offset(width * 0.16f, height * 0.35f))
        }
        captura("3_enfoque_1m")
        val enfoque = regla.onAllNodesWithText("Enfoque: 1 m", substring = true).fetchSemanticsNodes().size
        assertEquals("No enfoca a 1 m al tocar al hombre", 1, enfoque)
    }
}
