package com.antepod.lumentika.animation

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.render.MotionRenderProperties
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.PathMetrics
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Standard easing functions for structural and layout animations. */
public object StructuralEasings {
    public val linear: (Float) -> Float = { it }
    public val cubicOut: (Float) -> Float = { value -> 1f - (1f - value).pow(3) }
    public val cubicInOut: (Float) -> Float = { value ->
        if (value < .5f) 4f * value.pow(3) else 1f - (-2f * value + 2f).pow(3) / 2f
    }
}

/** Whether an element is entering or leaving the mounted structure. */
public enum class TransitionDirection {
    IN,
    OUT,
}

/** Lifecycle event emitted by a structural transition. */
public enum class TransitionEventType {
    INTRO_START,
    INTRO_END,
    OUTRO_START,
    OUTRO_END,
    CANCEL,
}

/** Describes one transition lifecycle event. */
public data class TransitionEvent(
    val element: Element,
    val type: TransitionEventType,
    val direction: TransitionDirection,
)

/** Callback set for transition start, end, and cancellation. */
public data class TransitionEvents(
    val onIntroStart: (TransitionEvent) -> Unit = {},
    val onIntroEnd: (TransitionEvent) -> Unit = {},
    val onOutroStart: (TransitionEvent) -> Unit = {},
    val onOutroEnd: (TransitionEvent) -> Unit = {},
    val onCancel: (TransitionEvent) -> Unit = {},
) {
    internal fun dispatch(event: TransitionEvent) {
        when (event.type) {
            TransitionEventType.INTRO_START -> onIntroStart(event)
            TransitionEventType.INTRO_END -> onIntroEnd(event)
            TransitionEventType.OUTRO_START -> onOutroStart(event)
            TransitionEventType.OUTRO_END -> onOutroEnd(event)
            TransitionEventType.CANCEL -> onCancel(event)
        }
    }
}

/** Visual properties sampled for one structural-animation frame. */
public data class TransitionFrame(
    val transform: Matrix3 = Matrix3.IDENTITY,
    val opacity: Float = 1f,
    val clip: Rect? = null,
    val blurRadius: Float = 0f,
    val drawLength: Float? = null,
    val drawProgress: Float = 1f,
) {
    init {
        require(opacity.isFinite() && opacity in 0f..1f)
        require(blurRadius.isFinite() && blurRadius >= 0f)
        require(drawLength == null || drawLength.isFinite() && drawLength >= 0f)
        require(drawProgress.isFinite() && drawProgress in 0f..1f)
    }
}

/** Element geometry and direction supplied when creating a transition. */
public data class ElementTransitionContext(
    val element: Element,
    val bounds: Rect,
    val direction: TransitionDirection,
)

/** Timing callbacks and sampling function returned by an [ElementTransition]. */
public data class ElementTransitionConfig(
    val delayMillis: Long = 0,
    val durationMillis: Long = 400,
    val easing: (Float) -> Float = StructuralEasings.linear,
    val tick: (t: Float, u: Float) -> Unit = { _, _ -> },
    val sample: (t: Float, u: Float) -> TransitionFrame = { _, _ -> TransitionFrame() },
) {
    init {
        require(delayMillis >= 0)
        require(durationMillis >= 0)
    }
}

/** Creates a visual enter or exit transition for an element. */
public fun interface ElementTransition {
    /** Creates timing and sampling configuration for [context]. */
    public fun create(context: ElementTransitionContext): ElementTransitionConfig
}

private interface PreparedElementTransition : ElementTransition {
    fun prepare(context: ElementTransitionContext)

    fun finish(context: ElementTransitionContext)
}

/** Bidirectional or independently configured enter/exit effects for structural content. */
public data class StructuralTransition(
    val bidirectional: ElementTransition? = null,
    val enter: ElementTransition? = null,
    val exit: ElementTransition? = null,
) {
    init {
        require(bidirectional == null || enter == null && exit == null) {
            "A bidirectional transition cannot be combined with independent enter/exit transitions"
        }
    }
}

