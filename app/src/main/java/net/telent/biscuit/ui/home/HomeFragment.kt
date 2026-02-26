package net.telent.biscuit.ui.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.color.MaterialColors
import net.telent.biscuit.BiscuitDatabase
import net.telent.biscuit.BiscuitService
import net.telent.biscuit.DistanceFormatter
import net.telent.biscuit.R
import net.telent.biscuit.VibrationHelper
import net.telent.biscuit.ui.track.TrackViewModel
import java.time.Instant
import java.time.Instant.EPOCH
import java.time.LocalDateTime
import java.time.ZoneId

class HomeFragment : Fragment() {
    private val model: TrackViewModel by activityViewModels()
    private lateinit var prefs: SharedPreferences
    private lateinit var displayContainer: LinearLayout

    private var speedText: FrameLayout? = null
    private var cadenceText: FrameLayout? = null
    private var distanceText: FrameLayout? = null
    private var timeText: FrameLayout? = null
    private var movingDurationText: FrameLayout? = null
    private var avgSpeedText: FrameLayout? = null

    private var lastMovingTime = EPOCH
    private val TAG = "HomeFragment"

    // Edge glow view for low-speed alert visual feedback
    private var edgeGlowView: EdgeGlowView? = null

    // Panel height management
    private var panelHeightManager: PanelHeightManager? = null
    private var currentWeights: MutableList<Float> = mutableListOf()
    private var currentVisibleKeys: List<String> = emptyList()

    // Recording controller
    private var recordButton: ImageButton? = null
    private var pauseButton: ImageButton? = null
    private var recordingDurationText: TextView? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0L
    private var accumulatedDurationMs: Long = 0L
    private val durationHandler = Handler(Looper.getMainLooper())
    private val durationRunnable = object : Runnable {
        override fun run() {
            if (isRecording && recordingStartTime > 0) {
                // Read the authoritative elapsed time from Service via SharedPreferences
                accumulatedDurationMs = prefs.getLong("accumulated_duration_ms", accumulatedDurationMs)
                recordingDurationText?.text = formatAccumulatedDuration(accumulatedDurationMs)
                durationHandler.postDelayed(this, 1000)
            }
        }
    }

    private val db by lazy {
        BiscuitDatabase.getInstance(requireActivity().applicationContext)
    }

    private val vibrationHelper by lazy {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        VibrationHelper(vibrator, requireContext())
    }

    private var vibrationRepeatHandler = Handler(Looper.getMainLooper())
    private var vibrationRepeatRunnable: Runnable? = null
    private var alertCycleRunning = false

    private var alertGapHandler = Handler(Looper.getMainLooper())
    private var alertGapRunnable: Runnable? = null
    private var lowSpeedAlertActive = false

    private var stopProgressController: LongPressProgressController? = null
    private var pauseProgressController: LongPressProgressController? = null
    private var pauseButtonContainer: FrameLayout? = null
    private var justCompletedPause = false

