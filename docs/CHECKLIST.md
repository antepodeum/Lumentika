# Lumentika Core Implementation Checklist

## Core boundary

- [ ] `com.antepod:lumentika-core` contains all universal UI mechanics
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
- [ ] hover remains actual hit path
- [x] normalized keyboard model
- [x] key repeat/modifiers
- [x] one input focus manager/root
- [x] focus/blur + focusin/focusout
- [ ] focus-visible/focus-within
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
- [ ] slider-vs-scroll arbitration
- [ ] text-selection-vs-scroll arbitration

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
- [ ] semantic bounds use committed transforms/clips
- [x] accessibility focus separate from input focus
- [x] live regions/announcements
- [x] semantic dirty tracking
- [x] platform adapter action routing
- [ ] button/checkbox/slider/textField default semantics

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
- [ ] text measurement/render layout identity
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

- [x] TransformTree
- [x] ClipTree
- [x] EffectTree
- [x] ScrollTree
- [x] StackingContextTree
- [x] retained PaintArtifact
- [x] retained HitTestArtifact
- [x] top layer
- [ ] paint/property/order invalidation split
- [x] reverse paint-order hit testing
- [x] transform/clip parity with hit testing
- [x] property-only updates reuse paint
- [ ] transformed overflow can extend reachability only
- [ ] custom scene local hit/raycast
- [ ] semantic bounds use same coordinate chain
- [x] platform replay extension boundary

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

- [x] tween/spring
- [ ] transitions
- [x] generated AnimationAdapter
- [x] one track/element/property
- [x] continuous retargeting
- [ ] sparse effective overlay
- [x] root frame clock
- [x] motion-duration-scale policy
- [ ] layout animation routes through Taffy
- [x] fling/overscroll/cursor blink share frame time model

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
- [ ] interactive controls use shared gesture runtime
- [x] interactive controls install default semantics
- [x] textField uses shared text-editing runtime


## Core integration proof

- [x] deterministic headless host with real Taffy4J
- [x] no native platform or mod-loader classes in core
- [ ] unit revisions / accessibility / motion / insets update without remount
- [x] gesture arbitration trace
- [x] nested scroll/fling trace
- [x] text composition/editing/session trace
- [ ] semantic action/accessibility-focus trace
- [x] autofill/content-transfer trace
- [ ] opacity transition absence-of-layout trace
- [ ] width animation max-one-layout trace
- [ ] static scrolling absence-of-Taffy/repaint trace
- [x] retained paint/hit/semantic geometry parity
- [ ] repeated mount/unmount returns all ownership counters to baseline
- [ ] all universal components pass behavior and semantics tests
