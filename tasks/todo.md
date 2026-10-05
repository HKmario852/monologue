# Startup intro: Monologue logo reveal

## Brief (from the user)
- **R** — the Monologue logo (`res/drawable/ic_monologue.xml`): cream square #F5EFE4, black record r=19/48 #171717,
  terracotta label r=7/48 #A74932, cream spindle hole r=2/48.
- **I** — a 3-second reveal (user changed it from 5 s).
- **S** — exact logo shapes and colours; moves with the brand's own energy (a record player: warm, analogue, unhurried);
  a 3-second jingle written in code, locked to the motion; starts muted.
- **E** — every frame drawn in code from one `render(t)`; real texture so it feels hand-made; check frames at
  0 / 25 / 50 / 75 / 100 % and fix anything that looks off.
- Shown when the app starts, then the app opens.

## Idea — "needle drop" (3.0 s)
| time | picture | sound (jingle, locked to the same keyframes) |
|---|---|---|
| 0.00–0.25 | cream paper, grain fades in | faint vinyl crackle |
| 0.25–0.75 | the black disc drops in, lands at 0.60 with a small bounce and a soft shadow | low thump at 0.60 |
| 0.75–1.35 | disc starts turning; grooves are drawn in one by one, slightly wobbly like ink | 3-note arpeggio at 0.85 / 1.05 / 1.25 |
| 1.35–1.70 | terracotta label stamps onto the centre (squash, then ink bleed) | warm chord at 1.45 |
| 1.70–1.95 | the spindle hole punches through; a highlight sweeps across | high tick at 1.75 |
| 1.95–2.55 | the disc lifts up; "Monologue" writes in underneath | soft held note, gone by 2.9 |
| 2.55–3.00 | hold, then fade into the app | silence |

Plays on every cold start; tap anywhere to skip; 設定 can turn it off. Sound starts muted (speaker button to unmute).

Texture: seeded paper grain, slightly uneven groove strokes, ink bleed at the label edge, a tiny print mis-registration
on the label — deterministic, so every run looks the same.

## Plan
- [x] `ui/Intro.kt`: `fun DrawScope.renderIntro(t: Float)` draws the whole frame for t in 0..1 (only function that draws).
- [x] `IntroScreen` composable: animates t over 3 s with `Canvas { renderIntro(t) }`; tap anywhere skips;
      a mute/unmute button in the corner (muted at start); calls `onDone` at the end.
- [x] `IntroJingle`: synthesises the 3-second jingle into a PCM buffer (sine partials with envelopes + crackle noise),
      plays it through `AudioTrack`, timed from the same clock as the picture; nothing plays until unmuted.
- [x] `MainActivity` / `AppHost`: show the intro on a cold start before the app; the app loads behind it.
- [x] Setting 設定 › 外觀與導航 ›「開啟時播放動畫」(on by default) to turn it off.
- [x] Device test that renders frames at 0/25/50/75/100 % to PNG; look at each and fix what looks off.
- [x] Unit test: jingle is 3.0 s long, silent after 2.9 s, peaks at 0.8, and each sound starts on its keyframe.
- [x] Build, lint, unit + device tests; commit on a branch.
- [x] Published as v0.4.9.

## Review
- `domain/IntroJingle.kt`: `IntroTimeline` keyframes shared by picture and sound; `synthesizeIntroJingle()` makes the
  3 s jingle (crackle, thump, D-major arpeggio, stamp chord, punch tick, held D5) from code.
- `ui/Intro.kt`: `renderIntro(t)` is the only drawing function; `IntroTexture` holds the seeded grain, fibres,
  wobble and ink bleed; `IntroScreen` plays it over the app (tap / Back skips, speaker button unmutes);
  `IntroSound` plays the jingle through a static `AudioTrack` from the picture's current time.
- Shown once per cold start; off with 設定 ›「開啟時播放動畫」, 減少動態效果, or system animations off.
  Android 12+ splash shows plain paper so the intro starts seamlessly.
