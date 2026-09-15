plugins {
    alias(libs.plugins.android.application)
}

// A Watch Face Format package: declarative XML, no code at all. The system's
// watch face runtime renders it, which is why `hasCode` is false in the
// manifest and why there is no Kotlin source set here.
android {
    namespace = "com.dugcanlift.facelift"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "com.dugcanlift.facelift"
        // WFF v2 is Wear OS 5 (API 34). minSdk 33 would mean writing to v1 and
        // giving up expressions; there is no Wear 4 device in play.
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
}
