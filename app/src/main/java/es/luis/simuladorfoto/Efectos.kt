package es.luis.simuladorfoto

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.pow

// ---------- Revelado: exposición, balance de blancos, grano y avisos ----------
// Como en una cámara: la luz se multiplica en lineal y las altas luces se comprimen
// (hombro) antes de quemarse. Se aplica sobre la foto ya desenfocada, igual que el sensor.
// El grano se suma antes de la curva y crece con la raíz de la luz, como el ruido de un sensor.
const val HOMBRO = 0.85f
const val TECHO = 1.05f

const val AGSL_REVELADO = """
uniform shader contenido;
uniform float3 ganancia;
uniform float grano;
uniform float celda;
uniform float avisos;

float azar(float2 p) {
    return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
}

half4 main(float2 p) {
    half4 c = contenido.eval(p);
    float3 lin = float3(toLinearSrgb(c.rgb)) * ganancia;
    if (grano > 0.0) {
        float2 g = floor(p / celda);
        float luz = azar(g) + azar(g + float2(31.7, 5.3)) + azar(g + float2(67.3, 19.1)) - 1.5;
        float3 color = float3(azar(g + float2(3.1, 41.7)), azar(g + float2(7.7, 53.9)), azar(g + float2(11.9, 87.3))) - float3(0.5);
        lin = lin + grano * sqrt(lin + float3(0.003)) * (float3(luz * 1.1) + color * 0.7);
        lin = max(lin, float3(0.0));
    }
    float3 hombro = float3(0.85) + 0.2 * (float3(1.0) - exp(-(lin - float3(0.85)) / 0.2));
    lin = mix(lin, hombro, step(float3(0.85), lin));
    float3 fin = float3(fromLinearSrgb(half3(min(lin, float3(1.0)))));
    if (avisos > 0.5) {
        float raya = step(0.5, fract((p.x + p.y) / (celda * 9.0)));
        float alta = step(0.985, min(fin.r, min(fin.g, fin.b)));
        float baja = step(max(fin.r, max(fin.g, fin.b)), 0.035);
        fin = mix(fin, float3(0.90, 0.10, 0.10), alta * raya);
        fin = mix(fin, float3(0.15, 0.35, 1.00), baja * raya);
    }
    return half4(half3(fin), c.a);
}
"""

// Cuánto grano hay a cada ISO (0 a ISO 100)
fun granoDe(iso: Int): Float = if (iso <= 100) 0f else 0.027f * kotlin.math.sqrt(iso / 100f)

object Revelado {
    private var probado = false
    private var hayCurva = false
    private var programa: Any? = null

    // La curva necesita Android 13; en Android 12 se usa una multiplicación simple
    fun conCurva(): Boolean {
        if (!probado) {
            probado = true
            hayCurva = Build.VERSION.SDK_INT >= 33 && compila()
        }
        return hayCurva
    }

    @RequiresApi(33)
    private fun compilaShader() {
        programa = RuntimeShader(AGSL_REVELADO)
    }

    private fun compila(): Boolean = try {
        if (Build.VERSION.SDK_INT >= 33) { compilaShader(); true } else false
    } catch (e: Throwable) {
        false
    }

    fun efecto(ev: Float, wb: Blancos, iso: Int, celda: Float, avisos: Boolean): androidx.compose.ui.graphics.RenderEffect? =
        if (conCurva() && Build.VERSION.SDK_INT >= 33) efecto33(ev, wb, iso, celda, avisos) else null

    @RequiresApi(33)
    private fun efecto33(ev: Float, wb: Blancos, iso: Int, celda: Float, avisos: Boolean): androidx.compose.ui.graphics.RenderEffect {
        val sh = programa as RuntimeShader
        val f = 2f.pow(ev)
        sh.setFloatUniform("ganancia", f * wb.r, f * wb.g, f * wb.b)
        sh.setFloatUniform("grano", granoDe(iso))
        sh.setFloatUniform("celda", celda.coerceAtLeast(1f))
        sh.setFloatUniform("avisos", if (avisos) 1f else 0f)
        return android.graphics.RenderEffect.createRuntimeShaderEffect(sh, "contenido").asComposeRenderEffect()
    }

