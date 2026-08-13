package com.antepod.lumentika

import com.antepod.lumentika.components.button
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.input.EventType
import com.antepod.lumentika.input.PointerType
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformInputTest {
    @Test
    fun `committed hit test routes normalized pointer input into controls`() {
        var clicks = 0
        val root = headlessRoot(100f, 100f)
        val button = root.scope.button("Go") { clicks++ }
        root.styles.attach(
            button.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 40.px
                }
            ),
        )
        root.requestFrame()
        root.frame(1)

        assertSame(button.element, root.hitTest(Point(10f, 10f)))
        assertTrue(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.DOWN, 1, PointerType.MOUSE, Point(10f, 10f), 2)
            )
        )
        assertTrue(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.UP, 1, PointerType.MOUSE, Point(10f, 10f), 3)
            )
        )
        assertEquals(1, clicks)
        root.close()
    }

    @Test
    fun `backend can inspect same committed paint generation exposed to platform`() {
        val scheduler = HeadlessFrameScheduler()
        var generation = -1L
        val root =
            UiRoot(
                UiEnvironment(Size(20f, 20f)),
                PlatformServices(scheduler),
                object : RenderBackend {
                    override fun replay(artifact: com.antepod.lumentika.render.PaintArtifact) {
                        generation = artifact.generation
                    }
                },
            )
        root.requestFrame()
        root.frame(9)
        assertEquals(generation, root.committedRender.paint.generation)
        assertEquals(generation, root.committedRender.hitTest.generation)
        assertFalse(root.dispatchWheel(Point(30f, 30f), 0f, 1f, 10))
        root.close()
    }

    @Test
    fun `prevented platform event does not activate gesture default`() {
        var clicks = 0
        val root = headlessRoot(100f, 100f)
        val button = root.scope.button("Go") { clicks++ }
        root.styles.attach(
            button.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 40.px
                }
            ),
        )
        root.events.on(button.element, EventType.POINTER_DOWN) { it.preventDefault() }
        root.requestFrame()
        root.frame(1)
        assertFalse(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.DOWN, 1, PointerType.TOUCH, Point(5f, 5f), 2)
            )
        )
        root.dispatchPointer(
            PointerInput(PointerInputPhase.UP, 1, PointerType.TOUCH, Point(5f, 5f), 3)
        )
        assertEquals(0, clicks)
        root.close()
    }
}
