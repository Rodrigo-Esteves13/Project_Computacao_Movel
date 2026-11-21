import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional VirusTotal key, read from local.properties (never committed). Only letters and digits are kept,
// so a malformed value cannot break the generated source. Without it the reputation check is unavailable.
val virusTotalKey: String = Properties().let { props ->
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    (props.getProperty("VIRUSTOTAL_API_KEY") ?: "").filter { it.isLetterOrDigit() }
}

android {
    namespace = "pt.ismai.farol"
    compileSdk = 36

    defaultConfig {
        applicationId = "pt.ismai.farol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "VIRUSTOTAL_API_KEY", "\"$virusTotalKey\"")
    }

    // Release signing comes from keystore.properties in the project root (never committed, see README).
    // Without that file the release build is produced unsigned, so a clone still builds.
    val keystoreProperties = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: removes unused code and resources and renames the rest (smaller, harder to read).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        // The Firebase exception classes touch android.text in their constructors.
        unitTests.isReturnDefaultValues = true
        // Robolectric needs the app resources to draw Compose screens on the JVM.
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// The Google services plugin needs app/google-services.json (see README). Without the file the app
// still builds and runs, but without accounts (see AppContainer.auth).
if (file("google-services.json").exists()) {
    pluginManager.apply(libs.plugins.google.services.get().pluginId)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    debugImplementation(libs.okhttp.logging)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
