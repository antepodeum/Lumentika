# Lumentika Core Implementation Checklist

Every checked item below must have both a production implementation and executable coverage.
The evidence matrix at the end maps each checklist section to its acceptance tests; a green
`./gradlew clean build spotlessCheck` is the release gate. A checked item is not a roadmap claim.

## Core boundary

- [x] `com.antepod:lumentika-core` contains all universal UI mechanics
- [x] Taffy4J is included in core
- [x] Taffy4J is the sole layout implementation
- [x] core has no native platform or mod-loader dependency
- [x] native platform types do not appear in universal component APIs
- [x] concrete platform libraries depend on core, never the reverse

## Kotlin/component runtime

- [x] Kotlin-first public API
- [x] KSP generates Kotlin DSL
- [x] no positional component prop/content arguments
- [x] `Readable<T>.value`
- [x] `Mutable<T>.value`
- [x] dynamic derived dependencies
- [x] batching/untracked/effects
- [x] scope cleanup
- [x] async generation cancellation/stale suppression
- [x] `Prop<T>` one-way forms
- [x] `Binding<T>` two-way only through `bindX`
- [x] `Event<E>` typed events
- [x] `Slot` / `SlotList`
- [x] one-shot `view()`
- [x] structural `show`
- [x] keyed `forEach`

## Platform environment

- [x] typed `UiEnvironment`
- [x] viewport publication
- [x] `UnitEnvironment` metadata
- [x] `UnitResolver`
- [x] `fontScale` informational only
- [x] non-linear `sp` resolution supported
- [x] physical-pixel unit resolution
- [x] `px` / `dp` / `sp` / `physicalPx` resolution
- [x] layout direction
- [x] locales
- [x] color scheme
- [x] accessibility preferences
- [x] high-contrast text
- [x] font-weight adjustment
- [x] motion duration scale
- [x] system-bar insets
- [x] display-cutout insets
- [x] IME insets
- [x] system-gesture insets
- [x] safeDrawing/safeGestures/safeContent
- [x] lifecycle state
- [x] capability snapshot
- [x] environment updates do not remount root

## Frame/services

- [x] `FrameScheduler.requestFrame()`
- [x] one stable frame timestamp/root frame
- [x] frame requests coalesce
- [x] clipboard service
- [x] UI feedback service
- [x] pointer cursor service
- [x] accessibility adapter
- [x] content-transfer service
- [x] autofill service
- [x] URI launcher
- [x] normalized back dispatcher
- [x] missing optional capability has deterministic behavior

## Event/input/focus

- [x] capture/target/bubble/default action
- [x] stop propagation/immediate propagation
- [x] prevent default
- [x] unified pointer types
- [x] timestamped pointer samples
- [x] optional historical/coalesced samples
- [x] pointer capture
- [x] hover remains actual hit path
- [x] normalized keyboard model
- [x] key repeat/modifiers
- [x] one input focus manager/root
- [x] focus/blur + focusin/focusout
- [x] focus-visible/focus-within
- [x] focus repair before disposal
- [x] accessibility focus remains separate
- [x] IME composition is not raw key input

## Gestures

- [x] GestureArena
- [x] tap
- [x] double tap
- [x] long press
- [x] drag/pan
- [x] scale
- [x] cooperative gesture teams
- [x] touch slop from environment
- [x] long-press/double-tap timing from environment
- [x] velocity tracking
- [x] min/max fling velocity from environment
- [x] gesture cancellation on unmount
- [x] slider-vs-scroll arbitration
- [x] text-selection-vs-scroll arbitration

## Scrolling

- [x] core ScrollState
- [x] wheel source
- [x] touch/pen drag source
- [x] keyboard/accessibility/programmatic source
- [x] nested preScroll/local/postScroll
- [x] consumed/unconsumed conservation
- [x] preFling/local/postFling
- [x] root-clock fling animation
- [x] overscroll state separated from range
- [x] scrollbar behavior
- [x] scroll offset does not trigger Taffy

## Semantics/accessibility

- [x] `SemanticsRuntime`
- [x] stable `SemanticsNodeId`
- [x] typed roles
- [x] typed actions
- [x] label/value/stateDescription/hint
- [x] enabled/selected/checked/expanded/readOnly/password
- [x] range semantics
- [x] text selection semantics
- [x] collection/item metadata
- [x] merge descendants
- [x] clear descendants
- [x] hidden semantics
- [x] semantic bounds use committed transforms/clips
- [x] accessibility focus separate from input focus
- [x] live regions/announcements
- [x] semantic dirty tracking
- [x] platform adapter action routing
- [x] button/checkbox/slider/textField default semantics

## Text layout/editing

