plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
}

android {
    namespace = "ai.bilejack"
    compileSdk = 36

    defaultConfig {
        applicationId = "ai.bilejack"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // HTTP Client
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // JSON
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    // Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.room.rxjava3)
    kapt(libs.room.compiler)

    // RxJava
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("com.jakewharton.rxbinding4:rxbinding:4.0.0")

    // Lifecycle
    implementation(libs.lifecycle.service)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
