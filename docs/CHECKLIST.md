# Lumentika Core Implementation Checklist

## Core boundary

- [ ] `com.antepod:lumentika-core` contains all universal UI mechanics
- [x] Taffy4J is included in core
- [x] Taffy4J is the sole layout implementation
- [x] core has no native platform or mod-loader dependency
- [ ] native platform types do not appear in universal component APIs
- [ ] concrete platform libraries depend on core, never the reverse

## Kotlin/component runtime

- [x] Kotlin-first public API
- [ ] KSP generates Kotlin DSL
- [ ] no positional component prop/content arguments
- [x] `Readable<T>.value`
- [x] `Mutable<T>.value`
- [x] dynamic derived dependencies
- [x] batching/untracked/effects
- [x] scope cleanup
- [x] async generation cancellation/stale suppression
- [x] `Prop<T>` one-way forms
- [ ] `Binding<T>` two-way only through `bindX`
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
- [ ] `fontScale` informational only
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
- [ ] environment updates do not remount root

## Frame/services

- [x] `FrameScheduler.requestFrame()`
- [ ] one stable frame timestamp/root frame
- [x] frame requests coalesce
- [x] clipboard service
- [x] UI feedback service
- [x] pointer cursor service
- [ ] accessibility adapter
- [x] content-transfer service
- [ ] autofill service
- [x] URI launcher
- [x] normalized back dispatcher
- [ ] missing optional capability has deterministic behavior

## Event/input/focus

- [ ] capture/target/bubble/default action
- [ ] stop propagation/immediate propagation
- [ ] prevent default
- [ ] unified pointer types
- [ ] timestamped pointer samples
- [ ] optional historical/coalesced samples
- [ ] pointer capture
- [ ] hover remains actual hit path
- [ ] normalized keyboard model
- [ ] key repeat/modifiers
- [ ] one input focus manager/root
- [ ] focus/blur + focusin/focusout
- [ ] focus-visible/focus-within
- [ ] focus repair before disposal
- [ ] accessibility focus remains separate
- [ ] IME composition is not raw key input

## Gestures

- [ ] GestureArena
- [ ] tap
- [ ] double tap
- [ ] long press
- [ ] drag/pan
- [ ] scale
- [ ] cooperative gesture teams
- [ ] touch slop from environment
- [ ] long-press/double-tap timing from environment
- [ ] velocity tracking
- [ ] min/max fling velocity from environment
- [ ] gesture cancellation on unmount
- [ ] slider-vs-scroll arbitration
- [ ] text-selection-vs-scroll arbitration

## Scrolling

- [ ] core ScrollState
- [ ] wheel source
- [ ] touch/pen drag source
- [ ] keyboard/accessibility/programmatic source
- [ ] nested preScroll/local/postScroll
- [ ] consumed/unconsumed conservation
- [ ] preFling/local/postFling
- [ ] root-clock fling animation
- [ ] overscroll state separated from range
- [ ] scrollbar behavior
- [ ] scroll offset does not trigger Taffy

## Semantics/accessibility

- [ ] `SemanticsRuntime`
- [ ] stable `SemanticsNodeId`
- [ ] typed roles
- [ ] typed actions
- [ ] label/value/stateDescription/hint
- [ ] enabled/selected/checked/expanded/readOnly/password
- [ ] range semantics
- [ ] text selection semantics
- [ ] collection/item metadata
- [ ] merge descendants
- [ ] clear descendants
- [ ] hidden semantics
- [ ] semantic bounds use committed transforms/clips
- [ ] accessibility focus separate from input focus
- [ ] live regions/announcements
- [ ] semantic dirty tracking
- [ ] platform adapter action routing
- [ ] button/checkbox/slider/textField default semantics

## Text layout/editing

- [ ] UTF-16 offset model
- [ ] grapheme-aware navigation/deletion
- [ ] bidi-aware caret affinity
- [ ] `TextEditingValue`
- [ ] selection
- [ ] independent composition range
- [ ] `TextEditingController`
- [ ] typed `TextEditCommand`
- [ ] commit text
- [ ] composing text/region
- [ ] finish composition
- [ ] delete surrounding text/codepoints
- [ ] batch edits
- [ ] external value reconciliation
- [ ] `TextInputService`
- [ ] one active session per focused editor
- [ ] session closes on focus loss/unmount
- [ ] `TextLayoutService`
- [ ] line/baseline metrics
- [ ] point→offset
- [ ] offset→caret rect
- [ ] selection rectangles
- [ ] cursor geometry publication
- [ ] text measurement/render layout identity
- [ ] caret auto-scroll
- [ ] cursor blink uses root clock

