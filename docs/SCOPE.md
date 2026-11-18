# StudyPack: Android app scope plan

A very basic, offline Android app for revising modules. It imports two kinds of file:

1. **Question packs** (`.studypack.json`): multiple-choice questions grouped by module and topic, in the same style as the ECG Amp Bench Demo Quiz.
2. **Notes PDFs** (`.pdf`): consolidated overview notes for a module or topic.

The app runs quizzes from the packs and opens the PDFs. Nothing goes to a server, and there are no accounts or sync.

---

## 1. Goals and non-goals

### Goals (v1)

- Import question packs and notes PDFs from the phone's storage, Downloads, Google Drive or OneDrive, using the system file picker or "Open with / Share to StudyPack".
- Keep a library grouped by module, with the notes and question packs for each module side by side.
- Run quizzes with the same behaviour as the web quiz:
  - questions shuffled, answer options shuffled
  - picks spread across topics, so short exams still cover every topic
  - a topic filter and exam lengths (12 / 25 / 45 / all)
  - a "weak spots" mode built from questions you have missed before
  - tutor mode (explain after each answer) or exam mode (explain at the end)
  - a results screen with scores by topic, a review of misses and "retry missed"
- Open notes PDFs inside the app, with page swiping and pinch zoom.
- Track per-question history on the device: times seen, times wrong, last seen.

### Non-goals (v1)

- No cloud sync, accounts, sharing or leaderboards.
- No question editing inside the app. Packs are written outside the app and re-imported.
- No question types other than single-answer multiple choice.
- ~~No maths typesetting (LaTeX). Text is plain Unicode (τ, Ω, µ, √, ≈, ±).~~ Added in 1.1.0: text fields may contain LaTeX formulas, `$…$` inline and `$$…$$` on their own line (see 3.6). Plain Unicode still works.
- No numbers generated on the phone. Calculation variety comes from variant groups prepared ahead of time (see 3.4).
- No Play Store release. The app is a sideloaded APK for personal use.
- No iOS version.

---

## 2. Users and use cases

The one user is a student revising for a bench demo, a viva or an exam.

| # | Use case | Flow |
|---|---|---|
| U1 | Import a pack | Claude makes a `.studypack.json` → the file is saved to Drive or Downloads → open StudyPack → **Import** → pick the file → it shows up under its module |
| U2 | Import notes | Same flow with a `.pdf`. If the file name is one the pack lists in `pack.notes`, it is attached to that module. Otherwise the user picks a module |
| U3 | Quick revision | Module → **Quiz** → Warm-up (12) → answer → read the explanation → results |
| U4 | Mock exam | Module → **Quiz** → Full mock (45), exam mode on → results at the end |
| U5 | Drill weak spots | Module → **Quiz** → My weak spots |
| U6 | Read notes | Module → tap a notes PDF → the viewer remembers the last page |
| U7 | Update a pack | Re-import a newer version of the same pack. Questions are replaced and history is kept for unchanged question IDs |
| U8 | Remove | Long-press a pack or PDF → Delete. Its history goes too |

---

## 3. File formats

### 3.1 Why JSON

- Claude and scripts can write and check it easily, and it is human-readable.
- Kotlin can parse it strictly with `kotlinx.serialization`.
- One file holds a whole pack, including its topics and the names of its notes.
- PDFs stay as separate files: they are big, binary, and useful outside the app as well.

**File extension:** `.studypack.json`. It is still a `.json` file, so every picker and cloud drive accepts it. The double extension is what lets the app recognise it in "Open with". The app also accepts plain `.json` files if `format` is set correctly inside.

### 3.2 Question pack schema (`schemaVersion: 1`)

