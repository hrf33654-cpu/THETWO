import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseApiBaseUrl = providers.gradleProperty("thetwo.releaseApiBaseUrl")
    .orElse(providers.environmentVariable("THETWO_RELEASE_API_BASE_URL"))
    .orElse(localProperties.getProperty("thetwo.releaseApiBaseUrl") ?: "https://example.invalid/")
    .get()
val easyArLicenseKey = providers.gradleProperty("thetwo.easyArLicenseKey")
    .orElse(providers.environmentVariable("THETWO_EASYAR_LICENSE_KEY"))
    .orElse(localProperties.getProperty("thetwo.easyArLicenseKey") ?: "")
    .get()

android {
    namespace = "com.thetwo.app"
    buildToolsVersion = "36.1.0"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.thetwo.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"http://111.231.14.253/\"")
            buildConfigField("String", "EASYAR_LICENSE_KEY", "\"${easyArLicenseKey}\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"${releaseApiBaseUrl}\"")
            buildConfigField("String", "EASYAR_LICENSE_KEY", "\"${easyArLicenseKey}\"")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.filament.android)
    implementation(libs.filament.gltfio.android)
    implementation(libs.filament.utils.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
