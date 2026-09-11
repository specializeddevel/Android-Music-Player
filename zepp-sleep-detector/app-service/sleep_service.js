import { Sleep, Time } from '@zos/sensor'
import { localStorage } from '@zos/storage'

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
  const entry = ts + ' ' + msg

  const existing = localStorage.getItem(LOG_KEY) || ''
  const lines = existing.split('\n').filter(function (l) { return l.length > 0 })
  lines.push(entry)
  if (lines.length > 30) lines.shift()
  localStorage.setItem(LOG_KEY, lines.join('\n'))
}

function notifyAndroid(sleepOnsetMinutes, timestamp) {
  const eventData = JSON.stringify({
    type: 'sleep_detected',
    sleepOnsetMinutes: sleepOnsetMinutes,
    timestamp: timestamp
  })
  localStorage.setItem(STORAGE_KEY, eventData)
  logEvent('EVENT写入localStorage onset=' + sleepOnsetMinutes)
}

function checkSleep() {
  checkCount++
  localStorage.setItem(COUNT_KEY, String(checkCount))
  heartbeat('checking')

  try {
    if (!sleepSensor) {
      localStorage.setItem(STATUS_KEY, 'no sleep sensor')
      logEvent('ERROR: sleep sensor missing')
      return
    }

    const status = sleepSensor.getSleepingStatus()
    const now = Date.now()
    localStorage.setItem(STATUS_KEY, 'running')
    localStorage.setItem(HEARTBEAT_KEY, String(now))
    localStorage.setItem(LAST_CHECK_KEY, String(now))
    localStorage.setItem(LAST_SLEEP_STATUS_KEY, String(status))

    let timeStr = new Date(now).getHours() + ':' + new Date(now).getMinutes() + ':' + new Date(now).getSeconds()
    if (timeSensor) {
      try {
        timeStr = timeSensor.getHours() + ':' + timeSensor.getMinutes() + ':' + timeSensor.getSeconds()
      } catch (e) {}
    }
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
    heartbeat('onInit')
    logEvent('=== SERVICE START ===')

    try {
      sleepSensor = new Sleep()
      sleepSensor.initialize()
      logEvent('Sleep sensor initialized')
    } catch (e) {
      logEvent('Sleep init ERROR: ' + String(e))
    }

    try {
      timeSensor = new Time()
      timeSensor.initialize()
      logEvent('Time sensor initialized')
    } catch (e) {
      logEvent('Time init ERROR: ' + String(e))
    }

    if (timeSensor) {
      try {
        timeSensor.onPerMinute(function () {
          logEvent('onPerMinute fired')
          checkSleep()
        })
      } catch (e) {
        logEvent('Time onPerMinute ERROR: ' + String(e))
        localStorage.setItem(STATUS_KEY, 'timer error: ' + String(e))
      }
    }

    checkSleep()
    logEvent('First check done, onPerMinute registered')
  },

  onDestroy() {
    logEvent('=== SERVICE DESTROYED ===')
    localStorage.setItem(STATUS_KEY, 'stopped')
    try { if (sleepSensor) sleepSensor.stop() } catch (e) {}
    try { if (timeSensor) timeSensor.stop() } catch (e) {}
  }
})
