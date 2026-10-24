package no.madival.latlong.domain

import no.madival.latlong.data.Country
import no.madival.latlong.data.SamplePoint
import no.madival.latlong.data.WorldData
import no.madival.latlong.ui.FlagResources
import kotlin.random.Random
import kotlin.time.TimeSource

data class Question(
    val targetCountry: Country,
    val coordinate: SamplePoint,
    val options: List<Country>,
    val correctIndex: Int
)

class QuestionGenerator {
    private fun getFreshRandom(): Random {
        val nano = TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds
        val seed = nano xor Random.nextLong()
        return Random(seed)
    }

    fun generateQuestion(
        world: WorldData,
        recentCountryCodes: Set<String> = emptySet(),
        requireFlag: Boolean = false,
        continentFilter: ContinentFilter = ContinentFilter.ALL
    ): Question {
        val rng = getFreshRandom()

        // 1. Filter countries by continent filter
        val continentCountries = world.countries.filter { continentFilter.matches(it.continent) }
            .ifEmpty { world.countries }

        // 2. Filter countries by flag/samplePoints capability
        val validCountries = if (requireFlag) {
            val withFlags = continentCountries.filter { it.iso2.isNotEmpty() && FlagResources.getFlag(it.iso2) != null }
            withFlags.ifEmpty {
                world.countries.filter { it.iso2.isNotEmpty() && FlagResources.getFlag(it.iso2) != null }
            }
        } else {
            val withPoints = continentCountries.filter { it.samplePoints.isNotEmpty() }
            withPoints.ifEmpty {
                world.countries.filter { it.samplePoints.isNotEmpty() }
            }
        }

        require(validCountries.size >= 4) { "At least 4 countries required to generate a question" }

        val candidatePool = validCountries.filter { it.code !in recentCountryCodes }
            .ifEmpty { validCountries }

        val target = candidatePool.shuffled(rng).first()
        val coordinate = target.samplePoints.shuffled(rng).firstOrNull()
            ?: SamplePoint(0f, 0f, listOf(-180f, -90f, 180f, 90f))

        val distractors = mutableListOf<Country>()

        // 1. Same subregion within the filtered pool
        val sameSubregion = validCountries.filter {
            it.code != target.code &&
            it.subregion.equals(target.subregion, ignoreCase = true)
        }.shuffled(rng)
        distractors.addAll(sameSubregion.take(3))

        // 2. Fallback: same continent within the filtered pool
        if (distractors.size < 3) {
            val sameContinent = validCountries.filter {
                it.code != target.code &&
                distractors.none { d -> d.code == it.code } &&
                it.continent.equals(target.continent, ignoreCase = true)
            }.shuffled(rng)
            distractors.addAll(sameContinent.take(3 - distractors.size))
        }

        // 3. Fallback: any country from the filtered pool
        if (distractors.size < 3) {
            val anyFromFiltered = validCountries.filter {
                it.code != target.code &&
                distractors.none { d -> d.code == it.code }
            }.shuffled(rng)
            distractors.addAll(anyFromFiltered.take(3 - distractors.size))
        }

        val allOptions = (distractors + target).shuffled(rng)
        val correctIndex = allOptions.indexOfFirst { it.code == target.code }

        return Question(
            targetCountry = target,
            coordinate = coordinate,
            options = allOptions,
            correctIndex = correctIndex
        )
    }
}
