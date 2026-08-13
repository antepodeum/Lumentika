package com.antepod.lumentika.platform

import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.state

/** Revision numbers used to invalidate values resolved from platform units. */
public data class UnitRevisions(val dp: Long = 0, val sp: Long = 0, val physicalPx: Long = 0)

/** Platform density and scale values used by a [UnitResolver]. */
public data class UnitEnvironment(
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val physicalPixelScale: Float = 1f,
    val revisions: UnitRevisions = UnitRevisions(),
)

/** The logical inline direction of UI content. */
public enum class LayoutDirection {
    LTR,
    RTL,
}

/** The platform's preferred light or dark color scheme. */
public enum class ColorScheme {
    LIGHT,
    DARK,
}

/** Lifecycle state controlling frame-dependent work. */
public enum class UiLifecycleState {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    DISPOSED,
}

/** A locale represented by an IETF BCP 47 language tag. */
public data class UiLocale(val tag: String)

/** Accessibility presentation preferences published by the platform. */
public data class AccessibilityPreferences(
    val highContrastText: Boolean = false,
    val fontWeightAdjustment: Int = 0,
)

/** Platform-specific thresholds used by gesture recognizers. */
public data class GestureConfiguration(
    val touchSlop: Float = 8f,
    val doubleTapTimeoutMillis: Long = 300,
    val longPressTimeoutMillis: Long = 500,
    val minimumFlingVelocity: Float = 50f,
    val maximumFlingVelocity: Float = 8_000f,
)

/** Native services and input modes available to the current UI surface. */
public data class PlatformCapabilities(
    val touch: Boolean = false,
    val mouse: Boolean = true,
    val pen: Boolean = false,
    val hover: Boolean = true,
    val hardwareKeyboard: Boolean = true,
    val softwareKeyboard: Boolean = false,
    val handwriting: Boolean = false,
    val clipboard: Boolean = false,
    val haptics: Boolean = false,
    val accessibility: Boolean = false,
    val autofill: Boolean = false,
    val dragDrop: Boolean = false,
    val richContent: Boolean = false,
)

/** Insets reported by the host for system UI, cutouts, IME, and safe areas. */
public data class UiInsets(
    val systemBars: Insets = Insets(),
    val displayCutout: Insets = Insets(),
    val ime: Insets = Insets(),
    val systemGestures: Insets = Insets(),
    val mandatorySystemGestures: Insets = Insets(),
    val tappableElement: Insets = Insets(),
    val safeDrawing: Insets = Insets(),
    val safeGestures: Insets = Insets(),
    val safeContent: Insets = Insets(),
)

/** Immutable platform state consumed by one UI root. */
public data class UiEnvironment(
    val viewport: Size,
    val units: UnitEnvironment = UnitEnvironment(),
    val layoutDirection: LayoutDirection = LayoutDirection.LTR,
    val locales: List<UiLocale> = listOf(UiLocale("en")),
    val colorScheme: ColorScheme = ColorScheme.LIGHT,
    val accessibility: AccessibilityPreferences = AccessibilityPreferences(),
    val motionDurationScale: Float = 1f,
    val insets: UiInsets = UiInsets(),
    val gesture: GestureConfiguration = GestureConfiguration(),
    val capabilities: PlatformCapabilities = PlatformCapabilities(),
    val lifecycle: UiLifecycleState = UiLifecycleState.ACTIVE,
) {
    init {
        require(motionDurationScale.isFinite() && motionDurationScale >= 0f)
    }
}

/** Converts platform-relative units into logical core pixels. */
public interface UnitResolver {
    public fun resolveDp(value: Float, environment: UiEnvironment): Float

    public fun resolveSp(value: Float, environment: UiEnvironment): Float

    public fun resolvePhysicalPx(value: Float, environment: UiEnvironment): Float
}

/** Default resolver that applies the scales stored in [UiEnvironment.units]. */
public object LogicalUnitResolver : UnitResolver {
    override fun resolveDp(value: Float, environment: UiEnvironment): Float =
        value * environment.units.density

    override fun resolveSp(value: Float, environment: UiEnvironment): Float =
        value * environment.units.density

