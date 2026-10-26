package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secret settings (API keys): kept AES-GCM encrypted, with the key in the app's own folder in the
 * user's profile. Own code.
 */
object MasterKeys {
    const val AES256_GCM_SPEC = "AES256_GCM"
    fun getOrCreate(spec: String): String = "master"
}

object EncryptedSharedPreferences {
    enum class PrefKeyEncryptionScheme { AES256_SIV }
    enum class PrefValueEncryptionScheme { AES256_GCM }

    private fun key(): SecretKeySpec {
        val f = File(Context.root, "settings/.key")
        if (!f.exists()) { f.parentFile.mkdirs(); f.writeBytes(ByteArray(32).also { SecureRandom().nextBytes(it) }) }
        return SecretKeySpec(f.readBytes(), "AES")
    }

    fun create(name: String, alias: String, context: Context, k: PrefKeyEncryptionScheme, v: PrefValueEncryptionScheme): SharedPreferences =
        Secure(context.getSharedPreferences("$name.secure", Context.MODE_PRIVATE), key())

    private class Secure(private val p: SharedPreferences, private val key: SecretKeySpec) : SharedPreferences(File(Context.root, "settings/unused")) {
        fun enc(s: String): String {
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            return Base64.getEncoder().encodeToString(iv + c.doFinal(s.toByteArray()))
        }
        fun dec(s: String?): String? = try {
            val b = Base64.getDecoder().decode(s ?: return null)
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b, 0, 12))
            String(c.doFinal(b, 12, b.size - 12))
        } catch (_: Throwable) { null }
        override fun getString(k: String, d: String?): String? = dec(p.getString(k, null)) ?: d
        override fun edit(): Editor = SecureEditor()
        inner class SecureEditor : Editor(null) {
            private val e = p.edit()
            override fun putString(k: String, v: String?): Editor = apply { e.putString(k, v?.let { enc(it) }) }
            override fun remove(k: String): Editor = apply { e.remove(k) }
            override fun commit(): Boolean = e.commit()
            override fun apply() = e.apply()
        }
    }
}
