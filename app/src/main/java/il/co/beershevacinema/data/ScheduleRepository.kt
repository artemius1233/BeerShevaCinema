package il.co.beershevacinema.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Reads structured JSON endpoints used by the cinemas' booking systems. No HTML parsing. */
class ScheduleRepository {
    suspend fun loadAll(): List<SourceResult> = coroutineScope {
        cinemaSources.map { source ->
            async(Dispatchers.IO) { load(source) }
        }.awaitAll()
    }

    private suspend fun load(source: CinemaSource): SourceResult = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        try {
            val screenings = when (source.id) {
                "planet" -> loadPlanet()
                "cinemacity" -> loadCinemaCity()
                else -> emptyList()
            }
            SourceResult(
                source = source,
                screenings = screenings.sortedWith(compareBy<Screening> { it.dateKey }.thenBy { it.time }),
                message = if (screenings.isEmpty()) "API ответил, но сеансы не найдены на доступные даты." else null,
                updatedAt = stamp
            )
        } catch (e: Exception) {
            SourceResult(
                source = source,
                screenings = emptyList(),
                message = "Не удалось получить расписание через API (${e.message ?: "ошибка сети"}). Можно открыть официальный сайт.",
                updatedAt = null
            )
        }
    }

    private val countryMap = mapOf(
        "יפן" to "Японский",
        "צרפת" to "Французский",
        "ספרד" to "Испанский",
        "גרמניה" to "Немецкий",
        "איטליה" to "Итальянский",
        "ישראל" to "Иврит",
        "דרום קוריאה" to "Корейский",
        "סין" to "Китайский",
        "רוסיה" to "Русский",
        "ארה\"ב" to "Английский",
        "בריטניה" to "Английский",
        "אוסטרליה" to "Английский",
        "קנדה" to "Английский"
    )

    private fun getPlanetFilmCountry(filmLink: String): String? {
        if (filmLink.isBlank()) return null
        return try {
            val connection = URL(filmLink).openConnection() as HttpURLConnection
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BeerShevaCinema/0.2")
            if (connection.responseCode in 200..299) {
                val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val countryRegex = Regex("\"releaseCountry\":\"(.*?)\"")
                countryRegex.find(html)?.groupValues?.get(1)?.let { raw ->
                    unescapeUnicode(raw)
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun unescapeUnicode(str: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < str.length) {
            if (i + 5 < str.length && str[i] == '\\' && str[i + 1] == 'u') {
                runCatching {
                    val code = str.substring(i + 2, i + 6).toInt(16)
                    sb.append(code.toChar())
                    i += 6
                }.onFailure {
                    sb.append(str[i])
                    i++
                }
            } else {
                sb.append(str[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun loadPlanet(): List<Screening> {
        val base = "https://www.planetcinema.co.il/il/data-api-service/v1/quickbook/10100"
        val cinemaId = "1074" // Planet Beer Sheva
        val today = israelToday()
        val until = addDays(today, 7)
        val datesBody = getJson("$base/dates/in-cinema/$cinemaId/until/$until?attr=&lang=en_GB")
            .optJSONObject("body") ?: error("Planet API: нет body")
        val dates = datesBody.optJSONArray("dates").toStringList().filter { it >= today && it <= until }
        val out = mutableListOf<Screening>()
        val filmCountryCache = mutableMapOf<String, String?>()

        for (date in dates) {
            val body = getJson("$base/film-events/in-cinema/$cinemaId/at-date/$date?attr=&lang=en_GB")
                .optJSONObject("body") ?: continue
            val films = body.optJSONArray("films") ?: JSONArray()
            val events = body.optJSONArray("events") ?: JSONArray()
            val filmsById = mutableMapOf<String, JSONObject>()
            for (i in 0 until films.length()) {
                val film = films.optJSONObject(i) ?: continue
                filmsById[film.optString("id")] = film
            }
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val dateTime = event.optString("eventDateTime")
                val eventDate = dateTime.take(10).ifBlank { event.optString("businessDay", date) }
                val time = dateTime.substringAfter('T', "").take(5)
                if (time.length != 5) continue
                val film = filmsById[event.optString("filmId")] ?: JSONObject()
                val title = film.optString("name").trim()
                if (title.isBlank()) continue
                val posterUrl = film.optString("posterLink").trim().takeIf { it.isNotBlank() }
                val attrs = strings(film.optJSONArray("attributeIds")) + strings(event.optJSONArray("attributeIds"))
                val languages = parsePlanetLanguages(film, attrs, filmCountryCache)
                val rawBooking = event.optString("bookingLink")
                val booking = rawBooking.replace("/api/order/", "/order/").ifBlank { sourceUrlPlanet() }
                val filmSlug = film.optString("link").substringAfter("/films/", "").substringBefore("/").replace('-', ' ')
                val id = "planet:${event.optString("id", "$eventDate-$time-$title")}"
                out += Screening(
                    id = id, title = title, originalTitle = filmSlug, cinema = "Planet Cinema", dateKey = eventDate,
                    dateLabel = labelDate(eventDate), time = time,
                    audioLanguage = languages.audio, dubbing = languages.dubbing,
                    subtitles = languages.subtitles,
                    format = attrs.firstOrNull { it.equals("3d", true) || it.equals("imax", true) || it.equals("4dx", true) }
                        ?.uppercase(Locale.ROOT).orEmpty(),
                    ticketUrl = booking, sourceUrl = sourceUrlPlanet(), sourceLabel = "Planet Cinema API",
                    posterUrl = posterUrl
                )
            }
        }
        return out
    }

    private data class CinemaCityFeature(
        val englishTitle: String?,
        val posterUrl: String?
    )

    private fun loadCinemaCityFeatureMap(): Map<String, CinemaCityFeature> {
        return try {
            val connection = URL("https://tickets.cinema-city.co.il/api/features/").openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BeerShevaCinema/0.2")
            val code = connection.responseCode
            if (code !in 200..299) return emptyMap()
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = JSONArray(text)
            val map = mutableMapOf<String, CinemaCityFeature>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val codeStr = item.optString("code").trim()
                val additionalName = item.optString("additionalName").trim().takeIf { it.isNotBlank() }
                val name = item.optString("name").trim().takeIf { it.isNotBlank() }
                val imageData = item.optString("imageData").trim().takeIf { it.isNotBlank() }
                val feat = CinemaCityFeature(englishTitle = additionalName ?: name, posterUrl = imageData)
                if (id.isNotBlank()) map[id] = feat
                if (codeStr.isNotBlank()) map[codeStr] = feat
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadCinemaCity(): List<Screening> {
        val today = israelToday()
        val featureMap = loadCinemaCityFeatureMap()
        val endpoint = "https://tickets.cinema-city.co.il/api/presentations/?locationId=1178&startDate=$today"
        val root = getJson(endpoint)
        val rows = root.optJSONArray("presentations") ?: error("Cinema City API: нет presentations")
        val out = mutableListOf<Screening>()
        val seen = mutableSetOf<String>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optInt("venueTypeId", -1) == 101) continue // VOD, not a cinema screening
            val id = row.optString("id")
            if (id.isBlank() || !seen.add(id)) continue
            val dateTime = row.optString("dateTime")
            val date = dateTime.take(10)
            val time = dateTime.substringAfter(' ', "").take(5)
            val rawName = row.optString("featureName").trim()
            val rawAdditional = row.optString("featureAdditionalName").trim()
            val featureId = row.optString("featureId").trim()
            val featureCode = row.optString("featureCode").trim()
            val feat = featureMap[featureId] ?: featureMap[featureCode]
            val englishTitle = feat?.englishTitle.orEmpty()
            val title = rawAdditional.ifBlank { englishTitle }.ifBlank { rawName }
            val posterUrl = feat?.posterUrl
            if (date.length != 10 || time.length != 5 || title.isBlank()) continue
            val attrs = extractStringValues(row)
            val languages = parseCinemaCityLanguages(row)
            val venue = row.optString("venueName").trim()
            val booking = "https://tickets.cinema-city.co.il/order/$id"
            out += Screening(
                id = "cinemacity:$id", title = title, originalTitle = rawName, cinema = "Cinema City", dateKey = date,
                dateLabel = labelDate(date), time = time,
                audioLanguage = languages.audio, dubbing = languages.dubbing,
                subtitles = languages.subtitles,
                format = venue.takeIf { it.isNotBlank() } ?: attrs.firstOrNull { it.equals("3d", true) || it.equals("imax", true) || it.equals("4dx", true) }?.uppercase(Locale.ROOT).orEmpty(),
                ticketUrl = booking, sourceUrl = sourceUrlCinemaCity(), sourceLabel = "Cinema City API",
                posterUrl = posterUrl
            )
        }
        return out
    }

    private data class LanguageDetails(val audio: String, val dubbing: String, val subtitles: String)

    private fun parsePlanetLanguages(film: JSONObject, attributes: List<String>, filmCountryCache: MutableMap<String, String?>): LanguageDetails {
        val tags = attributes.map { it.lowercase(Locale.ROOT) }
        val filmId = film.optString("id")
        val link = film.optString("link")

        val dubbing = when {
            "dubbed-lang-he" in tags -> "Ивритский дубляж"
            "dubbed-lang-ru" in tags -> "Русский дубляж"
            "dubbed-lang-en" in tags -> "Английский дубляж"
            "dubbed" in tags -> "Есть"
            else -> "Не указано"
        }

        val audio = when {
            dubbing == "Русский дубляж" -> "Русский"
            dubbing == "Ивритский дубляж" -> "Иврит"
            dubbing == "Английский дубляж" -> "Английский"
            else -> {
                val country = filmCountryCache.getOrPut(filmId) { getPlanetFilmCountry(link) }
                val mappedLang = country?.let { countryMap[it] }
                mappedLang ?: when {
                    "israeli" in tags -> "Иврит"
                    else -> "Английский"
                }
            }
        }

        val subs = mutableListOf<String>()
        if ("first-subbed-lang-he" in tags || "subbed-lang-he" in tags) subs += "Иврит"
        if ("second-subbed-lang-en" in tags || "subbed-lang-en" in tags) subs += "Английские"
        if ("subbed-lang-ru" in tags) subs += "Русские"
        if (subs.isEmpty() && ("subbed" in tags || tags.any { it.contains("subbed") })) subs += "Иврит"

        val subtitles = if (subs.isNotEmpty()) subs.joinToString(", ") else "Не указаны"
        return LanguageDetails(audio, dubbing, subtitles)
    }

    private fun parseCinemaCityLanguages(row: JSONObject): LanguageDetails {
        val rawName = row.optString("featureName")
        val langIso = row.optString("languageISO").ifBlank { row.optString("language2ISO") }.lowercase(Locale.ROOT)
        val dubIso = row.optString("dubbedLanguageISO").ifBlank { row.optString("dubbedLanguage2ISO") }.lowercase(Locale.ROOT)
        val subIso = row.optString("subbedLanguageISO").ifBlank { row.optString("subbedLanguage2ISO") }.lowercase(Locale.ROOT)

        val isoMap = mapOf(
            "en" to "Английский", "he" to "Иврит", "ru" to "Русский",
            "ja" to "Японский", "fr" to "Французский", "es" to "Испанский",
            "de" to "Немецкий", "it" to "Итальянский"
        )
        val dubMap = mapOf(
            "he" to "Ивритский дубляж", "ru" to "Русский дубляж", "en" to "Английский дубляж"
        )
        val subMap = mapOf(
            "he" to "Иврит", "en" to "Английские", "ru" to "Русские"
        )

        val isHebrewDubbed = rawName.contains("מדובב") || dubIso == "he"
        val isRussianDubbed = rawName.contains("מדובלן") || rawName.contains("רוסית") || dubIso == "ru"

        val dubbing = when {
            isHebrewDubbed -> "Ивритский дубляж"
            isRussianDubbed -> "Русский дубляж"
            else -> dubMap[dubIso] ?: if (dubIso.isNotBlank()) "Есть" else "Не указано"
        }

        val audio = when {
            isHebrewDubbed -> "Иврит"
            isRussianDubbed -> "Русский"
            else -> dubMap[dubIso] ?: isoMap[langIso] ?: if (langIso.contains("he") || rawName.contains("hebrew")) "Иврит" else "Английский"
        }

        val subtitles = subMap[subIso] ?: if (subIso.isNotBlank() || rawName.contains("כתוביות")) "Иврит" else "Не указаны"

        return LanguageDetails(audio, dubbing, subtitles)
    }

    private fun extractStringValues(obj: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            when (value) {
                is String -> if (key.contains("lang", true) || key.contains("audio", true) || key.contains("dub", true) || key.contains("sub", true) || key.contains("attribute", true) || key.contains("format", true) || key.contains("category", true)) out += "$key-$value"
                is JSONArray -> out += strings(value)
            }
        }
        return out
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() && it != "null" } }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) BeerShevaCinema/0.2")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun israelToday(): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Jerusalem") }
        return format.format(Date())
    }

    private fun addDays(date: String, days: Int): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val parsed = format.parse(date) ?: Date()
        return format.format(Date(parsed.time + days * 24L * 60L * 60L * 1000L))
    }

    private fun labelDate(iso: String): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = runCatching { format.parse(iso) }.getOrNull() ?: return iso
        val today = israelToday()
        val tomorrow = addDays(today, 1)
        return when (iso) {
            today -> "Сегодня · ${SimpleDateFormat("d MMMM", Locale("ru")).format(date)}"
            tomorrow -> "Завтра · ${SimpleDateFormat("d MMMM", Locale("ru")).format(date)}"
            else -> SimpleDateFormat("EEE, d MMMM", Locale("ru")).format(date)
        }
    }

    private fun JSONArray?.toStringList(): List<String> = strings(this)
    private fun sourceUrlPlanet() = "https://www.planetcinema.co.il/cinemas/beersheva/1074"
    private fun sourceUrlCinemaCity() = "https://www.cinema-city.co.il/city/beer-sheva"
}
