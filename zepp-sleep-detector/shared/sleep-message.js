const PREFIX = 'LITERA_SLEEP_V1:'

export function encodeSleepMessage(message) {
  const value = PREFIX + JSON.stringify(message)
  const bytes = new Uint8Array(value.length)
  for (let i = 0; i < value.length; i++) bytes[i] = value.charCodeAt(i) & 0xff
  return bytes.buffer
}

export function decodeSleepMessage(payload) {
  const bytes = payload instanceof Uint8Array ? payload : new Uint8Array(payload)
  if (bytes.length < PREFIX.length) return null

  let value = ''
  for (let i = 0; i < bytes.length; i++) value += String.fromCharCode(bytes[i])
  if (value.indexOf(PREFIX) !== 0) return null

  try {
    return JSON.parse(value.substring(PREFIX.length))
  } catch (error) {
    return null
  }
}
