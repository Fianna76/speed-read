# SpeedRead — Project Plan

SpeedRead is an Android e-reader for EPUB and PDF books that uses **Rapid Serial Visual Presentation
(RSVP)**: words are shown one at a time at a fixed point on screen, each aligned on its
*optimal recognition point* (ORP), so the eyes don't have to travel across lines and pages.

This document is the plan for the app. The repository already contains a working
first release (v1.0.0) built from it, plus PDF support added in v1.1.0, the Newsstand in v1.2.0, 69 news sources in v1.3.0 Newsstand quality-of-life features in v1.4.0–v1.6.0, breaking-news alerts and the morning briefing in v1.7.0, and reader and convenience features in v1.8.0 (shortcuts, clipboard reading, custom feeds, a scrubber, bookmarks, hardware keys, chunk mode and speed training) ([`NEWSSTAND_UX_PLAN.md`](NEWSSTAND_UX_PLAN.md)). §6 marks what is done and what comes next.

---

## 1. Goals

| # | Requirement (from the brief) | How v1.0 meets it | Status |
|---|---|---|---|
| R1 | Accept standard `.epub` files provided by users | EPUB 2.0.1 and EPUB 3.x parser. Books arrive through the system file picker (multi-select), Android's "Open with" or "Share". Every file is validated, and DRM-protected and duplicate books are detected. | ✅ |
| R2 | A library of uploaded EPUBs, with selection | Cover grid with title, author and progress. Tap a book to read it. Long-press starts multi-select, with select-all and delete (after confirmation). Four sort orders. | ✅ |
| R3 | WPM easily adjustable with sliders | A 100–1000 wpm slider (25-wpm steps) plus −/+ buttons sit on the reading screen and apply mid-sentence. The speed is persisted, and Settings has a default-speed slider. | ✅ |
| R4 | Easy single-tap controls: pause, skip forward and back | Three invisible tap zones over the text: left = previous sentence, centre = play/pause, right = next sentence. A transport bar repeats them and adds previous/next chapter. | ✅ |
| R5 | Best practices | Modular architecture, unidirectional data flow, tests at every layer, lint-clean, CI, R8, privacy by default. See §4–§5. | ✅ |
| R7 | PDF support (added after v1.0) | Text-based PDFs import through the same paths. Chapters come from the PDF outline (bookmarks), or from 10-page sections. Line wrapping, hyphenation and page numbers are cleaned up. Password-protected and scanned (image-only) PDFs are rejected with a clear reason. The cover is the rendered first page. | ✅ |
| R8 | Newsstand (added after v1.1; widened in v1.3) | Top stories from 69 outlets' free public RSS feeds, organised Ireland → Europe → World (13 Irish, 22 European, 34 global; 22 on by default). Region and outlet filters; Irish stories lead each round of headlines. Each story shows a preview (outlet, age, headline, teaser, thumbnail, plus a detail sheet). Every story can be opened online (Custom Tab) or read in the app with RSVP: the page's story text is extracted reader-mode style. Sources can be switched on and off. Reuters and AP are not included because neither offers a public feed and both block automated access (§7). | ✅ |
| R6 | Deliverable: one packaged `.apk` | A signed, R8-minified release APK built with `./gradlew assembleRelease`. | ✅ |

### Non-functional goals

- **Privacy.** No analytics or tracking. Books never leave the device: they are copied into
  app-private storage, and cloud backups include preferences only. The `INTERNET`
  permission (added in v1.2) is used only by the Newsstand to fetch feeds, thumbnails and
  the articles the user opens.
- **Reading quality.** Accurate timing: a deadline-based scheduler, so speed doesn't drift.
  Words draw without jitter, in one draw pass with no layout pass. Pacing adapts to
  punctuation, long words and numbers.
- **Robustness.** Handle the messy EPUBs found in the wild, guard against hostile ones (ZIP
  bombs, XXE, path traversal), and never lose reading position.
