# Platform Environment and Services Specification

## 1. Purpose

`lumentika-core` receives platform-dependent runtime conditions through one typed root environment and narrow platform service contracts.

Core components never import native window/toolkit, game-engine, mod-loader, clipboard, haptic, IME, accessibility, or resource APIs.

Taffy4J remains internal to `lumentika-core` and is the only layout implementation.

## 2. UiEnvironment

Every `UiRoot` exposes one immutable reactive environment snapshot.

```kotlin
data class UiEnvironment(
    val viewport: Size<Float>,
    val units: UnitEnvironment,
    val layoutDirection: LayoutDirection,
    val locales: List<UiLocale>,
    val colorScheme: ColorScheme,
    val accessibility: AccessibilityPreferences,
    val motionDurationScale: Float,
    val insets: UiInsets,
    val gesture: GestureConfiguration,
    val capabilities: PlatformCapabilities,
    val lifecycle: UiLifecycleState
)
```

Environment publication is diffed by typed fields/tokens and never remounts the root by default.

## 3. Viewport

`viewport` is the definite logical root size passed to the synthetic Taffy root.

A viewport change requests layout.

The platform converts native surface/window dimensions into root logical coordinates before publication.

## 4. Unit environment

Unit metadata is explicit but does not define `sp` conversion with a linear formula.

```kotlin
data class UnitEnvironment(
    val density: Float,
    val fontScale: Float,
    val physicalPixelScale: Float,
    val revisions: UnitRevisions
)

data class UnitRevisions(
    val dp: Long,
    val sp: Long,
    val physicalPx: Long
)
```

Semantics:

```text
density             informational density / platform dp scale
fontScale           informational user font-size preference
physicalPixelScale  logical root units per physical device pixel
revisions.dp         changes when Dp conversion may change
revisions.sp         changes when Sp conversion may change
revisions.physicalPx changes when PhysicalPx conversion may change
```

`fontScale` is never multiplied directly into font size by core.

## 5. UnitResolver

Actual environment-aware unit conversion is a platform service:

```kotlin
interface UnitResolver {
    fun resolveDp(
        value: Float,
        environment: UiEnvironment
    ): Float

    fun resolveSp(
        value: Float,
        environment: UiEnvironment
    ): Float

    fun resolvePhysicalPx(
        value: Float,
        environment: UiEnvironment
    ): Float
}
```

This contract exists because text scaling is not universally linear.

A host resolves `sp` through its native text-scaling policy. Core never assumes that text scaling is linear.

Headless tests use a deterministic resolver.

## 6. Length units

Core length families support:

```kotlin
8.px
8.dp
14.sp
1.physicalPx
```

Semantics:

```text
px          root logical coordinate unit
dp          platform density-independent unit
sp          platform text-scaled unit
physicalPx  physical device pixel expressed in root coordinates
```

Resolution:

```text
Px          -> direct logical value
Dp          -> UnitResolver.resolveDp(...)
Sp          -> UnitResolver.resolveSp(...)
PhysicalPx  -> UnitResolver.resolvePhysicalPx(...)
```

Resolved float values are produced before Taffy projection.

No environment unit is implemented inside Taffy4J.

## 7. Unit invalidation

Compiled style values record the unit categories they depend on.

Unit revisions invalidate only their matching compiled dependency class:

```text
revisions.dp         -> DP_UNITS
revisions.sp         -> SP_UNITS
revisions.physicalPx -> PHYSICAL_PX_UNITS
```

After re-resolution, semantic equality suppresses downstream work when the resulting logical float is unchanged.

Text-layout cache keys include the relevant unit revision when text dimensions depend on environment-resolved units.

## 8. Accessibility preferences

```kotlin
data class AccessibilityPreferences(
    val highContrastText: Boolean,
    val fontWeightAdjustment: Int
)
```

`fontWeightAdjustment` uses `0` for no adjustment in core-normalized form.

The text/font adapter applies it consistently to measurement and rendering when the platform exposes such a preference.

Accessibility service enabled state and touch-exploration state belong to the platform accessibility adapter; universal component behavior must not fork solely because an accessibility service is enabled.

## 9. Motion policy

`motionDurationScale` is a finite value `>= 0`.

```text
1.0  normal duration
0.5  half duration
0.0  animations complete immediately
```

Animation, fling settling, overscroll animation, cursor blink policy, and other time-based UI behavior consume the same root motion policy where applicable.

Direct manipulation is never disabled by motion scaling.

## 10. Environment invalidation

