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
