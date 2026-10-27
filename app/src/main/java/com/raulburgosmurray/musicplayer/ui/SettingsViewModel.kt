package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.raulburgosmurray.musicplayer.EqPreset
import com.raulburgosmurray.musicplayer.ui.SortOrder

enum class LayoutMode {
    LIST, GRID
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val isFirstRun = prefs.getBoolean("is_first_run", true)
    
    private val _isDynamicThemingEnabled = MutableStateFlow(
        prefs.getBoolean("dynamic_theming", true)
    )
    val isDynamicThemingEnabled: StateFlow<Boolean> = _isDynamicThemingEnabled.asStateFlow()

    private val _historyLimit = MutableStateFlow(
        prefs.getInt("history_limit", 100)
    )
    val historyLimit: StateFlow<Int> = _historyLimit.asStateFlow()

    private val _libraryRootUris = MutableStateFlow(
        getInitialLibraryRootUris()
    )
    val libraryRootUris: StateFlow<List<String>> = _libraryRootUris.asStateFlow()

    private fun getInitialLibraryRootUris(): List<String> {
        return if (isFirstRun) emptyList()
        else prefs.getStringSet("library_root_uris", emptySet())?.toList() ?: emptyList()
    }

    private val _scanAllMemory = MutableStateFlow(
        if (isFirstRun) false else prefs.getBoolean("scan_all_memory", false)
    )
    val scanAllMemory: StateFlow<Boolean> = _scanAllMemory.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(
        if (isFirstRun) false
        else prefs.getBoolean("onboarding_completed", _libraryRootUris.value.isNotEmpty())
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    init {
        // Mark the first-run state only after both onboarding-related values have
        // been read from the same snapshot. This prevents a stale scan preference
        // from bypassing the onboarding screen on a clean install.
        if (isFirstRun) prefs.edit().putBoolean("is_first_run", false).apply()
    }

    private val _sortOrder = MutableStateFlow(
        SortOrder.valueOf(prefs.getString("sort_order", SortOrder.TITLE.name) ?: SortOrder.TITLE.name)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _layoutMode = MutableStateFlow(
        LayoutMode.valueOf(prefs.getString("layout_mode", LayoutMode.LIST.name) ?: LayoutMode.LIST.name)
    )
    val layoutMode: StateFlow<LayoutMode> = _layoutMode.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _isShakeEnabled = MutableStateFlow(prefs.getBoolean("shake_enabled", true))
    val isShakeEnabled: StateFlow<Boolean> = _isShakeEnabled.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", false))
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    private val _isTimeAnnouncementEnabled = MutableStateFlow(prefs.getBoolean("time_announcement_enabled", false))
    val isTimeAnnouncementEnabled: StateFlow<Boolean> = _isTimeAnnouncementEnabled.asStateFlow()

    private val _timeAnnouncementInterval = MutableStateFlow(prefs.getInt("time_announcement_interval", 30))
    val timeAnnouncementInterval: StateFlow<Int> = _timeAnnouncementInterval.asStateFlow()

    private val _defaultEqPreset = MutableStateFlow(
        prefs.getString("default_eq_preset", EqPreset.FLAT.name)?.let {
            try { EqPreset.valueOf(it) } catch (_: Exception) { EqPreset.FLAT }
        } ?: EqPreset.FLAT
    )
    val defaultEqPreset: StateFlow<EqPreset> = _defaultEqPreset.asStateFlow()

    // Sleep Detection (Amazfit Zepp OS)
    private val _isSleepDetectionEnabled = MutableStateFlow(prefs.getBoolean("sleep_detection_enabled", false))
    val isSleepDetectionEnabled: StateFlow<Boolean> = _isSleepDetectionEnabled.asStateFlow()

    private val _sleepDetectionPort = MutableStateFlow(prefs.getInt("sleep_detection_port", 50002))
    val sleepDetectionPort: StateFlow<Int> = _sleepDetectionPort.asStateFlow()

    private val _sleepRewindMinutes = MutableStateFlow(prefs.getInt("sleep_rewind_minutes", 0))
    val sleepRewindMinutes: StateFlow<Int> = _sleepRewindMinutes.asStateFlow()

    private val _sleepFallbackMinutes = MutableStateFlow(prefs.getInt("sleep_fallback_minutes", 0))
    val sleepFallbackMinutes: StateFlow<Int> = _sleepFallbackMinutes.asStateFlow()

    // Audiobookshelf Integration
    val absRepository = com.raulburgosmurray.musicplayer.data.AudiobookshelfRepository(application)

    private val _isAbsConnected = MutableStateFlow(absRepository.isConnected())
    val isAbsConnected: StateFlow<Boolean> = _isAbsConnected.asStateFlow()

    private val _absServerUrl = MutableStateFlow(absRepository.getServerUrl() ?: "")
    val absServerUrl: StateFlow<String> = _absServerUrl.asStateFlow()

    private val _absUsername = MutableStateFlow(absRepository.getUsername() ?: "")
    val absUsername: StateFlow<String> = _absUsername.asStateFlow()

    private val _absLibraryName = MutableStateFlow(absRepository.getLibraryName() ?: "")
    val absLibraryName: StateFlow<String> = _absLibraryName.asStateFlow()

    private val _absLastSyncTime = MutableStateFlow(absRepository.getLastSyncTime())
    val absLastSyncTime: StateFlow<Long> = _absLastSyncTime.asStateFlow()

    private val _isAbsSyncing = MutableStateFlow(false)
    val isAbsSyncing: StateFlow<Boolean> = _isAbsSyncing.asStateFlow()

    private val _absSyncError = MutableStateFlow<String?>(null)
    val absSyncError: StateFlow<String?> = _absSyncError.asStateFlow()

    fun refreshAbsStatus() {
        _isAbsConnected.value = absRepository.isConnected()
        _absServerUrl.value = absRepository.getServerUrl() ?: ""
        _absUsername.value = absRepository.getUsername() ?: ""
        _absLibraryName.value = absRepository.getLibraryName() ?: ""
        _absLastSyncTime.value = absRepository.getLastSyncTime()
    }

    fun connectAudiobookshelf(url: String, user: String, pass: String, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isAbsSyncing.value = true
            _absSyncError.value = null
            val result = absRepository.connectAndSync(url, user, pass)
            _isAbsSyncing.value = false
            result.fold(
                onSuccess = {
                    _isAbsConnected.value = true
                    _absServerUrl.value = absRepository.getServerUrl() ?: ""
                    _absUsername.value = absRepository.getUsername() ?: ""
                    _absLibraryName.value = absRepository.getLibraryName() ?: ""
                    _absLastSyncTime.value = absRepository.getLastSyncTime()
                    _hasCompletedOnboarding.value = true
                    onComplete(true)
                },
                onFailure = { error ->
                    _absSyncError.value = error.localizedMessage ?: "Error al conectar"
                    onComplete(false)
                }
            )
        }
    }

    fun resyncAudiobookshelf(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _isAbsSyncing.value = true
            _absSyncError.value = null
            val result = absRepository.resync()
            _isAbsSyncing.value = false
            result.fold(
                onSuccess = {
                    _absLastSyncTime.value = absRepository.getLastSyncTime()
                    onComplete(true)
                },
                onFailure = { error ->
                    _absSyncError.value = error.localizedMessage ?: "Error al sincronizar"
                    onComplete(false)
                }
            )
        }
    }

