package io.github.shaigrorb.mylifeuptoolkit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the saved [TaskProfile] list (and their shortcuts) as JSON in SharedPreferences. */
object TaskProfileStore {

    private const val PREFS_NAME = "task_profiles"
    private const val KEY_PROFILES = "profiles"
    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"
    private const val KEY_GID = "gid"
    private const val KEY_SHORTCUTS = "shortcuts"
    private const val KEY_COUNT = "count"

    fun getAll(context: Context): List<TaskProfile> {
        val json = prefs(context).getString(KEY_PROFILES, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i -> parseProfile(array.getJSONObject(i)) }
    }

    fun add(context: Context, profile: TaskProfile) {
        save(context, getAll(context) + profile)
    }

    fun remove(context: Context, profile: TaskProfile) {
        save(context, getAll(context).filterNot { it.id == profile.id })
    }

    fun addShortcut(context: Context, profileId: String, shortcut: TaskShortcut) {
        updateProfile(context, profileId) { it.copy(shortcuts = it.shortcuts + shortcut) }
    }

    fun removeShortcut(context: Context, profileId: String, shortcutId: String) {
        updateProfile(context, profileId) { profile ->
            profile.copy(shortcuts = profile.shortcuts.filterNot { it.id == shortcutId })
        }
    }

    private fun updateProfile(context: Context, profileId: String, transform: (TaskProfile) -> TaskProfile) {
        save(context, getAll(context).map { if (it.id == profileId) transform(it) else it })
    }

    private fun parseProfile(obj: JSONObject): TaskProfile {
        val gid = obj.getLong(KEY_GID)
        val shortcuts = obj.optJSONArray(KEY_SHORTCUTS)
        return TaskProfile(
            label = obj.getString(KEY_LABEL),
            gid = gid,
            shortcuts = if (shortcuts == null) emptyList() else (0 until shortcuts.length()).map { i ->
                parseShortcut(shortcuts.getJSONObject(i))
            },
            id = obj.optString(KEY_ID).ifEmpty { gid.toString() }
        )
    }

    private fun parseShortcut(obj: JSONObject): TaskShortcut {
        val label = obj.getString(KEY_LABEL)
        return TaskShortcut(
            label = label,
            count = obj.getInt(KEY_COUNT),
            id = obj.optString(KEY_ID).ifEmpty { label }
        )
    }

    private fun save(context: Context, profiles: List<TaskProfile>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            val shortcuts = JSONArray()
            profile.shortcuts.forEach { shortcut ->
                shortcuts.put(
                    JSONObject()
                        .put(KEY_ID, shortcut.id)
                        .put(KEY_LABEL, shortcut.label)
                        .put(KEY_COUNT, shortcut.count)
                )
            }
            array.put(
                JSONObject()
                    .put(KEY_ID, profile.id)
                    .put(KEY_LABEL, profile.label)
                    .put(KEY_GID, profile.gid)
                    .put(KEY_SHORTCUTS, shortcuts)
            )
        }
        prefs(context).edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
