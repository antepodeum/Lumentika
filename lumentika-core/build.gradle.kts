import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    id("com.vanniktech.maven.publish")
    id("org.jetbrains.dokka")
}

group = "com.antepod"

version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

abstract class GenerateStylePropertyCatalog : DefaultTask() {
    @get:Input abstract val propertyNames: ListProperty<String>

    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val names = propertyNames.get()
        require(names.size <= Long.SIZE_BITS) { "Style property masks require at most 64 entries" }
        fun constantName(name: String): String = buildString {
            name.forEach { character ->
                if (character.isUpperCase()) append('_')
                append(character.uppercaseChar())
            }
        }
        val output =
            outputDirectory
                .file("com/antepod/lumentika/style/GeneratedStylePropertyCatalog.kt")
                .get()
                .asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("package com.antepod.lumentika.style")
                appendLine()
                appendLine(
                    "// Generated from the ordered catalog in lumentika-core/build.gradle.kts."
                )
                appendLine("internal object GeneratedStylePropertyCatalog {")
                names.forEachIndexed { index, name ->
                    appendLine("    const val ${constantName(name)} = $index")
                }
                appendLine()
                appendLine("    val maskBits = longArrayOf(")
                names.forEach { name ->
                    appendLine("        1L shl ${constantName(name)},")
                }
                appendLine("    )")
                appendLine("}")
            }
        )

        val animationOutput =
            outputDirectory
                .file("com/antepod/lumentika/animation/GeneratedAnimationAdapters.kt")
                .get()
                .asFile
        animationOutput.parentFile.mkdirs()
        animationOutput.writeText(
            """
            package com.antepod.lumentika.animation

            import com.antepod.lumentika.style.DimensionValue
            import com.antepod.lumentika.style.Properties

            /** Animation adapter for the opacity style property. */
            public object GeneratedOpacityAnimationAdapter : AnimationAdapter<Float> by FloatAnimationAdapter

            /** Animation adapter for the width style property. */
            public object GeneratedWidthAnimationAdapter : AnimationAdapter<DimensionValue> by DimensionAnimationAdapter

            /** Animation adapter for the height style property. */
            public object GeneratedHeightAnimationAdapter : AnimationAdapter<DimensionValue> by DimensionAnimationAdapter

            /** Motion specification applied to opacity by [transitions]. */
            public var TransitionBuilder.opacity: MotionSpec
                get() = error("write-only")
                set(value) = set(Properties.Opacity, value, GeneratedOpacityAnimationAdapter)

            /** Motion specification applied to width by [transitions]. */
            public var TransitionBuilder.width: MotionSpec
                get() = error("write-only")
                set(value) = set(Properties.Width, value, GeneratedWidthAnimationAdapter)

            /** Motion specification applied to height by [transitions]. */
            public var TransitionBuilder.height: MotionSpec
                get() = error("write-only")
                set(value) = set(Properties.Height, value, GeneratedHeightAnimationAdapter)
            """
                .trimIndent() + "\n"
        )
    }
}

val generateStylePropertyCatalog =
    tasks.register<GenerateStylePropertyCatalog>("generateStylePropertyCatalog") {
        propertyNames.set(
            listOf(
                "display",
                "width",
                "height",
                "minWidth",
                "minHeight",
                "maxWidth",
                "maxHeight",
                "padding",
                "margin",
                "gap",
                "flexDirection",
                "flexGrow",
                "flexShrink",
                "overflow",
                "background",
                "borderRadius",
                "borderPaint",
                "boxShadows",
                "clipShape",
                "opacity",
                "zIndex",
                "visibility",
                "pointerEvents",
                "fontSize",
                "color",
                "itemIsTable",
                "itemIsReplaced",
                "boxSizing",
                "direction",
                "overflowX",
                "overflowY",
                "scrollbarWidth",
                "floatValue",
                "clear",
                "position",
                "inset",
                "aspectRatio",
                "border",
                "alignItems",
                "alignSelf",
                "justifyItems",
                "justifySelf",
                "alignContent",
                "justifyContent",
                "columnGap",
                "rowGap",
                "textAlign",
                "flexWrap",
                "flexBasis",
                "gridTemplateRows",
                "gridTemplateColumns",
                "gridAutoRows",
                "gridAutoColumns",
                "gridAutoFlow",
                "gridTemplateAreas",
                "gridTemplateColumnNames",
                "gridTemplateRowNames",
                "gridRow",
                "gridColumn",
            )
        )
        outputDirectory.set(layout.buildDirectory.dir("generated/sources/styleCatalog/kotlin"))
    }

kotlin.sourceSets.named("main") { kotlin.srcDir(generateStylePropertyCatalog) }

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}

dependencies {
    api(libs.kotlinxCoroutines)
    implementation(libs.taffy4j)
    testImplementation(kotlin("test"))
}

mavenPublishing {
    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = SourcesJar.Sources(),
        )
    )
    coordinates(group.toString(), "lumentika-core", version.toString())
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    pom {
        name.set("Lumentika Core")
        description.set("Platform-independent retained UI runtime for Kotlin/JVM")
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
