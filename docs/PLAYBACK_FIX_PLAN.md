# Plan de corrección de persistencia y reproducción

## Objetivo

Corregir estos problemas sin perder las preferencias o el avance existente:

- El avance, la velocidad y el tono no siempre se conservan, especialmente en libros nuevos.
- Algunos MP3 tardan demasiado en iniciar o reanudar la reproducción.
- Al arrastrar la barra de progreso, algunos archivos dejan de responder.
- El botón puede quedar mostrando pausa aunque no haya reproducción efectiva.
- Después de ciertos saltos, la reproducción puede regresar al inicio.

Implementar las etapas en orden. Mantener cada cambio acotado y ejecutar sus pruebas antes de continuar.

## Hallazgos confirmados en el código

1. `PlaybackViewModel.setPlaybackSpeed()` y `setPitch()` guardan el valor solamente cuando ya existe un `AudiobookProgress`. Si el libro es nuevo, no crean el registro.
2. Esos métodos toman el identificador desde `currentMusicDetails`, que se carga de forma asíncrona. Al cambiar rápido de libro puede ser `null` o conservar temporalmente los detalles del libro anterior.
3. `PlaybackService` y `PlaybackViewModel` restauran velocidad y tono por caminos separados. Las lecturas asíncronas no comprueban siempre que el libro siga siendo el elemento activo al completarse.
4. El registro completo se reemplaza en Room después de una lectura previa. Dos escrituras concurrentes pueden partir de versiones diferentes y pisarse entre sí.
5. El servicio guarda periódicamente cada 10 segundos y al pausar o cerrar, pero el cambio de elemento no captura explícitamente el estado completo del libro saliente antes de restaurar el siguiente.
6. El `Slider` llama a `seekTo()` en cada actualización mientras el dedo se mueve. Esto puede generar muchos saltos consecutivos en archivos costosos de indexar.
7. La interfaz usa `playWhenReady` para decidir si muestra reproducción o pausa. Ese valor puede seguir activo durante `STATE_BUFFERING` o después de una recuperación, aunque `isPlaying` sea falso.
8. El extractor MP3 tiene habilitadas la indexación y la estimación por bitrate constante. La indexación puede requerir leer una parte grande de ciertos MP3 antes de completar un salto preciso.

## Etapa 1: pruebas de regresión y diagnóstico

Antes de modificar el comportamiento, crear pruebas que fallen con la implementación actual.

Casos prioritarios:

1. Libro nuevo sin registro: cambiar velocidad y tono, salir antes del guardado periódico y comprobar que ambos valores se conservan.
2. Libro nuevo con avance: reproducir o buscar una posición y cambiar inmediatamente a otro libro. Al regresar debe recuperarse la posición.
3. Cambio A → B: B no tiene registro y debe comenzar con velocidad y tono `1.0`, sin heredar los valores de A.
4. Cambio rápido A → B → C: completar deliberadamente fuera de orden las lecturas de A y B. Ninguna debe modificar el controlador o la interfaz de C.
5. Detalles atrasados: cambiar la velocidad de B antes de que termine de cargar `currentMusicDetails`. La escritura debe pertenecer a B.
6. Escrituras simultáneas de avance, velocidad y tono: el registro final debe conservar los valores más recientes y no perder EQ, `isRead` ni las marcas de tiempo.
7. Buscar una posición mientras está pausado y salir: la posición debe persistir aunque no ocurra otro ciclo de reproducción.

Extraer, si es necesario, un coordinador pequeño de persistencia que reciba el repositorio, un dispatcher y un reloj inyectables. Probarlo con `runTest`, `StandardTestDispatcher` y barreras `CompletableDeferred`; evitar esperas reales.

Extender `PlaybackViewModelTest` para verificar el identificador utilizado y la delegación. Si se agregan consultas parciales o transacciones SQL, crear un `ProgressDaoTest` instrumentado con una base Room en memoria.

Agregar temporalmente registros de diagnóstico para las pruebas manuales:

- `mediaId` activo y el asociado a cada lectura o escritura.
- Posición solicitada, posición confirmada y duración.
- Velocidad y tono.
- `playWhenReady`, `isPlaying`, estado de reproducción y motivo de transición.
- Tiempo entre `prepare()`, `STATE_BUFFERING`, `STATE_READY` e inicio real.
- Tipo de archivo, URI y errores de Media3.

No registrar rutas personales completas en versiones de producción.

## Etapa 2: persistencia correcta desde el primer uso

Crear una única operación de actualización en `ProgressRepository` capaz de insertar un registro mínimo cuando no exista y actualizar únicamente los campos proporcionados.

Requisitos:

- Velocidad y tono deben usar `controller.currentMediaItem?.mediaId` como fuente del identificador.
- Un libro nuevo debe obtener un registro al primer cambio de avance, velocidad, tono, EQ o estado de lectura.
- Las actualizaciones deben ser atómicas mediante una transacción o consultas parciales del DAO.
- Una actualización de velocidad no debe sobrescribir posición, tono, EQ o `isRead`.
- Una actualización de avance no debe sobrescribir velocidad o tono.
- `lastUpdated` debe reflejar la última modificación válida.
- Los valores deben validarse antes de persistirlos.

Evitar el patrón `getProgress()` → `copy()` → `saveProgress()` desde varias corrutinas independientes, porque permite escrituras perdidas.

## Etapa 3: una sola coordinación de guardado y restauración

Centralizar la autoridad de persistencia de reproducción en el servicio o en un coordinador compartido por el servicio. El ViewModel debe enviar acciones y reflejar eventos, sin ejecutar una restauración paralela que compita con el servicio.

