# Newsstand: UX and quality-of-life plan

The Newsstand (v1.3) has 69 sources, previews, and two ways to read: RSVP in the app or
the original page. This plan covers making it pleasant to use every day. The source
catalogue is considered complete. New outlets are added only for a strong reason, such as
an outlet dropping its feed or a clear gap in Irish coverage.

## 1. What gets in the way today

Observations from using v1.3 on a phone-sized emulator with live feeds:

| # | Friction | Why it matters |
|---|---|---|
| F1 | Cards are tall. Two full-width buttons per story means only about 2.5 stories per screen. | Scanning headlines is the main task, and scrolling dominates it. |
| F2 | The same story appears many times. One event (a papal discharge, a sanctions vote) shows up from the BBC, DW, NPR, PBS and CNA in a row. | With 22 sources on, duplicates crowd out other news. |
| F3 | Nothing shows what you have already read. Opened stories look identical to new ones. | You re-scan the same cards on every visit. |
| F4 | There is no way to keep a story for later. Stories vanish when the feed moves on, and reading needs a connection at the moment you tap. | You can't "save for the commute"; RSVP is ideal for short offline sessions. |
| F5 | Half-read articles are only discoverable if the story is still in the feed. | Progress is saved but hard to get back to. |
| F6 | You can't find a story you saw earlier ("that piece about the budget"). | The list holds hundreds of headlines. |
| F7 | Muting a noisy outlet means a trip to Settings. | The friction discourages tuning the feed. |
| F8 | Stories can't be shared. | A basic expectation for news. |
| F9 | Pull to refresh gives no sense of what changed. | You don't know whether refreshing helped. |
| F10 | Tapping the News tab again does nothing. | The platform convention is "scroll to top". |

## 2. Plan

### Phase 1: implemented in v1.4 ✅

1. **Compact cards (F1).** Each card has a meta line (outlet · age), a 3-line headline and
   a 2-line teaser, with a 72 dp thumbnail beside the text. The action row has
   **Speed-read** (tonal button) plus icon buttons for **Save**, **Open online** and
   **More** (Share, Hide outlet). This fits about 4 stories per screen, and every action
   keeps a text label for TalkBack.
2. **"Also covered by" grouping (F2).** Headlines about the same event are grouped under
   the highest-ranked one (Irish before European before world, then feed position). The
   card says "Also covered by DW, NPR +2", and its preview sheet lists the other versions,
   each tappable. Grouping uses title similarity: significant words in common, ignoring
   stop words and plurals, with ≥ 3 shared words covering ≥ 50 % of the shorter title (numbers ignored).
   This is tuned to avoid merging unrelated stories that share a name. Filtering to a
   single outlet turns grouping off.
3. **Read state (F3).** Stories you speed-read or opened online are marked: the headline is
   dimmed and "Read" appears in the meta line. A **Hide read stories** switch in the
   Newsstand menu is remembered across launches.
4. **Reading list (F4).** **Save** keeps a story (title, teaser, image, outlet) after it
   leaves the feed. It also downloads the article text in the background, so it can be
   speed-read offline. A **Saved** chip shows the list, newest first. Saved articles are
   never pruned; unsaving lets normal 30-day retention apply.
5. **Continue reading (F5).** A strip at the top of the list holds articles you started
   but didn't finish, newest first, with their progress. It is hidden while searching or
   viewing Saved.
6. **Search (F6).** A search action in the top bar filters the loaded headlines by headline
   and teaser text, within the current region or outlet filter, or within Saved. It
   includes a clear button and a "No stories match" state.
7. **Hide an outlet from its card (F7).** **More → Hide stories from DW** switches the
   source off, with an **Undo** snackbar. The same switch is in Settings.
8. **Share (F8).** **More → Share** (also in the preview sheet) opens the Android share
   sheet with the headline and link.
9. **Refresh feedback (F9).** A manual refresh reports "12 new stories" or "No new stories
   yet".
10. **Tab reselect (F10).** Tapping News while on the Newsstand scrolls back to the top.

**Data.** Room schema v4 adds a `news_history` table: canonical URL (primary key), outlet,
headline, teaser, image, publish date, `read_at` and `saved_at`. It powers read marks,
Saved and Continue reading, survives feed rotation, and is pruned with the articles
(unsaved entries older than 30 days). The upgrade is an automatic migration, covered by
the existing upgrade test from version 1.

### Phase 2: implemented in v1.5 ✅

- **Read the news hands-free.** The Newsstand remembers the list a story was opened from
  (the current feed and filter, Saved, or Continue reading). While you read, the reader
  finds the next unread story in that list and downloads it in the background. When the
  article ends, an **Up next** panel shows it with a 5-second countdown, and the next story
  then opens and starts playing by itself. You can cancel, or tap to start now. A story
  that can't be read (paywall, no text, offline) is skipped for the one after. "Continue
  to the next story" in Settings turns the countdown off (default on). The next story
  replaces the current reader, so Back still returns to the Newsstand.
- **Data saver.** Settings → News images: **Always**, **On Wi-Fi only** (no thumbnails on
  metered networks, followed live from the system) or **Never**.
- **Background refresh and pre-download.** Settings → Download news in the background
  (off by default). WorkManager runs every 4 hours, only on unmetered networks with the
  battery not low. It refreshes the enabled feeds, then downloads saved stories and the
  top 8 unread stories (Irish first). Articles already downloaded are never fetched again.
- **Swipe actions.** Swipe a card right to save or unsave it, left to mark it read or
  unread. The card springs back. The same actions are on the card (Save, and Story
  options → Mark as read/unread) for TalkBack users.
- **Per-region switches** in Settings: "Turn on all of Europe" / "Turn off all of Europe".

### Phase 3: implemented in v1.6 ✅

- **Topic filters.** A **Topics** chip opens a menu with Politics, Business, Sport, Science
  & tech, Health, Climate and Culture, each with its story count. Topics come from the
  story's URL sections (`/sport/rugby/…`, `/news/business/…`), the most reliable signal
  across outlets, plus feed categories where outlets provide them. Article slugs are
  ignored, so "sport-shop-closes" isn't Sport. On live feeds about a fifth of headlines
  get a topic; the rest are general news, which is expected for top-stories feeds.
  Over-broad keywords were removed after checking real headlines ("energy" had tagged war
  coverage as Climate).
- **Home-screen widget** (Glance). It shows the latest headlines from the enabled Irish
  outlets, or top headlines if none are on. It reads the cached headlines and updates
  whenever they refresh, in the app or the background sync; it never fetches by itself.
  Tapping a headline opens the Newsstand with that story's preview (filters are cleared
  so it is visible), and tapping the title opens the Newsstand.
- **Share any web link to SpeedRead.** From a browser or any app, Share → SpeedRead
  extracts the article and opens it in the RSVP reader. The title comes from the page, and
  the outlet is credited if it is in the catalogue (otherwise the site name is used). A
  progress dialog can be cancelled. Failures (offline, no article text, subscriber-only)
  offer **Open online**. Shared text without a link gets a short message.

### Later ideas

- Notifications for breaking Irish headlines (opt-in), building on the background sync.
- A morning "briefing" playlist: the top story of each enabled region, read back to back.

## 3. Quality bar

- Every new action has a visible label or content description, and works with TalkBack
  and 200 % font scale.
- No new network traffic happens without a user action, except downloading articles the
  user explicitly saved.
- Unit and Robolectric tests cover:
  - grouping (positive and negative examples taken from real headlines);
  - history (read, save, prune);
  - ViewModel filtering and search;
  - the new card and sheet interactions.
- An emulator pass with live feeds is done before release.
