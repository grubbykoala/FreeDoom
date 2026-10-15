package com.freedoom.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.freedoom.app.BuildConfig

private enum class Screen { Dashboard, Permissions, Debug }

class MainActivity : ComponentActivity() {
    /**
     * Veces que el servicio se ha activado sin aceptar el aviso y ha abierto la app: cada una lleva al
     * aviso. Es un contador (no un sí/no) para que la segunda vez también funcione.
     */
    private val consentRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            takeConsentExtra(intent)
        } else {
            // Recreada (rotación, proceso nuevo): el intent original ya se atendió.
            consentRequests.intValue = savedInstanceState.getInt(KEY_CONSENT_REQUESTS)
        }
        setContent { FreeDoomTheme { FreeDoomRoot(consentRequests.intValue) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeConsentExtra(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CONSENT_REQUESTS, consentRequests.intValue)
    }

    private fun takeConsentExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_CONSENT, false) == true) consentRequests.intValue++
    }

    companion object {
        const val EXTRA_SHOW_CONSENT = "show_consent"
        private const val KEY_CONSENT_REQUESTS = "consent_requests"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeDoomRoot(consentRequests: Int) {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(Permissions.read(context)) }
    var screen by rememberSaveable {
        mutableStateOf(if (permissions.usageAccess) Screen.Dashboard else Screen.Permissions)
    }
    // Peticiones ya atendidas y si se muestra el aviso: sobreviven a la rotación.
    var handledRequests by rememberSaveable { mutableIntStateOf(0) }
    var consentNotice by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(consentRequests) {
        if (consentRequests > handledRequests) {
            handledRequests = consentRequests
            screen = Screen.Permissions
            consentNotice = true
        }
    }
    // El aviso solo vale hasta salir de Permisos (y se oculta en cuanto se acepta).
    LaunchedEffect(screen) { if (screen != Screen.Permissions) consentNotice = false }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = Permissions.read(context) }
    BackHandler(enabled = screen != Screen.Dashboard) { screen = Screen.Dashboard }
    // Conserva el scroll de cada pantalla al ir y volver.
    val stateHolder = rememberSaveableStateHolder()

    // safeDrawing incluye el recorte de la cámara: en horizontal nada queda debajo.
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                title = {
                    Text(
                        when (screen) {
                            Screen.Dashboard -> "Hoy"
                            Screen.Permissions -> "Permisos"
                            Screen.Debug -> "Depuración"
                        }
                    )
                },
                navigationIcon = {
                    if (screen != Screen.Dashboard) {
                        IconButton(onClick = { screen = Screen.Dashboard }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver al panel")
                        }
                    }
                },
                actions = {
                    if (screen != Screen.Permissions) {
                        TextButton(onClick = { screen = Screen.Permissions }) { Text("Permisos") }
                    }
                    if (BuildConfig.DEBUG && screen != Screen.Debug) {
                        TextButton(onClick = { screen = Screen.Debug }) { Text("Depuración") }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        stateHolder.SaveableStateProvider(screen.name) {
            when (screen) {
                Screen.Dashboard -> DashboardScreen(
                    permissions = permissions,
                    onOpenPermissions = { screen = Screen.Permissions },
                    modifier = modifier,
                    onTick = { permissions = Permissions.read(context) },
                )
                Screen.Permissions -> PermissionsScreen(
                    state = permissions,
                    onConsentChanged = {
                        Permissions.setAccessibilityConsent(context, it)
                        permissions = Permissions.read(context)
                    },
                    onDone = { screen = Screen.Dashboard },
                    modifier = modifier,
                    showConsentNotice = consentNotice && !permissions.accessibilityConsent,
                )
                Screen.Debug -> if (BuildConfig.DEBUG) DebugScreen(modifier) else Unit
            }
        }
    }
}

@Composable
private fun FreeDoomTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
