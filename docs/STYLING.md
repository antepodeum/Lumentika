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
    border = edges(2.px)
    borderPaint = rgb(70, 74, 86)
    borderRadius = CornerRadii(8f)
    boxShadows = listOf(BoxShadow(Point(0f, 3f), 8f, paint = rgb(0, 0, 0, 80)))
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
- Paint: solid colors, linear/radial gradients, images, layered paint, and external implementations
- Geometry: corner radii, painted borders, box shadows, rounded rectangles, paths, and shape clips
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

root.scope.theme(darkTheme) {
    button {
        value = "Save"
        partStyle(Button.Part.LABEL) { color = rgb(255, 255, 255) }
    }
}
```

`UiScope.theme` creates an inherited nearest-theme boundary. Part precedence is structural style,
nearest theme style, then component-instance `partStyle`. A caller style on a component root remains
last and can override its themed root. Conditional part styles evaluate against owner control state.

`StylePart` tokens are typed and identity-based. Standard controls keep their part elements stable
across reactive value and state updates.

## Shapes and clipping

`ClipShape` is shared by rendering and hit testing. `Rect`, `RoundedRect`, and `Path` implement it.
Use `clipShape` for an explicit style clip or `RenderProperties.clip` for a retained render clip.
Overflow clipping combines with `borderRadius`, so children are not hittable outside visible rounded
corners. Transforms and clips do not affect Taffy sizing or positioning.

Content can record `FillPath` and `StrokePath`. Styled boxes emit rounded fills, painted borders, and
box-shadow commands. Backends replay these immutable commands without changing layout.

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
