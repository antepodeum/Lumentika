package com.antepod.lumentika.gesture

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.platform.GestureConfiguration
import kotlin.math.abs
import kotlin.math.hypot

public enum class GestureDisposition { PENDING, ACCEPTED, REJECTED }
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
    public fun add(pointer: Int, recognizer: GestureRecognizer) { entries.getOrPut(pointer, ::mutableListOf) += recognizer }
    public fun resolve(pointer: Int, winner: GestureRecognizer) {
        entries.remove(pointer)?.forEach { it.resolve(if (it === winner || it.team != null && it.team === winner.team) GestureDisposition.ACCEPTED else GestureDisposition.REJECTED) }
    }
    public fun cancel(pointer: Int) { entries.remove(pointer)?.forEach(GestureRecognizer::cancel) }
}

public abstract class SinglePointerRecognizer(final override val team: Any?) : GestureRecognizer {
    protected var start = Point(0f, 0f); protected var last = start; protected var startTime = 0L; protected var rejected = false
    override fun down(point: Point, timeNanos: Long) { start = point; last = point; startTime = timeNanos; rejected = false }
    override fun cancel() { rejected = true }
    override fun resolve(disposition: GestureDisposition) { if (disposition == GestureDisposition.REJECTED) cancel() }
    override fun close() = cancel()
}

public class TapRecognizer(private val config: GestureConfiguration, private val onTap: () -> Unit, team: Any? = null) : SinglePointerRecognizer(team) {
    override fun move(point: Point, timeNanos: Long) { if (distance(start, point) > config.touchSlop) rejected = true; last = point }
    override fun up(point: Point, timeNanos: Long) { if (!rejected) onTap() }
}

public class DoubleTapRecognizer(private val config: GestureConfiguration, private val onDoubleTap: () -> Unit, team: Any? = null) : SinglePointerRecognizer(team) {
    private var previousUp = Long.MIN_VALUE; private var previousPoint: Point? = null
    override fun move(point: Point, timeNanos: Long) { if (distance(start, point) > config.touchSlop) rejected = true }
    override fun up(point: Point, timeNanos: Long) {
        val previous = previousPoint
        if (!rejected && previous != null && timeNanos - previousUp <= config.doubleTapTimeoutMillis * 1_000_000 && distance(previous, point) <= config.touchSlop * 2) { onDoubleTap(); previousPoint = null }
        else { previousUp = timeNanos; previousPoint = point }
    }
}

public class LongPressRecognizer(private val config: GestureConfiguration, private val onLongPress: () -> Unit, team: Any? = null) : SinglePointerRecognizer(team) {
    private var fired = false
    override fun down(point: Point, timeNanos: Long) { super.down(point, timeNanos); fired = false }
    override fun move(point: Point, timeNanos: Long) { if (distance(start, point) > config.touchSlop) rejected = true; advance(timeNanos) }
    public fun advance(timeNanos: Long) { if (!rejected && !fired && timeNanos - startTime >= config.longPressTimeoutMillis * 1_000_000) { fired = true; onLongPress() } }
    override fun up(point: Point, timeNanos: Long) { advance(timeNanos) }
}

public enum class DragAxis { FREE, HORIZONTAL, VERTICAL }
public data class DragUpdate(val position: Point, val delta: Point, val total: Point, val velocity: ScrollDelta)
public class DragRecognizer(private val config: GestureConfiguration, private val axis: DragAxis = DragAxis.FREE, private val onUpdate: (DragUpdate) -> Unit, team: Any? = null) : SinglePointerRecognizer(team) {
    private val velocity = VelocityTracker(); private var accepted = false
    override fun down(point: Point, timeNanos: Long) { super.down(point, timeNanos); accepted = false; velocity.reset(); velocity.add(timeNanos, point) }
    override fun move(point: Point, timeNanos: Long) {
        val total = Point(point.x - start.x, point.y - start.y); val eligible = when(axis) { DragAxis.FREE -> hypot(total.x.toDouble(), total.y.toDouble()).toFloat(); DragAxis.HORIZONTAL -> abs(total.x); DragAxis.VERTICAL -> abs(total.y) }
        if (eligible > config.touchSlop) accepted = true
        velocity.add(timeNanos, point); val delta = Point(point.x - last.x, point.y - last.y); last = point
        if (accepted && !rejected) onUpdate(DragUpdate(point, delta, total, velocity.velocity(config)))
    }
    override fun up(point: Point, timeNanos: Long) { move(point, timeNanos) }
}

public data class ScaleGestureUpdate(val centroid: Point, val scaleDelta: Float, val accumulatedScale: Float)
public class ScaleRecognizer(private val minimumSpan: Float, private val onUpdate: (ScaleGestureUpdate) -> Unit) {
    private val pointers = mutableMapOf<Int, Point>(); private var priorSpan = 0f; private var accumulated = 1f
    public fun update(pointer: Int, point: Point) { pointers[pointer] = point; if (pointers.size < 2) return; val pair = pointers.values.take(2); val span = distance(pair[0], pair[1]); if (priorSpan == 0f) priorSpan = span; if (span >= minimumSpan && priorSpan > 0f) { val delta = span / priorSpan; accumulated *= delta; onUpdate(ScaleGestureUpdate(Point((pair[0].x + pair[1].x)/2, (pair[0].y + pair[1].y)/2), delta, accumulated)) }; priorSpan = span }
    public fun remove(pointer: Int) { pointers.remove(pointer); priorSpan = 0f }
}

