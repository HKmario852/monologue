package io.hkmario.monologue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A group on the 設定 home: what it holds in a few words, its icon, and its colour (the 搜尋 tiles' warm palette). */
data class SettingsGroupInfo(val summary: String, val icon: ImageVector, val color: Long)

/** In the order of [settingsGroups]. */
val settingsGroupInfo = listOf(
    SettingsGroupInfo("主題、導航樣式、黑膠與開啟動畫", Icons.Outlined.Palette, 0xFFA74932),
    SettingsGroupInfo("隊列、播放速度、等化器、睡眠計時器", Icons.Outlined.PlayCircle, 0xFF2F5D50),
    SettingsGroupInfo("音樂資料夾、掃描、排序、搜尋紀錄", Icons.Outlined.LibraryMusic, 0xFFA36A1E),
    SettingsGroupInfo("連接、下載位置、Wi-Fi、離線下載", Icons.Outlined.Cloud, 0xFF2D4B6E),
    SettingsGroupInfo("播放暫存、歌詞及封面快取", Icons.Outlined.Storage, 0xFF5D6B2E),
    SettingsGroupInfo("歌詞來源、翻譯、羅馬拼音、字體", Icons.Outlined.Subtitles, 0xFF9C3D5A),
    SettingsGroupInfo("時區、回顧期間、匯出及清除", Icons.Outlined.Insights, 0xFF3E6670),
    SettingsGroupInfo("帳號、同步聆聽紀錄、推薦", Icons.Outlined.Hub, 0xFF6B3E6E),
    SettingsGroupInfo("下載通知、系統權限", Icons.Outlined.Notifications, 0xFF8A5A2B),
    SettingsGroupInfo("版本、診斷資料、備份與還原", Icons.Outlined.Info, 0xFF55606B),
    SettingsGroupInfo("外掛、配對音源、音質偏好", Icons.Outlined.Extension, 0xFF4A3F7A),
    SettingsGroupInfo("檢查及安裝新版本", Icons.Outlined.SystemUpdate, 0xFF7A3B2E),
)

/** A group's icon on the 設定 home: white on a disc of the group's colour. */
@Composable fun SettingsGroupIcon(info: SettingsGroupInfo) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(info.color)), contentAlignment = Alignment.Center) {
        Icon(info.icon, null, Modifier.size(24.dp), tint = Color.White)
    }
}

/** A settings row's icon: a small tile in the theme's colours, so it reads in light and dark. */
@Composable fun SettingRowIcon(icon: ImageVector) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/**
 * Icons for settings rows, by title, so every row in every group gets one without threading a parameter through
 * each call. Rows elsewhere that share the row composables (the song menu's actions) are not listed and show none.
 */
