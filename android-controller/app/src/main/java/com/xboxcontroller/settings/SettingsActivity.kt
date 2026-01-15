package com.xboxcontroller.settings

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xboxcontroller.ControllerActivity
import com.xboxcontroller.data.SettingsRepository
import com.xboxcontroller.databinding.ActivitySettingsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsRepository = SettingsRepository(this)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()

            binding.hapticSwitch.isChecked = settings.hapticFeedback
            binding.hapticIntensitySlider.progress = settings.hapticIntensity
            binding.hapticIntensitySlider.isEnabled = settings.hapticFeedback
            binding.deadzoneSlider.progress = (settings.deadzone * 100).toInt()
            binding.trackpadSwitch.isChecked = settings.showTrackpad
            binding.keyboardSwitch.isChecked = settings.showKeyboard

            updateLabels()
        }
    }

    private fun setupListeners() {
        // Edit Layout - launch controller in edit mode
        binding.editLayoutButton.setOnClickListener {
            val intent = Intent(this, ControllerActivity::class.java)
            intent.putExtra("edit_mode", true)
            startActivity(intent)
        }

        binding.hapticSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsRepository.updateHapticFeedback(isChecked) }
            binding.hapticIntensitySlider.isEnabled = isChecked
        }

        binding.hapticIntensitySlider.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            lifecycleScope.launch { settingsRepository.updateHapticIntensity(progress) }
        })

        binding.deadzoneSlider.setOnSeekBarChangeListener(createSeekBarListener { progress ->
            val deadzone = progress / 100f
            lifecycleScope.launch { settingsRepository.updateDeadzone(deadzone) }
        })

        binding.trackpadSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsRepository.updateShowTrackpad(isChecked) }
        }

        binding.keyboardSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsRepository.updateShowKeyboard(isChecked) }
        }
    }

    private fun createSeekBarListener(onChange: (Int) -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onChange(progress)
                    updateLabels()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    private fun updateLabels() {
        binding.hapticIntensityValue.text = "${binding.hapticIntensitySlider.progress}%"
        binding.deadzoneValue.text = "${binding.deadzoneSlider.progress}%"
    }
}
