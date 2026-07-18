package family.tvlink.core

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Security layer for the public-APK world: everything is keyed off a family
 * code that each device's owner types in once and that is never compiled into
 * the APK. From the code we derive:
 *
 *  - secret channel names ("to-tv-<hmac>"), so an outsider holding the APK
 *    (and thus the anon key) cannot even find the family's channels, and
 *  - an AES-256-GCM key that encrypts every payload, so messages can be
 *    neither read nor forged without the code. Payloads that fail to
 *    decrypt are dropped silently.
 *
 * All devices must use exactly the same code (case-sensitive, trimmed).
 */
object FamilyCrypto {

    private const val KDF_SALT = "familytv-link-v1"
    private const val KDF_ITERATIONS = 120_000
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val PAYLOAD_FIELD = "e"

    // Lenient decode so devices on an older build ignore fields added later
    // instead of dropping the whole message.
    private val json = Json { ignoreUnknownKeys = true }

    // No confusable characters (I/L/O/0/1), so the code survives being read
    // off a TV screen across the room.
    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 8

    /** Random link code, generated and displayed by a TV (~40 bits of entropy). */
    fun generateLinkCode(): String {
        val random = java.security.SecureRandom()
        return buildString(CODE_LENGTH) {
            repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) }
        }
    }

    /** Case, dashes and spaces don't matter when typing a code in. */
    fun normalizeCode(raw: String): String = raw.uppercase().filter { it.isLetterOrDigit() }

    /** "K7F3P2M9" -> "K7F3-P2M9" for on-screen display. */
    fun formatForDisplay(code: String): String = normalizeCode(code).chunked(4).joinToString("-")

    // PBKDF2 at this iteration count takes noticeable time on TV hardware;
    // derive once per code and reuse.
    private val keyCache = ConcurrentHashMap<String, SecretKeySpec>()

    fun deriveKey(code: String): SecretKeySpec {
        val normalized = normalizeCode(code)
        return keyCache.getOrPut(normalized) {
            val spec = PBEKeySpec(normalized.toCharArray(), KDF_SALT.toByteArray(), KDF_ITERATIONS, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES")
        }
    }

    fun channelName(base: String, key: SecretKeySpec): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.encoded, "HmacSHA256"))
        val digest = mac.doFinal(base.toByteArray())
        val suffix = digest.joinToString("") { "%02x".format(it) }.take(16)
        return "$base-$suffix"
    }

    fun encryptMessage(key: SecretKeySpec, message: Message): JsonObject {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(json.encodeToString(Message.serializer(), message).toByteArray())
        val blob = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        return buildJsonObject { put(PAYLOAD_FIELD, blob) }
    }

    /** Returns null (drop the payload) unless it decrypts and parses with this key. */
    fun decryptMessage(key: SecretKeySpec, payload: JsonObject): Message? = runCatching {
        val blob = Base64.decode(payload[PAYLOAD_FIELD]!!.jsonPrimitive.content, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(GCM_TAG_BITS, blob.copyOfRange(0, GCM_IV_BYTES)),
        )
        val plaintext = cipher.doFinal(blob.copyOfRange(GCM_IV_BYTES, blob.size))
        json.decodeFromString(Message.serializer(), String(plaintext))
    }.getOrNull()
}
