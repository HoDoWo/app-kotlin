// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.detekt)
}

sonar {
    properties {
        property("sonar.projectKey", "HoDoWo_app-kotlin")
        property("sonar.organization", "hodowo")
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            file("app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml").absolutePath,
        )
        property("sonar.coverage.exclusions", "**/MainActivity.kt,**/SkeletonScreen.kt,**/OpenRouterChatClient.kt,**/CameraScreen.kt,**/ChatScreen.kt,**/PanelOverlay.kt,**/HoDoWoApp.kt,**/ImageCompressor.kt,**/AiServiceProvider.kt,**/FirebaseTokenProvider.kt")
    }
}

tasks.named("sonar") {
    dependsOn(":app:jacocoDebugUnitTestReport")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}