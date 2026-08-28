package com.raulburgosmurray.musicplayer.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.raulburgosmurray.musicplayer.data.AppDatabase
import com.raulburgosmurray.musicplayer.data.AudiobookProgress
import com.raulburgosmurray.musicplayer.data.GoogleDriveService
import com.raulburgosmurray.musicplayer.data.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.util.Log
import androidx.annotation.VisibleForTesting

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    companion object { private const val TAG = "SyncViewModel" }
    private val progressRepository = ProgressRepository(AppDatabase.getDatabase(application).progressDao())
    private val prefs = application.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _userAccount = MutableStateFlow<GoogleSignInAccount?>(
        GoogleSignIn.getLastSignedInAccount(application)
    )
    val userAccount: StateFlow<GoogleSignInAccount?> = _userAccount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    fun onLoginSuccess(account: GoogleSignInAccount) {
        _userAccount.value = account
        syncNow() // Sincronizar inmediatamente al loguear
    }

    fun onLogout() {
        _userAccount.value = null
        prefs.edit().remove("last_sync_time").apply()
        _lastSyncTime.value = 0L
    }

    fun syncNow() {
        val account = _userAccount.value ?: return
        val context = getApplication<Application>().applicationContext
        
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val email = account.email ?: return@launch
                val driveService = GoogleDriveService(context, email)
                
                // 1. Descargar de la nube
                val cloudProgress = driveService.downloadProgress()
                
                // 2. Obtener local
                val localProgress = withContext(Dispatchers.IO) {
                    progressRepository.getAllProgress()
                }

                // 3. Mezclar inteligentemente (Merge)
                if (cloudProgress != null) {
                    mergeProgress(localProgress, cloudProgress)
                }

                // 4. Subir la versión definitiva a la nube
                val finalProgress = withContext(Dispatchers.IO) {
                    progressRepository.getAllProgress()
                }
                val success = driveService.uploadProgress(finalProgress)
                
                if (success) {
                    val now = System.currentTimeMillis()
                    _lastSyncTime.value = now
                    prefs.edit().putLong("last_sync_time", now).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun uploadOnly() {
        val account = _userAccount.value ?: return
        val context = getApplication<Application>().applicationContext
        
        viewModelScope.launch {
            try {
                val email = account.email ?: return@launch
                val driveService = GoogleDriveService(context, email)
                val localProgress = withContext(Dispatchers.IO) {
                    progressRepository.getAllProgress()
                }
                driveService.uploadProgress(localProgress)
            } catch (e: Exception) {
                Log.e(TAG, "Error during sync", e)
            }
        }
    }

    @VisibleForTesting
    internal suspend fun mergeProgress(local: List<AudiobookProgress>, cloud: List<AudiobookProgress>) = withContext(Dispatchers.IO) {
        val merged = mutableListOf<AudiobookProgress>()
        
        // Convertimos a mapa para acceso rápido
        val localMap = local.associateBy { it.mediaId }
        val cloudMap = cloud.associateBy { it.mediaId }

        val allIds = localMap.keys + cloudMap.keys

        for (id in allIds) {
            val l = localMap[id]
            val c = cloudMap[id]

            when {
                l != null && c != null -> {
                    // Quedarnos con el más reciente
                    merged.add(if (c.lastUpdated > l.lastUpdated) c else l)
                }
                l == null && c != null -> merged.add(c) // Nuevo desde la nube
                l != null && c == null -> merged.add(l) // Solo en local, conservar
            }
        }

        if (merged.isNotEmpty()) {
            progressRepository.saveAllProgress(merged)
        }
    }
}
