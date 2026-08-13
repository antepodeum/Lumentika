package com.antepod.lumentika.text

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.ClipboardService
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.batch
import com.antepod.lumentika.reactive.state
import java.text.BreakIterator
import java.util.Locale
import java.util.regex.Pattern

public data class TextRange(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start)
    }

    val collapsed
        get() = start == end
}

public enum class CaretAffinity {
    UPSTREAM,
    DOWNSTREAM,
}

public data class TextEditingValue(
    val text: String = "",
    val selection: TextRange = TextRange(0, 0),
    val composition: TextRange? = null,
    val affinity: CaretAffinity = CaretAffinity.DOWNSTREAM,
) {
    init {
        require(selection.end <= text.length)
        require(composition == null || composition.end <= text.length)
    }
}

public sealed interface TextEditCommand {
    public data class CommitText(val text: String) : TextEditCommand

    public data class SetComposingText(val text: String) : TextEditCommand

    public data class SetComposingRegion(val range: TextRange) : TextEditCommand

    public data object FinishComposition : TextEditCommand

    public data class SetSelection(val range: TextRange) : TextEditCommand

    public data class DeleteSurroundingText(val before: Int, val after: Int) : TextEditCommand

    public data class DeleteSurroundingCodePoints(val before: Int, val after: Int) : TextEditCommand
}

public class TextEditingController(initial: TextEditingValue = TextEditingValue()) :
    Mutable<TextEditingValue> {
    private val state: State<TextEditingValue> = state(initial)
    override var value: TextEditingValue
        get() = state.value
        set(value) {
            state.value = value
        }

    public fun apply(command: TextEditCommand) {
        value =
            when (command) {
                is TextEditCommand.CommitText -> replace(command.text, composing = false)
                is TextEditCommand.SetComposingText -> replace(command.text, composing = true)
                is TextEditCommand.SetComposingRegion -> value.copy(composition = command.range)
                TextEditCommand.FinishComposition -> value.copy(composition = null)
                is TextEditCommand.SetSelection -> value.copy(selection = command.range)
                is TextEditCommand.DeleteSurroundingText -> {
                    require(command.before >= 0 && command.after >= 0)
                    val s = (value.selection.start - command.before).coerceAtLeast(0)
                    val e = (value.selection.end + command.after).coerceAtMost(value.text.length)
                    value.copy(
                        text = value.text.removeRange(s, e),
                        selection = TextRange(s, s),
                        composition = null,
                    )
                }
                is TextEditCommand.DeleteSurroundingCodePoints -> {
                    require(command.before >= 0 && command.after >= 0)
                    val s =
                        value.text.offsetByCodePointsSafely(
                            value.selection.start,
                            -command.before,
                        )
                    val e =
                        value.text.offsetByCodePointsSafely(
                            value.selection.end,
                            command.after,
                        )
                    value.copy(
                        text = value.text.removeRange(s, e),
                        selection = TextRange(s, s),
                        composition = null,
                    )
                }
            }
    }

    public fun applyBatch(commands: List<TextEditCommand>) {
        val before = value
        batch {
            try {
                commands.forEach(::apply)
            } catch (failure: Throwable) {
                value = before
                throw failure
            }
        }
    }

    public fun reconcileExternal(text: String) {
        if (text == value.text) return
        val cursor = value.selection.end.coerceAtMost(text.length)
        value = TextEditingValue(text, TextRange(cursor, cursor), composition = null)
    }

    public fun deletePreviousGrapheme() {
        if (!value.selection.collapsed) {
            replaceSelection("")
            return
        }
        val cursor = value.selection.start
        if (cursor == 0) return
        val start = TextBoundaries.previousGrapheme(value.text, cursor)
        value =
            value.copy(
                text = value.text.removeRange(start, cursor),
                selection = TextRange(start, start),
                composition = null,
            )
    }

    public fun deleteNextGrapheme() {
        if (!value.selection.collapsed) {
            replaceSelection("")
            return
        }
        val cursor = value.selection.end
        if (cursor == value.text.length) return
        val end = TextBoundaries.nextGrapheme(value.text, cursor)
        value =
            value.copy(
                text = value.text.removeRange(cursor, end),
                selection = TextRange(cursor, cursor),
                composition = null,
            )
    }

    public fun copy(clipboard: ClipboardService) {
        clipboard.writeText(value.text.substring(value.selection.start, value.selection.end))
    }

    public fun cut(clipboard: ClipboardService) {
        copy(clipboard)
        apply(TextEditCommand.CommitText(""))
    }

    public fun paste(clipboard: ClipboardService) {
        clipboard.readText()?.let { apply(TextEditCommand.CommitText(it)) }
    }

    private fun replace(text: String, composing: Boolean): TextEditingValue {
        val range = value.composition ?: value.selection
        val next = value.text.replaceRange(range.start, range.end, text)
        val end = range.start + text.length
        return TextEditingValue(
            next,
            TextRange(end, end),
            if (composing) TextRange(range.start, end) else null,
        )
    }

    private fun replaceSelection(text: String) {
        value = value.copy(composition = null)
        value = replace(text, composing = false)
    }
}

