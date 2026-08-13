package com.antepod.lumentika.animation

import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.style.DimensionValue
import com.antepod.lumentika.style.Dp
import com.antepod.lumentika.style.Percent
import com.antepod.lumentika.style.PhysicalPx
import com.antepod.lumentika.style.Px
import com.antepod.lumentika.style.ResolvedStyle
import com.antepod.lumentika.style.Sp
import com.antepod.lumentika.style.StyleImpact
import com.antepod.lumentika.style.StyleProperty
import kotlin.math.exp

public sealed interface MotionSpec

public data class TweenSpec(val durationMillis: Long = 150, val easing: (Float) -> Float = { it }) :
    MotionSpec

public data class SpringSpec(val stiffness: Float = 360f, val dampingRatio: Float = .82f) :
    MotionSpec

public interface AnimationAdapter<T> {
    public fun canInterpolate(from: T, to: T): Boolean = true

    public fun interpolate(from: T, to: T, fraction: Float): T
}

public object FloatAnimationAdapter : AnimationAdapter<Float> {
    override fun interpolate(from: Float, to: Float, fraction: Float) =
        from + (to - from) * fraction
}

public object DimensionAnimationAdapter : AnimationAdapter<DimensionValue> {
    override fun canInterpolate(from: DimensionValue, to: DimensionValue): Boolean =
        from::class == to::class &&
            (from is Px || from is Dp || from is Sp || from is PhysicalPx || from is Percent)

    override fun interpolate(
        from: DimensionValue,
        to: DimensionValue,
        fraction: Float,
    ): DimensionValue =
        when {
            from is Px && to is Px ->
                Px(FloatAnimationAdapter.interpolate(from.value, to.value, fraction))
            from is Dp && to is Dp ->
                Dp(FloatAnimationAdapter.interpolate(from.value, to.value, fraction))
            from is Sp && to is Sp ->
                Sp(FloatAnimationAdapter.interpolate(from.value, to.value, fraction))
            from is PhysicalPx && to is PhysicalPx ->
                PhysicalPx(FloatAnimationAdapter.interpolate(from.value, to.value, fraction))
            from is Percent && to is Percent ->
                Percent(FloatAnimationAdapter.interpolate(from.fraction, to.fraction, fraction))
            else -> to
        }
}

public class AnimationTrack<T>(
    initial: T,
    private val adapter: AnimationAdapter<T>,
    private var spec: MotionSpec,
) {
    private var from = initial
    private var target = initial
    private var start = 0L
    public var value: T = initial
        private set

    public fun retarget(target: T, timeNanos: Long, spec: MotionSpec = this.spec) {
        from = value
        this.target = target
        start = timeNanos
        this.spec = spec
    }

    public fun canRetarget(target: T): Boolean = adapter.canInterpolate(value, target)

    public fun frame(timeNanos: Long, motionScale: Float): Boolean {
        val f =
            when (val s = spec) {
                is TweenSpec ->
                    if (motionScale == 0f) 1f
                    else
                        ((timeNanos - start) / 1_000_000f / (s.durationMillis * motionScale))
                            .coerceIn(0f, 1f)
                            .let(s.easing)
                is SpringSpec ->
                    (1 -
                            exp(
                                -((timeNanos - start) / 1_000_000_000f) *
                                    s.stiffness.coerceAtLeast(1f) / 40f * s.dampingRatio
                            ))
                        .coerceIn(0f, 1f)
            }
        value = adapter.interpolate(from, target, f)
        val active = f < .999f
        if (!active) value = target
        return active
    }
}

public data class Transition<T>(val spec: MotionSpec, val adapter: AnimationAdapter<T>)

public class TransitionSet
internal constructor(internal val values: Map<StyleProperty<*>, Transition<*>>) {
    @Suppress("UNCHECKED_CAST")
    public operator fun <T> get(property: StyleProperty<T>): Transition<T>? =
        values[property] as Transition<T>?
}

public class TransitionBuilder {
    private val values = mutableMapOf<StyleProperty<*>, Transition<*>>()

    public fun include(other: TransitionSet) {
        values += other.values
    }

    public fun <T> set(
        property: StyleProperty<T>,
        spec: MotionSpec,
        adapter: AnimationAdapter<T>,
    ) {
        values[property] = Transition(spec, adapter)
    }

    public var opacity: MotionSpec
        get() = error("write-only")
        set(value) =
            set(com.antepod.lumentika.style.Properties.Opacity, value, FloatAnimationAdapter)

