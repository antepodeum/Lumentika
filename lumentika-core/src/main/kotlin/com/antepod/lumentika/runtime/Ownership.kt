package com.antepod.lumentika.runtime

import java.util.concurrent.atomic.AtomicLong

public data class OwnershipSnapshot(val elements: Long, val componentScopes: Long)

public object OwnershipCounters {
    private val elements = AtomicLong()
    private val componentScopes = AtomicLong()

    public fun snapshot(): OwnershipSnapshot =
        OwnershipSnapshot(elements.get(), componentScopes.get())

    internal fun mountElement() {
        elements.incrementAndGet()
    }

    internal fun unmountElement() {
        elements.decrementAndGet()
    }

    internal fun createScope() {
        componentScopes.incrementAndGet()
    }

    internal fun disposeScope() {
        componentScopes.decrementAndGet()
    }
}
