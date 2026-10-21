import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseSigning = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}

fun signingValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull
        ?: releaseSigning.getProperty(propertyName)

android {
    namespace = "io.github.alice060404.wereadlyrics"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.alice060404.wereadlyrics"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.10.0"
    }

    signingConfigs {
        create("release") {
            storeFile = signingValue("KEYSTORE_FILE", "storeFile")?.let { rootProject.file(it) }
            storePassword = signingValue("KEYSTORE_PASSWORD", "storePassword")
            keyAlias = signingValue("KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
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

val validateReleaseSigningCredentials = tasks.register("validateReleaseSigningCredentials") {
    doLast {
        val fields = mapOf(
            "KEYSTORE_FILE" to "storeFile",
            "KEYSTORE_PASSWORD" to "storePassword",
            "KEY_ALIAS" to "keyAlias",
            "KEY_PASSWORD" to "keyPassword",
        )
        fields.forEach { (environmentName, propertyName) ->
            check(!signingValue(environmentName, propertyName).isNullOrBlank()) {
                "Release signing requires $environmentName or keystore.properties:$propertyName"
            }
        }
        check(rootProject.file(signingValue("KEYSTORE_FILE", "storeFile")!!).isFile) {
            "Release signing keystore does not exist"
        }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(validateReleaseSigningCredentials)
}

dependencies {
    implementation(project(":core"))
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.1.0")
    implementation("io.github.libxposed:service:101.0.0")
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation(kotlin("test-junit"))
}
