/*
  MessageBuilder for Device App
  Simplified version based on Zepp OS samples
  Handles BLE communication between Device App and Side Service
*/

import * as ble from '@zos/ble'

let _appId = 0
let _appDevicePort = 20
let _appSidePort = 0
let _ble = null

export class MessageBuilder {
  constructor(options = {}) {
    _appId = options.appId || 0
    _appDevicePort = options.appDevicePort || 20
    _appSidePort = options.appSidePort || 0
    _ble = options.ble || ble
  }

  connect() {
    if (!_ble) return
    _ble.createConnect((index, data, size) => {
      // Connection established
      console.log('BLE connected')
    })
  }

  disConnect() {
    if (!_ble) return
    _ble.disConnect()
  }

  request(payload) {
    return new Promise((resolve, reject) => {
      if (!_ble) {
        reject(new Error('BLE not available'))
        return
      }
      
      const buf = this.json2Buf(payload)
      _ble.send(buf, buf.byteLength)
      
      // Set up one-time listener for response
      const handler = (index, data, size) => {
        try {
          const response = this.buf2Json(data)
          resolve(response)
        } catch (e) {
          reject(e)
        }
      }
      
      _ble.createConnect(handler)
    })
  }

  json2Buf(json) {
    const str = JSON.stringify(json)
    const buffer = new ArrayBuffer(str.length * 2)
    const view = new Uint16Array(buffer)
    for (let i = 0; i < str.length; i++) {
      view[i] = str.charCodeAt(i)
    }
    return buffer
  }

  buf2Json(buf) {
    const view = new Uint16Array(buf)
    let str = ''
    for (let i = 0; i < view.length; i++) {
      str += String.fromCharCode(view[i])
    }
    return JSON.parse(str)
  }
}
