package com.antepod.lumentika.reactive

import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

public interface Readable<out T> {
    public val value: T
}

public interface Mutable<T> : Readable<T> {
    override var value: T

    public fun update(update: (T) -> T) {
        value = update(value)
    }
}

public interface State<T> : Mutable<T>

public interface Derived<out T> : Readable<T>

public interface AsyncDerived<out T> : Readable<T> {
    public val pending: Boolean
    public val hasValue: Boolean
    public val error: Throwable?
}

private interface Observer {
    fun invalidate()
}

private interface Source {
    fun addObserver(observer: Observer)

    fun removeObserver(observer: Observer)
}

private abstract class SourceNode : Source {
    private val observers = LinkedHashSet<Observer>()

    final override fun addObserver(observer: Observer) {
        observers += observer
    }

    final override fun removeObserver(observer: Observer) {
        observers -= observer
    }

    protected fun publishInvalidation() {
        observers.toList().forEach(Observer::invalidate)
    }
}

private interface TrackedComputation : Observer {
    fun track(source: Source)
}

private object ReactiveRuntime {
    val tracker = ThreadLocal<TrackedComputation?>()
    val cleanupRegistrar = ThreadLocal<((() -> Unit) -> Unit)?>()
    private val pendingDerived = LinkedHashSet<() -> Unit>()
    private val pendingEffects = LinkedHashSet<() -> Unit>()
    private var batchDepth = 0
    private var flushing = false

    fun track(source: Source) {
        tracker.get()?.track(source)
    }

    fun enqueueDerived(task: () -> Unit) {
        pendingDerived += task
        requestFlush()
    }

    fun enqueueEffect(task: () -> Unit) {
        pendingEffects += task
        requestFlush()
    }

    private fun requestFlush() {
        if (batchDepth == 0 && !flushing) flush()
    }

    fun flush() {
        if (flushing || batchDepth > 0) return
        flushing = true
        try {
            var waves = 0
            while (pendingDerived.isNotEmpty() || pendingEffects.isNotEmpty()) {
                check(++waves <= 10_000) { "Reactive scheduler did not stabilize; probable cycle" }
                val derived = pendingDerived.toList()
                pendingDerived.clear()
                derived.forEach { it() }
                if (pendingDerived.isEmpty()) {
                    val effects = pendingEffects.toList()
                    pendingEffects.clear()
                    effects.forEach { it() }
                }
            }
        } finally {
            flushing = false
        }
    }

    fun <T> batch(block: () -> T): T {
        batchDepth++
        try {
            return block()
        } finally {
            batchDepth--
            if (batchDepth == 0) flush()
        }
    }

    fun <T> untracked(block: () -> T): T {
        val previous = tracker.get()
        tracker.set(null)
        return try {
            block()
        } finally {
            tracker.set(previous)
        }
    }
}

private class StateImpl<T>(
    initial: T,
    private val equal: (T, T) -> Boolean,
) : SourceNode(), State<T> {
    private var current = initial

    override var value: T
        get() {
            ReactiveRuntime.track(this)
            return current
        }
        set(value) {
            if (equal(current, value)) return
            current = value
            publishInvalidation()
        }
}

private abstract class DependencyObserver : TrackedComputation {
    private var dependencies = LinkedHashSet<Source>()
    private var collecting: LinkedHashSet<Source>? = null

    final override fun track(source: Source) {
        collecting?.add(source)
    }

    protected fun <T> collect(block: () -> T): T {
        val next = LinkedHashSet<Source>()
        collecting = next
        val previous = ReactiveRuntime.tracker.get()
        ReactiveRuntime.tracker.set(this)
        try {
            return block()
        } finally {
            ReactiveRuntime.tracker.set(previous)
            collecting = null
            (dependencies - next).forEach { it.removeObserver(this) }
            (next - dependencies).forEach { it.addObserver(this) }
            dependencies = next
        }
    }

    protected fun clearDependencies() {
        dependencies.forEach { it.removeObserver(this) }
        dependencies.clear()
    }
}

private class DerivedImpl<T>(
    private val calculate: () -> T,
    private val equal: (T, T) -> Boolean,
) : SourceNode(), Derived<T>, DependencyObserverBridge {
    private val observer =
        object : DependencyObserver() {
            override fun invalidate() = invalidateDerived()

            fun evaluate(): T = collect(calculate)

            fun dispose() = clearDependencies()
        }
    private var initialized = false
    private var dirty = true
    private var computing = false
    private var current: Any? = null
    private val recomputeTask: () -> Unit = { recompute() }

    override val value: T
        get() {
            ReactiveRuntime.track(this)
            if (dirty) recompute()
            @Suppress("UNCHECKED_CAST")
            return current as T
        }

    private fun invalidateDerived() {
        if (dirty) return
        dirty = true
        ReactiveRuntime.enqueueDerived(recomputeTask)
    }

    private fun recompute() {
        if (!dirty) return
        check(!computing) { "Reactive cycle detected while evaluating derived value" }
        computing = true
        try {
            val next = observer.evaluate()
            @Suppress("UNCHECKED_CAST") val changed = !initialized || !equal(current as T, next)
            current = next
            initialized = true
            dirty = false
            if (changed) publishInvalidation()
        } finally {
            computing = false
        }
    }

    override fun disposeDependencies() = observer.dispose()
}

private interface DependencyObserverBridge {
    fun disposeDependencies()
}

public class ComponentScope(dispatcher: CoroutineDispatcher = Dispatchers.Default) : AutoCloseable {
    private val cleanups = ArrayDeque<() -> Unit>()
    internal val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    public var isDisposed: Boolean = false
        private set

    internal fun own(cleanup: () -> Unit) {
        if (isDisposed) cleanup() else cleanups.addFirst(cleanup)
    }

