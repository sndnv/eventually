buildscript {
    repositories {
        jcenter()
        google()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.4.10")
        classpath("com.android.tools.build:gradle:4.1.1")
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
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = true
            events("passed", "skipped", "failed", "standardOut", "standardError")
        }
    }
}
