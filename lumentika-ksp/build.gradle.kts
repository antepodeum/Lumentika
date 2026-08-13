import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

group = "com.antepod"

version = providers.gradleProperty("releaseVersion").get()

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

dependencies {
    implementation(libs.kspApi)
    testImplementation(kotlin("test"))
    testImplementation(project(":lumentika-core"))
    testImplementation(libs.kotlinCompileTestingKsp)
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )
    coordinates(group.toString(), "lumentika-ksp", version.toString())
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("Lumentika KSP")
        description.set("KSP compiler plugin for the Lumentika Kotlin UI DSL")
        inceptionYear.set("2026")
        url.set("https://github.com/antepodeum/lumentika")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("antepodeum")
                name.set("antepodeum")
                email.set("antepodeum@users.noreply.github.com")
                url.set("https://github.com/antepodeum")
                organization.set("antepodeum")
                organizationUrl.set("https://github.com/antepodeum")
            }
        }
        scm {
            url.set("https://github.com/antepodeum/lumentika")
            connection.set("scm:git:https://github.com/antepodeum/lumentika.git")
            developerConnection.set("scm:git:ssh://git@github.com/antepodeum/lumentika.git")
        }
    }
}

publishing.repositories.maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/antepodeum/lumentika")
    credentials {
        username = providers.gradleProperty("gprUser").orNull
        password = providers.gradleProperty("gprKey").orNull
    }
}
