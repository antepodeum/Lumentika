@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.antepod.lumentika.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LumentikaProcessorTest {
    @Test
    fun `generates compact typed arguments events and slots`() {
        val compilation =
            compilation(
                SourceFile.kotlin(
                    "Proof.kt",
                    """
                    package proof

                    import com.antepod.lumentika.component.*
                    import com.antepod.lumentika.reactive.*
                    import com.antepod.lumentika.runtime.*

                    @UIComponent
                    class Proof : Component() {
                        val title = prop("default")
                        val subtitle = requiredProp<String>()
                        val nullable = prop<String?>(null)
                        val count = prop(0)
                        val checked = binding(false)
                        val requiredValue = requiredBinding<Int>()
                        val note = binding("default note")
                        val changed = event<Boolean>()
                        val closed = event<Unit>()
                        val footer = slot()
                        val content = slot()
                        val items = slotList()

                        override fun view(): Element = ui.element {
                            footer.mount(this)
                            content.mount(this)
                            items.mount(this)
                        }
                    }

                    fun mountProof(
                        scope: UiScope,
                        title: Readable<String>,
                        count: State<Int>,
                        checked: State<Boolean>,
                        note: Readable<String>,
                    ): Element = scope.proof(
                        title = source(title),
                        subtitle = constant("required"),
                        nullable = constant(null),
                        count = formula { count.value * 2 },
                        checked = bind(checked),
                        requiredValue = constant(7),
                        note = source(note),
                        onChanged = {},
                        onClosed = {},
                        footer = { element() },
                        items = { element(); element() },
                    ) {
                        element()
                    }
                    """
                        .trimIndent(),
                )
            )

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated =
            compilation.kspSourcesDir.walkTopDown().single { it.name == "ProofDsl.kt" }.readText()
        assertContains(generated, "public fun UiScope.`proof`(")
        assertContains(generated, "`title`: PropInput<kotlin.String>")
        assertContains(generated, "`nullable`: PropInput<kotlin.String?>")
        assertContains(generated, "`checked`: BindingInput<kotlin.Boolean>")
        assertContains(generated, "`onChanged`: ((kotlin.Boolean) -> kotlin.Unit)?")
        assertContains(generated, "`onClosed`: (() -> kotlin.Unit)?")
        assertContains(generated, "`footer`: UiScope.() -> Unit = {}")
        assertContains(generated, "`items`: UiScope.() -> Unit = {}")
        assertContains(generated, "`content`: UiScope.() -> Unit = {}")
        assertContains(generated, "`content`: UiScope.() -> Unit = {},\n): Element")
        assertFalse("Builder" in generated)
        assertFalse("Any" in generated)
        assertTrue(generated.length < 6_000, "generated source was ${generated.length} chars")
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
    fun `returns declared component output after mounting`() {
        val compilation =
            compilation(
                SourceFile.kotlin(
                    "Output.kt",
                    """
                    package proof

                    import com.antepod.lumentika.component.*
                    import com.antepod.lumentika.runtime.*

                    class Handle(val element: Element)

                    @UIComponent
                    class Output : Component(), ComponentOutput<Handle> {
                        override lateinit var componentOutput: Handle

                        override fun view(): Element = ui.element().also {
                            componentOutput = Handle(it)
                        }
                    }

                    fun mountOutput(scope: UiScope): Handle = scope.output()
                    """
                        .trimIndent(),
                )
            )

        val result = compilation.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated =
            compilation.kspSourcesDir.walkTopDown().single { it.name == "OutputDsl.kt" }.readText()
        assertContains(generated, "): proof.Handle {")
        assertContains(generated, "instance.mount(this)")
        assertContains(generated, "return instance.componentOutput")
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
