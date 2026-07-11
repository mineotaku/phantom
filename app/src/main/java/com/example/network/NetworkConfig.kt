package com.example.network

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.CertificatePinner
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient

object NetworkConfig {
    val serverHost = MutableStateFlow("phantom-pu9t.onrender.com")
    val usePinningState = MutableStateFlow(false)
    val clientState = MutableStateFlow(buildClient(false))

    fun rebuildClient(usePinning: Boolean) {
        usePinningState.value = usePinning
        clientState.value = buildClient(usePinning)
    }

    fun rebuildClientCurrent() {
        clientState.value = buildClient(usePinningState.value)
    }

    private fun buildClient(usePinning: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (com.example.network.OnionRouter.isEnabled.value && com.example.network.OnionRouter.isConnected.value) {
            val proxy = java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", 9050))
            builder.proxy(proxy)
                .connectTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        } else if (usePinning) {
            val certPinner = CertificatePinner.Builder()
                .add("phantom-pu9t.onrender.com",
                    "sha256/+MYbkPTfMGLCOzqOKC2gMWfTfIxCc7u56wa2yIA3kCQ=",
                    "sha256/jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
                )
                .build()
            builder.certificatePinner(certPinner)
        }
        builder.connectionSpecs(listOf(ConnectionSpec.RESTRICTED_TLS, ConnectionSpec.MODERN_TLS))
        return builder.build()
    }

    fun getServerUrl(path: String): String {
        val host = serverHost.value.trim()
        val scheme = if (host.contains("10.0.2.2") || host.contains("192.168") || host.contains("localhost") || host.contains("127.0.0.1") || host.contains(":")) {
            "http"
        } else {
            "https"
        }
        return "$scheme://$host$path"
    }
}
