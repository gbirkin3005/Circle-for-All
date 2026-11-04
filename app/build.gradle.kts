plugins {
    id("com.android.application")
}

import java.util.Properties

/**
 * Ключ подписи релиза. Лежит вне репозитория: и сам файл, и пароли к нему —
 * это то, чем подтверждается, что обновление выпустил тот же автор.
 * Если файла нет, сборка не падает: релиз просто соберётся неподписанным.
 */
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.gbirkin3005.circleapp"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.gbirkin3005.circleapp"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (signing.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")

                enableV1Signing = false
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
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
}

dependencies {
    // Обход hidden API restrictions для вызовов IVoiceInteractionManagerService
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
}
