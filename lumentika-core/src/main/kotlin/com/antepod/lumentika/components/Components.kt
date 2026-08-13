package com.antepod.lumentika.components

import com.antepod.lumentika.gesture.ScrollState
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import com.antepod.lumentika.text.TextEditingController

public class ControlHandle(
    public val element: Element,
    public val semantics: SemanticsConfiguration,
    public val activate: () -> Unit = {},
)

public fun UiScope.block(content: UiScope.() -> Unit = {}): Element =
    element("block", block = content)

public fun UiScope.flex(content: UiScope.() -> Unit = {}): Element =
    element("flex", block = content)

public fun UiScope.row(content: UiScope.() -> Unit = {}): Element = element("row", block = content)

public fun UiScope.column(content: UiScope.() -> Unit = {}): Element =
    element("column", block = content)

public fun UiScope.grid(content: UiScope.() -> Unit = {}): Element =
    element("grid", block = content)

public fun UiScope.stack(content: UiScope.() -> Unit = {}): Element =
    element("stack", block = content)

public fun UiScope.scroll(
    state: ScrollState = ScrollState(),
    content: UiScope.() -> Unit = {},
): Element = element("scroll", block = content).also { it.attach(AttachmentKey(), state) }

public fun UiScope.list(content: UiScope.() -> Unit = {}): Element =
    element("list", block = content)

public fun UiScope.text(value: String): Element = element("text", TextContent(value))

public fun UiScope.image(
    source: ImageSource,
    size: com.antepod.lumentika.geometry.Size? = null,
): Element = element("image", ImageContent(source, size))

private fun control(
    element: Element,
    semantics: SemanticsConfiguration,
    activate: () -> Unit = {},
): ControlHandle {
    element.attach(SemanticsAttachment, semantics)
    return ControlHandle(element, semantics, activate)
}

public fun UiScope.button(label: String, onClick: () -> Unit = {}): ControlHandle {
    val e = element("button", TextContent(label))
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.BUTTON,
            label = label,
            actions =
                mapOf(
                    SemanticAction.CLICK to
                        {
                            onClick()
                            true
                        }
                ),
        ),
        onClick,
    )
}

public fun UiScope.checkbox(value: Mutable<Boolean>): ControlHandle {
    val action = { value.value = !value.value }
    val e = element("checkbox")
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.CHECKBOX,
            checked = value.value,
            actions =
                mapOf(
                    SemanticAction.CLICK to
                        {
                            action()
                            true
                        }
                ),
        ),
        action,
    )
}

public fun UiScope.slider(
    value: Mutable<Float>,
    minimum: Float = 0f,
    maximum: Float = 1f,
): ControlHandle {
    val e = element("slider")
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.SLIDER,
            range = SemanticRange(value.value, minimum, maximum),
            actions =
                mapOf(
                    SemanticAction.SET_VALUE to
                        { v ->
                            value.value = (v as Number).toFloat().coerceIn(minimum, maximum)
                            true
                        }
                ),
        ),
    )
}

public fun UiScope.textField(
    controller: TextEditingController = TextEditingController()
): ControlHandle {
    val e = element("textField", TextContent(controller.value.text))
    return control(
        e,
        SemanticsConfiguration(
            role = SemanticRole.TEXT_FIELD,
            value = controller.value.text,
            textSelection = controller.value.selection,
            password = false,
        ),
    )
}

public fun UiScope.tooltip(text: String, content: UiScope.() -> Unit = {}): Element =
    element("tooltip", block = content).also { it.content = TextContent(text) }
