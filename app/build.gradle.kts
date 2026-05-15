plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// versionCode is supplied by CI as the GitHub Actions run number; default 1 locally.
val runNumber: Int = (project.findProperty("versionCode") as String?)?.toInt() ?: 1

android {
    namespace = "ca.hld.covertart"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.hld.covertart"
        minSdk = 29
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
}
