package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.gesture.DragAxis
import com.antepod.lumentika.gesture.DragRecognizer
import com.antepod.lumentika.gesture.GestureArena
import com.antepod.lumentika.gesture.GestureRecognizer
import com.antepod.lumentika.gesture.ScrollDelta
import com.antepod.lumentika.gesture.ScrollSource
import com.antepod.lumentika.gesture.ScrollState
import com.antepod.lumentika.gesture.SelectionDragRecognizer
import com.antepod.lumentika.gesture.TapRecognizer
import com.antepod.lumentika.gesture.TextSelectionRecognizer
import com.antepod.lumentika.input.EventType
import com.antepod.lumentika.input.FocusCause
import com.antepod.lumentika.input.FocusProperties
import com.antepod.lumentika.input.KeyboardEvent
import com.antepod.lumentika.platform.GestureConfiguration
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.AutofillConfiguration
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditorRuntime
import com.antepod.lumentika.text.TextInputConfiguration
import com.antepod.lumentika.text.TextLayoutRequest

public class ControlHandle(
    public val element: Element,
    private val initialSemantics: SemanticsConfiguration,
    public val activate: () -> Unit = {},
    public val gestures: ControlGestureHandle? = null,
) {
    public val semantics: SemanticsConfiguration
        get() = element.attachment(SemanticsAttachment) ?: initialSemantics
}

public class ControlGestureHandle(public val recognizer: GestureRecognizer) : AutoCloseable {
    public fun down(
        pointer: Int,
        point: Point,
        timeNanos: Long,
        arena: GestureArena = GestureArena(),
    ) {
        arena.add(pointer, recognizer)
        recognizer.down(point, timeNanos)
    }

    public fun move(point: Point, timeNanos: Long) = recognizer.move(point, timeNanos)

    public fun up(point: Point, timeNanos: Long) = recognizer.up(point, timeNanos)

    public fun advance(timeNanos: Long) {
        when (val value = recognizer) {
            is SelectionDragRecognizer -> value.advance(timeNanos)
            is TextSelectionRecognizer -> value.advance(timeNanos)
            else -> Unit
        }
    }

    override fun close() = recognizer.close()
}

public val GestureAttachment: AttachmentKey<ControlGestureHandle> = AttachmentKey()
public val TextEditorAttachment: AttachmentKey<TextEditorRuntime> = AttachmentKey()
public val ReceiveContentAttachment: AttachmentKey<(TransferContent) -> TransferContent> =
    AttachmentKey()
private val TextFieldIntegrationAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()
private val ScrollIntegrationAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()
private val ScrollWheelAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()

public fun UiScope.block(content: ContainerBuilder.() -> Unit = {}): Element =
    container("block", style {}, content)

public fun UiScope.flex(content: ContainerBuilder.() -> Unit = {}): Element =
    container("flex", style { display = Display.FLEX }, content)

public fun UiScope.row(content: ContainerBuilder.() -> Unit = {}): Element =
    container(
        "row",
        style {
            display = Display.FLEX
            flexDirection = FlexDirection.ROW
        },
        content,
    )

public fun UiScope.column(content: ContainerBuilder.() -> Unit = {}): Element =
    container(
        "column",
        style {
            display = Display.FLEX
            flexDirection = FlexDirection.COLUMN
        },
        content,
    )

public fun UiScope.grid(content: ContainerBuilder.() -> Unit = {}): Element =
    container("grid", style { display = Display.GRID }, content)

public fun UiScope.stack(content: ContainerBuilder.() -> Unit = {}): Element =
    container("stack", style { display = Display.GRID }, content)

