# Animation and Transition Runtime Specification

## 1. Scope

This phase implements transitions between typed target style values.

Animation is not a second style-precedence system.

Pipeline:

```text
target ResolvedStyle + ResolvedTransitions
→ target diff
→ AnimationRuntime
→ sparse EffectiveStyleOverlay
→ effective property changes
→ normal layout/render projection
```

## 2. MotionSpec

Tween:

```kotlin
val FAST = tween {
    duration = 150.ms
    easing = EASE_OUT
}
```

Delayed tween:

```kotlin
val DELAYED = tween {
    duration = 180.ms
    delay = 40.ms
    easing = EASE_IN_OUT
}
```

Spring:

```kotlin
val SOFT = spring {
    stiffness = 360f
    dampingRatio = 0.82f
}
```

`MotionSpec` values are immutable.

Builders are short-lived.

## 3. Transitions

```kotlin
val INTERACTION = transitions {
    opacity = FAST
    transform = SOFT
}
```

Composition:

```kotlin
val PANEL_MOTION = transitions {
    include(INTERACTION)
    width = SOFT
    height = SOFT
}
```

Only properties marked animatable by the generated style catalog receive typed transition accessors.

## 4. Style attachment

```kotlin
val BUTTON = style {
    transitions = INTERACTION

    opacity = 1f

    on(HOVER) {
        opacity = 0.9f
        transform = scale(1.03f)
    }

    on(ACTIVE) {
        transform = scale(0.98f)
    }
}
```

No transition policy means target changes snap.

## 5. Target policy

Style resolution produces both:

```text
ResolvedStyle
ResolvedTransitions
```

Changing transition policy without changing a target value does not restart the property.

A running track snapshots the `MotionSpec` selected when it starts.

## 6. Track identity

At most one active transition track exists for:

```text
UiRoot + Element + PropertyId
```

A track stores:

```text
start value/vector
target value/vector
current value/vector
velocity where meaningful
MotionSpec snapshot
start/root time
adapter
```

## 7. AnimationAdapter

Generated per animatable property/value family:

```kotlin
interface AnimationAdapter<T> {
    val dimensions: Int

    fun canInterpolate(
        from: T,
        to: T
    ): Boolean

    fun encode(
        value: T,
        out: FloatArray
    )

    fun decode(
        vector: FloatArray
    ): T

    fun threshold(
        out: FloatArray
    )
}
```

A running track reuses its arrays.

No per-frame vector allocation is required.

## 8. Compatible values

Initial compatible families:

```text
Float → Float
Px → Px
Percent → Percent
Color → Color
compatible transforms
compatible shadows
```

Discrete/incompatible:

```text
Auto ↔ Px
Px ↔ Percent
Display
Overflow
Position
grid structure
hitTest
```

An incompatible endpoint cancels the track and snaps to target.

## 9. Tween

Tween supports:

```text
duration
delay
easing
```

Built-in easing:

```text
LINEAR
EASE_IN
EASE_OUT
EASE_IN_OUT
cubicBezier(...)
```

The completed value is set to the exact target, not an accumulated float approximation.

## 10. Spring

Spring supports:

```text
stiffness
dampingRatio
```

Completion occurs when both:

```text
distance to target
velocity
```

are below adapter-provided thresholds.

Final publication uses the exact target.

## 11. Retargeting

When a target changes while a track is active:

```text
sample old track at current root frame time
→ sampled effective value becomes new start
→ install new target/spec
```

Value continuity is mandatory.

Spring retarget preserves current velocity when adapters/endpoints are compatible.

Tween retarget preserves value but does not promise velocity continuity.

## 12. Same-target update

If the target value is semantically equal to the existing track target:

```text
policy-only change
→ do not restart
```

The existing track continues with its snapped policy.

## 13. Color

Color interpolation uses canonical linear-light RGBA.

Decode returns canonical framework `Color`.

## 14. Transform

