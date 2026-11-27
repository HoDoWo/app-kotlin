import java.util.Properties
import groovy.json.JsonSlurper

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

/** Web OAuth client (`client_type == 3`) of google-services.json, or "" (#54). */
fun googleWebClientIdFromServices(): String {
    val candidates = listOf(
        rootProject.file("app/google-services.json"),
        rootProject.file("google-services.json"),
    )
    for (file in candidates) {
        if (!file.exists()) continue
        val json = JsonSlurper().parse(file) as? Map<*, *> ?: continue
        val clients = json["client"] as? List<*> ?: continue
        for (client in clients) {
            val oauthClients = (client as? Map<*, *>)?.get("oauth_client") as? List<*> ?: continue
            for (oauth in oauthClients) {
                val entry = oauth as? Map<*, *> ?: continue
                if ((entry["client_type"] as? Number)?.toInt() == 3) {
                    val id = entry["client_id"] as? String
                    if (!id.isNullOrBlank()) return id
                }
            }
        }
    }
    return ""
}

val backendUrl = localProperties.getProperty("backend.url", "")
// OAuth *Web* client ID of the Firebase project, used by Google sign-in (#54).
// When local.properties has no value it is read from google-services.json
// (the oauth_client of type 3), so the button works after enabling Google in
// the Firebase console without editing local files.
val googleWebClientId: String = localProperties.getProperty("google.web.client.id", "").trim()
    .ifBlank { googleWebClientIdFromServices() }

// Stripe publishable TEST key (pk_test_...) for the PaymentSheet (#143). It is not a secret,
// but it stays in local.properties like backend.url. Empty = payments disabled in the app.
val stripePublishableKey = localProperties.getProperty("stripe.publishable.key", "")

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
            xml.outputLocation.set(layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml"))
            html.required.set(true)
        }
        // The classes that need the Android framework (or a real Firebase
        // project) are left out of the report; the rest of them are pure
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
            "**/*ArCoreAvailabilityChecker*.class",
            "**/*ArSupportProvider*.class",
            "**/*AiServiceProvider*.class",
            "**/*FirebaseTokenProvider*.class",
            // Android / Firebase bound, so they cannot be unit tested (#54).
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
        buildConfigField("String", "STRIPE_PUBLISHABLE_KEY", "\"$stripePublishableKey\"")
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
        // Java 21: SceneView ships Java 21 bytecode with public inline functions (#240).
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Appearance, accessibility and language preferences (#211).
    implementation(libs.androidx.datastore.preferences)
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
    implementation(libs.firebase.storage)
    implementation(libs.coil.compose)
    // Stripe PaymentSheet: card data is entered in Stripe's own UI, never in ours (#143).
    implementation(libs.stripe.android)
    implementation(libs.androidx.compose.material.icons.extended)
    // Google ARCore, optional: only phones that support it get the AR guide (#239).
    implementation(libs.arcore)
    // SceneView: ARCore session + 3D rendering (Filament) in Compose, for the AR screen (#240).
    // Its Fuel dependency still pulls the old kotlin-android-extensions runtime, whose
    // kotlinx.android.parcel classes duplicate kotlin-parcelize-runtime: keep only the latter.
    implementation(libs.arsceneview) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
    }
    testImplementation(kotlin("test"))
}