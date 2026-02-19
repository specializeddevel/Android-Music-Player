package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.raulburgosmurray.musicplayer.ui.SortOrder

enum class LayoutMode {
    LIST, GRID
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    
    private val _isDynamicThemingEnabled = MutableStateFlow(
        prefs.getBoolean("dynamic_theming", true)
    )
    val isDynamicThemingEnabled: StateFlow<Boolean> = _isDynamicThemingEnabled.asStateFlow()

    private val _historyLimit = MutableStateFlow(
        prefs.getInt("history_limit", 100)
    )
    val historyLimit: StateFlow<Int> = _historyLimit.asStateFlow()

    private val _libraryRootUri = MutableStateFlow(
        prefs.getString("library_root_uri", null)
    )
    val libraryRootUri: StateFlow<String?> = _libraryRootUri.asStateFlow()

    private val _sortOrder = MutableStateFlow(
        SortOrder.valueOf(prefs.getString("sort_order", SortOrder.TITLE.name) ?: SortOrder.TITLE.name)
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _layoutMode = MutableStateFlow(
        LayoutMode.valueOf(prefs.getString("layout_mode", LayoutMode.LIST.name) ?: LayoutMode.LIST.name)
    )
    val layoutMode: StateFlow<LayoutMode> = _layoutMode.asStateFlow()

    private val _isShakeEnabled = MutableStateFlow(prefs.getBoolean("shake_enabled", true))
    val isShakeEnabled: StateFlow<Boolean> = _isShakeEnabled.asStateFlow()

    private val _isVibrationEnabled = MutableStateFlow(prefs.getBoolean("vibration_enabled", true))
    val isVibrationEnabled: StateFlow<Boolean> = _isVibrationEnabled.asStateFlow()

    private val _isSoundEnabled = MutableStateFlow(prefs.getBoolean("sound_enabled", false))
    val isSoundEnabled: StateFlow<Boolean> = _isSoundEnabled.asStateFlow()

    fun setDynamicThemingEnabled(enabled: Boolean) {
        _isDynamicThemingEnabled.value = enabled
        prefs.edit().putBoolean("dynamic_theming", enabled).apply()
    }

    fun setHistoryLimit(limit: Int) {
        _historyLimit.value = limit
        prefs.edit().putInt("history_limit", limit).apply()
    }

    fun setLibraryRootUri(uri: String?) {
        _libraryRootUri.value = uri
        prefs.edit().putString("library_root_uri", uri).apply()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        prefs.edit().putString("sort_order", order.name).apply()
    }

    fun setLayoutMode(mode: LayoutMode) {
        _layoutMode.value = mode
        prefs.edit().putString("layout_mode", mode.name).apply()
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
}
