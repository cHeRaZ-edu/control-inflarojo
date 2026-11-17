plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.edu.controlinflarojo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edu.controlinflarojo"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        // Llave fija en el repo para que cada Release se pueda instalar encima del anterior.
        // Es una app personal sideloaded; si algún día va a Play Store, usa una llave en secrets.
        create("release") {
            storeFile = file("control.keystore")
            storePassword = "controltv"
            keyAlias = "control"
            keyPassword = "controltv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sin dependencias extra: solo framework de Android + stdlib de Kotlin.
