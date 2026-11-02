package com.bismopower.app.ui.sheets

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.bismopower.app.container
import com.bismopower.app.data.db.TrackEntity
import com.bismopower.app.ui.common.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Borra canciones del teléfono. Se pide con [LocalDeleter] desde cualquier pantalla. */
@Stable
interface TrackDeleter {
    /** Empieza a borrar: en Android 11+ Android pregunta; antes, la app muestra su propia confirmación. */
    fun request(
        tracks: List<TrackEntity>,
        onDone: () -> Unit = {},
    )

    /** Android 8 a 10: el usuario ya confirmó en la hoja de la app. */
    fun confirmLegacy(tracks: List<TrackEntity>)
}

val LocalDeleter = staticCompositionLocalOf<TrackDeleter> { error("Falta el TrackDeleter") }

/**
 * Crea el borrador de canciones. Va una sola vez en la pantalla raíz.
 *
 * - Android 11 o más: se piden todas juntas con [MediaStore.createTrashRequest]. Android muestra
 *   un solo aviso y las manda a la papelera (se pueden recuperar unos 30 días desde la app de
 *   Archivos). Así no hace falta ningún permiso extra.
 * - Android 8 a 10: se borran directamente, con el permiso de escritura (solo existe hasta el 10).
 *
 * Después se quitan de la biblioteca, de las listas, de las favoritas y de la cola.
 */
@Composable
fun rememberTrackDeleter(sheets: SheetController): TrackDeleter {
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }
    var after by remember { mutableStateOf<() -> Unit>({}) }

    fun finish(deleted: List<TrackEntity>) {
        val ids = deleted.map { it.id }
        app.player.removeIds(ids.toSet())
        scope.launch {
            app.library.forget(ids)
            context.toast(if (deleted.size == 1) "Canción borrada" else "${plural(deleted.size, "canción borrada", "canciones borradas")}")
            after()
            after = {}
        }
    }

    val trash =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finish(pending)
            pending = emptyList()
        }

    fun deleteLegacy(tracks: List<TrackEntity>) {
        scope.launch {
            val done =
                withContext(Dispatchers.IO) {
                    tracks.filter { t -> runCatching { context.contentResolver.delete(Uri.parse(t.uri), null, null) > 0 }.getOrDefault(false) }
                }
            if (done.isNotEmpty()) finish(done)
            if (done.size < tracks.size) context.toast("No se pudieron borrar ${tracks.size - done.size}")
        }
    }

    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) deleteLegacy(pending) else context.toast("Sin ese permiso no se pueden borrar canciones")
            pending = emptyList()
        }

    val sheetsNow by rememberUpdatedState(sheets)
    return remember {
        object : TrackDeleter {
            override fun request(
                tracks: List<TrackEntity>,
                onDone: () -> Unit,
            ) {
                if (tracks.isEmpty()) return
                after = onDone
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pending = tracks
                    val uris = tracks.map { Uri.parse(it.uri) }
                    val request = runCatching { MediaStore.createTrashRequest(context.contentResolver, uris, true) }.getOrNull()
                    if (request == null) {
                        context.toast("Android no dejó borrar estas canciones")
                        return
                    }
                    trash.launch(IntentSenderRequest.Builder(request.intentSender).build())
                } else {
                    sheetsNow.open(Sheet.ConfirmDelete(tracks))
                }
            }

            override fun confirmLegacy(tracks: List<TrackEntity>) {
                val write = Manifest.permission.WRITE_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(context, write) == PackageManager.PERMISSION_GRANTED) {
                    deleteLegacy(tracks)
                } else {
                    pending = tracks
                    permission.launch(write)
                }
            }
        }
    }
}
