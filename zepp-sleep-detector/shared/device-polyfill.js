/*
  Device polyfill for shared modules
  Required by /shared/message.js
*/

// Polyfill para entorno Zepp OS Device App
if (typeof globalThis === 'undefined') {
  var globalThis = this;
}

// Asegurar que console existe
if (typeof console === 'undefined') {
  globalThis.console = {
    log: function(...args) { hmFS.SysProSetChars('console.log', args.join(' ')) },
    error: function(...args) { hmFS.SysProSetChars('console.error', args.join(' ')) },
    warn: function(...args) { hmFS.SysProSetChars('console.warn', args.join(' ')) }
  };
}
