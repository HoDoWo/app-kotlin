// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "CLAVE_DE_TU_PROYECTO")
        property("sonar.organization", "CLAVE_DE_TU_ORGANIZACION")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}
