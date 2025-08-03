import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.collektive)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.taskTree)
}

tasks.withType<Detekt>().configureEach {
    exclude("**/ui/theme/**")
}

android {
    namespace = "it.unibo.collektive"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.unibo.collektive"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources.excludes += "META-INF/*.md"
        resources.excludes += "META-INF/INDEX.LIST"
        resources.excludes += "META-INF/io.netty.versions.properties"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        compileOptions {
            allWarningsAsErrors = true
            freeCompilerArgs += listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi")
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.collektive.dsl.jvm)
    implementation(libs.collektive.stdlib)
    implementation(libs.bundles.serialization)
    implementation(libs.bundles.hivemq)
    implementation(libs.bundles.logging)
    implementation(libs.kotlinx.datetime)
    implementation(libs.mktt)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.play.services.location)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Testing Dependencies (unit test)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.junit.v130)

    // AndroidX Test - Instrumented testing
    androidTestImplementation(libs.androidx.junit.v130)
    androidTestImplementation(libs.androidx.core.v150)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.rules)
    androidTestImplementation(libs.androidx.espresso.core.v350)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.navigation.compose.v277)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.junit.jupiter)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Debug-only dependencies
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation(kotlin("test"))
}
