import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.symbol.processing)
    id("com.google.gms.google-services")
}
val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

val apiKey = localProperties.getProperty("HOLIDAY_API_KEY") ?: ""
val mapboxToken = localProperties.getProperty("MAPBOX_ACCESS_TOKEN") ?: ""
android {
    namespace = "com.MateuszW.aigrowthtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.MateuszW.aigrowthtracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "HOLIDAY_API_KEY", "\"$apiKey\"")
        buildConfigField("String", "MAPBOX_ACCESS_TOKEN", "\"$mapboxToken\"")
        manifestPlaceholders["MAPBOX_ACCESS_TOKEN"] = mapboxToken
        resValue("string", "mapbox_access_token", mapboxToken)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.animation.core)
    implementation("com.google.firebase:firebase-auth-ktx:23.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui)
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.junit:junit:1.1.5")
    androidTestImplementation("androidx.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.firebase:firebase-firestore-ktx:25.0.0")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    // Mapbox
    implementation("com.mapbox.maps:android:11.9.0")
    implementation("com.mapbox.extension:maps-compose:11.9.0")
    // Google Play Services Location (for FusedLocationProviderClient)
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