/** Uses [effect] for both entering and exiting, with reversible interruption. */
public fun transition(effect: ElementTransition): StructuralTransition =
    StructuralTransition(bidirectional = effect)

/** Creates a structural transition with independent [enter] and [exit] effects. */
public fun inOut(
    enter: ElementTransition? = null,
    exit: ElementTransition? = null,
): StructuralTransition = StructuralTransition(enter = enter, exit = exit)

/** Creates an opacity transition. */
public fun fade(
    durationMillis: Long = 400,
    delayMillis: Long = 0,
    opacity: Float = 0f,
    easing: (Float) -> Float = StructuralEasings.linear,
): ElementTransition {
    require(opacity in 0f..1f)
    return ElementTransition {
        ElementTransitionConfig(delayMillis, durationMillis, easing) { t, _ ->
            TransitionFrame(opacity = opacity + (1f - opacity) * t)
        }
    }
}

/** Creates a translated opacity transition. */
public fun fly(
    x: Float = 0f,
    y: Float = 0f,
    opacity: Float = 0f,
    durationMillis: Long = 400,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicOut,
): ElementTransition {
    require(opacity in 0f..1f)
    return ElementTransition {
        ElementTransitionConfig(delayMillis, durationMillis, easing) { t, u ->
            TransitionFrame(
                transform = Matrix3.translation(x * u, y * u),
                opacity = opacity + (1f - opacity) * t,
            )
        }
    }
}

/** Creates an origin-aware scale and opacity transition. */
public fun scale(
    start: Float = 0f,
    opacity: Float = 0f,
    originX: Float = .5f,
    originY: Float = .5f,
    durationMillis: Long = 400,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicOut,
): ElementTransition {
    require(start >= 0f && start.isFinite())
    require(opacity in 0f..1f)
    return ElementTransition { context ->
        val centerX = context.bounds.width * originX
        val centerY = context.bounds.height * originY
        ElementTransitionConfig(delayMillis, durationMillis, easing) { t, _ ->
            val value = start + (1f - start) * t
            TransitionFrame(
                transform =
                    Matrix3.translation(centerX, centerY) *
                        Matrix3.scale(value) *
                        Matrix3.translation(-centerX, -centerY),
                opacity = opacity + (1f - opacity) * t,
            )
        }
    }
}

/** Axis revealed by [slide]. */
public enum class SlideAxis {
    HORIZONTAL,
    VERTICAL,
}

/** Creates a clipped horizontal or vertical reveal transition. */
public fun slide(
    axis: SlideAxis = SlideAxis.VERTICAL,
    durationMillis: Long = 400,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicOut,
): ElementTransition = ElementTransition { context ->
    ElementTransitionConfig(delayMillis, durationMillis, easing) { t, _ ->
        TransitionFrame(
            clip =
                if (axis == SlideAxis.HORIZONTAL)
                    Rect(0f, 0f, context.bounds.width * t, context.bounds.height)
                else Rect(0f, 0f, context.bounds.width, context.bounds.height * t),
            opacity = min(t * 20f, 1f),
        )
    }
}

/** Creates a blur and opacity transition. */
public fun blur(
    amount: Float = 5f,
    opacity: Float = 0f,
    durationMillis: Long = 400,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicInOut,
): ElementTransition {
    require(amount.isFinite() && amount >= 0f)
    require(opacity in 0f..1f)
    return ElementTransition {
        ElementTransitionConfig(delayMillis, durationMillis, easing) { t, u ->
            TransitionFrame(
                opacity = opacity + (1f - opacity) * t,
                blurRadius = amount * u,
            )
        }
    }
}

