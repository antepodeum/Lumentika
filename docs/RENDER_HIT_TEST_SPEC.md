# Render, Paint, Compositing, and Hit-Test Runtime Specification

## 1. Scope

This phase defines the retained rendering runtime that consumes the persistent `Element` tree and committed Taffy layout snapshots.

It owns:

- render-tree projection without adding visual properties to `Element`;
- paint order and stacking contexts;
- transform, clip, effect, and scroll property trees;
- `PrePaint`, paint recording, and frame replay;
- paint invalidation and property-only invalidation;
- scroll transforms and transformed overflow;
- retained paint artifacts;
- hit-test regions and hit-test ordering;
- coordinate conversion;
- top-layer rendering;
- interaction with pointer targeting and pointer capture.

Platform render-backend implementations and typed style resolution are separate specifications. `lumentika-core` owns the retained artifact model; platform modules only replay it.

## 2. Render projection

Core render flow:

```text
logical Element tree
→ committed Taffy geometry
→ PrePaint property trees
→ retained PaintArtifact + HitTestArtifact
→ platform backend replay
```

The logical `Element` tree remains the ownership, lifecycle, context, semantics, and event-propagation tree.

Rendering is an internal projection using ordered paint items plus hierarchical Transform, Clip, Effect, and Scroll state. These structures are not public UI trees or public Element properties.

## 3. Core invariants

1. Taffy4J is the sole layout solver. No render phase may reimplement or refine layout.
2. `PrePaint` MUST NOT measure, constrain, size, position, reflow, wrap, or otherwise solve layout boxes.
3. `PrePaint` may only transform, clip, order, cull, and aggregate geometry already committed by Taffy.
4. Render-space geometry MUST NOT feed back into sibling/ancestor layout without an explicit later layout-affecting state/style change.
5. `Element` remains free of universal `background`, `border`, `opacity`, `clip`, `transform`, `zIndex`, or scroll fields.
6. Components/content implementations install render behavior through internal runtime associations.
7. Taffy geometry is never modified to implement scrolling or visual transforms.
8. Paint order is explicit and deterministic.
9. A stacking context is atomic relative to its parent stacking context.
10. Hit testing uses the same stacking order, transforms, clips, and scroll offsets as painting.
11. Pointer capture overrides the normal hit-test target after hit testing would otherwise select a target.
12. Scroll-offset and transform updates do not repaint unchanged content.
13. Paint-only content changes do not request Taffy layout when intrinsic size is unchanged.
14. `Display.NONE` subtrees produce no paint items and no hit-test regions.
15. Non-invertible transforms produce no visible or hit-testable subtree.
16. Backend state never leaks from one paint item into another.

## 4. Root-owned render runtime

Each `UiRoot` owns one `RenderRuntime`.

Conceptually:

```text
RenderRuntime
├─ TransformTree
├─ ClipTree
├─ EffectTree
├─ ScrollTree
├─ StackingContextTree
├─ PaintArtifact
├─ HitTestArtifact
├─ TopLayer
├─ dirty render participants
├─ dirty property nodes
├─ dirty stacking contexts
└─ dirty paint records
```

All render runtime objects are owned and mutated on the UI thread.

The root property state is:

```text
RootTransform = identity
RootClip      = viewport clip
RootEffect    = identity / opacity 1
RootScroll    = non-scrollable root
RootStackingContext
```

## 5. Render participants

An `Element` participates in rendering only when its concrete implementation supplies one or more internal render capabilities.

The render runtime recognizes three orthogonal responsibilities:

```text
PaintSource
RenderModifier
HitRegionSource
```

### PaintSource

Produces visual content.

### RenderModifier

Changes descendant render state without becoming a universal property of `Element`.

Examples:

- transform;
- clip;
- opacity/effect;
- scroll translation;
- stacking isolation.

### HitRegionSource

Creates an input target region even when the element paints nothing.

This is required for transparent interactive controls and layout-only interaction surfaces.

A concrete element can supply any combination of these responsibilities.

`Fragment` supplies none of them by itself.

## 6. Content paint contract

Terminal visual content records paint commands rather than receiving a platform backend directly.

The content boundary is:

```kotlin
interface Content {
    fun paint(
        recorder: PaintRecorder,
        geometry: BoxGeometry
    )
}
```

Intrinsic measurement remains the separate `IntrinsicMeasurable` capability defined by `PRIMITIVES_SPEC.md`.