- Frame check (IntroFramesTest, 1080×2340): first pass showed a faceted record edge and label and spotty ink bleed;
  fixed with smooth 240-point outlines, less wobble and a finer bleed. Re-checked 0/25/50/75/100 %, land, stamp, hold.
- Verified on MuMu: plays on cold start, fades into the library, tap skips, unmuting starts a 44.1 kHz static track
  in audio_flinger (emulator music volume is muted, so not audible there).
- 59 unit + 31 device tests pass, lint clean.

---

# Faster start (user saw ~1.5 s of blank screen before the intro)

## Findings (MuMu, cold start, ms from process start)
- 0 → ~530: process start plus ML Kit's and WorkManager's own start-up providers, before any app code.
- ~530 → ~650: app graph and ViewModel (~0.1 s).
- ~650 → ~1080: the whole app composed and laid out together with the intro; intro's first frame at ~1080.
- ~1080 → ~1360: data arriving, recompositions, skipped frames.
- The intro's first ~0.4 s was paper only (record started above the screen).

## Plan
- [x] Remove ML Kit's and WorkManager's start-up providers; start ML Kit on the first translation and WorkManager on
      first use (`Configuration.Provider`), with scheduling off the main thread.
- [x] Intro: the record is in view from the first frame; keyframes 0.2 s earlier.
- [x] Draw the intro first, build the app one frame later, and start the intro clock only after that.
- [x] Device tests: ML Kit starts on first use; WorkManager starts on first use.

## Review
- First frame (with the record already visible) now at 0.85–0.93 s on MuMu, from 1.0–1.5 s of paper before;
  no "Skipped frames" warnings during the intro.
- 59 unit + 33 device tests pass, lint clean.

---

# Three playback / recap features (requested 2026-10-02)

## Plan (user chose: add silence between songs)
- [x] **Silence between songs** — 設定 › 播放「歌曲之間的靜音」: 關閉 / 2 / 3 / 5 / 10 秒.
- [x] **Listening history** — 聆聽回顧 gets a third chip「按最近播放」: every library song, last played first, never-played
      songs last, each row showing when it was last played. Not limited to the selected period.
- [x] **Resume after other media** — when another app's music or video takes audio focus, pause; when no other app is
      playing any more (AudioManager playback callback), take focus back and resume. Only if the interruption was
      shorter than 30 minutes. Folded into the existing 設定 › 播放「中斷後恢復播放」 (on by default) instead of a second toggle.
- [x] Tests: unit tests for the history order and the gap timing; device check on MuMu (play a video in another app).
- [x] Build, lint, unit + device tests; commit on a branch.
- [x] Published as v0.4.11.

## Review
- Gap: ExoPlayer pauses at the end of each song (`pauseAtEndOfMediaItems`), the next one starts after the gap; the app
  shows "playing" during the gap; pause during the gap stops the next song; skip starts the chosen song at once.
- Resume: on `PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS`, poll `AudioManager.isMusicActive` each second; resume once
  the other app has played and then been quiet for 1.5 s; give up after 30 min or on any user play/pause.
- History: `listeningHistory()` (unit-tested) orders library + played songs by last listen; never-played last;
  labels 剛剛 / N 分鐘前 / N 小時前 / N 日前 / date / 從未播放.
- GapAndFocusDeviceTest (real audio): next song only after the gap; another player taking focus pauses Monologue and it
  resumes when that player stops. It cleans up its fixtures (first version left counted plays that broke
  PlaybackDeviceTest).
- 61 unit + 35 device tests pass (twice), lint clean.

---

# 加入時間 sort in 媒體庫 and 雲端 (requested 2026-10-03)

## Plan
- [x] `Track.addedMs` + database column (version 3, `MIGRATION_2_3`, default 0).
- [x] Drive: request `createdTime` (when the file was uploaded); local: MediaStore `DATE_ADDED`; chosen folders: last modified.
- [x] 媒體庫「依加入時間（由新至舊）」 and 雲端「加入時間，由新至舊」; songs without a date last, by title.
- [x] Unit tests (`sortLibrary`, `driveTimeMs`); migration checked on MuMu by installing over the version-2 database.

