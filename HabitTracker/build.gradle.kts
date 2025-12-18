
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.google.gms.google-services")
    alias(libs.plugins.kotlin.compose)
}

// Читаем local.properties файл
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

// Разрешаем использование ksp с Room
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Единый источник versionName для использования и в defaultConfig, и в имени APK
val appVersionName = "1.1.1"

android {
    namespace = "com.habittracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.habittracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // FCM Server Key для отправки push-уведомлений
        // Читаем из local.properties, переменной окружения или используем дефолт
        val fcmServerKey = localProperties.getProperty("FCM_SERVER_KEY") 
            ?: System.getenv("FCM_SERVER_KEY") 
            ?: "YOUR_FCM_SERVER_KEY_HERE"
        buildConfigField("String", "FCM_SERVER_KEY", "\"$fcmServerKey\"")
        
        // Дополнительные Firebase конфигурации из local.properties
        val firebaseProjectId = localProperties.getProperty("FIREBASE_PROJECT_ID") ?: "habitttracker-505b6"
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
    }

    // Release signing via local.properties or environment variables
    val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE") ?: System.getenv("RELEASE_STORE_FILE")
    val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD") ?: System.getenv("RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS") ?: System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD") ?: System.getenv("RELEASE_KEY_PASSWORD")
    // Resolve keystore path robustly (supports absolute and relative to root/app)
    val resolvedStoreFile = run {
        if (releaseStoreFile.isNullOrBlank()) null else {
            val candidates = listOf(
                // As provided
                file(releaseStoreFile),
                // Relative to app module
                File(projectDir, releaseStoreFile),
                // Relative to root project
                File(rootProject.projectDir, releaseStoreFile),
                // If mistakenly starts with '/', try trimming it relative to root and app
                File(rootProject.projectDir, releaseStoreFile.removePrefix("/")),
                File(projectDir, releaseStoreFile.removePrefix("/"))
            )
            candidates.firstOrNull { it.exists() }
        }
    }
    val hasReleaseSigning = resolvedStoreFile != null &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            if (!hasReleaseSigning) {
                throw GradleException("Release signing is not configured. Please set RELEASE_* in local.properties or env and ensure keystore path is correct.")
            }
            storeFile = resolvedStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Keep same applicationId to match google-services.json, but change label
            resValue("string", "app_name", "HabitTracker Debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Strictly use release signing
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isReturnDefaultValues = true  // Возвращать default значения для Android методов вместо исключений
            isIncludeAndroidResources = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Исключаем конфликтующие META-INF файлы
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/LICENSE"
            excludes += "/META-INF/LICENSE.txt"
            excludes += "/META-INF/license.txt"
            excludes += "/META-INF/NOTICE"
            excludes += "/META-INF/NOTICE.txt"
            excludes += "/META-INF/notice.txt"
            excludes += "/META-INF/ASL2.0"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

// Автопереименование APK после упаковки (без Variant API)
val apkDateProvider = providers.provider { SimpleDateFormat("yyyyMMdd").format(Date()) }

tasks.register("renameApkDebug") {
    doLast {
        val dateStr = apkDateProvider.get()
        val srcFile = layout.buildDirectory.file("outputs/apk/debug/HabitTracker-debug.apk").get().asFile
        val destFile = layout.buildDirectory.file("outputs/apk/debug/HabitTracker-debug-$appVersionName-$dateStr.apk").get().asFile
        
        if (srcFile.exists()) {
            // Добавляем версию и дату к имени файла
            srcFile.renameTo(destFile)
            println("APK renamed: ${srcFile.name} -> ${destFile.name}")
        }
    }
}

tasks.register("renameApkRelease") {
    doLast {
        val dateStr = apkDateProvider.get()
        val srcFile = layout.buildDirectory.file("outputs/apk/release/HabitTracker-release.apk").get().asFile
        val destFile = layout.buildDirectory.file("outputs/apk/release/HabitTracker-release-$appVersionName-$dateStr.apk").get().asFile
        
        if (srcFile.exists()) {
            // Добавляем версию и дату к имени файла
            srcFile.renameTo(destFile)
            println("APK renamed: ${srcFile.name} -> ${destFile.name}")
        }
    }
}

// Правим output-metadata.json, чтобы он указывал на новое имя файла
tasks.register("fixDebugApkMetadata") {
    dependsOn("renameApkDebug")
    doLast {
        val dateStr = apkDateProvider.get()
        val meta = layout.buildDirectory.file("outputs/apk/debug/output-metadata.json").get().asFile
        if (meta.exists()) {
            val desired = "HabitTracker-debug-$appVersionName-$dateStr.apk"
            val content = meta.readText()
            val updated = content.replace(Regex("\"outputFile\"\\s*:\\s*\"[^\"]+\""), "\"outputFile\":\"$desired\"")
            meta.writeText(updated)
        }
    }
}

tasks.register("fixReleaseApkMetadata") {
    dependsOn("renameApkRelease")
    doLast {
        val dateStr = apkDateProvider.get()
        val meta = layout.buildDirectory.file("outputs/apk/release/output-metadata.json").get().asFile
        if (meta.exists()) {
            val desired = "HabitTracker-release-$appVersionName-$dateStr.apk"
            val content = meta.readText()
            val updated = content.replace(Regex("\"outputFile\"\\s*:\\s*\"[^\"]+\""), "\"outputFile\":\"$desired\"")
            meta.writeText(updated)
        }
    }
}

tasks.whenTaskAdded {
    if (name == "packageDebug") finalizedBy("renameApkDebug", "fixDebugApkMetadata")
    if (name == "packageRelease") finalizedBy("renameApkRelease", "fixReleaseApkMetadata")
    if (name == "createDebugApkListingFileRedirect") dependsOn("fixDebugApkMetadata")
    if (name == "createReleaseApkListingFileRedirect") dependsOn("fixReleaseApkMetadata")
}

// Когда выполняем installDebug, явно объявляем зависимость от задач переименования и фикса метаданных,
// чтобы избежать проблем Gradle с неявными зависимостями.
tasks.whenTaskAdded {
    if (name == "installDebug") dependsOn("renameApkDebug", "fixDebugApkMetadata")
}

dependencies {

    // Compose BOM - обязательно в начале
    implementation(platform(libs.androidx.compose.bom))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose UI
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    
    // Room database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Network
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    
    // Image loading
    implementation(libs.coil.compose)
    
    // FlowLayout from Accompanist
    implementation(libs.accompanist.flowlayout)
    // Compose Material (M2) for pull-refresh APIs
    implementation(libs.androidx.material)

    // Calendar
    implementation(libs.calendar.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    
    // DateTime
    implementation(libs.kotlinx.datetime)
    
    // Java 8+ API desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)
    // App Check (Debug provider for dev/testing)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    
    // Google Auth для FCM OAuth 2.0
    implementation(libs.google.auth.oauth2)
    
    // Credentials (нужны для Auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}