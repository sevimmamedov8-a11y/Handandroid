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
        versionCode = 10
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.webkit:webkit:1.17.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.28")
}
