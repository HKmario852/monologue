<div align="center">

<img src="../assets/logo.svg" width="96" alt="Monologue ロゴ">

# Monologue

**あたたかみのあるレコード風 Android 音楽プレーヤー。**<br>
スマホと Google Drive にある自分の音楽を、同期歌詞・ローマ字・翻訳つきで。

[![Release](https://img.shields.io/github/v/release/HKmario852/monologue?style=flat-square&color=A74932)](https://github.com/HKmario852/monologue/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HKmario852/monologue/total?style=flat-square&color=2F5D50)](https://github.com/HKmario852/monologue/releases)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/HKmario852/monologue?style=flat-square&color=55606B)](../../LICENSE)

[English](../../README.md) | [繁體中文](README.zh-TW.md) | [简体中文](README.zh-CN.md) | **日本語** | [한국어](README.ko.md) | [Español](README.es.md)

<img src="../screenshots/player.png" width="200" alt="再生中">&nbsp;
<img src="../screenshots/lyrics.png" width="200" alt="歌詞と翻訳">&nbsp;
<img src="../screenshots/library.png" width="200" alt="ライブラリ">

</div>

## ✨ 機能

- **🎵 ライブラリ**：スマホ内の曲・アーティスト・アルバム・フォルダ、プレイリスト、お気に入り、再生キュー。
- **☁️ Google Drive**：Drive 上の音楽を閲覧し、ストリーミング再生やオフライン用のダウンロードができます。
- **📝 歌詞**：再生に合わせてスクロールする同期歌詞。日本語の歌詞はスマホ上でローマ字を生成。有志による中国語訳を自動で探し、見つからない場合はスマホ上の機械翻訳を使用。全画面歌詞にも対応。
- **🔎 検索と発見**：ライブラリ・Drive・オンライン音楽をひとつの検索欄で。YouTube Music のカテゴリも。
- **📊 リスニング記録**：週・月・全期間のよく聴いた曲。ListenBrainz への送信も選べます。
- **🎚️ 再生**：イコライザー、スリープタイマー、曲間の無音、通話や他アプリの動画のあとに自動で再開。
- **🎨 デザイン**：ペーパー調とダークのテーマ、回るレコード、3 秒のオープニングアニメーション（オフにできます）。
- **⬆️ アプリ内アップデート**：GitHub Releases から新バージョンを取得し、SHA-256 と署名を確認してからインストール。

> [!NOTE]
> 現在、アプリの画面は繁体字中国語のみです。

## 📥 ダウンロード

1. **[Releases](https://github.com/HKmario852/monologue/releases/latest)** から `monologue-x.y.z.apk` をダウンロードしてインストールします（Android 8.0 以上）。
2. 以降のバージョンはアプリ内からインストールできます：**設定 › App 更新**。

<details>
<summary><b>その他のスクリーンショット</b></summary>
<br>
<p align="center">
<img src="../screenshots/search.png" width="200" alt="検索">&nbsp;
<img src="../screenshots/settings.png" width="200" alt="設定">&nbsp;
<img src="../screenshots/dark.png" width="200" alt="ダークテーマ">
</p>
<p align="center"><sub>スクリーンショットはすべてデモ用の曲と歌詞です。</sub></p>
</details>

## 📝 歌詞ソース

オンライン歌詞は、オンにするまで使われません。**設定 › 歌詞** でソースと優先順位を選べます。

| ソース | 内容 | 初期設定 |
|---|---|---|
| [LRCLIB](https://lrclib.net) | 同期歌詞（公開歌詞データベース） | オン |
| [VocaDB](https://vocadb.net) | ボカロ・同人曲；ローマ字、翻訳（公開 API） | オフ |
| [THBWiki](https://thwiki.cc) | 東方アレンジ曲；同期歌詞と中国語訳（CC BY-NC-SA） | オフ |
| NetEase Cloud Music | 同期歌詞、中国語訳、ローマ字（非公式） | オフ |
| 巴哈姆特 (Bahamut)、Kanogoma | 有志による中国語訳（非公式、Web ページを読み込み） | オフ |
| J-Lyric、うたてん | 歌詞テキスト、ローマ字（非公式、Web ページを読み込み） | オフ |

ローマ字は [Kuromoji](https://github.com/atilika/kuromoji) で、機械翻訳は [ML Kit](https://developers.google.com/ml-kit/language/translation) で、どちらもスマホ上で処理します。

## 🛠️ ソースからビルド

JDK 17 と Android SDK（platform 35）が必要です。

```bash
./gradlew :app:assembleDebug        # デバッグ版
./gradlew :app:assembleRelease      # Releases で配布している版
./gradlew :app:testDebugUnitTest    # ユニットテスト
```

Windows では `gradlew.bat` を使います。APK は `app/build/outputs/apk/` に出力されます。

**Google Drive** には自分の Google Cloud プロジェクトが必要です（Drive API を有効にし、`io.hkmario.monologue` 用の Android OAuth クライアントに署名鍵の SHA-1 を登録）。配布版の Google ログインはまだテスト段階のため、テストユーザーに追加されたアカウントのみ接続できます。詳しい設定と設計は [docs/DEVELOPMENT.md](../DEVELOPMENT.md)（中国語）を参照してください。

## 🧱 使用技術

[Kotlin](https://kotlinlang.org) · [Jetpack Compose](https://developer.android.com/compose)（Material 3）· [Media3 / ExoPlayer](https://developer.android.com/media/media3) · [Room](https://developer.android.com/jetpack/androidx/releases/room) · [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [Kuromoji](https://github.com/atilika/kuromoji) · [ML Kit](https://developers.google.com/ml-kit)

## 🔒 プライバシー

広告なし、登録不要。オンライン機能（Drive、歌詞、ListenBrainz）は、オンにしたときだけデータを送信します。詳細は [docs/PRIVACY.md](../PRIVACY.md)（中国語）。

## ⚠️ 免責事項

Monologue は個人の非営利プロジェクトで、Google、YouTube、各歌詞サイトとは関係ありません。オンライン音声は NewPipe Extractor を通じて YouTube から取得し、非公式の歌詞ソースは公開 Web ページを読み込みます。これらはいつでも使えなくなる可能性があり、各サービスの利用規約に沿わない場合があります。歌詞と翻訳の権利は作者と翻訳者に帰属します。

## 📄 ライセンス

[GPL-3.0-or-later](../../LICENSE)。サードパーティのライセンス：[THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md)、[licenses/](../../licenses/)。

[LRCLIB](https://lrclib.net)、[VocaDB](https://vocadb.net)、[THBWiki](https://thwiki.cc)、[MusicBrainz](https://musicbrainz.org)、[ListenBrainz](https://listenbrainz.org)、[NewPipe](https://newpipe.net)、そして歌詞に訳が表示されるすべての翻訳者に感謝します。
