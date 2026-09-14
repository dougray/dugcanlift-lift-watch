import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Absent for anyone who clones this repo without the release key. Release
// builds are then left unsigned rather than failing outright, which is the
// same arrangement LIFT Android uses.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasSigningConfig = keystorePropertiesFile.exists()
val keystoreProperties = Properties().apply {
    if (hasSigningConfig) load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.dugcanlift.liftwear"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.dugcanlift.liftwear"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // A debug build installs under its own application id so it sits
        // beside a sideloaded release rather than replacing it -- the release
        // APK is what people download from the site, and re-flashing a debug
        // build over it would take their food log with it.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { jvmToolchain(17) }
// liftkit's captured-export fixtures live in its own test resources, which aren't on :wear's test
// classpath (only liftkit's main sourceSet is, via `implementation(project(":liftkit"))`). Hand the
// path across as a system property instead of duplicating the files.
tasks.withType<Test>().configureEach {
    systemProperty("liftkitFixturesDir", rootProject.projectDir.resolve("liftkit/src/test/resources/fixtures").absolutePath)
}
dependencies {
    implementation(project(":liftkit"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.input)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
