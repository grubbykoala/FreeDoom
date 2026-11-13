package com.freedoom.app.ui

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedoom.app.R
import com.freedoom.app.accessibility.LiveCounter
import com.freedoom.app.accessibility.ServiceHealth
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.DayRange
import com.freedoom.app.debug.CalibrationStore
import com.freedoom.app.debug.DebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que se lee de ServiceHealth para enseñarlo en la pantalla. */
private data class HealthInfo(val connectedAt: Long?, val heartbeatAt: Long?, val gap: ServiceHealth.Gap?)

/** Acción destructiva pendiente de confirmar. */
private enum class Confirm { ClearLog, DeleteToday }

/** Solo en builds debug: contador en vivo, tanda de calibración, salud del servicio y registro de eventos. */
/** Tope del texto compartido: un Intent demasiado grande cierra la app. */
private const val MAX_SHARE_CHARS = 100_000

@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)
    val scope = rememberCoroutineScope()
    // Un fallo de la base de datos aquí no debe tumbar el proceso (que es también el del contador)
    // ni cancelar el scope de la pantalla, que la dejaría congelada: se captura dentro de cada tarea.
    val reportError = remember {
        { e: Exception ->
            Log.w("FreeDoom", "fallo en depuración", e)
            DebugLog.add("ERROR", "depuración: ${e.javaClass.simpleName}")
        }
    }
    val dao = remember { AppDatabase.get(context).shortVideoDao() }
    val live by LiveCounter.state.collectAsStateWithLifecycle()
    val log by DebugLog.entries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex) }

    var batch by remember { mutableStateOf(CalibrationStore.current(context)) }
    var lastResult by remember { mutableStateOf(CalibrationStore.lastResult(context)) }
    var manual by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val startedAt = batch?.startedAt
    val dbCountFlow: Flow<Int?> = remember(startedAt) {
        if (startedAt == null) flowOf(null) else dao.observeCount(startedAt, Long.MAX_VALUE)
    }
    val dbCount by dbCountFlow.collectAsStateWithLifecycle(initialValue = null)

    val health by produceState(HealthInfo(null, null, null)) {
        while (true) {
            value = withContext(Dispatchers.IO) {
                HealthInfo(
                    ServiceHealth.connectedAt(context),
                    ServiceHealth.lastHeartbeatAt(context),
                    ServiceHealth.lastGap(context),
                )
            }
            delay(5_000)
        }
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Parte de arriba desplazable, para que el registro siga visible en pantallas pequeñas.
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Servicio: ${if (live.serviceRunning) "en marcha" else "parado"} · " +
                            "Reels: ${if (live.inShortVideos) "dentro" else "fuera"}"
                    )
                    Text("${live.countSinceStart}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("vídeos desde que arrancó el servicio", style = MaterialTheme.typography.bodySmall)
                    live.lastReason?.let { Text("Último: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Salud del contador", style = MaterialTheme.typography.titleMedium)
                    val now = System.currentTimeMillis()
                    Text("Conectado: ${health.connectedAt?.let { formatMoment(it, now) } ?: "nunca"}")
                    val beat = health.heartbeatAt?.let { "${formatMoment(it, now)} (hace ${formatDuration(now - it)})" }
                    Text("Último latido: ${beat ?: "ninguno"}")
                    val gap = health.gap?.let {
                        "${formatMoment(it.from, now)} – ${formatMoment(it.to, now)}, " +
                            "${formatDuration(it.instagramMs)} de Instagram"
                    }
                    Text("Último hueco: ${gap ?: "ninguno"}", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Calibración", style = MaterialTheme.typography.titleMedium)
                    val current = batch
                    if (current == null) {
                        Text("Pulsa empezar, ve a Instagram, cuenta a mano los reels que pasas y vuelve aquí.")
                        Button(onClick = {
                            val started = CalibrationStore.start(context, System.currentTimeMillis())
                            LiveCounter.startCalibration()
                            manual = ""
                            batch = started
                            DebugLog.add("CALIBRACIÓN", "tanda ${started.number} inicio")
                        }) { Text("Empezar tanda") }
                        lastResult?.let {
                            Text("Último resultado:", style = MaterialTheme.typography.labelLarge)
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    } else {
                        val liveCount = live.calibrationCount
                        val restarted = health.connectedAt?.let { it > current.startedAt } == true
                        Text("Tanda ${current.number} · desde las ${formatMoment(current.startedAt, System.currentTimeMillis())}")
                        Text("Guardados en la BD: ${dbCount ?: "…"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("En vivo: ${liveCount ?: "—"}")
                        val db = dbCount
                        if (db != null && liveCount != null && db != liveCount) {
                            Text(
                                "La BD y el contador en vivo no coinciden: se han perdido escrituras.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (liveCount == null || restarted) {
                            Text(
                                "El servicio se reinició durante la tanda: usa la cifra de la BD.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it.filter(Char::isDigit).take(4) },
                            label = { Text("Tu cuenta a mano") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        val m = manual.toIntOrNull()
                        if (db != null && m != null) {
                            Text("Diferencia (BD − a mano): ${signed(db - m)}")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) { dao.count(current.startedAt, Long.MAX_VALUE) }
                                    val vivo = LiveCounter.state.value.calibrationCount
                                    val reinicio = vivo == null ||
                                        (ServiceHealth.connectedAt(context)?.let { it > current.startedAt } == true)
                                    val note = if (reinicio) " (el servicio se reinició durante la tanda)" else ""
                                    val dif = m?.let { signed(saved - it) } ?: "?"
                                    val line = "tanda ${current.number} fin: bd=$saved vivo=${vivo ?: "?"} " +
                                        "manual=${m ?: "?"} dif=$dif$note"
                                    DebugLog.add("CALIBRACIÓN", line)
                                    CalibrationStore.finish(context, line)
                                    LiveCounter.stopCalibration()
                                    lastResult = line
                                    batch = null
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    reportError(e)
                                }
                            }
                        }) { Text("Terminar tanda") }
                    }
                }
            }

            Button(onClick = {
                // Android no deja pasar textos enormes entre apps: si es muy largo, se envía el final.
                val full = DebugLog.export()
                val text = if (full.length > MAX_SHARE_CHARS) {
                    "(registro recortado: solo las últimas líneas)\n" + full.takeLast(MAX_SHARE_CHARS)
                } else {
                    full
                }
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Registro $appName")
                    .putExtra(Intent.EXTRA_TEXT, text)
                try {
                    context.startActivity(Intent.createChooser(send, "Compartir registro"))
                } catch (e: Exception) {
                    reportError(e)
                    Toast.makeText(context, "No se pudo compartir el registro", Toast.LENGTH_LONG).show()
                }
            }) { Text("Compartir registro") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirm = Confirm.ClearLog }) { Text("Vaciar registro") }
                OutlinedButton(onClick = { confirm = Confirm.DeleteToday }) { Text("Borrar hoy") }
            }
        }

        Text("Registro (${log.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(log) { entry ->
                Text(DebugLog.format(entry), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                HorizontalDivider()
            }
        }
    }

    when (confirm) {
        Confirm.ClearLog -> ConfirmDialog(
            text = "¿Vaciar el registro? Se pierde lo que no hayas compartido.",
            confirmText = "Vaciar",
            onConfirm = { DebugLog.clear() },
            onDismiss = { confirm = null },
        )
        Confirm.DeleteToday -> ConfirmDialog(
            text = "¿Borrar todos los reels guardados hoy? No se puede deshacer.",
            confirmText = "Borrar",
            onConfirm = {
                val today = DayRange.today()
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { dao.deleteBetween(today.start, today.end) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportError(e)
                    }
                }
                DebugLog.add("DATOS", "borrados los reels de hoy")
            },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmDialog(text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun signed(n: Int) = if (n > 0) "+$n" else n.toString()
