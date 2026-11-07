# Simulador Foto

App Android para aprender fotografía con una foto real de una plaza con tres personas a 1 m, 3 m y 10 m.
La foto está separada en capas (las tres personas y el suelo por franjas de distancia), así que el desenfoque de cada parte depende de su distancia real.

- **Objetivo**: zoom de 24 a 200 mm (formato completo), con botones o pellizcando con dos dedos. Manteniendo pulsado un botón 3 s se puede poner otra focal en ese rango. Cambia el encuadre, la profundidad de campo y la velocidad mínima a pulso (regla de 1/focal). Con zoom, la foto se mueve arrastrando con dos dedos.
- **Velocidad**: si es más lenta que 1/focal, la foto sale movida por el pulso, salvo que se active el **trípode**.
- **Diafragma**: cambia la profundidad de campo.
- **ISO**: añade grano.
- **Enfoque**: toca cualquier punto de la foto (una persona o el suelo).
- **Exposición**: en la esquina, en pasos (EV) respecto a la correcta (1/125 s, f/5,6, ISO 100). Al tocarla aparece el histograma; al tocar el histograma vuelve.

Requiere Android 12 o superior. Cada push a `main` compila el APK con GitHub Actions y lo publica en *Releases*.
