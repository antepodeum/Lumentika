# Gesture and Scroll Interaction Specification

## 1. Purpose

`lumentika-core` owns recognition and arbitration of platform-independent gestures built from normalized pointer events.

Platform modules translate native pointer streams and provide platform interaction thresholds/policies.

Universal components consume core gesture recognizers rather than implementing unrelated drag/tap logic independently.

## 2. Pointer foundation

Gesture recognition consumes the normalized pointer model from `EVENT_INPUT_FOCUS_SPEC.md`.

Each pointer sample includes at least:

```text
pointer id
type
position
timestamp
buttons/pressure where available
modifiers
```

Historical/coalesced samples may be supplied when a platform provides them.

## 3. Gesture sequence

A gesture sequence begins with pointer down and ends when all participating pointers are up/cancelled.

The runtime tracks active pointers independently from `Element` lifetime.

Unmounted recognizers are cancelled before disposal.

## 4. Gesture arena

For each pointer sequence, recognizers collected from the hit path enter a `GestureArena`.

A recognizer can:

```text
remain pending
accept
reject
cancel
```

Exclusive recognizers cannot simultaneously own the same pointer stream unless they explicitly belong to a cooperative team.

This prevents a slider drag and ancestor scroll drag from both treating the same movement as exclusive ownership.

## 5. Hit-path participation

Recognizers are registered from target toward ancestors according to component configuration.

Initial pointer target still comes from normal render hit testing.

Gesture recognition does not replace event capture/target/bubble dispatch.

Low-level pointer listeners can coexist with recognizers.

## 6. Platform gesture configuration

Thresholds are root environment values, not hardcoded constants.

```kotlin
data class GestureConfiguration(
    val touchSlop: Float,
    val doubleTapSlop: Float,
    val doubleTapTimeout: Duration,
    val longPressTimeout: Duration,
    val minimumFlingVelocity: Float,
    val maximumFlingVelocity: Float,
    val minimumScaleSpan: Float
)
```

A platform adapter maps its native interaction configuration into root logical units/time.

Tests can replace this configuration deterministically.

## 7. Tap recognizer

Supports:

```text
tap
double tap
multi-press count where requested
```

Tap rejects when movement exceeds configured slop or another exclusive recognizer wins.

Button activation can use tap/press behavior without platform-specific click logic.

## 8. Long press

Long press begins only after configured timeout while movement remains within accepted tolerance.

Cancellation occurs on:

```text
pointer cancel
movement rejection
winner conflict
unmount
root suspension policy where appropriate
```

Platform haptic feedback can be triggered through `UiFeedbackService`; the recognizer itself does not call native haptics.

## 9. Drag/pan recognizer

A drag recognizer tracks:

```text
start position
current position
delta
total displacement
velocity
axis policy
```

Axis policies:

```text
FREE
HORIZONTAL
VERTICAL
```

The recognizer normally accepts after touch slop is exceeded in an eligible direction.

## 10. Velocity tracking

Velocity is calculated from timestamped pointer samples.

The core algorithm is deterministic for a given sample stream/configuration.

Platforms may supply a calibrated velocity estimator only through an explicit capability if exact native feel is required; component semantics consume logical units/second.

Velocity is clamped to environment maximum fling velocity before fling policy.

## 11. Scale gesture

Multi-pointer scale recognition exposes:

```kotlin
data class ScaleGestureUpdate(
    val centroid: Point,
    val scaleDelta: Float,
    val accumulatedScale: Float
)
```

Scale recognition begins only after the configured minimum span/slop criteria.


## 12. Gesture teams

Recognizers that semantically cooperate may form a team.

Examples:

```text
scroll drag + scrollbar visual tracking
text selection drag + caret auto-scroll
pan + scale in a custom scene
```

Team membership is explicit and never inferred from element class names.

## 13. Scroll state

Core scroll containers own:

```kotlin
class ScrollState {
    val offsetX: Double
    val offsetY: Double
    val minX: Double
    val maxX: Double
    val minY: Double
    val maxY: Double
    val isScrolling: Boolean
}
```

Base ranges derive from committed layout + render overflow rules.

Offsets remain render/runtime state and do not mutate Taffy layout.

## 14. Scroll sources

Normalized sources:

```text
WHEEL
TOUCH_DRAG
PEN_DRAG
KEYBOARD
ACCESSIBILITY
PROGRAMMATIC
FLING
SCROLLBAR
```

Source information is available to scroll policy/feedback without changing range semantics.

