package com.antepod.lumentika.input

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.runtime.Element

public enum class EventPhase {
    CAPTURE,
    TARGET,
    BUBBLE,
}

public enum class EventType {
    POINTER_DOWN,
    POINTER_MOVE,
    POINTER_UP,
    POINTER_ENTER,
    POINTER_LEAVE,
    POINTER_CANCEL,
    WHEEL,
    KEY_DOWN,
    KEY_UP,
    FOCUS,
    BLUR,
    FOCUS_IN,
    FOCUS_OUT,
}

public interface UIEvent {
    public val target: Element
    public val currentTarget: Element
    public val phase: EventPhase
    public val defaultPrevented: Boolean

    public fun stopPropagation()

    public fun stopImmediatePropagation()

    public fun preventDefault()
}

public open class BaseEvent(
    final override val target: Element,
    public val cancelable: Boolean = true,
) : UIEvent {
    final override lateinit var currentTarget: Element
        internal set

    final override var phase: EventPhase = EventPhase.TARGET
        internal set

    final override var defaultPrevented: Boolean = false
        private set

    internal var propagationStopped: Boolean = false
    internal var immediatePropagationStopped: Boolean = false

    override fun stopPropagation() {
        propagationStopped = true
    }

    override fun stopImmediatePropagation() {
        propagationStopped = true
        immediatePropagationStopped = true
    }

    override fun preventDefault() {
        if (cancelable) defaultPrevented = true
    }
}

public enum class PointerType {
    MOUSE,
    TOUCH,
    PEN,
    UNKNOWN,
}

public data class KeyModifiers(
    val shift: Boolean = false,
    val control: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
)

public data class PointerSample(val position: Point, val pressure: Float?, val timestampNanos: Long)

public class PointerEvent(
    target: Element,
    val pointerId: Int,
    val pointerType: PointerType,
    val position: Point,
    val button: Int = 0,
    val buttons: Int = 0,
    val pressure: Float? = null,
    val timestampNanos: Long,
    val modifiers: KeyModifiers = KeyModifiers(),
    val historical: List<PointerSample> = emptyList(),
    cancelable: Boolean = true,
) : BaseEvent(target, cancelable)

public class WheelEvent(
    target: Element,
    val position: Point,
    val deltaX: Float,
    val deltaY: Float,
    val timestampNanos: Long,
) : BaseEvent(target)

public enum class LogicalKey {
    TAB,
    ENTER,
    SPACE,
    ESCAPE,
    ARROW_LEFT,
    ARROW_RIGHT,
    ARROW_UP,
    ARROW_DOWN,
    HOME,
    END,
    BACKSPACE,
    DELETE,
    CHARACTER,
    UNKNOWN,
}

public class KeyboardEvent(
    target: Element,
    val logicalKey: LogicalKey,
    val physicalKey: String,
    val text: String? = null,
    val repeat: Boolean = false,
    val modifiers: KeyModifiers = KeyModifiers(),
    val timestampNanos: Long,
) : BaseEvent(target)

private data class Listener(val capture: Boolean, val callback: (UIEvent) -> Unit)

public class EventDispatcher(private val root: Element) {
    private val listeners = mutableMapOf<Element, MutableMap<EventType, MutableList<Listener>>>()
    private val defaultActions = mutableMapOf<Element, MutableMap<EventType, (UIEvent) -> Unit>>()
    private val pointerCapture = mutableMapOf<Int, Element>()
    private var hoverPath: List<Element> = emptyList()

    public fun on(
        element: Element,
        type: EventType,
        capture: Boolean = false,
        listener: (UIEvent) -> Unit,
    ): AutoCloseable {
        require(isInRoot(element)) { "Listener element is outside dispatcher root" }
        val entry = Listener(capture, listener)
        listeners.getOrPut(element, ::mutableMapOf).getOrPut(type, ::mutableListOf) += entry
        return AutoCloseable { listeners[element]?.get(type)?.remove(entry) }
    }

