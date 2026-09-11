import * as hmUI from "@zos/ui"
import { log as Logger } from "@zos/utils"
import { localStorage } from "@zos/storage"
import { createSysTimer, stopTimer } from "@zos/timer"
import { BasePage } from "@zeppos/zml/base-page"
import * as appService from "@zos/app-service"
import { queryPermission, requestPermission } from "@zos/app"

var logger = Logger.getLogger("sd")
var KEY = "sleep_detection_enabled"
var SVC = "app-service/index"
var HB = "sleep_service_heartbeat"
var LAST = "last_sleep_check"
var STATUS = "sleep_service_status"
var COUNT = "sleep_check_count"
var START_RESULT = "sleep_service_start_result"
var SLEEP_STATUS = "last_sleep_status"
var PERMS = ["device:os.bg_service"]
var RETRY_MS = 60000
var START_DELAY_MS = 3000
var starting = false
var lastStartAt = 0
var pendingStartTimer = null

function fmt(ts) {
  if (!ts) return "--:--:--"
  var d = new Date(ts)
  return String(d.getHours()).padStart(2, "0") + ":" +
         String(d.getMinutes()).padStart(2, "0") + ":" +
         String(d.getSeconds()).padStart(2, "0")
}

function alive() {
  var h = localStorage.getItem(HB)
  if (!h) return false
  return (Date.now() - Number(h)) < 180000
}

function running() {
  try {
    var services = appService.getAllAppServices()
    return services && services.indexOf(SVC) >= 0
  } catch (e) {
    logger.log("get services failed: " + String(e))
    return false
  }
}

function serviceListText() {
  try {
    var services = appService.getAllAppServices()
    if (!services || services.length === 0) return "none"
    return services.join(",")
  } catch (e) {
    return "err"
  }
}

function enabled() {
  var stored = localStorage.getItem(KEY) === "true"
  var active = running()
  if (active && !stored) localStorage.setItem(KEY, "true")
  return stored || active
}

function statusText() {
  var status = localStorage.getItem(STATUS)
  if (starting) return "starting"
  if (alive()) return status || "running"
  if (running()) {
    if (status === "start requested" || status === "starting") return "waiting heartbeat"
    return status || "stale listed"
  }
  if (localStorage.getItem(KEY) === "true") {
    if (status === "start requested" || status === "starting") return "not confirmed"
    return status || "not running"
  }
  return "off"
}

function startWithFile(showToast) {
  var ret = appService.start({ file: SVC, reload: false, complete_func: function (i) {
    starting = false
    logger.log("start file done: " + JSON.stringify(i))
    localStorage.setItem(START_RESULT, "ret " + ret + " cb " + (i && i.result ? "true" : "false"))
    if (i && i.result) {
      localStorage.setItem(KEY, "true")
      localStorage.setItem(STATUS, "start requested")
      if (showToast) hmUI.showToast({ text: "Service starting" })
    } else {
      localStorage.setItem(KEY, "false")
      localStorage.setItem(STATUS, "start failed")
      if (showToast) hmUI.showToast({ text: "Start failed" })
    }
  }})
  localStorage.setItem(START_RESULT, "ret " + ret + " cb pending")
  logger.log("start file ret=" + ret)
  if ((typeof ret === "number" && ret !== 0) || ret === false) {
    starting = false
    localStorage.setItem(KEY, "false")
    localStorage.setItem(STATUS, "start ret " + ret)
    if (showToast) hmUI.showToast({ text: "Start ret " + ret })
  }
}

function stopSvc(cb) {
  var ret = appService.stop({ file: SVC, complete_func: function (i) {
    logger.log("stop file done: " + JSON.stringify(i))
    if (i && i.result) {
      if (cb) cb()
    } else {
      if (cb) cb()
    }
  }})
  logger.log("stop file ret=" + ret)
  localStorage.setItem(START_RESULT, "stop ret " + ret)
  if ((typeof ret === "number" && ret !== 0) || ret === false) if (cb) cb()
}