`PaintRecorder` is backend-neutral and records retained paint commands/items.

The concrete drawing-command vocabulary is extensible so any platform module can provide typed opaque commands without putting platform types in `lumentika-core`.

A paint call must not directly mutate global render state.

## 7. Box geometry

Every layout participant exposes a local `BoxGeometry` derived solely from its committed Taffy `Layout`.

Local coordinates use the border-box top-left as `(0, 0)`.

Conceptually:

```kotlin
data class BoxGeometry(
    val borderBox: Rect,
    val paddingBox: Rect,
    val contentBox: Rect
)
```

For a node with size `w × h`:

```text
borderBox  = [0, 0, w, h]
paddingBox = borderBox inset by border widths
contentBox = paddingBox inset by padding widths
```

`Layout.location` is not baked into these local rectangles. It participates in the transform chain from the node's local space to its parent space.

## 8. Paint artifact

The retained result of painting one root is a `PaintArtifact`.

Conceptually:

```text
PaintArtifact
├─ ordered PaintChunk[]
└─ retained paint records referenced by those chunks
```

A `PaintChunk` is a contiguous sequence of paint items sharing one `PaintPropertyState`.

```kotlin
data class PaintPropertyState(
    val transform: TransformNode,
    val clip: ClipNode,
    val effect: EffectNode,
    val scroll: ScrollNode
)
```

A paint item contains:

- stable owner identity;
- stable local paint identity/key;
- retained recorded drawing;
- local visual bounds;
- property state;
- final paint-order index.

Adjacent compatible items can be coalesced into one chunk.

The artifact is persistent across frames. Unchanged paint records are reused.

## 9. Property trees

The runtime uses four independent hierarchical property trees.

They exist because these properties update independently and have different semantics.

### 9.1 Transform tree

Each `TransformNode` defines a local coordinate transform relative to a parent transform node.

Conceptually:

```text
TransformNode
├─ parent
├─ local matrix
├─ transform origin
├─ reference box
├─ cached/incremental root matrix
└─ invertibility state
```

The transform tree contains both:

- layout translations derived from Taffy `Layout.location`;
- component-defined render transforms;
- scroll translations generated from scroll offsets.

Transforms are cumulative.

Mapping local coordinates to root coordinates multiplies the node's transform with its ancestor transforms in order.

A render transform applies after layout sizing and positioning and does not change Taffy flow geometry.

A non-invertible accumulated transform suppresses painting and hit testing for the affected subtree.

### 9.2 Clip tree

Each `ClipNode` defines a clip in a specified transform coordinate space.

Conceptually:

```text
ClipNode
├─ parent
├─ transform node
└─ clip shape
```

Supported clip shapes include at least:

```text
Rect
RoundedRect
Path
```

The effective clip is the intersection of the node's transformed clip shape with all ancestor clips.

Overflow clips are generated from Taffy layout style and geometry:

- `VISIBLE` creates no overflow clip;
- `CLIP` clips at the overflow clip edge, equal to the padding box when no explicit clip margin exists;
- `HIDDEN` clips to the padding box;
- `SCROLL` clips to the scrollport, equal to the padding box.

The clip itself is not translated by the scroll offset. Descendant content is translated underneath the fixed scrollport clip.

### 9.3 Effect tree

Each `EffectNode` defines group-level compositing behavior.

Conceptually:

```text
EffectNode
├─ parent
├─ opacity
├─ blend/composite mode
├─ optional filter
├─ optional mask
├─ isolation
├─ associated transform
└─ optional output clip
```

Effects apply to the rendered result of the group represented by the effect node.

Group opacity is therefore not implemented by multiplying opacity independently into every descendant draw command.

An effect requiring group semantics creates an isolated render group/render pass in the backend.

Opacity alone does not remove an element from hit testing. A fully transparent interactive element remains targetable unless its hit-test behavior disables targeting.

### 9.4 Scroll tree

Each scroll container owns a `ScrollNode`.

Conceptually:

```text
ScrollNode
├─ parent scroll node
├─ scrollport geometry
├─ scrollable overflow geometry
├─ current offset
├─ user-scrollable axes
└─ descendant scroll TransformNode
```

The scroll offset is represented in geometry as a transform:

```text
translate(-scrollX, -scrollY)
```

Keeping scroll metadata separate from transform metadata allows input/default actions to reason about scroll ranges while geometry operations still use transform composition.

