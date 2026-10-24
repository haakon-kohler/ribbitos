package dev.ribbitos.lite.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.ribbitos.lite.R
import java.io.IOException
import java.security.PublicKey

/**
 * ribbitOS updates itself: every [PERIOD_MS] on Wi-Fi it checks its [Feed], and Android runs the
 * check only once the R1 has been left alone with its screen off (JobScheduler's idle), so an
 * update never interrupts a chat. docs/UPDATES.md has the whole story.
 */
class UpdateJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread({
            try {
                val key = releaseKey()
                val installed = packageManager.getPackageInfo(packageName, 0).longVersionCode
                val updater = Updater(Feed(Feed.url(this), key), installed, cacheDir) { Installer.install(this, it) }
                val outcome = updater.run()
                if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "Update check: $outcome")
            } catch (e: IOException) {
                Log.w(TAG, "Update check failed: ${e.message}")
            }
            jobFinished(params, false)
        }, "ribbit-update").start()
        return true
    }

    private fun releaseKey(): PublicKey =
        Release.publicKey(resources.openRawResource(R.raw.update_key).bufferedReader().use { it.readText() })

    // Stopped part way: the download goes to waste and the next run starts over.
    override fun onStopJob(params: JobParameters) = true

    companion object {
        const val ID = 1
        private const val TAG = "ribbitos"
        private const val PERIOD_MS = 6 * 60 * 60 * 1000L

        /** Sets the check going, unless it already is. Never fails: the home screen calls it. */
        fun schedule(ctx: Context) {
            val jobs = ctx.getSystemService(JobScheduler::class.java)
            if (jobs.getPendingJob(ID) != null) return
            val job = JobInfo.Builder(ID, ComponentName(ctx, UpdateJob::class.java))
                .setPeriodic(PERIOD_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                .setRequiresDeviceIdle(true)
                .setPersisted(true)
                .build()
            try {
                jobs.schedule(job)
            } catch (e: SecurityException) {
                Log.w(TAG, "No update checks: ${e.message}") // A permission missing from the manifest.
            }
        }
    }
}
