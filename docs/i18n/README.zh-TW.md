<div align="center">

<img src="../assets/logo.svg" width="96" alt="Monologue 標誌">

# Monologue

**暖白黑膠風格的 Android 音樂播放器。**<br>
播放手機與 Google Drive 裡你自己的音樂，附同步歌詞、羅馬拼音與翻譯。

[![Release](https://img.shields.io/github/v/release/HKmario852/monologue?style=flat-square&color=A74932)](https://github.com/HKmario852/monologue/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HKmario852/monologue/total?style=flat-square&color=2F5D50)](https://github.com/HKmario852/monologue/releases)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/HKmario852/monologue?style=flat-square&color=55606B)](../../LICENSE)

[English](../../README.md) | **繁體中文** | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md)

<img src="../screenshots/player.png" width="200" alt="正在播放">&nbsp;
<img src="../screenshots/lyrics.png" width="200" alt="歌詞與翻譯">&nbsp;
<img src="../screenshots/library.png" width="200" alt="媒體庫">

</div>

## ✨ 功能

- **🎵 媒體庫**：手機裡的單曲、歌手、專輯與資料夾，還有歌單、最愛與播放隊列。
- **☁️ Google Drive**：瀏覽 Drive 上的音樂，直接串流或下載離線聆聽。
- **📝 歌詞**：跟著播放捲動的同步歌詞；日文歌詞可在手機上產生羅馬拼音；自動尋找網友人手中文翻譯，找不到才用手機上的機器翻譯；全螢幕歌詞。
- **🔎 搜尋與探索**：一個搜尋框同時搜尋媒體庫、雲端與線上音樂，另有 YouTube Music 分類。
- **📊 聆聽回顧**：按週、月或全部時間的熱門歌曲，可選擇同步到 ListenBrainz。
- **🎚️ 播放**：等化器、睡眠計時器、歌曲之間的靜音，來電或其他 App 播放影片後自動繼續。
- **🎨 設計**：暖白與深色主題、轉動的黑膠，以及 3 秒開場動畫（可關閉）。
- **⬆️ App 內更新**：從 GitHub Releases 下載新版，安裝前核對 SHA-256 與簽章。

> [!NOTE]
> App 介面目前只有繁體中文。

## 📥 下載

1. 到 **[Releases](https://github.com/HKmario852/monologue/releases/latest)** 下載 `monologue-x.y.z.apk` 並安裝（需要 Android 8.0 或以上）。
2. 之後的新版可在 App 內安裝：**設定 › App 更新**。

<details>
<summary><b>更多截圖</b></summary>
<br>
<p align="center">
<img src="../screenshots/search.png" width="200" alt="搜尋">&nbsp;
<img src="../screenshots/settings.png" width="200" alt="設定">&nbsp;
<img src="../screenshots/dark.png" width="200" alt="深色主題">
</p>
<p align="center"><sub>所有截圖都使用示範歌曲與歌詞。</sub></p>
</details>

## 📝 歌詞來源

線上歌詞預設關閉，開啟後才會查詢。可在 **設定 › 歌詞** 選擇來源及排列優先次序。

| 來源 | 提供內容 | 預設 |
|---|---|---|
| [LRCLIB](https://lrclib.net) | 同步歌詞（公開歌詞庫） | 開 |
| [VocaDB](https://vocadb.net) | Vocaloid 與同人音樂；羅馬拼音、翻譯（公開 API） | 關 |
| [THBWiki](https://thwiki.cc) | 東方同人歌；同步歌詞與中文翻譯（CC BY-NC-SA） | 關 |
| 網易雲音樂 | 同步歌詞、中文翻譯、羅馬拼音（非官方） | 關 |
| 巴哈姆特、Kanogoma | 網友人手中文翻譯（非官方，讀取網頁） | 關 |
| J-Lyric、うたてん | 純文字歌詞、羅馬拼音（非官方，讀取網頁） | 關 |

羅馬拼音由 [Kuromoji](https://github.com/atilika/kuromoji) 在手機上產生；機器翻譯由 [ML Kit](https://developers.google.com/ml-kit/language/translation) 在手機上進行。

## 🛠️ 從原始碼建置

需要 JDK 17 及 Android SDK（platform 35）。

```bash
./gradlew :app:assembleDebug        # 除錯版
./gradlew :app:assembleRelease      # Releases 發佈的版本
./gradlew :app:testDebugUnitTest    # 單元測試
```

Windows 請用 `gradlew.bat`。APK 位於 `app/build/outputs/apk/`。

**Google Drive** 需要你自己的 Google Cloud 專案（啟用 Drive API，並為 `io.hkmario.monologue` 建立 Android OAuth 用戶端，登記你簽署金鑰的 SHA-1）。發佈版的 Google 登入仍在測試階段，只有已加入為測試使用者的帳戶才能連接。完整設定與架構說明見 [docs/DEVELOPMENT.md](../DEVELOPMENT.md)。

## 🧱 技術

[Kotlin](https://kotlinlang.org) · [Jetpack Compose](https://developer.android.com/compose)（Material 3）· [Media3 / ExoPlayer](https://developer.android.com/media/media3) · [Room](https://developer.android.com/jetpack/androidx/releases/room) · [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [Kuromoji](https://github.com/atilika/kuromoji) · [ML Kit](https://developers.google.com/ml-kit)

## 🔒 私隱

沒有廣告，不需註冊。線上功能（Drive、歌詞、ListenBrainz）要你開啟後才會傳送資料。詳情見 [docs/PRIVACY.md](../PRIVACY.md)。

## ⚠️ 聲明

Monologue 是個人、非商業專案，與 Google、YouTube 或任何歌詞網站無關。線上音訊經 NewPipe Extractor 取自 YouTube，非官方歌詞來源讀取公開網頁；這些功能可能隨時失效，也可能不符合相關服務的條款。歌詞與翻譯屬於原作者及譯者。

## 📄 授權

[GPL-3.0-or-later](../../LICENSE)。第三方授權見 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) 與 [licenses/](../../licenses/)。

感謝 [LRCLIB](https://lrclib.net)、[VocaDB](https://vocadb.net)、[THBWiki](https://thwiki.cc)、[MusicBrainz](https://musicbrainz.org)、[ListenBrainz](https://listenbrainz.org)、[NewPipe](https://newpipe.net)，以及每一位作品出現在歌詞中的譯者。
