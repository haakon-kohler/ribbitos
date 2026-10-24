package dev.ribbitos.lite.update

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * One published ribbitOS build, as a feed's `update.json` describes it. The APK's own signing key
 * is AOSP's public one (keys/README.md), so it proves nothing: what makes an update trustworthy is
 * this file's signature by the release key (docs/UPDATES.md), which [parse] insists on.
 */
class Release(val versionCode: Long, val versionName: String, val apk: String, val sha256: String) {
    override fun toString() = "Release($versionName, code $versionCode, $apk)"

    companion object {
        private val APK_NAME = Regex("[A-Za-z0-9._-]{1,100}\\.apk")
        private val SHA256 = Regex("[0-9a-f]{64}")

        /** Reads [manifest] once its [signature] checks out against [key]; throws [IOException] otherwise. */
        @Throws(IOException::class)
        fun parse(manifest: ByteArray, signature: ByteArray, key: PublicKey): Release {
            if (!signed(manifest, signature, key)) throw IOException("update.json isn't signed by the release key")
            val r = read(manifest)
            if (r.versionCode <= 0 || !APK_NAME.matches(r.apk) || !SHA256.matches(r.sha256)) {
                throw IOException("Incomplete update.json")
            }
            return r
        }

        private fun read(manifest: ByteArray): Release = try {
            val o = JSONObject(String(manifest, Charsets.UTF_8))
            Release(
                versionCode = o.optLong("versionCode"),
                versionName = o.optString("versionName"),
                apk = o.optString("apk"),
                sha256 = o.optString("sha256"),
            )
        } catch (e: JSONException) {
            throw IOException("Malformed update.json", e)
        }

        /** The release key from its PEM text (res/raw/update_key.pem). */
        fun publicKey(pem: String): PublicKey {
            val body = pem.lines().filterNot { it.startsWith("-----") }.joinToString("")
            return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(body)))
        }

        private fun signed(manifest: ByteArray, signature: ByteArray, key: PublicKey): Boolean = try {
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(manifest)
                verify(signature)
            }
        } catch (expected: SignatureException) {
            false // Not even a well-formed signature.
        }
    }
}
