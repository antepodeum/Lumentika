package com.antepod.lumentika.input

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.runtime.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class InputTest {
    @Test
    fun `capture target bubble and default action have deterministic order`() {
        val root = Element("root")
        val parent = Element("parent").also(root::append)
        val target = Element("target").also(parent::append)
        val dispatcher = EventDispatcher(root)
        val trace = mutableListOf<String>()
        dispatcher.on(root, EventType.POINTER_DOWN, capture = true) { trace += "root-capture" }
        dispatcher.on(target, EventType.POINTER_DOWN, capture = true) { trace += "target-capture" }
        dispatcher.on(target, EventType.POINTER_DOWN) { trace += "target" }
        dispatcher.on(parent, EventType.POINTER_DOWN) { trace += "parent-bubble" }
        dispatcher.defaultAction(target, EventType.POINTER_DOWN) { trace += "default" }
        dispatcher.dispatch(EventType.POINTER_DOWN, PointerEvent(target, 1, PointerType.MOUSE, Point(1f, 2f), timestampNanos = 1))
        assertEquals(listOf("root-capture", "target-capture", "target", "parent-bubble", "default"), trace)
    }

    @Test
    fun `prevent default and pointer capture work`() {
        val root = Element("root")
        val first = Element("first").also(root::append)
        val second = Element("second").also(root::append)
        val dispatcher = EventDispatcher(root)
        var delivered: Element? = null
        var defaultRan = false
        dispatcher.on(first, EventType.POINTER_MOVE) { delivered = it.currentTarget; it.preventDefault() }
        dispatcher.defaultAction(first, EventType.POINTER_MOVE) { defaultRan = true }
        dispatcher.setPointerCapture(first, 2)
        val allowed = dispatcher.dispatch(EventType.POINTER_MOVE, PointerEvent(second, 2, PointerType.TOUCH, Point(0f, 0f), timestampNanos = 2))
        assertSame(first, delivered)
        assertFalse(allowed)
        assertFalse(defaultRan)
    }

    @Test
    fun `focus traversal and repair exclude removed subtree`() {
        val root = Element("root")
        val first = Element("first").also(root::append)
        val branch = Element("branch").also(root::append)
        val second = Element("second").also(branch::append)
        val dispatcher = EventDispatcher(root)
        val focus = FocusManager(root, dispatcher)
        focus.configure(first, FocusProperties(focusable = true))
        focus.configure(second, FocusProperties(focusable = true))
        focus.focusNext()
        assertSame(first, focus.activeElement)
        focus.focusNext()
        assertSame(second, focus.activeElement)
        focus.repairBeforeRemoval(branch)
        assertSame(first, focus.activeElement)
        root.remove(branch)
        focus.blur(first)
        assertNull(focus.activeElement)
    }
}
