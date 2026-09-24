// ============================================================
//  GPDb Android — app/build.gradle.kts
//  Kotlin + Jetpack Compose + Room + Coil 依赖配置
// ============================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)                 // Room annotation processor
}

android {
    namespace = "com.gpdb.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gpdb.android"
        minSdk = 26                          // Android 8.0+，覆盖 97%+ 活跃设备
        targetSdk = 35
        versionCode = 270
        versionName = "2.7.0"

        // Room schema export 目录（方便版本迁移审计）
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    // ── Kotlin 核心 ──────────────────────────────────────────
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)

    // ── Jetpack Compose BOM (统一版本，防止依赖冲突) ─────────
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)   // 扩展图标集

    // ── Activity & Navigation ────────────────────────────────
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ── Room Database（SQLite WAL 外挂模式）──────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)              // Flow/coroutine 扩展
    ksp(libs.androidx.room.compiler)

    // ── Coil 图片加载（含自定义 Fetcher 支持）───────────────
    implementation(libs.coil.compose)
    implementation(libs.coil.core)

    // ── Accompanist（权限）───────────────────────────────────
    implementation(libs.accompanist.permissions)

    // ── DataStore（持久化用户设置，如挂载路径）───────────────
    implementation(libs.androidx.datastore.preferences)

    // ── Splashscreen API ─────────────────────────────────────
    implementation(libs.androidx.core.splashscreen)

    // ── Debug 工具 ───────────────────────────────────────────
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ── 测试 ─────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
