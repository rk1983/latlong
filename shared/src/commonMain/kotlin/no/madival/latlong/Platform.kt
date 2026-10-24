package no.madival.latlong

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform