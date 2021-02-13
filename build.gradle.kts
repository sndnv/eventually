import org.gradle.api.tasks.testing.logging.TestExceptionFormat

buildscript {
    repositories {
        jcenter()
        google()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.21")
        classpath("com.android.tools.build:gradle:4.1.2")
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.12.0-RC1"
    jacoco
}

allprojects {
    repositories {
        jcenter()
        mavenCentral()
        google()
    }
}

subprojects {
    version = "1.0.0-SNAPSHOT"
}

allprojects {
    tasks.withType<Test> {
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = true
            events("passed", "skipped", "failed", "standardOut", "standardError")
        }
    }
}
