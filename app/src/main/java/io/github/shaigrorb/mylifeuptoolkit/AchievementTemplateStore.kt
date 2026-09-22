package io.github.shaigrorb.mylifeuptoolkit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persists the saved [AchievementTemplate] list as JSON in SharedPreferences, same pattern as [TaskProfileStore]. */
object AchievementTemplateStore {

    private const val PREFS_NAME = "achievement_templates"
    private const val KEY_TEMPLATES = "templates"
    private const val KEY_ID = "id"
    private const val KEY_TEMPLATE_NAME = "templateName"
    private const val KEY_CATEGORY_NAME = "categoryName"
    private const val KEY_USE_EXISTING_CATEGORY = "useExistingCategory"
    private const val KEY_EXISTING_CATEGORY_ID = "existingCategoryId"
    private const val KEY_SUBCATEGORY_NAME = "subcategoryName"
    private const val KEY_SKILL_IDS = "skillIds"
    private const val KEY_USE_SHARED_CONDITION = "useSharedCondition"
    private const val KEY_SHARED_CONDITION_TYPE = "sharedConditionType"
    private const val KEY_SHARED_RELATED_ID_TEMPLATE = "sharedRelatedIdTemplate"
    private const val KEY_VARIABLES = "variables"
    private const val KEY_TIERS = "tiers"
    private const val KEY_VAR_KEY = "key"
    private const val KEY_VAR_VALUE = "value"
    private const val KEY_NAME_TEMPLATE = "nameTemplate"
    private const val KEY_DESC_TEMPLATE = "descTemplate"
    private const val KEY_CONDITION_TYPE = "conditionType"
    private const val KEY_RELATED_ID_TEMPLATE = "relatedIdTemplate"
    private const val KEY_TARGET = "target"
    private const val KEY_COIN = "coin"
    private const val KEY_EXP = "exp"

    fun getAll(context: Context): List<AchievementTemplate> {
        val json = prefs(context).getString(KEY_TEMPLATES, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i -> parseTemplate(array.getJSONObject(i)) }
    }

    fun findByName(context: Context, templateName: String): AchievementTemplate? {
        return getAll(context).find { it.templateName == templateName }
    }

    /** Inserts, or overwrites the existing template with the same [AchievementTemplate.templateName]. */
    fun save(context: Context, template: AchievementTemplate) {
        val existing = getAll(context)
        val replaced = existing.filterNot { it.templateName == template.templateName } + template
        saveAll(context, replaced)
    }

    fun delete(context: Context, templateName: String) {
        saveAll(context, getAll(context).filterNot { it.templateName == templateName })
    }

    private fun parseTemplate(obj: JSONObject): AchievementTemplate {
        val variables = obj.optJSONObject(KEY_VARIABLES)
        val variablesMap = mutableMapOf<String, String>()
        variables?.keys()?.forEach { key -> variablesMap[key] = variables.getString(key) }

        val tiers = obj.optJSONArray(KEY_TIERS)
        return AchievementTemplate(
            id = obj.optString(KEY_ID).ifEmpty { java.util.UUID.randomUUID().toString() },
            templateName = obj.getString(KEY_TEMPLATE_NAME),
            categoryName = obj.optString(KEY_CATEGORY_NAME),
            useExistingCategory = obj.optBoolean(KEY_USE_EXISTING_CATEGORY, false),
            existingCategoryId = obj.optString(KEY_EXISTING_CATEGORY_ID),
            subcategoryName = obj.optString(KEY_SUBCATEGORY_NAME),
            skillIds = obj.optString(KEY_SKILL_IDS),
            useSharedCondition = obj.optBoolean(KEY_USE_SHARED_CONDITION, false),
            sharedConditionType = if (obj.isNull(KEY_SHARED_CONDITION_TYPE)) null else obj.optInt(KEY_SHARED_CONDITION_TYPE),
            sharedRelatedIdTemplate = obj.optString(KEY_SHARED_RELATED_ID_TEMPLATE),
            variables = variablesMap,
            tiers = if (tiers == null) emptyList() else (0 until tiers.length()).map { i ->
                parseTier(tiers.getJSONObject(i))
            }
        )
    }

    private fun parseTier(obj: JSONObject): AchievementTier {
        return AchievementTier(
            id = obj.optString(KEY_ID).ifEmpty { java.util.UUID.randomUUID().toString() },
            nameTemplate = obj.getString(KEY_NAME_TEMPLATE),
            descTemplate = obj.optString(KEY_DESC_TEMPLATE),
            conditionType = if (obj.isNull(KEY_CONDITION_TYPE)) null else obj.optInt(KEY_CONDITION_TYPE),
            relatedIdTemplate = obj.optString(KEY_RELATED_ID_TEMPLATE),
            target = if (obj.isNull(KEY_TARGET)) null else obj.optInt(KEY_TARGET),
            coin = if (obj.isNull(KEY_COIN)) null else obj.optInt(KEY_COIN),
            exp = if (obj.isNull(KEY_EXP)) null else obj.optInt(KEY_EXP)
        )
    }

    private fun saveAll(context: Context, templates: List<AchievementTemplate>) {
        val array = JSONArray()
        templates.forEach { template ->
            val variables = JSONObject()
            template.variables.forEach { (key, value) -> variables.put(key, value) }

            val tiers = JSONArray()
            template.tiers.forEach { tier ->
                tiers.put(
                    JSONObject()
                        .put(KEY_ID, tier.id)
                        .put(KEY_NAME_TEMPLATE, tier.nameTemplate)
                        .put(KEY_DESC_TEMPLATE, tier.descTemplate)
                        .put(KEY_CONDITION_TYPE, tier.conditionType ?: JSONObject.NULL)
                        .put(KEY_RELATED_ID_TEMPLATE, tier.relatedIdTemplate)
                        .put(KEY_TARGET, tier.target ?: JSONObject.NULL)
                        .put(KEY_COIN, tier.coin ?: JSONObject.NULL)
                        .put(KEY_EXP, tier.exp ?: JSONObject.NULL)
                )
            }

            array.put(
                JSONObject()
                    .put(KEY_ID, template.id)
                    .put(KEY_TEMPLATE_NAME, template.templateName)
                    .put(KEY_CATEGORY_NAME, template.categoryName)
                    .put(KEY_USE_EXISTING_CATEGORY, template.useExistingCategory)
                    .put(KEY_EXISTING_CATEGORY_ID, template.existingCategoryId)
                    .put(KEY_SUBCATEGORY_NAME, template.subcategoryName)
                    .put(KEY_SKILL_IDS, template.skillIds)
                    .put(KEY_USE_SHARED_CONDITION, template.useSharedCondition)
                    .put(KEY_SHARED_CONDITION_TYPE, template.sharedConditionType ?: JSONObject.NULL)
                    .put(KEY_SHARED_RELATED_ID_TEMPLATE, template.sharedRelatedIdTemplate)
                    .put(KEY_VARIABLES, variables)
                    .put(KEY_TIERS, tiers)
            )
        }
        prefs(context).edit().putString(KEY_TEMPLATES, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