public data class ScrollDelta(val x: Float, val y: Float) { public operator fun minus(other: ScrollDelta) = ScrollDelta(x-other.x,y-other.y); public operator fun plus(other: ScrollDelta) = ScrollDelta(x+other.x,y+other.y); public operator fun times(scale: Float)=ScrollDelta(x*scale,y*scale) }
public enum class ScrollSource { WHEEL, TOUCH_DRAG, PEN_DRAG, KEYBOARD, ACCESSIBILITY, PROGRAMMATIC, FLING, SCROLLBAR }
public interface NestedScrollConnection { public fun preScroll(delta: ScrollDelta, source: ScrollSource)=ScrollDelta(0f,0f); public fun postScroll(consumed: ScrollDelta, remaining: ScrollDelta, source: ScrollSource)=ScrollDelta(0f,0f); public fun preFling(velocity: ScrollDelta)=ScrollDelta(0f,0f); public fun postFling(consumed: ScrollDelta, remaining: ScrollDelta)=ScrollDelta(0f,0f) }
public interface FlingBehavior { public fun delta(velocity: ScrollDelta, elapsedSeconds: Float): ScrollDelta }
public object ExponentialFling : FlingBehavior { override fun delta(velocity: ScrollDelta, elapsedSeconds: Float): ScrollDelta = velocity * (elapsedSeconds * kotlin.math.exp(-4f * elapsedSeconds)) }

public class ScrollState(initialX: Float=0f, initialY: Float=0f) {
    public var x=initialX; private set; public var y=initialY; private set; public var maxX=0f; public var maxY=0f; public var overscroll=ScrollDelta(0f,0f); private set; public var isScrolling=false; private set
    public fun scroll(delta: ScrollDelta, source: ScrollSource, connection: NestedScrollConnection?=null): ScrollDelta { val pre=bounded(connection?.preScroll(delta,source)?:ScrollDelta(0f,0f),delta); val local=delta-pre; val nx=(x+local.x).coerceIn(0f,maxX); val ny=(y+local.y).coerceIn(0f,maxY); val used=ScrollDelta(nx-x,ny-y); x=nx;y=ny; val remain=local-used;overscroll=remain;val post=bounded(connection?.postScroll(used,remain,source)?:ScrollDelta(0f,0f),remain);return pre+used+post }
    public fun fling(velocity: ScrollDelta, config: GestureConfiguration, clock: UiAnimationClock, connection: NestedScrollConnection?=null, behavior: FlingBehavior=ExponentialFling) { val clamped=ScrollDelta(velocity.x.coerceIn(-config.maximumFlingVelocity,config.maximumFlingVelocity),velocity.y.coerceIn(-config.maximumFlingVelocity,config.maximumFlingVelocity));if(hypot(clamped.x.toDouble(),clamped.y.toDouble())<config.minimumFlingVelocity)return;val pre=bounded(connection?.preFling(clamped)?:ScrollDelta(0f,0f),clamped);val local=clamped-pre;var prior=0L;isScrolling=true;clock.animate{time->if(prior==0L){prior=time;true}else{val elapsed=(time-prior)/1_000_000_000f;prior=time;val consumed=scroll(behavior.delta(local,elapsed),ScrollSource.FLING);val remaining=local-consumed;connection?.postFling(consumed,remaining);val active=abs(local.x)+abs(local.y)>1f&&overscroll==ScrollDelta(0f,0f);if(!active)isScrolling=false;active}} }
    public val scrollbarXFraction get()=if(maxX==0f)0f else x/maxX; public val scrollbarYFraction get()=if(maxY==0f)0f else y/maxY
    private fun bounded(value:ScrollDelta,available:ScrollDelta)=ScrollDelta(value.x.coerceIn(minOf(0f,available.x),maxOf(0f,available.x)),value.y.coerceIn(minOf(0f,available.y),maxOf(0f,available.y)))
}

public class VelocityTracker { private val samples=ArrayDeque<Pair<Long,Point>>(); public fun reset()=samples.clear(); public fun add(time:Long,point:Point){samples+=time to point;while(samples.size>8)samples.removeFirst()};public fun velocity(config:GestureConfiguration?=null):ScrollDelta{if(samples.size<2)return ScrollDelta(0f,0f);val a=samples.first();val b=samples.last();val scale=1_000_000_000f/(b.first-a.first).coerceAtLeast(1);val max=config?.maximumFlingVelocity?:Float.MAX_VALUE;return ScrollDelta(((b.second.x-a.second.x)*scale).coerceIn(-max,max),((b.second.y-a.second.y)*scale).coerceIn(-max,max))} }
private fun distance(a:Point,b:Point)=hypot((a.x-b.x).toDouble(),(a.y-b.y).toDouble()).toFloat()