```text
viewport
→ LAYOUT

unit revision
→ only matching DP_UNITS / SP_UNITS / PHYSICAL_PX_UNITS style/text candidates

layoutDirection
→ inherited direction + text layout + Taffy direction

locales
→ text/resource consumers that depend on locale

colorScheme
→ theme/environment dependents

accessibility.highContrastText
→ theme/text paint dependents

accessibility.fontWeightAdjustment
→ affected text measurement/layout/paint

motionDurationScale
→ active motion policy adjustment

insets
→ inset-dependent style/layout consumers

gesture configuration
→ recognizer policy for new/current gestures according to gesture spec
```

No environment publication causes unconditional full remount.

## 11. Insets

Core models distinct inset sources.

```kotlin
data class UiInsets(
    val systemBars: Insets,
    val displayCutout: Insets,
    val ime: Insets,
    val systemGestures: Insets,
    val mandatorySystemGestures: Insets,
    val tappableElement: Insets,
    val safeDrawing: Insets,
    val safeGestures: Insets,
    val safeContent: Insets
)
```

Platforms that do not expose a category publish zero insets.

## 12. Insets are data

Core does not automatically pad the root.

Applications/components consume insets explicitly.

Runtime logic:

```kotlin
val env = environment()

text {
    value {
        env.value.locales
            .firstOrNull()
            ?.tag
            ?: ""
    }
}
```

Style logic:

```kotlin
block {
    style {
        padding = envInsets(SAFE_DRAWING)
    }
}
```

`envInsets(type)` is a typed dynamic style value resolved by `StyleRuntime` and records an `INSETS` dependency.

This allows intentional edge-to-edge drawing without losing safe placement for important content or gestures.

## 13. IME insets

Software keyboard occlusion is separate from system bars.

IME inset updates can be frame-synchronized with platform window/inset animations.

Core performs ordinary style/layout work only for consumers that use the changed inset value.

## 14. System gesture insets

`systemGestures`, `mandatorySystemGestures`, and `safeGestures` are available separately from visual safe-area insets.

Gesture-aware components can avoid placing critical drag targets in regions where the platform reserves navigation gestures.

Platform-specific gesture exclusion APIs remain outside core.

## 15. Layout direction

```kotlin
enum class LayoutDirection {
    LTR,
    RTL
}
```

Root direction feeds:

```text
inherited `direction`
Taffy direction
START/END alignment
text shaping/layout
navigation policy where direction matters
```

Local style overrides follow normal inheritance.

## 16. Locale

`UiLocale` is a platform-neutral language/region identifier.

Core does not own application localization resources.

Locale is available to:

```text
text shaping/boundaries
application formatting/localization layers
resource adapters
theme logic
```

## 17. Platform capabilities

```kotlin
data class PlatformCapabilities(
    val touch: Boolean,
    val mouse: Boolean,
    val pen: Boolean,
    val hover: Boolean,
    val hardwareKeyboard: Boolean,
    val softwareKeyboard: Boolean,
    val handwriting: Boolean,
    val clipboard: Boolean,
    val haptics: Boolean,
    val accessibility: Boolean,
    val autofill: Boolean,
    val dragDrop: Boolean,
    val richContent: Boolean
)
```

Capabilities describe availability, not guaranteed success of every service call.

## 18. Lifecycle

```kotlin
enum class UiLifecycleState {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    DISPOSED
}
```

```text
ACTIVE      visible/interactive normal operation
INACTIVE    mounted but not primary interactive surface
SUSPENDED   mounted state retained; frame time is suspended by root policy
DISPOSED    terminal root state
```

## 19. Frame scheduling

Core requests a platform frame through:

```kotlin
interface FrameScheduler {
    fun requestFrame()
}
```

The platform invokes:

```kotlin
root.frame(
    frameTimeNanos = timestamp
)
```

One root frame uses one stable timestamp for:

```text
animation
fling/overscroll
cursor blink
frame-dependent component state
```

Core does not use wall-clock calls as the authoritative animation/frame time.

## 20. Frame coalescing

`requestFrame()` is idempotent from the caller's perspective.

Multiple causes before the next platform frame coalesce into one pending request per root/surface.

## 21. PlatformServices

```kotlin
data class PlatformServices(
    val frameScheduler: FrameScheduler,
    val units: UnitResolver,
    val textInput: TextInputService?,
    val clipboard: ClipboardService?,
    val feedback: UiFeedbackService?,
    val cursor: PointerCursorService?,
    val accessibility: AccessibilityAdapter?,
    val contentTransfer: ContentTransferService?,
    val autofill: AutofillService?,
    val uriLauncher: UriLauncher?,
    val back: BackDispatcher?
)
```

Optional services are explicitly absent rather than represented by native handles in component APIs.

## 22. UI feedback

Core emits semantic feedback requests.

