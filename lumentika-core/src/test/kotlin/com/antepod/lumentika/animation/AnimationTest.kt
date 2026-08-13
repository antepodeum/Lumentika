package com.antepod.lumentika.animation

import com.antepod.lumentika.HeadlessFrameScheduler
import com.antepod.lumentika.HeadlessRenderBackend
import com.antepod.lumentika.PlatformServices
import com.antepod.lumentika.UiRoot
import com.antepod.lumentika.components.text
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UiLifecycleState
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.MotionRenderProperties
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
    fun `environment motion scale snaps and suspension has no hidden time jump`() {
        val root =
            UiRoot(
                UiEnvironment(Size(100f, 100f)),
                PlatformServices(HeadlessFrameScheduler()),
                HeadlessRenderBackend(),
            )
        val element = root.scope.element("animated")
        root.styles.attach(element, state(style { opacity = 1f }))
        root.frame(10_000_000)
        root.styleAnimations.transition(
            element,
            Properties.Opacity,
            0f,
            1f,
            Transition(TweenSpec(100), FloatAnimationAdapter),
        )
        root.publishEnvironment(root.environment.value.copy(lifecycle = UiLifecycleState.SUSPENDED))
        root.frame(510_000_000)
        assertEquals(
            0f,
            root.styleAnimations.overlay
                .effective(element, root.styles.resolve(element).first)[Properties.Opacity],
        )

        root.publishEnvironment(root.environment.value.copy(lifecycle = UiLifecycleState.ACTIVE))
        root.frame(520_000_000)
        val resumed =
            root.styleAnimations.overlay
                .effective(element, root.styles.resolve(element).first)[Properties.Opacity]
        assertTrue(resumed in .09f..0.11f)

        root.publishEnvironment(root.environment.value.copy(motionDurationScale = 0f))
        root.frame(530_000_000)
        assertEquals(1f, root.styles.resolve(element).first[Properties.Opacity])
        assertEquals(0, root.styleAnimations.overlay.size)
        root.close()
    }

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
    fun `retargeting continues from the sampled value without a jump`() {
        val root =
            UiRoot(
                UiEnvironment(Size(100f, 100f)),
                PlatformServices(HeadlessFrameScheduler()),
                HeadlessRenderBackend(),
            )
        val child = root.scope.text("retarget")
        root.requestFrame()
        root.frame(1)
        val transition = Transition(TweenSpec(100), FloatAnimationAdapter)
        root.styleAnimations.transition(child, Properties.Opacity, 0f, 1f, transition)
        root.frame(50_000_001)
        val sampled =
            root.styleAnimations
                .effective(child, root.styles.resolve(child).first)[Properties.Opacity]

        root.styleAnimations.transition(child, Properties.Opacity, 1f, 0f, transition)
        val afterRetarget =
            root.styleAnimations
                .effective(child, root.styles.resolve(child).first)[Properties.Opacity]

        assertEquals(sampled, afterRetarget)
        root.frame(150_000_001)
        assertEquals(0, root.styleAnimations.overlay.size)
        root.close()
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
        assertTrue(policy[Properties.Opacity]?.adapter === GeneratedOpacityAnimationAdapter)
    }

    @Test
    fun `crossfade pairs send and receive geometry and falls back when unmatched`() {
        val clock = UiAnimationClock()
        val motions = mutableMapOf<Element, MotionRenderProperties>()
        val bounds = mutableMapOf<Element, Rect>()
        val root = Element("root")
        val sender = Element("sender").also(root::append)
        val receiver = Element("receiver").also(root::append)
        bounds[sender] = Rect(0f, 0f, 20f, 20f)
        bounds[receiver] = Rect(100f, 0f, 40f, 40f)
        val runtime =
            ElementAnimationRuntime(
                clock,
                { element, motion ->
                    if (motion == null) motions.remove(element) else motions[element] = motion
                },
                bounds::get,
                {},
            )
        val pair =
            crossfade(
                durationMillis = { 100L },
                fallback = fade(durationMillis = 100),
            )

        runtime.start(sender, "send", pair.send("item"), TransitionDirection.OUT)
        runtime.start(receiver, "receive", pair.receive("item"), TransitionDirection.IN)
        runtime.afterCommit()
        assertEquals(
            Point(-100f, 0f),
            motions.getValue(receiver).transform.transform(Point(0f, 0f)),
        )

        clock.frame(50_000_000)
        assertEquals(Point(50f, 0f), motions.getValue(sender).transform.transform(Point(0f, 0f)))
        assertEquals(Point(-50f, 0f), motions.getValue(receiver).transform.transform(Point(0f, 0f)))
        assertEquals(.5f, motions.getValue(sender).opacity)
        assertEquals(.5f, motions.getValue(receiver).opacity)
        clock.frame(100_000_000)
        assertTrue(motions.isEmpty())

        runtime.start(sender, "fallback", pair.send("missing"), TransitionDirection.OUT)
        runtime.afterCommit()
        clock.frame(150_000_000)
        assertEquals(.5f, motions.getValue(sender).opacity)
        clock.frame(200_000_000)
        assertTrue(motions.isEmpty())
        runtime.close()
        root.close()
    }

    @Test
    fun `structural builtins expose natural state at one and transformed state at zero`() {
        val element = Element()
        val bounds = Rect(0f, 0f, 20f, 10f)
        val context = ElementTransitionContext(element, bounds, TransitionDirection.IN)

        assertEquals(0f, fade().create(context).sample(0f, 1f).opacity)
        assertEquals(
            Point(12f, 8f),
            fly(x = 12f, y = 8f).create(context).sample(0f, 1f).transform.transform(Point(0f, 0f)),
        )
        assertEquals(
            Point(10f, 5f),
            scale(start = .5f).create(context).sample(0f, 1f).transform.transform(Point(10f, 5f)),
        )
        assertEquals(
            Point(0f, 5f),
            slide(SlideAxis.HORIZONTAL)
                .create(context)
                .sample(0f, 1f)
                .transform
                .transform(Point(10f, 5f)),
        )
        assertEquals(TransitionFrame(), fade().create(context).sample(1f, 0f))
        element.close()
    }

    @Test
    fun `custom transition receives committed context and honors scaled delay`() {
        val clock = UiAnimationClock().also { it.motionScale = 2f }
        val element = Element()
        val bounds = Rect(4f, 5f, 20f, 10f)
        var received: ElementTransitionContext? = null
        var motion: MotionRenderProperties? = null
        val runtime =
            ElementAnimationRuntime(
                clock,
                { _, value -> motion = value },
                { bounds },
                {},
            )
        val custom = ElementTransition { context ->
            received = context
            ElementTransitionConfig(delayMillis = 25, durationMillis = 50) { t, _ ->
                TransitionFrame(opacity = t)
            }
        }

        runtime.start(element, "custom", custom, TransitionDirection.IN)
        runtime.afterCommit()
        clock.frame(49_000_000)
        assertEquals(0f, motion?.opacity)
        clock.frame(100_000_000)
        assertEquals(.5f, motion?.opacity)
        clock.frame(150_000_000)

        assertEquals(
            ElementTransitionContext(element, bounds, TransitionDirection.IN),
            received,
        )
        assertEquals(null, motion)
        runtime.close()
        element.close()
    }
}
