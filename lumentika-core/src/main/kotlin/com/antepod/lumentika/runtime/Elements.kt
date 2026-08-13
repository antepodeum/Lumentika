package com.antepod.lumentika.runtime

import com.antepod.lumentika.animation.ElementAnimationRuntime
import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.input.EventDispatcher
import com.antepod.lumentika.input.FocusManager
import com.antepod.lumentika.platform.ClipboardService
import com.antepod.lumentika.platform.GestureConfiguration
import com.antepod.lumentika.platform.PointerCursorService
import com.antepod.lumentika.platform.UiFeedbackService
import com.antepod.lumentika.reactive.ComponentScope
import com.antepod.lumentika.render.RenderProperties
import com.antepod.lumentika.style.Paint
import com.antepod.lumentika.style.Style
import com.antepod.lumentika.style.StyleState
import com.antepod.lumentika.style.rgb
import com.antepod.lumentika.text.AutofillRuntime
import com.antepod.lumentika.text.HeadlessTextLayoutService
import com.antepod.lumentika.text.TextInputService
import com.antepod.lumentika.text.TextLayoutService
import java.util.concurrent.atomic.AtomicLong

/** Prevents accidental receiver mixing inside the UI builder DSL. */
@DslMarker public annotation class UiDsl

/** Constraints supplied when measuring intrinsic content. */
public data class IntrinsicMeasureInput(
    val knownWidth: Float? = null,
    val knownHeight: Float? = null,
    val availableWidth: MeasureSpace = MeasureSpace.MaxContent,
    val availableHeight: MeasureSpace = MeasureSpace.MaxContent,
)

/** Definite or intrinsic available space for content measurement. */
public sealed interface MeasureSpace {
    public data class Definite(val value: Float) : MeasureSpace

    public data object MinContent : MeasureSpace

    public data object MaxContent : MeasureSpace
}

/** Content that can report intrinsic dimensions to layout. */
public interface IntrinsicMeasurable {
    /** Measures content under [input] constraints. */
    public fun measure(input: IntrinsicMeasureInput): Size
}

/** Records immutable core paint commands. */
public interface PaintRecorder {
    /** Appends [command] to the current retained paint recording. */
    public fun record(command: PaintCommand)
}

/** One core or adapter-defined retained drawing command. */
public sealed interface PaintCommand {
    public data class FillRect(val rect: Rect, val color: Int) : PaintCommand

    public data class DrawText(
        val request: com.antepod.lumentika.text.TextLayoutRequest,
        val layout: com.antepod.lumentika.text.TextLayoutResult,
        val rect: Rect,
        val paint: Paint,
    ) : PaintCommand {
        public val text: String
            get() = request.text
    }

    public data class Fill(val rect: Rect, val paint: Paint) : PaintCommand

    public data class DrawImage(val source: ImageSource, val rect: Rect) : PaintCommand

    public data class Backend(val extension: BackendPaintCommand) : PaintCommand
}

/** Marker for a paint command interpreted only by a specific platform backend. */
public interface BackendPaintCommand

/** Resolves intrinsic image dimensions for layout. */
public fun interface ImageService {
    /** Returns known dimensions for [source], or `null` while unavailable. */
    public fun intrinsicSize(source: ImageSource): Size?
}

/** Byte-backed or URI-backed image reference. */
public sealed interface ImageSource {
    public data class Bytes(val bytes: ByteArray, val mimeType: String) : ImageSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }

    public data class Uri(val value: String) : ImageSource
}

/** Terminal element content capable of recording paint commands. */
public interface Content {
    /** Records this content within local [bounds]. */
    public fun record(recorder: PaintRecorder, bounds: Rect)
}

/** Geometry required by the standard draw transition. Values are expressed in local pixels. */
public interface PathMetrics {
    public val pathLength: Float

    /** Extra visible length contributed by non-butt stroke caps. */
    public val strokeExtension: Float
        get() = 0f
}

