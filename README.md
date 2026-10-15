# FreeDoom — fase 0 (prueba de concepto)

App Android que mide el tiempo con el móvil, los reels vistos y en qué apps se va el tiempo.
Todo se queda en el móvil: sin internet, sin cuentas, sin analítica.

Objetivo de esta fase: comprobar en un móvil real que el recuento automático de reels de
Instagram coincide con el conteo a mano.

## Probarla en tu móvil

1. **Instala Android Studio** (gratis): <https://developer.android.com/studio>. Al abrirlo por
   primera vez, acepta la instalación por defecto (descarga el SDK de Android).
2. **Abre el proyecto**: *File > Open* y elige la carpeta `FreeDoom`. Espera a que termine
   la sincronización de Gradle (barra inferior).
   - **No aceptes ninguna sugerencia de actualizar versiones**: ni "Upgrade Gradle wrapper",
     ni el asistente de actualización del plugin de Android (AGP Upgrade Assistant), ni los
     avisos de "versión más nueva disponible" en `libs.versions.toml`. Las versiones están
     fijadas a propósito y tienen que ir juntas; di que no o cierra el aviso.
   - **Si la sincronización falla** con "Unsupported Java", "Incompatible Gradle JVM" o
     "Unsupported class file major version 69" (pasa con las versiones nuevas de Android
     Studio, que traen Java 25):
     1. En Mac, *Android Studio > Settings…*; en Windows, *File > Settings…*
     2. Ve a *Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK >
        Download JDK…*
     3. Elige *Version* **21** (vale también la 17) y cualquier proveedor (por ejemplo
        JetBrains Runtime o Eclipse Temurin). Pulsa *Download* y luego *OK*.
     4. *File > Sync Project with Gradle Files*.

     Para saber si te afecta: *Help > About* (en Mac, *Android Studio > About Android
     Studio*). Si "Runtime version" empieza por 21, no tienes que hacer nada.
3. **Activa la depuración USB en el móvil**:
   - Ajustes > Información del teléfono > toca 7 veces "Número de compilación".
   - Ajustes > Sistema > Opciones de desarrollador > activa "Depuración USB".
4. **Conecta el móvil por cable** y acepta en el móvil el aviso "¿Permitir depuración USB?".
5. En Android Studio, arriba, elige tu móvil en la lista de dispositivos y pulsa ▶ (*Run*).
   Se instala **"FreeDoom (debug)"**: la versión de pruebas, con modo depuración. Se llama
   distinto para no confundirla con la definitiva si algún día tienes las dos.

> **Ojo con el botón ■ Stop de Android Studio.** Si lo pulsas, o si fuerzas la detención de
> FreeDoom en Ajustes (o la cierra un "limpiador"), Android apaga el contador de reels. El
> panel te avisará: vuelve a activarlo con el botón "Abrir Accesibilidad".

## Permisos dentro de la app

En la pestaña **Permisos** (en las listas de Ajustes, la versión de pruebas aparece como
"FreeDoom (debug)"):

1. **Acceso a datos de uso** → "Abrir Ajustes" → activa FreeDoom. Sin esto no hay tiempos.
   Es el único paso imprescindible.
2. **Contador de reels** (opcional) → lee el aviso, marca la casilla y pulsa "Activar en
   Ajustes". En Accesibilidad, entra en "Apps instaladas" / "Servicios descargados" y activa
   **"FreeDoom debug · contador de vídeos cortos"** (en la versión definitiva se llama
   "FreeDoom · contador de vídeos cortos").
   - Android mostrará un aviso genérico de "control total del dispositivo". Sale con
     cualquier servicio de accesibilidad; FreeDoom solo lo usa para contar reels.
   - Sin la casilla marcada no se cuenta nada, aunque el servicio esté activado en Ajustes.
     Si la desmarcas, FreeDoom deja de contar.
3. **Batería** (recomendado) → quita FreeDoom de la optimización de batería (sobre todo en
   Xiaomi, Huawei, Samsung, OPPO y vivo), o el sistema puede apagar el contador. En Samsung,
   además: Batería > Límites de uso en segundo plano > "Apps que nunca se suspenden" > añade
   FreeDoom. En Xiaomi, bloquea FreeDoom en Recientes (el candado).

**Si un interruptor sale gris con "Ajuste restringido"**: pasa en el contador de reels
(Android 13 o posterior) y también en el acceso a datos de uso (Android 15 o posterior),
pero **solo si la app se instaló desde un archivo** (un APK descargado o recibido por
mensaje). Tócalo una vez y acepta el aviso; luego Ajustes > Apps > FreeDoom > menú ⋮ arriba
a la derecha > "Permitir ajustes restringidos", y vuelve a intentarlo. Para evitarlo,
instala por cable con Android Studio (como arriba) o, más adelante, desde la prueba cerrada
de Google Play.

