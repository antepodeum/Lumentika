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

## Built-ins and custom transitions

Core includes `fade`, `fly`, `slide`, and `scale`. Each supports duration, delay, and easing.
Transition progress uses `t = 1` for the natural state and `u = 1 - t` for its complement.

A custom `ElementTransition` receives the element, committed bounds, and direction, then returns an
`ElementTransitionConfig`:

```kotlin
val custom = ElementTransition { context ->
    ElementTransitionConfig(durationMillis = 180) { t, u ->
        TransitionFrame(
            transform = Matrix3.translation(0f, 20f * u),
            opacity = t,
        )
    }
}
```

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

`FlipAnimation.durationMillis` may derive duration from travel distance. `LayoutAnimationEvents`
reports start, end, and cancellation.

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
transform. Semantic bounds therefore follow the committed animated coordinate chain.

When lifecycle is `SUSPENDED` or `DISPOSED`, the logical animation clock does not advance. A motion
scale of zero completes tracks immediately. Active tracks are cancelled during root disposal.
