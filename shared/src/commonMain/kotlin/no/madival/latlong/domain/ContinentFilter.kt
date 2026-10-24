package no.madival.latlong.domain

enum class ContinentFilter(val label: String) {
    ALL("All World"),
    EUROPE("Europe"),
    ASIA("Asia"),
    AFRICA("Africa"),
    AMERICAS("Americas"),
    OCEANIA("Oceania");

    fun matches(countryContinent: String): Boolean {
        return when (this) {
            ALL -> true
            EUROPE -> countryContinent.equals("Europe", ignoreCase = true)
            ASIA -> countryContinent.equals("Asia", ignoreCase = true)
            AFRICA -> countryContinent.equals("Africa", ignoreCase = true)
            AMERICAS -> countryContinent.equals("North America", ignoreCase = true) ||
                        countryContinent.equals("South America", ignoreCase = true) ||
                        countryContinent.equals("Americas", ignoreCase = true)
            OCEANIA -> countryContinent.equals("Oceania", ignoreCase = true)
        }
    }
}
