package com.example.crypto

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

data class MessageHeader(
    val dhRatchetPublicKey: ByteArray,
    val previousChainLength: Int,
    val messageNumber: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessageHeader) return false
        return dhRatchetPublicKey.contentEquals(other.dhRatchetPublicKey) &&
                previousChainLength == other.previousChainLength &&
                messageNumber == other.messageNumber
    }

    override fun hashCode(): Int {
        var result = dhRatchetPublicKey.contentHashCode()
        result = 31 * result + previousChainLength
        result = 31 * result + messageNumber
        return result
    }
}

data class RatchetMessage(
    val header: MessageHeader,
    val ciphertext: ByteArray,
    val nonce: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetMessage) return false
        return header == other.header &&
                ciphertext.contentEquals(other.ciphertext) &&
                nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }
}

data class RatchetState(
    val rootKey: ByteArray,
    val sendingChainKey: ByteArray?,
    val receivingChainKey: ByteArray?,
    val dhSendingKeyPair: KeyPair?,
    val dhReceivingPublicKey: ByteArray?,
    val sendingCounter: Int,
    val receivingCounter: Int,
    val previousChainLength: Int,
    val skippedMessageKeys: Map<Pair<String, Int>, ByteArray>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RatchetState) return false
        return rootKey.contentEquals(other.rootKey) &&
                (sendingChainKey?.contentEquals(other.sendingChainKey) ?: (other.sendingChainKey == null)) &&
                (receivingChainKey?.contentEquals(other.receivingChainKey) ?: (other.receivingChainKey == null)) &&
                sendingCounter == other.sendingCounter &&
                receivingCounter == other.receivingCounter &&
                previousChainLength == other.previousChainLength
    }

    override fun hashCode(): Int {
        var result = rootKey.contentHashCode()
        result = 31 * result + (sendingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + (receivingChainKey?.contentHashCode() ?: 0)
        result = 31 * result + sendingCounter
        result = 31 * result + receivingCounter
        result = 31 * result + previousChainLength
        return result
    }
}

