plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

val appVersionCode = 23
val appVersionName = "2.3.4"

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.autopanel.app"
    compileSdk = 37

    val hasReleaseSigning = listOf(
        "KEYSTORE_PASSWORD",
        "KEY_ALIAS",
        "KEY_PASSWORD"
    ).all { !System.getenv(it).isNullOrBlank() } && rootProject.file("release.keystore").exists()

    defaultConfig {
        applicationId = "com.autopanel.app"
        minSdk = 31
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "FIREBASE_APPLICATION_ID",
            buildConfigString(providers.environmentVariable("AZUREQL_FIREBASE_APPLICATION_ID").orElse("").get())
        )
        buildConfigField(
            "String",
            "FIREBASE_API_KEY",
            buildConfigString(providers.environmentVariable("AZUREQL_FIREBASE_API_KEY").orElse("").get())
        )
        buildConfigField(
            "String",
            "FIREBASE_PROJECT_ID",
            buildConfigString(providers.environmentVariable("AZUREQL_FIREBASE_PROJECT_ID").orElse("").get())
        )
        buildConfigField(
            "String",
            "FIREBASE_SENDER_ID",
            buildConfigString(providers.environmentVariable("AZUREQL_FIREBASE_SENDER_ID").orElse("").get())
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file("release.keystore")
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        if (variant.name == "debug" || variant.name == "release") {
            variant.outputs.forEach { output ->
                output.outputFileName.set("AzureQL-$appVersionName-${variant.name}.apk")
            }
        }
    }

    onVariants(selector().withName("benchmarkRelease")) { variant ->
        variant.sources.manifests.addStaticManifestFile(
            "src/benchmarkRelease/AndroidManifest.xml"
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:mcp"))
    implementation(project(":feature:login"))
    implementation(project(":feature:task"))
    implementation(project(":feature:env"))
    implementation(project(":feature:script"))
    implementation(project(":feature:dependency"))
    implementation(project(":feature:log"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:backup"))
    implementation(project(":feature:mcp"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.navigation)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    baselineProfile(project(":benchmark"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
