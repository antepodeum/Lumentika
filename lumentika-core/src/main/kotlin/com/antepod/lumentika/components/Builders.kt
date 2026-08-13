package com.antepod.lumentika.components

import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.gesture.NestedScrollConnection
import com.antepod.lumentika.gesture.ScrollState
import com.antepod.lumentika.platform.GestureConfiguration
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.reactive.untracked
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.ImageContent
import com.antepod.lumentika.runtime.ImageSource
import com.antepod.lumentika.runtime.TextContent
import com.antepod.lumentika.runtime.UiScope
import com.antepod.lumentika.semantics.CollectionInfo
import com.antepod.lumentika.semantics.CollectionItemInfo
import com.antepod.lumentika.semantics.LiveRegion
import com.antepod.lumentika.semantics.SemanticAction
import com.antepod.lumentika.semantics.SemanticRange
import com.antepod.lumentika.semantics.SemanticRole
import com.antepod.lumentika.semantics.SemanticsAttachment
import com.antepod.lumentika.semantics.SemanticsConfiguration
import com.antepod.lumentika.style.Direction
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.Overflow
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.style.StylePart
import com.antepod.lumentika.text.AutofillConfiguration
import com.antepod.lumentika.text.TextAlign
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextEditingValue
import com.antepod.lumentika.text.TextLayoutRequest
import com.antepod.lumentika.text.TextRange

/** Mutable projection used to construct an immutable accessibility configuration. */
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

/** Scope-independent typed accessibility updates applied over component-provided semantics. */
public class ElementSemantics internal constructor(internal val update: SemanticsBuilder.() -> Unit)

/** Builds an accessibility update for a component's `semantics` argument. */
public fun semantics(block: SemanticsBuilder.() -> Unit): ElementSemantics = ElementSemantics(block)

internal fun Element.applySemantics(value: ElementSemantics?) {
    if (value == null) return
    val current = attachment(SemanticsAttachment) ?: SemanticsConfiguration()
    attach(SemanticsAttachment, SemanticsBuilder(current).apply(value.update).build())
}

internal fun UiScope.configure(
    element: Element,
    style: Style?,
    partStyles: Map<out StylePart<*>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
) {
    style?.let { context.attachStyle(element, it) }
    partStyles.forEach { (part, value) -> context.attachPartStyle(element, part, value) }
    element.applySemantics(semantics)
    if (style != null || partStyles.isNotEmpty()) context.requestFrame(true)
    if (semantics != null) context.requestFrame(false)
}

