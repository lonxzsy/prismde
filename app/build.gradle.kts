plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.prismde"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.prismde"
        minSdk = 26
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GITHUB_OWNER", "\"lonxzsy\"")
        buildConfigField("String", "GITHUB_REPO_APP", "\"prismde\"")
        buildConfigField("String", "GITHUB_REPO_NDK", "\"prismde-ndk\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("${rootDir}/prismde-release.jks")
            storePassword = System.getenv("PRISM_KEYSTORE_PASSWORD") ?: "prismde-release"
            keyAlias = System.getenv("PRISM_KEY_ALIAS") ?: "prismde"
            keyPassword = System.getenv("PRISM_KEY_PASSWORD") ?: "prismde-release"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        disable += listOf("ExpiredTargetSdkVersion")
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // DataStore & Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)

    // Sora Code Editor
    implementation(libs.sora.editor.core)
    implementation(libs.sora.editor.textmate)

    // Networking, JSON, & Archive unpacking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.commons.compress)
    implementation(libs.tukaani.xz)

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
