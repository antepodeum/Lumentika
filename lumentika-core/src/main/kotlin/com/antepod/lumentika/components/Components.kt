package com.antepod.lumentika.components

import com.antepod.lumentika.component.Component
import com.antepod.lumentika.component.ComponentOutput
import com.antepod.lumentika.component.UIComponent
import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.gesture.DragAxis
import com.antepod.lumentika.gesture.DragRecognizer
import com.antepod.lumentika.gesture.GestureArena
import com.antepod.lumentika.gesture.GestureRecognizer
import com.antepod.lumentika.gesture.LongPressRecognizer
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
import com.antepod.lumentika.platform.PointerCursorRole
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.platform.UiFeedbackRequest
import com.antepod.lumentika.platform.UiFeedbackType
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.style.ACTIVE
import com.antepod.lumentika.style.DISABLED
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FOCUS
import com.antepod.lumentika.style.FOCUS_VISIBLE
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.HOVER
import com.antepod.lumentika.style.Position
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.style.StylePart
import com.antepod.lumentika.style.StyleState
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.AutofillConfiguration
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditorRuntime
import com.antepod.lumentika.text.TextInputConfiguration
import com.antepod.lumentika.text.TextLayoutRequest

/** Standard button declared through the same component model used by application components. */
@UIComponent
public class Button : Component(), ComponentOutput<ControlHandle> {
    public val value = prop("")
    public val enabled = prop(true)
    public val gestures = prop<GestureConfiguration?>(null)
    public val style = prop<Style?>(null)
    public val partStyles = prop<Map<StylePart<Button>, Style>>(emptyMap())
    public val semantics = prop<ElementSemantics?>(null)
    public val click = event<Unit>()

    override lateinit var componentOutput: ControlHandle

    public object Part {
        public val ROOT: StylePart<Button> = StylePart()
        public val LABEL: StylePart<Button> = StylePart()
        public val ICON: StylePart<Button> = StylePart()
    }

    override fun view(): Element {
        val element = ui.element()
        componentOutput =
            ui.buttonControl(
                element,
                value,
                gestures.value ?: ui.context.gestureConfiguration(),
                enabled,
            ) {
                click.emit(Unit)
            }
        ui.configure(element, style.value, partStyles.value, semantics.value)
        return element
    }
}

/** Standard checkbox declared through the shared component model. */
@UIComponent
public class Checkbox : Component(), ComponentOutput<ControlHandle> {
    public val checked = binding(false)
    public val label = prop<String?>(null)
    public val enabled = prop(true)
    public val gestures = prop<GestureConfiguration?>(null)
    public val style = prop<Style?>(null)
    public val partStyles = prop<Map<StylePart<Checkbox>, Style>>(emptyMap())
    public val semantics = prop<ElementSemantics?>(null)
    public val change = event<Boolean>()

    override lateinit var componentOutput: ControlHandle

    public object Part {
        public val ROOT: StylePart<Checkbox> = StylePart()
        public val INDICATOR: StylePart<Checkbox> = StylePart()
        public val LABEL: StylePart<Checkbox> = StylePart()
    }

    override fun view(): Element {
        val element = ui.element()
        componentOutput =
            ui.checkboxControl(
                element,
                checked,
                label,
                gestures.value ?: ui.context.gestureConfiguration(),
                enabled,
            ) {
                change.emit(it)
            }
        ui.configure(element, style.value, partStyles.value, semantics.value)
        return element
    }
}

/** Stable theme-part namespace for sliders. */
public object Slider {
    public object Part {
        public val ROOT: StylePart<Slider> = StylePart()
        public val TRACK: StylePart<Slider> = StylePart()
        public val THUMB: StylePart<Slider> = StylePart()
        public val LABEL: StylePart<Slider> = StylePart()
    }
}

/** Stable theme-part namespace for text fields. */
public object TextField {
    public object Part {
        public val ROOT: StylePart<TextField> = StylePart()
        public val TEXT: StylePart<TextField> = StylePart()
        public val PLACEHOLDER: StylePart<TextField> = StylePart()
        public val CURSOR: StylePart<TextField> = StylePart()
        public val SELECTION: StylePart<TextField> = StylePart()
        public val SCROLLBAR_TRACK: StylePart<TextField> = StylePart()
        public val SCROLLBAR_THUMB: StylePart<TextField> = StylePart()
    }
}

/** Stable theme-part namespace for scroll containers. */
public object Scroll {
    public object Part {
        public val ROOT: StylePart<Scroll> = StylePart()
        public val SCROLLBAR_TRACK: StylePart<Scroll> = StylePart()
        public val SCROLLBAR_THUMB: StylePart<Scroll> = StylePart()
    }
}

/** Stable theme-part namespace for tooltips. */
public object Tooltip {
    public object Part {
        public val ROOT: StylePart<Tooltip> = StylePart()
        public val POPUP: StylePart<Tooltip> = StylePart()
    }
}

/** Semantic states exposed to standard-control part styles. */
public enum class ControlStyleState : StyleState {
    CHECKED,
    EMPTY,
}

/** Element attachment containing persistent visual-part elements keyed by typed tokens. */
public val VisualPartsAttachment: AttachmentKey<Map<StylePart<*>, Element>> = AttachmentKey()

/** Returns this component's persistent element for [part], when present. */
public fun <T : Any> Element.partElement(part: StylePart<T>): Element? =
    attachment(VisualPartsAttachment)?.get(part)

/** Public interaction and semantics handle returned by control builders. */
public class ControlHandle(
    public val element: Element,
    private val initialSemantics: SemanticsConfiguration,
    public val activate: () -> Unit = {},
    public val gestures: ControlGestureHandle? = null,
) {
    public val semantics: SemanticsConfiguration
        get() = element.attachment(SemanticsAttachment) ?: initialSemantics

    /** Returns persistent visual element for typed [part]. */
    public fun <T : Any> partElement(part: StylePart<T>): Element? = element.partElement(part)
}

/** Drives the shared gesture recognizer attached to a control. */
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
            is LongPressRecognizer -> value.advance(timeNanos)
            is SelectionDragRecognizer -> value.advance(timeNanos)
            is TextSelectionRecognizer -> value.advance(timeNanos)
            else -> Unit
        }
    }

    override fun close() = recognizer.close()
}

/** Element attachment containing the control gesture bridge. */
public val GestureAttachment: AttachmentKey<ControlGestureHandle> = AttachmentKey()

/** Element attachment containing an active text editor runtime. */
public val TextEditorAttachment: AttachmentKey<TextEditorRuntime> = AttachmentKey()

/** Element attachment containing a receive-content handler. */
public val ReceiveContentAttachment: AttachmentKey<(TransferContent) -> TransferContent> =
    AttachmentKey()

/** Element attachment exposing scroll runtime state to the root. */
public val ScrollRuntimeAttachment: AttachmentKey<ScrollRuntimeHandle> = AttachmentKey()

/** Element attachment updating control-part transforms after committed Taffy geometry changes. */
public val ControlVisualLayoutAttachment: AttachmentKey<() -> Unit> = AttachmentKey()

/** Element attachment exposing tooltip lifecycle state. */
public val TooltipRuntimeAttachment: AttachmentKey<TooltipRuntimeHandle> = AttachmentKey()
private val TextFieldIntegrationAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()
private val ScrollIntegrationAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()
private val ScrollWheelAttachment: AttachmentKey<AutoCloseable> = AttachmentKey()

private fun UiScope.mountContainer(
    structuralStyle: Style,
    style: Style?,
    semantics: ElementSemantics?,
    content: UiScope.() -> Unit,
): Element {
    val element = element()
    context.attachStyle(element, structuralStyle)
    style?.let { context.attachStyle(element, it) }
    element.applySemantics(semantics)
    nested(element).content()
    return element
}

