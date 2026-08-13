package com.antepod.lumentika.platform

import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.state

public data class UnitRevisions(val dp: Long = 0, val sp: Long = 0, val physicalPx: Long = 0)
public data class UnitEnvironment(
    val density: Float = 1f,
    val fontScale: Float = 1f,
    val physicalPixelScale: Float = 1f,
    val revisions: UnitRevisions = UnitRevisions(),
)

public enum class LayoutDirection { LTR, RTL }
public enum class ColorScheme { LIGHT, DARK }
public enum class UiLifecycleState { ACTIVE, INACTIVE, SUSPENDED, DISPOSED }
public data class UiLocale(val tag: String)
public data class AccessibilityPreferences(val highContrastText: Boolean = false, val fontWeightAdjustment: Int = 0)
public data class GestureConfiguration(
    val touchSlop: Float = 8f,
    val doubleTapTimeoutMillis: Long = 300,
    val longPressTimeoutMillis: Long = 500,
    val minimumFlingVelocity: Float = 50f,
    val maximumFlingVelocity: Float = 8_000f,
)
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
    init { require(motionDurationScale.isFinite() && motionDurationScale >= 0f) }
}

public interface UnitResolver {
    public fun resolveDp(value: Float, environment: UiEnvironment): Float
    public fun resolveSp(value: Float, environment: UiEnvironment): Float
    public fun resolvePhysicalPx(value: Float, environment: UiEnvironment): Float
}

public object LogicalUnitResolver : UnitResolver {
    override fun resolveDp(value: Float, environment: UiEnvironment): Float = value * environment.units.density
    override fun resolveSp(value: Float, environment: UiEnvironment): Float = value * environment.units.density
    override fun resolvePhysicalPx(value: Float, environment: UiEnvironment): Float = value * environment.units.physicalPixelScale
}

public interface FrameScheduler { public fun requestFrame() }
public enum class UiFeedbackType { PRESS, RELEASE, LONG_PRESS, TOGGLE, SELECTION_CHANGE, SCROLL_TICK, SCROLL_LIMIT, CONFIRM, ERROR }
public data class UiFeedbackRequest(val type: UiFeedbackType, val intensity: Float = 1f)
public interface UiFeedbackService { public fun perform(request: UiFeedbackRequest) }
public enum class PointerCursorRole { DEFAULT, POINTER, TEXT, CROSSHAIR, RESIZE_HORIZONTAL, RESIZE_VERTICAL, GRAB, GRABBING, HANDWRITING }
public interface PointerCursorService { public fun set(role: PointerCursorRole) }
public data class UiUri(val value: String)
public interface UriLauncher { public fun open(uri: UiUri): Boolean }

public enum class TransferAction { COPY, MOVE, LINK }
public data class TransferItem(val mimeType: String, val text: String? = null, val uri: UiUri? = null, val bytes: ByteArray? = null)
public data class TransferContent(val items: List<TransferItem>)
public interface DragPreview
public data class DragRequest(val content: TransferContent, val preview: DragPreview? = null, val allowedActions: Set<TransferAction> = setOf(TransferAction.COPY))
public interface DragSession : AutoCloseable
public interface ContentTransferService { public fun startDrag(request: DragRequest): DragSession? }

public enum class BackPhase { START, PROGRESS, CANCEL, COMMIT }
public data class BackEvent(val phase: BackPhase, val progress: Float = if (phase == BackPhase.COMMIT) 1f else 0f)
public interface BackDispatcher { public fun register(handler: (BackEvent) -> Boolean): AutoCloseable }

public interface ClipboardService {
    public fun readText(): String?
    public fun writeText(text: String)
}

public class UiEnvironmentState(initial: UiEnvironment) : Readable<UiEnvironment> {
    private val state: State<UiEnvironment> = state(initial)
    override val value: UiEnvironment get() = state.value
    public fun publish(environment: UiEnvironment) { state.value = environment }
}

public class CoalescingFrameScheduler(private val delegate: FrameScheduler) {
    public var pending: Boolean = false
        private set
    public fun requestFrame() {
        if (pending) return
        pending = true
        delegate.requestFrame()
    }
    public fun consume() { pending = false }
}
