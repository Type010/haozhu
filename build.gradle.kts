// 顶层构建文件：仅声明插件版本，不在此应用
plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 起内置 Kotlin 支持，不再需要 kotlin-android 插件
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
