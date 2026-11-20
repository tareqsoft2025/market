plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Version is injected by CI from the git tag (v1.2.3 -> name "1.2.3", code 10203).
// Each published release MUST have a higher versionCode than the previous one,
// otherwise Android refuses to install it as an update.
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1

// Release signing comes from environment variables (GitHub Secrets in CI).
// The SAME keystore must be used for every release forever, or updates will
// fail and users would have to uninstall (losing their data).
val keystorePath: String? = System.getenv("MARKET_KEYSTORE_FILE")

android {
    namespace = "com.tareqsoft.market"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tareqsoft.market"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Used by the in-app updater to look for new releases.
        buildConfigField("String", "UPDATE_REPO", "\"tareqsoft2025/market\"")
        buildConfigField(
            "String", "UPDATE_MANIFEST_URL",
            "\"https://raw.githubusercontent.com/tareqsoft2025/market/main/dist/update.json\"",
        )
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("MARKET_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MARKET_KEY_ALIAS")
                keyPassword = System.getenv("MARKET_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Lets a test build live next to the real app without touching its data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    // Database schema history. Committed to git so every future version can
    // migrate old data instead of deleting it.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.zxing.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
