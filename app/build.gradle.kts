plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    buildFeatures {
        viewBinding = true
    }

    namespace = "com.example.hospitalnotifier"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.hospitalnotifier"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

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
}

dependencies {
    // ... 기본 라이브러리

    // 1. WorkManager: 주기적인 백그라운드 작업을 위함
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // 2. Retrofit & OkHttp: HTTP 통신 (Python의 requests 대체)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // JSON 파싱용
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0") // 일반 텍스트 파싱용
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0") // 일반 텍스트 파싱용
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0") // (선택) 통신 로그 확인용

    // 3. Kotlin Coroutines: 비동기 처리를 위함
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 4. Persistent Cookie Jar: 쿠키 관리를 위함
    implementation("com.github.franmontiel:PersistentCookieJar:v1.0.1")

    // 5. Jsoup: HTML 파싱을 위함
    implementation("org.jsoup:jsoup:1.17.2")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("org.robolectric:robolectric:4.10.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("io.mockk:mockk:1.13.7")
    testImplementation("androidx.work:work-testing:2.9.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}