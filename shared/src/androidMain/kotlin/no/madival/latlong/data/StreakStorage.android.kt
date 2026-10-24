package no.madival.latlong.data

import android.content.Context

actual class StreakStorage {
    private val memoryFallback = mutableMapOf<String, Int>()

    actual fun getBestStreak(key: String): Int {
        val prefs = appContext?.getSharedPreferences("latlong_trivia_prefs", Context.MODE_PRIVATE)
        return prefs?.getInt("best_streak_$key", 0) ?: memoryFallback.getOrElse(key) { 0 }
    }

    actual fun saveBestStreak(key: String, streak: Int) {
        memoryFallback[key] = streak
        val prefs = appContext?.getSharedPreferences("latlong_trivia_prefs", Context.MODE_PRIVATE)
        prefs?.edit()?.putInt("best_streak_$key", streak)?.apply()
    }

    companion object {
        var appContext: Context? = null
        fun init(context: Context) {
            appContext = context.applicationContext
        }
    }
}

actual fun getStreakStorage(): StreakStorage = StreakStorage()
