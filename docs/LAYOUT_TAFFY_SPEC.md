# Taffy4J Layout Integration Specification

## 1. Scope

Taffy4J is the authoritative layout engine for the UI runtime.

This phase defines:

- ownership and lifetime of the Taffy tree;
- projection of the persistent UI tree into Taffy nodes;
- layout style synchronization;
- intrinsic content measurement;
- text wrapping;
- flex/grid/block layout delegation;
- overflow and scroll geometry;
- layout invalidation and scheduling;
- retained layout snapshots;
- root sizing and coordinate semantics;
- interaction with `afterLayout`.

Rendering, typed style resolution, transitions, and platform replay are separate subsystem phases. This document defines the layout boundary inside `lumentika-core`.

## 2. Core invariants

1. Taffy4J is the only layout solver.
2. Once a Taffy layout generation is committed, width, height, intrinsic measurement, wrapping, box positions, and flow relationships are final for that generation.
3. Render/`PrePaint` phases may not refine, correct, or recompute those layout results. They consume them as immutable geometry.
4. The UI runtime does not implement parallel flex, grid, block, intrinsic sizing, or base overflow geometry algorithms.
5. `Element` does not gain a public layout-property bag.
6. Layout participation is an internal runtime association supplied by the component/content implementation.
7. `Fragment` and other boxless structural nodes never receive Taffy nodes.
8. Layout geometry cached by the UI runtime is a mirror of Taffy output and is never independently solved or mutated.
9. A layout pass runs only after a layout-affecting mutation or root viewport change.
10. At most one Taffy compute is performed for a root during one scheduler flush.
11. Scroll offsets, render transforms, hover/focus state, and paint-only changes do not invalidate Taffy layout.
12. The same `TaffyTree` must never be re-entered from its measurement callback.

## 3. Root ownership

Each mounted `UiRoot` owns exactly one:

```kotlin
TaffyTree<MeasureHandle>
```

The tree:

- is created when the root mounts;
- is used only on the UI thread;
- is never shared between roots;
- is closed when the root is disposed.

`UiRoot` also owns the framework-side layout runtime state:

```text
LayoutRuntime
├─ TaffyTree<MeasureHandle>
├─ synthetic root NodeId
├─ Element -> LayoutNode side table
├─ NodeId  -> LayoutNode side table
├─ pending style updates
├─ pending topology updates
├─ pending intrinsic-dirty nodes
└─ layoutRequested
```

The mappings are side tables owned by the layout runtime. `Element` remains free of a generic mutable layout-property bag.

## 4. Synthetic root

Every `UiRoot` has one internal synthetic Taffy root.

The synthetic root:

- is not a user-visible `Element`;
- establishes the containing block for the screen;
- allows a component root to be a `Fragment` or multiple top-level elements;
- receives top-layer layout roots through the render/runtime projection override;
- uses `Display.BLOCK`;
- has a definite width and height equal to the current logical UI viewport;
- has zero margin, border, padding, and scrollbar gutter.

On viewport resize, the synthetic root style and root available space are updated in the same layout flush.

Layout is computed with:

```kotlin
Size(
    AvailableSpace.definite(viewportWidth),
    AvailableSpace.definite(viewportHeight)
)
```

## 5. Layout participants

A mounted UI node participates in Taffy only when its implementation declares that it generates a layout box.

This is an internal runtime capability, not a universal set of setters on `Element`.

Conceptually:

```text
Element
  └─ optional runtime LayoutNode association
```

A `LayoutNode` contains runtime integration data such as:

```text
LayoutNode
├─ owning Element
├─ Taffy NodeId
├─ applied immutable Style
├─ optional MeasureHandle
└─ current Layout snapshot
```

The representation is private to the layout runtime; the ownership and identity semantics above are fixed.

Typical layout participants include:

- container nodes created by layout-oriented components;
- content-backed leaves such as text and images;
- platform-specific content leaves when they require a box.

A component boundary does not create a Taffy node by itself.

## 6. Boxless structural projection

`Fragment` never receives a Taffy node.

For each layout participant, its direct Taffy children are the first descendant layout participants reachable through any number of boxless structural nodes.

Example UI tree:

