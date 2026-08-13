package com.antepod.lumentika.animation

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.render.MotionRenderProperties
import com.antepod.lumentika.runtime.Element
import kotlin.math.abs

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
        val events: TransitionEvents,
        val fromOverride: Float?,
        val onFinished: () -> Unit,
    )

    private data class Track(
        val key: Key,
        val config: ElementTransitionConfig,
        val direction: TransitionDirection,
        val events: TransitionEvents,
        val from: Float,
        val target: Float,
        val startNanos: Long,
        val durationMillis: Float,
        val onFinished: () -> Unit,
        var current: Float = from,
    )

    private val pending = linkedMapOf<Key, Pending>()
    private val tracks = linkedMapOf<Key, Track>()
    private var scheduled = false

    public val activeCount: Int
        get() = pending.size + tracks.size

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
        pending[key] = Pending(key, effect, direction, events, fromOverride, onFinished)
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

    /** Starts transitions after layout and committed geometry are available. */
    public fun afterCommit(): Boolean {
        if (pending.isEmpty()) return false
        val starts = pending.values.toList()
        pending.clear()
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
            pendingTrack.events.start(element, pendingTrack.direction)
            if (clock.motionScale == 0f || config.durationMillis == 0L) {
                configureMotion(element, config.sample(target, 1f - target).toMotion())
                pendingTrack.events.end(element, pendingTrack.direction)
                pendingTrack.onFinished()
            } else {
                tracks[pendingTrack.key] =
                    Track(
                        pendingTrack.key,
                        config,
                        pendingTrack.direction,
                        pendingTrack.events,
                        from,
                        target,
                        clock.frameTimeNanos,
                        config.durationMillis * abs(target - from),
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
            track.events.end(track.key.element, track.direction)
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
        events.cancel(key.element, direction)
    }

    private fun Track.cancel() {
        events.cancel(key.element, direction)
    }

    override fun close() {
        val elements = (pending.keys + tracks.keys).map(Key::element).distinct()
        pending.values.forEach { it.cancel() }
        tracks.values.forEach { it.cancel() }
        pending.clear()
        tracks.clear()
        elements.forEach { configureMotion(it, null) }
        scheduled = false
    }
}

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
