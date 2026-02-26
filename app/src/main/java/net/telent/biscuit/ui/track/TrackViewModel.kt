package net.telent.biscuit.ui.track

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import net.telent.biscuit.Trackpoint
import org.osmdroid.util.GeoPoint

class TrackViewModel : ViewModel() {

    private val _tpt = MutableLiveData<Trackpoint>().apply {
        value = Trackpoint(java.time.Instant.now(), null, null)
    }
    val trackpoint: LiveData<Trackpoint> = _tpt

    private val _track = MutableLiveData<ArrayList<GeoPoint>>()
    val track: LiveData<ArrayList<GeoPoint>> = _track

    private val _paused = MutableLiveData<Boolean>().apply { value = false }
    val paused: LiveData<Boolean> = _paused

    private val _manualPaused = MutableLiveData<Boolean>().apply { value = false }
    val manualPaused: LiveData<Boolean> = _manualPaused

    private val _lowSpeedAlert = MutableLiveData<Boolean>().apply { value = false }
    val lowSpeedAlert: LiveData<Boolean> = _lowSpeedAlert

    fun setPaused(paused: Boolean) {
        _paused.value = paused
    }

    fun setManualPaused(paused: Boolean) {
        _manualPaused.value = paused
    }

    fun setLowSpeedAlert(alert: Boolean) {
        _lowSpeedAlert.value = alert
    }

    fun move(tp: Trackpoint) {
        _tpt.value = tp
        if(tp.lat != null && tp.lng !=null) {
            val p = _track.value ?: ArrayList()
            p.add(GeoPoint(tp.lat, tp.lng))
            _track.value = p
        }
    }

    fun clearTrack() {
        _track.value = ArrayList()
    }

    fun get() {
        _tpt.value
    }
}