```text
A            layout participant
├─ Fragment  boxless
│  ├─ B      layout participant
│  └─ Fragment
│     └─ C   layout participant
└─ D         layout participant
```

Taffy topology:

```text
A
├─ B
├─ C
└─ D
```

This projection preserves source order.

Structural changes under a boxless node invalidate the projected child list of the nearest layout ancestor.

A root registered in the UI top layer uses the synthetic Taffy root as its projected layout parent while preserving its original logical `Element` ancestry. `RENDER_HIT_TEST_SPEC.md` defines top-layer paint and event semantics.

## 7. Node identity and structural mutation

A mounted layout participant receives one Taffy `NodeId` and keeps it for its mounted lifetime.

Keyed `forEach(...)` reorder must preserve the existing `NodeId` for surviving elements.

Structural mutation is staged until the layout flush.

For each affected layout parent, the runtime computes its final projected Taffy child list and applies it with one:

```kotlin
tree.setChildren(
    parentNode,
    finalChildren
)
```

The runtime does not replay intermediate insert/remove/reorder operations.

Unmount uses deterministic post-order cleanup:

1. remove the subtree root from its surviving Taffy parent projection;
2. remove descendant Taffy nodes in post-order;
3. delete both framework-side mappings;
4. release layout-owned measurement state.

No layout compute occurs between intermediate structural mutations inside one scheduler flush.

## 8. Layout styles

The framework's resolved typed layout properties are the public/source-of-truth style representation.

Taffy `Style` is an internal immutable projection snapshot applied to each Taffy node.

The framework does not expose Taffy4J types in consumer APIs and does not duplicate any layout algorithm. `LayoutStyleProjection` converts framework-owned layout values into one final internal Taffy `Style` per affected node. Component/content runtime metadata contributes internal layout facts such as intrinsic/replaced-element semantics.

Layout styles may contain any layout capability supported by Taffy4J, including:

- `Display.BLOCK`, `FLOW_ROOT`, `FLEX`, `GRID`, `NONE`;
- size, min-size, max-size;
- margin, padding, border width;
- relative/absolute positioning and inset;
- aspect ratio;
- flex direction, wrap, basis, grow, shrink;
- alignment and gaps;
- grid tracks/placement/auto-flow;
- per-axis overflow;
- scrollbar gutter width.

`Element` itself does not expose these as universal public properties.

### Public dependency boundary

No public API under the UI framework exposes `com.antepod.taffy.*`.

Taffy4J is a direct implementation dependency of `lumentika-core` and the sole layout solver. Consumers declare the UI core/distribution dependency rather than a separate layout backend. No public layout-provider SPI exists.

The internal adapter is conceptually:

```kotlin
internal class LayoutStyleProjection {
    fun project(
        style: ResolvedLayoutStyle,
        metadata: LayoutNodeMetadata
    ): com.antepod.taffy.style.Style =
        projectResolvedStyle(style, metadata)
}
```

`LayoutStyleProjection` and every Taffy4J type in the UI implementation are non-public implementation details.

### Style update batching

Reactive changes may produce several layout-style changes during one reactive wave.

The layout runtime:

1. keeps only the final `Style` for each affected node;
2. ignores a style equal to the currently applied style;
3. applies changed styles using `tree.setStyles(nodes, styles)` when multiple nodes are pending;
4. does not additionally call `markDirty` for style changes.

Taffy owns cache invalidation caused by `setStyle`, `setStyles`, and hierarchy mutation.

## 9. Intrinsic measurement

Taffy lays out boxes but framework-owned content determines intrinsic sizes for text, images, embedded controls, and similar leaves.

The content boundary exposes intrinsic measurement as an optional capability.

Conceptually:

```kotlin
interface IntrinsicMeasurable {
    fun measure(
        input: IntrinsicMeasureInput
    ): Size<Float>
}

data class IntrinsicMeasureInput(
    val knownDimensions:
        Size<Float?>,
    val availableSpace:
        Size<MeasureSpace>
)
```

`MeasureSpace` has exactly three semantics:

```text
Definite(value)
MinContent
MaxContent
```

The Taffy bridge maps them one-to-one to `AvailableSpace.DEFINITE`, `MIN_CONTENT`, and `MAX_CONTENT`.