## Review
- MuMu: database upgraded to version 3 in place; after the Drive refresh all 329 songs had dates (2023-08 → today),
  and both lists showed the newest upload first, matching the database.
- 63 unit + 35 device tests pass, lint clean.
- 2026-10-03 follow-up: choosing 大小 (雲端) or 加入時間 (both) again flips the direction (`nextSort`, `baseSort`);
  labels show the direction in use (由新至舊 ↔ 由舊至新, 由大至小 ↔ 由小至大). Checked on MuMu against the database.

---

# 搜尋 page like Spotify's browse grid (requested 2026-10-03, screenshot)

## Plan (waiting for the user's choices)
- [ ] Before typing: the search box on top, then a 2-column grid of coloured tiles; each tile has a bold title top-left
      and one of the user's album covers tilted in the bottom-right corner, clipped by the tile.
- [ ] Tiles come from the user's own music (only ones with songs show): 最近加入, 最近播放, 最愛, 未聽過,
      日文歌, 中文歌, 英文歌 (by the script of title/artist), 已下載. Tapping opens the list with 播放全部 / 隨機播放.
- [ ] Typing works as today (媒體庫, Google Drive, 線上).
- [ ] Tile colours: to be chosen by the user.
- [x] UI test at phone size; build, lint, unit + device tests; published as v0.4.12.
- Decisions: warm palette; tiles are online categories (YouTube Music), not the user's own songs; all 16 categories;
  covers load when 搜尋 opens (only category words are sent; cached a day).

## Review
- 16 tiles (熱門新歌 … 放鬆) in a 2-column grid under a solid search box; each tile's cover is its top song's album art,
  tilted into the corner. Tapping opens a category page (coloured header, songs; tap to play, arrow to download).
- `OnlineRepository.browse()` uses YouTube Music's song search (falls back to ordinary YouTube search when empty),
  cached 24 h in `online-browse`; "J-Pop" returned no songs, so its query is "jpop hits"; empty results count as
  failures so the tile retries when opened.
- Checked on MuMu: all covers load, 日本樂曲 lists 30 songs, tapping 夜に駆ける plays it, switching tabs returns to the
  grid, typing still searches 媒體庫/Drive/線上. Phone-size UI test checks the two-column layout and tile tap.

---

# Fan translations not found (agony / KOTOKO, 君にふれて / 安月名莉子; reported 2026-10-05)

## Findings
- The search stops at the first good original (usually LRCLIB synced Japanese), so 網易雲 / 巴哈姆特 / VocaDB are never
  asked for their translation; songs already saved are never looked at again either.
- 巴哈姆特 agony post (sn=2160052): title has no artist (KOTOKO is only in the text and tags), and each line comes as
  Japanese / kana reading / romaji / Chinese, so the pairing failed; credits (作詞：…, 歌：…, 線上試聽：…) at the top.
- 巴哈姆特 君にふれて post (sn=5827854): the credit lines contain kana, so they counted as lyrics and broke the pairing.
- marumaru-x.com: not usable — lyrics load through a token-protected script (403 to other clients) and robots.txt
  disallows it.

## Plan
- [x] `splitBilingualLyrics`: skip the credit header (more labels: 歌, 翻譯, 線上試聽, 作詞．作曲 …) and credit lines;
      strip inline furigana "俯(うつむ)"; when an original run is 2× or 3× its Chinese run and the extra lines are
      readings (kana / romaji), keep only the lyric lines.
- [x] `borrowTranslation`: put a translation from another source onto the shown lyrics by matching line text
      (joined / split lines handled), keeping the shown lyrics' timestamps; only when ≥ 70 % of lines match.
