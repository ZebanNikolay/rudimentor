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

// Escape hatch for a throwaway local build (a fresh emulator, a machine without the keystore):
// `./gradlew assembleDebug -Prudimentor.localDebugSigning=true`.
val allowLocalDebugSigning = providers.gradleProperty("rudimentor.localDebugSigning")
    .map(String::toBoolean)
    .orElse(false)
val appVersionName = "0.1.0-dev.49"
val appVersionCode = 49

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
            } else {
                // Without the shared keystore Gradle quietly falls back to its own generated
                // debug key. Such an APK installs only on a clean device: over an existing
                // RudiMentor it fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE and the only way
                // out is uninstalling the app together with the learner's progress. So the
                // build breaks instead — at packaging time, to keep IDE sync working.
                tasks.matching { it.name == "packageDebug" }.configureEach {
                    doFirst {
                        if (!allowLocalDebugSigning.get()) {
                            throw GradleException(
                                "RUDIMENTOR_DEBUG_KEYSTORE is not set, so this APK would be " +
                                    "signed with a throwaway debug key and could not be " +
                                    "installed over an existing RudiMentor. Export the shared " +
                                    "keystore (see the rudimentor-build skill), or pass " +
                                    "-Prudimentor.localDebugSigning=true for a throwaway build.",
                            )
                        }
                    }
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
