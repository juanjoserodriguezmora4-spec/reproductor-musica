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
  audio/AudioEffects.kt    Cadena de AudioProcessor + RenderersFactory + ParametricEq (motor)
  audio/Biquad.kt          Fórmulas de los filtros (Audio EQ Cookbook) y respuesta en dB
  audio/EqSettings.kt      Ajustes del ecualizador (inmutables), presets, guardar/leer como texto
  data/EqRepository.kt     Estado del ecualizador: lo guarda (DataStore "equalizer") y lo aplica
  ui/equalizer/            Pantalla del ecualizador (visor oscuro con curva, faders, perillas)
  ui/settings/FolderPicker Elegir carpetas de música (overlay desde Biblioteca o Ajustes)
  ui/playlists/            Pestaña Listas: favoritas, más escuchadas, recientes y listas propias
  ui/sheets/Sheets.kt      Hojas de acciones (menú de canción, añadir a lista, nombrar lista,
                           menú de lista, temporizador). Se abren con LocalSheets.current.open(...)
                           y las dibuja SheetHost al final de BismoRoot, encima de todo
  ui/common/Reorder.kt     Reordenar arrastrando el asa ≡ (filas de alto fijo)
  data/db/Playlists.kt     Tablas playlists, playlist_tracks, favorites, play_stats + PlaylistDao
  library/PlaylistRepository  Listas, favoritas, conteo de reproducciones
  player/PlayCounter       Cuenta una reproducción a los 30 s (o la mitad si es más corta)
  player/SleepTimer        Temporizador con fundido de 20 s; también "al terminar la canción"
  player/Crossfader        Fundido entre canciones con un segundo ExoPlayer (ver Fase 3)
  library/LyricsParser     LRC + letras incluidas en el archivo (ID3 USLT, Vorbis LYRICS, M4A ©lyr)
  library/LyricsRepository Busca la letra: .lrc al lado → carpeta de letras (SAF) → incluida
  ui/player/LyricsView     Letra en el lugar de la carátula; la línea actual se ilumina
  widget/                  Widget 4×1 (RemoteViews): PlayerWidget, WidgetUpdater, WidgetSync
  data/Settings.kt         DataStore: tema, color, reducir transparencia, carpetas de música
  data/db/BismoDatabase.kt Room: TrackEntity, consultas de álbumes/artistas/carpetas
  library/                 Escaneo de MediaStore → Room (filtrado por carpetas), permiso de audio, carátulas
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
| Acento           | Según el tema de color (Bruma: #4B5BFF)     | Según el tema de color (Bruma: #8B98FF)          |
| Esquinas         | Tarjetas 20 dp, botones 14 dp, barra inferior 26 dp | Iguales                                  |

Temas de color (`COLOR_THEMES` en `Theme.kt`): Bruma, Océano, Atardecer, Bosque, Uva,
Cereza, Grafito y "Fondo de pantalla" (Material You, Android 12+). Cada tema solo define
acento, `onAccent` y las 4 manchas en claro y oscuro; el resto del vidrio es igual.
Encima del acento siempre se usa `c.onAccent`, nunca `Color.White` fijo.
`LocalBismoDark` es la versión oscura del tema actual (la usa el reproductor).

Reglas:
- "Reducir transparencia": paneles 100 % opacos sin blur. Android 8 a 11 sin esa opción:
  paneles al 85 % sin blur (`LocalSolid`, colores `solid`).
- Solo las barras flotantes (barra inferior y mini reproductor) desenfocan de verdad;
  las tarjetas de las listas solo tienen detrás las manchas del fondo, que ya están
  difuminadas, así que usan el tinte translúcido y la app va fluida en celulares viejos.
- Sin carátula: degradado de Bruma según el nombre del álbum (`gradientFor`).
- Carátulas: siempre por canción con `trackArtUri(trackId, albumId)` (esquema `bismoart://`),
  nunca con `albumArtUri` directo. `ArtLoader` prueba: miniatura de Android (10+) → imagen
  incrustada en el archivo → carátula del álbum. Coil lo lee con `TrackArtFetcher` (registrado
  en `BismoApp.newImageLoader`) y Media3 con `TrackArtBitmapLoader` (en `PlaybackService`).
  Motivo: las descargas de Bruma no traen álbum y Android las junta en un solo "álbum" con
  el nombre de la carpeta, así que la carátula del álbum sale vacía o repetida.
- Por eso, para cada canción se usa `TrackEntity.artUri()` y `artKey()`: el respaldo de la
  carátula del álbum y el degradado compartido solo aplican si `hasRealAlbum()` (el álbum no
  es "Álbum desconocido" ni el nombre de la carpeta). `gradientFor` arma el degradado con un
  tono sacado del nombre (HSV), así no se repite entre canciones.
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

**Fase 2**
- 2a (hecha) Ecualizador paramétrico propio en `ParametricEq`:
  - 10 bandas (31 Hz–16 kHz) con frecuencia, ganancia (±15 dB), Q y forma (campana,
    estante de graves, estante de agudos) + perillas de graves (100 Hz) y agudos (10 kHz)
  - Preamplificador y limitador suave (tanh desde el 90 %) para que no sature
  - Acepta PCM 16 bits y float; calcula en Double con forma directa II transpuesta
  - Los ajustes llegan desde la UI con `update()` (objeto inmutable + @Volatile); el hilo
    de audio recalcula coeficientes cuando cambia la versión, la frecuencia o los canales
  - Presets de fábrica; cualquier cambio a mano pasa a "Personalizado"
  - Pantalla (rediseño 0.3): visor oscuro tipo equipo de sonido con lectura de la banda
    elegida (cifras tabulares, `tnum`) y la curva con brillo; faders de consola con marcas
    cada 6 dB; editor de banda y tono con perillas (arrastrar arriba/abajo, doble toque =
    valor normal); paneles con `glass(strong = true)` para que se lea mejor
- Biblioteca: elegir carpetas de música (todas o solo las marcadas, con subcarpetas);
  el filtro se aplica en `LibraryRepository.scan()`
- 2b (hecha) Listas de reproducción, favoritas, más escuchadas y recientes:
  - Base de datos versión 2. Las tablas del usuario NO tienen llave foránea hacia `tracks`
    (se rellena en cada escaneo); las consultas hacen JOIN, así que una canción que ya no
    está simplemente no aparece. Desde la v2, cualquier cambio de tablas necesita una
    `Migration` (solo la v1 se borra sola: `fallbackToDestructiveMigrationFrom(true, 1)`)
  - Sin "INSERT ... ON CONFLICT DO UPDATE": el SQLite de Android 8 no lo soporta
  - Menú ⋯ en cada canción: reproducir después, añadir a la cola, favorita, añadir a lista
  - En el reproductor: corazón, añadir a lista y temporizador en la fila de abajo
- 2c (hecha) Cola reordenable (asa ≡), temporizador para dormir y búsqueda en la
  Biblioteca (sin tildes, varias palabras a la vez: canciones, artistas y álbumes)
- Pendiente del ecualizador: presets propios con nombre, onda real en el reproductor

**Fase 3 (hecha)**
- Ecualizador: la curva ya no se arrastra; tocar un punto solo elige la banda (se edita
  con los faders y las perillas)
- Gapless: lo hace ExoPlayer solo (lee los datos de relleno de MP3/M4A). No poner nada que
  vacíe el AudioProcessor entre canciones
- Crossfade (Ajustes → Reproducción: apagado, 3, 6 o 10 s): `Crossfader` crea un segundo
  ExoPlayer con la siguiente canción, cruza volúmenes con curva de igual potencia y al final
  el principal salta (seekTo) a esa canción en la misma posición. Se cancela al pausar,
  saltar, mover la barra o cambiar la cola, y no se usa con el temporizador "al terminar la
  canción". Cada reproductor necesita su propio `ParametricEq`: `AudioEffects.newProcessors()`
- Widget 4×1: el servicio lo actualiza (`WidgetSync`); los botones mandan un broadcast a
  `PlayerWidget`, que usa `PlayerConnection.command`. Sin cola, todo el widget abre la app
- Letras: botón en la barra de arriba del reproductor. Android 11+ no deja leer .lrc sueltos,
  por eso existe la "carpeta de letras" (OpenDocumentTree con permiso persistente)

**Después de la Fase 3**
- Biblioteca: deslizar hacia abajo vuelve a escanear (`PullToRefreshBox` en
  `SectionsWithRefresh`; el indicador usa `library.scanning`, así que también sale en el
  escaneo automático al abrir)
- Ícono nuevo: corchea grande de vidrio (blanca translúcida, un solo trazado) sobre fondo
  rojo desvanecido (#B3122E → #E5384F → #FF9AA8 con brillo blanco arriba)

**Ideas para después**
- Reanudar la última cola al abrir (onPlaybackResumption) y que el widget pueda arrancarla
- Motor nativo (C++/Oboe), onda real en el reproductor, presets propios del ecualizador

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
