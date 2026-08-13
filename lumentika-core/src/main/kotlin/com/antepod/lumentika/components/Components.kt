package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.gesture.DragAxis
import com.antepod.lumentika.gesture.DragRecognizer
import com.antepod.lumentika.gesture.GestureArena
import com.antepod.lumentika.gesture.GestureRecognizer
import com.antepod.lumentika.gesture.NestedScrollConnection
import com.antepod.lumentika.gesture.ScrollAxis
import com.antepod.lumentika.gesture.ScrollDelta
import com.antepod.lumentika.gesture.ScrollSource
import com.antepod.lumentika.gesture.ScrollState
import com.antepod.lumentika.gesture.ScrollbarController
import com.antepod.lumentika.gesture.SelectionDragRecognizer
import com.antepod.lumentika.gesture.TapRecognizer
import com.antepod.lumentika.gesture.TextSelectionRecognizer
import com.antepod.lumentika.input.EventType
import com.antepod.lumentika.input.FocusCause
import com.antepod.lumentika.input.FocusProperties
import com.antepod.lumentika.input.KeyboardEvent
import com.antepod.lumentika.input.LogicalKey
import com.antepod.lumentika.input.PointerType
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
import com.antepod.lumentika.style.Position
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

public class ControlGestureHandle(
    public val recognizer: GestureRecognizer,
    private val pointerStarted: (PointerType) -> Unit = {},
) : AutoCloseable {
    public fun down(
        pointer: Int,
        point: Point,
        timeNanos: Long,
        arena: GestureArena = GestureArena(),
        pointerType: PointerType = PointerType.UNKNOWN,
    ) {
        pointerStarted(pointerType)
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
public val ScrollRuntimeAttachment: AttachmentKey<ScrollRuntimeHandle> = AttachmentKey()
public val TooltipRuntimeAttachment: AttachmentKey<TooltipRuntimeHandle> = AttachmentKey()
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
    explicitConnection: NestedScrollConnection?,
): Element = element.also {
    val parentScroll = {
        generateSequence(element.parent) { it.parent }
            .mapNotNull { it.attachment(ScrollRuntimeAttachment) }
            .firstOrNull()
    }
    val connection =
        explicitConnection
            ?: object : NestedScrollConnection {
                override fun postScroll(
                    consumed: ScrollDelta,
                    remaining: ScrollDelta,
                    source: ScrollSource,
                ): ScrollDelta =
                    parentScroll()?.state?.scroll(remaining, source) ?: ScrollDelta(0f, 0f)

                override fun postFling(
                    consumed: ScrollDelta,
                    remaining: ScrollDelta,
                ): ScrollDelta {
                    val parent = parentScroll() ?: return ScrollDelta(0f, 0f)
                    parent.state.fling(remaining, gestures, context.animationClock)
                    return remaining
                }
            }
    var dragSource = ScrollSource.TOUCH_DRAG
    val handle =
        ControlGestureHandle(
            DragRecognizer(
                gestures,
                DragAxis.VERTICAL,
                onUpdate = { update ->
                    state.scroll(
                        ScrollDelta(-update.delta.x, -update.delta.y),
                        dragSource,
                        connection,
                    )
                },
                onEnd = { velocity ->
                    state.fling(
                        ScrollDelta(-velocity.x, -velocity.y),
                        gestures,
                        context.animationClock,
                        connection,
                    )
                    context.requestFrame(false)
                },
            ),
            pointerStarted = { type ->
                dragSource =
                    if (type == PointerType.PEN) ScrollSource.PEN_DRAG else ScrollSource.TOUCH_DRAG
            },
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
                    state.scroll(
                        ScrollDelta(event.deltaX, event.deltaY),
                        ScrollSource.WHEEL,
                        connection,
                    )
                }
            },
        )
    }
    val runtime = ScrollRuntimeHandle(element, state)
    element.attach(ScrollRuntimeAttachment, runtime)
    context.focus?.let { focus ->
        focus.configure(element, FocusProperties(focusable = true))
        element.scope.own { focus.unconfigure(element) }
    }
    context.events?.let { events ->
        val keyListener =
            events.on(element, EventType.KEY_DOWN) { event ->
                event as KeyboardEvent
                val delta =
                    when (event.logicalKey) {
                        LogicalKey.ARROW_UP -> ScrollDelta(0f, -40f)
                        LogicalKey.ARROW_DOWN -> ScrollDelta(0f, 40f)
                        LogicalKey.ARROW_LEFT -> ScrollDelta(-40f, 0f)
                        LogicalKey.ARROW_RIGHT -> ScrollDelta(40f, 0f)
                        LogicalKey.HOME -> ScrollDelta(-state.x, -state.y)
                        LogicalKey.END -> ScrollDelta(state.maxX - state.x, state.maxY - state.y)
                        else -> return@on
                    }
                if (state.scroll(delta, ScrollSource.KEYBOARD, connection) != ScrollDelta(0f, 0f)) {
                    event.preventDefault()
                }
            }
        element.scope.own(keyListener::close)
    }
    val semanticStep = { direction: Float ->
        state.scroll(
            ScrollDelta(0f, runtime.viewportHeight * .8f * direction),
            ScrollSource.ACCESSIBILITY,
            connection,
        ) != ScrollDelta(0f, 0f)
    }
    element.attach(
        SemanticsAttachment,
        SemanticsConfiguration(
            role = SemanticRole.SCROLL_VIEW,
            actions =
                mapOf(
                    SemanticAction.SCROLL_FORWARD to { semanticStep(1f) },
                    SemanticAction.SCROLL_BACKWARD to { semanticStep(-1f) },
                ),
        ),
    )
    updateRender()
}

