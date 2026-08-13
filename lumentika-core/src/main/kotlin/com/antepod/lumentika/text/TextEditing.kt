package com.antepod.lumentika.text

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.ClipboardService
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
