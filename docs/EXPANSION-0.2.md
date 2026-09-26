# monologue 0.2 — 音源、探索及導航

本次保留原有 Kotlin／Compose 暖白黑膠設計。底部導航為媒體庫、雲端、排行榜、探索、設定；主要頁面不再重複顯示右上角設定。ListenBrainz Token 只在「設定 → ListenBrainz」輸入。

## 探索及統計

- 第一張卡顯示全歷史實際聆聽分鐘及達標播放次數。點入可查看每首歌的累計時間、次數，含未達計次門檻的短片段；週／月／全部可切換，歷史週月可回看。
- 第二張卡預設顯示本月支持金額估算，點入按歌手彙總。計算是使用者參考圖所示的 **假設 US$ 0.003–0.005 × 本機達標播放次數**，不是 Spotify 固定費率、實際欠款或已付款收益。所有值由 Room 事件計算，無示範統計注入。
- 統計明細的週期獨立於排行榜，不會因點開卡片改動排行榜原選擇。時區沿用設定。

## 真實音源與資料

YouTube Audio：採固定 NewPipe Extractor v0.26.5 搜尋與解析 progressive audio stream；Media3 播放、Range seek、串流快取、背景播放沿用同一播放服務。永久下載使用既有 Room／WorkManager 循序隊列，亦保留曲間暫停與失敗重試。音訊 URL 到期後重新解析，不把短效 URL 寫入隊列；品質偏好被固定到該播放項目的 URI／cache key，避免切換設定混合不同品質的快取。

MusicBrainz：真實 recording 搜尋，包含歌名、歌手、專輯、長度及 MBID；節流至少 1.1 秒一次。與 ListenBrainz 帳號、推薦及聆聽同步共用外掛分類入口。資料結果不是音訊，按「尋找可播音源」後讓使用者選擇正確演出版本；不以搜尋第一筆假裝精準配對。

Spotify：原生瀏覽器 PKCE 登入、state／10 分鐘有效期驗證、加密 refresh token、搜尋與分享播放清單分頁讀取。實際音訊來源另行標示。沒有 Spotify client secret，也沒有宣稱從 Spotify Web API 取得原音訊。此交付未配置 Client ID／已驗證 HTTPS redirect，登入按鈕有清楚未設定原因；尚未做真實帳號測試。

## 音質

最高可用／節省流量（優先 ≤192 kbps），解析音源時套用；已下載檔案不自動轉碼。播放頁顯示 Media3 解碼器實際回報的 codec、可用 bitrate、sample rate、channels；未知資料不補造。這些是輸入音訊資訊，不代表藍牙、系統 mixer 或 DAC 的最終輸出，也不宣稱 bit-perfect。

Spotube Dab 插件原碼的轉換器把第一項寫成 24-bit／96kHz，另有 mp3／aac 項被標為 lossless。這些常數沒有作為 monologue 音質證據。2026-09-25 對該插件使用的 `/api/search` 端點檢查回傳 HTTP 404，因此沒有將 Dab 列為已可用來源。Jiosaavn、Lossless Sources 也尚未完成可用性及來源授權核對；本版不宣稱已接入。

## 外掛

內建接入模組可分類、停用及設定預設音訊來源。第三方擴充支援宣告式 **monologue HTTP provider v1**，見 [PROVIDER_PROTOCOL.md](PROVIDER_PROTOCOL.md)：由 HTTPS 取得描述檔，先展示服務與允許的音訊網域，再由使用者確認安裝；DataStore 持久化；更新重新確認；可移除。不執行下載的 Dart、Hetu 或任意 APK。**不相容 Spotube `.smplug` 二進位檔**，此限制在 App 明示。沒有把尚未接通的第三方服務放成假安裝成功卡片。

## App 更新

