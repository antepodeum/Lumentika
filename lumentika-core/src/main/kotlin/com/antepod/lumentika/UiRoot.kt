package com.antepod.lumentika

import com.antepod.lumentika.animation.ElementAnimationRuntime
import com.antepod.lumentika.animation.StyleAnimationRuntime
import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.components.ControlGestureHandle
import com.antepod.lumentika.components.ControlVisualLayoutAttachment
import com.antepod.lumentika.components.GestureAttachment
import com.antepod.lumentika.components.ReceiveContentAttachment
import com.antepod.lumentika.components.ScrollRuntimeAttachment
import com.antepod.lumentika.components.TextEditorAttachment
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.gesture.GestureArena
import com.antepod.lumentika.input.*
import com.antepod.lumentika.layout.LayoutRuntime
import com.antepod.lumentika.platform.*
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.render.RenderCommit
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.render.RenderRuntime
import com.antepod.lumentika.render.SceneRaycastHit
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.ImageService
import com.antepod.lumentika.runtime.UiContext
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.semantics.SemanticsRuntime
import com.antepod.lumentika.style.StyleImpact
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.AutofillNodeId
import com.antepod.lumentika.text.AutofillRuntime

/**
 * Native services used by a [UiRoot]; unavailable optional capabilities are represented by `null`.
 */
public data class PlatformServices(
    val frameScheduler: FrameScheduler,
    val units: UnitResolver = LogicalUnitResolver,
    val clipboard: ClipboardService? = null,
    val feedback: UiFeedbackService? = null,
    val cursor: PointerCursorService? = null,
    val accessibility: com.antepod.lumentika.semantics.AccessibilityAdapter? = null,
    val textInput: com.antepod.lumentika.text.TextInputService? = null,
    val textLayout: com.antepod.lumentika.text.TextLayoutService =
        com.antepod.lumentika.text.HeadlessTextLayoutService,
    val images: ImageService? = null,
    val autofill: com.antepod.lumentika.text.AutofillService? = null,
    val contentTransfer: ContentTransferService? = null,
    val uriLauncher: UriLauncher? = null,
    val back: BackDispatcher? = null,
)

