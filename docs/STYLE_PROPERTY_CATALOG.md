# Style Property Catalog

This document is the authoritative built-in public style surface.

All values are framework-owned Kotlin/JVM types.

No public property exposes Taffy4J.

## Common value syntax

```kotlin
8.px
8.dp
14.sp
1.physicalPx
envInsets(SAFE_DRAWING)
100.percent
auto()
calc(expression)

size(100.percent, auto())
edges(8.px)
edges(8.px, 12.px)
corners(6.px)
```

`px` is a root logical coordinate unit. `dp`, `sp`, and `physicalPx` resolve through the root `UnitResolver`; `100.percent` means 100%.

## Layout

### Display and box

```text
display
boxSizing
direction
```

Required display values:

```text
BLOCK
FLOW_ROOT
FLEX
GRID
NONE
```

Framework default: `FLEX`.

### Size

```text
width
height
size
minWidth
minHeight
minSize
maxWidth
maxHeight
maxSize
aspectRatio
```

### Positioning

```text
position
inset
top
right
bottom
left
```

### Box model

```text
margin
padding
borderWidth
```

### Alignment

```text
alignItems
alignSelf
justifyItems
justifySelf
alignContent
justifyContent
gap
rowGap
columnGap
```

### Overflow

```text
overflow
overflowX
overflowY
scrollbarWidth
```

Values:

```text
VISIBLE
CLIP
HIDDEN
SCROLL
```

No `AUTO` value exists because Taffy does not provide it as an overflow mode.

Default `scrollbarWidth = 0.px`.

### Block-specific

```text
floatMode
clear
```

### Flex

```text
flexDirection
flexWrap
flexBasis
flexGrow
flexShrink
```

Wrap values:

```text
NO_WRAP
WRAP
WRAP_REVERSE
```

### Grid

```text
gridTemplateColumns
gridTemplateRows
gridAutoColumns
gridAutoRows
gridAutoFlow
gridTemplateAreas
gridColumn
gridRow
```

Track/line/placement values are immutable framework-owned Kotlin types projected internally to Taffy.

## Paint

```text
background
borderPaint
borderColor convenience
borderRadius
boxShadow
```

`background` and `borderPaint` accept immutable `Paint`.

Core Paint:

```text
SolidPaint
LinearGradientPaint
RadialGradientPaint
ImagePaint
LayeredPaint
```

Backend modules provide platform-specific `Paint` implementations through the same immutable `Paint` contract.

`borderColor` is convenience for a solid border paint.

## Typography

```text
color
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
```

Inherited by default:

```text
color
fontFamily
fontSize
fontWeight
fontStyle
lineHeight
letterSpacing
textWrap
textAlign
```

`textAlign` uses framework `START/CENTER/END`, direction-aware.

Taffy `TextAlign` remains backend-only and is not exposed.

## Render

```text
visibility
opacity
transform
transformOrigin
clip
zIndex
isolation
```

`visibility` is inherited.

`opacity` valid range: `0f..1f`.

## Interaction

```text
hitTest
```

Values:

```text
AUTO
SELF_NONE
SUBTREE_NONE
```

## Paint value contract

Canonical Kotlin use:

```kotlin
val CARD = style {
    background = rgb(28, 28, 32)
    borderPaint =
        linearGradient(/* ... */)
}
```

Image Paint supports:

```text
source region
fit
alignment
repeat/tile
sampling
tint
opacity
```

Layered Paint records back-to-front.

Platform-specific Paint implementations own their own resource-resolution semantics outside `lumentika-core`.

## Style variables

```kotlin
val ACCENT =
    styleVar(rgb(90, 120, 255))

val CONTROL = style {
    color = variable(ACCENT)
}
```

Theme override forms:

```kotlin
theme {
    set(ACCENT, rgb(255, 0, 0))
    set(SURFACE, surfaceReadable)

    set(TEXT) {
        deriveTextColor(mode.value)
    }
}
```

Lambda theme sources become scope-owned derived readables.

## Conditions

Built-in:

```text
HOVER
ACTIVE
FOCUS
FOCUS_VISIBLE
FOCUS_WITHIN
DISABLED
```

Combinators:

```kotlin
all(HOVER, not(DISABLED))
any(FOCUS, HOVER)
not(DISABLED)
```

Custom:

```kotlin
enum class State : StyleState {
    INVALID
}
```

## StylePart

```kotlin
enum class Part : StylePart {
    ROOT,
    TRACK,
    THUMB
}
```

Theme mapping:

```kotlin
theme {
    style(
        Slider.Part.THUMB,
        VANILLA_THUMB
    )
}
```

No string part names.

## Property impacts

Representative mapping:

```text
display             LAYOUT | SEMANTICS
width/height         LAYOUT
margin/padding       LAYOUT
borderWidth          LAYOUT
flex/grid            LAYOUT
overflow             LAYOUT | CLIP | SCROLL
fontFamily           INTRINSIC_MEASURE | LAYOUT | PAINT | INHERITANCE
fontSize             INTRINSIC_MEASURE | LAYOUT | PAINT | INHERITANCE
textWrap             INTRINSIC_MEASURE | LAYOUT | PAINT | INHERITANCE
color                PAINT | INHERITANCE
background           PAINT
borderPaint          PAINT
borderRadius         PAINT | CLIP when used for clipping
boxShadow            PAINT | EFFECT
textShadow           PAINT
textOutline          PAINT
visibility           PAINT | INTERACTION | SEMANTICS | INHERITANCE
opacity              EFFECT
transform            TRANSFORM
transformOrigin      TRANSFORM
clip                 CLIP
zIndex               STACKING
isolation            STACKING | EFFECT
hitTest              INTERACTION
```

The generated catalog is the single source of truth for impact and animation metadata.

## Animation metadata

Only animatable properties receive generated `TransitionsBuilder` accessors.

Compatible examples:

```text
Float → Float
Px → Px
Percent → Percent
Color → Color
compatible transform decompositions
compatible shadow vectors
```

Discrete/incompatible examples:

```text
Auto ↔ Px
Px ↔ Percent
Display
Overflow
Position
grid structure
hitTest
```

Exact behavior is defined by `ANIMATION_RUNTIME_SPEC.md`.

## Validation

Reject:

```text
non-finite numeric geometry
negative scrollbar width
negative flexGrow/flexShrink
aspectRatio <= 0
fontSize <= 0
fontWeight outside supported range
opacity outside 0..1
negative shadow blur
empty all()/any()
null
```

## Backend-only Taffy values

Do not expose publicly:

```text
Taffy itemIsReplaced
Taffy itemIsTable
Taffy block-layout TextAlign
native NodeId
Taffy Style
```

The framework sets these internally where content/layout semantics require them.

## Acceptance

The catalog is complete when every built-in public property has:

```text
Kotlin value type
initial value
inheritance flag
StyleImpact
validation
Taffy/render projection where applicable
animation adapter metadata where applicable
generated builder accessor
generated resolved storage slot
generated differ support
```
