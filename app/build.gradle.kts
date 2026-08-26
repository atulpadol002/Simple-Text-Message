plugins {
        alias(libs.plugins.android.application)
        alias(libs.plugins.kotlin.compose)
        alias(libs.plugins.google.services)
        alias(libs.plugins.firebase.crashlytics)

}

val umpDebugGeography = providers.gradleProperty("umpDebugGeography")
    .orNull
    ?.trim()
    ?.uppercase()
    ?.takeIf { it in setOf("DISABLED", "EEA", "REGULATED_US_STATE", "OTHER") }
    ?: "DISABLED"
val umpTestDeviceHash = providers.gradleProperty("umpTestDeviceHash")
    .orNull
    ?.trim()
    ?.uppercase()
    ?.takeIf { it.matches(Regex("[A-F0-9]+")) }
    .orEmpty()
val umpResetTestState = providers.gradleProperty("umpResetTestState")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

android {
    namespace = "com.ap.simpletextmessage"

    compileSdk = 37

    defaultConfig {
        applicationId = "com.ap.simpletextmessage"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"$umpDebugGeography\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"$umpTestDeviceHash\"")
            buildConfigField("boolean", "UMP_RESET_TEST_STATE", umpResetTestState.toString())
        }
        release {
            buildConfigField("String", "UMP_DEBUG_GEOGRAPHY", "\"DISABLED\"")
            buildConfigField("String", "UMP_TEST_DEVICE_HASH", "\"\"")
            buildConfigField("boolean", "UMP_RESET_TEST_STATE", "false")
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.config)
    implementation(libs.google.mobile.ads)
    implementation(libs.google.ump)
    implementation(libs.google.play.review)
    implementation("com.android.billingclient:billing:9.1.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
