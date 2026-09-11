# Zepp OS Mini Program - Hallazgos Críticos y Guía de Referencia

> ⚠️ **FUENTE DE CONSULTA OBLIGATORIA** para cualquier desarrollo de Zepp OS Mini Program
> 
> Este documento contiene hallazgos críticos basados en pruebas reales con **Amazfit Active Max** (Zepp OS 5.0, API Level 4.2) y comparación con la documentación oficial.
> 
> **Última actualización**: 2026-06-12
> **Fuentes verificadas**: https://docs.zepp.com/, https://github.com/zepp-health/zeppos-samples

---

## 🔴 Hallazgo #1: Extensión de archivos `.page.js` y `.layout.js` (CRÍTICO)

### El problema
La documentación oficial de `app.json` muestra:
```json
"page": {
  "pages": ["page/index"]
}
```

Pero esto **NO funciona**. El archivo se compila pero la página no renderiza (pantalla negra).

### La solución correcta
La página **DEBE** usar la extensión `.page.js` y el archivo de estilos **DEBE** ser `.layout.js`:

```
page/
└── home/
    ├── index.page.js      ← Archivo de lógica de la página
    └── index.page.layout.js ← Archivo de estilos separados
```

Y en `app.json`:
```json
"page": {
  "pages": ["page/home/index.page"]
}
```

### Importación de estilos
En el archivo `.page.js` se importan los estilos así:
```javascript
import { TEXT_STYLE } from "zosLoader:./index.page.[pf].layout.js"
```

> **NO** usar `./index.page.layout.js` directamente. El patrón `zosLoader:./[pf]` es un loader especial de Zepp OS.

### Ejemplo completo funcional
**index.page.layout.js:**
```javascript
export const TEXT_STYLE = {
  x: 0, y: 100, w: 480, h: 80,
  color: 0xFFFFFF, text_size: 32,
  align_h: 'CENTER_H',
  text: 'Hello'
}
```

**index.page.js:**
```javascript
import * as hmUI from "@zos/ui"
import { log as Logger } from "@zos/utils"
import { TEXT_STYLE } from "zosLoader:./index.page.[pf].layout.js"

const logger = Logger.getLogger("myapp")

Page({
  onInit() { logger.debug("onInit") },
  build() { 
    hmUI.createWidget(hmUI.widget.TEXT, TEXT_STYLE)
  },
  onDestroy() { logger.debug("onDestroy") }
})
```

---

## 🔴 Hallazgo #2: Importación de módulos debe ser namespace (para módulos built-in)

### El problema
```javascript
import { hmUI } from "@zos/ui"  // ❌ No funciona
```

### La solución correcta
```javascript
import * as hmUI from "@zos/ui"  // ✅ Namespace import para módulos built-in
```

### Excepción: @zeppos/zml
```javascript
import { BasePage } from '@zeppos/zml/base-page'  // ✅ Named import para ZML
import { BaseSideService } from '@zeppos/zml/base-side'  // ✅ Named import para ZML
```

El asterisco importa todo el módulo, garantizando que las APIs estén disponibles. Para ZML (dependencia npm), se usan named imports.

---

## 🔴 Hallazgo #3: El icono DEBE estar en `assets/<target-name>/`

### El problema
Poner el icono en `assets/` raíz o usar rutas absolutas causa errores de compilación o que el icono no se incluya en el paquete final.

### La solución correcta
El icono debe estar en la carpeta del target específico:
```
assets/
└── active-max/
    └── icon.png    ← Aquí para target "active-max"
```

Y en `app.json`, **NO** especificar `icon` en la sección `app` (se toma automáticamente del target):
```json
{
  "app": {
    // NO incluir "icon" aquí
  }
}
```

Si el icono no se incluye, el compilador dice: `0 files, 3ms, done!` en la sección `[PNG2TGA]`.

**Verificar que el output diga**: `[PNG2TGA] 1 files, Xms, done!`

---

## 🟡 Hallazgo #4: API Level del dispositivo y runtime config

### Amazfit Active Max
- **API Level**: 4.2
- **Zepp OS**: 5.0
- **deviceSource**: `10813697`, `10813699`

### Configuración correcta del runtime
```json
"runtime": {
  "apiVersion": {
    "compatible": "4.0",
    "target": "4.0",
    "minVersion": "4.0"
  }
}
```

> **Nota**: El ejemplo oficial usa `"4.0"` (sin decimales extra como `4.0.0`).