Updating a scroll offset updates the scroll node and its transform node only. It does not repaint content and does not request Taffy layout.

## 10. Layout translation

Each layout participant establishes a local render coordinate space whose origin is its Taffy border-box origin.

The local-to-parent transform includes:

```text
translate(Layout.location.x, Layout.location.y)
```

This means paint records stay in stable local coordinates.

A pure Taffy position change updates transform/property state without requiring the content to be repainted.

Size/border/padding changes update `BoxGeometry` and repaint paint sources whose recorded output depends on that geometry.

## 11. Render modifiers are compositional

Visual behavior is implemented through components that install property nodes or paint content.

Examples:

```kotlin
background {
    paint = color

    text {
        value = "Hello"
    }
}

clip(shape) {
    child()
}

transform(matrix) {
    child()
}

opacity(0.5f) {
    child()
}
```

These are component/factory APIs.

They do not mutate generic fields on `Element`.

Conceptually:

```text
transform(...)
    -> RenderModifier creating TransformNode

clip(...)
    -> RenderModifier creating ClipNode

opacity(...)
    -> RenderModifier creating EffectNode

background(...)
    -> PaintSource ordered before its content subtree
```

## 12. PrePaint phase

`PrePaint` runs after the committed layout generation and `afterLayout` effects.

It is not a second layout pass.

`PrePaint` receives committed Taffy geometry as immutable input. It MUST NOT:

- call intrinsic measurement to decide box sizes;
- choose flex/grid/block positions;
- perform text wrapping decisions that affect layout;
- change width, height, margin, padding, border, inset, or layout position;
- resize or reposition siblings because of transforms or effects;
- mutate committed Taffy `Layout` snapshots.

It may derive render-space geometry only.

It updates the derived render structures required before paint/replay:

1. project mounted render participants;
2. update layout translation nodes from committed `Layout` snapshots;
3. update transform/clip/effect/scroll property nodes;
4. update stacking-context membership and ordering;
5. calculate local/root visual bounds required for culling and hit testing;
6. calculate transformed scrollable-overflow contributions;
7. clamp scroll offsets to the current final scroll range;
8. mark paint records dirty when geometry changes require re-recording;
9. rebuild affected hit-test regions/order.

`PrePaint` runs only when layout or render-property state requires it.

A scroll offset update can use the existing property-tree topology and update the affected transform/scroll values without reconstructing paint records.

## 13. Paint phase

Paint runs for dirty `PaintSource`s.

For each dirty paint source:

1. resolve its current `BoxGeometry`;
2. create/reset a local `PaintRecorder`;
3. invoke `Content.paint(...)`;
4. replace that source's retained paint record;
5. update local visual bounds from the record;
6. attach the record to its current `PaintPropertyState`.

Clean paint sources reuse the previous retained record.

A stacking-order change does not repaint clean records. It only changes the ordering of their chunks/items in the artifact.

## 14. Frame replay

A host may request retained UI replay on every visible frame even when the core artifact is clean.

Frame replay:

```text
PaintArtifact
    ↓
paint chunks in bottom-to-top order
    ↓
resolve Transform / Clip / Effect / Scroll state
    ↓
backend draw commands
```

Replay is separate from paint recording.

This permits:

- scrolling without re-recording content;
- transform animations without re-recording content;
- opacity/effect updates without re-recording unaffected content;
- retained content caching across frames.

The backend is responsible for restoring native rendering state at command/group boundaries so one item cannot corrupt subsequent items.

## 15. Paint invalidation classes

The runtime distinguishes four invalidation classes.

### Layout invalidation

Owned by `LAYOUT_TAFFY_SPEC.md`.

Triggers Taffy compute.

### Property invalidation

Changes transform, clip, effect, or scroll property values/topology.

Examples:

- transform matrix changes;
- opacity changes;
- clip geometry changes;
- scroll offset changes;
- layout position changes.

Does not automatically repaint content.

### Paint invalidation

The recorded visual content itself changed.

Examples:

- text glyph content changed without changing intrinsic size;
- image/texture changed;
- color or stroke changed;
- size-dependent paint geometry changed;
- a custom content record changed.

Does not request layout unless intrinsic measurement also changed.

### Order invalidation

Changes stacking-context membership, stack level, top-layer membership, or source ordering.

Reorders retained items without repainting unchanged records.

