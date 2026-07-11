package com.example.crypto

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class IdentityKeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityKeyPair) return false
        return publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int = 31 * publicKey.contentHashCode() + privateKey.contentHashCode()
}

data class SignedPreKey(val keyId: Int, val publicKey: ByteArray, val privateKey: ByteArray, val signature: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedPreKey) return false
        return keyId == other.keyId && publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey) && signature.contentEquals(other.signature)
    }
    override fun hashCode(): Int = 31 * (31 * (31 * keyId + publicKey.contentHashCode()) + privateKey.contentHashCode()) + signature.contentHashCode()
}

data class OneTimePreKey(val keyId: Int, val publicKey: ByteArray, val privateKey: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OneTimePreKey) return false
        return keyId == other.keyId && publicKey.contentEquals(other.publicKey) && privateKey.contentEquals(other.privateKey)
    }
    override fun hashCode(): Int = 31 * (31 * keyId + publicKey.contentHashCode()) + privateKey.contentHashCode()
}

data class PreKeyBundle(
    val identityKey: ByteArray,
    val signedPreKey: ByteArray,
    val signedPreKeySignature: ByteArray,
    val signedPreKeyId: Int,
    val oneTimePreKey: ByteArray?,
    val oneTimePreKeyId: Int?
)

data class X3DHResult(
    val sharedSecret: ByteArray,
    val ephemeralPublicKey: ByteArray,
    val usedOneTimePreKeyId: Int?
)

object X3DHProtocol {
    private const val KEY_ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    fun generateIdentityKeyPair(): IdentityKeyPair {
        // Generate classical P-256 EC identity key pair
        val classicalGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        classicalGenerator.initialize(256)
        val classicalPair = classicalGenerator.generateKeyPair()

        // Generate PQ ML-KEM-768 identity key pair
        val pqGenerator = org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator()
        pqGenerator.init(org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters(SecureRandom(), org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters.ml_kem_768))
        val pqPair = pqGenerator.generateKeyPair()

        val pqPub = pqPair.public as org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
        val pqPriv = pqPair.private as org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters

        val serializedPublic = serializeHybridPublic(classicalPair.public.encoded, pqPub.encoded)
        val serializedPrivate = serializeHybridPublic(classicalPair.private.encoded, pqPriv.encoded)

        return IdentityKeyPair(serializedPublic, serializedPrivate)
    }

    fun generateSignedPreKey(identityPrivateKey: ByteArray, keyId: Int): SignedPreKey {
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(256)
        val keyPair = keyPairGenerator.generateKeyPair()
        val publicKeyBytes = keyPair.public.encoded

        // Sign the public key bytes with the classical identity private key (from hybrid identity key)
        val (classicalIdPrivBytes, _) = deserializeHybridPublic(identityPrivateKey)
        val privateKeySpec = PKCS8EncodedKeySpec(classicalIdPrivBytes)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val identityPrivate = keyFactory.generatePrivate(privateKeySpec)

        // Context-bind: include keyId in the signed data to prevent replay/swap attacks
        val dataToSign = java.nio.ByteBuffer.allocate(4 + publicKeyBytes.size)
            .putInt(keyId)
            .put(publicKeyBytes)
            .array()

        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(identityPrivate)
        signature.update(dataToSign)
        val sigBytes = signature.sign()

        return SignedPreKey(keyId, publicKeyBytes, keyPair.private.encoded, sigBytes)
    }

