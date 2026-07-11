package com.example.crypto

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.pqc.crypto.mlkem.*
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import javax.crypto.KeyAgreement

data class HybridKeyPair(
    val classicalPublic: ByteArray,
    val classicalPrivate: ByteArray,
    val pqPublic: ByteArray,
    val pqPrivate: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HybridKeyPair) return false
        return classicalPublic.contentEquals(other.classicalPublic) &&
                classicalPrivate.contentEquals(other.classicalPrivate) &&
                pqPublic.contentEquals(other.pqPublic) &&
                pqPrivate.contentEquals(other.pqPrivate)
    }

    override fun hashCode(): Int {
        var result = classicalPublic.contentHashCode()
        result = 31 * result + classicalPrivate.contentHashCode()
        result = 31 * result + pqPublic.contentHashCode()
        result = 31 * result + pqPrivate.contentHashCode()
        return result
    }
}

data class HybridEncapsulation(
    val classicalCiphertext: ByteArray,
    val pqCiphertext: ByteArray,
    val sharedSecret: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HybridEncapsulation) return false
        return classicalCiphertext.contentEquals(other.classicalCiphertext) &&
                pqCiphertext.contentEquals(other.pqCiphertext) &&
                sharedSecret.contentEquals(other.sharedSecret)
    }

    override fun hashCode(): Int {
        var result = classicalCiphertext.contentHashCode()
        result = 31 * result + pqCiphertext.contentHashCode()
        result = 31 * result + sharedSecret.contentHashCode()
        return result
    }
}

object PostQuantumHybrid {
    private const val CLASSICAL_ALGO = "EC"
    private val random = SecureRandom()

    fun isAvailable(): Boolean {
        return true // Bouncy Castle PQC dependency is guaranteed by build.gradle.kts
    }

    fun generateHybridKeyPair(): HybridKeyPair {
        // 1. Classical P-256 EC key pair
        val classicalGenerator = KeyPairGenerator.getInstance(CLASSICAL_ALGO)
        classicalGenerator.initialize(256)
        val classicalPair = classicalGenerator.generateKeyPair()

        // 2. Real post-quantum ML-KEM-768 key pair using BC lightweight API
        val pqGenerator = MLKEMKeyPairGenerator()
        pqGenerator.init(MLKEMKeyGenerationParameters(random, MLKEMParameters.ml_kem_768))
        val pqPair = pqGenerator.generateKeyPair()

        val pqPub = pqPair.public as MLKEMPublicKeyParameters
        val pqPriv = pqPair.private as MLKEMPrivateKeyParameters

        return HybridKeyPair(
            classicalPublic = classicalPair.public.encoded,
            classicalPrivate = classicalPair.private.encoded,
            pqPublic = pqPub.encoded,
            pqPrivate = pqPriv.encoded
        )
    }

    fun hybridEncapsulate(recipientPublicKey: HybridKeyPair): HybridEncapsulation {
        // 1. Classical ephemeral P-256 EC key pair
        val classicalGenerator = KeyPairGenerator.getInstance(CLASSICAL_ALGO)
        classicalGenerator.initialize(256)
        val ephemeralClassical = classicalGenerator.generateKeyPair()

        // Classical Diffie-Hellman secret derivation
        val classicalSecret = ecdhSharedSecret(ephemeralClassical, recipientPublicKey.classicalPublic)

        // 2. Real ML-KEM-768 encapsulation
        val pubParams = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, recipientPublicKey.pqPublic)
        val generator = MLKEMGenerator(random)
        val secretWithEncaps = generator.generateEncapsulated(pubParams)

        val pqCiphertext = secretWithEncaps.encapsulation
        val pqSecret = secretWithEncaps.secret

        // Combine using HKDF-SHA256 for domain separation
        val combinedInput = classicalSecret + pqSecret
        val combinedSecret = X3DHProtocol.hkdf(combinedInput, ByteArray(32), "PhantomPQC".toByteArray(Charsets.UTF_8), 32)

        return HybridEncapsulation(
            classicalCiphertext = ephemeralClassical.public.encoded,
            pqCiphertext = pqCiphertext,
            sharedSecret = combinedSecret
        )
    }

    fun hybridDecapsulate(encapsulation: HybridEncapsulation, recipientPrivateKey: HybridKeyPair): ByteArray {
        val keyFactory = java.security.KeyFactory.getInstance(CLASSICAL_ALGO)
        
        // 1. Decapsulate classical EC DH
        val recipientClassicalPrivate = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(recipientPrivateKey.classicalPrivate))
        val ephemeralClassicalPublic = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(encapsulation.classicalCiphertext))
        val classicalSecret = ecdhSharedSecret(recipientClassicalPrivate, ephemeralClassicalPublic)

        // 2. Decapsulate ML-KEM-768 via extractor
        val privParams = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, recipientPrivateKey.pqPrivate)
        val extractor = MLKEMExtractor(privParams)
        val pqSecret = extractor.extractSecret(encapsulation.pqCiphertext)

        // Combine using HKDF-SHA256
        val combinedInput = classicalSecret + pqSecret
        return X3DHProtocol.hkdf(combinedInput, ByteArray(32), "PhantomPQC".toByteArray(Charsets.UTF_8), 32)
    }

    private fun ecdhSharedSecret(ourKeyPair: KeyPair, peerPublicKeyBytes: ByteArray): ByteArray {
        val keyFactory = java.security.KeyFactory.getInstance(CLASSICAL_ALGO)
        val peerPublic = keyFactory.generatePublic(java.security.spec.X509EncodedKeySpec(peerPublicKeyBytes))
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ourKeyPair.private)
        keyAgreement.doPhase(peerPublic, true)
        return keyAgreement.generateSecret()
    }

    private fun ecdhSharedSecret(ourPrivateKey: java.security.PrivateKey, peerPublicKey: java.security.PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(ourPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        return keyAgreement.generateSecret()
    }
}
