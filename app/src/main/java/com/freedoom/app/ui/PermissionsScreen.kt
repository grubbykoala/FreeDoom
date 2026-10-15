package com.freedoom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedoom.app.R
import com.freedoom.app.accessibility.LiveCounter

/** Estado que se muestra junto al título de cada paso. */
private enum class Status { Done, Pending, Optional, Recommended, CheckAlso, NotWorking }

@Composable
fun PermissionsScreen(
    state: PermissionState,
    onConsentChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Se llegó aquí porque el servicio se activó en Ajustes sin aceptar el aviso y se apagó solo. */
    showConsentNotice: Boolean = false,
) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val serviceLabel = stringResource(R.string.accessibility_service_label)
    val live by LiveCounter.state.collectAsStateWithLifecycle()
    val notWorking = rememberDelayedTrue(state.counterOn && !live.serviceRunning, delayMs = 3_000)
    val restrictedHint = "Si el interruptor sale gris (\"Ajuste restringido\"): tócalo una vez y acepta el aviso; " +
        "luego pulsa \"Info de la app\", abre el menú ⋮ de arriba, elige \"Permitir ajustes restringidos\" " +
        "y vuelve a intentarlo. Solo pasa si la app se instaló desde un archivo."

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "$appName te muestra cada día cuánto usas el móvil, en qué apps y cuántos reels ves. " +
                "Solo el paso 1 es necesario; el 2 y el 3 son opcionales.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Todo se queda en tu móvil: $appName no tiene acceso a internet, no tiene cuentas " +
                "y no envía nada a ningún sitio.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Section(
            title = "1. Acceso a datos de uso",
            status = if (state.usageAccess) Status.Done else Status.Pending,
            body = "Para medir cuánto tiempo pasas en cada app y cuántas veces desbloqueas el móvil. " +
                "En la lista que se abrirá, busca $appName y activa el permiso.",
        ) {
            if (state.usageAccess) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Ver mi panel") }
                OutlinedButton(onClick = { Permissions.openUsageAccessSettings(context) }) { Text("Revisar en Ajustes") }
            } else {
                Button(onClick = { Permissions.openUsageAccessSettings(context) }) { Text("Abrir Ajustes") }
                if (state.restrictedSettingsLikely) {
                    Text(restrictedHint, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { Permissions.openAppDetails(context) }) { Text("Info de la app") }
                }
            }
        }

        Section(
            title = "2. Contador de reels (opcional)",
            status = when {
                notWorking -> Status.NotWorking
                state.counterOn -> Status.Done
                else -> Status.Optional
            },
            body = null,
        ) {
            if (showConsentNotice && !state.accessibilityConsent) {
                Text(
                    "El contador se ha apagado solo porque aún no habías aceptado este aviso. Léelo, marca la " +
                        "casilla y vuelve a activarlo.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text("Qué hace", fontWeight = FontWeight.Bold)
            Text(
                "Usa el servicio de accesibilidad de Android para contar cuántos reels ves en Instagram " +
                    "y cuánto tiempo pasas en cada uno."
            )
            Text("Qué lee", fontWeight = FontWeight.Bold)
            Text(
                "Solo mientras Instagram está abierto, y solo datos técnicos de la pantalla: sus identificadores " +
                    "(por ejemplo, \"esto es el visor de Reels\"), si ese visor se ve y a qué tamaño, qué número de " +
                    "vídeo de la lista tienes delante y un número interno que distingue un visor de otro (no su " +
                    "contenido; solo en memoria, para no contar dos veces el mismo reel al volver). Con eso sabe " +
                    "cuándo pasas al siguiente vídeo."
            )
            Text("Qué NO hace", fontWeight = FontWeight.Bold)
            Text(
                "No lee ni guarda textos, mensajes, nombres de cuentas, fotos ni vídeos. No mira lo que haces " +
                    "en otras apps: al salir de Instagram solo comprueba qué app tienes delante, para saber que " +
                    "has salido. No pulsa nada por ti ni bloquea nada. No envía datos: la app no tiene internet."
            )
            Text("Qué guarda", fontWeight = FontWeight.Bold)
            Text(
                "Solo en tu móvil: cuántos reels has visto y a qué hora empezó y terminó cada uno. Para avisarte " +
                    "si el contador deja de funcionar, guarda también la hora de la última vez que recibió algo " +
                    "de Instagram (se sobrescribe, como mucho una vez por minuto) y cuándo se conectó. Si al volver " +
                    "a conectarse ve en los datos de uso que Instagram estuvo abierto mientras él estaba parado, " +
                    "guarda ese hueco (de qué hora a qué hora y cuántos minutos de Instagram) hasta que pulses " +
                    "\"Entendido\"."
            )
            Text(
                "Si no lo activas, $appName sigue funcionando con el tiempo por app.",
                style = MaterialTheme.typography.bodySmall,
            )

            // Toda la fila es el control: se puede tocar el texto y TalkBack lee qué se acepta.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.accessibilityConsent,
                        role = Role.Checkbox,
                        onValueChange = onConsentChanged,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = state.accessibilityConsent, onCheckedChange = null)
                Text(
                    "He leído lo anterior y acepto que $appName use el servicio de accesibilidad para esto.",
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                "Si la desmarcas, $appName deja de contar. Para apagarlo del todo, desmárcala y desactívalo " +
                    "también en Ajustes > Accesibilidad.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.accessibilityEnabled && !state.accessibilityConsent) {
                Text(
                    "El contador está activado en Ajustes, pero no contará nada hasta que marques la casilla.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (notWorking) {
                Text(
                    "Está activado pero no funciona. Apágalo y vuelve a encenderlo en Ajustes > Accesibilidad > " +
                        "$serviceLabel. Si sigue igual, reinicia el móvil y revisa el paso 3.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!state.accessibilityEnabled) {
                Text(
                    "Cómo activarlo: en Ajustes > Accesibilidad, entra en \"Apps instaladas\" (o \"Servicios " +
                        "descargados\"), elige \"$serviceLabel\" y actívalo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Al activarlo, Android te mostrará un aviso genérico: \"¿Permitir que $serviceLabel tenga " +
                        "control total del dispositivo?\". Sale con cualquier app que use accesibilidad. " +
                        "$appName no usa ese control: no pulsa ni cambia nada, solo cuenta reels.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.restrictedSettingsLikely) {
                    Text(restrictedHint, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { Permissions.openAppDetails(context) }) { Text("Info de la app") }
                }
                Text(
                    "Si el interruptor aparece bloqueado o se desactiva solo: en Android 17 con la Protección " +
                        "avanzada activada, Android no deja usar este contador. $appName seguirá midiendo el " +
                        "tiempo por app y los desbloqueos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                enabled = state.accessibilityConsent || state.accessibilityEnabled,
                onClick = { Permissions.openAccessibilitySettings(context) },
            ) {
                Text(if (state.accessibilityEnabled) "Revisar en Ajustes" else "Activar en Ajustes")
            }
        }

        val batteryTip = Permissions.manufacturerBatteryTip(context)
        Section(
            title = "3. Batería (recomendado)",
            status = when {
                !state.ignoringBatteryOptimizations -> Status.Recommended
                batteryTip != null -> Status.CheckAlso
                else -> Status.Done
            },
            body = "Algunos móviles cierran los servicios en segundo plano para ahorrar batería y el contador " +
                "deja de funcionar. Para evitarlo: pulsa \"Optimización de batería\", cambia el desplegable de " +
                "arriba a \"Todas las aplicaciones\", busca $appName y elige \"No optimizar\". También puedes ir a " +
                "\"Info de la app\" > Batería y elegir \"Sin restricciones\" (o similar, según el móvil).",
        ) {
            if (batteryTip != null) Text(batteryTip, style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Permissions.openBatterySettings(context) }) { Text("Optimización de batería") }
                OutlinedButton(onClick = { Permissions.openAppDetails(context) }) { Text("Info de la app") }
            }
        }
    }
}

@Composable
private fun Section(title: String, status: Status, body: String?, content: @Composable () -> Unit) {
    val (label, color) = when (status) {
        Status.Done -> "✓ Listo" to MaterialTheme.colorScheme.primary
        Status.Pending -> "Pendiente" to MaterialTheme.colorScheme.error
        Status.NotWorking -> "No funciona" to MaterialTheme.colorScheme.error
        Status.Optional -> "Opcional" to MaterialTheme.colorScheme.onSurfaceVariant
        Status.Recommended -> "Recomendado" to MaterialTheme.colorScheme.onSurfaceVariant
        Status.CheckAlso -> "Revisa también ↓" to MaterialTheme.colorScheme.tertiary
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                StatusLabel(label, color)
            }
            if (body != null) Text(body)
            content()
        }
    }
}

@Composable
private fun StatusLabel(text: String, color: Color) {
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
}
