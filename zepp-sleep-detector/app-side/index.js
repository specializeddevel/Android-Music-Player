import { BaseSideService } from '@zeppos/zml/base-side'
import { peerSocket } from '@zos/messaging'
import { encodeSleepMessage, decodeSleepMessage } from '../shared/sleep-message'

let lastEventTimestamp = 0

function sendToAndroid(event) {
  console.log('Sending to Android:', JSON.stringify(event))
  return fetch({
    url: 'http://localhost:50002/sleep',
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      sleepOnsetMinutes: event.sleepOnsetMinutes,
      timestamp: event.timestamp
    })
  }).then(function (response) {
    console.log('Android responded:', response.status)
    if (response.status < 200 || response.status >= 300) {
      throw new Error('Android HTTP status ' + response.status)
    }
    return response
  }).catch(function (error) {
    console.error('Failed to notify Android:', error)
    throw error
  })
}

AppSideService(
  BaseSideService({
    onInit() {
      console.log('Side Service initialized')
      this.sleepMessageListener = function (payload) {
        var event = decodeSleepMessage(payload)
        if (!event || event.type !== 'sleep_detected') return

        if (event.timestamp === lastEventTimestamp) return
        sendToAndroid(event).then(function () {
          lastEventTimestamp = event.timestamp
          peerSocket.send(encodeSleepMessage({ type: 'sleep_ack', timestamp: event.timestamp }))
        }).catch(function (error) {
          console.error('Sleep event remains pending:', error)
        })
      }
      peerSocket.addListener('message', this.sleepMessageListener)
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
      console.log('Side Service destroying')
      if (this.sleepMessageListener) {
        peerSocket.removeListener('message', this.sleepMessageListener)
      }
    },
  })
)