    public fun defaultAction(
        element: Element,
        type: EventType,
        action: (UIEvent) -> Unit,
    ): AutoCloseable {
        defaultActions.getOrPut(element, ::mutableMapOf)[type] = action
        return AutoCloseable {
            if (defaultActions[element]?.get(type) === action) {
                defaultActions[element]?.remove(type)
                if (defaultActions[element].isNullOrEmpty()) defaultActions.remove(element)
            }
        }
    }

    public fun setPointerCapture(element: Element, pointerId: Int) {
        require(element.isMounted && isInRoot(element))
        pointerCapture[pointerId] = element
    }

    public fun releasePointerCapture(element: Element, pointerId: Int) {
        if (pointerCapture[pointerId] === element) pointerCapture.remove(pointerId)
    }

    public fun captured(pointerId: Int): Element? =
        pointerCapture[pointerId]?.takeIf { it.isMounted && isInRoot(it) }

    public fun updateHover(actualHit: Element?, timestampNanos: Long) {
        val next = actualHit?.let(::path) ?: emptyList()
        (hoverPath - next.toSet()).asReversed().forEach {
            dispatch(
                EventType.POINTER_LEAVE,
                PointerEvent(
                    it,
                    -1,
                    PointerType.MOUSE,
                    Point(0f, 0f),
                    timestampNanos = timestampNanos,
                    cancelable = false,
                ),
            )
        }
        (next - hoverPath.toSet()).forEach {
            dispatch(
                EventType.POINTER_ENTER,
                PointerEvent(
                    it,
                    -1,
                    PointerType.MOUSE,
                    Point(0f, 0f),
                    timestampNanos = timestampNanos,
                    cancelable = false,
                ),
            )
        }
        hoverPath = next
    }

    public fun dispatch(type: EventType, event: BaseEvent): Boolean {
        val target =
            if (event is PointerEvent) captured(event.pointerId) ?: event.target else event.target
        require(target.isMounted && isInRoot(target)) {
            "Cannot dispatch to stale/out-of-root element"
        }
        val actualEvent =
            if (target === event.target) event else retarget(event as PointerEvent, target)
        val path = path(target)
        for (element in path.dropLast(1)) {
            invoke(element, type, actualEvent, EventPhase.CAPTURE, capture = true)
            if (actualEvent.propagationStopped) break
        }
        if (!actualEvent.propagationStopped) {
            invoke(target, type, actualEvent, EventPhase.TARGET, capture = true)
            if (!actualEvent.immediatePropagationStopped)
                invoke(target, type, actualEvent, EventPhase.TARGET, capture = false)
        }
        if (!actualEvent.propagationStopped) {
            for (element in path.dropLast(1).asReversed()) {
                invoke(element, type, actualEvent, EventPhase.BUBBLE, capture = false)
                if (actualEvent.propagationStopped) break
            }
        }
        if (!actualEvent.defaultPrevented) defaultActions[target]?.get(type)?.invoke(actualEvent)
        if (type == EventType.POINTER_UP || type == EventType.POINTER_CANCEL)
            pointerCapture.remove((actualEvent as? PointerEvent)?.pointerId)
        return !actualEvent.defaultPrevented
    }

    public fun repairRemovedSubtree(subtree: Element) {
        pointerCapture.entries.removeIf { (_, element) ->
            element === subtree || isDescendant(element, subtree)
        }
        listeners.keys.removeIf { it === subtree || isDescendant(it, subtree) }
        defaultActions.keys.removeIf { it === subtree || isDescendant(it, subtree) }
    }

    private fun invoke(
        element: Element,
        type: EventType,
        event: BaseEvent,
        phase: EventPhase,
        capture: Boolean,
    ) {
        event.currentTarget = element
        event.phase = phase
        event.immediatePropagationStopped = false
        listeners[element]
            ?.get(type)
            ?.toList()
            ?.filter { it.capture == capture }
            ?.forEach {
                it.callback(event)
                if (event.immediatePropagationStopped) return
            }
    }

    private fun path(target: Element): List<Element> {
        val result = ArrayDeque<Element>()
        var current: Element? = target
        while (current != null) {
            result.addFirst(current)
            current = current.parent
        }
        return result.toList()
    }

