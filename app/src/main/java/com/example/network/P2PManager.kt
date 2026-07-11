package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.Socket

data class P2PPeer(val name: String, val host: String, val port: Int)

class P2PManager(private val context: Context) {
    private val TAG = "P2PManager"
    private val SERVICE_TYPE = "_phantom._tcp."
    private val SERVICE_NAME = "PhantomE2EE"

    val discoveredPeers = MutableStateFlow<List<P2PPeer>>(emptyList())
    val isDiscovering = MutableStateFlow(false)
    val isHosting = MutableStateFlow(false)

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var serverSocket: ServerSocket? = null
    private var listeningJob: kotlinx.coroutines.Job? = null

    // Callback for received E2EE messages
    var messageListener: ((ByteArray) -> Unit)? = null

    fun startHosting(userId: String, port: Int, scope: kotlinx.coroutines.CoroutineScope) {
        if (isHosting.value) return
        try {
            val server = ServerSocket(port)
            serverSocket = server
            isHosting.value = true

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "$SERVICE_NAME-$userId"
                serviceType = SERVICE_TYPE
                setPort(port)
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service registered: ${NsdServiceInfo.serviceName}")
                }
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Registration failed: $errorCode")
                }
                override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            }

            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

            // Background P2P accept loop
            listeningJob = scope.launch(Dispatchers.IO) {
                while (isHosting.value && !server.isClosed) {
                    try {
                        val socket = server.accept()
                        launch {
                            try {
                                socket.use { s ->
                                    val data = receiveMessage(s)
                                    messageListener?.invoke(data)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing incoming P2P message", e)
                            }
                        }
                    } catch (e: Exception) {
                        if (!server.isClosed) {
                            Log.e(TAG, "Error in P2P accept loop", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server socket", e)
            isHosting.value = false
        }
    }

    fun stopHosting() {
        if (!isHosting.value) return
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
            listeningJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop hosting", e)
        } finally {
            isHosting.value = false
            registrationListener = null
            listeningJob = null
            serverSocket = null
        }
    }

    fun startDiscovery() {
        if (isDiscovering.value) return
        discoveredPeers.value = emptyList()
        isDiscovering.value = true

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                nsdManager.stopServiceDiscovery(this)
                isDiscovering.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
                isDiscovering.value = false
            }

            override fun onDiscoveryStarted(regType: String) {}

            override fun onDiscoveryStopped(regType: String) {
                isDiscovering.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            val host = resolvedServiceInfo.host?.hostAddress ?: ""
                            val name = resolvedServiceInfo.serviceName.substringAfter("$SERVICE_NAME-")
                            val port = resolvedServiceInfo.port
                            val peer = P2PPeer(name, host, port)
                            val currentList = discoveredPeers.value.toMutableList()
                            if (!currentList.contains(peer)) {
                                currentList.add(peer)
                                discoveredPeers.value = currentList
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName.substringAfter("$SERVICE_NAME-")
                val currentList = discoveredPeers.value.toMutableList()
                currentList.removeAll { it.name == name }
                discoveredPeers.value = currentList
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        if (!isDiscovering.value) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        } finally {
            isDiscovering.value = false
            discoveryListener = null
        }
    }

    suspend fun sendMessageToPeer(peer: P2PPeer, encryptedMessage: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket(peer.host, peer.port).use { socket ->
                val out = socket.getOutputStream()
                val buffer = java.nio.ByteBuffer.allocate(4).putInt(encryptedMessage.size)
                out.write(buffer.array())
                out.write(encryptedMessage)
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message to P2P peer ${peer.name}", e)
            false
        }
    }

    suspend fun receiveMessage(socket: Socket): ByteArray = withContext(Dispatchers.IO) {
        val stream = socket.getInputStream()
        val lenBytes = ByteArray(4)
        var offset = 0
        while (offset < 4) {
            val r = stream.read(lenBytes, offset, 4 - offset)
            if (r == -1) throw java.io.IOException("Stream closed before reading length prefix")
            offset += r
        }
        val size = java.nio.ByteBuffer.wrap(lenBytes).int
        
        // Bounds checking: prevent OutOfMemory/negative size crashes
        val MAX_MESSAGE_SIZE = 10 * 1024 * 1024 // 10 MB limit
        if (size <= 0 || size > MAX_MESSAGE_SIZE) {
            throw java.io.IOException("Invalid frame length size prefix: $size")
        }
        
        val data = ByteArray(size)
        var total = 0
        while (total < size) {
            val read = stream.read(data, total, size - total)
            if (read == -1) throw java.io.IOException("Stream closed prematurely")
            total += read
        }
        data
    }

    fun cleanup() {
        stopHosting()
        stopDiscovery()
    }
}
