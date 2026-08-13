import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-library`
    `maven-publish`
}

group = "com.antepod"

version = "0.1.0-SNAPSHOT"

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
