# Structural Animation and Transition Contract

Lumentika core provides two different motion systems:

- style transitions interpolate changes to typed style properties;
- structural transitions animate elements entering, leaving, or moving because the UI tree changed.

Both systems use the root `UiAnimationClock`, lifecycle policy, and `motionDurationScale`.

## Enter and exit

`show` accepts either one bidirectional transition or independent enter and exit transitions:

```kotlin
show(visible, transition(fade(durationMillis = 150))) {
    text("Reversible")
}

show(
    visible,
    inOut(
        enter = fly(y = 16f),
        exit = fade(durationMillis = 100),
    ),
) {
    text("Independent")
}
```

A bidirectional transition reverses from its currently sampled value. Its reversed duration is
proportional to the remaining distance and its delay is not replayed. Independent enter and exit
tracks may overlap. Restarting an interrupted independent transition begins that direction again.

An outgoing element remains mounted until every active top-level transition in the `show` block
finishes. Disposal, focus repair, semantic removal, and ownership release happen only after that
barrier. Re-entering before completion cancels the pending removal.

## Events and cancellation

`TransitionEvents` exposes `introStart`, `introEnd`, `outroStart`, `outroEnd`, and cancellation
callbacks. `ElementTransitionHandle.close()` explicitly cancels a track. Root or element disposal
also cancels owned motion and clears its sparse render overlay.

## Standard transitions

Core includes the complete standard set:

- `blur` animates blur radius and opacity;
- `draw` animates path visibility;
- `fade` animates opacity;
- `fly` animates translation and opacity;
- `slide` reveals through an animated clip and opacity;
- `scale` animates scale around a configurable origin and opacity;
- `crossfade` pairs keyed send/receive geometry.

Every transition supports delay, duration, and easing. Defaults are stable library API: `fade`,
`fly`, `slide`, `scale`, and `blur` use 400 ms; `draw` uses 800 ms unless fixed duration, path
length, or speed selects another duration. Default easing is linear for `fade`, cubic-out for
`fly`, `slide`, `scale`, and `crossfade`, and cubic-in-out for `blur` and `draw`.

`draw` requires the element's `Content` to implement the platform-neutral `PathMetrics` contract.
`pathLength` is the measured centerline length and `strokeExtension` accounts for visible
non-butt caps. A platform renderer reads `drawLength` and `drawProgress` from the effect tree and
maps them to its native stroke-dash mechanism.

## Custom transitions

Transition progress uses `t = 1` for the natural state and `u = 1 - t` for its complement.

A custom `ElementTransition` receives the element, committed bounds, and direction, then returns an
`ElementTransitionConfig`:

```kotlin
val custom = ElementTransition { context ->
    ElementTransitionConfig(
        durationMillis = 180,
        tick = { t, u -> updateExternalState(t, u) },
        sample = { t, u ->
            TransitionFrame(
                transform = Matrix3.translation(0f, 20f * u),
                opacity = t,
                blurRadius = 3f * u,
            )
        },
    )
}
```

`sample(t, u)` is the typed render equivalent of generating dynamic visual declarations. It can
return transform, opacity, local clip, blur radius, and path-draw state without exposing a native
renderer. `tick(t, u)` is called at the initial value, each sampled frame, and the exact terminal
value; it is intended for custom state that cannot be expressed by `TransitionFrame`.

## Keyed FLIP animation

`forEach` accepts `animation = flip(...)`:

```kotlin
forEach(items, key = { it.id }, animation = flip(durationMillis = 200)) { item ->
    text(item.name)
}
```

Only retained keys whose committed bounds changed are animated. Added and removed keys are not FLIP
animated; use enter/exit transitions for them. The runtime captures the old visual bounds, performs
one normal Taffy layout, commits the new bounds, applies the inverse transform, and animates it to
identity. Animation frames update property trees only, with no extra layout or paint recording.

The default duration is `sqrt(distance) * 120` ms with cubic-out easing.
`FlipAnimation.durationMillis` may instead use a fixed or custom distance-derived duration.
`LayoutAnimationEvents` reports start, end, and cancellation.

## Custom keyed layout animations

`forEach` accepts any `LayoutAnimation`, not only `flip`. The factory receives the retained element
and its committed old and new bounds:

```kotlin
val customMove = LayoutAnimation { context ->
    LayoutAnimationConfig(
        durationMillis = 220,
        tick = { t, u -> updateExternalState(t, u) },
        sample = { _, u ->
            TransitionFrame(
                transform = Matrix3.translation(
                    (context.from.x - context.to.x) * u,
                    (context.from.y - context.to.y) * u,
                )
            )
        },
    )
}

forEach(items, key = { it.id }, animation = customMove) { item ->
    text(item.name)
}
```

`LayoutAnimationConfig` has the same delay, duration, easing, typed `sample(t, u)`, and
`tick(t, u)` contract as transitions. Only retained keys with changed positive bounds start a
layout animation.

## Crossfade

`crossfade` creates keyed `send` and `receive` transition factories:

```kotlin
val cards = crossfade(fallback = fade())

show(inSource, inOut(exit = cards.send(cardId))) { card(cardId) }
show(inTarget, inOut(enter = cards.receive(cardId))) { card(cardId) }
```

Matching keys are resolved together after both committed geometries exist. The pair interpolates
position, size, and opacity. An unmatched side uses the configured fallback. Duplicate send or
receive keys in one resolution batch fail deterministically.

## Platform and accessibility policy

Structural motion is stored in a render-property overlay separate from ordinary transforms,
scrolling, clipping, and top-layer configuration. Paint and hit testing consume the same composed
transform and clip. Blur and path-draw values are immutable `EffectNode` fields for platform replay.
Semantic bounds therefore follow the committed animated coordinate chain.

When lifecycle is `SUSPENDED` or `DISPOSED`, the logical animation clock does not advance. A motion
scale of zero completes tracks immediately. Active tracks are cancelled during root disposal.
