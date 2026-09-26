# 私隱及資料傳輸

monologue 的預設狀態沒有帳號、不啟用 ListenBrainz 上傳、不啟用線上歌詞及自動增量下載。沒有內建分析 SDK、廣告、遙測或遠端錯誤回報。介面示範資料只供 Compose Preview／測試，初次啟動不注入歌曲或假下載。

| 功能 | 資料 | 目的地／時機 |
|---|---|---|
| 本機索引 | 歌名、歌手、專輯、長度、URI／路徑、封面 | 本機 Room；獲授權後掃描 |
| 統計 | 播放實例 ID、實際聆聽時段及計次事件 | 本機 Room；預設開啟，可關閉／匯出／刪除 |
| Google Drive | Google 授權、檔案 ID、資料夾／檔案查詢及音訊 range | Google；明確連接後，用於讀取既有音樂；唯讀 |
| ListenBrainz | 歌名、歌手、專輯、時間、長度、提交 App 名稱 | api.listenbrainz.org；獨立啟用同步後才上傳 |
| 線上歌詞 | 目前歌曲曲名、歌手、專輯、長度 | 預設 lrclib.net 或使用者配置 HTTPS 相容服務；獨立同意後查詢 |
| 推薦 | 帳號使用者名稱 | ListenBrainz；使用者要求更新推薦時 |

Google 短效存取權杖只留記憶體。ListenBrainz Token 以 Android Keystore AES-GCM 加密，放在 noBackupFilesDir；不寫入 DataStore、路由或診斷。App 系統備份關閉。開啟 Token 顯示是使用者可控的暫時畫面狀態，不構成驗證成功。

關閉同步停止新上傳；不會刪除 ListenBrainz 伺服器既有歷史。斷開帳號可選擇保留或刪除該帳號 outbox；重新連接另一帳號不會把原帳號佇列混入新帳號。

統計 CSV 包含媒體穩定 ID；設定／播放清單匯出可能含使用者配置的資料夾 ID、名稱與本機媒體 ID，應視為個人資料。兩者不包含 Token。診斷只含版本、Android API、總數和播放狀態，排除帳號、曲名、URI 及私人路徑。

清除串流快取不會刪除永久下载。本機索引清除不會刪除原始音訊。刪除永久下載、清除統計、還原偏好為分開操作。永久下載存在 App 私人內部／外置目錄；解除安裝會移除這些資料。此版本沒有雲端備份音訊功能。

## 0.2 線上音源及更新

- YouTube／MusicBrainz：按搜尋後傳送查詢字串；選擇播放／下載後請求該來源的音訊。MusicBrainz 僅提供資料，沒有把完整媒體庫上傳作配對。
- Spotify：使用外部瀏覽器登入及 PKCE，只讀取使用者授權的歌曲／歌單資料。access token、refresh token、PKCE state/verifier 使用 Keystore 加密的 no-backup store；沒有 client secret、密碼收集或憑證匯出。Spotify 未配置時不發出登入請求。
- HTTP 音源外掛：安裝前展示服務 endpoint 及允許的音訊網域；查詢送到使用者安裝的提供者。外掛設定不隨一般設定匯入自動啟用。
- GitHub 更新：使用者配置 repository 並按檢查後查詢公開 Releases；只下載使用者選定的更新，不上传媒體庫或 Token。安裝前檢驗套件、版本及簽章，Android 再確認安裝。
- 探索支持金額為本機假設計算，沒有付款、債務、廣告收益追蹤或向藝人發送資料。