private object TextBoundaries {
    private val grapheme = Pattern.compile("\\X")

    fun previousGrapheme(text: String, offset: Int): Int {
        require(offset in 0..text.length)
        var previous = 0
        val matcher = grapheme.matcher(text)
        while (matcher.find()) {
            if (matcher.end() >= offset) return matcher.start()
            previous = matcher.end()
        }
        return previous
    }

    fun nextGrapheme(text: String, offset: Int): Int {
        require(offset in 0..text.length)
        val matcher = grapheme.matcher(text)
        while (matcher.find()) if (matcher.end() > offset) return matcher.end()
        return text.length
    }

    fun previousWord(text: String, offset: Int): Int {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
        var boundary = iterator.preceding(offset)
        while (boundary > 0 && text[boundary].isWhitespace()) boundary =
            iterator.preceding(boundary)
        return boundary.coerceAtLeast(0)
    }

    fun nextWord(text: String, offset: Int): Int {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text) }
        var boundary = iterator.following(offset)
        while (boundary in 1 until text.length && text[boundary - 1].isWhitespace()) {
            boundary = iterator.following(boundary)
        }
        return if (boundary == BreakIterator.DONE) text.length else boundary
    }
}

private fun String.offsetByCodePointsSafely(index: Int, delta: Int): Int {
    require(index in indices || index == length)
    return if (delta < 0) {
        offsetByCodePoints(index, -codePointCount(0, index).coerceAtMost(-delta))
    } else {
        offsetByCodePoints(index, codePointCount(index, length).coerceAtMost(delta))
    }
}

public data class TextInputConfiguration(
    val multiline: Boolean = false,
    val secure: Boolean = false,
    val autofillHints: Set<String> = emptySet(),
)

public interface TextInputClient {
    public fun apply(command: TextEditCommand)
}

public interface TextInputSession : AutoCloseable {
    public fun update(value: TextEditingValue)

    public fun show()

    public fun hide()
}

public interface TextInputService {
    public fun start(
        configuration: TextInputConfiguration,
        client: TextInputClient,
    ): TextInputSession
}

public data class TextLine(val range: TextRange, val baseline: Float, val bounds: Rect)

public interface TextLayoutResult {
    public val size: Size
    public val lines: List<TextLine>
    public val text: String

    public fun offsetForPoint(point: Point): Int

    public fun caretRect(offset: Int): Rect

    public fun caretRect(offset: Int, affinity: CaretAffinity): Rect = caretRect(offset)

    public fun selectionRects(range: TextRange): List<Rect>

    public fun moveCaret(value: TextEditingValue, forward: Boolean): Pair<Int, CaretAffinity> =
        if (forward) {
            TextBoundaries.nextGrapheme(text, value.selection.end) to CaretAffinity.DOWNSTREAM
        } else {
            TextBoundaries.previousGrapheme(text, value.selection.start) to CaretAffinity.UPSTREAM
        }
}

public data class BasicTextLayoutResult(
    override val size: Size,
    override val lines: List<TextLine>,
    override val text: String,
) : TextLayoutResult {
    override fun offsetForPoint(point: Point): Int =
        ((point.x / 8f).toInt()).coerceIn(0, text.length)

    override fun caretRect(offset: Int): Rect =
        Rect(offset.coerceIn(0, text.length) * 8f, 0f, 1f, 16f)

    override fun selectionRects(range: TextRange): List<Rect> =
        listOf(Rect(range.start * 8f, 0f, (range.end - range.start) * 8f, 16f))
}

public data class TextLayoutRequest(
    val text: String,
    val maxWidth: Float? = null,
    val fontSize: Float = 16f,
)

