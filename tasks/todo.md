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
