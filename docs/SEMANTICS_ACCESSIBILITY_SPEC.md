# Semantics and Accessibility Specification

## 1. Purpose

`lumentika-core` owns a platform-independent semantic representation of the interactive UI.

The semantic tree is consumed by platform accessibility adapters, narration systems, testing tools, and other semantic/non-visual services. Autofill uses its own artifact and stable field identity as defined by `TEXT_EDITING_INPUT_SPEC.md`.

Visual `Element` structure and semantic structure are related but not identical: a component may merge, hide, or synthesize semantic nodes without changing layout or paint structure.

## 2. Root ownership

Each `UiRoot` owns one `SemanticsRuntime` and one committed `SemanticsArtifact`.

```text
Element/component state
→ semantic configuration
→ semantic tree resolution
→ transformed/clipped bounds attachment
→ SemanticsArtifact
→ platform accessibility adapter
```

The platform adapter never traverses private component implementation objects directly.

## 3. Semantic node identity

Every committed semantic node has stable root-local identity while its owning semantic element remains mounted.

```kotlin
data class SemanticsNodeId(
    val value: Long
)
```

Identity is not derived from labels, strings, tree indices, or screen coordinates.

Reordering an existing keyed component preserves semantic identity.

Unmount destroys its semantic identity.

## 4. Semantic configuration

Conceptual configuration:

```kotlin
data class SemanticsConfiguration(
    val role: SemanticRole? = null,
    val label: String? = null,
    val value: String? = null,
    val stateDescription: String? = null,
    val hint: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean? = null,
    val checked: ToggleState? = null,
    val expanded: Boolean? = null,
    val readOnly: Boolean = false,
    val password: Boolean = false,
    val heading: Boolean = false,
    val liveRegion: LiveRegionMode = LiveRegionMode.NONE,
    val range: SemanticRange? = null,
    val textSelection: TextRange? = null,
    val mergeDescendants: Boolean = false,
    val clearDescendants: Boolean = false,
    val hidden: Boolean = false
)
```

Concrete generated storage may use compact typed fields rather than a generic map.

## 5. Roles

Baseline roles:

```text
BUTTON
CHECKBOX
RADIO
SWITCH
SLIDER
TEXT
TEXT_FIELD
IMAGE
LINK
LIST
LIST_ITEM
GRID
GRID_CELL
TAB
TAB_LIST
MENU
MENU_ITEM
DIALOG
TOOLTIP
PROGRESS
SCROLL_CONTAINER
GENERIC
```

A role describes semantic purpose, not platform class identity.

Platform adapters map roles to the nearest native accessibility representation.

## 6. Actions

Semantic actions are typed operations owned by core components.

Baseline actions:

```text
CLICK
LONG_CLICK
FOCUS
CLEAR_FOCUS
SET_VALUE
SET_TEXT
SET_SELECTION
COPY
CUT
PASTE
INCREMENT
DECREMENT
SCROLL_BY
SCROLL_FORWARD
SCROLL_BACKWARD
SCROLL_TO_INDEX
EXPAND
COLLAPSE
DISMISS
```

Actions may carry typed arguments.

Conceptual API:

```kotlin
semantics {
    role = SemanticRole.BUTTON
    label = "Save"

    on(SemanticAction.CLICK) {
        activate()
        true
    }
}
```

Normal universal components install their default semantics automatically.

## 7. Component defaults

### button

```text
role = BUTTON
label from label/content semantics
CLICK action
FOCUS action when focusable
enabled/disabled state
```

### checkbox

```text
role = CHECKBOX
checked state
CLICK action
```

### slider

```text
role = SLIDER
range min/max/current
INCREMENT / DECREMENT / SET_VALUE actions
```

### textField

```text
role = TEXT_FIELD
editable/readOnly/password state
current text value according to privacy policy
selection
SET_TEXT / SET_SELECTION / COPY / CUT / PASTE actions as permitted
```

### scroll/list

```text
scroll-container/list roles
scroll actions
collection metadata where applicable
```

## 8. Merge and clear rules

A component can expose one semantic control while rendering many internal elements.

```kotlin
semantics {
    mergeDescendants = true
}
```

