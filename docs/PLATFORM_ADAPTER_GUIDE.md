# Lumentika platform adapter guide

`lumentika-core` owns UI mechanics. A platform library owns scheduling, native input,
text shaping, image metadata, accessibility, and replay into its renderer.

## Required adapter surface

Construct one `UiRoot` with:

- `FrameScheduler`: enqueue one native frame callback. Call `root.frame(timeNanos)` from it.
- `RenderBackend`: replay the committed `PaintArtifact`.
- `TextLayoutService`: shape text and return platform-specific `TextLayoutResult` geometry.
- optional `TextInputService`: bridge IME sessions and send typed `TextEditCommand` values.
- optional `ImageService`: publish intrinsic image dimensions.
- `UiEnvironment`: publish viewport, units, input thresholds, insets, locales, theme, and
  capabilities.

Clipboard, feedback, cursor, accessibility, autofill, drag/drop, URI, and back services are
optional. Missing services have deterministic no-op behavior.

## Render replay

`RenderBackend.replay` receives immutable retained output. Iterate `artifact.chunks` in order.
For each chunk:

1. Resolve `PaintPropertyState` IDs in `artifact.trees`.
2. Apply transform, clip, scroll, opacity, blur, path-draw, and stacking state. `EffectNode`
   exposes blur radius plus path length/progress; map draw state to the backend's stroke-dash
   mechanism.
3. Replay `PaintCommand.Fill`, `FillRect`, `DrawText`, `DrawImage`, or platform-defined
   `PaintCommand.Backend`.
4. Restore native renderer state before the next chunk.

`DrawText.layout` is the exact `TextLayoutResult` used for intrinsic measurement. Renderer and
caret/hit geometry therefore share shaping output.

Game/world rendering can implement `SceneContent`. Core keeps scene objects outside `Element`,
uses local `HitRegionSource`, and exposes `UiRoot.raycast` for platform selection.

## Native input

Translate native events into:

- `UiRoot.dispatchPointer(PointerInput)`;
- `UiRoot.dispatchWheel(...)`;
- `UiRoot.dispatchKey(...)`;
- IME callbacks through the active `TextInputClient`.

Core performs hit testing, hover tracking, capture/target/bubble dispatch, pointer capture,
gesture arbitration, focus, default actions, scrolling, and text editing. Platform code must not
repeat hit testing or choose gesture winners.

## Minecraft mapping

A Minecraft adapter normally maps:

```text
game render tick          -> FrameScheduler / UiRoot.frame
GUI scale + window size   -> UiEnvironment.viewport + UnitResolver
GuiGraphics/shader calls  -> RenderBackend
font renderer             -> TextLayoutService + DrawText replay
resource locations        -> ImageSource / ImageService / DrawImage replay
mouse + keyboard          -> dispatchPointer / dispatchWheel / dispatchKey
char/IME callbacks        -> TextInputService
narrator                   -> AccessibilityAdapter
custom item/world preview -> BackendPaintCommand or SceneContent
```

No Minecraft or mod-loader type crosses the core public component API. Keep those types inside
the adapter module and backend command implementations.

## Adapter acceptance test

Before publishing a platform library, prove:

- text measurement and replay use the same platform layout object;
- pointer coordinates hit transformed/clipped content;
- wheel and drag scrolling move descendants without Taffy recomputation or paint recording;
- focused text fields open one IME session and close it before unmount;
- accessibility receives stable IDs and committed bounds;
- custom backend commands and scene raycasts remain platform-owned;
- blur and path-draw effect fields replay without paint re-recording;
- closing repeated roots returns ownership counters to baseline.
