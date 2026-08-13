package com.antepod.lumentika

import com.antepod.lumentika.animation.DimensionAnimationAdapter
import com.antepod.lumentika.animation.FloatAnimationAdapter
import com.antepod.lumentika.animation.Transition
import com.antepod.lumentika.animation.TweenSpec
import com.antepod.lumentika.components.button
import com.antepod.lumentika.components.checkbox
import com.antepod.lumentika.components.column
import com.antepod.lumentika.components.slider
import com.antepod.lumentika.components.text
import com.antepod.lumentika.components.textField
import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.platform.AccessibilityPreferences
import com.antepod.lumentika.platform.UiInsets
import com.antepod.lumentika.platform.UnitEnvironment
import com.antepod.lumentika.platform.UnitRevisions
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.runtime.OwnershipCounters
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IntegrationProofTest {
    @Test
    fun `environment families update without remounting`() {
        val root = headlessRoot(100f, 100f)
        val child = root.scope.text("stable")
        root.requestFrame()
        root.frame(1)
        val updated =
            root.environment.value.copy(
                units = UnitEnvironment(2f, 1.4f, 0.5f, UnitRevisions(1, 1, 1)),
                accessibility = AccessibilityPreferences(true, 200),
                motionDurationScale = 0.5f,
                insets = UiInsets(systemBars = Insets(1f, 2f, 3f, 4f)),
            )

        root.publishEnvironment(updated)
        root.frame(2)

        assertSame(child, root.element.children.single())
        assertSame(child.scope, root.element.children.single().scope)
        root.close()
    }

    @Test
    fun `opacity transition performs no layout and width computes at most once per frame`() {
        val root = headlessRoot(100f, 100f)
        val child = root.scope.text("animated")
        root.styles.attach(
            child,
            state(
                style {
                    width = 20.px
                    height = 10.px
                    opacity = 0.5f
                }
            ),
        )
        root.requestFrame()
        root.frame(1)
        val initialLayouts = root.layoutComputeCount
        val initialRecords = root.renderRecordCount

        root.styleAnimations.transition(
            child,
            Properties.Opacity,
            1f,
            0.5f,
            Transition(TweenSpec(100), FloatAnimationAdapter),
        )
        root.frame(50_000_000)
        assertEquals(initialLayouts, root.layoutComputeCount)
        assertEquals(initialRecords, root.renderRecordCount)

        root.styleAnimations.transition(
            child,
            Properties.Width,
            10.px,
            20.px,
            Transition(TweenSpec(100), DimensionAnimationAdapter),
        )
        root.frame(60_000_000)
        val firstWidthFrame = root.layoutComputeCount
        root.requestFrame()
        root.frame(60_000_000)
        assertEquals(firstWidthFrame, root.layoutComputeCount)
        root.close()
    }

    @Test
    fun `static scrolling performs no Taffy compute or paint recording`() {
        val root = headlessRoot(100f, 100f)
        val child = root.scope.text("retained")
        root.requestFrame()
        root.frame(1)
        val layouts = root.layoutComputeCount
        val records = root.renderRecordCount

        root.configureRender(child, RenderProperties(scrollOffset = Point(0f, 10f)))
        root.frame(2)

        assertEquals(layouts, root.layoutComputeCount)
        assertEquals(records, root.renderRecordCount)
        root.close()
    }

    @Test
    fun `repeated roots return ownership counters to baseline`() {
        val baseline = OwnershipCounters.snapshot()
        repeat(20) {
            val root = headlessRoot(100f, 100f)
            val checked = state(false)
            root.scope.column {
                text("owned")
                button("button")
                checkbox(checked)
                slider(state(0f))
                textField()
            }
            root.close()
        }
        assertEquals(baseline, OwnershipCounters.snapshot())
    }
}
