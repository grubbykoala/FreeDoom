package com.freedoom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    state: PermissionState,
    onConsentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "FreeDoom funciona solo en tu móvil: no tiene acceso a internet, no tiene cuentas " +
                "y no envía nada a ningún sitio.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Section(
            title = "1. Acceso a datos de uso",
            done = state.usageAccess,
            body = "Para medir cuánto tiempo pasas en cada app y cuántas veces desbloqueas el móvil. " +
                "En la lista que se abrirá, busca FreeDoom y activa el permiso.",
        ) {
            Button(onClick = { Permissions.openUsageAccessSettings(context) }) {
                Text(if (state.usageAccess) "Revisar en Ajustes" else "Abrir Ajustes")
            }
        }

        Section(
            title = "2. Contador de reels (opcional)",
            done = state.accessibilityEnabled,
            body = null,
        ) {
            Text("Qué hace", fontWeight = FontWeight.Bold)
            Text(
                "Usa el servicio de accesibilidad de Android para contar cuántos reels ves en Instagram " +
                    "y cuánto tiempo pasas en cada uno."
            )
            Text("Qué lee", fontWeight = FontWeight.Bold)
            Text(
                "Solo mientras Instagram está abierto, y solo los identificadores técnicos de la pantalla " +
                    "(por ejemplo, \"esto es el visor de Reels\") y el momento en que pasas al siguiente vídeo."
            )
            Text("Qué NO hace", fontWeight = FontWeight.Bold)
            Text(
                "No lee ni guarda textos, mensajes, nombres de cuentas, fotos ni vídeos. No mira otras apps. " +
                    "No pulsa nada por ti ni bloquea nada. No envía datos: la app no tiene internet."
            )
            Text("Qué guarda", fontWeight = FontWeight.Bold)
            Text("Solo en tu móvil: cuántos reels has visto y a qué hora empezó y terminó cada uno.")
            Text(
                "Si no lo activas, FreeDoom sigue funcionando con el tiempo por app.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.accessibilityConsent, onCheckedChange = onConsentChanged)
                Text("He leído lo anterior y acepto que FreeDoom use el servicio de accesibilidad para esto.")
            }
            Button(
                enabled = state.accessibilityConsent || state.accessibilityEnabled,
                onClick = { Permissions.openAccessibilitySettings(context) },
            ) {
                Text(if (state.accessibilityEnabled) "Revisar en Ajustes" else "Activar en Ajustes")
            }
            if (!state.accessibilityEnabled) {
                Text(
                    "En Ajustes > Accesibilidad, entra en \"Apps instaladas\" (o \"Servicios descargados\"), " +
                        "elige \"FreeDoom · contador de vídeos cortos\" y actívalo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Section(
            title = "3. Batería",
            done = state.ignoringBatteryOptimizations,
            body = "Algunos móviles cierran los servicios en segundo plano para ahorrar batería y el contador " +
                "deja de funcionar. Para evitarlo, quita a FreeDoom de la optimización de batería.",
        ) {
            Permissions.manufacturerBatteryTip()?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Permissions.openBatterySettings(context) }) { Text("Optimización de batería") }
                OutlinedButton(onClick = { Permissions.openAppDetails(context) }) { Text("Info de la app") }
            }
        }
    }
}

@Composable
private fun Section(title: String, done: Boolean, body: String?, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (done) "✓ Listo" else "Pendiente", color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            if (body != null) Text(body)
            content()
        }
    }
}
