package com.antepod.lumentika.gesture

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.platform.GestureConfiguration
import kotlin.math.abs
import kotlin.math.hypot

public enum class GestureDisposition {
    PENDING,
    ACCEPTED,
    REJECTED,
}

public interface GestureRecognizer : AutoCloseable {
    public val team: Any?

    public fun down(point: Point, timeNanos: Long)

    public fun move(point: Point, timeNanos: Long)

    public fun up(point: Point, timeNanos: Long)

    public fun cancel()

    public fun resolve(disposition: GestureDisposition)
}

public class GestureArena {
    private val entries = mutableMapOf<Int, MutableList<GestureRecognizer>>()

    public fun add(pointer: Int, recognizer: GestureRecognizer) {
        entries.getOrPut(pointer, ::mutableListOf) += recognizer
        (recognizer as? SinglePointerRecognizer)?.join(this, pointer)
    }

    public fun resolve(pointer: Int, winner: GestureRecognizer) {
        entries.remove(pointer)?.forEach {
            it.resolve(
                if (it === winner || it.team != null && it.team === winner.team)
                    GestureDisposition.ACCEPTED
                else GestureDisposition.REJECTED
            )
        }
    }

    public fun cancel(pointer: Int) {
        entries.remove(pointer)?.forEach(GestureRecognizer::cancel)
    }
}

public abstract class SinglePointerRecognizer(final override val team: Any?) : GestureRecognizer {
    protected var start = Point(0f, 0f)
    protected var last = start
    protected var startTime = 0L
    protected var rejected = false
    protected var accepted = false
        private set

    private var arena: GestureArena? = null
    private var pointer: Int = 0

    internal fun join(arena: GestureArena, pointer: Int) {
        this.arena = arena
        this.pointer = pointer
    }

    override fun down(point: Point, timeNanos: Long) {
        start = point
        last = point
        startTime = timeNanos
        rejected = false
        accepted = arena == null
    }

    override fun cancel() {
        rejected = true
    }

    override fun resolve(disposition: GestureDisposition) {
        when (disposition) {
            GestureDisposition.ACCEPTED -> accepted = true
            GestureDisposition.REJECTED -> cancel()
            GestureDisposition.PENDING -> Unit
        }
    }

    protected fun accept() {
        val currentArena = arena
        if (currentArena == null) accepted = true else currentArena.resolve(pointer, this)
    }

    override fun close() = cancel()
}

public class TapRecognizer(
    private val config: GestureConfiguration,
    private val onTap: () -> Unit,
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    override fun move(point: Point, timeNanos: Long) {
        if (distance(start, point) > config.touchSlop) rejected = true
        last = point
    }

    override fun up(point: Point, timeNanos: Long) {
        if (!rejected) {
            accept()
            if (accepted) onTap()
        }
    }
}

public class DoubleTapRecognizer(
    private val config: GestureConfiguration,
    private val onDoubleTap: () -> Unit,
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    private var previousUp = Long.MIN_VALUE
    private var previousPoint: Point? = null

    override fun move(point: Point, timeNanos: Long) {
        if (distance(start, point) > config.touchSlop) rejected = true
    }

    override fun up(point: Point, timeNanos: Long) {
        val previous = previousPoint
        if (
            !rejected &&
                previous != null &&
                timeNanos - previousUp <= config.doubleTapTimeoutMillis * 1_000_000 &&
                distance(previous, point) <= config.touchSlop * 2
        ) {
            accept()
            if (accepted) onDoubleTap()
            previousPoint = null
        } else {
            previousUp = timeNanos
            previousPoint = point
        }
    }
}

public class LongPressRecognizer(
    private val config: GestureConfiguration,
    private val onLongPress: () -> Unit,
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    private var fired = false

    override fun down(point: Point, timeNanos: Long) {
        super.down(point, timeNanos)
        fired = false
    }

    override fun move(point: Point, timeNanos: Long) {
        if (distance(start, point) > config.touchSlop) rejected = true
        advance(timeNanos)
    }

    public fun advance(timeNanos: Long) {
        if (
            !rejected &&
                !fired &&
                timeNanos - startTime >= config.longPressTimeoutMillis * 1_000_000
        ) {
            accept()
            fired = true
            if (accepted) onLongPress()
        }
    }

    override fun up(point: Point, timeNanos: Long) {
        advance(timeNanos)
    }
}

