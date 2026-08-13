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

Built-in value properties use finite, strongly typed overloads:

| Argument | Meaning |
| --- | --- |
| `T` | Constant or control-local initial value |
| `Readable<T>` | One-way reactive source |
| `Mutable<T>` | Two-way source for binding-capable properties |
| `() -> T` | Scope-owned tracked formula |

`Mutable<T>` extends `Readable<T>`, so `slider(value = volume)` selects the two-way overload while
`slider(value = derivedVolume)` remains one-way. Tracked formulas use explicit `.value` reads and
update only the owned property target.

```kotlin
val volume = state(50f)
val enabled = state(true)
val title = derived { "Volume: ${volume.value.toInt()}%" }

root.scope.column {
    text(value = title)
    text(value = { "Enabled: ${enabled.value}" })
    slider(
        value = volume,
        min = 0f,
        max = 100f,
        step = 1f,
        onInput = ::previewVolume,
        onChange = ::commitVolume,
    )
    checkbox(checked = enabled, label = "Enabled")
    button(value = "Reset", onClick = { volume.value = 50f })
}
```

External changes flow into `Mutable` controls. Control interaction writes back to the same
`Mutable` and then invokes its event callback. `Readable` and formula inputs never receive writes.

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
