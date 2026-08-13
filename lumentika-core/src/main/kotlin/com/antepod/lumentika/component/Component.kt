package com.antepod.lumentika.component

import com.antepod.lumentika.animation.ElementAnimationRuntime
import com.antepod.lumentika.animation.ElementTransition
import com.antepod.lumentika.animation.LayoutAnimation
import com.antepod.lumentika.animation.LayoutAnimationEvents
import com.antepod.lumentika.animation.StructuralTransition
import com.antepod.lumentika.animation.TransitionDirection
import com.antepod.lumentika.animation.TransitionEvents
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
/** Marks a [Component] for type-safe builder generation by `lumentika-ksp`. */
public annotation class UIComponent

private object Missing

/** Base class for externally configured component values. */
public sealed class Declaration<T>(initial: Any?) {
    private enum class ExternalMode {
        ONE_WAY,
        TWO_WAY,
    }

    private val local: State<T>
    private var sourceHandle: AutoCloseable? = null
    private var externalMode: ExternalMode? = null
    private var configured = initial !== Missing
    internal val required: Boolean = initial === Missing

    init {
        @Suppress("UNCHECKED_CAST")
        local = state(if (initial === Missing) null as T else initial as T)
    }

    public open val value: T
        get() = local.value

    internal fun ensureConfigured(name: String) {
        require(configured) { "Required declaration '$name' was not configured" }
    }

    public fun set(value: T) {
        requireExternalMode(ExternalMode.ONE_WAY)
        sourceHandle?.close()
        sourceHandle = null
        configured = true
        local.value = value
    }

    public fun source(source: Readable<T>, scope: ComponentScope) {
        requireExternalMode(ExternalMode.ONE_WAY)
        sourceHandle?.close()
        configured = true
        sourceHandle = withComponentScope(scope) { effect { local.value = source.value } }
    }

    public fun source(scope: ComponentScope, block: () -> T) =
        source(withComponentScope(scope) { derived(block = block) }, scope)

    internal open fun dispose() {
        sourceHandle?.close()
        sourceHandle = null
    }

    protected fun write(value: T) {
        local.value = value
    }

    protected fun configureTwoWay() {
        requireExternalMode(ExternalMode.TWO_WAY)
    }

    private fun requireExternalMode(mode: ExternalMode) {
        require(externalMode == null || externalMode == mode) {
            "One-way and two-way declaration sources are mutually exclusive"
        }
        externalMode = mode
    }
}

/** A one-way component input. */
public class Prop<T> internal constructor(initial: Any?) : Declaration<T>(initial), Readable<T>

/** A component input that can write changes back to a bound [Mutable]. */
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
        configureTwoWay()
        require(bound == null) { "Binding already has a two-way source" }
        bound = source
        write(source.value)
        withComponentScope(scope) {
            effect {
                val next = source.value
                if (next != value) {
                    syncing = true
                    try {
                        write(next)
                    } finally {
                        syncing = false
                    }
                }
            }
        }
    }

    internal override fun dispose() {
        super.dispose()
        bound = null
    }
}

/** A typed component event with independently disposable listeners. */
public class Event<E> internal constructor() {
    private val listeners = LinkedHashSet<(E) -> Unit>()
    public var bubbles: Boolean = false
        private set

    public fun bubbles(): Event<E> = apply { bubbles = true }

    public fun listen(listener: (E) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    public fun emit(event: E) {
        listeners.toList().forEach { it(event) }
    }
}

/** A configurable block of child UI mounted by a component. */
public open class Slot internal constructor() {
    internal var content: (UiScope.() -> Unit)? = null

    public fun configure(content: UiScope.() -> Unit) {
        this.content = content
    }

