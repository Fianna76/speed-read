package io.github.fianna76.speedread.news.sports

import io.github.fianna76.speedread.news.sports.MotorsportSeries.Group

/**
 * The motorsport series the Sports section offers: Formula 1 and the single-seater ladder
 * (including the FIA feeders), endurance and touring cars. Every series has free news feeds
 * (checked October 2026). Calendars come from free machine-readable sources where they exist:
 * - sportstimes (MIT-licensed, behind f1calendar.com) for F1, F2, F3, F1 Academy, Formula E and IndyCar;
 * - the Le Mans series sites' race pages for WEC, ELMS and the Asian Le Mans Series.
 * Super Formula, Formula Regional, Formula 4 and Supercars publish no free machine-readable
 * calendar, so they are news-only.
 */
object MotorsportCatalog {

    // Declared before ALL, which uses it.
    private val FRECA = Regex("Formula Regional|FRECA|Eurocup", RegexOption.IGNORE_CASE)

    private fun motorsportCom(slug: String) = SeriesFeed("https://www.motorsport.com/rss/$slug/news/", "Motorsport.com")

    private fun autosport(slug: String) = SeriesFeed("https://www.autosport.com/rss/$slug/news/", "Autosport")

    private fun sportscar365(category: String) = SeriesFeed("https://sportscar365.com/category/lemans/$category/feed/", "Sportscar365")

    val ALL: List<MotorsportSeries> = listOf(
        MotorsportSeries(
            "f1", "Formula 1", "F1", Group.FORMULA, CalendarSource.SportsTimes("f1"),
            listOf(motorsportCom("f1"), autosport("f1")),
            raceMinutes = 120,
        ),
        MotorsportSeries(
            "f2", "FIA Formula 2", "F2", Group.FORMULA, CalendarSource.SportsTimes("f2"),
            listOf(motorsportCom("fia-f2"), SeriesFeed("https://www.formulascout.com/category/formula-2/feed/", "Formula Scout")),
        ),
        MotorsportSeries("f3", "FIA Formula 3", "F3", Group.FORMULA, CalendarSource.SportsTimes("f3"), listOf(motorsportCom("fia-f3"))),
        MotorsportSeries("f1-academy", "F1 Academy", "F1 Academy", Group.FORMULA, CalendarSource.SportsTimes("f1-academy"), listOf(motorsportCom("f1-academy"))),
        MotorsportSeries(
            "formula-regional", "Formula Regional European Championship", "FRECA", Group.FORMULA, null,
            listOf(
                SeriesFeed("https://www.formulascout.com/category/formula-regional/feed/", "Formula Scout", FRECA),
                SeriesFeed("https://feederseries.net/category/formula-regional/feed/", "Feeder Series", FRECA),
            ),
        ),
        MotorsportSeries(
            "formula-4", "Formula 4 (FIA-certified)", "F4", Group.FORMULA, null,
            listOf(SeriesFeed("https://www.formulascout.com/category/formula-4/feed/", "Formula Scout", Regex("\\bF4\\b|Formula 4"))),
        ),
        MotorsportSeries(
            "formula-e", "Formula E", "Formula E", Group.FORMULA, CalendarSource.SportsTimes("fe"),
            listOf(motorsportCom("formula-e"), autosport("formula-e")),
            raceMinutes = 75,
        ),
        MotorsportSeries(
            "indycar", "IndyCar", "IndyCar", Group.FORMULA, CalendarSource.SportsTimes("indycar"),
            listOf(motorsportCom("indycar"), SeriesFeed("https://racer.com/category/indycar/feed/", "RACER")),
            raceMinutes = 150,
        ),
        MotorsportSeries(
            "super-formula", "Super Formula", "Super Formula", Group.FORMULA, null,
            listOf(motorsportCom("super-formula"), autosport("super-formula")),
        ),
        MotorsportSeries(
            "wec", "FIA World Endurance Championship", "WEC", Group.ENDURANCE, CalendarSource.LeMansSite("https://www.fiawec.com"),
            listOf(motorsportCom("wec"), sportscar365("wec")),
            raceMinutes = 360,
        ),
        MotorsportSeries(
            "elms", "European Le Mans Series", "ELMS", Group.ENDURANCE, CalendarSource.LeMansSite("https://www.europeanlemansseries.com"),
            listOf(sportscar365("elms"), motorsportCom("elms")),
            raceMinutes = 240,
        ),
        MotorsportSeries(
            "alms", "Asian Le Mans Series", "ALMS", Group.ENDURANCE, CalendarSource.LeMansSite("https://www.asianlemansseries.com"),
            listOf(sportscar365("asian-lms")),
            raceMinutes = 240,
        ),
        MotorsportSeries(
            "supercars", "Repco Supercars Championship", "Supercars", Group.TOURING, null,
            listOf(SeriesFeed("https://www.speedcafe.com/category/supercars/feed/", "Speedcafe"), autosport("supercars"), motorsportCom("v8supercars")),
            raceMinutes = 120,
        ),
    )

    fun byId(id: String): MotorsportSeries? = ALL.firstOrNull { it.id == id }

}