A content leaf without intrinsic sizing does not need a `MeasureHandle`.

### Shared callback

The root uses one shared Taffy `MeasureFunction<MeasureHandle>` for the complete tree.

A stable `MeasureHandle` points at the current measurable content and its measurement cache/revision.

The callback:

1. receives Taffy's `knownDimensions` and `availableSpace`;
2. resolves the stable `MeasureHandle` from node context;
3. converts Taffy constraints to `IntrinsicMeasureInput`;
4. calls the content measurer;
5. preserves every known dimension in the returned result;
6. returns a finite content-box size.

The callback must not query or mutate the same `TaffyTree`.

### Intrinsic invalidation

When intrinsic data changes without a Taffy style change, for example:

- text contents change;
- font changes;
- image intrinsic dimensions become known;
- an embedded control changes its natural size;

the runtime:

1. updates the stable measurement state/revision;
2. invalidates the relevant measurement cache entries;
3. calls `tree.markDirty(node)` exactly once for the pending flush;
4. requests layout.

Changing intrinsic data must not replace the Taffy node or node context merely to force invalidation.

## 10. Text wrapping

Text wrapping is intrinsic content measurement, not a Taffy layout algorithm.

`TextContent` must interpret the complete measurement input.

For wrapping text:

### Definite inline space

With a definite available width, text is laid out with that width as the wrapping constraint unless a known width already fixes the content box.

The measured height is derived from the resulting lines.

### Max-content

`MAX_CONTENT` measures the text without optional soft wrapping.

Explicit line breaks still create lines.

### Min-content

`MIN_CONTENT` returns the smallest intrinsic inline contribution allowed by the active line-breaking policy.

### No-wrap

With wrapping disabled, optional soft wrapping is never introduced. The intrinsic width may therefore exceed a definite available width and contribute to overflow.

### Known dimensions

A known dimension supplied by Taffy is authoritative.

If width is known, line layout uses that width when computing the dependent text height. If height is known, the returned height remains that value.

### Cache

Text shaping/line-breaking is cached outside Taffy.

The cache key must include every input that affects line layout, including at minimum:

```text
text revision
font identity/revision
font size or scale
wrap/line-breaking policy
known width/height
available width/height kind and definite value
line-height-affecting data
```

Measurement may run multiple times with different candidate constraints during one Taffy compute. The renderer must therefore not assume that the last measurement callback represents the final layout.

At render time, the text renderer uses the final Taffy content-box width and reuses a matching cached text layout when available.

## 11. Replaced intrinsic content

Content such as images and form-like native controls may require replaced-element sizing semantics.

The component that creates such a Taffy node sets:

```kotlin
itemIsReplaced(true)
```

when the content's sizing behavior is that of a replaced element.

Text is not replaced content.

The generic layout runtime does not guess this from Kotlin class names; the content/component implementation declares the sizing semantics.

## 12. Flexbox, Grid, Block, and positioning

The UI runtime does not implement layout algorithms above Taffy.

### Flex wrapping

Flex wrapping maps directly to:

```text
FlexWrap.NO_WRAP
FlexWrap.WRAP
FlexWrap.WRAP_REVERSE
```

No UI-side line-building or child repositioning is allowed.

### Grid and block

Grid and block layout use Taffy's native algorithms through the same node/style/tree integration.

### Absolute and relative positioning

Positioning remains part of Taffy `Style`. The UI runtime consumes only the resulting layout.

No separate absolute-position solver is added.

## 13. Overflow layout semantics

Overflow is per axis and maps to Taffy's:

```text
VISIBLE
CLIP
HIDDEN
SCROLL
```

Taffy overflow values control layout semantics only. They do not perform rendering clips or scroll-offset translation.

Important Taffy layout effects are preserved:

- `VISIBLE` allows overflowing descendants to contribute to ancestor scroll regions and keeps content-based automatic minimum sizing;
- `CLIP` excludes overflow from ancestor scroll regions while keeping content-based automatic minimum sizing;
- `HIDDEN` establishes scroll-container sizing semantics with zero automatic minimum size for flex/grid items;
- `SCROLL` has the same zero automatic minimum-size behavior and may reserve a scrollbar gutter using `scrollbarWidth`.