    public var width: MotionSpec
        get() = error("write-only")
        set(value) =
            set(com.antepod.lumentika.style.Properties.Width, value, DimensionAnimationAdapter)

    public var height: MotionSpec
        get() = error("write-only")
        set(value) =
            set(com.antepod.lumentika.style.Properties.Height, value, DimensionAnimationAdapter)

    internal fun build(): TransitionSet = TransitionSet(values.toMap())
}

public fun transitions(block: TransitionBuilder.() -> Unit): TransitionSet =
    TransitionBuilder().apply(block).build()

public class EffectiveStyleOverlay {
    private val values = mutableMapOf<Element, MutableMap<StyleProperty<*>, Any?>>()

    public val size: Int
        get() = values.values.sumOf(Map<*, *>::size)

    internal fun <T> set(element: Element, property: StyleProperty<T>, value: T) {
        values.getOrPut(element, ::mutableMapOf)[property] = value
    }

    internal fun remove(element: Element, property: StyleProperty<*>) {
        values[element]?.let { properties ->
            properties.remove(property)
            if (properties.isEmpty()) values.remove(element)
        }
    }

    public fun effective(element: Element, target: ResolvedStyle): ResolvedStyle =
        values[element]?.entries?.fold(target) { style, (property, value) ->
            style.withUntyped(property, value)
        } ?: target
}

public class StyleAnimationRuntime(
    private val clock: UiAnimationClock,
    private val onImpact: (Element, StyleImpact) -> Unit,
    private val requestFrame: () -> Unit,
) : AutoCloseable {
    private data class Key(val element: Element, val property: StyleProperty<*>)

    private interface RunningTrack {
        val key: Key

        fun frame(timeNanos: Long, motionScale: Float, overlay: EffectiveStyleOverlay): Boolean
    }

    private class TypedTrack<T>(
        override val key: Key,
        private val property: StyleProperty<T>,
        private val track: AnimationTrack<T>,
    ) : RunningTrack {
        override fun frame(
            timeNanos: Long,
            motionScale: Float,
            overlay: EffectiveStyleOverlay,
        ): Boolean {
            val active = track.frame(timeNanos, motionScale)
            overlay.set(key.element, property, track.value)
            return active
        }
    }

    public val overlay: EffectiveStyleOverlay = EffectiveStyleOverlay()
    private val tracks = mutableMapOf<Key, RunningTrack>()
    private var scheduled = false
    public var motionScale: Float = 1f

    public fun <T> transition(
        element: Element,
        property: StyleProperty<T>,
        from: T,
        to: T,
        transition: Transition<T>,
    ) {
        val key = Key(element, property)
        if (!transition.adapter.canInterpolate(from, to)) {
            tracks.remove(key)
            overlay.remove(element, property)
            onImpact(element, property.impact)
            return
        }
        val track = AnimationTrack(from, transition.adapter, transition.spec)
        track.retarget(to, clock.frameTimeNanos)
        tracks[key] = TypedTrack(key, property, track)
        ensureScheduled()
        requestFrame()
    }

    public fun effective(element: Element, target: ResolvedStyle): ResolvedStyle =
        overlay.effective(element, target)

    private fun ensureScheduled() {
        if (scheduled) return
        scheduled = true
        clock.animate(::frame)
    }

    private fun frame(timeNanos: Long): Boolean {
        val completed = mutableListOf<Key>()
        tracks.forEach { (key, track) ->
            if (!key.element.isMounted || !track.frame(timeNanos, motionScale, overlay)) {
                completed += key
            }
            onImpact(key.element, key.property.impact)
        }
        completed.forEach { key ->
            tracks.remove(key)
            overlay.remove(key.element, key.property)
        }
        val active = tracks.isNotEmpty()
        scheduled = active
        if (active) requestFrame()
        return active
    }

    override fun close() {
        tracks.keys.toList().forEach { overlay.remove(it.element, it.property) }
        tracks.clear()
        scheduled = false
    }
}

public class UiAnimationClock {
    public var frameTimeNanos: Long = 0
        private set

    private val callbacks = linkedSetOf<(Long) -> Boolean>()

    public fun animate(callback: (Long) -> Boolean) {
        callbacks += callback
    }

    public fun frame(timeNanos: Long) {
        frameTimeNanos = timeNanos
        callbacks.removeIf { !it(timeNanos) }
    }
}
