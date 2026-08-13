# Core concepts

## Reactive state

`state` is mutable, `derived` is lazy computed state, and `effect` tracks reads and reruns after a
dependency changes.

```kotlin
val first = state("Ada")
val last = state("Lovelace")
val fullName = derived { "${first.value} ${last.value}" }

val subscription = effect {
    println(fullName.value)
}

batch {
    first.value = "Grace"
    last.value = "Hopper"
}

subscription.close()
```

Use `untracked` to read without adding a dependency. `derivedAsync` exposes a loading/error/value
lifecycle for coroutine work. Effects created while a component or element scope is active are
disposed with that owner.

## Custom components

Annotate a `Component` to generate a typed mounting function. Function arguments configure the
component; its trailing UI lambda configures only the canonical/default slot.

```kotlin
@UIComponent
class LabeledToggle : Component() {
    val label = requiredProp<String>()
    val checked = binding(false)
    val change = event<Boolean>()
    val content = slot()

    override fun view(): Element = ui.row {
        text(value = label)
        checkbox(checked = checked, onChange = change::emit)
        content.mount(this)
    }
}
```

With `lumentika-ksp` configured, configuration is passed before trailing content:

```kotlin
val enabled = state(false)

labeledToggle(
    label = constant("Experimental"),
    checked = bind(enabled),
    onChange = { println("changed: $it") },
) {
    text(value = "Restart required")
}
```

Generated declaration arguments use explicit typed wrappers because one generated function can
contain many independent declarations:

| Wrapper | Declaration behavior |
| --- | --- |
| `constant(value)` | Fixed `Prop` or control-local `Binding` value |
| `source(readable)` | One-way `Prop` or `Binding` source |
| `formula { ... }` | Scope-owned tracked one-way formula |
| `bind(mutable)` | Two-way `Binding` source |

Generated `PropInput<T>` parameters accept `constant`, `source`, and `formula`.
`BindingInput<T>` also accepts `bind`. This distinction is statically typed. Events become nullable
`onX` function arguments whose listeners are owned by the component scope.

The slot named `content` is the canonical trailing slot. If no slot has that name, the first
declared `Slot` or `SlotList` is canonical. Other slots are named `UiScope.() -> Unit` arguments:

```kotlin
dialog(
    title = constant("Delete?"),
    footer = {
        row {
            button(value = "Cancel", onClick = ::cancel)
            button(value = "Delete", onClick = ::delete)
        }
    },
) {
    text(value = "This cannot be undone.")
}
```

- `prop` is one-way input; `requiredProp` must be configured before mount.
- `binding` is a writable input; `bind(mutable)` connects it two-way.
- `event` supports one or more listeners.
- `slot` and `slotList` accept typed child content functions.
- Omitted optional declarations retain their component-defined defaults.
- Required declarations fail before `view()` when omitted; nullable values use `constant(null)`.
- `Component.view` runs once for a mounted component; reactive arguments update independently.

Do not reuse one component instance in multiple locations. Closing or structurally removing it
disposes declarations, effects, coroutine work, and its mounted subtree.

## Structural reactivity

Use `show` for conditional content and `forEach` for keyed collections:

```kotlin
val visible = state(true)
val items = state(listOf("one", "two"))

root.scope.column {
    show(visible) { text("Visible") }
    forEach(items, key = { it }) { item -> text(item) }
}
```

Keys must be unique. Existing keyed elements retain identity when their order changes.

## Context

Context passes adapter or library values through the mounted tree without global state:

```kotlin
data class Palette(val foreground: Int) {
    companion object {
        val dark = Palette(0xffeeeeee.toInt())
    }
}

val palette = contextKey<Palette>()

root.scope.provide(palette, Palette.dark) {
    val inherited = element().context(palette)
}
```

Context lookup walks ancestors and fails if no value was provided.