設定 → App 更新可配置公開 `owner/repository`。檢查 GitHub latest 非預發布 release，數字比較版本，選單一／universal APK。WorkManager 下載，驗證大小、GitHub 提供的 SHA-256（若有）、套件 ID、較高 versionCode、相同簽署，才開啟 Android 安裝介面。下載中不因關閉設定頁停止。不會自動建立或發布 GitHub repository。尚未有正式 Release，因此正式檢查→下載→升級端到端流程未實測。

## 開發方設定

### Google Drive

Mario 提供的專案：`grand-bridge-486116-u2`。

1. [啟用 Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=grand-bridge-486116-u2)。
2. [設定 OAuth 同意畫面](https://console.cloud.google.com/auth/branding?project=grand-bridge-486116-u2)，測試模式加入測試登入帳號。
3. [建立 Android OAuth client](https://console.cloud.google.com/auth/clients?project=grand-bridge-486116-u2)，package `io.hkmario.monologue`。
4. 本次 Debug 簽署 SHA-1：`BD:B7:63:15:55:00:11:6E:EC:1B:F4:DD:1D:98:7E:59:14:2F:8A:07`。
5. 完成後以 `-PgoogleAuthConfigured=true` 建置。正式簽章改變時須另外登記其 SHA-1。

上述只由開發方設定一次。一般使用者只需選擇 Google 帳號並授予唯讀存取。Mario 已於本次對話確認完成 Cloud 設定，因此交付 APK 使用 `-PgoogleAuthConfigured=true`。MuMu 實測已彈出 Google 原生帳戶選擇器；沒有替使用者選擇帳號或同意授權，故 Drive 檔案清單／串流／下載仍未做已授權帳號測試。Browser 工具未能操作 Cloud Console，這裡不宣稱已獨立核驗主控台各項設定。

### Spotify

建立自己的 Spotify Developer App（API 選 Web API），取得公開 Client ID。0.2.1 起預設 redirect 為 App 專用 scheme，不需自設網站：

```
io.hkmario.monologue://spotify/callback
```

在 Spotify 後台 Redirect URIs 填入上面整行，然後：

```
gradlew.bat -PgoogleAuthConfigured=true -PspotifyClientId=YOUR_PUBLIC_CLIENT_ID :app:assembleDebug
```

若 Spotify 後台不接受自訂 scheme，可改用自己控制的 HTTPS 網址（例如 GitHub Pages 的 `/spotify/callback`，該頁把 `code` 與 `state` 轉交給 `io.hkmario.monologue://spotify/callback`），並以 `-PspotifyRedirectUri=https://...` 建置。scheme 只接受 `https` 或與 applicationId 相同的自訂 scheme。

redirect 必須與 Spotify 後台登記值完全一致。SDK 沒有保存密碼，PKCE verifier／state／token 放入 Keystore 加密的 no-backup store。需要帳號授權後才讀取歌單。開發模式的帳號／API 存取限制須依 Spotify 當時規則配置。普通使用者不輸入開發 Client ID。

## 參考與授權

- [Spotube](https://github.com/KRTirtho/spotube)：功能研究，沒有複製其 Flutter UI 或 Hetu 插件程式碼。
- [Dab 來源轉換器](https://github.com/KRTirtho/spotube-plugin-dab-music/blob/main/src/segments/audio_source.ht)。
- [NewPipe Extractor v0.26.5](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.26.5)：實際程式依賴，GPL-3.0-or-later。monologue 本次原始碼採相同授權，完整文字見 LICENSE 及 licenses/。
- [Spotify PKCE](https://developer.spotify.com/documentation/web-api/tutorials/code-pkce-flow)、[Redirect URI](https://developer.spotify.com/documentation/web-api/concepts/redirect_uri)、[Android SDK](https://developer.spotify.com/documentation/android)。
- [Android 授權](https://developer.android.com/identity/authorization)。

測試證據另見 VERIFICATION-0.2.md。無憑證或外部端點不可用的項目，明確列為未完成外部配置／未實測，沒有 mock 成功替代。
