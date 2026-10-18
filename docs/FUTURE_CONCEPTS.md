# SpeedRead: future concepts

This is the full list of ideas for SpeedRead, gathered in one place. ✅ marks concepts already shipped (v1.8 and v1.9). It covers
the reader, the library, the Newsstand, the platform and the project itself. Nothing here
is committed work. [`PROJECT_PLAN.md`](PROJECT_PLAN.md) §6 holds the prioritised backlog,
and items move there when they are scheduled.

Each concept gets a rough **value** (how much it improves daily use) and **effort**
(S ≈ a day, M ≈ a few days, L ≈ a week or more). ★ marks the recommended next picks.

The constraints from the project plan still apply. No tracking or analytics. Books never
leave the device. The internet is used only for news, and only when the user asks. Every
control works with TalkBack and at 200 % font scale.

---

## 1. Reading engine (RSVP)

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ✅ Chunk mode | **Done in v1.8:** up to 2 or 3 short words per flash. Phrases stop at commas and sentence ends, and long words stand alone. | High | M |
| ✅ Speed training | **Done in v1.8:** +25 wpm per 2 minutes of reading, up to a chosen target. Still to come: suggesting a new default speed. | High | S |
| Comprehension checks | An optional 3-question quiz after a chapter or article, generated on-device from the text: cloze sentences drawn from key sentences, no AI service. Its score informs speed suggestions. | Medium | M |
| Adaptive pacing v2 | Slow down for rare words (a frequency list per language), numbers, proper nouns and words in quotes. Speed up through dialogue tags ("he said"). | Medium | M |
| Sentence-aware rewind | Swipe left on the word strip to step back word by word while paused, and long-press to scrub. | Medium | S |
| Peripheral context | A faint previous word and next word on either side of the focus word, for readers who find pure RSVP disorienting. | Medium | S |
| Pause on punctuation styles | Different pauses for paragraph, section break (`***`), dialogue and list item. | Low | S |
| Eye-rest reminders | A gentle reminder every 20 minutes to look away (20-20-20 rule), with a configurable interval. | Low | S |
| Bionic or "focus prefix" mode | An alternative to ORP colouring: the first letters are bold. It is shown in the normal page view too. | Low | S |
| ✅ Classic page view toggle | **Done in v1.9:** the chapter as text with the current word highlighted. Tapping a word continues RSVP from there. | High | M |

## 2. Reader UI

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ✅ Scrubber and bookmarks | **Done in v1.8:** the chapter scrubber previews the word under the thumb. Bookmarks are added from the top bar and listed beside the chapters. Still to come: named bookmarks. | High | M |
| ✅ Hardware controls | **Done in v1.8:** keyboards, page turners, headset buttons and (optionally) volume keys. Still to come: haptic ticks. | High | S |
| Highlights and notes | Long-press while paused to highlight the current sentence and add a note. Export as Markdown. | Medium | M |
| Landscape and large screens | A landscape reader layout, plus tablet and foldable list-detail via Material 3 adaptive. Tabletop posture on foldables puts the word on top and controls below. | Medium | M |
| Sleep timer | Stop after N minutes or at the end of the chapter, for bedtime reading. | Medium | S |
| Reading themes | Sepia and true-black OLED themes, letter spacing, line height for context view, and custom ORP colour. | Medium | S |
| Accessibility font pack | Bundled Atkinson Hyperlegible and OpenDyslexic, with an extra-wide letter-spacing option. | Medium | S |
| Picture-in-picture reading | Keep a small RSVP window over other apps (Android PiP), e.g. while cooking or on a video call. | Low | M |
| Lock-screen and media controls | A media-style session so play/pause works from headphones, the lock screen and Wear OS. | Medium | M |

