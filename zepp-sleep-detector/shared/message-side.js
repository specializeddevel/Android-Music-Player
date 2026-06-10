/*
  MessageBuilder for Side Service
  Simplified version based on Zepp OS samples
  Handles BLE communication between Device App and Side Service
*/

import { peerSocket } from '@zos/messaging'

export class MessageBuilder {
  constructor() {
    this.listeners = {}
  }

  listen(callback) {
    peerSocket.addListener('message', (payload) => {
      const message = this.buf2Json(payload)
      callback && callback(message)
    })
  }

  on(event, callback) {
    if (!this.listeners[event]) {
      this.listeners[event] = []
    }
    this.listeners[event].push(callback)
    
    peerSocket.addListener('message', (payload) => {
      const message = this.buf2Json(payload)
      if (message.type === event || message.event === event) {
        callback(message)
      }
    })
  }

  request(payload) {
    const buf = this.json2Buf(payload)
    peerSocket.send(buf)
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
