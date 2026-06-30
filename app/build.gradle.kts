import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "me.arnabsaha.airpodscompanion"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.arnabsaha.airpodscompanion"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        create("release") {
            // Local builds read keystore.properties (gitignored); CI reads KEYSTORE_* env vars.
            val propsFile = rootProject.file("keystore.properties")
            val envStore = System.getenv("KEYSTORE_FILE")
            if (propsFile.exists()) {
                val props = Properties()
                propsFile.inputStream().use { props.load(it) }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else if (envStore != null) {
                storeFile = file(envStore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Minify is off for now: the app relies on reflection (hidden APIs, L2CAP socket,
            // removeBond) that R8 would strip without extensive keep rules. Re-enable later
            // with proper rules for a smaller APK.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign only when a keystore is configured; otherwise the release APK stays unsigned.
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    lint {
        // Don't let lintVitalRelease fail the release build — debug already compiles/validates,
        // and a personal sideload release shouldn't be blocked by non-fatal lint findings.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Hidden API bypass (for L2CAP socket creation via reflection)
    implementation(libs.hiddenapibypass)

    // Wearable Data Layer (sync state to Wear OS companion)
    implementation(libs.play.services.wearable)

    // Glance widgets (home screen + lock screen)
    implementation(libs.glance.appwidget)

    // Android Auto
    implementation(libs.car.app)

    // Liquid-glass backdrop blur
    implementation(libs.haze)
    implementation(libs.haze.materials)

    // Testing
    testImplementation("junit:junit:4.13.2")
}