internal fun UiScope.mountScroll(
    element: Element,
    state: ScrollState,
    gestures: GestureConfiguration,
): Element = element.also {
    val handle =
        ControlGestureHandle(
            DragRecognizer(
                gestures,
                DragAxis.VERTICAL,
                onUpdate = { update ->
                    state.scroll(
                        ScrollDelta(-update.delta.x, -update.delta.y),
                        ScrollSource.TOUCH_DRAG,
                    )
                },
            )
        )
    element.attach(GestureAttachment, handle)
    val updateRender = {
        context.configureRender(
            element,
            RenderProperties(scrollOffset = Point(state.x, state.y)),
        )
    }
    element.attach(ScrollIntegrationAttachment, state.onChanged { updateRender() })
    context.events?.let { events ->
        element.attach(
            ScrollWheelAttachment,
            events.on(element, EventType.WHEEL) { event ->
                if (!event.defaultPrevented) {
                    event as com.antepod.lumentika.input.WheelEvent
                    state.scroll(ScrollDelta(event.deltaX, event.deltaY), ScrollSource.WHEEL)
                }
            },
        )
    }
    updateRender()
}

public fun UiScope.list(content: ContainerBuilder.() -> Unit = {}): Element =
    container(
        "list",
        style {
            display = Display.FLEX
            flexDirection = FlexDirection.COLUMN
        },
        content,
    )

public fun UiScope.text(value: String): Element =
    element("text", TextContent(value, context.textLayout))

public fun UiScope.text(value: Readable<String>): Element = text { value(value) }

public fun UiScope.image(
    source: ImageSource,
    size: com.antepod.lumentika.geometry.Size? = null,
): Element = element("image", ImageContent(source, size ?: context.images?.intrinsicSize(source)))

private fun control(
    element: Element,
    semantics: SemanticsConfiguration,
    activate: () -> Unit = {},
    gestures: ControlGestureHandle? = null,
): ControlHandle {
    element.attach(SemanticsAttachment, semantics)
    gestures?.let { element.attach(GestureAttachment, it) }
    return ControlHandle(element, semantics, activate, gestures)
}

internal fun UiScope.buttonControl(
    e: Element,
    label: Readable<String>,
    gestures: GestureConfiguration,
    onClick: () -> Unit,
): ControlHandle {
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.BUTTON,
                label = label.value,
                actions =
                    mapOf(
                        SemanticAction.CLICK to
                            {
                                onClick()
                                true
                            }
                    ),
            ),
            onClick,
            ControlGestureHandle(TapRecognizer(gestures, onClick)),
        )
    withComponentScope(e.scope) {
        effect {
            val next = label.value
            e.content = TextContent(next, context.textLayout)
            e.attach(SemanticsAttachment, handle.semantics.copy(label = next))
            context.requestFrame(true)
        }
    }
    return handle
}

internal fun UiScope.checkboxControl(
    e: Element,
    value: Mutable<Boolean>,
    label: String?,
    gestures: GestureConfiguration,
    onChange: (Boolean) -> Unit = {},
): ControlHandle {
    val action = {
        value.value = !value.value
        onChange(value.value)
    }
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.CHECKBOX,
                label = label,
                checked = value.value,
                actions =
                    mapOf(
                        SemanticAction.CLICK to
                            {
                                action()
                                true
                            }
                    ),
            ),
            action,
            ControlGestureHandle(TapRecognizer(gestures, action)),
        )
    withComponentScope(e.scope) {
        effect {
            e.attach(SemanticsAttachment, handle.semantics.copy(checked = value.value))
            context.requestFrame(false)
        }
    }
    return handle
}

internal fun UiScope.sliderControl(
    e: Element,
    value: Mutable<Float>,
    minimum: Float,
    maximum: Float,
    gestures: GestureConfiguration,
    onChange: (Float) -> Unit = {},
): ControlHandle {
    val gesture =
        ControlGestureHandle(
            DragRecognizer(
                gestures,
                DragAxis.HORIZONTAL,
                onUpdate = { update ->
                    val width = e.geometry.width.takeIf { it > 0f } ?: 100f
                    value.value =
                        (value.value + update.delta.x / width * (maximum - minimum)).coerceIn(
                            minimum,
                            maximum,
                        )
                    onChange(value.value)
                },
            )
        )
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.SLIDER,
                range = SemanticRange(value.value, minimum, maximum),
                actions =
                    mapOf(
                        SemanticAction.SET_VALUE to
                            { v ->
                                value.value = (v as Number).toFloat().coerceIn(minimum, maximum)
                                onChange(value.value)
                                true
                            }
                    ),
            ),
            gestures = gesture,
        )
    withComponentScope(e.scope) {
        effect {
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(range = SemanticRange(value.value, minimum, maximum)),
            )
            context.requestFrame(false)
        }
    }
    return handle
}

