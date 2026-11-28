import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Secrets are supplied by the build environment; no debug-key fallback for release.
val releaseStore = providers.environmentVariable("EOLSSU_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("EOLSSU_STORE_PASSWORD").orNull
val releaseAlias = providers.environmentVariable("EOLSSU_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("EOLSSU_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStore, releaseStorePassword, releaseAlias, releaseKeyPassword)
val debugApiBaseUrl = providers.gradleProperty("eolssu.debugApiBaseUrl").orElse("https://eolssu.site").get()
val qaLive = providers.gradleProperty("eolssu.qaLive").orElse("false").get().toBooleanStrict()
check(!qaLive || debugApiBaseUrl == "http://127.0.0.1:18180") {
    "Live QA requires the isolated loopback API http://127.0.0.1:18180."
}
val debugLoopbackPort = Regex("http://127\\.0\\.0\\.1:([0-9]{1,5})").matchEntire(debugApiBaseUrl)
    ?.groupValues?.get(1)?.toIntOrNull()
check(debugApiBaseUrl == "https://eolssu.site" || debugLoopbackPort in 1..65535) {
    "Debug API override must be a loopback URL; release always uses production HTTPS."
}
check(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningValues.all { !it.isNullOrBlank() }) {
    "Supply all four EOLSSU_STORE_FILE/STORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD environment variables."
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    testImplementation(libs.junit)
}

android {
    namespace = "com.ssafy.eolssu"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.ssafy.eolssu"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("eolssu.versionCode").orElse("2").get().toInt()
        versionName = providers.gradleProperty("eolssu.versionName").orElse("1.0.0").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (!releaseStore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            if (qaLive) {
                applicationIdSuffix = ".qa"
                versionNameSuffix = "-qa283-live"
            }
            buildConfigField("boolean", "QA_LIVE", qaLive.toString())
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        release {
            buildConfigField("boolean", "QA_LIVE", "false")
            buildConfigField("String", "API_BASE_URL", "\"https://eolssu.site\"")
            isDebuggable = false
            if (!releaseStore.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
            // R8 shrink/obfuscate (S15P31E102-1120). The AAB carries mapping.txt for Play crash retrace.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Same R8 setup as release, debug-signed and installable next to the Play app to check and time it locally.
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
