# monologue HTTP provider v1

這是 monologue 自有擴充協定，不是任何現存服務的虛構官方 API，也不是 Spotube `.smplug` 執行器。服務端需自行實作這個協定，並有權提供其音訊。App 不內建代理站、使用者密碼或其他人的憑證。

## HTTPS 描述檔

必要欄位：`schema`（1）、`id`（3–64 個小寫字母／數字／點／連字號，首位字母；不能用 youtube、spotify、musicbrainz）、`name`、`version`、`endpoint`（HTTPS API base URL）、`audioHosts`（精確網域名陣列，無 wildcard）。App 自行記錄描述檔網址為 `manifestUrl`。安裝前會展示 endpoint／audioHosts，更新時再次確認。描述檔上限 5 MB。

## 搜尋

`GET {endpoint}/search?q={URL-encoded text}`

回應物件含 `tracks` 陣列（App 最多讀取 100 首），每首必要 `id`、`title`；可選 `artist`、`album`、`durationMs`、`artwork`（HTTPS）。搜尋結果必須來自該服務，不使用測試成功資料替代外部連接。

## 解析

`GET {endpoint}/resolve?id={URL-encoded stable ID}`

回應物件含 `streams` 陣列；每項必要 `url`（HTTPS，host 在已批准 audioHosts 內）、`mime`；可選 `bitrate`（bits/s）、`sampleRate`（Hz）、`bitDepth`、`lossless`。未知數字留空／0。不要把有損轉碼標為 lossless。最高音質策略優先服務宣告的無損、bit depth、bitrate／sample rate；播放頁仍顯示實際解碼資料。

目前協定不含帳號登入／付費 entitlement 交換。需要憑證的提供者須新增專用、隔離儲存憑證的原生 adapter；不要把 secret 放在描述檔 URL 或音訊 host 設定。端點應支援 Range／可持續下載，否則 App 只能使用其實際能力。

## 儲存與生命週期

已安裝描述檔存於 DataStore。停用阻止新查詢及新解析；移除後已在播放服務開啟的 stream 可能持續至釋放。永久下載保留，不因移除外掛而刪除。音訊快取與永久下載沿用原本分離的儲存機制。此版沒有經真實第三方 v1 服務完成安裝至播放端到端測試；相關契約與安全檢查需服務提供者配合測試。