## Si el contador no cuenta

El panel avisa cuando el contador de reels no está funcionando:

- **Apagado**: Android lo apaga si se fuerza la detención de FreeDoom (también con ■ Stop
  en Android Studio) o si un limpiador la cierra. Vuelve a activarlo. Si lo apagaste tú a
  propósito, desmarca la casilla en Permisos y el aviso desaparece.
- **Activado pero no funciona**: apágalo y vuelve a encenderlo en Ajustes > Accesibilidad;
  si sigue igual, reinicia el móvil y revisa la batería.
- **Huecos detectados**: mientras funciona, el contador apunta (como mucho una vez por
  minuto, sobrescribiendo la anterior) la hora de la última vez que recibió algo de
  Instagram, y la hora a la que se conecta; solo esas horas, nada más. Cuando vuelve a
  arrancar tras un cierre inesperado, mira si Instagram se abrió sin que él se enterase y
  cuánto tiempo estuvo abierto. Si pasa de 1 minuto, el panel lo dice, por ejemplo: "El
  contador estuvo parado de 14:05 a 19:30 (unos 32 min de Instagram sin contar)". Los reels
  de ese rato no se recuperan. Si lo apagas tú en Ajustes, no se considera un hueco.

En la pantalla **Depuración**, "Salud del contador" muestra cuándo se conectó, el último
latido y el último hueco.

## Calibración (lo que hay que hacer para cerrar la fase 0)

1. Abre FreeDoom > **Depuración** > "Empezar tanda". Las tandas van numeradas y se guardan
   en el móvil: si la app se cierra a mitad, la tanda sigue.
2. Ve a Instagram y pasa reels contándolos a mano.
3. Vuelve a FreeDoom > Depuración:
   - **"Guardados en la BD"** es la cifra que cuenta: lo que de verdad se guardó y verá el
     panel. "En vivo" es el contador en memoria; si no coinciden, se han perdido escrituras.
   - Escribe tu número en **"Tu cuenta a mano"** y mira la diferencia.
   - Pulsa "Terminar tanda": el resultado queda escrito en el registro (base de datos, en
     vivo, a mano y diferencia) y a la vista como "Último resultado".
4. Pulsa "Compartir registro" para enviarme el registro (por ejemplo, a ti mismo por correo
   o Telegram) y pégamelo en el chat. Con él ajusto los identificadores de pantalla.
   "Vaciar registro" y "Borrar hoy" piden confirmación.

Haz una tanda de cada (unos 20 reels si se puede, salvo que se diga otra cosa):

1. Ritmo normal.
2. Muy rápido, deslizando sin parar.
3. Medio deslizamiento con rebote: empiezas a pasar y lo sueltas para que vuelva.
4. Abrir Instagram en Inicio y quedarte allí un minuto sin entrar en Reels (debe dar 0).
5. Un reel abierto desde un mensaje.
6. Reels abiertos desde Explorar.
7. Reels abiertos desde un perfil.
8. Instagram que abre directamente en Reels.
9. Reels largos, de hasta 3 minutos.
10. Bloquear y desbloquear el móvil a mitad de un reel (no debe sumar uno más).
11. Ir a WhatsApp y volver al mismo reel (no debe sumar uno más).
12. Reels con anuncios entre medias (los anuncios cuentan).
13. Tres reels distintos abiertos seguidos desde el mismo chat, en menos de 3 minutos (deben
    contar 3).

El registro solo contiene tipos de evento, clases e ids técnicos de las vistas de Instagram;
nunca textos, nombres de cuentas ni imágenes.

## Qué cuenta como reel

- **Cuenta** cada vídeo del visor de Reels a pantalla completa, llegues desde la pestaña
  Reels, Explorar, un perfil o un mensaje. Aunque lo veas medio segundo.
- **Los anuncios de Reels cuentan**: no se pueden distinguir sin leer el texto de la
  pantalla, y FreeDoom no lee textos.
- **Volver deslizando a un reel anterior cuenta**: es un gesto y un vídeo visto otra vez.
- **Volver al mismo reel sin deslizar no cuenta otra vez** si vuelves en menos de 3 minutos:
  tras bloquear el móvil, ir a otra app o mirar un perfil o un audio. Para saber que es "el
  mismo", FreeDoom compara la identidad técnica del visor (no su contenido): un reel nuevo
  abierto desde un mensaje o desde Explorar abre otro visor y sí cuenta. *Pendiente de que
  Santi lo confirme.*