---

## 🟡 Hallazgo #5: Comandos y herramientas

### Verificar versión de Node
```bash
node -v
# Debe ser v20.x.x (NO v22 - incompatible con zeus-cli)
```

### Cambiar versión con fnm
```bash
fnm install 20
fnm use 20
```

### Ver QR generado
- El QR **expira** después de un tiempo (mostrado en la terminal)
- Si expira, volver a ejecutar `zeus preview`

### Ver logs del dispositivo real
1. Activar **Modo Desarrollador** en app Zepp (7 clics en Acerca de)
2. En el menú de Modo Desarrollador, tocar el icono del Mini Program
3. Activar "Iniciar recolección de logs"
4. Ejecutar el Mini Program en el reloj
5. Ver los logs en el teléfono

---

## 🟢 Hallazgo #6: APIs que SÍ funcionan en este dispositivo

| API | Estado | Notas |
|-----|--------|-------|
| `@zos/sensor` (Sleep) | ✅ | `Sleep.getSleepingStatus()`, `Sleep.getInfo()` |
| `@zos/storage` (localStorage) | ✅ | Persistencia funciona correctamente |
| `@zos/timer` (createSysTimer) | ✅ | Timer periódico cada 2 min |
| `@zos/app` (getPackageInfo) | ✅ | Información del paquete |
| `@zos/ble` | ⚠️ | Solo desde Device App, no desde Page |
| `@zos/messaging` (peerSocket) | ⚠️ | Solo en app-service y app-side, NO en Page |
| `@zeppos/zml/base-page` | ✅ | Comunicación Device → Side (requiere npm install) |
| `@zeppos/zml/base-side` | ✅ | Comunicación Side → Device (requiere npm install) |
| `fetch` (Side Service) | ✅ | HTTP requests desde Side Service |

---

## 🔴 Hallazgo #7: Lo que NO hacer

### ❌ NO usar `hmUI.reloadPage()` (no existe en Zepp OS v3+)
```javascript
// ❌ Esto causa pantalla negra
hmUI.reloadPage()
```

### ❌ NO usar rutas absolutas para el icono
```json
// ❌ Falla en compilación
"icon": "C:/Users/.../icon.png"
```

### ❌ NO usar `Page` con archivo `.js` simple
```javascript
// ❌ Pantalla negra
// page/index.js
Page({ build() { ... } })
```

### ❌ NO usar `hmUI.showToast()` (puede no existir en todas las versiones)
```javascript
// ❌ Puede causar crash
hmUI.showToast({ text: 'Hello' })
```

---

## 📚 Estructura de proyecto validada

```
zepp-sleep-detector/
├── app.json                    ← Configuración principal
├── app.js                      ← Lógica del Device App
├── app-side/
│   ├── index.js                ← Side Service (corre en app Zepp)
│   └── i18n/
│       └── en-US.po            ← ⚠️ REQUERIDO
├── app-service/
│   └── index.js                ← Background service
├── page/
│   ├── home/
│   │   ├── index.page.js       ← ⚠️ EXTENSIÓN OBLIGATORIA
│   │   └── index.page.layout.js ← Estilos separados
│   └── i18n/
│       └── en-US.po            ← ⚠️ REQUERIDO
├── shared/
│   ├── device-polyfill.js
│   ├── message.js              ← MessageBuilder (Device)
│   └── message-side.js         ← MessageBuilder (Side)
├── assets/
│   └── <target>.r/             ← ⚠️ DEBE ser <target>.r o <target>.s
│       └── icon.png
└── icon.png                    ← Respaldo (opcional)
```

---

## 🔴 Hallazgo #8: El icono en `app.json` requiere ruta absoluta en Windows

### El problema
En Windows, las rutas relativas para el campo `"icon"` en `app.json` no funcionan correctamente. El ZPM no encuentra el icono aunque exista en la ruta especificada.

