package com.antepod.lumentika.semantics
import com.antepod.lumentika.geometry.*
import com.antepod.lumentika.render.*
import com.antepod.lumentika.runtime.Element
import kotlin.test.*
class SemanticsTest{@Test fun `semantic action focus and geometry use committed transform chain`(){val root=Element("root").apply{geometry=Rect(0f,0f,10f,10f)};var clicked=false;root.attach(SemanticsAttachment,SemanticsConfiguration(role=SemanticRole.BUTTON,actions=mapOf(SemanticAction.CLICK to {clicked=true;true})));val entry=HitTestEntry(root,Rect(0f,0f,10f,10f),Matrix3.translation(20f,30f),Rect(0f,0f,100f,100f),0,false);val runtime=SemanticsRuntime(root);runtime.commit(HitTestArtifact(1,listOf(entry)));val node=runtime.artifact.nodes.values.single();assertEquals(Rect(20f,30f,10f,10f),node.bounds);assertTrue(runtime.perform(node.id,SemanticAction.CLICK));assertTrue(clicked);assertTrue(runtime.requestAccessibilityFocus(node.id));assertEquals(node.id,runtime.accessibilityFocus)}}
