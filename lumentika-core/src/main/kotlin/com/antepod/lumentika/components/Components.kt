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
import com.antepod.lumentika.input.EventType
import com.antepod.lumentika.input.FocusCause
import com.antepod.lumentika.input.FocusProperties
import com.antepod.lumentika.input.KeyboardEvent
import com.antepod.lumentika.input.LogicalKey
import com.antepod.lumentika.platform.GestureConfiguration
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.text.TextEditCommand
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditorRuntime
import com.antepod.lumentika.text.TextInputConfiguration
import com.antepod.lumentika.text.TextLayoutRequest
import com.antepod.lumentika.text.TextRange

public class ControlHandle(
    public val element: Element,
    public val semantics: SemanticsConfiguration,
    public val activate: () -> Unit = {},
    public val gestures: ControlGestureHandle? = null,
)

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
            else -> Unit
        }
    }

    override fun close() = recognizer.close()
}

public val GestureAttachment: AttachmentKey<ControlGestureHandle> = AttachmentKey()
public val TextEditorAttachment: AttachmentKey<TextEditorRuntime> = AttachmentKey()
private val TextFieldIntegrationAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()

public fun UiScope.block(content: UiScope.() -> Unit = {}): Element =
    element("block", block = content)

public fun UiScope.flex(content: UiScope.() -> Unit = {}): Element =
    element("flex", block = content)

public fun UiScope.row(content: UiScope.() -> Unit = {}): Element = element("row", block = content)

public fun UiScope.column(content: UiScope.() -> Unit = {}): Element =
    element("column", block = content)

public fun UiScope.grid(content: UiScope.() -> Unit = {}): Element =
    element("grid", block = content)

public fun UiScope.stack(content: UiScope.() -> Unit = {}): Element =
    element("stack", block = content)

public fun UiScope.scroll(
    state: ScrollState = ScrollState(),
    gestures: GestureConfiguration = GestureConfiguration(),
    content: UiScope.() -> Unit = {},
): Element =
    element("scroll", block = content).also { element ->
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
    }

public fun UiScope.list(content: UiScope.() -> Unit = {}): Element =
    element("list", block = content)

public fun UiScope.text(value: String): Element =
    element("text", TextContent(value, context.textLayout))

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

public fun UiScope.button(
    label: String,
    gestures: GestureConfiguration = GestureConfiguration(),
    onClick: () -> Unit = {},
): ControlHandle {
    val e = element("button", TextContent(label, context.textLayout))
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.BUTTON,
            label = label,
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
}

public fun UiScope.checkbox(
    value: Mutable<Boolean>,
    gestures: GestureConfiguration = GestureConfiguration(),
): ControlHandle {
    val action = { value.value = !value.value }
    val e = element("checkbox")
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.CHECKBOX,
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
}

public fun UiScope.slider(
    value: Mutable<Float>,
    minimum: Float = 0f,
    maximum: Float = 1f,
    gestures: GestureConfiguration = GestureConfiguration(),
): ControlHandle {
    val e = element("slider")
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
                },
            )
        )
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.SLIDER,
            range = SemanticRange(value.value, minimum, maximum),
            actions =
                mapOf(
                    SemanticAction.SET_VALUE to
                        { v ->
                            value.value = (v as Number).toFloat().coerceIn(minimum, maximum)
                            true
                        }
                ),
        ),
        gestures = gesture,
    )
}

public fun UiScope.textField(
    controller: TextEditingController = TextEditingController(),
    gestures: GestureConfiguration = GestureConfiguration(),
    multiline: Boolean = false,
    secure: Boolean = false,
): ControlHandle {
    val e = element("textField", TextContent(controller.value.text, context.textLayout))
    val editor =
        TextEditorRuntime(
            controller,
            context.textInput,
            context.textLayout,
            context.animationClock,
            TextInputConfiguration(multiline = multiline, secure = secure),
        )
    e.attach(TextEditorAttachment, editor)
    val gesture =
        ControlGestureHandle(
            SelectionDragRecognizer(
                gestures,
                onUpdate = { update ->
                    val layout = context.textLayout.layout(TextLayoutRequest(controller.value.text))
                    val offset = layout.offsetForPoint(update.position)
                    editor.apply(TextEditCommand.SetSelection(TextRange(offset, offset)))
                },
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
                    handleEditorKey(editor, event as KeyboardEvent, multiline)
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
            e.content = TextContent(TextLayoutRequest(value.text), context.textLayout)
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(value = value.text, textSelection = value.selection),
            )
            context.requestFrame(true)
        }
    }
    return handle
}

public fun UiScope.tooltip(text: String, content: UiScope.() -> Unit = {}): Element =
    element("tooltip", block = content).also {
        it.content = TextContent(text, context.textLayout)
    }

private fun handleEditorKey(
    editor: TextEditorRuntime,
    event: KeyboardEvent,
    multiline: Boolean,
) {
    val value = editor.controller.value
    when (event.logicalKey) {
        LogicalKey.BACKSPACE -> editor.deletePreviousGrapheme()
        LogicalKey.DELETE -> editor.apply(TextEditCommand.DeleteSurroundingText(0, 1))
        LogicalKey.ARROW_LEFT -> {
            val offset = (value.selection.start - 1).coerceAtLeast(0)
            editor.apply(TextEditCommand.SetSelection(TextRange(offset, offset)))
        }
        LogicalKey.ARROW_RIGHT -> {
            val offset = (value.selection.end + 1).coerceAtMost(value.text.length)
            editor.apply(TextEditCommand.SetSelection(TextRange(offset, offset)))
        }
        LogicalKey.HOME -> editor.apply(TextEditCommand.SetSelection(TextRange(0, 0)))
        LogicalKey.END -> {
            val end = value.text.length
            editor.apply(TextEditCommand.SetSelection(TextRange(end, end)))
        }
        LogicalKey.CHARACTER -> event.text?.let { editor.apply(TextEditCommand.CommitText(it)) }
        LogicalKey.ENTER -> if (multiline) editor.apply(TextEditCommand.CommitText("\n"))
        else -> Unit
    }
}