    public fun mount(scope: UiScope): Fragment = scope.fragment { content?.invoke(this) }
}

/** A slot intended to hold an arbitrary list of child declarations. */
public class SlotList internal constructor() : Slot()

/** Base class for persistent, owner-scoped UI components. */
public abstract class Component : AutoCloseable {
    private val declarations = mutableListOf<Pair<String, Declaration<*>>>()
    public val componentScope: ComponentScope = ComponentScope()
    private var mounted: Element? = null
    private var disposing = false
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
        val result =
            withComponentScope(componentScope) {
                viewExecutions++
                view()
            }
        mounted = result
        result.attach(ComponentLifecycleAttachment, AutoCloseable { dispose(detach = false) })
        return result
    }

    protected abstract fun view(): Element

    override fun close() = dispose(detach = true)

    private fun dispose(detach: Boolean) {
        if (disposing || mounted == null && componentScope.isDisposed) return
        disposing = true
        val current = mounted
        mounted = null
        declarations.forEach { it.second.dispose() }
        try {
            if (detach) current?.parent?.remove(current)
            componentScope.close()
        } finally {
            disposing = false
        }
    }
}

private val ComponentLifecycleAttachment =
    com.antepod.lumentika.runtime.AttachmentKey<AutoCloseable>()

/** Creates a standalone one-way declaration with [default]. */
public fun <T> prop(default: T): Prop<T> = Prop(default)

/** Creates a required standalone one-way declaration. */
public fun <T> prop(): Prop<T> = Prop(Missing)

/** Creates a standalone two-way declaration with [default]. */
public fun <T> binding(default: T): Binding<T> = Binding(default)

/** Creates a required standalone two-way declaration. */
public fun <T> binding(): Binding<T> = Binding(Missing)

/** Creates a standalone typed component event. */
public fun <E> event(): Event<E> = Event()

/** Creates a standalone content slot. */
public fun slot(): Slot = Slot()

/** Creates a standalone list content slot. */
public fun slotList(): SlotList = SlotList()

/** A typed key used to inherit a value through an element subtree. */
public class ContextKey<T> internal constructor()

private val contextAttachment =
    com.antepod.lumentika.runtime.AttachmentKey<MutableMap<ContextKey<*>, Any?>>()

/** Creates a unique typed context key. */
public fun <T> contextKey(): ContextKey<T> = ContextKey()

/** Mounts [content] below an element that provides [value] for [key]. */
public fun <T> UiScope.provide(key: ContextKey<T>, value: T, content: UiScope.() -> Unit): Element {
    val provider = element("context-provider")
    provider.attach(contextAttachment, mutableMapOf(key to value))
    nested(provider).content()
    return provider
}

/** Finds the nearest ancestor-provided value for [key]. */
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

/** Reactively mounts or removes [content], optionally animating its entrance and exit. */
public fun UiScope.show(
    condition: Readable<Boolean>,
    transition: StructuralTransition? = null,
    events: TransitionEvents = TransitionEvents(),
    content: UiScope.() -> Unit,
): Element {
    val anchor = element("show")
    val controller =
        ShowTransitionController(
            anchor,
            this,
            transition,
            events,
            content,
        )
    anchor.attach(ShowTransitionAttachment, controller)
    withComponentScope(anchor.scope) {
        effect { controller.update(condition.value) }
    }
    return anchor
}

private object BidirectionalTransitionChannel

private object IntroTransitionChannel

private object OutroTransitionChannel

private val ShowTransitionAttachment =
    com.antepod.lumentika.runtime.AttachmentKey<ShowTransitionController>()