## 3. Library and formats

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ✅ Library search and collections | **Done in v1.9:** search by title or author, Reading / Not started / Finished filters, and collections (rename, delete, add and remove books). | High | M |
| EPUB chapters from TOC fragments | Split chapters at TOC anchors for books that keep several chapters in one file. | Medium | M |
| PDF quality | Header and footer stripping, multi-column detection, a password prompt, and cached extracted text. | Medium | M |
| More formats | Plain text, Markdown, HTML files, FB2, DOCX (via ZIP + XML), and DRM-free MOBI/AZW3. | Medium | L |
| ✅ Clipboard reading | **Done in v1.8:** a copied link opens its article, and copied or shared text is read as it is. | High | S |
| Read-later integrations | Import from Pocket-style exports and Instapaper CSV, and "Share → SpeedRead" for Wallabag and other self-hosted services. | Medium | M |
| Reading statistics | Words read, time saved versus 250 wpm, effective wpm, streaks and a weekly chart, all on device. | Medium | M |
| Goals | Optional daily minutes or words goal, with a gentle streak (never guilt notifications). | Low | S |
| ✅ Backup and restore | **Done in v1.9:** one ZIP file through the system picker, holding books, covers, positions, bookmarks, collections, feeds and settings. Restoring merges. | High | M |
| Optional sync | End-to-end-encrypted progress sync through the user's own storage (WebDAV, or a file in their cloud drive). It is opt-in and has no SpeedRead server. | Medium | L |
| OPDS catalogues | Browse and download from Project Gutenberg, Standard Ebooks and Calibre servers. | Medium | M |

## 4. Newsstand

Implemented so far: 69 sources (v1.3); grouping, reading list, read marks and search
(v1.4); next story, data saver, background downloads and swipes (v1.5); topics, widget and
share-a-link (v1.6); breaking Irish news alerts and the morning briefing (v1.7).

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ✅ Custom feeds | **Done in v1.8:** feed or site address with autodiscovery, a chosen region, and removable feeds. Still to come: per-feed extraction hints. | High | M |
| ✅ Briefing options | **Done in v1.9:** stories per region, regions covered, and weekend notifications. Still to come: topic choice. | Medium | S |
| ✅ Alert options | **Done in v1.9:** alert regions, quiet hours (22:00–07:00) and a daily limit. Still to come: keyword alerts. | Medium | S |
| Story timeline | For a grouped story, show how coverage developed: first report, updates and other outlets, with times. | Medium | M |
| Bias and perspective view | Show a story's versions side by side by region ("Irish view / European view / world view"), using existing grouping. No ratings, just the versions. | Medium | M |
| Follow a story | "Follow" a grouped story to be notified, or see it in a Following tab, when new versions appear. | Medium | M |
| Local news | County-level Irish outlets with free feeds, where they meet the source criteria. Requires a strong case, per the source policy. | Medium | M |
| Language editions | Show DW, France 24, Euronews and others in German, French, Spanish and Irish, with matching tokenisation. | Medium | M |
| Podcast-style queue | A persistent "Up next" queue you can reorder: add from cards, and read it as a playlist. This generalises the briefing. | High | M |
| Article quality signals | Hide very short items (<150 words) and syndicated duplicates. Show reading time on cards once text is downloaded. | Medium | S |
| Offline edition | "Download today's edition": the top 30 stories with images, for flights. | Medium | S |
| Widget v2 | A resizable widget with a briefing-play button, region tabs and a Material You dynamic-colour theme. | Medium | M |
| Wear OS tile | Today's top 3 headlines, and tap to read on the phone. | Low | M |
| Feed health monitor | A weekly in-app check that flags sources whose feeds broke or went paywalled, with one-tap disable. | Low | S |

## 5. Text-to-speech and audio

| Concept | What it is | Value | Effort |
|---|---|---|---|
| RSVP + speech | Optional on-device TTS, word-synced with the RSVP display, for language learners and dyslexic readers. | High | L |
| Audio briefing | The morning briefing read aloud with on-device TTS: hands-free, while driving or walking. | Medium | M |

