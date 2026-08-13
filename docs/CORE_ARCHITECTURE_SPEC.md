# Lumentika Core Architecture Specification

## 1. Goal

`com.antepod:lumentika-core` is a Kotlin-first retained UI engine and universal component library.

It owns all hard UI mechanics and exposes narrow host/render/input/resource/service contracts. Concrete platform libraries implement those contracts outside this artifact.

The core is complete without any native platform implementation.

## 2. Published artifacts

```text
com.antepod:lumentika-core
com.antepod:lumentika-ksp
```

Canonical package roots:

```text
com.antepod.lumentika.reactive
com.antepod.lumentika.component
com.antepod.lumentika.components
com.antepod.lumentika.event
com.antepod.lumentika.gesture
com.antepod.lumentika.text
com.antepod.lumentika.semantics
com.antepod.lumentika.style
com.antepod.lumentika.animation
com.antepod.lumentika.layout
com.antepod.lumentika.render
com.antepod.lumentika.platform
com.antepod.lumentika.codegen
```

`lumentika-ksp` is build-time code generation only. Runtime behavior does not require a compiler plugin.

## 3. Core ownership

```text
reactive graph / scheduler / scopes
component runtime / declarations / structural composition
universal components
events / pointer capture / focus
gesture arena / nested scrolling / fling
text layout and editing contracts
semantics/accessibility model
platform environment and service contracts
typed styles / themes / animation
Taffy4J layout runtime
retained paint/compositing/hit-test model
```

## 4. Taffy4J

Taffy4J is a direct implementation dependency of `lumentika-core` and is the sole layout solver.

There is no public layout-provider SPI.

```text
resolved framework layout/style values
→ internal LayoutStyleProjection
→ Taffy4J
→ committed framework geometry
```

No public API exposes `com.antepod.taffy.*`.

## 5. Universal components

Core ships the universal vocabulary:

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

Universal components own portable behavior and semantics, not native resources or drawing APIs.

For example, `button` owns focusability, activation, disabled behavior, semantic role/action, content, and StyleParts. A concrete renderer/theme decides how it looks.

## 6. Primitive boundary

Core owns persistent `Element`, boxless `Fragment`, terminal `Content`, intrinsic measurement, retained Paint commands, and render property trees.

Platform-specific content and paint commands may extend the retained command vocabulary through typed opaque extension points. They do not change Element semantics or layout ownership.

## 7. Platform boundary

A concrete host supplies only the capabilities required by core contracts:

```text
viewport/environment publication
UnitResolver
FrameScheduler
native input normalization
TextLayoutService
TextInputService
ClipboardService
UiFeedbackService
PointerCursorService
AccessibilityAdapter
ContentTransferService
AutofillService
UriLauncher
BackDispatcher
render-artifact replay
```

Core imports no native window toolkit, game engine, mod-loader, GPU backend, resource manager, or native accessibility API.

## 8. One runtime

A mounted `UiRoot` owns exactly one coordinated runtime:

```text
ReactiveRuntime
ComponentRuntime
EventRuntime / FocusManager
GestureRuntime
TextEditingRuntime
SemanticsRuntime
StyleRuntime
AnimationRuntime
Taffy layout tree
RenderRuntime
```

Platform adapters do not create a second component, event, focus, semantics, or layout tree.

## 9. Scheduler ownership

The only complete normative flush/frame order lives in `INTEGRATION_PROOF_SPEC.md`.

Subsystem specifications define only local ordering constraints.

Core guarantees at most one Taffy compute per root per frame/flush and does not permit render geometry to feed back into committed layout.

## 10. Dependency rules

```text
lumentika-ksp  -> lumentika-core declarations/model
platform libs  -> lumentika-core
lumentika-core -> no native platform or mod-loader dependency
```

Build enforcement:

```text
no native platform packages in core sources
no loader packages in core sources
no platform resource/render types in universal component APIs
Taffy4J remains internal implementation detail
headless tests exercise the full core with real Taffy4J
```

## 11. Acceptance criteria

Core is implementation-ready when:

- Taffy4J is the only layout authority;
- universal components are implemented without native types;
- environment/services are narrow typed contracts;
- gesture/nested-scroll behavior is core-owned;
- text editing state and commands are core-owned;
- semantics/accessibility structure is core-owned;
- retained rendering and hit testing share one transform/clip/order model;
- unit conversion, frame time, insets, lifecycle and capabilities are explicit host inputs;
- deterministic headless tests prove all subsystem ownership and absence-of-work constraints;
- repeated mount/unmount returns all runtime ownership counters to baseline.
