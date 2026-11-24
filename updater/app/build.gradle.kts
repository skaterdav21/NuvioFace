plugins {
    id("com.android.application")
}

// Signing: same key as Nuvio Face (passed in by CI), so both come from one place you trust.
val storeFilePath = providers.gradleProperty("updater.storeFile").orNull
val storePass = providers.gradleProperty("updater.storePassword").orNull
val keyAliasName = providers.gradleProperty("updater.keyAlias").orNull

android {
    namespace = "com.dave.nuviofaceupdater"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dave.nuviofaceupdater"
        minSdk = 23
        targetSdk = 36
        versionCode = providers.gradleProperty("updater.versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("updater.versionName").orNull ?: "dev"
    }

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (storeFilePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
