package com.raulburgosmurray.musicplayer

object FeatureFlags {
    val P2P_TRANSFER: Boolean = BuildConfig.FEATURE_P2P_TRANSFER
    val CLOUD_SYNC: Boolean   = BuildConfig.FEATURE_CLOUD_SYNC
    val SCAN_ALL_MEMORY: Boolean = BuildConfig.FEATURE_SCAN_ALL_MEMORY
    val SLEEP_DETECTION: Boolean = BuildConfig.FEATURE_SLEEP_DETECTION
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
    const val SOCKET_ACCEPT_TIMEOUT_MS = 30000
    const val TRANSFER_SERVER_PORT = 50001
    const val SLEEP_DETECTION_PORT = 50002
    const val STATEFLOW_STOP_TIMEOUT_MS = 5000L
    const val VIBRATION_DURATION_MS = 500L
    // Smart Rewind
    const val SMART_REWIND_VERY_SHORT_PAUSE_MS = 10_000L
    const val SMART_REWIND_SHORT_PAUSE_MS = 300_000L
    const val SMART_REWIND_MEDIUM_PAUSE_MS = 1_800_000L
    const val SMART_REWIND_LONG_PAUSE_MS = 7_200_000L
    const val SMART_REWIND_VERY_SHORT_AMOUNT_MS = 2_000L
    const val SMART_REWIND_SHORT_AMOUNT_MS = 3_000L
    const val SMART_REWIND_MEDIUM_AMOUNT_MS = 5_000L
    const val SMART_REWIND_LONG_AMOUNT_MS = 10_000L
    const val SMART_REWIND_VERY_LONG_AMOUNT_MS = 20_000L
    // Audiobook filtering
    const val MIN_AUDIOBOOK_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB minimum
    val EXCLUDED_AUDIO_PATHS = listOf(
        "WhatsApp",
        "Telegram",
        "Signal",
        "Discord",
        "Messenger",
        "Slack",
        "Skype",
        "Viber",
        "WeChat",
        "Line",
        "Snapchat",
        "Instagram",
        "Facebook",
        "抖音",  // Douyin
        "快手",  // Kuaishou
        "voice recorder",
        "Voice Recorder",
        "recording",
        "Recording"
    )
}