public enum class DragAxis {
    FREE,
    HORIZONTAL,
    VERTICAL,
}

public data class DragUpdate(
    val position: Point,
    val delta: Point,
    val total: Point,
    val velocity: ScrollDelta,
)

public class DragRecognizer(
    private val config: GestureConfiguration,
    private val axis: DragAxis = DragAxis.FREE,
    private val onUpdate: (DragUpdate) -> Unit,
    private val onEnd: (ScrollDelta) -> Unit = {},
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    private val velocity = VelocityTracker()
    private var thresholdCrossed = false

    override fun down(point: Point, timeNanos: Long) {
        super.down(point, timeNanos)
        thresholdCrossed = false
        velocity.reset()
        velocity.add(timeNanos, point)
    }

    override fun move(point: Point, timeNanos: Long) {
        val total = Point(point.x - start.x, point.y - start.y)
        val eligible =
            when (axis) {
                DragAxis.FREE -> hypot(total.x.toDouble(), total.y.toDouble()).toFloat()
                DragAxis.HORIZONTAL -> abs(total.x)
                DragAxis.VERTICAL -> abs(total.y)
            }
        val competing =
            when (axis) {
                DragAxis.FREE -> 0f
                DragAxis.HORIZONTAL -> abs(total.y)
                DragAxis.VERTICAL -> abs(total.x)
            }
        if (!thresholdCrossed && eligible > config.touchSlop && eligible > competing) {
            thresholdCrossed = true
            accept()
        } else if (!thresholdCrossed && competing > config.touchSlop && competing > eligible) {
            rejected = true
        }
        velocity.add(timeNanos, point)
        val delta = Point(point.x - last.x, point.y - last.y)
        last = point
        if (thresholdCrossed && accepted && !rejected)
            onUpdate(DragUpdate(point, delta, total, velocity.velocity(config)))
    }

    override fun up(point: Point, timeNanos: Long) {
        move(point, timeNanos)
        if (thresholdCrossed && accepted && !rejected) onEnd(velocity.velocity(config))
    }
}

public class SelectionDragRecognizer(
    private val config: GestureConfiguration,
    private val onUpdate: (DragUpdate) -> Unit,
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    private val velocity = VelocityTracker()
    private var selecting = false

    override fun down(point: Point, timeNanos: Long) {
        super.down(point, timeNanos)
        selecting = false
        velocity.reset()
        velocity.add(timeNanos, point)
    }

    public fun advance(timeNanos: Long) {
        if (
            !selecting &&
                !rejected &&
                timeNanos - startTime >= config.longPressTimeoutMillis * 1_000_000
        ) {
            selecting = true
            accept()
        }
    }

    override fun move(point: Point, timeNanos: Long) {
        advance(timeNanos)
        val total = Point(point.x - start.x, point.y - start.y)
        if (!selecting && distance(start, point) > config.touchSlop) rejected = true
        velocity.add(timeNanos, point)
        val delta = Point(point.x - last.x, point.y - last.y)
        last = point
        if (selecting && accepted && !rejected) {
            onUpdate(DragUpdate(point, delta, total, velocity.velocity(config)))
        }
    }

    override fun up(point: Point, timeNanos: Long) {
        move(point, timeNanos)
    }
}

