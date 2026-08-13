package com.antepod.lumentika.component

import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.UiScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ComponentTest {
    private class Counter : Component() {
        val label = prop("Count")
        val count = binding(0)

        override fun view(): Element = ui.element("counter")
    }

    @Test
    fun `view executes once and binding is two way`() {
        val root = Element("root")
        val external = state(2)
        val counter = Counter()
        counter.count.bind(external, counter.componentScope)
        val mounted = counter.mount(UiScope(root))
        external.value = 3
        assertEquals(3, counter.count.value)
        counter.count.value = 4
        assertEquals(4, external.value)
        assertEquals(1, counter.viewExecutions)
        assertSame(mounted, root.children.single())
    }

    @Test
    fun `one way and two way binding configuration are mutually exclusive`() {
        val scope = com.antepod.lumentika.reactive.ComponentScope()
        val external = state(1)

        val oneWay = binding(0)
        oneWay.set(2)
        assertFailsWith<IllegalArgumentException> { oneWay.bind(external, scope) }

        val twoWay = binding(0)
        twoWay.bind(external, scope)
        assertFailsWith<IllegalArgumentException> { twoWay.set(2) }
        scope.close()
    }

    @Test
    fun `show and keyed forEach preserve local identity`() {
        val root = Element("root")
        val visible = state(true)
        val items = state(listOf(1, 2))
        val scope = UiScope(root)
        val shown = scope.show(visible) { element("child") }
        val repeated = scope.forEach(items, key = { it }) { element("item-$it") }
        val two = repeated.children[1]
        visible.value = false
        items.value = listOf(2, 3)
        assertEquals(0, shown.children.size)
        assertSame(two, repeated.children[0])
        assertEquals(
            listOf("item-2", "item-3"),
            repeated.children.map { it.children.single().kind },
        )
    }

    @Test
    fun `duplicate keys fail`() {
        val root = Element("root")
        val items = state(listOf(1))
        UiScope(root).forEach(items, key = { it }) { element("item") }
        assertFailsWith<IllegalArgumentException> { items.value = listOf(1, 1) }
    }
}