    /** Refresh display items when display_* or label size settings change. */
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && isAdded && view != null) {
            when {
                key.startsWith("display_") || key == "dashboard_label_size" -> {
                    setupDisplayItems()
                }
                key == "dashboard_font_weight" -> {
                    val weight = prefs.getInt("dashboard_font_weight", 700)
                    applyFontWeight(weight)
                }
                key == "glow_width_dp" -> {
                    val newWidth = prefs.getInt("glow_width_dp", 20)
                    edgeGlowView?.setGlowWidth(newWidth.toFloat())
                }
            }
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        prefs = requireContext().getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        displayContainer = root.findViewById(R.id.display_container)
        Log.d(TAG, "onCreateView prefs=${prefs.all}")

        setupDisplayItems()
        setupRecordingController(root)

        // Initialize edge glow view for low-speed alert
        edgeGlowView = root.findViewById(R.id.edge_glow_view)

        // Listen for settings changes to refresh display items in real-time
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        model.trackpoint.observe(viewLifecycleOwner) { tp ->
            Log.d(TAG, "Received trackpoint: $tp")
            val (timestamp, _, _, speed, cadence, _, movingTime, distance) = tp
            val now = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault())
            if (speed > 0) lastMovingTime = timestamp
            val showMovingTime = (lastMovingTime > timestamp.minusSeconds(30))
            val timestring = if (showMovingTime) {
                val s = movingTime.seconds
                String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60))
            } else {
                String.format("%2d:%02d:%02d", now.hour, now.minute, now.second)
            }

            getValueText(speedText)?.text = String.format("%.1f", speed)
            getUnitText(speedText)?.text = "km/h"

            getValueText(cadenceText)?.text = String.format("%.0f", cadence)
            getUnitText(cadenceText)?.text = "rpm"

            getValueText(timeText)?.text = timestring
            getUnitText(timeText)?.text = ""

            distanceText?.let { dt ->
                val (distValue, distUnit) = DistanceFormatter.format(distance)
                getValueText(dt)?.text = distValue
                getUnitText(dt)?.text = distUnit
            }

            movingDurationText?.let { dt ->
                val s = movingTime.seconds
                getValueText(dt)?.text = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60))
                getUnitText(dt)?.text = ""
            }

            avgSpeedText?.let { at ->
                val movingSec = movingTime.seconds
                if (movingSec > 0 && distance > 0) {
                    val avgKmh = (distance / 1000.0) / (movingSec / 3600.0)
                    getValueText(at)?.text = String.format("%.1f", avgKmh)
                } else {
                    getValueText(at)?.text = "--"
                }
                getUnitText(at)?.text = "km/h"
            }
        }

        // Observe pause state to update pause button icon and stop button visibility
        model.paused.observe(viewLifecycleOwner) { isPaused ->
            updatePauseButtonIcon()
            updateRecordingUI()
        }

        model.manualPaused.observe(viewLifecycleOwner) { isManualPaused ->
            updatePauseButtonIcon()
            updateRecordingUI()
        }

        // Observe low-speed alert state to control edge glow effect
        model.lowSpeedAlert.observe(viewLifecycleOwner) { lowSpeedAlert ->
            if (lowSpeedAlert) {
                lowSpeedAlertActive = true
                startAlertCycle()
            } else {
                lowSpeedAlertActive = false
                stopAlertCycle()
            }
        }


        return root
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        setupDisplayItems()
        restoreRecordingState()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) {
            Log.d(TAG, "onHiddenChanged visible")
            setupDisplayItems()
            restoreRecordingState()
        }
    }

    override fun onPause() {
        super.onPause()
        durationHandler.removeCallbacks(durationRunnable)
        stopProgressController?.release()
        pauseProgressController?.release()
        stopAlertCycle()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun setupDisplayItems() {
        displayContainer.removeAllViews()

        val showSpeed = prefs.getBoolean("display_speed", true)
        val showCadence = prefs.getBoolean("display_cadence", true)
        val showDistance = prefs.getBoolean("display_distance", true)
        val showTime = prefs.getBoolean("display_time", true)
        val showMovingDuration = prefs.getBoolean("display_moving_duration", false)
        val showAvgSpeed = prefs.getBoolean("display_avg_speed", false)
        Log.d(TAG, "setupDisplayItems speed=$showSpeed cadence=$showCadence distance=$showDistance time=$showTime movingDuration=$showMovingDuration avgSpeed=$showAvgSpeed")

        val items = mutableListOf<FrameLayout>()
        val visibleKeys = mutableListOf<String>()

        if (showSpeed) {
            speedText = createDisplayItem(DISPLAY_COLORS["speed"], DISPLAY_LABELS["speed"])
            items.add(speedText!!)
            visibleKeys.add("speed")
        } else { speedText = null }

        if (showCadence) {
            cadenceText = createDisplayItem(DISPLAY_COLORS["cadence"], DISPLAY_LABELS["cadence"])
            items.add(cadenceText!!)
            visibleKeys.add("cadence")
        } else { cadenceText = null }

        if (showDistance) {
            distanceText = createDisplayItem(DISPLAY_COLORS["distance"], DISPLAY_LABELS["distance"])
            items.add(distanceText!!)
            visibleKeys.add("distance")
        } else { distanceText = null }

        if (showTime) {
            timeText = createDisplayItem(DISPLAY_COLORS["time"], DISPLAY_LABELS["time"])
            items.add(timeText!!)
            visibleKeys.add("time")
        } else { timeText = null }

        if (showMovingDuration) {
            movingDurationText = createDisplayItem(DISPLAY_COLORS["movingDuration"], DISPLAY_LABELS["movingDuration"])
            items.add(movingDurationText!!)
            visibleKeys.add("movingDuration")
        } else { movingDurationText = null }

        if (showAvgSpeed) {
            avgSpeedText = createDisplayItem(DISPLAY_COLORS["avgSpeed"], DISPLAY_LABELS["avgSpeed"])
            items.add(avgSpeedText!!)
            visibleKeys.add("avgSpeed")
        } else { avgSpeedText = null }

        if (items.isEmpty()) {
            speedText = createDisplayItem(DISPLAY_COLORS["speed"], DISPLAY_LABELS["speed"])
            displayContainer.addView(speedText)
            currentVisibleKeys = listOf("speed")
            currentWeights = mutableListOf(1.0f)
            Log.d(TAG, "display items count=${displayContainer.childCount}")
            return
        }

        // Initialize PanelHeightManager and get weights
        val manager = PanelHeightManager(prefs)
        panelHeightManager = manager
        currentVisibleKeys = visibleKeys
        val weights = manager.getWeights(visibleKeys)
        currentWeights = weights.toMutableList()

        // Add items with weights, inserting DividerHandleViews between adjacent items
        for (i in items.indices) {
            // Set the weight from PanelHeightManager
            val lp = items[i].layoutParams as LinearLayout.LayoutParams
            lp.weight = currentWeights[i]
            items[i].layoutParams = lp

            displayContainer.addView(items[i])

            // Insert DividerHandle between adjacent items (not after the last one)
            if (items.size > 1 && i < items.size - 1) {
                val dividerIndex = i
                val divider = DividerHandleView(requireContext())

                divider.onDragListener = { deltaPixels ->
                    val containerHeightPx = displayContainer.height.toFloat()
                    if (containerHeightPx > 0f) {
                        val totalWeight = currentWeights.sum()
                        val deltaRatio = deltaPixels / containerHeightPx * totalWeight
                        val density = resources.displayMetrics.density
                        val containerHeightDp = containerHeightPx / density

                        val newWeights = manager.applyDrag(
                            currentWeights, dividerIndex, deltaRatio, containerHeightDp
                        )
                        currentWeights = newWeights.toMutableList()

                        // Update all DisplayItem weights
                        updateDisplayItemWeights()
                    }
                }

                divider.onDragEndListener = {
                    manager.saveWeights(currentWeights, currentVisibleKeys)
                }

                displayContainer.addView(divider)
            }
        }
        Log.d(TAG, "display items count=${displayContainer.childCount}")
    }

    /**
     * Update the weight of all DisplayItem views in the container from currentWeights.
     * Skips DividerHandleView children.
     */
    private fun updateDisplayItemWeights() {
        var weightIndex = 0
        for (i in 0 until displayContainer.childCount) {
            val child = displayContainer.getChildAt(i)
            if (child is DividerHandleView) continue
            if (weightIndex < currentWeights.size) {
                val lp = child.layoutParams as LinearLayout.LayoutParams
                lp.weight = currentWeights[weightIndex]
                child.layoutParams = lp
                weightIndex++
            }
        }
    }

    private fun createDisplayItem(color: Int? = null, label: String? = null): FrameLayout {
        val frame = FrameLayout(requireContext())
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        params.setMargins(4, 4, 4, 4)
        frame.layoutParams = params
        val surfaceColor = MaterialColors.getColor(frame, com.google.android.material.R.attr.colorSurfaceVariant)
        frame.setBackgroundColor(surfaceColor)

        // Value text (centered, auto-size)
        val tv = AppCompatTextView(requireContext())
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            tv,
            12,   // minTextSize sp
            200,  // maxTextSize sp
            2,    // stepGranularity sp
            TypedValue.COMPLEX_UNIT_SP
        )
        tv.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        tv.gravity = Gravity.CENTER
        val padding = (8 * resources.displayMetrics.density).toInt()
        tv.setPadding(padding, padding, padding, padding)
        tv.maxLines = 1
        val fontWeight = prefs.getInt("dashboard_font_weight", 700)
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, fontWeight, false)
        } else {
            if (fontWeight >= 500) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        tv.typeface = typeface
        tv.fontFeatureSettings = "'tnum'"
        if (color != null) {
            tv.setTextColor(color)
        } else {
            val onSurfaceColor = MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOnSurface)
            tv.setTextColor(onSurfaceColor)
        }
        tv.text = "--"
        tv.tag = "value"
        frame.addView(tv)

        // Unit text (bottom-right, fixed small font)
        val labelSize = prefs.getInt("dashboard_label_size", 10)
        val unitTv = TextView(requireContext())
        val unitParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.END
        )
        val unitPadding = (4 * resources.displayMetrics.density).toInt()
        unitTv.setPadding(0, 0, unitPadding, unitPadding)
        unitTv.layoutParams = unitParams
        unitTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSize.toFloat())
        val onSurfaceUnit = MaterialColors.getColor(frame, com.google.android.material.R.attr.colorOnSurface)
        unitTv.setTextColor(ColorUtils.setAlphaComponent(onSurfaceUnit, 128))
        unitTv.tag = "unit"
        frame.addView(unitTv)

        // Label text (bottom-left, small)
        if (label != null) {
            val labelTv = TextView(requireContext())
            val labelParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START
            )
            val labelPadding = (4 * resources.displayMetrics.density).toInt()
            labelTv.setPadding(labelPadding, 0, 0, labelPadding)
            labelTv.layoutParams = labelParams
            labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSize.toFloat())
            val onSurface = MaterialColors.getColor(frame, com.google.android.material.R.attr.colorOnSurface)
            labelTv.setTextColor(ColorUtils.setAlphaComponent(onSurface, 128))
            labelTv.text = label
            frame.addView(labelTv)
        }

        return frame
    }

    /** Get the value TextView from a display item FrameLayout. */
    private fun getValueText(frame: FrameLayout?): TextView? {
        return frame?.findViewWithTag("value") as? TextView
    }

    /** Get the unit TextView from a display item FrameLayout. */
    private fun getUnitText(frame: FrameLayout?): TextView? {
        return frame?.findViewWithTag("unit") as? TextView
    }

    private fun startAlertCycle() {
        if (!lowSpeedAlertActive || !isAdded || alertCycleRunning) return
        alertCycleRunning = true

        val durationInterval = prefs.getInt("vibration_frame_interval", VIBRATION_FRAME_INTERVAL)
        val durationMs = durationInterval * 500L
        val gapInterval = prefs.getInt("alert_gap_interval", ALERT_GAP_INTERVAL)
        val gapMs = gapInterval * 500L

        // Start repeating vibration pulses every 400ms during glow
        vibrationRepeatRunnable = object : Runnable {
            override fun run() {
                if (alertCycleRunning && lowSpeedAlertActive && isAdded) {
                    vibrationHelper.triggerLowSpeedAlertPulse()
                    vibrationRepeatHandler.postDelayed(this, 400L)
                }
            }
        }
        vibrationHelper.triggerLowSpeedAlertPulse()
        vibrationRepeatHandler.postDelayed(vibrationRepeatRunnable!!, 400L)

        // Set up callback for when glow finishes (single cycle ends)
        edgeGlowView?.glowFinishedListener = object : EdgeGlowView.OnGlowFinishedListener {
            override fun onGlowFinished() {
                // Stop vibration repeater
                vibrationRepeatRunnable?.let { vibrationRepeatHandler.removeCallbacks(it) }
                vibrationRepeatRunnable = null
                // Keep alertCycleRunning = true during gap to prevent observer re-triggering

                // Schedule next cycle after gap
                if (lowSpeedAlertActive && isAdded) {
                    alertGapRunnable = Runnable {
                        alertCycleRunning = false  // Reset BEFORE calling startAlertCycle
                        if (lowSpeedAlertActive && isAdded) {
                            startAlertCycle()
                        }
                    }
                    alertGapHandler.postDelayed(alertGapRunnable!!, gapMs)
                } else {
                    alertCycleRunning = false
                }
            }
        }

        // Start glow with single fade-in/fade-out cycle matching duration
        edgeGlowView?.startGlow(durationMs = durationMs)
    }

    private fun stopAlertCycle() {
        alertCycleRunning = false
        alertGapRunnable?.let { alertGapHandler.removeCallbacks(it) }
        alertGapRunnable = null
        vibrationRepeatRunnable?.let { vibrationRepeatHandler.removeCallbacks(it) }
        vibrationRepeatRunnable = null
        edgeGlowView?.glowFinishedListener = null
        edgeGlowView?.alphaUpdateListener = null
        edgeGlowView?.stopGlow()
    }

    /**
     * Apply the given font weight to all value TextViews in the display container.
     * On API 28+ uses variable font weight via Typeface.create(). On older APIs,
     * falls back to DEFAULT_BOLD (weight >= 500) or DEFAULT (weight < 500).
     */
    private fun applyFontWeight(weight: Int) {
        val typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            if (weight >= 500) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        for (i in 0 until displayContainer.childCount) {
            val child = displayContainer.getChildAt(i)
            if (child is DividerHandleView) continue
            val valueTextView = child.findViewWithTag<TextView>("value")
            valueTextView?.typeface = typeface
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupRecordingController(root: View) {
        recordButton = root.findViewById(R.id.btn_record)
        pauseButton = root.findViewById(R.id.btn_pause)
        recordingDurationText = root.findViewById(R.id.tv_recording_duration)

        // Restore state from SharedPreferences
        isRecording = prefs.getBoolean("is_recording", false)
        recordingStartTime = prefs.getLong("recording_start_time", 0L)

        updateRecordingUI()

        // Initialize circular progress for stop long-press
        val recordContainer = root.findViewById<FrameLayout>(R.id.record_button_container)
        val progressDrawable = CircularProgressDrawable(
            color = Color.RED,
            strokeWidth = 6f * resources.displayMetrics.density
        )
        recordContainer.foreground = progressDrawable

        stopProgressController = LongPressProgressController(
            vibrationHelper = vibrationHelper,
            progressDrawable = progressDrawable,
            durationMs = LONG_PRESS_DURATION,
            onComplete = { showStopDialog() }
        )

        recordButton?.setOnClickListener {
            if (!isRecording) {
                startRecording()
            }
            // When recording, short press is no-op (stop requires long press)
        }

        recordButton?.setOnTouchListener { _, event ->
            if (!isRecording) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    stopProgressController?.onPressStart()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopProgressController?.onPressEnd()
                    true
                }
                else -> false
            }
        }

        // Initialize circular progress for pause long-press
        pauseButtonContainer = root.findViewById(R.id.pause_button_container)
        val pauseProgressDrawable = CircularProgressDrawable(
            color = Color.parseColor("#2196F3"),
            strokeWidth = 4f * resources.displayMetrics.density
        )
        pauseButtonContainer?.foreground = pauseProgressDrawable

        pauseProgressController = LongPressProgressController(
            vibrationHelper = vibrationHelper,
            progressDrawable = pauseProgressDrawable,
            durationMs = LONG_PRESS_DURATION,
            onComplete = {
                justCompletedPause = true
                // Immediately update ViewModel so UI reflects pause state right away
                // (don't wait for BiscuitService broadcast which has up to 1s delay)
                model.setManualPaused(true)
                model.setPaused(true)

                val intent = Intent(requireContext(), BiscuitService::class.java)
                intent.putExtra("toggle_pause", true)
                sendToService(intent)
                Log.d(TAG, "Pause toggle sent to service (long-press confirmed)")
            }
        )

        pauseButton?.setOnTouchListener { _, event ->
            val isPaused = model.paused.value ?: false
            val isManualPaused = model.manualPaused.value ?: false
            val effectivelyPaused = isPaused || isManualPaused

            if (effectivelyPaused) {
                // Currently paused
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (justCompletedPause) {
                            // This ACTION_UP is from the long-press that just completed pause.
                            // Don't treat it as a resume tap.
                            justCompletedPause = false
                            Log.d(TAG, "Ignoring ACTION_UP after pause completion")
                        } else {
                            // Genuine resume tap
                            vibrationHelper.triggerResumeVibration()
                            model.setManualPaused(false)
                            model.setPaused(false)

                            val intent = Intent(requireContext(), BiscuitService::class.java)
                            intent.putExtra("toggle_pause", true)
                            sendToService(intent)
                            Log.d(TAG, "Resume sent to service (instant tap)")
                        }
                    }
                    MotionEvent.ACTION_DOWN -> {
                        // Absorb ACTION_DOWN when paused (no long-press needed for resume)
                    }
                }
                true
            } else {
                // Not paused: long-press to pause
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        justCompletedPause = false
                        pauseProgressController?.onPressStart()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pauseProgressController?.onPressEnd()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun updatePauseButtonIcon() {
        val isPaused = model.paused.value ?: false
        val isManualPaused = model.manualPaused.value ?: false
        if (isPaused || isManualPaused) {
            pauseButton?.setImageResource(R.drawable.ic_resume)
            pauseButton?.contentDescription = getString(R.string.recording_resume)
        } else {
            pauseButton?.setImageResource(R.drawable.ic_pause)
            pauseButton?.contentDescription = getString(R.string.recording_pause)
        }
    }

    private fun startRecording() {
        model.clearTrack()
        vibrationHelper.triggerResumeVibration()
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        accumulatedDurationMs = 0L

        // Reset all state for a fresh recording
        model.setManualPaused(false)
        model.setPaused(false)

        // Save state to SharedPreferences
        prefs.edit()
            .putBoolean("is_recording", true)
            .putLong("recording_start_time", recordingStartTime)
            .putLong("accumulated_duration_ms", 0L)
            .putLong("last_duration_tick_ms", 0L)
            .putBoolean("is_manual_paused", false)
            .apply()

        // Start BiscuitService
        val intent = Intent(requireContext(), BiscuitService::class.java)
        sendToService(intent)

        updateRecordingUI()
        startDurationUpdater()
        Log.d(TAG, "Recording started at $recordingStartTime")
    }

    private fun showStopDialog() {
        val context = requireContext()
        val editText = android.widget.EditText(context).apply {
            hint = getString(R.string.dialog_input_hint)
            setPadding(48, 32, 48, 16)
        }

        // 使用字符串资源替代硬编码文本，支持国际化；添加取消按钮防止误触
        android.app.AlertDialog.Builder(context)
            .setTitle(getString(R.string.dialog_stop_title))
            .setView(editText)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                val name = editText.text.toString().trim()
                stopRecording(name.ifEmpty { null })
            }
            .setNegativeButton(getString(R.string.dialog_discard)) { _, _ ->
                discardRecording()
            }
            .setNeutralButton(getString(R.string.dialog_cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun stopRecording(sessionName: String? = null) {
        isRecording = false
        accumulatedDurationMs = 0L
        model.clearTrack()
        model.setManualPaused(false)
        model.setPaused(false)
        durationHandler.removeCallbacks(durationRunnable)

        prefs.edit()
            .putBoolean("is_recording", false)
            .putLong("recording_start_time", 0L)
            .putLong("accumulated_duration_ms", 0L)
            .putLong("last_duration_tick_ms", 0L)
            .apply()

        val stopIntent = Intent(requireContext(), BiscuitService::class.java)
        stopIntent.putExtra("stop_service", 1)
        sendToService(stopIntent)

        Thread {
            try {
                val now = java.time.Instant.now()
                db.sessionDao().close(now)
                // 使用数据库保存骑行名称，替代 SharedPreferences
                if (!sessionName.isNullOrBlank()) {
                    val startInstant = java.time.Instant.ofEpochMilli(recordingStartTime)
                    db.sessionDao().updateName(startInstant, sessionName)
                }
                Log.d(TAG, "Session closed with name=$sessionName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close session", e)
            }
        }.start()

        updateRecordingUI()
        Log.d(TAG, "Recording stopped")
    }

    private fun discardRecording() {
        val startTime = recordingStartTime
        isRecording = false
        accumulatedDurationMs = 0L
        model.clearTrack()
        model.setManualPaused(false)
        model.setPaused(false)
        durationHandler.removeCallbacks(durationRunnable)

        prefs.edit()
            .putBoolean("is_recording", false)
            .putLong("recording_start_time", 0L)
            .putLong("accumulated_duration_ms", 0L)
            .putLong("last_duration_tick_ms", 0L)
            .apply()

        val stopIntent = Intent(requireContext(), BiscuitService::class.java)
        stopIntent.putExtra("stop_service", 1)
        sendToService(stopIntent)

        // Delete the session and its trackpoints
        Thread {
            try {
                val now = java.time.Instant.now()
                db.sessionDao().close(now)
                // Find and delete the session we just closed
                val startInstant = java.time.Instant.ofEpochMilli(startTime)
                db.trackpointDao().deleteByTimeRange(startInstant, now)
                db.sessionDao().deleteByStart(startInstant)
                Log.d(TAG, "Session and trackpoints discarded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to discard session", e)
            }
        }.start()

        updateRecordingUI()
        Log.d(TAG, "Recording discarded")
    }

    private fun updateRecordingUI() {
        val isPaused = model.paused.value ?: false
        val isManualPaused = model.manualPaused.value ?: false
        val effectivelyPaused = isPaused || isManualPaused

        if (isRecording) {
            recordingDurationText?.visibility = View.VISIBLE
            pauseButtonContainer?.visibility = View.VISIBLE
            updatePauseButtonIcon()

            // Stop button only visible when paused
            val recordContainer = view?.findViewById<FrameLayout>(R.id.record_button_container)
            if (effectivelyPaused) {
                recordContainer?.visibility = View.VISIBLE
                recordButton?.setImageResource(R.drawable.ic_record_stop)
                recordButton?.contentDescription = getString(R.string.recording_stop)
            } else {
                recordContainer?.visibility = View.GONE
            }
        } else {
            // Not recording - show start button
            val recordContainer = view?.findViewById<FrameLayout>(R.id.record_button_container)
            recordContainer?.visibility = View.VISIBLE
            recordButton?.setImageResource(R.drawable.ic_record_start)
            recordButton?.contentDescription = getString(R.string.recording_start)
            recordingDurationText?.visibility = View.GONE
            recordingDurationText?.text = ""
            pauseButtonContainer?.visibility = View.GONE
        }
    }

    private fun restoreRecordingState() {
        isRecording = prefs.getBoolean("is_recording", false)
        recordingStartTime = prefs.getLong("recording_start_time", 0L)
        accumulatedDurationMs = prefs.getLong("accumulated_duration_ms", 0L)

        // If recording flag is set but service appears dead (no tick in 30s), reset
        if (isRecording) {
            val lastTick = prefs.getLong("last_duration_tick_ms", 0L)
            val staleMs = System.currentTimeMillis() - lastTick
            if (lastTick > 0 && staleMs > 30_000) {
                Log.w(TAG, "Recording state stale (${staleMs}ms since last tick), resetting")
                isRecording = false
                accumulatedDurationMs = 0L
                prefs.edit()
                    .putBoolean("is_recording", false)
                    .putLong("recording_start_time", 0L)
                    .putLong("accumulated_duration_ms", 0L)
                    .putLong("last_duration_tick_ms", 0L)
                    .putBoolean("is_manual_paused", false)
                    .apply()
            }
        }

        updateRecordingUI()

        if (isRecording && recordingStartTime > 0) {
            startDurationUpdater()
        }
    }

    private fun startDurationUpdater() {
        durationHandler.removeCallbacks(durationRunnable)
        durationRunnable.run()
    }

    /** Send an intent to BiscuitService, using startForegroundService on O+ for reliability. */
    private fun sendToService(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }

    companion object {
        const val LONG_PRESS_DURATION = 3000L
        const val VIBRATION_FRAME_INTERVAL = 6
        const val ALERT_GAP_INTERVAL = 6  // default gap = 6 * 500ms = 3 seconds

        val DISPLAY_COLOR_HEX = mapOf(
            "speed"          to "#4CAF50", // green
            "cadence"        to "#2196F3", // blue
            "distance"       to "#FF9800", // orange
            "time"           to "#9C27B0", // purple
            "movingDuration" to "#F44336", // red
            "avgSpeed"       to "#00BCD4"  // cyan
        )
        val DISPLAY_COLORS = DISPLAY_COLOR_HEX.mapValues { Color.parseColor(it.value) }

        /** Chinese labels for each display item type. */
        val DISPLAY_LABELS = mapOf(
            "speed"          to "速度",
            "cadence"        to "踏频",
            "distance"       to "距离",
            "time"           to "时间",
            "movingDuration" to "骑行时长",
            "avgSpeed"       to "均速"
        )

        /** Ordered list of display item keys matching settings checkbox order. */
        val DISPLAY_KEYS = listOf("speed", "cadence", "distance", "time", "movingDuration", "avgSpeed")

        fun formatAccumulatedDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
