plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.registratorelezioni"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.registratorelezioni"
        minSdk = 26          // Android 8.0 in su (il Realme 12 è Android 14)
        targetSdk = 34       // Android 14
        versionCode = 2
        versionName = "2.0"
    }

    // Chiave di firma fissa (inclusa nel repo): così ogni APK compilato da
    // GitHub si installa come AGGIORNAMENTO sopra il precedente, senza dover
    // disinstallare l'app (e perdere le registrazioni).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