## 6. Learning and languages

| Concept | What it is | Value | Effort |
|---|---|---|---|
| CJK and Thai segmentation | Word segmentation with ICU `BreakIterator`, and character-based pacing for Chinese and Japanese. | Medium | M |
| RTL support | Mirrored ORP for Arabic and Hebrew, and RTL context view. | Medium | M |
| Hyphenation-aware long words | Split very long words (German compounds) into two flashes at hyphenation points. | Medium | S |
| Dictionary lookup | Long-press a paused word for an offline dictionary (Wiktionary dump), with a "learn later" word list. | Medium | M |
| Language-learner mode | Slower pace for words above a chosen frequency rank, with the tap-to-translate system API. | Low | M |

## 7. Platform and integration

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ✅ App shortcuts and Quick Settings tile | **Done in v1.8:** Continue reading, Play briefing and Speed-read clipboard, plus a clipboard tile. Shared plain text is read too. | High | S |
| Predictive back and shared-element transitions | A card-to-reader transition, and a predictive back preview on Android 15+. | Low | S |
| Per-app language | `LocaleConfig` and translated UI: Irish (Gaeilge), German, French and Spanish first. | Medium | M |
| Assistant and App Actions | "Hey Google, speed-read my briefing" and "continue my book" via App Actions. | Low | M |
| ChromeOS and desktop | Keyboard shortcuts and resizable windows. | Low | S |

## 8. Accessibility and wellbeing

| Concept | What it is | Value | Effort |
|---|---|---|---|
| Accessibility audit | A TalkBack, Switch Access and Voice Access pass, contrast checks, and Compose accessibility-test-framework checks in CI. | High | S |
| Reduced-motion mode | No animated countdowns or transitions when the system setting is on. | Medium | S |
| Seizure-safe limits | A cap on flash rate and contrast flicker at extreme speeds, as an optional safety limit. | Medium | S |
| Dyslexia profile | A one-tap profile: font, spacing, slower start, and peripheral context on. | Medium | S |

## 9. Quality, performance and release

| Concept | What it is | Value | Effort |
|---|---|---|---|
| ★ Baseline profile and macrobenchmarks | Faster cold start and smoother reader frames. Measure startup and reader jank in CI. | High | S |
| Instrumented tests on Gradle Managed Devices | Run the Robolectric UI tests on real emulators (API 26, 30, 36) in CI. | High | M |
| Roborazzi screenshot diffs | Turn today's screenshot renders into reviewed visual regression tests. | Medium | S |
| EPUB and PDF corpus test | Import ~200 public-domain books in CI and check for crashes, empty chapters and timing. | Medium | M |
| Feed contract tests | A nightly job that probes all 69 feeds and extractors and opens an issue when one breaks. | Medium | S |
| Play Store release | Upload key, AAB, store listing, privacy policy, Data safety form ("no data collected") and staged rollout. | High | M |
| F-Droid build | Reproducible builds and metadata; the app already has no proprietary dependencies. | Medium | S |
| Hilt and convention plugins | Adopt them if the graph grows: `build-logic` convention plugins and Hilt replacing `AppContainer`. | Low | M |
| Crash reporting, privately | An opt-in, on-device crash log the user can share by email. No SDKs. | Medium | S |

---

## Recommended next steps

v1.8 shipped shortcuts and the clipboard tile, custom feeds, the scrubber, bookmarks, hardware
controls, chunk mode and speed training. v1.9 added library search and collections, briefing
and alert options, backup and restore, and page view. Next:

1. **Baseline profile and an accessibility audit.** Faster start-up and a TalkBack pass, before
   wider release.
2. **Reading statistics.** Words read, time saved and streaks, now that there's history worth
   showing.
3. **Highlights and notes.** They build on bookmarks.
4. **Play Store and F-Droid release.**
5. **Podcast-style reading queue.** It generalises the briefing.
