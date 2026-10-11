// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.sonarqube)
    alias(libs.plugins.detekt)
}

sonar {
    properties {
        property("sonar.projectKey", "HoDoWo_app-kotlin")
        property("sonar.organization", "hodowo")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml")
        property("sonar.coverage.exclusions", "**/MainActivity.kt,**/SkeletonScreen.kt,**/OpenRouterChatClient.kt")
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
}
