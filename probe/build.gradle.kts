plugins {
    id("com.android.application")
}

val sourceRevision: String = System.getenv("GITHUB_SHA")
    ?.takeIf { it.matches(Regex("[a-f0-9]{40}")) } ?: "LOCAL_UNVERIFIED"

android {
    namespace = "io.geoshift.probe"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "io.geoshift.probe"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.5-g08"
        buildConfigField("String", "SOURCE_REVISION", "\"$sourceRevision\"")
    }

    flavorDimensions += "testRole"
    productFlavors {
        create("target") {
            dimension = "testRole"
            applicationIdSuffix = ".target"
            buildConfigField("String", "PROBE_ROLE", "\"target\"")
        }
        create("control1") {
            dimension = "testRole"
            applicationIdSuffix = ".control1"
            buildConfigField("String", "PROBE_ROLE", "\"control1\"")
        }
        create("control2") {
            dimension = "testRole"
            applicationIdSuffix = ".control2"
            buildConfigField("String", "PROBE_ROLE", "\"control2\"")
        }
        create("control3") {
            dimension = "testRole"
            applicationIdSuffix = ".control3"
            buildConfigField("String", "PROBE_ROLE", "\"control3\"")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
