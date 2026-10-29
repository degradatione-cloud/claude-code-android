import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.degradatione.claudecode"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.degradatione.claudecode"
        minSdk = 35
        targetSdk = 36
        versionCode = 10002
        versionName = "1.0.2"
        resourceConfigurations += listOf("en", "ru")
    }

    // Постоянный ключ подписи: без него Android не даёт обновить приложение поверх старого.
    signingConfigs {
        create("release") {
            val ks = rootProject.file("app-release.p12")
            if (ks.exists()) {
                storeFile = ks
                storeType = "PKCS12"
                storePassword = System.getenv("KEYSTORE_PASS") ?: "ccandroid2026"
                keyAlias = "app"
                keyPassword = System.getenv("KEYSTORE_PASS") ?: "ccandroid2026"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            val cfg = signingConfigs.getByName("release")
            signingConfig = if (cfg.storeFile != null) cfg else signingConfigs.getByName("debug")
        }
        debug {
            val cfg = signingConfigs.getByName("release")
            signingConfig = if (cfg.storeFile != null) cfg else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // SSH-клиент для режима «claude на своём сервере»
    implementation("com.github.mwiede:jsch:2.28.8")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
