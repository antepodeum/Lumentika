# Styling and themes

Styles are immutable typed programs attached through element builders. Layout and rendering use the
same resolved style, so state and environment changes invalidate only affected work.

```kotlin
val panel = style {
    width = 320.px
    minHeight = 120.px
    padding = edges(12.px, 16.px)
    gap = 8.px
    background = rgb(28, 30, 36)
    color = rgb(240, 240, 244)
    fontSize = 14.sp

    on(HOVER) {
        background = rgb(36, 39, 47)
    }
    on(FOCUS_VISIBLE) {
        border = edges(2.px)
    }
}

root.scope.column {
    style(panel)
}
```

Built-in states are `HOVER`, `ACTIVE`, `FOCUS`, `FOCUS_VISIBLE`, `FOCUS_WITHIN`, and `DISABLED`.
Conditions can be combined with `all`, `any`, and `not`.

## Values

- Dimensions: `px`, `dp`, `sp`, `physicalPx`, `percent`, `Auto`, and `Calc`
- Box values: `edges(all)`, `edges(vertical, horizontal)`, or four independent edges
- Paint: solid colors, linear/radial gradients, images, and layered paint
- Layout: block, flex, grid, relative/absolute positioning, alignment, gaps, and overflow
- Render and interaction: opacity, stacking order, visibility, and pointer-event policy

`dp`, `sp`, and physical pixels are resolved by the adapter's `UnitResolver` and current
`UiEnvironment`. Use `px` for logical core coordinates.

## Composition

Reuse a style with `include`; later assignments win:

```kotlin
val base = style {
    padding = edges(8.px)
    color = rgb(255, 255, 255)
}

val danger = style {
    include(base)
    background = rgb(180, 32, 42)
}
```

## Themes

Style variables carry defaults and themes override them. Component parts allow a UI library to
publish stable skinning points:

```kotlin
val accent = styleVar(rgb(80, 120, 255))

val darkTheme = theme {
    set(accent, rgb(120, 150, 255))
    style(Button.Part.ROOT, style {
        background = rgb(40, 44, 54)
    })
}
```

Theme objects are platform-neutral data. A platform library decides how themes are selected and
attached to its component layer.

## Property animation

Opacity, width, and height have generated typed adapters. Custom properties can use a custom
`AnimationAdapter<T>`. Property animation is currently started explicitly through
`UiRoot.styleAnimations`:

```kotlin
root.styleAnimations.transition(
    element,
    Properties.Opacity,
    from = 0f,
    to = 1f,
    transition = Transition(TweenSpec(180), FloatAnimationAdapter),
)
```

See [Animation](ANIMATION.md) for structural and keyed animation.
