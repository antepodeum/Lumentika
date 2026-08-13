package com.antepod.lumentika.text

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.input.KeyModifiers
import com.antepod.lumentika.input.LogicalKey
import com.antepod.lumentika.platform.ClipboardService
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.platform.TransferItem
import com.antepod.lumentika.platform.TransferSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextEditingTest {
    @Test
    fun `editing uses extended graphemes and code point deletion`() {
        val family = "👨‍👩‍👧‍👦"
        val controller =
            TextEditingController(
                TextEditingValue("A${family}B", TextRange(1 + family.length, 1 + family.length))
            )

        controller.deletePreviousGrapheme()
        assertEquals("AB", controller.value.text)
        assertEquals(TextRange(1, 1), controller.value.selection)

        controller.value = TextEditingValue("😀x", TextRange(0, 0))
        controller.apply(TextEditCommand.DeleteSurroundingCodePoints(0, 1))
        assertEquals("x", controller.value.text)

        controller.value = TextEditingValue("x😀", TextRange(3, 3))
        controller.apply(TextEditCommand.DeleteSurroundingCodePoints(1, 0))
        assertEquals("x", controller.value.text)
    }

    @Test
    fun `batch rollback is atomic when a command is invalid`() {
        val controller = TextEditingController(TextEditingValue("abc", TextRange(3, 3)))
        assertFailsWith<IllegalArgumentException> {
            controller.applyBatch(
                listOf(
                    TextEditCommand.CommitText("d"),
                    TextEditCommand.DeleteSurroundingText(-1, 0),
                )
            )
        }
        assertEquals(TextEditingValue("abc", TextRange(3, 3)), controller.value)
    }

    @Test
    fun `keyboard editing delegates visual movement and enforces secure clipboard policy`() {
        val clipboard = MemoryClipboard("paste")
        val layout =
            object : TextLayoutResult {
                override val size = Size(80f, 16f)
                override val lines = listOf(TextLine(TextRange(0, 2), 12f, Rect(0f, 0f, 80f, 16f)))
                override val text = "אב"

                override fun offsetForPoint(point: Point) = 0

                override fun caretRect(offset: Int) = Rect(offset * 8f, 0f, 1f, 16f)

                override fun selectionRects(range: TextRange) = emptyList<Rect>()

                override fun moveCaret(
                    value: TextEditingValue,
                    forward: Boolean,
                ) = (if (forward) 0 else 2) to CaretAffinity.UPSTREAM
            }
        val layoutService =
            object : TextLayoutService {
                override fun layout(request: TextLayoutRequest): TextLayoutResult = layout
            }
        val controller = TextEditingController(TextEditingValue("אב", TextRange(2, 2)))
        val editor =
            TextEditorRuntime(
                controller,
                null,
                layoutService,
                UiAnimationClock(),
                clipboard = clipboard,
            )

        assertTrue(editor.handleKey(LogicalKey.ARROW_RIGHT, null, "ArrowRight", KeyModifiers()))
        assertEquals(TextRange(0, 0), controller.value.selection)

        editor.handleKey(LogicalKey.CHARACTER, "a", "KeyA", KeyModifiers(control = true))
        editor.handleKey(LogicalKey.CHARACTER, "c", "KeyC", KeyModifiers(control = true))
        assertEquals("אב", clipboard.text)
        editor.handleKey(LogicalKey.CHARACTER, "v", "KeyV", KeyModifiers(control = true))
        assertEquals("אב", controller.value.text)

        val secureController = TextEditingController(TextEditingValue("secret", TextRange(0, 6)))
        val secure =
            TextEditorRuntime(
                secureController,
                null,
                HeadlessTextLayoutService,
                UiAnimationClock(),
                TextInputConfiguration(secure = true),
                clipboard,
            )
        clipboard.text = "unchanged"
        secure.handleKey(LogicalKey.CHARACTER, "c", "KeyC", KeyModifiers(control = true))
        secure.handleKey(LogicalKey.CHARACTER, "x", "KeyX", KeyModifiers(control = true))
        assertEquals("unchanged", clipboard.text)
        assertEquals("secret", secureController.value.text)
    }

    @Test
    fun `composition batch reconciliation and session lifecycle trace`() {
        val trace = mutableListOf<String>()
        val service =
            object : TextInputService {
                override fun start(configuration: TextInputConfiguration, client: TextInputClient) =
                    object : TextInputSession {
                        override fun update(value: TextEditingValue) {
                            trace += "update:${value.text}:${value.composition}"
                        }

                        override fun show() {
                            trace += "show"
                        }

                        override fun hide() {
                            trace += "hide"
                        }

                        override fun close() {
                            trace += "close"
                        }
                    }
            }
        val controller = TextEditingController()
        val clock = UiAnimationClock()
        val editor = TextEditorRuntime(controller, service, HeadlessTextLayoutService, clock)
        editor.focus()
        editor.apply(TextEditCommand.SetComposingText("abc"))
        editor.apply(TextEditCommand.FinishComposition)
        controller.applyBatch(
            listOf(TextEditCommand.SetSelection(TextRange(0, 3)), TextEditCommand.CommitText("xyz"))
        )
        controller.reconcileExternal("external")
        clock.frame(500_000_000)
        editor.blur()
        assertEquals("external", controller.value.text)
        assertNull(controller.value.composition)
        assertTrue("show" in trace)
        assertEquals(listOf("hide", "close"), trace.takeLast(2))
        assertFalse(editor.cursorGeometry.visible)
    }

    @Test
    fun `content returns unsupported items and autofill redacts sensitive values`() {
        val controller = TextEditingController()
        val editor =
            TextEditorRuntime(controller, null, HeadlessTextLayoutService, UiAnimationClock())
        val binary = TransferItem("image/png", bytes = byteArrayOf(1))
        val remaining =
            editor.receive(
                TransferContent(
                    listOf(TransferItem("text/plain", text = "hello"), binary),
                    TransferSource.DRAG_DROP,
                )
            )
        assertEquals("hello", controller.value.text)
        assertEquals(listOf(binary), remaining.items)
        val autofill = AutofillRuntime()
        val id =
            autofill.register(
                Any(),
                controller,
                AutofillConfiguration(setOf(AutofillHint.PASSWORD), sensitive = true),
                Rect(1f, 2f, 3f, 4f),
            )
        val (artifact, first) = autofill.commit()
        assertNull(artifact.nodes.single().value)
        assertTrue(id in first.changedNodes)
        assertTrue(autofill.apply(id, "secret"))
        assertEquals("secret", controller.value.text)
        val stable = artifact.nodes.single().id
        assertEquals(stable, autofill.commit().first.nodes.single().id)
    }
}

private class MemoryClipboard(var text: String?) : ClipboardService {
    override fun readText(): String? = text

    override fun writeText(text: String) {
        this.text = text
    }
}
