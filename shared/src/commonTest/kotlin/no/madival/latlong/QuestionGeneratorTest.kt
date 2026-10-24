package no.madival.latlong

import no.madival.latlong.data.Country
import no.madival.latlong.data.Ring
import no.madival.latlong.data.SamplePoint
import no.madival.latlong.data.WorldData
import no.madival.latlong.domain.ContinentFilter
import no.madival.latlong.domain.QuestionGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionGeneratorTest {

    private fun makeCountry(code: String, name: String, continent: String, subregion: String): Country {
        val sample = SamplePoint(10f, 20f, listOf(5f, 15f, 15f, 25f))
        val ring = Ring(listOf(5f, 15f, 15f, 25f), listOf(listOf(5f, 15f), listOf(15f, 15f), listOf(15f, 25f)))
        return Country(
            code = code,
            iso2 = code.take(2).lowercase(),
            name = name,
            continent = continent,
            subregion = subregion,
            bbox = listOf(5f, 15f, 15f, 25f),
            rings = listOf(ring),
            samplePoints = listOf(sample)
        )
    }

    @Test
    fun testGenerateQuestionHasFourDistinctOptions() {
        val countries = listOf(
            makeCountry("FRA", "France", "Europe", "Western Europe"),
            makeCountry("DEU", "Germany", "Europe", "Western Europe"),
            makeCountry("BEL", "Belgium", "Europe", "Western Europe"),
            makeCountry("NLD", "Netherlands", "Europe", "Western Europe"),
            makeCountry("ITA", "Italy", "Europe", "Southern Europe"),
            makeCountry("ESP", "Spain", "Europe", "Southern Europe")
        )
        val world = WorldData(countries)
        val generator = QuestionGenerator()
        val question = generator.generateQuestion(world)

        assertEquals(4, question.options.size)
        assertEquals(4, question.options.map { it.code }.toSet().size, "All 4 options must be unique")
        assertTrue(question.correctIndex in 0..3)
        assertEquals(question.targetCountry.code, question.options[question.correctIndex].code)
    }

    @Test
    fun testDistractorPrioritizesSubregion() {
        val westernEurope = listOf(
            makeCountry("FRA", "France", "Europe", "Western Europe"),
            makeCountry("DEU", "Germany", "Europe", "Western Europe"),
            makeCountry("BEL", "Belgium", "Europe", "Western Europe"),
            makeCountry("NLD", "Netherlands", "Europe", "Western Europe")
        )
        val other = listOf(
            makeCountry("JPN", "Japan", "Asia", "Eastern Asia"),
            makeCountry("BRA", "Brazil", "South America", "South America")
        )
        val world = WorldData(westernEurope + other)
        val generator = QuestionGenerator()
        val question = generator.generateQuestion(world)

        if (question.targetCountry.subregion == "Western Europe") {
            val distractorCodes = question.options.filter { it.code != question.targetCountry.code }.map { it.code }.toSet()
            val expectedCodes = westernEurope.map { it.code }.toSet() - question.targetCountry.code
            assertEquals(expectedCodes, distractorCodes)
        }
    }

    @Test
    fun testContinentFilterAmericasGroupsNorthAndSouthAmerica() {
        val countries = listOf(
            makeCountry("USA", "United States", "North America", "Northern America"),
            makeCountry("CAN", "Canada", "North America", "Northern America"),
            makeCountry("MEX", "Mexico", "North America", "Central America"),
            makeCountry("BRA", "Brazil", "South America", "South America"),
            makeCountry("ARG", "Argentina", "South America", "South America"),
            makeCountry("FRA", "France", "Europe", "Western Europe"),
            makeCountry("JPN", "Japan", "Asia", "Eastern Asia")
        )
        val world = WorldData(countries)
        val generator = QuestionGenerator()
        val question = generator.generateQuestion(world, continentFilter = ContinentFilter.AMERICAS)

        assertTrue(ContinentFilter.AMERICAS.matches(question.targetCountry.continent))
        question.options.forEach { option ->
            assertTrue(
                ContinentFilter.AMERICAS.matches(option.continent),
                "Option ${option.name} (${option.continent}) must belong to Americas"
            )
        }
    }
}