object DoubleRatchet {
    private const val KEY_ALGORITHM = "EC"
    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(256)
        return keyPairGenerator.generateKeyPair()
    }

    private fun dhSecret(privateKey: KeyPair, publicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey.private)
        keyAgreement.doPhase(pubKey, true)
        return keyAgreement.generateSecret()
    }

    private fun kdfRk(rootKey: ByteArray, dhOutput: ByteArray): Pair<ByteArray, ByteArray> {
        val salt = rootKey
        val info = CryptoConstants.ROOT_KEY_INFO.toByteArray(Charsets.UTF_8)
        val okm = X3DHProtocol.hkdf(dhOutput, salt, info, 64)
        val nextRootKey = okm.sliceArray(0 until 32)
        val chainKey = okm.sliceArray(32 until 64)
        return Pair(nextRootKey, chainKey)
    }

    private fun kdfCk(chainKey: ByteArray): Pair<ByteArray, ByteArray> {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        
        // Input 0x01 for next chain key
        mac.update(0x01.toByte())
        val nextChainKey = mac.doFinal()

        // Input 0x02 for message key
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        mac.update(0x02.toByte())
        val messageKey = mac.doFinal()

        return Pair(nextChainKey, messageKey)
    }

    fun initSender(sharedSecret: ByteArray, recipientPublicKey: ByteArray): RatchetState {
        val dhSendingKeyPair = generateKeyPair()
        val dhOutput = dhSecret(dhSendingKeyPair, recipientPublicKey)
        val (rootKey, sendingChainKey) = kdfRk(sharedSecret, dhOutput)
        return RatchetState(
            rootKey = rootKey,
            sendingChainKey = sendingChainKey,
            receivingChainKey = null,
            dhSendingKeyPair = dhSendingKeyPair,
            dhReceivingPublicKey = recipientPublicKey,
            sendingCounter = 0,
            receivingCounter = 0,
            previousChainLength = 0,
            skippedMessageKeys = mutableMapOf()
        )
    }

    fun initReceiver(sharedSecret: ByteArray, ourKeyPair: KeyPair): RatchetState {
        return RatchetState(
            rootKey = sharedSecret,
            sendingChainKey = null,
            receivingChainKey = null,
            dhSendingKeyPair = ourKeyPair,
            dhReceivingPublicKey = null,
            sendingCounter = 0,
            receivingCounter = 0,
            previousChainLength = 0,
            skippedMessageKeys = mutableMapOf()
        )
    }

    fun ratchetEncrypt(state: RatchetState, plaintext: ByteArray): Triple<RatchetMessage, RatchetState, ByteArray> {
        val currentChainKey = state.sendingChainKey ?: throw IllegalStateException("Sending chain key not initialized!")
        val (nextChainKey, messageKey) = kdfCk(currentChainKey)
        val nonce = ByteArray(CryptoConstants.NONCE_SIZE).also { random.nextBytes(it) }

        val header = MessageHeader(
            dhRatchetPublicKey = state.dhSendingKeyPair?.public?.encoded ?: throw IllegalStateException("No sending DH key!"),
            previousChainLength = state.previousChainLength,
            messageNumber = state.sendingCounter
        )

        // Bind header to AEAD Associated Data (AAD)
        val aad = serializeHeader(header)
        val paddedPlaintext = com.example.CryptoUtils.pad(plaintext)
        val ciphertext = aesGcmEncrypt(paddedPlaintext, messageKey, nonce, aad)

        val nextState = state.copy(
            sendingChainKey = nextChainKey,
            sendingCounter = state.sendingCounter + 1
        )

        return Triple(RatchetMessage(header, ciphertext, nonce), nextState, messageKey)
    }

    fun ratchetDecrypt(state: RatchetState, message: RatchetMessage): Triple<ByteArray, RatchetState, ByteArray> {
        var activeState = state
        val peerKeyBase64 = Base64.encodeToString(message.header.dhRatchetPublicKey, Base64.NO_WRAP)

        // 1. Try to decrypt using skipped message keys
        val skippedKey = Pair(peerKeyBase64, message.header.messageNumber)
        if (activeState.skippedMessageKeys.containsKey(skippedKey)) {
            val messageKey = activeState.skippedMessageKeys[skippedKey]!!
            val aad = serializeHeader(message.header)
            val paddedPlaintext = aesGcmDecrypt(message.ciphertext, messageKey, message.nonce, aad)
            val plaintext = com.example.CryptoUtils.unpad(paddedPlaintext)
            
            // Cleanup used key by copying
            val updatedKeys = activeState.skippedMessageKeys.toMutableMap()
            updatedKeys.remove(skippedKey)
            val nextState = activeState.copy(skippedMessageKeys = updatedKeys)
            return Triple(plaintext, nextState, messageKey)
        }

        // 2. If new DH key received, perform DH ratchet step first
        if (activeState.dhReceivingPublicKey == null || !activeState.dhReceivingPublicKey.contentEquals(message.header.dhRatchetPublicKey)) {
            // Replay check for older DH chains: if we already switched to a new DH key, any messages from the old DH key
            // MUST be in skippedMessageKeys. If not, they are replays/duplicates!
            if (activeState.dhReceivingPublicKey != null) {
                throw SecurityException("Replayed or duplicate message from older DH ratchet chain detected.")
            }
            activeState = skipMessageKeys(activeState, message.header.previousChainLength)
            activeState = dhRatchetStep(activeState, message.header.dhRatchetPublicKey)
        }

        // Replay check: reject if message number is older than the current counter for this DH key
        if (message.header.messageNumber < activeState.receivingCounter) {
            throw SecurityException("Replayed or duplicate message detected (messageNumber older than counter).")
        }

        // 3. Skip any missed message keys in the current chain
        activeState = skipMessageKeys(activeState, message.header.messageNumber)

        // 4. Perform chain key step
        val currentReceivingChainKey = activeState.receivingChainKey ?: throw IllegalStateException("Receiving chain key not initialized!")
        val (nextChainKey, messageKey) = kdfCk(currentReceivingChainKey)
        
        val aad = serializeHeader(message.header)
        val paddedPlaintext = aesGcmDecrypt(message.ciphertext, messageKey, message.nonce, aad)
        val plaintext = com.example.CryptoUtils.unpad(paddedPlaintext)

        val nextState = activeState.copy(
            receivingChainKey = nextChainKey,
            receivingCounter = activeState.receivingCounter + 1
        )

        return Triple(plaintext, nextState, messageKey)
    }

    private fun dhRatchetStep(state: RatchetState, receivedPublicKey: ByteArray): RatchetState {
        val previousChainLength = state.sendingCounter
        val dhSendingKeyPair = generateKeyPair()
        
        // DH input with received public key using current sending private key
        val dhOutput1 = dhSecret(state.dhSendingKeyPair ?: throw IllegalStateException("No local DH sending pair"), receivedPublicKey)
        val (rootKey1, receivingChainKey) = kdfRk(state.rootKey, dhOutput1)

        // DH input with received public key using new sending private key
        val dhOutput2 = dhSecret(dhSendingKeyPair, receivedPublicKey)
        val (rootKey2, sendingChainKey) = kdfRk(rootKey1, dhOutput2)

        return state.copy(
            rootKey = rootKey2,
            sendingChainKey = sendingChainKey,
            receivingChainKey = receivingChainKey,
            dhSendingKeyPair = dhSendingKeyPair,
            dhReceivingPublicKey = receivedPublicKey,
            sendingCounter = 0,
            receivingCounter = 0,
            previousChainLength = previousChainLength
        )
    }

    private fun skipMessageKeys(state: RatchetState, until: Int): RatchetState {
        if (state.receivingChainKey == null) return state // No active receiving chain yet
        if (state.receivingCounter + CryptoConstants.MAX_SKIPPED_KEYS < until) {
            throw SecurityException("Too many missed messages; gap exceeds limit.")
        }
        if (state.receivingCounter >= until) return state
        
        var currentChainKey = state.receivingChainKey
        var counter = state.receivingCounter
        val peerKeyBase64 = Base64.encodeToString(state.dhReceivingPublicKey ?: return state, Base64.NO_WRAP)
        val updatedKeys = state.skippedMessageKeys.toMutableMap()

        while (counter < until) {
            val (nextChainKey, messageKey) = kdfCk(currentChainKey!!)
            updatedKeys[Pair(peerKeyBase64, counter)] = messageKey
            currentChainKey = nextChainKey
            counter++
        }

        return state.copy(
            receivingChainKey = currentChainKey,
            receivingCounter = counter,
            skippedMessageKeys = updatedKeys
        )
    }

    private fun serializeHeader(header: MessageHeader): ByteArray {
        val pubKey = header.dhRatchetPublicKey
        val prevLen = header.previousChainLength
        val msgNum = header.messageNumber
        val buffer = java.nio.ByteBuffer.allocate(4 + pubKey.size + 4 + 4)
        buffer.putInt(pubKey.size)
        buffer.put(pubKey)
        buffer.putInt(prevLen)
        buffer.putInt(msgNum)
        return buffer.array()
    }

    private fun aesGcmEncrypt(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(CryptoConstants.TAG_SIZE, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(CryptoConstants.TAG_SIZE, nonce)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), spec)
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
