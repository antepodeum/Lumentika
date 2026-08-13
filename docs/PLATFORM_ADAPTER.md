# Platform adapter guide

A platform adapter turns `lumentika-core` into a usable renderer on a specific JVM host. The core
owns UI behavior; the adapter owns native scheduling, drawing, shaping, and services.

## Create the root

Construct one root per independent UI surface:

```kotlin
val root = UiRoot(
    initialEnvironment = environment,
    services = PlatformServices(
        frameScheduler = frameScheduler,
        units = unitResolver,
        textLayout = textLayoutService,
        textInput = textInputService,
        images = imageService,
        clipboard = clipboardService,
    ),
    backend = renderBackend,
)
```

Only `FrameScheduler` is required. The defaults make headless execution deterministic; production
adapters should provide accurate text layout and every capability they advertise in
`UiEnvironment.capabilities`.

## Scheduling

`FrameScheduler.requestFrame` must enqueue one native frame callback. Coalesce callbacks on the
platform side if its scheduler does not already do so. From the callback, invoke:

```kotlin
root.frame(monotonicTimeNanos)
```

Do not run layout or rendering independently. Publish viewport, units, direction, locales, color
scheme, accessibility preferences, motion scale, insets, gestures, capabilities, and lifecycle with
`root.publishEnvironment(updated)`.

## Render replay

`RenderBackend.replay` receives an immutable `PaintArtifact`. Iterate chunks in order and resolve
their transform, clip, scroll, and effect IDs through `artifact.trees`.

Replay these core commands:

- `PaintCommand.Fill` and `FillRect`
- `PaintCommand.DrawText`
- `PaintCommand.DrawImage`
- adapter-defined `PaintCommand.Backend`

Apply opacity, blur, and path-draw state from the effect tree. Restore renderer state between
chunks. The `TextLayoutResult` stored in `DrawText` must be the same shaped object used for intrinsic
measurement and caret geometry.

Use `BackendPaintCommand` for native drawing operations that do not belong in core. Use
`SceneContent` when custom content also needs local hit regions and `UiRoot.raycast` integration.

## Input bridge

Translate native events into `PointerInput`, `dispatchWheel`, `dispatchKey`, and active
`TextInputClient` callbacks. Preserve pointer IDs, buttons, pressure, modifiers, historical samples,
and monotonic timestamps when the host exposes them.

Do not repeat core hit testing or gesture selection in the adapter. The root handles capture,
capture/target/bubble propagation, hover, focus, gesture arbitration, scrolling, and control default
actions.

## Optional services

Implement the services needed by the host:

- `ClipboardService`
- `UiFeedbackService`
- `PointerCursorService`
- `AccessibilityAdapter`
- `TextInputService`
- `ImageService`
- `AutofillService`
- `ContentTransferService`
- `UriLauncher`
- `BackDispatcher`

An unavailable service should be `null`, and the matching capability should be false.

## Minecraft adapter mapping

```text
render callback             → FrameScheduler / UiRoot.frame
window and GUI scale        → UiEnvironment / UnitResolver
GUI graphics and shaders    → RenderBackend
font renderer               → TextLayoutService / DrawText replay
resource identifiers        → ImageSource / ImageService
mouse and keyboard          → pointer, wheel, and key dispatch
character or IME callbacks  → TextInputService
narrator                     → AccessibilityAdapter
item or world preview       → BackendPaintCommand / SceneContent
```

Keep Minecraft and mod-loader classes inside the adapter. Higher-level components should depend on
neutral values or adapter-owned abstractions.

## Acceptance checks

Before publishing an adapter, verify that:

- measurement, rendering, caret placement, and text hit testing share shaped text output;
- transformed and clipped content receives correct pointer targets;
- scrolling updates visual descendants without unnecessary layout or paint recording;
- a focused text field opens exactly one input session and closes it before unmount;
- accessibility receives stable IDs, committed bounds, state changes, and actions;
- custom backend commands and scene raycasts remain adapter-owned;
- opacity, blur, path draw, and structural transforms replay correctly;
- environment changes invalidate the expected frame work;
- repeatedly creating and closing roots returns ownership counters to baseline.