public class TextSelectionRecognizer(
    private val config: GestureConfiguration,
    private val onCaret: (Point) -> Unit,
    private val onWord: (Point) -> Unit,
    private val onSelectionStart: (Point) -> Unit,
    private val onSelectionUpdate: (Point) -> Unit,
    team: Any? = null,
) : SinglePointerRecognizer(team) {
    private var selecting = false
    private var previousUp = Long.MIN_VALUE
    private var previousPoint: Point? = null

    override fun down(point: Point, timeNanos: Long) {
        super.down(point, timeNanos)
        selecting = false
    }

    public fun advance(timeNanos: Long) {
        if (
            !selecting &&
                !rejected &&
                timeNanos - startTime >= config.longPressTimeoutMillis * 1_000_000
        ) {
            selecting = true
            accept()
            if (accepted) onSelectionStart(start)
        }
    }

    override fun move(point: Point, timeNanos: Long) {
        advance(timeNanos)
        if (!selecting && distance(start, point) > config.touchSlop) rejected = true
        last = point
        if (selecting && accepted && !rejected) onSelectionUpdate(point)
    }

    override fun up(point: Point, timeNanos: Long) {
        advance(timeNanos)
        if (selecting) {
            if (accepted && !rejected) onSelectionUpdate(point)
            return
        }
        if (rejected) return
        accept()
        if (!accepted) return
        val prior = previousPoint
        if (
            prior != null &&
                timeNanos - previousUp <= config.doubleTapTimeoutMillis * 1_000_000 &&
                distance(prior, point) <= config.touchSlop * 2
        ) {
            onWord(point)
            previousPoint = null
            previousUp = Long.MIN_VALUE
        } else {
            onCaret(point)
            previousPoint = point
            previousUp = timeNanos
        }
    }
}

public data class ScaleGestureUpdate(
    val centroid: Point,
    val scaleDelta: Float,
    val accumulatedScale: Float,
)

public class ScaleRecognizer(
    private val minimumSpan: Float,
    private val onUpdate: (ScaleGestureUpdate) -> Unit,
) {
    private val pointers = mutableMapOf<Int, Point>()
    private var priorSpan = 0f
    private var accumulated = 1f

    public fun update(pointer: Int, point: Point) {
        pointers[pointer] = point
        if (pointers.size < 2) return
        val pair = pointers.values.take(2)
        val span = distance(pair[0], pair[1])
        if (priorSpan == 0f) priorSpan = span
        if (span >= minimumSpan && priorSpan > 0f) {
            val delta = span / priorSpan
            accumulated *= delta
            onUpdate(
                ScaleGestureUpdate(
                    Point((pair[0].x + pair[1].x) / 2, (pair[0].y + pair[1].y) / 2),
                    delta,
                    accumulated,
                )
            )
        }
        priorSpan = span
    }

    public fun remove(pointer: Int) {
        pointers.remove(pointer)
        priorSpan = 0f
    }
}

public data class ScrollDelta(val x: Float, val y: Float) {
    public operator fun minus(other: ScrollDelta) = ScrollDelta(x - other.x, y - other.y)

    public operator fun plus(other: ScrollDelta) = ScrollDelta(x + other.x, y + other.y)

    public operator fun times(scale: Float) = ScrollDelta(x * scale, y * scale)
}

public enum class ScrollSource {
    WHEEL,
    TOUCH_DRAG,
    PEN_DRAG,
    KEYBOARD,
    ACCESSIBILITY,
    PROGRAMMATIC,
    FLING,
    SCROLLBAR,
}

public interface NestedScrollConnection {
    public fun preScroll(delta: ScrollDelta, source: ScrollSource) = ScrollDelta(0f, 0f)

    public fun postScroll(consumed: ScrollDelta, remaining: ScrollDelta, source: ScrollSource) =
        ScrollDelta(0f, 0f)

    public fun preFling(velocity: ScrollDelta) = ScrollDelta(0f, 0f)

    public fun postFling(consumed: ScrollDelta, remaining: ScrollDelta) = ScrollDelta(0f, 0f)
}

public interface FlingBehavior {
    public fun delta(velocity: ScrollDelta, elapsedSeconds: Float): ScrollDelta
}

public object ExponentialFling : FlingBehavior {
    override fun delta(velocity: ScrollDelta, elapsedSeconds: Float): ScrollDelta =
        velocity * (elapsedSeconds * kotlin.math.exp(-4f * elapsedSeconds))
}

