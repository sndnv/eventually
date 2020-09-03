buildscript {
    repositories {
        jcenter()
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
    }
}

subprojects {
    version = "1.0.0"
}