## 14. Scroll containers

Scrolling is implemented as a component/runtime behavior backed by Taffy overflow geometry.

A scroll component creates or owns a layout participant whose relevant axis uses:

```kotlin
Overflow.SCROLL
```

The default UI scrollbar is overlay-style:

```kotlin
scrollbarWidth(0.0f)
```

Therefore scrollbar visuals do not consume layout space unless a component explicitly requests a non-zero gutter.

### Base scroll extent

After Taffy layout, the base layout overflow comes from the node's `Layout`:

```kotlin
val baseMaxX =
    layout.scrollWidth()

val baseMaxY =
    layout.scrollHeight()
```

The runtime never computes base layout overflow by summing child sizes.

`Layout.contentSize` and `Layout.scrollbarSize` remain available for scrollbar/thumb calculations.

Render transforms do not alter Taffy flow geometry. `RENDER_HIT_TEST_SPEC.md` may derive an additional transformed-overflow reachability extent from committed rectangles. That value is not layout: it cannot resize/reposition boxes, trigger wrapping, or alter Taffy results.

### Scroll state

The scroll component owns mutable runtime state:

```text
scrollX
scrollY
```

After layout and `PrePaint` establish the final scrollable-overflow rectangle, offsets are clamped to:

```text
0 <= scrollX <= finalMaxX
0 <= scrollY <= finalMaxY
```

Changing only a scroll offset:

- does not call `markDirty`;
- does not update Taffy style;
- does not request layout.

The offset is consumed by the scroll transform defined in `RENDER_HIT_TEST_SPEC.md`.

### Auto behavior

If a higher-level component exposes `AUTO` semantics, it is a UI behavior rather than a Taffy enum.

With the default zero-width overlay scrollbar gutter, its Taffy layout representation is `Overflow.SCROLL`. Taffy provides the base range and `PrePaint` adds any transformed-overflow extension before scrollbar visibility is resolved.

### Nested scrolling

Nested scroll containers consume only the portion of a wheel/gesture delta that their current range can accept.

Any residual delta continues through the existing event propagation path to an eligible ancestor scroll container.

This changes scroll state only and does not trigger layout.

## 15. Retained layout snapshots

Render, hit testing, focus geometry, and `afterLayout` must not perform one JNI `layout(node)` call per element.

Each `LayoutNode` retains the latest Taffy `Layout` snapshot in JVM memory.

The snapshot includes:

```text
order
location
size
contentSize
scrollbarSize
border
padding
margin
```

It is a read-only mirror.

A newly mounted layout node starts with `Layout.zero()`.

The UI runtime never manually edits this snapshot.

## 16. Layout-change tracking

When a layout pass is requested, the runtime uses the measured tracked compute:

```kotlin
val changes =
    tree.computeLayoutWithChanges(
        syntheticRoot,
        rootAvailableSpace,
        sharedMeasureFunction
    )
```

The explicit returned `LayoutChanges` batch is used instead of installing a `LayoutChangeListener`.

For every returned change:

1. resolve the owning `LayoutNode` from `NodeId`;
2. replace its retained snapshot with `change.newLayout()`;
3. record downstream geometry invalidation when `geometryChanged()`;
4. update scroll state when size, content size, or scrollbar size changed;
5. forward order/border/padding/margin changes to the render invalidation mapping.

New nodes start with `Layout.zero()`, matching Taffy4J tracked-compute comparison semantics.

Unchanged nodes keep their existing snapshots.

The runtime applies the entire returned batch before any `afterLayout` effect runs.

## 17. Layout scheduling

`UiRoot` owns a boolean/layout-generation request state.

Layout is requested by:

- mount/unmount/reorder/reparent of layout participants;
- a changed Taffy `Style`;
- changed intrinsic measurement data;
- root viewport size change;
- top-layer layout-parent attachment/detachment.

Layout is not requested by:

- paint-only content changes with stable intrinsic size;
- pointer movement;
- hover/active/focus state that does not alter layout style;
- scroll offset changes;
- render-only transforms;
- ordinary state writes that have no layout-dependent consumer.

