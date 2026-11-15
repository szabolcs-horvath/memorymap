// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dependency.analysis) apply false
}

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Checks the declared dependencies against what the code uses, only when asked for:
// ./gradlew buildHealth -PdependencyAnalysis (the report: build/reports/dependency-analysis/build-health-report.txt).
// Normal builds and CI only have it on the classpath, so its version stays in the catalog with the rest.
val dependencyAnalysis = providers.gradleProperty("dependencyAnalysis").isPresent
if (dependencyAnalysis) {
    apply(plugin = "com.autonomousapps.dependency-analysis")
    configure<com.autonomousapps.DependencyAnalysisExtension> {
        // Libraries of one family: the code may use any module of one it declares, which brings the others.
        structure {
            bundle("compose") { include("^androidx\\.compose\\..*") }
            bundle("androidx-core") { include("^androidx\\.(core|annotation|collection):.*") }
            bundle("lifecycle") { include("^androidx\\.(lifecycle|savedstate):.*") }
            bundle("activity") { include("^androidx\\.(activity|navigationevent):.*") }
            bundle("datastore") { include("^androidx\\.datastore:.*") }
            bundle("room") { include("^androidx\\.(room|sqlite):.*") }
            bundle("media3") { include("^androidx\\.media3:.*") }
            bundle("androidx-test") { include("^androidx\\.test(\\..*)?:.*") }
            bundle("coil") { include("^io\\.coil-kt\\.coil3:.*") }
            bundle("telephoto") { include("^me\\.saket\\.telephoto:.*") }
        }
        issues {
            all {
                // junit-jupiter is JUnit 5's one-line way to get its API, parameterized tests and engine.
                onUnusedDependencies { exclude("org.junit.jupiter:junit-jupiter") }
                onUsedTransitiveDependencies {
                    exclude("org.junit.jupiter:junit-jupiter-api", "org.junit.jupiter:junit-jupiter-params")
                    // How the reorderable library publishes its debug variant, not a library of its own.
                    exclude("sh.calvin.reorderable:reorderable-android-debug")
                    // The icons tint with Android's own colorControlNormal, which the plugin takes for AppCompat's attribute of
                    // the same name: the app doesn't use AppCompat.
                    exclude("androidx.appcompat:appcompat")
                }
            }
        }
    }
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    if (dependencyAnalysis) {
        apply(plugin = "com.autonomousapps.dependency-analysis")
    }

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
