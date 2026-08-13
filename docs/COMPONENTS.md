# Built-in components

Lumentika ships behavior-first components. Their appearance is controlled by styles and themes, so
a UI library can provide its own visual language without replacing interaction logic.

| Builder | Purpose |
| --- | --- |
| `block` | Block-layout container |
| `flex` | Generic flex container |
| `row`, `column` | Horizontal and vertical flex containers |
| `grid` | Grid-layout container |
| `stack` | Overlay container |
| `scroll` | Scroll container with gesture and wheel behavior |
| `list` | Scrollable semantic collection |
| `text` | Reactive text content |
| `image` | Image content resolved by the platform image service |
| `button` | Pressable action control |
| `checkbox` | Boolean control with two-way binding |
| `slider` | Bounded numeric control with input/change events |
| `textField` | Single-line or multiline editor with selection and IME support |
| `tooltip` | Delayed anchored popup |

## Controls

```kotlin
val checked = state(false)
val volume = state(0.5f)
val query = state("")

root.scope.column {
    button {
        value = "Save"
        enabled = true
        onClick { save() }
    }
    checkbox {
        label = "Notifications"
        bindValue(checked)
    }
    slider {
        min = 0f
        max = 1f
        step = 0.05f
        bindValue(volume)
        onInput { previewVolume(it) }
    }
    textField {
        placeholder = "Search"
        bindValue(query)
    }
}
```

Each control returns a `ControlHandle` containing its element, current semantics, activation entry
point, gesture handle when applicable, and typed access to persistent visual parts.

| Control | Stable visual parts |
| --- | --- |
| `Button` | `ROOT`, `LABEL`, `ICON` |
| `Checkbox` | `ROOT`, `INDICATOR`, `LABEL` |
| `Slider` | `ROOT`, `TRACK`, `THUMB`, `LABEL` |
| `TextField` | `ROOT`, `TEXT`, `PLACEHOLDER`, `CURSOR`, `SELECTION`, scrollbar parts |
| `Scroll` | `ROOT`, `SCROLLBAR_TRACK`, `SCROLLBAR_THUMB` |

Theme styles and builder `partStyle` calls target these tokens. Behavior and semantics remain on the
owner control; visual child elements do not replace or remount when values change.

## Text and images

```kotlin
text("Static")
text(reactiveString)
text { "Computed: ${count.value}" }
text {
    value = "Direction-aware"
    alignment = TextAlign.START
    direction = Direction.RTL
}

image {
    source = ImageSource.Uri("textures/icon.png")
    size = Size(16f, 16f)
    description = "Status icon"
}
```

Text alignment is part of `TextLayoutRequest`. `START` and `END` resolve using its `Direction`;
physical `LEFT` and `RIGHT`, `CENTER`, and `JUSTIFY` are also available.

Mark an image `decorative = true` when accessibility should ignore it. Image loading and intrinsic
metadata belong to the platform `ImageService`.

## Scrolling and lists

```kotlin
val scrollState = ScrollState()

root.scope.list {
    state = scrollState
    forEach(items, key = Item::id) { item ->
        text(item.label)
    }
}
```

Scroll state is explicit and can be shared with scrollbars or inspected by higher-level components.
Nested scroll and gesture thresholds can be customized through the builder.

## Semantics

Every element builder accepts a semantics block:

```kotlin
text {
    value = "42"
    semantics {
        label = "Current score"
        value = "42 points"
    }
}
```

Built-in controls already publish their role, state, and actions. Add custom semantics only for
domain-specific meaning or custom elements.
