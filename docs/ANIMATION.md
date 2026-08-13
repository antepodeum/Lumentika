# Animation

Lumentika supports property animation, enter/exit transitions, keyed layout animation, crossfade,
and user-defined effects. Animation is driven only by `UiRoot.frame`; there is no background timer.

## Enter and exit

Attach a transition to reactive conditional content:

```kotlin
val visible = state(false)

root.scope.show(
    visible,
    transition(fly(y = 12f, durationMillis = 180)),
) {
    text("Connected")
}
```

Use one reversible effect with `transition(effect)`, or separate effects with `inOut`:

```kotlin
show(
    visible,
    inOut(
        enter = scale(start = 0.9f, durationMillis = 160),
        exit = fade(durationMillis = 100),
    ),
) {
    text("Details")
}
```

Built-in effects:

| Effect | Behavior |
| --- | --- |
| `fade` | Opacity |
| `fly` | Translation and opacity |
| `scale` | Scale around an origin and opacity |
| `slide` | Horizontal or vertical reveal clip |
| `blur` | Blur radius and opacity |
| `draw` | Progressive path drawing for content implementing `PathMetrics` |
| `crossfade` | Pairs outgoing and incoming elements by key |

All effects accept timing and easing options appropriate to the effect. `TransitionEvents` exposes
start, end, and cancel callbacks.

## Custom transitions

Implement `ElementTransition` with a function. `t` is eased progress toward the natural state and
`u` is its inverse:

```kotlin
val lift = ElementTransition { context ->
    ElementTransitionConfig(
        durationMillis = 220,
        easing = StructuralEasings.cubicOut,
        sample = { t, u ->
            TransitionFrame(
                transform = Matrix3.translation(0f, context.bounds.height * 0.2f * u),
                opacity = t,
            )
        },
    )
}
```

A frame can control transform, opacity, clip, blur, path length, and path progress. The adapter must
replay the corresponding motion fields.

## Keyed layout animation

`forEach` preserves element identity by key. Add `flip` to animate position and size changes:

```kotlin
root.scope.forEach(
    items,
    key = Item::id,
    animation = flip(durationMillis = 240),
) { item ->
    text(item.label)
}
```

Implement `LayoutAnimation` to define another geometry-to-geometry effect.

## Crossfade

Create one pair registry and use matching keys on outgoing and incoming content:

```kotlin
val shared = crossfade(fallback = fade())

show(compact, inOut(exit = shared.send("cover"))) { compactCover() }
show(expanded, inOut(enter = shared.receive("cover"))) { expandedCover() }
```

Unmatched elements use the optional fallback or remain natural.

## Property animation

`TweenSpec` and `SpringSpec` animate typed style values through an `AnimationAdapter`. Built-in
adapters cover `Float` and compatible dimensions. An adapter can support another value family:

```kotlin
object PointAdapter : AnimationAdapter<Point> {
    override fun interpolate(from: Point, to: Point, fraction: Float) = Point(
        from.x + (to.x - from.x) * fraction,
        from.y + (to.y - from.y) * fraction,
    )
}
```

## Motion policy and lifecycle

`UiEnvironment.motionDurationScale` scales animation duration; zero completes motion immediately.
Suspended and disposed roots do not advance animation time. Closing the root cancels running motion
and releases owned callbacks.