- **Accessibility.** TalkBack labels for every control and tap zone, 48 dp+ touch
  targets, `sp` text sizes, and no meaning carried by colour alone.
- **Compatibility.** Android 8.0 (API 26) through Android 17 (API 37). Phone-first, but
  works on tablets (adaptive grid).

### Out of scope for v1

DRM-protected books (Adobe ADEPT, Readium LCP), OCR for scanned PDFs, MOBI/AZW3, cloud sync, text-to-speech,
annotations, CJK word segmentation, right-to-left ORP tuning, paywalled or sign-in-only news,
user-added feeds.

---

## 2. User experience

### 2.1 Screens and flows

```
   bottom bar: Library | News
 ┌──────────────┐  tap book   ┌──────────────┐  ☰   ┌───────────────┐
 │   Library    │ ──────────▶ │    Reader    │ ───▶ │ Chapter sheet │
 │  (grid)      │ ◀────────── │   (RSVP)     │      └───────────────┘
 └──────┬───────┘    back     └──────▲───────┘
        │                  Speed-read │        ↗ Open online (Custom Tab)
 ┌──────┴───────┐  tap card  ┌───────┴──────┐ ─┘
 │  Newsstand   │ ─────────▶ │ Preview sheet│
 │ (headlines)  │            └──────────────┘
 └──────┬───────┘
        │ ⚙
        ▼
 ┌──────────────┐     Other apps ──"Open with"/"Share"──▶ import queue ──▶ Library
 │   Settings   │                                              (snackbar: "Added …  [Read]")
 └──────────────┘
```

- **Library.** Book grid with generated covers when a book has none, a progress bar and
  "New", "42%" or "Finished" labels. A long press switches to a contextual top bar
  ("2 selected", select all, delete). The import FAB opens the system picker. Import
  progress and results show as a banner and snackbars, and a successful import's snackbar
  has a **Read** action.
- **Newsstand.** Source filter chips ("All" plus each enabled outlet), "Updated 5 min. ago"
  or a note naming unreachable outlets, then headline cards: outlet and age, headline,
  teaser, thumbnail, reading progress for started articles, and two buttons, **Speed-read**
  ("Continue" once started) and **Open online**. Tapping a card opens a preview sheet with
  a large image, byline, full teaser and the same two actions. Pull to refresh. Empty,
  offline and all-sources-off states each say what to do. Articles that can't be read in
  the app (video or live pages, network errors) get a snackbar with an **Open online** action.
- **Reader.** The word strip sits in the middle, with tap zones over the whole reading
  area. While paused, the surrounding sentence is shown with the current word highlighted.
  The bottom panel holds chapter progress, "43% · 4 hr, 40 min left", the speed slider and
  the transport buttons.
- **Settings.** Default speed, smart pacing, gentle start, text size (with a live preview),
  font, focus-letter highlight, context-when-paused, theme, and an About section.

### 2.2 Reader interaction design

```
 ┌──────────────┬──────────────────┬──────────────┐
 │ ◀◀ previous  │   ▶ / ❚❚         │    next ▶▶   │  ← single taps anywhere in the
 │   sentence   │   play / pause   │   sentence   │    reading area
 │     30%      │       40%        │      30%     │
 │                                                │
 │  ──────────────────┬───────────────────────    │  ← guide line, tick marks the focus
 │                 reading                        │  ← the "a" (the ORP) is drawn in red,
 │  ──────────────────┴───────────────────────    │    pinned under the tick at 40% width
 └────────────────────────────────────────────────┘
```

- **ORP alignment.** The fixation letter moves right by one letter for every four letters
  of word length (1 → 1st, 2–5 → 2nd, 6–9 → 3rd, 10–13 → 4th, 14+ → 5th). Leading and
  trailing punctuation is ignored when counting.
- **"Previous" works like a music player.** It restarts the current sentence. Tapped again
  at a sentence start, or within ~3 words while playing (to allow for reaction time), it
  goes to the previous sentence. Before the first sentence of a chapter it jumps to the
  previous chapter's last sentence.
