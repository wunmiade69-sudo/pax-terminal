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
        versionCode = 7
        versionName = "0.4.3"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.28.0")
}
