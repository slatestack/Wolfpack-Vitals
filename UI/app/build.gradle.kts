import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Override with -PfastApiBaseUrl=https://your-api.example/ (no credentials in this setting).
val fastApiBaseUrl = providers.gradleProperty("fastApiBaseUrl").orElse("http://10.0.2.2:8000/")
abstract class PreparePatient16Assets : DefaultTask() {
    @get:InputDirectory abstract val dataDirectory: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @get:Inject abstract val fileSystem: FileSystemOperations

    @TaskAction fun prepare() {
        fileSystem.sync {
            from(dataDirectory) {
                include("HR_016.csv", "Dexcom_016.csv", "IBI_016.csv", "ACC_016.csv", "EDA_016.csv", "TEMP_016.csv")
            }
            into(outputDirectory.dir("patient-16-data"))
        }
    }
}
val preparePatient16Assets = tasks.register<PreparePatient16Assets>("preparePatient16Assets") {
    dataDirectory.set(layout.projectDirectory.dir("../../local-database/patient-16-data"))
    outputDirectory.set(layout.buildDirectory.dir("generated/patient16Assets"))
}

android {
    namespace = "com.example.wolfpackvitals"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.wolfpackvitals"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "FASTAPI_BASE_URL", "\"${fastApiBaseUrl.get()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["test"].resources.srcDir("../../local-database")
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(preparePatient16Assets, PreparePatient16Assets::outputDirectory)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Jetpack Compose Navigation & Lifecycle ViewModel
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.1")

    // AndroidX Core SplashScreen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Material Icons Extended (For Favorite, Settings, Science, DirectionsRun icons)
    implementation("androidx.compose.material:material-icons-extended:1.7.2")

    // Vico Charting Library for Compose
    implementation("com.patrykandpatrick.vico:compose:1.15.0")
    implementation("com.patrykandpatrick.vico:compose-m3:1.15.0")
    implementation("com.patrykandpatrick.vico:core:1.15.0")
}
