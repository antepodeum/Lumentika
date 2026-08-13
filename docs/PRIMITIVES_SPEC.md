# Primitive and Content Boundary Specification

## 1. Module ownership

The primitive/content runtime lives in `lumentika-core`.

It contains no native platform, mod loader, platform-specific control, window-system, or GPU-backend types.

## 2. Fundamental runtime node

`Element` is the persistent logical/visual structural node.

It owns only:

```text
identity
parent/children
lifecycle/scope
event connection
runtime connection points
semantic configuration connection
```

It does not expose a universal visual/layout property bag.

## 3. No visual mega-object

Base `Element` must not directly expose:

```text
background
border
radius
opacity
clip
transform
texture
font
camera
scene state
Taffy Style
platform widget state
```

These belong to style/render/layout side tables and component/content abstractions.

## 4. Fragment

`Fragment` is boxless structural content.

It:

```text
has logical children
has no Taffy node
has no independent geometry box
flattens children into Taffy layout projection
preserves logical ancestry for event/context/theme/semantics resolution
```

## 5. Terminal content

The fundamental terminal rendering abstraction:

```kotlin
interface Content {
    fun paint(
        recorder: PaintRecorder,
        geometry: BoxGeometry
    )
}
```

Content records retained commands through the core recorder.

It does not directly own layout or a platform render loop.

## 6. Intrinsic measurement

Optional contract:

```kotlin
interface IntrinsicMeasurable {
    fun measure(
        input: IntrinsicMeasureInput
    ): Size<Float>
}
```

The public input is framework-owned.

Internally it maps to the known-dimension/available-space information required by the built-in Taffy4J bridge.

## 7. Hit regions

A content leaf may optionally expose `HitRegionSource`.

Normal content uses its element box.

Custom geometry/scenes may refine local hit testing.

## 8. Core content families

Initial platform-neutral content implementations:

```text
TextContent
ImageContent
ShapeContent
CustomContent
```

Platform modules may add their own Content/Paint types without changing core.

## 9. Universal core components are compositions

Core ships universal components such as:

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

They are components/compositions built on the same core Element/style/Taffy primitives, not new fundamental Element subclasses. Their behavior is core-owned; their concrete platform appearance is provided by platform themes/render backends.

## 10. Text and image

Platform-neutral text and image components live in core:

```kotlin
text {
    value = "Hello"
}

image {
    source = icon
}
```

The platform backend/services provide actual font measurement, glyph rendering, resource decoding, and drawing.

## 11. Custom render content

Custom content can record arbitrary commands supported by the retained command model.

It is still constrained by core:

```text
transform
clip
effect
stacking
hit testing
committed layout geometry
```

## 12. Scene content

A 2D/3D scene is hosted as content under one element.

Scene objects are not framework Elements.

A scene may provide:

```text
camera
internal scene graph
backend commands
local raycast
semantic events
```

without contaminating the base Element hierarchy.

## 13. Platform-specific content extension

A platform module may add:

```text
backend-specific Paint
backend-specific Content
backend-specific retained command
native-widget adapter content
```

The module uses core extension contracts and does not modify Element semantics.

native platform-specific item/entity/map/widget content is one example and is specified only in the native platform documents.

## 14. Layout participation

A box-producing element may project one Taffy node.

Component boundaries alone do not create Taffy nodes.

Fragments do not create Taffy nodes.

Terminal content hosted in a box may provide intrinsic measurement.

## 15. Replaced content

Content whose intrinsic behavior corresponds to a replaced box may cause internal Taffy `itemIsReplaced` configuration.

This remains internal and is not a public style property.

## 16. Style connection

Components/content receive presentation through normal style attachment and typed `StylePart` integration.

Ordinary control skins should use style/Paint/parts rather than opaque drawing whenever their subvisuals are independently themeable.

## 17. Render connection

After layout commit:

```text
element local BoxGeometry
→ Content.paint(...)
→ retained PaintArtifact
```

Property trees provide transform/clip/effect/scroll state separately.

Content may not mutate layout during paint.

## 18. Platform reuse

The same core primitives and universal components run under any integration that implements the host/render/environment/service contracts.

Concrete libraries add native rendering, services, themes, and platform-specific components without another component runtime.

## 19. Tests

Required:

```text
Fragment has no Taffy node
Fragment preserves logical ancestry
Content receives committed local geometry
IntrinsicMeasurable maps correct constraints to Taffy bridge
custom content respects clips/transforms
custom scene hit maps through inverse transform
backend-specific content can be registered without platform types in core
base Element exposes no visual/layout mega-bag
lumentika-core compiles without concrete platform or mod-loader dependencies
```

## 20. Invariants

- `Element` is minimal and persistent;
- Taffy4J remains the core layout solver behind public primitive geometry;
- layout/render/style state is externalized;
- universal controls are core compositions over the primitive runtime;
- scene objects are not Elements;
- platform types never leak into generic core;
- paint/content cannot refine committed layout.
