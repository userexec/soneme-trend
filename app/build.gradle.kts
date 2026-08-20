plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.userexec.soneme.trend"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.userexec.soneme.trend"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    val keystorePath = System.getenv("SONEME_KEYSTORE")
    val storePasswordValue = System.getenv("SONEME_STORE_PASSWORD")
    val keyPasswordValue = System.getenv("SONEME_KEY_PASSWORD")
    val releaseSigning = if (!keystorePath.isNullOrBlank() &&
        !storePasswordValue.isNullOrBlank() && !keyPasswordValue.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = storePasswordValue
            keyAlias = "soneme"
            keyPassword = keyPasswordValue
        }
    } else null

    buildTypes {
        getByName("release") {
            releaseSigning?.let { signingConfig = it }
            isMinifyEnabled = false
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
    kotlinOptions { jvmTarget = "17" }
}
