package com.example.ipnotifier.geo

import com.example.ipnotifier.SimpleHttp
import org.json.JSONObject

internal interface IpGeoProvider {
    val name: String

    fun lookup(ip: String): IpGeoInfo
}

internal object IpWhoIsProvider : IpGeoProvider {
    override val name = "ipwho.is"

    override fun lookup(ip: String): IpGeoInfo {
        val json = JSONObject(SimpleHttp.get("https://ipwho.is/$ip"))
        check(json.optBoolean("success", false)) { json.optString("message", "lookup failed") }
        val connection = json.optJSONObject("connection")
        return IpGeoInfo(
            countryCode = json.requireCountryCode("country_code"),
            isp = connection?.optNonBlank("isp") ?: connection?.optNonBlank("org"),
            asn = connection?.optLong("asn")?.takeIf { it > 0 }?.let { "AS$it" },
            source = name,
        )
    }
}

internal object IpApiCoProvider : IpGeoProvider {
    override val name = "ipapi.co"

    override fun lookup(ip: String): IpGeoInfo {
        val json = JSONObject(SimpleHttp.get("https://ipapi.co/$ip/json/"))
        check(!json.optBoolean("error", false)) { json.optString("reason", "lookup failed") }
        return IpGeoInfo(
            countryCode = json.requireCountryCode("country_code"),
            isp = json.optNonBlank("org"),
            asn = json.optNonBlank("asn"),
            source = name,
        )
    }
}

internal object FreeIpApiProvider : IpGeoProvider {
    override val name = "freeipapi.com"

    override fun lookup(ip: String): IpGeoInfo {
        val json = JSONObject(SimpleHttp.get("https://free.freeipapi.com/api/json/$ip"))
        return IpGeoInfo(
            countryCode = json.requireCountryCode("countryCode"),
            isp = json.optNonBlank("asnOrganization"),
            asn = json.optNonBlank("asn")?.let { "AS$it" },
            source = name,
        )
    }
}

internal object CountryIsProvider : IpGeoProvider {
    override val name = "country.is"

    override fun lookup(ip: String): IpGeoInfo {
        val json = JSONObject(SimpleHttp.get("https://api.country.is/$ip"))
        return IpGeoInfo(countryCode = json.requireCountryCode("country"), source = name)
    }
}

/** Reports only the caller's own address, so it is accepted only when that address matches [ip]. */
internal object CloudflareTraceProvider : IpGeoProvider {
    override val name = "cloudflare"

    override fun lookup(ip: String): IpGeoInfo {
        val fields = SimpleHttp.get("https://1.1.1.1/cdn-cgi/trace")
            .lineSequence()
            .mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
            .associate { (key, value) -> key to value.trim() }
        check(fields["ip"] == ip) { "trace ip ${fields["ip"]} differs from $ip" }
        return IpGeoInfo(countryCode = normalizeCountryCode(fields["loc"]), source = name)
    }
}

private fun JSONObject.optNonBlank(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.requireCountryCode(key: String): String = normalizeCountryCode(optNonBlank(key))

private fun normalizeCountryCode(raw: String?): String {
    val code = raw?.trim()?.uppercase()
    require(code != null && code.length == 2 && code.all { it in 'A'..'Z' || it in '0'..'9' }) {
        "invalid country code: $raw"
    }
    return code
}
