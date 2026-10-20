import java.util.Properties

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

val backendUrl = localProperties.getProperty("backend.url", "")
// OAuth *Web* client ID of the Firebase project, used by Google sign-in (#54).
val googleWebClientId = localProperties.getProperty("google.web.client.id", "")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    jacoco
}

android {
    namespace = "com.hodowo"
    compileSdk {
        version = release(37)
    }

    tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
        dependsOn("testDebugUnitTest")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        // Only the classes that need the Android framework (or a real Firebase
        // project) are left out of the report: the rest of them are pure
        // Kotlin and are covered by the unit tests. The same list is in
        // sonar.coverage.exclusions (root project).
        val excludes = listOf(
            "**/ui/**",
            "**/*MainActivity*.class",
            "**/*SkeletonScreen*.class",
            "**/*OpenRouterChatClient*.class",
            "**/*CameraScreen*.class",
            "**/*ChatScreen*.class",
            "**/*PanelOverlayKt*.class",
            "**/*HoDoWoApp*.class",
            "**/*ImageCompressor*.class",
            "**/*BitmapPanelCropper*.class",
            "**/*AiServiceProvider*.class",
            "**/*FirebaseTokenProvider*.class",
            "**/*FirebaseAuthRepository*.class",
            "**/*GoogleSignInHelper*.class",
            "**/BuildConfig.class"
        )
        val kotlinClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
            exclude(excludes)
        }
        val tmpKotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
            exclude(excludes)
        }
        val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
            exclude("**/MainActivity.class")
            // Generated code, it has nothing to do with the tests.
            exclude("**/BuildConfig.class")
        }
        classDirectories.setFrom(files(kotlinClasses, tmpKotlinClasses, javaClasses))
        sourceDirectories.setFrom(files("src/main/java"))
        executionData.setFrom(fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        })
    }

    defaultConfig {
        applicationId = "com.hodowo"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
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
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.constraintlayout)
    // CameraX: camera preview and photo capture (#38).
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("io.mockk:mockk:1.13.12")
    // Fake HTTP server and a real org.json for RemoteHoDoWoAiService tests.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Google sign-in through Credential Manager (#54). Same versions that
    // firebase-auth already pulls in, so they are in verification-metadata.xml.
    implementation("androidx.credentials:credentials:1.2.0-rc01")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.0-rc01")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.0")
    implementation(libs.firebase.firestore)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(kotlin("test"))
}