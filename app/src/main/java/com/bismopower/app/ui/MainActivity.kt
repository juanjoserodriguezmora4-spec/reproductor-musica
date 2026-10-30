package com.bismopower.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bismopower.app.container
import com.bismopower.app.data.AppSettings
import com.bismopower.app.player.EXTRA_ROUTE
import com.bismopower.app.ui.theme.BismoTheme
import com.bismopower.app.ui.theme.LocalBismo

class MainActivity : ComponentActivity() {
    private val route = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val settings by container.settings.flow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            val s = settings
            BismoTheme(s?.theme ?: "SYSTEM", s?.colorTheme ?: "BRUMA", s?.reduceTransparency ?: false) {
                if (s == null) {
                    Box(Modifier.fillMaxSize().background(LocalBismo.current.bg))
                } else {
                    val startRoute by route
                    BismoRoot(startRoute = startRoute, onRouteConsumed = { route.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROUTE)?.let { route.value = it }
    }
}
