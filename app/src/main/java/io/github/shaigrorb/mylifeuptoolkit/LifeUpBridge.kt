package io.github.shaigrorb.mylifeuptoolkit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle

object LifeUpBridge {

    private const val PACKAGE_NAME = "net.sarasarasa.lifeup"
    private val PROVIDER_URI: Uri = Uri.parse("content://net.sarasarasa.lifeup.provider.api/")

    class LifeUpCallException(val errorCode: String?, message: String?) : Exception(message)

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launches LifeUp's own permission screen. Call this once (e.g. a "Grant access"
     * button) before any [call] will succeed. The user approves inside LifeUp.
     */
    fun requestContentProviderPermission(context: Context, appName: String) {
        val url = "lifeup://api/request_permission" +
                "?request_content_provider=true" +
                "&app_name=${Uri.encode(appName)}" +
                "&package_name=${Uri.encode(context.packageName)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Raw call — [method] is the endpoint name (e.g. "complete"),
     * [arg] is the query-string-style argument (e.g. "gid=1234&ui=false").
     * Throws [LifeUpCallException] if LifeUp reports an error.
     */
    fun call(context: Context, method: String, arg: String): Bundle? {
        val result = context.contentResolver.call(PROVIDER_URI, method, arg, null)
        val errorCode = result?.getString("error_code")
        if (errorCode != null) {
            throw LifeUpCallException(errorCode, result.getString("error_message"))
        }
        return result
    }

    /** Convenience wrapper for the Bulk Complete screen. */
    fun completeTask(context: Context, gid: Long): Result<Unit> {
        return try {
            call(context, "complete", "gid=$gid&ui=false")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}