```json
{
  "format": "studypack-mcq",
  "schemaVersion": 1,
  "pack": {
    "id": "ee5105-ecg-amp-single-supply",
    "title": "ECG Amplifier: Single-Supply Bench Demo",
    "moduleCode": "EE5105",
    "moduleName": "Applied Circuit Design for Medical Electronics",
    "version": "1.0.0",
    "created": "2026-11-18",
    "description": "Every component, corner and derivation of the final single-supply ECG amplifier.",
    "notes": ["EE5105-ecg-amp-overview.pdf"]
  },
  "topics": [
    { "id": "cmrr", "name": "CMRR", "description": "Common-mode rejection: mechanisms, budget, measurement" }
  ],
  "questions": [
    {
      "id": "cmrr-001",
      "topic": "cmrr",
      "type": "single",
      "difficulty": 2,
      "prompt": "Why does the CMRR graph have its shape (rising, peaking near 5 Hz, then falling)?",
      "correct": "Time-constant mismatch at low f, resistor ratio mid-band, stray C at high f",
      "distractors": [
        "It follows A_d: CMRR falls wherever the differential gain falls off",
        "The OP27's own CMRR rolls off above about 5 Hz, setting the peak",
        "The 50 Hz notch used in the analysis creates the peak near 5 Hz"
      ],
      "explanation": "A_c is a bowl: the time-constant term falls with f, the stray-C term rises with f, the resistor term is flat...",
      "modelAnswer": "Three mechanisms: input time-constant mismatch limits it at low frequency...",
      "sayIt": "Below 5 hertz it is limited by the mismatch of the two 68 second input time constants...",
      "variantGroup": null,
      "source": "Annotated Design Reference, derivation 5",
      "tags": ["demo-asked"]
    }
  ]
}
```

#### Field rules

| Field | Type | Required | Rule |
|---|---|---|---|
| `format` | string | yes | Must be `"studypack-mcq"` |
| `schemaVersion` | int | yes | `1`. The app refuses higher versions with the message "Update the app" |
| `pack.id` | string | yes | Lowercase slug `[a-z0-9-]{3,64}`. Unique key for the pack |
| `pack.title` | string | yes | Up to 80 characters |
| `pack.moduleCode` | string | yes | Groups packs and notes in the library (for example `EE5105`) |
| `pack.moduleName` | string | no | Display name for the module |
| `pack.version` | string | yes | Semver. Re-importing replaces the pack only if this version is higher; otherwise the app asks first |
| `pack.created` | string | yes | ISO date |
| `pack.description` | string | no | Up to 300 characters |
| `pack.notes` | string[] | no | File names of companion PDFs, used to attach them automatically on import |
| `topics[].id` | string | yes | Slug, unique within the pack |
| `topics[].name` | string | yes | Up to 40 characters, shown on chips |
| `topics[].description` | string | no | |
| `questions[].id` | string | yes | Unique within the pack and **stable across versions**, because history is keyed on it |
| `questions[].topic` | string | yes | Must match a `topics[].id` |
| `questions[].type` | string | yes | `"single"` only in v1 |
| `questions[].difficulty` | int | no | 1 = recall, 2 = apply/explain, 3 = derive/what-if. Default 2 |
| `questions[].prompt` | string | yes | Up to 400 characters. Plain text; `\n` allowed |
| `questions[].correct` | string | yes | Up to 160 characters |
| `questions[].distractors` | string[3] | yes | Exactly 3, all different from each other and from `correct` |
| `questions[].explanation` | string | yes | Up to 1200 characters. Shown after answering |
| `questions[].modelAnswer` | string | no | The full spoken-style answer, shown above the explanation |
| `questions[].sayIt` | string | no | One or two sentences to memorise, shown in a highlighted box |
| `questions[].variantGroup` | string \| null | no | Questions sharing a value are variants of each other: one is chosen per session (see 3.4) |
| `questions[].source` | string | no | Where the fact comes from (lecture, slide, page) |
| `questions[].tags` | string[] | no | Free tags. `demo-asked` gets a filter chip |

Text is plain UTF-8, optionally with LaTeX formulas (3.6). The app does not render markdown or HTML, so a stray `<b>` shows up as literal text.