- **"Next".** Jumps to the next sentence, or into the next chapter after the last one.
- **Smart pacing.** Display time is a multiple of 60 000 ms ÷ wpm:

  | Word ends with / is | Multiplier |
  |---|---|
  | plain word | ×1.0 |
  | clause punctuation `, ; : —` | ×1.5 |
  | sentence end `. ! ? …` | ×2.1 |
  | paragraph end | ×2.6 |
  | long word (> 7 letters) | +0.08 per extra letter (max +0.6) |
  | contains digits | +0.3 |
  | first 4 words after play/skip ("gentle start") | ×2.0, ×1.6, ×1.3, ×1.1 |

- **Safety nets.** The screen stays on while playing. Playback auto-pauses when the app
  goes to the background. The position is saved on every pause, on every chapter change,
  every 10 s while playing, and when the screen closes.

---

## 3. Technology choices

| Area | Choice | Why |
|---|---|---|
| Language | Kotlin 2.4 | Official Android language; coroutines and Flow for async and streams |
| Build | Gradle 9.8 (wrapper, checksum-pinned), AGP 9.4 with built-in Kotlin, version catalog, configuration cache | Current stable toolchain; one place for versions |
| SDK levels | `minSdk 26`, `compileSdk`/`targetSdk 37` | Covers the large majority of active devices; meets Play's target-API policy |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 1.4, dynamic colour, edge-to-edge | Modern declarative UI and Material You theming |
| Navigation | Navigation 3 (1.2) with per-entry ViewModel stores | Back stack is plain, saveable state; ViewModels are cleared when their screen is popped |
| State | ViewModel + `StateFlow`, UI state as immutable data classes, one-off messages modelled as state | Unidirectional data flow, survives configuration changes |
| Persistence | Room 2.8 (exported schemas) for the library; DataStore Preferences for settings | Transactional, observable storage; schemas ready for migrations |
| EPUB parsing | Own parser on `java.util.zip` + jsoup 1.23 (lenient XML/HTML) | Small, testable on the JVM; full libraries such as Readium are overkill for text extraction |
| PDF text | PdfBox-Android 2.0.27 (text extraction, outline); platform `PdfRenderer` for covers | Android has no built-in PDF text API; PdfBox is the established open-source extractor. Unused Bouncy Castle data tables are stripped from the APK (4.1 MB total with the Newsstand) |
| Images | Coil 3 (OkHttp network fetcher) | Async, cached cover and news thumbnails in Compose |
| Networking | OkHttp 5 (shared client, disk cache, timeouts, 5 MB response cap) | The standard Android HTTP client; one connection pool for feeds, articles and images |
| News parsing | jsoup (XML parser for RSS 2.0 / RDF / Atom; HTML for articles) | Already a dependency; lenient with real-world feeds and pages |
| Open online | AndroidX Browser (Custom Tabs) | The user's own browser with their sign-ins and settings, on top of the app |
| DI | Manual `AppContainer` | No codegen for a graph this small; the constructor-injected classes can move to Hilt unchanged |
| Testing | JUnit 4, kotlinx-coroutines-test (virtual time), Robolectric 4.17, Compose UI test | Fast tests on the JVM at every layer, including UI, with no device required |
| Release | R8 full-mode shrinking, resource shrinking, optimized ProGuard defaults | Smaller APK, obfuscated code |

---

## 4. Architecture

### 4.1 Modules

```
:app         Android application: UI (Compose), ViewModels, data layer (Room, DataStore, files, HTTP), DI
:core:epub   Pure Kotlin/JVM: EPUB container/package/TOC parsing, XHTML → paragraphs
:core:news   Pure Kotlin/JVM: news source catalogue, RSS/Atom parsing, article text extraction
:core:rsvp   Pure Kotlin/JVM: tokenizer, ORP, pacing, coroutine-driven RsvpPlayer
```