```kotlin
enum class UiFeedbackType {
    PRESS,
    RELEASE,
    LONG_PRESS,
    TOGGLE,
    SELECTION_CHANGE,
    SCROLL_TICK,
    SCROLL_LIMIT,
    CONFIRM,
    ERROR
}
```

```kotlin
interface UiFeedbackService {
    fun perform(
        request: UiFeedbackRequest
    )
}
```

Platform implementations choose sound/haptic behavior and respect native/user policy.

Visual style does not own haptics or sound.

## 23. Pointer cursor service

Semantic cursor roles:

```text
DEFAULT
POINTER
TEXT
CROSSHAIR
RESIZE_HORIZONTAL
RESIZE_VERTICAL
GRAB
GRABBING
HANDWRITING
```

The platform resolves roles to native cursor/icon APIs where supported.

No native cursor object enters core.

## 24. Content transfer

Clipboard paste, keyboard-provided rich content, and drag/drop share one core payload model.

```kotlin
data class DragRequest(
    val content: TransferContent,
    val preview: DragPreview?,
    val allowedActions: Set<TransferAction>
)

interface ContentTransferService {
    fun startDrag(
        request: DragRequest
    ): DragSession?
}
```

Incoming content is normalized as typed `TransferContent` data and dispatched through the target component's receive-content path.

Plain-text clipboard convenience uses `ClipboardService`; rich content uses the same normalized content model rather than separate component-specific APIs.

Permission/lifetime handling for native URIs/files remains platform-owned.

## 25. URI/external actions

```kotlin
interface UriLauncher {
    fun open(
        uri: UiUri
    ): Boolean
}
```

Core link default actions may invoke this service.

## 26. Back navigation

`BackDispatcher` normalizes committed and progressive back.

```text
START
PROGRESS
CANCEL
COMMIT
```

Scoped handlers consume back independently of raw keyboard key codes.

Platforms without progressive back emit `COMMIT` only.

## 27. Accessibility adapter

`AccessibilityAdapter` consumes the committed `SemanticsArtifact` and routes platform accessibility actions back into `SemanticsRuntime`.

The adapter owns platform-specific virtual-node IDs, platform accessibility focus bookkeeping, native events, and touch-exploration transport.

Core semantic identity remains independent of platform node IDs.

## 28. Autofill service

Core owns normalized field metadata/current values; platform integration owns the native virtual autofill hierarchy.

```kotlin
interface AutofillService {
    fun onArtifactCommitted(
        artifact: AutofillArtifact,
        changes: AutofillChangeSet
    )

    fun requestAutofill(
        node: AutofillNodeId
    )
}
```

Autofill values are applied through normal component bindings/controllers.

Autofill identity is stable for the lifetime of the mounted field and is not based on screen coordinates or labels.

## 29. Root mounting configuration

```kotlin
data class UiRootConfiguration(
    val environment: UiEnvironment,
    val services: PlatformServices,
    val renderBackend: RenderBackend,
    val textLayoutService: TextLayoutService,
    val imageService: ImageService
)
```

Taffy4J is not supplied because it is the mandatory internal core layout implementation.

## 30. Headless platform

Core tests use deterministic fakes for:

```text
FrameScheduler
UnitResolver
environment publication
TextInputService
ClipboardService
UiFeedbackService
AccessibilityAdapter
ContentTransferService
AutofillService
RenderBackend
TextLayoutService
ImageService
BackDispatcher
```

Real Taffy4J is still used.

## 33. Tests

Environment/unit tests:

```text
viewport invalidation
px direct resolution
dp UnitResolver resolution
sp UnitResolver resolution with non-linear fake curve
physicalPx UnitResolver resolution
per-unit revision candidate invalidation
fontScale informational-only behavior
fontWeightAdjustment text invalidation
RTL propagation
locale publication
color/high-contrast dependencies
motionDurationScale changes
all inset classes
IME inset animation publication
```

Service tests:

```text
frame request coalescing
stable frame timestamp
missing optional service behavior
feedback routing
cursor role boundary
accessibility artifact/action round trip
rich content normalization
autofill virtual-node lifecycle
back start/progress/cancel/commit
```

## 34. Invariants

- platform conditions enter through typed environment/services;
- no native window/input/resource/service type leaks into universal component contracts;
- Taffy4J is the mandatory core layout implementation;
- environment changes do not remount the root;
- one stable frame timestamp drives frame-dependent core behavior;
- `sp` is resolved by `UnitResolver`, never by hardcoded `density * fontScale`;
- `fontScale` is informational, not a conversion formula;
- insets are explicit data, not automatic root padding;
- accessibility semantics are core-owned while platform virtual-node transport is adapter-owned;
- haptic/sound feedback is service behavior, not style behavior;
- autofill/content transfer use stable core identities and payloads rather than platform widget assumptions.
