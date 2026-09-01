plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
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
// The release key that signs the App Bundle uploaded to Google Play. Google holds the
// real app signing key under Play App Signing; this is only the upload key, so it can
// be reset by Google if it is ever lost. It never lives in this repository.
val uploadKeystorePath = providers.environmentVariable("RUDIMENTOR_UPLOAD_KEYSTORE")
val uploadKeystorePassword = providers.environmentVariable("RUDIMENTOR_UPLOAD_KEYSTORE_PASSWORD")
val uploadKeyAlias = providers.environmentVariable("RUDIMENTOR_UPLOAD_KEY_ALIAS")
    .orElse("rudimentor-upload")
val uploadKeyPassword = providers.environmentVariable("RUDIMENTOR_UPLOAD_KEY_PASSWORD")
    .orElse(uploadKeystorePassword)

// Versioning: `versionName` is semver, `versionCode` is a plain monotonic counter that
// keeps rising across every build regardless of the semver channel. Play only requires
// monotonicity, and a counter survives building the same version twice.
//   1.0.0-dev.N  sandbox build installed on the developer's phone
//   1.0.0-rc.N   candidate uploaded to the internal / closed testing track
//   1.0.0        production
val appVersionName = "1.0.0-dev.53"
val appVersionCode = 53

base {
    archivesName.set("RudiMentor-$appVersionName-build-$appVersionCode")
}

android {
    namespace = "com.rudimentor.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.rudimentor.app"
        minSdk = 27
        targetSdk = 36
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (uploadKeystorePath.isPresent) {
                signingConfig = signingConfigs.create("upload").apply {
                    storeFile = rootProject.file(uploadKeystorePath.get())
                    storePassword = uploadKeystorePassword.get()
                    keyAlias = uploadKeyAlias.get()
                    keyPassword = uploadKeyPassword.get()
                }
            } else {
                // An unsigned (or debug-signed) release artifact is worse than no artifact:
                // Play rejects it, and a locally installed one can collide with the store
                // build later. Fail at packaging time so IDE sync still works.
                tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }
                    .configureEach {
                        doFirst {
                            throw GradleException(
                                "RUDIMENTOR_UPLOAD_KEYSTORE is not set, so this release artifact " +
                                    "would not carry the Play upload key. Export the upload " +
                                    "keystore (see the rudimentor-release skill).",
                            )
                        }
                    }
            }
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

// Google Play publishing. Credentials come from the ANDROID_PUBLISHER_CREDENTIALS
// environment variable (the service account JSON), never from a file in this repository.
// Release notes are read from `src/main/play/release-notes/<locale>/<track>.txt`.
play {
    defaultToAppBundles.set(true)
    // Every automated publish lands on `internal`. Moving a build to closed testing or
    // production is a separate, deliberate `promoteArtifact` run.
    track.set("internal")
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
    if (!providers.environmentVariable("ANDROID_PUBLISHER_CREDENTIALS").isPresent) {
        // Keeps `./gradlew tasks` and IDE sync usable before the service account exists.
        enabled.set(false)
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
