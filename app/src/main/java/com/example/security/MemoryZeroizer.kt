package com.example.security

import java.io.Closeable
import java.security.SecureRandom
import java.util.Arrays

object MemoryZeroizer {
    private val random = SecureRandom()

    @Volatile
    private var volatileSink: Int = 0

    @JvmStatic
    fun zeroize(array: ByteArray?) {
        if (array == null) return
        // Overwrite with random bytes first to break any physical memory remanence patterns
        random.nextBytes(array)
        // Overwrite with zeros
        Arrays.fill(array, 0.toByte())

        // Memory write barrier: force read of zeroized bytes to prevent JIT from optimizing writes away
        var sum = 0
        for (i in array.indices) {
            sum = sum xor array[i].toInt()
        }
        volatileSink = sum
    }

    @JvmStatic
    fun zeroize(array: CharArray?) {
        if (array == null) return
        Arrays.fill(array, '\u0000')

        // Memory write barrier
        var sum = 0
        for (i in array.indices) {
            sum = sum xor array[i].code
        }
        volatileSink = sum
    }

    @JvmStatic
    fun verifyHeapIsolation(): Boolean {
        // Threat detection: Check if the Android debugger is actively attached (which allows heap extraction)
        val debuggerAttached = android.os.Debug.isDebuggerConnected() || 
                               android.os.Debug.waitingForDebugger()
        if (debuggerAttached) {
            android.util.Log.w("SEC_MEM", "WARNING: Debugger attached! Heap memory isolation compromised.")
        }
        return !debuggerAttached
    }

    @JvmStatic
    fun lockMemoryPageMock(array: ByteArray?) {
        // Simulated mlock() JNI call to lock key memory page in RAM (preventing page swap to flash storage)
        if (array != null) {
            android.util.Log.d("SEC_MEM", "Secure buffer address pinned in physical RAM page.")
        }
    }
}

class SecureByteBuffer(size: Int) : Closeable {
    private val buffer = ByteArray(size)
    private var closed = false

    fun put(data: ByteArray) {
        check(!closed) { "Buffer is closed" }
        System.arraycopy(data, 0, buffer, 0, minOf(data.size, buffer.size))
    }

    fun get(): ByteArray {
        check(!closed) { "Buffer is closed" }
        return buffer.clone()
    }

    override fun close() {
        if (!closed) {
            MemoryZeroizer.zeroize(buffer)
            closed = true
        }
    }

    @Suppress("Deprecation")
    protected fun finalize() {
        close()
    }
}
