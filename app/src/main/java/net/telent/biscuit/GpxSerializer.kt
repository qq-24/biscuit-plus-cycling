package net.telent.biscuit

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object GpxSerializer {

    private const val GPX_NAMESPACE = "http://www.topografix.com/GPX/1/1"
    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    /**
     * Serialize a list of Trackpoints to a GPX 1.1 XML string.
     * Only trackpoints with non-null lat and lng are included.
     */
    fun serialize(trackpoints: List<Trackpoint>, trackName: String): String {
        val validPoints = trackpoints.filter { it.lat != null && it.lng != null }
        val writer = StringWriter()

        writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        writer.append("<gpx version=\"1.1\" creator=\"Biscuit\"")
        writer.append(" xmlns=\"$GPX_NAMESPACE\">\n")
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
        val trackpoints = mutableListOf<Trackpoint>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(gpxXml))

        var lat: Double? = null
        var lon: Double? = null
        var timestamp: Instant? = null
        var speed: Float = -1.0f
        var currentTag = ""
        var inExtensions = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "trkpt" -> {
                            lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            timestamp = null
                            speed = -1.0f
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
                            lat = null
                            lon = null
                            timestamp = null
                            speed = -1.0f
                        }
                        "extensions" -> inExtensions = false
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return trackpoints
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
