import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "test.hook.debug"
    compileSdk = 36

    defaultConfig {
        applicationId = "test.hook.debug"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = true
            proguardFiles("proguard-rules.pro")
        }
        named("debug") {
            versionNameSuffix = "-debug-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_21.toString()
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.dexkit)
}