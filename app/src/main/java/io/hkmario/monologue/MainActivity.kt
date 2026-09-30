package io.hkmario.monologue

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.auth.api.identity.Identity
import io.hkmario.monologue.domain.*
import io.hkmario.monologue.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity: ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var message by mutableStateOf<UiEffect?>(null)
    private var exportText: String?=null
    private var translationImport=false
    private var systemAnimations by mutableStateOf(true)
    private val audioPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) {vm.permissionResult(it)}
    private val googleResolution=registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {result ->
        runCatching { Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(result.data) }.onSuccess { auth -> auth.accessToken?.let(vm::driveAuthorized) ?: vm.driveFailed("未有取得授權，請重新連接") }.onFailure { vm.driveFailed("Google 授權未完成；請檢查帳號及 OAuth 設定") }
    }
    private val folderPicker=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->
        if(uri!=null) { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); vm.refreshSystemStatus(); vm.dispatch(UiEvent.Scan) }
    }
    private val lyricPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->if(uri!=null) vm.dispatch(UiEvent.ImportLyrics(uri.toString(),translationImport))}
    private val exportPicker=registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {uri ->
        val text=exportText; exportText=null
        if(uri!=null && text!=null) lifecycleScope.launch { runCatching {withContext(Dispatchers.IO) {contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {it.write(text)} ?: error("無法写入檔案")}}.onSuccess {message=UiEffect.Message("檔案已匯出")}.onFailure {message=UiEffect.Message("匯出失敗：${it.message}")} }
    }
    private val importPicker=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->if(uri!=null) vm.importSettings(uri)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        if(savedInstanceState==null) intent?.data?.takeIf {vm.graph.spotify.accepts(it)}?.let(vm::spotifyCallback)
        lifecycleScope.launch {repeatOnLifecycle(Lifecycle.State.STARTED) {vm.effects.collect {effect -> when(effect) {
            is UiEffect.OpenUrl -> runCatching {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(effect.url)))}.onFailure {message=UiEffect.Message("未有可用瀏覽器")}
            is UiEffect.InstallApk -> installUpdate(effect.path)
            UiEffect.AudioPermission -> audioPermission.launch(if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            UiEffect.GoogleAuthorization -> authorizeDrive()
            UiEffect.PickFolder -> folderPicker.launch(null)
            UiEffect.ImportSettings -> importPicker.launch(arrayOf("application/json","text/plain","application/octet-stream"))
            is UiEffect.Export -> {exportText=effect.text;exportPicker.launch("monologue-${effect.kind}.${if(effect.kind=="statistics") "csv" else if(effect.kind=="settings") "json" else "txt"}")}
            else -> message=effect
        }}}}
        // The logo intro plays once per cold start, not when the activity is recreated (rotation, theme change).
        val coldStart=savedInstanceState==null
        setContent {
            var intro by rememberSaveable { mutableStateOf(coldStart) }
            // On a cold start the intro's first frame goes up before the app is built, so the logo shows as early as possible.
            var appBuilt by remember { mutableStateOf(!coldStart) }
            LaunchedEffect(Unit) { withFrameNanos { }; appBuilt=true }
            val state by vm.state.collectAsStateWithLifecycle()
            val progress=vm.progress.collectAsStateWithLifecycle()
            val lifecycleState by lifecycle.currentStateFlow.collectAsState()
            val dark=when(state.settings.text("theme","paper")) {"dark"->true;"system"->androidx.compose.foundation.isSystemInDarkTheme();else->false}
            SideEffect {
                val bar=if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle=bar,navigationBarStyle=bar)
            }
            MonologueTheme(state.settings) { androidx.compose.foundation.layout.Box {
                if(appBuilt) AppHost(state,progress,vm.vinyl,lifecycleState.isAtLeast(Lifecycle.State.STARTED) && systemAnimations,vm::dispatch,message,{message=null},{entry,index->vm.undoQueue(entry,index)},
                    importLyrics={translation -> translationImport=translation;lyricPicker.launch(arrayOf("text/*","application/octet-stream","application/x-subrip"))},
                    systemSettings={startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))})
                // Over the app, which loads underneath; off when turned off in 設定 or when motion is reduced.
                if(intro && state.settings.bool("introAnimation",true) && !state.settings.bool("reduceMotion") && systemAnimations) IntroScreen(start=appBuilt) { intro=false }
            } }
        }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);intent.data?.takeIf {vm.graph.spotify.accepts(it)}?.let(vm::spotifyCallback)}
    private fun installUpdate(path: String) {
        runCatching {
            val file=java.io.File(path);vm.graph.updates.validateApk(file)
            if(!packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                message=UiEffect.Message("允許此 App 安裝更新後，返回再按安裝")
            } else {
                val uri=androidx.core.content.FileProvider.getUriForFile(this,"$packageName.updates",file)
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
        }.onFailure {message=UiEffect.Message(it.message ?: "更新安裝未完成")}
    }
    override fun onResume() {super.onResume();systemAnimations=Settings.Global.getFloat(contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;vm.refreshSystemStatus()}
    private fun authorizeDrive() {
        if(!BuildConfig.GOOGLE_AUTH_CONFIGURED) {
            android.app.AlertDialog.Builder(this).setTitle("Google Drive 尚未設定").setMessage("此測試版本尚未完成開發方的 Google 登入配置。正式配置後，你只需選擇帳號及同意唯讀存取；無需自行設定 API 或 OAuth。").setPositiveButton("知道了",null).show();return
        }
        // The Drive tab's own intro page explains the read-only access, so Google's sign-in opens directly.
        Identity.getAuthorizationClient(this).authorize(vm.graph.drive.request()).addOnSuccessListener {result ->
            if(result.hasResolution()) result.pendingIntent?.let {googleResolution.launch(IntentSenderRequest.Builder(it.intentSender).build())}
            else result.accessToken?.let(vm::driveAuthorized) ?: vm.driveFailed("Google 未傳回存取權杖")
        }.addOnFailureListener {vm.driveFailed("授權失敗；請核對 Google Play 服務、OAuth 簽署與網絡")}
    }
}
