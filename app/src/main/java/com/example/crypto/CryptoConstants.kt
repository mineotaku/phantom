package com.example.crypto

object CryptoConstants {
    const val PROTOCOL_VERSION = 1
    const val AES_KEY_SIZE = 256
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 128

    const val HKDF_X3DH_INFO = "PhantomX3DH"
    const val HKDF_RATCHET_INFO = "PhantomRatchet"

    const val MAX_SKIPPED_KEYS = 1000
    const val SESSION_EXPIRY_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    const val MAX_OTP_ATTEMPTS = 5
    const val PREKEY_REPLENISH_THRESHOLD = 20
    const val DEFAULT_OTP_BATCH_SIZE = 100
    const val MAX_MESSAGE_AGE_MILLIS = 24L * 60 * 60 * 1000 // 24 hours

    const val RATCHET_KEY_INFO = "PhantomRatchetKey"
    const val MESSAGE_KEY_INFO = "PhantomMessageKey"
    const val CHAIN_KEY_INFO = "PhantomChainKey"
    const val ROOT_KEY_INFO = "PhantomRootKey"
}
