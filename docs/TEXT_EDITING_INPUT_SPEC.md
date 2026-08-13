# Text Layout, Editing, and Platform Input Specification

## 1. Purpose

`lumentika-core` owns text editing state, editing commands, cursor/selection/composition behavior, text-field semantics, and the platform-neutral contract used by IMEs and other text-input systems.

Platform modules own native keyboard/IME integration, font implementation, clipboard integration, autofill bridge, and rich-content transport.

## 2. Text model

Kotlin `String` is the canonical plain-text storage type.

Text offsets use UTF-16 code-unit indices because they map directly to Kotlin/JVM `String` indexing and platform IME selection/composition ranges.

User-visible cursor movement, deletion, and selection expansion must use grapheme/word boundaries supplied by text-layout/boundary services rather than assuming one code unit equals one character.

## 3. TextRange

```kotlin
data class TextRange(
    val start: Int,
    val end: Int
) {
    val collapsed: Boolean
        get() = start == end
}
```

Rules:

```text
0 <= start <= text.length
0 <= end <= text.length
```

Direction/affinity may be carried separately where bidirectional text requires it.

## 4. Atomic editing state

```kotlin
data class TextEditingValue(
    val text: String,
    val selection: TextRange,
    val composition: TextRange? = null
)
```

`composition` is independent from `selection`.

All platform editing commands apply atomically to one `TextEditingValue`.

## 5. Public textField value

The normal `textField` API remains ergonomic:

```kotlin
val name = state("")

textField {
    bindValue(name)
}
```

The public text binding is `String`.

The control owns an internal `TextEditingController` containing the complete editing state.

Optional advanced bindings may expose selection where applications require it:

```kotlin
textField {
    bindValue(name)
    bindSelection(selection)
}
```

Composition remains runtime/IME state unless explicitly exposed as read-only diagnostic state.

## 6. External value reconciliation

When an externally bound string changes:

```text
replace controller text
→ clamp/reconcile selection
→ clear or reconcile stale composition
→ update text layout
→ notify active text-input session
```

The control never leaves selection/composition outside the new string bounds.

## 7. TextEditingController

Conceptual core object:

```kotlin
class TextEditingController(
    initial: TextEditingValue
) {
    val value: TextEditingValue

    fun apply(
        command: TextEditCommand
    ): TextEditingChange
}
```

It owns:

```text
selection movement
composition state
replacement/deletion
clipboard command routing
undo transaction boundaries when implemented
cursor blink state
auto-scroll request to keep caret visible
```

## 8. Editing commands

Platform text input is normalized into typed commands.

Baseline:

```text
CommitText
SetComposingText
SetComposingRegion
FinishComposition
SetSelection
DeleteSurroundingText
DeleteSurroundingTextInCodePoints
InsertText
ReplaceSelection
MoveCursor
SelectWord
SelectAll
Copy
Cut
Paste
PerformEditorAction
```

Conceptual sealed hierarchy:

```kotlin
sealed interface TextEditCommand
```

Platform modules may translate richer native operations into these core commands or add capability-gated typed extensions.

## 9. Batch edits

A text-input session supports nested batch editing.

```text
beginBatch
→ commands
→ commands
→ endBatch
```

During a batch:

```text
editing state mutates normally
external binding publication is coalesced
selection/composition notification is coalesced
layout invalidation is coalesced
```

Exceptions cannot leave batch depth corrupted.

## 10. Text input configuration

```kotlin
data class TextInputConfiguration(
    val multiline: Boolean,
    val readOnly: Boolean,
    val secure: Boolean,
    val keyboardType: KeyboardType,
    val capitalization: Capitalization,
    val autoCorrect: Boolean,
    val imeAction: ImeAction,
    val enterBehavior: EnterBehavior
)
```

Platform support is capability-dependent.

Unsupported hints degrade without changing core editing correctness.

## 11. Platform TextInputService

