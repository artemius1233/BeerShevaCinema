package il.co.beershevacinema.data

data class CinemaSource(
    val id: String,
    val name: String,
    val url: String,
    val note: String = ""
)

data class Screening(
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val cinema: String,
    val dateKey: String,
    val dateLabel: String,
    val time: String,
    val audioLanguage: String = "Не указано",
    val dubbing: String = "Не указано",
    val subtitles: String = "Не указаны",
    val format: String = "",
    val ticketUrl: String,
    val sourceUrl: String,
    val sourceLabel: String,
    val posterUrl: String? = null
)

data class SourceResult(
    val source: CinemaSource,
    val screenings: List<Screening>,
    val message: String? = null,
    val updatedAt: String? = null
)

val cinemaSources = listOf(
    CinemaSource(
        id = "planet",
        name = "Planet Cinema",
        url = "https://www.planetcinema.co.il/cinemas/beersheva/1074",
        note = "Расписание из JSON API системы бронирования Planet Cinema."
    ),
    CinemaSource(
        id = "cinemacity",
        name = "Cinema City",
        url = "https://www.cinema-city.co.il/city/beer-sheva",
        note = "Расписание из JSON API системы продажи билетов Cinema City."
    )

)
