package com.raulburgosmurray.musicplayer

object FeatureFlags {
    val P2P_TRANSFER: Boolean = BuildConfig.FEATURE_P2P_TRANSFER
    val CLOUD_SYNC: Boolean   = BuildConfig.FEATURE_CLOUD_SYNC
}

object Constants {
    const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
    const val POSITION_SAVE_INTERVAL_MS = 10000L
    const val SLEEP_TIMER_WARNING_MS = 30000L
    const val SKIP_BACKWARD_MS = 30000L
    const val SKIP_FORWARD_MS = 10000L
    const val MIN_AUDIO_DURATION_MS = 5000L
    const val QR_SCAN_DELAY_MS = 1500L
    const val SOCKET_CONNECT_TIMEOUT_MS = 10000
    const val SOCKET_READ_TIMEOUT_MS = 60000
    const val TRANSFER_SERVER_PORT = 50001
    const val STATEFLOW_STOP_TIMEOUT_MS = 5000L
    const val VIBRATION_DURATION_MS = 500L
}
