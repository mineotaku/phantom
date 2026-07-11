package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.db.PhantomRepository
import com.example.sync.MultiDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class IdentityViewModel(val repository: PhantomRepository) : ViewModel() {
    val client = OkHttpClient()
    val multiDeviceManager = MultiDeviceManager(repository)

    val identityPublicKey = MutableStateFlow("")
    val identityPrivateKey = MutableStateFlow("")
    val signedPreKey = MutableStateFlow("")
    val deviceId = MutableStateFlow("")
    val loginEmail = MutableStateFlow("")
    val activeSessionToken = MutableStateFlow("")

    init {
        viewModelScope.launch {
            runCatching {
                val session = repository.getSession()
                if (session != null) {
                    identityPublicKey.value = session.identityPublicKey
                    identityPrivateKey.value = com.example.CryptoUtils.decrypt(session.identityPrivateKey)
                    signedPreKey.value = session.signedPreKey
                    deviceId.value = session.deviceId
                    loginEmail.value = session.email
                    activeSessionToken.value = session.sessionToken
                }
            }.onFailure { e ->
                android.util.Log.e("IDENTITY_VM_INIT", "Failed to load session from database", e)
            }
        }
    }

    fun addLog(tag: String, msg: String) {
        PhantomViewModel.addLogGlobal(tag, msg)
    }
}
