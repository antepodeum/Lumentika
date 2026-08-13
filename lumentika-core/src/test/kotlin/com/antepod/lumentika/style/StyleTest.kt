package com.antepod.lumentika.style

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.LogicalUnitResolver
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StyleTest {
    @Test
    fun `style variants resolve and report orthogonal impact`() {
        val element = Element()
        val source =
            state(
                style {
                    width = 10.px
                    background = rgb(1, 2, 3)
                    on(HOVER) { opacity = 0.5f }
                }
            )
        val runtime = StyleRuntime()
        runtime.attach(element, source)
        val (_, initial) = runtime.resolve(element)
        assertTrue(initial.impact.contains(StyleImpact.LAYOUT))
        assertTrue(initial.impact.contains(StyleImpact.PAINT))
        runtime.setState(element, HOVER, true)
        val (hovered, changes) = runtime.resolve(element)
        assertEquals(0.5f, hovered[Properties.Opacity])
        assertTrue(changes.impact.contains(StyleImpact.EFFECT))
        assertFalse(changes.impact.contains(StyleImpact.LAYOUT))
    }

    @Test
    fun `environment units resolve before layout`() {
        val environment =
            UiEnvironment(
                Size(100f, 100f),
                units =
                    com.antepod.lumentika.platform.UnitEnvironment(
                        density = 2f,
                        fontScale = 1.5f,
                        physicalPixelScale = 0.5f,
                    ),
            )
        assertEquals(10f, resolveLength(5.dp, environment, LogicalUnitResolver))
        assertEquals(10f, resolveLength(5.sp, environment, LogicalUnitResolver))
        assertEquals(2.5f, resolveLength(5.physicalPx, environment, LogicalUnitResolver))
        assertEquals(25f, resolveLength(25.percent, environment, LogicalUnitResolver, 100f))
    }

    @Test
    fun `style compiles masks and shares unchanged resolved groups`() {
        val source =
            state(
                style {
                    width = 10.dp
                    set(Properties.FontSize, 14.sp)
                    on(HOVER) { opacity = 0.5f }
                }
            )
        val program = source.value.program
        assertTrue(Properties.Width in program.writtenProperties)
        assertTrue(Properties.Opacity in program.stateDependencies.getValue(HOVER))
        assertTrue(
            Properties.Width in
                program.environmentDependencies.getValue(EnvironmentDependency.DP_UNITS)
        )
        assertTrue(
            Properties.FontSize in
                program.environmentDependencies.getValue(EnvironmentDependency.SP_UNITS)
        )

        val element = Element()
        val runtime = StyleRuntime()
        runtime.attach(element, source)
        val initial = runtime.resolve(element).first
        runtime.setState(element, HOVER, true)
        val hovered = runtime.resolve(element).first

        assertSame(initial.boxLayout, hovered.boxLayout)
        assertSame(initial.inherited, hovered.inherited)
        assertEquals(0.5f, hovered.render.opacity)
    }

    @Test
    fun `generated property catalog has stable unique ids and masks`() {
        assertEquals(Properties.all.indices.toList(), Properties.all.map { it.id })
        assertEquals(
            Properties.all.size,
            Properties.all.map { it.mask }.toSet().size,
        )
        Properties.all.forEach { property ->
            assertTrue(
                property in
                    PropertyMask(Properties.all.fold(0L) { bits, item -> bits or item.mask.bits })
            )
        }
    }

    @Test
    fun `complete Taffy style surface compiles and resolves as typed values`() {
        val template =
            GridTemplateComponent.Repeat(
                GridRepetition.Count(2),
                listOf(GridTrackSizing.flex(1)),
                listOf(listOf("start"), listOf("end")),
            )
        val areas = GridTemplateAreas(listOf(GridTemplateArea("main", 0, 1, 0, 1)), 1, 1)
        val source =
            state(
                style {
                    itemIsTable = true
                    itemIsReplaced = true
                    boxSizing = BoxSizing.CONTENT_BOX
                    direction = Direction.RTL
                    overflowX = Overflow.CLIP
                    overflowY = Overflow.SCROLL
                    scrollbarWidth = 8.dp
                    floatValue = FloatLayout.LEFT
                    clear = Clear.BOTH
                    position = Position.ABSOLUTE
                    inset = edges(Auto, 10.dp, Auto, 5.percent)
                    aspectRatio = 16f / 9f
                    border = edges(1.dp)
                    alignItems = AlignItems.SAFE_CENTER
                    alignSelf = AlignItems.BASELINE
                    justifyItems = AlignItems.CENTER
                    justifySelf = AlignItems.END
                    alignContent = AlignContent.SPACE_AROUND
                    justifyContent = AlignContent.SPACE_BETWEEN
                    columnGap = 3.dp
                    rowGap = 4.dp
                    textAlign = TextAlign.LEGACY_CENTER
                    flexWrap = FlexWrap.WRAP_REVERSE
                    flexBasis = 20.percent
                    gridTemplateRows = listOf(template)
                    gridTemplateColumns =
                        listOf(GridTemplateComponent.Single(GridTrackSizing.fixed(20.dp)))
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
            )
        val element = Element()
        val runtime = StyleRuntime()
        runtime.attach(element, source)

        val resolved = runtime.resolve(element).first

        assertEquals(BoxSizing.CONTENT_BOX, resolved[Properties.BoxSizing])
        assertEquals(Overflow.CLIP, resolved[Properties.OverflowX])
        assertEquals(Overflow.SCROLL, resolved[Properties.OverflowY])
        assertEquals(template, resolved[Properties.GridTemplateRows].single())
        assertEquals(areas, resolved[Properties.GridTemplateAreas])
        assertEquals(GridAutoFlow.COLUMN_DENSE, resolved[Properties.GridAutoFlow])
        assertTrue(
            Properties.GridTemplateColumns in
                source.value.program.environmentDependencies.getValue(
                    EnvironmentDependency.DP_UNITS
                )
        )
    }
}
