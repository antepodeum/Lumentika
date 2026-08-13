plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    `maven-publish`
}

group = "com.antepod"

version = "0.1.0-SNAPSHOT"

dependencies {
    api(libs.kotlinxCoroutines)
    implementation(libs.taffy4j)
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = "lumentika-core"
            from(components["java"])
        }
    }
}
