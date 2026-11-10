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
  audio/AudioEffects.kt    Motor: cadena de AudioProcessor (EQ → limitador → analizador → salida) + RenderersFactory
  audio/Dsp.kt             Las cuentas, sin Android: EqEngine, LookaheadLimiter, Dither
  audio/Loudness.kt        Medidor de sonoridad (LUFS, norma BS.1770) y cuánto subir/bajar cada canción
  library/LoudnessScanner  LoudnessStore (caché en archivo) + medición de canciones en segundo plano
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
  audio/AudioAnalyzer.kt   Copia en mono del audio (AnalyzerTap, después del ecualizador) para el visualizador
  ui/player/visualizer/    Spectrum (FFT 2048, 64 bandas), VizStyles (10 estilos + paletas), VisualizerView
  ui/library/SongSort.kt   Orden de Canciones (nombre, artista, álbum, recientes, duración, mezclada)
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
| Fondo base       | #EEF1F7 + manchas azul, lila, rosa, menta   | #050507 (casi negro) + manchas del tema al 38 %  |
| Panel de vidrio  | Blanco 62 %, blur 22 dp                     | Blanco 7 %, blur 22 dp                           |
| Borde            | Blanco 90 %                                 | Blanco 12 %                                      |
| Texto principal  | #121826                                     | #EEF1F8                                          |
| Texto secundario | #5B6475                                     | #A0A0A8                                          |
| Acento           | Según el tema de color (Bruma: #4B5BFF)     | Según el tema de color (Bruma: #8B98FF)          |
| Esquinas         | Tarjetas 20 dp, botones 14 dp, barra inferior 26 dp | Iguales                                  |

Temas de color (`COLOR_THEMES` en `Theme.kt`): Bruma, Océano, Atardecer, Bosque, Uva,
Cereza, Grafito y "Fondo de pantalla" (Material You, Android 12+). Cada tema solo define
acento, `onAccent` y las 4 manchas en claro y oscuro; el resto del vidrio es igual.
Encima del acento siempre se usa `c.onAccent`, nunca `Color.White` fijo.
`LocalBismoDark` es la versión oscura del tema actual (la usa el reproductor).
El modo oscuro es negro neutro, no azulado: paneles #141416, superficies #121214–#1C1C20,
hoja inferior #121214, fondo del reproductor #050507. Grafito oscuro usa manchas grises.

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
  - Preamplificador y limitador (ver "Motor de audio: calidad" más abajo)
  - Acepta PCM 16 bits y float; calcula en Double con forma directa II transpuesta
  - Los ajustes llegan desde la UI con `AudioEffects.update()` (objeto inmutable + @Volatile);
    el hilo de audio recalcula coeficientes cuando cambia el objeto, la frecuencia o los canales
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
  canción". Cada reproductor necesita su propia cadena de efectos: `AudioEffects.newChain()`
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

**Orden y visualizador**
- Canciones: el botón "Aleatorio" se cambió por un menú (`SortButton`). "Mezclar la lista"
  reordena la lista misma con una semilla guardada (`shuffleSeed`), y "Reproducir" la toca
  en ese orden con el modo aleatorio del reproductor apagado. Las demás listas siguen igual
- Visualizador sin permiso de micrófono: `AnalyzerTap` copia el audio dentro de ExoPlayer.
  El audio suena ~280 ms después de procesarse (`AudioAnalyzer.LATENCY_MS`); `Spectrum`
  mueve un "cabezal" a ritmo real y lo corrige hacia ese punto para que se vea suave.
  El reproductor del fundido no alimenta el analizador (`newChain(analyze = false)`)
- Estilos: Halo, Eclipse, Prisma, Aura, Línea, Barras, LED, Pulso, Cordillera, Brillo, Espejo
  (Anillo se quitó; un `vizStyle` desconocido cae en Halo). `VizFrame.slow` son las bandas
  muy suavizadas; `VizFrame.spin` es un ángulo que avanza más rápido con música fuerte (casi
  quieto en pausa). Línea dibuja la onda (`wave`) tal cual, como la primera versión (así
  le gusta al usuario).
  Eclipse = luz real alrededor de un disco negro: se pinta en una capa (`saveLayer`) con los
  colores de la paleta alrededor + núcleo blanco, y una máscara radial `BlendMode.DstIn` hecha
  con la suma de 4 caídas exponenciales (fuerte en el borde, cola larga hasta el fondo).
  Prisma = como Eclipse pero con 6 focos de color separados (azul, rosado, verde, rojo, blanco,
  morado; no usa la paleta) que giran despacio (`time·0.2`). Cada foco escucha un grupo de
  bandas (`PRISM_LIGHTS`) y el blanco además el golpe del bombo. Capa: anillo de colores
  mezclados por cercanía + focos con `BlendMode.Plus` + filo blanco + la misma máscara radial.
  Aura = estilo NoCopyrightSounds: carátula en círculo (CoverMode.CIRCLE), 120 barras blancas
  simétricas hacia afuera. `whiteGlow()` y `particles()` (resplandor blanco con los graves y
  partículas que se alejan con `spin`) se comparten: Aura usa `whiteGlow`, Halo usa
  `rimLight()` (luz blanca pegada al aro, misma caída que la del Eclipse) y las partículas.
  Eclipse reacciona al "golpe" de los graves: `bass = energy·1.15 + kick·1.6`, donde
  `kick = energy − promedio lento de los graves` (de `VizFrame.slow`).
  Paletas: Carátula, Neón, Lavanda, Fuego, Aurora, Hielo, Atardecer, Blanco y "Mi color"
  (CUSTOM: tono `vizCustomHue` 0–360 e intensidad `vizCustomSat` 0–1, elegidos con dos barras
  en `Sheet.Visualizer`; se guardan al soltar con `setVizCustom`). Se guardan en Ajustes
  (`vizStyle`, `vizPalette`) igual que la vista del centro del reproductor (`playerView`: COVER/LYRICS/VIZ)
- En el visualizador: deslizar a los lados cambia de estilo, un toque pausa, el botón de la
  esquina abre `Sheet.Visualizer` (miniaturas dibujadas con `PreviewFrame`)

**Borrar, seleccionar y repetidas**
- Borrar del teléfono (`ui/sheets/TrackDeleter.kt`, `LocalDeleter`): Android 11+ usa
  `MediaStore.createTrashRequest` (un solo aviso del sistema, papelera ~30 días, sin permiso);
  Android 8-10 confirma con `Sheet.ConfirmDelete` y borra con WRITE_EXTERNAL_STORAGE
  (maxSdk 29, `requestLegacyExternalStorage`). Al terminar: `library.forget(ids)` (tracks,
  listas, favoritas, conteos) y `player.removeIds`
- Selección múltiple (`ui/common/Selection.kt`, `LocalSelection`): mantener presionada una
  canción en Biblioteca (Canciones, álbum/artista/carpeta, búsqueda). Mientras hay marcadas,
  `SelectionBar` reemplaza al mini reproductor y a las pestañas; "atrás" la suelta. Cada
  pantalla pone su lista en `selection.visible` (para "Todas" y el orden)
- Repetidas (`library/Duplicates.kt`): mismo título (sin "(1)", "- copia"...) y artista,
  sin tildes, y duración ±2,5 s. Se queda el archivo más pesado. Con "Ocultar canciones
  repetidas" (por defecto encendido) el escaneo guarda solo una copia; la pantalla
  `DuplicatesScreen` deja borrar las demás. La cola tampoco repite: `addToQueue` salta las
  que ya están y `playNext` mueve la canción si ya estaba

**Álbumes a mano**
- Tabla `album_overrides` (base de datos versión 3, `MIGRATION_2_3`): álbum elegido por el
  usuario para una canción. `LibraryRepository.scan()` la aplica encima de las etiquetas;
  `moveToAlbum` también actualiza `tracks` al instante. Álbum nuevo = `albumId` negativo
  (hash del nombre). El archivo no se modifica
- `Sheet.MoveToAlbum` (menú ⋯ y barra de selección: "Álbum") y `Sheet.AddToAlbum` (botón +
  en el detalle de un álbum; marca solas las canciones "sueltas" del mismo artista)
- Por qué pasaba: las canciones sin etiqueta de álbum quedan en un "álbum" con el nombre de
  su carpeta (por ejemplo "Música"). Bruma ya graba el álbum en todas las canciones de un
  álbum de YouTube Music (`--ppa Metadata+ffmpeg_o:-metadata album=…`)

**Motor de audio: calidad**
- Cadena propia (`EffectChain`, un `AudioProcessorChain` sin Sonic ni saltar silencios, que
  solo aceptan 16 bits): `ParametricEq` (16 bits → float, preamp + EQ) → `PeakLimiter` →
  `AnalyzerTap` → `OutputStage`. Con "Alta precisión" `OutputStage` queda inactivo y el
  AudioTrack recibe float de 32 bits; apagada, pasa a 16 bits con dither triangular.
  `setEnableFloatOutput` de ExoPlayer se deja en false a propósito: en true se salta los
  procesadores con archivos de 24 bits. Límite conocido: el decodificador sigue entregando
  16 bits (los FLAC de 24 bits se reducen antes del EQ); arreglarlo pide un AudioSink propio
- Limitador con anticipación (`LookaheadLimiter`): mira 5 ms adelante, baja la ganancia con
  un mínimo deslizante (5 ms + 30 ms de espera) suavizado, suelta en 160 ms, canales unidos,
  techo 0,985. Reemplaza al `tanh` (con un tono 6 dB pasado: 0 % de distorsión contra 22 %).
  No añade silencio: guarda los primeros 5 ms y los entrega al final del flujo (`getOutput`/
  `isEnded` sobrescritos, como `TrimmingAudioProcessor`), así el gapless sigue sin hueco
- Preamp automático (`EqSettings.autoPreamp`, `headroomDb`): resta el máximo de la curva
  (bandas + tono) para que nada se pase; la perilla de preamp suma encima (`effectivePreampDb`)
- `EqSettings.encode()` añadió dos campos al final (auto, alta precisión); `decode` acepta
  el texto viejo de 8 campos
- "Alta precisión" se decide al configurar el sink: `PlaybackService` hace `stop()` +
  `prepare()` cuando cambia. El medidor del limitador (`AudioEffects.limiterReductionDb` /
  `limiterAt`) lo lee la tarjeta "Calidad" del ecualizador cada 90 ms
- Probar el motor sin Android: `Dsp.kt` no depende de nada; se compila con kotlinc y se le
  pasan tonos (así se midió lo de arriba)

**Volumen parejo (normalización)**
- `LoudnessMeter` mide la sonoridad en LUFS (filtro K, bloques de 400 ms, dos puertas). Probado
  con el tono de la norma: 1 kHz estéreo a −23 dBFS da −23,0
- `LoudnessScanner` mide con MediaExtractor + MediaCodec en un hilo propio de prioridad mínima:
  tres trozos de 20 s (15 %, 45 %, 75 %; las de menos de 75 s completas). Primero las que van a
  sonar (`prioritize`, lo llama `PlaybackService`) y luego toda la biblioteca. Solo trabaja con
  `EqSettings.normalize` encendido. `progress` alimenta el texto de la tarjeta "Calidad"
- `LoudnessStore` es una caché en `filesDir/loudness.tsv` (id, LUFS, pico), a propósito fuera
  de Room: se puede recalcular y así no hay migración. Las canciones que no se pudieron leer
  no se guardan en el archivo (se reintentan al volver a abrir la app)
- Ganancia: `normalizationGainDb` lleva cada canción a `TARGET_LUFS` (−10). Bajar siempre;
  subir solo hasta 3 dB por encima de su pico con limitador (0 sin él). Canción sin medir = 0 dB
- Cómo sabe el motor qué canción suena: `ParametricEq.onFlush(StreamMetadata)` recibe la línea
  de tiempo y el período del tramo que empieza (ExoPlayer reconfigura y vacía la cadena en cada
  canción) y saca el `mediaId`. Si no viene, usa `AudioEffects.currentTrackId` (lo pone el
  servicio). La medida se fija al empezar el tramo: no cambia a mitad de canción
- `EqEngine` aplica preamp + volumen parejo como una sola ganancia que persigue al objetivo en
  ~15 ms (sin clics al cambiar de canción ni al mover el preamp). El volumen parejo funciona
  aunque el ecualizador esté apagado; el limitador se activa si hay EQ o volumen parejo

**Ideas para después**
- Volumen parejo por álbum (que un álbum conserve sus diferencias) y elegir el nivel objetivo
- AudioSink propio para decodificar en float (FLAC de 24 bits de punta a punta)
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