- **No cuentan** las Historias ni los vídeos que se reproducen dentro del feed de Inicio
  (se decidirá en la fase 1 si estos últimos deben contar).

## ¿Por qué no coincide con Bienestar digital?

- FreeDoom mide el **tiempo en primer plano**: la app que tienes abierta delante. Bienestar
  digital y otros temporizadores miden el tiempo **visible**, que incluye la ventana
  flotante (PiP). Un vídeo de YouTube o Maps en ventana flotante no suma en FreeDoom.
- Con **pantalla dividida**, cada app suma su rato, pero el total no cuenta dos veces el
  mismo tiempo. Por eso la suma de la lista puede superar al total.
- El escritorio (lanzador) no cuenta como app.
- Sin bloqueo de pantalla ("Ninguno"), los **desbloqueos** son los encendidos de pantalla
  (el panel lo indica).
- Cada sistema redondea y agrupa a su manera: unos minutos de diferencia son normales.

## Para programar

- Kotlin + Jetpack Compose, Room, WorkManager. `minSdk 26`, `compileSdk 36`, `targetSdk 36`.
  Google Play sube cada agosto el `targetSdk` mínimo para publicar: revísalo antes de cada
  subida en <https://developer.android.com/google/play/requirements/target-sdk>.
- **Versiones fijadas** (combinación revisada): AGP 8.9.1, Gradle 8.11.1, Kotlin 2.1.0,
  KSP 2.1.0-1.0.29, Room 2.6.1, Compose BOM 2024.12.01, WorkManager 2.10.0, Java 17.
  Kotlin, KSP y Room se actualizan siempre juntos. La migración grande (AGP 9, Kotlin 2.2+,
  Room 2.7+) se hará de una vez en la fase 1, cuando el proyecto ya compile.
- **Sin internet, garantizado**: el manifiesto quita `INTERNET` con `tools:node="remove"`
  aunque una librería lo pida. Cada vez que añadas una dependencia, mira la pestaña
  *Merged Manifest* de `AndroidManifest.xml`.
- **Copias**: `allowBackup="false"` y `res/xml/data_extraction_rules.xml` excluyen todo de la
  copia en la nube y de la transferencia entre móviles.
- **Esquema de Room**: tras la primera compilación aparece
  `app/schemas/com.freedoom.app.data.AppDatabase/1.json`. Súbelo a git: hará falta para
  migrar sin borrar datos cuando haya testers.
- `accessibility/ShortVideoConfig.kt`: **único sitio** con los ids de pantalla de cada app.
  Cuando Instagram se actualice y deje de contar bien, se cambia ahí.
- `accessibility/ShortVideoTracker.kt`: reglas de recuento (sin Android, con tests).
- `accessibility/ShortVideoAccessibilityService.kt`: servicio de accesibilidad.
- `accessibility/ServiceHealth.kt`: latido del contador y huecos detectados.
- `data/ConsentStore.kt`: la casilla de aceptación del contador.
- `usage/`: tiempo por app y desbloqueos con `UsageStatsManager`.
- `ui/DebugScreen.kt`, `debug/DebugLog.kt` y `debug/CalibrationStore.kt`: modo depuración,
  solo en builds debug.
- `app/src/debug/res/`: nombres de la versión de pruebas ("FreeDoom (debug)").
- Para publicar (fase 1): *Build > Generate Signed App Bundle* con la clave de subida; no
  hace falta poner la firma en Gradle.
- Tests: `./gradlew test`.

### Cómo se cuenta un reel

- Entrar en la pantalla de Reels cuenta el reel que ya se ve.
- Solo cuenta el visor de Reels visible: Instagram mantiene uno oculto y precargado en otras
  pantallas, y sus movimientos no suman.
- Si Instagram informa del índice del vídeo, cada vez que el visor se asienta en un índice
  distinto suma uno. Los eventos intermedios de un mismo deslizamiento no cuentan, así que
  un gesto nunca suma dos.
- Al abrir un reel concreto (desde un mensaje, un perfil o Explorar), Instagram coloca el
  visor a saltos: lo que pasa en los primeros 400 ms tras entrar no suma.
- Si no informa del índice, cada ráfaga de eventos de desplazamiento separada de la anterior
  por más de 400 ms es un gesto, y cada gesto es un reel.
- Volver atrás a un reel anterior también cuenta (es un vídeo visto otra vez).
- Volver al mismo reel en menos de 3 minutos sin deslizar es una reanudación y no cuenta
  (pendiente de confirmar por Santi).
- El tiempo de cada reel va desde que aparece hasta que aparece el siguiente, se sale de
  Reels, se pasa a otra app o se apaga la pantalla. Si el contador se para antes de
  cerrarlo, su duración queda como desconocida y no entra en la media.
