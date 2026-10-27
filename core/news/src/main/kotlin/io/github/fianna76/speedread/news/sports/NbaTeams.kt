package io.github.fianna76.speedread.news.sports

/** An NBA team. [espnId] is the team's id in ESPN's public API; [abbreviation] is ESPN's code. */
data class NbaTeam(
    val abbreviation: String,
    val espnId: Int,
    val city: String,
    val nickname: String,
    val conference: Conference,
) {
    enum class Conference { EAST, WEST }

    val fullName: String get() = "$city $nickname"

    /** Matches the team in a headline or teaser: its nickname as a capitalised word ("Heat", not "heat"). */
    private val mention = Regex("\\b${Regex.escape(nickname)}\\b")

    fun isMentionedIn(text: String): Boolean = mention.containsMatchIn(text)
}

object NbaTeams {

    val ALL: List<NbaTeam> = listOf(
        NbaTeam("ATL", 1, "Atlanta", "Hawks", NbaTeam.Conference.EAST),
        NbaTeam("BOS", 2, "Boston", "Celtics", NbaTeam.Conference.EAST),
        NbaTeam("BKN", 17, "Brooklyn", "Nets", NbaTeam.Conference.EAST),
        NbaTeam("CHA", 30, "Charlotte", "Hornets", NbaTeam.Conference.EAST),
        NbaTeam("CHI", 4, "Chicago", "Bulls", NbaTeam.Conference.EAST),
        NbaTeam("CLE", 5, "Cleveland", "Cavaliers", NbaTeam.Conference.EAST),
        NbaTeam("DAL", 6, "Dallas", "Mavericks", NbaTeam.Conference.WEST),
        NbaTeam("DEN", 7, "Denver", "Nuggets", NbaTeam.Conference.WEST),
        NbaTeam("DET", 8, "Detroit", "Pistons", NbaTeam.Conference.EAST),
        NbaTeam("GS", 9, "Golden State", "Warriors", NbaTeam.Conference.WEST),
        NbaTeam("HOU", 10, "Houston", "Rockets", NbaTeam.Conference.WEST),
        NbaTeam("IND", 11, "Indiana", "Pacers", NbaTeam.Conference.EAST),
        NbaTeam("LAC", 12, "LA", "Clippers", NbaTeam.Conference.WEST),
        NbaTeam("LAL", 13, "Los Angeles", "Lakers", NbaTeam.Conference.WEST),
        NbaTeam("MEM", 29, "Memphis", "Grizzlies", NbaTeam.Conference.WEST),
        NbaTeam("MIA", 14, "Miami", "Heat", NbaTeam.Conference.EAST),
        NbaTeam("MIL", 15, "Milwaukee", "Bucks", NbaTeam.Conference.EAST),
        NbaTeam("MIN", 16, "Minnesota", "Timberwolves", NbaTeam.Conference.WEST),
        NbaTeam("NO", 3, "New Orleans", "Pelicans", NbaTeam.Conference.WEST),
        NbaTeam("NY", 18, "New York", "Knicks", NbaTeam.Conference.EAST),
        NbaTeam("OKC", 25, "Oklahoma City", "Thunder", NbaTeam.Conference.WEST),
        NbaTeam("ORL", 19, "Orlando", "Magic", NbaTeam.Conference.EAST),
        NbaTeam("PHI", 20, "Philadelphia", "76ers", NbaTeam.Conference.EAST),
        NbaTeam("PHX", 21, "Phoenix", "Suns", NbaTeam.Conference.WEST),
        NbaTeam("POR", 22, "Portland", "Trail Blazers", NbaTeam.Conference.WEST),
        NbaTeam("SAC", 23, "Sacramento", "Kings", NbaTeam.Conference.WEST),
        NbaTeam("SA", 24, "San Antonio", "Spurs", NbaTeam.Conference.WEST),
        NbaTeam("TOR", 28, "Toronto", "Raptors", NbaTeam.Conference.EAST),
        NbaTeam("UTAH", 26, "Utah", "Jazz", NbaTeam.Conference.WEST),
        NbaTeam("WSH", 27, "Washington", "Wizards", NbaTeam.Conference.EAST),
    )

    fun byAbbreviation(abbreviation: String): NbaTeam? = ALL.firstOrNull { it.abbreviation == abbreviation }

    /** Teams named in [text] (a headline plus teaser). */
    fun mentionedIn(text: String): Set<String> = ALL.filter { it.isMentionedIn(text) }.mapTo(HashSet()) { it.abbreviation }
}