The core modules have no Android dependencies. That enforces the layering, keeps the logic
reusable, and lets their tests run in milliseconds. `:core:epub` publishes **test
fixtures**, an in-memory EPUB builder that the app's tests reuse, so the repository contains
no binary test files.

### 4.2 Layers and data flow

```
            ┌─────────────────────── UI layer (:app/ui) ───────────────────────┐
 events ──▶ │ Composables  ──calls──▶  ViewModels  ──StateFlow<UiState>──▶ UI │
            └───────────────────────────────┬─────────────────────────────────┘
                                            │ suspend calls / Flows
            ┌──────────────────── Data layer (:app/data) ─────────────────────┐
            │ BookRepository   UserPreferencesRepository   ImportCoordinator  │
            │   │  Room (books, chapters)   DataStore    BookImporter          │
            │   └─ BookFileStore (app-private EPUB copies + cover thumbnails)  │
            └───────────────┬────────────────────────────────┬────────────────┘
                            │                                │
                   :core:epub (parse, extract)      :core:rsvp (tokenize, pace, play)
```

- **ViewModels** expose one immutable `UiState` each. `ReaderViewModel` also exposes the
  fast-changing `PlayerState` on its own, so a new word only recomposes what reads it.
- **`ImportCoordinator`** is application-scoped and processes imports one at a time.
  Imports therefore finish even if the user leaves the screen, and the picker, "Open with"
  and "Share" all share one path.
- **Writes that must outlive a screen** (the final position save) run in an application
  scope. A mutex serialises them, so an older position can never overwrite a newer one.

### 4.3 EPUB pipeline (import time)

1. **Copy and hash.** Stream the content URI into `files/import-tmp/` while computing
   SHA-256. Anything over 256 MiB is rejected.
2. **Deduplicate.** A matching hash returns *"already in your library"*.
3. **Parse.** `META-INF/container.xml` → OPF package document (metadata, manifest, spine) →
   TOC from the EPUB 3 navigation document, falling back to the EPUB 2 NCX. Lenient
   fallbacks: missing `container.xml`, prefixed elements, percent-encoded hrefs, entry-name
   case mismatches.
4. **Check for DRM.** Book content listed in `META-INF/encryption.xml` means DRM; font
   obfuscation alone is fine.
5. **Index chapters.** For every linear spine document, extract paragraphs, count words with
   the same tokenizer the reader uses, and take the title from the TOC or the first
   heading. Empty sections are skipped.
6. **Cover.** EPUB 3 `cover-image` → EPUB 2 `<meta name="cover">` → guide cover page → name
   heuristic. The image is decoded down-sampled and stored as a 600 px JPEG thumbnail.
7. **Commit.** The EPUB moves into `files/books/<sha256>.epub` with an atomic rename. The
   book and its chapters are inserted in one Room transaction. Files are rolled back on any
   failure.

When reading, only the current chapter is extracted and tokenised (fast, low memory).

### 4.3.1 PDF pipeline

Steps 1, 2 and 7 are shared with EPUB. The format is identified from the file's first bytes
(`PK` = EPUB, `%PDF-` = PDF), never from the name or MIME type.

- **Open.** PdfBox opens the file with mixed memory/temp-file buffering, so large files
  don't exhaust the heap. A required user password → *password-protected*. A damaged file
  → *not a valid EPUB or PDF*.
- **Sections.** If the outline has at least two top-level bookmarks, each bookmark runs to
  the next one, plus an untitled front-matter section before the first. Otherwise pages are
  grouped in tens. Sections longer than 30 pages are split into 10-page parts, so
  extracting one while reading stays quick. A section's page range is stored as its
  `href` (`pages:<start>-<end>`).
- **Text.** Lines are joined into PdfBox-detected paragraphs. End-of-line hyphenation is
  undone when the next line continues in lowercase. Lines holding only a page number are
  dropped. Sections with no text layer (scans) are skipped, and a book with none is
  rejected.
- **Cover.** The first page is rendered at 600 px with the platform `PdfRenderer`. This is
  best-effort: failure just means a generated cover.
