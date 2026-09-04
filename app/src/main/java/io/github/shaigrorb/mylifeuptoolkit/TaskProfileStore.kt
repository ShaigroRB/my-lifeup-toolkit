package io.github.shaigrorb.mylifeuptoolkit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the saved [TaskProfile] list as JSON in SharedPreferences. */
object TaskProfileStore {

    private const val PREFS_NAME = "task_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_LABEL = "label"
    private const val KEY_GID = "gid"

    fun getAll(context: Context): List<TaskProfile> {
        val json = prefs(context).getString(KEY_PROFILES, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            TaskProfile(label = obj.getString(KEY_LABEL), gid = obj.getLong(KEY_GID))
        }
    }

    fun add(context: Context, profile: TaskProfile) {
        save(context, getAll(context) + profile)
    }

    fun remove(context: Context, profile: TaskProfile) {
        save(context, getAll(context).filterNot { it == profile })
    }

    private fun save(context: Context, profiles: List<TaskProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject()
                    .put(KEY_LABEL, profile.label)
                    .put(KEY_GID, profile.gid)
            )
        }
        prefs(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
