package com.antepod.lumentika.runtime

import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.reactive.ComponentScope
import java.util.concurrent.atomic.AtomicLong

@DslMarker public annotation class UiDsl

public data class IntrinsicMeasureInput(
    val knownWidth: Float? = null,
    val knownHeight: Float? = null,
    val availableWidth: MeasureSpace = MeasureSpace.MaxContent,
    val availableHeight: MeasureSpace = MeasureSpace.MaxContent,
)

public sealed interface MeasureSpace {
    public data class Definite(val value: Float) : MeasureSpace

    public data object MinContent : MeasureSpace

    public data object MaxContent : MeasureSpace
}

public interface IntrinsicMeasurable {
    public fun measure(input: IntrinsicMeasureInput): Size
}

public interface PaintRecorder {
    public fun record(command: PaintCommand)
}

public sealed interface PaintCommand {
    public data class FillRect(val rect: Rect, val color: Int) : PaintCommand

    public data class DrawText(val text: String, val rect: Rect, val color: Int) : PaintCommand

    public data class DrawImage(val source: ImageSource, val rect: Rect) : PaintCommand

    public data class Backend(val extension: BackendPaintCommand) : PaintCommand
}

public interface BackendPaintCommand

public sealed interface ImageSource {
    public data class Bytes(val bytes: ByteArray, val mimeType: String) : ImageSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && mimeType == other.mimeType && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }

    public data class Uri(val value: String) : ImageSource
}

public interface Content {
    public fun record(recorder: PaintRecorder, bounds: Rect)
}

public interface HitRegionSource {
    public fun hitTest(localPoint: com.antepod.lumentika.geometry.Point, bounds: Rect): Boolean
}

public interface SceneContent : Content, HitRegionSource {
    public fun raycast(localPoint: com.antepod.lumentika.geometry.Point): Any?
}

public class TextContent(
    public val request: com.antepod.lumentika.text.TextLayoutRequest,
    private val layoutService: com.antepod.lumentika.text.TextLayoutService =
        com.antepod.lumentika.text.HeadlessTextLayoutService,
) : Content, IntrinsicMeasurable {
    public constructor(text: String) : this(com.antepod.lumentika.text.TextLayoutRequest(text))

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
        layout(bounds.width)
        recorder.record(PaintCommand.DrawText(text, bounds, 0xff000000.toInt()))
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

    init {
        OwnershipCounters.mountElement()
    }

    public fun append(child: Element) {
        require(child.parent == null) { "Element ${child.id} already has a parent" }
        child.parent = this
        mutableChildren += child
    }

    public fun insert(index: Int, child: Element) {
        require(child.parent == null) { "Element ${child.id} already has a parent" }
        child.parent = this
        mutableChildren.add(index, child)
    }

    public fun remove(child: Element, dispose: Boolean = true): Boolean {
        if (!mutableChildren.remove(child)) return false
        child.parent = null
        if (dispose) child.close()
        return true
    }

    public fun move(child: Element, index: Int) {
        require(child.parent === this)
        mutableChildren.remove(child)
        mutableChildren.add(index.coerceIn(0, mutableChildren.size), child)
    }

    public fun <T : Any> attach(key: AttachmentKey<T>, value: T) {
        attachments[key] = value
    }

    public fun <T : Any> attachment(key: AttachmentKey<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return attachments[key] as T?
    }

    public fun onContentChanged(listener: (Content?) -> Unit): AutoCloseable {
        contentListeners += listener
        return AutoCloseable { contentListeners -= listener }
    }

    override fun close() {
        if (!isMounted) return
        isMounted = false
        OwnershipCounters.unmountElement()
        mutableChildren.toList().asReversed().forEach { remove(it) }
        scope.close()
        contentListeners.clear()
        attachments.values.filterIsInstance<AutoCloseable>().forEach(AutoCloseable::close)
        attachments.clear()
    }

    public companion object {
        private val nextId = AtomicLong()
    }
}

public class Fragment : Element("fragment")

public class AttachmentKey<T : Any>

@UiDsl
public open class UiScope(public val parent: Element) {
    public fun element(
        kind: String = "element",
        content: Content? = null,
        block: UiScope.() -> Unit = {},
    ): Element {
        val element = Element(kind)
        element.content = content
        parent.append(element)
        UiScope(element).block()
        return element
    }

    public fun fragment(block: UiScope.() -> Unit = {}): Fragment {
        val fragment = Fragment()
        parent.append(fragment)
        UiScope(fragment).block()
        return fragment
    }
}
