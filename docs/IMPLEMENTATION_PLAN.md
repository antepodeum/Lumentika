# Lumentika Core Implementation Plan

## Phase 1 — Reactive graph

Implement `Readable`, `Mutable`, `State`, `Derived`, dynamic dependencies, equality suppression, cycle diagnostics, batching, and deterministic invalidation.

## Phase 2 — Scheduler, scopes, effects, and async

Implement root flush waves, `ComponentScope`, `effect`, cleanup, `untracked`, coroutine-backed `derivedAsync`, cancellation, and stale-generation suppression.

## Phase 3 — Component declaration runtime

Implement `Component`, `Prop`, `Binding`, `Event`, `Slot`, `SlotList`, required/default semantics, one-shot `view()`, structural `show`, keyed `forEach`, and context.

## Phase 4 — KSP Kotlin DSL

Implement `com.antepod:lumentika-ksp`: generated type-safe builders, static/Readable/reactive-lambda prop forms, `bindX`, events, slots, common `@DslMarker`, and compile-time API validation.

## Phase 5 — Primitive/content boundary

Implement persistent `Element`, `Fragment`, `Content`, intrinsic measurement, semantic/runtime attachment points, and backend-safe retained command recording.

## Phase 6 — Event, pointer, focus, and back runtime

Implement normalized pointer/keyboard events, capture/target/bubble/default action, committed-hit dispatch, pointer capture, focus, focus-visible/focus-within, focus repair, and scoped back handlers.

## Phase 7 — Platform environment and service contracts

Implement `UiEnvironment`, `UnitResolver`, `px/dp/sp/physicalPx`, viewport/unit revisions, layout direction, locale, accessibility preferences, motion scale, insets, lifecycle, gesture configuration, capability publication, frame scheduling, clipboard, feedback, cursor, accessibility, content transfer, autofill, URI, and back contracts.

## Phase 8 — Gestures and nested scrolling

Implement `GestureArena`, tap/double-tap/long-press/drag/pan/scale recognizers, velocity tracking, configured thresholds, nested pre/local/post scroll, fling, overscroll, scrollbar behavior, cancellation, and lifecycle ownership.

## Phase 9 — Semantics/accessibility runtime

Implement typed roles/states/actions, merge/clear rules, stable semantic identity, semantic artifact generation, geometry from committed transform/clip state, separate accessibility focus, live regions, collection/range metadata, and change tracking.

## Phase 10 — Text layout/editing runtime

Implement `TextLayoutService` contract, layout results, UTF-16 edit ranges, grapheme-aware editing, bidi caret mapping, `TextEditingController`, selection/composition, typed edit commands, text-input sessions, caret/selection geometry, clipboard/default actions, content transfer, and autofill metadata.

## Phase 11 — Taffy4J layout runtime

Implement the only layout path: stable tree projection, environment-resolved lengths, intrinsic bridge, text measurement integration, dirty/cache integration, committed geometry, scroll-range base data, and at-most-one compute per root frame.

## Phase 12 — Retained render/compositing/hit testing

Implement `PaintArtifact`, `HitTestArtifact`, transform/clip/effect/scroll/stacking property trees, top layer, retained chunks, invalidation classes, coordinate conversion, semantic geometry feed, and typed backend extension commands.

## Phase 13 — Typed styles/themes

Implement immutable `Style`, `StyleVar`, `Theme`, `StylePart`, Paint values, generated property catalog/IDs/masks, inheritance, environment-aware unit resolution, orthogonal impacts, fine-grained resolver, and grouped `ResolvedStyle` storage.

## Phase 14 — Animation and frame-dependent interaction

Implement tween/spring motion, typed transitions, sparse effective overlays, retargeting, root clock, motion-scale policy, layout animation through Taffy, and shared frame time for cursor blink/fling/overscroll.

## Phase 15 — Universal components

Implement exactly:

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

Each component integrates the applicable core events, focus, gestures, semantics, feedback, text editing, nested scrolling, StyleParts/themes, Taffy layout, and retained painting without importing native platform types.

## Phase 16 — Core integration proof

Complete `INTEGRATION_PROOF_SPEC.md` with a deterministic headless host using real Taffy4J and fake platform services.

Prove:

```text
reactive scheduling and disposal
one-shot component mounting and structural updates
event/focus/pointer capture ordering
gesture arbitration and nested scrolling
text editing/session/composition behavior
semantic actions and accessibility focus
unit/inset/lifecycle/capability updates without remount
Taffy at-most-once and no-second-layout rules
retained paint/hit/semantic geometry parity
style/theme/animation invalidation boundaries
absence of unnecessary layout/repaint work
full universal component behavior
leak-free repeated mount/unmount
```

Exit criterion: the core requires no concrete platform code to pass its complete acceptance suite.
