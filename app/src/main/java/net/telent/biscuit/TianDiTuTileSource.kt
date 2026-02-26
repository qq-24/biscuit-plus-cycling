package net.telent.biscuit

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

object TianDiTuTileSource {

    private val BASE_URLS = (0..7).map { "https://t$it.tianditu.gov.cn/" }.toTypedArray()

    /** 矢量底图 (vec_w) */
    val vectorTileSource = createTileSource("TianDiTu-Vec", "vec_w", "vec", 18)

    /** 矢量注记 (cva_w) */
    val vectorAnnotationSource = createTileSource("TianDiTu-Cva", "cva_w", "cva", 18)

    /** 影像底图 (img_w) */
    val imageryTileSource = createTileSource("TianDiTu-Img", "img_w", "img", 18)

    /** 影像注记 (cia_w) */
    val imageryAnnotationSource = createTileSource("TianDiTu-Cia", "cia_w", "cia", 18)

    /** OpenTopoMap 等高线地形底图 */
    val openTopoMapSource: OnlineTileSourceBase = XYTileSource(
        "OpenTopoMap",
        0, 17, 256, ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        )
    )

    // Keep backward compatibility alias
    val annotationTileSource get() = vectorAnnotationSource

    var token: String = ""
        private set

    fun init(token: String) {
        this.token = token
    }

    fun getTileSources(mapType: String): Pair<OnlineTileSourceBase, OnlineTileSourceBase> {
        return when (mapType) {
            "satellite" -> Pair(imageryTileSource, imageryAnnotationSource)
            "terrain" -> Pair(openTopoMapSource, vectorAnnotationSource)
            else -> Pair(vectorTileSource, vectorAnnotationSource)
        }
    }

    private fun createTileSource(name: String, urlPath: String, layer: String, maxZoom: Int): OnlineTileSourceBase {
        return object : OnlineTileSourceBase(
            name, 0, maxZoom, 256, ".png",
            BASE_URLS.map { "${it}${urlPath}/wmts" }.toTypedArray()
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val serverIndex = (x + y) % 8
                return "https://t$serverIndex.tianditu.gov.cn/${urlPath}/wmts" +
                    "?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
                    "&LAYER=$layer&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles" +
                    "&TILECOL=$x&TILEROW=$y&TILEMATRIX=$z" +
                    "&tk=$token"
            }
        }
    }
}