- [x] UTF-16 offset model
- [x] grapheme-aware navigation/deletion
- [x] bidi-aware caret affinity
- [x] `TextEditingValue`
- [x] selection
- [x] independent composition range
- [x] `TextEditingController`
- [x] typed `TextEditCommand`
- [x] commit text
- [x] composing text/region
- [x] finish composition
- [x] delete surrounding text/codepoints
- [x] batch edits
- [x] external value reconciliation
- [x] `TextInputService`
- [x] one active session per focused editor
- [x] session closes on focus loss/unmount
- [x] `TextLayoutService`
- [x] line/baseline metrics
- [x] point→offset
- [x] offset→caret rect
- [x] selection rectangles
- [x] cursor geometry publication
- [x] text measurement/render layout identity
- [x] caret auto-scroll
- [x] cursor blink uses root clock

## Clipboard/content/autofill

- [x] copy/cut/paste default actions
- [x] rich receive-content model
- [x] platform drag/drop normalization
- [x] unconsumed content propagation
- [x] autofill metadata/hints
- [x] stable autofill node identity
- [x] autofill updates normal binding/controller
- [x] secure-field privacy policy

## Primitive/content boundary

- [x] minimal persistent Element
- [x] Fragment boxless
- [x] Content retained recording
- [x] IntrinsicMeasurable
- [x] HitRegionSource
- [x] no visual/layout mega-bag on Element
- [x] platform-specific Content/Paint extension boundary
- [x] scene objects are not Elements

## Taffy layout

- [x] one Taffy tree/root
- [x] synthetic definite viewport root
- [x] stable projected node identity
- [x] Fragment flattening
- [x] public API contains no Taffy types
- [x] environment units resolved before Taffy
- [x] text layout intrinsic bridge
- [x] stable measurement cache
- [x] markDirty on intrinsic changes
- [x] retained committed geometry
- [x] rounding policy defined
- [x] compute only when requested
- [x] max one compute/root/frame
- [x] render cannot feed layout geometry back

## Render/hit testing

- [x] TransformTree
- [x] ClipTree
- [x] EffectTree
- [x] ScrollTree
- [x] StackingContextTree
- [x] retained PaintArtifact
- [x] retained HitTestArtifact
- [x] top layer
- [x] paint/property/order invalidation split
- [x] reverse paint-order hit testing
- [x] transform/clip parity with hit testing
- [x] property-only updates reuse paint
- [x] transformed overflow can extend reachability only
- [x] custom scene local hit/raycast
- [x] semantic bounds use same coordinate chain
- [x] platform replay extension boundary
- [x] committed render/hit artifacts exposed to platform adapter
- [x] normalized pointer/wheel/key adapter entry points
- [x] platform input routes through core hit testing and gesture arena
- [x] styled backgrounds and exact text layout objects reach replay
- [x] scroll state updates descendant property transforms without layout/repaint

## Styles/themes

- [x] immutable Style
- [x] `style {}` / `on(condition) {}`
- [x] generated property IDs/masks
- [x] compiled StyleProgram
- [x] grouped ResolvedStyle
- [x] structural sharing
- [x] inheritance
- [x] StyleVar token identity
- [x] typed StylePart
- [x] theme part mappings
- [x] `DP_UNITS` / `SP_UNITS` / `PHYSICAL_PX_UNITS` dependency masks
- [x] orthogonal StyleImpact includes SEMANTICS
- [x] style resolution never calls renderer/layout directly

## Animation

- [x] tween/spring
- [x] transitions
- [x] generated AnimationAdapter
- [x] one track/element/property
- [x] continuous retargeting
- [x] sparse effective overlay
- [x] root frame clock
- [x] motion-duration-scale policy
- [x] layout animation routes through Taffy
- [x] fling/overscroll/cursor blink share frame time model

## Structural animation/transitions

- [x] root-owned structural animation runtime
- [x] bidirectional enter/exit transition with continuous reversal
- [x] independent enter and exit transitions
- [x] outgoing elements remain mounted until the transition group completes
- [x] interrupted exit cancels deferred removal
- [x] intro/outro start/end and cancellation events
- [x] explicit transition cancellation handle
- [x] custom transition receives committed bounds and direction
- [x] custom transition supports typed frame sampling and per-frame `tick(t, u)`
- [x] transition delay/duration/easing
- [x] `blur` / `draw` / `fade` / `fly` / `slide` / `scale` built-ins
- [x] draw duration supports fixed, path-length-derived, and speed-derived timing
- [x] draw consumes platform-neutral path and stroke-cap metrics
- [x] keyed `crossfade` send/receive pairing
- [x] crossfade fallback for unmatched keys
- [x] keyed `forEach` FLIP animation
- [x] keyed `forEach` accepts custom layout animations
- [x] custom layout animation receives element and committed `from` / `to` bounds
- [x] custom layout animation supports delay/duration/easing, typed sampling, and `tick(t, u)`
- [x] FLIP animates retained moved keys only
- [x] distance-derived FLIP duration supported
- [x] FLIP uses one layout then property-only frames
- [x] animated transform/clip and hit testing share the same property chain
- [x] animated blur and path-draw state reach immutable effect artifacts
- [x] structural motion honors lifecycle suspension
- [x] structural motion honors motion-duration scale
- [x] active structural motion is cancelled on disposal

## Universal components

