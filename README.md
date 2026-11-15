# Simulador Foto

App Android para aprender fotografía con una foto real de una plaza con tres personas a 1 m, 3 m y 10 m.
La foto está separada en capas (las tres personas y el suelo), así que el desenfoque de cada parte depende de su distancia real.

- **Objetivo**: zoom de 24 a 200 mm (formato completo), con botones o pellizcando con dos dedos. Manteniendo pulsado un botón 3 s se puede poner otra focal en ese rango. Cambia el encuadre, la profundidad de campo y la velocidad mínima a pulso (regla de 1/focal). La foto se mueve arrastrando con un dedo.
- **Velocidad**: si es más lenta que 1/focal, la foto sale movida por el pulso, salvo que se active el **trípode**.
- **Diafragma**: cambia la profundidad de campo. Bajo la foto se lee qué distancias quedan nítidas.
- **ISO**: añade grano, más visible cuanto más alto.
- **Modos**: M (manual), A (prioridad de diafragma: la cámara pone la velocidad) y S (prioridad de velocidad: la cámara pone el diafragma).
- **Enfoque**: toca cualquier punto de la foto (una persona o el suelo).
- **Exposición**: exposímetro bajo la foto, en pasos (EV) respecto a la correcta (1/125 s, f/5,6, ISO 100). Cada posición de velocidad, diafragma o ISO es un paso exacto.
- **Histograma**: se abre tocando el exposímetro. Al tocarlo cambia entre RGB superpuesto, luminancia y los tres canales por separado. Marca en las esquinas si hay sombras sin detalle o luces quemadas.
- **Opciones**: balance de blancos, rejilla de tercios, aviso de luces quemadas sobre la foto y restablecer la cámara.

En horizontal la foto queda a la izquierda y los tres controles de la exposición a la derecha, siempre a la vista.

## Desenfoque

Con Android 13 o superior el desenfoque es de objetivo: cada punto fuera de foco se abre en un disco del tamaño de su círculo de confusión (`Efectos.kt`, en la tarjeta gráfica). El fondo es una sola capa en la que el desenfoque cambia de forma continua con la distancia del suelo. En Android 12 se usa el desenfoque gaussiano del sistema, con el fondo repartido en franjas.

## Código

- `Camara.kt`: valores de la cámara, geometría de la foto, estado y cálculos.
- `Efectos.kt`: revelado (exposición, balance de blancos, grano, avisos) y desenfoque de objetivo.
- `Histograma.kt`: histogramas y exposímetro.
- `Controles.kt`: aspecto, diales, opciones.
- `MainActivity.kt`: pantalla y foto.
- `Cuestionarios.kt`: 1000 preguntas en 7 temas.

Requiere Android 12 o superior. Cada push a `main` compila el APK con GitHub Actions y lo publica en *Releases*. Un push a una rama `prueba-*` lanza las pruebas en emulador sin publicar nada.
