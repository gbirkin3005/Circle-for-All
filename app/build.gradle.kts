plugins {
    id("com.android.application")
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

    buildTypes {
        release {
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
