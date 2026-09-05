import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 起内置 Kotlin 支持，无需 kotlin-android 插件；
    // compose 编译器插件与 serialization 编译器插件仍需显式声明
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release 签名配置：读取项目根目录 keystore.properties（本地与 CI 通用）。
// 该文件与 .keystore 均不入库；CI 由 GitHub Secrets 解码生成。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.jmzs.app"
    // Miuix 0.9.3 构件要求 compileSdk >= 37；
    // API 37 平台包采用 minor 命名（android-37.0），需同时声明 compileSdkMinor
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.jmzs.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    // Compose：与 Miuix 0.9.3 编译环境（CMP 1.11.1 -> androidx 1.11.2）精确对齐
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)

    // Miuix（compose-miuix-ui）组件库
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)

    // Miuix 弹窗依赖的返回手势事件库（App 根组合需提供 NavigationEventDispatcherOwner）
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)

    // 网络与序列化
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
