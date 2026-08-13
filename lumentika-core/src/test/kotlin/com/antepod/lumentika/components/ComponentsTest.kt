package com.antepod.lumentika.components

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.component.bind
import com.antepod.lumentika.component.forEach
import com.antepod.lumentika.component.source
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.headlessRoot
import com.antepod.lumentika.input.EventDispatcher
import com.antepod.lumentika.input.PointerType
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.style.Direction
import com.antepod.lumentika.style.HOVER
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.rgb
import com.antepod.lumentika.style.style
import com.antepod.lumentika.style.theme
import com.antepod.lumentika.text.TextAlign
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditingValue
import com.antepod.lumentika.text.TextRange
import kotlin.test.*

class ComponentsTest {
    @Test
    fun `generated text field binds value and reacts to placeholder and enabled props`() {
        val root = Element()
        val value = state("")
        val selection = state<TextRange?>(null)
        val placeholder = state<String?>("before")
        val enabled = state(false)
        var changed: String? = null
        val field =
            UiScope(root)
                .textField(
                    value = bind(value),
                    selection = bind(selection),
                    placeholder = source(placeholder),
                    enabled = source(enabled),
                    onChange = { changed = it },
                )

        assertFalse(field.semantics.enabled)
        placeholder.value = "after"
        enabled.value = true
        assertTrue(field.semantics.enabled)
        assertEquals(
            "after",
            (field.partElement(TextField.Part.PLACEHOLDER)!!.content as TextContent).text,
        )

        val controller = field.element.attachment(TextEditorAttachment)!!.controller
        controller.reconcileExternal("edited")
        assertEquals("edited", value.value)
        assertEquals("edited", changed)
        assertEquals(controller.value.selection, selection.value)
        root.close()
    }

    @Test
    fun `generated slider reacts across range props and binds only value`() {
        val root = Element()
        val value = state(5f)
        val minimum = state(0f)
        val maximum = state(10f)
        val step = state<Float?>(1f)
        val label = state<String?>("Volume")
        val enabled = state(false)
        val slider =
            UiScope(root)
                .slider(
                    value = bind(value),
                    min = source(minimum),
                    max = source(maximum),
                    step = source(step),
                    label = source(label),
                    enabled = source(enabled),
                )

        assertTrue(slider.semantics.actions.isEmpty())
        minimum.value = 6f
        maximum.value = 8f
        label.value = "Level"
        enabled.value = true

        assertEquals(6f, value.value)
        assertEquals(6f, slider.semantics.range?.minimum)
        assertEquals(8f, slider.semantics.range?.maximum)
        assertEquals("Level", slider.semantics.label)
        slider.semantics.actions.getValue(SemanticAction.SET_VALUE)(7f)
        assertEquals(7f, value.value)
        root.close()
    }

    @Test
    fun `generated checkbox separates binding from reactive props`() {
        val root = Element()
        val checked = state(false)
        val label = state<String?>("before")
        val enabled = state(false)
        var changed: Boolean? = null
        val checkbox =
            UiScope(root)
                .checkbox(
                    checked = bind(checked),
                    label = source(label),
                    enabled = source(enabled),
                    onChange = { changed = it },
                )

        checkbox.activate()
        assertFalse(checked.value)
        assertNull(changed)

        label.value = "after"
        enabled.value = true
        checkbox.activate()

        assertTrue(checked.value)
        assertEquals(true, changed)
        assertEquals(
            "after",
            (checkbox.partElement(Checkbox.Part.LABEL)!!.content as TextContent).text,
        )
        root.close()
    }

    @Test
    fun `generated button props stay reactive without two way prop semantics`() {
        val root = Element()
        val label = state("before")
        val enabled = state(false)
        var clicks = 0
        val button =
            UiScope(root)
                .button(
                    value = source(label),
                    enabled = source(enabled),
                    onClick = { clicks++ },
                )

        button.activate()
        assertEquals(0, clicks)
        assertFalse(button.semantics.enabled)

        label.value = "after"
        enabled.value = true
        button.activate()

        assertEquals(1, clicks)
        assertTrue(button.semantics.enabled)
        assertEquals(
            "after",
            (button.partElement(Button.Part.LABEL)!!.content as TextContent).text,
        )
        root.close()
    }

