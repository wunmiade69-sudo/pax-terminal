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
        versionCode = 12
        versionName = "0.4.8"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // The Debian root filesystem is already a gzip-compressed tar archive.
        // Recompressing it in the APK asset pipeline creates a large temporary
        // byte array and caused Gradle Zipflinger to exhaust its Java heap.
        // Store .gz assets as-is; AssetManager will still provide the gzip bytes
        // that the app streams into its private storage and extracts.
        noCompress += "rootfs"
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
