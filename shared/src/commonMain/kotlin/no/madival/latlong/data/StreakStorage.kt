package no.madival.latlong.data

expect class StreakStorage() {
    fun getBestStreak(key: String): Int
    fun saveBestStreak(key: String, streak: Int)
}

expect fun getStreakStorage(): StreakStorage
