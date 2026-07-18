plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "family.tvlink"
    compileSdk = 35

    defaultConfig {
        applicationId = "family.tvlink"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.1"
    }

    flavorDimensions += "device"
    productFlavors {
        create("phone") {
            dimension = "device"
            applicationIdSuffix = ".phone"
        }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
