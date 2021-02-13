plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-android-extensions")
    kotlin("kapt")
}

dependencies {
    implementation(project(":core"))

    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.21")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.3")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.3")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.preference:preference-ktx:1.1.1")
    implementation("com.google.android.material:material:1.3.0")
    implementation("androidx.room:room-runtime:2.3.0-beta01")
    implementation("androidx.room:room-ktx:2.3.0-beta01")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.3.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.3.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.3.0")
    implementation("androidx.work:work-runtime-ktx:2.5.0")
    implementation("ru.cleverpumpkin:crunchycalendar:2.0.0")
    implementation("ca.antonious:materialdaypicker:0.7.4")

    kapt("androidx.room:room-compiler:2.3.0-beta01")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.4.2")

    testImplementation("io.mockk:mockk:1.10.3")
    testImplementation("org.robolectric:robolectric:4.4")
    testImplementation("junit:junit:4.13.1")
    testImplementation("androidx.test:core:1.3.0")

    androidTestImplementation("androidx.test:runner:1.3.0")
    androidTestImplementation("androidx.test:rules:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
    androidTestImplementation("androidx.room:room-testing:2.3.0-beta01")
    androidTestImplementation("androidx.arch.core:core-testing:2.1.0")

    debugImplementation("androidx.fragment:fragment-testing:1.2.5") {
        exclude(group = "androidx.test", module = "monitor")
    }
}

android {
    compileSdkVersion(30)
    buildToolsVersion("30.0.2")

    defaultConfig {
        minSdkVersion(26)
        targetSdkVersion(30)

        applicationId = "eventually.client"
        versionCode = 1
        versionName = "1.0.0-SNAPSHOT"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    packagingOptions {
        exclude("META-INF/atomicfu.kotlin_module")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

tasks.register("qa") {
    dependsOn("check")
}
