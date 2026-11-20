plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val run = (System.getenv("RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.cpd360.ambulancerun"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cpd360.ambulancerun"
        minSdk = 26
        targetSdk = 36
        versionCode = run
        versionName = "1.0.$run"
    }

    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("ci/debug-keystore.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
}