internal fun UiScope.textFieldControl(
    e: Element,
    controller: TextEditingController,
    gestures: GestureConfiguration,
    multiline: Boolean,
    secure: Boolean,
    placeholder: String?,
    autofill: AutofillConfiguration?,
): ControlHandle {
    val editor =
        TextEditorRuntime(
            controller,
            context.textInput,
            context.textLayout,
            context.animationClock,
            TextInputConfiguration(
                multiline = multiline,
                secure = secure,
                autofillHints = autofill?.hints?.mapTo(mutableSetOf()) { it.name }.orEmpty(),
            ),
            context.clipboard,
        )
    e.attach(TextEditorAttachment, editor)
    e.attach(ReceiveContentAttachment, editor::receive)
    autofill?.let { configured ->
        val configuration = if (secure) configured.copy(sensitive = true) else configured
        context.autofill?.register(e, controller, configuration) {
            context.committedBounds(e) ?: e.geometry
        }
        e.scope.own { context.autofill?.unregister(e) }
    }
    val gesture =
        ControlGestureHandle(
            TextSelectionRecognizer(
                gestures,
                onCaret = { editor.placeCaret(it.relativeTo(e)) },
                onWord = { editor.selectWord(it.relativeTo(e)) },
                onSelectionStart = { editor.placeCaret(it.relativeTo(e)) },
                onSelectionUpdate = { editor.extendSelection(it.relativeTo(e)) },
            )
        )
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.TEXT_FIELD,
                value = controller.value.text,
                textSelection = controller.value.selection,
                password = secure,
            ),
            gestures = gesture,
        )
    val cleanups = mutableListOf<AutoCloseable>()
    context.focus?.let { focus ->
        focus.configure(e, FocusProperties(focusable = true))
        cleanups += AutoCloseable { focus.unconfigure(e) }
        context.events?.let { events ->
            cleanups +=
                events.defaultAction(e, EventType.POINTER_DOWN) {
                    focus.focus(e, FocusCause.POINTER)
                }
            cleanups += events.on(e, EventType.FOCUS) { editor.focus() }
            cleanups += events.on(e, EventType.BLUR) { editor.blur() }
            cleanups +=
                events.defaultAction(e, EventType.KEY_DOWN) { event ->
                    handleEditorKey(editor, event as KeyboardEvent)
                }
        }
    }
    e.attach(
        TextFieldIntegrationAttachment,
        AutoCloseable { cleanups.asReversed().forEach { it.close() } },
    )
    withComponentScope(e.scope) {
        effect {
            val value = controller.value
            val displayText =
                if (value.text.isEmpty()) placeholder.orEmpty()
                else if (secure) "•".repeat(value.text.codePointCount(0, value.text.length))
                else value.text
            e.content = TextContent(TextLayoutRequest(displayText), context.textLayout)
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(
                    value = value.text.takeUnless { secure },
                    textSelection = value.selection,
                ),
            )
            context.requestFrame(true)
        }
    }
    return handle
}

private fun Point.relativeTo(element: Element): Point =
    Point(x - element.geometry.x, y - element.geometry.y)

private fun handleEditorKey(
    editor: TextEditorRuntime,
    event: KeyboardEvent,
) {
    editor.handleKey(event.logicalKey, event.text, event.physicalKey, event.modifiers)
}

private fun UiScope.container(
    kind: String,
    defaultStyle: Style,
    content: ContainerBuilder.() -> Unit,
): Element {
    val element = element(kind)
    context.attachStyle(element, defaultStyle)
    ContainerBuilder(element, context).content()
    return element
}