    override fun close() {
        if (isDisposed) return
        isDisposed = true
        coroutineScope.cancel()
        var failure: Throwable? = null
        while (cleanups.isNotEmpty()) {
            try {
                cleanups.removeFirst()()
            } catch (error: Throwable) {
                failure = failure ?: error
            }
        }
        failure?.let { throw it }
    }
}

private val currentScope = ThreadLocal<ComponentScope?>()

public fun <T> withComponentScope(scope: ComponentScope, block: () -> T): T {
    check(!scope.isDisposed) { "ComponentScope is disposed" }
    val previous = currentScope.get()
    currentScope.set(scope)
    return try {
        block()
    } finally {
        currentScope.set(previous)
    }
}

public fun currentComponentScope(): ComponentScope? = currentScope.get()

public fun onCleanup(cleanup: () -> Unit) {
    val registrar = ReactiveRuntime.cleanupRegistrar.get()
    if (registrar != null) registrar(cleanup)
    else
        requireNotNull(currentScope.get()) { "onCleanup requires an active ComponentScope" }
            .own(cleanup)
}

private class Effect(
    private val block: () -> Unit,
    scope: ComponentScope?,
) : DependencyObserver(), AutoCloseable {
    private var cleanup: (() -> Unit)? = null
    private var scheduled = false
    private var disposed = false
    private val runTask: () -> Unit = { run() }

    init {
        scope?.own(::close)
        invalidate()
    }

    override fun invalidate() {
        if (disposed || scheduled) return
        scheduled = true
        ReactiveRuntime.enqueueEffect(runTask)
    }

    private fun run() {
        if (disposed) return
        scheduled = false
        cleanup?.invoke()
        cleanup = null
        val callbacks = ArrayDeque<() -> Unit>()
        val previous = ReactiveRuntime.cleanupRegistrar.get()
        ReactiveRuntime.cleanupRegistrar.set { callbacks.addFirst(it) }
        try {
            collect(block)
            cleanup = { callbacks.forEach { it() } }
        } finally {
            ReactiveRuntime.cleanupRegistrar.set(previous)
        }
    }

    override fun close() {
        if (disposed) return
        disposed = true
        clearDependencies()
        cleanup?.invoke()
        cleanup = null
    }
}

private class AsyncDerivedImpl<T>(
    private val calculate: suspend () -> T,
    private val scope: ComponentScope,
) : SourceNode(), AsyncDerived<T>, TrackedComputation, AutoCloseable {
    private val dependencies = LinkedHashSet<Source>()
    private val nextDependencies = LinkedHashSet<Source>()
    private val generation = AtomicLong()
    private var job: Job? = null
    private var stored: Any? = null
    @Volatile private var disposed = false
    @Volatile
    override var pending: Boolean = true
        private set

    @Volatile
    override var hasValue: Boolean = false
        private set

    @Volatile
    override var error: Throwable? = null
        private set

    init {
        scope.own(::close)
        start()
    }

    override val value: T
        get() {
            ReactiveRuntime.track(this)
            check(hasValue) { "AsyncDerived has no value yet" }
            @Suppress("UNCHECKED_CAST")
            return stored as T
        }

    override fun track(source: Source) {
        synchronized(this) {
            if (source !in dependencies && source !in nextDependencies) {
                source.addObserver(this)
            }
            nextDependencies += source
        }
    }

    override fun invalidate() = start()

    private fun start() {
        if (disposed) return
        val activeGeneration = generation.incrementAndGet()
        job?.cancel()
        pending = true
        error = null
        publishInvalidation()
        synchronized(this) {
            (nextDependencies - dependencies).forEach { it.removeObserver(this) }
            nextDependencies.clear()
        }
        job =
            scope.coroutineScope.launch(ReactiveRuntime.tracker.asContextElement(this)) {
                try {
                    val result = calculate()
                    if (generation.get() != activeGeneration || disposed) return@launch
                    synchronized(this@AsyncDerivedImpl) {
                        (dependencies - nextDependencies).forEach {
                            it.removeObserver(this@AsyncDerivedImpl)
                        }
                        dependencies.clear()
                        dependencies += nextDependencies
                    }
                    stored = result
                    hasValue = true
                    pending = false
                    error = null
                    publishInvalidation()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    if (generation.get() != activeGeneration || disposed) return@launch
                    pending = false
                    error = failure
                    publishInvalidation()
                }
            }
    }

    override fun close() {
        if (disposed) return
        disposed = true
        generation.incrementAndGet()
        job?.cancel()
        synchronized(this) {
            dependencies.forEach { it.removeObserver(this) }
            dependencies.clear()
            nextDependencies.clear()
        }
    }
}

public fun <T> state(
    initial: T,
    equal: (T, T) -> Boolean = { first, second -> first == second },
): State<T> = StateImpl(initial, equal)

public fun <T> derived(
    equal: (T, T) -> Boolean = { first, second -> first == second },
    block: () -> T,
): Derived<T> {
    val result = DerivedImpl(block, equal)
    currentScope.get()?.own(result::disposeDependencies)
    return result
}

public fun effect(block: () -> Unit): AutoCloseable = Effect(block, currentScope.get())

public fun <T> derivedAsync(block: suspend () -> T): AsyncDerived<T> {
    val scope =
        requireNotNull(currentScope.get()) { "derivedAsync requires an active ComponentScope" }
    return AsyncDerivedImpl(block, scope)
}

public fun <T> batch(block: () -> T): T = ReactiveRuntime.batch(block)

public fun <T> untracked(block: () -> T): T = ReactiveRuntime.untracked(block)

public fun flushReactiveWork(): Unit = ReactiveRuntime.flush()