public interface TextLayoutService {
    public fun layout(request: TextLayoutRequest): TextLayoutResult
}

public object HeadlessTextLayoutService : TextLayoutService {
    override fun layout(request: TextLayoutRequest): TextLayoutResult {
        val width = minOf(request.maxWidth ?: Float.MAX_VALUE, request.text.length * 8f)
        return BasicTextLayoutResult(
            Size(width, 16f),
            listOf(TextLine(TextRange(0, request.text.length), 12f, Rect(0f, 0f, width, 16f))),
            request.text,
        )
    }
}

public data class TextCursorGeometry(
    val caret: Rect,
    val selection: List<Rect>,
    val visible: Boolean,
)

public class TextEditorRuntime(
    public val controller: TextEditingController,
    private val service: TextInputService?,
    private val layoutService: TextLayoutService,
    private val clock: UiAnimationClock,
    public val configuration: TextInputConfiguration = TextInputConfiguration(),
    private val clipboard: ClipboardService? = null,
) : TextInputClient, AutoCloseable {
    private var session: TextInputSession? = null
    private var focused = false
    private var blinkEpoch = 0L
    public var cursorGeometry = TextCursorGeometry(Rect(0f, 0f, 1f, 16f), emptyList(), false)
        private set

    public var scrollX = 0f
        private set

    public var scrollY = 0f
        private set

    private var viewportWidth = Float.POSITIVE_INFINITY
    private var viewportHeight = Float.POSITIVE_INFINITY

    public fun updateViewport(width: Float, height: Float) {
        val nextWidth = width.coerceAtLeast(0f)
        val nextHeight = height.coerceAtLeast(0f)
        if (viewportWidth == nextWidth && viewportHeight == nextHeight) return
        viewportWidth = nextWidth
        viewportHeight = nextHeight
        publishGeometry()
    }

    public fun focus() {
        if (focused) return
        focused = true
        session =
            service?.start(configuration, this)?.also {
                it.show()
                it.update(controller.value)
            }
        blinkEpoch = clock.frameTimeNanos
        clock.animate(::onFrame)
        publishGeometry()
    }

    public fun blur() {
        if (!focused) return
        focused = false
        session?.hide()
        session?.close()
        session = null
        publishGeometry()
    }

    override fun apply(command: TextEditCommand) {
        controller.apply(command)
        session?.update(controller.value)
        blinkEpoch = clock.frameTimeNanos
        publishGeometry()
    }

    public fun deletePreviousGrapheme() {
        controller.deletePreviousGrapheme()
        session?.update(controller.value)
        blinkEpoch = clock.frameTimeNanos
        publishGeometry()
    }

    public fun deleteNextGrapheme() {
        controller.deleteNextGrapheme()
        editingChanged()
    }

    public fun placeCaret(point: Point) {
        val layout = layoutService.layout(TextLayoutRequest(controller.value.text))
        val offset = layout.offsetForPoint(point)
        controller.value =
            controller.value.copy(
                selection = TextRange(offset, offset),
                composition = null,
                affinity = CaretAffinity.DOWNSTREAM,
            )
        editingChanged()
    }

    public fun selectWord(point: Point) {
        val layout = layoutService.layout(TextLayoutRequest(controller.value.text))
        val offset = layout.offsetForPoint(point)
        val start = TextBoundaries.previousWord(controller.value.text, offset)
        val end = TextBoundaries.nextWord(controller.value.text, start)
        controller.value =
            controller.value.copy(
                selection = TextRange(start, end),
                composition = null,
                affinity = CaretAffinity.DOWNSTREAM,
            )
        editingChanged()
    }

    public fun extendSelection(point: Point) {
        val layout = layoutService.layout(TextLayoutRequest(controller.value.text))
        val target = layout.offsetForPoint(point)
        moveSelection(target, extend = true, affinity = CaretAffinity.DOWNSTREAM)
        editingChanged()
    }

    public fun handleKey(
        logicalKey: com.antepod.lumentika.input.LogicalKey,
        text: String?,
        physicalKey: String,
        modifiers: com.antepod.lumentika.input.KeyModifiers,
    ): Boolean {
        val shortcut = modifiers.control || modifiers.meta
        val character = (text ?: physicalKey).lowercase(Locale.ROOT)
        if (shortcut) {
            when (character) {
                "a",
                "keya" -> setSelection(TextRange(0, controller.value.text.length))
                "c",
                "keyc" -> if (!configuration.secure) clipboard?.let(controller::copy)
                "x",
                "keyx" -> if (!configuration.secure) clipboard?.let(controller::cut)
                "v",
                "keyv" -> clipboard?.let(controller::paste)
                else -> return handleNavigation(logicalKey, modifiers, byWord = true)
            }
            editingChanged()
            return true
        }
        if (handleNavigation(logicalKey, modifiers, byWord = false)) return true
        when (logicalKey) {
            com.antepod.lumentika.input.LogicalKey.BACKSPACE -> deletePreviousGrapheme()
            com.antepod.lumentika.input.LogicalKey.DELETE -> deleteNextGrapheme()
            com.antepod.lumentika.input.LogicalKey.CHARACTER -> {
                if (modifiers.alt) return false
                text?.let { apply(TextEditCommand.CommitText(it)) } ?: return false
            }
            com.antepod.lumentika.input.LogicalKey.ENTER -> {
                if (!configuration.multiline) return false
                apply(TextEditCommand.CommitText("\n"))
            }
            else -> return false
        }
        return true
    }

    private fun handleNavigation(
        key: com.antepod.lumentika.input.LogicalKey,
        modifiers: com.antepod.lumentika.input.KeyModifiers,
        byWord: Boolean,
    ): Boolean {
        val current = controller.value
        val layout = layoutService.layout(TextLayoutRequest(current.text))
        val forward = key == com.antepod.lumentika.input.LogicalKey.ARROW_RIGHT
        val backward = key == com.antepod.lumentika.input.LogicalKey.ARROW_LEFT
        val target =
            when {
                forward && byWord -> TextBoundaries.nextWord(current.text, activeOffset(current))
                backward && byWord ->
                    TextBoundaries.previousWord(current.text, activeOffset(current))
                forward || backward -> layout.moveCaret(current, forward).first
                key == com.antepod.lumentika.input.LogicalKey.HOME ->
                    lineFor(layout, activeOffset(current)).range.start
                key == com.antepod.lumentika.input.LogicalKey.END ->
                    lineFor(layout, activeOffset(current)).range.end
                key == com.antepod.lumentika.input.LogicalKey.ARROW_UP ||
                    key == com.antepod.lumentika.input.LogicalKey.ARROW_DOWN -> {
                    val caret = layout.caretRect(activeOffset(current), current.affinity)
                    val lineHeight =
                        lineFor(layout, activeOffset(current)).bounds.height.coerceAtLeast(1f)
                    val direction =
                        if (key == com.antepod.lumentika.input.LogicalKey.ARROW_UP) -1f else 1f
                    layout.offsetForPoint(Point(caret.x, caret.y + direction * lineHeight))
                }
                else -> return false
            }
        moveSelection(
            target,
            modifiers.shift,
            if (forward) CaretAffinity.DOWNSTREAM else CaretAffinity.UPSTREAM,
        )
        editingChanged()
        return true
    }

    private fun activeOffset(value: TextEditingValue): Int =
        if (value.selection.collapsed || value.affinity == CaretAffinity.DOWNSTREAM) {
            value.selection.end
        } else {
            value.selection.start
        }

    private fun moveSelection(target: Int, extend: Boolean, affinity: CaretAffinity) {
        val current = controller.value
        val anchor =
            if (!extend) target
            else if (current.selection.collapsed) activeOffset(current)
            else if (current.affinity == CaretAffinity.DOWNSTREAM) current.selection.start
            else current.selection.end
        controller.value =
            current.copy(
                selection = TextRange(minOf(anchor, target), maxOf(anchor, target)),
                composition = null,
                affinity = if (target < anchor) CaretAffinity.UPSTREAM else affinity,
            )
    }

    private fun setSelection(range: TextRange) {
        controller.value = controller.value.copy(selection = range, composition = null)
    }

    private fun lineFor(layout: TextLayoutResult, offset: Int): TextLine =
        layout.lines.firstOrNull { offset in it.range.start..it.range.end }
            ?: layout.lines.lastOrNull()
            ?: TextLine(TextRange(0, layout.text.length), 0f, Rect(0f, 0f, 0f, 0f))

    private fun editingChanged() {
        session?.update(controller.value)
        blinkEpoch = clock.frameTimeNanos
        publishGeometry()
    }

    public fun receive(content: TransferContent): TransferContent {
        val unconsumed = mutableListOf<com.antepod.lumentika.platform.TransferItem>()
        content.items.forEach { item ->
            if (item.text != null && item.mimeType.startsWith("text/"))
                controller.apply(TextEditCommand.CommitText(item.text))
            else unconsumed += item
        }
        session?.update(controller.value)
        return content.copy(items = unconsumed)
    }

    private fun onFrame(time: Long): Boolean {
        if (!focused) return false
        publishGeometry((time - blinkEpoch) / 500_000_000 % 2L == 0L)
        return true
    }

    private fun publishGeometry(visible: Boolean = focused) {
        val layout = layoutService.layout(TextLayoutRequest(controller.value.text))
        val caret = layout.caretRect(controller.value.selection.end, controller.value.affinity)
        val selections = layout.selectionRects(controller.value.selection)
        if (viewportWidth.isFinite()) {
            scrollX =
                when {
                    caret.x < scrollX -> caret.x
                    caret.right > scrollX + viewportWidth -> caret.right - viewportWidth
                    else -> scrollX
                }.coerceIn(0f, (layout.size.width - viewportWidth).coerceAtLeast(0f))
        }
        if (viewportHeight.isFinite()) {
            scrollY =
                when {
                    caret.y < scrollY -> caret.y
                    caret.bottom > scrollY + viewportHeight -> caret.bottom - viewportHeight
                    else -> scrollY
                }.coerceIn(0f, (layout.size.height - viewportHeight).coerceAtLeast(0f))
        }
        cursorGeometry = TextCursorGeometry(caret, selections, visible)
    }

    override fun close() = blur()
}

