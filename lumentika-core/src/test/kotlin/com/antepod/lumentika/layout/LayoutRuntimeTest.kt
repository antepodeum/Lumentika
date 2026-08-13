package com.antepod.lumentika.layout

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.LogicalUnitResolver
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.IntrinsicMeasureInput
import com.antepod.lumentika.runtime.MeasureSpace
import com.antepod.lumentika.runtime.PaintRecorder
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.dp
import com.antepod.lumentika.style.edges
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutRuntimeTest {
    @Test
    fun `padding margin and gap are projected into real Taffy geometry`() {
        val root = Element("root")
        val first = Element("first").also(root::append)
        val second = Element("second").also(root::append)
        val styles = StyleRuntime()
        styles.attach(
            root,
            state(
                style {
                    display = com.antepod.lumentika.style.Display.FLEX
                    width = 100.px
                    height = 100.px
                    flexDirection = com.antepod.lumentika.style.FlexDirection.COLUMN
                    padding = edges(10.px)
                    gap = 5.px
                }
            ),
        )
        styles.attach(
            first,
            state(
                style {
                    height = 10.px
                    margin = edges(2.px)
                }
            ),
        )
        styles.attach(second, state(style { height = 10.px }))
        val runtime = LayoutRuntime(root, LogicalUnitResolver, { styles.resolve(it).first })

        runtime.frame(1, UiEnvironment(Size(100f, 100f)))

        assertEquals(12f, first.geometry.x)
        assertEquals(12f, first.geometry.y)
        assertEquals(29f, second.geometry.y)
        runtime.close()
        root.close()
    }

    @Test
    fun `real Taffy projects stable tree and computes at most once per frame`() {
        val root = Element("root")
        val scope = UiScope(root)
        scope.fragment {
            element("first")
            element("text", TextContent("hello"))
        }
        val styles = StyleRuntime()
        styles.attach(
            root,
            state(
                style {
                    width = 100.dp
                    height = 100.dp
                }
            ),
        )
        val first = root.children.single().children.first()
        styles.attach(
            first,
            state(
                style {
                    width = 40.dp
                    height = 20.dp
                }
            ),
        )
        val runtime =
            LayoutRuntime(root, LogicalUnitResolver, { styles.resolve(it).first }, rounding = false)
        val environment = UiEnvironment(Size(100f, 100f))

        val initial = runtime.frame(1, environment)
        runtime.requestLayout()
        val sameFrame = runtime.frame(1, environment)
        assertEquals(initial.generation, sameFrame.generation)
        assertEquals(1, runtime.computeCount)
        assertEquals(40f, first.geometry.width)
        assertTrue(initial.geometries.containsKey(root.children.single().id))

        runtime.requestLayout()
        runtime.frame(2, environment)
        assertEquals(2, runtime.computeCount)
        runtime.close()
    }

    @Test
    fun `intrinsic content keeps a stable cached handle and coalesces mark dirty`() {
        val root = Element("root")
        val text = UiScope(root).element("text", TextContent("first"))
        val styles = StyleRuntime()
        styles.attach(root, state(style {}))
        val runtime = LayoutRuntime(root, LogicalUnitResolver, { styles.resolve(it).first }, false)
        val environment = UiEnvironment(Size(200f, 100f))

        runtime.frame(1, environment)
        val initialMeasurements = runtime.measurementCount
        text.content = TextContent("second")
        text.content = TextContent("third")

        assertEquals(1, runtime.intrinsicMarkDirtyCount)
        runtime.frame(2, environment)
        assertTrue(runtime.measurementCount > initialMeasurements)
        assertEquals("third", (text.content as TextContent).text)
        runtime.close()
    }

    @Test
    fun `text measurement and painting share the same layout result`() {
        val content = TextContent("shared")
        val input = IntrinsicMeasureInput(availableWidth = MeasureSpace.Definite(48f))
        content.measure(input)
        val measuredLayout = content.lastLayoutResult

        content.record(
            object : PaintRecorder {
                override fun record(command: com.antepod.lumentika.runtime.PaintCommand) = Unit
            },
            com.antepod.lumentika.geometry.Rect(0f, 0f, 48f, 16f),
        )

        assertSame(measuredLayout, content.lastLayoutResult)
    }
}
