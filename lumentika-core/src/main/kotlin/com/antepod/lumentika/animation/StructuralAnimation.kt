package com.antepod.lumentika.animation

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.render.MotionRenderProperties
import com.antepod.lumentika.runtime.Element
import kotlin.math.abs
import kotlin.math.hypot

public enum class TransitionDirection {
    IN,
    OUT,
}

public enum class TransitionEventType {
    INTRO_START,
    INTRO_END,
    OUTRO_START,
    OUTRO_END,
    CANCEL,
}

public data class TransitionEvent(
    val element: Element,
    val type: TransitionEventType,
    val direction: TransitionDirection,
)

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

public data class TransitionFrame(
    val transform: Matrix3 = Matrix3.IDENTITY,
    val opacity: Float = 1f,
) {
    init {
        require(opacity.isFinite() && opacity in 0f..1f)
    }
}

public data class ElementTransitionContext(
    val element: Element,
    val bounds: Rect,
    val direction: TransitionDirection,
)

public data class ElementTransitionConfig(
    val delayMillis: Long = 0,
    val durationMillis: Long = 150,
    val easing: (Float) -> Float = { it },
    val sample: (t: Float, u: Float) -> TransitionFrame,
) {
    init {
        require(delayMillis >= 0)
        require(durationMillis >= 0)
    }
}

public fun interface ElementTransition {
    public fun create(context: ElementTransitionContext): ElementTransitionConfig
}

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

public fun transition(effect: ElementTransition): StructuralTransition =
    StructuralTransition(bidirectional = effect)

public fun inOut(
    enter: ElementTransition? = null,
    exit: ElementTransition? = null,
): StructuralTransition = StructuralTransition(enter = enter, exit = exit)

public fun fade(
    durationMillis: Long = 150,
    delayMillis: Long = 0,
    opacity: Float = 0f,
    easing: (Float) -> Float = { it },
): ElementTransition {
    require(opacity in 0f..1f)
    return ElementTransition {
        ElementTransitionConfig(delayMillis, durationMillis, easing) { t, _ ->
            TransitionFrame(opacity = opacity + (1f - opacity) * t)
        }
    }
}

