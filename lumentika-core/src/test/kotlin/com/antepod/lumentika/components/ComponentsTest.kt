package com.antepod.lumentika.components
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.runtime.*
import com.antepod.lumentika.semantics.*
import kotlin.test.*
class ComponentsTest{@Test fun `controls attach default semantics and actions`(){val root=Element("root");val ui=UiScope(root);var clicks=0;val button=ui.button("Go"){clicks++};val checked=state(false);val checkbox=ui.checkbox(checked);val sliderValue=state(0f);val slider=ui.slider(sliderValue);val field=ui.textField();assertEquals(listOf(SemanticRole.BUTTON,SemanticRole.CHECKBOX,SemanticRole.SLIDER,SemanticRole.TEXT_FIELD),listOf(button,checkbox,slider,field).map{it.element.attachment(SemanticsAttachment)!!.role});button.activate();checkbox.activate();slider.semantics.actions.getValue(SemanticAction.SET_VALUE)(.75f);assertEquals(1,clicks);assertTrue(checked.value);assertEquals(.75f,sliderValue.value)}}