Al cambiar de libro:

1. Capturar de forma síncrona en memoria el `mediaId`, posición, duración, velocidad y tono del libro saliente.
2. Encolar su escritura antes de restaurar el nuevo elemento.
3. Cargar una sola vez el registro del libro entrante.
4. Aplicar valores predeterminados cuando no exista registro.
5. Antes de aplicar el resultado, comprobar que su `mediaId` sigue siendo el elemento actual.
6. No aplicar una restauración antigua después de una modificación manual más reciente.

Serializar las operaciones por libro o usar una generación de selección para descartar resultados atrasados. Mantener los guardados de pausa, intervalo, retirada de recientes y cierre.

Guardar también después de un salto confirmado cuando el reproductor esté pausado, y antes de reemplazar o retirar el elemento actual de la cola.

## Etapa 4: interacción segura con la barra de progreso

Cambiar el `Slider` para usar una posición provisional durante el arrastre:

- `onValueChange`: actualizar solamente el valor visual local.
- `onValueChangeFinished`: enviar un único `seekTo()` con el destino final.
- Mientras se arrastra, no reemplazar el valor provisional con actualizaciones periódicas del reproductor.
- Deshabilitar o limitar el control cuando la duración no sea válida o el elemento no sea desplazable.
- Mostrar un indicador de carga cuando el salto deja al reproductor en `STATE_BUFFERING`.
- Persistir el destino únicamente después de confirmarlo o conservar la última posición válida si el salto falla.

Revisar la interacción con el historial, “Deshacer” y el rebobinado inteligente. Un salto manual no debe interpretarse como una nueva reanudación ni provocar dos saltos superpuestos.

## Etapa 5: estados y recuperación del reproductor

Modelar en la interfaz al menos estos estados: reproduciendo, pausado, cargando y error.

- Mostrar pausa solamente cuando exista intención de reproducir; acompañarla con carga cuando esté en `STATE_BUFFERING`.
- Permitir que el botón cancele la intención de reproducir mientras está cargando.
- Si Media3 entra en `STATE_IDLE` por un error, conservar el último elemento y posición válidos.
- El reintento debe ejecutar una secuencia controlada de `prepare()` y `seekTo()` y tener un límite de intentos.
- No reiniciar silenciosamente en cero. Si no puede restaurarse, mostrar un error recuperable.
- Cancelar trabajos anteriores de recuperación, restauración o pitch cuando cambie el elemento.

## Etapa 6: diagnóstico y ajuste específico de MP3

Usar al menos un MP3 rápido y uno problemático. Registrar:

- Bitrate constante o variable.
- Presencia de encabezados Xing/VBRI y duración informada.
- Tamaño, almacenamiento local o URI SAF.
- Tiempo de inicio desde cero, reanudación y búsqueda cercana o lejana.
- Estado y error de Media3 durante cada operación.

Comparar configuraciones del extractor de forma aislada:

1. Preferir el índice incluido en metadatos cuando sea válido.
2. Usar estimación por bitrate constante cuando corresponda.
3. Usar indexación completa únicamente como respaldo para archivos sin tabla de búsqueda fiable.

No cambiar simultáneamente el extractor, los buffers y la recuperación, porque impediría identificar qué ajuste resuelve el problema. Considerar actualizar Media3 solo después de comprobar las notas de versión y reproducir el archivo afectado con la versión candidata.

## Etapa 7: validación final

Ejecutar en el orden requerido por el repositorio:

```powershell
.\gradlew assembleDebug
.\gradlew lint
.\gradlew test
```

Validación manual en dispositivo:

1. Libro A con avance y parámetros guardados.
2. Libro B nunca reproducido: fijar velocidad y tono, buscar una posición mientras está pausado y salir antes de 10 segundos.
3. Reabrir B y comprobar los tres valores.
4. Cambiar rápidamente A → B → A y verificar que ningún valor se cruza.
5. Arrastrar lentamente y rápidamente la barra en MP3 de bitrate constante y variable.
6. Probar saltos cercanos y lejanos durante reproducción y pausa.
7. Cerrar desde recientes y volver a abrir.
8. Repetir con Bluetooth y altavoz si el bloqueo estaba asociado a la reconstrucción de audio por cambio de tono.
9. Verificar que el rebobinado inteligente explica únicamente el retroceso configurado y no un retorno al inicio.

## Criterios de aceptación

- Un libro nuevo conserva avance, velocidad y tono aunque se abandone inmediatamente.
- Cada libro mantiene valores independientes después de cambios rápidos.
- Ninguna lectura o escritura atrasada modifica otro libro.
- Arrastrar la barra produce un solo salto al soltar.
- La interfaz distingue pausa, carga y error, y el botón siempre responde.
- Un salto fallido no guarda cero ni reinicia silenciosamente el libro.
- Los MP3 problemáticos comienzan y buscan dentro de tiempos medidos y aceptables, sin sacrificar precisión de forma inadvertida.
- Los datos existentes sobreviven sin migración destructiva.
- Compilación, lint y pruebas pasan.

## División recomendada para un modelo de menor costo

Entregar una etapa por solicitud. Pedir que revise este documento y los cambios existentes antes de editar. Después de cada etapa debe:

1. Mostrar los archivos modificados.
2. Explicar qué caso de regresión quedó cubierto.
3. Ejecutar las pruebas específicas de la etapa.
4. Detenerse si una decisión cambia el esquema de base de datos o la estrategia del extractor.

Orden sugerido: pruebas y persistencia → coordinación → barra y estados → diagnóstico MP3 → validación completa.