/** Supplies custom local-coordinate hit testing for content. */
public interface HitRegionSource {
    public fun hitTest(localPoint: com.antepod.lumentika.geometry.Point, bounds: Rect): Boolean
}

/** Custom rendered content that can return adapter-owned raycast objects. */
public interface SceneContent : Content, HitRegionSource {
    public fun raycast(localPoint: com.antepod.lumentika.geometry.Point): Any?
}

/** Text content measured and recorded through a shared [TextLayoutService]. */
public class TextContent(
    public val request: com.antepod.lumentika.text.TextLayoutRequest,
    private val layoutService: TextLayoutService = HeadlessTextLayoutService,
) : Content, IntrinsicMeasurable {
    public constructor(
        text: String,
        layoutService: TextLayoutService = HeadlessTextLayoutService,
    ) : this(com.antepod.lumentika.text.TextLayoutRequest(text), layoutService)

    public val text: String
        get() = request.text

    private val layouts =
        mutableMapOf<
            com.antepod.lumentika.text.TextLayoutRequest,
            com.antepod.lumentika.text.TextLayoutResult,
        >()
    public var lastLayoutResult: com.antepod.lumentika.text.TextLayoutResult? = null
        private set

    override fun measure(input: IntrinsicMeasureInput): Size {
        val maxWidth =
            input.knownWidth
                ?: (input.availableWidth as? MeasureSpace.Definite)?.value
                ?: request.maxWidth
        return layout(maxWidth).size.let {
            Size(input.knownWidth ?: it.width, input.knownHeight ?: it.height)
        }
    }

    override fun record(recorder: PaintRecorder, bounds: Rect) {
        val result = layout(bounds.width)
        recorder.record(
            PaintCommand.DrawText(
                request.copy(maxWidth = bounds.width),
                result,
                bounds,
                rgb(0, 0, 0),
            )
        )
    }

    private fun layout(maxWidth: Float?): com.antepod.lumentika.text.TextLayoutResult {
        val effective = request.copy(maxWidth = maxWidth)
        return layouts
            .getOrPut(effective) { layoutService.layout(effective) }
            .also {
                lastLayoutResult = it
            }
    }
}

/** Image content with optional known intrinsic dimensions. */
public data class ImageContent(val source: ImageSource, val intrinsicSize: Size? = null) :
    Content, IntrinsicMeasurable {
    override fun measure(input: IntrinsicMeasureInput): Size =
        Size(
            input.knownWidth ?: intrinsicSize?.width ?: 0f,
            input.knownHeight ?: intrinsicSize?.height ?: 0f,
        )

    override fun record(recorder: PaintRecorder, bounds: Rect) =
        recorder.record(PaintCommand.DrawImage(source, bounds))
}

/** Persistent node in the retained logical UI tree. */
public open class Element(public val kind: String = "element") : AutoCloseable {
    public val id: Long = nextId.incrementAndGet()
    public var parent: Element? = null
        private set

    private val mutableChildren = mutableListOf<Element>()
    public val children: List<Element>
        get() = mutableChildren

    private val contentListeners = LinkedHashSet<(Content?) -> Unit>()
    public var content: Content? = null
        set(value) {
            if (field === value) return
            field = value
            contentListeners.toList().forEach { it(value) }
        }

    public var geometry: Rect = Rect(0f, 0f, 0f, 0f)
    public val scope: ComponentScope = ComponentScope()
    private val attachments = mutableMapOf<AttachmentKey<*>, Any>()
    public var isMounted: Boolean = true
        private set

    private var isClosing: Boolean = false

    init {
        OwnershipCounters.mountElement()
    }

    /** Moves [child] to the end of this element's child list. */
    public fun append(child: Element) {
        require(child.parent == null) { "Element ${child.id} already has a parent" }
        child.parent = this
        mutableChildren += child
    }

    /** Moves [child] to [index] in this element's child list. */
    public fun insert(index: Int, child: Element) {
        require(child.parent == null) { "Element ${child.id} already has a parent" }
        child.parent = this
        mutableChildren.add(index, child)
    }

