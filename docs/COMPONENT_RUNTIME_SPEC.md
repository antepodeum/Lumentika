# Component Runtime Specification

This document defines component ownership, declaration semantics, KSP facade generation, structural composition, context, and component lifecycle.

Layout, styling, rendering, events/focus, and platform integration remain owned by their subsystem specifications. `Component` itself is platform-neutral and lives in `lumentika-core`.

## 1. Component model

A `Component` is a stateful/compositional boundary.

It owns:

```text
ComponentScope
Props / Bindings / Events / Slots
internal state
effects and async work
cleanup
the subtree returned by view()
```

`view()` executes once per mount.

State changes do not rerun the whole function.

Fine-grained bindings update existing runtime objects.

Structural helpers mount/unmount/reconcile only their local branches.

Canonical declaration:

```kotlin
@UIComponent
class VolumeControl : Component() {
    val title = prop("Volume")
    val min = prop(0.0)
    val max = prop(100.0)

    val value = binding(50.0)

    val expanded = state(false)

    val change = event<ChangeEvent>()

    val footer = slot()

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

        text {
            value {
                "${value.value.toInt()}%"
            }
        }

        footer()
    }
}
```

## 2. Persistent runtime identity

A component mount owns one persistent component instance and one persistent logical subtree until structural operations explicitly replace local branches.

Reactive writes do not imply virtual-tree rebuild/reconciliation.


## 3. Kotlin DSL construction

Generated components have no positional prop/content arguments.

Canonical:

```kotlin
button {
    value = "Save"
}
```

Reactive value:

```kotlin
button {
    value {
        "Clicks: ${count.value}"
    }
}
```

Container:

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

The trailing builder lambda configures the generated component DSL. It is not positional component data.

An empty component may be created as:

```kotlin
button {}
```

All configurable data comes from declaration properties.

## 4. `@UIComponent`

Only the component class is annotated:

```kotlin
@UIComponent
class SliderField : Component() {
    val label = prop("Value")
    val value = binding(50.0)
    val change = event<ChangeEvent>()
    val footer = slot()
}
```

There are no field/property annotations such as:

```text
@Prop
@Bindable
@Event
@Slot
```

The resolved declaration property type is the source of truth.

## 5. Declaration types

```text
Prop<T>       externally configurable one-way reactive input
Binding<T>    externally configurable input with optional two-way binding
Event<E>      externally subscribable semantic occurrence
Slot          externally supplied single content insertion point
SlotList      externally supplied repeated content insertion point
State<T>      internal mutable reactive state only
```

`Prop<T>` implements `Readable<T>`.

`Binding<T>` implements `Mutable<T>`.

`State<T>` implements `Mutable<T>` but does not participate in generated external API.

## 6. `Prop<T>`

Declaration:

```kotlin
val title = prop("Volume")
val requiredTitle = prop<String>()
```

Internal read:

```kotlin
title.value
```

Internal propagation:

```kotlin
text {
    value(title)
}
```

Generated external forms:

```kotlin
volumeControl {
    title = "Music"
}
```

```kotlin
volumeControl {
    title(titleReadable)
}
```

```kotlin
volumeControl {
    title {
        computeTitle()
    }
}
```

Semantics:

- assignment is a constant one-way source;
- `Readable<T>` preserves one-way reactivity;
- lambda becomes a component-scope derived source;
- changing the source updates the same declaration object;
- `view()` is not rerun.

## 7. `Binding<T>`

Declaration:

```kotlin
val value = binding(50.0)
val requiredValue = binding<Double>()
```

Internal use:

```kotlin
value.value = 42.0

slider {
    bindValue(value)
}
```

Generated forms:

```kotlin
slider {
    value = 42.0
}
```

```kotlin
slider {
    value(readableValue)
}
```

```kotlin
slider {
    value {
        computedValue()
    }
}
```

```kotlin
slider {
    bindValue(mutableValue)
}
```

Only `bindValue(...)` creates two-way synchronization.

The one-way forms and two-way form are mutually exclusive.

With no external `Mutable<T>`, the declaration owns its local mutable value.

With external binding:

```text
external mutable write
→ declaration
→ component consumers

component declaration write
→ external mutable
```

## 8. `State<T>`

Internal state:

```kotlin
val open = state(false)
```

It generates no external component API.

```text
Binding<T> = component contract + mutable runtime value
State<T>   = internal runtime value only
```

## 9. `Event<E>`

Declaration:

```kotlin
val change = event<ChangeEvent>()
```

Generated external API:

```kotlin
volumeControl {
    onChange { event ->
        handle(event)
    }
}
```

Emission:

```kotlin
change.emit(event)
```

Semantic component events are non-bubbling by default.

Opt-in bubbling:

```kotlin
val select =
    event<SelectEvent>().bubbles()
```

Bindings synchronize state.

Events report occurrences.

They remain distinct abstractions.

## 10. `Slot` and `SlotList`

Declaration:

```kotlin
val footer = slot()
val items = slotList()
```

Generated consumer DSL:

```kotlin
volumeControl {
    footer {
        text {
            value = "Footer"
        }
    }

    items {
        itemRow { /* ... */ }
        itemRow { /* ... */ }
    }
}
```

Inside the component, a slot is inserted structurally:

```kotlin
override fun view() = column {
    mainContent()
    footer()
}
```

A slot is identified by declaration property identity/name during KSP generation; runtime does not use string lookup.

