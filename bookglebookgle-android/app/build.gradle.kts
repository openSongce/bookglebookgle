import java.util.Properties
import java.io.File

val localProperties = Properties().apply {
    load(File(rootDir, "local.properties").inputStream())
}

val googleClientId = localProperties.getProperty("GOOGLE_CLIENT_ID")
val kakaoNativeKey = localProperties.getProperty("KAKAO_NATIVE_KEY")


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt.android)
    id("kotlin-parcelize")
    id("com.google.protobuf") version "0.9.4"
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ssafy.bookglebookgle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ssafy.bookglebookgle"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "KAKAO_NATIVE_KEY", "\"$kakaoNativeKey\"")

        vectorDrawables {
            useSupportLibrary = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    hilt{
        enableAggregatingTask = false
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
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.core.i18n)
    implementation(libs.androidx.hilt.common)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation ("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Retrofit + Network
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // 스플래시 화면 관련 의존성 추가
    implementation("androidx.compose.animation:animation:1.7.8")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Lifecycle Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Hilt for Compose
    implementation("com.google.dagger:hilt-android:2.48.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    // PDF libraries
    implementation(libs.pdfbox.android)
    implementation (libs.pdfium.android)

    // JSON 파싱
    implementation("com.google.code.gson:gson:2.10.1")

    // 권한 요청
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")

    // 시스템 UI 제어 (상태바/네비게이션바 색상 등)
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    // 이미지 로딩 (PDF 썸네일 등 이미지 비동기 로딩)
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Room (로컬 DB - SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // FlowLayout (태그, 버튼 등 가변적인 Row/Column 레이아웃)
    implementation("com.google.accompanist:accompanist-flowlayout:0.32.0")

    //Google Login
    implementation ("androidx.credentials:credentials:1.5.0")
    implementation ("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation ("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    //Kakao Login
    implementation("com.kakao.sdk:v2-user:2.21.6")

    //gRPC
    implementation("io.grpc:grpc-okhttp:1.64.0")
    implementation("io.grpc:grpc-protobuf-lite:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("com.google.protobuf:protobuf-javalite:3.25.3")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.grpc:grpc-netty-shaded:1.64.0")

    // ⬇️ Firebase (BOM으로 버전 정합)
    implementation(platform("com.google.firebase:firebase-bom:33.3.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    // ⬇️ 토큰 async 사용
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt WorkManager
    implementation("androidx.hilt:hilt-work:1.1.0")
    kapt("androidx.hilt:hilt-compiler:1.1.0")
}

kapt {
    correctErrorTypes = true
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }

    // 여기를 수정
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
    }

    generatedFilesBaseDir = "$projectDir/build/generated"

    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc") {
                    option("lite") // 옵션은 선택
                }
            }
            it.builtins {
                create("java") {
                    option("lite")
                }
            }
            it.inputs.dir("src/main/proto")
        }
    }
}