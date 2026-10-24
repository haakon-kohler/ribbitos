package dev.ribbitos.lite.update

import android.content.Context
import dev.ribbitos.lite.Limits
import dev.ribbitos.lite.Settings
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.HashingSink
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.security.PublicKey
import java.util.concurrent.TimeUnit

/**
 * Where updates come from: a folder URL holding `update.json`, its signature `update.json.sig`, and
 * the APK it names. By default the latest GitHub release ([DEFAULT]); over adb, any other.
 */
class Feed(
    private val base: String,
    private val key: PublicKey,
    private val maxApkBytes: Long = Limits.MAX_UPDATE_APK_BYTES,
    private val client: OkHttpClient = CLIENT,
) {
    init {
        requireNotNull(base.toHttpUrlOrNull()) { "Not an update feed URL: $base" }
    }

    /** The newest release on the feed, signature checked. */
    @Throws(IOException::class)
    fun latest(): Release = Release.parse(fetch(MANIFEST), fetch("$MANIFEST.sig"), key)

    /** Downloads [release]'s APK to [to] and checks its SHA-256; leaves nothing behind on failure. */
    @Throws(IOException::class)
    fun download(release: Release, to: File) {
        try {
            val hash = call(release.apk) { body ->
                HashingSink.sha256(to.sink()).use { sink ->
                    copy(body, sink.buffer())
                    sink.hash.hex()
                }
            }
            if (hash != release.sha256) throw IOException("${release.apk} doesn't match update.json")
        } catch (e: IOException) {
            to.delete()
            throw e
        }
    }

    private fun fetch(name: String): ByteArray = call(name) { body ->
        val source = body.source()
        if (source.request(Limits.MAX_UPDATE_MANIFEST_BYTES + 1)) throw IOException("$name is too large")
        source.buffer.readByteArray()
    }

    private fun <T> call(name: String, read: (ResponseBody) -> T): T {
        val url = base.trimEnd('/') + "/" + name
        client.newCall(Request.Builder().url(url).build()).execute().use { resp: Response ->
            if (!resp.isSuccessful) throw IOException("$name: HTTP ${resp.code}")
            return read(resp.body ?: throw IOException("$name: empty response"))
        }
    }

    /** Copies at most [maxApkBytes]; a bigger download fails. */
    private fun copy(body: ResponseBody, sink: okio.BufferedSink) {
        val source = body.source()
        val chunk = Buffer()
        var total = 0L
        var read = source.read(chunk, CHUNK_BYTES)
        while (read != -1L) {
            total += read
            if (total > maxApkBytes) throw IOException("Update larger than $maxApkBytes bytes")
            sink.write(chunk, read)
            read = source.read(chunk, CHUNK_BYTES)
        }
        sink.flush()
    }

    companion object {
        /** This repository's latest GitHub release. A fork points it at its own (docs/UPDATES.md). */
        const val DEFAULT = "https://github.com/haakon-kohler/ribbitos/releases/latest/download/"

        /** The settings key for another feed, set over adb (ConfigReceiver). */
        const val SETTING = "update_feed"
        private const val MANIFEST = "update.json"
        private const val CHUNK_BYTES = 64 * 1024L
        private const val CONNECT_TIMEOUT_S = 15L
        private const val READ_TIMEOUT_S = 60L

        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        /** The feed this R1 updates from. */
        fun url(ctx: Context): String =
            Settings.prefs(ctx).getString(SETTING, null)?.takeIf { it.isNotBlank() } ?: DEFAULT

        /** Points this R1 at another feed; blank goes back to [DEFAULT]. */
        fun set(ctx: Context, url: String) {
            Settings.prefs(ctx).edit().putString(SETTING, url.trim()).apply()
        }
    }
}
