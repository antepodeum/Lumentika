package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.headlessRoot
import com.antepod.lumentika.input.PointerType
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditingValue
import com.antepod.lumentika.text.TextRange
import kotlin.test.*

class ComponentsTest {
    @Test
    fun `layout primitives install real default Taffy display modes`() {
        val root = headlessRoot(100f, 100f)
        lateinit var first: Element
        lateinit var second: Element
        root.scope.row {
            first = block()
            second = block()
        }
        listOf(first, second).forEach {
            root.styles.attach(
                it,
                state(
                    style {
                        width = 10.px
                        height = 10.px
                    }
                ),
            )
        }
        root.requestFrame()
        root.frame(1)

        assertEquals(0f, first.geometry.x)
        assertEquals(10f, second.geometry.x)
        assertEquals(first.geometry.y, second.geometry.y)
        root.close()
    }

    @Test
    fun `controls attach default semantics and actions`() {
        val root = Element("root")
        val ui = UiScope(root)
        var clicks = 0
        val button = ui.button {
            value = "Go"
            onClick { clicks++ }
        }
        val checked = state(false)
        val checkbox = ui.checkbox { bindValue(checked) }
        val sliderValue = state(0f)
        val slider = ui.slider { bindValue(sliderValue) }
        val field = ui.textField()
        assertEquals(
            listOf(
                SemanticRole.BUTTON,
                SemanticRole.CHECKBOX,
                SemanticRole.SLIDER,
                SemanticRole.TEXT_FIELD,
            ),
            listOf(button, checkbox, slider, field).map {
                it.element.attachment(SemanticsAttachment)!!.role
            },
        )
        button.activate()
        checkbox.activate()
        slider.semantics.actions.getValue(SemanticAction.SET_VALUE)(.75f)
        assertEquals(1, clicks)
        assertTrue(checked.value)
        assertEquals(.75f, sliderValue.value)
    }

    @Test
    fun `interactive controls use shared gesture recognizers`() {
        val root = Element("root")
        val ui = UiScope(root)
        var clicks = 0
        val button = ui.button {
            value = "Go"
            onClick { clicks++ }
        }
        button.gestures!!.down(1, Point(0f, 0f), 0)
        button.gestures.up(Point(0f, 0f), 1)
        assertEquals(1, clicks)

        val sliderValue = state(0f)
        val slider = ui.slider { bindValue(sliderValue) }
        slider.gestures!!.down(2, Point(0f, 0f), 0)
        slider.gestures.move(Point(20f, 0f), 10_000_000)
        assertEquals(0.2f, sliderValue.value)

        val controller = TextEditingController(TextEditingValue("abcd", TextRange(0, 0)))
        val field = ui.textField { this.controller = controller }
        field.gestures!!.down(3, Point(0f, 0f), 0)
        field.gestures.advance(500_000_000)
        field.gestures.move(Point(16f, 0f), 510_000_000)
        assertEquals(TextRange(0, 2), controller.value.selection)

        root.close()
    }

    @Test
    fun `nested mounted scroll chains unconsumed drag to parent`() {
        val root = Element("root")
        val outerState = com.antepod.lumentika.gesture.ScrollState()
        val innerState = com.antepod.lumentika.gesture.ScrollState()
        lateinit var inner: Element
        UiScope(root).scroll {
            state = outerState
            inner = scroll { state = innerState }
        }
        outerState.setRange(0f, 100f)
        innerState.setRange(0f, 10f)
        innerState.scroll(
            com.antepod.lumentika.gesture.ScrollDelta(0f, 10f),
            com.antepod.lumentika.gesture.ScrollSource.PROGRAMMATIC,
        )
        val gesture = inner.attachment(GestureAttachment)!!
        gesture.down(1, Point(0f, 0f), 0, pointerType = PointerType.TOUCH)
        gesture.move(Point(0f, -20f), 10_000_000)

        assertEquals(10f, innerState.y)
        assertEquals(20f, outerState.y)
        root.close()
    }

    @Test
    fun `stateful control semantics follow bound values`() {
        val root = Element("root")
        val ui = UiScope(root)
        val checked = state(false)
        val sliderValue = state(0.25f)
        val checkbox = ui.checkbox { bindValue(checked) }
        val slider = ui.slider { bindValue(sliderValue) }

        checked.value = true
        sliderValue.value = 0.75f

        assertEquals(true, checkbox.semantics.checked)
        assertEquals(0.75f, slider.semantics.range?.current)
        root.close()
    }

    @Test
    fun `reactive text replaces content without remounting element`() {
        val root = headlessRoot(100f, 100f)
        val value = state("first")
        val text = root.scope.text(value)
        root.requestFrame()
        root.frame(1)

        value.value = "second"
        root.frame(2)

        assertSame(text, root.element.children.single())
        assertEquals("second", (text.content as TextContent).text)
        assertTrue(
            root.committedRender.paint.chunks
                .flatMap { it.commands }
                .filterIsInstance<PaintCommand.DrawText>()
                .any { it.text == "second" }
        )
        root.close()
    }

