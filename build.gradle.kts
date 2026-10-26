// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9 по умолчанию тянет KGP 2.2.10 для built-in Kotlin; здесь поднимаем до версии из каталога
        classpath(libs.kotlin.gradle.plugin)
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
}