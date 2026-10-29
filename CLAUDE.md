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
  ui/library, ui/player, ui/settings, ui/soon
```

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
- Reproductor completo (hoja que sube): carátula, título, artista, progreso,
  play/pausa, siguiente/anterior, aleatorio, repetir; colores de fondo con Palette
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
- Cola visible y editable, temporizador de apagado, búsqueda

## Compilar

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/`. GitHub Actions compila un APK debug en
cada push a cualquier rama y lo sube como artifact (`.github/workflows/build.yml`).
En los pushes a `main` además lo publica en GitHub Releases (etiqueta `build-<número>`)
para descargarlo desde el celular.

En este equipo (Windows, JDK 25) Java no puede crear sus sockets locales en la carpeta
temporal por defecto; si Gradle falla con "Unable to establish loopback connection":

```powershell
$env:JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:\Users\Public"
```