    // Lo mismo que hace la pantalla, para un valor sRGB de 0 a 1 y la ganancia de su canal
    fun aplica(v: Float, ev: Float, ganancia: Float = 1f): Float {
        if (!conCurva()) return (v * (2f.pow(ev) * ganancia).pow(1f / 2.2f)).coerceAtMost(1f)
        var l = aLineal(v) * 2f.pow(ev) * ganancia
        if (l > HOMBRO) l = HOMBRO + (TECHO - HOMBRO) * (1f - kotlin.math.exp(-(l - HOMBRO) / (TECHO - HOMBRO)))
        return aSrgb(l.coerceAtMost(1f))
    }

    private fun aLineal(x: Float) = if (x <= 0.04045f) x / 12.92f else ((x + 0.055f) / 1.055f).pow(2.4f)
    private fun aSrgb(x: Float) = if (x <= 0.0031308f) x * 12.92f else 1.055f * x.pow(1f / 2.4f) - 0.055f
}

// ---------- Desenfoque de objetivo ----------
// Un objetivo no difumina como una gaussiana: cada punto fuera de foco se convierte en un disco
// del tamaño del círculo de confusión. Aquí cada píxel reúne la luz de un disco de ese radio
// (muestras en espiral), sumando en lineal para que las luces altas se abran como en una foto real.
// Una segunda pasada, con un disco pequeño, rellena los huecos entre muestras.
//
// Para las personas el radio es fijo (todas sus partes están a la misma distancia).
// Para el fondo el radio cambia en cada fila: el suelo está más lejos cuanto más arriba.
private fun fuenteBokeh(muestras: Int, suelo: Boolean): String {
    val radio = if (suelo) """
uniform float oy;
uniform float s;
uniform float k;
uniform float enfoque;
uniform float tope;

float radio(float2 p) {
    float fila = (p.y - oy) / s;
    float d = $DISTANCIA_EDIFICIOS;
    if (fila > ${HORIZONTE + KH / DISTANCIA_EDIFICIOS}) {
        d = clamp($KH / (fila - $HORIZONTE), 1.5, $DISTANCIA_EDIFICIOS);
    }
    return min(k * abs(d - enfoque) / d, tope);
}
""" else """
uniform float radioFijo;

float radio(float2 p) {
    return radioFijo;
}
"""
    return """
uniform shader contenido;
uniform float2 tam;
uniform float escala;
$radio
half4 main(float2 p) {
    float r = radio(p) * escala;
    if (r < 0.4) {
        return contenido.eval(p);
    }
    float3 suma = float3(0.0);
    float alfa = 0.0;
    float peso = 0.0;
    for (int i = 0; i < $muestras; i++) {
        float fi = float(i) + 0.5;
        float rr = sqrt(fi / $muestras.0) * r;
        float ang = fi * 2.39996323;
        float2 q = clamp(p + rr * float2(cos(ang), sin(ang)), float2(0.5), tam - float2(0.5));
        float4 c = float4(contenido.eval(q));
        float a = max(c.a, 0.001);
        float3 lin = c.rgb * c.rgb / a;
        float luz = dot(lin, float3(0.2126, 0.7152, 0.0722)) / a;
        float w = 1.0 + 4.0 * smoothstep(0.8, 1.0, luz);
        suma += lin * w;
        alfa += c.a;
        peso += c.a * w;
    }
    float alfaFinal = alfa / $muestras.0;
    float3 color = sqrt(suma / max(peso, 0.0001));
    return half4(half3(color * alfaFinal), half(alfaFinal));
}
"""
}

object Bokeh {
    private var probado = false
    private var ok = false
    private var programas: Any? = null

