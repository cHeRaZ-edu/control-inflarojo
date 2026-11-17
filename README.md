# control-inflarojo

Control remoto IR sin anuncios para tele AOC (probado contra LC42H163), usando el emisor infrarrojo del celular (POCO X3 Pro, POCO F7, etc.).

- Kotlin, sin AndroidX ni dependencias extra.
- minSdk 24, targetSdk 35.
- Protocolo NEC a 38 kHz.

## Descargar

El APK listo está en [Releases](https://github.com/cHeRaZ-edu/control-inflarojo/releases/latest).

Para sacar una versión nueva, crea un Release en GitHub (Releases → Create a new release → tag nuevo `vX.Y` sobre `main` → Publish), o:

```bash
gh release create v1.2 --target main --title v1.2 --notes ""
```

GitHub Actions lo compila (`.github/workflows/release.yml`) y lo publica. El APK se firma con `app/control.keystore`, que está en el repo a propósito: así cada versión se instala encima de la anterior.

## Compilar local

Ábrelo en Android Studio y dale Run, o desde terminal:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Si no tienes adb, pasa `app-debug.apk` al celular e instálalo (activa "instalar apps desconocidas").

## Uso

- **Control**: botones grandes. Los botones transparentes todavía no tienen código asignado.
- **Escáner (escondido)**: mantén presionado el título **"Control TV"**.

## Códigos de fábrica (IRDB `codes/AOC/TV/0,189.csv`)

NEC, address `0x00`, subaddress `0xBD` (189).

| Botón | Comando |
|---|---|
| Power | 1 |
| Mute | 4 |
| Fuente | 5 |
| Menú | 10 |
| Derecha | 12 |
| Izquierda | 16 |
| Arriba | 24 |
| Abajo | 28 |
| Info | 64 |
| Canal anterior | 68 |
| PC / VGA | 69 |
| Sleep | 19 |
| Formato | 20 |
| Modo de imagen | 3 (encontrado con el escáner, no viene en IRDB) |
| HDMI 1 directo | 6 (encontrado con el escáner) |
| 1–9 | 21, 22, 23, 25, 26, 27, 29, 30, 31 |
| 0 | 65 |

Ese set **no trae** Vol ±, Canal ±, OK, Salir, Input ni HDMI/Componente/AV directos: esos se encuentran con el escáner.

El escáner se brinca por defecto los comandos que ya tienen botón (casilla "Saltar comandos que ya tienen botón"), así no apaga la tele a cada vuelta. Tip: Vol +/− y Canal +/− casi siempre son comandos consecutivos; ya que encuentres uno, prueba ±1.

## Cómo encontrar los códigos que faltan

1. Abre el escáner y apunta el celular a la tele (a 1–2 m, con el emisor de arriba hacia la tele).
2. Presiona **Enviar** con comando `1`. Si la tele se apaga o enciende, la address `0x00/0xBD` es correcta.
3. Elige **Recorrer comando**, empieza en `0` y dale **▶ Auto**. Mira la tele.
4. Cuando la tele haga algo (sube el volumen, cambia de canal…), presiona **⏸ Pausa**. Usa **◀ ▶** y **Enviar** para confirmar cuál fue y luego **Asignar a botón…**.
   - Ojo con comandos que apagan la tele o abren el menú de fábrica: si pasa, presiona Pausa y regrésala con el control.

### Si el power (1) no hace nada

- Elige **Recorrer address** con comando `1`.
- Prueba con y sin **NEC estándar**:
  - Con NEC estándar marcado, la subaddress se calcula sola (address invertida).
  - Sin marcar, se queda fija en la subaddress que escribas.
- Cuando la tele se apague o encienda, esa es la address. Cambia a **Recorrer comando** desde ahí.

### Si nada responde

- Revisa que el IR sí salga: apunta el emisor a la cámara frontal de otro celular y presiona Enviar; se ve una lucecita morada.
- Es posible que la tele no use NEC (algunas TPV usan RC5/RC6). En ese caso se agrega otro codificador en `Nec.kt`.

## Estructura

```
app/src/main/java/com/edu/controlinflarojo/
  Nec.kt              codificador NEC → patrón en µs
  IrSender.kt         ConsumerIrManager en hilo aparte (sin cola al repetir)
  Config.kt           botones, códigos por botón (SharedPreferences), defaults AOC
  MainActivity.kt     control (repetición al mantener Vol/Canal/flechas)
  ScannerActivity.kt  escáner de comando/address + asignación
  Ui.kt               helpers de UI por código
```
