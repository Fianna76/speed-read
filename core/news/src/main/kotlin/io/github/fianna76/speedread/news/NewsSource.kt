package io.github.fianna76.speedread.news

/** Where an outlet is based and what it mostly covers; also the Newsstand's display order. */
enum class NewsRegion { IRELAND, EUROPE, WORLD }

/**
 * A news outlet the Newsstand reads from: its public RSS feed of top stories, plus hints that
 * help [ArticleExtractor] find the story text on its article pages.
 */
data class NewsSource(
    /** Stable identifier, stored in settings; never rename. */
    val id: String,
    val name: String,
    val feedUrl: String,
    val homepage: String,
    val region: NewsRegion = NewsRegion.WORLD,
    /** Switched on for users who haven't chosen; the rest are one switch away in Settings. */
    val enabledByDefault: Boolean = false,
    /** Short description shown in Settings, e.g. "Irish language" or "Farming". */
    val note: String? = null,
    /**
     * CSS selector of the element holding an article's body text on this outlet's pages. Only a
     * hint: extraction falls back to content scoring when it matches nothing useful.
     */
    val bodySelector: String? = null,
)

/**
 * The built-in sources, Irish first, then European, then global. Every outlet here publishes
 * a free RSS feed and serves its articles without a subscription; pages an outlet marks as
 * subscriber-only are never extracted (see [ArticleExtractor.isPaywalled]).
 *
 * Each feed was checked for fresh items, and sample articles for clean text extraction.
 * Left out on purpose: Reuters and AP (no public feeds, automated access blocked); The Irish
 * Times, Irish Examiner, The Irish News, Business Post, NYT, Washington Post, FT, The Economist,
 * SCMP and the LA Times (paywalled); CNN, VOA and BreakingNews.ie (feeds no longer updated).
 */
object NewsSources {

    private fun ireland(
        id: String,
        name: String,
        feedUrl: String,
        homepage: String,
        on: Boolean = false,
        note: String? = null,
    ) = NewsSource(id, name, feedUrl, homepage, NewsRegion.IRELAND, on, note)

    private fun europe(
        id: String,
        name: String,
        feedUrl: String,
        homepage: String,
        on: Boolean = false,
        note: String? = null,
        bodySelector: String? = null,
    ) = NewsSource(id, name, feedUrl, homepage, NewsRegion.EUROPE, on, note, bodySelector)

    private fun world(
        id: String,
        name: String,
        feedUrl: String,
        homepage: String,
        on: Boolean = false,
        note: String? = null,
        bodySelector: String? = null,
    ) = NewsSource(id, name, feedUrl, homepage, NewsRegion.WORLD, on, note, bodySelector)

