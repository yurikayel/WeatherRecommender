plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kover)
  alias(libs.plugins.detekt)
  alias(libs.plugins.paparazzi)
}

android {
    namespace = "com.example.weatherrecommender"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.weatherrecommender.concierge"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
      compose = true
      buildConfig = true
      aidl = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    // Paparazzi 2.0.0-alpha05 requires Java 21+ at test runtime.
    jvmToolchain(21)
}

kover {
    reports {
        filters {
            includes {
                packages("com.example.weatherrecommender.domain.*")
                packages("com.example.weatherrecommender.data.*")
            }
        }
        total {
            xml {
                onCheck = true
            }
        }
        verify {
            rule("domain-data-line-coverage") {
                bound {
                    minValue = 65
                }
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/detekt.yml")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Network
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging.interceptor)
  implementation(libs.kotlinx.serialization.json)

  // Room, WorkManager & DataStore
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.datastore.preferences)

  // Hilt
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)

  // MapLibre Compose (OSM / OpenFreeMap — no Google Maps API key).
  // OpenGL backend for broader emulator support (Vulkan can fail on some AVDs).
  implementation(libs.maplibre.compose) {
    exclude(group = "org.maplibre.gl", module = "android-sdk")
  }
  implementation(libs.maplibre.android.opengl)

  // Remote images (Wikipedia city thumbnails on detail)
  implementation(libs.coil.compose)

  // Extra testing
  testImplementation(libs.turbine)
  testImplementation(libs.mockk)
  testImplementation(libs.okhttp.mockwebserver)

  androidTestImplementation(libs.androidx.room.testing)
}

tasks.withType<Test>().configureEach {
    if (!project.hasProperty("paparazzi")) {
        exclude("**/WeatherScreenSnapshotTest.class")
    }
    // Paparazzi + Gradle 9: disable HTML test reports to avoid internal API breakage.
    reports.html.required.set(false)
}
