package com.antepod.lumentika.text

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.ClipboardService
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.state
import java.text.BreakIterator
import java.util.Locale

public data class TextRange(val start: Int, val end: Int) { init { require(start >= 0 && end >= start) }; val collapsed get() = start == end }
public enum class CaretAffinity { UPSTREAM, DOWNSTREAM }
public data class TextEditingValue(val text: String = "", val selection: TextRange = TextRange(0,0), val composition: TextRange? = null, val affinity: CaretAffinity = CaretAffinity.DOWNSTREAM) { init { require(selection.end <= text.length); require(composition == null || composition.end <= text.length) } }
public sealed interface TextEditCommand {
    public data class CommitText(val text: String) : TextEditCommand
    public data class SetComposingText(val text: String) : TextEditCommand
    public data class SetComposingRegion(val range: TextRange) : TextEditCommand
    public data object FinishComposition : TextEditCommand
    public data class SetSelection(val range: TextRange) : TextEditCommand
    public data class DeleteSurroundingText(val before: Int, val after: Int) : TextEditCommand
}
public class TextEditingController(initial: TextEditingValue = TextEditingValue()) : Mutable<TextEditingValue> {
    private val state: State<TextEditingValue> = state(initial)
    override var value: TextEditingValue get() = state.value; set(value) { state.value = value }
    public fun apply(command: TextEditCommand) { value = when(command) {
        is TextEditCommand.CommitText -> replace(command.text, composing = false)
        is TextEditCommand.SetComposingText -> replace(command.text, composing = true)
        is TextEditCommand.SetComposingRegion -> value.copy(composition = command.range)
        TextEditCommand.FinishComposition -> value.copy(composition = null)
        is TextEditCommand.SetSelection -> value.copy(selection = command.range)
        is TextEditCommand.DeleteSurroundingText -> { val s=(value.selection.start-command.before).coerceAtLeast(0); val e=(value.selection.end+command.after).coerceAtMost(value.text.length); value.copy(text=value.text.removeRange(s,e),selection=TextRange(s,s),composition=null) }
    } }
    public fun applyBatch(commands: List<TextEditCommand>) {
        val before = value
        try { commands.forEach(::apply) } catch (failure: Throwable) { value = before; throw failure }
    }
    public fun reconcileExternal(text: String) {
        if (text == value.text) return
        val cursor = value.selection.end.coerceAtMost(text.length)
        value = TextEditingValue(text, TextRange(cursor, cursor), composition = null)
    }
    public fun deletePreviousGrapheme() { val cursor=value.selection.start; if(cursor==0)return; val it=BreakIterator.getCharacterInstance(Locale.ROOT); it.setText(value.text); val start=it.preceding(cursor); value=value.copy(text=value.text.removeRange(start,cursor),selection=TextRange(start,start),composition=null) }
    public fun copy(clipboard: ClipboardService) { clipboard.writeText(value.text.substring(value.selection.start,value.selection.end)) }
    public fun cut(clipboard: ClipboardService) { copy(clipboard); apply(TextEditCommand.CommitText("")) }
    public fun paste(clipboard: ClipboardService) { clipboard.readText()?.let { apply(TextEditCommand.CommitText(it)) } }
    private fun replace(text:String, composing:Boolean): TextEditingValue { val range=value.composition ?: value.selection; val next=value.text.replaceRange(range.start,range.end,text); val end=range.start+text.length; return TextEditingValue(next,TextRange(end,end),if(composing) TextRange(range.start,end) else null) }
}
public data class TextInputConfiguration(val multiline:Boolean=false,val secure:Boolean=false,val autofillHints:Set<String> = emptySet())
public interface TextInputClient { public fun apply(command: TextEditCommand) }
public interface TextInputSession:AutoCloseable { public fun update(value:TextEditingValue); public fun show(); public fun hide() }
public interface TextInputService { public fun start(configuration:TextInputConfiguration,client:TextInputClient):TextInputSession }
public data class TextLine(val range:TextRange,val baseline:Float,val bounds:Rect)
public data class TextLayoutResult(val size:Size,val lines:List<TextLine>,val text:String) { public fun offsetForPoint(point:Point):Int = ((point.x/8f).toInt()).coerceIn(0,text.length); public fun caretRect(offset:Int)=Rect(offset.coerceIn(0,text.length)*8f,0f,1f,16f); public fun selectionRects(range:TextRange)=listOf(Rect(range.start*8f,0f,(range.end-range.start)*8f,16f)) }
public data class TextLayoutRequest(val text:String,val maxWidth:Float?=null,val fontSize:Float=16f)
public interface TextLayoutService { public fun layout(request:TextLayoutRequest):TextLayoutResult }
public object HeadlessTextLayoutService:TextLayoutService { override fun layout(request:TextLayoutRequest):TextLayoutResult { val width=minOf(request.maxWidth ?: Float.MAX_VALUE,request.text.length*8f); return TextLayoutResult(Size(width,16f),listOf(TextLine(TextRange(0,request.text.length),12f,Rect(0f,0f,width,16f))),request.text) } }

