plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    `maven-publish`
}

group = "com.antepod"

version = "0.1.0-SNAPSHOT"

dependencies {
    implementation(project(":lumentika-core"))
    implementation(libs.kspApi)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinCompileTestingKsp)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "lumentika-ksp"
            from(components["java"])
        }
    }
}
