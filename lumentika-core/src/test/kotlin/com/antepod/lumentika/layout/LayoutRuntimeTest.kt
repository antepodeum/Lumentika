package com.antepod.lumentika.layout

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.LogicalUnitResolver
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.dp
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutRuntimeTest {
    @Test
    fun `real Taffy projects stable tree and computes at most once per frame`() {
        val root = Element("root")
        val scope = UiScope(root)
        scope.fragment {
            element("first")
            element("text", TextContent("hello"))
        }
        val styles = StyleRuntime()
        styles.attach(root, state(style { width = 100.dp; height = 100.dp }))
        val first = root.children.single().children.first()
        styles.attach(first, state(style { width = 40.dp; height = 20.dp }))
        val runtime = LayoutRuntime(root, LogicalUnitResolver, { styles.resolve(it).first }, rounding = false)
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
}