Example: a button containing icon + text normally appears as one semantic button.

A component can remove private implementation descendants from the semantic tree:

```kotlin
semantics {
    clearDescendants = true
}
```

Merging never changes Element/event/layout ancestry.

## 9. Bounds

Semantic bounds come from committed render geometry, not raw Taffy boxes alone.

They account for:

```text
layout translation
render transforms
scroll transforms
clips
visibility
```

A semantic node may expose both local bounds and root-space bounds internally.

The committed platform-facing bounds use the same transform chain as hit testing.

## 10. Visibility and clipping

A semantic node is excluded from normal platform exposure when:

```text
semantic hidden = true
subtree is not mounted
visibility suppresses participation
subtree is fully removed from platform-visible UI
```

Clipping affects reported visible bounds where the platform requires it.

Opacity alone does not automatically remove semantics.

## 11. Input focus and accessibility focus

Input focus and accessibility focus are separate concepts.

`FocusManager` owns keyboard/input focus.

`SemanticsRuntime` tracks platform accessibility focus when a platform exposes that concept.

```text
input focus          != accessibility focus
```

A platform accessibility action may request input focus through the normal `FocusManager`.

Accessibility focus does not silently become `FOCUS` style state.

## 12. Live regions and announcements

Core semantics can mark content as:

```text
NONE
POLITE
ASSERTIVE
```

A platform adapter decides how to surface the change.

Core also exposes an explicit semantic announcement boundary for application-critical transient messages:

```kotlin
semantics.announce(
    "Saved",
    priority = POLITE
)
```

The platform adapter owns concrete narration/accessibility APIs.

## 13. Collection metadata

Lists and grids may expose:

```kotlin
data class CollectionInfo(
    val rowCount: Int?,
    val columnCount: Int?,
    val hierarchical: Boolean = false
)

data class CollectionItemInfo(
    val rowIndex: Int?,
    val columnIndex: Int?,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
    val selected: Boolean? = null
)
```

Unknown/unbounded counts remain nullable rather than fabricated.

## 14. Range semantics

```kotlin
data class SemanticRange(
    val value: Double,
    val min: Double,
    val max: Double,
    val step: Double? = null
)
```

Slider/progress controls publish range information through this model.

## 15. Semantics invalidation

Semantic changes use their own dirty tracking.

Examples:

```text
button label change
→ SEMANTICS

slider value change
→ SEMANTICS

background color change
→ no semantics work

transform change
→ semantic geometry update after PrePaint
```

Semantics invalidation does not imply layout or paint.

## 16. Scheduler integration

Canonical relevant ordering:

```text
reactive/component work
→ semantic configuration updates
→ style/animation/layout
→ PrePaint property-tree update
→ commit semantic geometry/tree changes
→ repaint if required
→ platform replay/accessibility publication
```

Semantic properties can be resolved before layout, but platform-visible bounds are committed only from final frame geometry.

## 17. Platform adapter contract

Conceptually:

```kotlin
interface AccessibilityAdapter {
    fun onSemanticsCommitted(
        artifact: SemanticsArtifact,
        changes: SemanticsChangeSet
    )

    fun performAction(
        node: SemanticsNodeId,
        action: SemanticActionRequest
    ): Boolean
}
```

The platform may query nodes lazily instead of receiving a full push snapshot; the semantic source of truth remains core.

## 20. Testing

Required headless tests:

```text
button default semantics
checkbox checked state
slider range/actions
textField editing semantics
merge descendants
clear descendants
semantic identity under keyed reorder
semantic disposal
input focus vs accessibility focus
transform updates semantic bounds
clip updates visible bounds
paint-only change causes no semantic work
semantic action routes into component default action
live-region change generation
```

Platform contract tests:

```text
query stable node tree
action dispatch
focus query
bounds after transform/scroll
incremental semantic changes
```

## 21. Invariants

- semantic structure is core-owned;
- platform accessibility APIs never become component semantics;
- semantic and visual trees may differ without changing logical Element identity;
- accessibility focus and input focus remain distinct;
- semantic actions route through normal component/runtime behavior;
- semantic geometry derives from committed render geometry;
- style-only visual changes do not invalidate semantics unless they alter semantic participation or geometry.
