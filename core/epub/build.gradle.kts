import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM module: EPUB container + package parsing and XHTML text extraction.
// No Android dependencies, so everything here is unit-testable on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
    // Shares the in-memory EPUB builder with other modules' tests.
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Compile against the JDK 17 API so newer JDK-only methods can't leak into the Android app.
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    implementation(libs.jsoup)

    testImplementation(libs.junit)
}
