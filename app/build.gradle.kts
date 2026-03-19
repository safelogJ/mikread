import com.android.build.api.dsl.ApplicationExtension

plugins {
    alias(libs.plugins.android.application)
}

configure<ApplicationExtension> {
    namespace = "com.safelogj.mikread"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.safelogj.mikread"
        minSdk = 23
        targetSdk = 36
        versionCode = 14
        versionName = "192.168.88.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.documentfile)
    implementation(libs.constraintlayout)
    implementation(libs.tik4j)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}