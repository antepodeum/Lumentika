# Style Runtime Internals Specification

## 1. Goal

The style runtime resolves immutable Kotlin `Style` values into compact typed `ResolvedStyle` snapshots with fine-grained invalidation.

The hot path must avoid:

```text
reflection
string property lookup
generic Map<Property, Any?> per element
full source rescans for unrelated property changes
whole-subtree invalidation when an inherited property is locally overridden
```

## 2. Generated property catalog

A generated property catalog owns:

```text
contiguous PropertyId
initial value
inheritance flag
group
StyleImpact bits
animation adapter metadata
validation
public Kotlin builder accessors
```

Built-in IDs are dense and fit fixed-width `PropertyMask` structures.

Dynamic identity maps are reserved for genuinely dynamic tokens such as user-defined `StyleState` and `StyleVar`.

## 3. Style compilation

Kotlin source:

```kotlin
val BUTTON = style {
    background = BG
    color = TEXT

    on(HOVER) {
        background = HOVER_BG
    }

    on(DISABLED) {
        opacity = 0.5f
    }
}
```

Builder completion compiles one immutable shared `StyleProgram`.

Program concept:

```kotlin
data class StyleProgram(
    val propertyIds: IntArray,
    val valueRefs: Array<Any>,
    val conditionIds: IntArray,
    val previousAssignmentForSameProperty: IntArray,
    val lastAssignmentForProperty: IntArray,
    val conditions: Array<CompiledCondition>,
    val writtenProperties: PropertyMask,
    val stateDependencies: StateDependencyTable,
    val environmentDependencies: EnvironmentDependencyTable
)
```

Actual implementation may use more compact specialized storage.

## 4. Include flattening

```kotlin
val ROOT = style {
    include(A)
    include(B)
    gap = 8.px
}
```

is flattened at compile time preserving exact declaration order.

There is no runtime recursive style include traversal.

## 5. Conditions

Every condition compiles to:

```text
condition evaluator
state-token dependency set
```

Nested conditions combine semantically.

Example:

```kotlin
on(all(HOVER, not(DISABLED))) {
    opacity = 0.9f
}
```

The compiler records that the affected declarations depend on `HOVER` and `DISABLED`.

A source-level `state token -> PropertyMask` index drives invalidation.

## 6. Per-property assignment chains

For every property, the program stores the latest assignment and links to the previous assignment of that same property.

Resolution of one candidate property walks only:

```text
latest source
→ latest assignment for property
→ earlier active assignment for property
→ earlier source
→ inheritance / initial
```

It does not scan declarations for unrelated properties.

## 7. ElementStyleState

Conceptual side-table state:

```kotlin
data class ElementStyleState(
    val sources: MutableList<StyleSourceSlot>,
    var resolved: ResolvedStyle,
    var pendingProperties: PropertyMask,
    var localInheritedOverrides: PropertyMask,
    val dependencies: StyleDependencyState,
    val conditionCache: ConditionCache
)
```

The concrete implementation may use arrays/specialized structures rather than general collections.

Per-element state contains only:

```text
source references
resolved group references
candidate masks
dependency edges
small condition caches
```

Shared `Style`/`StyleProgram` objects are not copied per element.

## 8. Source slots

An attached source slot may represent:

```text
constant Style
Readable<Style>
reactive style lambda
styleWhen toggle
theme part style
component-instance part override
```

Every slot has stable position in source precedence until structurally changed.

A reactive source publication from program A to B computes:

```text
candidate =
A.writtenProperties OR B.writtenProperties
```

## 9. State invalidation

When a state token changes:

```text
candidate =
OR sourceProgram.stateDependencies[token]
```

Only elements/sources depending on that token receive work.

Built-in states use generated IDs.

Custom typed `StyleState` tokens use dynamic identity indexing.

## 10. Condition cache

Condition evaluation is cached per attached source/state generation.

If candidate properties all depend on already-evaluated conditions for the current state generation, resolution reuses the cached result.

The cache remains small and scoped to the source slot.

## 11. Property-oriented resolver

Pseudo-Kotlin:

```kotlin
fun resolveProperty(
    element: ElementStyleState,
    property: PropertyId
): ResolvedValue {
    for (source in element.sources.asReversed()) {
        val program = source.program

        var assignment =
            program.lastAssignment(property)

        while (assignment != NONE) {
            if (
                source.conditionActive(
                    program.conditionId(assignment)
                )
            ) {
                return resolveValue(
                    source,
                    assignment
                )
            }

            assignment =
                program.previousAssignment(assignment)
        }
    }

    return inheritedOrInitial(
        element,
        property
    )
}
```

The actual implementation should avoid allocation in this path.

## 12. Resolved groups

`ResolvedStyle` is generated immutable typed grouped storage:

```kotlin
data class ResolvedStyle(
    val inherited: InheritedValues,
    val boxLayout: BoxLayoutValues,
    val flexGrid: FlexGridValues,
    val paint: PaintValues,
    val render: RenderValues,
    val interaction: InteractionValues
)
```

The exact type may be a regular class rather than Kotlin `data class` if generated equality/copy control is preferable.

Default groups are shared.

## 13. Draft updates

Candidate resolution starts from the previous resolved snapshot.

Conceptual:

```kotlin
val draft =
    ResolvedStyleDraft.from(
        element.resolved
    )

candidates.forEach { propertyId ->
    draft[propertyId] =
        resolveProperty(
            element,
            propertyId
        )
}

val next =
    draft.freezeSharingUnchangedGroups()
```

Only modified groups materialize.

If all fields in a touched group remain semantically equal, the old group reference is retained.

## 14. Semantic equality

Property values use property-specific semantic equality.

Examples:

```text
Float exact/canonical equality policy
immutable value structural equality
token identity where identity is semantic
Paint equality by immutable value semantics
Style/Theme token identity where specified
```

Equivalent publications suppress downstream invalidation.

## 15. Inheritance

Ancestor style resolution occurs before descendant inheritance propagation.

When inherited winners change:

```text
changedInheritedProperties
```

is propagated into descendants.

Every element keeps:

```text
localInheritedOverrides
```

meaning properties whose semantic winner is local.

Propagation candidate:

```text
ancestor changed inherited bits
AND NOT localInheritedOverrides
```

A local value equal to the inherited value still counts as a local override.

This is semantic ownership, not value inequality.

## 16. StyleVar resolution

A candidate declaration may contain a `StyleVar<T>` reference.

Only the winning declaration registers variable lookup dependencies.

Resolution:

```text
element logical ancestry
→ nearest theme scope overriding token
→ outer scopes
→ token default
```

Dependency state records every traversed relevant theme scope so insertion/removal of a nearer override invalidates the lookup.

If the winning override value is reactive, a direct dependency on its `Readable<T>` is registered.

Hidden variable candidates have no active dependency edge.

## 17. Theme replacement

A `Readable<Theme>` may replace a mapping.

Replacement computes token identity differences:

```text
added overrides
removed overrides
changed value sources
```

Only consumers whose lookup dependency graph references changed tokens/scopes are invalidated.

No global theme flush.

## 18. StylePart sources

Theme `StylePart` mappings are inserted as stable style sources for the exposed part.

Precedence:

```text
component structural/base
→ theme part style
→ instance part override
```

For root, caller style sources follow the theme root skin.

Part style conditions evaluate the owner component style state unless the component deliberately exposes a typed part-local semantic state.

## 19. Environment-dependent style values

Compiled style values record environment dependencies when their resolved value depends on root environment.

```text
Dp         -> DP_UNITS
Sp         -> SP_UNITS
PhysicalPx -> PHYSICAL_PX_UNITS
envInsets  -> INSETS
root direction default -> LAYOUT_DIRECTION
Calc       -> union of referenced environment/unit dependencies
```

Each `StyleProgram` stores an `EnvironmentDependencyTable` mapping environment tokens to written `PropertyMask`s.

`DP_UNITS`, `SP_UNITS`, and `PHYSICAL_PX_UNITS` are invalidated by their corresponding `UiEnvironment.units.revisions` field. Resolution calls the root `UnitResolver`; it does not infer `Sp` from density/font-scale multiplication.

