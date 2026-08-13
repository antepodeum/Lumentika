# Lumentika Core Integration Proof Specification

## 1. Purpose

Prove that `lumentika-core` forms one deterministic platform-independent UI runtime with real Taffy4J layout and no concrete platform dependency.

The proof covers required work and required absence of work.

## 2. UiRoot ownership

One `UiRoot` coordinates:

```text
ReactiveRuntime
ComponentRuntime / ComponentScope
EventRuntime
FocusManager
GestureRuntime
TextEditingRuntime
SemanticsRuntime
StyleRuntime
AnimationRuntime
LayoutRuntime / TaffyTree
RenderRuntime
PlatformEnvironment/services
```

Each authoritative concept has one owner.

## 3. Canonical frame scheduler

```text
1 collect normalized platform input / external publications
2 apply pending reactive writes
3 invalidate derived
4 recompute demanded derived
5 apply reactive component inputs/bindings
6 reconcile structural nodes
7 run normal effects
8 resolve semantic/autofill configuration changes
9 resolve dirty styles/themes/state variants/environment-dependent style values
10 diff target ResolvedStyle / ResolvedTransitions
11 sample/retarget animations for current root frame time
12 advance root-clock gesture physics/cursor blink when active
13 compute effective animated property changes
14 stage layout/intrinsic/render/semantic mutations
15 compute Taffy layout at most once if requested
16 commit LayoutChanges
17 run afterLayout effects
18 run PrePaint/property-tree updates if required
19 commit semantic/autofill geometry/tree changes
20 repaint dirty PaintSources
21 commit PaintArtifact + HitTestArtifact
22 publish platform-facing semantic/autofill changes and replay retained artifact
```

No subsystem recursively starts another complete flush.

## 4. Headless root proof

A deterministic fake platform supplies:

```text
manual frame scheduler/time
manual UiEnvironment
fake RenderBackend
fake TextLayoutService / ImageService
fake TextInputService
fake ClipboardService
fake UiFeedbackService
fake accessibility/autofill adapters
input injection
```

Core uses real Taffy4J.

Mounting and interacting with a normal component tree requires no native platform/native platform class.

## 5. Reactive component proof

```kotlin
@UIComponent
class VolumeControl : Component() {
    val value = binding(50.0)

    override fun view() = column {
        text {
            value {
                "${value.value.toInt()}%"
            }
        }

        slider {
            bindValue(value)
        }
    }
}
```

Proof:

```text
mount -> view executes once
external mutable change -> slider/text update without view rerun
slider gesture -> Binding writes -> external Mutable updates
```

## 6. Universal component proof

Core components execute under headless services/theme:

```text
block/flex/grid
button/checkbox/slider
scroll/list
text/image
textField
tooltip
```

No universal component loads native platform classes.

## 7. Environment unit proof

Static style:

```kotlin
val CARD = style {
    padding = 8.dp
}

val LABEL = style {
    fontSize = 14.sp
}
```

Environment change:

```text
UnitEnvironment.revisions.sp changes
→ only properties/programs dependent on SP_UNITS become candidates
→ UnitResolver recomputes their logical values
→ resolved lengths change
→ layout/intrinsic as required
→ no component remount
```

`fontScale` is informational; `sp` behavior is determined by `UnitResolver`. A non-linear test resolver proves that `sp` is not implemented as scalar multiplication.

A fake `UnitResolver` can change `physicalPx` conversion independently while semantic equality prevents unrelated downstream work.

## 8. Insets proof

Application consumes `environment.insets.safeDrawing` explicitly.

Inset publication:

```text
IME/system inset update
→ reactive environment dependency
→ affected padding/layout only
→ no unconditional root padding
→ no remount
```

## 9. Gesture arena proof

Slider inside vertical scroll:

```text
pointer down
→ slider drag recognizer + ancestor scroll recognizer pending
→ horizontal motion crosses slop
→ slider accepts
→ scroll rejects
→ only slider receives drag updates
```

Vertical motion produces the inverse winner.

