package com.example

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.DoubleRatchet
import com.example.crypto.MessageEnvelope
import com.example.crypto.MessageHeader
import com.example.crypto.SafetyNumbers
import com.example.crypto.X3DHProtocol
import com.example.security.DuressPin
import com.example.security.SealedSender
import com.example.security.SenderCertificate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSafetyNumbers_noCrashOnLargeIndex() {
    for (i in 0..100) {
      val safetyNumber = String.format("%05d", i * 13) + "1234567890123456789012345678901234567890123456789012345"
      val words = SafetyNumbers.computeSasWords(safetyNumber)
      assertEquals(4, words.size)
      for (word in words) {
        assertNotNull(word)
        assertTrue(word.isNotBlank())
      }
    }
  }

  @Test
  fun testMessageEnvelope_verifyMac() {
    val macKey = ByteArray(32) { 1.toByte() }
    val senderIdentityKey = ByteArray(32) { 2.toByte() }
    val header = MessageHeader(ByteArray(32) { 3.toByte() }, 1, 5)
    val nonce = ByteArray(12) { 4.toByte() }
    val ciphertext = "Hello world".toByteArray()

    val envelope = MessageEnvelope.create(
      senderIdentityKey,
      header,
      System.currentTimeMillis(),
      nonce,
      ciphertext,
      macKey
    )

    assertTrue(envelope.verifyMac(macKey))
    assertFalse(envelope.verifyMac(ByteArray(32) { 0.toByte() }))
  }

  @Test
  fun testSealedSender_correctRecipientAddress() {
    val senderCert = SenderCertificate(
      senderUserId = "alice",
      senderIdentityKey = X3DHProtocol.generateIdentityKeyPair().publicKey,
      expiration = System.currentTimeMillis() + 100000,
      serverSignature = ByteArray(64) { 2.toByte() }
    )
    val recipientIdentityKeyPair = X3DHProtocol.generateIdentityKeyPair()

    val envelope = SealedSender.sealMessage(
      recipientUserId = "bob",
      senderCertificate = senderCert,
      recipientIdentityKey = recipientIdentityKeyPair.publicKey,
      messageContent = "test content".toByteArray()
    )

    assertEquals("bob", envelope.recipientUserId)
  }

  @Test
  fun testDoubleRatchetRoundTrip() {
    val sharedKey = ByteArray(32) { 9.toByte() }
    val bobIdentityKeyPair = X3DHProtocol.generateIdentityKeyPair()
    val bobDHKeyPair = X3DHProtocol.generateSignedPreKey(bobIdentityKeyPair.privateKey, 1)

    // Reconstruct Java Security KeyPair for Receiver
    val keyFactory = java.security.KeyFactory.getInstance("EC")
    val pubSpec = java.security.spec.X509EncodedKeySpec(bobDHKeyPair.publicKey)
    val privSpec = java.security.spec.PKCS8EncodedKeySpec(bobDHKeyPair.privateKey)
    val keyPair = java.security.KeyPair(keyFactory.generatePublic(pubSpec), keyFactory.generatePrivate(privSpec))

    val aliceState = DoubleRatchet.initSender(sharedKey, bobDHKeyPair.publicKey)
    val bobState = DoubleRatchet.initReceiver(sharedKey, keyPair)

    val plaintext = "Hello Bob, this is a secret!".toByteArray(Charsets.UTF_8)
    val (ratchetMsg, _, _) = DoubleRatchet.ratchetEncrypt(aliceState, plaintext)
    
    val (decrypted1, _, _) = DoubleRatchet.ratchetDecrypt(bobState, ratchetMsg)

    assertArrayEquals(plaintext, decrypted1)
  }

  @Test
  fun testDuressPinHashing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    DuressPin.storeRealPin(context, "1234")
    assertTrue(DuressPin.isAppLockEnabled(context))
    assertTrue(DuressPin.verifyRealPin(context, "1234"))
    assertFalse(DuressPin.verifyRealPin(context, "9999"))

    DuressPin.storeDuressPin(context, "4321")
    assertTrue(DuressPin.isEnabled(context))
    assertTrue(DuressPin.verifyDuressPin(context, "4321"))
    assertFalse(DuressPin.verifyDuressPin(context, "1111"))
  }
}
