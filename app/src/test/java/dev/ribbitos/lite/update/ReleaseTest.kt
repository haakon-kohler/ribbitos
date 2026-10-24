package dev.ribbitos.lite.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.IOException

class ReleaseTest {
    private val apk = byteArrayOf(1, 2, 3)
    private val key = Keys.release.public

    private fun refused(manifest: ByteArray, signature: ByteArray = Keys.sign(manifest)) {
        assertThrows(IOException::class.java) { Release.parse(manifest, signature, key) }
    }

    @Test
    fun aSignedManifestIsRead() {
        val manifest = Keys.manifest(7, apk)
        val r = Release.parse(manifest, Keys.sign(manifest), key)
        assertEquals(7L, r.versionCode)
        assertEquals("ribbitos-lite-v9.apk", r.apk)
        assertEquals(Keys.sha256(apk), r.sha256)
    }

    @Test
    fun aChangedManifestIsRefused() {
        val manifest = Keys.manifest(7, apk)
        refused(Keys.manifest(8, apk), Keys.sign(manifest))
    }

    @Test
    fun anotherKeysSignatureIsRefused() {
        val manifest = Keys.manifest(7, apk)
        refused(manifest, Keys.sign(manifest, Keys.other))
    }

    @Test
    fun garbageForASignatureIsRefused() = refused(Keys.manifest(7, apk), byteArrayOf(0x30, 1, 2))

    @Test
    fun signedGarbageIsRefused() = refused("not json".toByteArray())

    @Test
    fun aManifestMissingWhatItNeedsIsRefused() {
        refused(Keys.manifest(0, apk))
        refused(Keys.manifest(7, apk, name = "../escape.apk"))
        refused(Keys.manifest(7, apk, name = "ribbitos.zip"))
        refused("""{"versionCode":7,"apk":"a.apk","sha256":"abc"}""".toByteArray())
    }

    @Test
    fun theShippedReleaseKeyLoads() {
        val pem = File("src/main/res/raw/update_key.pem").readText()
        assertEquals("EC", Release.publicKey(pem).algorithm)
    }
}
