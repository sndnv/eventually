buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")
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
    version = "1.0.0"
}
