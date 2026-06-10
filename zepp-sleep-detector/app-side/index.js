import { BaseSideService } from '@zeppos/zml/base-side'

AppSideService(
  BaseSideService({
    onInit() {
      console.log('Side Service initialized')
    },

    onRequest(req, res) {
      console.log('Request from device:', req.method)

      if (req.method === 'SLEEP_DETECTED') {
        const { sleepOnsetMinutes, timestamp } = req
        console.log('Sleep detected! Onset:', sleepOnsetMinutes)

        fetch({
          url: 'http://localhost:50002/sleep',
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            sleepOnsetMinutes: sleepOnsetMinutes,
            timestamp: timestamp
          })
        }).then(response => {
          console.log('Android app responded:', response.status)
          res(null, { success: true })
        }).catch(error => {
          console.error('Failed to notify Android app:', error)
          res(null, { success: false, error: String(error) })
        })
      }
    },

    onRun() {},
    onDestroy() {},
  })
)
