# Reactive Runtime Specification

## Goals

The public API is Kotlin-first and provides fine-grained reactive ergonomics without requiring a Kotlin compiler plugin.

KSP may generate type-safe component DSL facades, but the reactive graph is an ordinary Kotlin/JVM runtime.

Canonical syntax:

```kotlin
val value = state(2)
val doubled = derived {
    value.value * 2
}

text {
    value(doubled)
}

slider {
    bindValue(value)
}
```

Dependencies are discovered from `.value` reads performed while a derived computation, effect, reactive prop lambda, or reactive theme lambda is active.

## Core type hierarchy

```kotlin
interface Readable<out T> {
    val value: T
}

interface Mutable<T> : Readable<T> {
    override var value: T

    fun update(update: (T) -> T) {
        value = update(value)
    }
}

interface State<T> : Mutable<T>
interface Derived<out T> : Readable<T>
```

`State<T>` is writable.

`Derived<T>` is read-only and recomputes according to scheduler demand while preserving dynamic dependency tracking.

## State

```kotlin
val count = state(0)

count.value = 1
count.update { it + 1 }
count.value++
```

Required semantics:

- equality suppression by configurable/default equality policy;
- writes invalidate only actual dependents;
- nested writes are scheduler-safe;
- writes inside `batch` defer downstream flushes;
- reads inside a tracking context register dependencies;
- reads inside `untracked` do not.

State objects have stable identity and are passed directly to two-way bindings.

Delegated-property syntax is not canonical because it hides the mutable handle needed by bindings.

## Derived

```kotlin
val doubled = derived {
    count.value * 2
}

val label = derived {
    "Count: ${doubled.value}"
}
```

Required semantics:

- automatic dependency discovery;
- dynamic dependency replacement after every recomputation;
- no manual subscription API required by consumers;
- cycles fail with a clear diagnostic;
- downstream consumers invalidate only when the output changes semantically.

## Async derived

Async loading is a derived capability, not a separate fundamental resource primitive.

```kotlin
val inventory = derivedAsync {
    server.loadInventory(
        playerId.value,
        page.value
    )
}
```

The lambda is `suspend`.

`derivedAsync` tracks reactive reads made before and during supported tracked suspension boundaries according to the runtime implementation contract. Dependency changes start a new generation.

Required semantics:

- every evaluation receives a monotonically increasing generation;
- stale completions cannot overwrite newer results;
- the previous coroutine/job is cancelled when possible;
- disposal cancels/invalidates the active generation;
- state exposes `pending`, `hasValue`, `value`, and `error`;
- last success remains readable during refresh;
- first load starts with `pending == true` and `hasValue == false`;
- a failed refresh exposes its error while retaining generation safety.

Conceptual contract:

```kotlin
interface AsyncDerived<out T> : Readable<T> {
    val pending: Boolean
    val hasValue: Boolean
    override val value: T
    val error: Throwable?
}
```

## Effects

```kotlin
effect {
    audio.volume = volume.value
}
```

Effects are for external side-effect boundaries.

Pure dataflow belongs in derived values, bindings, and reactive props.

Cleanup:

```kotlin
effect {
    val subscription =
        server.subscribe(channel.value)

    cleanup(subscription::close)
}
```

Required semantics:

- dependency discovery matches derived;
- cleanup runs before rerun;
- cleanup runs on scope disposal;
- effects are scheduled after graph invalidation rather than recursively inside state assignment;
- self-invalidating effects cannot create uncontrolled reentrant recursion.

## Batch

```kotlin
batch {
    first.value = "A"
    second.value = "B"
    third.value = "C"
}
```

Writes happen immediately while downstream flushes coalesce until the outermost batch exits.

Nested batches are supported.

Exceptions cannot poison scheduler batch depth.

## Untracked

```kotlin
effect {
    val id = selected.value

    val debug = untracked {
        debugState.value
    }

    logger.debug("{} {}", id, debug)
}
```

`debugState` does not become an effect dependency.

## Component scope

Every mounted component owns a `ComponentScope`.

It owns:

