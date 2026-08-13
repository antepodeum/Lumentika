package com.antepod.lumentika

import com.antepod.lumentika.animation.StyleAnimationRuntime
import com.antepod.lumentika.component.Component
import com.antepod.lumentika.components.ControlHandle
import com.antepod.lumentika.gesture.GestureArena
import com.antepod.lumentika.input.EventDispatcher
import com.antepod.lumentika.layout.LayoutRuntime
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.render.RenderRuntime
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.semantics.SemanticsRuntime
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.text.TextEditorRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArchitectureTest {
    @Test
    fun `all universal mechanics are supplied by core`() {
        val coreLocation = UiRoot::class.java.protectionDomain.codeSource.location
        val universalTypes =
            listOf(
                Element::class,
                Readable::class,
                Component::class,
                UiEnvironment::class,
                EventDispatcher::class,
                GestureArena::class,
                LayoutRuntime::class,
                RenderRuntime::class,
                StyleRuntime::class,
                StyleAnimationRuntime::class,
                SemanticsRuntime::class,
                TextEditorRuntime::class,
                ControlHandle::class,
            )
        universalTypes.forEach { type ->
            assertEquals(
                coreLocation,
                type.java.protectionDomain.codeSource.location,
                type.qualifiedName,
            )
        }
    }

    @Test
    fun `Element has no visual or layout mega bag`() {
        val forbiddenPrefixes =
            listOf(
                "com.antepod.taffy",
                "com.antepod.lumentika.style.Resolved",
                "com.antepod.lumentika.render.Render",
                "com.antepod.lumentika.layout.Layout",
            )
        val fieldTypes = Element::class.java.declaredFields.map { it.type.name }
        assertTrue(
            fieldTypes.none { type -> forbiddenPrefixes.any(type::startsWith) },
            fieldTypes.toString(),
        )
    }
}
