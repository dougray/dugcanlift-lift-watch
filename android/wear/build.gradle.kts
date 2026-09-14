plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.dugcanlift.liftwear"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.dugcanlift.liftwear"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
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
    implementation(libs.watchface.complications.data.source.ktx)
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
