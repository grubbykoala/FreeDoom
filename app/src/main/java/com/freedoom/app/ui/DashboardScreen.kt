package com.freedoom.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freedoom.app.accessibility.LiveCounter

@Composable
fun DashboardScreen(
    permissions: PermissionState,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DashboardViewModel = viewModel(),
) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val usage by vm.usage.collectAsStateWithLifecycle()
    val reels by vm.reelsToday.collectAsStateWithLifecycle()
    val avgMs by vm.averageWatchMs.collectAsStateWithLifecycle()
    val live by LiveCounter.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!permissions.usageAccess) {
            item {
                Warning("Falta el acceso a datos de uso: sin él no podemos medir el tiempo por app.", onOpenPermissions)
            }
        } else if (permissions.accessibilityEnabled && !live.serviceRunning) {
            item {
                Warning(
                    "El contador de reels está activado pero el sistema lo ha detenido. " +
                        "Suele ser el ahorro de batería: revisa la sección de batería en Permisos.",
                    onOpenPermissions,
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Tiempo de pantalla", usage?.let { formatDuration(it.totalMs) } ?: "—", Modifier.weight(1f))
                StatCard(
                    "Desbloqueos",
                    when {
                        usage == null -> "—"
                        usage?.unlocks == null -> "n/d"
                        else -> usage?.unlocks.toString()
                    },
                    Modifier.weight(1f),
                )
            }
        }

        item {
            val reelsValue = if (permissions.accessibilityEnabled || reels > 0) reels.toString() else "—"
            val sub = avgMs?.let { "%.1f s de media por reel".format(it / 1000) }
                ?: if (!permissions.accessibilityEnabled) "Activa el contador en Permisos" else null
            StatCard("Reels de Instagram", reelsValue, Modifier.fillMaxWidth(), sub)
        }

        val apps = usage?.apps.orEmpty()
        if (apps.isNotEmpty()) {
            item {
                Text("Por app", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            val max = apps.first().ms.toFloat()
            items(apps, key = { it.packageName }) { row -> AppUsageRow(row, row.ms / max) }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppUsageRow(row: AppRow, fraction: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(formatDuration(row.ms), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Warning(text: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            Button(onClick = onClick) { Text("Ir a permisos") }
        }
    }
}
