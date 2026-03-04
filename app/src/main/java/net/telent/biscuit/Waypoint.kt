package net.telent.biscuit

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * 路径标记点，骑行中用户手动标记的兴趣点。
 */
data class Waypoint(
    val index: Int,        // 标号，从 1 开始
    val lat: Double,
    val lng: Double,
    val timestamp: Instant
) {
    companion object {
        /** 将 Waypoint 列表序列化为 JSON 字符串 */
        fun toJson(waypoints: List<Waypoint>): String {
            val arr = JSONArray()
            for (wp in waypoints) {
                val obj = JSONObject()
                obj.put("index", wp.index)
                obj.put("lat", wp.lat)
                obj.put("lng", wp.lng)
                obj.put("timestamp", wp.timestamp.toEpochMilli())
                arr.put(obj)
            }
            return arr.toString()
        }

        /** 从 JSON 字符串反序列化 Waypoint 列表 */
        fun fromJson(json: String): List<Waypoint> {
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            val list = mutableListOf<Waypoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Waypoint(
                        index = obj.getInt("index"),
                        lat = obj.getDouble("lat"),
                        lng = obj.getDouble("lng"),
                        timestamp = Instant.ofEpochMilli(obj.getLong("timestamp"))
                    )
                )
            }
            return list
        }
    }
}
