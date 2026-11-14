import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) {
    localFile.inputStream().use { localProps.load(it) }
}

// Optional escape hatch when the repo lives in a folder that locks build outputs (OneDrive).
val externalBuild = localProps.getProperty("frameai.buildDir")?.trim().orEmpty()
if (externalBuild.isNotEmpty()) {
    layout.buildDirectory.set(file(externalBuild))
}

android {
    namespace = "com.team.frameai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.team.frameai"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SHOT_REVIEW_BASE_URL", gradleString(localProps.getProperty("frameai.baseUrl")))
        buildConfigField("String", "DEMO_TOKEN", gradleString(localProps.getProperty("frameai.demoToken")))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

val packagedContracts = listOf(
    "live-guide.mock.json",
    "live-guide.ready.mock.json",
    "live-guide.noface.mock.json",
    "shot-review.mock.json",
    "shot-review.offline.json",
)

tasks.register("checkPackagedContracts") {
    doLast {
        packagedContracts.forEach { name ->
            val expected = rootProject.file("contracts/$name").readText()
            val actual = file("src/main/assets/contracts/$name").readText()
            if (expected != actual) {
                throw GradleException("app/src/main/assets/contracts/$name does not match contracts/$name")
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn("checkPackagedContracts") }

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

fun gradleString(value: String?): String {
    val clean = (value ?: "").trim().replace("\"", "").replace("\n", "").replace("\r", "")
    return "\"$clean\""
}