private val settingIcons: Map<String, ImageVector> = mapOf(
    // 外觀與導航
    "主題" to Icons.Outlined.Palette, "動態主題色" to Icons.Outlined.AutoAwesome, "導航樣式" to Icons.Outlined.Dashboard,
    "黑膠旋轉" to Icons.Outlined.Album, "減少動態效果" to Icons.Outlined.MotionPhotosOff, "開啟時播放動畫" to Icons.Outlined.Animation,
    "顯示語言" to Icons.Outlined.Language,
    // 播放
    "恢復上次播放隊列" to Icons.Outlined.Restore, "啟動時自動播放" to Icons.Outlined.PlayCircle, "中斷後恢復播放" to Icons.Outlined.Replay,
    "歌曲之間的靜音" to Icons.Outlined.HourglassEmpty, "歌曲間暫停" to Icons.Outlined.PauseCircle, "耳機拔除時暫停" to Icons.Outlined.HeadsetOff,
    "播放速度" to Icons.Outlined.Speed, "等化器" to Icons.Outlined.Equalizer, "啟用等化器" to Icons.Outlined.GraphicEq,
    "睡眠計時器" to Icons.Outlined.Bedtime, "睡眠結束前淡出" to Icons.AutoMirrored.Outlined.VolumeDown,
    "允許行動網絡串流" to Icons.Outlined.SignalCellularAlt, "完整下載優先" to Icons.Outlined.DownloadForOffline,
    // 媒體庫
    "音樂權限" to Icons.Outlined.LibraryMusic, "授權音樂資料夾" to Icons.Outlined.CreateNewFolder, "更換音樂資料夾" to Icons.Outlined.FolderOpen,
    "取消資料夾授權" to Icons.Outlined.FolderOff, "隱藏資料夾" to Icons.Outlined.VisibilityOff, "重新掃描" to Icons.Outlined.Refresh,
    "自動更新索引" to Icons.Outlined.Sync, "清除本機索引" to Icons.Outlined.DeleteSweep, "最短音訊長度（秒）" to Icons.Outlined.Timer,
    "預設 Tab" to Icons.Outlined.Tab, "歌曲排序" to Icons.AutoMirrored.Outlined.Sort, "專輯排序" to Icons.Outlined.SortByAlpha,
    "記錄搜尋歷史" to Icons.Outlined.History, "清空目前 Tab 搜尋紀錄" to Icons.Outlined.ClearAll,
    // Google Drive 與下載
    "連接／重新驗證" to Icons.Outlined.CloudSync, "斷開 Google Drive" to Icons.Outlined.CloudOff, "永久下載位置" to Icons.Outlined.Folder,
    "只用 Wi-Fi 下載" to Icons.Outlined.Wifi, "每日檢查新歌曲" to Icons.Outlined.Schedule, "下載中心" to Icons.Outlined.Download,
    "管理離線下載" to Icons.Outlined.OfflinePin,
    // 儲存空間
    "清除播放暫存" to Icons.Outlined.Cached, "清除歌詞快取" to Icons.Outlined.Subtitles, "清除封面快取" to Icons.Outlined.Image,
    "重新整理用量" to Icons.Outlined.DataUsage,
    // 歌詞
    "線上歌詞搜尋（LRCLIB）" to Icons.AutoMirrored.Outlined.ManageSearch, "優先同步歌詞" to Icons.Outlined.AvTimer,
    "歌詞服務網址" to Icons.Outlined.Link, "歌詞文字大小" to Icons.Outlined.FormatSize, "全域時間偏移（毫秒）" to Icons.Outlined.MoreTime,
    "本曲時間偏移（毫秒）" to Icons.Outlined.AccessTime, "自動捲動歌詞" to Icons.Outlined.SwapVert, "顯示翻譯歌詞" to Icons.Outlined.Translate,
    "翻譯目標語言" to Icons.Outlined.GTranslate, "翻譯顯示方式" to Icons.Outlined.ViewAgenda,
    "沒有翻譯時在裝置上翻譯" to Icons.Outlined.Smartphone, "自動產生羅馬拼音" to Icons.Outlined.Abc,
    "在其他顯示方式也顯示羅馬拼音" to Icons.Outlined.TextFields, "隱藏括號內的和聲" to Icons.Outlined.VoiceOverOff,
    "匯入本曲原文歌詞" to Icons.Outlined.UploadFile, "匯入本曲翻譯歌詞" to Icons.AutoMirrored.Outlined.NoteAdd,
    // 聆聽統計
    "記錄本機聆聽資料" to Icons.Outlined.Insights, "統計時區" to Icons.Outlined.Public, "預設回顧期間" to Icons.Outlined.DateRange,
    "預設按聆聽時間排序" to Icons.Outlined.Timer, "匯出個人統計" to Icons.Outlined.FileDownload,
    "清除目前回顧期間" to Icons.Outlined.EventBusy, "清除全部本機統計" to Icons.Outlined.DeleteForever,
    // ListenBrainz
    "連接 ListenBrainz" to Icons.Outlined.Hub, "ListenBrainz 帳號與同步" to Icons.Outlined.AccountCircle,
    "同步聆聽紀錄" to Icons.Outlined.CloudUpload, "立即同步" to Icons.Outlined.Sync, "更新推薦" to Icons.Outlined.Recommend,
    "斷開帳號，保留待同步資料" to Icons.Outlined.LinkOff, "斷開帳號，刪除待同步資料" to Icons.Outlined.DeleteForever,
    // 通知與背景行為
    "下載完成／失敗通知" to Icons.Outlined.Notifications, "通知權限及系統設定" to Icons.Outlined.Settings,
    // 關於、隱私及維護
    "GitHub 發布專案" to Icons.Outlined.Code, "匯出診斷資料" to Icons.Outlined.BugReport, "匯出設定及播放清單" to Icons.Outlined.Backup,
    "匯入設定及播放清單" to Icons.Outlined.SettingsBackupRestore, "還原設定" to Icons.Outlined.RestartAlt,
    // 音源外掛與音質
    "外掛分類" to Icons.Outlined.Category, "配對歌曲的預設音源" to Icons.Outlined.Source, "音質偏好" to Icons.Outlined.HighQuality,
)

/** The icon for a settings row titled [title]; null for rows that are not settings. */
fun settingIcon(title: String): ImageVector? = settingIcons[title] ?: if(title.startsWith("啟用 ")) Icons.Outlined.Extension else null