public class ScrollState(initialX: Float = 0f, initialY: Float = 0f) {
    private val listeners = linkedSetOf<(ScrollState) -> Unit>()
    private var flingGeneration = 0L
    public var x = initialX
        private set

    public var y = initialY
        private set

    public var maxX = 0f
    public var maxY = 0f
    public var overscroll = ScrollDelta(0f, 0f)
        private set

    public var isScrolling = false
        private set

    public fun scroll(
        delta: ScrollDelta,
        source: ScrollSource,
        connection: NestedScrollConnection? = null,
    ): ScrollDelta {
        val pre = bounded(connection?.preScroll(delta, source) ?: ScrollDelta(0f, 0f), delta)
        val local = delta - pre
        val nx = (x + local.x).coerceIn(0f, maxX)
        val ny = (y + local.y).coerceIn(0f, maxY)
        val used = ScrollDelta(nx - x, ny - y)
        x = nx
        y = ny
        val remain = local - used
        overscroll = remain
        val post =
            bounded(connection?.postScroll(used, remain, source) ?: ScrollDelta(0f, 0f), remain)
        if (used != ScrollDelta(0f, 0f) || overscroll != ScrollDelta(0f, 0f)) {
            listeners.toList().forEach { it(this) }
        }
        return pre + used + post
    }