- effects;
- implicit derived computations created by reactive prop/theme lambdas;
- async jobs;
- cleanup callbacks;
- generated prop bindings;
- generated event registrations belonging to the component.

Disposal is deterministic and idempotent.

Canonical cleanup:

```kotlin
onCleanup(resource::close)
```

`afterLayout { ... }` belongs to the UI scheduler and is ordered by the component/layout specifications.

## Reactive component declarations

The same graph powers component declarations:

```text
Prop<T>       : Readable<T>
Binding<T>    : Mutable<T>
State<T>      : Mutable<T>
```

There is no compiler-only reactive field semantics.

### Props

```kotlin
val max = prop(100.0)
```

Internal use:

```kotlin
max.value

slider {
    max(max)
}
```

Generated consumer forms:

```kotlin
slider {
    max = 10.0
}
```

```kotlin
slider {
    max(maxState)
}
```

```kotlin
slider {
    max {
        state.value * 2
    }
}
```

The lambda overload becomes a component-scope derived readable.

### Bindings

```kotlin
val value = binding(50.0)
```

Internal use:

```kotlin
value.value = 75.0

slider {
    bindValue(value)
}
```

Generated consumer forms:

```kotlin
slider {
    value = 10.0
}
```

```kotlin
slider {
    value(valueReadable)
}
```

```kotlin
slider {
    value {
        deriveValue()
    }
}
```

```kotlin
slider {
    bindValue(externalMutable)
}
```

The first three forms are one-way.

Only `bindValue(...)` is two-way.

One-way and two-way configuration are mutually exclusive for a declaration.

### Events

```kotlin
val input = event<SliderInputEvent>()
```

Generated API:

```kotlin
slider {
    onInput { event ->
        println(event.value)
    }
}
```

Emission:

```kotlin
input.emit(event)
```

Events are occurrence channels, not mutable state.

### Slots

```kotlin
val actions = slot()
val items = slotList()
```

KSP generates nested DSL entry points from property names.

Slot content participates in normal component/element ownership.

## Structural helpers

`when` is reserved by Kotlin, so the conditional structural helper is:

```kotlin
show(expanded) {
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

Structural helpers update persistent structural nodes and never implement reactivity by rebuilding the entire component.

## KSP boundary

`@UIComponent` marks a component class for API generation.

KSP reads declaration property types:

```text
Prop<T>
Binding<T>
Event<E>
Slot
SlotList
```

It ignores `State<T>` and unrelated implementation properties.

KSP generates ordinary Kotlin builder/factory APIs.

It is not a reactive compiler and does not rewrite arbitrary Kotlin expressions.

Dependency tracking comes from runtime `.value` reads.

See `KOTLIN_API_SPEC.md` for the canonical generated surface.

## Non-goals

This document does not define:

- style property catalog;
- transitions;
- Taffy bindings;
- rendering;
- hit testing;
- focus/event propagation;
- platform host/render-backend integration.

## Reactive theme values

`StyleVar<T>` is an immutable typed identity token with a default.

A theme override source can be:

```text
constant T
Readable<T>
() -> T   -> scoped Derived<T>
```

Example:

```kotlin
val accent =
    state(rgb(120, 150, 255))

val theme = theme {
    set(ACCENT, accent)
}
```

Changing `accent.value` invalidates only consumers that currently resolve `ACCENT` through the affected theme lookup path.

A `Readable<Theme>` is used only when the mapping object itself changes reactively.

## Generated prop lambda reactivity

Every generated `x { ... }` prop lambda is reactive.

Example:

```kotlin
text {
    value {
        prefix.value +
            count.value +
            suffix.value
    }
}
```

The component/input scope adapts the lambda to a derived readable and disposes it with the owning scope.

For `Binding<T>`, this lambda form remains one-way.

Two-way propagation exists only through generated `bindX(Mutable<T>)`.

## Integration proof authority

`INTEGRATION_PROOF_SPEC.md` verifies scheduling, batching, reentrancy, bindings, structural updates, async cancellation, and disposal in the complete runtime.
