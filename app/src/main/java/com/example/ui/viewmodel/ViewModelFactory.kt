package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.db.PhantomRepository

class ViewModelFactory(private val repository: PhantomRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> AuthViewModel(repository) as T
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(com.example.PhantomApplication.instance, repository) as T
            modelClass.isAssignableFrom(IdentityViewModel::class.java) -> IdentityViewModel(repository) as T
            modelClass.isAssignableFrom(SecurityViewModel::class.java) -> SecurityViewModel(repository) as T
            modelClass.isAssignableFrom(PhantomViewModel::class.java) -> {
                val app = com.example.PhantomApplication.instance
                PhantomViewModel(app, repository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
