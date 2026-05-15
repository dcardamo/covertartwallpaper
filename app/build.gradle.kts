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

    // Release signing is supplied by CI via -P properties; absent locally.
    val signingKeystore = project.findProperty("signingKeystore") as String?
    if (signingKeystore != null) {
        val required = listOf("signingStorePassword", "signingKeyAlias", "signingKeyPassword")
        val missing = required.filter { project.findProperty(it) == null }
        check(missing.isEmpty()) {
            "signingKeystore is set but the following Gradle properties are missing: $missing"
        }
    }
    signingConfigs {
        if (signingKeystore != null) {
            create("release") {
                storeFile = file(signingKeystore)
                storePassword = project.findProperty("signingStorePassword") as String?
                keyAlias = project.findProperty("signingKeyAlias") as String?
                keyPassword = project.findProperty("signingKeyPassword") as String?
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
            if (signingKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// AGP passes -no-jdk to the Kotlin compiler for Android variants, which strips
// java.awt from the (test) compile classpath. The host-side preview harness in
// src/test needs java.awt, so we extract the JDK's java.desktop module into a
// jar and put it on the test-only compile classpath. testCompileOnly => never
// reaches the APK. Declared inputs/outputs make this correct across JDK changes
// and `clean`, and keep configuration caching intact.
val extractJavaDesktop by tasks.registering {
    val javaHome = File(System.getProperty("java.home")!!)
    val jmod = File(javaHome, "jmods/java.desktop.jmod")
    val outJar = layout.buildDirectory.file("jdk-stubs/java-desktop.jar")
    onlyIf { jmod.exists() }
    inputs.file(jmod)
    outputs.file(outJar)
    doLast {
        val extracted = layout.buildDirectory.dir("jdk-stubs/jmod-extracted").get().asFile
        extracted.deleteRecursively()
        exec {
            commandLine(File(javaHome, "bin/jmod").path, "extract",
                "--dir", extracted.path, jmod.path)
        }
        exec {
            commandLine(File(javaHome, "bin/jar").path, "--create",
                "--file", outJar.get().asFile.path,
                "-C", File(extracted, "classes").path, ".")
        }
    }
}

dependencies {
    testCompileOnly(files(extractJavaDesktop))
}

// Renders the preview harness independently of the full unit-test suite.
// Reuses the debug unit-test classpath so AWT + test-source classes are available.
// No declared outputs / always re-runs intentionally — this is an iterate-on-visuals task.
tasks.register<Test>("runPreviews") {
    description = "Renders sample wallpapers to build/previews/ via the AWT executor."
    group = "verification"
    val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
    testClassesDirs = files(debugUnitTest.map { it.testClassesDirs })
    classpath = files(debugUnitTest.map { it.classpath })
    useJUnit()
    filter { includeTestsMatching("*PreviewHarnessTest") }
    systemProperty(
        "previews.outputDir",
        layout.buildDirectory.dir("previews").get().asFile.absolutePath,
    )
}
