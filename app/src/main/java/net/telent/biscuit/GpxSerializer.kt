package net.telent.biscuit

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** GPX 反序列化结果，包含轨迹点和路径标记点 */
data class GpxData(
    val trackpoints: List<Trackpoint>,
    val waypoints: List<Waypoint>
)

object GpxSerializer {

    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    /**
     * Serialize a list of Trackpoints to a GPX 1.1 XML string.
     * Only trackpoints with non-null lat and lng are included.
     * Optionally includes waypoints as <wpt> elements.
     */
    fun serialize(trackpoints: List<Trackpoint>, trackName: String, waypoints: List<Waypoint> = emptyList()): String {
        val validPoints = trackpoints.filter { it.lat != null && it.lng != null }
        val writer = StringWriter()

        writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        writer.append("<gpx version=\"1.1\" creator=\"Biscuit\"")
        writer.append(" xmlns=\"$GPX_NAMESPACE\">\n")

        // 路径标记点
        for (wp in waypoints) {
            writer.append("  <wpt lat=\"${wp.lat}\" lon=\"${wp.lng}\">\n")
            writer.append("    <time>${ISO_FORMATTER.format(wp.timestamp.atOffset(ZoneOffset.UTC))}</time>\n")
            writer.append("    <name>${wp.index}</name>\n")
            writer.append("  </wpt>\n")
        }

        writer.append("  <trk>\n")
        writer.append("    <name>${escapeXml(trackName)}</name>\n")
        writer.append("    <trkseg>\n")

        for (pt in validPoints) {
            writer.append("      <trkpt lat=\"${pt.lat}\" lon=\"${pt.lng}\">\n")
            writer.append("        <time>${ISO_FORMATTER.format(pt.timestamp.atOffset(ZoneOffset.UTC))}</time>\n")
            writer.append("        <extensions>\n")
            writer.append("          <speed>${pt.speed}</speed>\n")
            writer.append("        </extensions>\n")
            writer.append("      </trkpt>\n")
        }

        writer.append("    </trkseg>\n")
        writer.append("  </trk>\n")
        writer.append("</gpx>")

        return writer.toString()
    }

    /**
     * Deserialize a GPX XML string back to a list of Trackpoints.
     * Fields not present in GPX (cadence, wheelRevolutions, movingTime, distance) are set to defaults.
     */
    fun deserialize(gpxXml: String): List<Trackpoint> {
        return deserializeFull(gpxXml).trackpoints
    }

    /**
     * 完整反序列化 GPX XML，返回轨迹点和路径标记点。
     */
    fun deserializeFull(gpxXml: String): GpxData {
        val trackpoints = mutableListOf<Trackpoint>()
        val waypoints = mutableListOf<Waypoint>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(gpxXml))

        var lat: Double? = null
        var lon: Double? = null
        var timestamp: Instant? = null
        var speed: Float = -1.0f
        var wptName: String? = null
        var currentTag = ""
        var inExtensions = false
        var inWpt = false
        var inTrkpt = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "trkpt" -> {
                            inTrkpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            timestamp = null
                            speed = -1.0f
                        }
                        "wpt" -> {
                            inWpt = true
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            timestamp = null
                            wptName = null
                        }
                        "extensions" -> inExtensions = true
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text.trim()
                    if (text.isNotEmpty()) {
                        when {
                            currentTag == "time" && !inExtensions -> {
                                timestamp = Instant.parse(text)
                            }
                            currentTag == "speed" && inExtensions -> {
                                speed = text.toFloatOrNull() ?: -1.0f
                            }
                            currentTag == "name" && inWpt -> {
                                wptName = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "trkpt" -> {
                            if (lat != null && lon != null && timestamp != null) {
                                trackpoints.add(
                                    Trackpoint(
                                        timestamp = timestamp!!,
                                        lat = lat,
                                        lng = lon,
                                        speed = speed
                                    )
                                )
                            }
                            lat = null; lon = null; timestamp = null; speed = -1.0f
                            inTrkpt = false
                        }
                        "wpt" -> {
                            if (lat != null && lon != null && timestamp != null) {
                                val index = wptName?.toIntOrNull() ?: (waypoints.size + 1)
                                waypoints.add(
                                    Waypoint(
                                        index = index,
                                        lat = lat!!,
                                        lng = lon!!,
                                        timestamp = timestamp!!
                                    )
                                )
                            }
                            lat = null; lon = null; timestamp = null; wptName = null
                            inWpt = false
                        }
                        "extensions" -> inExtensions = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return GpxData(trackpoints, waypoints)
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
