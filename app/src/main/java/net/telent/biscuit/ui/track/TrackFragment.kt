package net.telent.biscuit.ui.track

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import net.telent.biscuit.BuildConfig
import net.telent.biscuit.BiscuitDatabase
import net.telent.biscuit.DistanceFormatter
import net.telent.biscuit.DoubleTapDragZoomOverlay
import net.telent.biscuit.R
import net.telent.biscuit.TianDiTuTileSource
import org.osmdroid.tileprovider.MapTileProviderBasic

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.IconOverlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

class TrackFragment : Fragment() {
    private lateinit var map : MapView
    private lateinit var youAreHere: IconOverlay
    private lateinit var tileLoadErrorView: TextView
    private val model: TrackViewModel by activityViewModels()
    private var tileLoadFailureCount = 0
    private val TILE_FAILURE_THRESHOLD = 3
    private var currentMapType: String = "vector"

    // GPS location - same approach as PositionSensor
    private var locationManager: LocationManager? = null
    private var lastGpsLocation: Location? = null

    // Feature 3: Auto-center on first fix + auto-follow
    private var hasInitialFix = false
    private var isAutoFollow = false
    private var btnAutoFollow: ImageButton? = null

    // Start/End markers
    private var startMarker: IconOverlay? = null
    private var endMarker: IconOverlay? = null

    // Zoom sensitivity real-time update
    private var currentZoomSensitivity: Int = 150

