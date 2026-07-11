package com.example.network

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object OnionRouter {
    val isEnabled = MutableStateFlow(false)
    val isConnected = MutableStateFlow(false)

    private const val ORBOT_PACKAGE = "org.torproject.android"
    private const val SOCKS5_HOST = "127.0.0.1"
    private const val SOCKS5_PORT = 9050

    fun isOrbotInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ORBOT_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun requestOrbotStart(context: Context) {
        val intent = Intent("org.torproject.android.intent.action.START")
        intent.setPackage(ORBOT_PACKAGE)
        try {
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            // Ignore broadcast failure, try starting activity as fallback
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(ORBOT_PACKAGE)
                if (launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            } catch (ex: Exception) {
                // ignore
            }
        }
    }

    fun createTorEnabledClient(): OkHttpClient {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(SOCKS5_HOST, SOCKS5_PORT))
        return OkHttpClient.Builder()
            .proxy(proxy)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun enable(context: Context) {
        isEnabled.value = true
        if (isOrbotInstalled(context)) {
            requestOrbotStart(context)
            isConnected.value = true
        } else {
            isConnected.value = false
        }
        com.example.network.NetworkConfig.rebuildClientCurrent()
    }

    fun disable() {
        isEnabled.value = false
        isConnected.value = false
        com.example.network.NetworkConfig.rebuildClientCurrent()
    }

    fun getClientForRequest(): OkHttpClient? {
        return if (isEnabled.value && isConnected.value) {
            createTorEnabledClient()
        } else {
            null
        }
    }
}
