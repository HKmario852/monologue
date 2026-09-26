# 第三方授權及素材

本專案依賴的 AndroidX、Material Components、Media3、Kotlin、Kotlinx Coroutines、Kotlinx Collections Immutable、OkHttp、Okio、Coil 使用 Apache License 2.0；個別依賴包附帶的 LICENSE／NOTICE 保持原有效力。Google Play services 屬 Google API／服務條款管理的分發元件。

- [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [AndroidX 原始碼及授權](https://android.googlesource.com/platform/frameworks/support/)
- [Media3](https://github.com/androidx/media)
- [Kotlin](https://github.com/JetBrains/kotlin)
- [Kotlinx Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Kotlinx Collections Immutable](https://github.com/Kotlin/kotlinx.collections.immutable)
- [OkHttp](https://github.com/square/okhttp)
- [Coil](https://github.com/coil-kt/coil)
- [Google APIs 服務條款](https://developers.google.com/terms)

字體使用 Android 系統 Serif／Sans Serif fallback，沒有附帶未授權字體。黑膠、唱臂、App 圖示由程式繪製；Material icons 隨相應套件提供。正式 App 不包含參考圖封面、示範歌曲或測試音訊。

LRCLIB／ListenBrainz 的資料權利與服務條款獨立於 App 程式碼。歌詞屬各自權利人；App 不附帶歌詞資料庫。使用者匯入的音訊、封面及歌詞屬其各自權利人。

## 0.2 新增依賴

monologue 原始碼採 **GPL-3.0-or-later**，完整授權見 LICENSE。NewPipe Extractor v0.26.5 同為 GPL-3.0-or-later，版權屬 [Team NewPipe 與貢獻者](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5)；授權副本見 licenses/NewPipeExtractor-GPL-3.0.txt。發佈 APK 時需一併提供對應完整來源、修改及建置材料，並保留授權。本次隨附來源 ZIP，未公開發布。

NewPipe v0.26.5 的 POM 宣告 nanojson、jsoup、JSR305、Protocol Buffers Java Lite、Mozilla Rhino／Rhino Engine 為依賴。已核對 Rhino／Rhino Engine 的 POM 標示 MPL-2.0、jsoup 的 POM 標示 MIT；其餘傳遞依賴及 Android desugar libraries 須按實際封裝版本的原始 LICENSE／NOTICE 保留，不能一律當作 Apache 2.0。公開發佈前需對最終 APK 的完整依賴與素材再作授權盤點。

Spotube 僅用於功能及接入架構研究；沒有複製其 Flutter／Hetu 實作，也不宣稱可直接安裝 `.smplug`。外掛服務的資料、音訊及條款獨立於 monologue 程式授權。
