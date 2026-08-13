package com.antepod.lumentika.animation

import kotlin.math.exp

public sealed interface MotionSpec

public data class TweenSpec(val durationMillis: Long = 150, val easing: (Float) -> Float = { it }) :
    MotionSpec

public data class SpringSpec(val stiffness: Float = 360f, val dampingRatio: Float = .82f) :
    MotionSpec

public interface AnimationAdapter<T> {
    public fun interpolate(from: T, to: T, fraction: Float): T
}

public object FloatAnimationAdapter : AnimationAdapter<Float> {
    override fun interpolate(from: Float, to: Float, fraction: Float) =
        from + (to - from) * fraction
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
        return f < .999f
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