### 3.3 Notes PDFs

- Any PDF is accepted. The skill produces A4 portrait PDFs with a contents page and bookmarks.
- Naming convention: `<MODULECODE>-<topic-slug>-overview.pdf`, for example `EE5105-ecg-amp-overview.pdf`.
- How a PDF is linked to a module on import:
  1. If the file name matches a name in an imported pack's `pack.notes`, it attaches to that pack's module.
  2. Otherwise, if the file name starts with a known module code, it attaches to that module.
  3. Otherwise the app asks: pick an existing module or create a new one.
- PDFs are copied into the app's private storage, so they still work if the original is deleted.

### 3.4 Calculation variants (variant groups)

The web quiz generated fresh numbers with JavaScript. A JSON pack cannot safely carry code, so v1 does this instead:

- The pack generator (the skill) pre-computes **4–8 instances** of each calculation question with different values. Each instance is a complete question with its own `id` and its own worked explanation, and all instances share a `variantGroup`, for example `"calc-r3-gain"`.
- When building a session, the app treats each variant group as **one slot** and picks one instance at random. So each exam has one "what if R3 were X?" question, with X changing between runs.
- History is tracked per instance ID, and the weak-spot ranking rolls it up by group.

Possible later (v2): a `template` question type with numeric ranges and expressions evaluated by a small sandboxed maths parser on the phone. It is out of scope here.

### 3.6 Formulas (added in 1.1.0)

- `$…$` (or `\(…\)`) is an inline formula; `$$…$$` (or `\[…\]`) is a display formula on its own line; `\$` is a literal dollar sign.
- An inline `$` opens only before a non-space and closes only after a non-space and not before a digit, so ordinary prices stay text.
- Formulas are allowed in `prompt`, `correct`, `distractors`, `explanation`, `modelAnswer` and `sayIt`. Length limits count the source, but the "longest answer" check measures the formula as shown.
- Rendering is native and offline (JLatexMath). A formula that fails to parse shows as its source in monospace; screen readers get a plain-text reading (`\frac{a}{b}` → "(a)/(b)").
- The validator warns about unbalanced braces in a formula and unclosed `$$`, `\(` or `\[`.

### 3.5 Optional bundle (v1.1)

A `.studypack.zip` containing `pack.json` plus the PDFs it lists, imported in one go. The file formats inside are the same as above.

---

## 4. Features in detail

### 4.1 Library (home screen)

- A list of modules (from `moduleCode`), each showing the number of packs, notes and questions, and the last score.
- Tapping a module opens the module screen.
- Floating **Import** button: opens the system picker filtered to `application/json` and `application/pdf`, with multiple selection allowed.
- Empty state: one line explaining how to import, plus a **Load sample pack** button that loads a bundled demo pack so the app can be tried straight away.

### 4.2 Module screen

- **Notes** section: PDF cards (title from the file name, page count, last page read).
- **Question packs** section: one card per pack (title, version, number of questions, best and last score).
- **Quiz** button: starts the setup screen for all packs in the module, or for a single pack from its card menu.

### 4.3 Quiz setup

Mirrors the web quiz:

- Length: Warm-up 12 / Bench demo 25 / Full mock 45 / Everything / My weak spots (disabled with a reason when nothing has been missed yet).
- Topic chips, multi-select, each showing its question count, with a **Select all** link.
- `demo-asked` chip, shown only when some questions carry that tag.
- Exam conditions toggle.
- A summary line, for example "25 questions from 10 topics", and **Start**.

### 4.4 Session building (quiz engine)

Pure Kotlin with no Android dependencies, so it can be unit tested.

1. Build the pool from the selected packs and topics. Collapse each variant group to one slot by choosing one instance at random.
2. Choose questions:
   - **Fixed length:** stratified round-robin. Group by topic, shuffle each group, shuffle the topic order, take one per topic in turn until N, then shuffle the final list.
   - **Everything:** shuffle the whole pool.
   - **Weak spots:** rank by `wrong / seen` (ties broken by `wrong`), take the top 25, then shuffle.