    override fun resolvePhysicalPx(value: Float, environment: UiEnvironment): Float =
        value * environment.units.physicalPixelScale
}

/** Requests a future native frame callback for a UI root. */
public interface FrameScheduler {
    public fun requestFrame()
}

/** Semantic feedback requested by a core interaction. */
public enum class UiFeedbackType {
    PRESS,
    RELEASE,
    LONG_PRESS,
    TOGGLE,
    SELECTION_CHANGE,
    SCROLL_TICK,
    SCROLL_LIMIT,
    CONFIRM,
    ERROR,
}

/** A request for host-provided audio or haptic feedback. */
public data class UiFeedbackRequest(val type: UiFeedbackType, val intensity: Float = 1f)

/** Performs native feedback for semantic UI interactions. */
public interface UiFeedbackService {
    public fun perform(request: UiFeedbackRequest)
}

/** Semantic cursor shapes understood by platform adapters. */
public enum class PointerCursorRole {
    DEFAULT,
    POINTER,
    TEXT,
    CROSSHAIR,
    RESIZE_HORIZONTAL,
    RESIZE_VERTICAL,
    GRAB,
    GRABBING,
    HANDWRITING,
}

/** Updates the native pointer cursor. */
public interface PointerCursorService {
    public fun set(role: PointerCursorRole)
}

/** A platform-neutral URI value. */
public data class UiUri(val value: String)

/** Opens external URIs through the host platform. */
public interface UriLauncher {
    public fun open(uri: UiUri): Boolean
}

/** Operations allowed for a content-transfer session. */
public enum class TransferAction {
    COPY,
    MOVE,
    LINK,
}

/** One MIME-typed item transferred through clipboard, drag/drop, or input. */
public data class TransferItem(
    val mimeType: String,
    val text: String? = null,
    val uri: UiUri? = null,
    val bytes: ByteArray? = null,
)

/** Origin of transferred content. */
public enum class TransferSource {
    CLIPBOARD,
    DRAG_DROP,
    INPUT_METHOD,
    SHARE,
}

/** A collection of items entering or leaving the UI. */
public data class TransferContent(
    val items: List<TransferItem>,
    val source: TransferSource = TransferSource.CLIPBOARD,
)

/** Adapter-owned visual preview for a native drag operation. */
public interface DragPreview

/** Parameters used to begin a native drag operation. */
public data class DragRequest(
    val content: TransferContent,
    val preview: DragPreview? = null,
    val allowedActions: Set<TransferAction> = setOf(TransferAction.COPY),
)

/** An active native drag operation. Closing it cancels or releases the session. */
public interface DragSession : AutoCloseable

/** Starts native drag-and-drop operations. */
public interface ContentTransferService {
    public fun startDrag(request: DragRequest): DragSession?
}

/** Phase of a predictive or immediate back-navigation gesture. */
public enum class BackPhase {
    START,
    PROGRESS,
    CANCEL,
    COMMIT,
}

/** Back-navigation progress delivered to registered handlers. */
public data class BackEvent(
    val phase: BackPhase,
    val progress: Float = if (phase == BackPhase.COMMIT) 1f else 0f,
)

/** Registers handlers with the host back-navigation dispatcher. */
public interface BackDispatcher {
    public fun register(handler: (BackEvent) -> Boolean): AutoCloseable
}

/** Reads and writes plain text through the native clipboard. */
public interface ClipboardService {
    public fun readText(): String?

    public fun writeText(text: String)
}

/** Observable holder for the latest immutable [UiEnvironment]. */
public class UiEnvironmentState(initial: UiEnvironment) : Readable<UiEnvironment> {
    private val state: State<UiEnvironment> = state(initial)
    override val value: UiEnvironment
        get() = state.value

    public fun publish(environment: UiEnvironment) {
        state.value = environment
    }
}

/** Coalesces repeated frame requests until the pending frame is consumed. */
public class CoalescingFrameScheduler(private val delegate: FrameScheduler) {
    public var pending: Boolean = false
        private set

    public fun requestFrame() {
        if (pending) return
        pending = true
        delegate.requestFrame()
    }

    public fun consume() {
        pending = false
    }
}
