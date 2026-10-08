# Lessons

Rules learned from the user's corrections in this project.

- **Bottom tabs never remember inner pages.** Tapping a tab always opens that tab's first page.
- **A control that exists should always be visible.** Don't hide a chip or button when the current song lacks the
  data (e.g. 原文＋羅馬拼音): show it and explain what's missing instead.
- **Everything must work inside the app.** No external downloads, plugins or tools as part of a feature.
- **Check what a "translation" really is before removing it.** Bracketed English in Japanese lyrics can be sung
  backing vocals; offer a display option rather than deleting source data.
- **Lyrics views should use the available height.** Test portrait layouts at phone size (MuMu is landscape-only;
  use a UI test with a phone-sized box).
- **Only push or publish when asked** ("publish" / "發布"); commit on a branch otherwise.
- **The startup intro is 3 seconds**, not 5. Keep intros short; always skippable.
- **Run new regexes on the device, not only in unit tests.** Android's ICU regex engine rejects a bare `}` or `]`
  that the desktop JVM accepts; a top-level `Regex` then breaks the whole class with ExceptionInInitializerError.
  Escape brackets and keep `LyricsParsersDeviceTest` covering every lyric parser.
- **Never write to the database at UI-tick rate.** Room re-runs every observing query on each write; a 500 ms
  listening write grew to 43k rows and kept the phone's CPU above 100 %. Keep state in memory, write on change of
  state (pause, song change) and at most every ~15 s.
- **Publish release builds, not debug.** A debuggable APK skips ahead-of-time compilation and runs Compose with debug
  checks; measure performance on the user's phone with the build that will ship.
- **Never stream with one open-ended HTTP request.** The network fills socket buffers with the rest of the file while
  the player waits, so a skipped song still costs its full size. Use bounded range requests and measure data with
  `TrafficStats.getUidRxBytes` before and after.
- **Decide a text layer's language by the majority of its lines**, not "≥3 characters of a script anywhere": one credit
  line can flip the whole layer.