## 15. Nested scroll protocol

Nested scroll is explicit core behavior.

Each scroll operation has pre-consumption, local consumption, and post-consumption stages.

```text
raw delta
→ ancestors preScroll
→ local scroll consumes
→ ancestors postScroll receive consumed + remaining
```

Conceptual contract:

```kotlin
interface NestedScrollConnection {
    fun preScroll(
        available: ScrollDelta,
        source: ScrollSource
    ): ScrollDelta

    fun postScroll(
        consumed: ScrollDelta,
        available: ScrollDelta,
        source: ScrollSource
    ): ScrollDelta
}
```

Consumed amounts can never exceed available amounts on an axis.

## 16. Fling protocol

Fling uses velocity rather than pointer delta.

```text
input velocity
→ preFling ancestors
→ local fling consumes supported velocity
→ postFling ancestors receive consumed + remaining velocity
```

An ancestor may consume a fling entirely.

Unsupported axes are not propagated as meaningful velocity.

## 17. Fling behavior

Core owns fling integration/runtime state.

Platform policy supplies parameters through environment configuration.

Conceptual:

```kotlin
interface FlingBehavior {
    fun start(
        state: ScrollState,
        velocity: ScrollVelocity
    ): FlingAnimation
}
```

The animation uses the root frame clock, not an external timer.

## 18. Overscroll

Range clamping and overscroll visualization are separate.

Core may track transient overscroll displacement/velocity when an enabled `OverscrollBehavior` requests it.

Platform/theme code may visualize that state as glow/stretch/bounce without changing scroll content geometry authority.

native platform can choose no overscroll effect.

## 19. Wheel scrolling

Platform wheel/trackpad deltas are normalized to logical pixel deltas before core dispatch.

The platform uses its native scroll factor where needed.

Wheel input does not pass through touch-slop recognition.

It enters nested scroll directly as `WHEEL`.

## 20. Scrollbar behavior

Universal scrollbar interaction is core-owned:

```text
thumb size/position derived from viewport/content range
thumb drag maps to scroll offset
track click policy
focus/keyboard semantics when interactive
```

Appearance remains StylePart/theme behavior.

## 21. Slider vs scroll arbitration

A slider inside a vertical scroll container demonstrates required arena behavior.

Example policy:

```text
horizontal slider recognizer
vertical scroll recognizer
```

Horizontal movement beyond slop lets slider win.

Vertical movement beyond slop lets scroll win.

Ambiguous movement stays pending until policy resolves.

No duplicate drag updates occur.

## 22. Text selection vs scroll arbitration

A text field inside a scroll container can team selection drag with caret auto-scroll while still competing with ancestor scrolling according to selection/gesture state.

Long press may transition into selection drag without creating a second raw input pipeline.

## 23. Pointer capture interaction

A recognizer can request pointer capture after acceptance when continuous delivery is required.

Capture is released on gesture end/cancel/unmount.

Hover still follows actual hit testing as defined by the event specification.

## 24. Motion duration scale

`UiEnvironment.motionDurationScale` scales or disables inertial settling and overscroll animation according to the shared root motion policy.

A value of `0f` completes time-based settling immediately.

Direct manipulation remains active.

## 25. Feedback

Gesture/component feedback is semantic:

```text
PRESS
LONG_PRESS
TOGGLE
SELECTION_CHANGE
SCROLL_TICK
SCROLL_LIMIT
CONFIRM
ERROR
```

Core emits `UiFeedbackRequest`.

Platform services map it to sound/haptic behavior while respecting platform/user settings.

## 26. Testing

Arena:

```text
tap vs drag arbitration
horizontal slider vs vertical scroll
recognizer cancellation on unmount
cooperative gesture team
pointer capture lifecycle
```

Gesture configuration:

```text
touch slop
double-tap timeout
long-press timeout
scale span
minimum/maximum fling velocity
```

Scroll:

```text
pre/local/post delta conservation
nested residual propagation
pre/local/post fling velocity routing
wheel bypasses touch slop
fling uses root frame clock
overscroll never changes Taffy range
scrollbar drag maps correctly
transformed hit coordinates
```

## 27. Invariants

- raw pointer normalization is platform-owned;
- gesture recognition/arbitration is core-owned;
- universal components share one gesture runtime;
- gesture ownership does not create a second event tree;
- scroll offset is not layout geometry;
- nested scrolling preserves consumed/unconsumed accounting;
- fling/overscroll use the root clock;
- gesture thresholds are environment policy, not hardcoded component constants.
