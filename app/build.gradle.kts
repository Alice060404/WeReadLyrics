plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.alice060404.wereadlyrics"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.alice060404.wereadlyrics"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "0.8.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":core"))
    compileOnly("io.github.libxposed:api:102.0.0")
}
