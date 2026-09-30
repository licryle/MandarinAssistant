# Maestro E2E suite

Black-box E2E for Mandarin Assistant (Android `fr.berliat.hskwidget`).
Covers every `docs/CUJs.md` row marked `Maestro`, except the OCR-seeded rows
(which need the `ocr-test` hook, see below).

## Layout

```
e2e/
  README.md
  setup/00_setup1.yaml        seed: clearState + annotate 你好 + E2E-list(你好)
  dictionary/01..07           search, annotated filter, locale(TODO), add-to-list,
                              paginate, text size, no-result
  annotations/08..12          create, edit, delete, custom(fragile), exam(TODO)
  wordlists/13..18            create, view, remove, rename, delete, duplicate
  widgets/19..20              SMOKE-ONLY (no widget placeable by Maestro)
  config/21..22               app language round-trip, locale persist
  misc/23                     about + support
```

Conventions: every flow is hermetic — it starts with
`runFlow: setup/00_setup1.yaml` (fresh `clearState` + seed: annotated 你好,
 `E2E-list` with 你好), so flows run in any order and leave no
useful state behind. Each wipe re-provisions the full dictionary asset
(see "Test database"), so the suite runs against production data at the cost
of a slower first launch per flow.

## Test database

Debug and release APKs ship the same full production database (~177MB):
there is no `src/debug`/`src/release` asset override. The asset is copied
into the library module by the `copyGeneratedDatabase` task from
`database_generation/output/Mandarin_Assistant.db`.

- First launch (and every `clearState: true` wipe) provisions the full asset,
  so allow a long timeout on the first `assertVisible` in
  `setup/00_setup1.yaml`.
- iOS: `iosApp/hskwidget/databases/` is a folder reference populated with the
  same full DB (no Xcode change needed).

## Run

1. Install Maestro CLI: https://maestro.mobile.dev (`maestro --version`).
   Java 24 + `adb` already present on this machine.
2. Start an emulator (Pixel, API 30+, camera emulated is fine — no OCR tests
   here) or plug a device; check `adb devices`.
3. Build + install debug APK:
   `.\gradlew.bat :androidApp:assembleDebug` then
   `adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk`
4. Run — flows are hermetic (each seeds itself), no prep needed.
   `maestro test` does not recurse, so list the folders:
   `maestro test e2e/setup e2e/dictionary e2e/annotations e2e/wordlists e2e/widgets e2e/config e2e/misc`
   Single flow: `maestro test e2e/dictionary/01_dict_search.yaml`.
   (Seed-only run, if ever needed: `maestro test e2e/setup/00_setup1.yaml`.)
   Same via Gradle (builds + installs first):
   `./gradlew maestroTest [-Pmaestro.flow=<file-or-dir>]`
6. Iterate flaky selectors with Maestro Studio: `maestro studio`.

## Selector rules (learned the hard way)

- `HSKTextView` exposes hanzi/pinyin as one node PER CHARACTER (`你`|`好`,
  `nǐ`|`hǎo`). Assert/tap single chars (`你`, `谢`, `一`), never whole words.
- Everywhere else assert the FULL node string (Maestro matches per-node):
  `hello; hi`, `Error: This list name already exist, use another one.`,
  ` only` (leading space!), `No result!`.
- Always `tapOn` a text field (hint or its current query text) before
  `eraseText`/`inputText`: drawer use clears field focus, and blind input goes
  nowhere. `hideKeyboard` only after a tapped field — with no keyboard open it
  acts as BACK and pops the nav stack.
- After `hideKeyboard` or dropdown-menu option taps, add
  `waitForAnimationToEnd` before the next tap: IME/menu animations shift layout
  and taps land stale.
- `DropdownSelector` needs TWO taps on its value when another field holds focus
  (tap 1 transfers focus, tap 2 expands); ONE tap from unfocused — including
  right after a menu option was selected (focus is dropped with the popup). In
  these flows notes are always typed first, so: double-tap type, single-tap
  level.

## Known gaps (fix in this order)

1. `03_dict_locale.yaml` + `12_annotate_exam.yaml` are TODO skeletons: the
   dictionary-language chip is icon-only and the Exam `Switch` has no
   content-desc, so Maestro cannot reach them. 1-line a11y fixes each
   (`contentDescription` on `LanguageFilterChip`'s chip and on the Exam
   container in `AnnotateScreen.kt`), then fill in the marked steps.
2. `11_annotate_custom.yaml` is fragile: needs a query with zero exact hits;
   confirm/replace the seed query in Studio.
3. `16_list_rename.yaml` taps a row icon by `index: 0` (assumes newest-first
   sort); confirm in Studio.
4. `19/20_widget_*.yaml` are smoke-only: Maestro cannot place a home-screen
   widget, so in-app config-with-preview needs a manually placed widget.
5. OCR-seeded CUJs need the debug hook first: handle
   `hskwidget://ocr-test?text=...` (Android intent-filter + iOS `onOpenURL`)
   as `Screen.OCRDisplay(preText)`, then write the 3 flows.
6. First-run system dialogs (notification permission) may need one manual
   grant during the first Studio pass.
