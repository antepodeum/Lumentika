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

Annotate a `Component` to generate a type-safe builder. Declarations made with the protected
component helpers become builder configuration:

```kotlin
@UIComponent
class LabeledToggle : Component() {
    val label = requiredProp<String>()
    val checked = binding(false)
    val change = event<Boolean>()
    val content = slot()

    override fun view(): Element = ui.row {
        text { label.value }
        checkbox {
            bindValue(checked)
            onChange(change::emit)
        }
        content.mount(this)
    }
}
```

With `lumentika-ksp` configured, the generated DSL is used like this:

```kotlin
val enabled = state(false)

root.scope.labeledToggle {
    label { "Experimental" }
    bindChecked(enabled)
    onChange { println("changed: $it") }
    content { text("Restart required") }
}
```

- `prop` is one-way input; `requiredProp` must be configured before mount.
- `binding` is two-way input; `bindX` connects it to `Mutable<T>`.
- `event` supports one or more listeners.
- `slot` and `slotList` accept child content.
- `Component.view` runs once for a mounted component; reactive value blocks update independently.

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