### La solución (workaround actual)
Usar **ruta absoluta con barras `/`** (no `\`):
```json
"icon": "C:/Users/user/project/zepp-sleep-detector/assets/active-max.r/icon.png"
```

> **Nota**: Esto no es ideal - la documentación oficial muestra rutas relativas. Pero en este entorno Windows es lo único que compila consistentemente.

### Verificación
El output debe decir:
- `[RESIZE] The target size is same as input image` ← icono procesado
- `[PNG2TGA] 0 files, Xms, done!` ← ⚠️ **BUG CONOCIDO**: dice "0 files" pero el QR se genera. El icono se incluye a través de la ruta absoluta.

---

## 🔴 Hallazgo #9: El `appId` puede ser muy grande

### El problema
El `appId` 400001234 es demasiado grande. Los `appId` oficiales de Zepp suelen ser números más pequeños (ej: 1000089, 20001).

### Solución
Usar un `appId` más pequeño, como `20001` o `10001`.

---

## 🔴 Hallazgo #10: La estructura completa del proyecto debe seguir el patrón oficial EXACTO

### Estructura mínima validada que compila
```
zepp-sleep-detector/
├── app.json                    ← Con icon absoluto
├── app.js                      ← App principal
├── app-side/
│   ├── index.js
│   └── i18n/
│       └── en-US.po
├── app-service/
│   └── index.js
├── page/
│   ├── home/
│   │   ├── index.page.js
│   │   └── index.page.layout.js
│   └── i18n/
│       └── en-US.po
├── shared/
│   ├── device-polyfill.js
│   ├── message.js
│   └── message-side.js
└── assets/
    └── <target>.r/
        └── icon.png
```

### `app.json` mínimo viable
```json
{
  "configVersion": "v3",
  "app": {
    "appId": 20001,
    "appName": "Test",
    "appType": "app",
    "version": { "code": 1, "name": "1.0.0" },
    "icon": "C:/ruta/absoluta/a/assets/test.r/icon.png",
    "vender": "test",
    "description": "Test"
  },
  "runtime": {
    "apiVersion": { "compatible": "4.0", "target": "4.0", "minVersion": "4.0" }
  },
  "permissions": [],
  "targets": {
    "test": {
      "module": {
        "page": { "pages": ["page/home/index.page"] }
      },
      "platforms": [{ "st": "r" }],
      "designWidth": 480
    }
  },
  "i18n": { "en-US": { "appName": "Test" } },
  "defaultLanguage": "en-US"
}
```

---

## 🟢 Hallazgo #13: Patrón de UI validado en simulador y reloj real

### Estructura funcional confirmada
**`page/home/index.page.js`** + **`page/home/index.page.layout.js`** funcionan en:
- ✅ Simulador Zepp v2.1.1
- ✅ Amazfit Active Max (Zepp OS 5.0, API 4.2)

### Código que funciona (v1.0.1)
**index.page.layout.js:**
```javascript
export const BG_STYLE = {
  x: 0, y: 0, w: 480, h: 480, color: 0x000000
}
export const TITLE_STYLE = {
  x: 0, y: 50, w: 480, h: 50,
  color: 0xFFFFFF, text_size: 32,
  align_h: 'CENTER_H',
  text: 'Sleep Detector'
}
// ... más estilos
```

**index.page.js:**
```javascript
import * as hmUI from "@zos/ui"
import { log as Logger } from "@zos/utils"
import { localStorage } from "@zos/storage"
import {
  BG_STYLE, TITLE_STYLE, /* ... */
} from "zosLoader:./index.page.[pf].layout.js"

const logger = Logger.getLogger("sleep-detector")

function getState() {
  return localStorage.getItem("sleep_detection_enabled") === "true"
}

Page({
  onInit() { logger.log("onInit") },
  build() {
    const isEnabled = getState()
    // ... crear widgets con hmUI.createWidget
    button.addEventListener(hmUI.event.CLICK_DOWN, () => {
      // ... handler
    })
  },
  onDestroy() { logger.log("onDestroy") }
})
```

### API que funciona (validada)
| API | Funciona | Notas |
|-----|----------|-------|
| `import * as hmUI from "@zos/ui"` | ✅ | Namespace import |
| `hmUI.createWidget(hmUI.widget.FILL_RECT, ...)` | ✅ | Fondo |
| `hmUI.createWidget(hmUI.widget.TEXT, ...)` | ✅ | Texto |
| `import { log as Logger } from "@zos/utils"` | ✅ | Logger |
| `import { localStorage } from "@zos/storage"` | ✅ | Persistencia |
| `hmUI.event.CLICK_DOWN` | ✅ | Eventos |
| `hmUI.reloadPage()` | ✅ | Recargar page |
| `zosLoader:./[pf].layout.js` | ⚠️ | Warning pero funciona |
| `app-service` con `localStorage` | ✅ | Background service |

### Limitaciones conocidas
- `@zeppos/zml` requiere `npm i @zeppos/zml` (no es built-in)
- `app-side` requiere `"path": "app-side/index"` (con `/index`) en `app.json`
- `app-side` no se compila con `[QJSC]` pero funciona en runtime
- `PNG2TGA` reporta "0 files" pero el icono se incluye vía ruta absoluta
- Loop de rebuild infinito en `zeus dev` requiere workaround

---

## 🔴 Hallazgo #12: Nombre del target en `zeus dev` debe ser el nombre completo

### El problema
Al usar `zeus dev -t <target>`, el target name debe ser el **nombre completo con espacios y mayúsculas**:
```bash
# ❌ Incorrecto
zeus dev -t active-max
# Warning: 'active-max' is not a valid device

# ✅ Correcto
zeus dev -t "Amazfit Active Max"
```

### Lista de targets válidos
El CLI muestra los nombres exactos. Ejemplos para v2.1.1:
- `"Amazfit Active Max"`
- `"Amazfit Balance 3"`
- `"Amazfit T-Rex 3"`
- `"Amazfit Cheetah 2 Pro"`
- Etc.

### Cómo encontrar el nombre exacto
1. Ejecutar `zeus dev` sin `-t`
2. Ver la lista interactiva que aparece
3. Usar el nombre exacto (con comillas)

---

## 🔴 Hallazgo #11: Loop infinito de rebuild en `zeus dev` con assets (CRÍTICO)

### El problema
Al ejecutar `zeus dev` (modo watch conectado al simulador), el compilador entra en un **loop infinito** de rebuild:

```
assets\active-max.r\icon.png_origin add
[RESIZE] The target size is same as input image
assets\active-max.r\icon.png change
rebuild done
refreshing simulator...
watching the changes in this project...
assets\active-max.r\icon.png_origin unlink
rebuilding...
[ℹ] Start building package, device sources: 10813697, 10813699.
... (se repite infinitamente)
```

### Causa raíz
El ZPM (Zepp Package Manager) crea un archivo temporal `icon.png_origin` como backup, modifica el `icon.png` original, y elimina el temporal. **Este ciclo de 3 operaciones en el archivo dispara el watch de `zeus dev`**, que recompila, que vuelve a crear el temporal, y así infinitamente.

### Workarounds

#### Workaround A: Usar `zeus build` + carga manual (RECOMENDADO para diagnóstico)
```bash
# Detener cualquier zeus dev en ejecución
taskkill /F /IM node.exe

# Compilar una vez sin watch
cd zepp-sleep-detector
zeus build

# El .zab se genera en dist/
# Cargar manualmente arrastrando al simulador o usando el menú
```

#### Workaround B: Iniciar `zeus dev` y dejarlo correr
Una vez que el simulador carga el Mini Program, **NO** se vuelve a recompilar. El loop solo ocurre si los archivos cambian constantemente. Si no modificas archivos, el simulador mantiene el preview.

**Problema**: Cada vez que editas un archivo, el loop se reinicia.

#### Workaround C: Mover assets fuera del watch
Renombrar temporalmente `assets/active-max.r/` a `assets/_active-max.r/` durante el desarrollo, y restaurarlo solo para hacer `zeus build`.

### Solución permanente (pendiente)
- Reportar este bug al equipo de Zepp
- Mientras tanto, usar **Workaround A** para desarrollo

---

## 🔴 Hallazgo #12: Compilación exitosa pero el simulador no actualiza con `zeus build`

### El problema
`zeus build` compila exitosamente y genera `dist/*.zab`, pero **el simulador Zepp v2 no recarga automáticamente** el Mini Program cuando se reemplaza el archivo.

### Solución
1. **Opción 1**: Usar `zeus dev` (que SÍ recarga automáticamente, pero con el bug del loop)
2. **Opción 2**: Cerrar y reabrir el simulador después de cada `zeus build`
3. **Opción 3**: Arrastrar el archivo `.zab` al simulador manualmente

---

## 🟡 Hallazgo #14: Spam de `ScreenManager_getScreenByName` en simulador

### El síntoma
El log del simulador Zepp muestra líneas repetidas masivas:
```
ScreenManager_getScreenByName scan idx:0 candidate:0x80011614
ScreenManager_getScreenByName cmp idx:0 candidate:0x80011614 idptr:0x...
... (se repite para ~100 índices)
```

Esto se acompaña de un loop de destrucción/recreación de la página:
```
LOG > sleep-detector > page onDestroy invoked
LOG > sleep-detector > page onInit invoked
LOG > sleep-detector > page build invoked
```

### Causa
- Los `ScreenManager_getScreenByName` son **ruido interno del runtime del simulador**, no del código del Mini Program. No se pueden suprimir.
- El loop de `onDestroy → onInit → build` cada ~1 segundo es causado por el **bug de `zeus dev`** (ver Hallazgo #11): el watcher detecta cambios en los assets del icono, recompila, y recrea la página infinitamente.

### Solución
- Usar `zeus build` en lugar de `zeus dev` para compilación única sin watcher
- Si se necesita `zeus dev`, no tocar archivos después de la carga inicial
- Los logs de `ScreenManager` son normales y no indican un error en el código

---

## 🔄 Proceso de deploy verificado

1. **Crear/cambiar archivos** del proyecto
2. **Ejecutar**:
   ```bash
   cd zepp-sleep-detector
   zeus preview
   ```
3. **Verificar output**:
   - `[PNG2TGA] 1 files, Xms` ← icono OK
   - `[QJSC] 2 files, Xms` ← JS OK
   - QR generado exitosamente
4. **Escanear QR** con app Zepp en Modo Desarrollador
5. **Verificar en el reloj**

---

## ⚠️ Troubleshooting

### Pantalla negra
1. Verificar que el archivo de página tiene extensión `.page.js`
2. Verificar que existe un archivo `.layout.js` con los estilos
3. Verificar que los imports usan `zosLoader:./[pf].layout.js`
4. Revisar logs en app Zepp (Modo Desarrollador → icono del Mini Program)

### Icono no aparece
1. Verificar que el icono está en `assets/<target>/icon.png`
2. Verificar output: `[PNG2TGA] 1 files`
3. NO incluir `"icon"` en la sección `app` del app.json

### QR no se genera
1. Verificar que Node es v20 (no v22)
2. Usar `fnm use 20` si es necesario
3. Verificar que la versión de zeus es compatible

### Compilación falla
1. Verificar que todos los archivos referenciados en app.json existen
2. Verificar que las rutas de `page`, `app-side`, `app-service` son correctas
3. Revisar mensajes de error en la consola

---

## 📖 Recursos oficiales

- **Documentación principal**: https://docs.zepp.com/
- **Samples de GitHub**: https://github.com/zepp-health/zeppos-samples
- **Consola de desarrolladores**: https://console.zepp.com/
- **Discord oficial**: https://t.zepp.com/t/b6e70
- **GitHub Discussions**: https://github.com/orgs/zepp-health/discussions

---

## 🟢 Hallazgo #15: Comunicación Device App ↔ Side Service con ZML

### Arquitectura correcta
```
Device App (Page)  →  BLE interno  →  Side Service  →  fetch()  →  HTTP
     ↑                                       ↑
  BasePage                              BaseSideService
  this.request()                        onRequest()
```

### Pasos para implementar
1. `npm i @zeppos/zml` en el directorio del mini program
2. Usar `BaseApp` en `app.js`
3. Usar `BasePage` en la página con `this.request()`
4. Usar `BaseSideService` en el side service con `onRequest()`
5. Declarar `app-side` en `app.json` con `"path": "app-side/index"`

---

## 🟢 Hallazgo #16: `@zeppos/zml` (ZML) SÍ funciona con npm install

### El problema anterior
`@zeppos/zml` no funcionaba porque se intentaba importar como módulo built-in, pero es una **dependencia npm** que debe instalarse.

### La solución
```bash
cd zepp-sleep-detector
npm init -y
npm i @zeppos/zml
```

### Uso correcto
**app.js:**
```javascript
import { BaseApp } from '@zeppos/zml/base-app'
App(BaseApp({ globalData: {}, onCreate() {}, onDestroy() {} }))
```

**page/home/index.page.js:**
```javascript
import { BasePage } from '@zeppos/zml/base-page'
Page(BasePage({
  state: {},
  build() { /* UI */ },
  sendToSide() {
    this.request({ method: 'MY_ACTION', data: 'value' })
      .then((response) => console.log('Response:', response))
      .catch((error) => console.log('Error:', error))
  }
}))
```

**app-side/index.js:**
```javascript
import { BaseSideService } from '@zeppos/zml/base-side'
AppSideService(BaseSideService({
  onRequest(req, res) {
    if (req.method === 'MY_ACTION') {
      fetch({ url: 'http://localhost:50002/endpoint', method: 'POST', body: JSON.stringify(req) })
        .then(response => res(null, { success: true }))
        .catch(error => res(null, { success: false }))
    }
  }
}))
```

### Notas importantes
- ZML requiere API_LEVEL 3.0+ (Amazfit Active Max tiene API 4.2 ✅)
- `app.json` debe declarar `app-side` con `"path": "app-side/index"`
- NO necesitas `app-service` para comunicación básica
- `fetch` está disponible globalmente en el Side Service

---

## 🟡 Hallazgo #18: En handlers de widgets, captura la instancia de `BasePage`

### El problema
Dentro de callbacks de widgets como `click_func`, `this` puede no ser la instancia de `BasePage`. Llamar `this.request(...)` desde ahí puede fallar o no enviar eventos al Side Service.

### La solución
Capturar la instancia de página al inicio de `build()` y usar esa referencia dentro del callback:

```javascript
build: function () {
  var page = this

  hmUI.createWidget(hmUI.widget.BUTTON, {
    click_func: function () {
      page.request({ method: "SLEEP_DETECTED" })
    }
  })
}
```

---

## 🟡 Hallazgo #19: La UI debe leer el estado real del App Service

### El problema
Si la página solo usa una bandera en `localStorage` para pintar `ON/OFF`, puede mostrar `OFF` aunque el App Service siga activo. También es engañoso actualizar "Last check" con `Date.now()` desde la página, porque eso no confirma que el servicio haya consultado el sensor.

### La solución
Usar `appService.getAllAppServices()` para confirmar si el servicio está corriendo y mostrar el último chequeo escrito por el servicio:

```javascript
function running() {
  var services = appService.getAllAppServices()
  return services && services.indexOf("app-service/sleep_service") >= 0
}