    @RequiresApi(33)
    private class Programas {
        // Calidad completa: 48 muestras + 12 de relleno. Rápida (mientras se mueve la foto): 20 + 8
        val fijo1 = RuntimeShader(fuenteBokeh(48, false))
        val fijo2 = RuntimeShader(fuenteBokeh(12, false))
        val fijoRapido1 = RuntimeShader(fuenteBokeh(20, false))
        val fijoRapido2 = RuntimeShader(fuenteBokeh(8, false))
        val suelo1 = RuntimeShader(fuenteBokeh(48, true))
        val suelo2 = RuntimeShader(fuenteBokeh(12, true))
        val sueloRapido1 = RuntimeShader(fuenteBokeh(20, true))
        val sueloRapido2 = RuntimeShader(fuenteBokeh(8, true))
    }

    @RequiresApi(33)
    private fun crea() {
        programas = Programas()
    }

    // Necesita Android 13; si no, se usa el desenfoque gaussiano del sistema
    fun disponible(): Boolean {
        if (!probado) {
            probado = true
            ok = try {
                if (Build.VERSION.SDK_INT >= 33) { crea(); true } else false
            } catch (e: Throwable) {
                android.util.Log.e("Bokeh", "No compila el desenfoque de objetivo", e)
                false
            }
        }
        return ok
    }

    @RequiresApi(33)
    private fun cadena(p1: RuntimeShader?, p2: RuntimeShader?, trepX: Float, trepY: Float): android.graphics.RenderEffect? {
        var e: android.graphics.RenderEffect? = null
        if (p1 != null) e = android.graphics.RenderEffect.createRuntimeShaderEffect(p1, "contenido")
        if (p2 != null && e != null) {
            e = android.graphics.RenderEffect.createChainEffect(
                android.graphics.RenderEffect.createRuntimeShaderEffect(p2, "contenido"), e
            )
        }
        // Trepidación: el temblor de la mano alarga la imagen, más en horizontal que en vertical
        if (trepX > 0.5f) {
            val ry = trepY.coerceAtLeast(0.1f)
            e = if (e != null) android.graphics.RenderEffect.createBlurEffect(trepX, ry, e, Shader.TileMode.CLAMP)
                else android.graphics.RenderEffect.createBlurEffect(trepX, ry, Shader.TileMode.CLAMP)
        }
        return e
    }

    // Capa con todo a la misma distancia (una persona)
    @RequiresApi(33)
    fun fijo(radio: Float, w: Float, h: Float, trepX: Float, trepY: Float, rapido: Boolean): android.graphics.RenderEffect? {
        val p = programas as Programas
        if (radio < 0.4f) return cadena(null, null, trepX, trepY)
        val p1 = if (rapido) p.fijoRapido1 else p.fijo1
        val p2 = if (rapido) p.fijoRapido2 else p.fijo2
        val relleno = if (rapido) 0.38f else 0.25f
        p1.setFloatUniform("tam", w, h)
        p1.setFloatUniform("escala", 1f)
        p1.setFloatUniform("radioFijo", radio)
        p2.setFloatUniform("tam", w, h)
        p2.setFloatUniform("escala", relleno)
        p2.setFloatUniform("radioFijo", radio)
        return cadena(p1, p2, trepX, trepY)
    }

    // El fondo: el desenfoque depende de la fila (distancia del suelo)
    // k: radio en píxeles de pantalla de un punto en el infinito; oy y s sitúan la foto en la pantalla
    @RequiresApi(33)
    fun suelo(
        k: Float, enfoque: Float, tope: Float, oy: Float, s: Float, w: Float, h: Float,
        trepX: Float, trepY: Float, rapido: Boolean
    ): android.graphics.RenderEffect? {
        val p = programas as Programas
        val p1 = if (rapido) p.sueloRapido1 else p.suelo1
        val p2 = if (rapido) p.sueloRapido2 else p.suelo2
        val relleno = if (rapido) 0.38f else 0.25f
        for ((sh, escala) in listOf(p1 to 1f, p2 to relleno)) {
            sh.setFloatUniform("tam", w, h)
            sh.setFloatUniform("escala", escala)
            sh.setFloatUniform("oy", oy)
            sh.setFloatUniform("s", s)
            sh.setFloatUniform("k", k)
            sh.setFloatUniform("enfoque", enfoque)
            sh.setFloatUniform("tope", tope)
        }
        return cadena(p1, p2, trepX, trepY)
    }
}