Compatible transforms are interpolated through affine decomposition where possible.

Rules:

```text
shortest angular path
preserve value continuity
singular/non-decomposable pair snaps
transform never affects Taffy geometry
```

## 15. Layout-affecting transitions

Compatible layout values may animate.

Example:

```kotlin
val RESIZE = transitions {
    width = SOFT
}

val PANEL = style {
    transitions = RESIZE
    width = 160.px

    on(OPEN) {
        width = 320.px
    }
}
```

Sampled effective width goes through the normal layout projection.

An active layout transition may request layout every visible frame.

The root still computes Taffy at most once per frame.

There is no separate geometry interpolation solver.

## 16. EffectiveStyleOverlay

Target `ResolvedStyle` remains unchanged while animations run.

Active tracks publish only sparse property overrides.

Concept:

```text
PropertyId -> effective animated value
```

Only active properties exist in the overlay.

When a track completes, its overlay entry is removed after exact target publication.

## 17. Impact routing

Every sampled effective change uses generated property `StyleImpact`.

Examples:

```text
opacity sample
→ EFFECT

transform sample
→ TRANSFORM

background color sample
→ PAINT

width sample
→ LAYOUT
```

The animation runtime does not special-case subsystem invalidation manually.

## 18. UiAnimationClock

Each `UiRoot` owns one clock.

Properties:

```text
monotonic
nanosecond source
sampled once per frame
replaceable in tests
suspension-aware
```

All tracks in a frame use the exact same sampled root time.

Root suspension does not silently advance UI animation time by wall-clock duration.

## 19. Frame ordering

For current frame time `T`:

```text
resolve target style
→ sample existing tracks at T
→ retarget using sampled current values
→ sample/install effective values
→ stage impacts
→ layout at most once
→ PrePaint/paint
```

This prevents a one-frame discontinuity at retarget.

## 20. Frame demand

A root with any active visible transition requests another frame through the root `FrameScheduler`.

When no animation or backend-dynamic content requires a frame, animation runtime does not keep the root hot.

## 21. Motion policy

Effective motion scale uses `UiEnvironment.motionDurationScale` together with any explicit root/test override.

A scale of `0f` means:

```text
snap all active tracks to target
remove overlays
stop animation frame demand
```

The policy is deterministic and can also be overridden by tests.

All active tracks react to a motion-policy change in the same scheduler wave.

## 22. Lifecycle

Unmounting an element or component scope cancels all owned tracks.

No track may retain an unmounted element.

Root disposal clears the entire animation runtime.

## 23. Theme/state changes

A theme or component state change first resolves a new target style.

Animation then treats the target change exactly like any other property target change.

Animation does not become a dependency source for theme/style resolution.

## 24. Tests

Required deterministic tests with manual clock:

```text
tween exact endpoints
tween delay
easing
spring completion
spring velocity-preserving retarget
tween value-continuous retarget
same-target policy change does not restart
incompatible endpoint snaps
color interpolation
transform shortest angle
layout animation requests layout
max one Taffy compute/root/frame
opacity animation causes EFFECT but no layout
track cleanup on unmount
motionDurationScale=0 snaps
root suspension has no hidden time jump
no per-frame vector allocation in steady track sampling
```

Golden scheduler trace for hover opacity:

```text
STATE
STYLE_TARGET
ANIMATION_RETARGET
ANIMATION_SAMPLE
EFFECT
PREPAINT
EXTRACT
```

and explicitly:

```text
no LAYOUT
no PAINT_RERECORD
```

when only opacity changes.

## 25. Invariants

- target style remains authoritative;
- one track per element/property;
- retarget starts from sampled current effective value;
- spring may preserve velocity;
- incompatible values snap;
- all tracks use one root time sample per frame;
- animation impacts use property catalog metadata;
- layout animation uses Taffy, never a second solver;
- animation does not participate in inheritance/theme precedence;
- active tracks are scope-owned and leak-free.