public class ScrollRuntimeHandle
internal constructor(
    private val element: Element,
    public val state: ScrollState,
) {
    public val horizontal: ScrollbarController = ScrollbarController(state, ScrollAxis.HORIZONTAL)
    public val vertical: ScrollbarController = ScrollbarController(state, ScrollAxis.VERTICAL)
    public var viewportWidth: Float = 0f
        private set

    public var viewportHeight: Float = 0f
        private set

    public fun updateLayout() {
        viewportWidth = element.geometry.width
        viewportHeight = element.geometry.height
        val descendants = element.children.flatMap(::flatten)
        val contentRight = descendants.maxOfOrNull { it.geometry.right } ?: element.geometry.x
        val contentBottom = descendants.maxOfOrNull { it.geometry.bottom } ?: element.geometry.y
        val contentWidth = (contentRight - element.geometry.x).coerceAtLeast(viewportWidth)
        val contentHeight = (contentBottom - element.geometry.y).coerceAtLeast(viewportHeight)
        state.setRange(
            (contentWidth - viewportWidth).coerceAtLeast(0f),
            (contentHeight - viewportHeight).coerceAtLeast(0f),
        )
        horizontal.updateExtents(viewportWidth, contentWidth)
        vertical.updateExtents(viewportHeight, contentHeight)
        if (element.kind == "list") updateListSemantics()
    }

    private fun updateListSemantics() {
        val items =
            element.children
                .filter { it.isMounted }
                .flatMap { child ->
                    if (child.kind == "for-each") child.children.filter { it.isMounted }
                    else listOf(child)
                }
        val rootSemantics = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
        element.attach(
            SemanticsAttachment,
            rootSemantics.copy(
                role = SemanticRole.LIST,
                collection = CollectionInfo(rows = items.size, columns = 1),
            ),
        )
        items.forEachIndexed { index, item ->
            val semantics = item.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
            item.attach(
                SemanticsAttachment,
                semantics.copy(
                    role =
                        if (semantics.role == SemanticRole.NONE) SemanticRole.LIST_ITEM
                        else semantics.role,
                    item = CollectionItemInfo(row = index, column = 0),
                ),
            )
        }
    }

    private fun flatten(current: Element): List<Element> =
        listOf(current) + current.children.flatMap(::flatten)
}

public fun UiScope.text(value: String): Element =
    element("text", TextContent(value, context.textLayout))

public fun UiScope.text(value: Readable<String>): Element = text { value(value) }

public fun UiScope.image(
    source: ImageSource,
    size: com.antepod.lumentika.geometry.Size? = null,
): Element = element("image", ImageContent(source, size ?: context.images?.intrinsicSize(source)))

public enum class TooltipPlacement {
    AUTO,
    ABOVE,
    BELOW,
}

public data class TooltipPlacementRequest(
    val anchorBounds: Rect,
    val placement: TooltipPlacement,
    val offset: Float,
)

