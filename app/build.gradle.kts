import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.robolectric.extension.gradle.plugin)
}

val localProperties = Properties()
val localPropertiesFile: File = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }

    android {
        namespace = "com.szabolcshorvath.memorymap"
        compileSdk = 37

        defaultConfig {
            applicationId = "com.szabolcshorvath.memorymap"
            minSdk = 34
            targetSdk = 37
            versionCode = 38
            versionName = "minSdk 34"

            // Installs fakes for sign-in and Drive before the app starts (androidTest).
            testInstrumentationRunner = "com.szabolcshorvath.memorymap.MemoryMapTestRunner"
            // Every UI test starts from a fresh install: no memories, preferences or files from an earlier test.
            // This also wipes the debug app's data on the device the tests run on.
            testInstrumentationRunnerArguments["clearPackageData"] = "true"

            // Pass only the values the app needs: local.properties also holds the signing passwords.
            manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""

            // Empty when not set: sign-in then shows a clear "not configured" message instead of failing with "null".
            val oauthClientId = localProperties.getProperty("OAUTH_WEB_CLIENT_ID") ?: System.getenv("OAUTH_WEB_CLIENT_ID") ?: ""
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
                // Default debug keystore on purpose: never sign debuggable builds with the release key.
                isMinifyEnabled = false
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
            // UI tests: each test runs in its own app process, so a crash fails only that test.
            execution = "ANDROIDX_TEST_ORCHESTRATOR"
            animationsDisabled = true

            // The emulators CI runs the UI tests on: ./gradlew pixel6Api35DebugAndroidTest pixel6Api37DebugAndroidTest
            // 37 is the targetSdk. 35 stands in for minSdk 34: every API 34 emulator image ships Google Play services
            // 23.18 (2023), whose map renderer crashes on the app's advanced markers, and can't update it.
            managedDevices {
                allDevices {
                    for (sdk in listOf(35, 37)) {
                        create<ManagedVirtualDevice>("pixel6Api$sdk") {
                            device = "Pixel 6"
                            sdkVersion = sdk
                            systemImageSource = "google" // Google APIs: the map needs Google Play services.
                        }
                    }
                }
            }

            unitTests {
                isIncludeAndroidResources = true
                all {
                    it.useJUnitPlatform()
                    // Robolectric's SDK 36+ images touch FileDescriptor internals.
                    it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                }
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
    implementation(libs.pie.chart)

    // Google Maps
    implementation(libs.google.places)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.android.maps.utils)
    implementation(libs.maps.ktx)
    implementation(libs.maps.utils.ktx)

    // Room
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Background work (automatic backups)
    implementation(libs.androidx.work.runtime.ktx)

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
    implementation(libs.firebase.perf)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestUtil(libs.androidx.test.orchestrator)

    // Debug tools
    debugImplementation(libs.leakcanary)
}