    // Feature 6: Mini dashboard
    private var mapDash1: TextView? = null
    private var mapDash2: TextView? = null
    private var mapDash3: TextView? = null

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            lastGpsLocation = loc
            if (isAdded && ::map.isInitialized) {
                val gp = GeoPoint(loc.latitude, loc.longitude)
                youAreHere.moveTo(gp, map)
                // Auto-center on first GPS fix
                if (!hasInitialFix) {
                    hasInitialFix = true
                    map.controller.animateTo(gp, 18.0, 500L)
                }
                // Auto-follow when enabled and recording
                if (isAutoFollow) {
                    map.controller.animateTo(gp)
                }
                map.invalidate()
            }
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_track, container, false)

        map = root.findViewById(R.id.map)
        tileLoadErrorView = root.findViewById(R.id.tv_tile_load_error)

        // Initialize TianDiTu token
        TianDiTuTileSource.init(BuildConfig.TIANDITU_TOKEN)

        // this will be replaced by our track marker.
        // we add it first so we know we can replace it at position 0
        map.overlayManager.add(Polyline())

        // Read map type from settings
        val prefs = requireContext().getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        val mapType = prefs.getString("map_type", "vector") ?: "vector"
        currentMapType = mapType
        val (baseTileSource, annotationSource) = TianDiTuTileSource.getTileSources(mapType)

        map.setTileSource(baseTileSource)
        map.overlayManager.tilesOverlay.loadingBackgroundColor = Color.TRANSPARENT
        map.overlayManager.tilesOverlay.loadingLineColor = Color.TRANSPARENT

        // Add annotation (Chinese labels) overlay
        val annotationProvider = MapTileProviderBasic(requireContext(), annotationSource)
        val annotationOverlay = TilesOverlay(annotationProvider, requireContext())
        annotationOverlay.loadingBackgroundColor = Color.TRANSPARENT
        map.overlays.add(annotationOverlay)

        map.zoomController.setZoomInEnabled(true)
        map.zoomController.setZoomOutEnabled(true)
        map.setMultiTouchControls(true)
        map.isTilesScaledToDpi = false
        val tileScale = prefs.getFloat("tile_scale_factor", 1.5f)
        map.tilesScaleFactor = tileScale
        map.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Read zoom sensitivity from settings
        val sensitivity = prefs.getInt("zoom_sensitivity", 150)
        currentZoomSensitivity = sensitivity
        map.overlays.add(DoubleTapDragZoomOverlay(map, sensitivity.toFloat()))

        // Monitor tile load failures
        map.addOnFirstLayoutListener { _, _, _, _, _ ->
            setupTileLoadFailureListener()
        }

        // Create a simple blue dot drawable
        val dotSize = (16 * resources.displayMetrics.density).toInt()
        val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setSize(dotSize, dotSize)
            setColor(Color.parseColor("#2196F3"))
            setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
        }
        youAreHere = IconOverlay(GeoPoint(0.0, 0.0), dotDrawable)
        map.overlays.add(youAreHere)

        // Default: China overview
        map.controller.setZoom(5.0)
        map.controller.setCenter(GeoPoint(35.0, 105.0))

        // Start GPS immediately (same as PositionSensor does)
        startGps()

        model.trackpoint.observe(viewLifecycleOwner) {
            if (it.lat != null && it.lng != null && (it.lat != 0.0 || it.lng != 0.0)) {
                val loc = GeoPoint(it.lat, it.lng)
                youAreHere.moveTo(loc, map)
                // Auto-follow when enabled
                if (isAutoFollow) {
                    map.controller.animateTo(loc)
                }
            }
            // Update mini dashboard
            updateMiniDashboard(it)
        }
        model.track.observe(viewLifecycleOwner ,{
            val line = Polyline()
            line.setPoints(it)
            map.overlayManager[0] = line

            // Remove old start/end markers
            startMarker?.let { m -> map.overlays.remove(m) }
            endMarker?.let { m -> map.overlays.remove(m) }
            startMarker = null
            endMarker = null

            if (it.size >= 2) {
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
                val sm = IconOverlay(it.first(), startDrawable)
                startMarker = sm
                // Insert before youAreHere (which is last)
                val youAreHereIndex = map.overlays.indexOf(youAreHere)
                if (youAreHereIndex >= 0) {
                    map.overlays.add(youAreHereIndex, sm)
                } else {
                    map.overlays.add(sm)
                }

                // End marker (red)
                val endDrawable = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setSize(markerSize, markerSize)
                    setColor(Color.parseColor("#F44336"))
                    setStroke(strokeWidth, Color.WHITE)
                }
                val em = IconOverlay(it.last(), endDrawable)
                endMarker = em
                val youAreHereIndex2 = map.overlays.indexOf(youAreHere)
                if (youAreHereIndex2 >= 0) {
                    map.overlays.add(youAreHereIndex2, em)
                } else {
                    map.overlays.add(em)
                }
            }
        })

        val locateButton = root.findViewById<ImageButton>(R.id.btn_locate_me)
        locateButton.setOnClickListener {
            // Priority 1: trackpoint from recording
            val tp = model.trackpoint.value
            if (tp?.lat != null && tp.lng != null && (tp.lat != 0.0 || tp.lng != 0.0)) {
                map.controller.animateTo(GeoPoint(tp.lat, tp.lng), 18.0, 500L)
                return@setOnClickListener
            }
            // Priority 2: our own GPS/network listener result
            val loc = lastGpsLocation
            if (loc != null) {
                map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 18.0, 500L)
                return@setOnClickListener
            }
            // Priority 3: try system last known location as final fallback
            @android.annotation.SuppressLint("MissingPermission")
            fun tryLastKnown(): Location? {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
                val lm = locationManager ?: return null
                val gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val net = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                return when {
                    gps != null && net != null -> if (gps.time > net.time) gps else net
                    gps != null -> gps
                    net != null -> net
                    else -> null
                }
            }
            val fallback = tryLastKnown()
            if (fallback != null) {
                lastGpsLocation = fallback
                youAreHere.moveTo(GeoPoint(fallback.latitude, fallback.longitude), map)
                map.controller.animateTo(GeoPoint(fallback.latitude, fallback.longitude), 18.0, 500L)
                return@setOnClickListener
            }
            // 使用字符串资源替代硬编码文本，支持国际化
            Toast.makeText(requireContext(), getString(R.string.toast_gps_waiting), Toast.LENGTH_SHORT).show()
        }

        // Auto-follow button
        btnAutoFollow = root.findViewById(R.id.btn_auto_follow)
        val savedAutoFollow = prefs.getBoolean("map_auto_follow", false)
        isAutoFollow = savedAutoFollow
        updateAutoFollowButton()

        btnAutoFollow?.setOnClickListener {
            isAutoFollow = !isAutoFollow
            prefs.edit().putBoolean("map_auto_follow", isAutoFollow).apply()
            updateAutoFollowButton()
            if (isAutoFollow) {
                // Immediately center on current position
                val tp = model.trackpoint.value
                if (tp?.lat != null && tp.lng != null && (tp.lat != 0.0 || tp.lng != 0.0)) {
                    map.controller.animateTo(GeoPoint(tp.lat, tp.lng), 18.0, 500L)
                } else {
                    val loc = lastGpsLocation
                    if (loc != null) {
                        map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 18.0, 500L)
                    }
                }
                // 使用字符串资源替代硬编码文本，支持国际化
                Toast.makeText(requireContext(), getString(R.string.toast_auto_follow_on), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.toast_auto_follow_off), Toast.LENGTH_SHORT).show()
            }
        }

        // Mini dashboard
        mapDash1 = root.findViewById(R.id.map_dash_1)
        mapDash2 = root.findViewById(R.id.map_dash_2)
        mapDash3 = root.findViewById(R.id.map_dash_3)

        return root
    }

    private fun updateAutoFollowButton() {
        btnAutoFollow?.alpha = if (isAutoFollow) 1.0f else 0.4f
    }

    /**
     * Build a SpannableString with label (small), value (normal), and unit (small).
     */
    private fun buildDashSpan(label: String, value: String, unit: String): CharSequence {
        val ssb = SpannableStringBuilder()
        val labelStart = 0
        ssb.append(label)
        ssb.append(" ")
        ssb.setSpan(RelativeSizeSpan(0.7f), labelStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        ssb.append(value)
        if (unit.isNotEmpty()) {
            val unitStart = ssb.length
            ssb.append(" ")
            ssb.append(unit)
            ssb.setSpan(RelativeSizeSpan(0.7f), unitStart, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return ssb
    }

    private fun updateMiniDashboard(tp: net.telent.biscuit.Trackpoint) {
        val prefs = requireContext().getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        
        // Build list of enabled display items with SpannableString formatting
        val items = mutableListOf<CharSequence>()
        if (prefs.getBoolean("display_speed", true)) {
            items.add(buildDashSpan("速度", String.format("%.1f", tp.speed), "km/h"))
        }
        if (prefs.getBoolean("display_cadence", true)) {
            items.add(buildDashSpan("踏频", String.format("%.0f", tp.cadence), "rpm"))
        }
        if (prefs.getBoolean("display_distance", true)) {
            val (distValue, distUnit) = DistanceFormatter.format(tp.distance)
            items.add(buildDashSpan("距离", distValue, distUnit))
        }
        if (prefs.getBoolean("display_time", true)) {
            val s = tp.movingTime.seconds
            items.add(buildDashSpan("时间", String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60), ""))
        }
        if (prefs.getBoolean("display_moving_duration", false)) {
            val s = tp.movingTime.seconds
            items.add(buildDashSpan("骑行", String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60), ""))
        }
        if (prefs.getBoolean("display_avg_speed", false)) {
            val movingSec = tp.movingTime.seconds
            if (movingSec > 0 && tp.distance > 0) {
                val avgKmh = (tp.distance / 1000.0) / (movingSec / 3600.0)
                items.add(buildDashSpan("均速", String.format("%.1f", avgKmh), "km/h"))
            } else {
                items.add(buildDashSpan("均速", "--", "km/h"))
            }
        }
        
        // Show first 3 items
        mapDash1?.text = items.getOrElse(0) { "--" as CharSequence }
        mapDash2?.text = items.getOrElse(1) { "--" as CharSequence }
        mapDash3?.text = items.getOrElse(2) { "--" as CharSequence }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startGps() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener)
        // Also listen to network provider for faster initial fix (especially indoors)
        try {
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, gpsListener)
            }
        } catch (_: Exception) {}
        // Try to use last known location immediately
        val lastGps = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val best = when {
            lastGps != null && lastNet != null -> if (lastGps.time > lastNet.time) lastGps else lastNet
            lastGps != null -> lastGps
            lastNet != null -> lastNet
            else -> null
        }
        if (best != null) {
            lastGpsLocation = best
            if (isAdded && ::map.isInitialized && ::youAreHere.isInitialized) {
                youAreHere.moveTo(GeoPoint(best.latitude, best.longitude), map)
                map.invalidate()
            }
        }
    }

    private fun stopGps() {
        try { locationManager?.removeUpdates(gpsListener) } catch (_: Exception) {}
    }

    private fun setupTileLoadFailureListener() {
        val tileProvider = map.tileProvider
        tileProvider.tileSource?.let {
            val handler = android.os.Handler(requireContext().mainLooper)
            val checkRunnable = object : Runnable {
                override fun run() {
                    if (!isAdded) return
                    val queueSize = tileProvider.queueSize
                    if (queueSize > 10) {
                        tileLoadFailureCount++
                        if (tileLoadFailureCount >= TILE_FAILURE_THRESHOLD) {
                            tileLoadErrorView.visibility = View.VISIBLE
                        }
                    } else {
                        if (tileLoadFailureCount > 0) {
                            tileLoadFailureCount = 0
                            tileLoadErrorView.visibility = View.GONE
                        }
                    }
                    handler.postDelayed(this, 5000)
                }
            }
            handler.postDelayed(checkRunnable, 5000)
        }
    }

    override fun onCreateOptionsMenu(menu : Menu, inflater : MenuInflater) {
        inflater.inflate(R.menu.track_action_menu, menu)
    }

    private val db by lazy {
        BiscuitDatabase.getInstance(requireActivity())
    }

    override fun onOptionsItemSelected(item : MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_tracks ->  popupTrackPicker(item)
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun popupTrackPicker(item: MenuItem): Boolean {
        val trackPickerView = layoutInflater.inflate(R.layout.track_picker, null)
        val layout = trackPickerView.findViewById<LinearLayout>(R.id.track_picker)
        val tracks = db.sessionDao().getClosed()
        tracks.observe(viewLifecycleOwner) {
            layout.removeAllViews()
            it.forEach { s ->
                val v = TextView(requireContext())
                v.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f)
                v.text = "${s.start} ${s.duration()}"
                v.setOnClickListener({ v ->
                    Log.d("hey", "clicked on ${s.start}")
                })
                v.setTextSize(TypedValue.COMPLEX_UNIT_PT, 9.0F)
                layout.addView(v)
            }
        }
        TrackPicker(trackPickerView).show(childFragmentManager, TrackPicker.TAG)
        return true
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshMapTypeIfNeeded()
        startGps()
    }

    private fun refreshMapTypeIfNeeded() {
        val prefs = requireContext().getSharedPreferences("biscuit_settings", Context.MODE_PRIVATE)
        val mapType = prefs.getString("map_type", "vector") ?: "vector"
        if (mapType != currentMapType) {
            currentMapType = mapType
            val (baseTileSource, annotationSource) = TianDiTuTileSource.getTileSources(mapType)

            map.setTileSource(baseTileSource)

            val overlayIndex = map.overlays.indexOfFirst { it is TilesOverlay }
            if (overlayIndex >= 0) {
                map.overlays.removeAt(overlayIndex)
                val annotationProvider = MapTileProviderBasic(requireContext(), annotationSource)
                val annotationOverlay = TilesOverlay(annotationProvider, requireContext())
                annotationOverlay.loadingBackgroundColor = Color.TRANSPARENT
                map.overlays.add(overlayIndex, annotationOverlay)
            }

            map.invalidate()
        }

        val tileScale = prefs.getFloat("tile_scale_factor", 1.5f)
        if (map.tilesScaleFactor != tileScale) {
            map.tilesScaleFactor = tileScale
        }

        // Refresh zoom sensitivity if changed
        val zoomSensitivity = prefs.getInt("zoom_sensitivity", 150)
        if (zoomSensitivity != currentZoomSensitivity) {
            currentZoomSensitivity = zoomSensitivity
            val oldOverlayIndex = map.overlays.indexOfFirst { it is DoubleTapDragZoomOverlay }
            if (oldOverlayIndex >= 0) {
                map.overlays.removeAt(oldOverlayIndex)
                map.overlays.add(oldOverlayIndex, DoubleTapDragZoomOverlay(map, zoomSensitivity.toFloat()))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        stopGps()
    }
}
