package dev.ribbitos.lite.update

import org.json.JSONObject
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/** A throwaway release key, and manifests signed with it the way tools/release.py signs them. */
object Keys {
    val release: KeyPair = generate()
    val other: KeyPair = generate()

    private fun generate(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun sign(data: ByteArray, key: KeyPair = release): ByteArray = Signature.getInstance("SHA256withECDSA").run {
        initSign(key.private)
        update(data)
        sign()
    }

    fun sha256(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") {
        "%02x".format(it)
    }

    fun manifest(versionCode: Long, apk: ByteArray, name: String = "ribbitos-lite-v9.apk"): ByteArray =
        JSONObject()
            .put("versionCode", versionCode)
            .put("versionName", "9")
            .put("apk", name)
            .put("sha256", sha256(apk))
            .toString()
            .toByteArray()
}