- [x] `LyricsSources.findTranslation`: ask 網易雲 / 巴哈姆特 / VocaDB (enabled ones, user's order) for a translation.
- [x] ViewModel: when lyrics have no person-made translation (none or ML Kit), look once per session before ML Kit.
- [x] 巴哈姆特: a post whose title names the song but not the artist counts when its text or tags name the artist.
- [x] Unit tests with both posts' layouts; device check with agony and 君にふれて on MuMu.
- [x] Translation credit under the lyrics when the translation came from elsewhere (translator's name kept).

## Review
- Real posts: agony (sn=2160052) now gives 43 lyric lines with 43 translations (readings and romaji left out);
  君にふれて (sn=5827854 and sn=4315151) 27/27 and 16/16. Moved onto LRCLIB's synced lyrics: agony 42/43 lines,
  君にふれて 16/16 (12/16 from the post that writes 掛け/始め in kanji where LRCLIB uses kana).
- MuMu, only LRCLIB + 巴哈姆特 on: agony shows LRCLIB lyrics after ~1 s and the 巴哈姆特 translation (synced,
  saved to the database) after ~3.5 s. With MuMu's own order 網易雲 already had agony's translation.
- The search is marked done only after it runs, so a settings change mid-search doesn't skip the song.
- 72 unit + 38 device tests pass, no lint errors.

---

# Paused song resumed after a video (bug) + Kanogoma source (requested 2026-10-05)

## Review
- Bug: Media3 1.7.1 keeps the audio focus while paused and reports losing it as a pause (reason AUDIO_FOCUS_LOSS,
  playWhenReady false → false), so the "resume after other media" watcher started for a song the user had paused.
  Now it only starts when the song was playing (or in the silence between songs, which is cancelled) just before.
  New device test `staysPausedWhenItWasPausedBeforeAnotherAppPlayed` failed before the fix and passes after; the
  resume-when-playing test still passes.
- Kanogoma 歌の胡麻 (kanogoma.com, ~400 hand translations): new source, off by default, also used for the translation
  lookup. Songs found with the site's WordPress search API, lyrics read from the song page (`#kngm div[data-i]`:
  furigana in `<rt>` dropped, `p.zh` is the translation). robots.txt allows it; no protection to get past.
- Checked on MuMu: アイドル / YOASOBI found in 1.6 s (59 lines, all translated). Moved onto LRCLIB's synced lyrics
  offline: 57 of 77 lines get a translation (the rest are lines Kanogoma writes joined with the line before).
  agony and 君にふれて are not on Kanogoma.
- 73 unit + 39 device tests pass, no lint errors.

---

# More online translation sources (requested 2026-10-05: "95% of the time I won't use ML Kit")

## Looked at
- **THBWiki** (thwiki.cc, Touhou doujin songs): public MediaWiki API, 歌词 pages with timestamps, Japanese and a named
  translator's Chinese, CC BY-NC-SA 3.0 → added.
- 萌娘百科: API answers "Unauthorized API call" to anonymous use → not used (would mean getting past a block).
- QQ音樂: lyric API refuses without its own site's Referer (-1310) → not used.
- 酷狗: lyrics are encrypted (KRC) → not used.
- UtaTime (was Lyrical Nonsense): English translations only.
- Other LRCLIB uploads of the same song: none carried Chinese for agony / 君にふれて / アイドル.

## Review
- `ThbWikiLyrics`: search the 歌词 namespace for the title; a page counts when the circle in its title, or an album
  page linking to it (制作方 / 演唱 …), names one of the artists or the album; last timestamp within the file's length.
  Off by default (new sources are added off); also used by the translation lookup.
- MuMu: FELT "Time and again" and "OUR SHIP" found in 0.7 s / 0.3 s, synced, translation on 41/41 and 40/40 lines,
  translator credited, converted to 繁體中文.
- First device run failed: Android's regex engine rejects a bare "}" / "]" that the desktop JVM accepts, so the unit
  tests passed. Escaped, and `LyricsParsersDeviceTest` now runs every lyric parser on the device.
- 74 unit + 40 device tests pass, no lint errors.
