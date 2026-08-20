# Interactive subtree removal findings

## Failure

A reactive route change can remove the element currently under the mouse. The next mouse event then
fails in `EventDispatcher.dispatch` with:

```text
IllegalArgumentException: Cannot dispatch to stale/out-of-root element
EventDispatcher.updateHover -> EventDispatcher.dispatch
```

The failure is deterministic: hover a control, activate it so reactive composition replaces its
branch, then move the pointer.

## Upstream defects

- `EventDispatcher.repairRemovedSubtree` and `FocusManager.repairBeforeRemoval` existed but no tree
  lifecycle called them.
- The event repair did not remove detached elements from `hoverPath`.
- Focus configuration retained detached elements.
- The immutable hit-test artifact can legitimately retain an element until the next render commit;
  `UiRoot.hitTest` exposed that stale target to immediate pointer and wheel events.
- Cleanup after detachment cannot safely emit `POINTER_LEAVE`, `BLUR`, or `FOCUS_OUT`, because event
  dispatch correctly rejects out-of-root targets.

## Required lifecycle contract

`UiRoot` must receive a notification before any mounted child subtree is detached. While parent links
and listeners are still valid it must:

1. repair focus and emit focus-exit events;
2. emit pointer-leave events for hovered elements, deepest first;
3. clear hover state, pointer capture, listeners, default actions, and focus registrations;
4. detach or dispose the subtree.

Until the next render commit, public hit testing and scene raycasting must filter entries that are no
longer mounted beneath the root. Native input arriving in that interval must report no target rather
than dispatching to the previous commit's detached element.

The observer must propagate to children appended later and must be removed from a detached subtree so
its independent mutations cannot affect the former root. The legacy `repairRemovedSubtree(Element)`
entry point must remain available for adapter compatibility, but cannot emit lifecycle events after
detachment.

## Regression coverage

- Removing a hovered, captured, focused subtree emits leave and repairs focus automatically.
- The following hover update does not dispatch to a stale element.
- Pointer and wheel input between subtree removal and the next frame ignores stale committed hits.
- The legacy post-removal cleanup remains safe.
- New descendants inherit the root lifecycle observer.

## Separate adapter findings

These are not core subtree-removal defects:

- A Minecraft screen must forward native mouse move/down/up/drag/wheel and keyboard/character events
  to `UiRoot`; rendering alone does not make controls interactive.
- A production Minecraft adapter must provide a native `FrameScheduler`. Using
  `HeadlessFrameScheduler` violates the platform-adapter contract and can delay hover repaint until a
  later game render tick.
- Forge resource-pack compatibility warnings are adapter metadata and unrelated to input dispatch.
