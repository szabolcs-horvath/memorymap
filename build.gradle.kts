// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.detekt) apply false
}

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension>("detekt") {
        toolVersion = rootProject.libs.versions.detekt.get()
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        baseline = file("${rootProject.projectDir}/config/detekt/baseline.xml")
        // The default sources plus the UI tests.
        source.setFrom(
            listOf("main", "test", "androidTest").flatMap { set -> listOf("src/$set/java", "src/$set/kotlin") }
        )
    }

    dependencies {
        "detektPlugins"(rootProject.libs.detekt.formatting)
    }

    // Only what CI reads: SARIF for GitHub code scanning, Markdown for the job summary. Locally, the console lists
    // every finding. Detekt writes every report unless told not to.
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.required.set(false)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(true)
            md.required.set(true)
        }
    }
}