One reactive update can set several invalidation classes.

## 16. Layout-change mapping

A committed Taffy `LayoutChange` maps into render invalidation as follows.

### Position only

```text
positionChanged
→ update layout TransformNode
→ property/hit-test geometry dirty
→ no paint invalidation
```

### Size, border, or padding

```text
size/border/padding changed
→ update BoxGeometry
→ update overflow clips/scrollport geometry
→ repaint own size-dependent paint sources
→ update hit regions
```

### Content size or scrollbar size

```text
contentSize/scrollbarSize changed
→ update ScrollNode base extent/gutter state
→ PrePaint transformed-overflow calculation
→ clamp offsets
```

### Layout order

`Layout.order` remains retained as Taffy output and can participate in consistency checks, but it is not sufficient to define final paint order because paint-only decorators, stacking contexts, effects, and top-layer content also participate.

Final paint order is owned by the render runtime.

## 17. Stacking contexts

The root creates the root stacking context.

A stacking context contains paint participants and child stacking contexts.

Child stacking contexts are atomic: content outside a child context cannot be interleaved between descendants inside that context.

A render behavior creates a stacking context when it introduces:

- explicit stacking isolation/stack level;
- a render transform;
- a group effect that requires isolation;
- top-layer membership.

Within one stacking context, ordering is:

```text
negative stack levels
normal/default level in source order
positive stack levels
```

Equal explicit stack levels preserve source order.

The flattened result assigns monotonically increasing paint-order indices from bottom to top.

Hit testing traverses the same order from top to bottom.

## 18. Source order

Source order is derived from the persistent logical `Element` tree plus explicit ordering inside a component's own render projection.

`Fragment` contributes no paint item but preserves the ordering of its descendants.

A decorator component controls whether its own paint source appears before or after its content by how its internal render projection is constructed.

The runtime does not contain special concepts such as "background field" or "border field" on every element.

## 19. Top layer

`UiRoot` owns an ordered top layer for modal, popover, menu, tooltip, and similar root-overlay components.

A subtree mounted in the top layer:

- remains owned by its logical component/`Element` ancestry for lifecycle and event propagation;
- projects its layout root under the synthetic `UiRoot` layout root;
- creates a stacking context whose parent is the root stacking context;
- paints above the normal root content;
- is not clipped or faded by ordinary logical ancestors outside the top-layer subtree.

Top-layer entries paint in top-layer order. The last entry is topmost.

A top-layer entry can own a separate backdrop entry immediately below itself.

Hit testing uses the same top-layer order before testing normal root content.

## 20. Top-layer layout projection

The layout runtime supports a layout-parent override only for top-layer roots.

Normal boxless projection remains unchanged.

For a top-layer root:

```text
logical parent     = original Element/component ancestry
layout parent      = UiRoot synthetic Taffy root
paint stack parent = root stacking context / top layer
event parent       = original Element ancestry
```

This separation is intentional and matches the need for overlays to escape ancestor clipping while preserving logical ownership and event propagation.

Top-layer removal restores normal projection or disposes the subtree according to the owning component operation.

## 21. Scrollable overflow

Taffy `Layout.contentSize` and `scrollWidth()/scrollHeight()` are the authoritative base layout overflow.

Render transforms do not alter Taffy flow geometry, but they can extend the area that must be reachable through a scroll container.

`PrePaint` therefore computes a transformed overflow extension from already committed layout rectangles.

This is a render-space bounds aggregation, not layout.

For each scroll container:

```text
committed Taffy rectangles
        ↓ apply existing render transforms
transformed rectangles
        ↓ union only for scroll reachability
final scrollable overflow
=
union(
    Taffy base scrollable overflow,
    eligible transformed descendant layout bounds
)
```

No box is remeasured, resized, rewrapped, or repositioned during this calculation.

Rules:

- the Taffy base extent is authoritative layout output;
- transformed-overflow aggregation changes only scroll reachability, never layout geometry;
- transforms can extend the scrollable overflow but never shrink the Taffy base extent;
- paint-only ink overflow such as shadows/glows does not extend the scroll range;
- a nested scroll container establishes its own scrollable-overflow boundary;
- clipping that establishes a non-scrollable boundary prevents clipped descendants from extending an outer scroll range;
- calculation occurs in the scroll container's local coordinate system.

Final maximum offsets are computed from the final scrollable-overflow rectangle and scrollport.