- **Schema.** `books.format` was added in database version 2. Room's auto-migration
  defaults existing rows to `EPUB`, and a test upgrades a real version-1 database built
  from the exported schema.

### 4.3.2 Newsstand pipeline

- **Sources.** `NewsSources.ALL` (`:core:news`) lists each outlet's region (Ireland, Europe
  or World), top-stories RSS feed, homepage, whether it is on by default, a short note
  ("As Gaeilge", "Farming", "Poland"…) and an optional CSS hint for its article body.
- **Selection criteria.** A source must:
  - publish a free public feed with fresh items;
  - serve articles without a subscription;
  - extract cleanly in an automated probe that fetches the feed and three live articles
    through the app's own parser and extractor.

  Over 100 candidates were probed. Rejected:
  - paywalled: The Irish Times, Irish Examiner, The Irish News, The Local, SCMP, LA Times;
  - stale feeds: CNN, VOA, BreakingNews.ie, Middle East Eye;
  - blocked or challenge pages: Politico Europe, Euractiv, Times of Israel, Japan Today;
  - JavaScript-only article text: Times of India, Hindustan Times;
  - no public feed: Reuters, AP.

  Four outlets (Irish Independent, Belfast Telegraph, Sky News, Ukrainska Pravda) had
  working feeds, but their article pages refused the test environment's data-centre
  network. They are off by default.
- **Paywalls.** A page whose JSON-LD declares `isAccessibleForFree: false` (the schema.org
  paywall convention) is never extracted, even when the subscriber text is in the HTML.
  The app says the article is for subscribers and offers **Open online**.
