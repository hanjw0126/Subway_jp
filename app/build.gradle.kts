import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(prop: String, env: String): String = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop) ?: ""

android {
    namespace = "io.github.jpsubway.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.jpsubway.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0"
        // 비어 있으면 데모 시간표 모드. 사용자가 앱 설정에서 직접 토큰을 입력할 수도 있습니다.
        buildConfigField("String", "ODPT_CONSUMER_KEY", "\"${secret("odpt.consumerKey", "ODPT_CONSUMER_KEY")}\"")
    }

    signingConfigs {
        create("release") {
            val store = secret("release.storeFile", "ANDROID_KEYSTORE_PATH")
            if (store.isNotBlank()) {
                storeFile = file(store)
                storePassword = secret("release.storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = secret("release.keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = secret("release.keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val rel = signingConfigs.getByName("release")
            signingConfig = if (rel.storeFile != null) rel else signingConfigs.getByName("debug")
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
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += "MissingTranslation"
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
