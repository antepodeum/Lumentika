package com.antepod.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactiveProofTest {
    @Test
    fun `generated DSL proves derived prop binding event slot and cleanup end to end`() {
        val proof = runReactiveProof()

        assertTrue("Generated value: 7" in proof.initialTexts)
        assertTrue("Generated slot" in proof.initialTexts)
        assertTrue("Derived value: 14" in proof.initialTexts)
        assertTrue("Generated value: 9" in proof.externalUpdateTexts)
        assertTrue("Derived value: 18" in proof.externalUpdateTexts)
        assertTrue("Generated value: 11" in proof.bindingWriteTexts)
        assertTrue("Derived value: 22" in proof.bindingWriteTexts)
        assertEquals(false, proof.finalBindingValue)
        assertEquals(false, proof.deliveredEvent)
        assertEquals(3, proof.derivedExecutions)
        assertEquals(1, proof.viewExecutions)
        assertTrue(proof.componentDisposed)
    }
}
