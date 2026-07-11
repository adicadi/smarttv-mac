plugins {
    id("com.android.application")
}

android {
    namespace = "com.smarttv.remote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smarttv.remote"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // The single allowed third-party dependency: WebSocket client.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
