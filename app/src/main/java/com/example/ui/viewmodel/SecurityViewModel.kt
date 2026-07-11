package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.PhantomRepository
import com.example.security.SelfDestructTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SecurityViewModel(val repository: PhantomRepository) : ViewModel() {
    val biometricEnabled = MutableStateFlow(true)
    val loginEmail = MutableStateFlow("")
    val defaultSelfDestructTimer = MutableStateFlow(SelfDestructTimer.OFF)
    val certificatePinningActive = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            runCatching {
                val session = repository.getSession()
                if (session != null) {
                    loginEmail.value = session.email
                }
            }.onFailure { e ->
                android.util.Log.e("SECURITY_VM_INIT", "Failed to load session from database", e)
            }
        }
    }

    fun addLog(tag: String, msg: String) {
        PhantomViewModel.addLogGlobal(tag, msg)
    }

    fun toggleCertPinning(active: Boolean) {
        certificatePinningActive.value = active
        com.example.network.NetworkConfig.rebuildClient(active)
        addLog("SEC", "Certificate pinning state updated: $active. OkHttpClient rebuilt dynamically.")
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearAllMessages()
            addLog("SYS", "Local chat message history purged.")
        }
    }

    fun resetApp() {
        viewModelScope.launch {
            repository.clearSession()
            repository.clearAllMessages()
            repository.clearAllDevices()
            PhantomViewModel.isLoggedInGlobal.value = false
            addLog("SYS", "Local application container reset.")
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.clearSession()
            PhantomViewModel.isLoggedInGlobal.value = false
            addLog("SYS", "Secure session logged out.")
        }
    }
}