## Clipboard/content/autofill

- [ ] copy/cut/paste default actions
- [ ] rich receive-content model
- [ ] platform drag/drop normalization
- [ ] unconsumed content propagation
- [ ] autofill metadata/hints
- [ ] stable autofill node identity
- [ ] autofill updates normal binding/controller
- [ ] secure-field privacy policy

## Primitive/content boundary

- [x] minimal persistent Element
- [x] Fragment boxless
- [x] Content retained recording
- [x] IntrinsicMeasurable
- [ ] HitRegionSource
- [ ] no visual/layout mega-bag on Element
- [x] platform-specific Content/Paint extension boundary
- [ ] scene objects are not Elements

## Taffy layout

- [x] one Taffy tree/root
- [x] synthetic definite viewport root
- [x] stable projected node identity
- [x] Fragment flattening
- [x] public API contains no Taffy types
- [x] environment units resolved before Taffy
- [ ] text layout intrinsic bridge
- [ ] stable measurement cache
- [ ] markDirty on intrinsic changes
- [x] retained committed geometry
- [x] rounding policy defined
- [x] compute only when requested
- [x] max one compute/root/frame
- [x] render cannot feed layout geometry back

## Render/hit testing

- [ ] TransformTree
- [ ] ClipTree
- [ ] EffectTree
- [ ] ScrollTree
- [ ] StackingContextTree
- [ ] retained PaintArtifact
- [ ] retained HitTestArtifact
- [ ] top layer
- [ ] paint/property/order invalidation split
- [ ] reverse paint-order hit testing
- [ ] transform/clip parity with hit testing
- [ ] property-only updates reuse paint
- [ ] transformed overflow can extend reachability only
- [ ] custom scene local hit/raycast
- [ ] semantic bounds use same coordinate chain
- [ ] platform replay extension boundary

## Styles/themes

- [x] immutable Style
- [x] `style {}` / `on(condition) {}`
- [ ] generated property IDs/masks
- [ ] compiled StyleProgram
- [ ] grouped ResolvedStyle
- [ ] structural sharing
- [x] inheritance
- [x] StyleVar token identity
- [x] typed StylePart
- [x] theme part mappings
- [ ] `DP_UNITS` / `SP_UNITS` / `PHYSICAL_PX_UNITS` dependency masks
- [x] orthogonal StyleImpact includes SEMANTICS
- [x] style resolution never calls renderer/layout directly

## Animation

- [ ] tween/spring
- [ ] transitions
- [ ] generated AnimationAdapter
- [ ] one track/element/property
- [ ] continuous retargeting
- [ ] sparse effective overlay
- [ ] root frame clock
- [ ] motion-duration-scale policy
- [ ] layout animation routes through Taffy
- [ ] fling/overscroll/cursor blink share frame time model

## Universal components

- [ ] block
- [ ] flex
- [ ] row
- [ ] column
- [ ] grid
- [ ] stack
- [ ] scroll
- [ ] list
- [ ] text
- [ ] image
- [ ] button
- [ ] checkbox
- [ ] slider
- [ ] textField
- [ ] tooltip
- [ ] universal components have no native platform resource/render/service types
- [ ] interactive controls use shared gesture runtime
- [ ] interactive controls install default semantics
- [ ] textField uses shared text-editing runtime


## Core integration proof

- [ ] deterministic headless host with real Taffy4J
- [ ] no native platform or mod-loader classes in core
- [ ] unit revisions / accessibility / motion / insets update without remount
- [ ] gesture arbitration trace
- [ ] nested scroll/fling trace
- [ ] text composition/editing/session trace
- [ ] semantic action/accessibility-focus trace
- [ ] autofill/content-transfer trace
- [ ] opacity transition absence-of-layout trace
- [ ] width animation max-one-layout trace
- [ ] static scrolling absence-of-Taffy/repaint trace
- [ ] retained paint/hit/semantic geometry parity
- [ ] repeated mount/unmount returns all ownership counters to baseline
- [ ] all universal components pass behavior and semantics tests
