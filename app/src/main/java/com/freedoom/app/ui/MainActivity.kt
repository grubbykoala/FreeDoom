package com.freedoom.app.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.freedoom.app.BuildConfig

private enum class Screen { Dashboard, Permissions, Debug }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FreeDoomTheme { FreeDoomRoot() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeDoomRoot() {
    val context = LocalContext.current
    var permissions by remember { mutableStateOf(Permissions.read(context)) }
    var screen by rememberSaveable {
        mutableStateOf(if (permissions.usageAccess) Screen.Dashboard else Screen.Permissions)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = Permissions.read(context) }
    BackHandler(enabled = screen != Screen.Dashboard) { screen = Screen.Dashboard }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.Dashboard -> "Hoy"
                            Screen.Permissions -> "Permisos"
                            Screen.Debug -> "Depuración"
                        }
                    )
                },
                actions = {
                    if (screen != Screen.Dashboard) {
                        TextButton(onClick = { screen = Screen.Dashboard }) { Text("Panel") }
                    }
                    if (screen != Screen.Permissions) {
                        TextButton(onClick = { screen = Screen.Permissions }) { Text("Permisos") }
                    }
                    if (BuildConfig.DEBUG && screen != Screen.Debug) {
                        TextButton(onClick = { screen = Screen.Debug }) { Text("Debug") }
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (screen) {
            Screen.Dashboard -> DashboardScreen(
                permissions = permissions,
                onOpenPermissions = { screen = Screen.Permissions },
                modifier = modifier,
            )
            Screen.Permissions -> PermissionsScreen(
                state = permissions,
                onConsentChanged = {
                    Permissions.setAccessibilityConsent(context, it)
                    permissions = Permissions.read(context)
                },
                modifier = modifier,
            )
            Screen.Debug -> if (BuildConfig.DEBUG) DebugScreen(modifier) else Unit
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
