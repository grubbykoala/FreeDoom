package com.freedoom.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freedoom.app.R
import com.freedoom.app.accessibility.LiveCounter
import com.freedoom.app.data.DayRange
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    permissions: PermissionState,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DashboardViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Al volver a la app, cada minuto mientras se ve y justo al pasar la medianoche.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                val untilMidnight = DayRange.today().end - System.currentTimeMillis()
                delay(minOf(60_000L, untilMidnight.coerceAtLeast(1_000L)))
            }
        }
    }
    val usage by vm.usage.collectAsStateWithLifecycle()
    val reels by vm.reelsToday.collectAsStateWithLifecycle()
    val avgMs by vm.averageWatchMs.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val live by LiveCounter.state.collectAsStateWithLifecycle()
    val appName = stringResource(R.string.app_name)
    val serviceLabel = stringResource(R.string.accessibility_service_label)
    val now = System.currentTimeMillis()

    val counterOn = permissions.counterOn
    // LiveCounter empieza en "parado" al arrancar el proceso: se espera un poco para no avisar en falso.
    val counterStopped = rememberDelayedTrue(counterOn && !live.serviceRunning, delayMs = 3_000)
    val missedAt = health.missedInstagramAt.takeIf { counterOn && live.serviceRunning }
    val gap = health.gap
    val openAccessibility = { Permissions.openAccessibilitySettings(context) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!permissions.usageAccess) {
            item {
                Warning(
                    "Falta el acceso a datos de uso: sin él no podemos medir el tiempo por app.",
                    "Ir a permisos",
                    onOpenPermissions,
                )
            }
        }
        if (permissions.accessibilityConsent && !permissions.accessibilityEnabled) {
            item {
                Warning(
                    "El contador de reels está apagado. Si no lo apagaste tú, Android lo apaga al forzar la " +
                        "detención de $appName o si un limpiador la cierra: vuelve a activarlo en Ajustes > " +
                        "Accesibilidad > $serviceLabel. Si lo apagaste tú, desmarca la casilla en Permisos y este " +
                        "aviso desaparecerá.",
                    "Abrir Accesibilidad",
                    openAccessibility,
                    secondaryText = "Ir a permisos",
                    onSecondary = onOpenPermissions,
                )
            }
        }
        if (permissions.accessibilityEnabled && !permissions.accessibilityConsent) {
            item {
                Warning(
                    "La casilla de aceptación está desmarcada, así que el contador no cuenta nada. Para apagarlo " +
                        "del todo, desactívalo también en Ajustes > Accesibilidad > $serviceLabel; para que vuelva " +
                        "a contar, marca la casilla en Permisos.",
                    "Abrir Accesibilidad",
                    openAccessibility,
                    secondaryText = "Ir a permisos",
                    onSecondary = onOpenPermissions,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        }
        if (counterStopped) {
            item {
                Warning(
                    "El contador de reels está activado pero no funciona (en Ajustes puede salir como " +
                        "\"No funciona\"). Apágalo y vuelve a encenderlo en Ajustes > Accesibilidad > $serviceLabel. " +
                        "Si sigue igual, reinicia el móvil y revisa la batería en Permisos.",
                    "Abrir Accesibilidad",
                    openAccessibility,
                    secondaryText = "Batería",
                    onSecondary = onOpenPermissions,
                )
            }
        }
        if (missedAt != null) {
            item {
                Warning(
                    "El contador no está recibiendo nada de Instagram: la abriste (${formatMoment(missedAt, now)}) " +
                        "y no se enteró, así que esos reels no se cuentan. Apágalo y vuelve a encenderlo en " +
                        "Ajustes > Accesibilidad > $serviceLabel. Si sigue igual, reinicia el móvil y revisa la batería.",
                    "Abrir Accesibilidad",
                    openAccessibility,
                    secondaryText = "Batería",
                    onSecondary = onOpenPermissions,
                )
            }
        }
        if (gap != null) {
            item {
                Warning(
                    "El contador estuvo parado de ${formatMoment(gap.from, now)} a ${formatMoment(gap.to, now)} " +
                        "(unos ${formatDuration(gap.instagramMs)} de Instagram sin contar). Suele pasar al forzar " +
                        "la detención de $appName (también con Stop en Android Studio) o si el móvil la cierra " +
                        "para ahorrar batería.",
                    "Entendido",
                    vm::dismissGap,
                    secondaryText = "Batería",
                    onSecondary = onOpenPermissions,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
            }
        }

        item {
            val u = usage
            val loading = u == null && permissions.usageAccess
            StatCard(
                title = "Tiempo de pantalla hoy",
                value = when {
                    u != null -> formatDuration(u.totalMs)
                    loading -> "…"
                    else -> "—"
                },
                spokenValue = when {
                    u != null -> spokenDuration(u.totalMs)
                    loading -> "cargando"
                    else -> "sin datos"
                },
                valueStyle = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val r = reels
                val avg = avgMs
                StatCard(
                    title = "Reels de Instagram",
                    value = when {
                        r == null -> "…"
                        counterOn || r > 0 -> r.toString()
                        else -> "—"
                    },
                    spokenValue = when {
                        r == null -> "cargando"
                        counterOn || r > 0 -> r.toString()
                        else -> "sin datos"
                    },
                    subtitle = when {
                        permissions.accessibilityConsent && !permissions.accessibilityEnabled -> "Contador apagado"
                        permissions.accessibilityEnabled && !permissions.accessibilityConsent -> "Falta tu aceptación"
                        !counterOn -> null
                        r == 0 && live.serviceRunning -> "Abre Reels en Instagram y aquí verás cuántos ves"
                        avg != null && r != null && r > 0 -> "%.1f\u00A0s de media por reel".format(avg / 1000)
                        else -> null
                    },
                    actionText = if (counterOn) null else "Activar contador",
                    onAction = {
                        // Con la casilla ya aceptada basta con encenderlo; si no, primero hay que leer el aviso.
                        if (permissions.accessibilityConsent) openAccessibility() else onOpenPermissions()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )

                val u = usage
                val unlocks = u?.unlocks
                StatCard(
                    title = "Desbloqueos",
                    value = when {
                        u == null -> if (permissions.usageAccess) "…" else "—"
                        unlocks == null -> "n/d"
                        else -> unlocks.toString()
                    },
                    spokenValue = when {
                        u == null -> if (permissions.usageAccess) "cargando" else "sin datos"
                        unlocks == null -> "no disponible en este Android"
                        else -> unlocks.toString()
                    },
                    subtitle = if (u?.unlocksAreScreenOns == true) "Sin bloqueo de pantalla: cuenta encendidos" else null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        val apps = usage?.apps.orEmpty()
        if (apps.isNotEmpty()) {
            item {
                Text(
                    "Por app",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
            }
            val max = apps.first().ms.coerceAtLeast(1L).toFloat()
            items(apps, key = { it.packageName }) { row -> AppUsageRow(row, row.ms / max) }
        } else if (usage != null) {
            item {
                Text(
                    "Todavía no hay uso registrado hoy.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** true solo cuando [condition] lleva al menos [delayMs] siendo cierta: evita avisos que parpadean al arrancar. */
@Composable
internal fun rememberDelayedTrue(condition: Boolean, delayMs: Long): Boolean {
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(condition) {
        settled = false
        if (condition) {
            delay(delayMs)
            settled = true
        }
    }
    return condition && settled
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    spokenValue: String = value,
    valueStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            // TalkBack lee la tarjeta de una vez: "título, valor, detalle".
            Column(
                Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = listOfNotNull(title, spokenValue, subtitle).joinToString(", ")
                },
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(value, style = valueStyle, fontWeight = FontWeight.Bold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (actionText != null) {
                TextButton(onClick = onAction) { Text(actionText) }
            }
        }
    }
}

@Composable
private fun AppUsageRow(row: AppRow, fraction: Float) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = "${row.label}: ${spokenDuration(row.ms)}"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (icon != null) {
            Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        } else {
            Spacer(Modifier.size(32.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(formatDuration(row.ms), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Warning(
    text: String,
    buttonText: String,
    onClick: () -> Unit,
    secondaryText: String? = null,
    onSecondary: () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onClick) { Text(buttonText) }
                if (secondaryText != null) TextButton(onClick = onSecondary) { Text(secondaryText) }
            }
        }
    }
}
