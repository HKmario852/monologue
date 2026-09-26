> 此檔保留 0.1 歷史證據。最新 0.2 結果請看 [VERIFICATION-0.2.md](VERIFICATION-0.2.md)，不可將舊測試當作新增整合的驗證。

# monologue 驗證報告

驗證日期：2026-09-25（Asia/Hong_Kong）。版本 0.1.0／build 1，package `io.hkmario.monologue`。交付為可安裝的 Debug 開發版；尚未完成全部實體裝置及外部帳號的端到端驗收，不能視為已上架的 production release。

## 結果摘要

| 類別 | 結果 | 證據／界線 |
|---|---|---|
| Kotlin／Compose／Room KSP／APK 編譯 | PASS | Gradle BUILD SUCCESSFUL；固定依賴組合 |
| 來源 ZIP 解壓後獨立建置 | PASS | 新目錄 assembleDebug＋17 個 unit tests，45 個建置工作均實際執行；[記錄](evidence/source-zip-build.log) |
| 純邏輯 JUnit | 17 / 17 PASS | [unit.xml](evidence/unit.xml) |
| Android instrumentation | 17 / 17 PASS | Android 11 API 30 x86_64 模擬器；[phone.xml](evidence/phone.xml) |
| 320 × 640 dp 窄螢幕 | 4 / 4 PASS | 媒體庫／設定、播放器、1.6 倍字體、Scrubber；[small.xml](evidence/small.xml) |
| 640 × 320 dp 橫向 | 3 / 3 PASS | 播放器、1.6 倍字體、Scrubber；[landscape.xml](evidence/landscape.xml) |
| Lint | 0 errors，31 warnings | [lint.xml](evidence/lint.xml)；沒有用 baseline 隱藏錯誤 |
| 固定暖白／深色語意對比 | 122 / 122 PASS | [完整對比](CONTRAST.md)；細字 ≥4.5:1，必要控制邊界 ≥3:1 |
| APK 安裝／啟動／簽章 | PASS | adb 安裝成功、MainActivity 啟動；[APK 資訊](evidence/apk-info.txt)、[簽章](evidence/apk-signature.txt) |
| 程序終止後偏好／隊列恢復 | PASS | 真正 force-stop 後產生新 PID；Drawer、隊列恢復，自動播放仍關閉；[記錄](evidence/process-restart.json) |
| Google Drive／ListenBrainz／線上歌詞帳號實測 | 未執行 | 沒有提供 Google OAuth 專案／ListenBrainz Token；沒有宣稱連線、下載或同步成功 |

窄螢幕與橫向的 7 次執行是重跑部分 Android 案例，並非另有 7 個獨立案例。1.6 倍字體由 Compose Density 提供；不等同已測試所有 OEM 系統字體設定。

Lint warnings 包括 19 個較新工具／依賴提示、公開 MediaSessionService 提示、備份規則提示、3 個剩餘空間檢查提示及 7 個 KTX 建議。版本刻意固定為已解析成功的穩定組合，沒有宣稱最新。MediaSessionService 按媒體控制需求公開；備份已關閉。空間檢查後的實際 I/O 失敗仍由下載流程處理。

## 已驗證的必要行為

| 項目 | 驗證方式與結果 |
|---|---|
| 品牌一致 | 主程式／資源掃描沒有舊品牌字串；APK label 為 monologue |
| 暖白／圓形黑膠／右唱臂 | 實際 Compose 截圖目視檢查；橫向裁切問題已修正 |
| 暫停、繼續、換曲及隱藏角度 | 單調時間演算法自動測試：定格、延續、不追補隱藏時間、降低動態、長時無累加漂移 |
| 每幀更新範圍 | 角度只在 Vinyl graphicsLayer 讀取；沒有 track ID key／每曲重新啟動角度 |
| 下滑與 Scrubber | 真實觸控事件：封面下滑收合、取消拖曳不收合、Scrubber 拖動只提交 seek 並不收合 |
| Mini Player 分離點按 | 播放與隊列按鈕均不誤觸展開 |
| Tab 搜尋 | 單曲、歌手、專輯、資料夾限定模型；Unicode NFKC、歷史去重上限測試 |
| 快速輸入／切 Tab | 真正 ViewModel／Room 測試，旧請求不能覆蓋新 Tab；載入時清空舊結果 |
| 首次音樂權限 | 實際安裝按鈕觸發 Android 權限對話框；補上 Room 初始回傳不可蓋過權限狀態的回歸断言 |
| 真實本機掃描及播放 | 在隔離模擬器發佈自行產生的 3 秒 WAV，App 掃描到歌曲並播放；排行榜顯示 1 次。測試檔不包入交付 App |
| Media3 背景／暫停 | 另一個 20 秒 WAV 裝置測試確認引擎 position 前進、暫停固定、HOME 後繼續播放；不是模擬播放計時器 |
| 導航切換 | UI 目的地保持；實際播放 Repository 保留相同 queue entry，沒有重建服務來切換導航 |
| 隊列 | 重複歌曲不同 UUID、目前項目重新排序不重播、延後移除仍繼續播放 |
| LRC | 多時間戳、offset、重複行、純文字及保守翻譯配對；時間不可靠時保留原文 |
| 曲間暫停 | 狀態機測試：等目前項目完成／失敗才 Waiting；關閉開關不自動繼續；最後一首直接 Complete |
| 下載恢復／失敗重試 | 真正 Room 關閉再開啟；Waiting 保留、只重排 Failed，Complete bytes 與狀態不變 |
| 快取保護 | 實際 SimpleCache 磁碟測試，以縮小限額驗證 LRU、播放 pin、拒絕不安全寫入、延後清理與永久檔案分離；正式上限 1,000,000,000 bytes |
| 排行週期 | 週一／月初、歷史範圍、end-exclusive、DST 邊界單元測試；不刪歷史 |
| 聆聽時間 | 單調實際時間與媒體位置分開；暫停、緩衝與 seek 不灌水；一次播放實例只計一次 |
| 同步 outbox | 實際 Room 關閉重開保留 payload，穩定 ID 重複插入只有一筆，不同帳號分隔；只在 ack 流程刪除 |
| 未設定／無音源 | Drive 未設定畫面測試；未匹配推薦沒有可執行播放事件，沒有假成功訊息 |
| 資料持久化 | DataStore 讀回及真實程序重啟；Room queue、下載控制、outbox 關閉重開 |
| 小螢幕／大字體／橫向 | 指定尺寸的實際 Compose 測試及截圖檢查；控制可捲動到達 |

