package io.github.shaigrorb.mylifeuptoolkit

import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doOnTextChanged
import java.util.UUID

import io.github.shaigrorb.mylifeuptoolkit.databinding.ActivityAchievementTemplateBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.DialogProgressBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemAchievementTierBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemSavedTemplateBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemTierItemRewardBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemTemplateVariableBinding

class AchievementTemplateActivity : AppCompatActivity() {

    /** In-memory editable row — separate from the persisted [AchievementTier] so text fields stay free-form while typing. */
    private data class VariableRow(val id: String = UUID.randomUUID().toString(), var key: String, var value: String)

    private data class ItemRewardRow(val itemId: Long, val itemName: String, var amount: String)

    private data class TierRow(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        var desc: String,
        var conditionType: Int?,
        var relatedId: String,
        var target: String,
        var coin: String,
        var exp: String,
        val items: MutableList<ItemRewardRow> = mutableListOf()
    )

    /**
     * Recap line severity, most severe first. [glyph] is drawn in a [textColor] circle badge,
     * [textColor] also colors the line, [borderColor] colors the box when it's the worst one.
     */
    private enum class RecapSeverity(val glyph: String, val textColor: Int, val borderColor: Int) {
        ERROR("✕", 0xFFB3261E.toInt(), 0xFFE57373.toInt()),
        WARNING("!", 0xFF9A5B00.toInt(), 0xFFFFB74D.toInt()),
        INFO("i", 0xFF666B60.toInt(), 0xFFE0E0E0.toInt())
    }

    /**
     * Draws [glyph] in white inside a filled [color] circle, in place of one character.
     * Drawn rather than taken from a font, so it looks the same on every device.
     */
    private class BadgeSpan(private val glyph: String, private val color: Int) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            width(paint.textSize)

