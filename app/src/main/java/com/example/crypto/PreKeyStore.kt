package com.example.crypto

import android.util.Base64
import com.example.db.OneTimePreKeyEntity
import com.example.db.PhantomRepository
import com.example.db.SignedPreKeyEntity

class PreKeyStore(private val repository: PhantomRepository) {

    suspend fun generateAndStoreSignedPreKey(identityPrivateKey: ByteArray, keyId: Int): SignedPreKey {
        val spk = X3DHProtocol.generateSignedPreKey(identityPrivateKey, keyId)
        val entity = SignedPreKeyEntity(
            keyId = spk.keyId,
            publicKey = Base64.encodeToString(spk.publicKey, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(spk.privateKey, Base64.NO_WRAP),
            signature = Base64.encodeToString(spk.signature, Base64.NO_WRAP),
            createdAt = System.currentTimeMillis()
        )
        repository.insertSignedPreKey(entity)
        return spk
    }

    suspend fun generateAndStoreOneTimePreKeys(startId: Int, count: Int): List<OneTimePreKey> {
        val otks = X3DHProtocol.generateOneTimePreKeys(startId, count)
        val entities = otks.map {
            OneTimePreKeyEntity(
                keyId = it.keyId,
                publicKey = Base64.encodeToString(it.publicKey, Base64.NO_WRAP),
                privateKey = Base64.encodeToString(it.privateKey, Base64.NO_WRAP),
                isConsumed = false,
                createdAt = System.currentTimeMillis()
            )
        }
        repository.insertOneTimePreKeys(entities)
        return otks
    }

    suspend fun getSignedPreKey(keyId: Int): SignedPreKey? {
        val entity = repository.getSignedPreKey(keyId) ?: return null
        return SignedPreKey(
            keyId = entity.keyId,
            publicKey = Base64.decode(entity.publicKey, Base64.DEFAULT),
            privateKey = Base64.decode(entity.privateKey, Base64.DEFAULT),
            signature = Base64.decode(entity.signature, Base64.DEFAULT)
        )
    }

    suspend fun getOneTimePreKey(keyId: Int): OneTimePreKey? {
        val entity = repository.getOneTimePreKey(keyId) ?: return null
        return OneTimePreKey(
            keyId = entity.keyId,
            publicKey = Base64.decode(entity.publicKey, Base64.DEFAULT),
            privateKey = Base64.decode(entity.privateKey, Base64.DEFAULT)
        )
    }

    suspend fun consumeOneTimePreKey(keyId: Int) {
        repository.consumeOneTimePreKey(keyId)
    }

    suspend fun getAvailableOneTimePreKeyCount(): Int {
        return repository.getAvailableOneTimePreKeyCount()
    }

    suspend fun shouldReplenishPreKeys(): Boolean {
        return getAvailableOneTimePreKeyCount() < CryptoConstants.PREKEY_REPLENISH_THRESHOLD
    }

    suspend fun getLocalPreKeyBundle(identityPublicKey: ByteArray): PreKeyBundle {
        val spkEntity = repository.getLatestSignedPreKey() ?: throw IllegalStateException("No signed prekey generated!")
        val otkEntity = repository.getNextAvailableOneTimePreKey()

        return PreKeyBundle(
            identityKey = identityPublicKey,
            signedPreKey = Base64.decode(spkEntity.publicKey, Base64.DEFAULT),
            signedPreKeySignature = Base64.decode(spkEntity.signature, Base64.DEFAULT),
            signedPreKeyId = spkEntity.keyId,
            oneTimePreKey = otkEntity?.let { Base64.decode(it.publicKey, Base64.DEFAULT) },
            oneTimePreKeyId = otkEntity?.keyId
        )
    }
}
