package com.moez.QKSMS.util

import android.util.Base64
import by.cyberpartisan.psms.Message
import by.cyberpartisan.psms.PSmsEncryptor
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Version 2: AES-GCM, random 96-bit nonce, 128-bit tag and authenticated version. */
class SecureMessages {
    fun encode(message: Message, key: ByteArray): String {
        require(key.size == 32) { "A 256-bit shared key is required" }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(AAD)
        return PREFIX + Base64.encodeToString(nonce + cipher.doFinal(message.text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(text: String, key: ByteArray): String {
        require(text.startsWith(PREFIX) && key.size == 32)
        val packed = Base64.decode(text.substring(PREFIX.length), Base64.NO_WRAP)
        require(packed.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    }
    fun isEncrypted(text: String, key: ByteArray): Boolean = try { decrypt(text, key); true } catch (_: Exception) { false }
    /** Incoming commands/classification never fall back to the unauthenticated old protocol. */
    fun decodeIncoming(text: String, key: ByteArray): Message =
        Message(try { decrypt(text, key) } catch (_: Exception) { text })
    /** Historical display only. Never used to authorize an incoming message. */
    fun tryDecode(text: String, key: ByteArray, allowHistorical: Boolean = false): Message {
        if (text.startsWith(PREFIX)) return decodeIncoming(text, key)
        if (!allowHistorical) return Message(text)
        return try { PSmsEncryptor().tryDecode(text, key) } catch (_: Exception) { Message(text) }
    }
    companion object {
        private const val PREFIX = "SL2:"
        private val AAD = "Spamless SMS protocol version 2".toByteArray(Charsets.UTF_8)
    }
}