    @Test
    fun `theme and instance part styles resolve from owner state on stable elements`() {
        val themedLabel = rgb(10, 20, 30)
        val hoveredLabel = rgb(30, 40, 50)
        val instanceLabel = rgb(50, 60, 70)
        val indicatorPaint = rgb(80, 90, 100)
        val skin = theme {
            style(Button.Part.ROOT, style { background = rgb(200, 200, 200) })
            style(
                Button.Part.LABEL,
                style {
                    background = themedLabel
                    on(HOVER) { opacity = .4f }
                },
            )
            style(Checkbox.Part.INDICATOR, style { background = indicatorPaint })
            style(
                Checkbox.Part.LABEL,
                style { on(ControlStyleState.CHECKED) { background = hoveredLabel } },
            )
        }
        val root = headlessRoot(200f, 100f)
        val label = state("before")
        val checked = state(false)
        lateinit var button: ControlHandle
        lateinit var checkbox: ControlHandle
        root.scope.theme(skin) {
            button =
                button(
                    value = label,
                    partStyles = mapOf(Button.Part.LABEL to style { color = instanceLabel }),
                    style = style { background = rgb(1, 2, 3) },
                    semantics = semantics { this.label = "Themed action" },
                )
            checkbox = checkbox(checked = checked, label = "check")
        }
        root.requestFrame()
        root.frame(1)

        val buttonLabel = button.partElement(Button.Part.LABEL)!!
        val indicator = checkbox.partElement(Checkbox.Part.INDICATOR)!!
        val checkboxLabel = checkbox.partElement(Checkbox.Part.LABEL)!!
        assertEquals(themedLabel, root.styles.resolve(buttonLabel).first[Properties.Background])
        assertEquals(instanceLabel, root.styles.resolve(buttonLabel).first[Properties.Color])
        assertEquals(rgb(1, 2, 3), root.styles.resolve(button.element).first[Properties.Background])
        assertEquals(indicatorPaint, root.styles.resolve(indicator).first[Properties.Background])
        assertEquals(SemanticRole.BUTTON, button.semantics.role)
        assertEquals("Themed action", button.semantics.label)

        val stableLabel = buttonLabel
        val stableIndicatorPaint = root.styles.resolve(indicator).first.paint
        root.styles.setState(button.element, HOVER, true)
        assertEquals(.4f, root.styles.resolve(buttonLabel).first[Properties.Opacity])
        assertSame(stableIndicatorPaint, root.styles.resolve(indicator).first.paint)

        checked.value = true
        label.value = "after"
        root.frame(2)
        assertEquals(
            hoveredLabel,
            root.styles.resolve(checkboxLabel).first[Properties.Background],
        )
        assertSame(stableLabel, button.partElement(Button.Part.LABEL))
        assertEquals("after", (stableLabel.content as TextContent).text)
        root.close()
    }

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
        val root = Element()
        val ui = UiScope(root)
        var clicks = 0
        val button = ui.button(value = "Go", onClick = { clicks++ })
        val checked = state(false)
        val checkbox = ui.checkbox(checked = checked)
        val sliderValue = state(0f)
        val slider = ui.slider(value = sliderValue)
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
    fun `universal components expose readable content semantics and typed theme parts`() {
        val root = Element()
        val ui = UiScope(root)
        val text = ui.text("hello")
        val image = ui.image(ImageSource.Uri("asset"))
        val rootStyle = style { opacity = .5f }
        val theme =
            com.antepod.lumentika.style.theme {
                style(Button.Part.ROOT, rootStyle)
                style(Checkbox.Part.INDICATOR, style {})
                style(Slider.Part.THUMB, style {})
                style(TextField.Part.CURSOR, style {})
                style(Scroll.Part.SCROLLBAR_THUMB, style {})
                style(Tooltip.Part.POPUP, style {})
            }

        assertEquals(SemanticRole.TEXT, text.attachment(SemanticsAttachment)!!.role)
        assertEquals("hello", text.attachment(SemanticsAttachment)!!.label)
        assertEquals(SemanticRole.IMAGE, image.attachment(SemanticsAttachment)!!.role)
        assertSame(rootStyle, theme[Button.Part.ROOT])
        root.close()
    }