    /** Detaches [child] and optionally disposes its owned subtree. */
    public fun remove(child: Element, dispose: Boolean = true): Boolean {
        if (child !in mutableChildren) return false
        try {
            if (dispose) child.close()
        } finally {
            mutableChildren.remove(child)
            child.parent = null
        }
        return true
    }

    /** Reorders an existing direct [child] to [index]. */
    public fun move(child: Element, index: Int) {
        require(child.parent === this)
        mutableChildren.remove(child)
        mutableChildren.add(index.coerceIn(0, mutableChildren.size), child)
    }

    /** Associates [value] with the identity-based attachment [key]. */
    public fun <T : Any> attach(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

    /** Returns the value associated with [key], if present. */
    public fun <T : Any> attachment(key: AttachmentKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attachments[key] as T?
    }

    public fun onContentChanged(listener: (Content?) -> Unit): AutoCloseable {
        contentListeners += listener
        return AutoCloseable { contentListeners -= listener }
    }

    override fun close() {
        if (!isMounted || isClosing) return
        isClosing = true
        var failure: Throwable? = null
        fun safely(block: () -> Unit) {
            try {
                block()
            } catch (error: Throwable) {
                failure = failure ?: error
            }
        }
        mutableChildren.toList().asReversed().forEach { child -> safely { remove(child) } }
        safely(scope::close)
        contentListeners.clear()
        attachments.values.filterIsInstance<AutoCloseable>().forEach { safely(it::close) }
        attachments.clear()
        isMounted = false
        isClosing = false
        OwnershipCounters.unmountElement()
        failure?.let { throw it }
    }

    public companion object {
        private val nextId = AtomicLong()
    }
}

/** Boxless structural element used to group child declarations. */
public class Fragment : Element("fragment")

/** Identity-based typed key for element-local runtime attachments. */
public class AttachmentKey<T : Any>

/** Services and root callbacks inherited by nested [UiScope] instances. */
public data class UiContext(
    val textLayout: TextLayoutService = HeadlessTextLayoutService,
    val textInput: TextInputService? = null,
    val clipboard: ClipboardService? = null,
    val autofill: AutofillRuntime? = null,
    val images: ImageService? = null,
    val animationClock: UiAnimationClock = UiAnimationClock(),
    val elementAnimations: ElementAnimationRuntime? = null,
    val focus: FocusManager? = null,
    val events: EventDispatcher? = null,
    val requestFrame: (layoutDirty: Boolean) -> Unit = {},
    val configureRender: (Element, RenderProperties) -> Unit = { _, _ -> },
    val attachStyle: (Element, Style) -> Unit = { _, _ -> },
    val committedBounds: (Element) -> Rect? = { null },
    val gestureConfiguration: () -> GestureConfiguration = { GestureConfiguration() },
    val feedback: UiFeedbackService? = null,
    val cursor: PointerCursorService? = null,
    val setStyleState: (Element, StyleState, Boolean) -> Unit = { _, _, _ -> },
)

@UiDsl
/** DSL receiver that mounts elements beneath [parent]. */
public open class UiScope(public val parent: Element, public val context: UiContext = UiContext()) {
    /** Creates and appends an element with optional content and child declarations. */
    public fun element(
        kind: String = "element",
        content: Content? = null,
        block: UiScope.() -> Unit = {},
    ): Element {
        val element = Element(kind)
        element.content = content
        parent.append(element)
        nested(element).block()
        return element
    }

    /** Creates a boxless fragment and mounts [block] beneath it. */
    public fun fragment(block: UiScope.() -> Unit = {}): Fragment {
        val fragment = Fragment()
        parent.append(fragment)
        nested(fragment).block()
        return fragment
    }

    /** Creates a child scope sharing this scope's runtime context. */
    public fun nested(parent: Element): UiScope = UiScope(parent, context)
}
