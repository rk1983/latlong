package no.madival.latlong.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import no.madival.latlong.data.Country
import no.madival.latlong.data.SamplePoint

data class CameraTarget(
    val panX: Float,
    val panY: Float,
    val zoom: Float
)

data class CachedCountry(
    val country: Country,
    val paths: List<Path>
)

object MapProjection {
    const val MIN_LON = -180f
    const val MAX_LON = 180f
    const val MIN_LAT = -60f
    const val MAX_LAT = 85f

    const val BASE_WIDTH = 2400f
    const val BASE_HEIGHT = 1200f

    fun project(lon: Float, lat: Float, baseW: Float = BASE_WIDTH, baseH: Float = BASE_HEIGHT): Offset {
        val clampedLon = lon.coerceIn(MIN_LON, MAX_LON)
        val clampedLat = lat.coerceIn(MIN_LAT, MAX_LAT)
        val x = (clampedLon - MIN_LON) / (MAX_LON - MIN_LON) * baseW
        val y = (MAX_LAT - clampedLat) / (MAX_LAT - MIN_LAT) * baseH
        return Offset(x, y)
    }

    fun buildCountryPaths(country: Country, baseW: Float = BASE_WIDTH, baseH: Float = BASE_HEIGHT): List<Path> {
        return country.rings.mapNotNull { ring ->
            if (ring.points.size < 3) return@mapNotNull null
            val path = Path()
            val first = ring.points[0]
            val firstPt = project(first[0], first[1], baseW, baseH)
            path.moveTo(firstPt.x, firstPt.y)
            for (i in 1 until ring.points.size) {
                val pt = ring.points[i]
                val proj = project(pt[0], pt[1], baseW, baseH)
                path.lineTo(proj.x, proj.y)
            }
            path.close()
            path
        }
    }

    fun getGlobalOverviewCamera(viewportWidth: Float, viewportHeight: Float): CameraTarget {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return CameraTarget(0f, 0f, 1f)
        }
        val zoomX = viewportWidth / BASE_WIDTH
        val zoomY = viewportHeight / BASE_HEIGHT
        val targetZoom = minOf(zoomX, zoomY).coerceIn(0.85f, 1.4f)
        val panX = (viewportWidth - (BASE_WIDTH * targetZoom)) / 2f
        val panY = (viewportHeight - (BASE_HEIGHT * targetZoom)) / 2f
        return CameraTarget(panX, panY, targetZoom)
    }

    /**
     * Calculates localized camera framing centered around the target sample point
     * and its containing ring's bounding box.
     */
    fun calculateCameraFraming(
        samplePoint: SamplePoint,
        viewportWidth: Float,
        viewportHeight: Float
    ): CameraTarget {
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return CameraTarget(0f, 0f, 1f)
        }

        val ringBbox = samplePoint.ringBbox
        val minLon = ringBbox.getOrElse(0) { samplePoint.lon - 12f }
        val minLat = ringBbox.getOrElse(1) { samplePoint.lat - 12f }
        val maxLon = ringBbox.getOrElse(2) { samplePoint.lon + 12f }
        val maxLat = ringBbox.getOrElse(3) { samplePoint.lat + 12f }

        val minSpan = 28f
        val rawSpanLon = (maxLon - minLon).coerceAtLeast(0.1f)
        val rawSpanLat = (maxLat - minLat).coerceAtLeast(0.1f)

        val spanLon = maxOf(rawSpanLon, minSpan)
        val spanLat = maxOf(rawSpanLat, minSpan)

        val centerLon = (minLon + maxLon) / 2f
        val centerLat = (minLat + maxLat) / 2f

        val baseCenter = project(centerLon, centerLat)

        val baseSpanX = (spanLon / (MAX_LON - MIN_LON)) * BASE_WIDTH
        val baseSpanY = (spanLat / (MAX_LAT - MIN_LAT)) * BASE_HEIGHT

        val zoomX = (viewportWidth * 0.75f) / baseSpanX
        val zoomY = (viewportHeight * 0.75f) / baseSpanY

        val targetZoom = minOf(zoomX, zoomY).coerceIn(1.1f, 3.5f)

        val panX = (viewportWidth / 2f) - (baseCenter.x * targetZoom)
        val panY = (viewportHeight / 2f) - (baseCenter.y * targetZoom)

        return CameraTarget(panX, panY, targetZoom)
    }
}