Changing a render transform can therefore update only the render-time scroll reachability range during `PrePaint` without requesting Taffy layout. It cannot cause sibling reflow, intrinsic remeasurement, or any Taffy box mutation.

## 22. Overflow and scrolling behavior

Taffy overflow mode supplies layout semantics; render runtime supplies visual/input semantics.

### `VISIBLE`

- no overflow clip;
- no scroll node solely because of overflow;
- descendants can paint outside the box subject to ancestor clips.

### `CLIP`

- clip at the overflow clip edge;
- no scrolling;
- no scroll offset.

### `HIDDEN`

- clip to padding box;
- owns scroll geometry;
- direct user scrolling is disabled;
- programmatic scroll state is supported.

### `SCROLL`

- clip to padding box;
- owns scroll geometry;
- user scrolling is enabled for configured axes.

Higher-level `AUTO` behavior uses scroll-container semantics and shows scroll UI only when final scroll range is non-zero.

## 23. Scroll chaining

Wheel/gesture scrolling is a default action.

The target event dispatch occurs first.

If not prevented, the scroll default action:

1. finds the nearest eligible scroll container on the event path;
2. consumes the portion of the delta that fits within its range;
3. updates its scroll node/scroll transform;
4. forwards any residual delta to the next eligible ancestor scroll container;
5. stops when the delta is fully consumed or no eligible ancestor remains.

No consumed scroll delta requests Taffy layout or paint recording.

## 24. Hit-test artifact

Hit testing is not derived by scanning raw `Element` bounds independently from painting.

`PrePaint` builds a retained `HitTestArtifact` from hit-region sources using the same property state and final stacking order.

Each `HitRegion` contains:

```text
owner Element
local hit shape
TransformNode
ClipNode
paint-order index
hit-test behavior
```

A hit region can exist without a paint item.

A paint item can exist without a hit region.

This preserves transparent interactive controls and decorative non-interactive content.

## 25. Hit-test eligibility

Hit testing distinguishes target eligibility from visibility/painting.

A hit-region source can opt itself out of targeting without removing eligible descendants.

Core behavior:

- `Display.NONE` suppresses the entire subtree;
- non-invertible transform suppresses the affected subtree;
- an effective clip rejects points outside the clip;
- target-disabled regions are skipped;
- descendants can remain targetable when an ancestor has no self hit region;
- opacity `0` does not by itself suppress hit testing.

A component that needs subtree-wide input suppression installs an explicit input-suppression behavior rather than relying on opacity.

## 26. Hit-test algorithm

For root-space point `p`:

```text
1. test top-layer regions from topmost to bottommost
2. then normal regions from topmost to bottommost
3. skip target-ineligible regions
4. reject if p is outside the effective clip
5. resolve the accumulated transform
6. if non-invertible, reject
7. map p through inverse transform into local coordinates
8. test local hit shape / custom HitRegionSource
9. first successful region supplies the Element target
```

This is the same visual ordering used by paint replay, reversed.

Custom hit shapes can represent:

- rectangles;
- rounded rectangles;
- paths;
- content-specific geometry;
- scene hit tests/ray casts.

A complex 2D/3D scene remains one atomic UI paint item but can perform its own local hit test and return scene-level interaction data through its component subsystem.

## 27. Pointer targeting

Normal pointer target selection is:

```text
root pointer coordinates
→ RenderRuntime.hitTest(...)
→ Element target
→ event propagation path from logical Element ancestry
```

If pointer capture is active for the pointer ID:

```text
capturing Element
```

replaces the normal hit-test result as the event target.

The render runtime does not implement capture. It supplies the normal target to the event runtime.

## 28. Coordinate conversion API

The render runtime exposes internal coordinate conversion required by input and component logic:

```kotlin
fun localToRoot(
    element: Element,
    local: Point
): Point

fun rootToLocal(
    element: Element,
    root: Point
): Point?

fun localRectToRoot(
    element: Element,
    local: Rect
): Rect
```

`rootToLocal` is empty when the accumulated transform is non-invertible.

Conversions use the same transform property tree as painting and hit testing.

No duplicate coordinate transform implementation is allowed in the event layer.

## 29. Visual bounds and culling

Each retained paint record has a local visual bounds rectangle.

`PrePaint` maps this through the transform tree and intersects it with effective clips to derive a root-space visible bounds estimate.

The backend can skip replay of chunks/items whose visible bounds do not intersect the root viewport clip.