public enum class AutofillHint {
    USERNAME,
    PASSWORD,
    NEW_PASSWORD,
    EMAIL,
    PHONE,
    NAME,
    ADDRESS,
    POSTAL_CODE,
    CREDIT_CARD_NUMBER,
    ONE_TIME_CODE,
}

public data class AutofillConfiguration(
    val hints: Set<AutofillHint>,
    val sensitive: Boolean = false,
    val enabled: Boolean = true,
)

@JvmInline public value class AutofillNodeId(val value: Long)

public data class AutofillNode(
    val id: AutofillNodeId,
    val bounds: Rect,
    val configuration: AutofillConfiguration,
    val value: String?,
)

public data class AutofillArtifact(val nodes: List<AutofillNode>)

public data class AutofillChangeSet(
    val changedNodes: Set<AutofillNodeId>,
    val removedNodes: Set<AutofillNodeId>,
)

public interface AutofillService {
    public fun onArtifactCommitted(artifact: AutofillArtifact, changes: AutofillChangeSet)

    public fun requestAutofill(node: AutofillNodeId)
}

public class AutofillRuntime {
    private data class Entry(
        val id: AutofillNodeId,
        val controller: TextEditingController,
        val config: AutofillConfiguration,
        var bounds: () -> Rect,
    )

