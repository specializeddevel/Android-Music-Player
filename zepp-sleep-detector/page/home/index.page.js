import * as hmUI from "@zos/ui"
import { log as Logger } from "@zos/utils"
import { localStorage } from "@zos/storage"
import { createSysTimer, stopTimer } from "@zos/timer"
import { BasePage } from "@zeppos/zml/base-page"

const logger = Logger.getLogger("sleep-detector")
const STORAGE_KEY = "sleep_detection_enabled"

function formatTime(ts) {
  if (!ts) return "--:--:--"
  const d = new Date(ts)
  const h = String(d.getHours()).padStart(2, "0")
  const m = String(d.getMinutes()).padStart(2, "0")
  const s = String(d.getSeconds()).padStart(2, "0")
  return h + ":" + m + ":" + s
}

Page(
  BasePage({
    state: {},

    onInit() {
      logger.log("onInit")
      this.enabled = localStorage.getItem(STORAGE_KEY) === "true"
    },

    build() {
      logger.log("build")

      hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: 0, y: 0, w: 480, h: 480, color: 0x000000
      })

      hmUI.createWidget(hmUI.widget.TEXT, {
        x: 0, y: 40, w: 480, h: 50,
        color: 0xFFFFFF, text_size: 32,
        align_h: hmUI.align.CENTER_H,
        text: "Sleep Detector"
      })

      this.statusWidget = hmUI.createWidget(hmUI.widget.TEXT, {
        x: 0, y: 110, w: 480, h: 60,
        color: this.enabled ? 0x00FF00 : 0xFF3333,
        text_size: 48,
        align_h: hmUI.align.CENTER_H,
        text: this.enabled ? "ON" : "OFF"
      })

      hmUI.createWidget(hmUI.widget.TEXT, {
        x: 0, y: 190, w: 480, h: 30,
        color: 0x888888, text_size: 20,
        align_h: hmUI.align.CENTER_H,
        text: "Last check"
      })

      this.lastCheckWidget = hmUI.createWidget(hmUI.widget.TEXT, {
        x: 0, y: 220, w: 480, h: 40,
        color: 0xCCCCCC, text_size: 28,
        align_h: hmUI.align.CENTER_H,
        text: formatTime(localStorage.getItem("last_sleep_check"))
      })

      this.btnBg = hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: 120, y: 290, w: 240, h: 55,
        color: this.enabled ? 0xCC3333 : 0x33AA33
      })

      this.btnText = hmUI.createWidget(hmUI.widget.TEXT, {
        x: 120, y: 290, w: 240, h: 55,
        color: 0xFFFFFF, text_size: 24,
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.CENTER_V,
        text: this.enabled ? "DISABLE" : "ENABLE"
      })

      hmUI.createWidget(hmUI.widget.BUTTON, {
        x: 120, y: 290, w: 240, h: 55,
        text: "",
        normal_src: "btn_transparent.png",
        press_src: "btn_transparent.png",
        click_func: () => {
          this.enabled = !this.enabled
          localStorage.setItem(STORAGE_KEY, this.enabled ? "true" : "false")
          this.statusWidget.setProperty(hmUI.prop.TEXT, this.enabled ? "ON" : "OFF")
          this.statusWidget.setProperty(hmUI.prop.COLOR, this.enabled ? 0x00FF00 : 0xFF3333)
          this.btnBg.setProperty(hmUI.prop.COLOR, this.enabled ? 0xCC3333 : 0x33AA33)
          this.btnText.setProperty(hmUI.prop.TEXT, this.enabled ? "DISABLE" : "ENABLE")
          logger.log("Toggled:", this.enabled ? "ON" : "OFF")
        }
      })

      this.testBtnBg = hmUI.createWidget(hmUI.widget.FILL_RECT, {
        x: 120, y: 370, w: 240, h: 55,
        color: 0x3366CC
      })

      hmUI.createWidget(hmUI.widget.TEXT, {
        x: 120, y: 370, w: 240, h: 55,
        color: 0xFFFFFF, text_size: 22,
        align_h: hmUI.align.CENTER_H,
        align_v: hmUI.align.CENTER_V,
        text: "TEST SLEEP"
      })

      hmUI.createWidget(hmUI.widget.BUTTON, {
        x: 120, y: 370, w: 240, h: 55,
        text: "",
        normal_src: "btn_transparent.png",
        press_src: "btn_transparent.png",
        click_func: () => {
          logger.log("TEST PRESSED!")
          this.testBtnBg.setProperty(hmUI.prop.COLOR, 0x1A4D80)

          const now = Date.now()
          const d = new Date(now - 1 * 60000)  // 1 minuto atrás (simula que te dormiste hace 1 min)
          const sleepOnsetMinutes = d.getHours() * 60 + d.getMinutes()
          logger.log("Sending SLEEP_DETECTED via ZML, onset:", sleepOnsetMinutes)

          this.request({
            method: 'SLEEP_DETECTED',
            sleepOnsetMinutes: sleepOnsetMinutes,
            timestamp: now
          })
            .then((data) => {
              logger.log("Response from side:", JSON.stringify(data))
              this.testBtnBg.setProperty(hmUI.prop.COLOR, 0x3366CC)
            })
            .catch((error) => {
              logger.log("Error:", String(error))
              this.testBtnBg.setProperty(hmUI.prop.COLOR, 0xFF0000)
              setTimeout(() => {
                this.testBtnBg.setProperty(hmUI.prop.COLOR, 0x3366CC)
              }, 2000)
            })
        }
      })

      this.checkTimer = createSysTimer(true, 15000, () => {
        const isEnabled = localStorage.getItem(STORAGE_KEY) === "true"
        if (isEnabled) {
          const now = Date.now()
          localStorage.setItem("last_sleep_check", now)
          this.lastCheckWidget.setProperty(hmUI.prop.TEXT, formatTime(now))
        }
      })

      logger.log("build done")
    },

    onDestroy() {
      logger.log("onDestroy")
      if (this.checkTimer) stopTimer(this.checkTimer)
    }
  })
)
