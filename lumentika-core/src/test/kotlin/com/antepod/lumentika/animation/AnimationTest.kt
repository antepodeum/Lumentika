package com.antepod.lumentika.animation

import com.antepod.lumentika.HeadlessFrameScheduler
import com.antepod.lumentika.HeadlessRenderBackend
import com.antepod.lumentika.PlatformServices
import com.antepod.lumentika.UiRoot
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.StyleImpact
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimationTest {
    @Test
    fun `transition publishes sparse overlay and removes it at exact target`() {
        val element = Element()
        val styles = StyleRuntime()
        styles.attach(element, state(style { opacity = 1f }))
        val target = styles.resolve(element).first
        val clock = UiAnimationClock()
        val impacts = mutableListOf<StyleImpact>()
        var requests = 0
        val runtime =
            StyleAnimationRuntime(clock, { _, impact -> impacts += impact }, { requests++ })
        runtime.transition(
            element,
            Properties.Opacity,
            0f,
            1f,
            Transition(TweenSpec(100), FloatAnimationAdapter),
        )

        clock.frame(50_000_000)
        assertEquals(0.5f, runtime.effective(element, target)[Properties.Opacity])
        assertEquals(1, runtime.overlay.size)
        assertTrue(impacts.single().contains(StyleImpact.EFFECT))

        clock.frame(100_000_000)
        assertEquals(1f, runtime.effective(element, target)[Properties.Opacity])
        assertEquals(0, runtime.overlay.size)
        assertTrue(requests >= 2)
        runtime.close()
    }

    @Test
    fun `layout transition routes sampled width through one Taffy compute per frame`() {
        val scheduler = HeadlessFrameScheduler()
        val root =
            UiRoot(
                UiEnvironment(Size(100f, 100f)),
                PlatformServices(scheduler),
                HeadlessRenderBackend(),
            )
        val child = root.scope.element("animated")
        root.styles.attach(
            child,
            state(
                style {
                    width = 20.px
                    height = 10.px
                }
            ),
        )
        root.styleAnimations.transition(
            child,
            Properties.Width,
            10.px,
            20.px,
            Transition(TweenSpec(100), DimensionAnimationAdapter),
        )

        root.frame(50_000_000)
        val afterFirst = root.layoutComputeCount
        assertEquals(15f, child.geometry.width)
        root.requestFrame()
        root.frame(50_000_000)
        assertEquals(afterFirst, root.layoutComputeCount)

        root.frame(100_000_000)
        assertEquals(20f, child.geometry.width)
        assertEquals(0, root.styleAnimations.overlay.size)
        root.close()
    }

    @Test
    fun `transition DSL exposes only typed animatable properties`() {
        val policy = transitions {
            opacity = TweenSpec(80)
            width = SpringSpec()
        }
        assertTrue(policy[Properties.Opacity] != null)
        assertTrue(policy[Properties.Width] != null)
        assertFalse(policy[Properties.Height] != null)
    }
}