private class ShowTransitionController(
    private val anchor: Element,
    private val scope: UiScope,
    private val transition: StructuralTransition?,
    private val events: TransitionEvents,
    private val content: UiScope.() -> Unit,
) : AutoCloseable {
    private val runtime: ElementAnimationRuntime? = scope.context.elementAnimations
    private var desiredVisible = false
    private var generation = 0L
    private var closed = false

    fun update(visible: Boolean) {
        if (closed) return
        desiredVisible = visible
        generation++
        val updateGeneration = generation
        if (visible) show(updateGeneration) else hide(updateGeneration)
    }

    private fun show(updateGeneration: Long) {
        if (anchor.children.isEmpty()) {
            scope.nested(anchor).content()
            scope.context.requestFrame(true)
        }
        val children = anchor.children.toList()
        val bidirectional = transition?.bidirectional
        val enter = bidirectional ?: transition?.enter
        children.forEach { child ->
            if (bidirectional == null) runtime?.cancel(child, OutroTransitionChannel)
            start(
                child,
                if (bidirectional != null) BidirectionalTransitionChannel
                else IntroTransitionChannel,
                enter,
                TransitionDirection.IN,
                reverse = bidirectional != null,
            ) {
                if (updateGeneration == generation || !desiredVisible) maybeRemove()
            }
        }
    }

    private fun hide(updateGeneration: Long) {
        val children = anchor.children.toList()
        if (children.isEmpty()) return
        val bidirectional = transition?.bidirectional
        val exit = bidirectional ?: transition?.exit
        if (exit == null || runtime == null) {
            removeChildren()
            return
        }
        children.forEach { child ->
            start(
                child,
                if (bidirectional != null) BidirectionalTransitionChannel
                else OutroTransitionChannel,
                exit,
                TransitionDirection.OUT,
                reverse = bidirectional != null,
            ) {
                if (updateGeneration == generation || !desiredVisible) maybeRemove()
            }
        }
    }

    private fun start(
        child: Element,
        channel: Any,
        effect: ElementTransition?,
        direction: TransitionDirection,
        reverse: Boolean,
        finished: () -> Unit,
    ) {
        if (effect == null || runtime == null) {
            finished()
            return
        }
        runtime.start(child, channel, effect, direction, events, reverse, finished)
    }

    private fun maybeRemove() {
        if (desiredVisible) return
        val animations = runtime
        if (
            animations != null &&
                anchor.children.any { child ->
                    animations.isActive(child, BidirectionalTransitionChannel) ||
                        animations.isActive(child, IntroTransitionChannel) ||
                        animations.isActive(child, OutroTransitionChannel)
                }
        ) {
            return
        }
        removeChildren()
    }

    private fun removeChildren() {
        anchor.children.toList().forEach { anchor.remove(it) }
        scope.context.requestFrame(true)
    }

    override fun close() {
        if (closed) return
        closed = true
        runtime?.let { animations ->
            anchor.children.forEach { child ->
                animations.cancel(child, BidirectionalTransitionChannel)
                animations.cancel(child, IntroTransitionChannel)
                animations.cancel(child, OutroTransitionChannel)
            }
        }
    }
}

/** Maintains keyed child identity as [items] change and optionally animates layout movement. */
public fun <T, K> UiScope.forEach(
    items: Readable<List<T>>,
    key: (T) -> K,
    animation: LayoutAnimation? = null,
    animationEvents: LayoutAnimationEvents = LayoutAnimationEvents(),
    content: UiScope.(T) -> Unit,
): Element {
    val anchor = element("for-each")
    val keyed = LinkedHashMap<K, Element>()
    withComponentScope(anchor.scope) {
        effect {
            val nextItems = items.value
            val keys = nextItems.map(key)
            require(keys.size == keys.toSet().size) { "Duplicate key in forEach: $keys" }
            val animationRuntime = context.elementAnimations
            val snapshot =
                if (animation != null && animationRuntime != null) {
                    animationRuntime.captureFlip(keyed.values)
                } else null
            val next = LinkedHashMap<K, Element>()
            nextItems.forEachIndexed { index, item ->
                val itemKey = key(item)
                val child =
                    keyed.remove(itemKey)
                        ?: Element("keyed-item").also {
                            anchor.append(it)
                            nested(it).content(item)
                        }
                next[itemKey] = child
                anchor.move(child, index)
            }
            keyed.values.forEach { anchor.remove(it) }
            keyed.clear()
            keyed.putAll(next)
            if (snapshot != null && animation != null) {
                animationRuntime?.queueFlip(snapshot, next.values, animation, animationEvents)
            }
            context.requestFrame(true)
        }
    }
    return anchor
}
