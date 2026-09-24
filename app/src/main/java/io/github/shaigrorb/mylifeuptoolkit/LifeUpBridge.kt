package io.github.shaigrorb.mylifeuptoolkit

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

object LifeUpBridge {

    private const val PACKAGE_NAME = "net.sarasarasa.lifeup"
    private val PROVIDER_URI: Uri = Uri.parse("content://net.sarasarasa.lifeup.provider.api/")
    private val TASKS_URI: Uri = Uri.parse("content://net.sarasarasa.lifeup.provider.api/tasks")
    private val ACHIEVEMENT_CATEGORIES_URI: Uri =
        Uri.parse("content://net.sarasarasa.lifeup.provider.api/achievement_categories")

    class LifeUpCallException(val errorCode: String?, message: String?) : Exception(message)

    /**
     * [gid] is the task's stable group id — what the `complete` API and bulk-complete profiles use.
     * [id] is the task's own row id — what achievement conditions' `related_id` expects for
     * task-based condition types (0, 1). Null when the provider didn't return one.
     */
    data class LifeUpTask(val gid: Long, val name: String, val id: Long?)

    data class LifeUpAchievementCategory(val id: Long, val name: String)

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

    /**
     * Lists LifeUp's tasks via its read-only tasks ContentProvider query
     * (a plain [android.content.ContentResolver.query], not [call]).
     * Run off the main thread — this is a cross-process IPC call.
     */
    fun listTasks(context: Context): Result<List<LifeUpTask>> {
        return try {
            val tasks = mutableListOf<LifeUpTask>()
            queryRows(context, TASKS_URI) { cursor ->
                val gidIndex = cursor.getColumnIndex("_GID")
                val idIndex = cursor.getColumnIndex("_ID")
                val nameIndex = cursor.getColumnIndex("name")
                do {
                    val gid = if (gidIndex != -1) cursor.getLong(gidIndex) else null
                    val id = if (idIndex != -1) cursor.getLong(idIndex) else null
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    if (gid != null && name != null) {
                        tasks.add(LifeUpTask(gid, name, id))
                    }
                } while (cursor.moveToNext())
            }
            Result.success(tasks.sortedBy { it.name.lowercase() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lists LifeUp's achievement categories via the read-only `achievement_categories`
     * ContentProvider query (columns `_ID`/`name`, per LifeUp-SDK's `AchievementApi.listCategories`).
     * Run off the main thread — this is a cross-process IPC call.
     */
    fun listAchievementCategories(context: Context): Result<List<LifeUpAchievementCategory>> {
        return try {
            val categories = mutableListOf<LifeUpAchievementCategory>()
            queryRows(context, ACHIEVEMENT_CATEGORIES_URI) { cursor ->
                val idIndex = cursor.getColumnIndex("_ID")
                val nameIndex = cursor.getColumnIndex("name")
                do {
                    val id = if (idIndex != -1) cursor.getLong(idIndex) else null
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                    if (id != null && name != null) {
                        categories.add(LifeUpAchievementCategory(id, name))
                    }
                } while (cursor.moveToNext())
            }
            Result.success(categories.sortedBy { it.name.lowercase() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries [uri] and hands the cursor, already positioned on the first row, to [readRows].
     * LifeUp reports errors as a single row with an `error_code` column — thrown here as
     * [LifeUpCallException]. [readRows] isn't called for an empty result.
     */
    private fun queryRows(context: Context, uri: Uri, readRows: (Cursor) -> Unit) {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return
            val errorCodeIndex = cursor.getColumnIndex("error_code")
            if (cursor.count == 1 && errorCodeIndex != -1) {
                val errorMessageIndex = cursor.getColumnIndex("error_message")
                throw LifeUpCallException(
                    cursor.getString(errorCodeIndex),
                    if (errorMessageIndex != -1) cursor.getString(errorMessageIndex) else null
                )
            }
            readRows(cursor)
        }
    }

    /** Creates an achievement category and returns its id, read straight off the response Bundle. */
    fun createCategory(context: Context, name: String): Result<Long> {
        return try {
            val arg = "type=achievements&name=${Uri.encode(name)}"
            Result.success(extractId(call(context, "category", arg), "category"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a subcategory (a name-only grouping row) inside [categoryId] and returns its id.
     * Subcategories are achievement rows with `is_subcategory=true` — see LifeUp's achievement API.
     */
    fun createSubcategory(context: Context, categoryId: Long, name: String): Result<Long> {
        return try {
            val arg = "name=${Uri.encode(name)}&category_id=$categoryId&is_subcategory=true"
            Result.success(extractId(call(context, "achievement", arg), "achievement"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Creates an achievement in [categoryId] and returns its id, read straight off the response Bundle. */
    fun createAchievement(
        context: Context,
        categoryId: Long,
        name: String,
        desc: String?,
        conditionType: Int?,
        relatedId: Long?,
        target: Int?,
        coin: Int?,
        exp: Int?,
        skillIds: List<Long>?
    ): Result<Long> {
        return try {
            val params = StringBuilder()
            fun append(key: String, value: String) {
                if (params.isNotEmpty()) params.append('&')
                params.append(key).append('=').append(Uri.encode(value))
            }

            append("name", name)
            if (!desc.isNullOrBlank()) append("desc", desc)
            append("category_id", categoryId.toString())
            if (conditionType != null) {
                val condition = JSONObject().put("type", conditionType)
                if (relatedId != null) condition.put("related_id", relatedId)
                if (target != null) condition.put("target", target)
                append("conditions_json", JSONArray().put(condition).toString())
            }
            if (coin != null) append("coin", coin.toString())
            if (exp != null) append("exp", exp.toString())
            skillIds?.forEach { append("skills", it.toString()) }

            Result.success(extractId(call(context, "achievement", params.toString()), "achievement"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads the "id" field LifeUp returns from category/achievement creation calls.
     * The docs only promise "Number", so the Bundle's actual type isn't guaranteed —
     * try a Long first and fall back to an Int.
     */
    private fun extractId(result: Bundle?, method: String): Long {
        if (result == null || !result.containsKey("id")) {
            throw LifeUpCallException(null, "LifeUp did not return an id for \"$method\"")
        }
        return try {
            result.getLong("id")
        } catch (e: ClassCastException) {
            result.getInt("id").toLong()
        }
    }
}