package com.bismopower.app

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.bismopower.app.audio.AudioEffects
import com.bismopower.app.data.EqRepository
import com.bismopower.app.data.SettingsRepository
import com.bismopower.app.data.db.BismoDatabase
import com.bismopower.app.library.LibraryRepository
import com.bismopower.app.library.LoudnessScanner
import com.bismopower.app.library.LoudnessStore
import com.bismopower.app.library.LyricsRepository
import com.bismopower.app.library.PlaylistRepository
import com.bismopower.app.library.TrackArtFetcher
import com.bismopower.app.player.PlayerConnection
import com.bismopower.app.player.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class BismoApp :
    Application(),
    ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Cargador de imágenes de toda la app: sabe leer la carátula de cada canción (bismoart://). */
    override fun newImageLoader(): ImageLoader =
        ImageLoader
            .Builder(this)
            .components { add(TrackArtFetcher.Factory()) }
            .crossfade(180)
            .build()
}

/** Contenedor sencillo de dependencias (en lugar de Hilt, igual que en Bruma). */
@OptIn(UnstableApi::class)
class AppContainer(
    app: Application,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = BismoDatabase.build(app)
    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, db, settings)
    val player = PlayerConnection(app)

    /** Listas de reproducción, favoritas y lo más escuchado. */
    val playlists = PlaylistRepository(db)

    /** Letras de las canciones (.lrc o incluidas en el archivo). */
    val lyrics = LyricsRepository(app, settings)

    /** Temporizador para dormir. */
    val sleepTimer = SleepTimer(appScope, player)

    /** Cadena de efectos de audio que usa ExoPlayer (el ecualizador vive aquí). */
    val audioEffects = AudioEffects()

    /** Ajustes del ecualizador: los guarda y se los pasa al motor de audio. */
    val equalizer = EqRepository(app, appScope) { audioEffects.update(it) }

    /** Lo fuerte que suena cada canción (para "Volumen parejo") y quien lo mide en segundo plano. */
    val loudness = LoudnessStore(app)
    val loudnessScanner = LoudnessScanner(app, loudness, appScope)

    init {
        audioEffects.loudnessOf = { id -> loudness[id] }
        appScope.launch {
            equalizer.state
                .map { it.normalize }
                .distinctUntilChanged()
                .collect { loudnessScanner.setEnabled(it) }
        }
        appScope.launch {
            library.tracks
                .map { list -> list.map { it.id } }
                .distinctUntilChanged()
                .collect { loudnessScanner.setLibrary(it) }
        }
    }
}

val Context.container: AppContainer
    get() = (applicationContext as BismoApp).container
