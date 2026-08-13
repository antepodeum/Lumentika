# Built-in components

Lumentika ships behavior-first components whose appearance comes from typed styles and themes.

The DSL has one structural rule:

```text
Function arguments configure a component.
A trailing UI lambda is child or default-slot content only.
```

`block`, `flex`, `row`, `column`, `grid`, `stack`, `scroll`, and `list` accept trailing child
content. `text`, `image`, `button`, `checkbox`, `slider`, and `textField` are leaves. `tooltip`
accepts trailing anchor content.

## Reactive arguments

Built-in semantic components use the same `@UIComponent` declarations and KSP factories as
application components. There is no primary or privileged property. Every declaration follows its
own semantics:

| Declaration | Accepted generated inputs |
| --- | --- |
| `Prop<T>` | `constant(T)`, `source(Readable<T>)`, `formula { T }` |
| `Binding<T>` | All prop inputs plus `bind(Mutable<T>)` |

Kotlin has no union parameter type, so generated factories use `PropInput<T>` and
`BindingInput<T>` instead of Cartesian overload sets or `Any`. Direct overloads remain for source
compatibility, but generated inputs are the canonical declaration-complete API. A mutable supplied
through `source(...)` to a prop stays one-way; only `bind(...)` on a binding declaration writes
back.

```kotlin
val volume = state(50f)
val enabled = state(true)
val title = derived { "Volume: ${volume.value.toInt()}%" }

root.scope.column {
    text(value = source(title))
    text(value = formula { "Enabled: ${enabled.value}" })
    slider(
        value = bind(volume),
        min = constant(0f),
        max = constant(100f),
        step = constant(1f),
        enabled = source(enabled),
        onInput = ::previewVolume,
        onChange = ::commitVolume,
    )
    checkbox(checked = bind(enabled), label = constant("Enabled"))
    button(
        value = constant("Reset"),
        enabled = formula { enabled.value && volume.value != 50f },
        onClick = { volume.value = 50f },
    )
}
```

External changes flow into bound controls. Control interaction writes back to the same `Mutable`
and then invokes its event callback. Props, readable binding sources, and formulas never receive
writes. This applies equally to `enabled`, labels, placeholders, slider ranges, and other declared
inputs.

## Containers

```kotlin
val viewport = ScrollState()

column(style = PANEL) {
    row(style = TOOLBAR) {
        text(value = "Tools")
    }
    scroll(state = viewport, style = SCROLLER) {
        grid(style = GRID) {
            // children
        }
    }
}
```

Container configuration never uses the trailing receiver. `list` adds collection semantics and
works with keyed structural content:

```kotlin
list(state = listState, style = LIST) {
    forEach(items, key = Item::id) { item ->
        text(value = item.label)
    }
}
```

## Text, images, and editing

```kotlin
text(value = "Static")
text(value = reactiveString)
text(value = { "Computed: ${count.value}" })
text(
    value = "Direction-aware",
    alignment = TextAlign.START,
    direction = Direction.RTL,
)

image(
    source = ImageSource.Uri("textures/icon.png"),
    size = Size(16f, 16f),
    description = "Status icon",
)

textField(
    value = query,
    placeholder = "Search",
    multiline = false,
)
```

Text alignment is stored in `TextLayoutRequest`. Image loading and intrinsic metadata belong to
the platform `ImageService`. Set `decorative = true` when accessibility should ignore an image.
`textField(value = mutableText)` is two-way; `selection = mutableSelection` binds its selection.

## Styles, parts, and semantics

Every built-in accepts `style`. Controls with stable visual parts accept a typed `partStyles` map:

```kotlin
button(
    value = "Save",
    style = PRIMARY_BUTTON,
    partStyles = mapOf(
        Button.Part.LABEL to style { color = rgb(255, 255, 255) },
    ),
    semantics = semantics { label = "Save profile" },
    onClick = ::save,
)
```

| Control | Stable visual parts |
| --- | --- |
| `Button` | `ROOT`, `LABEL`, `ICON` |
| `Checkbox` | `ROOT`, `INDICATOR`, `LABEL` |
| `Slider` | `ROOT`, `TRACK`, `THUMB`, `LABEL` |
| `TextField` | `ROOT`, `TEXT`, `PLACEHOLDER`, `CURSOR`, `SELECTION`, scrollbar parts |
| `Scroll` | `ROOT`, `SCROLLBAR_TRACK`, `SCROLLBAR_THUMB` |

Each control returns a `ControlHandle` containing its owner element, current semantics, activation
entry point, gesture handle, and typed access to persistent part elements. Reactive changes do not
replace those parts.