```kotlin
interface TextInputService {
    fun startSession(
        client: TextInputClient,
        configuration: TextInputConfiguration,
        initialValue: TextEditingValue
    ): TextInputSession
}
```

```kotlin
interface TextInputSession : AutoCloseable {
    fun updateState(
        value: TextEditingValue
    )

    fun updateCursorGeometry(
        geometry: TextCursorGeometry
    )

    fun showKeyboard()
    fun hideKeyboard()
}
```

```kotlin
interface TextInputClient {
    fun apply(command: TextEditCommand)
    fun performAction(action: ImeAction)
}
```

Only the focused editable control owns the active session unless the platform explicitly supports multiple simultaneous editors.

## 12. Focus lifecycle

Focus gain on an editable control:

```text
create TextInputSession
publish current editing state/configuration
publish caret/editor geometry
optionally request software keyboard
```

Focus loss/unmount:

```text
finish/cancel composition according to policy
close session
release platform references
```

A stale session cannot mutate an unmounted controller.

## 13. Text layout service

Both `text` and `textField` require a richer shared layout result than width/height alone.

Core contract:

```kotlin
interface TextLayoutService {
    fun layout(
        request: TextLayoutRequest
    ): TextLayoutResult
}
```

Request includes:

```text
text / styled runs
font family/resolved size/weight/style
platform font-weight adjustment
letter spacing / line height
wrap mode
available inline size
layout direction / locale
maximum lines / overflow policy where supported
```

Result includes:

```text
size
baselines
line metrics
glyph/run geometry sufficient for rendering
hit-test point -> text offset
text offset -> caret rectangle
selection range -> visual rectangles
word/grapheme boundary queries or references to boundary service
```

The same resolved layout result is used for measurement and rendering for one resource/environment generation. A font-weight-adjustment change invalidates affected text layout even when the declared `fontWeight` style value is unchanged.

## 14. Bidirectional text

Text layout and cursor movement must support bidirectional text.

Core stores logical UTF-16 offsets.

`TextLayoutResult` supplies visual caret geometry and affinity where one logical position has multiple visual edges.

`Direction` and locale environment feed text layout and Taffy direction consistently.

## 15. Cursor and selection geometry

The editor publishes:

```kotlin
data class TextCursorGeometry(
    val caretRect: Rect,
    val editorRect: Rect,
    val visibleLineRects: List<Rect>,
    val transformToRoot: Matrix
)
```

The platform adapter receives geometry after layout/PrePaint so transforms and scroll offsets are current.

This supports IME candidate positioning, handwriting, accessibility, and selection handles.

## 16. Pointer editing behavior

Core `textField` owns:

```text
tap -> caret placement
drag -> selection extension
double tap -> word selection
long press -> platform/context selection behavior hook
scroll while selecting
caret auto-scroll
```

Recognition uses the core gesture runtime.

Text boundary/layout service determines the resulting offsets.

## 17. Keyboard editing behavior

Core owns platform-independent commands such as:

```text
left/right logical/visual cursor movement
up/down line movement
home/end
word movement
backspace/delete
selection extension with modifiers
select all
copy/cut/paste shortcuts after normalized key mapping
enter/tab behavior according to configuration
```

Raw native key codes are translated by the platform adapter.

## 18. Clipboard service

```kotlin
interface ClipboardService {
    fun read(): ClipboardContent?
    fun write(content: ClipboardContent)
}
```

Baseline content:

```text
plain text
styled text when available
platform-rich payload reference when available
```

`textField` uses the service only through core editing/default actions.

## 19. Receive-content contract

Core supports insertion/handling of transferable content independently of whether it arrived from:

```text
clipboard paste
drag and drop
software keyboard rich-content commit
platform share/content API
```

```kotlin
data class TransferContent(
    val items: List<TransferItem>,
    val source: TransferSource
)
```

Core standard text fields consume supported textual items and return unconsumed items.