/** Creates a path-drawing transition for content implementing `PathMetrics`. */
public fun draw(
    durationMillis: Long? = null,
    speed: Float? = null,
    durationForLength: ((length: Float) -> Long)? = null,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicInOut,
): ElementTransition {
    require(durationMillis == null || durationMillis >= 0)
    require(speed == null || speed.isFinite() && speed > 0f)
    require(listOf(durationMillis, speed, durationForLength).count { it != null } <= 1) {
        "draw accepts only one of durationMillis, speed, or durationForLength"
    }
    return ElementTransition { context ->
        val metrics =
            context.element.content as? PathMetrics
                ?: error("draw requires element content implementing PathMetrics")
        val length = metrics.pathLength + metrics.strokeExtension
        require(length.isFinite() && length >= 0f) { "Path length must be finite and non-negative" }
        val duration =
            durationMillis
                ?: speed?.let { (length / it).toLong() }
                ?: durationForLength?.invoke(length)
                ?: 800L
        require(duration >= 0) { "draw duration must be non-negative" }
        ElementTransitionConfig(delayMillis, duration, easing) { t, _ ->
            TransitionFrame(drawLength = length, drawProgress = t)
        }
    }
}

/** Paired send/receive transitions that interpolate matching element geometry. */
public class CrossfadeTransitions
internal constructor(
    private val delayMillis: Long,
    private val durationMillis: (distance: Float) -> Long,
    private val easing: (Float) -> Float,
    private val fallback: ElementTransition?,
) {
    private enum class Side {
        SEND,
        RECEIVE,
    }

    private data class Candidate(val element: Element, val bounds: Rect)

    private val sends = mutableMapOf<Any, Candidate>()
    private val receives = mutableMapOf<Any, Candidate>()

    /** Creates the outgoing side of a crossfade identified by [key]. */
    public fun send(key: Any): ElementTransition = CrossfadeTransition(key, Side.SEND)

    /** Creates the incoming side of a crossfade identified by [key]. */
    public fun receive(key: Any): ElementTransition = CrossfadeTransition(key, Side.RECEIVE)

    private inner class CrossfadeTransition(
        private val key: Any,
        private val side: Side,
    ) : PreparedElementTransition {
        override fun prepare(context: ElementTransitionContext) {
            val candidates = if (side == Side.SEND) sends else receives
            require(key !in candidates) { "Duplicate crossfade ${side.name.lowercase()} key: $key" }
            candidates[key] = Candidate(context.element, context.bounds)
        }

        override fun create(context: ElementTransitionContext): ElementTransitionConfig {
            val counterpart =
                (if (side == Side.SEND) receives[key] else sends[key])
                    ?: return fallback?.create(context) ?: naturalTransition()
            val dx = counterpart.bounds.x - context.bounds.x
            val dy = counterpart.bounds.y - context.bounds.y
            val scaleX =
                if (context.bounds.width == 0f) 1f
                else counterpart.bounds.width / context.bounds.width
            val scaleY =
                if (context.bounds.height == 0f) 1f
                else counterpart.bounds.height / context.bounds.height
            val duration = durationMillis(hypot(dx, dy))
            require(duration >= 0) { "Crossfade duration must be non-negative" }
            return ElementTransitionConfig(delayMillis, duration, easing) { t, u ->
                TransitionFrame(
                    transform =
                        Matrix3.translation(dx * u, dy * u) *
                            Matrix3.scale(
                                1f + (scaleX - 1f) * u,
                                1f + (scaleY - 1f) * u,
                            ),
                    opacity = t,
                )
            }
        }

        override fun finish(context: ElementTransitionContext) {
            if (side == Side.SEND) sends.remove(key) else receives.remove(key)
        }
    }
}

/** Creates a keyed crossfade registry with an optional unmatched [fallback]. */
public fun crossfade(
    delayMillis: Long = 0,
    durationMillis: (distance: Float) -> Long = { distance ->
        sqrt(distance.coerceAtLeast(0f)).times(30f).toLong()
    },
    easing: (Float) -> Float = StructuralEasings.cubicOut,
    fallback: ElementTransition? = null,
): CrossfadeTransitions {
    require(delayMillis >= 0)
    return CrossfadeTransitions(delayMillis, durationMillis, easing, fallback)
}