public fun fly(
    x: Float = 0f,
    y: Float = 0f,
    opacity: Float = 0f,
    durationMillis: Long = 200,
    delayMillis: Long = 0,
    easing: (Float) -> Float = { it },
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

public fun scale(
    start: Float = 0f,
    opacity: Float = 0f,
    originX: Float = .5f,
    originY: Float = .5f,
    durationMillis: Long = 150,
    delayMillis: Long = 0,
    easing: (Float) -> Float = { it },
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

public enum class SlideAxis {
    HORIZONTAL,
    VERTICAL,
}

public fun slide(
    axis: SlideAxis = SlideAxis.VERTICAL,
    durationMillis: Long = 200,
    delayMillis: Long = 0,
    easing: (Float) -> Float = { it },
): ElementTransition = ElementTransition {
    ElementTransitionConfig(delayMillis, durationMillis, easing) { t, _ ->
        TransitionFrame(
            transform =
                if (axis == SlideAxis.HORIZONTAL) Matrix3.scale(t, 1f) else Matrix3.scale(1f, t),
            opacity = t,
        )
    }
}

public data class FlipAnimation(
    val delayMillis: Long = 0,
    val durationMillis: (distance: Float) -> Long = { 200L },
    val easing: (Float) -> Float = { it },
) {
    init {
        require(delayMillis >= 0)
    }
}

public fun flip(
    durationMillis: Long = 200,
    delayMillis: Long = 0,
    easing: (Float) -> Float = { it },
): FlipAnimation {
    require(durationMillis >= 0)
    return FlipAnimation(delayMillis, { durationMillis }, easing)
}

public data class LayoutAnimationEvent(val element: Element)

public data class LayoutAnimationEvents(
    val onStart: (LayoutAnimationEvent) -> Unit = {},
    val onEnd: (LayoutAnimationEvent) -> Unit = {},
    val onCancel: (LayoutAnimationEvent) -> Unit = {},
)

public class FlipSnapshot internal constructor(internal val bounds: Map<Element, Rect>)

public interface ElementTransitionHandle : AutoCloseable {
    public val isActive: Boolean
}

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

    private data class PendingFlip(
        val element: Element,
        val from: Rect,
        val animation: FlipAnimation,
        val events: LayoutAnimationEvents,
    )

    private val pending = linkedMapOf<Key, Pending>()
    private val pendingFlips = linkedMapOf<Element, PendingFlip>()
    private val tracks = linkedMapOf<Key, Track>()
    private var scheduled = false

    public val activeCount: Int
        get() = pending.size + pendingFlips.size + tracks.size

    public fun isActive(element: Element, channel: Any): Boolean {
        val key = Key(element, channel)
        return key in pending || key in tracks
    }

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

    public fun cancel(element: Element, channel: Any): Boolean {
        val key = Key(element, channel)
        val pendingTrack = pending.remove(key)
        val runningTrack = tracks.remove(key)
        pendingTrack?.cancel()
        runningTrack?.cancel()
        if (pendingTrack != null || runningTrack != null) apply(element)
        return pendingTrack != null || runningTrack != null
    }

    public fun captureFlip(elements: Collection<Element>): FlipSnapshot {
        val snapshot =
            elements.filter(Element::isMounted).associateWith { committedBounds(it) ?: it.geometry }
        elements.forEach { cancel(it, FlipChannel) }
        return FlipSnapshot(snapshot)
    }

    public fun queueFlip(
        snapshot: FlipSnapshot,
        retained: Collection<Element>,
        animation: FlipAnimation,
        events: LayoutAnimationEvents = LayoutAnimationEvents(),
    ) {
        retained.forEach { element ->
            val from = snapshot.bounds[element] ?: return@forEach
            pendingFlips.put(element, PendingFlip(element, from, animation, events))?.let {
                it.events.onCancel(LayoutAnimationEvent(it.element))
            }
        }
        if (pendingFlips.isNotEmpty()) requestFrame()
    }

    /** Starts transitions after layout and committed geometry are available. */
    public fun afterCommit(): Boolean {
        if (pending.isEmpty() && pendingFlips.isEmpty()) return false
        val starts = pending.values.toMutableList()
        pending.clear()
        val flips = pendingFlips.values.toList()
        pendingFlips.clear()
        flips.forEach { flip ->
            val to = committedBounds(flip.element) ?: return@forEach
            if (to == flip.from || to.width <= 0f || to.height <= 0f) return@forEach
            val dx = flip.from.x - to.x
            val dy = flip.from.y - to.y
            val scaleX = flip.from.width / to.width
            val scaleY = flip.from.height / to.height
            val distance = hypot(dx, dy)
            val duration = flip.animation.durationMillis(distance)
            require(duration >= 0) { "FLIP duration must be non-negative" }
            val event = LayoutAnimationEvent(flip.element)
            starts +=
                Pending(
                    Key(flip.element, FlipChannel),
                    ElementTransition {
                        ElementTransitionConfig(
                            flip.animation.delayMillis,
                            duration,
                            flip.animation.easing,
                        ) { t, u ->
                            TransitionFrame(
                                transform =
                                    Matrix3.translation(dx * u, dy * u) *
                                        Matrix3.scale(
                                            1f + (scaleX - 1f) * u,
                                            1f + (scaleY - 1f) * u,
                                        )
                            )
                        }
                    },
                    TransitionDirection.IN,
                    fromOverride = null,
                    onStart = { flip.events.onStart(event) },
                    onEnd = { flip.events.onEnd(event) },
                    onCancel = { flip.events.onCancel(event) },
                    onFinished = {},
                )
        }
        starts.forEach { pendingTrack ->
            val element = pendingTrack.key.element
            if (!element.isMounted) {
                pendingTrack.cancel()
                return@forEach
            }
            val bounds = committedBounds(element) ?: element.geometry
            val createdConfig =
                pendingTrack.effect.create(
                    ElementTransitionContext(element, bounds, pendingTrack.direction)
                )
            val config =
                if (pendingTrack.fromOverride == null) createdConfig
                else createdConfig.copy(delayMillis = 0)
            val target = if (pendingTrack.direction == TransitionDirection.IN) 1f else 0f
            val defaultFrom = if (pendingTrack.direction == TransitionDirection.IN) 0f else 1f
            val from = pendingTrack.fromOverride ?: defaultFrom
            pendingTrack.onStart()
            if (clock.motionScale == 0f || config.durationMillis == 0L) {
                configureMotion(element, config.sample(target, 1f - target).toMotion())
                pendingTrack.onEnd()
                pendingTrack.onFinished()
            } else {
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
            val fraction =
                if (elapsedMillis < track.config.delayMillis) 0f
                else if (track.durationMillis == 0f) 1f
                else
                    ((elapsedMillis - track.config.delayMillis) / track.durationMillis)
                        .coerceIn(0f, 1f)
                        .let(track.config.easing)
                        .coerceIn(0f, 1f)
            track.current = track.from + (track.target - track.from) * fraction
            if (fraction >= 1f) completed += track
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
        pendingFlips.values.forEach {
            it.events.onCancel(LayoutAnimationEvent(it.element))
        }
        pendingFlips.clear()
        tracks.clear()
        elements.forEach { configureMotion(it, null) }
        scheduled = false
    }
}

private object FlipChannel

private fun TransitionFrame.toMotion(): MotionRenderProperties =
    MotionRenderProperties(transform, opacity)

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