The runtime does not poll `tree.dirty(root)` every frame. It already knows when it performs layout-affecting mutations.

### Flush ordering constraint

LayoutRuntime receives already-staged topology/style/intrinsic mutations from the root scheduler.

```text
stage final layout mutations
→ apply Taffy mutations
→ if layoutRequested:
     computeLayoutWithChanges(..., sharedMeasureFunction)
     commit returned Layout snapshots
→ afterLayout
→ PrePaint consumes committed geometry
```

`INTEGRATION_PROOF_SPEC.md` is the sole authority for the complete root scheduler.

If an `afterLayout` effect performs a layout-affecting write, it schedules another reactive/layout wave. It does not re-enter the completed layout pass.

## 18. Change tracking and performance policy

Tracked layout is used only on an actual layout pass.

There is no unconditional per-frame `computeLayoutWithChanges`.

The phase intentionally relies on Taffy's internal dirty/cache propagation rather than trying to compute a framework-side dirty subtree.

Layout computes from the synthetic root whenever layout is requested. This preserves correct ancestor/sibling interactions while allowing Taffy to reuse cached branches internally.

Boundary traffic is minimized by:

- batching style writes with `setStyles`;
- reducing structural synchronization to one final `setChildren` call per changed layout parent;
- preserving `NodeId` across keyed reorder;
- keeping measurement contexts stable;
- marking only intrinsically changed measured leaves dirty;
- receiving all observable layout changes in one tracked compute result;
- retaining framework layout snapshots for render/hit-test hot paths.

## 19. Rounding and logical coordinates

The UI layout runtime disables Taffy's pixel rounding:

```kotlin
tree.disableRounding()
```

Authoritative layout geometry is floating-point logical UI geometry.

Reasons for this contract:

- flex/grid distribution may legitimately produce fractional coordinates;
- layout animation must not become quantized to whole logical pixels;
- render transforms remain independent from layout;
- backend-specific pixel snapping belongs to the renderer/content that knows whether snapping is desirable.

A platform backend may snap specific pixel-art or raster output at replay time without altering layout geometry.

`computeLayoutWithChanges` therefore compares unrounded final layouts.

## 20. Coordinate model

Taffy `Layout.location` is retained as local layout geometry relative to the layout parent.

The render/hit-test system derives screen coordinates through the render transform property tree.

Conceptually:

```text
Taffy local layout
→ layout translation transform nodes
→ scroll transforms
→ component render transforms
→ root/screen coordinates
```

Taffy is not used for render transforms.

Hit testing uses the inverse of the same accumulated transform chain as defined in `RENDER_HIT_TEST_SPEC.md`.

## 21. Display none

`Display.NONE` is delegated to Taffy.

A subtree whose effective layout node is `Display.NONE` does not participate in rendering, hit testing, focus traversal, or pointer targeting while hidden.

The runtime does not emulate hidden layout with zero sizes of its own.

## 22. Failure behavior

Measurement exceptions propagate from the Taffy compute.

If a compute fails:

- no returned `LayoutChanges` batch is applied;
- retained framework layout snapshots remain at their previous committed values;
- the scheduler reports the failure;
- a subsequent successful flush recomputes the dirty layout.

The measurement callback must return finite sizes and must not re-enter the same `TaffyTree`.

## 23. Required tests

### Tree projection

- nested `Fragment` nodes flatten into the correct Taffy child order;
- component boundaries add no accidental layout box;
- multiple top-level roots mount under the synthetic root;
- keyed reorder preserves Taffy `NodeId`;
- unmount releases all corresponding native nodes.

### Style synchronization

- repeated writes in one reactive wave produce one final style per node;
- unchanged equal `Style` values cause no Taffy update;
- multi-node changes use the batched style path;
- style mutation requests one layout pass.

### Intrinsic measurement

- measurable content receives definite, min-content, and max-content constraints correctly;
- known dimensions are preserved;
- content changes call `markDirty` and relayout without replacing the node;
- unchanged intrinsic data does not remeasure after an unrelated cached pass;
- a measurement callback cannot re-enter the same tree.

### Text

