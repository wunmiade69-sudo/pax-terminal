plugins {
    id("com.android.application")
}

android {
    namespace = "com.paxterminal.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paxterminal.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