    fun generateOneTimePreKeys(startId: Int, count: Int): List<OneTimePreKey> {
        val list = mutableListOf<OneTimePreKey>()
        val keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM)
        keyPairGenerator.initialize(256)
        for (i in 0 until count) {
            val keyPair = keyPairGenerator.generateKeyPair()
            list.add(OneTimePreKey(startId + i, keyPair.public.encoded, keyPair.private.encoded))
        }
        return list
    }

    fun serializeHybridPublic(classical: ByteArray, pq: ByteArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(4 + classical.size + 4 + pq.size)
        buffer.putInt(classical.size)
        buffer.put(classical)
        buffer.putInt(pq.size)
        buffer.put(pq)
        return buffer.array()
    }

    fun deserializeHybridPublic(serialized: ByteArray): Pair<ByteArray, ByteArray> {
        val buffer = java.nio.ByteBuffer.wrap(serialized)
        val classicalLen = buffer.int
        val classical = ByteArray(classicalLen)
        buffer.get(classical)
        val pqLen = buffer.int
        val pq = ByteArray(pqLen)
        buffer.get(pq)
        return Pair(classical, pq)
    }

    fun deriveX3DHSalt(aliceIdKey: ByteArray, bobIdKey: ByteArray, aliceEphemKey: ByteArray, bobSpk: ByteArray): ByteArray {
        val (classicalAliceId, _) = deserializeHybridPublic(aliceIdKey)
        val (classicalBobId, _) = deserializeHybridPublic(bobIdKey)
        val (classicalAliceEphem, _) = deserializeHybridPublic(aliceEphemKey)
        return classicalAliceId + classicalBobId + classicalAliceEphem + bobSpk
    }

    fun performX3DHSender(
        localIdentityPrivate: ByteArray,
        localIdentityPublic: ByteArray,
        remoteBundle: PreKeyBundle
    ): X3DHResult {
        // Ephemeral hybrid key pair
        val hybridPair = PostQuantumHybrid.generateHybridKeyPair()

        // Verify signed pre key signature first (with context-bound keyId)
        val verified = verifySignedPreKey(remoteBundle.identityKey, remoteBundle.signedPreKey, remoteBundle.signedPreKeySignature, remoteBundle.signedPreKeyId)
        if (!verified) {
            throw SecurityException("Signed prekey signature verification failed!")
        }

        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val (classicalIdPrivBytes, _) = deserializeHybridPublic(localIdentityPrivate)
        val localIdPrivate = keyFactory.generatePrivate(PKCS8EncodedKeySpec(classicalIdPrivBytes))
        val remoteSignedPreKeyPublic = keyFactory.generatePublic(X509EncodedKeySpec(remoteBundle.signedPreKey))

        val (classicalRemoteIdPubBytes, pqRemoteIdPubBytes) = deserializeHybridPublic(remoteBundle.identityKey)
        val remoteIdPublic = keyFactory.generatePublic(X509EncodedKeySpec(classicalRemoteIdPubBytes))

        // Ephemeral private key is hybridPair.classicalPrivate
        val ephemeralClassicalPrivate = keyFactory.generatePrivate(PKCS8EncodedKeySpec(hybridPair.classicalPrivate))

        // DH1 = ECDH(localIdentityPrivate, remoteSignedPreKeyPublic)
        val dh1 = ecdhSharedSecret(localIdPrivate, remoteSignedPreKeyPublic)
        // DH2 = ECDH(ephemeralPrivate, remoteIdentityKey)
        val dh2 = ecdhSharedSecret(ephemeralClassicalPrivate, remoteIdPublic)
        // DH3 = ECDH(ephemeralPrivate, remoteSignedPreKeyPublic)
        val dh3 = ecdhSharedSecret(ephemeralClassicalPrivate, remoteSignedPreKeyPublic)

        var dh4: ByteArray? = null
        if (remoteBundle.oneTimePreKey != null) {
            val remoteOneTimePreKeyPublic = keyFactory.generatePublic(X509EncodedKeySpec(remoteBundle.oneTimePreKey))
            // DH4 = ECDH(ephemeralPrivate, remoteOneTimePreKeyPublic)
            dh4 = ecdhSharedSecret(ephemeralClassicalPrivate, remoteOneTimePreKeyPublic)
        }

        // Ephemeral public key represents the hybrid package
        // We temporarily serialize it to derive the dynamic salt
        val recipientPublicKey = HybridKeyPair(
            classicalPublic = remoteBundle.signedPreKey,
            classicalPrivate = ByteArray(0),
            pqPublic = pqRemoteIdPubBytes,
            pqPrivate = ByteArray(0)
        )
        val encapsulation = PostQuantumHybrid.hybridEncapsulate(recipientPublicKey)
        val ephemeralPublicKeyBytes = serializeHybridPublic(hybridPair.classicalPublic, encapsulation.pqCiphertext)

        // Combine classical DH inputs using the dynamic key-derived salt
        val classicalInput = dh1 + dh2 + dh3 + (dh4 ?: ByteArray(0))
        val x3dhSalt = deriveX3DHSalt(localIdentityPublic, remoteBundle.identityKey, ephemeralPublicKeyBytes, remoteBundle.signedPreKey)
        val classicalSecret = hkdf(classicalInput, x3dhSalt, CryptoConstants.HKDF_X3DH_INFO.toByteArray(Charsets.UTF_8), 32)

        // Combine classical secret and PQ hybrid secret using dynamic salt
        val combinedInput = classicalSecret + encapsulation.sharedSecret
        val combinedSalt = hkdf(x3dhSalt, "PhantomCombinedSalt".toByteArray(Charsets.UTF_8), "CombinedSaltInfo".toByteArray(Charsets.UTF_8), 32)
        val sharedSecret = hkdf(combinedInput, combinedSalt, "PhantomHybridX3DH".toByteArray(Charsets.UTF_8), 32)

        return X3DHResult(sharedSecret, ephemeralPublicKeyBytes, remoteBundle.oneTimePreKeyId)
    }

    fun performX3DHReceiver(
        localIdentityPrivate: ByteArray,
        localIdentityPublic: ByteArray,
        localSignedPreKeyPrivate: ByteArray,
        localSignedPreKeyPublic: ByteArray,
        localOneTimePreKeyPrivate: ByteArray?,
        senderIdentityKey: ByteArray,
        senderEphemeralKey: ByteArray
    ): ByteArray {
        val (ephemeralClassicalBytes, ephemeralPqBytes) = deserializeHybridPublic(senderEphemeralKey)
        val (classicalIdPrivBytes, pqIdPrivBytes) = deserializeHybridPublic(localIdentityPrivate)
        val (classicalSenderIdPubBytes, _) = deserializeHybridPublic(senderIdentityKey)

        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val localIdPrivate = keyFactory.generatePrivate(PKCS8EncodedKeySpec(classicalIdPrivBytes))
        val localSpkPrivate = keyFactory.generatePrivate(PKCS8EncodedKeySpec(localSignedPreKeyPrivate))
        val senderIdPublic = keyFactory.generatePublic(X509EncodedKeySpec(classicalSenderIdPubBytes))
        val senderEphemeralPublic = keyFactory.generatePublic(X509EncodedKeySpec(ephemeralClassicalBytes))

        // DH1 = ECDH(localSignedPreKeyPrivate, senderIdentityKey)
        val dh1 = ecdhSharedSecret(localSpkPrivate, senderIdPublic)
        // DH2 = ECDH(localIdentityPrivate, senderEphemeralKey)
        val dh2 = ecdhSharedSecret(localIdPrivate, senderEphemeralPublic)
        // DH3 = ECDH(localSignedPreKeyPrivate, senderEphemeralKey)
        val dh3 = ecdhSharedSecret(localSpkPrivate, senderEphemeralPublic)

        var dh4: ByteArray? = null
        if (localOneTimePreKeyPrivate != null) {
            val localOtkPrivate = keyFactory.generatePrivate(PKCS8EncodedKeySpec(localOneTimePreKeyPrivate))
            // DH4 = ECDH(localOneTimePreKeyPrivate, senderEphemeralKey)
            dh4 = ecdhSharedSecret(localOtkPrivate, senderEphemeralPublic)
        }

        // Combine classical DH inputs using the dynamic key-derived salt
        val classicalInput = dh1 + dh2 + dh3 + (dh4 ?: ByteArray(0))
        val x3dhSalt = deriveX3DHSalt(senderIdentityKey, localIdentityPublic, senderEphemeralKey, localSignedPreKeyPublic)
        val classicalSecret = hkdf(classicalInput, x3dhSalt, CryptoConstants.HKDF_X3DH_INFO.toByteArray(Charsets.UTF_8), 32)

        // Perform ML-KEM / PQ Hybrid decapsulation using the extracted local PQ identity private key
        val recipientPrivateKey = HybridKeyPair(
            classicalPublic = ByteArray(0),
            classicalPrivate = localSignedPreKeyPrivate,
            pqPublic = ByteArray(0),
            pqPrivate = pqIdPrivBytes
        )
        val encapsulation = HybridEncapsulation(
            classicalCiphertext = ephemeralClassicalBytes,
            pqCiphertext = ephemeralPqBytes,
            sharedSecret = ByteArray(0)
        )
        val pqSecret = PostQuantumHybrid.hybridDecapsulate(encapsulation, recipientPrivateKey)

        // Combine classical secret and PQ hybrid secret using dynamic salt
        val combinedInput = classicalSecret + pqSecret
        val combinedSalt = hkdf(x3dhSalt, "PhantomCombinedSalt".toByteArray(Charsets.UTF_8), "CombinedSaltInfo".toByteArray(Charsets.UTF_8), 32)
        return hkdf(combinedInput, combinedSalt, "PhantomHybridX3DH".toByteArray(Charsets.UTF_8), 32)
    }

    fun verifySignedPreKey(identityPublicKey: ByteArray, signedPreKeyPublic: ByteArray, signature: ByteArray, signedPreKeyId: Int = 0): Boolean {
        val (classicalIdPubBytes, _) = deserializeHybridPublic(identityPublicKey)
        val keyFactory = KeyFactory.getInstance(KEY_ALGORITHM)
        val identityPublic = keyFactory.generatePublic(X509EncodedKeySpec(classicalIdPubBytes))

        // Context-bind: include keyId in the verified data to match signing
        val dataToVerify = java.nio.ByteBuffer.allocate(4 + signedPreKeyPublic.size)
            .putInt(signedPreKeyId)
            .put(signedPreKeyPublic)
            .array()

        val sig = Signature.getInstance(SIGNATURE_ALGORITHM)
        sig.initVerify(identityPublic)
        sig.update(dataToVerify)
        return sig.verify(signature)
    }

    private fun ecdhSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        // HMAC-SHA256 Extraction
        val macExtract = Mac.getInstance("HmacSHA256")
        val saltKey = if (salt.isEmpty()) ByteArray(32) else salt
        macExtract.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = macExtract.doFinal(ikm)

        // HMAC-SHA256 Expansion
        val macExpand = Mac.getInstance("HmacSHA256")
        macExpand.init(SecretKeySpec(prk, "HmacSHA256"))

        val okm = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var i = 1
        while (offset < length) {
            macExpand.update(t)
            macExpand.update(info)
            macExpand.update(i.toByte())
            t = macExpand.doFinal()
            val chunkLength = minOf(t.size, length - offset)
            System.arraycopy(t, 0, okm, offset, chunkLength)
            offset += chunkLength
            i++
        }
        return okm
    }
}
