package net.telent.biscuit.ui.history

import android.animation.ValueAnimator
import android.content.ContentValues
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.android.material.color.MaterialColors
import net.telent.biscuit.BuildConfig
import net.telent.biscuit.BiscuitDatabase
import net.telent.biscuit.CalorieCalculator
import net.telent.biscuit.DistanceFormatter
import net.telent.biscuit.DoubleTapDragZoomOverlay
import net.telent.biscuit.GpxSerializer
import net.telent.biscuit.KmSplit
import net.telent.biscuit.R
import net.telent.biscuit.RideStatsCalculator
import net.telent.biscuit.Session
import net.telent.biscuit.SpeedColorMapper
import net.telent.biscuit.TianDiTuTileSource
import net.telent.biscuit.Waypoint
import net.telent.biscuit.Trackpoint
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryDetailFragment : Fragment() {

    private val db by lazy { BiscuitDatabase.getInstance(requireActivity()) }

    private var sessionStartMillis: Long = 0L
    private var session: Session? = null
    private var trackpoints: List<Trackpoint> = emptyList()

    /** Coroutine scope tied to the Fragment's view lifecycle; cancelled in onDestroyView. */
    private var fragmentScope: CoroutineScope? = null

    // Views
    private lateinit var scrollContent: ScrollView
    private lateinit var mapView: MapView
    private lateinit var mapContainer: FrameLayout
    private lateinit var btnMapToggle: ImageButton
    private lateinit var tvTotalDistance: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var tvRecordingTime: TextView
    private lateinit var tvCalories: TextView
    private lateinit var tvAvgSpeed: TextView
    private lateinit var tvAvgSpeedDetail: TextView
    private lateinit var tvMaxSpeed: TextView
    private lateinit var chartSpeed: LineChart
    private lateinit var paceTableRows: LinearLayout
    private lateinit var btnExpandPace: TextView
    private lateinit var btnExportGpx: Button

    // Map expansion state
    private var isMapExpanded = false
    private val MAP_COLLAPSED_HEIGHT_DP = 200

    // Pace table expand state
    private var isPaceExpanded = false
    private var allPaceRows: List<View> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionStartMillis = arguments?.getLong("session_start", 0L) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_history_detail, container, false)

        scrollContent = root.findViewById(R.id.scroll_content)
        mapView = root.findViewById(R.id.map_detail)
        mapContainer = root.findViewById(R.id.map_container)
        btnMapToggle = root.findViewById(R.id.btn_map_toggle)
        tvTotalDistance = root.findViewById(R.id.tv_total_distance)
        tvDate = root.findViewById(R.id.tv_date)
        tvTotalTime = root.findViewById(R.id.tv_total_time)
        tvRecordingTime = root.findViewById(R.id.tv_recording_time)
        tvCalories = root.findViewById(R.id.tv_calories)
        tvAvgSpeed = root.findViewById(R.id.tv_avg_speed)
        tvAvgSpeedDetail = root.findViewById(R.id.tv_avg_speed_detail)
        tvMaxSpeed = root.findViewById(R.id.tv_max_speed)
        chartSpeed = root.findViewById(R.id.chart_speed)
        paceTableRows = root.findViewById(R.id.pace_table_rows)
        btnExpandPace = root.findViewById(R.id.btn_expand_pace)
        btnExportGpx = root.findViewById(R.id.btn_export_gpx)

        // Back button
        root.findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // Prevent ScrollView from intercepting map touch events when expanded
        mapView.setOnTouchListener { v, event ->
            if (isMapExpanded) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        scrollContent.requestDisallowInterceptTouchEvent(true)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        scrollContent.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
            false // let the map handle the event
        }

        // Map expand/collapse button
        btnMapToggle.setOnClickListener {
            toggleMapExpansion()
        }

        // Create a coroutine scope tied to this view's lifecycle
        fragmentScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        loadSessionData()

        return root
    }

    private fun toggleMapExpansion() {
        val displayMetrics = resources.displayMetrics
        val collapsedPx = (MAP_COLLAPSED_HEIGHT_DP * displayMetrics.density).toInt()
        val expandedPx = displayMetrics.heightPixels

        val startHeight = if (isMapExpanded) expandedPx else collapsedPx
        val endHeight = if (isMapExpanded) collapsedPx else expandedPx

        val animator = ValueAnimator.ofInt(startHeight, endHeight)
        animator.duration = 300
        animator.addUpdateListener {
            val params = mapContainer.layoutParams
            params.height = it.animatedValue as Int
            mapContainer.layoutParams = params
        }
        animator.start()
        isMapExpanded = !isMapExpanded

        // Update button icon
        if (isMapExpanded) {
            btnMapToggle.setImageResource(android.R.drawable.ic_menu_revert)
            // 使用字符串资源替代硬编码文本，支持国际化
            btnMapToggle.contentDescription = getString(R.string.map_collapse)
            scrollContent.smoothScrollTo(0, 0)
        } else {
            btnMapToggle.setImageResource(android.R.drawable.ic_menu_crop)
            // 使用字符串资源替代硬编码文本，支持国际化
            btnMapToggle.contentDescription = getString(R.string.map_expand)
        }
    }

    private fun loadSessionData() {
        if (sessionStartMillis == 0L) return

        fragmentScope?.launch {
            try {
                val startInstant = Instant.ofEpochMilli(sessionStartMillis)
                val loadedSession = withContext(Dispatchers.IO) {
                    db.sessionDao().getSessionByStart(startInstant)
                }
                session = loadedSession

                val s = loadedSession ?: return@launch
                val endInstant = s.end ?: return@launch

                trackpoints = withContext(Dispatchers.IO) {
                    db.trackpointDao()
                        .getTrackpointsForSession(startInstant, endInstant)
                }

                setupMap()
                populateStats()
            } catch (_: Exception) {
            }
        }
    }

    private fun setupMap() {
        TianDiTuTileSource.init(BuildConfig.TIANDITU_TOKEN)

        // Read map type from settings
        val prefs = requireContext().getSharedPreferences("biscuit_settings", android.content.Context.MODE_PRIVATE)
        val mapType = prefs.getString("map_type", "vector") ?: "vector"
        val (baseTileSource, annotationSource) = TianDiTuTileSource.getTileSources(mapType)

        mapView.setTileSource(baseTileSource)
        mapView.overlayManager.tilesOverlay.loadingBackgroundColor = Color.TRANSPARENT
        mapView.overlayManager.tilesOverlay.loadingLineColor = Color.TRANSPARENT

        val annotationProvider = MapTileProviderBasic(requireContext(), annotationSource)
        val annotationOverlay = TilesOverlay(annotationProvider, requireContext())
        annotationOverlay.loadingBackgroundColor = Color.TRANSPARENT
        mapView.overlays.add(annotationOverlay)

        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = false
        val tileScale = prefs.getFloat("tile_scale_factor", 1.5f)
        mapView.tilesScaleFactor = tileScale
        mapView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        val sensitivity = prefs.getInt("zoom_sensitivity", 150)
        mapView.overlays.add(DoubleTapDragZoomOverlay(mapView, sensitivity.toFloat()))

        val validPoints = trackpoints.filter { it.lat != null && it.lng != null }

        if (validPoints.isEmpty()) {
            mapContainer.visibility = View.GONE
            return
        }

        // Filter using the same low speed filter settings as in Settings
        val lowSpeedFilterEnabled = prefs.getBoolean("gps_low_speed_filter", true)
        val lowSpeedThreshold = prefs.getFloat("gps_low_speed_threshold", 3f)
        val pointsForMap = if (lowSpeedFilterEnabled) {
            val filtered = validPoints.filter { it.speed >= lowSpeedThreshold }
            if (filtered.size >= 2) filtered else validPoints
        } else {
            validPoints
        }

        val geoPoints = pointsForMap.map { GeoPoint(it.lat!!, it.lng!!) }

        // 按速度着色的多段轨迹
        if (pointsForMap.size >= 2) {
            val speeds = pointsForMap.map { it.speed }
            val validSpeeds = speeds.filter { it > 0f }
            val minSpeed = if (validSpeeds.isNotEmpty()) validSpeeds.min() else 0f
            val maxSpeed = if (validSpeeds.isNotEmpty()) validSpeeds.max() else 1f

            for (i in 0 until geoPoints.size - 1) {
                val segment = Polyline()
                segment.setPoints(listOf(geoPoints[i], geoPoints[i + 1]))
                val avgSpeed = (pointsForMap[i].speed + pointsForMap[i + 1].speed) / 2f
                segment.outlinePaint.color = SpeedColorMapper.getColor(avgSpeed, minSpeed, maxSpeed)
                segment.outlinePaint.strokeWidth = 8f
                segment.outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                segment.outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                mapView.overlays.add(segment)
            }
        } else {
            val polyline = Polyline()
            polyline.setPoints(geoPoints)
            polyline.outlinePaint.color = Color.parseColor("#4CAF50")
            polyline.outlinePaint.strokeWidth = 8f
            mapView.overlays.add(polyline)
        }

        // Add start/end markers
        if (geoPoints.size >= 2) {
            val density = resources.displayMetrics.density
            val markerSize = (12 * density).toInt()
            val strokeWidth = (2 * density).toInt()

            // Start marker (green)
            val startDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setSize(markerSize, markerSize)
                setColor(Color.parseColor("#4CAF50"))
                setStroke(strokeWidth, Color.WHITE)
            }
            mapView.overlays.add(org.osmdroid.views.overlay.IconOverlay(geoPoints.first(), startDrawable))

            // End marker (red)
            val endDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setSize(markerSize, markerSize)
                setColor(Color.parseColor("#F44336"))
                setStroke(strokeWidth, Color.WHITE)
            }
            mapView.overlays.add(org.osmdroid.views.overlay.IconOverlay(geoPoints.last(), endDrawable))
        }

        val boundingBox = BoundingBox.fromGeoPoints(geoPoints)
        mapView.post {
            mapView.zoomToBoundingBox(boundingBox, true, 50)
        }

        // 显示路径标记点
        displayWaypoints()
    }

    /** 从 SharedPreferences 读取并显示路径标记点 */
    private fun displayWaypoints() {
        val prefs = requireContext().getSharedPreferences("biscuit_settings", android.content.Context.MODE_PRIVATE)
        val waypointsKey = "waypoints_$sessionStartMillis"
        val json = prefs.getString(waypointsKey, null) ?: return
        val waypoints = Waypoint.fromJson(json)
        if (waypoints.isEmpty()) return

        val density = resources.displayMetrics.density
        for (wp in waypoints) {
            val size = (24 * density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)

            // 蓝色圆点 + 白色描边
            val circlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2196F3")
                style = android.graphics.Paint.Style.FILL
            }
            val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2 * density
            }
            val cx = size / 2f
            val cy = size / 2f
            val radius = size / 2f - 2 * density
            canvas.drawCircle(cx, cy, radius, circlePaint)
            canvas.drawCircle(cx, cy, radius, strokePaint)

            // 标号文字
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11 * density
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText("${wp.index}", cx, textY, textPaint)

            val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            mapView.overlays.add(org.osmdroid.views.overlay.IconOverlay(GeoPoint(wp.lat, wp.lng), drawable))
        }
    }

    private fun populateStats() {
        val s = session ?: return
        val stats = RideStatsCalculator.calculateStats(trackpoints)

        // Total distance
        tvTotalDistance.text = DistanceFormatter.formatFull((stats.totalDistanceKm * 1000).toFloat())

        // Date
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
        tvDate.text = formatter.format(s.start)

        // 从数据库读取骑行名称，替代 SharedPreferences
        val sessionName = s.name
        if (!sessionName.isNullOrBlank()) {
            tvDate.text = "$sessionName  ${formatter.format(s.start)}"
        }

        // Moving time
        val movingTimeSec = if (trackpoints.isNotEmpty()) {
            trackpoints.last().movingTime.seconds
        } else {
            stats.totalTimeSeconds
        }
        val displayTimeSec = if (movingTimeSec > 0) movingTimeSec else stats.totalTimeSeconds
        val hours = displayTimeSec / 3600
        val minutes = (displayTimeSec % 3600) / 60
        val seconds = displayTimeSec % 60
        tvTotalTime.text = "%d:%02d:%02d".format(hours, minutes, seconds)

        // Recording time (wall-clock from first to last trackpoint, i.e. session duration)
        val recordingTimeSec = if (trackpoints.size >= 2) {
            java.time.Duration.between(trackpoints.first().timestamp, trackpoints.last().timestamp).seconds
        } else {
            s.duration().seconds
        }
        val rh = recordingTimeSec / 3600
        val rm = (recordingTimeSec % 3600) / 60
        val rs = recordingTimeSec % 60
        tvRecordingTime.text = "%d:%02d:%02d".format(rh, rm, rs)

        // Calories
        val ridingTimeHours = displayTimeSec / 3600.0
        val calories = CalorieCalculator.calculate(ridingTimeHours, stats.avgSpeedKmh)
        tvCalories.text = "%.0f".format(calories)

        // Average speed
        tvAvgSpeed.text = "%.1f".format(stats.avgSpeedKmh)
        tvAvgSpeedDetail.text = "%.1f".format(stats.avgSpeedKmh)

        // Max speed
        tvMaxSpeed.text = "%.1f".format(stats.maxSpeedKmh)

        // Charts
        setupSpeedChart()

        // Pace table
        setupPaceTable()

        // GPX export
        if (trackpoints.isEmpty()) {
            btnExportGpx.isEnabled = false
        } else {
            btnExportGpx.isEnabled = true
            btnExportGpx.setOnClickListener { exportGpx() }
        }
    }

    private fun setupPaceTable() {
        val splits = RideStatsCalculator.calculateKmSplits(trackpoints)

        // Always show the pace table section
        view?.findViewById<LinearLayout>(R.id.section_pace_table)?.visibility = View.VISIBLE

        if (splits.isEmpty()) {
            paceTableRows.removeAllViews()
            btnExpandPace.visibility = View.GONE
            // Show a "no data" message
            val noDataText = TextView(requireContext()).apply {
                text = "距离不足，暂无配速数据"
                textSize = 13f
                setTextColor(MaterialColors.getColor(requireView(), android.R.attr.textColorSecondary))
                gravity = Gravity.CENTER
                setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (16 * resources.displayMetrics.density).toInt())
            }
            paceTableRows.addView(noDataText)
            return
        }

        val maxSpeed = splits.maxOf { it.speedKmh }.coerceAtLeast(1.0)

        paceTableRows.removeAllViews()
        allPaceRows = splits.map { split -> createPaceRow(split, maxSpeed) }

        val initialCount = minOf(5, allPaceRows.size)
        for (i in 0 until initialCount) {
            paceTableRows.addView(allPaceRows[i])
        }

        if (allPaceRows.size > 5) {
            btnExpandPace.visibility = View.VISIBLE
            btnExpandPace.setOnClickListener {
                if (isPaceExpanded) {
                    while (paceTableRows.childCount > 5) {
                        paceTableRows.removeViewAt(paceTableRows.childCount - 1)
                    }
                    btnExpandPace.text = "展开全部数据 ▼"
                    isPaceExpanded = false
                } else {
                    for (i in 5 until allPaceRows.size) {
                        paceTableRows.addView(allPaceRows[i])
                    }
                    btnExpandPace.text = "收起 ▲"
                    isPaceExpanded = true
                }
            }
        } else {
            btnExpandPace.visibility = View.GONE
        }
    }

    private fun createPaceRow(split: KmSplit, maxSpeed: Double): View {
        val density = resources.displayMetrics.density
        val context = requireContext()
        val textPrimaryColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface)
        val textSecondaryColor = MaterialColors.getColor(requireView(), android.R.attr.textColorSecondary)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
            gravity = Gravity.CENTER_VERTICAL
        }

        // Km number
        val tvKm = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (50 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = split.kmNumber.toString()
            setTextColor(textPrimaryColor)
            textSize = 13f
        }
        row.addView(tvKm)

        // Speed bar + value container
        val barContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val barWidthFraction = (split.speedKmh / maxSpeed).coerceIn(0.0, 1.0)
        val bar = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                (16 * density).toInt(),
                barWidthFraction.toFloat()
            )
            setBackgroundColor(Color.parseColor("#4CAF50"))
        }
        barContainer.addView(bar)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                (16 * density).toInt(),
                (1.0 - barWidthFraction).toFloat()
            )
        }
        barContainer.addView(spacer)

        val tvSpeed = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (6 * density).toInt()
            }
            text = "%.1f".format(split.speedKmh)
            setTextColor(textPrimaryColor)
            textSize = 12f
        }
        barContainer.addView(tvSpeed)

        row.addView(barContainer)

        // Cumulative time
        val tvTime = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (100 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = split.cumulativeTimeFormatted
            setTextColor(textSecondaryColor)
            textSize = 12f
            gravity = Gravity.END
        }
        row.addView(tvTime)

        return row
    }

    private fun setupSpeedChart() {
        if (trackpoints.size < 2) {
            chartSpeed.visibility = View.GONE
            return
        }

        val themeTextColor = MaterialColors.getColor(requireView(), com.google.android.material.R.attr.colorOnSurface)

        val firstTimestamp = trackpoints.first().timestamp.toEpochMilli()
        val entries = trackpoints
            .filter { it.speed >= 0f }
            .map { tp ->
                val timeMinutes = (tp.timestamp.toEpochMilli() - firstTimestamp) / 60000f
                Entry(timeMinutes, tp.speed)
            }

        if (entries.isEmpty()) {
            chartSpeed.visibility = View.GONE
            return
        }

        val dataSet = LineDataSet(entries, "速度").apply {
            color = Color.parseColor("#4CAF50")
            setDrawCircles(false)
            lineWidth = 2f
            setDrawFilled(true)
            fillColor = Color.parseColor("#4CAF50")
            fillAlpha = 50
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            isHighlightEnabled = false
        }

        configureSpeedChart(chartSpeed, dataSet, themeTextColor)
    }

    companion object {
        /**
         * Configures the speed chart with the given data set and theme color.
         * Extracted for testability.
         */
        fun configureSpeedChart(chart: LineChart, dataSet: LineDataSet, themeTextColor: Int) {
            chart.apply {
                data = LineData(dataSet)

                // Disable corner description, use axis labels instead
                description.isEnabled = false

                legend.isEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = themeTextColor
                    granularity = 1f
                    setDrawGridLines(false)
                    // X axis label
                    setLabelCount(5, false)
                }
                axisLeft.apply {
                    textColor = themeTextColor
                    axisMinimum = 0f
                    setDrawGridLines(true)
                    gridColor = Color.argb(50, 128, 128, 128)
                }
                axisRight.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true

                setScaleEnabled(false)
                isScaleXEnabled = false
                isScaleYEnabled = false
                setPinchZoom(false)

                isHighlightPerTapEnabled = false
                isHighlightPerDragEnabled = false

                // Add axis labels using description workaround:
                // MPAndroidChart doesn't have native axis titles, so we use
                // the description positioned at bottom-right as speed indicator
                description.isEnabled = true
                description.text = "时间(分钟) → 速度(km/h)"
                description.textColor = themeTextColor
                description.textSize = 10f

                invalidate()
            }
        }
    }

    private fun exportGpx() {
        val s = session ?: return
        val nameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
            .withZone(ZoneId.systemDefault())
        val trackName = "Biscuit_${nameFormatter.format(s.start)}"

        try {
            val gpxXml = GpxSerializer.serialize(trackpoints, trackName)

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "$trackName.gpx")
                put(MediaStore.Downloads.MIME_TYPE, "application/gpx+xml")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = requireContext().contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues
            )
            if (uri != null) {
                requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(gpxXml.toByteArray())
                }
                Toast.makeText(
                    requireContext(),
                    "GPX 已保存到 Downloads/$trackName.gpx",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(requireContext(), "GPX 导出失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "GPX 导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel the fragment scope to avoid leaks when the view is destroyed (e.g. theme switch)
        fragmentScope?.cancel()
        fragmentScope = null
    }
}