    @Test
    fun `text has one compact form and one configured form`() {
        val root = Element("root")
        val ui = UiScope(root)
        val direct = ui.text("direct")
        val readableValue = state("readable-1")
        val readable = ui.text(readableValue)
        val computedValue = state(1)
        val computed = ui.text { "computed-${computedValue.value}" }
        val configured = ui.text {
            value = "configured"
            semantics { label = "configured label" }
        }

        assertEquals("direct", (direct.content as TextContent).text)
        assertEquals("readable-1", (readable.content as TextContent).text)
        assertEquals("computed-1", (computed.content as TextContent).text)
        assertEquals("configured", (configured.content as TextContent).text)
        assertEquals(
            "configured label",
            configured.attachment(SemanticsAttachment)?.label,
        )

        readableValue.value = "readable-2"
        computedValue.value = 2

        assertEquals("readable-2", (readable.content as TextContent).text)
        assertEquals("computed-2", (computed.content as TextContent).text)
        root.close()
    }

    @Test
    fun `uniform control builders preserve reactive values and two way bindings`() {
        val root = Element("root")
        val ui = UiScope(root)
        val buttonLabel = state("before")
        var clicks = 0
        val button = ui.button {
            value(buttonLabel)
            onClick { clicks++ }
        }
        val checked = state(false)
        var checkboxChange: Boolean? = null
        val checkbox = ui.checkbox {
            bindValue(checked)
            onChange { checkboxChange = it }
        }
        val sliderValue = state(0.25f)
        val slider = ui.slider { bindValue(sliderValue) }
        val fieldValue = state("initial")
        val selection = state(TextRange(0, 0))
        val field = ui.textField {
            bindValue(fieldValue)
            bindSelection(selection)
        }
        val placeholderField = ui.textField { placeholder = "Name" }

        buttonLabel.value = "after"
        button.activate()
        checkbox.activate()
        slider.semantics.actions.getValue(SemanticAction.SET_VALUE)(0.75f)
        val fieldController = field.element.attachment(TextEditorAttachment)!!.controller
        fieldController.reconcileExternal("edited")
        selection.value = TextRange(1, 1)

        assertEquals("after", (button.element.content as TextContent).text)
        assertEquals(1, clicks)
        assertTrue(checked.value)
        assertEquals(true, checkboxChange)
        assertEquals(0.75f, sliderValue.value)
        assertEquals("edited", fieldValue.value)
        assertEquals(TextRange(1, 1), fieldController.value.selection)
        assertEquals("Name", (placeholderField.element.content as TextContent).text)
        assertEquals("", placeholderField.semantics.value)
        root.close()
    }

    @Test
    fun `secure text field masks paint content and redacts semantics`() {
        val root = Element("root")
        val field =
            UiScope(root).textField {
                value = "s😀"
                secure = true
            }

        assertEquals("••", (field.element.content as TextContent).text)
        assertNull(field.semantics.value)
        assertTrue(field.semantics.password)
        root.close()
    }

    @Test
    fun `image and tooltip builders update reactive primary values`() {
        val root = Element("root")
        val ui = UiScope(root)
        val imageSource = state<ImageSource>(ImageSource.Uri("first"))
        val image = ui.image { source(imageSource) }
        val tooltipText = state("first tip")
        val tooltip = ui.tooltip {
            value(tooltipText)
            text("anchor")
        }

        imageSource.value = ImageSource.Uri("second")
        tooltipText.value = "second tip"

        assertEquals(ImageSource.Uri("second"), (image.content as ImageContent).source)
        assertEquals("second tip", (tooltip.content as TextContent).text)
        assertEquals("anchor", (tooltip.children.single().content as TextContent).text)
        root.close()
    }

    @Test
    fun `all universal components mount expected behavior and semantics`() {
        val root = Element("root")
        val ui = UiScope(root)
        val elements =
            listOf(
                ui.block(),
                ui.flex(),
                ui.row(),
                ui.column(),
                ui.grid(),
                ui.stack(),
                ui.scroll(),
                ui.list(),
                ui.text("text"),
                ui.image(ImageSource.Bytes(byteArrayOf(1), "image/test")),
                ui.button { value = "button" }.element,
                ui.checkbox { value = false }.element,
                ui.slider { value = 0f }.element,
                ui.textField().element,
                ui.tooltip { value = "tip" },
            )
        assertEquals(
            listOf(
                "block",
                "flex",
                "row",
                "column",
                "grid",
                "stack",
                "scroll",
                "list",
                "text",
                "image",
                "button",
                "checkbox",
                "slider",
                "textField",
                "tooltip",
            ),
            elements.map(Element::kind),
        )
        assertTrue(elements[6].attachment(GestureAttachment) != null)
        assertEquals(
            listOf(
                SemanticRole.BUTTON,
                SemanticRole.CHECKBOX,
                SemanticRole.SLIDER,
                SemanticRole.TEXT_FIELD,
            ),
            elements.slice(10..13).map { it.attachment(SemanticsAttachment)!!.role },
        )
        root.close()
    }
}