/** Mounts a block-layout container; trailing [content] mounts children only. */
public fun UiScope.block(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element = mountContainer(com.antepod.lumentika.style.style {}, style, semantics, content)

/** Mounts a generic flex-layout container; trailing [content] mounts children only. */
public fun UiScope.flex(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountContainer(
        com.antepod.lumentika.style.style { display = Display.FLEX },
        style,
        semantics,
        content,
    )

/** Mounts a horizontal flex container; trailing [content] mounts children only. */
public fun UiScope.row(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountContainer(
        com.antepod.lumentika.style.style {
            display = Display.FLEX
            flexDirection = FlexDirection.ROW
        },
        style,
        semantics,
        content,
    )

/** Mounts a vertical flex container; trailing [content] mounts children only. */
public fun UiScope.column(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountContainer(
        com.antepod.lumentika.style.style {
            display = Display.FLEX
            flexDirection = FlexDirection.COLUMN
        },
        style,
        semantics,
        content,
    )

/** Mounts a grid-layout container; trailing [content] mounts children only. */
public fun UiScope.grid(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountContainer(
        com.antepod.lumentika.style.style { display = Display.GRID },
        style,
        semantics,
        content,
    )

/** Mounts an overlay stack container; trailing [content] mounts children only. */
public fun UiScope.stack(
    style: Style? = null,
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountContainer(
        com.antepod.lumentika.style.style { display = Display.GRID },
        style,
        semantics,
        content,
    )

internal fun UiScope.mountScroll(
    element: Element,
    state: ScrollState,
    gestures: GestureConfiguration,
    explicitConnection: NestedScrollConnection?,
): Element = element.also {
    installParts(
        element,
        Scroll.Part.ROOT to style {},
        Scroll.Part.SCROLLBAR_TRACK to style { position = Position.ABSOLUTE },
        Scroll.Part.SCROLLBAR_THUMB to style { position = Position.ABSOLUTE },
    )
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

/** Coordinates scroll ranges, offsets, and scrollbar controllers for one element. */
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
        if (element.attachment(SemanticsAttachment)?.role == SemanticRole.LIST) {
            updateListSemantics()
        }
    }

    private fun updateListSemantics() {
        val visualParts = element.attachment(VisualPartsAttachment)?.values.orEmpty().toSet()
        val items =
            element.children
                .filter { it.isMounted && it !in visualParts }
                .flatMap { child ->
                    if (child.attachment(CollectionItemContainerAttachment) != null) {
                        child.children.filter { it.isMounted }
                    } else {
                        listOf(child)
                    }
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

/** Preferred placement of a tooltip relative to its anchor. */
public enum class TooltipPlacement {
    AUTO,
    ABOVE,
    BELOW,
}

/** Geometry request consumed by an adapter or popup positioning layer. */
public data class TooltipPlacementRequest(
    val anchorBounds: Rect,
    val placement: TooltipPlacement,
    val offset: Float,
)

/** Controls delayed visibility and placement state for a tooltip. */
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
            Element().also {
                it.content = TextContent(text.value, context.textLayout)
                it.attach(
                    SemanticsAttachment,
                    SemanticsConfiguration(
                        role = SemanticRole.TOOLTIP,
                        liveRegion = LiveRegion.POLITE,
                    ),
                )
                wrapper.append(it)
                context.attachPart(
                    wrapper,
                    it,
                    Tooltip.Part.POPUP,
                    style { position = Position.ABSOLUTE },
                )
                wrapper.attach(
                    VisualPartsAttachment,
                    wrapper.attachment(VisualPartsAttachment).orEmpty() +
                        (Tooltip.Part.POPUP to it),
                )
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
        wrapper.attach(
            VisualPartsAttachment,
            wrapper.attachment(VisualPartsAttachment).orEmpty() - Tooltip.Part.POPUP,
        )
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
    context.attachPart(element, element, Tooltip.Part.ROOT, style {})
    element.attach(VisualPartsAttachment, mapOf(Tooltip.Part.ROOT to element))
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

private fun UiScope.installParts(
    owner: Element,
    vararg definitions: Pair<StylePart<*>, Style>,
): Map<StylePart<*>, Element> {
    val parts = linkedMapOf<StylePart<*>, Element>()
    definitions.forEach { (token, structural) ->
        val target =
            if (definitions.first().first === token) owner
            else
                Element().also {
                    owner.append(it)
                    context.attachStyle(
                        it,
                        style { pointerEvents = com.antepod.lumentika.style.PointerEvents.NONE },
                    )
                }
        context.attachPart(owner, target, token, structural)
        parts[token] = target
    }
    owner.attach(VisualPartsAttachment, parts)
    return parts
}

private fun UiScope.installHoverAndFocusStates(
    element: Element,
    cursor: PointerCursorRole,
) {
    val cleanups = mutableListOf<AutoCloseable>()
    context.events?.let { events ->
        cleanups +=
            events.on(element, EventType.POINTER_ENTER) {
                context.setStyleState(element, HOVER, true)
                context.cursor?.set(cursor)
            }
        cleanups +=
            events.on(element, EventType.POINTER_LEAVE) {
                context.setStyleState(element, HOVER, false)
                context.setStyleState(element, ACTIVE, false)
                context.cursor?.set(PointerCursorRole.DEFAULT)
            }
        cleanups +=
            events.on(element, EventType.FOCUS) {
                context.setStyleState(element, FOCUS, true)
                context.setStyleState(element, FOCUS_VISIBLE, true)
            }
        cleanups +=
            events.on(element, EventType.BLUR) {
                context.setStyleState(element, FOCUS, false)
                context.setStyleState(element, FOCUS_VISIBLE, false)
                context.setStyleState(element, ACTIVE, false)
            }
    }
    element.scope.own { cleanups.asReversed().forEach(AutoCloseable::close) }
}

private fun UiScope.installControlInteraction(
    element: Element,
    enabled: Readable<Boolean>,
    activate: () -> Unit,
    cursor: PointerCursorRole,
    keyAction: (LogicalKey) -> Boolean = { false },
) {
    installHoverAndFocusStates(element, cursor)
    context.focus?.let { focus ->
        withComponentScope(element.scope) {
            effect {
                val active = enabled.value
                focus.configure(element, FocusProperties(focusable = active))
                context.setStyleState(element, DISABLED, !active)
                if (!active) context.setStyleState(element, ACTIVE, false)
            }
        }
        element.scope.own { focus.unconfigure(element) }
    }
    if (context.focus == null) {
        withComponentScope(element.scope) {
            effect { context.setStyleState(element, DISABLED, !enabled.value) }
        }
    }
    val cleanups = mutableListOf<AutoCloseable>()
    context.events?.let { events ->
        cleanups +=
            events.on(element, EventType.POINTER_DOWN) {
                if (!enabled.value) return@on
                context.setStyleState(element, ACTIVE, true)
                context.focus?.focus(element, FocusCause.POINTER)
                context.feedback?.perform(UiFeedbackRequest(UiFeedbackType.PRESS))
            }
        cleanups +=
            events.on(element, EventType.POINTER_UP) {
                context.setStyleState(element, ACTIVE, false)
            }
        cleanups +=
            events.on(element, EventType.POINTER_CANCEL) {
                context.setStyleState(element, ACTIVE, false)
            }
        cleanups +=
            events.on(element, EventType.KEY_DOWN) { event ->
                if (!enabled.value) return@on
                event as KeyboardEvent
                val handled =
                    keyAction(event.logicalKey) ||
                        event.logicalKey == LogicalKey.ENTER ||
                        event.logicalKey == LogicalKey.SPACE
                if (handled) {
                    if (
                        event.logicalKey == LogicalKey.ENTER || event.logicalKey == LogicalKey.SPACE
                    ) {
                        activate()
                    }
                    event.preventDefault()
                }
            }
    }
    element.scope.own { cleanups.asReversed().forEach(AutoCloseable::close) }
}

internal fun UiScope.buttonControl(
    e: Element,
    label: Readable<String>,
    gestures: GestureConfiguration,
    enabled: Readable<Boolean>,
    onClick: () -> Unit,
): ControlHandle {
    val parts =
        installParts(
            e,
            Button.Part.ROOT to style { display = Display.FLEX },
            Button.Part.LABEL to style {},
            Button.Part.ICON to style {},
        )
    val labelElement = parts.getValue(Button.Part.LABEL)
    val action = {
        if (enabled.value) {
            onClick()
            context.feedback?.perform(UiFeedbackRequest(UiFeedbackType.CONFIRM))
        }
    }
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.BUTTON,
                label = label.value,
                enabled = enabled.value,
                mergeDescendants = true,
                actions =
                    if (enabled.value)
                        mapOf(
                            SemanticAction.CLICK to
                                {
                                    action()
                                    true
                                }
                        )
                    else emptyMap(),
            ),
            action,
            ControlGestureHandle(TapRecognizer(gestures, action)),
        )
    installControlInteraction(e, enabled, action, PointerCursorRole.POINTER)
    withComponentScope(e.scope) {
        effect {
            val next = label.value
            labelElement.content = TextContent(next, context.textLayout)
            e.attach(SemanticsAttachment, handle.semantics.copy(label = next))
            context.requestFrame(true)
        }
        effect {
            val active = enabled.value
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(
                    enabled = active,
                    actions =
                        if (active)
                            mapOf(
                                SemanticAction.CLICK to
                                    {
                                        action()
                                        true
                                    }
                            )
                        else emptyMap(),
                ),
            )
            context.requestFrame(false)
        }
    }
    return handle
}

internal fun UiScope.checkboxControl(
    e: Element,
    value: Mutable<Boolean>,
    label: Readable<String?>,
    gestures: GestureConfiguration,
    enabled: Readable<Boolean>,
    onChange: (Boolean) -> Unit = {},
): ControlHandle {
    val parts =
        installParts(
            e,
            Checkbox.Part.ROOT to
                style {
                    display = Display.FLEX
                    flexDirection = FlexDirection.ROW
                },
            Checkbox.Part.INDICATOR to style {},
            Checkbox.Part.LABEL to style {},
        )
    val labelElement = parts.getValue(Checkbox.Part.LABEL)
    val action = {
        if (enabled.value) {
            value.value = !value.value
            onChange(value.value)
            context.feedback?.perform(UiFeedbackRequest(UiFeedbackType.TOGGLE))
        }
    }
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.CHECKBOX,
                label = label.value,
                enabled = enabled.value,
                checked = value.value,
                actions =
                    if (enabled.value)
                        mapOf(
                            SemanticAction.CLICK to
                                {
                                    action()
                                    true
                                }
                        )
                    else emptyMap(),
            ),
            action,
            ControlGestureHandle(TapRecognizer(gestures, action)),
        )
    installControlInteraction(e, enabled, action, PointerCursorRole.POINTER)
    withComponentScope(e.scope) {
        effect {
            val next = label.value
            labelElement.content = next?.let { TextContent(it, context.textLayout) }
            e.attach(SemanticsAttachment, handle.semantics.copy(label = next))
            context.requestFrame(true)
        }
        effect {
            val active = enabled.value
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(
                    enabled = active,
                    actions =
                        if (active)
                            mapOf(
                                SemanticAction.CLICK to
                                    {
                                        action()
                                        true
                                    }
                            )
                        else emptyMap(),
                ),
            )
            context.requestFrame(false)
        }
        effect {
            context.setStyleState(e, ControlStyleState.CHECKED, value.value)
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
    step: Float?,
    label: String?,
    gestures: GestureConfiguration,
    enabled: Boolean,
    onInput: (Float) -> Unit = {},
    onChange: (Float) -> Unit = {},
): ControlHandle {
    val parts =
        installParts(
            e,
            Slider.Part.ROOT to
                style {
                    display = Display.FLEX
                    flexDirection = FlexDirection.ROW
                },
            Slider.Part.TRACK to style { flexGrow = 1f },
            Slider.Part.THUMB to style { position = Position.ABSOLUTE },
            Slider.Part.LABEL to style {},
        )
    label?.let { parts.getValue(Slider.Part.LABEL).content = TextContent(it, context.textLayout) }
    fun normalized(next: Float): Float {
        val clamped = next.coerceIn(minimum, maximum)
        return step?.let {
            (minimum + kotlin.math.round((clamped - minimum) / it) * it).coerceIn(minimum, maximum)
        } ?: clamped
    }
    fun setValue(next: Float, final: Boolean) {
        if (!enabled) return
        value.value = normalized(next)
        onInput(value.value)
        if (final) onChange(value.value)
        context.feedback?.perform(UiFeedbackRequest(UiFeedbackType.SELECTION_CHANGE))
    }
    value.value = normalized(value.value)
    val gesture =
        ControlGestureHandle(
            DragRecognizer(
                gestures,
                DragAxis.HORIZONTAL,
                onUpdate = { update ->
                    val width = e.geometry.width.takeIf { it > 0f } ?: 100f
                    setValue(value.value + update.delta.x / width * (maximum - minimum), false)
                },
                onEnd = { setValue(value.value, true) },
            )
        )
    val handle =
        control(
            e,
            SemanticsConfiguration(
                role = SemanticRole.SLIDER,
                enabled = enabled,
                range = SemanticRange(value.value, minimum, maximum, step),
                actions =
                    if (enabled)
                        mapOf(
                            SemanticAction.SET_VALUE to
                                { v ->
                                    setValue((v as Number).toFloat(), true)
                                    true
                                },
                            SemanticAction.INCREMENT to
                                {
                                    setValue(
                                        value.value + (step ?: (maximum - minimum) / 10f),
                                        true,
                                    )
                                    true
                                },
                            SemanticAction.DECREMENT to
                                {
                                    setValue(
                                        value.value - (step ?: (maximum - minimum) / 10f),
                                        true,
                                    )
                                    true
                                },
                        )
                    else emptyMap(),
            ),
            gestures = gesture,
        )
    installControlInteraction(
        e,
        com.antepod.lumentika.reactive.state(enabled),
        activate = { setValue(value.value + (step ?: (maximum - minimum) / 10f), true) },
        cursor = PointerCursorRole.POINTER,
        keyAction = { key ->
            when (key) {
                LogicalKey.ARROW_RIGHT,
                LogicalKey.ARROW_UP ->
                    setValue(value.value + (step ?: (maximum - minimum) / 10f), true)
                LogicalKey.ARROW_LEFT,
                LogicalKey.ARROW_DOWN ->
                    setValue(value.value - (step ?: (maximum - minimum) / 10f), true)
                LogicalKey.HOME -> setValue(minimum, true)
                LogicalKey.END -> setValue(maximum, true)
                else -> return@installControlInteraction false
            }
            true
        },
    )
    withComponentScope(e.scope) {
        var lastWidth = Float.NaN
        var lastFraction = Float.NaN
        val updateVisual = {
            val fraction =
                if (maximum == minimum) 0f else (value.value - minimum) / (maximum - minimum)
            if (e.geometry.width != lastWidth || fraction != lastFraction) {
                lastWidth = e.geometry.width
                lastFraction = fraction
                context.configureRender(
                    parts.getValue(Slider.Part.THUMB),
                    RenderProperties(
                        transform = Matrix3.translation(e.geometry.width * fraction, 0f)
                    ),
                )
            }
        }
        e.attach(ControlVisualLayoutAttachment, updateVisual)
        effect {
            value.value
            updateVisual()
            e.attach(
                SemanticsAttachment,
                handle.semantics.copy(range = SemanticRange(value.value, minimum, maximum, step)),
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
    enabled: Boolean,
): ControlHandle {
    val parts =
        installParts(
            e,
            TextField.Part.ROOT to style {},
            TextField.Part.SELECTION to style { position = Position.ABSOLUTE },
            TextField.Part.TEXT to style {},
            TextField.Part.PLACEHOLDER to style { position = Position.ABSOLUTE },
            TextField.Part.CURSOR to
                style {
                    position = Position.ABSOLUTE
                    width = 1.px
                    height = 1.px
                },
            TextField.Part.SCROLLBAR_TRACK to style { position = Position.ABSOLUTE },
            TextField.Part.SCROLLBAR_THUMB to style { position = Position.ABSOLUTE },
        )
    val textElement = parts.getValue(TextField.Part.TEXT)
    val placeholderElement = parts.getValue(TextField.Part.PLACEHOLDER)
    context.attachStyle(
        parts.getValue(TextField.Part.SELECTION),
        style {
            width = 1.px
            height = 1.px
        },
    )
    val editor =
        TextEditorRuntime(
            controller,
            context.textInput,
            context.textLayout,
            context.animationClock,
            TextInputConfiguration(
                multiline = multiline,
                secure = secure,
                autofillHints = autofill?.hints.orEmpty(),
            ),
            context.clipboard,
        )
    e.attach(TextEditorAttachment, editor)
    var lastCursorTransform: Matrix3? = null
    var lastSelectionRender: RenderProperties? = null
    e.attach(ControlVisualLayoutAttachment) {
        val geometry = editor.cursorGeometry
        val caret = geometry.caret
        val cursorTransform =
            Matrix3.translation(caret.x - editor.scrollX, caret.y - editor.scrollY) *
                Matrix3.scale(
                    if (geometry.visible) caret.width.coerceAtLeast(1f) else 0f,
                    caret.height,
                )
        if (cursorTransform != lastCursorTransform) {
            lastCursorTransform = cursorTransform
            context.configureRender(
                parts.getValue(TextField.Part.CURSOR),
                RenderProperties(transform = cursorTransform),
            )
        }
        val selection = geometry.selection
        val union =
            selection
                .takeIf { it.isNotEmpty() }
                ?.let { rects ->
                    Rect(
                        rects.minOf { it.x },
                        rects.minOf { it.y },
                        rects.maxOf { it.right } - rects.minOf { it.x },
                        rects.maxOf { it.bottom } - rects.minOf { it.y },
                    )
                }
        val selectionRender =
            union?.let {
                val contours = selection.flatMap { rect ->
                    val width = it.width.coerceAtLeast(1e-6f)
                    val height = it.height.coerceAtLeast(1e-6f)
                    val left = (rect.x - it.x) / width
                    val top = (rect.y - it.y) / height
                    val right = (rect.right - it.x) / width
                    val bottom = (rect.bottom - it.y) / height
                    listOf(
                        com.antepod.lumentika.geometry.PathSegment.MoveTo(Point(left, top)),
                        com.antepod.lumentika.geometry.PathSegment.LineTo(Point(right, top)),
                        com.antepod.lumentika.geometry.PathSegment.LineTo(Point(right, bottom)),
                        com.antepod.lumentika.geometry.PathSegment.LineTo(Point(left, bottom)),
                        com.antepod.lumentika.geometry.PathSegment.Close,
                    )
                }
                RenderProperties(
                    transform =
                        Matrix3.translation(it.x - editor.scrollX, it.y - editor.scrollY) *
                            Matrix3.scale(it.width, it.height),
                    clip = com.antepod.lumentika.geometry.Path(contours),
                )
            } ?: RenderProperties(transform = Matrix3.scale(0f))
        if (selectionRender != lastSelectionRender) {
            lastSelectionRender = selectionRender
            context.configureRender(
                parts.getValue(TextField.Part.SELECTION),
                selectionRender,
            )
        }
    }
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
                enabled = enabled,
                readOnly = !enabled,
                value = controller.value.text,
                textSelection = controller.value.selection,
                password = secure,
            ),
            gestures = gesture,
        )
    val cleanups = mutableListOf<AutoCloseable>()
    if (enabled)
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
    context.setStyleState(e, DISABLED, !enabled)
    if (enabled) installHoverAndFocusStates(e, PointerCursorRole.TEXT)
    withComponentScope(e.scope) {
        effect {
            val value = controller.value
            val empty = value.text.isEmpty()
            context.setStyleState(e, ControlStyleState.EMPTY, empty)
            val displayText =
                if (secure) "•".repeat(value.text.codePointCount(0, value.text.length))
                else value.text
            textElement.content =
                displayText.takeIf(String::isNotEmpty)?.let {
                    TextContent(TextLayoutRequest(it), context.textLayout)
                }
            placeholderElement.content =
                placeholder?.takeIf { empty }?.let { TextContent(it, context.textLayout) }
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
