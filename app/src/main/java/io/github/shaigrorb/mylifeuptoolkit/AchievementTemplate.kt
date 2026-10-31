package io.github.shaigrorb.mylifeuptoolkit

import java.util.UUID

/** [itemName] is a snapshot for display — LifeUp only needs [itemId]. [amount] is within [AMOUNT_RANGE]. */
data class AchievementItemReward(
    val itemId: Long,
    val itemName: String,
    val amount: Int = 1
) {
    companion object {
        /** LifeUp's documented `item_amount` range. */
        val AMOUNT_RANGE = 1..99

        /** Reads a free-form amount field; blank or out-of-range input falls back into [AMOUNT_RANGE]. */
        fun parseAmount(text: String): Int =
            (text.trim().toIntOrNull() ?: AMOUNT_RANGE.first).coerceIn(AMOUNT_RANGE)
    }
}

data class AchievementTier(
    val id: String = UUID.randomUUID().toString(),
    val nameTemplate: String,
    val descTemplate: String = "",
    val conditionType: Int? = null,
    val relatedIdTemplate: String = "",
    val target: Int? = null,
    val coin: Int? = null,
    val exp: Int? = null,
    val items: List<AchievementItemReward> = emptyList()
)

data class AchievementTemplate(
    val id: String = UUID.randomUUID().toString(),
    val templateName: String,
    val categoryName: String = "",
    val useExistingCategory: Boolean = false,
    val existingCategoryId: String = "",
    val subcategoryName: String = "",
    val skillIds: String = "",
    val useSharedCondition: Boolean = false,
    val sharedConditionType: Int? = null,
    val sharedRelatedIdTemplate: String = "",
    val variables: Map<String, String> = emptyMap(),
    val tiers: List<AchievementTier> = emptyList()
)

/** Simple `{key}` -> value substitution, matching the HTML prototype's regex approach. */
object TemplateSubstitution {
    private val PLACEHOLDER_REGEX = Regex("\\{(\\w+)\\}")

    fun substitute(text: String, variables: Map<String, String>): String {
        if (text.isEmpty()) return text
        return PLACEHOLDER_REGEX.replace(text) { match ->
            variables[match.groupValues[1]] ?: match.value
        }
    }
}