Unmount during pending/accepted gesture cancels recognizers and pointer capture safely.

## 10. Nested scroll proof

```text
available delta
→ preScroll ancestors
→ local consume
→ postScroll ancestors
```

Assertions:

```text
consumed + remaining conserves input delta
no axis over-consumption
nested child/parent chain deterministic
scroll offsets change render state without Taffy
```

## 11. Fling proof

Timestamped pointer samples produce velocity.

```text
preFling
→ local fling
→ postFling
```

Fling animation uses root frame time.

`motionDurationScale` can scale or complete inertial animation immediately without changing direct drag behavior.

## 12. Text input proof

Focused textField:

```text
focus gain
→ TextInputService.startSession
→ current TextEditingValue/configuration published
```

IME command sequence:

```text
SetComposingText
SetSelection
CommitText
FinishComposition
```

applies atomically through `TextEditingController`.

Assertions:

```text
composition and selection independent
String binding publication correct
text layout invalidated only when needed
session closed before editor unmount
stale session cannot mutate disposed editor
```

## 13. Text geometry proof

One `TextLayoutResult` supplies:

```text
measurement size
line metrics
point -> offset
offset -> caret rect
selection rectangles
```

The same text layout/configuration is used by intrinsic measurement and paint recording.

Bidi caret mapping and grapheme-aware navigation use the text layout/boundary service rather than code-unit assumptions.

## 14. Clipboard/content proof

Core text field default actions call fake clipboard/content services.

```text
COPY/CUT/PASTE
→ normal editing controller path
```

Incoming `TransferContent` consumes supported text items and returns unsupported items unchanged.

No platform URI/native content object is stored in normal core text state.

## 15. Autofill proof

Mounted editable fields publish stable autofill node identity, metadata, value, and current bounds.

Fake autofill response:

```text
AutofillService response
→ normal TextEditingController/Binding update
→ semantics/text layout/render updates
```

No accessibility action is abused as autofill transport.

## 16. Semantics tree proof

Button with text/icon:

```text
multiple visual Elements
→ mergeDescendants
→ one semantic BUTTON node
```

Assertions:

```text
stable node identity under non-destructive update
keyed reorder preserves semantic identity
unmount removes node
display/visibility participation updates semantics
paint color change does no semantic work
```

## 17. Accessibility focus proof

Fake platform sets accessibility focus on a semantic node while keyboard input focus remains elsewhere.

Assertions:

```text
accessibility focus != input focus
FOCUS style state unchanged by accessibility focus alone
semantic FOCUS action may request normal input focus explicitly
```

## 18. Semantic geometry proof

Transform/scroll changes:

```text
no Taffy
→ PrePaint property-tree change
→ semantic root-space bounds change
→ platform semantic geometry publication
```

Bounds use the same transform/clip chain as hit testing.

## 19. Semantic action proof

Platform semantic CLICK on button:

```text
SemanticsRuntime action
→ normal button activation/default-action path
→ click event/callback
→ optional feedback request
```

There is no duplicated accessibility-specific button behavior.

## 20. Theme/style proof

Reactive StyleVar:

```kotlin
val ACCENT = styleVar(rgb(90, 120, 255))
val accent = state(rgb(120, 150, 255))

val THEME = theme {
    set(ACCENT, accent)
}
```

Changing `accent.value` invalidates only current consumers.

Theme StylePart mapping styles internal universal component parts without accessing private elements.

## 21. Opacity transition proof

Hover changes target opacity with transition:

```text
style target
→ animation track
→ effective opacity
→ EFFECT
→ PrePaint/backend replay
```

Assert absence:

```text
no component view rerun
no Taffy
no PaintSource rerecord
no semantics work
```

## 22. Width transition proof

Animated compatible width:

```text
animation sample
→ LAYOUT
→ Taffy projection
→ exactly one compute/root/frame
```

No second geometry interpolation solver exists.

## 23. Text intrinsic proof

```text
text change
→ TextLayoutResult invalidation
→ intrinsic/Taffy markDirty
→ layout when measured size/wrap changes
→ paint
```

