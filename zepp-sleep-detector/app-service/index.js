import { localStorage } from '@zos/storage'

const STATUS_KEY = 'sleep_service_status'
const COUNT_KEY = 'sleep_check_count'
const LOG_KEY = 'sleep_service_log'
const HEARTBEAT_KEY = 'sleep_service_heartbeat'
const LAST_CHECK_KEY = 'last_sleep_check'
const LAST_SLEEP_STATUS_KEY = 'last_sleep_status'

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

AppService({
  onInit() {
    localStorage.setItem(COUNT_KEY, '1')
    localStorage.setItem(LAST_SLEEP_STATUS_KEY, 'service-only')
    heartbeat('service alive')
    logEvent('=== MINIMAL SERVICE START index ===')
  },

  onDestroy() {
    logEvent('=== MINIMAL SERVICE DESTROYED index ===')
    localStorage.setItem(STATUS_KEY, 'stopped')
  }
})