- definite-width text wraps and increases height;
- max-content text performs no optional soft wrap;
- min-content returns the minimum line-breaking contribution;
- no-wrap content may overflow a definite width;
- explicit newlines work in all modes;
- render can reuse a cached layout keyed by final content-box width.

### Flex/Grid/Block

- `NO_WRAP`, `WRAP`, and `WRAP_REVERSE` match Taffy results;
- flex gaps/min/max/auto sizing work through Taffy;
- representative Grid and Block containers require no runtime special case;
- absolute positioning is produced solely by Taffy.

### Overflow and scroll

- `VISIBLE`, `CLIP`, `HIDDEN`, and `SCROLL` are passed per axis correctly;
- overflowing content updates `Layout.contentSize`;
- `scrollWidth()` and `scrollHeight()` define the base clamp range before transformed-overflow extension;
- content shrink clamps an existing offset;
- changing scroll offset never requests layout;
- overlay scrollbar mode uses zero Taffy gutter;
- nested scroll passes residual delta to an ancestor.

### Scheduling and snapshots

- many layout-affecting writes inside one batch cause one root compute;
- paint-only state causes zero layout computes;
- every returned `LayoutChange.newLayout()` replaces the matching snapshot;
- unchanged nodes retain their previous snapshot;
- `afterLayout` observes the complete committed batch;
- writes from `afterLayout` schedule a subsequent pass rather than re-entering layout.

### Coordinates and rounding

- Taffy rounding is disabled for the root tree;
- fractional layouts remain fractional in retained snapshots;
- root resize updates the synthetic root and recomputes once;
- scroll/render transforms do not mutate retained Taffy layout.

## 24. Phase exit criteria

This phase is complete when:

- the persistent UI tree can be projected into one native Taffy tree without making `Element` a layout-property god object;
- structural and style changes are synchronized incrementally and batched;
- content measurement supports Taffy's full definite/min-content/max-content contract;
- text wrapping works under Taffy constraints;
- Flexbox wrapping is entirely delegated to Taffy;
- base scroll ranges come from Taffy `Layout`; render transforms can only extend render-time scroll reachability during `PrePaint`, never layout geometry, while offsets remain layout-independent runtime state;
- layout executes no more than once per root scheduler flush;
- render/hit-test consumers can use retained framework `Layout` snapshots without per-frame JNI geometry reads;
- `afterLayout` observes a fully committed layout generation;
- the required tests pass against real Taffy4J.


## Animated effective layout values

`ANIMATION_RUNTIME_SPEC.md` may produce transient effective values for compatible layout properties.

Those values enter the exact same internal Taffy style projection path as non-animated resolved values.

There is no interpolated geometry solver outside Taffy.

At most one Taffy layout computation occurs per root frame/flush even when several layout properties are actively animated.


## Integration proof authority

`INTEGRATION_PROOF_SPEC.md` verifies that every layout-affecting path reaches Taffy exactly once per root frame/flush and no later phase performs a second layout solution in the complete runtime.

## Environment-resolved lengths

Taffy4J receives only resolved framework layout values.

Environment-dependent units resolve before `LayoutStyleProjection`:

```text
Dp         -> UnitResolver.resolveDp(...)
Sp         -> UnitResolver.resolveSp(...)
PhysicalPx -> UnitResolver.resolvePhysicalPx(...)
Px         -> root logical coordinate value
```

When a `UiEnvironment.units.revisions` value changes, only compiled properties depending on that unit family become candidates for re-resolution. Semantic equality suppresses layout work when the resulting logical value is unchanged.

`Sp` conversion may be non-linear and is never implemented as `density * fontScale` inside core.

The resolved value then follows the normal style-diff and Taffy mutation path.

No environment unit is implemented inside Taffy4J.

## Text layout service integration

Intrinsic text measurement uses the current `TextLayoutService` and the same `TextLayoutRequest` configuration used for retained text rendering.

The measurement cache key includes all inputs that can change text metrics, including:

```text
text/styled runs
font identity/resource generation
resolved font size/weight/style
platform font-weight adjustment
letter spacing/line height
wrap configuration
available inline size
layout direction
locale where shaping/boundaries depend on it
```

A cached `TextLayoutResult` may be shared by intrinsic measurement, caret/selection geometry, and paint recording for the same generation/configuration.
