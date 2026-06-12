import { Sleep, Time } from '@zos/sensor'
import { localStorage } from '@zos/storage'
import * as notification from '@zos/notification'

const STORAGE_KEY = 'sleep_event'
const STATUS_KEY = 'sleep_service_status'
const COUNT_KEY = 'sleep_check_count'
const LOG_KEY = 'sleep_service_log'
const LAST_WAKE_KEY = 'sleep_last_wake_event'

const sleepSensor = new Sleep()
const timeSensor = new Time()

let checkCount = 0
let lastStatus = -1
let wasSleeping = false

function logEvent(msg) {
  const now = new Date()
  const ts = now.getHours().toString().padStart(2, '0') + ':' +
             now.getMinutes().toString().padStart(2, '0') + ':' +
             now.getSeconds().toString().padStart(2, '0')
  const entry = ts + ' ' + msg

  const existing = localStorage.getItem(LOG_KEY) || ''
  const lines = existing.split('\n').filter(function (l) { return l.length > 0 })
  lines.push(entry)
  if (lines.length > 30) lines.shift()
  localStorage.setItem(LOG_KEY, lines.join('\n'))
}

function notifyAndroid(sleepOnsetMinutes, timestamp) {
  notification.notify({
    title: 'Sleep Detected',
    content: 'Notifying Android app...',
    actions: []
  })

  const eventData = JSON.stringify({
    type: 'sleep_detected',
    sleepOnsetMinutes: sleepOnsetMinutes,
    timestamp: timestamp
  })
  localStorage.setItem(STORAGE_KEY, eventData)
  logEvent('EVENT写入localStorage onset=' + sleepOnsetMinutes)
}

function checkSleep() {
  try {
    const status = sleepSensor.getSleepingStatus()
    checkCount++

    localStorage.setItem(COUNT_KEY, String(checkCount))
    localStorage.setItem(STATUS_KEY, 'running')

    const now = Date.now()
    localStorage.setItem('last_sleep_check', now)

    const timeStr = timeSensor.getHours() + ':' + timeSensor.getMinutes() + ':' + timeSensor.getSeconds()
    logEvent('#' + checkCount + ' time=' + timeStr + ' sleep=' + status + ' prev=' + lastStatus)

    if (status === 1) {
      if (!wasSleeping) {
        wasSleeping = true
        const d = new Date(now)
        const sleepOnsetMinutes = d.getHours() * 60 + d.getMinutes()
        logEvent('SLEEP START onset=' + sleepOnsetMinutes)
        notifyAndroid(sleepOnsetMinutes, now)
      }
    } else if (status === 0) {
      if (wasSleeping) {
        wasSleeping = false
        logEvent('SLEEP END')
        localStorage.setItem(LAST_WAKE_KEY, now)
      }
    }

    lastStatus = status
  } catch (e) {
    logEvent('ERROR: ' + String(e))
    localStorage.setItem(STATUS_KEY, 'error: ' + String(e))
  }
}

AppService({
  onInit() {
    logEvent('=== SERVICE START ===')
    localStorage.setItem(STATUS_KEY, 'starting')

    try {
      sleepSensor.initialize()
      logEvent('Sleep sensor initialized')
    } catch (e) {
      logEvent('Sleep init ERROR: ' + String(e))
    }

    try {
      timeSensor.initialize()
      logEvent('Time sensor initialized')
    } catch (e) {
      logEvent('Time init ERROR: ' + String(e))
    }

    timeSensor.onPerMinute(function () {
      logEvent('onPerMinute fired')
      checkSleep()
    })

    checkSleep()
    logEvent('First check done, onPerMinute registered')
  },

  onDestroy() {
    logEvent('=== SERVICE DESTROYED ===')
    localStorage.setItem(STATUS_KEY, 'stopped')
    try { sleepSensor.stop() } catch (e) {}
    try { timeSensor.stop() } catch (e) {}
  }
})