# Lumentika Kotlin Public API Specification

## 1. Language target

The framework is Kotlin-first on Kotlin/JVM.

The reactive graph, component runtime, universal components, style/animation runtime, retained rendering, and Taffy4J layout live in `com.antepod:lumentika-core`.

Concrete platform integration and concrete rendering/resource backends are outside the core artifact.

KSP generates component DSL facades from declaration property types.

No Kotlin compiler plugin is required for:

```text
reactivity
derived dependency tracking
effects
bindings
component declarations
component DSL generation
style DSL
theme DSL
animation DSL
structural updates
```

KSP is an API generator only. It does not rewrite arbitrary expressions or inject reactive semantics into Kotlin bytecode.

## 2. Canonical state API

```kotlin
val count = state(0)

count.value++
count.value = 10
count.update { it + 1 }
```

Reactive reads happen through `.value`.

Inside a tracked computation:

```kotlin
val doubled = derived {
    count.value * 2
}
```

reading `.value` registers the dependency automatically.

`State<T>` remains an object with stable identity so it can be passed directly to bindings.

Delegated state syntax is not canonical because it hides the mutable handle required by two-way binding.

## 3. Reactive computations

```kotlin
val label = derived {
    "Clicks: ${count.value}"
}

effect {
    audio.volume = volume.value
}

effect {
    val subscription =
        server.subscribe(channel.value)

    cleanup(subscription::close)
}

batch {
    first.value = "A"
    second.value = "B"
}

val debug = untracked {
    debugState.value
}
```

Async derived uses a suspend lambda:

```kotlin
val inventory = derivedAsync {
    server.loadInventory(
        playerId.value,
        page.value
    )
}
```

The active coroutine/job is scope-owned and cancelled on dependency restart or disposal when possible.

## 4. Component declarations

A component declaration uses normal Kotlin properties:

```kotlin
@UIComponent
class VolumeControl : Component() {
    val title = prop("Volume")
    val min = prop(0.0)
    val max = prop(100.0)

    val value = binding(50.0)

    val change = event<ChangeEvent>()

    val footer = slot()

    val expanded = state(false)

    override fun view() = column {
        text {
            value(title)
        }

        slider {
            min(min)
            max(max)
            bindValue(value)

            onChange(change::emit)
        }

        footer()
    }
}
```

The declaration property type is the only component-contract metadata.

No field-level `@Prop`, `@Binding`, `@Event`, or `@Slot` annotations exist.

## 5. Generated component DSL

For:

```kotlin
val title = prop("Volume")
```

KSP generates builder capabilities equivalent to:

```kotlin
var title: String

fun title(source: Readable<String>)

fun title(block: () -> String)
```

Canonical use:

```kotlin
volumeControl {
    title = "Music"
}
```

One-way reactive source:

```kotlin
volumeControl {
    title(titleState)
}
```

Inline reactive expression:

```kotlin
volumeControl {
    title {
        "Page ${page.value}"
    }
}
```

The lambda overload creates a scope-owned derived readable and automatically tracks reactive reads.

## 6. Binding DSL

For:

```kotlin
val value = binding(50.0)
```

KSP generates:

```kotlin
var value: Double

fun value(source: Readable<Double>)

fun value(block: () -> Double)

fun bindValue(source: Mutable<Double>)
```

The first three forms are one-way.

Only:

```kotlin
bindValue(source)
```

creates two-way propagation.

Examples:

```kotlin
slider {
    value = 50.0
}
```

```kotlin
slider {
    value {
        initial.value
    }
}
```

```kotlin
slider {
    bindValue(volume)
}
```

Configuring both a one-way value source and `bindValue(...)` for the same declared `Binding<T>` is an error before `view()` executes.

## 7. Events

For:

```kotlin
val change = event<ChangeEvent>()
```

KSP generates:

```kotlin
fun onChange(listener: (ChangeEvent) -> Unit)
```

Use:

```kotlin
slider {
    onChange { event ->
        println(event.value)
    }
}
```

Emission inside the component:

```kotlin
change.emit(event)
```

## 8. Slots

For:

```kotlin
val footer = slot()
val items = slotList()
```

KSP generates nested DSL entry points:

```kotlin
footer {
    text {
        value = "Footer"
    }
}

items {
    itemRow { /* ... */ }
    itemRow { /* ... */ }
}
```

A normal container therefore reads:

```kotlin
column {
    text {
        value = "A"
    }

    text {
        value = "B"
    }
}
```

There are no positional component data/content arguments.

The builder lambda configures the component; it is not a positional prop.

## 9. Type-safe builders

Component builders use Kotlin lambdas with receiver.

Conceptually:

```kotlin
@UiDsl
class ButtonBuilder {
    var value: String
    fun value(source: Readable<String>)
    fun value(block: () -> String)
    fun onClick(listener: (ClickEvent) -> Unit)
}

fun UiScope.button(
    block: ButtonBuilder.() -> Unit = {}
): Element
```

All framework builders use a common `@DslMarker` so an inner component builder cannot accidentally mutate an unrelated outer receiver.

## 10. Structural API

`when` is a Kotlin keyword, so the structural helper is:

```kotlin
show(condition) {
    detailsPanel()
}
```

Keyed repeated content:

```kotlin
forEach(
    users,
    key = User::id
) { user ->
    userRow {
        value(user)
    }
}
```

These create/update persistent structural nodes. They do not re-run the owning component `view()`.

## 11. Style DSL

Canonical:

```kotlin
val BUTTON = style {
    height = 20.dp
    background = rgb(32, 32, 36)

    on(HOVER) {
        opacity = 0.9f
    }

    on(DISABLED) {
        opacity = 0.5f
    }
}
```

`on(...)` is the canonical state-variant DSL and avoids the Kotlin `when` keyword conflict.

Composition:

```kotlin
val ROOT = style {
    include(VERTICAL)
    include(SURFACE)

    gap = 8.dp
}
```

`Style` remains immutable after builder completion.

## 12. Theme DSL

```kotlin
val ACCENT =
    styleVar(rgb(90, 120, 255))

val DARK = theme {
    set(ACCENT, rgb(120, 150, 255))

    style(
        Button.Part.ROOT,
        VANILLA_BUTTON
    )
}
```

Reactive token source:

```kotlin
theme {
    set(ACCENT, accentState)
}
```

Reactive expression:

```kotlin
theme {
    set(ACCENT) {
        deriveAccent(mode.value)
    }
}
```

The lambda creates a scope-owned derived value.

## 13. Motion DSL

```kotlin
val FAST = tween {
    duration = 150.ms
    easing = EASE_OUT
}

val SOFT = spring {
    stiffness = 360f
    dampingRatio = 0.82f
}

val INTERACTION = transitions {
    opacity = FAST
    transform = SOFT
}
```

Styles attach transition policy normally:

```kotlin
val BUTTON = style {
    transitions = INTERACTION

    opacity = 1f

    on(HOVER) {
        opacity = 0.9f
        transform = scale(1.03f)
    }
}
```

## 14. Platform environment API

Root environment is readable from component scope:

```kotlin
val env = environment()

text {
    value {
        env.value.locales
            .firstOrNull()
            ?.tag
            ?: ""
    }
}
```

Environment-aware style units are direct typed values:

```kotlin
val CARD = style {
    padding = 12.dp
}

val TITLE = style {
    fontSize = 18.sp
}
```

Platform insets are referenced without snapshotting them into a static style:

```kotlin
block {
    style {
        padding = envInsets(SAFE_DRAWING)
    }
}
```

Environment changes are reactive and do not remount the component tree.

## 15. Semantics and text editing API

Universal controls install default semantics automatically.

Application refinement:

```kotlin
button {
    value = "Apply"

    semantics {
        hint = "Apply current settings"
    }
}
```

Normal text binding remains simple:

```kotlin
val name = state("")

textField {
    bindValue(name)
    placeholder = "Name"
}
```

The text field internally owns cursor, selection, composition, text-input session, and editing commands.

Advanced selection binding is available when needed:

```kotlin
textField {
    bindValue(name)
    bindSelection(selection)
}
```

Raw platform IME/accessibility/autofill types are not part of the component API.

## 16. KSP generation boundary

KSP reads the resolved declaration property types:

```text
Prop<T>
Binding<T>
Event<E>
Slot
SlotList
```

It ignores:

```text
State<T>
Derived<T>
private implementation values
unrelated properties
```

KSP generates normal Kotlin source.

It does not:

```text
rewrite property reads
rewrite arbitrary lambdas
inspect arbitrary expression semantics
modify bytecode
implement dependency tracking
```

All dependency tracking still occurs at runtime through `Readable.value`.
