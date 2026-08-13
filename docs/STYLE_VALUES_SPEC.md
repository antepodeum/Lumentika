# Typed Style Values Specification

## 1. Purpose

The style system is a typed immutable Kotlin value layer applied directly to persistent elements.

It is not a stylesheet/selector/cascade engine.

Normal public construction uses Kotlin type-safe builders:

```kotlin
val BUTTON = style {
    padding = edges(8.px, 12.px)
    background = BG
    borderRadius = 6.px

    on(HOVER) {
        opacity = 0.9f
        transform = scale(1.03f)
    }

    on(DISABLED) {
        opacity = 0.5f
    }
}
```

`Style` is immutable after builder completion.

## 2. Composition

```kotlin
val ROOT = style {
    include(VERTICAL)
    include(SURFACE)

    gap = 8.px
}
```

`include(...)` is flattened at style-program compilation at the exact declaration position.

Later assignments win within the same composed source.

## 3. State variants

`on(...)` is the canonical Kotlin API:

```kotlin
val CONTROL = style {
    background = BG

    on(all(HOVER, not(DISABLED))) {
        background = HOVER_BG
    }
}
```

`when` is not used as a public style method because it is a Kotlin keyword.

Built-in style states:

```text
HOVER
ACTIVE
FOCUS
FOCUS_VISIBLE
FOCUS_WITHIN
DISABLED
```

A component may expose typed semantic states:

```kotlin
enum class State : StyleState {
    SELECTED,
    INVALID
}
```

Use:

```kotlin
control {
    state(State.INVALID, invalid)
}
```

Style condition combinators are typed values:

```kotlin
all(...)
any(...)
not(...)
```

## 4. Reactive style sources

Static:

```kotlin
panel {
    style(PANEL)
}
```

Multiple sources:

```kotlin
panel {
    style(BASE)
    style(OVERRIDE)
}
```

Reactive style source:

```kotlin
panel {
    style {
        if (compact.value) {
            COMPACT
        } else {
            SPACIOUS
        }
    }
}
```

Conceptually the reactive overload accepts `Readable<Style>` or an inline reactive lambda adapted to a scoped derived source.

Conditional style attachment:

```kotlin
panel {
    styleWhen(compact, COMPACT)
}
```

## 5. No style data on base Element

Base `Element` does not expose visual fields.

Style runtime state lives in side tables associated with the persistent element.

`Element.style(...)` / component builder `style(...)` are the connection points.

## 6. Property model

Every built-in property has a generated descriptor:

```kotlin
interface StyleProperty<T> {
    val initialValue: T
    val inherited: Boolean
    val impact: StyleImpact
}
```

Built-in properties receive generated contiguous internal IDs.

No reflection or string lookup exists in the hot path.

## 7. Style impact

Impact is an orthogonal bitset, not a severity enum.

Required bits:

```text
LAYOUT
INTRINSIC_MEASURE
PAINT
TRANSFORM
CLIP
EFFECT
STACKING
SCROLL
INTERACTION
SEMANTICS
INHERITANCE
```

One property may carry multiple impacts.

Examples:

```text
width              -> LAYOUT
fontSize           -> INTRINSIC_MEASURE | LAYOUT | PAINT | INHERITANCE
background         -> PAINT
transform          -> TRANSFORM
opacity            -> EFFECT
clip               -> CLIP
zIndex             -> STACKING
overflow           -> LAYOUT | CLIP | SCROLL
visibility         -> PAINT | INTERACTION | SEMANTICS | INHERITANCE
```

## 8. Layout public values

Public layout types are framework-owned.

No Taffy type appears in public API.

Required public families include:

```text
Display
BoxSizing
Direction
Position
Overflow
FloatMode
Clear
AlignItems
AlignContent
JustifyItems
JustifyContent
FlexDirection
FlexWrap
GridAutoFlow
grid track/placement values
dimension/length-percentage values
```

Internal projection:

```text
framework style value
→ LayoutStyleProjection
→ Taffy4J Style
```

## 9. Length values

Framework-owned values support direct logical, density-aware, text-scaled, physical-pixel, percentage, auto, and calculated lengths.

```kotlin
sealed interface DimensionValue
sealed interface LengthPercentageValue
sealed interface LengthPercentageAutoValue

data class Px(
    val value: Float
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue,
    AbsoluteLengthValue

data class Dp(
    val value: Float
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue,
    AbsoluteLengthValue

data class Sp(
    val value: Float
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue,
    AbsoluteLengthValue

data class PhysicalPx(
    val value: Float
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue,
    AbsoluteLengthValue

data class Percent(
    val fraction: Float
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue

data object Auto :
    DimensionValue,
    LengthPercentageAutoValue

data class Calc(
    val expression: CalcExpression
) : DimensionValue,
    LengthPercentageValue,
    LengthPercentageAutoValue
```

