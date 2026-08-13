# Lumentika

[![CI](https://github.com/antepodeum/lumentika/actions/workflows/ci.yml/badge.svg)](https://github.com/antepodeum/lumentika/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Lumentika is a retained UI runtime for Kotlin/JVM. It includes reactive state, a component DSL,
Taffy-based layout, retained rendering and hit testing, input and focus, gestures, text editing,
accessibility semantics, styles, and animation.

## Artifacts

```text
com.antepod:lumentika-core:<version>
com.antepod:lumentika-ksp:<version>
```

`lumentika-core` contains the runtime and public UI API. Rendering environments integrate it through
interfaces for frame scheduling, paint replay, text layout, input, images, accessibility, and other
services.

## Features

- Fine-grained state, derived values, effects, batching, async state, and owned cleanup
- Kotlin component DSL with generated props, bindings, events, and slots
- Block, flex, and grid layout backed by Taffy4J
- Retained paint artifacts, property trees, clipping, compositing, hit testing, and scene content
- Pointer, keyboard, focus, gestures, nested scrolling, text editing, clipboard, drag/drop, and autofill contracts
- Accessibility semantics with stable nodes, actions, ranges, collections, and live regions
- Typed styles, state conditions, themes, logical units, and environment-aware values
- Tween and spring style animation; enter/exit, keyed layout, draw, blur, and crossfade animation
- Headless services for deterministic tests

Built-in UI building blocks include `block`, `flex`, `row`, `column`, `grid`, `stack`, `scroll`,
`list`, `text`, `image`, `button`, `checkbox`, `slider`, `textField`, and `tooltip`.

## Installation

Use the same release version for both artifacts. The KSP artifact is only needed when declaring
custom `@UIComponent` classes.

```kotlin
plugins {
    kotlin("jvm") version "<kotlin-version>"
    id("com.google.devtools.ksp") version "<ksp-version>"
}

dependencies {
    implementation("com.antepod:lumentika-core:<version>")
    ksp("com.antepod:lumentika-ksp:<version>")
}
```

Releases are available from Maven Central.

## Quick start

The headless root is useful for examples and tests. Applications create `UiRoot` with the services
required by their rendering environment.

```kotlin
import com.antepod.lumentika.components.*
import com.antepod.lumentika.headlessRoot
import com.antepod.lumentika.reactive.derived
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.style.*

val root = headlessRoot(640f, 360f)
val count = state(0)
val summary = derived { "Clicks: ${count.value}" }

root.scope.column {
    style {
        width = 320.px
        padding = edges(16.px)
        gap = 8.px
        background = rgb(24, 26, 32)
        color = rgb(245, 245, 245)
    }

    text(summary)
    button {
        value = "Increment"
        onClick { count.value++ }
    }
}

root.requestFrame()
root.frame(1_000_000L)
root.close()
```

The root owns the mounted tree and all runtime subsystems. Close it when its UI surface is destroyed.

## Documentation

- [Getting started](docs/GETTING_STARTED.md)
- [Core concepts and custom components](docs/CORE_CONCEPTS.md)
- [Built-in components](docs/COMPONENTS.md)
- [Styling and themes](docs/STYLING.md)
- [Animation and structural transitions](docs/ANIMATION.md)
- [Architecture and platform boundary](docs/ARCHITECTURE.md)
- [Platform adapter guide](docs/PLATFORM_ADAPTER.md)
- [Contributing and verification](docs/CONTRIBUTING.md)

## License

Licensed under the [Apache License 2.0](LICENSE).
