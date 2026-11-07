# Simulador Foto

App Android para aprender fotografía. Muestra una escena con tres personas a 1 m, 3 m y 10 m, y tres deslizadores:

- **Objetivo**: focal de 14 a 200 mm (formato completo). Hace zoom desde el mismo sitio, cambia la profundidad de campo y la velocidad mínima a pulso (regla de 1/focal).
- **Velocidad**: si es más lenta que 1/focal, la foto sale movida por el pulso. También aclara la imagen.
- **Diafragma**: cambia la profundidad de campo y la luz.
- **ISO**: aclara la imagen y añade ruido.

Toca una persona para enfocarla. La exposición correcta de referencia es 1/125 s, f/5.6 e ISO 100.

Requiere Android 12 o superior. Cada push a `main` compila el APK con GitHub Actions y lo publica en *Releases*.
