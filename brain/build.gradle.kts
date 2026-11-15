plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
}

sourceSets.named("main") {
    kotlin.setSrcDirs(listOf("../app/src/main/java"))
    kotlin.exclude("**/MainActivity.kt")
    kotlin.exclude("**/ui/**")
    kotlin.exclude("**/camera/**")
    kotlin.exclude("**/ContractJson.kt")
    kotlin.exclude("**/FaceDetectionAdapter.kt")
    kotlin.exclude("**/MlKitPrimaryFaceDetector.kt")
    kotlin.exclude("**/BitmapLutRepository.kt")
    kotlin.exclude("**/CubePhotoGrader.kt")
}

tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.test {
    useJUnitPlatform()
}
