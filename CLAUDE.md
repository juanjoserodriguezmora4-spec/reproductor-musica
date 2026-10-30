# BismoPower

Reproductor de música local para Android, con funciones al estilo de Poweramp y el
diseño de vidrio esmerilado de la app hermana **Bruma**
(https://github.com/juanjoserodriguezmora4-spec/Bruma). Las dos apps deben verse de la
misma familia: cualquier componente nuevo se basa primero en lo que ya existe en
`ui/theme` y `ui/common`.

Textos de la interfaz, comentarios y mensajes de commit en español.

## Stack

- Kotlin, Jetpack Compose (Material 3 solo como base; el aspecto es propio)
- Media3: ExoPlayer + `MediaSessionService` (notificación y pantalla de bloqueo)
- Room (caché de la biblioteca escaneada), DataStore (ajustes)
- Haze 1.7 (desenfoque), Coil 2 (carátulas), Palette (colores de la carátula)
- minSdk 26, targetSdk/compileSdk 37
- AGP 9.5 (Kotlin integrado: **no** se aplica `org.jetbrains.kotlin.android`),
  Kotlin 2.3, KSP 2.3, Gradle 9.8
- Sin Hilt: `AppContainer` en `BismoApp.kt` es el contenedor de dependencias

## Estructura

```
app/src/main/java/com/bismopower/app/
  BismoApp.kt              AppContainer (db, ajustes, biblioteca, reproductor, efectos)
  audio/AudioEffects.kt    Cadena de AudioProcessor + RenderersFactory (gancho de la Fase 2)
  data/Settings.kt         DataStore: tema, reducir transparencia
  data/db/BismoDatabase.kt Room: TrackEntity, consultas de álbumes/artistas/carpetas
  library/                 Escaneo de MediaStore → Room, permiso de audio, URI de carátulas
  player/PlaybackService   MediaSessionService con ExoPlayer
  player/PlayerConnection  MediaController + StateFlow<PlayerUiState> para la UI
  ui/theme/Theme.kt        Colores, tipografía Inter, LocalSolid
  ui/common/Glass.kt       Modifier.glass(), glassSegment(), GlassBackground, Thumb, gradientFor
  ui/common/Bars.kt        Barra inferior flotante y mini reproductor
  ui/common/Icons.kt       Íconos de línea (los mismos de Bruma + extras de música)
  ui/BismoRoot.kt          Pestañas, capa de Haze, hoja del reproductor
  ui/player/PlayerScreen   Reproductor a pantalla completa con gestos (ver abajo)
  ui/player/WaveSeekBar    Barra de progreso en forma de onda
  ui/player/QueueSheet     Cola que sube encima del reproductor
  ui/player/PlayerColors   Acento y brillos sacados de la carátula (Palette)
  ui/library, ui/settings, ui/soon
```

## Reproductor: gestos y animaciones (estilo Poweramp, identidad propia)

El reproductor imita la forma de usar Poweramp (gestos, animaciones, distribución), no su
aspecto: no se copian su logo, nombre, skins ni íconos. Siempre es oscuro, aunque la app
esté en modo claro, con la carátula desenfocada de fondo (Android 12+) y el acento de la
canción en los controles.

| Dónde | Gesto | Acción |
|---|---|---|
| Carátula | Deslizar izquierda / derecha | Siguiente / anterior (la carátula sigue al dedo, sale volando y la nueva entra con rebote) |
| Carátula | Deslizar arriba | Abrir la cola |
| Carátula o pantalla | Deslizar abajo | Cerrar el reproductor |
| Carátula | Un toque | Pausa / reproducir (ícono grande que aparece y se desvanece) |
| Carátula | Doble toque izquierda / derecha | −10 s / +10 s |
| Anterior / siguiente | Mantener | Retroceder / adelantar 5 s cada 180 ms |
| Onda | Tocar o arrastrar | Saltar a ese punto (burbuja con el tiempo) |
| Mini reproductor | Deslizar izquierda / derecha | Siguiente / anterior |
| Mini reproductor | Tocar o deslizar arriba | Abrir el reproductor |

- La onda todavía es decorativa (se genera con el id de la canción). La onda real, leída
  del audio, queda para una fase posterior.
- Pausado, la carátula se encoge al 88 %; los botones se hunden al tocarlos y rebotan.
- Los gestos de carátula usan `nextTrack()`/`previousTrack()` de `PlayerConnection`
  (cambian de canción siempre); el botón "anterior" usa `previous()`, que primero
  reinicia la canción si lleva más de unos segundos.

## Sistema de diseño (vidrio esmerilado en tres capas)

1. **Fondo**: color base con 4 manchas grandes muy difuminadas (degradados radiales) —
   `GlassBackground`. En el reproductor las manchas toman los colores de la carátula.
2. **Paneles de vidrio**: tarjetas translúcidas, borde de 1 dp y sombra muy suave —
   `Modifier.glass()`. Las barras flotantes usan `blur = true` (Haze, 22 dp).
3. **Contenido**: texto de alto contraste, íconos de línea y un solo color de acento
   para lo que se puede tocar.

| Elemento         | Modo claro                                  | Modo oscuro                                      |
|------------------|---------------------------------------------|--------------------------------------------------|
| Fondo base       | #EEF1F7 + manchas azul, lila, rosa, menta   | #0B0F1A + manchas índigo, morado, verde petróleo |
| Panel de vidrio  | Blanco 62 %, blur 22 dp                     | Blanco 7 %, blur 22 dp                           |
| Borde            | Blanco 90 %                                 | Blanco 12 %                                      |
| Texto principal  | #121826                                     | #EEF1F8                                          |
| Texto secundario | #5B6475                                     | #9AA3B5                                          |
| Acento           | #4B5BFF                                     | #8B98FF                                          |
| Esquinas         | Tarjetas 20 dp, botones 14 dp, barra inferior 26 dp | Iguales                                  |

Reglas:
- Android 8 a 11 (sin desenfoque eficiente) o "Reducir transparencia": paneles sólidos
  al 85 % sin blur (`LocalSolid`, colores `solid`).
- Solo las barras flotantes (barra inferior y mini reproductor) desenfocan de verdad;
  las tarjetas de las listas solo tienen detrás las manchas del fondo, que ya están
  difuminadas, así que usan el tinte translúcido y la app va fluida en celulares viejos.
- Sin carátula: degradado de Bruma según el nombre del álbum (`gradientFor`).
- Tema claro/oscuro según el sistema, con opción en Ajustes.
- Tipografía Inter (la misma de Bruma) y estilos de texto en el objeto `T`.
- Animaciones cortas (150–320 ms) con `tween`/`animateColorAsState`; nada de efectos
  continuos ni sombras pesadas.

## Fases

**Fase 1 (hecha)**
- Permiso de audio (+ notificaciones en Android 13+) y escaneo con MediaStore
- Biblioteca con pestañas Canciones, Álbumes, Artistas, Carpetas y vistas de detalle
- Reproductor completo: carátula, título, artista, progreso,
  play/pausa, siguiente/anterior, aleatorio, repetir; colores de fondo con Palette
- (0.2.0) Reproductor rehecho con gestos y animaciones estilo Poweramp, cola visible
  (saltar y quitar canciones) y APK firmado
- Reproducción en segundo plano con controles en notificación y pantalla de bloqueo
- Listas y Ecualizador como pantallas "Próximamente"

**Fase 2 (siguiente)**
- Ecualizador paramétrico con filtros biquad: implementar `ParametricEq` en
  `audio/AudioEffects.kt` (ya está conectado al `DefaultAudioSink` de ExoPlayer vía
  `BismoRenderersFactory`; hoy devuelve `AudioFormat.NOT_SET` y queda inactivo).
  Aceptar PCM 16 bits y float, un biquad por banda y canal, recalcular coeficientes en
  `onFlush`. La pantalla del ecualizador cambia las bandas a través de
  `container.audioEffects.equalizer`.
- Listas de reproducción (Room), favoritas, más escuchadas
- Cola: reordenar arrastrando (ya se puede ver, saltar y quitar), temporizador de apagado, búsqueda

## Compilar

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. GitHub Actions compila un APK **release
firmado** en cada push a cualquier rama y lo sube como artifact (`.github/workflows/build.yml`).
En los pushes a `main` además lo publica en GitHub Releases (etiqueta `build-<número>`)
para descargarlo desde el celular.

Firma: el keystore (`bismopower.jks`, alias `bismopower`) NO está en el repositorio. Vive en
cuatro secretos de GitHub: `BISMO_KEYSTORE_BASE64`, `BISMO_KEYSTORE_PASSWORD`,
`BISMO_KEY_ALIAS`, `BISMO_KEY_PASSWORD`. Sin ellos el workflow avisa y firma con la clave de
depuración. Nunca cambiar de clave: Android no deja actualizar una app firmada con otra.

En este equipo (Windows, JDK 25) Java no puede crear sus sockets locales en la carpeta
temporal por defecto; si Gradle falla con "Unable to establish loopback connection":

```powershell
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\Users\Public"
```
