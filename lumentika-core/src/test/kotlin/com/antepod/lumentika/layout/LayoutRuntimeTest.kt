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
import com.antepod.lumentika.style.AlignContent
import com.antepod.lumentika.style.AlignItems
import com.antepod.lumentika.style.Auto
import com.antepod.lumentika.style.BoxSizing
import com.antepod.lumentika.style.Clear
import com.antepod.lumentika.style.Direction
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexWrap
import com.antepod.lumentika.style.FloatLayout
import com.antepod.lumentika.style.GridAutoFlow
import com.antepod.lumentika.style.GridLine
import com.antepod.lumentika.style.GridPlacement
import com.antepod.lumentika.style.GridRepetition
import com.antepod.lumentika.style.GridTemplateArea
import com.antepod.lumentika.style.GridTemplateAreas
import com.antepod.lumentika.style.GridTemplateComponent
import com.antepod.lumentika.style.GridTrackSizing
import com.antepod.lumentika.style.Overflow
import com.antepod.lumentika.style.Position
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.dp
import com.antepod.lumentika.style.edges
import com.antepod.lumentika.style.percent
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutRuntimeTest {
    @Test
    fun `padding margin and gap are projected into real Taffy geometry`() {
        val root = Element()
        val first = Element().also(root::append)
        val second = Element().also(root::append)
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
        val root = Element()
        val scope = UiScope(root)
        scope.fragment {
            element()
            element(TextContent("hello"))
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
        val root = Element()
        val text = UiScope(root).element(TextContent("first"))
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

    @Test
    fun `every Taffy style field is projected without exposing Taffy in public API`() {
        val root = Element()
        val styles = StyleRuntime()
        val areas = GridTemplateAreas(listOf(GridTemplateArea("main", 0, 1, 0, 1)), 1, 1)
        styles.attach(
            root,
            state(
                style {
                    display = Display.GRID
                    itemIsTable = true
                    itemIsReplaced = true
                    boxSizing = BoxSizing.CONTENT_BOX
                    direction = Direction.RTL
                    overflowX = Overflow.CLIP
                    overflowY = Overflow.SCROLL
                    scrollbarWidth = 7.px
                    floatValue = FloatLayout.RIGHT
                    clear = Clear.BOTH
                    position = Position.ABSOLUTE
                    inset = edges(Auto, 2.px, 3.px, 4.px)
                    width = 200.px
                    height = 100.px
                    minWidth = 50.px
                    minHeight = 40.px
                    maxWidth = 300.px
                    maxHeight = 150.px
                    aspectRatio = 2f
                    margin = edges(1.px)
                    padding = edges(2.px)
                    border = edges(3.px)
                    alignItems = AlignItems.SAFE_CENTER
                    alignSelf = AlignItems.BASELINE
                    justifyItems = AlignItems.CENTER
                    justifySelf = AlignItems.END
                    alignContent = AlignContent.SPACE_AROUND
                    justifyContent = AlignContent.SPACE_BETWEEN
                    columnGap = 5.px
                    rowGap = 6.px
                    flexWrap = FlexWrap.WRAP_REVERSE
                    flexBasis = 25.percent
                    flexGrow = 2f
                    flexShrink = 3f
                    gridTemplateRows =
                        listOf(
                            GridTemplateComponent.Repeat(
                                GridRepetition.Count(2),
                                listOf(GridTrackSizing.flex(1)),
                                listOf(listOf("a"), listOf("b")),
                            )
                        )
                    gridTemplateColumns =
                        listOf(GridTemplateComponent.Single(GridTrackSizing.fixed(40.px)))
                    gridAutoRows = listOf(GridTrackSizing.MinContent)
                    gridAutoColumns = listOf(GridTrackSizing.fitContent(50.percent))
                    gridAutoFlow = GridAutoFlow.COLUMN_DENSE
                    gridTemplateAreas = areas
                    gridTemplateColumnNames = listOf(listOf("left"), listOf("right"))
                    gridTemplateRowNames = listOf(listOf("top"), listOf("bottom"))
                    gridRow = GridLine(GridPlacement.Line(1), GridPlacement.Span(2))
                    gridColumn =
                        GridLine(GridPlacement.Line(1, "left"), GridPlacement.Span(1, "cell"))
                }
            ),
        )
        val runtime = LayoutRuntime(root, LogicalUnitResolver, { styles.resolve(it).first }, false)

        val projected = runtime.project(styles.resolve(root).first, UiEnvironment(Size(400f, 300f)))

        assertEquals(com.antepod.taffy.style.Display.GRID, projected.display)
        assertTrue(projected.itemIsTable)
        assertTrue(projected.itemIsReplaced)
        assertEquals(com.antepod.taffy.style.BoxSizing.CONTENT_BOX, projected.boxSizing)
        assertEquals(com.antepod.taffy.style.Direction.RTL, projected.direction)
        assertEquals(com.antepod.taffy.style.Overflow.CLIP, projected.overflow.x)
        assertEquals(com.antepod.taffy.style.Overflow.SCROLL, projected.overflow.y)
        assertEquals(7f, projected.scrollbarWidth)
        assertEquals(com.antepod.taffy.style.Float.RIGHT, projected.floatValue)
        assertEquals(com.antepod.taffy.style.Clear.BOTH, projected.clear)
        assertEquals(com.antepod.taffy.style.Position.ABSOLUTE, projected.position)
        assertEquals(2f, projected.inset.right.intoRaw().value())
        assertEquals(200f, projected.size.width.value())
        assertEquals(40f, projected.minSize.height.value())
        assertEquals(300f, projected.maxSize.width.value())
        assertEquals(2f, projected.aspectRatio.orElseThrow())
        assertEquals(1f, projected.margin.left.intoRaw().value())
        assertEquals(2f, projected.padding.left.intoRaw().value())
        assertEquals(3f, projected.border.left.intoRaw().value())
        assertEquals(com.antepod.taffy.style.AlignItems.SAFE_CENTER, projected.alignItems.get())
        assertEquals(com.antepod.taffy.style.AlignItems.BASELINE, projected.alignSelf.get())
        assertEquals(com.antepod.taffy.style.AlignItems.CENTER, projected.justifyItems.get())
        assertEquals(com.antepod.taffy.style.AlignItems.END, projected.justifySelf.get())
        assertEquals(
            com.antepod.taffy.style.AlignContent.SPACE_AROUND,
            projected.alignContent.get(),
        )
        assertEquals(
            com.antepod.taffy.style.AlignContent.SPACE_BETWEEN,
            projected.justifyContent.get(),
        )
        assertEquals(5f, projected.gap.width.intoRaw().value())
        assertEquals(6f, projected.gap.height.intoRaw().value())
        assertEquals(com.antepod.taffy.style.FlexWrap.WRAP_REVERSE, projected.flexWrap)
        assertEquals(0.25f, projected.flexBasis.value())
        assertEquals(2f, projected.flexGrow)
        assertEquals(3f, projected.flexShrink)
        assertEquals(1, projected.gridTemplateRows.size)
        assertEquals(1, projected.gridTemplateColumns.size)
        assertEquals(1, projected.gridAutoRows.size)
        assertEquals(1, projected.gridAutoColumns.size)
        assertEquals(com.antepod.taffy.style.GridAutoFlow.COLUMN_DENSE, projected.gridAutoFlow)
        assertEquals("main", projected.gridTemplateAreas.get().areas.single().name)
        assertEquals(listOf("left"), projected.gridTemplateColumnNames.first())
        assertEquals(listOf("top"), projected.gridTemplateRowNames.first())
        assertEquals(1, projected.gridRow.start.value)
        assertEquals(2, projected.gridRow.end.value)
        assertEquals("left", projected.gridColumn.start.name)
        assertEquals("cell", projected.gridColumn.end.name)
        runtime.close()
        root.close()
    }
}
