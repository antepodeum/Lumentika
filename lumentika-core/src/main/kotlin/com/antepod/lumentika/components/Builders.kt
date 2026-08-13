package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.gesture.NestedScrollConnection
import com.antepod.lumentika.gesture.ScrollState
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
import com.antepod.lumentika.semantics.CollectionInfo
import com.antepod.lumentika.semantics.CollectionItemInfo
import com.antepod.lumentika.semantics.LiveRegion
import com.antepod.lumentika.semantics.SemanticAction
import com.antepod.lumentika.semantics.SemanticRange
import com.antepod.lumentika.semantics.SemanticRole
import com.antepod.lumentika.semantics.SemanticsAttachment
import com.antepod.lumentika.semantics.SemanticsConfiguration
import com.antepod.lumentika.style.Overflow
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.AutofillConfiguration
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

public open class ContainerBuilder internal constructor(element: Element, context: UiContext) :
    ElementBuilder(element, context)

public open class ScrollBuilder internal constructor(element: Element, context: UiContext) :
    ContainerBuilder(element, context) {
    public var state: ScrollState = ScrollState()
    public var gestures: GestureConfiguration = context.gestureConfiguration()
    public var nestedScroll: NestedScrollConnection? = null

    internal fun mount(): Element = mountScroll(element, state, gestures, nestedScroll)
}

public fun UiScope.scroll(block: ScrollBuilder.() -> Unit = {}): Element {
    val element = element("scroll")
    context.attachStyle(element, style { overflow = Overflow.SCROLL })
    return ScrollBuilder(element, context).apply(block).mount()
}

public class ListBuilder internal constructor(element: Element, context: UiContext) :
    ScrollBuilder(element, context)

public fun UiScope.list(block: ListBuilder.() -> Unit = {}): Element {
    val element = element("list")
    context.attachStyle(
        element,
        style {
            display = com.antepod.lumentika.style.Display.FLEX
            flexDirection = com.antepod.lumentika.style.FlexDirection.COLUMN
            overflow = Overflow.SCROLL
        },
    )
    val builder = ListBuilder(element, context).apply(block)
    builder.mount()
    val semantics = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
    element.attach(
        SemanticsAttachment,
        semantics.copy(role = SemanticRole.LIST),
    )
    return element
}

public class SemanticsBuilder internal constructor(private val initial: SemanticsConfiguration) {
    public var role: SemanticRole = initial.role
    public var label: String? = initial.label
    public var value: String? = initial.value
    public var hint: String? = initial.hint
    public var stateDescription: String? = initial.stateDescription
    public var enabled: Boolean = initial.enabled
    public var selected: Boolean? = initial.selected
    public var checked: Boolean? = initial.checked
    public var expanded: Boolean? = initial.expanded
    public var readOnly: Boolean = initial.readOnly
    public var password: Boolean = initial.password
    public var hidden: Boolean = initial.hidden
    public var mergeDescendants: Boolean = initial.mergeDescendants
    public var clearDescendants: Boolean = initial.clearDescendants
    public var range: SemanticRange? = initial.range
    public var collection: CollectionInfo? = initial.collection
    public var item: CollectionItemInfo? = initial.item
    public var liveRegion: LiveRegion = initial.liveRegion
    public var textSelection: TextRange? = initial.textSelection
    public var actions: Map<SemanticAction, (Any?) -> Boolean> = initial.actions