/** Mounts a scrollable container. Function arguments configure it; [content] mounts children. */
public fun UiScope.scroll(
    state: ScrollState = ScrollState(),
    gestures: GestureConfiguration = context.gestureConfiguration(),
    nestedScroll: NestedScrollConnection? = null,
    style: Style? = null,
    partStyles: Map<StylePart<Scroll>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element {
    val element = element()
    context.attachStyle(element, com.antepod.lumentika.style.style { overflow = Overflow.SCROLL })
    mountScroll(element, state, gestures, nestedScroll)
    configure(element, style, partStyles, semantics)
    nested(element).content()
    return element
}

/** Mounts a scrollable vertical list. Function arguments configure it; [content] mounts items. */
public fun UiScope.list(
    state: ScrollState = ScrollState(),
    gestures: GestureConfiguration = context.gestureConfiguration(),
    nestedScroll: NestedScrollConnection? = null,
    style: Style? = null,
    partStyles: Map<StylePart<Scroll>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element {
    val element = element()
    context.attachStyle(
        element,
        com.antepod.lumentika.style.style {
            display = Display.FLEX
            flexDirection = FlexDirection.COLUMN
            overflow = Overflow.SCROLL
        },
    )
    mountScroll(element, state, gestures, nestedScroll)
    val baseSemantics = element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()
    element.attach(
        SemanticsAttachment,
        baseSemantics.copy(role = SemanticRole.LIST),
    )
    configure(element, style, partStyles, semantics)
    nested(element).content()
    return element
}

internal fun UiScope.mountText(
    source: () -> String,
    alignment: Readable<TextAlign>,
    direction: Readable<Direction>,
    style: Style?,
    semantics: ElementSemantics?,
): Element {
    val element = element()
    var lastValue = untracked(source)
    element.attach(
        SemanticsAttachment,
        SemanticsConfiguration(role = SemanticRole.TEXT, label = lastValue),
    )
    configure(element, style, semantics = semantics)
    withComponentScope(element.scope) {
        effect {
            val value = source()
            element.content =
                TextContent(
                    TextLayoutRequest(
                        value,
                        alignment = alignment.value,
                        direction = direction.value,
                    ),
                    context.textLayout,
                )
            val current = element.attachment(SemanticsAttachment)!!
            if (current.label == lastValue) {
                element.attach(SemanticsAttachment, current.copy(label = value))
            }
            lastValue = value
            context.requestFrame(true)
        }
    }
    return element
}

/** Mounts constant text. */
public fun UiScope.text(
    value: String,
    alignment: TextAlign = TextAlign.START,
    direction: Direction = Direction.LTR,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element = mountText({ value }, state(alignment), state(direction), style, semantics)

/** Mounts text backed by a one-way reactive source. */
public fun UiScope.text(
    value: Readable<String>,
    alignment: TextAlign = TextAlign.START,
    direction: Direction = Direction.LTR,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element = mountText({ value.value }, state(alignment), state(direction), style, semantics)

/** Mounts text computed by a scope-owned tracked formula. */
public fun UiScope.text(
    value: () -> String,
    alignment: TextAlign = TextAlign.START,
    direction: Direction = Direction.LTR,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element = mountText(value, state(alignment), state(direction), style, semantics)

private fun <T> UiScope.controlValue(
    source: () -> T,
    element: Element,
): com.antepod.lumentika.reactive.State<T> {
    val local = state(untracked(source))
    withComponentScope(element.scope) {
        effect {
            val next = source()
            if (untracked { local.value } != next) local.value = next
        }
    }
    return local
}

private fun UiScope.mountButton(
    source: () -> String,
    gestures: GestureConfiguration,
    enabled: Boolean,
    style: Style?,
    partStyles: Map<StylePart<Button>, Style>,
    semantics: ElementSemantics?,
    onClick: () -> Unit,
): ControlHandle {
    val element = element()
    val handle =
        buttonControl(element, controlValue(source, element), gestures, state(enabled), onClick)
    configure(element, style, partStyles, semantics)
    return handle
}

/** Mounts a button with a constant label. */
public fun UiScope.button(
    value: String = "",
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Button>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onClick: () -> Unit = {},
): ControlHandle = mountButton({ value }, gestures, enabled, style, partStyles, semantics, onClick)

/** Mounts a button with a one-way reactive label. */
public fun UiScope.button(
    value: Readable<String>,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Button>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onClick: () -> Unit = {},
): ControlHandle =
    mountButton({ value.value }, gestures, enabled, style, partStyles, semantics, onClick)

/** Mounts a button with a label computed by a tracked formula. */
public fun UiScope.button(
    value: () -> String,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Button>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onClick: () -> Unit = {},
): ControlHandle = mountButton(value, gestures, enabled, style, partStyles, semantics, onClick)

private fun UiScope.mountCheckbox(
    source: () -> Boolean,
    binding: Mutable<Boolean>?,
    label: String?,
    gestures: GestureConfiguration,
    enabled: Boolean,
    style: Style?,
    partStyles: Map<StylePart<Checkbox>, Style>,
    semantics: ElementSemantics?,
    onChange: (Boolean) -> Unit,
): ControlHandle {
    val element = element()
    val local = controlValue(source, element)
    val handle =
        checkboxControl(element, local, state(label), gestures, state(enabled)) { next ->
            binding?.let { if (it.value != next) it.value = next }
            onChange(next)
        }
    configure(element, style, partStyles, semantics)
    return handle
}

/** Mounts a checkbox with a constant local value. */
public fun UiScope.checkbox(
    checked: Boolean = false,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Checkbox>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (Boolean) -> Unit = {},
): ControlHandle =
    mountCheckbox(
        { checked },
        null,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a checkbox with a one-way reactive value. */
public fun UiScope.checkbox(
    checked: Readable<Boolean>,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Checkbox>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (Boolean) -> Unit = {},
): ControlHandle =
    mountCheckbox(
        { checked.value },
        null,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a checkbox bidirectionally bound to [checked]. */
public fun UiScope.checkbox(
    checked: Mutable<Boolean>,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Checkbox>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (Boolean) -> Unit = {},
): ControlHandle =
    mountCheckbox(
        { checked.value },
        checked,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a checkbox with a value computed by a tracked formula. */
public fun UiScope.checkbox(
    checked: () -> Boolean,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Checkbox>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (Boolean) -> Unit = {},
): ControlHandle =
    mountCheckbox(
        checked,
        null,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

private fun UiScope.mountSlider(
    source: () -> Float,
    binding: Mutable<Float>?,
    min: Float,
    max: Float,
    step: Float?,
    label: String?,
    gestures: GestureConfiguration,
    enabled: Boolean,
    style: Style?,
    partStyles: Map<StylePart<Slider>, Style>,
    semantics: ElementSemantics?,
    onInput: (Float) -> Unit,
    onChange: (Float) -> Unit,
): ControlHandle {
    require(max >= min) { "slider max must be greater than or equal to min" }
    require(step == null || step.isFinite() && step > 0f)
    val element = element()
    val local = controlValue(source, element)
    val handle =
        sliderControl(
            element,
            local,
            state(min),
            state(max),
            state(step),
            state(label),
            gestures,
            state(enabled),
            onInput = { next ->
                binding?.let { if (it.value != next) it.value = next }
                onInput(next)
            },
            onChange = onChange,
        )
    configure(element, style, partStyles, semantics)
    return handle
}

/** Mounts a slider with a constant local value. */
public fun UiScope.slider(
    value: Float = 0f,
    min: Float = 0f,
    max: Float = 1f,
    step: Float? = null,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Slider>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onInput: (Float) -> Unit = {},
    onChange: (Float) -> Unit = {},
): ControlHandle =
    mountSlider(
        { value },
        null,
        min,
        max,
        step,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onInput,
        onChange,
    )

/** Mounts a slider with a one-way reactive value. */
public fun UiScope.slider(
    value: Readable<Float>,
    min: Float = 0f,
    max: Float = 1f,
    step: Float? = null,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Slider>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onInput: (Float) -> Unit = {},
    onChange: (Float) -> Unit = {},
): ControlHandle =
    mountSlider(
        { value.value },
        null,
        min,
        max,
        step,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onInput,
        onChange,
    )

/** Mounts a slider bidirectionally bound to [value]. */
public fun UiScope.slider(
    value: Mutable<Float>,
    min: Float = 0f,
    max: Float = 1f,
    step: Float? = null,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Slider>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onInput: (Float) -> Unit = {},
    onChange: (Float) -> Unit = {},
): ControlHandle =
    mountSlider(
        { value.value },
        value,
        min,
        max,
        step,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onInput,
        onChange,
    )

/** Mounts a slider with a value computed by a tracked formula. */
public fun UiScope.slider(
    value: () -> Float,
    min: Float = 0f,
    max: Float = 1f,
    step: Float? = null,
    label: String? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<Slider>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onInput: (Float) -> Unit = {},
    onChange: (Float) -> Unit = {},
): ControlHandle =
    mountSlider(
        value,
        null,
        min,
        max,
        step,
        label,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onInput,
        onChange,
    )

private fun UiScope.mountTextField(
    source: () -> String,
    binding: Mutable<String>?,
    controller: TextEditingController?,
    selection: Mutable<TextRange>?,
    multiline: Boolean,
    secure: Boolean,
    placeholder: String?,
    autofill: AutofillConfiguration?,
    gestures: GestureConfiguration,
    enabled: Boolean,
    style: Style?,
    partStyles: Map<StylePart<TextField>, Style>,
    semantics: ElementSemantics?,
    onChange: (String) -> Unit,
): ControlHandle {
    val element = element()
    val initial = untracked(source)
    val activeController =
        controller
            ?: TextEditingController(
                TextEditingValue(initial, TextRange(initial.length, initial.length))
            )
    var lastChangedText = initial
    withComponentScope(element.scope) {
        if (controller == null) {
            effect {
                val next = source()
                untracked { activeController.reconcileExternal(next) }
            }
        }
        selection?.let { selected ->
            effect {
                val next = selected.value
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
            binding?.let { if (untracked { it.value } != text) it.value = text }
            selection?.let {
                if (untracked { it.value } != editingValue.selection) {
                    it.value = editingValue.selection
                }
            }
            if (text != lastChangedText) {
                lastChangedText = text
                onChange(text)
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
            state(placeholder),
            autofill,
            state(enabled),
        )
    configure(element, style, partStyles, semantics)
    return handle
}

/** Mounts a text field with a constant local value. */
public fun UiScope.textField(
    value: String = "",
    controller: TextEditingController? = null,
    selection: Mutable<TextRange>? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    secure: Boolean = false,
    autofill: AutofillConfiguration? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<TextField>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (String) -> Unit = {},
): ControlHandle =
    mountTextField(
        { value },
        null,
        controller,
        selection,
        multiline,
        secure,
        placeholder,
        autofill,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a text field with a one-way reactive value. */
public fun UiScope.textField(
    value: Readable<String>,
    controller: TextEditingController? = null,
    selection: Mutable<TextRange>? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    secure: Boolean = false,
    autofill: AutofillConfiguration? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<TextField>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (String) -> Unit = {},
): ControlHandle =
    mountTextField(
        { value.value },
        null,
        controller,
        selection,
        multiline,
        secure,
        placeholder,
        autofill,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a text field bidirectionally bound to [value]. */
public fun UiScope.textField(
    value: Mutable<String>,
    controller: TextEditingController? = null,
    selection: Mutable<TextRange>? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    secure: Boolean = false,
    autofill: AutofillConfiguration? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<TextField>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (String) -> Unit = {},
): ControlHandle =
    mountTextField(
        { value.value },
        value,
        controller,
        selection,
        multiline,
        secure,
        placeholder,
        autofill,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

/** Mounts a text field with a value computed by a tracked formula. */
public fun UiScope.textField(
    value: () -> String,
    controller: TextEditingController? = null,
    selection: Mutable<TextRange>? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    secure: Boolean = false,
    autofill: AutofillConfiguration? = null,
    enabled: Boolean = true,
    gestures: GestureConfiguration = context.gestureConfiguration(),
    style: Style? = null,
    partStyles: Map<StylePart<TextField>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    onChange: (String) -> Unit = {},
): ControlHandle =
    mountTextField(
        value,
        null,
        controller,
        selection,
        multiline,
        secure,
        placeholder,
        autofill,
        gestures,
        enabled,
        style,
        partStyles,
        semantics,
        onChange,
    )

internal fun UiScope.mountImage(
    source: () -> ImageSource,
    size: Readable<Size?>,
    description: Readable<String?>,
    decorative: Readable<Boolean>,
    style: Style?,
    semantics: ElementSemantics?,
): Element {
    val element = element()
    element.attach(
        SemanticsAttachment,
        SemanticsConfiguration(
            role = SemanticRole.IMAGE,
            label = description.value,
            hidden = decorative.value,
        ),
    )
    withComponentScope(element.scope) {
        effect {
            val next = source()
            element.content = ImageContent(next, size.value ?: context.images?.intrinsicSize(next))
            element.attach(
                SemanticsAttachment,
                (element.attachment(SemanticsAttachment) ?: SemanticsConfiguration()).copy(
                    label = description.value,
                    hidden = decorative.value,
                ),
            )
            context.requestFrame(true)
        }
    }
    configure(element, style, semantics = semantics)
    return element
}

/** Mounts a constant image source. */
public fun UiScope.image(
    source: ImageSource,
    size: Size? = null,
    description: String? = null,
    decorative: Boolean = false,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element =
    mountImage(
        { source },
        state(size),
        state(description),
        state(decorative),
        style,
        semantics,
    )

/** Mounts a one-way reactive image source. */
public fun UiScope.image(
    source: Readable<ImageSource>,
    size: Size? = null,
    description: String? = null,
    decorative: Boolean = false,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element =
    mountImage(
        { source.value },
        state(size),
        state(description),
        state(decorative),
        style,
        semantics,
    )

/** Mounts an image source computed by a tracked formula. */
public fun UiScope.image(
    source: () -> ImageSource,
    size: Size? = null,
    description: String? = null,
    decorative: Boolean = false,
    style: Style? = null,
    semantics: ElementSemantics? = null,
): Element =
    mountImage(
        source,
        state(size),
        state(description),
        state(decorative),
        style,
        semantics,
    )

private fun UiScope.mountTooltipComponent(
    source: () -> String,
    showDelayMillis: Long,
    hideDelayMillis: Long,
    placement: TooltipPlacement,
    offset: Float,
    style: Style?,
    partStyles: Map<StylePart<Tooltip>, Style>,
    semantics: ElementSemantics?,
    content: UiScope.() -> Unit,
): Element {
    require(showDelayMillis >= 0 && hideDelayMillis >= 0)
    require(offset.isFinite() && offset >= 0f)
    val element = element()
    val local = controlValue(source, element)
    mountTooltip(element, local, showDelayMillis, hideDelayMillis, placement, offset)
    configure(element, style, partStyles, semantics)
    nested(element).content()
    return element
}

/** Mounts a tooltip anchor; trailing [content] is anchor content only. */
public fun UiScope.tooltip(
    value: String,
    showDelayMillis: Long = 500,
    hideDelayMillis: Long = 100,
    placement: TooltipPlacement = TooltipPlacement.AUTO,
    offset: Float = 8f,
    style: Style? = null,
    partStyles: Map<StylePart<Tooltip>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountTooltipComponent(
        { value },
        showDelayMillis,
        hideDelayMillis,
        placement,
        offset,
        style,
        partStyles,
        semantics,
        content,
    )

/** Mounts a tooltip anchor with one-way reactive tooltip text. */
public fun UiScope.tooltip(
    value: Readable<String>,
    showDelayMillis: Long = 500,
    hideDelayMillis: Long = 100,
    placement: TooltipPlacement = TooltipPlacement.AUTO,
    offset: Float = 8f,
    style: Style? = null,
    partStyles: Map<StylePart<Tooltip>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountTooltipComponent(
        { value.value },
        showDelayMillis,
        hideDelayMillis,
        placement,
        offset,
        style,
        partStyles,
        semantics,
        content,
    )

/** Mounts a tooltip anchor with tooltip text computed by a tracked formula. */
public fun UiScope.tooltip(
    value: () -> String,
    showDelayMillis: Long = 500,
    hideDelayMillis: Long = 100,
    placement: TooltipPlacement = TooltipPlacement.AUTO,
    offset: Float = 8f,
    style: Style? = null,
    partStyles: Map<StylePart<Tooltip>, Style> = emptyMap(),
    semantics: ElementSemantics? = null,
    content: UiScope.() -> Unit = {},
): Element =
    mountTooltipComponent(
        value,
        showDelayMillis,
        hideDelayMillis,
        placement,
        offset,
        style,
        partStyles,
        semantics,
        content,
    )
