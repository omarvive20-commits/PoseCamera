plugins {
 id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose")
}
android { namespace="com.example.poseoverlay"; compileSdk=35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
 defaultConfig { applicationId="com.example.poseoverlay"; minSdk=26; targetSdk=35; versionCode=4; versionName = "1.9" }
}
dependencies {
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.compose.ui:ui:1.7.6")
 implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
 implementation("androidx.compose.material3:material3:1.3.1")
 implementation("androidx.camera:camera-camera2:1.4.1")
 implementation("androidx.camera:camera-lifecycle:1.4.1")
 implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-video:1.4.1")
}