public data class TextCursorGeometry(val caret: Rect, val selection: List<Rect>, val visible: Boolean)
public class TextEditorRuntime(
    public val controller: TextEditingController,
    private val service: TextInputService?,
    private val layoutService: TextLayoutService,
    private val clock: UiAnimationClock,
    public val configuration: TextInputConfiguration = TextInputConfiguration(),
) : TextInputClient, AutoCloseable {
    private var session: TextInputSession? = null
    private var focused = false
    private var blinkEpoch = 0L
    public var cursorGeometry = TextCursorGeometry(Rect(0f,0f,1f,16f), emptyList(), false); private set
    public var scrollX = 0f; private set
    public fun focus() {
        if (focused) return
        focused = true
        session = service?.start(configuration, this)?.also { it.show(); it.update(controller.value) }
        blinkEpoch = clock.frameTimeNanos
        clock.animate(::onFrame)
        publishGeometry()
    }
    public fun blur() { if (!focused) return; focused = false; session?.hide(); session?.close(); session = null; publishGeometry() }
    override fun apply(command: TextEditCommand) { controller.apply(command); session?.update(controller.value); blinkEpoch = clock.frameTimeNanos; publishGeometry() }
    public fun receive(content: TransferContent): TransferContent {
        val unconsumed = mutableListOf<com.antepod.lumentika.platform.TransferItem>()
        content.items.forEach { item -> if (item.text != null && item.mimeType.startsWith("text/")) controller.apply(TextEditCommand.CommitText(item.text)) else unconsumed += item }
        session?.update(controller.value)
        return content.copy(items = unconsumed)
    }
    private fun onFrame(time: Long): Boolean { if (!focused) return false; publishGeometry((time-blinkEpoch)/500_000_000 % 2L == 0L); return true }
    private fun publishGeometry(visible: Boolean = focused) { val layout=layoutService.layout(TextLayoutRequest(controller.value.text));val caret=layout.caretRect(controller.value.selection.end);val selections=layout.selectionRects(controller.value.selection);scrollX=maxOf(0f,caret.right-(layout.size.width.coerceAtLeast(1f)));cursorGeometry=TextCursorGeometry(caret,selections,visible) }
    override fun close() = blur()
}

public enum class AutofillHint { USERNAME, PASSWORD, NEW_PASSWORD, EMAIL, PHONE, NAME, ADDRESS, POSTAL_CODE, CREDIT_CARD_NUMBER, ONE_TIME_CODE }
public data class AutofillConfiguration(val hints: Set<AutofillHint>, val sensitive: Boolean = false, val enabled: Boolean = true)
@JvmInline public value class AutofillNodeId(val value: Long)
public data class AutofillNode(val id: AutofillNodeId, val bounds: Rect, val configuration: AutofillConfiguration, val value: String?)
public data class AutofillArtifact(val nodes: List<AutofillNode>)
public data class AutofillChangeSet(val changedNodes: Set<AutofillNodeId>, val removedNodes: Set<AutofillNodeId>)
public interface AutofillService { public fun onArtifactCommitted(artifact: AutofillArtifact, changes: AutofillChangeSet); public fun requestAutofill(node: AutofillNodeId) }
public class AutofillRuntime {
    private data class Entry(val id: AutofillNodeId, val controller: TextEditingController, val config: AutofillConfiguration, var bounds: Rect)
    private val entries = linkedMapOf<Any, Entry>(); private var previous = emptyMap<AutofillNodeId, AutofillNode>(); private var nextId = 1L
    public fun register(identity: Any, controller: TextEditingController, config: AutofillConfiguration, bounds: Rect): AutofillNodeId = entries.getOrPut(identity) { Entry(AutofillNodeId(nextId++), controller, config, bounds) }.also { it.bounds = bounds }.id
    public fun unregister(identity: Any) { entries.remove(identity) }
    public fun apply(id: AutofillNodeId, text: String): Boolean { val entry=entries.values.firstOrNull{it.id==id}?:return false;entry.controller.reconcileExternal(text);return true }
    public fun commit(): Pair<AutofillArtifact, AutofillChangeSet> { val nodes=entries.values.filter{it.config.enabled}.map{AutofillNode(it.id,it.bounds,it.config,if(it.config.sensitive)null else it.controller.value.text)};val current=nodes.associateBy{it.id};val changes=AutofillChangeSet(current.keys.filterTo(linkedSetOf()){previous[it]!=current[it]},previous.keys-current.keys);previous=current;return AutofillArtifact(nodes) to changes }
}