function clearServiceState(status) {
  starting = true
  localStorage.setItem(STATUS, status)
  localStorage.setItem(COUNT, "0")
  localStorage.setItem(HB, "")
  localStorage.setItem(LAST, "")
  localStorage.setItem(SLEEP_STATUS, "")
  localStorage.setItem(START_RESULT, "")
}

function doStartSvc(showToast) {
  starting = true
  logger.log("starting service")
  localStorage.setItem(STATUS, "starting")
  if (showToast) hmUI.showToast({ text: "Starting service..." })
  startWithFile(showToast)
}

function scheduleStartAfterStop(showToast) {
  if (pendingStartTimer) {
    stopTimer(pendingStartTimer)
    pendingStartTimer = null
  }
  localStorage.setItem(STATUS, "reset wait")
  localStorage.setItem(START_RESULT, "stop done wait")
  pendingStartTimer = createSysTimer(false, START_DELAY_MS, function () {
    if (pendingStartTimer) {
      stopTimer(pendingStartTimer)
      pendingStartTimer = null
    }
    doStartSvc(showToast)
  })
}

function startSvc(showToast) {
  var now = Date.now()
  if (starting && now - lastStartAt < RETRY_MS) return
  if (now - lastStartAt < 5000) return
  lastStartAt = now
  clearServiceState("resetting")
  logger.log("resetting service before start")
  stopSvc(function () {
    logger.log("pre-start stop done")
    scheduleStartAfterStop(showToast)
  })
}

