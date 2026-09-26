# 設計系統

正式 App 採 Concept 02 的四目的地、水平清單、直向最近播放、底部 Mini Player 與大型黑膠結構；配色、留白及文字質感採 Concept 03。品牌只顯示 monologue。唱片仍為 #171717，唱臂是獨立不旋轉圖層；沒有使用大型長方形封套播放器。

完整 Kotlin Material 3 ColorScheme、全部 Typography 角色及 Shapes 見 `app/src/main/java/io/hkmario/monologue/ui/Theme.kt`。可供設計工具使用的全部顏色值見 `PaperColors.json`／`NightColors.json`。

| Token | 暖白 |
|---|---|
| background / onBackground | #F5EFE4 / #2E2722 |
| surface / onSurface | #FBF7EF / #2E2722 |
| surfaceContainer | #EEE5D9 |
| primary / onPrimary | #9F412C / #FFFFFF |
| primaryContainer / onPrimaryContainer | #F3DDD1 / #542718 |
| secondary / onSurfaceVariant | #655A50 |
| tertiary / 成功 | #4B6551 |
| error / onError | #9C2039 / #FFFFFF |
| outline / outlineVariant | #8A7C6E / #D8CABC |
| 黑膠 / 中央標籤起始色 | #171717 / #A74932 |

原定主要色 #A74932 在較深的 surfaceDim / surfaceContainerHigh / surfaceContainerHighest 上，細字對比只有 4.07–4.38:1。因此操作／文字語意色調整為 #9F412C，保留唱片標籤的原始赤陶色。122 組暖白及深色靜態配對全部通過指定門檻，完整公式結果見 CONTRAST.md。淡分隔線是裝飾用途；不作唯一互動邊界。

預設暖白；深色及跟隨系統由 DataStore 即時套用。Dynamic Color 預設關閉，Android 12+ 開啟時只替換 Material ColorScheme，字型、形狀及黑膠幾何保持一致。

字體使用 Android 系統 Serif / SansSerif fallback，不封裝未授權字體。品牌、大標題及曲名使用襯線；內文、按鈕、搜尋及狀態使用無襯線。中文字形按裝置系統字體回退；不保證所有廠牌提供相同中文襯線字形。時間使用 `fontFeatureSettings = "tnum"`。

| 項目 | 尺寸 |
|---|---|
| 設計基準寬度 | 390 dp |
| 頁邊距 | 24 dp；紧湊頁 20 dp |
| 階梯 | 4 / 8 dp |
| 一般互動範圍 | 至少 48 dp |
| 主播放按鈕 | 76 dp |
| Mini Player | 約 72 dp，加獨立安全區 |
| M3 Shapes | 4 / 8 / 14 / 20 / 28 dp |
| 唱片轉速 | 15°/秒，24 秒一圈 |
| Scrubber | 3 dp 視覺線、12 dp 圓點、48 dp 觸控高度 |
| Display | 52 / 44 / 36 sp |
| Headline | 32 / 28 / 24 sp |
| Title | 22 / 16 / 14 sp |
| Body | 16 / 14 / 12 sp |
| Label | 14 / 12 / 11 sp |

畫面使用系統字體縮放、可捲動內容及橫向双欄播放器。主頁 Scaffold 的 bottom content padding 包含 Mini Player 和導航列。Now Playing 使用 safeDrawingPadding；列表底部不由 Mini Player 覆蓋。沒有玻璃、霓虹、背景噪點或網上素材。
