# Event, Input, and Focus Specification

## 1. Event pipeline

Every normalized user input event follows:

```text
platform normalization
→ hit test
→ capture
→ target
→ bubble
→ default action
```

The logical `Element` tree defines propagation ancestry.

Top-layer render projection does not create a second event tree.

## 2. Base event contract

```kotlin
interface UIEvent {
    val target: Element
    val currentTarget: Element
    val phase: EventPhase
    val defaultPrevented: Boolean

    fun stopPropagation()
    fun stopImmediatePropagation()
    fun preventDefault()
}
```

`target` remains stable for one dispatch.

`currentTarget` changes while traversing the path.

## 3. Listener API

Conceptually:

```kotlin
element.onPointerDown { event ->
    // target/bubble listener
}

element.onPointerDownCapture { event ->
    // capture listener
}
```

Generated component semantic events use typed `onX { ... }` APIs and remain distinct from low-level pointer listeners.

## 4. Propagation

Capture:

```text
root
→ descendants
→ target parent
```

Target:

```text
target capture listeners
→ target bubble listeners
```

Bubble:

```text
target parent
→ ancestors
→ root
```

`stopPropagation()` stops further element traversal.

`stopImmediatePropagation()` also stops later listeners on the current target.

`preventDefault()` suppresses a cancelable default action.

## 5. Pointer model

Normalized pointer events:

```text
pointerDown
pointerMove
pointerUp
pointerEnter
pointerLeave
pointerCancel
wheel
```

Pointer types:

```kotlin
enum class PointerType {
    MOUSE,
    TOUCH,
    PEN,
    UNKNOWN
}
```

Conceptual event:

```kotlin
interface PointerEvent : UIEvent {
    val pointerId: Int
    val pointerType: PointerType
    val x: Double
    val y: Double
    val button: Int
    val buttons: Int
    val pressure: Float?
    val timestampNanos: Long
    val modifiers: KeyModifiers
}
```

Coordinates are root logical coordinates.

Target-local coordinates use the shared render transform tree.

## 6. Coalesced/historical pointer samples

A platform may provide historical/coalesced pointer samples.

They are exposed as optional timestamped samples on the normalized event and are primarily consumed by gesture/velocity tracking and drawing applications.

Core correctness never requires their presence.

## 7. Hit testing

Hit testing uses the committed `HitTestArtifact`:

```kotlin
fun hitTest(
    x: Double,
    y: Double
): Element?
```

It uses:

```text
reverse paint order
render transforms
render clips
inverse local transform
hit-test participation style
custom HitRegionSource when present
```

There is no separate input geometry solver.

## 8. Pointer capture

```kotlin
element.setPointerCapture(pointerId)
element.releasePointerCapture(pointerId)
```

After normal hit testing determines actual hover path, capture can override delivery target for eligible pointer events.

Hover continues to use the real hit path.

Capture ends on explicit release, cancellation/end according to policy, owner unmount, or root disposal.

## 9. Hover and active states

Core derives built-in style states:

```text
HOVER
ACTIVE
```

Hover uses current hit path.

Active/pressed state is owned by control/default-action semantics and may be driven by the gesture runtime.

## 10. Gesture boundary

Low-level pointer events are the input to `GestureRuntime`.

Core gesture recognizers provide:

```text
tap / double tap / long press
drag / pan / scale
gesture arbitration
velocity tracking
```

Universal components use this shared runtime.

Exact rules are defined in `GESTURE_SCROLL_SPEC.md`.

## 11. Wheel/scroll input

Platform wheel/trackpad input is normalized into logical scroll deltas before dispatch.

Scroll default action enters the core nested-scroll protocol.

Wheel input does not emulate touch dragging.

## 12. Keyboard model

The root accepts normalized:

```text
key down
key up
key repeat information
logical key
physical key/scancode identity where available
modifiers
```

Keyboard dispatch targets the current input-focused element/component.

Raw native key codes stay in the platform adapter.

## 13. Text input boundary

Text composition/IME operations are not represented as ordinary character key events.

Focused editors use `TextInputService` and typed `TextEditCommand`s defined in `TEXT_EDITING_INPUT_SPEC.md`.

Simple platforms may additionally emit direct text insertion commands from keyboard character events.

## 14. Focus manager

Each `UiRoot` owns one input `FocusManager`.

