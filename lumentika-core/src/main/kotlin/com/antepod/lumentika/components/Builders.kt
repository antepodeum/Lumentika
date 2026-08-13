package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.GestureConfiguration
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.reactive.untracked
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.ImageContent
import com.antepod.lumentika.runtime.ImageSource
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiContext
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.semantics.SemanticsAttachment
import com.antepod.lumentika.semantics.SemanticsConfiguration
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditingValue
import com.antepod.lumentika.text.TextRange

public open class ElementBuilder
internal constructor(
    public val element: Element,
    context: UiContext,
) : UiScope(element, context) {
    private val semanticUpdates = mutableListOf<SemanticsBuilder.() -> Unit>()
    internal var hasConfiguration: Boolean = false
        private set

    public fun style(value: Style) {
        hasConfiguration = true
        context.attachStyle(element, value)
        context.requestFrame(true)
    }

    public fun style(block: com.antepod.lumentika.style.StyleBuilder.() -> Unit) {
        style(com.antepod.lumentika.style.style(block))
    }

    public fun semantics(block: SemanticsBuilder.() -> Unit) {
        hasConfiguration = true
        semanticUpdates += block
        applySemantics()
    }

    internal fun applySemantics() {
        if (semanticUpdates.isEmpty()) return
        val current = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
        val builder = SemanticsBuilder(current)
        semanticUpdates.forEach { update -> builder.update() }
        element.attach(SemanticsAttachment, builder.build())
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
    private var explicit = false

    public var value: String
        get() = source()
        set(value) {
            explicit = true
            source = { value }
        }

    public fun value(source: Readable<String>) {
        explicit = true
        this.source = { source.value }
    }

    public fun value(block: () -> String) {
        explicit = true
        source = block
    }

    internal fun configure(block: TextBuilder.() -> Any?) {
        val result = block()
        if (!explicit) {
            require(!hasConfiguration) {
                "configured text must set value/value { }; shorthand text must only return String"
            }
            require(result is String) {
                "text shorthand must return String; use value/value { } in multi-property blocks"
            }
            source = {
                val next = block()
                require(next is String) { "text shorthand must keep returning String" }
                next
            }
        }
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

public fun UiScope.text(block: TextBuilder.() -> Any?): Element {
    val element = element("text")
    TextBuilder(element, context).also {
        it.configure(block)
        it.mount()
    }
    return element
}

public abstract class ValueElementBuilder<T>
internal constructor(element: Element, context: UiContext, initial: T) :
    ElementBuilder(element, context) {
    protected val local: State<T> = state(initial)
    private var configured = false

    public var value: T
        get() = local.value
        set(value) {
            check(!configured) { "value source already configured" }
            local.value = value
        }

    public fun value(source: Readable<T>) {
        check(!configured) { "value source already configured" }
        configured = true
        withComponentScope(element.scope) { effect { local.value = source.value } }
    }

    public fun value(block: () -> T) {
        check(!configured) { "value source already configured" }
        configured = true
        withComponentScope(element.scope) { effect { local.value = block() } }
    }

    protected fun bind(source: Mutable<T>) {
        value(source)
        withComponentScope(element.scope) {
            effect {
                val next = local.value
                if (untracked { source.value } != next) source.value = next
            }
        }
    }
}

public class ButtonBuilder internal constructor(element: Element, context: UiContext) :
    ValueElementBuilder<String>(element, context, "") {
    public var gestures: GestureConfiguration = GestureConfiguration()
    private var click: () -> Unit = {}

    public fun onClick(listener: () -> Unit) {
        click = listener
    }

    internal fun mount(): ControlHandle {
        val handle = buttonControl(element, local, gestures) { click() }
        applySemantics()
        return handle
    }
}

public fun UiScope.button(block: ButtonBuilder.() -> Unit): ControlHandle {
    val element = element("button")
    return ButtonBuilder(element, context).apply(block).mount()
}

public class CheckboxBuilder internal constructor(element: Element, context: UiContext) :
    ValueElementBuilder<Boolean>(element, context, false) {
    public var label: String? = null
    public var gestures: GestureConfiguration = GestureConfiguration()
    private var changed: (Boolean) -> Unit = {}

    public fun bindValue(source: Mutable<Boolean>) = bind(source)

    public fun onChange(listener: (Boolean) -> Unit) {
        changed = listener
    }

    internal fun mount(): ControlHandle {
        val handle = checkboxControl(element, local, label, gestures) { changed(it) }
        applySemantics()
        return handle
    }
}

public fun UiScope.checkbox(block: CheckboxBuilder.() -> Unit): ControlHandle {
    val element = element("checkbox")
    return CheckboxBuilder(element, context).apply(block).mount()
}

public class SliderBuilder internal constructor(element: Element, context: UiContext) :
    ValueElementBuilder<Float>(element, context, 0f) {
    public var min: Float = 0f
    public var max: Float = 1f
    public var gestures: GestureConfiguration = GestureConfiguration()
    private var changed: (Float) -> Unit = {}

    public fun bindValue(source: Mutable<Float>) = bind(source)

    public fun onChange(listener: (Float) -> Unit) {
        changed = listener
    }

    internal fun mount(): ControlHandle {
        require(max >= min) { "slider max must be greater than or equal to min" }
        val handle = sliderControl(element, local, min, max, gestures) { changed(it) }
        applySemantics()
        return handle
    }
}

public fun UiScope.slider(block: SliderBuilder.() -> Unit): ControlHandle {
    val element = element("slider")
    return SliderBuilder(element, context).apply(block).mount()
}

public class TextFieldBuilder internal constructor(element: Element, context: UiContext) :
    ElementBuilder(element, context) {
    private var source: () -> String = { "" }
    private var bound: Mutable<String>? = null
    private var changed: (String) -> Unit = {}
    public var multiline: Boolean = false
    public var secure: Boolean = false
    public var placeholder: String? = null
    public var gestures: GestureConfiguration = GestureConfiguration()

    public var value: String
        get() = source()
        set(value) {
            check(bound == null) { "value binding already configured" }
            source = { value }
        }

    public fun value(source: Readable<String>) {
        check(bound == null) { "value binding already configured" }
        this.source = { source.value }
    }

    public fun value(block: () -> String) {
        check(bound == null) { "value binding already configured" }
        source = block
    }

    public fun bindValue(source: Mutable<String>) {
        check(bound == null) { "value binding already configured" }
        bound = source
        this.source = { source.value }
    }

    public fun onChange(listener: (String) -> Unit) {
        changed = listener
    }

    internal fun mount(): ControlHandle {
        val initial = source()
        val controller =
            TextEditingController(
                TextEditingValue(initial, TextRange(initial.length, initial.length))
            )
        withComponentScope(element.scope) {
            effect {
                val next = source()
                untracked { controller.reconcileExternal(next) }
            }
            effect {
                val text = controller.value.text
                bound?.let { if (untracked { it.value } != text) it.value = text }
                changed(text)
            }
        }
        val handle = textFieldControl(element, controller, gestures, multiline, secure)
        applySemantics()
        return handle
    }
}

public fun UiScope.textField(block: TextFieldBuilder.() -> Unit = {}): ControlHandle {
    val element = element("textField")
    return TextFieldBuilder(element, context).apply(block).mount()
}

public class ImageBuilder internal constructor(element: Element, context: UiContext) :
    ElementBuilder(element, context) {
    public lateinit var source: ImageSource
    public var size: Size? = null

    internal fun mount() {
        require(::source.isInitialized) { "image source is required" }
        element.content = ImageContent(source, size ?: context.images?.intrinsicSize(source))
        applySemantics()
        context.requestFrame(true)
    }
}

public fun UiScope.image(block: ImageBuilder.() -> Unit): Element {
    val element = element("image")
    ImageBuilder(element, context).apply(block).mount()
    return element
}
