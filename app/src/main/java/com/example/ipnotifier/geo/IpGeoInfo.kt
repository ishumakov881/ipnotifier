package com.example.ipnotifier.geo

import java.util.Locale

data class IpGeoInfo(
    val countryCode: String,
    val isp: String? = null,
    val asn: String? = null,
    val source: String,
) {
    val flagEmoji: String
        get() = if (isIsoCountry) {
            countryCode.map { Character.toChars(REGIONAL_INDICATOR_A + (it - 'A')).concatToString() }
                .joinToString("")
        } else {
            UNKNOWN_FLAG
        }

    val countryName: String
        get() = when {
            isIsoCountry -> Locale.Builder().setRegion(countryCode).build()
                .getDisplayCountry(Locale.getDefault())
            countryCode == TOR_CODE -> "Tor"
            else -> countryCode
        }

    private val isIsoCountry: Boolean
        get() = countryCode in ISO_COUNTRIES

    private companion object {
        const val REGIONAL_INDICATOR_A = 0x1F1E6
        const val UNKNOWN_FLAG = "🌐"
        const val TOR_CODE = "T1"
        val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()
    }
}
