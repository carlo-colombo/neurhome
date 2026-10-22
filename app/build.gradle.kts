import java.net.InetAddress

plugins {
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    // Coverage is measurement-only for now: CI runs :app:koverXmlReportDevDebug and
    // :app:koverHtmlReportDevDebug and uploads both as an artifact, with no
    // verification rule (and therefore no threshold) configured here. Once that
    // report has established a baseline, add a `kover { reports { total { verify { ... } } } }`
    // rule and make the CI step blocking again by dropping its continue-on-error.
    alias(libs.plugins.kover)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Dev-only application/version suffix. CI passes -PdevHostSuffix=<constant> so
// the dev variant (and therefore the remote build cache) is identical across
// machines; locally we keep the historical short-hostname behaviour. The DNS
// lookup is fully guarded so it can never fail configuration or break the
// configuration cache.
val devHostSuffix: String = providers.gradleProperty("devHostSuffix").orNull
    ?.takeIf { it.isNotBlank() }
    ?: runCatching {
        InetAddress.getLocalHost().canonicalHostName
            .replace("-", "_")
            .replace(Regex("""(\.|^)[0-9]"""), "a")
            .split(".")
            .first()
    }.getOrNull()?.takeIf { it.isNotBlank() }
    ?: "local"

// Robolectric resolves its own android-all runtime: LegacyDependencyResolver falls back to a live
// Maven fetch into ~/.m2 unless it is pointed at a local jar, and a release run failed when that
// fetch returned 404 while a sandbox was initialising. Declaring the artifact here puts the
// download through ordinary Gradle dependency resolution (and Gradle's dependency caches), and
// `copyRobolectricAndroidAll` stages the jar flat, which is the layout Robolectric's
// LocalDependencyResolver looks in. The version is the one Robolectric pairs with the sdk=37 pin
// (see gradle/libs.versions.toml and app/src/test/resources/robolectric.properties).
val robolectricAndroidAll = configurations.create("robolectricAndroidAll") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

val robolectricAndroidAllDir = layout.buildDirectory.dir("robolectric-android-all")

val copyRobolectricAndroidAll = tasks.register<Copy>("copyRobolectricAndroidAll") {
    description = "Stages Robolectric's android-all runtime jar for offline unit tests."
    from(robolectricAndroidAll)
    into(robolectricAndroidAllDir)
}

// The app's own license and the dependency notices are staged into a generated assets directory
// rather than committed under app/src/main/assets: the root LICENSE/NOTICE/THIRD_PARTY_LICENSES.md
// stay the single source of truth, so a packaged copy can never drift from them. The Variant-API
// wiring below carries the task dependency, so every asset merge waits for the copy.
// `LicenseAssetsTest` pins that they actually reach the merged assets.
val licenseAssetsDir = layout.buildDirectory.dir("generated/license-assets")

val stageLicenseAssets = tasks.register<Copy>("stageLicenseAssets") {
    description = "Stages LICENSE, NOTICE and THIRD_PARTY_LICENSES.md into the APK's assets."
    into(licenseAssetsDir)
    from(rootProject.file("LICENSE")) { into("licenses") }
    from(rootProject.file("NOTICE")) { into("licenses") }
    from(rootProject.file("THIRD_PARTY_LICENSES.md")) { into("licenses") }
}

dependencies {
    // Deliberately not `testImplementation`: the android-all jar must not be on the test classpath,
    // it is only the runtime Robolectric loads for the Android classes it shadows.
    add(robolectricAndroidAll.name, libs.android.all.instrumented)
}

android {
    namespace = "ovh.litapp.neurhome3"
    compileSdk = 37

    defaultConfig {
        applicationId = "ovh.litapp.neurhome3"
        minSdk = 34
        targetSdk = 37
        versionCode = 300
        versionName = findProperty("android.versionName").toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Surfaced on the About screen so the app can state its own license and point at the
        // Corresponding Source for the exact build (GPLv3 section 6).
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/carlo-colombo/neurhome\"")
        buildConfigField("String", "LICENSE_IDENTIFIER", "\"GPL-3.0-or-later\"")
    }

    buildTypes {
        getByName("release") {
            // Enables code shrinking, obfuscation, and optimization for only
            // your project's release build type. Make sure to use a build
            // variant with `isDebuggable=false`.
            isMinifyEnabled = true

            // Enables resource shrinking, which is performed by the
            // Android Gradle plugin.
            isShrinkResources = true

            proguardFiles(
                // Includes the default ProGuard rules files that are packaged with
                // the Android Gradle plugin. To learn more, go to the section about
                // R8 configuration files.
                getDefaultProguardFile("proguard-android-optimize.txt"),

                // Includes a local, custom Proguard rules file
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions.add("env")
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev-$devHostSuffix"
            resValue("string", "app_name", "Neurhome3 (Dev $devHostSuffix)")
        }

        create("prod") {
            dimension = "env"
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    buildFeatures {
        compose = (true)
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric reads the merged manifest and resources through the unit-test classpath.
            isIncludeAndroidResources = true
            // Refuse the live Maven fetch and read the staged jar instead. The runtime is not on the
            // test classpath, so its version is declared as an input: otherwise a bump of the staged
            // runtime would leave the test task up to date and silently run against the previous jar.
            all {
                it.dependsOn(copyRobolectricAndroidAll)
                // The API 36+ android-all runtime initialises ApplicationSharedMemory, which reflects
                // into jdk.internal.access. JDK 21 does not export that package to the unnamed module,
                // so without this every test fails in AndroidTestEnvironment.setUpApplicationState
                // with "Failed to interact with raw FileDescriptor internals".
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty(
                    "robolectric.dependency.dir",
                    robolectricAndroidAllDir.get().asFile.absolutePath,
                )
                it.inputs.property(
                    "robolectricAndroidAll",
                    libs.android.all.instrumented.get().version,
                )
            }
        }
    }
    // Treat lint warnings as errors so new issues cannot slip in; the checked-in
    // baseline records the violations that already existed when the gate landed.
    lint {
        warningsAsErrors = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

// AGP 9 refuses Provider-backed task outputs in the legacy `sourceSets` API ("You cannot add
// Provider instances to the Android SourceSet API"), so the generated assets directory is wired
// through the Variant API, which carries the task dependency for every variant. `Copy` exposes its
// `destinationDirectory` as a DirectoryProperty, which is what `addGeneratedSourceDirectory` needs.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageLicenseAssets) {
            it.destinationDirectory
        }
    }
}

composeCompiler {
    // `data.Application` holds platform values (Drawable, Intent, LauncherActivityInfo) the
    // compiler cannot prove stable, so application rows would compare by instance instead of by
    // value. app/compose_stability.conf declares the type stable instead and records the promise
    // that makes.
    stabilityConfigurationFiles.add(layout.projectDirectory.file("compose_stability.conf"))
}

dependencies {
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.datastore.preferences)


    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.room.runtime)

    implementation(libs.accompanist.drawablePainter)
    implementation(libs.accompanist.permissions)


    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation.compose)

    // Installs the Baseline Profile embedded in the APK, for non-debuggable builds.
    // Nothing is embedded until a profile is committed under src/main/baseline-prof.txt,
    // so this is a no-op today; see baselineprofile/README.md.
    implementation(libs.androidx.profileinstaller)


    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // To use Kotlin Symbol Processing (KSP)
    ksp(libs.androidx.room.compiler)

    // optional - Kotlin Extensions and Coroutines support for Room
    implementation(libs.androidx.room.ktx)

    implementation(libs.geohash)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
