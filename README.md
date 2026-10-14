# FreeDoom — fase 0 (prueba de concepto)

App Android que mide el tiempo con el móvil, los reels vistos y en qué apps se va el tiempo.
Todo se queda en el móvil: sin internet, sin cuentas, sin analítica.

Objetivo de esta fase: comprobar en un móvil real que el recuento automático de reels de
Instagram coincide con el conteo a mano.

## Probarla en tu móvil

1. **Instala Android Studio** (gratis): <https://developer.android.com/studio>. Al abrirlo por
   primera vez, acepta la instalación por defecto (descarga el SDK de Android).
2. **Abre el proyecto**: *File > Open* y elige la carpeta `FreeDoom`. Espera a que termine
   la sincronización de Gradle (barra inferior). Si propone actualizar el plugin de Gradle,
   puedes decir que no.
3. **Activa la depuración USB en el móvil**:
   - Ajustes > Información del teléfono > toca 7 veces "Número de compilación".
   - Ajustes > Sistema > Opciones de desarrollador > activa "Depuración USB".
4. **Conecta el móvil por cable** y acepta en el móvil el aviso "¿Permitir depuración USB?".
5. En Android Studio, arriba, elige tu móvil en la lista de dispositivos y pulsa ▶ (*Run*).
   Se instala "FreeDoom" (la versión de pruebas, con modo depuración).

## Permisos dentro de la app

En la pestaña **Permisos**:

1. **Acceso a datos de uso** → "Abrir Ajustes" → activa FreeDoom. Sin esto no hay tiempos.
2. **Contador de reels** → lee el aviso, marca la casilla y pulsa "Activar en Ajustes".
   En Accesibilidad, entra en "Apps instaladas" / "Servicios descargados" y activa
   "FreeDoom · contador de vídeos cortos".
   - Si el interruptor sale gris con "Ajuste restringido": Ajustes > Apps > FreeDoom >
     menú ⋮ arriba a la derecha > "Permitir ajustes restringidos", y vuelve a intentarlo.
3. **Batería** → quita FreeDoom de la optimización de batería (sobre todo en Xiaomi,
   Huawei, Samsung, OPPO y vivo), o el sistema puede apagar el contador.

## Calibración (lo que hay que hacer para cerrar la fase 0)

1. Abre FreeDoom > **Debug** > "Empezar tanda".
2. Ve a Instagram > Reels y pasa reels contándolos a mano. Haz varias tandas: ritmo normal,
   muy rápido (deslizando sin parar) y alguno a medio deslizar que vuelve atrás.
   El primer reel que ves al entrar también cuenta.
3. Vuelve a FreeDoom > Debug y compara "Recuento automático de esta tanda" con tu cuenta.
4. Pulsa "Compartir" para enviarme el registro (por ejemplo, a ti mismo por correo o
   Telegram) y pégamelo en el chat. Con él ajusto los identificadores de pantalla.

El registro solo contiene tipos de evento, clases e ids técnicos de las vistas de Instagram;
nunca textos, nombres de cuentas ni imágenes.

## Para programar

- Kotlin + Jetpack Compose, Room, WorkManager. `minSdk 26`, `compileSdk 35`.
- `accessibility/ShortVideoConfig.kt`: **único sitio** con los ids de pantalla de cada app.
  Cuando Instagram se actualice y deje de contar bien, se cambia ahí.
- `accessibility/ShortVideoTracker.kt`: reglas de recuento (sin Android, con tests).
- `accessibility/ShortVideoAccessibilityService.kt`: servicio de accesibilidad.
- `usage/`: tiempo por app y desbloqueos con `UsageStatsManager`.
- `ui/DebugScreen.kt` y `debug/DebugLog.kt`: modo depuración, solo en builds debug.
- Tests: `./gradlew test`.

### Cómo se cuenta un reel

- Entrar en la pantalla de Reels cuenta el reel que ya se ve.
- Si Instagram informa del índice del vídeo, cada vez que el visor se asienta en un índice
  distinto suma uno. Los eventos intermedios de un mismo deslizamiento no cuentan, así que
  un gesto nunca suma dos.
- Si no informa del índice, cada ráfaga de eventos de desplazamiento separada de la anterior
  por más de 400 ms es un gesto, y cada gesto es un reel.
- Volver atrás a un reel anterior también cuenta (es un vídeo visto otra vez).
- El tiempo de cada reel va desde que aparece hasta que aparece el siguiente, se sale de
  Reels o se apaga la pantalla.