Platform-specific binary handles remain opaque typed platform payloads with platform-owned permission/lifetime rules.

## 20. Drag and drop

The gesture runtime handles in-process drag gestures.

Cross-application/platform drag-and-drop uses `ContentTransferService` from `PLATFORM_ENVIRONMENT_SPEC.md`.

The core drag source constructs a `DragRequest` from its normalized `TransferContent`, preview, and allowed transfer actions. The platform service owns the native drag session.

Incoming platform drops are normalized to `TransferContent` and dispatched through hit-tested receive-content targets.

## 21. Autofill

Editable controls can publish platform-independent autofill metadata:

```kotlin
data class AutofillConfiguration(
    val hints: Set<AutofillHint>,
    val sensitive: Boolean,
    val enabled: Boolean = true
)
```

Baseline hints include semantic categories such as:

```text
USERNAME
PASSWORD
NEW_PASSWORD
EMAIL
PHONE
NAME
ADDRESS
POSTAL_CODE
CREDIT_CARD_NUMBER
ONE_TIME_CODE
```

Core commits a separate autofill artifact from mounted autofill-enabled controls:

```kotlin
@JvmInline
value class AutofillNodeId(
    val value: Long
)

data class AutofillArtifact(
    val nodes: List<AutofillNode>
)

data class AutofillChangeSet(
    val changedNodes: Set<AutofillNodeId>,
    val removedNodes: Set<AutofillNodeId>
)
```

Autofill identity is stable for the lifetime of the mounted field and is independent from label text, tree index, and screen coordinates.

Autofill geometry is attached from committed render geometry using the same transform/clip chain as hit testing and semantics.

The platform adapter builds its native virtual autofill structure from this artifact.

Autofill response applies through the same normal text/value binding/controller path.

Autofill and accessibility are separate platform contracts even when both derive geometry from the same mounted control.

## 22. Secure fields

For `secure = true`:

```text
visual masking is component/theme behavior
semantics expose password state
clipboard/cut policy is explicit
logs/diagnostics must not dump text by default
platform autofill may still be enabled according to AutofillConfiguration
```

The underlying app value remains the application-owned string unless a separate secure storage layer is used.

## 23. Cursor blink

Cursor blink is core component state driven by the root clock.

It does not use platform timers that bypass the scheduler.

When the root is suspended, cursor blink follows root suspension policy.

## 24. Intrinsic/layout invalidation

Text content/style changes route precisely:

```text
text content change
→ text layout invalidation
→ intrinsic/layout when metrics/wrapping change
→ paint

color-only change
→ paint only

selection/caret change
→ paint + semantics + cursor geometry
→ no Taffy unless auto-scroll/layout policy requires it

composition text change
→ text layout/intrinsic/layout/paint as required
```

## 25. Platform capability boundary

Core correctness must not require:

```text
software keyboard
IME composition
handwriting
autofill
rich content
clipboard
```

A platform may supply no-op/unsupported services.

Components expose capabilities and degrade predictably.

## 26. Tests

Editing model:

```text
UTF-16 range validation
commit text
composition replacement
finish composition
selection replacement
delete surrounding text/codepoints
batch edit coalescing
external value reconciliation
stale session after unmount
```

Geometry:

```text
point -> offset
offset -> caret rect
selection rects
bidi caret affinity
wrapped lines
auto-scroll to caret
```

Platform contracts:

```text
session start/update/close
cursor geometry publication
clipboard copy/cut/paste
receive content consumption
autofill apply through binding
secure field privacy behavior
```

## 27. Invariants

- editing state is core-owned;
- platform IME sends commands rather than mutating component internals;
- selection and composition are independent;
- one focused text editor owns one platform text-input session;
- measurement and rendering share one text-layout result/configuration;
- clipboard/autofill/drag-drop are platform services, not platform-specific textField implementations;
- external value changes reconcile editor state deterministically;
- text input never creates a second focus/event tree.