    @Test
    fun `interactive controls use shared gesture recognizers`() {
        val root = Element()
        val ui = UiScope(root)
        var clicks = 0
        val button = ui.button(value = "Go", onClick = { clicks++ })
        button.gestures!!.down(1, Point(0f, 0f), 0)
        button.gestures.up(Point(0f, 0f), 1)
        assertEquals(1, clicks)

        val sliderValue = state(0f)
        val slider = ui.slider(value = sliderValue)
        slider.gestures!!.down(2, Point(0f, 0f), 0)
        slider.gestures.move(Point(20f, 0f), 10_000_000)
        assertEquals(0.2f, sliderValue.value)

        val controller = TextEditingController(TextEditingValue("abcd", TextRange(0, 0)))
        val field = ui.textField(controller = controller)
        field.gestures!!.down(3, Point(0f, 0f), 0)
        field.gestures.advance(500_000_000)
        field.gestures.move(Point(16f, 0f), 510_000_000)
        assertEquals(TextRange(0, 2), controller.value.selection)

        root.close()
    }

    @Test
    fun `nested mounted scroll chains unconsumed drag to parent`() {
        val root = Element()
        val outerState = com.antepod.lumentika.gesture.ScrollState()
        val innerState = com.antepod.lumentika.gesture.ScrollState()
        lateinit var inner: Element
        UiScope(root).scroll(state = outerState) {
            inner = scroll(state = innerState)
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
        val root = Element()
        val ui = UiScope(root)
        val checked = state(false)
        val sliderValue = state(0.25f)
        val checkbox = ui.checkbox(checked = checked)
        val slider = ui.slider(value = sliderValue)

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
        val root = Element()
        val ui = UiScope(root)
        val direct = ui.text("direct")
        val readableValue = state("readable-1")
        val readable = ui.text(readableValue)
        val computedValue = state(1)
        val computed = ui.text(value = { "computed-${computedValue.value}" })
        val configured =
            ui.text(
                value = "configured",
                alignment = TextAlign.END,
                direction = Direction.RTL,
                semantics = semantics { label = "configured label" },
            )

        assertEquals("direct", (direct.content as TextContent).text)
        assertEquals("readable-1", (readable.content as TextContent).text)
        assertEquals("computed-1", (computed.content as TextContent).text)
        assertEquals("configured", (configured.content as TextContent).text)
        assertEquals(TextAlign.END, (configured.content as TextContent).request.alignment)
        assertEquals(Direction.RTL, (configured.content as TextContent).request.direction)
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
    fun `typed control arguments preserve reactive values and two way bindings`() {
        val root = Element()
        val ui = UiScope(root)
        val buttonLabel = state("before")
        var clicks = 0
        val button = ui.button(value = buttonLabel, onClick = { clicks++ })
        val checked = state(false)
        var checkboxChange: Boolean? = null
        val checkbox = ui.checkbox(checked = checked, onChange = { checkboxChange = it })
        val sliderValue = state(0.25f)
        val slider = ui.slider(value = sliderValue)
        val fieldValue = state("initial")
        val selection = state(TextRange(0, 0))
        val field = ui.textField(value = fieldValue, selection = selection)
        val placeholderField = ui.textField(placeholder = "Name")

        buttonLabel.value = "after"
        button.activate()
        checkbox.activate()
        slider.semantics.actions.getValue(SemanticAction.SET_VALUE)(0.75f)
        val fieldController = field.element.attachment(TextEditorAttachment)!!.controller
        fieldController.reconcileExternal("edited")
        selection.value = TextRange(1, 1)

        assertEquals("after", (button.partElement(Button.Part.LABEL)!!.content as TextContent).text)
        assertEquals(1, clicks)
        assertTrue(checked.value)
        assertEquals(true, checkboxChange)
        assertEquals(0.75f, sliderValue.value)
        assertEquals("edited", fieldValue.value)
        assertEquals(TextRange(1, 1), fieldController.value.selection)
        assertEquals(
            "Name",
            (placeholderField.partElement(TextField.Part.PLACEHOLDER)!!.content as TextContent)
                .text,
        )
        assertEquals("", placeholderField.semantics.value)
        root.close()
    }

    @Test
    fun `secure text field masks paint content and redacts semantics`() {
        val root = Element()
        val field = UiScope(root).textField(value = "s😀", secure = true)

        assertEquals("••", (field.partElement(TextField.Part.TEXT)!!.content as TextContent).text)
        assertNull(field.semantics.value)
        assertTrue(field.semantics.password)
        root.close()
    }

    @Test
    fun `image and tooltip arguments update reactive primary values`() {
        val root = Element()
        val clock = UiAnimationClock()
        val renderProperties = mutableMapOf<Element, RenderProperties>()
        val context =
            UiContext(
                animationClock = clock,
                events = EventDispatcher(root),
                committedBounds = { com.antepod.lumentika.geometry.Rect(10f, 20f, 40f, 10f) },
                configureRender = { element, properties -> renderProperties[element] = properties },
            )
        val ui = UiScope(root, context)
        val imageSource = state<ImageSource>(ImageSource.Uri("first"))
        val image = ui.image(source = imageSource)
        val tooltipText = state("first tip")
        val tooltip =
            ui.tooltip(value = tooltipText) {
                text("anchor")
            }

        imageSource.value = ImageSource.Uri("second")
        tooltipText.value = "second tip"

        assertEquals(ImageSource.Uri("second"), (image.content as ImageContent).source)
        assertEquals("anchor", (tooltip.children.single().content as TextContent).text)
        val runtime = tooltip.attachment(TooltipRuntimeAttachment)!!
        runtime.requestVisible(true)
        clock.frame(499_000_000)
        assertFalse(runtime.visible)
        clock.frame(500_000_000)
        val popup = runtime.popup!!
        assertEquals("second tip", (popup.content as TextContent).text)
        assertTrue(renderProperties.getValue(popup).topLayer)
        assertEquals(SemanticRole.TOOLTIP, popup.attachment(SemanticsAttachment)!!.role)
        assertEquals("second tip", tooltip.children.first().attachment(SemanticsAttachment)!!.hint)
        runtime.requestVisible(false)
        clock.frame(599_000_000)
        assertTrue(runtime.visible)
        clock.frame(600_000_000)
        assertFalse(runtime.visible)
        root.close()
    }

    @Test
    fun `list composes scrolling keyed items and collection semantics`() {
        val root = Element()
        val items = state(listOf(1, 2, 3))
        val list =
            UiScope(root).list {
                forEach(items, key = { it }) { value -> text("item-$value") }
            }
        val runtime = list.attachment(ScrollRuntimeAttachment)!!
        runtime.updateLayout()

        assertEquals(3, list.attachment(SemanticsAttachment)!!.collection!!.rows)
        val keyedContainer =
            list.children.single { it.attachment(CollectionItemContainerAttachment) != null }
        val keyedItems = keyedContainer.children
        assertEquals(
            listOf(0, 1, 2),
            keyedItems.map { it.attachment(SemanticsAttachment)!!.item!!.row },
        )
        val identity = keyedItems.associateBy { (it.children.single().content as TextContent).text }
        items.value = listOf(3, 1)
        runtime.updateLayout()
        assertSame(identity.getValue("item-3"), keyedContainer.children[0])
        assertSame(identity.getValue("item-1"), keyedContainer.children[1])
        assertEquals(2, list.attachment(SemanticsAttachment)!!.collection!!.rows)
        root.close()
    }

    @Test
    fun `all universal components mount expected behavior and semantics`() {
        val root = Element()
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
                ui.button(value = "button").element,
                ui.checkbox(checked = false).element,
                ui.slider(value = 0f).element,
                ui.textField().element,
                ui.tooltip(value = "tip"),
            )
        assertEquals(15, elements.size)
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
