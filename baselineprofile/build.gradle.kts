plugins {
    // AGP is already on the buildscript classpath (the root build file declares its
    // plugins), so the id is applied without a version here.
    id("com.android.test")
}

// Test-only module that records a Baseline Profile for the app; it is deliberately
// absent from every app build. See README.md in this directory for the generation
// command and for why no profile is committed to the repository.
android {
    namespace = "ovh.litapp.neurhome3.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A test module only resolves variants that exist in the module it targets, so the
    // app's `env` dimension is mirrored here: `prodDebug` records against the shipped
    // application id, `devDebug` against the dev build.
    flavorDimensions.add("env")
    productFlavors {
        create("dev") { dimension = "env" }
        create("prod") { dimension = "env" }
    }

    targetProjectPath = ":app"

    // The generator APK is installed on its own and drives the app under test through
    // UiAutomator; that is what lets BaselineProfileRule record the app's own process.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
