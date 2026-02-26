package net.telent.biscuit.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.telent.biscuit.BikeActivity
import net.telent.biscuit.BiscuitDatabase
import net.telent.biscuit.DistanceFormatter
import net.telent.biscuit.R
import net.telent.biscuit.RideStatsCalculator
import net.telent.biscuit.Session
import kotlinx.coroutines.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HistoryFragment : Fragment() {

    private val db by lazy { BiscuitDatabase.getInstance(requireActivity()) }
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var adapter: SessionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_history, container, false)
        recyclerView = root.findViewById(R.id.recycler_sessions)
        emptyView = root.findViewById(R.id.tv_empty)

        adapter = SessionAdapter(
            db = db,
            onItemClick = { session ->
                (requireActivity() as BikeActivity).navigateToHistoryDetail(session.start.toEpochMilli())
            }
        )

        // 初始可见性：显示空状态，直到数据加载完成
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.VISIBLE

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        db.sessionDao().getClosedSessions().observe(viewLifecycleOwner) { sessions ->
            adapter.submitList(sessions)
            if (sessions.isNullOrEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE
            }
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        // Activity 重建后确保 adapter 已设置
        if (recyclerView.adapter == null) {
            recyclerView.adapter = adapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel all in-flight ViewHolder coroutines to avoid leaks on theme switch / Fragment destruction
        adapter.cancelAllScopes()
    }
}

class SessionAdapter(
    private val db: BiscuitDatabase,
    private val onItemClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {

    private var sessions: List<Session> = emptyList()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /** Parent scope for all ViewHolder coroutines; cancel via [cancelAllScopes]. */
    private var adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun submitList(list: List<Session>) {
        sessions = list
        notifyDataSetChanged()
    }

    /** Cancel all in-flight ViewHolder loads. Called when the adapter is detached. */
    fun cancelAllScopes() {
        adapterScope.cancel()
    }

    /** Re-create the adapter scope after a previous cancel (e.g. Fragment re-creation). */
    fun resetScope() {
        adapterScope.cancel()
        adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount(): Int = sessions.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDateTime: TextView = itemView.findViewById(R.id.tv_date_time)
        private val tvDistance: TextView = itemView.findViewById(R.id.tv_distance)
        private val tvDuration: TextView = itemView.findViewById(R.id.tv_duration)
        private val tvAvgSpeed: TextView = itemView.findViewById(R.id.tv_avg_speed)
        private var loadJob: Job? = null

        fun bind(session: Session) {
            // Check for custom session name
            val prefs = itemView.context.getSharedPreferences("biscuit_settings", android.content.Context.MODE_PRIVATE)
            val sessionName = prefs.getString("session_name_${session.start.toEpochMilli()}", null)
            
            if (sessionName != null) {
                tvDateTime.text = "$sessionName  ${dateFormatter.format(session.start)}"
            } else {
                tvDateTime.text = dateFormatter.format(session.start)
            }

            // Format duration
            val duration = session.duration()
            val totalSec = duration.seconds
            tvDuration.text = String.format(
                "%02d:%02d:%02d",
                totalSec / 3600,
                (totalSec % 3600) / 60,
                totalSec % 60
            )

            // Default values while loading
            tvDistance.text = "-- km"
            tvAvgSpeed.text = "-- km/h"

            // 在后台加载轨迹点以计算距离和平均速度
            val endTime = session.end ?: return
            // 取消上一次加载任务，避免 ViewHolder 复用时旧数据覆盖
            loadJob?.cancel()
            loadJob = adapterScope.launch {
                try {
                    val (stats, movingTimeSec) = withContext(Dispatchers.IO) {
                        val trackpoints = db.trackpointDao()
                            .getTrackpointsForSession(session.start, endTime)
                        val stats = RideStatsCalculator.calculateStats(trackpoints)
                        // 使用最后一个轨迹点的运动时间作为时长
                        val movingTimeSec = if (trackpoints.isNotEmpty()) {
                            trackpoints.last().movingTime.seconds
                        } else {
                            0L
                        }
                        Pair(stats, movingTimeSec)
                    }
                    // UI 更新在 Main 调度器上执行，无需 itemView.post
                    tvDistance.text = DistanceFormatter.formatFull((stats.totalDistanceKm * 1000).toFloat())
                    tvAvgSpeed.text = String.format("%.1f km/h", stats.avgSpeedKmh)
                    if (movingTimeSec > 0) {
                        tvDuration.text = String.format(
                            "%02d:%02d:%02d",
                            movingTimeSec / 3600,
                            (movingTimeSec % 3600) / 60,
                            movingTimeSec % 60
                        )
                    }
                } catch (_: Exception) {
                    // 保持默认值
                }
            }

            itemView.setOnClickListener { onItemClick(session) }
        }
    }
}
