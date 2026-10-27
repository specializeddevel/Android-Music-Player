import { Sleep, Time } from '@zos/sensor'
import { localStorage } from '@zos/storage'
import { peerSocket } from '@zos/messaging'
import { encodeSleepMessage, decodeSleepMessage } from '../shared/sleep-message'

const STORAGE_KEY = 'sleep_event'
const STATUS_KEY = 'sleep_service_status'
const COUNT_KEY = 'sleep_check_count'
const LOG_KEY = 'sleep_service_log'
const HEARTBEAT_KEY = 'sleep_service_heartbeat'
const LAST_CHECK_KEY = 'last_sleep_check'
const LAST_SLEEP_STATUS_KEY = 'last_sleep_status'

let sleepSensor = null
let timeSensor = null
let checkCount = 0
let lastStatus = -1
let wasSleeping = false

function heartbeat(status) {
  const now = Date.now()
  localStorage.setItem(HEARTBEAT_KEY, String(now))
  localStorage.setItem(LAST_CHECK_KEY, String(now))
  if (status) localStorage.setItem(STATUS_KEY, status)
}

function logEvent(msg) {
  const now = new Date()
  const ts = now.getHours().toString().padStart(2, '0') + ':' +
             now.getMinutes().toString().padStart(2, '0') + ':' +
             now.getSeconds().toString().padStart(2, '0')
  const existing = localStorage.getItem(LOG_KEY) || ''
  const lines = existing.split('\n').filter(function (l) { return l.length > 0 })
  lines.push(ts + ' ' + msg)
  if (lines.length > 30) lines.shift()
  localStorage.setItem(LOG_KEY, lines.join('\n'))
}

function sendPendingEvent() {
  const eventData = localStorage.getItem(STORAGE_KEY)
  if (!eventData) return
  try {
    peerSocket.send(encodeSleepMessage(JSON.parse(eventData)))
    logEvent('sleep event sent to side service')
  } catch (error) {
    logEvent('send pending ERROR: ' + String(error))
  }
}

function notifyAndroid(sleepOnsetMinutes, timestamp) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify({
    type: 'sleep_detected',
    sleepOnsetMinutes: sleepOnsetMinutes,
    timestamp: timestamp
  }))
  sendPendingEvent()
}

function onPeerMessage(payload) {
  const message = decodeSleepMessage(payload)
  if (!message || message.type !== 'sleep_ack') return
  const pending = localStorage.getItem(STORAGE_KEY)
  if (!pending) return
  try {
    if (JSON.parse(pending).timestamp === message.timestamp) {
      localStorage.removeItem(STORAGE_KEY)
      logEvent('sleep event acknowledged')
    }
  } catch (error) {}
}

function checkSleep() {
  checkCount++
  localStorage.setItem(COUNT_KEY, String(checkCount))
  heartbeat('checking')

  try {
    if (!sleepSensor) {
      localStorage.setItem(STATUS_KEY, 'no sleep sensor')
      return
    }

    const status = sleepSensor.getSleepingStatus()
    const now = Date.now()
    localStorage.setItem(STATUS_KEY, 'running')
    localStorage.setItem(LAST_SLEEP_STATUS_KEY, String(status))
    heartbeat('running')
    logEvent('#' + checkCount + ' sleep=' + status + ' prev=' + lastStatus)

    if (status === 1 && !wasSleeping) {
      wasSleeping = true
      const date = new Date(now)
      notifyAndroid(date.getHours() * 60 + date.getMinutes(), now)
    } else if (status === 0 && wasSleeping) {
      wasSleeping = false
      logEvent('SLEEP END')
    }

    lastStatus = status
    sendPendingEvent()
  } catch (error) {
    logEvent('check ERROR: ' + String(error))
    localStorage.setItem(STATUS_KEY, 'error: ' + String(error))
  }
}

AppService({
  onInit() {
    heartbeat('onInit')
    localStorage.setItem(COUNT_KEY, '0')
    logEvent('=== SERVICE START index ===')

    try { peerSocket.addListener('message', onPeerMessage) } catch (error) {
      logEvent('peer listener ERROR: ' + String(error))
    }
    try {
      sleepSensor = new Sleep()
      sleepSensor.initialize()
    } catch (error) {
      logEvent('Sleep init ERROR: ' + String(error))
    }
    try {
      timeSensor = new Time()
      timeSensor.initialize()
      timeSensor.onPerMinute(checkSleep)
    } catch (error) {
      logEvent('Time init ERROR: ' + String(error))
    }
    checkSleep()
  },

  onDestroy() {
    logEvent('=== SERVICE DESTROYED index ===')
    localStorage.setItem(STATUS_KEY, 'stopped')
    try { peerSocket.removeListener('message', onPeerMessage) } catch (error) {}
    try { if (sleepSensor) sleepSensor.stop() } catch (error) {}
    try { if (timeSensor) timeSensor.stop() } catch (error) {}
  }
})
