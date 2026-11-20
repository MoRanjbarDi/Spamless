package com.moez.QKSMS.util

import android.content.Context
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

object KeyVault {
    private val cachedKeys = mutableMapOf<String, ByteArray>()
    private const val ALIAS = "spamless.storage.v1"
    @Synchronized fun key(context: Context, name: String, size: Int): ByteArray {
        cachedKeys[name]?.let { require(it.size == size); return it.copyOf() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 50) }
            val spec = KeyPairGeneratorSpec.Builder(context).setAlias(ALIAS)
                .setSubject(X500Principal("CN=Spamless local storage"))
                .setSerialNumber(BigInteger.ONE).setStartDate(Calendar.getInstance().time)
                .setEndDate(end.time).setKeySize(2048).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
        }
        val file = File(context.noBackupFilesDir, name)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        if (file.exists()) {
            cipher.init(Cipher.DECRYPT_MODE, store.getKey(ALIAS, null))
            return cipher.doFinal(file.readBytes()).also { require(it.size == size); cachedKeys[name] = it.copyOf() }
        }
        val bytes = ByteArray(size).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, store.getCertificate(ALIAS).publicKey)
        val temporary = File(file.path + ".tmp")
        temporary.outputStream().use { it.write(cipher.doFinal(bytes)); it.fd.sync() }
        check(temporary.renameTo(file)) { "Unable to save protected storage key" }
        cachedKeys[name] = bytes.copyOf()
        return bytes
    }
    fun protect(context: Context, text: String): String {
        if (text.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key(context, "preference-key", 32), "AES"), GCMParameterSpec(128, nonce))
        return "vault1:" + Base64.encodeToString(nonce + cipher.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun unprotect(context: Context, text: String): String {
        if (text.isEmpty()) return ""
        require(text.startsWith("vault1:"))
        val bytes = Base64.decode(text.substring(7), Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key(context, "preference-key", 32), "AES"), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
        return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)), Charsets.UTF_8)
    }
}

class ProtectedStringPreference(private val context: Context, private val preference: com.f2prateek.rx.preferences2.Preference<String>) {
    init {
        val value = preference.get()
        if (value.isNotEmpty() && !value.startsWith("vault1:")) preference.set(KeyVault.protect(context, value))
    }
    fun get(): String = KeyVault.unprotect(context, preference.get())
    fun set(value: String) = preference.set(KeyVault.protect(context, value))
    fun asObservable(): io.reactivex.Observable<String> = preference.asObservable().map { KeyVault.unprotect(context, it) }
}
