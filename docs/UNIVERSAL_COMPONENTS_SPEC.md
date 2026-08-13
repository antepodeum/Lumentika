# Universal Core Components Specification

## 1. Purpose

`lumentika-core` ships a minimal universal component vocabulary whose behavior is stable across platform integrations.

Universal components own structure, interaction, accessibility semantics, and style-part contracts.

Platform integrations provide concrete rendering/resources/themes/native services.

## 2. Baseline catalog

```text
block
flex
row
column
grid
stack
scroll
list
text
image
button
checkbox
slider
textField
tooltip
```

## 3. block

Generic box/div-like container.

```kotlin
block {
    style(CARD)

    text {
        value = "Hello"
    }
}
```

`block` adds no layout algorithm. Its box participates in normal Taffy-backed style/layout.

## 4. flex

Generic flex container.

```kotlin
flex {
    direction = ROW
    wrap = WRAP
}
```

Convenience props configure core flex style values projected to Taffy4J.

## 5. row / column

Ergonomic flex compositions:

```kotlin
row { /* children */ }
column { /* children */ }
```

They preserve normal style/layout semantics and introduce no additional solver.

## 6. grid

Generic grid container using core grid values and Taffy4J.

Grid-specific convenience props map directly to the core style catalog.

## 7. stack

Generic overlapping child composition using normal position/stacking behavior.

It does not create a platform overlay/window by itself.

## 8. text

Platform-independent text content component.

```kotlin
text {
    value = "Hello"
}
```

Reactive:

```kotlin
text {
    value {
        "Count: ${count.value}"
    }
}
```

Core owns text value/style/layout semantics.

`TextLayoutService` supplies shaping/measurement/run geometry through the platform boundary.

Default semantics expose readable text unless explicitly hidden/merged by a parent semantic node.

## 9. image

Platform-independent image component using generic `ImageSource`.

Core owns intrinsic/layout/fit/content semantics.

Platform image/resource services own decode/upload/native rendering.

Image semantics support label/description or decorative suppression.

## 10. button

Core behavior:

```text
focusable by default
pointer/tap activation
keyboard activation
disabled state
pressed/active state
semantic CLICK action
click event
activation feedback request
```

Canonical:

```kotlin
button {
    value = "Save"
    onClick { save() }
}
```

Stable parts:

```text
ROOT
LABEL
ICON
```

Default semantic role: `BUTTON`.

A label/icon subtree normally merges into the button semantic node.

## 11. checkbox

Core behavior:

```text
boolean selected value
two-way binding
pointer/tap toggle
keyboard toggle
disabled/focus states
change event
semantic checked state
```

Parts:

```text
ROOT
INDICATOR
LABEL
```

Default semantic role: `CHECKBOX` with `CLICK` action.

## 12. slider

Core behavior:

```text
min/max/value
clamping
pointer drag recognizer
keyboard adjustment
two-way binding
input/change events
semantic range
increment/decrement/set-value semantic actions
```

Parts:

```text
ROOT
TRACK
THUMB
LABEL
```

Slider drag participates in the shared `GestureArena`, allowing deterministic arbitration with ancestor scroll containers.

## 13. textField

Core owns the complete platform-independent editor behavior:

```text
String value/binding
TextEditingController
cursor/selection/composition
keyboard editing
pointer caret/selection behavior
TextInputService session
clipboard actions
receive-content handling
autofill metadata
caret auto-scroll
editing/accessibility semantics
```

Normal API:

```kotlin
val name = state("")

textField {
    bindValue(name)
    placeholder = "Name"
}
```

Parts:

```text
ROOT
TEXT
PLACEHOLDER
CURSOR
SELECTION
SCROLLBAR_TRACK
SCROLLBAR_THUMB
```

The raw platform IME never mutates component internals directly; it emits `TextEditCommand`s through `TextInputService`.

## 14. scroll

Core scroll behavior:

```text
viewport
scroll offsets/ranges
wheel scrolling
touch/pen drag scrolling
nested pre/local/post consumption
fling
overscroll state
scroll chaining
scrollbar behavior
semantic scroll actions
```

Scroll offset remains render/runtime state rather than Taffy geometry.

Parts include platform-themeable scrollbar track/thumb where exposed.

## 15. list

Universal repeated-content helper built from:

```text
scroll
keyed forEach
collection semantics
```

It does not define a separate reconciliation mechanism.

A list can expose row count and item metadata to the semantic tree.


## 16. tooltip

Universal anchored top-layer behavior.

Core owns:

```text
anchor relationship
show/hide timing state
placement request
top-layer lifetime
semantic description relationship where appropriate
```

Platform themes/resources provide appearance.

Platform-specific payload adapters may produce tooltip content.

## 17. Units/environment

Universal components can consume environment-aware lengths:

```kotlin
column {
    style {
        gap = 8.dp
        padding = 12.dp
    }
}

text {
    style {
        fontSize = 14.sp
    }
}
```

Environment changes resolve through the normal style/layout invalidation path.

## 18. Insets

Universal components do not silently consume platform insets.

Higher-level application layouts may explicitly use:

```kotlin
block {
    style {
        padding = envInsets(SAFE_DRAWING)
    }
}
```


## 19. Style and theme

Universal components expose typed stable `StylePart`s.

A platform theme styles them without changing their behavior.

```kotlin
val VANILLA = theme {
    style(
        Button.Part.ROOT,
        VANILLA_BUTTON_ROOT
    )
}
```

No platform-native resource type is stored in the universal component implementation.

## 20. Platform feedback

Components emit semantic feedback requests rather than calling native sound/haptic APIs.

Examples:

```text
button activation -> PRESS/CONFIRM policy
checkbox toggle -> TOGGLE
slider step -> SELECTION_CHANGE
scroll limit -> SCROLL_LIMIT
```

Platform services choose actual feedback behavior.

## 21. Accessibility semantics

Every interactive universal component installs default core semantics.

Applications can refine semantics through normal typed semantic APIs.

Visual child structure may merge into one semantic control node.

The platform accessibility adapter consumes the committed `SemanticsArtifact`.

## 22. Platform extension components

Platform libraries can declare additional components using the same component runtime.

native platform examples:

```text
item
entity
menuSlot
vanillaWidget
```

Platform-specific data/behavior remains outside the universal catalog.

## 23. Default appearance

Universal behavior does not depend on a platform theme.

A headless/reference renderer may use minimal deterministic styles for tests.

Normal platform applications supply a platform theme.

## 24. Tests

Every universal interactive component requires tests for:

```text
keyboard behavior
pointer/gesture behavior
focus
bindings/events
disabled state
StyleParts
semantics/actions
feedback requests
scope disposal
```

Text/scroll controls additionally require their subsystem-specific editing/gesture/nested-scroll tests.

## 25. Acceptance criteria

- universal behavior is implemented once in core;
- all layout containers use Taffy4J through core style projection;
- interactive controls use shared event/focus/gesture runtimes;
- textField uses the shared text-editing/input contract;
- interactive controls install platform-independent semantics;
- platform visuals/resources/services are injected externally;
- platform-specific components compose with universal components without runtime forks.
