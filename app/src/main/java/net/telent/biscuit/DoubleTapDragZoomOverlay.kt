package net.telent.biscuit

import android.view.GestureDetector
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class DoubleTapDragZoomOverlay(
    private val mapView: MapView,
    private val sensitivity: Float = 150f
) : Overlay() {

    private var isDoubleTapDragging = false
    private var startY = 0f
    private var baseZoom = 0.0
    private var pinnedCenter: GeoPoint? = null

    private val gestureDetector = GestureDetector(mapView.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDoubleTapDragging = true
                    startY = e.y
                    baseZoom = mapView.zoomLevelDouble
                    // Pin the current center — we'll force it back after every zoom change
                    val c = mapView.mapCenter
                    pinnedCenter = GeoPoint(c.latitude, c.longitude)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDoubleTapDragging = false
                    pinnedCenter = null
                    return true
                }
            }
            return isDoubleTapDragging
        }
    })

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        gestureDetector.onTouchEvent(event)
        if (!isDoubleTapDragging) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val totalDelta = event.y - startY
                val zoomDelta = totalDelta / sensitivity.toDouble()
                val newZoom = (baseZoom + zoomDelta).coerceIn(
                    mapView.minZoomLevel.toDouble(),
                    mapView.maxZoomLevel.toDouble()
                )
                mapView.controller.setZoom(newZoom)
                // Force center back to pinned position to prevent drift
                pinnedCenter?.let { mapView.controller.setCenter(it) }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDoubleTapDragging = false
                pinnedCenter = null
            }
        }
        return isDoubleTapDragging
    }
}
