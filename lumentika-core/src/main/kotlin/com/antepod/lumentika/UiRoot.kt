package com.antepod.lumentika

import com.antepod.lumentika.animation.StyleAnimationRuntime
import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.components.ControlGestureHandle
import com.antepod.lumentika.components.GestureAttachment
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

public enum class PointerInputPhase {
    DOWN,
    MOVE,
    UP,
    CANCEL,
}

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

public class UiRoot(
    initialEnvironment: UiEnvironment,
    public val services: PlatformServices,
    private val backend: RenderBackend,
) : AutoCloseable {
    public val element = Element("ui-root")
    public val environment = UiEnvironmentState(initialEnvironment)
    public val styles = StyleRuntime()
    public val events = EventDispatcher(element)
    public val focus = FocusManager(element, events)
    public val semantics = SemanticsRuntime(element)
    public val animations = UiAnimationClock()
    private val defaultStyle = state(style {})
    private val frame = CoalescingFrameScheduler(services.frameScheduler)
    public val scope =
        UiScope(
            element,
            UiContext(
                services.textLayout,
                services.textInput,
                services.images,
                animations,
                focus,
                events,
                ::requestFrame,
                ::configureRender,
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
    private val render =
        RenderRuntime(element) { styleAnimations.effective(it, styles.resolve(it).first) }
    private val pointerGestures = mutableMapOf<Int, PointerGestureSession>()
    public var frameTimeNanos = 0L
        private set

    public val layoutComputeCount: Long
        get() = layout.computeCount

    public val renderRecordCount: Long
        get() = render.recordCount

    public val committedRender: RenderCommit
        get() = render.committed

    public fun hitTest(point: Point): Element? = render.committed.hitTest.hitTest(point)

    public fun raycast(point: Point): SceneRaycastHit? = render.committed.hitTest.raycast(point)

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

    public fun configureRender(element: Element, properties: RenderProperties) {
        render.configure(element, properties)
        requestFrame(layoutDirty = false)
    }

    init {
        styles.attach(element, defaultStyle)
    }

    public fun requestFrame(layoutDirty: Boolean = true) {
        if (layoutDirty) layout.requestLayout()
        frame.requestFrame()
    }

    public fun publishEnvironment(value: UiEnvironment) {
        environment.publish(value)
        requestFrame()
    }

    public fun frame(timeNanos: Long) {
        frame.consume()
        frameTimeNanos = timeNanos
        animations.frame(timeNanos)
        layout.frame(timeNanos, environment.value)
        val commit = render.commit()
        val changes = semantics.commit(commit.hitTest)
        services.accessibility?.onArtifactCommitted(semantics.artifact, changes)
        render.replay(backend)
    }

    override fun close() {
        pointerGestures.keys.toList().forEach(::cancelPointerGestures)
        styleAnimations.close()
        layout.close()
        element.close()
    }

    private fun routeGestures(input: PointerInput, target: Element) {
        when (input.phase) {
            PointerInputPhase.DOWN -> {
                cancelPointerGestures(input.pointerId)
                val arena = GestureArena()
                val handles =
                    generateSequence(target) { it.parent }
                        .mapNotNull { it.attachment(GestureAttachment) }
                        .toList()
                handles.forEach {
                    it.down(input.pointerId, input.position, input.timestampNanos, arena)
                }
                if (handles.isNotEmpty()) {
                    pointerGestures[input.pointerId] = PointerGestureSession(arena, handles)
                }
            }
            PointerInputPhase.MOVE ->
                pointerGestures[input.pointerId]?.handles?.forEach {
                    it.move(input.position, input.timestampNanos)
                }
            PointerInputPhase.UP ->
                pointerGestures.remove(input.pointerId)?.handles?.forEach {
                    it.up(input.position, input.timestampNanos)
                }
            PointerInputPhase.CANCEL -> cancelPointerGestures(input.pointerId)
        }
    }

    private fun cancelPointerGestures(pointerId: Int) {
        pointerGestures.remove(pointerId)?.arena?.cancel(pointerId)
    }

    private data class PointerGestureSession(
        val arena: GestureArena,
        val handles: List<ControlGestureHandle>,
    )
}

public class HeadlessFrameScheduler : FrameScheduler {
    public var requests = 0

    override fun requestFrame() {
        requests++
    }
}

public class HeadlessRenderBackend : RenderBackend {
    public var replays = 0
    public var last: com.antepod.lumentika.render.PaintArtifact? = null

    override fun replay(artifact: com.antepod.lumentika.render.PaintArtifact) {
        replays++
        last = artifact
    }
}

public fun headlessRoot(width: Float = 800f, height: Float = 600f): UiRoot {
    val scheduler = HeadlessFrameScheduler()
    return UiRoot(
        UiEnvironment(Size(width, height)),
        PlatformServices(scheduler),
        HeadlessRenderBackend(),
    )
}
