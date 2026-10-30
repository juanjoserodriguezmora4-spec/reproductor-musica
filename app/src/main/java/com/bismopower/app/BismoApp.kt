package com.bismopower.app

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.bismopower.app.audio.AudioEffects
import com.bismopower.app.data.EqRepository
import com.bismopower.app.data.SettingsRepository
import com.bismopower.app.data.db.BismoDatabase
import com.bismopower.app.library.LibraryRepository
import com.bismopower.app.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BismoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Contenedor sencillo de dependencias (en lugar de Hilt, igual que en Bruma). */
@OptIn(UnstableApi::class)
class AppContainer(
    app: Application,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = BismoDatabase.build(app)
    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, db)
    val player = PlayerConnection(app)

    /** Cadena de efectos de audio que usa ExoPlayer (el ecualizador vive aquí). */
    val audioEffects = AudioEffects()

    /** Ajustes del ecualizador: los guarda y se los pasa al motor de audio. */
    val equalizer = EqRepository(app, appScope) { audioEffects.equalizer.update(it) }
}

val Context.container: AppContainer
    get() = (applicationContext as BismoApp).container