Effects that can expand pixels, such as blur/glow, expand visual bounds according to their effect metadata.

Visual/ink bounds are separate from scrollable overflow.

## 30. Effect isolation and render passes

Effects that operate on a group result require an isolated intermediate render target.

Examples:

- group opacity;
- mask;
- filter;
- non-trivial blend/composite mode.

The backend receives effect-group boundaries from the retained artifact/property trees and creates render passes/FBOs only where required.

A simple group with identity effects does not require an offscreen target.

Embedded backend-specific 3D scenes are atomic paint content from the surrounding UI stacking perspective. Their internal depth buffer and 3D ordering remain inside the scene command/pass.

## 31. Paint recording cache

Each paint source has a stable paint identity.

The runtime retains its recorded paint until one of these occurs:

- paint source is disposed;
- paint-affecting data changes;
- size-dependent local geometry changes;
- backend/resource contract invalidates the record.

Property-only updates reuse the record.

Structural reorder and stack-level changes reuse the record.

A repaint replaces only the affected source's retained record and the affected paint chunks.

## 32. Dirty propagation

Dirty flags are owned by their subsystem.

### Layout dirty

Handled by Taffy.

### PrePaint dirty

Set by:

- committed layout geometry changes;
- transform/clip/effect topology changes;
- stacking changes;
- top-layer changes;
- hit-region changes;
- scroll-range-affecting transform changes.

### Paint dirty

Set by a paint source whose recorded drawing changed.

### Composite/property dirty

Set by value-only changes that can use existing property-tree topology:

- scroll offset;
- transform matrix;
- opacity;
- compatible effect parameters.

The scheduler coalesces repeated invalidations during one reactive wave.

## 33. Scheduler integration

RenderRuntime participates after the root has staged effective layout/render mutations.

Relevant ordering constraint:

```text
Taffy layout when requested
→ commit LayoutChanges
→ afterLayout
→ PrePaint/property-tree update
→ semantic/autofill geometry commit
→ repaint dirty PaintSources
→ commit PaintArtifact + HitTestArtifact
→ platform replay
```

`INTEGRATION_PROOF_SPEC.md` is the sole authority for the complete root scheduler.

The active platform backend replays the most recently committed artifact when the host requests a frame.

A property-only frame update can update retained property-node values and replay without a Taffy compute or paint re-record.

An `afterLayout` write that affects layout schedules another layout wave and does not re-enter the completed layout operation.

## 34. Failure and commit semantics

`PrePaint` and paint build a new generation of changed derived state before commit.

If paint recording for a dirty source throws:

- the previous committed paint artifact remains valid;
- the failing generation is not partially installed for that source;
- the exception propagates through the UI runtime error boundary.

Property-tree updates that are committed before replay must leave the trees internally consistent.

Native backend exceptions are isolated at the frame-render boundary and cannot leave backend matrix/clip/effect state unbalanced for later UI roots.

## 35. Required tests

### Tree/projection

- `Fragment` contributes no paint item/property node solely by existing;
- logical ancestry remains unchanged by render projection;
- a content leaf creates paint output without adding properties to `Element`;
- transparent interactive component can create a hit region without paint;
- decorative paint can exist without a hit region.

### Paint order and stacking

- default siblings paint in source order;
- equal stack levels preserve source order;
- negative/default/positive stack levels order correctly;
- child stacking context is atomic;
- transform creates an isolated stacking context;
- effect isolation creates an atomic context;
- order-only changes reuse cached paint records.

### Property trees

- nested transforms accumulate correctly;
- local-to-root and root-to-local are inverse for invertible transforms;
- non-invertible transforms neither paint nor hit-test;
- nested clips intersect correctly in root space;
- clip defined in a transformed coordinate space follows its transform;
- group opacity applies after child compositing;
- scroll node owns a `translate(-offset)` transform.

### Layout integration

- position-only Taffy change causes no repaint;
- size change updates `BoxGeometry` and repaints size-dependent content;
- padding/border changes update content/padding boxes and clips;
- `Display.NONE` removes paint and hit-test participation;
- no render transform mutates a Taffy `Layout`.

### Overflow and scroll

