plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.unalivin.kodra"
    compileSdk = 34

    defaultConfig {
        applicationId = "dev.unalivin.kodra"
        minSdk = 21
        // Owner decision: targetSdk 28, distribute via GitHub Releases (not Play Store).
        // compileSdk is 34 to get modern WebView/WindowInsets APIs; target remains 28.
        targetSdk = 28
        versionCode = 1
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release signing via local.properties or env — never commit keystore:
            // storeFile = file(System.getenv("KODRA_KEYSTORE") ?: "")
        }
        getByName("debug") {
            isDebuggable = true
            // WebView debugging only in debug (chrome://inspect)
            buildConfigField("boolean", "ENABLE_WEBVIEW_DEBUGGING", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
        // Stage 3 scaffold — allow missing translations before i18n pass
        disable += setOf("MissingTranslation", "ExtraTranslation")
    }

    // Keep 16 KB page-size ready (Android 15) — NDK r26+ already supports it.
    // ndkVersion = "26.1.10909125"
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.webkit:webkit:1.9.0")
    implementation("com.google.android.material:material:1.11.0")
    // Notification +Insets
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
}