3. For each question, shuffle `[correct, d1, d2, d3]` (Fisher–Yates) and remember where the correct answer landed.
4. Use a seeded `Random` per session, with the seed stored, so a session can be reproduced when debugging.

### 4.5 Quiz screen

- Top: question N / M, running score (tutor mode) or number answered (exam mode), and a segmented progress bar that colours each segment as it is answered.
- A topic chip, plus a "calculation" chip when `variantGroup` is set.
- The prompt, then four option cards labelled A–D, each at least 56 dp tall.
- **Tutor mode:** tapping an option locks the answers, shows green/red, and reveals the model answer, the explanation and the say-it box. **Next** moves on.
- **Exam mode:** tapping selects (you can change it until **Next**). No feedback.
- **End early**, with a confirmation dialog. History is recorded only for questions actually answered.
- Back button → the "End quiz?" dialog.
- Screen rotation and process death keep the session (state held in a ViewModel and SavedStateHandle).

### 4.6 Results screen

- Percentage and a verdict: ≥85 % "Demo-ready", ≥65 % "Nearly there", otherwise "Revise and retry".
- Score per topic as bars, worst topics first.
- Missed questions: your answer, the correct answer, the explanation and the say-it line. Correct answers sit in a collapsed section.
- **Retry missed** (tutor mode, only the missed IDs) and **New quiz**.
- A session summary is saved for "last score" on the library and module screens.

### 4.7 PDF viewer

- Built on `android.graphics.pdf.PdfRenderer`, which is part of the platform, so no extra library is needed.
- Vertical page list (LazyColumn of rendered bitmaps), pinch zoom, a "page N / M" indicator and a jump-to-page dialog.
- Remembers the last page per PDF.
- Bookmarks / contents navigation is v1.1 (PdfRenderer does not expose the outline; it needs a small parser or a library).
- Menu item **Open in another app**: hands the file to an external viewer with `ACTION_VIEW` through a FileProvider URI.

### 4.8 Import and validation

- Entry points: the in-app picker (SAF `OpenMultipleDocuments`), an `ACTION_VIEW` intent filter (Open with) and an `ACTION_SEND` / `SEND_MULTIPLE` intent filter (Share to).
- JSON import steps:
  1. Read the stream (maximum 5 MB).
  2. Parse strictly. Unknown fields are ignored so newer generators still import, but missing required fields fail.
  3. Run the semantic checks in the table below.
  4. If the pack ID already exists, compare versions: higher → replace; same or lower → ask "Replace anyway?".
  5. Save in one Room transaction.
  6. Show a summary such as "Imported 128 questions in 10 topics (2 warnings)".
- PDF import: check it opens with PdfRenderer, copy it into internal storage, link it to a module (3.3).
- Errors and warnings say what went wrong and how to fix it, and name the question ID.

| Check | Severity |
|---|---|
| `format` / `schemaVersion` wrong | Error, import refused |
| A question's `topic` is not in `topics` | Error |
| Duplicate question ID | Error |
| Distractors not exactly 3, or duplicated, or equal to `correct` | Error |
| Empty prompt, correct answer or explanation | Error |
| Text over its length limit | Warning (imported, truncated on screen with "more") |
| Variant group with only 1 member | Warning |
| `pack.notes` names a PDF that has not been imported yet | Info ("Import EE5105-ecg-amp-overview.pdf to attach the notes") |

---

## 5. Technical design

### 5.1 Stack

