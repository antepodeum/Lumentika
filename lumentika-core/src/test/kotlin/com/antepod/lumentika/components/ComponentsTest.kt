package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditingValue
import com.antepod.lumentika.text.TextRange
import kotlin.test.*

class ComponentsTest {
    @Test
    fun `controls attach default semantics and actions`() {
        val root = Element("root")
        val ui = UiScope(root)
        var clicks = 0
        val button = ui.button("Go") { clicks++ }
        val checked = state(false)
        val checkbox = ui.checkbox(checked)
        val sliderValue = state(0f)
        val slider = ui.slider(sliderValue)
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
        val button = ui.button("Go") { clicks++ }
        button.gestures!!.down(1, Point(0f, 0f), 0)
        button.gestures.up(Point(0f, 0f), 1)
        assertEquals(1, clicks)

        val sliderValue = state(0f)
        val slider = ui.slider(sliderValue)
        slider.gestures!!.down(2, Point(0f, 0f), 0)
        slider.gestures.move(Point(20f, 0f), 10_000_000)
        assertEquals(0.2f, sliderValue.value)

        val controller = TextEditingController(TextEditingValue("abcd", TextRange(0, 0)))
        val field = ui.textField(controller)
        field.gestures!!.down(3, Point(0f, 0f), 0)
        field.gestures.advance(500_000_000)
        field.gestures.move(Point(16f, 0f), 510_000_000)
        assertEquals(TextRange(2, 2), controller.value.selection)

        root.close()
    }

    @Test
    fun `stateful control semantics follow bound values`() {
        val root = Element("root")
        val ui = UiScope(root)
        val checked = state(false)
        val sliderValue = state(0.25f)
        val checkbox = ui.checkbox(checked)
        val slider = ui.slider(sliderValue)

        checked.value = true
        sliderValue.value = 0.75f

        assertEquals(true, checkbox.semantics.checked)
        assertEquals(0.75f, slider.semantics.range?.current)
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
                ui.button("button").element,
                ui.checkbox(state(false)).element,
                ui.slider(state(0f)).element,
                ui.textField().element,
                ui.tooltip("tip"),
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
