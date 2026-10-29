package com.bismopower.app.ui.library

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bismopower.app.library.AUDIO_PERMISSION
import com.bismopower.app.library.hasAudioPermission

class AudioPermissionState(
    val granted: Boolean,
    val request: () -> Unit,
)

/**
 * Estado del permiso de audio. Al pedirlo también se piden las notificaciones (Android 13+),
 * que es donde aparecen los controles de reproducción. Si el usuario lo negó para siempre,
 * abre los ajustes de la app.
 */
@Composable
fun rememberAudioPermission(): AudioPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(context)) }
    var asked by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            granted = hasAudioPermission(context)
            asked = true
        }

    // Volver de los ajustes del sistema con el permiso ya concedido.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) granted = hasAudioPermission(context)
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    return AudioPermissionState(granted) {
        val activity = context as? Activity
        val blocked = asked && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, AUDIO_PERMISSION)
        if (blocked) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            val perms = mutableListOf(AUDIO_PERMISSION)
            if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
            launcher.launch(perms.toTypedArray())
        }
    }
}
