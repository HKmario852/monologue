import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
android {
    namespace = "io.hkmario.monologue"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.hkmario.monologue"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // In-app updates read GitHub Releases of this repository unless the user sets another one.
        buildConfigField("String", "UPDATE_REPOSITORY", "\"" + providers.gradleProperty("updateRepository").orElse("HKmario852/monologue").get() + "\"")
        buildConfigField("boolean", "GOOGLE_AUTH_CONFIGURED", providers.gradleProperty("googleAuthConfigured").orElse("false").get())
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"" + providers.gradleProperty("spotifyClientId").orElse("").get().replace(Regex("[^a-zA-Z0-9]"), "") + "\"")
        // A private-use scheme named after the package needs no website; Spotify accepts it for PKCE apps.
        val spotifyRedirect = providers.gradleProperty("spotifyRedirectUri").orElse("io.hkmario.monologue://spotify/callback").get()
        val spotifyUri = URI(spotifyRedirect)
        require((spotifyUri.scheme == "https" || spotifyUri.scheme == applicationId) && spotifyUri.host != null && spotifyUri.path.startsWith("/") && spotifyUri.query == null && spotifyUri.fragment == null && spotifyUri.userInfo == null)
        buildConfigField("String", "SPOTIFY_REDIRECT_URI", "\"$spotifyRedirect\"")
        manifestPlaceholders["spotifyScheme"] = spotifyUri.scheme
        manifestPlaceholders["spotifyHost"] = spotifyUri.host
        manifestPlaceholders["spotifyPath"] = spotifyUri.path
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    testOptions { unitTests.isReturnDefaultValues = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.media3:media3-exoplayer:1.7.1")
    implementation("androidx.media3:media3-session:1.7.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.7.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
