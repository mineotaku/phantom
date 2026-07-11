package com.example.crypto

import android.util.Base64
import com.example.db.PhantomRepository
import com.example.db.RatchetSessionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

class SessionManager(private val repository: PhantomRepository) {

    suspend fun hasSession(peerId: String): Boolean {
        return repository.getRatchetSession(peerId) != null
    }

    suspend fun deleteSession(peerId: String) {
        repository.deleteRatchetSession(peerId)
    }

    private fun ecdhSharedSecret(privateKey: java.security.PrivateKey, publicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = javax.crypto.KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    suspend fun encryptMessage(
        localIdentityPrivate: ByteArray,
        localIdentityPublic: ByteArray,
        peerId: String,
        peerPublicKey: ByteArray,
        plaintext: ByteArray,
        peerBundle: PreKeyBundle? = null
    ): MessageEnvelope {
        var state = loadSession(peerId)
        var x3dhEphemeralPublicKeyBytes: ByteArray? = null
        if (state == null) {
            val bundle = peerBundle ?: throw IllegalStateException("Prekey bundle required to initiate E2EE session for $peerId")
            val x3dhResult = X3DHProtocol.performX3DHSender(localIdentityPrivate, localIdentityPublic, bundle)
            x3dhEphemeralPublicKeyBytes = x3dhResult.ephemeralPublicKey
            state = DoubleRatchet.initSender(x3dhResult.sharedSecret, bundle.signedPreKey)
        }

        val (ratchetMsg, nextState, messageKey) = DoubleRatchet.ratchetEncrypt(state, plaintext)
        saveSession(peerId, nextState)

        val macKey = X3DHProtocol.hkdf(messageKey, ByteArray(32), "PhantomMacKey".toByteArray(Charsets.UTF_8), 32)
        return MessageEnvelope.create(
            senderIdentityKey = localIdentityPublic,
            header = ratchetMsg.header,
            timestamp = System.currentTimeMillis(),
            nonce = ratchetMsg.nonce,
            ciphertext = ratchetMsg.ciphertext,
            macKey = macKey,
            x3dhEphemeralKey = x3dhEphemeralPublicKeyBytes
        )
    }

    suspend fun decryptMessage(
        localIdentityPrivate: ByteArray,
        peerId: String,
        envelope: MessageEnvelope
    ): ByteArray {
        var state = loadSession(peerId)
        val peerPublicKeyBytes = Base64.decode(envelope.senderIdentityKey, Base64.DEFAULT)

        if (state == null) {
            state = initNewSessionReceiver(localIdentityPrivate, peerId, peerPublicKeyBytes, envelope)
        }

        val ratchetMsg = RatchetMessage(
            header = MessageHeader(
                dhRatchetPublicKey = Base64.decode(envelope.dhRatchetKey, Base64.DEFAULT),
                previousChainLength = envelope.previousChainLength,
                messageNumber = envelope.messageNumber
            ),
            ciphertext = Base64.decode(envelope.ciphertext, Base64.DEFAULT),
            nonce = Base64.decode(envelope.nonce, Base64.DEFAULT)
        )

        val (plaintext, nextState, messageKey) = DoubleRatchet.ratchetDecrypt(state, ratchetMsg)

        val macKey = X3DHProtocol.hkdf(messageKey, ByteArray(32), "PhantomMacKey".toByteArray(Charsets.UTF_8), 32)
        if (!envelope.verifyMac(macKey)) {
            throw SecurityException("Envelope integrity verification failed (MAC mismatch).")
        }
        saveSession(peerId, nextState)

        return plaintext
    }

    private fun initNewSessionSender(
        localIdentityPrivate: ByteArray,
        localIdentityPublic: ByteArray,
        peerId: String,
        peerPublicKey: ByteArray
    ): RatchetState {
        // Build a simulated prekey bundle for the peer since we aren't querying the directory server here
        // (the directory bundle fetching happens in PreKeyStore / PhantomViewModel, we assume we got the bundle)
        val simulatedPreKeyId = 999
        val simulatedSpk = X3DHProtocol.generateSignedPreKey(peerPublicKey, simulatedPreKeyId)
        
        val bundle = PreKeyBundle(
            identityKey = peerPublicKey,
            signedPreKey = simulatedSpk.publicKey,
            signedPreKeySignature = simulatedSpk.signature,
            signedPreKeyId = simulatedPreKeyId,
            oneTimePreKey = null,
            oneTimePreKeyId = null
        )

        val x3dhResult = X3DHProtocol.performX3DHSender(localIdentityPrivate, localIdentityPublic, bundle)
        return DoubleRatchet.initSender(x3dhResult.sharedSecret, bundle.signedPreKey)
    }

    private suspend fun initNewSessionReceiver(
        localIdentityPrivate: ByteArray,
        peerId: String,
        peerPublicKey: ByteArray,
        envelope: MessageEnvelope
    ): RatchetState {
        val keyFactory = KeyFactory.getInstance("EC")
        
        // Fetch or generate our signed prekey keypair
        val latestSpk = repository.getLatestSignedPreKey()
        val localSpkKeyPair = if (latestSpk != null) {
            val pub = keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(latestSpk.publicKey, Base64.DEFAULT)))
            val priv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(latestSpk.privateKey, Base64.DEFAULT)))
            KeyPair(pub, priv)
        } else {
            // Fallback: generate a fresh one
            val spk = X3DHProtocol.generateSignedPreKey(localIdentityPrivate, 1)
            val pub = keyFactory.generatePublic(X509EncodedKeySpec(spk.publicKey))
            val priv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(spk.privateKey))
            KeyPair(pub, priv)
        }

        val ephemKeyStr = envelope.x3dhEphemeralKey ?: throw SecurityException("First message in session must carry X3DH ephemeral key")
        val senderEphemeral = Base64.decode(ephemKeyStr, Base64.DEFAULT)

        val session = repository.getSession() ?: throw IllegalStateException("User session not found")
        val localIdentityPublic = Base64.decode(session.identityPublicKey, Base64.DEFAULT)

        val sharedSecret = X3DHProtocol.performX3DHReceiver(
            localIdentityPrivate = localIdentityPrivate,
            localIdentityPublic = localIdentityPublic,
            localSignedPreKeyPrivate = localSpkKeyPair.private.encoded,
            localSignedPreKeyPublic = localSpkKeyPair.public.encoded,
            localOneTimePreKeyPrivate = null,
            senderIdentityKey = peerPublicKey,
            senderEphemeralKey = senderEphemeral
        )

        return DoubleRatchet.initReceiver(sharedSecret, localSpkKeyPair)
    }

    private suspend fun loadSession(peerId: String): RatchetState? {
        val entity = repository.getRatchetSession(peerId) ?: return null
        val keyFactory = KeyFactory.getInstance("EC")

        val rootKey = Base64.decode(entity.rootKey, Base64.DEFAULT)
        val sendingChainKey = if (entity.sendingChainKey.isNotEmpty()) Base64.decode(entity.sendingChainKey, Base64.DEFAULT) else null
        val receivingChainKey = if (entity.receivingChainKey.isNotEmpty()) Base64.decode(entity.receivingChainKey, Base64.DEFAULT) else null

        val dhSendingKeyPair = if (entity.dhSendingPrivateKey.isNotEmpty() && entity.dhSendingPublicKey.isNotEmpty()) {
            val priv = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(entity.dhSendingPrivateKey, Base64.DEFAULT)))
            val pub = keyFactory.generatePublic(X509EncodedKeySpec(Base64.decode(entity.dhSendingPublicKey, Base64.DEFAULT)))
            KeyPair(pub, priv)
        } else null

        val dhReceivingPublicKey = if (entity.dhReceivingPublicKey.isNotEmpty()) Base64.decode(entity.dhReceivingPublicKey, Base64.DEFAULT) else null

        // Parse skipped keys map
        val skippedMessageKeys = mutableMapOf<Pair<String, Int>, ByteArray>()
        if (entity.skippedMessageKeys.isNotEmpty()) {
            val arr = JSONArray(entity.skippedMessageKeys)
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                val pubStr = item.getString("pub")
                val num = item.getInt("num")
                val key = Base64.decode(item.getString("key"), Base64.DEFAULT)
                skippedMessageKeys[Pair(pubStr, num)] = key
            }
        }

        return RatchetState(
            rootKey = rootKey,
            sendingChainKey = sendingChainKey,
            receivingChainKey = receivingChainKey,
            dhSendingKeyPair = dhSendingKeyPair,
            dhReceivingPublicKey = dhReceivingPublicKey,
            sendingCounter = entity.sendingCounter,
            receivingCounter = entity.receivingCounter,
            previousChainLength = entity.previousChainLength,
            skippedMessageKeys = skippedMessageKeys
        )
    }

    private suspend fun saveSession(peerId: String, state: RatchetState) {
        val rootKeyStr = Base64.encodeToString(state.rootKey, Base64.NO_WRAP)
        val sendingChainKeyStr = state.sendingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        val receivingChainKeyStr = state.receivingChainKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        val dhSendingPrivateKeyStr = state.dhSendingKeyPair?.private?.encoded?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        val dhSendingPublicKeyStr = state.dhSendingKeyPair?.public?.encoded?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        val dhReceivingPublicKeyStr = state.dhReceivingPublicKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""

        // Serialize skipped keys
        val arr = JSONArray()
        state.skippedMessageKeys.forEach { (pair, key) ->
            val obj = JSONObject().apply {
                put("pub", pair.first)
                put("num", pair.second)
                put("key", Base64.encodeToString(key, Base64.NO_WRAP))
            }
            arr.put(obj)
        }

        val entity = RatchetSessionEntity(
            peerId = peerId,
            rootKey = rootKeyStr,
            sendingChainKey = sendingChainKeyStr,
            receivingChainKey = receivingChainKeyStr,
            dhSendingPrivateKey = dhSendingPrivateKeyStr,
            dhSendingPublicKey = dhSendingPublicKeyStr,
            dhReceivingPublicKey = dhReceivingPublicKeyStr,
            sendingCounter = state.sendingCounter,
            receivingCounter = state.receivingCounter,
            previousChainLength = state.previousChainLength,
            skippedMessageKeys = arr.toString(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        repository.insertRatchetSession(entity)
    }
}
