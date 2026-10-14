package com.freedoom.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freedoom.app.accessibility.LiveCounter
import com.freedoom.app.data.AppDatabase
import com.freedoom.app.data.DayRange
import com.freedoom.app.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Solo en builds debug: contador en vivo, tanda de calibración y registro de eventos. */
@Composable
fun DebugScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by LiveCounter.state.collectAsStateWithLifecycle()
    val log by DebugLog.entries.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.scrollToItem(log.lastIndex) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Calibración", style = MaterialTheme.typography.titleMedium)
                val count = live.calibrationCount
                if (count == null) {
                    Text("Pulsa empezar, ve a Instagram, cuenta a mano los reels que pasas y vuelve aquí.")
                    Button(onClick = { LiveCounter.startCalibration(); DebugLog.add("CALIBRACIÓN", "inicio") }) {
                        Text("Empezar tanda")
                    }
                } else {
                    Text("Recuento automático de esta tanda: $count", fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        DebugLog.add("CALIBRACIÓN", "fin: $count vídeos")
                        LiveCounter.stopCalibration()
                    }) { Text("Terminar tanda") }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Registro FreeDoom")
                    .putExtra(Intent.EXTRA_TEXT, DebugLog.export())
                context.startActivity(Intent.createChooser(send, "Compartir registro"))
            }) { Text("Compartir") }
            OutlinedButton(onClick = { DebugLog.clear() }) { Text("Vaciar") }
            OutlinedButton(onClick = {
                val today = DayRange.today()
                scope.launch(Dispatchers.IO) {
                    AppDatabase.get(context).shortVideoDao().deleteBetween(today.start, today.end)
                }
                DebugLog.add("DATOS", "borrados los reels de hoy")
            }) { Text("Borrar hoy") }
        }

        Text("Registro (${log.size})", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(log) { entry ->
                Text(DebugLog.format(entry), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                HorizontalDivider()
            }
        }
    }
}
