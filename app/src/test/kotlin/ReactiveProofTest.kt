package com.antepod.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactiveProofTest {
    @Test
    fun `generated DSL proves prop binding event slot and cleanup end to end`() {
        val proof = runReactiveProof()

        assertTrue("Generated value: 7" in proof.initialTexts)
        assertTrue("Generated slot" in proof.initialTexts)
        assertTrue("Generated value: 9" in proof.externalUpdateTexts)
        assertTrue("Generated value: 11" in proof.bindingWriteTexts)
        assertEquals(false, proof.finalBindingValue)
        assertEquals(false, proof.deliveredEvent)
        assertEquals(1, proof.viewExecutions)
        assertTrue(proof.componentDisposed)
    }
}
