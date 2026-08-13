package com.antepod.lumentika.style

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.LogicalUnitResolver
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StyleTest {
    @Test
    fun `style variants resolve and report orthogonal impact`() {
        val element = Element()
        val source = state(style {
            width = 10.px
            background = rgb(1, 2, 3)
            on(HOVER) { opacity = 0.5f }
        })
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
        val environment = UiEnvironment(Size(100f, 100f), units = com.antepod.lumentika.platform.UnitEnvironment(density = 2f, fontScale = 1.5f, physicalPixelScale = 0.5f))
        assertEquals(10f, resolveLength(5.dp, environment, LogicalUnitResolver))
        assertEquals(15f, resolveLength(5.sp, environment, LogicalUnitResolver))
        assertEquals(2.5f, resolveLength(5.physicalPx, environment, LogicalUnitResolver))
        assertEquals(25f, resolveLength(25.percent, environment, LogicalUnitResolver, 100f))
    }
}
