package no.madival.latlong.data

import kotlinx.serialization.Serializable

@Serializable
data class WorldData(
    val countries: List<Country> = emptyList()
)

@Serializable
data class Country(
    val code: String,
    val iso2: String = "",
    val name: String,
    val continent: String,
    val subregion: String,
    val bbox: List<Float>,
    val rings: List<Ring> = emptyList(),
    val samplePoints: List<SamplePoint> = emptyList()
)

@Serializable
data class Ring(
    val bbox: List<Float>,
    val points: List<List<Float>>
)

@Serializable
data class SamplePoint(
    val lon: Float,
    val lat: Float,
    val ringBbox: List<Float>
)

data class BoundingBox(
    val minLon: Float,
    val minLat: Float,
    val maxLon: Float,
    val maxLat: Float
) {
    val spanLon: Float get() = (maxLon - minLon).coerceAtLeast(0.001f)
    val spanLat: Float get() = (maxLat - minLat).coerceAtLeast(0.001f)
    val centerLon: Float get() = (minLon + maxLon) / 2f
    val centerLat: Float get() = (minLat + maxLat) / 2f

    companion object {
        fun fromList(list: List<Float>): BoundingBox {
            if (list.size < 4) return BoundingBox(-180f, -90f, 180f, 90f)
            return BoundingBox(list[0], list[1], list[2], list[3])
        }
    }
}