Page(BasePage({
  state: {},
  onInit: function () { logger.log("onInit") },

  build: function () {
    logger.log("build")
    var page = this
    var raw = localStorage.getItem(KEY)
    var on = enabled()
    logger.log("raw=" + raw + " on=" + on)

    hmUI.createWidget(hmUI.widget.FILL_RECT, { x: 0, y: 0, w: 480, h: 480, color: 0x000000 })
    hmUI.createWidget(hmUI.widget.TEXT, { x: 0, y: 40, w: 480, h: 50, color: 0xFFFFFF, text_size: 32, align_h: hmUI.align.CENTER_H, text: "Sleep Detector" })

    var sw = hmUI.createWidget(hmUI.widget.TEXT, {
      x: 0, y: 110, w: 480, h: 60,
      color: on ? 0x00FF00 : 0xFF3333,
      text_size: 48, align_h: hmUI.align.CENTER_H,
      text: on ? "ON" : "OFF"
    })

    hmUI.createWidget(hmUI.widget.TEXT, { x: 0, y: 190, w: 480, h: 30, color: 0x888888, text_size: 20, align_h: hmUI.align.CENTER_H, text: "Last check" })
    var lc = hmUI.createWidget(hmUI.widget.TEXT, { x: 0, y: 220, w: 480, h: 40, color: 0xCCCCCC, text_size: 28, align_h: hmUI.align.CENTER_H, text: fmt(localStorage.getItem(LAST)) })
    var st = hmUI.createWidget(hmUI.widget.TEXT, {
      x: 0, y: 260, w: 480, h: 24,
      color: 0x888888, text_size: 18,
      align_h: hmUI.align.CENTER_H,
      text: "Status: " + statusText()
    })
    var dbg = hmUI.createWidget(hmUI.widget.TEXT, {
      x: 0, y: 435, w: 480, h: 24,
      color: 0x666666, text_size: 16,
      align_h: hmUI.align.CENTER_H,
      text: "checks " + (localStorage.getItem(COUNT) || "0") + " sleep " + (localStorage.getItem(SLEEP_STATUS) || "-") + " hb " + fmt(localStorage.getItem(HB))
    })
    var dbg2 = hmUI.createWidget(hmUI.widget.TEXT, {
      x: 0, y: 455, w: 480, h: 22,
      color: 0x555555, text_size: 14,
      align_h: hmUI.align.CENTER_H,
      text: "svc " + serviceListText() + " " + (localStorage.getItem(START_RESULT) || "ret -")
    })

    var bb = hmUI.createWidget(hmUI.widget.FILL_RECT, { x: 120, y: 290, w: 240, h: 55, color: on ? 0xCC3333 : 0x33AA33 })
    var bt = hmUI.createWidget(hmUI.widget.TEXT, {
      x: 120, y: 290, w: 240, h: 55,
      color: 0xFFFFFF, text_size: 24,
      align_h: hmUI.align.CENTER_H, align_v: hmUI.align.CENTER_V,
      text: on ? "DISABLE" : "ENABLE"
    })

    function setWidgetText(w, t) {
      w.setProperty(hmUI.prop.TEXT, t)
    }
    function setWidgetColor(w, c) {
      w.setProperty(hmUI.prop.COLOR, c)
    }

    function applyState(isOn) {
      setWidgetText(sw, isOn ? "ON" : "OFF")
      setWidgetColor(sw, isOn ? 0x00FF00 : 0xFF3333)
      setWidgetColor(bb, isOn ? 0xCC3333 : 0x33AA33)
      setWidgetText(bt, isOn ? "DISABLE" : "ENABLE")
    }

    function toggleUI() {
      var cur = enabled()
      var nxt = !cur
      logger.log("TOGGLE: " + cur + " -> " + nxt)
      localStorage.setItem(KEY, nxt ? "true" : "false")
      applyState(nxt)
      if (nxt) {
        var pr = queryPermission({ permissions: PERMS })[0]
        logger.log("perm=" + pr)
        if (pr === 2) { startSvc(true) }
        else {
          requestPermission({ permissions: PERMS, callback: function (r) {
            if (r[0] === 2) { startSvc(true) }
            else {
              localStorage.setItem(KEY, "false")
              localStorage.setItem(STATUS, "permission denied")
              applyState(false)
            }
          }})
        }
      } else {
        localStorage.setItem(STATUS, "stopping")
        stopSvc(function () {
          logger.log("stop done")
          localStorage.setItem(STATUS, "stopped")
        })
        localStorage.setItem(KEY, "false")
        hmUI.showToast({ text: "Service stopped" })
      }
    }

    hmUI.createWidget(hmUI.widget.BUTTON, {
      x: 120, y: 290, w: 240, h: 55, text: "",
      normal_src: "btn_transparent.png", press_src: "btn_transparent.png",
      click_func: function () { toggleUI() }
    })

    hmUI.createWidget(hmUI.widget.FILL_RECT, { x: 120, y: 370, w: 240, h: 55, color: 0x3366CC })
    hmUI.createWidget(hmUI.widget.TEXT, { x: 120, y: 370, w: 240, h: 55, color: 0xFFFFFF, text_size: 22, align_h: hmUI.align.CENTER_H, align_v: hmUI.align.CENTER_V, text: "TEST SLEEP" })
    hmUI.createWidget(hmUI.widget.BUTTON, {
      x: 120, y: 370, w: 240, h: 55, text: "",
      normal_src: "btn_transparent.png", press_src: "btn_transparent.png",
      click_func: function () {
        logger.log("TEST pressed")
        var now = Date.now()
        var d = new Date(now - 60000)
        page.request({ method: "SLEEP_DETECTED", sleepOnsetMinutes: d.getHours() * 60 + d.getMinutes(), timestamp: now })
      }
    })

    this.tmr = createSysTimer(true, 5000, function () {
      var e = enabled()
      setWidgetText(lc, fmt(localStorage.getItem(LAST)))
      setWidgetText(st, "Status: " + statusText())
      setWidgetText(dbg, "checks " + (localStorage.getItem(COUNT) || "0") + " sleep " + (localStorage.getItem(SLEEP_STATUS) || "-") + " hb " + fmt(localStorage.getItem(HB)))
      setWidgetText(dbg2, "svc " + serviceListText() + " " + (localStorage.getItem(START_RESULT) || "ret -"))
      applyState(e)
      if (localStorage.getItem(KEY) === "true" && !alive() && Date.now() - lastStartAt > RETRY_MS) {
        logger.log("hb stale, restart")
        startSvc(false)
      }
    })

    logger.log("build done")
  },

  onDestroy: function () {
    logger.log("onDestroy")
    if (this.tmr) stopTimer(this.tmr)
    if (pendingStartTimer) stopTimer(pendingStartTimer)
  }
}))
