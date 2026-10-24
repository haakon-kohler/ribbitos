package dev.ribbitos.lite.update

import dev.ribbitos.lite.Limits
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The update feed and the updater, against a fake release folder. */
class FeedTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private val apk = ByteArray(100_000) { it.toByte() }
    private val files = HashMap<String, ByteArray>()

    @Before
    fun start() {
        publish(Keys.manifest(5, apk))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val name = request.path.orEmpty().substringAfterLast('/')
                val body = files[name] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
        server.start()
    }

    @After
    fun stop() = server.shutdown()

    private fun publish(manifest: ByteArray, signature: ByteArray = Keys.sign(manifest)) {
        files["update.json"] = manifest
        files["update.json.sig"] = signature
        files["ribbitos-lite-v9.apk"] = apk
    }

    private fun feed(maxApkBytes: Long = Limits.MAX_UPDATE_APK_BYTES) =
        Feed(server.url("/releases/latest/download/").toString(), Keys.release.public, maxApkBytes)

    private val target: File get() = File(tmp.root, "update.apk")

    @Test
    fun latestIsTheSignedRelease() = assertEquals(5L, feed().latest().versionCode)

    @Test
    fun noSignatureNoRelease() {
        files.remove("update.json.sig")
        assertEquals("update.json.sig: HTTP 404", assertThrows(IOException::class.java) { feed().latest() }.message)
    }

    @Test
    fun anOversizedManifestIsRefused() {
        publish(ByteArray(Limits.MAX_UPDATE_MANIFEST_BYTES.toInt() + 1))
        assertEquals("update.json is too large", assertThrows(IOException::class.java) { feed().latest() }.message)
    }

    @Test
    fun theDownloadIsCheckedAgainstTheManifest() {
        feed().download(feed().latest(), target)
        assertArrayEquals(apk, target.readBytes())
    }

    @Test
    fun aMismatchedDownloadLeavesNothingBehind() {
        val release = feed().latest()
        files["ribbitos-lite-v9.apk"] = byteArrayOf(9)
        assertThrows(IOException::class.java) { feed().download(release, target) }
        assertFalse(target.exists())
    }

    @Test
    fun anOversizedDownloadStops() {
        val release = feed().latest()
        assertThrows(IOException::class.java) { feed(maxApkBytes = 1_000).download(release, target) }
        assertFalse(target.exists())
    }

    @Test
    fun aFeedMustBeAUrl() {
        assertThrows(IllegalArgumentException::class.java) { Feed("not a url", Keys.release.public) }
    }

    @Test
    fun aNewerReleaseIsDownloadedAndInstalled() {
        var installed: ByteArray? = null
        val outcome = Updater(feed(), 4, tmp.root) { installed = it.readBytes() }.run()
        assertEquals(Updater.Outcome.INSTALLING, outcome)
        assertArrayEquals(apk, installed)
    }

    @Test
    fun theSameOrAnOlderReleaseIsLeftAlone() {
        var installed: File? = null
        assertEquals(Updater.Outcome.UP_TO_DATE, Updater(feed(), 5, tmp.root) { installed = it }.run())
        assertEquals(Updater.Outcome.UP_TO_DATE, Updater(feed(), 6, tmp.root) { installed = it }.run())
        assertNull(installed)
    }
}
