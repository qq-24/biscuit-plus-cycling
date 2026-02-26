package net.telent.biscuit.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import net.telent.biscuit.R

class SettingsFragment : Fragment() {
    
    private lateinit var prefs: SharedPreferences
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        prefs = requireContext().getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        
        setupDisplayItems(root)
        setupFontWeight(root)
        setupUpdateInterval(root)
        setupGpsSettings(root)
        setupAutoPause(root)
        setupLowSpeedAlert(root)
        setupZoomSensitivity(root)
        setupTileScale(root)
        setupMapType(root)
        setupTheme(root)
        setupDebug(root)
        
        return root
    }
    
    private fun setupDisplayItems(root: View) {
        val checkSpeed: CheckBox = root.findViewById(R.id.check_speed)
        val checkCadence: CheckBox = root.findViewById(R.id.check_cadence)
        val checkDistance: CheckBox = root.findViewById(R.id.check_distance)
        val checkTime: CheckBox = root.findViewById(R.id.check_time)
        val checkMovingDuration: CheckBox = root.findViewById(R.id.check_moving_duration)
        val checkAvgSpeed: CheckBox = root.findViewById(R.id.check_avg_speed)
        
        checkSpeed.isChecked = prefs.getBoolean("display_speed", true)
        checkCadence.isChecked = prefs.getBoolean("display_cadence", true)
        checkDistance.isChecked = prefs.getBoolean("display_distance", true)
        checkTime.isChecked = prefs.getBoolean("display_time", true)
        checkMovingDuration.isChecked = prefs.getBoolean("display_moving_duration", false)
        checkAvgSpeed.isChecked = prefs.getBoolean("display_avg_speed", false)
        
        checkSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_speed", isChecked).apply()
        }
        checkCadence.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_cadence", isChecked).apply()
        }
        checkDistance.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_distance", isChecked).apply()
        }
        checkTime.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_time", isChecked).apply()
        }
        checkMovingDuration.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_moving_duration", isChecked).apply()
        }
        checkAvgSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("display_avg_speed", isChecked).apply()
        }

        // Label font size (SeekBar range 0-20 maps to 6sp-26sp)
        val seekLabelSize: android.widget.SeekBar = root.findViewById(R.id.seekbar_label_size)
        val tvLabelSizeValue: android.widget.TextView = root.findViewById(R.id.tv_label_size_value)
        val savedLabelSize = prefs.getInt("dashboard_label_size", 10)
        seekLabelSize.max = 20
        seekLabelSize.progress = (savedLabelSize - 6).coerceIn(0, 20)
        tvLabelSizeValue.text = "${savedLabelSize}sp"
        seekLabelSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 6
                tvLabelSizeValue.text = "${size}sp"
                if (fromUser) {
                    prefs.edit().putInt("dashboard_label_size", size).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }
    
    private fun setupFontWeight(root: View) {
        val seekBar: android.widget.SeekBar = root.findViewById(R.id.seekbar_font_weight)
        val tvValue: TextView = root.findViewById(R.id.tv_font_weight_value)

        val weightNames = mapOf(
            100 to "Thin",
            200 to "Extra Light",
            300 to "Light",
            400 to "Regular",
            500 to "Medium",
            600 to "Semi Bold",
            700 to "Bold",
            800 to "Extra Bold",
            900 to "Black"
        )

        val savedWeight = prefs.getInt("dashboard_font_weight", 700)
        val position = (savedWeight / 100) - 1
        seekBar.max = 8
        seekBar.progress = position.coerceIn(0, 8)
        tvValue.text = "$savedWeight ${weightNames[savedWeight] ?: "Bold"}"

        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val weight = (progress + 1) * 100
                tvValue.text = "$weight ${weightNames[weight] ?: ""}"
                if (fromUser) {
                    prefs.edit().putInt("dashboard_font_weight", weight).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun setupUpdateInterval(root: View) {
        val radioGroup: RadioGroup = root.findViewById(R.id.radio_update_interval)
        val interval = prefs.getInt("update_interval", 1000)
        
        when (interval) {
            1000 -> root.findViewById<RadioButton>(R.id.radio_interval_1).isChecked = true
            2000 -> root.findViewById<RadioButton>(R.id.radio_interval_2).isChecked = true
            5000 -> root.findViewById<RadioButton>(R.id.radio_interval_5).isChecked = true
        }
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newInterval = when (checkedId) {
                R.id.radio_interval_1 -> 1000
                R.id.radio_interval_2 -> 2000
                R.id.radio_interval_5 -> 5000
                else -> 1000
            }
            prefs.edit().putInt("update_interval", newInterval).apply()
        }
    }
    
    private fun setupGpsSettings(root: View) {
        // GPS update frequency
        val radioGpsInterval: RadioGroup = root.findViewById(R.id.radio_gps_interval)
        val gpsInterval = prefs.getInt("gps_interval", 1000)
        when (gpsInterval) {
            1000 -> root.findViewById<RadioButton>(R.id.radio_gps_interval_1).isChecked = true
            2000 -> root.findViewById<RadioButton>(R.id.radio_gps_interval_2).isChecked = true
            5000 -> root.findViewById<RadioButton>(R.id.radio_gps_interval_5).isChecked = true
        }
        radioGpsInterval.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_gps_interval_1 -> 1000
                R.id.radio_gps_interval_2 -> 2000
                R.id.radio_gps_interval_5 -> 5000
                else -> 1000
            }
            prefs.edit().putInt("gps_interval", value).apply()
        }

        // Minimum displacement distance
        val radioGpsMinDistance: RadioGroup = root.findViewById(R.id.radio_gps_min_distance)
        val gpsMinDistance = prefs.getFloat("gps_min_distance", 0f)
        when (gpsMinDistance) {
            0f -> root.findViewById<RadioButton>(R.id.radio_gps_distance_0).isChecked = true
            1f -> root.findViewById<RadioButton>(R.id.radio_gps_distance_1).isChecked = true
            3f -> root.findViewById<RadioButton>(R.id.radio_gps_distance_3).isChecked = true
            5f -> root.findViewById<RadioButton>(R.id.radio_gps_distance_5).isChecked = true
        }
        radioGpsMinDistance.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_gps_distance_0 -> 0f
                R.id.radio_gps_distance_1 -> 1f
                R.id.radio_gps_distance_3 -> 3f
                R.id.radio_gps_distance_5 -> 5f
                else -> 0f
            }
            prefs.edit().putFloat("gps_min_distance", value).apply()
        }

        // Accuracy filter switch + threshold
        val switchAccuracy: SwitchCompat = root.findViewById(R.id.switch_accuracy_filter)
        val radioAccuracyThreshold: RadioGroup = root.findViewById(R.id.radio_gps_accuracy_threshold)
        val accuracyFilterEnabled = prefs.getBoolean("gps_accuracy_filter", false)
        switchAccuracy.isChecked = accuracyFilterEnabled
        radioAccuracyThreshold.visibility = if (accuracyFilterEnabled) View.VISIBLE else View.GONE

        val accuracyThreshold = prefs.getFloat("gps_accuracy_threshold", 20f)
        when (accuracyThreshold) {
            10f -> root.findViewById<RadioButton>(R.id.radio_gps_accuracy_10).isChecked = true
            20f -> root.findViewById<RadioButton>(R.id.radio_gps_accuracy_20).isChecked = true
            50f -> root.findViewById<RadioButton>(R.id.radio_gps_accuracy_50).isChecked = true
        }

        switchAccuracy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gps_accuracy_filter", isChecked).apply()
            radioAccuracyThreshold.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        radioAccuracyThreshold.setOnCheckedChangeListener { _, checkedId ->
            val value = when (checkedId) {
                R.id.radio_gps_accuracy_10 -> 10f
                R.id.radio_gps_accuracy_20 -> 20f
                R.id.radio_gps_accuracy_50 -> 50f
                else -> 20f
            }
            prefs.edit().putFloat("gps_accuracy_threshold", value).apply()
        }

        // Low speed filter switch + threshold
        val switchLowSpeed: SwitchCompat = root.findViewById(R.id.switch_low_speed_filter)
        val layoutLowSpeedThreshold: LinearLayout = root.findViewById(R.id.layout_low_speed_threshold)
        val editLowSpeedThreshold: EditText = root.findViewById(R.id.edit_low_speed_threshold)
        val lowSpeedFilterEnabled = prefs.getBoolean("gps_low_speed_filter", true)
        switchLowSpeed.isChecked = lowSpeedFilterEnabled
        layoutLowSpeedThreshold.visibility = if (lowSpeedFilterEnabled) View.VISIBLE else View.GONE

        val lowSpeedThreshold = prefs.getFloat("gps_low_speed_threshold", 3f)
        editLowSpeedThreshold.setText(lowSpeedThreshold.toString())

        switchLowSpeed.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("gps_low_speed_filter", isChecked).apply()
            layoutLowSpeedThreshold.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        editLowSpeedThreshold.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toFloatOrNull() ?: return
                prefs.edit().putFloat("gps_low_speed_threshold", value).apply()
            }
        })
    }

    private fun setupAutoPause(root: View) {
        val switchAutoPause: SwitchCompat = root.findViewById(R.id.switch_auto_pause)
        val layoutSettings: LinearLayout = root.findViewById(R.id.layout_auto_pause_settings)
        val editSpeed: EditText = root.findViewById(R.id.edit_auto_pause_speed)
        val editDelay: EditText = root.findViewById(R.id.edit_auto_pause_delay)

        val enabled = prefs.getBoolean("auto_pause_enabled", false)
        switchAutoPause.isChecked = enabled
        layoutSettings.visibility = if (enabled) View.VISIBLE else View.GONE

        editSpeed.setText(prefs.getFloat("auto_pause_speed", 3f).toString())
        editDelay.setText(prefs.getInt("auto_pause_delay", 10).toString())

        // 手动暂停冷却时间 SeekBar (range 5-60, default 10)
        val seekCooldown: android.widget.SeekBar = root.findViewById(R.id.seekbar_cooldown)
        val tvCooldownValue: android.widget.TextView = root.findViewById(R.id.tv_cooldown_value)
        val savedCooldown = prefs.getInt("manual_pause_cooldown", 10).coerceIn(5, 60)
        seekCooldown.max = 55  // 0-55 maps to 5-60
        seekCooldown.progress = savedCooldown - 5
        tvCooldownValue.text = "${savedCooldown} 秒"

        seekCooldown.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val cooldown = progress + 5
                tvCooldownValue.text = "${cooldown} 秒"
                if (fromUser) {
                    prefs.edit().putInt("manual_pause_cooldown", cooldown).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        switchAutoPause.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_pause_enabled", isChecked).apply()
            layoutSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        editSpeed.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toFloatOrNull() ?: return
                prefs.edit().putFloat("auto_pause_speed", value).apply()
            }
        })

        editDelay.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.toIntOrNull() ?: return
                prefs.edit().putInt("auto_pause_delay", value).apply()
            }
        })
    }

    private fun setupLowSpeedAlert(root: View) {
        val switchAlert: SwitchCompat = root.findViewById(R.id.switch_low_speed_alert)
        val layoutSettings: LinearLayout = root.findViewById(R.id.layout_low_speed_alert_settings)
        val editThreshold: EditText = root.findViewById(R.id.edit_low_speed_alert_threshold)
        val tvHint: TextView = root.findViewById(R.id.tv_low_speed_alert_hint)

        val enabled = prefs.getBoolean("low_speed_alert_enabled", false)
        val threshold = prefs.getFloat("low_speed_alert_threshold", 10.0f)
        val autoPauseEnabled = prefs.getBoolean("auto_pause_enabled", false)

        switchAlert.isChecked = enabled
        layoutSettings.visibility = if (enabled) View.VISIBLE else View.GONE
        editThreshold.setText(threshold.toString())
        tvHint.visibility = if (autoPauseEnabled) View.VISIBLE else View.GONE

        switchAlert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("low_speed_alert_enabled", isChecked).apply()
            layoutSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        editThreshold.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: return
                val value = text.toFloatOrNull() ?: return
                val clamped = value.coerceIn(1.0f, 50.0f)
                if (clamped != value) {
                    editThreshold.removeTextChangedListener(this)
                    editThreshold.setText(clamped.toString())
                    editThreshold.setSelection(editThreshold.text.length)
                    editThreshold.addTextChangedListener(this)
                }
                prefs.edit().putFloat("low_speed_alert_threshold", clamped).apply()
            }
        })

        // Glow width slider
        val seekGlowWidth: android.widget.SeekBar = root.findViewById(R.id.seekbar_glow_width)
        val tvGlowWidthValue: TextView = root.findViewById(R.id.tv_glow_width_value)
        val savedGlowWidth = prefs.getInt("glow_width_dp", 20)
        seekGlowWidth.max = 55
        seekGlowWidth.progress = (savedGlowWidth - 5).coerceIn(0, 55)
        tvGlowWidthValue.text = "${savedGlowWidth}dp"

        seekGlowWidth.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val width = progress + 5
                tvGlowWidthValue.text = "${width}dp"
                if (fromUser) {
                    prefs.edit().putInt("glow_width_dp", width).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Vibration frame interval slider
        val seekVibrationInterval: android.widget.SeekBar = root.findViewById(R.id.seekbar_vibration_frame_interval)
        val tvVibrationIntervalValue: TextView = root.findViewById(R.id.tv_vibration_frame_interval_value)
        val savedInterval = prefs.getInt("vibration_frame_interval", 6)
        seekVibrationInterval.max = 17
        seekVibrationInterval.progress = (savedInterval - 3).coerceIn(0, 17)
        tvVibrationIntervalValue.text = "%.1f秒".format(savedInterval * 0.5f)

        seekVibrationInterval.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val interval = progress + 3
                tvVibrationIntervalValue.text = "%.1f秒".format(interval * 0.5f)
                if (fromUser) {
                    prefs.edit().putInt("vibration_frame_interval", interval).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Alert gap interval slider
        val seekGapInterval: android.widget.SeekBar = root.findViewById(R.id.seekbar_alert_gap_interval)
        val tvGapIntervalValue: TextView = root.findViewById(R.id.tv_alert_gap_interval_value)
        val savedGapInterval = prefs.getInt("alert_gap_interval", 6)
        seekGapInterval.max = 17
        seekGapInterval.progress = (savedGapInterval - 3).coerceIn(0, 17)
        tvGapIntervalValue.text = "%.1f秒".format(savedGapInterval * 0.5f)

        seekGapInterval.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val interval = progress + 3
                tvGapIntervalValue.text = "%.1f秒".format(interval * 0.5f)
                if (fromUser) {
                    prefs.edit().putInt("alert_gap_interval", interval).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun setupZoomSensitivity(root: View) {
        val seekBar: android.widget.SeekBar = root.findViewById(R.id.seekbar_zoom_sensitivity)
        val valueText: android.widget.TextView = root.findViewById(R.id.tv_zoom_sensitivity_value)
        
        // SeekBar range: 0-250, but we invert: seekBar value 0 = sensitivity 250 (low), value 250 = sensitivity 0
        // Actually: sensitivity = max(50, 300 - seekBarValue) so higher seekbar = lower divisor = more sensitive
        val savedSensitivity = prefs.getInt("zoom_sensitivity", 150)
        val seekBarValue = 300 - savedSensitivity
        seekBar.max = 250
        seekBar.progress = seekBarValue.coerceIn(0, 250)
        valueText.text = savedSensitivity.toString()
        
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val sensitivity = (300 - progress).coerceAtLeast(50)
                valueText.text = sensitivity.toString()
                if (fromUser) {
                    prefs.edit().putInt("zoom_sensitivity", sensitivity).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun setupTileScale(root: View) {
        val seekBar: android.widget.SeekBar = root.findViewById(R.id.seekbar_tile_scale)
        val valueText: android.widget.TextView = root.findViewById(R.id.tv_tile_scale_value)
        
        // SeekBar range 0-20 maps to scale factor 1.0 to 3.0 (step 0.1)
        // value = 1.0 + progress * 0.1
        val savedScale = prefs.getFloat("tile_scale_factor", 1.5f)
        val seekBarValue = ((savedScale - 1.0f) * 10).toInt().coerceIn(0, 20)
        seekBar.max = 20
        seekBar.progress = seekBarValue
        valueText.text = "%.1f".format(savedScale)
        
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = 1.0f + progress * 0.1f
                valueText.text = "%.1f".format(scale)
                if (fromUser) {
                    prefs.edit().putFloat("tile_scale_factor", scale).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })
    }

    private fun setupMapType(root: View) {
        val radioGroup: RadioGroup = root.findViewById(R.id.radio_map_type)
        val mapType = prefs.getString("map_type", "vector")
        
        when (mapType) {
            "vector" -> root.findViewById<RadioButton>(R.id.radio_map_vector).isChecked = true
            "satellite" -> root.findViewById<RadioButton>(R.id.radio_map_satellite).isChecked = true
            "terrain" -> root.findViewById<RadioButton>(R.id.radio_map_terrain).isChecked = true
        }
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newType = when (checkedId) {
                R.id.radio_map_vector -> "vector"
                R.id.radio_map_satellite -> "satellite"
                R.id.radio_map_terrain -> "terrain"
                else -> "vector"
            }
            prefs.edit().putString("map_type", newType).apply()
        }
    }

    private fun setupTheme(root: View) {
        val radioGroup: RadioGroup = root.findViewById(R.id.radio_theme)
        val theme = prefs.getString("theme", "dark")
        
        when (theme) {
            "light" -> root.findViewById<RadioButton>(R.id.radio_theme_light).isChecked = true
            "dark" -> root.findViewById<RadioButton>(R.id.radio_theme_dark).isChecked = true
            "oled" -> root.findViewById<RadioButton>(R.id.radio_theme_oled).isChecked = true
        }
        
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_theme_light -> "light"
                R.id.radio_theme_dark -> "dark"
                R.id.radio_theme_oled -> "oled"
                else -> "dark"
            }
            prefs.edit().putString("theme", newTheme).apply()
            requireActivity().recreate()
        }
    }

    private fun setupDebug(root: View) {
        val switchMockGps: SwitchCompat = root.findViewById(R.id.switch_mock_gps)
        switchMockGps.isChecked = prefs.getBoolean("debug_mock_gps", false)
        switchMockGps.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("debug_mock_gps", isChecked).apply()
        }
    }
}
