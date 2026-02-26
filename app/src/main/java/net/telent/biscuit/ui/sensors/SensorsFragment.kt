package net.telent.biscuit.ui.sensors

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.color.MaterialColors
import net.telent.biscuit.ISensor
import net.telent.biscuit.R
import net.telent.biscuit.SensorSummary

class SensorsFragment : Fragment() {
    private val model: SensorsViewModel by activityViewModels()
    private val TAG = "SensorsFragment"

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_sensors, container, false)
        Log.d(TAG, "onCreateView")
        
        val speedStatus: TextView = root.findViewById(R.id.speed_status)
        val cadenceStatus: TextView = root.findViewById(R.id.cadence_status)
        val heartStatus: TextView = root.findViewById(R.id.heart_status)
        val locationStatus: TextView = root.findViewById(R.id.location_status)
        
        val onSurface = MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface)
        val errorColor = MaterialColors.getColor(root, com.google.android.material.R.attr.colorError)
        model.states.observe(viewLifecycleOwner, { summaries ->
            Log.d(TAG, "sensor summaries size=${summaries.entries.size}")
            summaries.entries.forEach { summary ->
                Log.d(TAG, "sensor summary name=${summary.name} state=${summary.state} sensorName=${summary.sensorName}")
                val statusText = when (summary.state) {
                    ISensor.SensorState.PRESENT -> "已连接: ${summary.sensorName}"
                    ISensor.SensorState.SEARCHING -> "搜索中..."
                    ISensor.SensorState.ABSENT -> "未检测到"
                    ISensor.SensorState.BROKEN -> "设备故障"
                    ISensor.SensorState.FORBIDDEN -> "权限被拒绝"
                }
                
                val isConnected = summary.state == ISensor.SensorState.PRESENT
                val statusColor = if (isConnected) onSurface else errorColor
                
                when (summary.name) {
                    "speed" -> {
                        speedStatus.text = statusText
                        speedStatus.setTextColor(statusColor)
                    }
                    "cadence" -> {
                        cadenceStatus.text = statusText
                        cadenceStatus.setTextColor(statusColor)
                    }
                    "heart" -> {
                        heartStatus.text = statusText
                        heartStatus.setTextColor(statusColor)
                    }
                    "location" -> {
                        locationStatus.text = statusText
                        locationStatus.setTextColor(statusColor)
                    }
                }
            }
        })
        return root
    }
}
