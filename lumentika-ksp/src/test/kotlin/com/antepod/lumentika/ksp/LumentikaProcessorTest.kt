@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.antepod.lumentika.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LumentikaProcessorTest {
    @Test
    fun `generates complete typed component DSL`() {
        val compilation =
            compilation(
                SourceFile.kotlin(
                    "Proof.kt",
                    """
                    package proof

                    import com.antepod.lumentika.component.*
                    import com.antepod.lumentika.runtime.Element

                    @UIComponent
                    class Proof : Component() {
                        val title = prop("default")
                        val checked = binding(false)
                        val changed = event<Boolean>()
                        val content = slot()
                        val trailing = slotList()
                        override fun view(): Element = ui.element("proof")
                    }
                    """
                        .trimIndent(),
                )
            )

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated =
            compilation.kspSourcesDir.walkTopDown().single { it.name == "ProofDsl.kt" }.readText()
        assertContains(generated, "public class `ProofBuilder`")
        assertContains(generated, "public var `title`: kotlin.String")
        assertContains(generated, "public fun `title`(source: Readable<kotlin.String>)")
        assertContains(generated, "public fun `bindChecked`(source: Mutable<kotlin.Boolean>)")
        assertContains(generated, "public fun `onChanged`(listener: (kotlin.Boolean) -> Unit)")
        assertContains(generated, "public fun `content`(content: UiScope.() -> Unit)")
        assertContains(generated, "public fun `trailing`(content: UiScope.() -> Unit)")
        assertContains(generated, "public fun UiScope.`proof`(")
        assertTrue(result.classLoader.loadClass("proof.ProofBuilder") != null)
    }

    @Test
    fun `rejects abstract annotated components with processor diagnostic`() {
        val compilation =
            compilation(
                SourceFile.kotlin(
                    "Invalid.kt",
                    """
                    package proof

                    import com.antepod.lumentika.component.*

                    @UIComponent
                    abstract class Invalid : Component()
                    """
                        .trimIndent(),
                )
            )

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertContains(result.messages, "@UIComponent requires a concrete class")
    }

    @Test
    fun `rejects unsupported component shapes before generating invalid Kotlin`() {
        val cases =
            listOf(
                "class NotAComponent" to "must extend Component",
                "class RequiredConstructor(val value: String) : Component()" to
                    "requires an accessible zero-argument constructor",
                "class Generic<T> : Component()" to "cannot declare type parameters",
            )
        cases.forEach { (declaration, diagnostic) ->
            val result =
                compilation(
                        SourceFile.kotlin(
                            "Invalid.kt",
                            """
                            package proof

                            import com.antepod.lumentika.component.*

                            @UIComponent
                            $declaration
                            """
                                .trimIndent(),
                        )
                    )
                    .compile()
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
            assertContains(result.messages, diagnostic)
        }
    }

    private fun compilation(vararg sources: SourceFile): KotlinCompilation =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            messageOutputStream = System.out
            configureKsp { symbolProcessorProviders += LumentikaProcessorProvider() }
        }
}
