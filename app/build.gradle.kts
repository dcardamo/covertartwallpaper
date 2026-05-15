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

// AGP passes -no-jdk to the Kotlin compiler for Android targets, which removes java.awt from
// the compile classpath.  We extract java.desktop.jmod into a jar at configure time and add it
// as testCompileOnly so the AWT-backed preview harness (src/test) can resolve java.awt.* without
// polluting the APK classpath.  At runtime the full JDK is available, so no jvmArgs needed.
val javaHome = File(System.getProperty("java.home")!!)
val jdeskJmod = File(javaHome, "jmods/java.desktop.jmod")
val jdeskJar = layout.buildDirectory.file("jdk-stubs/java-desktop.jar").get().asFile

if (jdeskJmod.exists() && !jdeskJar.exists()) {
    jdeskJar.parentFile.mkdirs()
    // Extract classes/ tree from the jmod and repack as a plain jar.
    exec {
        commandLine(
            File(javaHome, "bin/jmod").absolutePath,
            "extract",
            "--dir", jdeskJar.parentFile.absolutePath + "/jmod-extracted",
            jdeskJmod.absolutePath,
        )
    }
    exec {
        commandLine(
            File(javaHome, "bin/jar").absolutePath,
            "--create",
            "--file", jdeskJar.absolutePath,
            "-C", jdeskJar.parentFile.absolutePath + "/jmod-extracted/classes",
            ".",
        )
    }
}

dependencies {
    testCompileOnly(files(jdeskJar))
}

// Renders the preview harness independently of the full unit-test suite.
// Reuses the debug unit-test classpath so AWT + test-source classes are available.
tasks.register<Test>("runPreviews") {
    description = "Renders sample wallpapers to build/previews/ via the AWT executor."
    group = "verification"
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = debugUnitTest.testClassesDirs
    classpath = debugUnitTest.classpath
    useJUnit()
    filter { includeTestsMatching("*PreviewHarnessTest") }
    systemProperty(
        "previews.outputDir",
        layout.buildDirectory.dir("previews").get().asFile.absolutePath,
    )
}