If a deliberately different external API name is ever required, it must be configured on the declaration value rather than duplicated as parallel annotation metadata.

## 11. Required/default declarations

```kotlin
val title = prop("Default")
val value = binding(50.0)
```

have defaults.

```kotlin
val title = prop<String>()
val value = binding<Double>()
```

are required.

Missing required values fail during component configuration before `view()`.

## 12. KSP facade generation

KSP scans an `@UIComponent` class and resolves declaration property types.

Conceptually:

```text
Prop<T>
    -> var x: T
    -> fun x(Readable<T>)
    -> fun x(() -> T)

Binding<T>
    -> var x: T
    -> fun x(Readable<T>)
    -> fun x(() -> T)
    -> fun bindX(Mutable<T>)

Event<E>
    -> fun onX((E) -> Unit)

Slot
    -> fun x(UiScope.() -> Unit)

SlotList
    -> fun x(UiScope.() -> Unit)

State<T>
    -> nothing

other property
    -> nothing
```

KSP generates ordinary Kotlin source.

It does not rewrite:

```text
property access
arbitrary expressions
lambda bodies
AST semantics
bytecode
```

Reactive tracking remains runtime behavior.

All builders use the common framework `@DslMarker`.

## 13. Builder ownership

The generated builder exists only during construction/configuration.

The runtime stores the configured declaration sources and persistent component instance, not the builder.

Builder configuration completes before `view()` executes.

A builder cannot escape as runtime component state.

## 14. Component scope

Every component mount owns a `ComponentScope`.

The scope owns:

```text
effects
derived sources created for prop lambdas
async jobs
binding synchronization
event listener registrations
slot associations
cleanup callbacks
```

Disposal removes all owned resources exactly once.

## 15. Structural reactivity

Conditional branch:

```kotlin
show(expanded) {
    detailsPanel()
}
```

When false, the branch is absent.

When true, the factory is invoked to mount the branch.

A later false transition disposes that branch and its scopes.

The owning component `view()` does not rerun.

Keyed repetition:

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

Rules:

- keys are unique within the local repeated region;
- matching keys preserve element/component identity;
- insert/remove/move updates only the repeated region;
- removed entries dispose their scopes;
- duplicate keys fail with a diagnostic;
- reconciliation is local, not whole-tree.

## 16. Context

Typed context remains hierarchical.

```kotlin
val THEME_CONTEXT =
    contextKey<Readable<Theme>>()
```

Provider:

```kotlin
provide(
    THEME_CONTEXT,
    themeState
) {
    child()
}
```

Read:

```kotlin
val theme =
    context(THEME_CONTEXT).value
```

Logical ancestry, not render/top-layer projection, determines context lookup.

Component boundaries do not block context.

## 17. Effects and `afterLayout`

Normal effect:

```kotlin
effect {
    soundEngine.volume =
        volume.value
}
```

Layout-dependent work:

```kotlin
afterLayout {
    val bounds = element.geometry
    // external side effect
}
```

`afterLayout` runs after committed Taffy layout and before PrePaint according to the root scheduler.

Writes from effects/after-layout callbacks schedule a later appropriate wave rather than recursively flushing subsystems.

## 18. Async component state

```kotlin
val inventory = derivedAsync {
    server.loadInventory(
        playerId.value,
        page.value
    )
}
```

The async job is component-scope owned.

A dependency change cancels/invalidates the previous generation.

Stale completion cannot publish.

The last successful value remains readable during refresh by default.

## 19. Scheduler participation

ComponentRuntime participates in the root scheduler through:

```text
reactive writes
→ demanded derived recomputation
→ reactive component inputs/bindings
→ structural reconciliation
→ normal effects
→ downstream semantic/style/layout/render staging
```

`INTEGRATION_PROOF_SPEC.md` is the sole authority for the complete root scheduler.

No subsystem recursively calls another subsystem flush.

## 20. Theme interaction

Theme resolution belongs to the style runtime and follows logical element ancestry.

Components do not thread themes through props.

Component unmount disposes theme-scope reactive subscriptions owned under its subtree.

`StyleVar<T>` tokens themselves require no disposal.

## 21. Architectural invariants

- `view()` is one-shot per mount;
- component builder lambdas are configuration, not reactive rebuild functions;
- fine-grained bindings update persistent runtime objects;
- structural helpers own local mounting/reconciliation;
- KSP generates API only;
- reactive semantics are runtime semantics;
- declaration property types are the sole facade-generation source;
- `State<T>` never becomes external API automatically;
- only `bindX(Mutable<T>)` is two-way;
- one-way and two-way sources for one `Binding<T>` conflict;
- scope disposal is deterministic;
- component events and bindings remain distinct;
- context/theme follow logical ancestry;
- no positional component prop/content arguments exist.

## 22. Canonical minimal component

```kotlin
@UIComponent
class Counter : Component() {
    val count = state(0)

    override fun view() = button {
        value {
            "Clicks: ${count.value}"
        }

        onClick {
            count.value++
        }
    }
}
```

`button` and the `Component` runtime both live in `lumentika-core`; the button owns universal interaction semantics while platform modules provide its concrete visual theme/rendering.

## Integration proof authority

`INTEGRATION_PROOF_SPEC.md` verifies one-shot `view()`, generated DSL propagation, structural identity, bindings, async cancellation, scheduler ordering, and disposal.
