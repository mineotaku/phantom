package com.example.notification

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.PhantomApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhantomFirebaseMessagingService : FirebaseMessagingService() {
    private val TAG = "PhantomFCM"

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New Firebase cloud token registered: $token")
        
        val repository = PhantomApplication.instance.repository
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val session = repository.getSession()
            if (session != null) {
                repository.insertSession(session.copy(tokenFCM = token))
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Incoming FCM secure message package received.")

        val context = applicationContext
        if (SecurePushHandler.shouldShowNotification(context)) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = SecurePushHandler.createSecureNotification(context, "New transmission received")
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
        }
    }
}
