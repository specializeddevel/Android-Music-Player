import { BaseSideService } from '@zeppos/zml/base-side'

const SLEEP_EVENT_KEY = 'sleep_event'
const POLL_INTERVAL = 5000

let lastEventTimestamp = 0
let pollCount = 0

function sendToAndroid(event) {
  console.log('Sending to Android:', JSON.stringify(event))
  fetch({
    url: 'http://localhost:50002/sleep',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sleepOnsetMinutes: event.sleepOnsetMinutes,
      timestamp: event.timestamp
    })
  }).then(function (response) {
    console.log('Android responded:', response.status)
  }).catch(function (error) {
    console.error('Failed to notify Android:', error)
  })
}

AppSideService(
  BaseSideService({
    onInit() {
      console.log('Side Service initialized')
      var self = this

      self.pollTimer = setInterval(function () {
        pollCount++
        try {
          var eventData = localStorage.getItem(SLEEP_EVENT_KEY)
          if (eventData) {
            var event = JSON.parse(eventData)
            if (event.timestamp !== lastEventTimestamp) {
              lastEventTimestamp = event.timestamp
              console.log('Sleep event found:', eventData)
              sendToAndroid(event)
            }
          }
        } catch (e) {
          console.error('Poll error:', e)
        }
      }, POLL_INTERVAL)
    },

    onRequest(req, res) {
      console.log('Request from device:', req.method)

      if (req.method === 'SLEEP_DETECTED') {
        var sleepOnsetMinutes = req.sleepOnsetMinutes
        var timestamp = req.timestamp
        console.log('Sleep detected! Onset:', sleepOnsetMinutes)

        sendToAndroid({
          sleepOnsetMinutes: sleepOnsetMinutes,
          timestamp: timestamp
        })
        res(null, { success: true })
      }
    },

    onRun() {},
    onDestroy() {
      console.log('Side Service destroying, clearing timer')
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
      }
    },
  })
)