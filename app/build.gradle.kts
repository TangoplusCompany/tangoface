import org.gradle.kotlin.dsl.implementation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id ("kotlin-kapt")
    id ("kotlin-parcelize")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
val obSignatureProp = Properties().apply {
    load(project.file("../keystore/signature.properties").inputStream())
}

android {
    namespace = "com.tangoplus.facebeautyexpert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tangoplus.facebeautyexpert"
        minSdk = 29
        targetSdk = 35
        versionCode = 4
        versionName = "1.03"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val format = SimpleDateFormat("yyyyMMdd")
        var dateFormat = format.format(Date())
        dateFormat += "01" // 01은 당일 첫 번째 빌드 버전
        versionCode = if (project.hasProperty("JENKINS_BUILD_VERSION_CODE")) {
            (project.property("JENKINS_BUILD_VERSION_CODE") as String).toInt()
        } else {
            dateFormat.toInt()
        }

        versionName = if (project.hasProperty("JENKINS_BUILD_VERSION_NAME")) {
            project.property("JENKINS_BUILD_VERSION_NAME") as String
        } else {
            "2.0.4"
        }
        externalNativeBuild {
            cmake {
                arguments(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE"
                )
                cppFlags += "-std=c++11"
                abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            }
        }
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.jks")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("../keystore/facebeautyexpert.jks")
            storePassword = obSignatureProp.getProperty("STORE_PASSWORD")
            keyAlias = obSignatureProp.getProperty("KEY_ALIAS")
            keyPassword = obSignatureProp.getProperty("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isJniDebuggable = false
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isJniDebuggable = true
        }
    }
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val buildTypeName = buildType.name
            val fileName = when (buildTypeName) {
                "release" -> "face_analyzer_expert_release.apk"
                "debug" -> "FaceBeautyExpert_v${versionName}_${versionCode}_beta.apk"
                else -> outputImpl.outputFile.name
            }
            outputImpl.outputFileName = fileName
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.18.1"
        }
    }
    ndkVersion = "21.4.7075529"
    packagingOptions {
        pickFirst("lib/armeabi-v7a/libc++_shared.so")
        pickFirst("lib/arm64-v8a/libc++_shared.so")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    viewBinding {
        enable = true
    }
}


//noinspection UseTomlInstead
dependencies {
    // 외부 라이브러리
//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.10")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
    implementation("com.hbb20:ccp:2.7.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics-ndk")

    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")
    implementation("com.github.skydoves:progressview:1.1.3")

    //noinspection Aligned16KB
    implementation("com.google.mediapipe:tasks-vision:0.10.26")


    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")
    implementation("com.github.skydoves:balloon:1.6.4")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.ui)
//    implementation(project(":obsensor_jni"))
    if (findProject(":obsensor_jni") != null) {
        implementation(project(":obsensor_jni"))
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"), "exclude" to listOf("obsensor_*.aar"))))
    } else {
        implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    }


    val camera_version = "1.5.0"
    implementation("androidx.camera:camera-core:$camera_version")
    implementation("androidx.camera:camera-camera2:$camera_version")
    implementation("androidx.camera:camera-lifecycle:$camera_version")
    implementation("androidx.camera:camera-video:$camera_version")
    implementation("androidx.camera:camera-extensions:$camera_version")
    implementation("androidx.camera:camera-view:$camera_version")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-effect:1.5.1")
    implementation("androidx.media3:media3-common:1.2.0")
    implementation("androidx.media3:media3-transformer:1.2.0")
    implementation("androidx.window:window:1.1.0-alpha03")

    implementation("com.google.android.exoplayer:exoplayer:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-core:2.19.1")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.19.1")

    val roomVersion = "2.8.1"
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-rxjava3:$roomVersion")
    implementation("androidx.room:room-rxjava2:$roomVersion")
    implementation("androidx.room:room-guava:$roomVersion")
    implementation("androidx.room:room-testing:$roomVersion")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    kapt("android.arch.persistence.room:rxjava2:1.1.1")
    //noinspection KaptUsageInsteadOfKsp
    kapt("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.sqlite:sqlite:2.4.0")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.3.0")

    //noinspection Aligned16KB
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    implementation ("androidx.core:core-ktx:1.14.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
//    implementation("com.github.Dimezis:BlurView:version-3.2.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}