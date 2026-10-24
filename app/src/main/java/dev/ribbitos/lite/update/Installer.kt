package dev.ribbitos.lite.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import dev.ribbitos.lite.HomeActivity
import java.io.File

/**
 * Installs a downloaded update over ribbitOS with Android's PackageInstaller. As a platform app
 * (keys/README.md) ribbitOS holds INSTALL_PACKAGES, so nobody has to confirm; Android still refuses
 * an APK signed with another key or older than the one installed. System-facing, so tested on the R1.
 */
object Installer {
    private const val INSTALL_PACKAGES = "android.permission.INSTALL_PACKAGES"
    private const val TAG = "ribbitos"

    fun install(ctx: Context, apk: File) {
        if (ctx.checkSelfPermission(INSTALL_PACKAGES) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Not a platform app here: install updates by hand (docs/UPDATES.md)")
            apk.delete()
            return
        }
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        installer.openSession(installer.createSession(params)).use { session ->
            write(session, apk)
            apk.delete()
            val done = Intent(ctx, Result::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            session.commit(PendingIntent.getBroadcast(ctx, 0, done, flags).intentSender)
        }
    }

    private fun write(session: PackageInstaller.Session, apk: File) {
        session.openWrite("ribbitos.apk", 0, apk.length()).use { out ->
            apk.inputStream().use { it.copyTo(out) }
            session.fsync(out)
        }
    }

    /**
     * After an update Android restarts ribbitOS, but whatever task was behind it (Android's
     * settings, say) would come to the front first: go back to the home screen instead.
     */
    class Replaced : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
            ctx.startActivity(Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Hears how an install went. Success restarts ribbitOS, so mostly failures arrive here. */
    class Result : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
            if (Log.isLoggable(TAG, Log.INFO)) Log.i(TAG, "Update install: status $status $message")
        }
    }
}