var lastCheck = localStorage.getItem("last_sleep_check")
```

La página puede tener su propio timer para refrescar la UI, pero el timestamp mostrado debe venir del App Service.

---

## 🟡 Hallazgo #20: No mostrar "Service started" hasta confirmar resultado

### El problema
Si la página reintenta arrancar un App Service cuando el heartbeat está viejo, puede mostrar toasts repetidos de "Service started" aunque el servicio no haya escrito heartbeat ni `last_sleep_check`.

### La solución
Tratar `appService.start()` como intento de arranque, no como confirmación de vida:
- Mostrar "starting" durante el intento.
- Solo considerar vivo al servicio cuando actualiza heartbeat o aparece en `getAllAppServices()`.
- Limitar reintentos automáticos, por ejemplo cada 60 segundos.
- En reintentos automáticos, evitar toasts salvo error accionable.

---

## 🟡 Hallazgo #21: Servicio listado sin heartbeat requiere stop antes de restart

### El problema
Después de `appService.stop()` y un `start()` inmediato, `getAllAppServices()` puede seguir listando el servicio aunque el App Service no esté ejecutando `onInit()` ni escribiendo heartbeat. La UI puede quedar alternando entre `start requested` y `service listed`.

### La solución
Si el servicio aparece en `getAllAppServices()` pero el heartbeat está vencido, tratarlo como instancia stale:

```javascript
if (running() && !alive()) {
  appService.stop({
    url: SERVICE_FILE,
    complete_func: function () {
      appService.start({ url: SERVICE_FILE, reload: true })
    }
  })
}
```

No asumir que "listed" equivale a "running"; la señal confiable es que el servicio escriba heartbeat o `last_sleep_check`.

---

## 🟡 Hallazgo #22: `appService.start/stop` debe probar `file` y usar `url` solo como fallback

### El problema
La documentación oficial v3+ de `@zos/app-service` define el parámetro requerido como `file`, y debe coincidir con el servicio declarado en `app.json`. Usar solo `url` puede dejar la UI en `starting` o con un servicio listado pero sin heartbeat.

### La solución
Usar `file` primero y registrar el retorno/callback. Si devuelve error explícito o callback fallido, intentar `url` como fallback por compatibilidad con builds anteriores probados en este repo.

```javascript
appService.start({
  file: "app-service/sleep_service",
  reload: true,
  complete_func: function (info) {
    console.log(JSON.stringify(info))
  }
})
```

Mostrar en la UI el retorno de `start`, la lista de `getAllAppServices()`, `sleep_service_heartbeat` y `sleep_check_count` para distinguir entre:
- arranque rechazado;
- servicio listado pero congelado;
- servicio realmente ejecutando checks.

---

## 🟡 Hallazgo #23: El App Service debe escribir heartbeat antes de inicializar sensores

### El problema
Si el servicio queda en `stale listed` y `sleep_service_heartbeat` no cambia, el runtime listó el servicio pero el módulo no llegó a ejecutar checks. Un fallo temprano en constructores/imports o en inicialización de sensores puede parecer un servicio vivo desde `getAllAppServices()`.

### La solución
En `onInit()`, escribir primero `sleep_service_heartbeat`, `last_sleep_check` y `sleep_service_status = "onInit"`. Después crear/inicializar `Sleep` y `Time` dentro de bloques `try/catch`.

También conviene incrementar `sleep_check_count` antes de llamar a `Sleep.getSleepingStatus()` y persistir `last_sleep_status` cuando la lectura funcione. Así la UI distingue:
- `checks 0`: `onInit()` no se ejecutó o el módulo crasheó antes.
- `status onInit` sin checks: fallo al inicializar sensores/timer.
- `checks > 0` y `sleep 0/1`: servicio vivo y sensor respondiendo.

---

## 🟡 Hallazgo #24: Preferir `app-service/index` como entrypoint del servicio

### El problema
En pruebas con `services: ["app-service/sleep_service"]`, el servicio podía quedar listado por `getAllAppServices()` pero sin ejecutar `onInit()` ni actualizar heartbeat. Para descartar problemas de resolución de rutas del runtime, usar el entrypoint convencional `app-service/index`.

### La solución
Declarar el servicio y arrancarlo con la misma ruta:

```json
"app-service": {
  "services": ["app-service/index"]
}
```

```javascript
appService.start({ file: "app-service/index", reload: true })
```

Mantener la UI y `getAllAppServices()` apuntando exactamente a esa ruta.

---

## 🟡 Hallazgo #25: Aislar imports estáticos del App Service

### El problema
Si el servicio queda en `waiting heartbeat`, aparece listado pero nunca escribe `sleep_service_heartbeat`. Eso puede pasar si un import estático o código top-level falla antes de que `AppService({ onInit })` se registre.

### Diagnóstico
Probar primero un `app-service/index.js` mínimo que solo importe `@zos/storage` y escriba:
- `sleep_service_status = "service alive"`
- `sleep_check_count = "1"`
- `sleep_service_heartbeat = Date.now()`

Si ese servicio mínimo funciona, el bloqueo está en imports/APIs añadidas después, por ejemplo `@zos/sensor`. Si tampoco funciona, el problema está en permisos, declaración de servicio, runtime o instalación del paquete.

---

## 🟡 Hallazgo #26: Mostrar código de retorno de `appService.start`

### El problema
`getAllAppServices()` puede listar un servicio aunque no escriba heartbeat, y los fallbacks silenciosos (`url` después de `file`) confunden el diagnóstico.

### La solución
Durante diagnóstico, usar solo:

```javascript
appService.start({
  file: "app-service/index",
  reload: false,
  complete_func: function (info) {
    // info.result true/false
  }
})
```

Mostrar en pantalla `ret` y `cb`. Códigos oficiales:
- `0`: éxito
- `1`: parámetro inválido
- `2`: error de estado del servicio
- `3`: sin permiso
- `4`: sin memoria
- `5`: no soportado
- `6`: prohibido
- `7`: límite de servicios alcanzado
- `255`: desconocido

---

## 🟡 Hallazgo #27: `ret 2` requiere detener y esperar antes de iniciar

### El problema
`appService.start()` puede devolver `ret 2`, que la documentación oficial define como `Service Status Error`. En la práctica aparece cuando Zepp conserva un servicio listado/atascado y rechaza otro arranque inmediato.

### La solución
Antes de iniciar, ejecutar `appService.stop({ file })`, esperar unos segundos y recién después llamar a `start({ file })`.

```javascript
appService.stop({
  file: "app-service/index",
  complete_func: function () {
    createSysTimer(false, 3000, function () {
      appService.start({ file: "app-service/index", reload: false })
    })
  }
})
```

No reintentar `start()` inmediatamente tras `stop()`: puede seguir devolviendo `ret 2`.

---

## 🔴 Hallazgo #17: App Service requiere `requestPermission` dinámico (CRÍTICO)

### El problema
El App Service **NO se ejecuta** aunque:
- `"permissions": ["device:os.bg_service"]` esté en `app.json`
- `appService.start()` se llame desde la página
- El servicio esté declarado correctamente en `app.json` con `"services": [...]`

El síntoma es que el servicio parece arrancar pero **se detiene inmediatamente**. La página sigue funcionando, pero los callbacks del servicio nunca se ejecutan.

### La solución correcta
El permiso `device:os.bg_service` requiere **solicitud dinámica al usuario** antes de iniciar el servicio:

```javascript
import * as appService from "@zos/app-service"
import { queryPermission, requestPermission } from "@zos/app"

