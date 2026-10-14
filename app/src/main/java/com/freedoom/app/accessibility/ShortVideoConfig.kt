package com.freedoom.app.accessibility

/**
 * ÚNICO sitio donde viven los identificadores de pantalla de cada app.
 *
 * Cuando Instagram (o TikTok, YouTube...) se actualice y deje de contar bien:
 *  1. Abre el modo depuración de FreeDoom y usa la app un rato.
 *  2. Mira en el registro qué ids de vista aparecen en la pantalla de vídeos cortos
 *     y cuál es la vista que emite VIEW_SCROLLED al pasar de vídeo.
 *  3. Actualiza las listas de abajo.
 */
data class ShortVideoPlatform(
    val packageName: String,
    val label: String,
    /** Ids de vista cuya presencia indica que estamos en la pantalla de vídeos cortos. */
    val screenViewIds: List<String>,
    /** Ids de la vista que se desplaza de un vídeo al siguiente (el "pager"). */
    val pagerViewIds: List<String>,
    /**
     * Clases que se aceptan como pager si la vista no expone id. Solo se usan
     * cuando ya sabemos que estamos en la pantalla de vídeos cortos.
     */
    val pagerClassNames: List<String> = emptyList(),
    val enabled: Boolean = true,
)

object ShortVideoConfig {

    /**
     * Si el pager no informa del índice del vídeo, los eventos de scroll separados
     * por más de este tiempo se consideran gestos distintos (= vídeos distintos).
     */
    const val GESTURE_GAP_MS = 400L

    /** Tiempo sin detectar la pantalla antes de dar por hecho que el usuario salió. */
    const val EXIT_GRACE_MS = 1_500L

    /** Silencio (sin eventos de la app) tras el cual se cierra el vídeo abierto. */
    const val MAX_SILENT_GAP_MS = 3 * 60_000L

    /** Cada cuánto, como máximo, se inspecciona el árbol de vistas en cambios de contenido. */
    const val SCREEN_CHECK_THROTTLE_MS = 700L

    private fun ig(id: String) = "com.instagram.android:id/$id"

    val platforms: List<ShortVideoPlatform> = listOf(
        ShortVideoPlatform(
            packageName = "com.instagram.android",
            label = "Instagram Reels",
            // Candidatos iniciales; se confirman con el registro de depuración.
            screenViewIds = listOf(
                ig("clips_viewer_view_pager"),
                ig("clips_viewer_container"),
                ig("clips_video_container"),
            ),
            pagerViewIds = listOf(ig("clips_viewer_view_pager")),
            pagerClassNames = listOf(
                "androidx.viewpager.widget.ViewPager",
                "androidx.viewpager2.widget.ViewPager2",
                "androidx.recyclerview.widget.RecyclerView",
            ),
        ),
        // Fase 1: TikTok, YouTube Shorts y Facebook. Desactivados en la fase 0.
        ShortVideoPlatform("com.zhiliaoapp.musically", "TikTok", emptyList(), emptyList(), enabled = false),
        ShortVideoPlatform("com.ss.android.ugc.trill", "TikTok", emptyList(), emptyList(), enabled = false),
        ShortVideoPlatform("com.google.android.youtube", "YouTube Shorts", emptyList(), emptyList(), enabled = false),
        ShortVideoPlatform("com.facebook.katana", "Facebook Reels", emptyList(), emptyList(), enabled = false),
    )

    val enabledPlatforms: List<ShortVideoPlatform> get() = platforms.filter { it.enabled }

    fun forPackage(packageName: String): ShortVideoPlatform? =
        enabledPlatforms.firstOrNull { it.packageName == packageName }
}
