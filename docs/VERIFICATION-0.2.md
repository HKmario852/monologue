# monologue 0.2.0 驗證報告

驗證日期：2026-09-25 至 26 日（Asia/Hong_Kong）。套件 `io.hkmario.monologue`，build 2，最低 API 26／目標 API 35。本報告區分**程式建置**、**確定性測試**、**MuMu 實際操作**和**外部服務**。`VERIFICATION.md` 是 0.1.0 歷史報告；不能拿舊結果當成本版新音源的端到端驗證。

## 編譯及來源

| 類別 | 結果 | 證據／含義 |
|---|---|---|
| Debug APK、Android 測試 APK、JUnit、Lint | 通過 | 固定依賴離線 Gradle `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`，`BUILD SUCCESSFUL`；[記錄](evidence/v0.2/build.log) |
| 來源 ZIP 解壓至新工作目錄重建 | 通過 | 在沒有專案 build 快取的新目錄、使用同一 `-PgoogleAuthConfigured=true`，`assembleDebug testDebugUnitTest --offline`：46/46 工作執行，19/19 JUnit 通過；[記錄](evidence/v0.2/clean-source-build.log) |
| 本機 JUnit | 19/19 通過 | 原有核心邏輯 17 項及更新版本／支持金額 2 項；[JUnit XML](evidence/v0.2/unit-core.xml)、[更新 XML](evidence/v0.2/unit-update.xml) |
| Lint | 0 errors、40 warnings | [原始 XML](evidence/v0.2/lint.xml)。警告主要包括已固定依賴版本與 Android API／圖示建議；沒有用 baseline 隱藏錯誤 |
| 安裝及身份 | 通過 | MuMu `adb install -r` 成功。APK 實際 package、versionCode、label、SHA-1／SHA-256 見 [APK 資訊](evidence/v0.2/apk-info.txt) |
| 品牌 | 通過 | 正式來源／資源無 `monologue player` 字串；APK label 為 `monologue` |
| 暖白 Concept 02 黑膠 | 通過視覺檢查 | 實際 Compose 截圖見獨立預覽；圓形深色唱片、槽紋、固定右唱臂、暖白背景。示範曲名僅在 debug preview／測試 |

交付 Debug APK 用 `-PgoogleAuthConfigured=true` 建置。這表示 App 不再顯示「開發方未配置」閘門，**不表示已核實 Google Console 或已登入 Drive**。此 APK 的 `SPOTIFY_CLIENT_ID` 是空字串、redirect 是 `.invalid` 佔位，因此 Spotify UI 正確顯示未設定，不會聲稱成功登入。來源 ZIP 須以相同 Gradle property 建置才可重現相同登入入口狀態。Debug 簽署只供開發；將來正式簽章必須另登記 Google OAuth 指紋。

## MuMu 實際介面與持久化

裝置：使用者已有的 MuMu Player 12，Android 12L／API 32，ADB `127.0.0.1:16448`。測試期間曾修改顯示尺寸／字體檢查響應式；交付前已復原原本的 900×1600、240 dpi、字體 1.0、原旋轉偏好。沒有清除整個 App 或使用者帳號；只移除本次已知 ID／路徑的測試媒體索引。

| 驗收 | 結果 | 證據／界線 |
|---|---|---|
| 主畫面、設定、黑膠、歌詞／收合、導航等既有 Compose 測試 | 12/12 通過 | 與新增測試一起執行的 [20 項記錄](evidence/v0.2/mumu-main.txt) |
| 探索兩卡、逐歌時長、Token 只在設定、底部設定、未配置更新 | 5/5 通過 | 同上；統計 UI 中 7 秒／零達標次數的項目是**明確測試 fixture**，正式 App 不注入 |
| 實際 Room 聆聽事件 → ViewModel 的全部／歷史月份明細，且不改排行榜期間 | 1/1 通過 | 同上；測試後刪除其 UUID 事件與歌曲 |
| Room outbox 關閉重開／去重、下載失敗項重試且成功項不重抓 | 2/2 通過 | 同上；使用隔離測試資料庫 |
| 真實本機 WAV、Media3 播放／暫停／背景、隊列與 Drawer 切換 | 1/1 通過 | [播放／搜尋／儲存三項記錄](evidence/v0.2/mumu-backend.txt)。自行產生音訊只在測試裝置，不包入 App |
| 快速輸入／切 Tab 過期搜尋、磁碟快取保護與離線檔案分離 | 2/2 通過 | 同上 |
| 320dp 級細直向與 1.5 倍字體 | 5/5 通過 | [細直向記錄](evidence/v0.2/mumu-small-portrait.txt) |
| 細橫向與 1.5 倍字體 | 5/5 通過 | [細橫向記錄](evidence/v0.2/mumu-small-landscape.txt)。導航列高度隨字體擴展、完整項目點按；也在真實 App 手動點按設定驗證 |
| 只移除已知 QA 媒體索引 | 1/1 通過 | [清理記錄](evidence/v0.2/mumu-fixture-cleanup.txt)；不作「清除所有 App 資料」 |

細直向／細橫向是在不同顯示設定**重跑同一組 5 項**，不能當作另有 10 個不同案例。早期橫向＋大字體曾出現測試點按標籤定位失敗；後來調整導航高度與測試以完整導航項目點按，重跑 5/5 通過，且用實際 App 點按「設定」確認。舊失敗記錄不作最終通過證據。上述不等同所有 OEM、字體縮放、TalkBack 焦點順序或長時背景測試。

