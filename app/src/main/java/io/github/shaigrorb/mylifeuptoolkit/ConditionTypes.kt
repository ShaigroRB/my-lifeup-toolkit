package io.github.shaigrorb.mylifeuptoolkit

/** One row of LifeUp's achievement unlock-condition type table. [code] is null for "no condition". */
data class ConditionTypeOption(val code: Int?, val label: String, val relatedIdLabel: String? = null)

/** Mirrors the CONDITION_TYPES table from the HTML achievement-template prototype. */
object ConditionTypes {
    val ALL: List<ConditionTypeOption> = listOf(
        ConditionTypeOption(null, "No condition (manual unlock)"),
        ConditionTypeOption(0, "Task completion count", "Task ID"),
        ConditionTypeOption(1, "Task completion streak", "Task ID"),
        ConditionTypeOption(3, "Pomodoro count"),
        ConditionTypeOption(4, "Days using LifeUp"),
        ConditionTypeOption(5, "Like count"),
        ConditionTypeOption(6, "Daily completion streak"),
        ConditionTypeOption(7, "Current coins"),
        ConditionTypeOption(8, "Coins earned in one day"),
        ConditionTypeOption(9, "Task pomodoro count", "Task ID"),
        ConditionTypeOption(10, "Item purchase count", "Item ID"),
        ConditionTypeOption(11, "Item usage count", "Item ID"),
        ConditionTypeOption(12, "Loot box item count", "Item ID"),
        ConditionTypeOption(13, "Skill level reached", "Skill ID"),
        ConditionTypeOption(14, "Life level"),
        ConditionTypeOption(15, "Total items obtained", "Item ID"),
        ConditionTypeOption(16, "Items from synthesis", "Item ID"),
        ConditionTypeOption(17, "Current item quantity", "Item ID"),
        ConditionTypeOption(18, "Task focus duration (minutes)", "Task ID"),
        ConditionTypeOption(19, "ATM savings"),
        ConditionTypeOption(20, "External API")
    )

    fun indexOf(code: Int?): Int = ALL.indexOfFirst { it.code == code }.coerceAtLeast(0)

    /** True for condition codes 0, 1, 9, 18 — the ones whose related ID is a LifeUp task. */
    fun isTaskBased(code: Int?): Boolean = ALL.find { it.code == code }?.relatedIdLabel == "Task ID"
}