/** Previous and current bounds supplied to a [LayoutAnimation]. */
public data class LayoutAnimationContext(
    val element: Element,
    val from: Rect,
    val to: Rect,
)

/** Timing callbacks and sampling function returned by a [LayoutAnimation]. */
public data class LayoutAnimationConfig(
    val delayMillis: Long = 0,
    val durationMillis: Long = 400,
    val easing: (Float) -> Float = StructuralEasings.linear,
    val tick: (t: Float, u: Float) -> Unit = { _, _ -> },
    val sample: (t: Float, u: Float) -> TransitionFrame = { _, _ -> TransitionFrame() },
) {
    init {
        require(delayMillis >= 0)
        require(durationMillis >= 0)
    }
}

/** Creates motion between an element's previous and current committed bounds. */
public fun interface LayoutAnimation {
    /** Creates timing and sampling configuration for [context]. */
    public fun create(context: LayoutAnimationContext): LayoutAnimationConfig
}

/** First-last-invert-play animation for keyed layout movement. */
public data class FlipAnimation(
    val delayMillis: Long = 0,
    val durationMillis: (distance: Float) -> Long = { distance ->
        (sqrt(distance) * 120f).toLong()
    },
    val easing: (Float) -> Float = StructuralEasings.cubicOut,
) : LayoutAnimation {
    init {
        require(delayMillis >= 0)
    }

    override fun create(context: LayoutAnimationContext): LayoutAnimationConfig {
        require(context.to.width > 0f && context.to.height > 0f) {
            "FLIP target bounds must have positive dimensions"
        }
        val dx = context.from.x - context.to.x
        val dy = context.from.y - context.to.y
        val scaleX = context.from.width / context.to.width
        val scaleY = context.from.height / context.to.height
        val duration = durationMillis(hypot(dx, dy))
        require(duration >= 0) { "FLIP duration must be non-negative" }
        return LayoutAnimationConfig(
            delayMillis,
            duration,
            easing,
            sample = { _, u ->
                TransitionFrame(
                    transform =
                        Matrix3.translation(dx * u, dy * u) *
                            Matrix3.scale(1f + (scaleX - 1f) * u, 1f + (scaleY - 1f) * u)
                )
            },
        )
    }
}

/** Creates a FLIP animation with a fixed or distance-derived duration. */
public fun flip(
    durationMillis: Long? = null,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicOut,
): FlipAnimation {
    require(durationMillis == null || durationMillis >= 0)
    return FlipAnimation(
        delayMillis,
        durationMillis?.let { fixed -> { _: Float -> fixed } }
            ?: { distance ->
                (sqrt(distance.coerceAtLeast(0f)) * 120f).toLong()
            },
        easing,
    )
}

/** Creates a FLIP animation whose duration is computed from travel distance. */
public fun flip(
    durationMillis: (distance: Float) -> Long,
    delayMillis: Long = 0,
    easing: (Float) -> Float = StructuralEasings.cubicOut,
): FlipAnimation = FlipAnimation(delayMillis, durationMillis, easing)

/** Event emitted for a keyed layout animation. */
public data class LayoutAnimationEvent(val element: Element)

/** Callback set for keyed layout animation lifecycle events. */
public data class LayoutAnimationEvents(
    val onStart: (LayoutAnimationEvent) -> Unit = {},
    val onEnd: (LayoutAnimationEvent) -> Unit = {},
    val onCancel: (LayoutAnimationEvent) -> Unit = {},
)

/** Captured committed bounds used to prepare keyed FLIP animation. */
public class FlipSnapshot internal constructor(internal val bounds: Map<Element, Rect>)

/** Disposable handle for a running structural element transition. */
public interface ElementTransitionHandle : AutoCloseable {
    public val isActive: Boolean
}

