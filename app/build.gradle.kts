import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "com.project.fridgemate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.project.fridgemate"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3001/\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.picasso)
    implementation(libs.lottie)

    // Maps & Location
    implementation(libs.osmdroid)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.swiperefreshlayout)
    implementation(libs.viewpager2)

    // Encrypted storage
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.emoji2.emojipicker)

    // Socket.IO
    implementation(libs.socket.io.client) {
        exclude(group = "org.json", module = "json")
    }

    // Firebase Cloud Messaging
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

apply(plugin = "com.google.gms.google-services")
