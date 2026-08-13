package com.antepod.lumentika.component

import com.antepod.lumentika.reactive.ComponentScope
import com.antepod.lumentika.reactive.Mutable
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.reactive.State
import com.antepod.lumentika.reactive.derived
import com.antepod.lumentika.reactive.effect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.reactive.withComponentScope
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.Fragment
import com.antepod.lumentika.runtime.UiScope

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class UIComponent

private object Missing

public sealed class Declaration<T>(initial: Any?) {
    private val local: State<T>
    private var sourceHandle: AutoCloseable? = null
    private var configured = initial !== Missing
    internal val required: Boolean = initial === Missing

    init {
        @Suppress("UNCHECKED_CAST")
        local = state(if (initial === Missing) null as T else initial as T)
    }

    public open val value: T get() = local.value
    internal fun ensureConfigured(name: String) { require(configured) { "Required declaration '$name' was not configured" } }

    public fun set(value: T) {
        sourceHandle?.close()
        sourceHandle = null
        configured = true
        local.value = value
    }

    public fun source(source: Readable<T>, scope: ComponentScope) {
        sourceHandle?.close()
        configured = true
        sourceHandle = withComponentScope(scope) { effect { local.value = source.value } }
    }

    public fun source(scope: ComponentScope, block: () -> T) = source(withComponentScope(scope) { derived(block = block) }, scope)

    internal fun dispose() { sourceHandle?.close() }
    protected fun write(value: T) { local.value = value }
}

public class Prop<T> internal constructor(initial: Any?) : Declaration<T>(initial), Readable<T>

public class Binding<T> internal constructor(initial: Any?) : Declaration<T>(initial), Mutable<T> {
    private var bound: Mutable<T>? = null
    private var syncing = false

    override var value: T
        get() = super.value
        set(value) {
            write(value)
            if (!syncing) bound?.value = value
        }

    public fun bind(source: Mutable<T>, scope: ComponentScope) {
        require(bound == null) { "Binding already has a two-way source" }
        bound = source
        set(source.value)
        source(source, scope)
        withComponentScope(scope) {
            effect {
                val next = source.value
                if (next != value) {
                    syncing = true
                    try { write(next) } finally { syncing = false }
                }
            }
        }
    }
}

public class Event<E> internal constructor() {
    private val listeners = LinkedHashSet<(E) -> Unit>()
    public var bubbles: Boolean = false
        private set
    public fun bubbles(): Event<E> = apply { bubbles = true }
    public fun listen(listener: (E) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
    public fun emit(event: E) { listeners.toList().forEach { it(event) } }
}

public open class Slot internal constructor() {
    internal var content: (UiScope.() -> Unit)? = null
    public fun configure(content: UiScope.() -> Unit) { this.content = content }
    public fun mount(scope: UiScope): Fragment = scope.fragment { content?.invoke(this) }
}

public class SlotList internal constructor() : Slot()

public abstract class Component : AutoCloseable {
    private val declarations = mutableListOf<Pair<String, Declaration<*>>>()
    public val componentScope: ComponentScope = ComponentScope()
    private var mounted: Element? = null
    protected lateinit var ui: UiScope
        private set
    public var viewExecutions: Int = 0
        private set

    protected fun <T> prop(default: T): Prop<T> = register(Prop(default))
    protected fun <T> requiredProp(): Prop<T> = register(Prop(Missing))
    protected fun <T> binding(default: T): Binding<T> = register(Binding(default))
    protected fun <T> requiredBinding(): Binding<T> = register(Binding(Missing))
    protected fun <E> event(): Event<E> = Event()
    protected fun slot(): Slot = Slot()
    protected fun slotList(): SlotList = SlotList()
    protected fun <T : Declaration<*>> register(declaration: T): T {
        declarations += "declaration${declarations.size}" to declaration
        return declaration
    }

    public fun mount(scope: UiScope): Element {
        check(mounted == null) { "Component already mounted" }
        declarations.forEach { (name, declaration) -> declaration.ensureConfigured(name) }
        ui = scope
        val result = withComponentScope(componentScope) {
            viewExecutions++
            view()
        }
        mounted = result
        return result
    }

    protected abstract fun view(): Element

    override fun close() {
        declarations.forEach { it.second.dispose() }
        mounted?.parent?.remove(mounted!!)
        mounted = null
        componentScope.close()
    }
}

public fun <T> prop(default: T): Prop<T> = Prop(default)
public fun <T> prop(): Prop<T> = Prop(Missing)
public fun <T> binding(default: T): Binding<T> = Binding(default)
public fun <T> binding(): Binding<T> = Binding(Missing)
public fun <E> event(): Event<E> = Event()
public fun slot(): Slot = Slot()
public fun slotList(): SlotList = SlotList()

public class ContextKey<T> internal constructor()
private val contextAttachment = com.antepod.lumentika.runtime.AttachmentKey<MutableMap<ContextKey<*>, Any?>>()
public fun <T> contextKey(): ContextKey<T> = ContextKey()

public fun <T> UiScope.provide(key: ContextKey<T>, value: T, content: UiScope.() -> Unit): Element {
    val provider = element("context-provider")
    provider.attach(contextAttachment, mutableMapOf(key to value))
    UiScope(provider).content()
    return provider
}

public fun <T> Element.context(key: ContextKey<T>): T {
    var cursor: Element? = this
    while (cursor != null) {
        val values = cursor.attachment(contextAttachment)
        if (values != null && key in values) {
            @Suppress("UNCHECKED_CAST")
            return values[key] as T
        }
        cursor = cursor.parent
    }
    error("No value provided for ContextKey")
}

public fun UiScope.show(condition: Readable<Boolean>, content: UiScope.() -> Unit): Element {
    val anchor = element("show")
    withComponentScope(anchor.scope) {
        effect {
            if (condition.value && anchor.children.isEmpty()) UiScope(anchor).content()
            else if (!condition.value) anchor.children.toList().forEach { anchor.remove(it) }
        }
    }
    return anchor
}

public fun <T, K> UiScope.forEach(items: Readable<List<T>>, key: (T) -> K, content: UiScope.(T) -> Unit): Element {
    val anchor = element("for-each")
    val keyed = LinkedHashMap<K, Element>()
    withComponentScope(anchor.scope) {
        effect {
            val nextItems = items.value
            val keys = nextItems.map(key)
            require(keys.size == keys.toSet().size) { "Duplicate key in forEach: $keys" }
            val next = LinkedHashMap<K, Element>()
            nextItems.forEachIndexed { index, item ->
                val itemKey = key(item)
                val child = keyed.remove(itemKey) ?: Element("keyed-item").also {
                    anchor.append(it)
                    UiScope(it).content(item)
                }
                next[itemKey] = child
                anchor.move(child, index)
            }
            keyed.values.forEach { anchor.remove(it) }
            keyed.clear()
            keyed.putAll(next)
        }
    }
    return anchor
}
