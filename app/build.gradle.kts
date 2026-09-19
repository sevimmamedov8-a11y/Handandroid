plugins {
    id("com.android.application")
}

android {
    namespace = "com.handar.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.handar.browser"
        minSdk = 24
        targetSdk = 36
        versionCode = 13
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            // Personal/test build: sign the release APK with the ephemeral Android debug key
            // available on the GitHub runner so the artifact is directly installable.
            signingConfig = signingConfigs.getByName("debug")
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.webkit:webkit:1.17.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.28")
}