    private fun isInRoot(element: Element): Boolean =
        element === root || isDescendant(element, root)

    private fun isDescendant(element: Element, ancestor: Element): Boolean {
        var current = element.parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }

    private fun retarget(event: PointerEvent, target: Element): PointerEvent =
        PointerEvent(
            target,
            event.pointerId,
            event.pointerType,
            event.position,
            event.button,
            event.buttons,
            event.pressure,
            event.timestampNanos,
            event.modifiers,
            event.historical,
            event.cancelable,
        )
}

public enum class FocusCause {
    POINTER,
    KEYBOARD,
    PROGRAMMATIC,
    REPAIR,
}

public data class FocusProperties(
    val focusable: Boolean = false,
    val disabled: Boolean = false,
    val tabIndex: Int = 0,
)

public class FocusManager(
    private val root: Element,
    private val dispatcher: EventDispatcher,
) {
    private val properties = mutableMapOf<Element, FocusProperties>()
    public var activeElement: Element? = null
        private set

    public var focusVisible: Boolean = false
        private set

    public val focusWithin: Set<Element>
        get() {
            val result = linkedSetOf<Element>()
            var current = activeElement
            while (current != null) {
                result += current
                current = current.parent
            }
            return result
        }

    public fun configure(element: Element, value: FocusProperties) {
        properties[element] = value
    }

    public fun unconfigure(element: Element) {
        if (activeElement === element) blur(element)
        properties.remove(element)
    }

    public fun focus(element: Element, cause: FocusCause = FocusCause.PROGRAMMATIC) {
        val config = properties[element] ?: FocusProperties()
        require(element.isMounted && config.focusable && !config.disabled) {
            "Element is not focusable"
        }
        if (activeElement === element) return
        val previous = activeElement
        previous?.let {
            dispatcher.dispatch(EventType.BLUR, BaseEvent(it, cancelable = false))
            dispatcher.dispatch(EventType.FOCUS_OUT, BaseEvent(it, cancelable = false))
        }
        activeElement = element
        focusVisible = cause == FocusCause.KEYBOARD
        dispatcher.dispatch(EventType.FOCUS, BaseEvent(element, cancelable = false))
        dispatcher.dispatch(EventType.FOCUS_IN, BaseEvent(element, cancelable = false))
    }

    public fun blur(element: Element) {
        if (activeElement !== element) return
        dispatcher.dispatch(EventType.BLUR, BaseEvent(element, cancelable = false))
        dispatcher.dispatch(EventType.FOCUS_OUT, BaseEvent(element, cancelable = false))
        activeElement = null
        focusVisible = false
    }

    public fun focusNext(): Element? = move(1)

    public fun focusPrevious(): Element? = move(-1)

    public fun repairBeforeRemoval(subtree: Element) {
        val active = activeElement ?: return
        if (active === subtree || isDescendant(active, subtree)) {
            val candidates = ordered().filterNot { it === subtree || isDescendant(it, subtree) }
            blur(active)
            candidates.firstOrNull()?.let { focus(it, FocusCause.REPAIR) }
        }
    }

    private fun move(direction: Int): Element? {
        val candidates = ordered()
        if (candidates.isEmpty()) return null
        val current = candidates.indexOf(activeElement)
        val next = if (current < 0) 0 else (current + direction).mod(candidates.size)
        return candidates[next].also { focus(it, FocusCause.KEYBOARD) }
    }

    private fun ordered(): List<Element> {
        val logical = buildList { walk(root, this) }
        return logical
            .filter {
                properties[it]?.let { value ->
                    value.focusable && !value.disabled && value.tabIndex >= 0
                } == true
            }
            .sortedWith(
                compareBy<Element> {
                        properties[it]?.tabIndex?.takeIf { index -> index > 0 } ?: Int.MAX_VALUE
                    }
                    .thenBy { logical.indexOf(it) }
            )
    }

    private fun walk(element: Element, result: MutableList<Element>) {
        result += element
        element.children.forEach { walk(it, result) }
    }

    private fun isDescendant(element: Element, ancestor: Element): Boolean {
        var current = element.parent
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent
        }
        return false
    }
}
