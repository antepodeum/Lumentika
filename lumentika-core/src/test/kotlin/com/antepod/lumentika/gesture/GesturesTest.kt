package com.antepod.lumentika.gesture

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.platform.GestureConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GesturesTest {
    private val config = GestureConfiguration(touchSlop = 5f, doubleTapTimeoutMillis = 250, longPressTimeoutMillis = 400, minimumFlingVelocity = 10f, maximumFlingVelocity = 1000f)

    @Test fun `double tap long press drag and scale use configured thresholds`() {
        var doubles = 0
        val double = DoubleTapRecognizer(config, onDoubleTap = { doubles++ })
        double.down(Point(0f,0f),0); double.up(Point(0f,0f),10)
        double.down(Point(1f,1f),100_000_000); double.up(Point(1f,1f),110_000_000)
        assertEquals(1,doubles)
        var long = 0
        val press = LongPressRecognizer(config, onLongPress = { long++ })
        press.down(Point(0f,0f),0); press.advance(400_000_000)
        assertEquals(1,long)
        val drags = mutableListOf<DragUpdate>()
        val drag = DragRecognizer(config,DragAxis.HORIZONTAL,drags::add)
        drag.down(Point(0f,0f),0); drag.move(Point(10f,1f),10_000_000)
        assertTrue(drags.isNotEmpty())
        var scale = 1f
        val recognizer = ScaleRecognizer(5f) { scale = it.accumulatedScale }
        recognizer.update(1,Point(0f,0f));recognizer.update(2,Point(10f,0f));recognizer.update(2,Point(20f,0f))
        assertEquals(2f,scale)
    }

    @Test fun `arena team arbitration accepts team and rejects competitor`() {
        val trace = mutableListOf<String>(); val team = Any()
        fun recognizer(name:String,member:Any?)=object:GestureRecognizer{override val team=member;override fun down(point:Point,timeNanos:Long){};override fun move(point:Point,timeNanos:Long){};override fun up(point:Point,timeNanos:Long){};override fun cancel(){trace+="$name:cancel"};override fun resolve(disposition:GestureDisposition){trace+="$name:$disposition"};override fun close(){}}
        val first=recognizer("first",team);val teammate=recognizer("team",team);val other=recognizer("other",null);val arena=GestureArena();arena.add(1,first);arena.add(1,teammate);arena.add(1,other);arena.resolve(1,first)
        assertEquals(listOf("first:ACCEPTED","team:ACCEPTED","other:REJECTED"),trace)
    }

    @Test fun `nested scroll conserves deltas and fling shares root clock`() {
        val trace=mutableListOf<String>()
        val connection=object:NestedScrollConnection{override fun preScroll(delta:ScrollDelta,source:ScrollSource)=ScrollDelta(0f,delta.y/4).also{trace+="pre:$it"};override fun postScroll(consumed:ScrollDelta,remaining:ScrollDelta,source:ScrollSource)=ScrollDelta(0f,remaining.y).also{trace+="post:$it"};override fun preFling(velocity:ScrollDelta)=ScrollDelta(0f,velocity.y/2).also{trace+="preFling"};override fun postFling(consumed:ScrollDelta,remaining:ScrollDelta)=ScrollDelta(0f,0f).also{trace+="postFling"}}
        val state=ScrollState();state.maxY=50f
        val consumed=state.scroll(ScrollDelta(0f,80f),ScrollSource.WHEEL,connection)
        assertEquals(80f,consumed.y)
        val clock=UiAnimationClock();state.fling(ScrollDelta(0f,-100f),config,clock,connection);clock.frame(1_000_000_000);clock.frame(1_016_000_000)
        assertTrue("preFling" in trace);assertTrue("postFling" in trace)
    }
}
