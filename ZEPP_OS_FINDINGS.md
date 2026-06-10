# Zepp OS Mini Program - Hallazgos Críticos y Guía de Referencia

> ⚠️ **FUENTE DE CONSULTA OBLIGATORIA** para cualquier desarrollo de Zepp OS Mini Program
> 
> Este documento contiene hallazgos críticos basados en pruebas reales con **Amazfit Active Max** (Zepp OS 5.0, API Level 4.2) y comparación con la documentación oficial.
> 
> **Última actualización**: 2026-06-09
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

## 🔄 Mantenimiento de este documento

**Este documento DEBE ser actualizado** cuando:
- Se descubra un nuevo error o solución
- Zepp OS publique una nueva versión mayor
- La documentación oficial cambie significativamente

**Verificación periódica recomendada**:
- Cada 3 meses revisar https://docs.zepp.com/ para cambios
- Probar builds con el simulador antes de deployar al reloj
- Mantener sincronizado con la versión del repo de samples