## 未實測及限制

- **Google Drive**：原生 AuthorizationClient、唯讀 scope、分頁瀏覽、ID／版本／MD5 比對、實際 HTTP 串流／下載、原子提交及錯誤處理均有程式；尚未用真實帳號測試 OAuth、Range seek、版本更新、失去權限、quota、網絡中斷與 WorkManager 恢復。交付 APK 預設 GOOGLE_AUTH_CONFIGURED=false，需按 README 登記 Android OAuth 套件與簽署 SHA-1 後重新建置。
- **ListenBrainz**：驗證、提交、推薦及限速處理接真實端點；沒有 Token，所以未驗證伺服器互動。穩定 ID 在本機去重，重試沿用原 timestamp／payload；不能僅靠本機測試宣稱網絡回應遺失時伺服器達到 exactly-once。需真實帳號確認伺服器的去重行為。
- **歌詞**：本機／快取／可選 LRCLIB 整合已寫入。線上服務未做實際曲目查詢。翻譯支援匯入可靠帶時間戳的 LRC；沒有內建自動翻譯供應商，也沒有虛構譯文。
- **實體裝置**：未做實際通話、耳機拔除、Bluetooth／鎖屏控制、硬件 EQ 頻段、長時間睡眠淡出、音質及 OEM 省電限制測試。AOSP 模擬器為無音訊輸出的測試環境，驗證的是 ExoPlayer 真實讀取與播放狀態，不能當成聽感測試。
- **平台覆蓋**：未跑 API 26／31+ Dynamic Color／33 音樂與通知權限／35 背景配額實機矩陣。Dynamic Color 使用平台方案，未核對個別壁紙的實際顏色組合。
- **TalkBack**：已提供控制描述、完整曲名、隊列上移／下移／移除替代操作；沒有真人開啟 TalkBack 完成逐頁朗讀及焦點順序驗收。語意存在與點按測試不等於 TalkBack 全面通過。
- **大型資料及長手勢**：沒有萬首歌壓力測試、長時間多人來源下載、長隊列邊缘拖曳的實機人工驗收。Queue 長按拖曳／邊缘自動捲動已實作，Repository 排序有測試；手指長距離操作仍需人工驗收。
- **持久下載取捨**：永久下載只提供 App 內部／外置私人位置；不是任意 SAF 雲端文件供應商。未完成的單檔在恢復後重新下載；不宣稱 byte-resume。舊版本保留以保護既有 session，使用者刪除該離線歌曲時連已索引舊版一併處理。
- **減少動態範圍**：App 開關停用黑膠旋轉；Material 3 Sheet／Drawer 的內建轉場仍依系統動畫設定，尚未全面以 App 開關禁用所有轉場。
- **畫面狀態**：同一播放工作階段的黑膠角度保留；程序重建後角度重新開始，沒有沿用舊程序單調錨點。UI 語言目前是繁體中文，其餘系統語言回退繁體中文。

## 修正紀錄及交付證據

驗證期間修正了暫停位置更新延遲、Scrubber 觸控區過薄、獨立深色預覽文字顏色、橫向唱片裁切、Room 初始回傳覆蓋權限狀態，以及未完成曲目的延後移除邊界。以上不是以刪除測試或永遠成功的 mock 規避；最終相應測試通過。

媒體庫／播放器預覽明確使用示範曲名，沒有寫入正式資料庫。真正安裝及執行測試使用自行產生的測試音訊，亦沒有包入 App。來源封裝不含 local.properties、建置快取、私人憑證或測試裝置資料。

APK SHA-256：`6ac7dbf205c7310e833cfe46d2ed40c0dcc519c5e5d4e91781d1f659d731853e`

APK bytes：24594668。開發簽章資訊與 build logs 在 evidence。程式碼、Gradle wrapper、Manifest、資源、Room schema、Compose Previews、單元及裝置測試都包含在來源 ZIP。
