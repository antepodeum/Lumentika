package com.antepod.lumentika.text

import com.antepod.lumentika.animation.UiAnimationClock
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.platform.TransferItem
import com.antepod.lumentika.platform.TransferSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextEditingTest {
    @Test fun `composition batch reconciliation and session lifecycle trace`() {
        val trace=mutableListOf<String>()
        val service=object:TextInputService{override fun start(configuration:TextInputConfiguration,client:TextInputClient)=object:TextInputSession{override fun update(value:TextEditingValue){trace+="update:${value.text}:${value.composition}"};override fun show(){trace+="show"};override fun hide(){trace+="hide"};override fun close(){trace+="close"}}}
        val controller=TextEditingController();val clock=UiAnimationClock();val editor=TextEditorRuntime(controller,service,HeadlessTextLayoutService,clock)
        editor.focus();editor.apply(TextEditCommand.SetComposingText("abc"));editor.apply(TextEditCommand.FinishComposition);controller.applyBatch(listOf(TextEditCommand.SetSelection(TextRange(0,3)),TextEditCommand.CommitText("xyz")));controller.reconcileExternal("external");clock.frame(500_000_000);editor.blur()
        assertEquals("external",controller.value.text);assertNull(controller.value.composition);assertTrue("show" in trace);assertEquals(listOf("hide","close"),trace.takeLast(2));assertFalse(editor.cursorGeometry.visible)
    }

    @Test fun `content returns unsupported items and autofill redacts sensitive values`() {
        val controller=TextEditingController();val editor=TextEditorRuntime(controller,null,HeadlessTextLayoutService,UiAnimationClock())
        val binary=TransferItem("image/png",bytes=byteArrayOf(1));val remaining=editor.receive(TransferContent(listOf(TransferItem("text/plain",text="hello"),binary),TransferSource.DRAG_DROP))
        assertEquals("hello",controller.value.text);assertEquals(listOf(binary),remaining.items)
        val autofill=AutofillRuntime();val id=autofill.register(Any(),controller,AutofillConfiguration(setOf(AutofillHint.PASSWORD),sensitive=true),Rect(1f,2f,3f,4f));val (artifact,first)=autofill.commit()
        assertNull(artifact.nodes.single().value);assertTrue(id in first.changedNodes);assertTrue(autofill.apply(id,"secret"));assertEquals("secret",controller.value.text)
        val stable=artifact.nodes.single().id;assertEquals(stable,autofill.commit().first.nodes.single().id)
    }
}