    private val entries = linkedMapOf<Any, Entry>()
    private var previous = emptyMap<AutofillNodeId, AutofillNode>()
    private var nextId = 1L

    public fun register(
        identity: Any,
        controller: TextEditingController,
        config: AutofillConfiguration,
        bounds: Rect,
    ): AutofillNodeId = register(identity, controller, config) { bounds }

    public fun register(
        identity: Any,
        controller: TextEditingController,
        config: AutofillConfiguration,
        bounds: () -> Rect,
    ): AutofillNodeId =
        entries
            .getOrPut(identity) { Entry(AutofillNodeId(nextId++), controller, config, bounds) }
            .also { it.bounds = bounds }
            .id

    public fun unregister(identity: Any) {
        entries.remove(identity)
    }

    public fun apply(id: AutofillNodeId, text: String): Boolean {
        val entry = entries.values.firstOrNull { it.id == id } ?: return false
        entry.controller.value = TextEditingValue(text, TextRange(text.length, text.length))
        return true
    }

    public fun commit(): Pair<AutofillArtifact, AutofillChangeSet> {
        val nodes =
            entries.values
                .filter { it.config.enabled }
                .map {
                    AutofillNode(
                        it.id,
                        it.bounds(),
                        it.config,
                        if (it.config.sensitive) null else it.controller.value.text,
                    )
                }
        val current = nodes.associateBy { it.id }
        val changes =
            AutofillChangeSet(
                current.keys.filterTo(linkedSetOf()) { previous[it] != current[it] },
                previous.keys - current.keys,
            )
        previous = current
        return AutofillArtifact(nodes) to changes
    }
}