/** Root-owned runtime for structural, crossfade, and keyed layout animation. */
public class ElementAnimationRuntime(
    private val clock: UiAnimationClock,
    private val configureMotion: (Element, MotionRenderProperties?) -> Unit,
    private val committedBounds: (Element) -> Rect?,
    private val requestFrame: () -> Unit,
) : AutoCloseable {
    private data class Key(val element: Element, val channel: Any)

    private data class Pending(
        val key: Key,
        val effect: ElementTransition,
        val direction: TransitionDirection,
        val fromOverride: Float?,
        val onStart: () -> Unit,
        val onEnd: () -> Unit,
        val onCancel: () -> Unit,
        val onFinished: () -> Unit,
    )

    private data class Track(
        val key: Key,
        val config: ElementTransitionConfig,
        val from: Float,
        val target: Float,
        val startNanos: Long,
        val durationMillis: Float,
        val onEnd: () -> Unit,
        val onCancel: () -> Unit,
        val onFinished: () -> Unit,
        var current: Float = from,
    )

    private data class PendingLayoutAnimation(
        val element: Element,
        val from: Rect,
        val animation: LayoutAnimation,
        val events: LayoutAnimationEvents,
    )

    private val pending = linkedMapOf<Key, Pending>()
    private val pendingLayoutAnimations = linkedMapOf<Element, PendingLayoutAnimation>()
    private val tracks = linkedMapOf<Key, Track>()
    private var scheduled = false

    public val activeCount: Int
        get() = pending.size + pendingLayoutAnimations.size + tracks.size

    public fun isActive(element: Element, channel: Any): Boolean {
        val key = Key(element, channel)
        return key in pending || key in tracks
    }

    /** Starts a transition in [channel], replacing incompatible motion for the same element. */
    public fun start(
        element: Element,
        channel: Any,
        effect: ElementTransition,
        direction: TransitionDirection,
        events: TransitionEvents = TransitionEvents(),
        reverse: Boolean = false,
        onFinished: () -> Unit = {},
    ): ElementTransitionHandle {
        val key = Key(element, channel)
        val previous = tracks.remove(key)
        val previousPending = pending.remove(key)
        val fromOverride = if (reverse) previous?.current else null
        previous?.cancel()
        previousPending?.cancel()
        pending[key] =
            Pending(
                key,
                effect,
                direction,
                fromOverride,
                onStart = { events.start(element, direction) },
                onEnd = { events.end(element, direction) },
                onCancel = { events.cancel(element, direction) },
                onFinished,
            )
        requestFrame()
        return handle(key)
    }

    /** Cancels active motion for [element] in [channel]. */
    public fun cancel(element: Element, channel: Any): Boolean {
        val key = Key(element, channel)
        val pendingTrack = pending.remove(key)
        val runningTrack = tracks.remove(key)
        pendingTrack?.cancel()
        runningTrack?.cancel()
        if (pendingTrack != null || runningTrack != null) apply(element)
        return pendingTrack != null || runningTrack != null
    }

    /** Captures committed bounds for later FLIP comparison. */
    public fun captureFlip(elements: Collection<Element>): FlipSnapshot {
        val snapshot =
            elements.filter(Element::isMounted).associateWith { committedBounds(it) ?: it.geometry }
        elements.forEach { cancel(it, FlipChannel) }
        return FlipSnapshot(snapshot)
    }

    /** Queues layout animation after the next committed bounds become available. */
    public fun queueFlip(
        snapshot: FlipSnapshot,
        retained: Collection<Element>,
        animation: LayoutAnimation,
        events: LayoutAnimationEvents = LayoutAnimationEvents(),
    ) {
        retained.forEach { element ->
            val from = snapshot.bounds[element] ?: return@forEach
            pendingLayoutAnimations
                .put(element, PendingLayoutAnimation(element, from, animation, events))
                ?.let {
                    it.events.onCancel(LayoutAnimationEvent(it.element))
                }
        }
        if (pendingLayoutAnimations.isNotEmpty()) requestFrame()
    }

    /** Starts transitions after layout and committed geometry are available. */
    public fun afterCommit(): Boolean {
        if (pending.isEmpty() && pendingLayoutAnimations.isEmpty()) return false
        val starts = pending.values.toMutableList()
        pending.clear()
        val layoutAnimations = pendingLayoutAnimations.values.toList()
        pendingLayoutAnimations.clear()
        layoutAnimations.forEach { pendingAnimation ->
            val to = committedBounds(pendingAnimation.element) ?: return@forEach
            if (to == pendingAnimation.from || to.width <= 0f || to.height <= 0f) return@forEach
            val config =
                pendingAnimation.animation.create(
                    LayoutAnimationContext(pendingAnimation.element, pendingAnimation.from, to)
                )
            val event = LayoutAnimationEvent(pendingAnimation.element)
            starts +=
                Pending(
                    Key(pendingAnimation.element, FlipChannel),
                    ElementTransition {
                        ElementTransitionConfig(
                            config.delayMillis,
                            config.durationMillis,
                            config.easing,
                            config.tick,
                            config.sample,
                        )
                    },
                    TransitionDirection.IN,
                    fromOverride = null,
                    onStart = { pendingAnimation.events.onStart(event) },
                    onEnd = { pendingAnimation.events.onEnd(event) },
                    onCancel = { pendingAnimation.events.onCancel(event) },
                    onFinished = {},
                )
        }
        val prepared = starts.mapNotNull { pendingTrack ->
            val element = pendingTrack.key.element
            if (!element.isMounted) {
                pendingTrack.cancel()
                null
            } else {
                pendingTrack to
                    ElementTransitionContext(
                        element,
                        committedBounds(element) ?: element.geometry,
                        pendingTrack.direction,
                    )
            }
        }
        prepared.forEach { (pendingTrack, context) ->
            (pendingTrack.effect as? PreparedElementTransition)?.prepare(context)
        }
        try {
            prepared.forEach { (pendingTrack, context) ->
                val element = pendingTrack.key.element
                val createdConfig = pendingTrack.effect.create(context)
                val config =
                    if (pendingTrack.fromOverride == null) createdConfig
                    else createdConfig.copy(delayMillis = 0)
                val target = if (pendingTrack.direction == TransitionDirection.IN) 1f else 0f
                val defaultFrom = if (pendingTrack.direction == TransitionDirection.IN) 0f else 1f
                val from = pendingTrack.fromOverride ?: defaultFrom
                pendingTrack.onStart()
                if (clock.motionScale == 0f || config.durationMillis == 0L) {
                    config.tick(target, 1f - target)
                    configureMotion(element, null)
                    pendingTrack.onEnd()
                    pendingTrack.onFinished()
                } else {
                    config.tick(from, 1f - from)
                    tracks[pendingTrack.key] =
                        Track(
                            pendingTrack.key,
                            config,
                            from,
                            target,
                            clock.frameTimeNanos,
                            config.durationMillis * abs(target - from),
                            pendingTrack.onEnd,
                            pendingTrack.onCancel,
                            pendingTrack.onFinished,
                        )
                }
            }
        } finally {
            prepared.forEach { (pendingTrack, context) ->
                (pendingTrack.effect as? PreparedElementTransition)?.finish(context)
            }
        }
        tracks.keys.map(Key::element).distinct().forEach(::apply)
        ensureScheduled()
        return true
    }

    private fun ensureScheduled() {
        if (scheduled || tracks.isEmpty()) return
        scheduled = true
        clock.animate(::frame)
    }

    private fun frame(timeNanos: Long): Boolean {
        val completed = mutableListOf<Track>()
        tracks.values.toList().forEach { track ->
            if (!track.key.element.isMounted) {
                tracks.remove(track.key)
                track.cancel()
                return@forEach
            }
            val elapsedMillis = (timeNanos - track.startNanos) / 1_000_000f / clock.motionScale
            val rawFraction =
                if (elapsedMillis < track.config.delayMillis) 0f
                else if (track.durationMillis == 0f) 1f
                else
                    ((elapsedMillis - track.config.delayMillis) / track.durationMillis).coerceIn(
                        0f,
                        1f,
                    )
            val fraction =
                if (rawFraction >= 1f) 1f
                else
                    track.config.easing(rawFraction).also {
                        require(it.isFinite()) { "Transition easing must return a finite value" }
                    }
            track.current = track.from + (track.target - track.from) * fraction
            track.config.tick(track.current, 1f - track.current)
            if (rawFraction >= 1f) completed += track
        }
        tracks.keys.map(Key::element).distinct().forEach(::apply)
        completed.forEach { track ->
            tracks.remove(track.key)
            track.onEnd()
            track.onFinished()
            apply(track.key.element)
        }
        val active = tracks.isNotEmpty()
        scheduled = active
        if (active) requestFrame()
        return active
    }

    private fun apply(element: Element) {
        val samples =
            tracks.values
                .filter { it.key.element === element }
                .map { track ->
                    track.config.sample(track.current, 1f - track.current)
                }
        if (samples.isEmpty()) {
            configureMotion(element, null)
            return
        }
        configureMotion(
            element,
            MotionRenderProperties(
                transform =
                    samples.fold(Matrix3.IDENTITY) { result, sample ->
                        result * sample.transform
                    },
                opacity = samples.fold(1f) { result, sample -> result * sample.opacity },
                clip =
                    samples.mapNotNull(TransitionFrame::clip).reduceOrNull { left, right ->
                        left.intersect(right) ?: Rect(0f, 0f, 0f, 0f)
                    },
                blurRadius = samples.sumOf { it.blurRadius.toDouble() }.toFloat(),
                drawLength = samples.mapNotNull(TransitionFrame::drawLength).lastOrNull(),
                drawProgress = samples.lastOrNull { it.drawLength != null }?.drawProgress ?: 1f,
            ),
        )
    }

    private fun handle(key: Key): ElementTransitionHandle =
        object : ElementTransitionHandle {
            override val isActive: Boolean
                get() = key in pending || key in tracks

            override fun close() {
                cancel(key.element, key.channel)
            }
        }

    private fun Pending.cancel() {
        onCancel()
    }

    private fun Track.cancel() {
        onCancel()
    }

    override fun close() {
        val elements = (pending.keys + tracks.keys).map(Key::element).distinct()
        pending.values.forEach { it.cancel() }
        tracks.values.forEach { it.cancel() }
        pending.clear()
        pendingLayoutAnimations.values.forEach {
            it.events.onCancel(LayoutAnimationEvent(it.element))
        }
        pendingLayoutAnimations.clear()
        tracks.clear()
        elements.forEach { configureMotion(it, null) }
        scheduled = false
    }
}

private object FlipChannel

private fun naturalTransition(): ElementTransitionConfig =
    ElementTransitionConfig(durationMillis = 0) { _, _ -> TransitionFrame() }

private fun TransitionEvents.start(element: Element, direction: TransitionDirection) {
    dispatch(
        TransitionEvent(
            element,
            if (direction == TransitionDirection.IN) TransitionEventType.INTRO_START
            else TransitionEventType.OUTRO_START,
            direction,
        )
    )
}

private fun TransitionEvents.end(element: Element, direction: TransitionDirection) {
    dispatch(
        TransitionEvent(
            element,
            if (direction == TransitionDirection.IN) TransitionEventType.INTRO_END
            else TransitionEventType.OUTRO_END,
            direction,
        )
    )
}

private fun TransitionEvents.cancel(element: Element, direction: TransitionDirection) {
    dispatch(TransitionEvent(element, TransitionEventType.CANCEL, direction))
}
