package com.antepod.lumentika

import com.antepod.lumentika.animation.StyleAnimationRuntime
import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.input.EventDispatcher
import com.antepod.lumentika.input.FocusManager
import com.antepod.lumentika.layout.LayoutRuntime
import com.antepod.lumentika.platform.*
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.render.RenderRuntime
import com.antepod.lumentika.runtime.Element
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
    val autofill: com.antepod.lumentika.text.AutofillService? = null,
    val contentTransfer: ContentTransferService? = null,
    val uriLauncher: UriLauncher? = null,
    val back: BackDispatcher? = null,
)

public class UiRoot(
    initialEnvironment: UiEnvironment,
    public val services: PlatformServices,
    private val backend: RenderBackend,
) : AutoCloseable {
    public val element = Element("ui-root")
    public val scope = UiScope(element)
    public val environment = UiEnvironmentState(initialEnvironment)
    public val styles = StyleRuntime()
    public val events = EventDispatcher(element)
    public val focus = FocusManager(element, events)
    public val semantics = SemanticsRuntime(element)
    public val animations = UiAnimationClock()
    private val defaultStyle = state(style {})
    private val frame = CoalescingFrameScheduler(services.frameScheduler)
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
    public var frameTimeNanos = 0L
        private set

    public val layoutComputeCount: Long
        get() = layout.computeCount

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
        styleAnimations.close()
        layout.close()
        element.close()
    }
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
