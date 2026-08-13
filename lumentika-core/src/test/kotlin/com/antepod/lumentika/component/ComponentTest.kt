package com.antepod.lumentika.component

import com.antepod.lumentika.animation.TransitionEventType
import com.antepod.lumentika.animation.TransitionEvents
import com.antepod.lumentika.animation.fade
import com.antepod.lumentika.animation.flip
import com.antepod.lumentika.animation.fly
import com.antepod.lumentika.animation.inOut
import com.antepod.lumentika.animation.transition
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.headlessRoot
import com.antepod.lumentika.platform.UiLifecycleState
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ComponentTest {
    private class Counter : Component() {
        val label = prop("Count")
        val count = binding(0)

        override fun view(): Element = ui.element()
    }

    @Test
    fun `view executes once and binding is two way`() {
        val root = Element()
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
        val root = Element()
        val visible = state(true)
        val items = state(listOf(1, 2))
        val scope = UiScope(root)
        val shown = scope.show(visible) { element() }
        val repeated = scope.forEach(items, key = { it }) { element(TextContent("item-$it")) }
        val two = repeated.children[1]
        visible.value = false
        items.value = listOf(2, 3)
        assertEquals(0, shown.children.size)
        assertSame(two, repeated.children[0])
        assertEquals(
            listOf("item-2", "item-3"),
            repeated.children.map { (it.children.single().content as TextContent).text },
        )
    }

    @Test
    fun `duplicate keys fail`() {
        val root = Element()
        val items = state(listOf(1))
        UiScope(root).forEach(items, key = { it }) { element() }
        assertFailsWith<IllegalArgumentException> { items.value = listOf(1, 1) }
    }

    @Test
    fun `unmount disposes component scope and detaches binding`() {
        val root = Element()
        val external = state(1)
        val counter = Counter()
        counter.count.bind(external, counter.componentScope)
        counter.mount(UiScope(root))

        root.close()
        counter.count.value = 9

        assertEquals(1, external.value)
        assertTrue(counter.componentScope.isDisposed)
    }

    @Test
    fun `bidirectional show transition reverses and defers unmount`() {
        val root = headlessRoot(100f, 100f)
        val visible = state(true)
        val trace = mutableListOf<TransitionEventType>()
        val shown =
            root.scope.show(
                visible,
                transition(fade(durationMillis = 100)),
                TransitionEvents(
                    onIntroStart = { trace += it.type },
                    onOutroStart = { trace += it.type },
                    onOutroEnd = { trace += it.type },
                    onCancel = { trace += it.type },
                ),
            ) {
                element()
            }
        root.frame(1)
        root.frame(50_000_001)

        visible.value = false
        assertEquals(1, shown.children.size)
        root.frame(50_000_001)
        assertEquals(1, shown.children.size)
        root.frame(100_000_001)

        assertTrue(shown.children.isEmpty())
        assertEquals(
            listOf(
                TransitionEventType.INTRO_START,
                TransitionEventType.CANCEL,
                TransitionEventType.OUTRO_START,
                TransitionEventType.OUTRO_END,
            ),
            trace,
        )
        root.close()
    }

    @Test
    fun `independent intro and outro complete as one deferred group`() {
        val root = headlessRoot(100f, 100f)
        val visible = state(true)
        val shown =
            root.scope.show(
                visible,
                inOut(
                    enter = fly(y = 10f, durationMillis = 100),
                    exit = fade(durationMillis = 40),
                ),
            ) {
                element()
            }
        root.frame(1)
        root.frame(20_000_001)

        visible.value = false
        root.frame(20_000_001)
        root.frame(60_000_001)
        assertEquals(1, shown.children.size)
        root.frame(100_000_001)

        assertTrue(shown.children.isEmpty())
        root.close()
    }

    @Test
    fun `outro group retains every child until the slowest transition ends`() {
        val root = headlessRoot(100f, 100f)
        val visible = state(true)
        lateinit var slow: Element
        val variableExit =
            com.antepod.lumentika.animation.ElementTransition { context ->
                com.antepod.lumentika.animation.ElementTransitionConfig(
                    durationMillis = if (context.element === slow) 100 else 40
                ) { t, _ ->
                    com.antepod.lumentika.animation.TransitionFrame(opacity = t)
                }
            }
        val shown =
            root.scope.show(visible, inOut(exit = variableExit)) {
                element()
                slow = element()
            }
        root.frame(1)

        visible.value = false
        root.frame(1)
        root.frame(40_000_001)
        assertEquals(2, shown.children.size)
        root.frame(100_000_001)

        assertTrue(shown.children.isEmpty())
        root.close()
    }

    @Test
    fun `keyed forEach FLIP preserves visual position then animates without layout`() {
        val root = headlessRoot(100f, 100f)
        val items = state(listOf(1, 2))
        var starts = 0
        var ends = 0
        var measuredDistance = 0f
        val repeated =
            root.scope.forEach(
                items,
                key = { it },
                animation =
                    flip(
                        durationMillis = { distance ->
                            measuredDistance = distance
                            100
                        }
                    ),
                animationEvents =
                    com.antepod.lumentika.animation.LayoutAnimationEvents(
                        onStart = { starts++ },
                        onEnd = { ends++ },
                    ),
            ) {
                element()
            }
        repeated.children.forEach { wrapper ->
            root.styles.attach(
                wrapper,
                state(
                    style {
                        width = 100.px
                        height = 20.px
                    }
                ),
            )
        }
        root.requestFrame()
        root.frame(1)
        val one = repeated.children[0]
        val oldOne = root.committedRender.hitTest.entries.single { it.element === one }
        val oldOrigin = oldOne.rootTransform.transform(Point(0f, 0f))

        items.value = listOf(2, 1)
        root.frame(2)
        val animatedOne = root.committedRender.hitTest.entries.single { it.element === one }
        val animatedOrigin = animatedOne.rootTransform.transform(Point(0f, 0f))
        val layoutsAfterMove = root.layoutComputeCount
        val recordsAfterMove = root.renderRecordCount

        assertEquals(oldOrigin, animatedOrigin)
        assertEquals(20f, measuredDistance)
        assertEquals(2, starts)
        root.frame(50_000_002)
        assertEquals(layoutsAfterMove, root.layoutComputeCount)
        assertEquals(recordsAfterMove, root.renderRecordCount)
        root.frame(100_000_002)
        val finalOne = root.committedRender.hitTest.entries.single { it.element === one }
        assertEquals(Point(0f, 20f), finalOne.rootTransform.transform(Point(0f, 0f)))
        assertEquals(2, ends)
        assertEquals(0, root.elementAnimations.activeCount)
        root.close()
    }

    @Test
    fun `structural transitions pause with lifecycle and snap at zero motion scale`() {
        val root = headlessRoot(100f, 100f)
        val visible = state(true)
        val shown =
            root.scope.show(visible, transition(fade(durationMillis = 100))) {
                element()
            }
        root.frame(1)
        root.publishEnvironment(root.environment.value.copy(lifecycle = UiLifecycleState.SUSPENDED))
        root.frame(1_000_000_001)
        assertEquals(1, root.elementAnimations.activeCount)
        assertEquals(1, shown.children.size)

        root.publishEnvironment(
            root.environment.value.copy(
                lifecycle = UiLifecycleState.ACTIVE,
                motionDurationScale = 0f,
            )
        )
        root.frame(1_000_000_002)
        assertEquals(0, root.elementAnimations.activeCount)

        visible.value = false
        root.frame(1_000_000_003)
        assertTrue(shown.children.isEmpty())
        root.close()
    }
}