- **Headlines.** `NewsRepository` fetches enabled sources in parallel. Each source fails
  independently: its older headlines are kept and it is named in the status line. Items
  that are video, audio, galleries or live blogs (by URL path) are dropped, because there
  is nothing to speed-read. Up to 20 items per source are kept. "All" interleaves outlets
  by feed position (every outlet's lead story first), so one busy feed can't crowd out the
  rest. Headlines are cached as JSON, so the screen opens instantly and offline. Feeds
  refresh when older than 15 minutes, on pull, or on demand.
- **Article text.** `ArticleExtractor` works like a reader mode:
  1. Strip page chrome: navigation, figures and captions, sharing, related links,
     screen-reader-only labels, timestamps, video players and promos.
  2. Choose the body. The source's selector is used if it yields ≥ 60 words. Otherwise
     paragraphs are scored (length and commas) towards their parent and, at half weight,
     their grandparent, and the winner widens to an ancestor holding ≥ 1.25× more story
     text (for sites that wrap each paragraph separately).
  3. Collect the innermost `p`/`h2–h4`/`blockquote`/`li`, minus link-heavy blocks and
     known boilerplate ("Edited by", newsletter or donation pleas).
  4. Pages under 60 words are rejected as "no article text".

  On the nine launch sources, the generic path and the hinted path produce identical text.
- **Reading.** A story opened in the app is saved as a hidden `ARTICLE` row in `books`.
  It has one chapter, its text sits in the chapter text cache, and `source_url` holds the
  canonical URL with tracking parameters stripped. The existing reader, position saving and
  progress therefore work unchanged. The library query excludes articles, the Newsstand
  shows their progress by URL, and articles not opened for 30 days are pruned.
- **Network etiquette.** The User-Agent is browser-compatible but names the app
  (`Mozilla/5.0 (Linux; Android …) SpeedRead/1.8.0`); one firewall rejects anything else.
  Pages are fetched only when the user asks to read them. Requests use 15 s connect /
  45 s call timeouts and a 5 MB response cap, and are cancelled with their screen.

### 4.4 RSVP engine (`:core:rsvp`)

- **`Tokenizer`.** Splits on Unicode whitespace and after em/en dashes inside tokens, and
  classifies each boundary (none, clause, sentence or paragraph). Abbreviations and
  initials such as "Mr.", "J." and "U.S." don't end sentences, and neither does terminal
  punctuation followed by lowercase ("Wait!" she said). It indexes sentence and paragraph
  starts for O(log n) navigation.
- **`OptimalRecognitionPoint`.** Counts letters by code point, so surrogate pairs and
  combining accents stay intact.
- **`RsvpPlayer`.** A coroutine loop that shows each word for its paced duration. Deadlines
  are absolute against an injected monotonic clock (the main looper's `uptimeMillis`).
  Small scheduling delays are absorbed, but a long stall never makes words "catch up".
  Speed changes take effect from the next word. The player is fully testable with virtual
  time.

### 4.5 Data model

| Table `books` | | Table `chapters` | |
|---|---|---|---|
| `id` PK | autoincrement | `book_id` FK → books (cascade) | composite PK with `chapter_index` |
| `title`, `author`, `language`, `format` | metadata; `format` = `EPUB`/`PDF` (v2)/`ARTICLE` (v3) | `chapter_index` | reading order |
| `file_name`, `cover_file_name` | app-private files | `title` | from TOC/heading, nullable |
| `content_hash` | unique, dedupe | `href` | EPUB document path, or PDF `pages:<start>-<end>` |
| `total_words` | | `word_count`, `start_word` | for book-level progress |
| `added_at`, `last_opened_at` | sorting | | |
| `chapter_index`, `word_index`, `words_read` | reading position | | |
| `source_url` | news article's canonical URL (v3), `NULL` for books | | |

Table `news_history` (v4): canonical `url` (PK), the published `link`, `source_id`, `title`,
`summary`, `image_url`, `published_at`, `author`, `read_at` and `saved_at`. It powers read
marks, the reading list and "Continue reading", and keeps a story's headline after the story
leaves its feed.

Settings live in DataStore Preferences (`files/datastore/settings.preferences_pb`): wpm,
smart pacing, gentle start, font size, font, highlight, context, theme, library sort,
news sources switched off and on (overrides of each source's default). Headlines are cached in `cache/news/headlines.json`, and HTTP
responses in `cache/http/`.

### 4.6 Security and privacy

- No runtime permissions. Files come in only through the Storage Access Framework or
  intents with temporary URI grants. `INTERNET` (install-time) serves the Newsstand and shared links only;
  `ACCESS_NETWORK_STATE` lets the data saver and background sync tell Wi-Fi from mobile
  data. Network access happens only on a user action, except the opt-in background sync.
  Traffic is HTTPS (cleartext is disabled by default at this target SDK), and no
  identifiers, cookies or analytics are sent.
- Archive entries are read into memory by name and never extracted, so zip-slip can't
  happen. Entry count and inflated sizes are capped against decompression bombs.
- jsoup never resolves DTDs or external entities, so parsing is safe from XXE.
- Cloud Auto Backup includes only preferences (books can be large and stay local). On
  Android 12+ a device-to-device transfer moves the whole library.
- Release signing credentials come from a git-ignored `keystore.properties` or environment
  variables. No secrets are in the repository.

---

## 5. Quality strategy

| Layer | Tooling | What's covered |
|---|---|---|
| Core logic (JVM) | JUnit 4 | EPUB 2/3 parsing, fallbacks, DRM detection, size limits, XHTML extraction (entities, footnotes, ruby, soft hyphens), tokenizer, ORP (Unicode), pacing, PDF text clean-up, format sniffing |
| PDF import | Robolectric + PDFs generated with PdfBox | Outline chapters with front matter, 10-page sections, password-protected, scanned and damaged files, chapter text by page range |
| Migrations | Robolectric + real SQLite | A version-1 database built from `app/schemas/…/1.json` opens with the current code (1→2→3) |
| News (JVM) | JUnit 4 | RSS 2.0/Atom parsing (media images, HTML teasers, zone-name dates, non-article links, limits), URL canonicalisation, article extraction (chrome removal, block-wrapped stories, source hints, title suffixes, rejection) |
| Newsstand v1.5–1.6 | JUnit 4 + Robolectric + WorkManager test helper | Next story (offer, prefetch, countdown, cancel, skip unreadable, auto-play), background sync (what is downloaded, once) and its scheduling constraints, data saver, read/unread, region switches, topic classifier on real URL patterns and topic filter, shared links (URL finding, titles, failures, dialogs), widget tap preview |
| Newsstand UX | JUnit 4 + Robolectric | Story grouping on real headline pairs (matches and near-misses), read/save history and pruning, ViewModel grouping/search/hide-read/reading list/continue reading/hide-and-undo/new-story counts, card menus, preview sheet versions, search bar and empty states |
| News (app) | Robolectric + MockWebServer + fakes | HTTP client (UA, errors, size cap); feed refresh, failure isolation, staleness, cache restart; saving articles, re-use, progress, failures, pruning; Newsstand ViewModel and UI (cards, preview sheet, filters, preparing, snackbar, empty/offline states) |
| Playback timing | kotlinx-coroutines-test (virtual time) | Exact word timing, pause, speed changes, ramp-up, sentence skips, end-of-text |
| Data layer | Robolectric + in-memory Room + real files | Import (success, duplicate, invalid, DRM, no text, unreadable, cover thumbnail), repository, preferences |
| ViewModels | Robolectric + fakes + virtual time | Library sorting, selection, deletion, import messages; reader position restore, chapter auto-advance, skips across chapters, finishing, speed persistence, error and retry |
| UI | Compose UI tests on Robolectric | Empty library, opening, long-press selection, delete confirmation, tap zones, transport and speed buttons, paused context, hint, finished state |
| End-to-end | Robolectric + real app graph | Import → library → read with the real clock → back → progress saved |
| Rendering | Robolectric native graphics | Every screen renders in light and dark themes; PNGs saved for review |
| Static analysis | Android Lint (`abortOnError`, dependencies checked) | Currently zero warnings |
| CI | GitHub Actions | Lint + all tests + release APK uploaded as a build artifact on every push and PR |
| Release smoke test | R8-minified APK on an Android 9 (API 28) emulator, with real Project Gutenberg EPUBs and live news feeds | Picker multi-import (including an invalid file), "Open with", duplicate detection, real covers, reading with tap zones, chapter jumps, speed changes, delete, live theme switch, restore after process death, upgrade install keeping data (1.1 → 1.2); Newsstand with live feeds and thumbnails, filters, preview sheet, Open online, speed-reading a live BBC article |

**Manual QA matrix before a store release.** API 26, 30, 34 and 37. A small phone, a large
phone and a tablet. Light and dark themes. TalkBack. 200% font scale. A corpus of
public-domain EPUBs from Project Gutenberg and Standard Ebooks (EPUB 2 and 3, with and
without covers, large books).

---

## 6. Delivery plan

| Phase | Scope | Status | Effort |
|---|---|---|---|
| 0. Foundations | Toolchain, modules, version catalog, theming, icon, CI | ✅ Done | 1–2 d |
| 1. Core engine | EPUB parser, text extraction, tokenizer, ORP, pacing, player, tests | ✅ Done | 3–4 d |
| 2. MVP app | Data layer, import flow and intents, library, reader, settings, tests | ✅ Done | 4–6 d |
| 3. Hardening | Device QA matrix (§5), on-device instrumented tests with Gradle Managed Devices or Firebase Test Lab, baseline profile (Macrobenchmark), accessibility audit, EPUB corpus test, low-end performance profiling | Next | 3–5 d |
| 4. Release | Upload key and Play App Signing, AAB, store listing and screenshots, privacy policy and Data safety form ("no data collected"), staged rollout | Planned | 2–3 d |
| 5. Enhancements | Prioritised backlog below | Backlog | — |

**Enhancement backlog, in priority order**

1. Chapters split at TOC fragments, for books with several chapters per file.
   PDF: header/footer stripping, multi-column detection, password prompt, cached extracted text.
2. ~~A scrubber to jump anywhere in a chapter, and bookmarks.~~ Done in v1.8.
3. ~~Chunk mode (2–3 words per flash) and an optional speed-training ramp.~~ Done in v1.8.
4. A bundled high-legibility font (e.g. Atkinson Hyperlegible), plus dyslexia-friendly options.
5. Reading statistics: words read, effective wpm, streaks.
6. Library search and collections.
7. Landscape reader layout, and tablet/foldable list-detail via Material 3 adaptive.
8. ~~Hardware controls: volume keys, Bluetooth page turners.~~ Done in v1.8. Optional haptics.
9. CJK segmentation (ICU `BreakIterator`), RTL ORP mirroring, more languages
   (strings are already externalised).
10. News: ~~user-added RSS feeds~~ (done in v1.8), briefing and alert options, a reorderable reading queue,
    Reuters/AP if they ever offer a licensed feed. (Sharing links, topics and offline
    pre-download shipped in v1.5–v1.6.)
11. Platform: Hilt if the graph grows, convention plugins in `build-logic`, Roborazzi screenshot diffs.

The complete, unprioritised list of future concepts is in [`FUTURE_CONCEPTS.md`](FUTURE_CONCEPTS.md).

---

## 7. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| PDF text order is only as good as the file (multi-column layouts, headers, footnotes) | Garbled reading order in some PDFs | Paragraph detection + clean-up heuristics; per-section extraction; EPUB recommended when available; layout analysis is a backlog item |
| Real-world EPUBs are often malformed | Import failures, missing text | Lenient parsing with fallbacks; failing sections are skipped, not fatal; clear error per reason; corpus testing in phase 3 |
| DRM-protected purchases | Users can't read store-bought books | Detect DRM and explain it; scope statement in the store listing |
| Comprehension drops at high speed (a known RSVP trade-off: no regressions) | Poor learning outcomes | Smart pacing, gentle start, context while paused, one-tap sentence rewind, 300 wpm default |
| Timing jitter on low-end devices | Uneven rhythm | Deadline-based scheduling; strip drawn in one pass; baseline profile in phase 3 |
| Large books, memory pressure | Slow chapter loads, OOM | Lazy per-chapter extraction; bounded reads; down-sampled covers |
| R8 breaking reflection (Navigation 3 restores keys by class name) | Crash after process death in release builds | Explicit keep rule for `NavKey` routes; restore verified on an emulator with the minified APK; repeat on physical devices in phase 3 |
| Yearly target-API policy changes | Store delisting | Bump `targetSdk` each year; catalog-managed versions |
| News sites change their layouts or feeds | A source shows no headlines, or articles extract badly | Generic extraction doesn't depend on site hints; per-source failures are isolated and shown; "Open online" always works; a fixture-free probe against live pages is part of release QA |
| Outlets' terms on automated access | A source asks not to be fetched | Only public RSS feeds are used; pages are fetched once per user request, like a browser's reader mode; sources can be removed from the catalogue (or switched off by users) |
| Reuters and AP have no free feeds | Users ask for them | Documented in-app (source list) and here; they can be added if a licensed feed becomes available |

---

## 8. Build and release

```bash
./gradlew lint test        # static analysis + all JVM/Robolectric tests
./gradlew assembleDebug    # app/build/outputs/apk/debug/SpeedRead-1.6.0-debug.apk
./gradlew assembleRelease  # app/build/outputs/apk/release/SpeedRead-1.6.0-release.apk
```

- **Versioning.** Semantic `versionName` (`1.8.0`) and a monotonically increasing
  `versionCode`.
- **Signing.** Put `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in
  `keystore.properties`, or set the `SPEEDREAD_KEYSTORE_*` / `SPEEDREAD_KEY_*` environment
  variables in CI. Without them the release build is signed with the debug key, which is
  fine for sideloading and testing but not for the Play Store.
- **Debug and release side by side.** The debug build uses the `.debug` application ID
  suffix.