        override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            val radius = paint.textSize * 0.55f
            val centerX = x + radius
            val centerY = y + (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
            val circlePaint = Paint(paint).apply { color = this@BadgeSpan.color; style = Paint.Style.FILL; isAntiAlias = true }
            canvas.drawCircle(centerX, centerY, radius, circlePaint)

            val glyphPaint = Paint(paint).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                textSize = paint.textSize * 0.8f
                isAntiAlias = true
            }
            canvas.drawText(glyph, centerX, centerY - (glyphPaint.fontMetrics.ascent + glyphPaint.fontMetrics.descent) / 2, glyphPaint)
        }

        companion object {
            /** Badge diameter plus a gap before the text. */
            fun width(textSize: Float): Int = (textSize * 1.5f).toInt()
        }
    }

    private data class RecapLine(val severity: RecapSeverity, val text: String)

    private lateinit var binding: ActivityAchievementTemplateBinding
    private val variables = mutableListOf<VariableRow>()
    private val tiers = mutableListOf<TierRow>()
    private var loadedTemplateId: String? = null

    /** True while [loadBlank]/[loadTemplate] fill the form, so setting the shared toggle doesn't sync tiers. */
    private var loadingForm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementTemplateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Achievement Templates"

        binding.addVariableButton.setOnClickListener {
            variables.add(VariableRow(key = "", value = ""))
            renderVariables()
        }
        binding.addTierButton.setOnClickListener {
            tiers.add(TierRow(name = "", desc = "", conditionType = null, relatedId = "", target = "", coin = "", exp = ""))
            renderTiers()
        }
        binding.saveTemplateButton.setOnClickListener { saveCurrentAsTemplate() }
        binding.newTemplateButton.setOnClickListener { loadBlank() }
        binding.generateButton.setOnClickListener { onGenerateClicked() }
        binding.categoryModeGroup.setOnCheckedChangeListener { _, checkedId ->
            applyCategoryModeVisibility(checkedId == binding.existingCategoryRadio.id)
            refreshRecap()
        }
        setupSharedConditionSpinner()
        binding.sharedConditionToggle.setOnCheckedChangeListener { _, isChecked ->
            if (!loadingForm) {
                if (isChecked) copyTierConditionToShared() else copySharedConditionToTiers()
            }
            applySharedConditionState(isChecked)
            renderTiers()
        }
        binding.pickCategoryButton.setOnClickListener { showCategoryPicker() }
        binding.pickSkillsButton.setOnClickListener { showSkillPicker(binding.skillIdsInput, multiSelect = true) }
        binding.skillIdsInput.setOnClickListener { showSkillPicker(binding.skillIdsInput, multiSelect = true) }
        listOf(
            binding.skillIdsInput,
            binding.categoryNameInput,
            binding.existingCategoryIdInput,
            binding.subcategoryNameInput,
            binding.sharedRelatedIdInput
        ).forEach { input -> input.doOnTextChanged { _, _, _, _ -> refreshRecap() } }

        loadBlank()
        renderSavedTemplateList()
    }

    private fun loadBlank() {
        loadingForm = true
        binding.templateNameInput.setText("")
        binding.categoryNameInput.setText("")
        binding.existingCategoryIdInput.setText("")
        binding.subcategoryNameInput.setText("")
        binding.newCategoryRadio.isChecked = true
        applyCategoryModeVisibility(useExisting = false)
        binding.skillIdsInput.setText("")
        binding.sharedConditionToggle.isChecked = false
        binding.sharedConditionSpinner.setSelection(ConditionTypes.indexOf(null))
        binding.sharedRelatedIdInput.setText("")
        applySharedRelatedIdState(null)
        applySharedConditionState(false)
        loadedTemplateId = null

        variables.clear()
        variables.add(VariableRow(key = "name", value = "Reading"))
        variables.add(VariableRow(key = "something", value = "Discipline"))

        tiers.clear()
        tiers.add(TierRow(name = "{name} Rookie", desc = "", conditionType = 0, relatedId = "", target = "10", coin = "", exp = ""))
        tiers.add(TierRow(name = "{name} Adept", desc = "", conditionType = 0, relatedId = "", target = "50", coin = "", exp = ""))
        tiers.add(TierRow(name = "{name} Champion", desc = "", conditionType = 0, relatedId = "", target = "200", coin = "", exp = ""))
        tiers.add(TierRow(name = "Master of {something}", desc = "", conditionType = 0, relatedId = "", target = "500", coin = "", exp = ""))

        renderVariables()
        renderTiers()
        loadingForm = false
    }

    private fun loadTemplate(template: AchievementTemplate) {
        loadingForm = true
        binding.templateNameInput.setText(template.templateName)
        binding.categoryNameInput.setText(template.categoryName)
        binding.existingCategoryIdInput.setText(template.existingCategoryId)
        binding.subcategoryNameInput.setText(template.subcategoryName)
        binding.newCategoryRadio.isChecked = !template.useExistingCategory
        binding.existingCategoryRadio.isChecked = template.useExistingCategory
        applyCategoryModeVisibility(template.useExistingCategory)
        binding.skillIdsInput.setText(template.skillIds)
        binding.sharedConditionToggle.isChecked = template.useSharedCondition
        binding.sharedConditionSpinner.setSelection(ConditionTypes.indexOf(template.sharedConditionType))
        binding.sharedRelatedIdInput.setText(template.sharedRelatedIdTemplate)
        applySharedRelatedIdState(template.sharedConditionType)
        applySharedConditionState(template.useSharedCondition)
        loadedTemplateId = template.id

        variables.clear()
        variables.addAll(template.variables.map { (key, value) -> VariableRow(key = key, value = value) })

        tiers.clear()
        tiers.addAll(
            template.tiers.map {
                TierRow(
                    id = it.id,
                    name = it.nameTemplate,
                    desc = it.descTemplate,
                    conditionType = it.conditionType,
                    relatedId = it.relatedIdTemplate,
                    target = it.target?.toString() ?: "",
                    coin = it.coin?.toString() ?: "",
                    exp = it.exp?.toString() ?: "",
                    items = it.items.map { item ->
                        ItemRewardRow(item.itemId, item.itemName, item.amount.toString())
                    }.toMutableList()
                )
            }
        )

        renderVariables()
        renderTiers()
        loadingForm = false
    }

    private fun applyCategoryModeVisibility(useExisting: Boolean) {
        binding.newCategoryGroup.visibility = if (useExisting) View.GONE else View.VISIBLE
        binding.existingCategoryGroup.visibility = if (useExisting) View.VISIBLE else View.GONE
    }

    private fun setupSharedConditionSpinner() {
        val conditionLabels = ConditionTypes.ALL.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, conditionLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sharedConditionSpinner.adapter = adapter
        binding.sharedConditionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applySharedRelatedIdState(ConditionTypes.ALL[position].code)
                updateTierSharedConditionNotes()
                refreshRecap()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Toggling shared on: the shared condition starts from tier 1's, so nothing visibly changes when tiers already agree. */
    private fun copyTierConditionToShared() {
        val first = tiers.firstOrNull() ?: return
        binding.sharedConditionSpinner.setSelection(ConditionTypes.indexOf(first.conditionType))
        binding.sharedRelatedIdInput.setText(first.relatedId)
        applySharedRelatedIdState(first.conditionType)

        val distinctConditions = tiers.map {
            it.conditionType to (if (ConditionTypes.hasRelatedId(it.conditionType)) it.relatedId.trim() else "")
        }.distinct()
        if (distinctConditions.size > 1) {
            Toast.makeText(this, "Tiers had different conditions — shared condition uses tier 1's", Toast.LENGTH_LONG).show()
        }
    }

    /** Toggling shared off: every tier takes the shared condition, so what was shown is what tiers keep. */
    private fun copySharedConditionToTiers() {
        val sharedConditionType = ConditionTypes.ALL[binding.sharedConditionSpinner.selectedItemPosition].code
        val sharedRelatedId = binding.sharedRelatedIdInput.text?.toString().orEmpty()
        tiers.forEach {
            it.conditionType = sharedConditionType
            it.relatedId = sharedRelatedId
        }
    }

    private fun applySharedConditionState(enabled: Boolean) {
        binding.sharedConditionGroup.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun applySharedRelatedIdState(conditionType: Int?) {
        val relatedLabel = ConditionTypes.ALL.find { it.code == conditionType }?.relatedIdLabel
        val relatedVisibility = if (relatedLabel != null) View.VISIBLE else View.GONE
        binding.sharedRelatedIdLabel.visibility = relatedVisibility
        binding.sharedRelatedIdInput.visibility = relatedVisibility
        binding.sharedRelatedIdLabel.text = relatedLabel
        binding.sharedRelatedIdInput.hint = ConditionTypes.relatedIdHint(conditionType)
        applyPickRelatedButtonState(binding.sharedPickRelatedButton, binding.sharedRelatedIdInput, conditionType)
    }

    private fun renderSavedTemplateList() {
        binding.savedTemplateListContainer.removeAllViews()
        val templates = AchievementTemplateStore.getAll(this)
        binding.noSavedTemplatesText.visibility = if (templates.isEmpty()) View.VISIBLE else View.GONE

        templates.forEach { template ->
            val row = ItemSavedTemplateBinding.inflate(layoutInflater, binding.savedTemplateListContainer, false)
            row.savedTemplateName.text = template.templateName
            row.savedTemplateName.setOnClickListener { loadTemplate(template) }
            row.deleteSavedTemplateButton.setOnClickListener { confirmDeleteTemplate(template) }
            binding.savedTemplateListContainer.addView(row.root)
        }
    }

    private fun confirmDeleteTemplate(template: AchievementTemplate) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${template.templateName}\"?")
            .setPositiveButton("Delete") { _, _ ->
                AchievementTemplateStore.delete(this, template.templateName)
                if (loadedTemplateId == template.id) loadedTemplateId = null
                renderSavedTemplateList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveCurrentAsTemplate() {
        val name = binding.templateNameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            Toast.makeText(this, "Give the template a name first", Toast.LENGTH_SHORT).show()
            return
        }
        val template = buildTemplateFromForm(name)
        AchievementTemplateStore.save(this, template)
        loadedTemplateId = template.id
        renderSavedTemplateList()
        Toast.makeText(this, "Saved \"$name\"", Toast.LENGTH_SHORT).show()
    }

    private fun buildTemplateFromForm(name: String): AchievementTemplate {
        return AchievementTemplate(
            id = loadedTemplateId ?: UUID.randomUUID().toString(),
            templateName = name,
            categoryName = binding.categoryNameInput.text?.toString().orEmpty(),
            useExistingCategory = binding.existingCategoryRadio.isChecked,
            existingCategoryId = binding.existingCategoryIdInput.text?.toString()?.trim().orEmpty(),
            subcategoryName = binding.subcategoryNameInput.text?.toString().orEmpty(),
            skillIds = binding.skillIdsInput.text?.toString().orEmpty(),
            useSharedCondition = binding.sharedConditionToggle.isChecked,
            sharedConditionType = ConditionTypes.ALL[binding.sharedConditionSpinner.selectedItemPosition].code,
            sharedRelatedIdTemplate = binding.sharedRelatedIdInput.text?.toString().orEmpty(),
            variables = currentVariables(),
            tiers = tiers.map {
                AchievementTier(
                    id = it.id,
                    nameTemplate = it.name,
                    descTemplate = it.desc,
                    conditionType = it.conditionType,
                    relatedIdTemplate = it.relatedId,
                    target = it.target.toIntOrNull(),
                    coin = it.coin.toIntOrNull(),
                    exp = it.exp.toIntOrNull(),
                    items = it.toItemRewards()
                )
            }
        )
    }

    private fun TierRow.toItemRewards(): List<AchievementItemReward> = items.map {
        AchievementItemReward(it.itemId, it.itemName, AchievementItemReward.parseAmount(it.amount))
    }

    private fun renderVariables() {
        binding.variableListContainer.removeAllViews()
        variables.forEach { row ->
            val itemBinding = ItemTemplateVariableBinding.inflate(layoutInflater, binding.variableListContainer, false)
            itemBinding.variableKeyInput.setText(row.key)
            itemBinding.variableValueInput.setText(row.value)
            itemBinding.variableKeyInput.doOnTextChanged { text, _, _, _ ->
                row.key = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.variableValueInput.doOnTextChanged { text, _, _, _ ->
                row.value = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.removeVariableButton.setOnClickListener {
                variables.remove(row)
                renderVariables()
            }
            binding.variableListContainer.addView(itemBinding.root)
        }
        refreshRecap()
    }

    private fun renderTiers() {
        binding.tierListContainer.removeAllViews()
        val conditionLabels = ConditionTypes.ALL.map { it.label }
        val sharedMode = binding.sharedConditionToggle.isChecked

        tiers.forEachIndexed { index, row ->
            val itemBinding = ItemAchievementTierBinding.inflate(layoutInflater, binding.tierListContainer, false)
            itemBinding.tierTag.text = "TIER ${index + 1}"
            itemBinding.tierNameInput.setText(row.name)
            itemBinding.tierDescInput.setText(row.desc)
            itemBinding.tierTargetInput.setText(row.target)
            itemBinding.tierCoinInput.setText(row.coin)
            itemBinding.tierExpInput.setText(row.exp)
            itemBinding.tierRelatedIdInput.setText(row.relatedId)

            itemBinding.tierConditionLabel.text = if (sharedMode) "Unlock condition (shared)" else "Unlock condition"
            itemBinding.tierSharedConditionNote.visibility = if (sharedMode) View.VISIBLE else View.GONE
            itemBinding.tierSharedConditionNote.text = sharedConditionNote()
            itemBinding.tierConditionSpinner.visibility = if (sharedMode) View.GONE else View.VISIBLE
            itemBinding.tierRelatedIdLabel.visibility = if (sharedMode) View.GONE else View.VISIBLE
            itemBinding.tierRelatedIdInput.visibility = if (sharedMode) View.GONE else View.VISIBLE
            itemBinding.tierPickRelatedButton.visibility = View.GONE

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, conditionLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemBinding.tierConditionSpinner.adapter = adapter
            itemBinding.tierConditionSpinner.setSelection(ConditionTypes.indexOf(row.conditionType))
            if (!sharedMode) applyRelatedIdState(itemBinding, row.conditionType)

            itemBinding.tierConditionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    row.conditionType = ConditionTypes.ALL[position].code
                    if (!sharedMode) applyRelatedIdState(itemBinding, row.conditionType)
                    refreshRecap()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            itemBinding.tierNameInput.doOnTextChanged { text, _, _, _ ->
                row.name = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.tierDescInput.doOnTextChanged { text, _, _, _ -> row.desc = text?.toString().orEmpty() }
            itemBinding.tierRelatedIdInput.doOnTextChanged { text, _, _, _ ->
                row.relatedId = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.tierTargetInput.doOnTextChanged { text, _, _, _ ->
                row.target = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.tierCoinInput.doOnTextChanged { text, _, _, _ ->
                row.coin = text?.toString().orEmpty()
                refreshRecap()
            }
            itemBinding.tierExpInput.doOnTextChanged { text, _, _, _ ->
                row.exp = text?.toString().orEmpty()
                refreshRecap()
            }

            renderTierItems(itemBinding, row)
            itemBinding.tierAddItemButton.setOnClickListener {
                showItemPicker(row) { renderTierItems(itemBinding, row) }
            }

            itemBinding.removeTierButton.setOnClickListener {
                tiers.remove(row)
                renderTiers()
            }

            binding.tierListContainer.addView(itemBinding.root)
        }
        refreshRecap()
    }

    /** Re-renders only [row]'s item rewards, so adding or removing one keeps the rest of the form as it is. */
    private fun renderTierItems(tierBinding: ItemAchievementTierBinding, row: TierRow) {
        tierBinding.tierItemsContainer.removeAllViews()
        row.items.forEach { item ->
            val itemBinding = ItemTierItemRewardBinding.inflate(layoutInflater, tierBinding.tierItemsContainer, false)
            itemBinding.itemRewardName.text = item.itemName
            itemBinding.itemRewardAmountInput.setText(item.amount)
            itemBinding.itemRewardAmountInput.doOnTextChanged { text, _, _, _ -> item.amount = text?.toString().orEmpty() }
            itemBinding.removeItemRewardButton.setOnClickListener {
                row.items.remove(item)
                renderTierItems(tierBinding, row)
            }
            tierBinding.tierItemsContainer.addView(itemBinding.root)
        }
        refreshRecap()
    }

    /**
     * Lists problems above the Generate button, which stays disabled while any is an error.
     * Errors are forms that can't succeed, or that LifeUp would quietly change. Warnings and
     * infos are valid but look unintended, so they never block.
     */
    private fun refreshRecap() {
        val lines = recapErrors() + recapWarnings() + recapInfos()
        binding.generateButton.isEnabled = lines.none { it.severity == RecapSeverity.ERROR }

        binding.recapGroup.visibility = if (lines.isEmpty()) View.GONE else View.VISIBLE
        if (lines.isEmpty()) return

        val sorted = lines.sortedBy { it.severity.ordinal }
        val badgeWidth = BadgeSpan.width(binding.recapText.textSize)
        val text = SpannableStringBuilder()
        sorted.forEachIndexed { index, line ->
            if (index > 0) text.append('\n')
            val start = text.length
            text.append(' ')
            text.setSpan(BadgeSpan(line.severity.glyph, line.severity.textColor), start, start + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.append(line.text)
            text.setSpan(ForegroundColorSpan(line.severity.textColor), start + 1, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            // Wrapped lines start under the text, not under the badge.
            text.setSpan(LeadingMarginSpan.Standard(0, badgeWidth), start, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.recapText.text = text

        val strokeWidth = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
        (binding.recapGroup.background.mutate() as GradientDrawable).setStroke(strokeWidth, sorted.first().severity.borderColor)
    }

    private fun recapErrors(): List<RecapLine> {
        val errors = mutableListOf<String>()
        if (binding.existingCategoryRadio.isChecked) {
            if (binding.existingCategoryIdInput.text?.toString()?.trim()?.toLongOrNull() == null) {
                errors.add("Choose an existing category.")
            }
            if (binding.subcategoryNameInput.text?.toString().isNullOrBlank()) errors.add("Enter a subcategory name.")
        } else if (binding.categoryNameInput.text?.toString().isNullOrBlank()) {
            errors.add("Enter an achievement category name.")
        }
        if (tiers.isEmpty()) errors.add("Add at least one tier.")

        val effective = effectiveTiers()
        val unnamed = tierNumbers(effective) { it.name.isBlank() }
        if (unnamed.isNotEmpty()) errors.add("${tiersHave(unnamed)} no name.")

        val variablesMap = currentVariables()
        val badRelatedId = effective.withIndex().filter { (_, tier) ->
            ConditionTypes.hasRelatedId(tier.conditionType) &&
                TemplateSubstitution.substitute(tier.relatedId, variablesMap).trim().toLongOrNull() == null
        }
        if (binding.sharedConditionToggle.isChecked) {
            badRelatedId.firstOrNull()?.let { (_, tier) ->
                errors.add("The shared unlock condition needs a valid ${relatedIdLabel(tier)}.")
            }
        } else {
            badRelatedId.groupBy { relatedIdLabel(it.value) }.forEach { (label, group) ->
                errors.add("${tiersHave(group.map { it.index + 1 })} no valid $label.")
            }
        }

        // LifeUp drops a condition sent without a target.
        val noTarget = tierNumbers(effective) { it.conditionType != null && it.target.toIntOrNull() == null }
        if (noTarget.isNotEmpty()) errors.add("${tiersHave(noTarget)} no target value.")

        return errors.map { RecapLine(RecapSeverity.ERROR, it) }
    }

    /** LifeUp only grants EXP through skills, so skills without EXP (or EXP without skills) rewards nothing. */
    private fun recapWarnings(): List<RecapLine> {
        val warnings = mutableListOf<String>()
        val hasSkills = binding.skillIdsInput.text?.toString().orEmpty().isNotBlank()
        val expTiers = tierNumbers(tiers, ::hasExpReward)
        if (hasSkills && expTiers.isEmpty()) {
            warnings.add("Skill IDs are set, but no tier has an EXP reward — the skills won't get anything.")
        }
        if (!hasSkills && expTiers.isNotEmpty()) {
            warnings.add("${tiersHave(expTiers)} an EXP reward, but no skill IDs are set — LifeUp ignores EXP without skills.")
        }

        val noCondition = tierNumbers(effectiveTiers()) { it.conditionType == null }
        if (noCondition.isNotEmpty()) {
            if (binding.sharedConditionToggle.isChecked) {
                warnings.add("The shared unlock condition is \"No condition\" — these can only be unlocked manually.")
            } else {
                warnings.add("${tiersHave(noCondition)} no unlock condition — only a manual unlock will work.")
            }
        }
        return warnings.map { RecapLine(RecapSeverity.WARNING, it) }
    }

    /** A reward that stops or skips a tier partway up the ladder usually wasn't meant to. */
    private fun recapInfos(): List<RecapLine> {
        val rewards = listOf<Pair<String, (TierRow) -> Boolean>>(
            "Coin reward" to { (it.coin.toIntOrNull() ?: 0) > 0 },
            "EXP reward" to ::hasExpReward,
            "Item reward" to { it.items.isNotEmpty() }
        )
        return rewards.mapNotNull { (label, hasReward) ->
            val first = tiers.indexOfFirst(hasReward)
            val missing = if (first == -1) emptyList() else {
                (first + 1 until tiers.size).filterNot { hasReward(tiers[it]) }.map { it + 1 }
            }
            if (missing.isEmpty()) return@mapNotNull null
            val tierWord = if (missing.size == 1) "tier" else "tiers"
            RecapLine(
                RecapSeverity.INFO,
                "$label is set from tier ${first + 1}, but missing on $tierWord ${missing.joinToString(", ")}."
            )
        }
    }

    private fun hasExpReward(tier: TierRow): Boolean = (tier.exp.toIntOrNull() ?: 0) > 0

    /** The 1-based numbers of the tiers matching [predicate]. */
    private fun tierNumbers(tiers: List<TierRow>, predicate: (TierRow) -> Boolean): List<Int> =
        tiers.withIndex().filter { predicate(it.value) }.map { it.index + 1 }

    private fun tiersHave(numbers: List<Int>): String =
        if (numbers.size == 1) "Tier ${numbers[0]} has" else "Tiers ${numbers.joinToString(", ")} have"

    private fun relatedIdLabel(tier: TierRow): String? =
        ConditionTypes.ALL.find { it.code == tier.conditionType }?.relatedIdLabel

    private fun currentVariables(): Map<String, String> =
        variables.filter { it.key.isNotBlank() }.associate { it.key to it.value }

    /** [tiers] as they'll be generated: with the shared condition applied when it's on. */
    private fun effectiveTiers(): List<TierRow> {
        if (!binding.sharedConditionToggle.isChecked) return tiers.toList()
        val sharedConditionType = ConditionTypes.ALL[binding.sharedConditionSpinner.selectedItemPosition].code
        val sharedRelatedId = binding.sharedRelatedIdInput.text?.toString().orEmpty()
        return tiers.map { it.copy(conditionType = sharedConditionType, relatedId = sharedRelatedId) }
    }

    private fun sharedConditionNote(): String {
        return ConditionTypes.ALL[binding.sharedConditionSpinner.selectedItemPosition].label
    }

    /** Refreshes each tier row's read-only note when the shared condition changes, without re-rendering the rows. */
    private fun updateTierSharedConditionNotes() {
        val note = sharedConditionNote()
        binding.tierListContainer.children.forEach { ItemAchievementTierBinding.bind(it).tierSharedConditionNote.text = note }
    }

    private fun applyRelatedIdState(itemBinding: ItemAchievementTierBinding, conditionType: Int?) {
        val relatedLabel = ConditionTypes.ALL.find { it.code == conditionType }?.relatedIdLabel
        val relatedVisibility = if (relatedLabel != null) View.VISIBLE else View.GONE
        itemBinding.tierRelatedIdLabel.visibility = relatedVisibility
        itemBinding.tierRelatedIdInput.visibility = relatedVisibility
        itemBinding.tierRelatedIdLabel.text = relatedLabel
        itemBinding.tierRelatedIdInput.hint = ConditionTypes.relatedIdHint(conditionType)
        applyPickRelatedButtonState(itemBinding.tierPickRelatedButton, itemBinding.tierRelatedIdInput, conditionType)
    }

    /** Shows [button] as a task or skill picker filling [targetInput], matching [conditionType]'s related ID kind. */
    private fun applyPickRelatedButtonState(button: Button, targetInput: EditText, conditionType: Int?) {
        when {
            ConditionTypes.isTaskBased(conditionType) -> {
                button.text = "Pick from LifeUp tasks…"
                button.setOnClickListener { showTaskPicker(targetInput) }
                button.visibility = View.VISIBLE
            }
            ConditionTypes.isSkillBased(conditionType) -> {
                button.text = "Pick from LifeUp skills…"
                button.setOnClickListener { showSkillPicker(targetInput, multiSelect = false) }
                button.visibility = View.VISIBLE
            }
            else -> button.visibility = View.GONE
        }
    }

    private fun showTaskPicker(targetInput: EditText) {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Loading tasks…")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        Thread {
            val result = LifeUpBridge.listTasks(this)
            runOnUiThread {
                loadingDialog.dismiss()
                result.fold(
                    onSuccess = { tasks -> showTaskPickerResult(tasks, targetInput) },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "Failed to load tasks", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }

    private fun showTaskPickerResult(tasks: List<LifeUpBridge.LifeUpTask>, targetInput: EditText) {
        val eligible = tasks.filter { it.id != null }
        if (eligible.isEmpty()) {
            Toast.makeText(this, "No tasks found in LifeUp", Toast.LENGTH_SHORT).show()
            return
        }
        val names = eligible.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a task")
            .setItems(names) { _, which -> targetInput.setText(eligible[which].id.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCategoryPicker() {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Loading categories…")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        Thread {
            val result = LifeUpBridge.listAchievementCategories(this)
            runOnUiThread {
                loadingDialog.dismiss()
                result.fold(
                    onSuccess = { categories -> showCategoryPickerResult(categories) },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "Failed to load categories", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }

    private fun showCategoryPickerResult(categories: List<LifeUpBridge.LifeUpAchievementCategory>) {
        if (categories.isEmpty()) {
            Toast.makeText(this, "No achievement categories found in LifeUp", Toast.LENGTH_SHORT).show()
            return
        }
        val names = categories.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a category")
            .setItems(names) { _, which -> binding.existingCategoryIdInput.setText(categories[which].id.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Fills [targetInput] with LifeUp skill IDs. [multiSelect] writes a comma-separated list
     * (pre-checking IDs already in the field and dropping entries that match no skill);
     * otherwise a single pick replaces the field.
     */
    private fun showSkillPicker(targetInput: EditText, multiSelect: Boolean) {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Loading skills…")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        Thread {
            val result = LifeUpBridge.listSkills(this)
            runOnUiThread {
                loadingDialog.dismiss()
                result.fold(
                    onSuccess = { skills ->
                        if (skills.isEmpty()) {
                            Toast.makeText(this, "No skills found in LifeUp", Toast.LENGTH_SHORT).show()
                        } else if (multiSelect) {
                            showSkillMultiPickerResult(skills, targetInput)
                        } else {
                            showSkillSinglePickerResult(skills, targetInput)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "Failed to load skills", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }

    private fun showSkillSinglePickerResult(skills: List<LifeUpBridge.LifeUpSkill>, targetInput: EditText) {
        val names = skills.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose a skill")
            .setItems(names) { _, which -> targetInput.setText(skills[which].id.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSkillMultiPickerResult(skills: List<LifeUpBridge.LifeUpSkill>, targetInput: EditText) {
        val currentEntries = targetInput.text?.toString().orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val skillIds = skills.map { it.id.toString() }
        val checked = BooleanArray(skills.size) { skillIds[it] in currentEntries }
        val names = skills.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose skills")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("OK") { _, _ ->
                val picked = skillIds.filterIndexed { index, _ -> checked[index] }
                targetInput.setText(picked.joinToString(", "))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showItemPicker(row: TierRow, onChanged: () -> Unit) {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Loading items…")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        Thread {
            val result = LifeUpBridge.listItems(this)
            runOnUiThread {
                loadingDialog.dismiss()
                result.fold(
                    onSuccess = { items ->
                        if (items.isEmpty()) {
                            Toast.makeText(this, "No items found in LifeUp", Toast.LENGTH_SHORT).show()
                        } else {
                            showItemPickerResult(items, row, onChanged)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "Failed to load items", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }.start()
    }

    /**
     * Lists [row]'s items first, so they're visible without scrolling. Rows of items that are
     * still checked keep their amount, and rows of items no longer in LifeUp are left alone,
     * since they aren't listed here to uncheck.
     */
    private fun showItemPickerResult(items: List<LifeUpBridge.LifeUpItem>, row: TierRow, onChanged: () -> Unit) {
        val currentIds = row.items.map { it.itemId }
        val ordered = items.sortedByDescending { it.id in currentIds }
        val checked = BooleanArray(ordered.size) { ordered[it].id in currentIds }
        val names = ordered.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Choose items")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("OK") { _, _ ->
                val picked = ordered.filterIndexed { index, _ -> checked[index] }
                val pickedIds = picked.map { it.id }
                val listedIds = ordered.map { it.id }
                row.items.removeAll { it.itemId in listedIds && it.itemId !in pickedIds }
                picked.filter { it.id !in currentIds }
                    .forEach { row.items.add(ItemRewardRow(it.id, it.name, amount = "1")) }
                onChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** The Generate button is disabled until [recapErrors] is empty, so the form is valid here. */
    private fun onGenerateClicked() {
        val variablesMap = currentVariables()
        val skillIds = binding.skillIdsInput.text?.toString().orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
        val tierSnapshot = effectiveTiers()

        if (binding.existingCategoryRadio.isChecked) {
            val existingCategoryId = binding.existingCategoryIdInput.text?.toString()?.trim()?.toLongOrNull() ?: return
            val subcategoryNameRaw = binding.subcategoryNameInput.text?.toString()?.trim().orEmpty()
            val subcategoryName = TemplateSubstitution.substitute(subcategoryNameRaw, variablesMap)
            runGenerateExisting(existingCategoryId, subcategoryName, tierSnapshot, variablesMap, skillIds)
        } else {
            val categoryNameRaw = binding.categoryNameInput.text?.toString()?.trim().orEmpty()
            val categoryName = TemplateSubstitution.substitute(categoryNameRaw, variablesMap)
            runGenerateNewCategory(categoryName, tierSnapshot, variablesMap, skillIds)
        }
    }

    private fun runGenerateNewCategory(
        categoryName: String,
        tierSnapshot: List<TierRow>,
        variablesMap: Map<String, String>,
        skillIds: List<Long>
    ) {
        val (progressBinding, progressDialog) =
            createProgressDialog(tierSnapshot.size, "Creating category \"$categoryName\"...")

        Thread {
            val categoryResult = LifeUpBridge.createCategory(this, categoryName)
            val categoryId = categoryResult.getOrNull()
            if (categoryId == null) {
                runOnUiThread {
                    progressDialog.dismiss()
                    showGenerateResult(0, tierSnapshot.size, categoryResult.exceptionOrNull())
                }
                return@Thread
            }
            generateTiers(categoryId, tierSnapshot, variablesMap, skillIds, progressBinding, progressDialog)
        }.start()
    }

    private fun runGenerateExisting(
        categoryId: Long,
        subcategoryName: String,
        tierSnapshot: List<TierRow>,
        variablesMap: Map<String, String>,
        skillIds: List<Long>
    ) {
        val (progressBinding, progressDialog) =
            createProgressDialog(tierSnapshot.size, "Creating subcategory \"$subcategoryName\"...")

        Thread {
            val subcategoryResult = LifeUpBridge.createSubcategory(this, categoryId, subcategoryName)
            if (subcategoryResult.isFailure) {
                runOnUiThread {
                    progressDialog.dismiss()
                    showGenerateResult(0, tierSnapshot.size, subcategoryResult.exceptionOrNull())
                }
                return@Thread
            }
            generateTiers(categoryId, tierSnapshot, variablesMap, skillIds, progressBinding, progressDialog)
        }.start()
    }

    private fun createProgressDialog(tierCount: Int, initialText: String): Pair<DialogProgressBinding, AlertDialog> {
        val progressBinding = DialogProgressBinding.inflate(layoutInflater)
        progressBinding.progressBar.max = tierCount
        progressBinding.progressText.text = initialText

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Generating achievements")
            .setView(progressBinding.root)
            .setCancelable(false)
            .create()
        progressDialog.show()

        return progressBinding to progressDialog
    }

    /** Runs on the calling background thread — does not spawn its own. */
    private fun generateTiers(
        categoryId: Long,
        tierSnapshot: List<TierRow>,
        variablesMap: Map<String, String>,
        skillIds: List<Long>,
        progressBinding: DialogProgressBinding,
        progressDialog: AlertDialog
    ) {
        var created = 0
        var failure: Throwable? = null

        for ((index, tier) in tierSnapshot.withIndex()) {
            runOnUiThread {
                progressBinding.progressBar.progress = index
                progressBinding.progressText.text = "Creating tier ${index + 1} / ${tierSnapshot.size}..."
            }

            val name = TemplateSubstitution.substitute(tier.name, variablesMap)
            val desc = TemplateSubstitution.substitute(tier.desc, variablesMap).ifBlank { null }
            val relatedId = if (ConditionTypes.hasRelatedId(tier.conditionType)) {
                TemplateSubstitution.substitute(tier.relatedId, variablesMap).trim().toLongOrNull()
            } else {
                null
            }

            val result = LifeUpBridge.createAchievement(
                this,
                categoryId,
                name,
                desc,
                tier.conditionType,
                relatedId,
                tier.target.toIntOrNull(),
                tier.coin.toIntOrNull(),
                tier.exp.toIntOrNull(),
                skillIds.ifEmpty { null },
                tier.toItemRewards()
            )

            if (result.isFailure) {
                failure = result.exceptionOrNull()
                break
            }
            created++
            val progressCount = created
            runOnUiThread { progressBinding.progressBar.progress = progressCount }
        }

        runOnUiThread {
            progressDialog.dismiss()
            showGenerateResult(created, tierSnapshot.size, failure)
        }
    }

    private fun showGenerateResult(created: Int, total: Int, failure: Throwable?) {
        if (failure == null) {
            Toast.makeText(this, "Created $created / $total achievements.", Toast.LENGTH_LONG).show()
            return
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Created $created / $total achievements")
            .setMessage(failure.message ?: failure.toString())
            .setPositiveButton("OK", null)

        if (failure is SecurityException) {
            builder.setNeutralButton("Grant Permission") { _, _ ->
                LifeUpBridge.requestContentProviderPermission(this, getString(R.string.app_name))
            }
        }
        builder.show()
    }
}
