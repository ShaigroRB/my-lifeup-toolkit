package io.github.shaigrorb.mylifeuptoolkit

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import io.github.shaigrorb.mylifeuptoolkit.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.itemBulkComplete.setOnClickListener {
            // TODO: replace with an Intent to BulkCompleteActivity once that screen exists
            Toast.makeText(this, "Bulk Complete Tasks — not built yet", Toast.LENGTH_SHORT).show()
        }

        binding.itemAchievementTemplates.setOnClickListener {
            // TODO: replace with an Intent to AchievementTemplateActivity once that screen exists
            Toast.makeText(this, "Achievement Templates — not built yet", Toast.LENGTH_SHORT).show()
        }

        // itemStats is intentionally not clickable (see activity_main.xml) — no listener needed yet.
    }
}
