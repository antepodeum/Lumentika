package com.antepod.lumentika.render

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.HitRegionSource
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.runtime.PaintRecorder
import com.antepod.lumentika.runtime.SceneContent
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.rgb
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RenderTest {
    @Test
    fun `resolved background and inherited text paint are replayable commands`() {
        val root = Element("root")
        val text =
            Element("text").also {
                it.content = TextContent("styled")
                it.geometry = Rect(0f, 0f, 60f, 20f)
                root.append(it)
            }
        root.geometry = Rect(0f, 0f, 100f, 100f)
        val styles = StyleRuntime()
        val background = rgb(10, 20, 30)
        val foreground = rgb(200, 210, 220)
        styles.attach(
            text,
            state(
                style {
                    this.background = background
                    color = foreground
                }
            ),
        )
        val runtime = RenderRuntime(root) { styles.resolve(it).first }

        val commands = runtime.commit().paint.chunks.single().commands

        assertEquals(background, (commands[0] as PaintCommand.Fill).paint)
        assertEquals(foreground, (commands[1] as PaintCommand.DrawText).paint)
        root.close()
    }

    @Test
    fun `paint hit and transform share committed property chain`() {
        val root = Element("root").apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val child =
            Element("child").apply {
                geometry = Rect(10f, 10f, 20f, 20f)
                content = TextContent("x")
            }
        root.append(child)
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        styles.attach(child, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configure(child, RenderProperties(transform = Matrix3.scale(2f)))
        val commit = render.commit()
        assertSame(child, commit.hitTest.hitTest(Point(30f, 30f)))
        assertEquals(Point(10f, 10f), render.rootToLocal(child, Point(30f, 30f)))
        assertEquals(commit.paint.generation, commit.hitTest.generation)
    }

    @Test
    fun `custom scene hit region overrides rectangular hit`() {
        val root =
            Element("root").apply {
                geometry = Rect(0f, 0f, 20f, 20f)
                content =
                    object : com.antepod.lumentika.runtime.Content, HitRegionSource {
                        override fun record(
                            recorder: com.antepod.lumentika.runtime.PaintRecorder,
                            bounds: Rect,
                        ) {}

                        override fun hitTest(localPoint: Point, bounds: Rect) = localPoint.x < 5f
                    }
            }
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val hit = RenderRuntime(root) { styles.resolve(it).first }.commit().hitTest
        assertSame(root, hit.hitTest(Point(2f, 10f)))
        assertEquals(null, hit.hitTest(Point(10f, 10f)))
    }

    @Test
    fun `custom scene raycast receives transformed local coordinates`() {
        val sceneObject = Any()
        var raycastPoint: Point? = null
        val root =
            Element("scene").apply {
                geometry = Rect(10f, 5f, 20f, 20f)
                content =
                    object : SceneContent {
                        override fun record(recorder: PaintRecorder, bounds: Rect) = Unit

                        override fun hitTest(localPoint: Point, bounds: Rect) =
                            bounds.contains(localPoint)

                        override fun raycast(localPoint: Point): Any {
                            raycastPoint = localPoint
                            return sceneObject
                        }
                    }
            }
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val artifact = RenderRuntime(root) { styles.resolve(it).first }.commit().hitTest

        val hit = artifact.raycast(Point(15f, 9f))

        assertSame(root, hit?.element)
        assertSame(sceneObject, hit?.sceneObject)
        assertEquals(Point(5f, 4f), raycastPoint)
    }

    @Test
    fun `property update reuses retained paint record`() {
        val root =
            Element("root").apply {
                geometry = Rect(0f, 0f, 10f, 10f)
                content = TextContent("x")
            }
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.commit()
        render.configure(root, RenderProperties(transform = Matrix3.translation(2f, 0f)))
        render.commit()
        assertEquals(1, render.recordCount)
    }

    @Test
    fun `paint property and order invalidations remain independent`() {
        var records = 0
        val root =
            Element("root").apply {
                geometry = Rect(0f, 0f, 10f, 10f)
                content =
                    object : com.antepod.lumentika.runtime.Content {
                        override fun record(recorder: PaintRecorder, bounds: Rect) {
                            records++
                            recorder.record(PaintCommand.FillRect(bounds, 0xff000000.toInt()))
                        }
                    }
            }
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.commit()

        render.configure(root, RenderProperties(transform = Matrix3.translation(1f, 0f)))
        render.commit()
        assertEquals(1, records)
        assertEquals(1, render.propertyInvalidationCount)

        render.configure(
            root,
            RenderProperties(transform = Matrix3.translation(1f, 0f), topLayer = true),
        )
        render.commit()
        assertEquals(1, records)
        assertEquals(1, render.orderInvalidationCount)

        render.invalidate(root, RenderInvalidation.PAINT)
        render.commit()
        assertEquals(2, records)
        assertEquals(1, render.paintInvalidationCount)
        assertTrue(render.committed.paint.chunks.single().topLayer)
    }
}
