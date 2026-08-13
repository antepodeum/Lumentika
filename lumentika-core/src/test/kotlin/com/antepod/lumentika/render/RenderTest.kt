package com.antepod.lumentika.render

import com.antepod.lumentika.geometry.CornerRadii
import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Path
import com.antepod.lumentika.geometry.PathSegment
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.RoundedRect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.HitRegionSource
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.runtime.PaintRecorder
import com.antepod.lumentika.runtime.SceneContent
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.style.BoxShadow
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.edges
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.rgb
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RenderTest {
    @Test
    fun `rounded nested and transformed path clips share exact hit geometry`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val rounded =
            Element().also {
                it.geometry = Rect(0f, 0f, 20f, 20f)
                it.content = TextContent("rounded")
                root.append(it)
            }
        val nested =
            Element().also {
                it.geometry = Rect(0f, 0f, 20f, 20f)
                it.content = TextContent("nested")
                rounded.append(it)
            }
        val transformed =
            Element().also {
                it.geometry = Rect(0f, 30f, 20f, 20f)
                it.content = TextContent("path")
                root.append(it)
            }
        val styles = StyleRuntime()
        listOf(root, rounded, nested, transformed).forEach { styles.attach(it, state(style {})) }
        val triangle =
            Path(
                listOf(
                    PathSegment.MoveTo(Point(0f, 0f)),
                    PathSegment.LineTo(Point(20f, 0f)),
                    PathSegment.LineTo(Point(0f, 20f)),
                    PathSegment.Close,
                )
            )
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configure(
            rounded,
            RenderProperties(clip = RoundedRect(Rect(0f, 0f, 20f, 20f), CornerRadii(10f))),
        )
        render.configure(nested, RenderProperties(clip = Rect(0f, 0f, 8f, 20f)))
        render.configure(
            transformed,
            RenderProperties(
                transform = Matrix3.translation(10f, 0f),
                clip = triangle,
            ),
        )
        val hit = render.commit().hitTest

        assertSame(root, hit.hitTest(Point(1f, 1f)))
        assertSame(nested, hit.hitTest(Point(5f, 10f)))
        assertSame(rounded, hit.hitTest(Point(10f, 10f)))
        assertSame(transformed, hit.hitTest(Point(12f, 32f)))
        assertSame(root, hit.hitTest(Point(28f, 48f)))
        assertTrue(
            hit.entries.single { it.element === transformed }.clips.any { it.shape === triangle }
        )
        root.close()
    }

    @Test
    fun `radius border shadow and path emit generic paint commands`() {
        val path =
            Path(
                listOf(
                    PathSegment.MoveTo(Point(0f, 0f)),
                    PathSegment.LineTo(Point(10f, 0f)),
                    PathSegment.LineTo(Point(10f, 10f)),
                    PathSegment.Close,
                )
            )
        val fill = rgb(1, 2, 3)
        val border = rgb(4, 5, 6)
        val shadow = BoxShadow(Point(2f, 3f), 4f, 1f, rgb(7, 8, 9))
        val root =
            Element().apply {
                geometry = Rect(0f, 0f, 40f, 30f)
                content =
                    object : com.antepod.lumentika.runtime.Content {
                        override fun record(recorder: PaintRecorder, bounds: Rect) {
                            recorder.record(PaintCommand.FillPath(path, fill))
                            recorder.record(PaintCommand.StrokePath(path, 2f, border))
                        }
                    }
            }
        val styles = StyleRuntime()
        styles.attach(
            root,
            state(
                style {
                    background = fill
                    borderRadius = CornerRadii(6f)
                    this.border = edges(2.px)
                    borderPaint = border
                    boxShadows = listOf(shadow)
                }
            ),
        )
        val commands =
            RenderRuntime(root) { styles.resolve(it).first }.commit().paint.chunks.single().commands

        assertTrue(commands[0] is PaintCommand.DrawBoxShadow)
        assertTrue(commands[1] is PaintCommand.FillRoundedRect)
        assertTrue(commands.any { it is PaintCommand.FillPath && it.path === path })
        assertTrue(commands.any { it is PaintCommand.StrokePath && it.path === path })
        assertEquals(
            edges(2f),
            commands.filterIsInstance<PaintCommand.DrawBorder>().single().widths,
        )
        root.close()
    }

    @Test
    fun `resolved background and inherited text paint are replayable commands`() {
        val root = Element()
        val text =
            Element().also {
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
        val root = Element().apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val child =
            Element().apply {
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
    fun `motion properties compose with retained render properties`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val child =
            Element().apply {
                geometry = Rect(10f, 10f, 20f, 20f)
                content = TextContent("x")
            }
        root.append(child)
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        styles.attach(child, state(style { opacity = .8f }))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configure(child, RenderProperties(transform = Matrix3.translation(2f, 0f)))
        render.configureMotion(
            child,
            MotionRenderProperties(
                transform = Matrix3.translation(3f, 0f),
                opacity = .5f,
            ),
        )

        val commit = render.commit()
        val entry = commit.hitTest.entries.single { it.element === child }
        val effect =
            commit.paint.trees.effects.single {
                it.id == commit.paint.chunks.single().properties.effect
            }

        assertEquals(Point(15f, 10f), entry.rootTransform.transform(Point(0f, 0f)))
        assertEquals(.4f, effect.opacity)
    }

    @Test
    fun `motion clip blur and draw reach immutable property trees`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 100f, 100f) }
        val child =
            Element().apply {
                geometry = Rect(10f, 10f, 20f, 20f)
                content = TextContent("x")
            }
        root.append(child)
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        styles.attach(child, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configureMotion(
            child,
            MotionRenderProperties(
                clip = Rect(0f, 0f, 8f, 20f),
                blurRadius = 3f,
                drawLength = 50f,
                drawProgress = .4f,
            ),
        )

        val commit = render.commit()
        val chunk = commit.paint.chunks.single()
        val effect = commit.paint.trees.effects.single { it.id == chunk.properties.effect }

        assertEquals(3f, effect.blurRadius)
        assertEquals(50f, effect.drawLength)
        assertEquals(.4f, effect.drawProgress)
        assertSame(child, commit.hitTest.hitTest(Point(15f, 15f)))
        assertSame(root, commit.hitTest.hitTest(Point(19f, 15f)))
        root.close()
    }

    @Test
    fun `custom scene hit region overrides rectangular hit`() {
        val root =
            Element().apply {
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
            Element().apply {
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
            Element().apply {
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
            Element().apply {
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

    @Test
    fun `top layer escapes ordinary ancestor clipping for paint and hit testing`() {
        val root =
            Element().apply {
                geometry = Rect(0f, 0f, 10f, 10f)
            }
        val popup =
            Element().also {
                it.geometry = Rect(20f, 0f, 10f, 10f)
                it.content = TextContent("popup")
                root.append(it)
            }
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val render = RenderRuntime(root) { styles.resolve(it).first }
        render.configure(root, RenderProperties(clip = Rect(0f, 0f, 10f, 10f)))
        render.configure(popup, RenderProperties(topLayer = true))

        render.commit()

        assertSame(popup, render.committed.hitTest.hitTest(Point(25f, 5f)))
        assertTrue(render.committed.paint.chunks.single().topLayer)
    }
}
