package com.antepod.lumentika.reactive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReactiveTest {
    @Test
    fun `derived tracks dynamic dependencies and suppresses equal outputs`() {
        val chooseFirst = state(true)
        val first = state(2)
        val second = state(4)
        var calculations = 0
        val selected = derived {
            calculations++
            if (chooseFirst.value) first.value else second.value
        }
        val observed = mutableListOf<Int>()
        effect { observed += selected.value }

        assertEquals(listOf(2), observed)
        second.value = 8
        assertEquals(listOf(2), observed)
        chooseFirst.value = false
        assertEquals(listOf(2, 8), observed)
        first.value = 9
        assertEquals(listOf(2, 8), observed)
        second.value = 8
        assertTrue(calculations >= 2)
    }

    @Test
    fun `batch coalesces effects and untracked excludes dependencies`() {
        val first = state(1)
        val ignored = state(1)
        var runs = 0
        var sum = 0
        effect {
            runs++
            sum = first.value + untracked { ignored.value }
        }

        batch {
            first.value = 2
            first.value = 3
            ignored.value = 4
        }
        assertEquals(2, runs)
        assertEquals(7, sum)
        ignored.value = 5
        assertEquals(2, runs)
    }

    @Test
    fun `effect cleanup and scope disposal are deterministic`() {
        val source = state(0)
        val trace = mutableListOf<String>()
        val scope = ComponentScope(Dispatchers.Unconfined)
        withComponentScope(scope) {
            effect {
                trace += "run:${source.value}"
                onCleanup { trace += "clean" }
            }
        }
        source.value = 1
        scope.close()
        scope.close()
        assertEquals(listOf("run:0", "clean", "run:1", "clean"), trace)
    }

    @Test
    fun `derived cycle reports diagnostic`() {
        lateinit var cyclic: Derived<Int>
        cyclic = derived { cyclic.value + 1 }
        val error = assertFailsWith<IllegalStateException> { cyclic.value }
        assertTrue(error.message.orEmpty().contains("cycle"))
    }

    @Test
    fun `async derived cancels stale generations`() = runBlocking {
        val source = state(1)
        val scope = ComponentScope(Dispatchers.Unconfined)
        val async =
            withComponentScope(scope) {
                derivedAsync {
                    val captured = source.value
                    delay(if (captured == 1) 30 else 1)
                    captured * 10
                }
            }
        source.value = 2
        delay(40)
        assertTrue(async.hasValue)
        assertFalse(async.pending)
        assertEquals(20, async.value)
        scope.close()
    }
}
