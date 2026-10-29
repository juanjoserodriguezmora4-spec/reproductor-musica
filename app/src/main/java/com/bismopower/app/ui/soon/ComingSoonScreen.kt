package com.bismopower.app.ui.soon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bismopower.app.ui.common.Badge
import com.bismopower.app.ui.common.EmptyState
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T

/** Pantalla de una sección que llega en la Fase 2, con el mismo estilo de vidrio. */
@Composable
fun ComingSoonScreen(
    title: String,
    icon: ImageVector,
    text: String,
) {
    val c = LocalBismo.current
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(title, style = T.h1, color = c.text)
        Spacer(Modifier.height(6.dp))
        Badge("Próximamente")
        Spacer(Modifier.height(18.dp))
        EmptyState(icon = icon, title = "Estamos trabajando en esto", text = text)
    }
}
