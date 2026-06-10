import './shared/device-polyfill'
import { BaseApp } from '@zeppos/zml/base-app'
import { getPackageInfo } from '@zos/app'

const { appId } = getPackageInfo()
console.log('app id', appId)

App(
  BaseApp({
    globalData: {},
    onCreate(options) {
      console.log('app on create invoke')
    },
    onDestroy(options) {
      console.log('app on destroy invoke')
    }
  })
)
