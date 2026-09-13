package io.github.shaigrorb.mylifeuptoolkit

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import java.util.UUID

import io.github.shaigrorb.mylifeuptoolkit.databinding.ActivityAchievementTemplateBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.DialogProgressBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemAchievementTierBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemSavedTemplateBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemTemplateVariableBinding

class AchievementTemplateActivity : AppCompatActivity() {

    /** In-memory editable row — separate from the persisted [AchievementTier] so text fields stay free-form while typing. */
    private data class VariableRow(val id: String = UUID.randomUUID().toString(), var key: String, var value: String)

    private data class TierRow(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        var desc: String,
        var conditionType: Int?,
        var relatedId: String,
        var target: String,
        var coin: String,
        var exp: String
    )

    private lateinit var binding: ActivityAchievementTemplateBinding
    private val variables = mutableListOf<VariableRow>()
    private val tiers = mutableListOf<TierRow>()
    private var loadedTemplateId: String? = null

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

        loadBlank()
        renderSavedTemplateList()
    }

    private fun loadBlank() {
        binding.templateNameInput.setText("")
        binding.categoryNameInput.setText("")
        binding.skillIdsInput.setText("")
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
    }

    private fun loadTemplate(template: AchievementTemplate) {
        binding.templateNameInput.setText(template.templateName)
        binding.categoryNameInput.setText(template.categoryName)
        binding.skillIdsInput.setText(template.skillIds)
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
                    exp = it.exp?.toString() ?: ""
                )
            }
        )

        renderVariables()
        renderTiers()
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
            skillIds = binding.skillIdsInput.text?.toString().orEmpty(),
            variables = variables.filter { it.key.isNotBlank() }.associate { it.key to it.value },
            tiers = tiers.map {
                AchievementTier(
                    id = it.id,
                    nameTemplate = it.name,
                    descTemplate = it.desc,
                    conditionType = it.conditionType,
                    relatedIdTemplate = it.relatedId,
                    target = it.target.toIntOrNull(),
                    coin = it.coin.toIntOrNull(),
                    exp = it.exp.toIntOrNull()
                )
            }
        )
    }

    private fun renderVariables() {
        binding.variableListContainer.removeAllViews()
        variables.forEach { row ->
            val itemBinding = ItemTemplateVariableBinding.inflate(layoutInflater, binding.variableListContainer, false)
            itemBinding.variableKeyInput.setText(row.key)
            itemBinding.variableValueInput.setText(row.value)
            itemBinding.variableKeyInput.doOnTextChanged { text, _, _, _ -> row.key = text?.toString().orEmpty() }
            itemBinding.variableValueInput.doOnTextChanged { text, _, _, _ -> row.value = text?.toString().orEmpty() }
            itemBinding.removeVariableButton.setOnClickListener {
                variables.remove(row)
                renderVariables()
            }
            binding.variableListContainer.addView(itemBinding.root)
        }
    }

    private fun renderTiers() {
        binding.tierListContainer.removeAllViews()
        val conditionLabels = ConditionTypes.ALL.map { it.label }

        tiers.forEachIndexed { index, row ->
            val itemBinding = ItemAchievementTierBinding.inflate(layoutInflater, binding.tierListContainer, false)
            itemBinding.tierTag.text = "TIER ${index + 1}"
            itemBinding.tierNameInput.setText(row.name)
            itemBinding.tierDescInput.setText(row.desc)
            itemBinding.tierTargetInput.setText(row.target)
            itemBinding.tierCoinInput.setText(row.coin)
            itemBinding.tierExpInput.setText(row.exp)
            itemBinding.tierRelatedIdInput.setText(row.relatedId)

            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, conditionLabels)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            itemBinding.tierConditionSpinner.adapter = adapter
            itemBinding.tierConditionSpinner.setSelection(ConditionTypes.indexOf(row.conditionType))
            applyRelatedIdState(itemBinding, row.conditionType)

            itemBinding.tierConditionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    row.conditionType = ConditionTypes.ALL[position].code
                    applyRelatedIdState(itemBinding, row.conditionType)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            itemBinding.tierNameInput.doOnTextChanged { text, _, _, _ -> row.name = text?.toString().orEmpty() }
            itemBinding.tierDescInput.doOnTextChanged { text, _, _, _ -> row.desc = text?.toString().orEmpty() }
            itemBinding.tierRelatedIdInput.doOnTextChanged { text, _, _, _ -> row.relatedId = text?.toString().orEmpty() }
            itemBinding.tierTargetInput.doOnTextChanged { text, _, _, _ -> row.target = text?.toString().orEmpty() }
            itemBinding.tierCoinInput.doOnTextChanged { text, _, _, _ -> row.coin = text?.toString().orEmpty() }
            itemBinding.tierExpInput.doOnTextChanged { text, _, _, _ -> row.exp = text?.toString().orEmpty() }

            itemBinding.removeTierButton.setOnClickListener {
                tiers.remove(row)
                renderTiers()
            }

            binding.tierListContainer.addView(itemBinding.root)
        }
    }

    private fun applyRelatedIdState(itemBinding: ItemAchievementTierBinding, conditionType: Int?) {
        val relatedLabel = ConditionTypes.ALL.find { it.code == conditionType }?.relatedIdLabel
        itemBinding.tierRelatedIdInput.isEnabled = relatedLabel != null
        itemBinding.tierRelatedIdLabel.text = relatedLabel ?: "Related ID (n/a)"
    }

    private fun onGenerateClicked() {
        val categoryNameRaw = binding.categoryNameInput.text?.toString()?.trim().orEmpty()
        if (categoryNameRaw.isEmpty()) {
            Toast.makeText(this, "Enter an achievement category name", Toast.LENGTH_SHORT).show()
            return
        }
        if (tiers.isEmpty()) {
            Toast.makeText(this, "Add at least one tier", Toast.LENGTH_SHORT).show()
            return
        }

        val variablesMap = variables.filter { it.key.isNotBlank() }.associate { it.key to it.value }
        val skillIds = binding.skillIdsInput.text?.toString().orEmpty()
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
        val categoryName = TemplateSubstitution.substitute(categoryNameRaw, variablesMap)

        runGenerate(categoryName, tiers.toList(), variablesMap, skillIds)
    }

    private fun runGenerate(
        categoryName: String,
        tierSnapshot: List<TierRow>,
        variablesMap: Map<String, String>,
        skillIds: List<Long>
    ) {
        val progressBinding = DialogProgressBinding.inflate(layoutInflater)
        progressBinding.progressBar.max = tierSnapshot.size
        progressBinding.progressText.text = "Creating category \"$categoryName\"..."

        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Generating achievements")
            .setView(progressBinding.root)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val categoryResult = LifeUpBridge.createCategory(this, categoryName)
            val categoryId = categoryResult.getOrNull()
            if (categoryId == null) {
                val error = categoryResult.exceptionOrNull()
                runOnUiThread {
                    progressDialog.dismiss()
                    showGenerateResult(0, tierSnapshot.size, error)
                }
                return@Thread
            }

            var created = 0
            var failure: Throwable? = null

            for ((index, tier) in tierSnapshot.withIndex()) {
                runOnUiThread {
                    progressBinding.progressBar.progress = index
                    progressBinding.progressText.text = "Creating tier ${index + 1} / ${tierSnapshot.size}..."
                }

                val name = TemplateSubstitution.substitute(tier.name, variablesMap)
                val desc = TemplateSubstitution.substitute(tier.desc, variablesMap).ifBlank { null }
                val relatedId = TemplateSubstitution.substitute(tier.relatedId, variablesMap).toLongOrNull()

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
                    skillIds.ifEmpty { null }
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
        }.start()
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
