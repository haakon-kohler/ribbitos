package dev.ribbitos.lite.update

import java.io.File
import java.io.IOException

/** One update check: if [feed] has a build newer than [installed], fetch it and hand it to [install]. */
class Updater(
    private val feed: Feed,
    private val installed: Long,
    private val cache: File,
    private val install: (File) -> Unit,
) {
    /** What a check did. */
    enum class Outcome { UP_TO_DATE, INSTALLING }

    @Throws(IOException::class)
    fun run(): Outcome {
        val release = feed.latest()
        if (release.versionCode <= installed) return Outcome.UP_TO_DATE
        val apk = File(cache, APK_FILE)
        feed.download(release, apk)
        install(apk)
        return Outcome.INSTALLING
    }

    private companion object {
        const val APK_FILE = "update.apk"
    }
}