    public fun onChanged(listener: (ScrollState) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    public fun setRange(maxX: Float, maxY: Float) {
        require(maxX.isFinite() && maxX >= 0f && maxY.isFinite() && maxY >= 0f)
        this.maxX = maxX
        this.maxY = maxY
        val nextX = x.coerceAtMost(maxX)
        val nextY = y.coerceAtMost(maxY)
        if (nextX != x || nextY != y) {
            x = nextX
            y = nextY
            listeners.toList().forEach { it(this) }
        }
    }

    public fun stop() {
        flingGeneration++
        isScrolling = false
    }

    public fun fling(
        velocity: ScrollDelta,
        config: GestureConfiguration,
        clock: UiAnimationClock,
        connection: NestedScrollConnection? = null,
        behavior: FlingBehavior = ExponentialFling,
    ) {
        stop()
        val clamped =
            ScrollDelta(
                velocity.x.coerceIn(-config.maximumFlingVelocity, config.maximumFlingVelocity),
                velocity.y.coerceIn(-config.maximumFlingVelocity, config.maximumFlingVelocity),
            )
        if (hypot(clamped.x.toDouble(), clamped.y.toDouble()) < config.minimumFlingVelocity) return
        val pre = bounded(connection?.preFling(clamped) ?: ScrollDelta(0f, 0f), clamped)
        val available = clamped - pre
        val local =
            ScrollDelta(
                available.x.takeIf { maxX > 0f && (it > 0f && x < maxX || it < 0f && x > 0f) }
                    ?: 0f,
                available.y.takeIf { maxY > 0f && (it > 0f && y < maxY || it < 0f && y > 0f) }
                    ?: 0f,
            )
        val unsupported = available - local
        if (unsupported != ScrollDelta(0f, 0f)) {
            connection?.postFling(ScrollDelta(0f, 0f), unsupported)
        }
        if (local == ScrollDelta(0f, 0f)) return
        if (clock.motionScale == 0f) {
            scroll(
                ScrollDelta(
                    if (local.x > 0f) maxX - x else if (local.x < 0f) -x else 0f,
                    if (local.y > 0f) maxY - y else if (local.y < 0f) -y else 0f,
                ),
                ScrollSource.FLING,
                connection,
            )
            return
        }
        val generation = ++flingGeneration
        var prior = 0L
        var current = local
        isScrolling = true
        clock.animate { time ->
            if (generation != flingGeneration) {
                false
            } else if (prior == 0L) {
                prior = time
                true
            } else {
                val elapsed = (time - prior) / 1_000_000_000f / clock.motionScale
                prior = time
                val requested = behavior.delta(current, elapsed)
                val consumed = scroll(requested, ScrollSource.FLING, connection)
                val decay = kotlin.math.exp(-4f * elapsed)
                current *= decay
                val active =
                    consumed != ScrollDelta(0f, 0f) &&
                        hypot(current.x.toDouble(), current.y.toDouble()) >=
                            config.minimumFlingVelocity
                if (!active) {
                    isScrolling = false
                    connection?.postFling(
                        local - current,
                        current.takeIf { consumed == ScrollDelta(0f, 0f) } ?: ScrollDelta(0f, 0f),
                    )
                }
                active
            }
        }
    }

    public val scrollbarXFraction
        get() = if (maxX == 0f) 0f else x / maxX

    public val scrollbarYFraction
        get() = if (maxY == 0f) 0f else y / maxY

    private fun bounded(value: ScrollDelta, available: ScrollDelta) =
        ScrollDelta(
            value.x.coerceIn(minOf(0f, available.x), maxOf(0f, available.x)),
            value.y.coerceIn(minOf(0f, available.y), maxOf(0f, available.y)),
        )
}

public enum class ScrollAxis {
    HORIZONTAL,
    VERTICAL,
}

public class ScrollbarController(
    private val state: ScrollState,
    public val axis: ScrollAxis,
    public val minimumThumbFraction: Float = 0.05f,
) {
    public var viewportExtent: Float = 0f
        private set

    public var contentExtent: Float = 0f
        private set

    public val thumbFraction: Float
        get() =
            if (contentExtent <= 0f) 1f
            else (viewportExtent / contentExtent).coerceIn(minimumThumbFraction, 1f)

    public val offsetFraction: Float
        get() {
            val range = if (axis == ScrollAxis.HORIZONTAL) state.maxX else state.maxY
            val offset = if (axis == ScrollAxis.HORIZONTAL) state.x else state.y
            return if (range == 0f) 0f else offset / range
        }

    public fun updateExtents(viewport: Float, content: Float) {
        require(viewport.isFinite() && viewport >= 0f && content.isFinite() && content >= 0f)
        viewportExtent = viewport
        contentExtent = content
    }

    public fun dragThumb(delta: Float, trackExtent: Float): Float {
        val movableTrack = trackExtent * (1f - thumbFraction)
        if (movableTrack <= 0f) return 0f
        val range = if (axis == ScrollAxis.HORIZONTAL) state.maxX else state.maxY
        return scroll(delta / movableTrack * range)
    }

    public fun clickTrack(position: Float, trackExtent: Float): Float {
        if (trackExtent <= 0f) return 0f
        val thumbStart = offsetFraction * trackExtent * (1f - thumbFraction)
        val page = viewportExtent * if (position < thumbStart) -1f else 1f
        return scroll(page)
    }

    private fun scroll(delta: Float): Float {
        val consumed =
            if (axis == ScrollAxis.HORIZONTAL) {
                state.scroll(ScrollDelta(delta, 0f), ScrollSource.SCROLLBAR).x
            } else {
                state.scroll(ScrollDelta(0f, delta), ScrollSource.SCROLLBAR).y
            }
        return consumed
    }
}

public class VelocityTracker {
    private val samples = ArrayDeque<Pair<Long, Point>>()

    public fun reset() = samples.clear()

    public fun add(time: Long, point: Point) {
        samples += time to point
        while (samples.size > 8) samples.removeFirst()
    }

    public fun velocity(config: GestureConfiguration? = null): ScrollDelta {
        if (samples.size < 2) return ScrollDelta(0f, 0f)
        val a = samples.first()
        val b = samples.last()
        val scale = 1_000_000_000f / (b.first - a.first).coerceAtLeast(1)
        val max = config?.maximumFlingVelocity ?: Float.MAX_VALUE
        return ScrollDelta(
            ((b.second.x - a.second.x) * scale).coerceIn(-max, max),
            ((b.second.y - a.second.y) * scale).coerceIn(-max, max),
        )
    }
}

private fun distance(a: Point, b: Point) =
    hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()
