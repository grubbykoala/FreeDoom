package com.freedoom.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import com.freedoom.app.R
import com.freedoom.app.accessibility.LiveCounter
import com.freedoom.app.accessibility.ServiceHealth
import com.freedoom.app.accessibility.ShortVideoAccessibilityService
import com.freedoom.app.data.ConsentStore
import com.freedoom.app.usage.UsageReader

/** Estado de los permisos y ajustes que necesita la app. */
data class PermissionState(
    val usageAccess: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConsent: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    /** APK instalado desde un archivo: Android puede mostrar los interruptores como "Ajuste restringido". */
    val restrictedSettingsLikely: Boolean = false,
    /** El contador ha llegado a funcionar alguna vez (con la casilla aceptada). */
    val counterEverConnected: Boolean = false,
) {
    /** El contador de reels solo cuenta con el servicio activado Y el consentimiento dado en la app. */
    val counterOn: Boolean get() = accessibilityEnabled && accessibilityConsent
}

object Permissions {

    fun read(context: Context) = PermissionState(
        usageAccess = UsageReader(context).hasPermission(),
        accessibilityEnabled = isAccessibilityEnabled(context),
        accessibilityConsent = ConsentStore.hasConsent(context),
        ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        restrictedSettingsLikely = installedFromFile(context),
        counterEverConnected = ServiceHealth.connectedAt(context) != null,
    )

    fun setAccessibilityConsent(context: Context, given: Boolean) {
        ConsentStore.set(context, given)
        // Lo que pase en Instagram con la casilla desmarcada lo decidió el usuario: no es un hueco.
        if (!given) ServiceHealth.clearHeartbeat(context)
        // Con el servicio en marcha, lo que pasó en Instagram sin consentimiento no cuenta como
        // "contador parado". Sin servicio no se escribe: no hay ningún contador que vigilar todavía.
        if (given && LiveCounter.state.value.serviceRunning) {
            ServiceHealth.recordHeartbeat(context, System.currentTimeMillis(), force = true)
        }
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShortVideoAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    /**
     * true si el APK se instaló desde un archivo (descargado o recibido), no por cable ni desde Play.
     * En ese caso Android 13+ bloquea la accesibilidad, y Android 15+ también el acceso a datos de uso,
     * hasta que se pulsa "Permitir ajustes restringidos".
     */
    fun installedFromFile(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val source = context.packageManager.getInstallSourceInfo(context.packageName).packageSource
            source == PackageInstaller.PACKAGE_SOURCE_LOCAL_FILE ||
                source == PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE
        } catch (e: Exception) {
            false
        }
    }

    fun openUsageAccessSettings(context: Context) {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, withPackage)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAccessibilitySettings(context: Context) {
        // Los Ajustes de Pixel/AOSP resaltan el servicio con estos extras; otros fabricantes los ignoran.
        val key = ComponentName(context, ShortVideoAccessibilityService::class.java).flattenToString()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        if (!tryStart(context, intent)) tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openBatterySettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    /** Consejo específico para fabricantes que cierran servicios en segundo plano. */
    fun manufacturerBatteryTip(context: Context): String? {
        val app = context.getString(R.string.app_name)
        return when (Build.MANUFACTURER.lowercase()) {
            "xiaomi", "redmi", "poco" ->
                "En Xiaomi: Ajustes > Apps > $app > Ahorro de batería > \"Sin restricciones\", y activa " +
                    "\"Inicio automático\". Además, bloquea $app en Recientes (mantén pulsada su tarjeta y toca el " +
                    "candado). Si aun así deja de contar, apaga y enciende el contador o reinicia el móvil."
            "huawei", "honor" ->
                "En Huawei/Honor: Ajustes > Batería > Inicio de aplicaciones > $app > gestionar manualmente y activa todo."
            "samsung" ->
                "En Samsung: Ajustes > Batería > Límites de uso en segundo plano: añade $app a \"Apps que nunca " +
                    "se suspenden\" y desactiva \"Poner en suspensión apps no usadas\"."
            "oppo", "realme", "oneplus" ->
                "En OPPO/realme/OnePlus: Ajustes > Batería > $app > permite la actividad en segundo plano."
            "vivo", "iqoo" ->
                "En vivo: Ajustes > Batería > Consumo en segundo plano > $app > permitir."
            else -> null
        }
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
