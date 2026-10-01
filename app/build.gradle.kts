import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
val tmdbToken = localProperties.getProperty("TMDB_TOKEN", "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace="com.hobitv.app"
    compileSdk=37
    defaultConfig {
        applicationId="com.hobitv.app"
        minSdk=23
        targetSdk=37
        versionCode=4
        versionName="4.0.0"
        buildConfigField("String", "TMDB_TOKEN", "\"$tmdbToken\"")
    }
    buildFeatures {
        compose=true
        buildConfig=true
    }
}

dependencies {
    val composeBom=platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("androidx.core:core-ktx:1.17.0")
}
