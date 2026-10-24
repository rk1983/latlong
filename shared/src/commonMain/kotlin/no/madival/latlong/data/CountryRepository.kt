package no.madival.latlong.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import latlong.shared.generated.resources.Res

class CountryRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedWorldData: WorldData? = null

    suspend fun getWorldData(): WorldData = withContext(Dispatchers.Default) {
        cachedWorldData?.let { return@withContext it }

        val bytes = Res.readBytes("files/countries_game_data.json")
        val jsonString = bytes.decodeToString()
        val data = json.decodeFromString<WorldData>(jsonString)
        cachedWorldData = data
        data
    }
}