public class TooltipRuntimeHandle
internal constructor(
    private val wrapper: Element,
    private val context: UiContext,
    private val text: Readable<String>,
    private val showDelayNanos: Long,
    private val hideDelayNanos: Long,
    private val preferredPlacement: TooltipPlacement,
    private val offset: Float,
) : AutoCloseable {
    private val cleanups = mutableListOf<AutoCloseable>()
    private var desiredVisible = false
    private var deadline = 0L
    private var placementFrames = 0
    private var closed = false
    private val tick: (Long) -> Boolean = ::onFrame
    public var popup: Element? = null
        private set

    public var placementRequest: TooltipPlacementRequest? = null
        private set

    public val visible: Boolean
        get() = popup != null

    init {
        context.events?.let { events ->
            cleanups += events.on(wrapper, EventType.POINTER_ENTER) { requestVisible(true) }
            cleanups += events.on(wrapper, EventType.POINTER_LEAVE) { requestVisible(false) }
            cleanups += events.on(wrapper, EventType.FOCUS_IN) { requestVisible(true) }
            cleanups += events.on(wrapper, EventType.FOCUS_OUT) { requestVisible(false) }
            cleanups +=
                events.on(wrapper, EventType.KEY_DOWN) { event ->
                    if ((event as KeyboardEvent).logicalKey == LogicalKey.ESCAPE) hideNow()
                }
        }
        withComponentScope(wrapper.scope) {
            effect {
                val value = text.value
                popup?.content = TextContent(value, context.textLayout)
                wrapper.children
                    .firstOrNull { it !== popup }
                    ?.let { anchor ->
                        val semantics =
                            anchor.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
                        anchor.attach(SemanticsAttachment, semantics.copy(hint = value))
                    }
                context.requestFrame(popup != null)
            }
        }
    }

    public fun requestVisible(visible: Boolean) {
        if (closed) return
        desiredVisible = visible
        val delay = if (visible) showDelayNanos else hideDelayNanos
        deadline = context.animationClock.frameTimeNanos + delay
        context.animationClock.animate(tick)
        context.requestFrame(false)
    }

    public fun showNow() {
        if (closed || popup != null) return
        val popup =
            Element("tooltip-popup").also {
                it.content = TextContent(text.value, context.textLayout)
                it.attach(
                    SemanticsAttachment,
                    SemanticsConfiguration(
                        role = SemanticRole.TOOLTIP,
                        liveRegion = LiveRegion.POLITE,
                    ),
                )
                wrapper.append(it)
                context.attachStyle(it, style { position = Position.ABSOLUTE })
                context.configureRender(it, RenderProperties(topLayer = true))
            }
        this.popup = popup
        placementFrames = 2
        updatePlacement()
        context.requestFrame(true)
    }

    public fun hideNow() {
        val popup = popup ?: return
        this.popup = null
        placementRequest = null
        wrapper.remove(popup)
        context.requestFrame(true)
    }

    private fun onFrame(timeNanos: Long): Boolean {
        if (closed) return false
        if (timeNanos >= deadline) {
            if (desiredVisible) showNow() else hideNow()
        }
        if (popup != null && placementFrames > 0) {
            placementFrames--
            updatePlacement()
            context.requestFrame(false)
        }
        return timeNanos < deadline || placementFrames > 0
    }

    private fun updatePlacement() {
        val popup = popup ?: return
        val anchor = wrapper.children.firstOrNull { it !== popup } ?: wrapper
        val anchorBounds = context.committedBounds(anchor) ?: anchor.geometry
        val popupHeight = popup.geometry.height
        val placement =
            when (preferredPlacement) {
                TooltipPlacement.AUTO ->
                    if (anchorBounds.y >= popupHeight + offset) TooltipPlacement.ABOVE
                    else TooltipPlacement.BELOW
                else -> preferredPlacement
            }
        val targetY =
            if (placement == TooltipPlacement.ABOVE) anchorBounds.y - popupHeight - offset
            else anchorBounds.bottom + offset
        placementRequest = TooltipPlacementRequest(anchorBounds, placement, offset)
        context.configureRender(
            popup,
            RenderProperties(
                transform =
                    Matrix3.translation(
                        anchorBounds.x - popup.geometry.x,
                        targetY - popup.geometry.y,
                    ),
                topLayer = true,
            ),
        )
    }

    override fun close() {
        if (closed) return
        closed = true
        cleanups.asReversed().forEach(AutoCloseable::close)
        cleanups.clear()
        hideNow()
    }
}

internal fun UiScope.mountTooltip(
    element: Element,
    text: Readable<String>,
    showDelayMillis: Long,
    hideDelayMillis: Long,
    placement: TooltipPlacement,
    offset: Float,
) {
    val runtime =
        TooltipRuntimeHandle(
            element,
            context,
            text,
            showDelayMillis * 1_000_000,
            hideDelayMillis * 1_000_000,
            placement,
            offset,
        )
    element.attach(TooltipRuntimeAttachment, runtime)
}

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
