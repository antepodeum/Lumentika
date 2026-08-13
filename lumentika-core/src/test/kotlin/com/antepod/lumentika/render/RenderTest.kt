package com.antepod.lumentika.render

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import com.antepod.lumentika.runtime.HitRegionSource

class RenderTest {
    @Test
    fun `paint hit and transform share committed property chain`() {
        val root = Element("root").apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val child = Element("child").apply { geometry = Rect(10f, 10f, 20f, 20f); content = TextContent("x") }
        root.append(child)
        val styles = StyleRuntime()
        styles.attach(root, state(style {})); styles.attach(child, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configure(child, RenderProperties(transform = Matrix3.scale(2f)))
        val commit = render.commit()
        assertSame(child, commit.hitTest.hitTest(Point(30f, 30f)))
        assertEquals(Point(10f, 10f), render.rootToLocal(child, Point(30f, 30f)))
        assertEquals(commit.paint.generation, commit.hitTest.generation)
    }

    @Test fun `custom scene hit region overrides rectangular hit`() {
        val root=Element("root").apply{geometry=Rect(0f,0f,20f,20f);content=object:com.antepod.lumentika.runtime.Content,HitRegionSource{override fun record(recorder:com.antepod.lumentika.runtime.PaintRecorder,bounds:Rect){};override fun hitTest(localPoint:Point,bounds:Rect)=localPoint.x<5f}}
        val styles=StyleRuntime();styles.attach(root,state(style{}));val hit=RenderRuntime(root){styles.resolve(it).first}.commit().hitTest
        assertSame(root,hit.hitTest(Point(2f,10f)));assertEquals(null,hit.hitTest(Point(10f,10f)))
    }

    @Test
    fun `property update reuses retained paint record`() {
        val root = Element("root").apply { geometry = Rect(0f, 0f, 10f, 10f); content = TextContent("x") }
        val styles = StyleRuntime(); styles.attach(root, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.commit()
        render.configure(root, RenderProperties(transform = Matrix3.translation(2f, 0f)))
        render.commit()
        assertEquals(1, render.recordCount)
    }
}