const SERVICE_FILE = "app-service/sleep_service"
const BG_PERMISSIONS = ["device:os.bg_service"]

function startServiceWithPermission(vm) {
  const [permResult] = queryPermission({ permissions: BG_PERMISSIONS })

  if (permResult === 2) {
    // Permiso ya concedido
    doStartService(vm)
  } else if (permResult === 0) {
    // Solicitar permiso al usuario (muestra diálogo)
    requestPermission({
      permissions: BG_PERMISSIONS,
      callback: (results) => {
        if (results[0] === 2) {
          doStartService(vm)
        } else {
          // Permiso denegado
        }
      }
    })
  }
}

function doStartService(vm) {
  const result = appService.start({
    url: SERVICE_FILE,  // ⚠️ Usa "url", NO "file"
    complete_func: (info) => {
      if (info.result) {
        // Servicio iniciado correctamente
      }
    }
  })
}
```

### Errores comunes que parecen funcionar pero fallan

| Error | Causa |
|-------|-------|
| `appService.start({ file: ... })` | El parámetro correcto es **`url`**, no `file` |
| No llamar `requestPermission` | El servicio se inicia pero se detiene inmediatamente |
| No verificar `queryPermission` primero | Puede causar errores innecesarios |
| Usar `start()` sin `complete_func` | No hay forma de saber si falló |

### Parámetros correctos de `appService.start()`
```javascript
appService.start({
  url: "app-service/sleep_service",  // ← CORRECTO (no "file")
  param: "optional=params",          // ← Opcional
  reload: true,                      // ← Persistir tras reinicio (API 4.0)
  complete_func: (info) => {         // ← OBLIGATORIO
    console.log(info.result)         // true/false
  }
})
```

### Verificar si el servicio está corriendo
```javascript
const services = appService.getAllAppServices()
// services es un array de strings con los paths de servicios activos
const isRunning = services.includes("app-service/sleep_service")
```

### APIs disponibles en App Service
| API | Disponible |
|-----|-----------|
| `@zos/sensor` (Sleep, Time, HeartRate) | ✅ |
| `@zos/storage` (localStorage) | ✅ |
| `@zos/notification` (notify) | ✅ |
| `@zos/app-service` (start, stop, exit) | ✅ |
| `@zos/app` (getPackageInfo, getProfile) | ✅ |
| `@zos/ble` (except mst*) | ✅ |
| `@zos/fs` (solo pantalla off/AOD) | ✅ |
| `@zos/ui` | ❌ No UI |
| `@zos/timer` (setTimeout, setInterval) | ❌ No timers |
| `@zos/sensor` (Accelerometer, Gyroscope) | ❌ Alta potencia |

### Verificación confirmada
- ✅ Amazfit Active Max (Zepp OS 5.0, API 4.2): `Sleep.getSleepingStatus()` funciona en App Service
- ✅ `Time.onPerMinute()` dispara callbacks cada minuto en background
- ✅ `localStorage` persiste entre instancias (App Service ↔ Page ↔ Side Service)
- ✅ El servicio sigue corriendo tras apagar la pantalla
- ✅ El servicio se reinicia automáticamente tras reinicio del sistema (con `reload: true`)

---

## 🔄 Mantenimiento de este documento

**Este documento DEBE ser actualizado** cuando:
- Se descubra un nuevo error o solución
- Zepp OS publique una nueva versión mayor
- La documentación oficial cambie significativamente

**Verificación periódica recomendada**:
- Cada 3 meses revisar https://docs.zepp.com/ para cambios
- Probar builds con el simulador antes de deployar al reloj
- Mantener sincronizado con la versión del repo de samples