Ergonomic extensions:

```kotlin
8.px
8.dp
14.sp
1.physicalPx
100.percent
auto()
calc(expression)
```

Resolution happens through the root unit/environment services before Taffy projection:

```text
px          -> root logical coordinate units
dp          -> UnitResolver.resolveDp(...)
sp          -> UnitResolver.resolveSp(...)
physicalPx  -> UnitResolver.resolvePhysicalPx(...)
envInsets   -> selected `UiInsets` edges in root logical coordinates
percent     -> fraction of the relevant containing size
```

`100.percent` is stored as fraction `1.0`.

`fontScale` is not a conversion multiplier. A platform may use non-linear text scaling, so `Sp` always resolves through `UnitResolver`.

Environment-dependent length changes participate in normal property invalidation and can request layout/intrinsic work when their resolved float changes.

`CalcExpression` is framework-owned and never exposes Taffy4J.

## 10. Geometry helpers

Typed helpers:

```kotlin
size(width, height)
edges(all)
edges(vertical, horizontal)
edges(top, right, bottom, left)
corners(all)
```

No untyped maps.

## 11. Paint

Paint-bearing properties use immutable typed `Paint`.

Core implementations:

```text
SolidPaint
LinearGradientPaint
RadialGradientPaint
ImagePaint
LayeredPaint
```

Backend modules may add more `Paint` implementations.

Platform modules may add backend-specific `Paint` implementations without changing core style semantics.

Canonical properties:

```kotlin
background = paint
borderPaint = paint
```

Color assignment is convenience for solid paint:

```kotlin
background = rgb(28, 28, 32)
borderColor = rgba(255, 255, 255, 24)
```

`ImagePaint` supports:

```text
source region / UV
fit
alignment
repeat/tile
sampling
tint
opacity
```

`LayeredPaint` records back-to-front.

## 12. Typography

Required typography properties:

```text
fontFamily
fontSize
fontWeight
fontStyle
lineHeight
letterSpacing
textWrap
textAlign
textShadow
textOutline
color
```

Framework `TextAlignment` is direction-aware:

```text
START
CENTER
END
```

It is not Taffy block-layout `TextAlign`.

## 13. Render properties

Required:

```text
visibility
opacity
transform
transformOrigin
clip
zIndex
isolation
```

Transforms are render-space properties and do not feed geometry back into Taffy.

## 14. Interaction

Required:

```text
hitTest
```

with:

```text
AUTO
SELF_NONE
SUBTREE_NONE
```

Focus participation is controlled by the component/event-focus model rather than hidden inside generic Paint.

## 15. Style variables

Declaration:

```kotlin
val ACCENT =
    styleVar(rgb(90, 120, 255))

val SURFACE =
    styleVar(rgb(28, 28, 32))
```

`StyleVar<T>` is:

```text
immutable identity token
+ default value
```

The object itself is identity.

There is no string key, registry name, or runtime dependence on the Kotlin property name.

Use in style:

```kotlin
val BUTTON = style {
    color = variable(ACCENT)
}
```

## 16. Theme values

Theme is an immutable typed mapping.

```kotlin
val DARK = theme {
    set(ACCENT, rgb(120, 150, 255))
    set(SURFACE, rgb(20, 20, 24))
}
```

Reactive source:

```kotlin
val accent =
    state(rgb(120, 150, 255))

val DARK = theme {
    set(ACCENT, accent)
}
```

Reactive expression:

```kotlin
val DYNAMIC = theme {
    set(ACCENT) {
        computeAccent(mode.value)
    }
}
```

The lambda becomes a scope-owned derived readable.

Nested theme scopes override only explicitly mapped tokens.

Missing override falls back through logical ancestry and finally to the token default.

Top-layer render projection does not alter logical theme ancestry.

## 17. Theme composition

```kotlin
val DANGER = theme {
    include(DARK)
    set(ACCENT, rgb(255, 90, 90))
}
```

There is no global mutable theme singleton.

`Readable<Theme>` is supported for mapping replacement when the mapping itself changes.

Replacement diffs changed token identities and invalidates only affected lookups.

## 18. Style inheritance

Initial inherited properties:

```text
direction
color
fontFamily
fontSize
fontWeight
fontStyle
lineHeight
letterSpacing
textWrap
textAlign
visibility
```

Inheritance follows logical ancestry.

A local winning assignment blocks propagation for that property into the subtree.

Top-layer projection does not alter inheritance ancestry.

## 19. StylePart

Reusable components expose typed stable internal parts only when independently themeable.

Example:

```kotlin
enum class Part : StylePart {
    ROOT,
    TRACK,
    THUMB,
    LABEL
}
```

Identity is the enum/token object.

No string part names exist.

Theme mapping:

```kotlin
val VANILLA = theme {
    style(
        Slider.Part.TRACK,
        VANILLA_TRACK
    )

    style(
        Slider.Part.THUMB,
        VANILLA_THUMB
    )
}
```

Per-instance override:

```kotlin
slider {
    partStyle(
        Slider.Part.THUMB,
        CUSTOM_THUMB
    )
}
```

Part precedence:

```text
component structural/base style
→ nearest Theme part style
→ component-instance part style
```

Caller root `style(...)` applies after the theme root skin.

Part conditions evaluate owner component state.

## 20. Resolved style

Resolution order:

```text
initial / inherited values
→ attached source 1
→ attached source 2
→ ...
→ active state variants at declaration positions
→ StyleVar resolution
→ target ResolvedStyle
```

There is no specificity.

A later attached style source outranks an earlier source for properties it writes.

Equivalent publications are suppressed by semantic equality.

## 21. Resolved storage

`ResolvedStyle` uses generated immutable typed groups with structural sharing:

```text
InheritedValues
BoxLayoutValues
FlexGridValues
PaintValues
RenderValues
InteractionValues
```

Primitives are unboxed where practical.

Default groups are shared singletons.

A transient `ResolvedStyleDraft` performs copy-on-write updates and materializes only changed groups.

## 22. Style program compilation

`style { ... }` compiles to shared immutable `StyleProgram`.

Conceptual program data:

```text
propertyIds[]
valueRefs[]
conditionIds[]
previousAssignmentForSameProperty[]
lastAssignmentForProperty[]
conditions[]
writtenProperties
stateDependencies
```

`include(...)` is flattened at exact declaration position.

Conditions preserve declaration order.

Provably dead declarations may be eliminated when safe.

Per-property reverse assignment chains allow resolving one candidate property without scanning unrelated declarations.

## 23. Candidate invalidation

Reactive source A → B:

```text
candidate properties =
A.writtenProperties OR B.writtenProperties
```

`styleWhen` toggle:

```text
candidate properties =
style.writtenProperties
```

State change:

```text
candidate properties =
union of sourceProgram.stateDependencies[state]
```

Dirty root queue deduplicates elements and ORs property masks until flush.

## 24. StyleVar dependencies

Only the winning variable reference registers a token dependency.

Hidden candidates do not remain subscribed.

Theme lookup tracks traversed scopes so adding/removing a nearer override invalidates affected consumers.

A winning reactive theme source also registers a direct dependency on its `Readable<T>`.

## 25. Diff

Generated differ uses group reference equality as fast path.

Only changed groups receive typed field comparisons.

Output:

```kotlin
data class StyleChangeSet(
    val changedProperties: PropertyMask,
    val impactBits: StyleImpactMask,
    val changedInheritedProperties: PropertyMask
)
```

Style resolution itself does not call Taffy or paint.

Projection consumes `StyleChangeSet` afterward.

## 26. Animation integration

Style resolution produces:

```text
target ResolvedStyle
target ResolvedTransitions
```

Animation runtime may overlay sparse effective values.

Animation values do not participate in:

```text
style precedence
inheritance
theme lookup
StyleVar dependency resolution
```

Effective animated property changes reuse the same generated impact metadata.

See `ANIMATION_RUNTIME_SPEC.md`.

## 27. Platform skinning compatibility

Core style semantics are platform-independent. Platform integration modules may combine typed StyleParts/StyleStates with backend-specific Paint and resource systems to reproduce native/platform visual languages.

This does not add platform types or resource identities to `lumentika-core`.

## 28. Validation

At construction time reject:

```text
non-finite geometry
negative scrollbar width
negative flex grow/shrink
aspect ratio <= 0
font size <= 0
font weight outside supported range
opacity outside 0..1
negative shadow blur
empty all()/any()
null values
```

Fragment/boxless nodes cannot receive layout-box properties.

## 29. Disposal

Unmount:

```text
remove reactive style source subscriptions
remove StyleVar/theme dependencies
remove resolved-style association
remove part-style bindings
remove condition caches
```

Shared immutable `Style`, `StyleProgram`, conditions, tokens, and default groups require no per-element disposal.

## 30. Invariants

- style values are typed Kotlin values;
- no selector/cascade/specificity system exists;
- no string style property lookup in hot path;
- no string StyleVar identity;
- no string StylePart identity;
- Taffy types never leak publicly;
- style resolution never computes layout directly;
- Paint never feeds geometry back into layout;
- conditions preserve declaration order;
- inheritance follows logical ancestry;
- animation overlays target style rather than becoming another precedence source.
