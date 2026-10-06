<div align="center">

<img src="../assets/logo.svg" width="96" alt="Monologue 로고">

# Monologue

**따뜻한 LP 감성의 Android 음악 플레이어.**<br>
휴대폰과 Google Drive에 있는 내 음악을 싱크 가사, 로마자 표기, 번역과 함께.

[![Release](https://img.shields.io/github/v/release/HKmario852/monologue?style=flat-square&color=A74932)](https://github.com/HKmario852/monologue/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HKmario852/monologue/total?style=flat-square&color=2F5D50)](https://github.com/HKmario852/monologue/releases)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/HKmario852/monologue?style=flat-square&color=55606B)](../../LICENSE)

[English](../../README.md) | [繁體中文](README.zh-TW.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | **한국어** | [Español](README.es.md)

<img src="../screenshots/player.png" width="200" alt="재생 중">&nbsp;
<img src="../screenshots/lyrics.png" width="200" alt="가사와 번역">&nbsp;
<img src="../screenshots/library.png" width="200" alt="보관함">

</div>

## ✨ 기능

- **🎵 보관함** — 휴대폰의 곡, 아티스트, 앨범, 폴더와 플레이리스트, 즐겨찾기, 재생 대기열.
- **☁️ Google Drive** — Drive의 음악을 탐색하고 스트리밍하거나 오프라인용으로 다운로드.
- **📝 가사** — 재생에 맞춰 스크롤되는 싱크 가사, 일본어 가사의 로마자 표기를 기기에서 생성, 사람이 번역한 중국어 번역을 자동으로 찾고 없으면 기기 내 기계 번역 사용, 전체 화면 가사.
- **🔎 검색과 탐색** — 보관함, Drive, 온라인 음악을 한 번에 검색. YouTube Music 카테고리도 제공.
- **📊 감상 기록** — 주간, 월간, 전체 기간의 많이 들은 곡. ListenBrainz 스크로블 선택 가능.
- **🎚️ 재생** — 이퀄라이저, 수면 타이머, 곡 사이 무음, 통화나 다른 앱의 동영상이 끝나면 자동으로 이어 재생.
- **🎨 디자인** — 종이 느낌과 다크 테마, 회전하는 LP, 3초 인트로 애니메이션(끌 수 있음).
- **⬆️ 앱 내 업데이트** — GitHub Releases에서 새 버전을 받고 SHA-256과 서명을 확인한 뒤 설치.

> [!NOTE]
> 현재 앱 화면은 번체 중국어만 지원합니다.

## 📥 다운로드

1. **[Releases](https://github.com/HKmario852/monologue/releases/latest)** 에서 `monologue-x.y.z.apk` 를 받아 설치하세요 (Android 8.0 이상).
2. 이후 버전은 앱 안에서 설치할 수 있습니다: **設定 (설정) › App 更新 (앱 업데이트)**.

<details>
<summary><b>스크린샷 더 보기</b></summary>
<br>
<p align="center">
<img src="../screenshots/search.png" width="200" alt="검색">&nbsp;
<img src="../screenshots/settings.png" width="200" alt="설정">&nbsp;
<img src="../screenshots/dark.png" width="200" alt="다크 테마">
</p>
<p align="center"><sub>모든 스크린샷은 데모용 곡과 가사를 사용합니다.</sub></p>
</details>

## 📝 가사 출처

온라인 가사는 직접 켜기 전까지 사용되지 않습니다. **設定 › 歌詞** (설정 › 가사) 에서 출처와 우선순위를 고를 수 있습니다.

| 출처 | 제공 내용 | 기본값 |
|---|---|---|
| [LRCLIB](https://lrclib.net) | 싱크 가사 (공개 가사 라이브러리) | 켜짐 |
| [VocaDB](https://vocadb.net) | 보컬로이드·동인 음악, 로마자, 번역 (공개 API) | 꺼짐 |
| [THBWiki](https://thwiki.cc) | 동방 동인 음악, 싱크 가사와 중국어 번역 (CC BY-NC-SA) | 꺼짐 |
| NetEase Cloud Music | 싱크 가사, 중국어 번역, 로마자 (비공식) | 꺼짐 |
| 巴哈姆特 (Bahamut), Kanogoma | 팬이 번역한 중국어 번역 (비공식, 웹 페이지 읽기) | 꺼짐 |
| J-Lyric, うたてん | 텍스트 가사, 로마자 (비공식, 웹 페이지 읽기) | 꺼짐 |

로마자 표기는 [Kuromoji](https://github.com/atilika/kuromoji), 기계 번역은 [ML Kit](https://developers.google.com/ml-kit/language/translation) 으로 모두 휴대폰에서 처리합니다.

## 🛠️ 소스에서 빌드

JDK 17과 Android SDK (platform 35) 가 필요합니다.

```bash
./gradlew :app:assembleDebug        # 디버그 빌드
./gradlew :app:assembleRelease      # Releases에 배포되는 빌드
./gradlew :app:testDebugUnitTest    # 단위 테스트
```

Windows에서는 `gradlew.bat` 을 사용하세요. APK는 `app/build/outputs/apk/` 에 생성됩니다.

**Google Drive** 를 쓰려면 직접 만든 Google Cloud 프로젝트가 필요합니다 (Drive API 활성화, `io.hkmario.monologue` 용 Android OAuth 클라이언트에 서명 키의 SHA-1 등록). 배포판의 Google 로그인은 아직 테스트 단계라서 테스트 사용자로 추가된 계정만 연결할 수 있습니다. 자세한 설정과 구조는 [docs/DEVELOPMENT.md](../DEVELOPMENT.md) (중국어) 를 참고하세요.

## 🧱 사용 기술

[Kotlin](https://kotlinlang.org) · [Jetpack Compose](https://developer.android.com/compose) (Material 3) · [Media3 / ExoPlayer](https://developer.android.com/media/media3) · [Room](https://developer.android.com/jetpack/androidx/releases/room) · [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [Kuromoji](https://github.com/atilika/kuromoji) · [ML Kit](https://developers.google.com/ml-kit)

## 🔒 개인정보

광고 없음, 가입 불필요. 온라인 기능 (Drive, 가사, ListenBrainz) 은 켰을 때만 데이터를 보냅니다. 자세한 내용: [docs/PRIVACY.md](../PRIVACY.md) (중국어).

## ⚠️ 고지

Monologue는 개인 비상업 프로젝트이며 Google, YouTube 또는 어떤 가사 사이트와도 관련이 없습니다. 온라인 오디오는 NewPipe Extractor를 통해 YouTube에서 가져오고, 비공식 가사 출처는 공개 웹 페이지를 읽습니다. 이 기능들은 언제든 작동하지 않을 수 있으며 해당 서비스의 약관을 따르지 않을 수 있습니다. 가사와 번역의 권리는 원작자와 번역자에게 있습니다.

## 📄 라이선스

[GPL-3.0-or-later](../../LICENSE). 서드파티 라이선스: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md), [licenses/](../../licenses/).

[LRCLIB](https://lrclib.net), [VocaDB](https://vocadb.net), [THBWiki](https://thwiki.cc), [MusicBrainz](https://musicbrainz.org), [ListenBrainz](https://listenbrainz.org), [NewPipe](https://newpipe.net), 그리고 가사에 번역이 실린 모든 번역자에게 감사드립니다.
