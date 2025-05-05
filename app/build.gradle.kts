import java.net.InetAddress

plugins {
    alias(libs.plugins.compose.compiler)

    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

val host = InetAddress.getLocalHost().canonicalHostName.replace("-","_").replace(Regex("""(\.|^)[0-9]"""), "a")

android {
    namespace = "ovh.litapp.neurhome3"
    compileSdk = 35

    defaultConfig {
        applicationId = "ovh.litapp.neurhome3"
        minSdk = 34
        targetSdk = 34
        versionCode = 300
        versionName = findProperty("android.versionName").toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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
            applicationIdSuffix = (".dev.${host}")
            versionNameSuffix = ("-dev-${host}")
        }

        create("prod") {
            dimension = "env"
        }
    }
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = (true)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.datastore.preferences)


    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)

    implementation(libs.glide)
    implementation(libs.glide.compose)
    ksp(libs.glide.ksp)

    implementation(libs.accompanist.drawablePainter)
    implementation(libs.accompanist.permissions)


    implementation(libs.androidx.appcompat.resources)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation.compose)


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

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