/** Lifecycle phase of a normalized pointer sample. */
public enum class PointerInputPhase {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

/** Normalized pointer input delivered to [UiRoot.dispatchPointer]. */
public data class PointerInput(
    val phase: PointerInputPhase,
    val pointerId: Int,
    val pointerType: PointerType,
    val position: Point,
    val timestampNanos: Long,
    val button: Int = 0,
    val buttons: Int = 0,
    val pressure: Float? = null,
    val modifiers: KeyModifiers = KeyModifiers(),
    val historical: List<PointerSample> = emptyList(),
)

/**
 * Owns one retained UI tree and coordinates reactivity, layout, rendering, input, semantics, and
 * animation.
 *
 * Platform adapters create a root, mount content through [scope], forward native input, and invoke
 * [frame] with monotonic timestamps. Close the root when its native surface is destroyed.
 */
public class UiRoot(
    initialEnvironment: UiEnvironment,
    public val services: PlatformServices,
    private val backend: RenderBackend,
) : AutoCloseable {
    public val element = Element()
    public val environment = UiEnvironmentState(initialEnvironment)
    public val styles = StyleRuntime()
    public val events = EventDispatcher(element)
    public val focus = FocusManager(element, events)
    public val semantics =
        SemanticsRuntime(element) { message, priority ->
            services.accessibility?.announce(message, priority)
        }
    public val autofill = AutofillRuntime()
    public val animations = UiAnimationClock()
    private val defaultStyle = state(style {})
    private val frame = CoalescingFrameScheduler(services.frameScheduler)
    public val elementAnimations =
        ElementAnimationRuntime(
            animations,
            { target, value -> render.configureMotion(target, value) },
            ::committedBounds,
            frame::requestFrame,
        )
    public val scope =
        UiScope(
            element,
            UiContext(
                textLayout = services.textLayout,
                textInput = services.textInput,
                clipboard = services.clipboard,
                autofill = autofill,
                images = services.images,
                animationClock = animations,
                elementAnimations = elementAnimations,
                focus = focus,
                events = events,
                requestFrame = ::requestFrame,
                configureRender = ::configureRender,
                attachStyle = { target, source -> styles.attach(target, state(source)) },
                attachTheme = { target, source -> styles.attachTheme(target, state(source)) },
                attachPart = { owner, target, part, structural ->
                    styles.attachPart(owner, target, part, state(structural))
                },
                attachPartStyle = { owner, part, source ->
                    styles.attachPartStyle(owner, part, state(source))
                },
                committedBounds = ::committedBounds,
                gestureConfiguration = { environment.value.gesture },
                feedback = services.feedback,
                cursor = services.cursor,
                setStyleState = { target, styleState, enabled ->
                    styles.setState(target, styleState, enabled)
                    requestFrame(false)
                },
            ),
        )
    public val styleAnimations =
        StyleAnimationRuntime(
            animations,
            onImpact = { _, impact -> requestFrame(impact.contains(StyleImpact.LAYOUT)) },
            requestFrame = frame::requestFrame,
        )
    private val layout =
        LayoutRuntime(
            element,
            services.units,
            { styleAnimations.effective(it, styles.resolve(it).first) },
            onLayoutRequested = frame::requestFrame,
        )
    private val render by lazy {
        RenderRuntime(
            root = element,
            resolveBorderLength = { value, basis ->
                com.antepod.lumentika.style.resolveLength(
                    value as com.antepod.lumentika.style.DimensionValue,
                    environment.value,
                    services.units,
                    basis,
                ) ?: 0f
            },
            resolveStyle = { styleAnimations.effective(it, styles.resolve(it).first) },
            onPaintRequested = { requestFrame(false) },
        )
    }
    private val pointerGestures = mutableMapOf<Int, PointerGestureSession>()
    private var lastPlatformFrameNanos = 0L
    private var logicalFrameTimeNanos = 0L
    public var frameTimeNanos = 0L
        private set

    public val layoutComputeCount: Long
        get() = layout.computeCount

    public val renderRecordCount: Long
        get() = render.recordCount

    public val committedRender: RenderCommit
        get() = render.committed

    /** Returns the frontmost interactive element at [point]. */
    public fun hitTest(point: Point): Element? = render.committed.hitTest.hitTest(point)

    /** Performs a scene-content raycast at [point]. */
    public fun raycast(point: Point): SceneRaycastHit? = render.committed.hitTest.raycast(point)

    /** Dispatches normalized pointer input through hit testing, events, and gesture recognition. */
    public fun dispatchPointer(input: PointerInput): Boolean {
        val actualHit = hitTest(input.position)
        if (input.pointerType == PointerType.MOUSE) {
            events.updateHover(actualHit, input.timestampNanos)
        }
        val target = events.captured(input.pointerId) ?: actualHit
        if (target == null) {
            if (input.phase == PointerInputPhase.UP || input.phase == PointerInputPhase.CANCEL) {
                cancelPointerGestures(input.pointerId)
            }
            return false
        }
        val type =
            when (input.phase) {
                PointerInputPhase.DOWN -> EventType.POINTER_DOWN
                PointerInputPhase.MOVE -> EventType.POINTER_MOVE
                PointerInputPhase.UP -> EventType.POINTER_UP
                PointerInputPhase.CANCEL -> EventType.POINTER_CANCEL
            }
        val allowed =
            events.dispatch(
                type,
                PointerEvent(
                    target,
                    input.pointerId,
                    input.pointerType,
                    input.position,
                    input.button,
                    input.buttons,
                    input.pressure,
                    input.timestampNanos,
                    input.modifiers,
                    input.historical,
                ),
            )
        if (allowed) routeGestures(input, actualHit ?: target)
        else if (input.phase == PointerInputPhase.UP || input.phase == PointerInputPhase.CANCEL) {
            cancelPointerGestures(input.pointerId)
        }
        return allowed
    }

    /** Dispatches a wheel delta to the hit-tested element at [position]. */
    public fun dispatchWheel(
        position: Point,
        deltaX: Float,
        deltaY: Float,
        timestampNanos: Long,
    ): Boolean {
        val target = hitTest(position) ?: return false
        return events.dispatch(
            EventType.WHEEL,
            WheelEvent(target, position, deltaX, deltaY, timestampNanos),
        )
    }

    /** Dispatches a key event to the currently focused element. */
    public fun dispatchKey(
        type: EventType,
        logicalKey: LogicalKey,
        physicalKey: String,
        timestampNanos: Long,
        text: String? = null,
        repeat: Boolean = false,
        modifiers: KeyModifiers = KeyModifiers(),
    ): Boolean {
        require(type == EventType.KEY_DOWN || type == EventType.KEY_UP)
        val target = focus.activeElement ?: return false
        return events.dispatch(
            type,
            KeyboardEvent(
                target,
                logicalKey,
                physicalKey,
                text,
                repeat,
                modifiers,
                timestampNanos,
            ),
        )
    }

    /** Sets adapter or component render properties for [element]. */
    public fun configureRender(element: Element, properties: RenderProperties) {
        render.configure(element, properties)
        requestFrame(layoutDirty = false)
    }

    init {
        element.installSubtreeRemovalObserver { subtree ->
            focus.repairBeforeRemoval(subtree)
            events.repairBeforeRemoval(subtree, frameTimeNanos)
        }
        styles.attach(element, defaultStyle)
    }

    /** Requests a coalesced platform frame, optionally marking layout dirty. */
    public fun requestFrame(layoutDirty: Boolean = true) {
        if (layoutDirty) layout.requestLayout()
        frame.requestFrame()
    }

    /** Publishes an immutable environment snapshot and requests recomputation. */
    public fun publishEnvironment(value: UiEnvironment) {
        environment.publish(value)
        requestFrame()
    }

    /** Advances the complete UI pipeline using a monotonic platform timestamp. */
    public fun frame(timeNanos: Long) {
        frame.consume()
        frameTimeNanos = timeNanos
        require(timeNanos >= lastPlatformFrameNanos) { "Frame timestamps must be monotonic" }
        val delta =
            if (lastPlatformFrameNanos == 0L) timeNanos else timeNanos - lastPlatformFrameNanos
        lastPlatformFrameNanos = timeNanos
        val lifecycle = environment.value.lifecycle
        val animationsActive =
            if (lifecycle == UiLifecycleState.SUSPENDED || lifecycle == UiLifecycleState.DISPOSED) {
                false
            } else {
                logicalFrameTimeNanos += delta
                animations.motionScale = environment.value.motionDurationScale
                styleAnimations.motionScale = environment.value.motionDurationScale
                animations.frame(logicalFrameTimeNanos)
            }
        if (lifecycle != UiLifecycleState.SUSPENDED && lifecycle != UiLifecycleState.DISPOSED) {
            pointerGestures.values.forEach { session ->
                session.handles.forEach { it.advance(logicalFrameTimeNanos) }
            }
        }
        layout.frame(timeNanos, environment.value)
        updateScrollRanges(element)
        updateTextEditorViewports(element)
        updateControlVisualLayouts(element)
        var commit = render.commit()
        if (elementAnimations.afterCommit()) commit = render.commit()
        val changes = semantics.commit(commit.hitTest)
        services.accessibility?.onArtifactCommitted(semantics.artifact, changes)
        val (autofillArtifact, autofillChanges) = autofill.commit()
        services.autofill?.onArtifactCommitted(autofillArtifact, autofillChanges)
        render.replay(backend)
        if (animationsActive) frame.requestFrame()
    }

    /** Applies autofill text to the registered field identified by [id]. */
    public fun applyAutofill(id: AutofillNodeId, text: String): Boolean = autofill.apply(id, text)

    /** Requests native autofill UI for [id] when an autofill service is available. */
    public fun requestAutofill(id: AutofillNodeId): Boolean =
        services.autofill?.let {
            it.requestAutofill(id)
            true
        } ?: false

    /** Opens [uri] through the platform service, returning whether it was handled. */
    public fun openUri(uri: UiUri): Boolean = services.uriLauncher?.open(uri) ?: false

    /** Starts a native drag operation, or returns `null` when unsupported. */
    public fun startDrag(request: DragRequest): DragSession? =
        services.contentTransfer?.startDrag(request)

    /** Registers a back handler and returns a disposable registration. */
    public fun registerBackHandler(handler: (BackEvent) -> Boolean): AutoCloseable =
        services.back?.register(handler) ?: AutoCloseable {}

    /** Offers transferred [content] to receivers under [position] and returns unconsumed items. */
    public fun dispatchContent(position: Point, content: TransferContent): TransferContent {
        var remaining = content
        var target = hitTest(position)
        while (target != null && remaining.items.isNotEmpty()) {
            target.attachment(ReceiveContentAttachment)?.let { remaining = it(remaining) }
            target = target.parent
        }
        return remaining
    }

    override fun close() {
        pointerGestures.keys.toList().forEach(::cancelPointerGestures)
        elementAnimations.close()
        styleAnimations.close()
        layout.close()
        render.close()
        element.close()
    }

    private fun routeGestures(input: PointerInput, target: Element) {
        val gestureTimeNanos = gestureTime(input.timestampNanos)
        when (input.phase) {
            PointerInputPhase.DOWN -> {
                cancelPointerGestures(input.pointerId)
                val arena = GestureArena()
                val handles =
                    generateSequence(target) { it.parent }
                        .mapNotNull { it.attachment(GestureAttachment) }
                        .toList()
                handles.forEach {
                    it.down(
                        input.pointerId,
                        input.position,
                        gestureTimeNanos,
                        arena,
                        input.pointerType,
                    )
                }
                if (handles.isNotEmpty()) {
                    pointerGestures[input.pointerId] = PointerGestureSession(arena, handles)
                }
            }
            PointerInputPhase.MOVE ->
                pointerGestures[input.pointerId]?.handles?.forEach {
                    it.move(input.position, gestureTimeNanos)
                }
            PointerInputPhase.UP ->
                pointerGestures.remove(input.pointerId)?.handles?.forEach {
                    it.up(input.position, gestureTimeNanos)
                }
            PointerInputPhase.CANCEL -> cancelPointerGestures(input.pointerId)
        }
    }

    private fun gestureTime(platformTimeNanos: Long): Long =
        if (lastPlatformFrameNanos == 0L) platformTimeNanos
        else logicalFrameTimeNanos + (platformTimeNanos - lastPlatformFrameNanos).coerceAtLeast(0L)

    private fun cancelPointerGestures(pointerId: Int) {
        pointerGestures.remove(pointerId)?.arena?.cancel(pointerId)
    }

    private fun updateScrollRanges(current: Element) {
        current.attachment(ScrollRuntimeAttachment)?.updateLayout()
        current.children.forEach(::updateScrollRanges)
    }

    private fun updateTextEditorViewports(current: Element) {
        current
            .attachment(TextEditorAttachment)
            ?.updateViewport(
                current.geometry.width,
                current.geometry.height,
            )
        current.children.forEach(::updateTextEditorViewports)
    }

    private fun updateControlVisualLayouts(current: Element) {
        current.attachment(ControlVisualLayoutAttachment)?.invoke()
        current.children.forEach(::updateControlVisualLayouts)
    }

    private fun committedBounds(target: Element): com.antepod.lumentika.geometry.Rect? {
        val entry =
            render.committed.hitTest.entries.firstOrNull { it.element === target } ?: return null
        val corners =
            listOf(
                    Point(entry.localBounds.x, entry.localBounds.y),
                    Point(entry.localBounds.right, entry.localBounds.y),
                    Point(entry.localBounds.right, entry.localBounds.bottom),
                    Point(entry.localBounds.x, entry.localBounds.bottom),
                )
                .map(entry.rootTransform::transform)
        val transformed =
            com.antepod.lumentika.geometry.Rect(
                corners.minOf(Point::x),
                corners.minOf(Point::y),
                corners.maxOf(Point::x) - corners.minOf(Point::x),
                corners.maxOf(Point::y) - corners.minOf(Point::y),
            )
        return transformed.intersect(entry.clip)
    }

    private data class PointerGestureSession(
        val arena: GestureArena,
        val handles: List<ControlGestureHandle>,
    )
}

/** Frame scheduler that only records requests, intended for tests and headless tools. */
public class HeadlessFrameScheduler : FrameScheduler {
    public var requests = 0

    override fun requestFrame() {
        requests++
    }
}

/** Render backend that records the last replayed artifact without drawing it. */
public class HeadlessRenderBackend : RenderBackend {
    public var replays = 0
    public var last: com.antepod.lumentika.render.PaintArtifact? = null

    override fun replay(artifact: com.antepod.lumentika.render.PaintArtifact) {
        replays++
        last = artifact
    }
}

/** Creates a deterministic root with headless platform services and the requested viewport. */
public fun headlessRoot(width: Float = 800f, height: Float = 600f): UiRoot {
    val scheduler = HeadlessFrameScheduler()
    return UiRoot(
        UiEnvironment(Size(width, height)),
        PlatformServices(scheduler),
        HeadlessRenderBackend(),
    )
}
