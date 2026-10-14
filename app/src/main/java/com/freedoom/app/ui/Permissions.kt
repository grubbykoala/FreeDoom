package com.freedoom.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.freedoom.app.accessibility.ShortVideoAccessibilityService
import com.freedoom.app.usage.UsageReader

/** Estado de los permisos y ajustes que necesita la app. */
data class PermissionState(
    val usageAccess: Boolean,
    val accessibilityEnabled: Boolean,
    val accessibilityConsent: Boolean,
    val ignoringBatteryOptimizations: Boolean,
)

object Permissions {
    private const val PREFS = "freedoom"
    private const val KEY_CONSENT = "accessibility_consent"

    fun read(context: Context) = PermissionState(
        usageAccess = UsageReader(context).hasPermission(),
        accessibilityEnabled = isAccessibilityEnabled(context),
        accessibilityConsent = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSENT, false),
        ignoringBatteryOptimizations = context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName),
    )

    fun setAccessibilityConsent(context: Context, given: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, given).apply()
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, ShortVideoAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    fun openUsageAccessSettings(context: Context) {
        val withPackage = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
        if (!tryStart(context, withPackage)) tryStart(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openAccessibilitySettings(context: Context) {
        tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
    fun manufacturerBatteryTip(): String? = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" ->
            "En Xiaomi: Ajustes > Apps > FreeDoom > Ahorro de batería > \"Sin restricciones\", y activa \"Inicio automático\"."
        "huawei", "honor" ->
            "En Huawei/Honor: Ajustes > Batería > Inicio de aplicaciones > FreeDoom > gestionar manualmente y activa todo."
        "samsung" ->
            "En Samsung: Ajustes > Batería > Límites de uso en segundo plano: quita FreeDoom de \"Apps en suspensión\"."
        "oppo", "realme", "oneplus" ->
            "En OPPO/realme/OnePlus: Ajustes > Batería > FreeDoom > permite la actividad en segundo plano."
        "vivo", "iqoo" ->
            "En vivo: Ajustes > Batería > Consumo en segundo plano > FreeDoom > permitir."
        else -> null
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}
