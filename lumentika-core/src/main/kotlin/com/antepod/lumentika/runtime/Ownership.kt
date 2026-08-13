package com.antepod.lumentika.runtime

import java.util.concurrent.atomic.AtomicLong

/** Counts live runtime objects for leak detection and adapter tests. */
public data class OwnershipSnapshot(val elements: Long, val componentScopes: Long)

/** Exposes process-wide ownership counters intended for diagnostics and tests. */
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