    internal fun build(): SemanticsConfiguration =
        initial.copy(
            role = role,
            label = label,
            value = value,
            hint = hint,
            stateDescription = stateDescription,
            enabled = enabled,
            selected = selected,
            checked = checked,
            expanded = expanded,
            readOnly = readOnly,
            password = password,
            hidden = hidden,
            mergeDescendants = mergeDescendants,
            clearDescendants = clearDescendants,
            range = range,
            collection = collection,
            item = item,
            liveRegion = liveRegion,
            textSelection = textSelection,
            actions = actions,
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
                val value = source()
                element.content = TextContent(value, context.textLayout)
                val semantics = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
                element.attach(
                    SemanticsAttachment,
                    semantics.copy(role = SemanticRole.TEXT, label = semantics.label ?: value),
                )
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
    public var gestures: GestureConfiguration = context.gestureConfiguration()
    public var enabled: Boolean = true
    private var click: () -> Unit = {}

    public fun onClick(listener: () -> Unit) {
        click = listener
    }

    internal fun mount(): ControlHandle {
        val handle = buttonControl(element, local, gestures, enabled) { click() }
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
    public var gestures: GestureConfiguration = context.gestureConfiguration()
    public var enabled: Boolean = true
    private var changed: (Boolean) -> Unit = {}

    public fun bindValue(source: Mutable<Boolean>) = bind(source)

    public fun onChange(listener: (Boolean) -> Unit) {
        changed = listener
    }

    internal fun mount(): ControlHandle {
        val handle = checkboxControl(element, local, label, gestures, enabled) { changed(it) }
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
    public var gestures: GestureConfiguration = context.gestureConfiguration()
    public var enabled: Boolean = true
    public var step: Float? = null
    private var input: (Float) -> Unit = {}
    private var changed: (Float) -> Unit = {}

    public fun bindValue(source: Mutable<Float>) = bind(source)

    public fun onChange(listener: (Float) -> Unit) {
        changed = listener
    }

    public fun onInput(listener: (Float) -> Unit) {
        input = listener
    }

    internal fun mount(): ControlHandle {
        require(max >= min) { "slider max must be greater than or equal to min" }
        require(step == null || step!!.isFinite() && step!! > 0f)
        val handle =
            sliderControl(
                element,
                local,
                min,
                max,
                step,
                gestures,
                enabled,
                onInput = input,
                onChange = changed,
            )
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
    private var boundSelection: Mutable<TextRange>? = null
    private var inputConfigured = false
    private var externalController: TextEditingController? = null
    public var controller: TextEditingController?
        get() = externalController
        set(value) {
            check(!inputConfigured) { "text field input already configured" }
            inputConfigured = value != null
            externalController = value
        }

    public var multiline: Boolean = false
    public var secure: Boolean = false
    public var placeholder: String? = null
    public var autofill: AutofillConfiguration? = null
    public var gestures: GestureConfiguration = context.gestureConfiguration()
    public var enabled: Boolean = true

    public var value: String
        get() = source()
        set(value) {
            check(!inputConfigured) { "text field input already configured" }
            inputConfigured = true
            source = { value }
        }

    public fun value(source: Readable<String>) {
        check(!inputConfigured) { "text field input already configured" }
        inputConfigured = true
        this.source = { source.value }
    }

    public fun value(block: () -> String) {
        check(!inputConfigured) { "text field input already configured" }
        inputConfigured = true
        source = block
    }

    public fun bindValue(source: Mutable<String>) {
        check(!inputConfigured) { "text field input already configured" }
        inputConfigured = true
        bound = source
        this.source = { source.value }
    }

    public fun onChange(listener: (String) -> Unit) {
        changed = listener
    }

    public fun bindSelection(source: Mutable<TextRange>) {
        check(boundSelection == null) { "selection binding already configured" }
        boundSelection = source
    }

    internal fun mount(): ControlHandle {
        val initial = source()
        val activeController =
            externalController
                ?: TextEditingController(
                    TextEditingValue(initial, TextRange(initial.length, initial.length))
                )
        var lastChangedText = initial
        withComponentScope(element.scope) {
            if (externalController == null) {
                effect {
                    val next = source()
                    untracked { activeController.reconcileExternal(next) }
                }
            }
            boundSelection?.let { selection ->
                effect {
                    val next = selection.value
                    untracked {
                        val current = activeController.value
                        if (current.selection != next) {
                            activeController.value = current.copy(selection = next)
                        }
                    }
                }
            }
            effect {
                val editingValue = activeController.value
                val text = editingValue.text
                bound?.let { if (untracked { it.value } != text) it.value = text }
                boundSelection?.let {
                    if (untracked { it.value } != editingValue.selection) {
                        it.value = editingValue.selection
                    }
                }
                if (text != lastChangedText) {
                    lastChangedText = text
                    changed(text)
                }
            }
        }
        val handle =
            textFieldControl(
                element,
                activeController,
                gestures,
                multiline,
                secure,
                placeholder,
                autofill,
                enabled,
            )
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
    private var sourceProvider: (() -> ImageSource)? = null
    public var source: ImageSource
        get() = requireNotNull(sourceProvider) { "image source is required" }.invoke()
        set(value) {
            check(sourceProvider == null) { "image source already configured" }
            sourceProvider = { value }
        }

    public var size: Size? = null
    public var description: String? = null
    public var decorative: Boolean = false

    public fun source(value: Readable<ImageSource>) {
        check(sourceProvider == null) { "image source already configured" }
        sourceProvider = { value.value }
    }

    public fun source(block: () -> ImageSource) {
        check(sourceProvider == null) { "image source already configured" }
        sourceProvider = block
    }

    internal fun mount() {
        val provider = requireNotNull(sourceProvider) { "image source is required" }
        withComponentScope(element.scope) {
            effect {
                val source = provider()
                element.content =
                    ImageContent(source, size ?: context.images?.intrinsicSize(source))
                context.requestFrame(true)
            }
        }
        element.attach(
            SemanticsAttachment,
            SemanticsConfiguration(
                role = SemanticRole.IMAGE,
                label = description,
                hidden = decorative,
            ),
        )
        applySemantics()
    }
}

public fun UiScope.image(block: ImageBuilder.() -> Unit): Element {
    val element = element("image")
    ImageBuilder(element, context).apply(block).mount()
    return element
}

public class TooltipBuilder internal constructor(element: Element, context: UiContext) :
    ValueElementBuilder<String>(element, context, "") {
    public var showDelayMillis: Long = 500
    public var hideDelayMillis: Long = 100
    public var placement: TooltipPlacement = TooltipPlacement.AUTO
    public var offset: Float = 8f

    internal fun mount(): Element {
        require(showDelayMillis >= 0 && hideDelayMillis >= 0)
        require(offset.isFinite() && offset >= 0f)
        mountTooltip(
            element,
            local,
            showDelayMillis,
            hideDelayMillis,
            placement,
            offset,
        )
        applySemantics()
        return element
    }
}

public fun UiScope.tooltip(block: TooltipBuilder.() -> Unit): Element {
    val element = element("tooltip")
    return TooltipBuilder(element, context).apply(block).mount()
}
