package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    fun setDynamicThemingEnabled(enabled: Boolean) {
        _isDynamicThemingEnabled.value = enabled
        prefs.edit().putBoolean("dynamic_theming", enabled).apply()
    }

    fun setHistoryLimit(limit: Int) {
        _historyLimit.value = limit
        prefs.edit().putInt("history_limit", limit).apply()
    }
}
