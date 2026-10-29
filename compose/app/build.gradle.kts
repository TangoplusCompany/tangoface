plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.google.devtools.ksp)
	id("kotlin-parcelize")
}

android {
	namespace = "com.tangoplus.tangoface_compose"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.tangoplus.tangoface_compose"
		minSdk = 31
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		ndk {
			abiFilters += listOf("arm64-v8a", "armeabi-v7a")
		}
		buildConfigField("String", "GEMINI_API_KEY", "\"\"")
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_11
		targetCompatibility = JavaVersion.VERSION_11
	}
	buildFeatures {
		compose = true
		buildConfig = true
	}
	ndkVersion = "21.4.7075529"
	packaging {
		jniLibs {
			pickFirsts += listOf(
				"lib/armeabi-v7a/libc++_shared.so",
				"lib/arm64-v8a/libc++_shared.so"
			)
		}
	}
}

dependencies {
	implementation(project(":obsensor_jni"))
	implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
	implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")
	implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(libs.androidx.lifecycle.runtime.compose)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.ui.graphics)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.androidx.navigation.compose)
	implementation(libs.androidx.room.runtime)
	implementation(libs.androidx.room.ktx)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material.icons.core)
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.coil.compose)
	implementation(libs.retrofit)
	implementation(libs.converter.moshi)
	implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
	implementation("com.google.code.gson:gson:2.11.0")
	implementation("com.google.mediapipe:tasks-vision:0.10.26")
	implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
	implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
	implementation(libs.kotlinx.coroutines.android)
	implementation(libs.kotlinx.coroutines.core)
	implementation(libs.accompanist.permissions)
	implementation(libs.play.services.location)
	implementation(libs.androidx.camera.camera2)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.view)
	implementation(libs.androidx.camera.core)
	implementation(libs.logging.interceptor)
	implementation(libs.okhttp)
	implementation(libs.moshi.kotlin)
	implementation(libs.androidx.datastore.preferences)
	implementation(libs.material)
	testImplementation(libs.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.androidx.core)
	testImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.compose.ui.test.junit4)
	androidTestImplementation(libs.androidx.runner)
	debugImplementation(libs.androidx.compose.ui.tooling)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
	"ksp"(libs.androidx.room.compiler)
	"ksp"(libs.moshi.kotlin.codegen)
}