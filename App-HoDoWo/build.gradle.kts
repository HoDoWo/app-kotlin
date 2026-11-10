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
            "${project.rootDir}/app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml,**/reports/jacoco/**/*.xml",
        )
        property(
            "sonar.coverage.exclusions",
            "**/*Test.kt,**/src/test/**,**/ui/**,**/adaptive/**,**/components/**,**/ui/theme/**,app/src/main/java/com/hodowo/ui/**,**/MainActivity.kt,**/OpenRouterChatClient.kt,**/ImageCompressor.kt,**/BitmapPanelCropper.kt,**/AiServiceProvider.kt,**/FirebaseTokenProvider.kt",
        )
        property(
            "sonar.cpd.exclusions",
            "**/*Test.kt,**/src/test/**,**/ui/**,**/adaptive/**,**/components/**,**/ui/theme/**,app/src/main/java/com/hodowo/ui/**,**/MainActivity.kt,**/OpenRouterChatClient.kt,**/ImageCompressor.kt,**/BitmapPanelCropper.kt,**/AiServiceProvider.kt,**/FirebaseTokenProvider.kt,**/MarketplaceService.kt",
        )
    }
}

tasks.named("sonar") {
    dependsOn(":app:jacocoDebugUnitTestReport")
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}