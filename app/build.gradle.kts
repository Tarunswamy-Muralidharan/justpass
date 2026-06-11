import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.justpass.app"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.justpass.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase creds for HumanBenchmark games leaderboard. Reads from
        // local.properties (NOT committed). Falls back to empty string so
        // ScoresApi short-circuits + local play still works offline.
        val localProps = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) FileInputStream(f).use { load(it) }
        }
        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${localProps.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\""
        )

        // Cloudinary credentials for the QPapers feature. Both come from
        // local.properties (gitignored) so the cloud name + preset name
        // aren't baked into the public repo. Falling back to empty strings
        // means QPaperRepository can detect missing config and disable
        // the feature gracefully rather than crashing.
        buildConfigField(
            "String", "CLOUDINARY_CLOUD_NAME",
            "\"${localProps.getProperty("CLOUDINARY_CLOUD_NAME", "")}\""
        )
        buildConfigField(
            "String", "CLOUDINARY_UPLOAD_PRESET",
            "\"${localProps.getProperty("CLOUDINARY_UPLOAD_PRESET", "")}\""
        )
    }

    buildFeatures {
        // Generates BuildConfig.DEBUG / BuildConfig.VERSION_NAME etc.
        // Off by default in AGP 8+ — needed by Crashlytics opt-out toggle.
        buildConfig = true
        compose = true
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val props = Properties()
                props.load(FileInputStream(keystorePropertiesFile))
                val ksFile = rootProject.file(props.getProperty("storeFile"))
                if (ksFile.exists()) {
                    storeFile = ksFile
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
            val releaseConfig = signingConfigs.getByName("release")
            signingConfig = if (releaseConfig.storeFile?.exists() == true) {
                releaseConfig
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

// Compose compiler diagnostic reports. Gated by `-Pcompose.metrics=true` so
// dev/CI builds stay fast; opt-in run:
//   ./gradlew :app:assembleRelease -Pcompose.metrics=true
// Outputs land in app/build/compose_compiler/ — `*-classes.txt` shows
// stability inference, `*-composables.txt` shows skippable%/restartable%.
if (project.findProperty("compose.metrics") == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Security
    implementation(libs.androidx.security.crypto)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Glance for Widget
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation("com.google.firebase:firebase-config")
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.storage)

    // Apache POI (Excel parsing)
    implementation(libs.poi)
    implementation(libs.poi.ooxml)

    // Liquid Glass Effects
    implementation(libs.liquid)

    // ML Kit Text Recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // LiteRT-LM — Google's on-device LLM inference (GPU/NPU acceleration)
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")

    // Google Mobile Ads (AdMob)
    implementation("com.google.android.gms:play-services-ads:25.2.0")

    // Play In-App Updates (force + flexible flows for Play Store installs)
    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    // Play In-App Review (rating prompt — Google quota-managed)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}