    val ALL: List<NewsSource> = listOf(
        // --- Ireland ---------------------------------------------------------------------------
        ireland("rte", "RTÉ News", "https://www.rte.ie/feeds/rss/?index=/news/", "https://www.rte.ie/news", on = true),
        ireland("thejournal", "TheJournal.ie", "https://www.thejournal.ie/feed/", "https://www.thejournal.ie", on = true),
        ireland("newstalk", "Newstalk", "https://www.newstalk.com/feed", "https://www.newstalk.com", on = true),
        ireland(
            "bbc_ni", "BBC News NI", "https://feeds.bbci.co.uk/news/northern_ireland/rss.xml",
            "https://www.bbc.co.uk/news/northern_ireland", on = true,
        ),
        ireland(
            "independent_ie", "Irish Independent", "https://www.independent.ie/rss", "https://www.independent.ie",
            note = "Some articles are for subscribers",
        ),
        ireland(
            "rte_brainstorm", "RTÉ Brainstorm", "https://www.rte.ie/feeds/rss/?index=/brainstorm/",
            "https://www.rte.ie/brainstorm", on = true, note = "Analysis by academics",
        ),
        ireland(
            "siliconrepublic", "Silicon Republic", "https://www.siliconrepublic.com/feed",
            "https://www.siliconrepublic.com", note = "Science and technology",
        ),
        ireland(
            "belfasttelegraph", "Belfast Telegraph", "https://www.belfasttelegraph.co.uk/rss",
            "https://www.belfasttelegraph.co.uk", note = "Some articles are for subscribers",
        ),
        ireland("irishpost", "The Irish Post", "https://www.irishpost.com/feed", "https://www.irishpost.com", note = "Irish in Britain"),
        ireland("limerickleader", "Limerick Leader", "https://www.limerickleader.ie/rss", "https://www.limerickleader.ie", note = "Regional"),
        ireland("agriland", "Agriland", "https://www.agriland.ie/feed/", "https://www.agriland.ie", note = "Farming"),
        ireland(
            "rte_nuacht", "RTÉ Nuacht", "https://www.rte.ie/feeds/rss/?index=/news/nuacht/",
            "https://www.rte.ie/news/nuacht", note = "As Gaeilge",
        ),
        ireland("tuairisc", "Tuairisc.ie", "https://tuairisc.ie/feed/", "https://tuairisc.ie", note = "As Gaeilge"),

        // --- Europe ----------------------------------------------------------------------------
        europe("dw", "DW", "https://rss.dw.com/xml/rss-en-top", "https://www.dw.com/en", on = true, bodySelector = ".rich-text"),
        europe(
            "france24", "France 24", "https://www.france24.com/en/rss", "https://www.france24.com/en",
            on = true, bodySelector = ".t-content__body",
        ),
        europe("euronews", "Euronews", "https://www.euronews.com/rss", "https://www.euronews.com", on = true),
        europe(
            "independent_uk", "The Independent", "https://www.independent.co.uk/news/world/rss",
            "https://www.independent.co.uk", on = true, note = "UK",
        ),
        europe(
            "elpais", "El País English", "https://feeds.elpais.com/mrss-s/pages/ep/site/english.elpais.com/portada",
            "https://english.elpais.com", on = true, note = "Spain",
        ),
        europe("ansa", "ANSA English", "https://www.ansa.it/english/news/english_nr_rss.xml", "https://www.ansa.it/english", on = true, note = "Italy"),
        europe("kyivindependent", "The Kyiv Independent", "https://kyivindependent.com/news-archive/rss/", "https://kyivindependent.com", on = true, note = "Ukraine"),
        europe(
            "guardian_europe", "The Guardian Europe", "https://www.theguardian.com/world/europe-news/rss",
            "https://www.theguardian.com/world/europe-news",
        ),
        europe("skynews", "Sky News", "https://feeds.skynews.com/feeds/rss/world.xml", "https://news.sky.com", note = "UK"),
        europe("rfi", "RFI English", "https://www.rfi.fr/en/rss", "https://www.rfi.fr/en", note = "France"),
        europe("pravda_ua", "Ukrainska Pravda", "https://www.pravda.com.ua/eng/rss/", "https://www.pravda.com.ua/eng", note = "Ukraine"),
        europe("moscowtimes", "The Moscow Times", "https://www.themoscowtimes.com/rss/news", "https://www.themoscowtimes.com", note = "Independent Russian news"),
        europe("meduza", "Meduza", "https://meduza.io/rss/en/all", "https://meduza.io/en", note = "Independent Russian news"),
        europe("notesfrompoland", "Notes from Poland", "https://notesfrompoland.com/feed/", "https://notesfrompoland.com", note = "Poland"),
        europe("balkaninsight", "Balkan Insight", "https://balkaninsight.com/feed/", "https://balkaninsight.com", note = "Balkans"),
        europe("yle", "Yle News", "https://feeds.yle.fi/uutiset/v1/recent.rss?publisherIds=YLE_NEWS", "https://yle.fi/news", note = "Finland"),
        europe("err", "ERR News", "https://news.err.ee/rss", "https://news.err.ee", note = "Estonia"),
        europe("lrt", "LRT English", "https://www.lrt.lt/en/news-in-english?rss", "https://www.lrt.lt/en", note = "Lithuania"),
        europe("nltimes", "NL Times", "https://nltimes.nl/rss", "https://nltimes.nl", note = "Netherlands"),
        europe("portugalnews", "The Portugal News", "https://www.theportugalnews.com/rss", "https://www.theportugalnews.com", note = "Portugal"),
        europe("novinite", "Novinite", "https://www.novinite.com/services/news_rdf.php", "https://www.novinite.com", note = "Bulgaria"),
        europe(
            "conversation_europe", "The Conversation Europe", "https://theconversation.com/europe/articles.atom",
            "https://theconversation.com/europe", note = "Analysis by academics",
        ),

        // --- World -----------------------------------------------------------------------------
        world("aljazeera", "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml", "https://www.aljazeera.com", on = true, bodySelector = ".wysiwyg"),
        world("bbc", "BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "https://www.bbc.co.uk/news", on = true, bodySelector = "article"),
        world("guardian", "The Guardian", "https://www.theguardian.com/international/rss", "https://www.theguardian.com", on = true, bodySelector = "#maincontent"),
        world("npr", "NPR", "https://feeds.npr.org/1001/rss.xml", "https://www.npr.org", on = true, note = "US", bodySelector = "#storytext"),
        world("pbs", "PBS NewsHour", "https://www.pbs.org/newshour/feeds/rss/headlines", "https://www.pbs.org/newshour", on = true, note = "US", bodySelector = ".body-text"),
        world("cbs", "CBS News", "https://www.cbsnews.com/latest/rss/world", "https://www.cbsnews.com", on = true, note = "US"),
        world("csmonitor", "The Christian Science Monitor", "https://rss.csmonitor.com/feeds/world", "https://www.csmonitor.com", on = true, note = "US"),
        world("cbc", "CBC News", "https://www.cbc.ca/webfeed/rss/rss-topstories", "https://www.cbc.ca/news", on = true, note = "Canada", bodySelector = ".story"),
        world("abc_au", "ABC News (Australia)", "https://www.abc.net.au/news/feed/45910/rss.xml", "https://www.abc.net.au/news", on = true, note = "Australia"),
        world("cna", "CNA", "https://www.channelnewsasia.com/rssfeeds/8395884", "https://www.channelnewsasia.com", on = true, note = "Singapore"),
        world("un_news", "UN News", "https://news.un.org/feed/subscribe/en/news/all/rss.xml", "https://news.un.org/en"),
        world("nbc", "NBC News", "https://feeds.nbcnews.com/nbcnews/public/world", "https://www.nbcnews.com", note = "US"),
        world("abc_us", "ABC News (US)", "https://abcnews.go.com/abcnews/internationalheadlines", "https://abcnews.go.com", note = "US"),
        world("propublica", "ProPublica", "https://feeds.propublica.org/propublica/main", "https://www.propublica.org", note = "US investigations"),
        world("semafor", "Semafor", "https://www.semafor.com/rss.xml", "https://www.semafor.com"),
        world("conversation", "The Conversation", "https://theconversation.com/global/articles.atom", "https://theconversation.com/global", note = "Analysis by academics"),
        world("globalvoices", "Global Voices", "https://globalvoices.org/feed/", "https://globalvoices.org", note = "Citizen media"),
        world("restofworld", "Rest of World", "https://restofworld.org/feed/latest", "https://restofworld.org", note = "Technology beyond the West"),
        world("rnz", "RNZ", "https://www.rnz.co.nz/rss/news.xml", "https://www.rnz.co.nz", note = "New Zealand"),
        world("sbs", "SBS News", "https://www.sbs.com.au/news/topic/world/feed", "https://www.sbs.com.au/news", note = "Australia"),
        world("straitstimes", "The Straits Times", "https://www.straitstimes.com/news/world/rss.xml", "https://www.straitstimes.com", note = "Singapore"),
        world("rappler", "Rappler", "https://www.rappler.com/feed/", "https://www.rappler.com", note = "Philippines"),
        world("koreaherald", "The Korea Herald", "https://www.koreaherald.com/rss/newsAll", "https://www.koreaherald.com", note = "South Korea"),
        world("yonhap", "Yonhap", "https://en.yna.co.kr/RSS/news.xml", "https://en.yna.co.kr", note = "South Korea"),
        world("thehindu", "The Hindu", "https://www.thehindu.com/news/international/feeder/default.rss", "https://www.thehindu.com", note = "India; some articles are for subscribers"),
        world("scroll", "Scroll.in", "https://feeds.feedburner.com/ScrollinArticles.rss", "https://scroll.in", note = "India"),
        world("dawn", "Dawn", "https://www.dawn.com/feeds/home", "https://www.dawn.com", note = "Pakistan"),
        world("africanews", "Africanews", "https://www.africanews.com/feed/rss", "https://www.africanews.com", note = "Africa"),
        world("allafrica", "AllAfrica", "https://allafrica.com/tools/headlines/rdf/latest/headlines.rdf", "https://allafrica.com", note = "Africa"),
        world("dailymaverick", "Daily Maverick", "https://www.dailymaverick.co.za/dmrss/", "https://www.dailymaverick.co.za", note = "South Africa"),
        world("premiumtimes", "Premium Times", "https://www.premiumtimesng.com/feed", "https://www.premiumtimesng.com", note = "Nigeria"),
        world("mercopress", "MercoPress", "https://en.mercopress.com/rss/", "https://en.mercopress.com", note = "South Atlantic"),
        world("batimes", "Buenos Aires Times", "https://www.batimes.com.ar/feed", "https://www.batimes.com.ar", note = "Argentina"),
        world("mexiconewsdaily", "Mexico News Daily", "https://mexiconewsdaily.com/feed/", "https://mexiconewsdaily.com", note = "Mexico"),
    )

    fun byId(id: String): NewsSource? = ALL.firstOrNull { it.id == id }
}
