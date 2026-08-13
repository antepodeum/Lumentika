package com.antepod.lumentika.components

import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiContext
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.semantics.SemanticsAttachment
import com.antepod.lumentika.semantics.SemanticsConfiguration
import com.antepod.lumentika.style.Style

public open class ElementBuilder
internal constructor(
    public val element: Element,
    context: UiContext,
) : UiScope(element, context) {
    public fun style(value: Style) {
        context.attachStyle(element, value)
        context.requestFrame(true)
    }

    public fun style(block: com.antepod.lumentika.style.StyleBuilder.() -> Unit) {
        style(com.antepod.lumentika.style.style(block))
    }

    public fun semantics(block: SemanticsBuilder.() -> Unit) {
        val current = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
        element.attach(SemanticsAttachment, SemanticsBuilder(current).apply(block).build())
        context.requestFrame(false)
    }
}

public class ContainerBuilder internal constructor(element: Element, context: UiContext) :
    ElementBuilder(element, context)

public class SemanticsBuilder internal constructor(private val initial: SemanticsConfiguration) {
    public var label: String? = initial.label
    public var value: String? = initial.value
    public var hint: String? = initial.hint
    public var stateDescription: String? = initial.stateDescription
    public var enabled: Boolean = initial.enabled
    public var selected: Boolean? = initial.selected
    public var expanded: Boolean? = initial.expanded
    public var hidden: Boolean = initial.hidden

    internal fun build(): SemanticsConfiguration =
        initial.copy(
            label = label,
            value = value,
            hint = hint,
            stateDescription = stateDescription,
            enabled = enabled,
            selected = selected,
            expanded = expanded,
            hidden = hidden,
        )
}

public class TextBuilder internal constructor(element: Element, context: UiContext) :
    ElementBuilder(element, context) {
    private var source: () -> String = { "" }

    public var value: String
        get() = source()
        set(value) {
            source = { value }
        }

    public fun value(source: Readable<String>) {
        this.source = { source.value }
    }

    public fun value(block: () -> String) {
        source = block
    }

    internal fun mount() {
        withComponentScope(element.scope) {
            effect {
                element.content = TextContent(source(), context.textLayout)
                context.requestFrame(true)
            }
        }
    }
}

public fun UiScope.text(block: TextBuilder.() -> Unit): Element {
    val element = element("text")
    TextBuilder(element, context).apply(block).mount()
    return element
}
