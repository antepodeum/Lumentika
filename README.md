# Lumentika

[![CI](https://github.com/antepodeum/lumentika/actions/workflows/ci.yml/badge.svg)](https://github.com/antepodeum/lumentika/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

Lumentika is a retained UI runtime for Kotlin/JVM. It provides the platform-neutral mechanics needed
to build a complete UI toolkit: reactive state, a component DSL, Taffy-based layout, retained
rendering and hit testing, input and focus, gestures, text editing, accessibility semantics, styles,
and animation.

The repository publishes two artifacts:

```text
com.antepod:lumentika-core:<version>
com.antepod:lumentika-ksp:<version>
```

`lumentika-core` does not render through a concrete graphics API and does not depend on Minecraft,
a mod loader, or a desktop toolkit. A platform adapter supplies frame scheduling, renderer replay,
text shaping, native input, and optional operating-system services. This makes the core suitable for
building a Minecraft UI library without putting game types into reusable UI code.

> Lumentika currently targets JVM 25. It is platform-neutral at the API boundary, but it is not a
> Kotlin Multiplatform library.

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
    kotlin("jvm") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.5"
}

dependencies {
    implementation("com.antepod:lumentika-core:<version>")
    ksp("com.antepod:lumentika-ksp:<version>")
}
```

Releases are available from Maven Central. GitHub Packages is also populated by the release
workflow and requires GitHub package credentials when used as a dependency repository.

## Quick start

The headless root is useful for learning and tests. A real application creates `UiRoot` through its
platform adapter.

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

The root owns the mounted tree and all runtime subsystems. Close it when the screen or adapter is
destroyed.

## Documentation

- [Getting started](docs/GETTING_STARTED.md)
- [Core concepts and custom components](docs/CORE_CONCEPTS.md)
- [Built-in components](docs/COMPONENTS.md)
- [Styling and themes](docs/STYLING.md)
- [Animation and structural transitions](docs/ANIMATION.md)
- [Architecture and platform boundary](docs/ARCHITECTURE.md)
- [Platform adapter guide](docs/PLATFORM_ADAPTER.md)
- [Contributing and verification](docs/CONTRIBUTING.md)
- [Publishing releases](docs/PUBLISHING.md)

Generated Dokka API documentation is distributed in each release's Javadoc-classified JAR.

## Status

The core runtime is implemented and covered by the repository test suite. Platform adapters are
separate libraries: applications cannot display native output using `lumentika-core` alone.
Compatibility is not guaranteed across `0.x` releases.

## License

Licensed under the [Apache License 2.0](LICENSE).
