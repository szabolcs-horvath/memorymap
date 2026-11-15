import com.android.build.api.dsl.ManagedVirtualDevice
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.robolectric.extension.gradle.plugin)
}

val localProperties = Properties()
val localPropertiesFile: File = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.reader().use { localProperties.load(it) }
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
            versionCode = 41
            versionName = "New look for my queen"

            // Installs fakes for sign-in and Drive before the app starts (androidTest).
            testInstrumentationRunner = "com.szabolcshorvath.memorymap.MemoryMapTestRunner"
            // Every UI test starts from a fresh install: no memories, preferences or files from an earlier test.
            // This also wipes the debug app's data on the device the tests run on.
            testInstrumentationRunnerArguments["clearPackageData"] = "true"
            // Lets a failing test save a screenshot that Gradle copies into the test results.
            testInstrumentationRunnerArguments["useTestStorageService"] = "true"

            // Pass only the values the app needs: local.properties also holds the signing passwords.
            manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""

            // Empty when not set: sign-in then shows a clear "not configured" message instead of failing with "null".
            val oauthClientId = localProperties.getProperty("OAUTH_WEB_CLIENT_ID") ?: System.getenv("OAUTH_WEB_CLIENT_ID") ?: ""
            buildConfigField("String", "OAUTH_WEB_CLIENT_ID", "\"$oauthClientId\"")
        }

        buildFeatures {
            buildConfig = true
            compose = true
        }

        signingConfigs {
            create("release") {
                storeFile = localProperties.getProperty("RELEASE_STORE_FILE")?.let { file(it) }
                storePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")
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
    implementation(libs.androidx.core)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.compose.material3.navigation.suite)
    debugImplementation(libs.compose.ui.tooling)

    // Google Maps
    implementation(libs.google.places)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.android.maps.utils.clustering)
    implementation(libs.maps.compose)
    implementation(libs.maps.compose.utils)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)

    // Background work (automatic backups)
    implementation(libs.androidx.work.runtime)

    // Media
    implementation(libs.coil)
    implementation(libs.coil.video)
    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose.material3)
    implementation(libs.reorderable)
    implementation(libs.androidx.exifinterface)

    // Auth
    implementation(libs.credentials)
    runtimeOnly(libs.credentials.play.services.auth) // Credential Manager finds it at run time.
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Drive
    implementation(libs.google.api.services.drive)
    // The Drive code calls these itself, not only through the Drive service.
    implementation(libs.google.api.client)
    implementation(libs.google.http.client)

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
    testImplementation(libs.robolectric.shadows.framework)
    testRuntimeOnly(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.android)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.uiautomator)
    // Not a duplicate of the app's Compose BOM line, whatever the IDE says: the UI tests' runtime classpath doesn't get the
    // app's platform, and without it can't resolve the Compose test library's version.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.services.storage)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)

    // Debug tools
    debugRuntimeOnly(libs.leakcanary)

    constraints {
        // core-ktx is empty now (its Kotlin API is in core), but libraries still bring older ones that aren't: keep it at
        // core's version, so the compile classpath doesn't get the same classes twice.
        implementation(libs.androidx.core.ktx)
        // maps-compose-utils brings android-maps-utils 6: keep all of it at the clustering module's version.
        implementation(libs.android.maps.utils)
        // Compose's test library brings an older Espresso, which fails on API 37. Nothing uses Espresso directly.
        androidTestImplementation(libs.androidx.test.espresso.core)
    }
}