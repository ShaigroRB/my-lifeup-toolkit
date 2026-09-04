package io.github.shaigrorb.mylifeuptoolkit

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.atomic.AtomicBoolean

import io.github.shaigrorb.mylifeuptoolkit.databinding.ActivityBulkCompleteBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.DialogAddProfileBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.DialogCountInputBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.DialogProgressBinding
import io.github.shaigrorb.mylifeuptoolkit.databinding.ItemTaskProfileBinding

class BulkCompleteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBulkCompleteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBulkCompleteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Bulk Complete Tasks"

        binding.addProfileButton.setOnClickListener { showAddProfileDialog() }

        renderProfileList()
    }

    private fun renderProfileList() {
        binding.profileListContainer.removeAllViews()
        val profiles = TaskProfileStore.getAll(this)
        binding.emptyStateText.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE

        profiles.forEach { profile ->
            val row = ItemTaskProfileBinding.inflate(
                LayoutInflater.from(this), binding.profileListContainer, false
            )
            row.profileLabel.text = profile.label
            row.profileGid.text = "gid: ${profile.gid}"
            row.profileRowClickArea.setOnClickListener { showCountDialog(profile) }
            row.removeProfileButton.setOnClickListener { confirmRemoveProfile(profile) }
            binding.profileListContainer.addView(row.root)
        }
    }

    private fun showAddProfileDialog() {
        val dialogBinding = DialogAddProfileBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle("Add task")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val label = dialogBinding.labelInput.text?.toString()?.trim().orEmpty()
                val gid = dialogBinding.gidInput.text?.toString()?.trim()?.toLongOrNull()
                if (label.isEmpty() || gid == null) {
                    Toast.makeText(this, "Enter a label and a valid gid", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                TaskProfileStore.add(this, TaskProfile(label, gid))
                renderProfileList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemoveProfile(profile: TaskProfile) {
        AlertDialog.Builder(this)
            .setTitle("Remove \"${profile.label}\"?")
            .setPositiveButton("Remove") { _, _ ->
                TaskProfileStore.remove(this, profile)
                renderProfileList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCountDialog(profile: TaskProfile) {
        val dialogBinding = DialogCountInputBinding.inflate(layoutInflater)
        AlertDialog.Builder(this)
            .setTitle(profile.label)
            .setView(dialogBinding.root)
            .setPositiveButton("Complete") { _, _ ->
                val count = dialogBinding.countInput.text?.toString()?.trim()?.toIntOrNull()
                if (count == null || count < 1) {
                    Toast.makeText(this, "Enter a count of 1 or more", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                runBulkComplete(profile, count)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun runBulkComplete(profile: TaskProfile, count: Int) {
        val progressBinding = DialogProgressBinding.inflate(layoutInflater)
        progressBinding.progressBar.max = count
        progressBinding.progressText.text = "Completing 0 / $count..."

        val stopRequested = AtomicBoolean(false)

        val progressDialog = AlertDialog.Builder(this)
            .setTitle(profile.label)
            .setView(progressBinding.root)
            .setCancelable(false)
            .setNegativeButton("Stop") { _, _ -> stopRequested.set(true) }
            .create()
        progressDialog.show()

        Thread {
            var completed = 0
            var failure: Throwable? = null

            while (completed < count && !stopRequested.get()) {
                val result = LifeUpBridge.completeTask(this, profile.gid)
                if (result.isFailure) {
                    failure = result.exceptionOrNull()
                    break
                }
                completed++
                val current = completed
                runOnUiThread {
                    progressBinding.progressBar.progress = current
                    progressBinding.progressText.text = "Completing $current / $count..."
                }
            }

            runOnUiThread {
                progressDialog.dismiss()
                showResult(profile, completed, count, failure)
            }
        }.start()
    }

    private fun showResult(profile: TaskProfile, completed: Int, requested: Int, failure: Throwable?) {
        if (failure == null) {
            val message = if (completed == requested) {
                "Completed \"${profile.label}\" $completed time(s)."
            } else {
                "Stopped after $completed / $requested."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
            .setTitle("Stopped after $completed / $requested")
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