```kotlin
val activeElement: Element?

fun focus(element: Element)
fun blur(element: Element)
fun focusNext()
fun focusPrevious()
```

Focusable participation is component/runtime metadata.

## 15. Focus events

Non-bubbling:

```text
focus
blur
```

Bubbling:

```text
focusin
focusout
```

One focus change is atomic and has deterministic event order.

## 16. Focus style states

Built-in:

```text
FOCUS
FOCUS_VISIBLE
FOCUS_WITHIN
```

`FOCUS_VISIBLE` follows core input-modality heuristics informed by normalized platform input.

`FOCUS_WITHIN` follows logical ancestry.

Accessibility focus is a distinct semantics concept and does not automatically produce these style states.

## 17. Disabled state

A disabled interactive control:

```text
suppresses its normal activation default action
is skipped by normal focus traversal unless explicitly configured
can remain hit-testable for tooltip/inspection policy
publishes disabled semantics
```

Visual disabled appearance is theme/style behavior.

## 18. Focus traversal

Traversal uses logical order plus `tabIndex`.

```text
tabIndex < 0
→ programmatic focus only by default

tabIndex == 0/default
→ normal logical traversal

tabIndex > 0
→ explicit priority order
```

Tie-breaking is deterministic.

Directional navigation may use spatial geometry when a component/platform policy enables it, but input focus remains owned by the same manager.

## 19. Focus repair

Before a focused element becomes stale because of unmount, branch removal, or focus-participation change:

```text
compute repair target/clear
emit required focus events
close active text-input session if affected
then dispose subtree
```

A stale element never receives future input.

## 20. Structural removal

Before subtree disposal:

```text
cancel owned gesture recognizers
release pointer capture
repair input focus
close text-input session
clear hover/active state
emit required pointer cancellation/leave events
```

Then scope/style/layout/render/semantics associations are removed.

## 21. Semantic component events

Declaration:

```kotlin
val change =
    event<ChangeEvent>()

val select =
    event<SelectEvent>().bubbles()
```

Semantic component events are non-bubbling by default.

Opt-in bubbling uses the same logical propagation path.

They are distinct from accessibility semantic actions in `SEMANTICS_ACCESSIBILITY_SPEC.md`.

## 22. Default actions

Examples:

```text
button tap/keyboard activation → click callback + semantics/feedback
checkbox activation → toggle binding
slider drag/key → update value
wheel/touch scroll → nested scroll
textField key/edit command → editing controller
semantic accessibility CLICK → same control activation path
platform-specific control action → platform adapter/default action
```

Default actions run after event propagation unless prevented.

State writes schedule normal reactive work.

## 23. Back navigation

Platform back behavior enters core through `BackDispatcher`, not as a synthetic raw key.

It can represent:

```text
start
progress
cancel
commit
```

A platform that only supports a discrete back action emits commit.

Back handlers are scope-owned.

## 24. Custom scenes

A custom content leaf can expose local hit/raycast behavior:

```text
framework hit finds scene leaf
→ root point transformed to local
→ local scene hit/raycast
→ semantic scene event through normal framework ancestry
```

Scene objects are not core `Element`s.

## 25. Platform ingress

Native platform callbacks are only ingress.

A platform adapter converts native pointer/keyboard/scroll/focus/back events into core types.

Native widget adapters receive translated core events exactly once and never establish a parallel event/focus tree.

## 26. Tests

Required:

```text
capture/target/bubble order
stopPropagation
stopImmediatePropagation
preventDefault
pointer enter/leave
pointer capture
capture release on unmount
hover during capture
historical sample passthrough
keyboard focus dispatch
focus/blur order
focusin/focusout bubbling
focus-visible modality
focus-within ancestry
accessibility focus remains separate
tab traversal
focus repair
text-input session closes on focus removal
disabled default-action suppression
back dispatcher lifecycle
custom scene local hit
semantic event bubbling rules
```

Gesture/nested-scroll tests live in `GESTURE_SCROLL_SPEC.md`.

## 27. Invariants

- one event tree per root;
- one input focus manager per root;
- hit testing uses committed render state;
- pointer capture never redefines hover;
- gesture recognition does not create a second event pipeline;
- IME composition does not masquerade as raw key input;
- accessibility focus remains separate from input focus;
- stale elements never receive input;
- native platform callbacks do not directly mutate universal component internals.
