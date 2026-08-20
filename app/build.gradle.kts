plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val developmentKeystorePath = providers.environmentVariable("RUDIMENTOR_DEBUG_KEYSTORE")
val developmentKeystorePassword = providers.environmentVariable("RUDIMENTOR_DEBUG_KEYSTORE_PASSWORD")
    .orElse("android")
val developmentKeyAlias = providers.environmentVariable("RUDIMENTOR_DEBUG_KEY_ALIAS")
    .orElse("androiddebugkey")
val developmentKeyPassword = providers.environmentVariable("RUDIMENTOR_DEBUG_KEY_PASSWORD")
    .orElse(developmentKeystorePassword)
val appVersionName = "0.1.0-dev.15"
val appVersionCode = 15

base {
    archivesName.set("RudiMentor-$appVersionName-build-$appVersionCode")
}

android {
    namespace = "com.rudimentor.app"
    compileSdk = 35
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.rudimentor.app"
        minSdk = 27
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-Wall", "-Wextra", "-Werror")
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        debug {
            if (developmentKeystorePath.isPresent) {
                signingConfig = signingConfigs.getByName("debug").apply {
                    storeFile = rootProject.file(developmentKeystorePath.get())
                    storePassword = developmentKeystorePassword.get()
                    keyAlias = developmentKeyAlias.get()
                    keyPassword = developmentKeyPassword.get()
                }
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.material.color.utilities)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.oboe)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