Color-only text change:

```text
PAINT only
```

Selection-only change:

```text
paint + semantics + caret geometry
no Taffy unless auto-scroll/layout policy requires it
```

## 24. Static scroll proof

Scroll offset change:

```text
ScrollTree offset
→ transform/property update
→ hit/semantic geometry update
→ backend replay
```

Assert:

```text
no Taffy
no unchanged PaintSource rerecord
```

## 25. Hit-test proof

Pointer target selection uses committed:

```text
paint order
transforms
clips
hit regions
```

The same coordinate conversion supports pointer gestures, text caret hit testing, and semantic bounds.

## 26. Focus repair proof

Removing focused text field:

```text
cancel gestures/capture
repair focus
close TextInputSession
remove semantics/autofill node
then dispose scope/tree
```

No stale platform session can call back into disposed component state.

## 27. Top-layer proof

Tooltip/popover top-layer projection changes visual/layout projection only.

Logical:

```text
event ancestry
context/theme ancestry
semantics ownership relationships
```

remain stable unless the component explicitly changes semantic structure.

## 28. Frame scheduler proof

Multiple causes before next frame:

```text
animation + cursor blink + fling + reactive paint change
```

produce one pending `FrameScheduler.requestFrame()` state.

One supplied `frameTimeNanos` is observed by all frame-dependent systems.

## 29. Platform service absence proof

A headless/minimal platform may omit:

```text
software keyboard
clipboard
haptics
autofill
drag-drop
URI opening
accessibility adapter
```

Universal component behavior remains deterministic and reports unsupported optional actions without native-type leakage.

## 30. Ownership matrix

```text
reactive graph                 ReactiveRuntime
component declarations        ComponentRuntime / ComponentScope
logical tree                  Element runtime
pointer/event dispatch        EventRuntime
input focus                   FocusManager
gesture arbitration           GestureRuntime
scroll/fling state            Gesture/Scroll runtime
text editing state            TextEditingRuntime/controller
semantic tree                 SemanticsRuntime
autofill metadata/artifact    core autofill runtime
style sources/theme deps      StyleRuntime
animation tracks              AnimationRuntime
layout tree                   LayoutRuntime / TaffyTree
paint/hit artifacts           RenderRuntime
platform environment          UiRoot environment
native capabilities           PlatformServices/platform adapter
```

## 31. Forbidden feedback loops

Assert absence of:

```text
render geometry -> Taffy
semantic bounds -> layout
accessibility action -> direct private component mutation bypassing normal action path
IME callback -> direct external State mutation bypassing editor controller
platform raw pointer -> component-specific native handler
animation overlay -> target style resolver
paint artifact -> style precedence
top-layer projection -> logical ancestry
scroll offset -> Taffy geometry
```

## 32. Dirty-work matrix

Representative expected work:

```text
state affects text        reactive + text-layout/intrinsic + layout + paint + semantics as needed
hover opacity             style + animation + effect + PrePaint
scroll offset             scroll + PrePaint + hit + semantic geometry
selection move            text paint + semantics + cursor geometry
unit revision change      matching unit-dependent style/text resolution only
semantic label change     semantics only
background color          paint only
font weight adjustment    affected text intrinsic/layout/paint
```

## 33. Disposal proof

Root disposal returns all ownership counters to baseline:

```text
component scopes
reactive nodes/coroutines
gesture recognizers/pointer capture
focus
text-input session
semantic/autofill nodes
style/theme deps
animation/fling tracks
Taffy nodes/tree
render artifacts
platform service subscriptions
```

## 34. Acceptance

Core is implementation-ready when deterministic tests prove:

- one authoritative owner per subsystem concept;
- one Taffy layout engine;
- platform environment/service isolation;
- gesture arbitration/nested scrolling;
- complete text editing/IME state model;
- semantics/accessibility and autofill virtual structures;
- exact retained render/hit/semantic geometry sharing;
- expected absence of unnecessary work;
- leak-free disposal;
- independent fake platform adapters can consume the same universal core contracts.
