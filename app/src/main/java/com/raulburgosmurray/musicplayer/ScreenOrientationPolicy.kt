package com.raulburgosmurray.musicplayer

import android.app.Activity
import android.content.pm.ActivityInfo

internal const val LARGE_SCREEN_MIN_WIDTH_DP = 600

internal fun allowsLandscapeLayout(smallestScreenWidthDp: Int): Boolean =
    smallestScreenWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP

fun Activity.applyScreenOrientationPolicy() {
    val targetOrientation = if (
        allowsLandscapeLayout(resources.configuration.smallestScreenWidthDp)
    ) {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    if (requestedOrientation != targetOrientation) {
        requestedOrientation = targetOrientation
    }
}
