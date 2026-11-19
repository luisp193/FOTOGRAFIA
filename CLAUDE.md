# Reglas del proyecto

## Numeración de versiones (obligatoria)
- El número de versión es el **número de compilación de GitHub Actions** (`github.run_number`): ejecución #7 = **V7**.
- `versionCode` es ese número y `versionName` es `V<n>`. Las versiones publicadas (release/etiqueta) se llaman `V<n>`.
- El artefacto de cada compilación se llama `SimuladorFoto_V<n>`. El APK publicado conserva el nombre fijo `simulador-foto.apk` porque la web luis-gloria.com lo descarga de la última release: no lo renombres sin actualizar también la web.
- No pongas números de versión a mano ni inventes otro esquema (1.0.x, version.txt, etc.).
- Cada app Android nueva de Luis lleva esta misma regla desde el primer commit.