| Concern | Choice | Why |
|---|---|---|
| Language | Kotlin 2.x | Standard for Android |
| UI | Jetpack Compose, Material 3 | Least boilerplate, light and dark themes for free |
| Architecture | Single activity, Navigation Compose, MVVM with a ViewModel per screen | Simple and testable |
| Storage | Room (SQLite) | Packs, questions and history; easy queries for weak spots |
| JSON | kotlinx.serialization | Strict, fast, no reflection |
| PDF | `android.graphics.pdf.PdfRenderer` | Built in |
| Files | Storage Access Framework, app-internal `filesDir/notes/` | No storage permissions needed |
| DI | Manual (a small `AppContainer`) | Hilt is overkill at this size |
| Min / target SDK | minSdk 26 (Android 8.0), targetSdk = latest stable | Covers almost all phones in use |
| Build | Gradle with version catalogs, Android Studio | |

**No runtime permissions are requested.** SAF and share intents grant access per file.

### 5.2 Data model (Room)

```
Module(code PK, name)
Pack(id PK, moduleCode FK, title, version, created, description, importedAt)
Topic(packId FK, id, name, description)            PK(packId, id)
Question(packId FK, id, topicId, type, difficulty, prompt, correct,
         distractorsJson, explanation, modelAnswer, sayIt,
         variantGroup, source, tagsJson)           PK(packId, id)
NoteFile(id PK autoinc, moduleCode FK, fileName, displayTitle,
         internalPath, pageCount, lastPage, importedAt)
QuestionStat(packId, questionId, seen, wrong, lastSeenAt, lastCorrect)  PK(packId, questionId)
SessionResult(id PK, startedAt, endedAt, mode, examMode, packIdsJson,
              total, answered, correct, perTopicJson, seed)
```

- `QuestionStat` is deliberately **not** linked by foreign key to `Question`, so history survives re-importing a pack. After an import, stats whose question ID no longer exists are left alone (harmless) and are removed when the pack is deleted.
- Weak-spot rule, same as the web quiz: a wrong answer adds 1 to `wrong`; in tutor mode a right answer takes 1 off `wrong` (never below 0); `seen` always goes up by 1.

### 5.3 Package layout

```
app/
  data/       db/ (entities, DAOs, AppDatabase), json/ (DTOs, Validator), repo/
  domain/     QuizEngine.kt, SessionBuilder.kt, Scoring.kt, models
  import/     ImportCoordinator.kt, PdfImporter.kt, PackImporter.kt
  ui/         library/, module/, setup/, quiz/, results/, pdf/, import/, theme/
  MainActivity.kt   (handles VIEW and SEND intents → import)
assets/sample/      demo.studypack.json, demo-overview.pdf
```

### 5.4 Manifest intent filters

```xml
<intent-filter>
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:scheme="content" android:mimeType="application/json" />
  <data android:scheme="content" android:mimeType="application/pdf" />
</intent-filter>
<intent-filter>
  <action android:name="android.intent.action.SEND" />
  <action android:name="android.intent.action.SEND_MULTIPLE" />
  <category android:name="android.intent.category.DEFAULT" />
  <data android:mimeType="application/json" />
  <data android:mimeType="application/pdf" />
  <data android:mimeType="application/octet-stream" />
</intent-filter>
```

Some cloud apps send `.json` as `application/octet-stream` or `text/plain`. For those, the importer sniffs the first bytes: `{` means JSON, `%PDF` means PDF.

---

## 6. UX and design notes

- One column throughout, thumb-reachable **Next** button at the bottom, option cards at least 56 dp tall.
- Follows the system light/dark setting. Semantic colours: green right, red wrong, amber for say-it boxes, separate from the accent colour.
- Monospace for numbers in progress and score labels, so they don't jitter.
- Accessibility: every option is one focusable element with a content description such as "Option B: …"; feedback is announced with a live region; dynamic text sizes up to 200 % without clipping.
- Haptic tick on answering (off when the system has haptics off).

---

## 7. Testing

