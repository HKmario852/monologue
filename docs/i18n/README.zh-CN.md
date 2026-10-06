<div align="center">

<img src="../assets/logo.svg" width="96" alt="Monologue 标志">

# Monologue

**暖白黑胶风格的 Android 音乐播放器。**<br>
播放手机和 Google Drive 里你自己的音乐，附同步歌词、罗马音和翻译。

[![Release](https://img.shields.io/github/v/release/HKmario852/monologue?style=flat-square&color=A74932)](https://github.com/HKmario852/monologue/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/HKmario852/monologue/total?style=flat-square&color=2F5D50)](https://github.com/HKmario852/monologue/releases)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
[![License](https://img.shields.io/github/license/HKmario852/monologue?style=flat-square&color=55606B)](../../LICENSE)

[English](../../README.md) | [繁體中文](README.zh-TW.md) | **简体中文** | [日本語](README.ja.md) | [한국어](README.ko.md) | [Español](README.es.md)

<img src="../screenshots/player.png" width="200" alt="正在播放">&nbsp;
<img src="../screenshots/lyrics.png" width="200" alt="歌词与翻译">&nbsp;
<img src="../screenshots/library.png" width="200" alt="媒体库">

</div>

## ✨ 功能

- **🎵 媒体库**：手机里的单曲、歌手、专辑和文件夹，还有歌单、收藏和播放队列。
- **☁️ Google Drive**：浏览 Drive 上的音乐，直接串流或下载离线收听。
- **📝 歌词**：随播放滚动的同步歌词；日文歌词可在手机上生成罗马音；自动查找网友人工中文翻译，找不到才用手机上的机器翻译；全屏歌词。
- **🔎 搜索与探索**：一个搜索框同时搜索媒体库、云端和在线音乐，另有 YouTube Music 分类。
- **📊 收听回顾**：按周、月或全部时间的热门歌曲，可选择同步到 ListenBrainz。
- **🎚️ 播放**：均衡器、睡眠定时器、歌曲之间的静音，来电或其他 App 播放视频后自动继续。
- **🎨 设计**：暖白与深色主题、转动的黑胶，以及 3 秒开场动画（可关闭）。
- **⬆️ App 内更新**：从 GitHub Releases 下载新版，安装前校验 SHA-256 和签名。

> [!NOTE]
> App 界面目前只有繁体中文。

## 📥 下载

1. 到 **[Releases](https://github.com/HKmario852/monologue/releases/latest)** 下载 `monologue-x.y.z.apk` 并安装（需要 Android 8.0 或以上）。
2. 之后的新版可在 App 内安装：**設定（设置）› App 更新**。

<details>
<summary><b>更多截图</b></summary>
<br>
<p align="center">
<img src="../screenshots/search.png" width="200" alt="搜索">&nbsp;
<img src="../screenshots/settings.png" width="200" alt="设置">&nbsp;
<img src="../screenshots/dark.png" width="200" alt="深色主题">
</p>
<p align="center"><sub>所有截图都使用示范歌曲和歌词。</sub></p>
</details>

## 📝 歌词来源

在线歌词默认关闭，开启后才会查询。可在 **設定 › 歌詞**（设置 › 歌词）选择来源并调整优先顺序。

| 来源 | 提供内容 | 默认 |
|---|---|---|
| [LRCLIB](https://lrclib.net) | 同步歌词（公开歌词库） | 开 |
| [VocaDB](https://vocadb.net) | Vocaloid 与同人音乐；罗马音、翻译（公开 API） | 关 |
| [THBWiki](https://thwiki.cc) | 东方同人歌；同步歌词和中文翻译（CC BY-NC-SA） | 关 |
| 网易云音乐 | 同步歌词、中文翻译、罗马音（非官方） | 关 |
| 巴哈姆特、Kanogoma | 网友人工中文翻译（非官方，读取网页） | 关 |
| J-Lyric、うたてん | 纯文本歌词、罗马音（非官方，读取网页） | 关 |

罗马音由 [Kuromoji](https://github.com/atilika/kuromoji) 在手机上生成；机器翻译由 [ML Kit](https://developers.google.com/ml-kit/language/translation) 在手机上进行。

## 🛠️ 从源码构建

需要 JDK 17 和 Android SDK（platform 35）。

```bash
./gradlew :app:assembleDebug        # 调试版
./gradlew :app:assembleRelease      # Releases 发布的版本
./gradlew :app:testDebugUnitTest    # 单元测试
```

Windows 请用 `gradlew.bat`。APK 位于 `app/build/outputs/apk/`。

**Google Drive** 需要你自己的 Google Cloud 项目（启用 Drive API，并为 `io.hkmario.monologue` 创建 Android OAuth 客户端，登记你签名密钥的 SHA-1）。发布版的 Google 登录仍在测试阶段，只有已加入为测试用户的账号才能连接。完整设置与架构说明见 [docs/DEVELOPMENT.md](../DEVELOPMENT.md)（繁体中文）。

## 🧱 技术

[Kotlin](https://kotlinlang.org) · [Jetpack Compose](https://developer.android.com/compose)（Material 3）· [Media3 / ExoPlayer](https://developer.android.com/media/media3) · [Room](https://developer.android.com/jetpack/androidx/releases/room) · [WorkManager](https://developer.android.com/jetpack/androidx/releases/work) · [OkHttp](https://square.github.io/okhttp/) · [Coil](https://coil-kt.github.io/coil/) · [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor) · [Kuromoji](https://github.com/atilika/kuromoji) · [ML Kit](https://developers.google.com/ml-kit)

## 🔒 隐私

没有广告，无需注册。在线功能（Drive、歌词、ListenBrainz）要你开启后才会发送数据。详情见 [docs/PRIVACY.md](../PRIVACY.md)（繁体中文）。

## ⚠️ 声明

Monologue 是个人、非商业项目，与 Google、YouTube 或任何歌词网站无关。在线音频经 NewPipe Extractor 取自 YouTube，非官方歌词来源读取公开网页；这些功能可能随时失效，也可能不符合相关服务的条款。歌词与翻译属于原作者及译者。

## 📄 许可证

[GPL-3.0-or-later](../../LICENSE)。第三方许可证见 [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) 和 [licenses/](../../licenses/)。

感谢 [LRCLIB](https://lrclib.net)、[VocaDB](https://vocadb.net)、[THBWiki](https://thwiki.cc)、[MusicBrainz](https://musicbrainz.org)、[ListenBrainz](https://listenbrainz.org)、[NewPipe](https://newpipe.net)，以及每一位作品出现在歌词中的译者。
