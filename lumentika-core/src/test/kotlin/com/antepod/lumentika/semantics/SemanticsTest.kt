package com.antepod.lumentika.semantics

import com.antepod.lumentika.geometry.*
import com.antepod.lumentika.render.*
import com.antepod.lumentika.runtime.Element
import kotlin.test.*

class SemanticsTest {
    @Test
    fun `live regions announce changed content and explicit messages`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 10f, 10f) }
        val announcements = mutableListOf<Pair<String, LiveRegion>>()
        val runtime =
            SemanticsRuntime(root) { message, priority ->
                announcements += message to priority
            }
        root.attach(
            SemanticsAttachment,
            SemanticsConfiguration(label = "first", liveRegion = LiveRegion.POLITE),
        )
        runtime.commit(HitTestArtifact(1, emptyList()))
        root.attach(
            SemanticsAttachment,
            SemanticsConfiguration(label = "second", liveRegion = LiveRegion.ASSERTIVE),
        )
        runtime.commit(HitTestArtifact(2, emptyList()))
        runtime.announce("saved")

        assertEquals(
            listOf(
                "first" to LiveRegion.POLITE,
                "second" to LiveRegion.ASSERTIVE,
                "saved" to LiveRegion.POLITE,
            ),
            announcements,
        )
        root.close()
    }

    @Test
    fun `semantic action focus and geometry use committed transform chain`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 10f, 10f) }
        var clicked = false
        root.attach(
            SemanticsAttachment,
            SemanticsConfiguration(
                role = SemanticRole.BUTTON,
                actions =
                    mapOf(
                        SemanticAction.CLICK to
                            {
                                clicked = true
                                true
                            }
                    ),
            ),
        )
        val entry =
            HitTestEntry(
                root,
                Rect(0f, 0f, 10f, 10f),
                Matrix3.translation(20f, 30f),
                Rect(0f, 0f, 100f, 100f),
                0,
                false,
            )
        val runtime = SemanticsRuntime(root)
        runtime.commit(HitTestArtifact(1, listOf(entry)))
        val node = runtime.artifact.nodes.values.single()
        assertEquals(Rect(20f, 30f, 10f, 10f), node.bounds)
        assertTrue(runtime.perform(node.id, SemanticAction.CLICK))
        assertTrue(clicked)
        assertTrue(runtime.requestAccessibilityFocus(node.id))
        assertEquals(node.id, runtime.accessibilityFocus)
    }

    @Test
    fun `merge consumes descendant nodes and combines readable semantics`() {
        val root = Element().apply { geometry = Rect(0f, 0f, 10f, 10f) }
        var clicked = false
        val child =
            Element().also {
                it.geometry = Rect(10f, 0f, 10f, 10f)
                it.attach(
                    SemanticsAttachment,
                    SemanticsConfiguration(
                        label = "child",
                        actions =
                            mapOf(
                                SemanticAction.CLICK to
                                    {
                                        clicked = true
                                        true
                                    }
                            ),
                    ),
                )
                root.append(it)
            }
        root.attach(
            SemanticsAttachment,
            SemanticsConfiguration(label = "parent", mergeDescendants = true),
        )
        val runtime = SemanticsRuntime(root)

        runtime.commit(HitTestArtifact(1, emptyList()))

        val merged = runtime.artifact.nodes.values.single()
        assertEquals("parent child", merged.config.label)
        assertTrue(runtime.perform(merged.id, SemanticAction.CLICK))
        assertTrue(clicked)
        assertEquals(Rect(0f, 0f, 20f, 10f), merged.bounds)
        assertTrue(child.isMounted)
        root.close()
    }
}