- [x] block
- [x] flex
- [x] row
- [x] column
- [x] grid
- [x] stack
- [x] scroll
- [x] list
- [x] text
- [x] image
- [x] button
- [x] checkbox
- [x] slider
- [x] textField
- [x] tooltip
- [x] universal components have no native platform resource/render/service types
- [x] interactive controls use shared gesture runtime
- [x] interactive controls install default semantics
- [x] textField uses shared text-editing runtime
- [x] layout primitives install real Taffy display/direction/overflow defaults
- [x] platform text layout/input/image services propagate through nested/structural scopes
- [x] textField owns focus, IME session, keyboard editing, and disposal lifecycle
- [x] checkbox/slider/textField semantics follow bound state

## Platform adapter readiness

- [x] renderer needs only public immutable paint/property artifacts
- [x] platform-specific paint commands remain opaque to core
- [x] platform text shaping controls measurement, paint, caret, and selection geometry
- [x] platform image service controls intrinsic dimensions
- [x] custom scene hit testing/raycast remains local and platform-owned
- [x] adapter implementation guide and acceptance test defined


## Core integration proof

- [x] deterministic headless host with real Taffy4J
- [x] no native platform or mod-loader classes in core
- [x] unit revisions / accessibility / motion / insets update without remount
- [x] gesture arbitration trace
- [x] nested scroll/fling trace
- [x] text composition/editing/session trace
- [x] semantic action/accessibility-focus trace
- [x] autofill/content-transfer trace
- [x] opacity transition absence-of-layout trace
- [x] width animation max-one-layout trace
- [x] static scrolling absence-of-Taffy/repaint trace
- [x] retained paint/hit/semantic geometry parity
- [x] repeated mount/unmount returns all ownership counters to baseline
- [x] all universal components pass behavior and semantics tests

## Build, CI, and publication

- [x] Java 25 clean build and formatting gate on pushes and pull requests
- [x] failed CI test reports retained as workflow artifacts
- [x] release version derived and validated from `v<semver>` tags
- [x] `lumentika-core` binary, sources, and Dokka API JAR publication
- [x] `lumentika-ksp` binary, sources, and Dokka API JAR publication
- [x] signed Maven Central Portal publication with complete POM metadata
- [x] GitHub Packages publication using the workflow token
- [x] GitHub Release contains directly downloadable JAR artifacts
- [x] Apache-2.0 license declared in repository and Maven metadata

## Verification evidence

| Checklist sections | Executable evidence |
| --- | --- |
| Core boundary | [`ArchitectureTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/ArchitectureTest.kt), Gradle dependency graph checked by `clean build` |
| Kotlin/component runtime | [`ReactiveTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/reactive/ReactiveTest.kt), [`ComponentTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/component/ComponentTest.kt), [`LumentikaProcessorTest.kt`](../lumentika-ksp/src/test/kotlin/com/antepod/lumentika/ksp/LumentikaProcessorTest.kt) |
| Platform environment; frame/services | [`IntegrationProofTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/IntegrationProofTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt) |
| Event/input/focus | [`InputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/input/InputTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt) |
| Gestures; scrolling | [`GesturesTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/gesture/GesturesTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt), [`ComponentsTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/components/ComponentsTest.kt) |
| Semantics/accessibility | [`SemanticsTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/semantics/SemanticsTest.kt), [`ComponentsTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/components/ComponentsTest.kt) |
| Text; clipboard/content/autofill | [`TextEditingTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/text/TextEditingTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt), [`ComponentsTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/components/ComponentsTest.kt) |
| Primitive/content boundary | [`ArchitectureTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/ArchitectureTest.kt), [`RenderTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/render/RenderTest.kt) |
| Taffy layout | [`LayoutRuntimeTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/layout/LayoutRuntimeTest.kt), [`IntegrationProofTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/IntegrationProofTest.kt) |
| Render/hit testing | [`RenderTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/render/RenderTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt) |
| Styles/themes | [`StyleTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/style/StyleTest.kt), generated-source compilation in `clean build` |
| Animation; structural animation/transitions | [`STRUCTURAL_ANIMATION_SPEC.md`](STRUCTURAL_ANIMATION_SPEC.md), [`AnimationTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/animation/AnimationTest.kt), [`ComponentTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/component/ComponentTest.kt), [`IntegrationProofTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/IntegrationProofTest.kt) |
| Universal components | [`ComponentsTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/components/ComponentsTest.kt), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt) |
| Platform adapter readiness | [`PLATFORM_ADAPTER_GUIDE.md`](PLATFORM_ADAPTER_GUIDE.md), [`PlatformInputTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/PlatformInputTest.kt) |
| Core integration proof | [`IntegrationProofTest.kt`](../lumentika-core/src/test/kotlin/com/antepod/lumentika/IntegrationProofTest.kt), full multi-module `clean build` |
| Build, CI, and publication | [`PUBLISHING.md`](PUBLISHING.md), [`ci.yml`](../.github/workflows/ci.yml), [`release.yml`](../.github/workflows/release.yml), local Maven publication verification |