    fun disconnectAudiobookshelf() {
        viewModelScope.launch {
            absRepository.disconnect()
            _isAbsConnected.value = false
            _absServerUrl.value = ""
            _absUsername.value = ""
            _absLibraryName.value = ""
            _absLastSyncTime.value = 0L
            _absSyncError.value = null
        }
    }

    fun setDynamicThemingEnabled(enabled: Boolean) {
        _isDynamicThemingEnabled.value = enabled
        prefs.edit().putBoolean("dynamic_theming", enabled).apply()
    }

    fun setHistoryLimit(limit: Int) {
        _historyLimit.value = limit
        prefs.edit().putInt("history_limit", limit).apply()
    }

    fun addLibraryRootUri(uri: String) {
        val current = _libraryRootUris.value.toMutableList()
        if (current.size < 3 && !current.contains(uri)) {
            current.add(uri)
            _libraryRootUris.value = current
            _hasCompletedOnboarding.value = true
            prefs.edit().putStringSet("library_root_uris", current.toSet())
                .putBoolean("onboarding_completed", true).apply()
        }
    }

    fun removeLibraryRootUri(uri: String) {
        val current = _libraryRootUris.value.toMutableList()
        current.remove(uri)
        _libraryRootUris.value = current
        if (current.isEmpty() && !_scanAllMemory.value) {
            _hasCompletedOnboarding.value = false
        }
        prefs.edit().putStringSet("library_root_uris", current.toSet())
            .putBoolean("onboarding_completed", _hasCompletedOnboarding.value).apply()
    }

    fun setScanAllMemory(enabled: Boolean) {
        _scanAllMemory.value = enabled
        if (enabled) {
            _hasCompletedOnboarding.value = true
        } else if (_libraryRootUris.value.isEmpty()) {
            _hasCompletedOnboarding.value = false
        }
        prefs.edit().putBoolean("scan_all_memory", enabled)
            .putBoolean("onboarding_completed", _hasCompletedOnboarding.value).apply()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    fun setLayoutMode(mode: LayoutMode) {
        _layoutMode.value = mode
        prefs.edit().putString("layout_mode", mode.name).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun setShakeEnabled(enabled: Boolean) {
        _isShakeEnabled.value = enabled
        prefs.edit().putBoolean("shake_enabled", enabled).apply()
    }

    fun setVibrationEnabled(enabled: Boolean) {
        _isVibrationEnabled.value = enabled
        prefs.edit().putBoolean("vibration_enabled", enabled).apply()
    }

    fun setSoundEnabled(enabled: Boolean) {
        _isSoundEnabled.value = enabled
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }

    fun setTimeAnnouncementEnabled(enabled: Boolean) {
        _isTimeAnnouncementEnabled.value = enabled
        prefs.edit().putBoolean("time_announcement_enabled", enabled).apply()
    }

    fun setTimeAnnouncementInterval(minutes: Int) {
        _timeAnnouncementInterval.value = minutes
        prefs.edit().putInt("time_announcement_interval", minutes).apply()
    }

    fun setDefaultEqPreset(preset: EqPreset) {
        _defaultEqPreset.value = preset
        prefs.edit().putString("default_eq_preset", preset.name).apply()
    }

    fun setSleepDetectionEnabled(enabled: Boolean) {
        _isSleepDetectionEnabled.value = enabled
        prefs.edit().putBoolean("sleep_detection_enabled", enabled).apply()
    }

    fun setSleepDetectionPort(port: Int) {
        _sleepDetectionPort.value = port
        prefs.edit().putInt("sleep_detection_port", port).apply()
    }

    fun setSleepRewindMinutes(minutes: Int) {
        _sleepRewindMinutes.value = minutes
        prefs.edit().putInt("sleep_rewind_minutes", minutes).apply()
    }

    fun setSleepFallbackMinutes(minutes: Int) {
        _sleepFallbackMinutes.value = minutes
        prefs.edit().putInt("sleep_fallback_minutes", minutes).apply()
    }
}