When an environment token changes, only attached source slots whose programs reference that token contribute candidate properties.

Resolved target style stores environment-resolved framework values; Taffy receives only final float-compatible layout values.

Equivalent resolution after an environment change suppresses downstream work.

Theme/reactive expressions that read environment values participate through the normal reactive dependency graph.

## 20. Dirty queue

Each `UiRoot` owns a style dirty queue.

An element is inserted at most once until flush.

Repeated invalidations OR candidate property masks.

Style flush order is ancestor-first where inheritance may matter.

Style resolution produces data only; it does not call Taffy, PrePaint, or paint recording.

## 21. Diff

Generated differ:

```kotlin
fun diff(
    oldStyle: ResolvedStyle,
    newStyle: ResolvedStyle
): StyleChangeSet
```

Fast path:

```text
group reference equal
→ skip group
```

Changed group:

```text
typed generated comparisons
→ changed PropertyMask
→ OR property StyleImpact
→ changed inherited mask
```

Output:

```kotlin
data class StyleChangeSet(
    val changedProperties: PropertyMask,
    val impactBits: StyleImpactMask,
    val changedInheritedProperties: PropertyMask
)
```

## 22. Projection

After resolution/diff:

```text
LAYOUT
→ LayoutStyleProjection
→ Taffy style mutation staging

INTRINSIC_MEASURE
→ measurement config/cache invalidation
→ markDirty in Taffy

PAINT
→ PaintSource dirty

TRANSFORM
→ TransformTree mutation

CLIP
→ ClipTree mutation

EFFECT
→ EffectTree mutation

STACKING
→ stacking/order mutation

SCROLL
→ ScrollTree / overflow mutation

INTERACTION
→ hit/focus participation mutation

SEMANTICS
→ semantic participation/geometry configuration mutation
```

No projection mutates resolved target style.

## 23. Animation boundary

Target diff flows to animation runtime.

Animation runtime maintains sparse tracks/effective overlay.

Per-frame effective changes reuse property catalog impact bits.

Target `ResolvedStyle` remains stable while a transition runs.

StyleVar/theme/state resolution never reads animation overlay.

## 24. Memory model

Per-element style memory should scale roughly with:

```text
number of attached sources
number of active dependencies
number of active custom state tokens
resolved group references
fixed masks
```

not with the full number of built-in style properties.

No per-element generic property map.

## 25. Allocation policy

Normal style flush should avoid transient allocation proportional to property count.

Reusable/transient mechanisms may include:

```text
root scratch buffers
PropertyMask values
generated drafts
pooled dependency edge nodes where justified
track-local arrays
```

Optimization must not alter public semantics.

## 26. Interning

Global style/group interning is outside the runtime contract.

Correctness relies on immutable values and semantic equality, not interning.

## 27. Threading

Style runtime is owned by the UI root thread unless a platform threading contract explicitly marshals mutations.

Reactive publications entering from other threads must marshal through the root scheduler before style mutation.

No style side table is concurrently mutated without root synchronization.

## 28. Tests

Required tests include:

```text
include order
later source precedence
condition declaration order
nested condition combination
state-token candidate masks
reactive source A→B written-property candidate union
styleWhen toggle candidates
semantic equality suppression
local inherited override blocking
ancestor inherited propagation
StyleVar winning-only dependency
theme scope insertion/removal invalidation
Readable<Theme> token diff
theme StylePart precedence
instance part override
group structural sharing
generated diff impacts
dirty queue dedup/coalescing
unmount dependency cleanup
```

Performance tests:

```text
many elements sharing one StyleProgram
single hover-state property change
single reactive theme color change
large subtree with local inherited overrides
source replacement with sparse written properties
```

## 29. Invariants

- no reflection in style hot path;
- no string built-in property lookup;
- no generic per-element property map;
- no full style scan for one candidate property;
- no animation value in target precedence;
- no hidden StyleVar dependency;
- no style resolver call into Taffy/render;
- no top-layer change to logical inheritance/theme ancestry;
- style source precedence is deterministic and declaration ordered.
