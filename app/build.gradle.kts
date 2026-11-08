plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cz.luna.voice"
    compileSdk = 35
    defaultConfig {
        applicationId = "cz.luna.voice"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
