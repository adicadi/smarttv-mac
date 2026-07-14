import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing lives outside version control (see keystore.properties in
// .gitignore); local dev builds without it still assemble fine, just unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) load(file.inputStream())
}

android {
    namespace = "com.adicadi.smarttv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.adicadi.smarttv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProperties.containsKey("storeFile")) {
                signingConfig = signingConfigs.getByName("release")
            }
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
