import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    jacoco
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.9")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.2.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.2.0")
    testImplementation("io.kotest:kotest-property-jvm:4.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.9")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}

tasks.register("qa") {
    dependsOn("check")
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        csv.isEnabled = false
        html.isEnabled = true
    }
}
