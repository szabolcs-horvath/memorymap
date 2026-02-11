import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.android.libraries.mapsplatform.secrets.gradle.plugin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }

    android {
        namespace = "com.szabolcshorvath.memorymap"
        compileSdk = 36

        defaultConfig {
            applicationId = "com.szabolcshorvath.memorymap"
            minSdk = 27
            targetSdk = 36
            versionCode = 6
            versionName = "Álmodj cuki keselyűkkel! <3"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

            val oauthClientId = localProperties.getProperty("OAUTH_WEB_CLIENT_ID")
            buildConfigField("String", "OAUTH_WEB_CLIENT_ID", "\"$oauthClientId\"")
        }

        buildFeatures {
            buildConfig = true
            viewBinding = true
        }

        signingConfigs {
            create("release") {
                storeFile = localProperties["RELEASE_STORE_FILE"]?.let { file(it) }
                storePassword = localProperties["RELEASE_STORE_PASSWORD"] as String?
                keyAlias = localProperties["RELEASE_KEY_ALIAS"] as String?
                keyPassword = localProperties["RELEASE_KEY_PASSWORD"] as String?
            }
        }

        buildTypes {
            release {
                if (localProperties.getProperty("RELEASE_STORE_FILE") != null) {
                    signingConfig = signingConfigs.getByName("release")
                }
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }

            debug {
                if (localProperties.getProperty("RELEASE_STORE_FILE") != null) {
                    signingConfig = signingConfigs.getByName("release")
                }
                isMinifyEnabled = false
                isShrinkResources = false
            }
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        packaging {
            resources {
                excludes += "META-INF/INDEX.LIST"
                excludes += "META-INF/DEPENDENCIES"
                excludes += "META-INF/LICENSE"
                excludes += "META-INF/LICENSE.txt"
                excludes += "META-INF/license.txt"
                excludes += "META-INF/NOTICE"
                excludes += "META-INF/NOTICE.txt"
                excludes += "META-INF/notice.txt"
                excludes += "META-INF/ASL2.0"
            }
        }

        testOptions {
            unitTests.all {
                it.useJUnitPlatform()
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core & UI
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.datastore.preferences)
    implementation(libs.material)
    implementation(libs.material.tap.target.prompt)

    // Google Maps
    implementation(libs.google.places)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    // Room
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.photoview)

    // Auth
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Drive
    implementation(libs.google.api.services.drive)

    // JSON
    implementation(libs.google.http.client.gson)

    // Firebase with BOM
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Testing
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}