原有 v0.1 的黑膠單調時間、下載狀態機、LRC、隊列、排行榜邊界及快取測試保留並在本版 19 項 JUnit／12 項 UI 中重跑相關案例；詳見歷史 [0.1 詳細驗收](VERIFICATION.md)。本次未重新以真實拖曳手勢逐一做歌詞列表、邊緣拖曳及各 OEM 音訊焦點測試，因此不把每個視覺互動寫成「MuMu 人手實測」。

## 真實外部音源測試

在 MuMu 上對公開查詢 `Bach cello suite` 跑單獨 [LiveSourceProbe 原始記錄](evidence/v0.2/live-source.txt)：MusicBrainz 取得 **25** 筆真實 recording 結果；YouTube 取得 **20** 筆真實搜尋結果；所選流的 HTTP Range 回 `206` 及 4096 bytes，resolver 回報 `audio/webm`、161940 bps；Media3 實際進入 `isPlaying`、位置前進至 1365ms、解碼格式回報 **Opus／48kHz／2 聲道**。這是**一個公開樣本、一段實際播放**；不證明所有歌曲／地區／會員狀態可用，亦不證明 24-bit、無損或裝置 DAC 輸出品質。`LiveSourceProbe` 即使外部服務失敗也寫出 FAILED 字樣，因此**通過測試 runner 並不等於所有網絡步驟成功**；這些數值直接核對原始報告。

另外在正式 App UI 實際搜尋相同關鍵字，點 Yo-Yo Ma 公開搜尋結果「播放」，看到 Mini Player 的「暫停」按鈕，再展開 [全螢幕實際播放截圖](evidence/v0.2/player-online-live.png)：封面、唱臂、約 16 秒進度及 `audio/opus · 48000 Hz · 2 聲道` 是實際 Media3 狀態。音訊由 UI 事件進入服務，之後已按暫停；截圖不是 Compose 測試 fixture。搜尋結果頁另有 [實際截圖](evidence/v0.2/online-search.png)。這個操作仍只是一首歌曲、一次目前地區與網路條件的樣本。

Google Drive：Mario 已表示完成 `grand-bridge-486116-u2` 的一次性 Cloud 設定。本版 APK 的「連接 Google Drive」已在 MuMu 顯示權限用途提示，按繼續後**真實進入 Google 原生選擇帳戶頁**。測試在選擇帳戶前收起，沒有替使用者同意授權，也沒記錄或展示私人電郵。因此**未實測**授權後帳戶名稱、檔案列表、Range 串流、永久下載、更新、失去授權及配額處理；也未能獨立查看 Cloud Console 設定。只有上述入口流程可稱通過。

Spotify：沒有 Developer Client ID 或有權控制的 HTTPS redirect，**未測真實 PKCE 回調、歌單分頁或帳號**；現有可配置程式碼與未設定畫面不能當作已接通。Spotify metadata 與音訊來源分開，不能宣稱 Web API 直接提供原音訊。

ListenBrainz：沒有 Token，Token 驗證、上傳、推薦沒有真帳號測試；Room outbox／狀態 UI 有本地測試。網絡錯誤與 Token 無效有分開分支，但伺服器重試 exactly-once 需要真帳號與失敗注入測試。

第三方音源插件：本版實作自己的宣告式 HTTPS provider v1 配置與限制，**沒有用真實第三方 v1 服務完成安裝→搜尋→解析→播放端到端測試**；不相容 Spotube `.smplug`。Dab 插件原端點曾回 HTTP 404，且插件範例音質欄位有硬編碼風險；Jiosaavn、Lossless Sources 並未列為已接入。不要將插件設定 UI 說成上述來源可播。

GitHub App 更新：檢查版本、WorkManager 下載及 APK 包名／versionCode／簽署驗證已編譯，單元測試涵蓋版本比較；Mario 尚未發佈公開 Release，**未做真實檢查→下載→系統升級端到端測試**。App 不會自行發佈 GitHub repository。

其他未實測：有權服務提供的真正無損音訊、YouTube 完整永久下載、Drive 真帳戶下載恢復、Spotify 真登入、ListenBrainz 網絡重試、實體耳機／通話／Bluetooth／音效硬體、API 26／35 裝置矩陣、真人 TalkBack 全流程、所有 Dynamic Color 壁紙配色。這些項目在程式內的可配置／未設定狀態、單元測試或 UI 測試，不可代替現場端到端驗證。

## 交付及重建

從專案根目錄可執行：

```powershell
./gradlew.bat -PgoogleAuthConfigured=true :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
```

`GOOGLE_AUTH_CONFIGURED=true` 只給已完成開發方 OAuth 設定的簽署／包名組合；其他環境省略該 property，顯示清楚未配置入口。從交付的來源 ZIP 解壓，需 JDK 17 或相容版本、Android SDK 35、網絡可取得 Gradle wrapper 與所列固定依賴。本次將 0.2 來源 ZIP 實際解壓至全新工作目錄，以已快取的固定依賴離線重建 APK，並重跑 19 個 JUnit；這證明來源可在此環境重建，未證明另一台沒有 SDK／快取的電腦可以離線獨立建置。可核對 ZIP 完整性與 APK 的 [SHA256SUMS](../../SHA256SUMS.txt)、[來源清單](../../verification/source-manifest.json)。

預覽 HTML 內嵌實際 Compose／MuMu 圖像：黑膠／媒體庫示範歌曲有標「設計測試資料」；探索、設定、Drive 未連接狀態由真實 App 截圖，不偽造連線或統計。來源 ZIP 不包含私人的 `local.properties`、keystore、Token、MuMu 數據、完整音訊或使用者帳戶資訊。
