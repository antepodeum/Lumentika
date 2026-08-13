package com.antepod.lumentika

import com.antepod.lumentika.animation.DimensionAnimationAdapter
import com.antepod.lumentika.animation.FloatAnimationAdapter
import com.antepod.lumentika.animation.Transition
import com.antepod.lumentika.animation.TweenSpec
import com.antepod.lumentika.animation.fade
import com.antepod.lumentika.animation.transition
import com.antepod.lumentika.component.show
import com.antepod.lumentika.components.button
import com.antepod.lumentika.components.checkbox
import com.antepod.lumentika.components.column
import com.antepod.lumentika.components.slider
import com.antepod.lumentika.components.text
import com.antepod.lumentika.components.textField
import com.antepod.lumentika.geometry.CornerRadii
import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.platform.AccessibilityPreferences
import com.antepod.lumentika.platform.UiInsets
import com.antepod.lumentika.platform.UnitEnvironment
import com.antepod.lumentika.platform.UnitRevisions
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.runtime.Content
import com.antepod.lumentika.runtime.ContentInvalidation
import com.antepod.lumentika.runtime.IntrinsicMeasurable
import com.antepod.lumentika.runtime.IntrinsicMeasureInput
import com.antepod.lumentika.runtime.OwnershipCounters
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.runtime.PaintRecorder
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.rgb
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IntegrationProofTest {
    @Test
    fun `retained content invalidation separates paint intrinsic and text metrics`() {
        class RetainedContent(var size: com.antepod.lumentika.geometry.Size) :
            Content, IntrinsicMeasurable {
            var color = 0xff000000.toInt()
            var records = 0

            override fun measure(input: IntrinsicMeasureInput) = size

            override fun record(
                recorder: PaintRecorder,
                bounds: com.antepod.lumentika.geometry.Rect,
            ) {
                records++
                recorder.record(PaintCommand.FillRect(bounds, color))
            }
        }

        val root = headlessRoot(100f, 100f)
        val content = RetainedContent(com.antepod.lumentika.geometry.Size(20f, 10f))
        val element = root.scope.element(content)
        root.requestFrame()
        root.frame(1)
        val initialElement = element
        val initialLayout = root.layoutComputeCount
        val initialRecords = content.records

        content.color = 0xffffffff.toInt()
        element.invalidateContent(ContentInvalidation.PAINT)
        root.frame(2)
        assertEquals(initialLayout, root.layoutComputeCount)
        assertEquals(initialRecords + 1, content.records)

        content.size = com.antepod.lumentika.geometry.Size(20f, 30f)
        element.invalidateContent(ContentInvalidation.INTRINSIC_MEASUREMENT)
        element.invalidateContent(ContentInvalidation.INTRINSIC_MEASUREMENT)
        root.frame(3)
        assertEquals(initialLayout + 1, root.layoutComputeCount)
        assertEquals(30f, element.geometry.height)

        val afterIntrinsic = root.layoutComputeCount
        element.invalidateContent(ContentInvalidation.TEXT_METRICS)
        root.frame(4)
        assertEquals(afterIntrinsic, root.layoutComputeCount)

        content.size = com.antepod.lumentika.geometry.Size(20f, 35f)
        element.invalidateContent(ContentInvalidation.TEXT_METRICS)
        root.frame(5)
        assertEquals(afterIntrinsic + 1, root.layoutComputeCount)
        assertEquals(35f, element.geometry.height)
        assertSame(initialElement, root.element.children.single())
        root.close()
    }

    @Test
    fun `paint radius and clip changes do not recompute Taffy`() {
        val root = headlessRoot(100f, 100f)
        val element = root.scope.element()
        val source =
            state(
                style {
                    width = 40.px
                    height = 30.px
                    background = rgb(1, 2, 3)
                }
            )
        root.styles.attach(element, source)
        root.requestFrame()
        root.frame(1)
        val computes = root.layoutComputeCount

        source.value = style {
            width = 40.px
            height = 30.px
            background = rgb(3, 2, 1)
            borderRadius = CornerRadii(8f)
            clipShape =
                com.antepod.lumentika.geometry.RoundedRect(
                    com.antepod.lumentika.geometry.Rect(0f, 0f, 40f, 30f),
                    CornerRadii(8f),
                )
        }
        root.requestFrame(layoutDirty = false)
        root.frame(2)

        assertEquals(computes, root.layoutComputeCount)
        assertTrue(
            root.committedRender.paint.chunks.single { it.element === element }.commands.single()
                is com.antepod.lumentika.runtime.PaintCommand.FillRoundedRect
        )
        root.close()
    }

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
                button(value = "button")
                checkbox(checked = checked)
                slider(value = 0f)
                textField()
            }
            root.close()
        }
        assertEquals(baseline, OwnershipCounters.snapshot())
    }

    @Test
    fun `closing root cancels active structural motion without ownership leaks`() {
        val baseline = OwnershipCounters.snapshot()
        val root = headlessRoot(100f, 100f)
        val visible = state(true)
        root.scope.show(visible, transition(fade(durationMillis = 1_000))) {
            text("animated")
        }
        root.frame(1)
        assertEquals(1, root.elementAnimations.activeCount)

        root.close()

        assertEquals(0, root.elementAnimations.activeCount)
        assertEquals(baseline, OwnershipCounters.snapshot())
    }
}