| Layer | What | How |
|---|---|---|
| Validator | Every rule in 4.8, with good and bad fixture packs | JUnit |
| Quiz engine | Stratified pick covers every topic when N ≥ number of topics; variant groups give one slot; weak-spot ordering; correct index tracked after shuffle; seeded reproducibility | JUnit, property-style loops (1,000 seeds) |
| Room | Re-import keeps stats; delete pack removes its stats | Instrumented tests, in-memory DB |
| Import | Picker, Open with from Files, Share from Drive/OneDrive, octet-stream sniffing | Manual checklist on a real phone |
| UI | Quiz flow happy path, rotation mid-question, back press dialog | Compose UI tests |
| PDF | 1-page and 80-page PDFs; a corrupt PDF gives a clear error | Manual |

Fixture: the full ECG amp bench-demo pack (128 questions, 10 topics) converted to `.studypack.json` is the main real-world test file.

---

## 8. Milestones

| # | Milestone | Contents | Done when |
|---|---|---|---|
| M0 | Project skeleton | Gradle, Compose, navigation, theme, Room database, sample pack in assets | App opens to a library showing the sample module |
| M1 | Pack import | DTOs, validator, SAF picker, import summary, replace-by-version | Sample and ECG packs import; each bad fixture gives the right error |
| M2 | Quiz engine | Session builder, scoring, stats updates, fully unit tested | All engine tests pass |
| M3 | Quiz UI | Setup, quiz (tutor and exam), results, retry missed | A 25-question run works end to end on a phone, stats persist after restart |
| M4 | Notes | PDF import, linking rules, viewer, last page | Overview PDF opens, zooms and reopens at the last page |
| M5 | Open with / Share | Intent filters, MIME sniffing | A file shared from Drive and OneDrive imports |
| M6 | Polish and release | Empty states, error copy, accessibility pass, signed release APK | Checklist in section 9 passes; APK installed on own phone |

Rough effort for one person learning Compose as they go: M0–M3 about 2–3 weekends, M4–M6 about 1–2 weekends.

---

## 9. Acceptance checklist (v1)

- [ ] Importing a valid `.studypack.json` from Drive shows it under the right module within 2 seconds for 500 questions.
- [ ] An invalid pack is refused with an error that names the field and question ID.
- [ ] A PDF listed in `pack.notes` attaches to its module automatically.
- [ ] A 25-question exam covers every topic of a 10-topic pack.
- [ ] Two sessions in a row have a different question order and answer order.
- [ ] Each variant group shows at most one instance per session.
- [ ] Tutor mode shows the model answer, explanation and say-it box after each answer.
- [ ] Exam mode shows nothing until results, and choices can be changed before Next.
- [ ] Weak spots lists only missed questions, worst first, and the option is disabled when there are none.
- [ ] Re-importing a newer pack version keeps history for unchanged question IDs.
- [ ] Rotating the phone mid-quiz loses nothing.
- [ ] Works fully in airplane mode.
- [ ] No storage permission prompt is ever shown.

---

## 10. Risks and decisions

| Risk | Mitigation |
|---|---|
| Cloud apps send odd MIME types | Byte sniffing (5.4); the in-app picker accepts `*/*` as a fallback |
| Question IDs change between pack versions and history is lost | The skill keeps IDs stable; the validator warns when a re-import keeps less than 50 % of the old IDs |
| Correct answers longer than distractors make the quiz guessable (seen in the web quiz) | The skill enforces length balance; the app shuffles options. A v1.1 import warning flags packs where the correct answer is the longest option more than 40 % of the time |
| Large PDFs use too much memory | Render pages on demand at screen width, cache only nearby pages |
| Maths notation | LaTeX formulas rendered natively with JLatexMath since 1.1.0 (3.6); Unicode still works |

---

## 11. Future (v2+) ideas

- `template` questions with on-device numeric generation.
- Multi-select and numeric-entry question types.
- Spaced repetition scheduling (SM-2) using `QuestionStat`.
- PDF contents navigation and search.
- Export history as CSV.
- Home-screen widget: "5 weak-spot questions".