- `VISIBLE` does not clip;
- `CLIP` clips and cannot scroll;
- `HIDDEN` clips, permits programmatic offset, and rejects direct user scroll;
- `SCROLL` clips and accepts user scroll;
- scrollport remains fixed while descendants translate;
- transformed descendants can extend but not shrink base Taffy scrollable overflow;
- ink-only visual effects do not extend scroll range;
- transform-only scroll-range update runs without Taffy compute;
- residual wheel delta chains to an ancestor scroll container.

### Paint invalidation

- content color/text/image update with stable intrinsic size repaints but does not layout;
- scroll offset update neither layouts nor repaints;
- transform matrix update neither layouts nor repaints clean content;
- opacity update neither layouts nor repaints clean content;
- stacking reorder does not repaint clean content;
- unchanged paint record survives many frame replays.

### Hit testing

- topmost eligible region wins;
- target-disabled top item reveals eligible item below;
- transparent opacity-zero item remains hit-testable;
- clip excludes otherwise matching region;
- scroll offset affects hit target through the same transform used for paint;
- custom inverse-transformed local hit shape works;
- pointer capture overrides the normal hit result in the event layer;
- `Fragment` is never a direct hit surface.

### Top layer

- top layer paints above normal root content regardless of ordinary ancestor stack levels;
- ordinary ancestor clip/opacity outside the top-layer subtree does not affect it;
- top-layer order is stable and last entry is topmost;
- backdrop paints immediately below its associated entry;
- logical event ancestry remains original;
- top-layer layout root attaches to synthetic Taffy root.

### Caching and commit

- one dirty paint source does not re-record unrelated content;
- property-only changes reuse all paint records;
- failed paint generation leaves previous committed record usable;
- backend replay restores transform/clip/effect state after every chunk/group.

## 36. Phase exit criteria

This phase is complete when:

- Taffy4J remains the only system that measures, constrains, sizes, wraps, positions, or reflows layout boxes;
- `PrePaint` operates exclusively on immutable committed Taffy geometry and never acts as a second layout solver;
- rendering consumes committed Taffy geometry without introducing a second layout solver;
- `Element` remains free of universal visual/render fields;
- paint output is retained and replayable across frames;
- transform, clip, effect, and scroll state are hierarchical and independently updatable;
- stacking contexts provide deterministic atomic paint order;
- scrolling and transforms update without repainting unchanged content;
- transformed geometry extends scrollable overflow without modifying Taffy flow layout;
- hit testing uses the same paint order/property state as rendering;
- transparent layout-only interactive regions are supported;
- pointer targeting composes correctly with pointer capture;
- top-layer content escapes ordinary ancestor clipping while preserving logical event ancestry;
- required tests pass.

## 37. Animated effective render values

`ANIMATION_RUNTIME_SPEC.md` supplies sparse effective animated property values before subsystem projection.

Transform/effect/clip/paint changes are routed through the same retained render property state and paint invalidation rules as ordinary resolved-style changes.

Animation does not create a parallel render tree or hit-test geometry model.


## Platform backend replay

`lumentika-core` defines a backend replay boundary for committed `PaintArtifact` generations.

A backend implementation consumes paint chunks, property-tree state, and typed opaque backend commands. It does not own component state, layout, style precedence, or hit testing.

A clean artifact may still be replayed every host frame without being re-recorded.

Platform-specific extraction/GPU details belong exclusively to the platform module.

## Integration proof authority

`INTEGRATION_PROOF_SPEC.md` verifies retained paint reuse, property-only updates, hit-test parity, top-layer semantics, and backend-neutral replay in the complete core runtime.


## Extensible styled paint

Style-owned visual fills use immutable `Paint`.

Core Paint implementations record backend-neutral commands.

Backend modules may provide Paint implementations that record opaque backend commands without changing style/layout semantics.

Platform modules may provide additional typed `Paint` implementations. `lumentika-core` contains no platform-specific Paint type.

Layered paint preserves declaration order.

Paint resource changes invalidate paint/backend state without changing geometry unless intrinsic metrics independently changed.

Concrete skinning requirements live outside core; core Paint semantics remain unchanged.

## Semantics geometry projection

`SemanticsRuntime` receives final root-space geometry from the same committed render property state used by hit testing.

For a semantic node, render runtime can provide:

```text
transformed root bounds
clip-intersected visible bounds
scroll-adjusted bounds
visibility/participation information
```

Transform/scroll/clip changes can therefore invalidate semantic geometry without requesting Taffy layout or repainting unchanged content.

Semantic geometry never feeds back into layout or paint order.
