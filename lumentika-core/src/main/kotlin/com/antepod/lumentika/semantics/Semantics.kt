package com.antepod.lumentika.semantics

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.render.HitTestArtifact
import com.antepod.lumentika.runtime.AttachmentKey
import com.antepod.lumentika.runtime.Element
import java.util.concurrent.atomic.AtomicLong

@JvmInline public value class SemanticsNodeId(val value: Long)

public enum class SemanticRole {
    NONE,
    BUTTON,
    CHECKBOX,
    SLIDER,
    TEXT_FIELD,
    IMAGE,
    TEXT,
    LIST,
    LIST_ITEM,
    SCROLL_VIEW,
    TOOLTIP,
}

public enum class SemanticAction {
    CLICK,
    LONG_CLICK,
    SET_VALUE,
    INCREMENT,
    DECREMENT,
    FOCUS,
    CLEAR_FOCUS,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    SET_SELECTION,
    COPY,
    CUT,
    PASTE,
}

public enum class LiveRegion {
    NONE,
    POLITE,
    ASSERTIVE,
}

public data class SemanticRange(
    val current: Float,
    val minimum: Float,
    val maximum: Float,
    val step: Float? = null,
)

public data class CollectionInfo(val rows: Int, val columns: Int, val hierarchical: Boolean = false)

public data class CollectionItemInfo(
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
)

public data class SemanticsConfiguration(
    val role: SemanticRole = SemanticRole.NONE,
    val label: String? = null,
    val value: String? = null,
    val stateDescription: String? = null,
    val hint: String? = null,
    val enabled: Boolean = true,
    val selected: Boolean? = null,
    val checked: Boolean? = null,
    val expanded: Boolean? = null,
    val readOnly: Boolean = false,
    val password: Boolean = false,
    val hidden: Boolean = false,
    val mergeDescendants: Boolean = false,
    val clearDescendants: Boolean = false,
    val range: SemanticRange? = null,
    val collection: CollectionInfo? = null,
    val item: CollectionItemInfo? = null,
    val liveRegion: LiveRegion = LiveRegion.NONE,
    val textSelection: com.antepod.lumentika.text.TextRange? = null,
    val actions: Map<SemanticAction, (Any?) -> Boolean> = emptyMap(),
)

public data class SemanticsNode(
    val id: SemanticsNodeId,
    val elementId: Long,
    val config: SemanticsConfiguration,
    val bounds: Rect,
    val children: List<SemanticsNodeId>,
)

public data class SemanticsArtifact(
    val generation: Long,
    val roots: List<SemanticsNodeId>,
    val nodes: Map<SemanticsNodeId, SemanticsNode>,
    val accessibilityFocus: SemanticsNodeId?,
)

public data class SemanticsChangeSet(
    val added: Set<SemanticsNodeId>,
    val removed: Set<SemanticsNodeId>,
    val changed: Set<SemanticsNodeId>,
)

public interface AccessibilityAdapter {
    public fun onArtifactCommitted(artifact: SemanticsArtifact, changes: SemanticsChangeSet)

    public fun announce(message: String, priority: LiveRegion)
}

public val SemanticsAttachment: AttachmentKey<SemanticsConfiguration> = AttachmentKey()

public class SemanticsRuntime(private val root: Element) {
    private val ids = mutableMapOf<Element, SemanticsNodeId>()
    private val configs = mutableMapOf<Element, SemanticsConfiguration>()
    private var generation = 0L
    public var accessibilityFocus: SemanticsNodeId? = null
        private set

    public var artifact = SemanticsArtifact(0, emptyList(), emptyMap(), null)
        private set

    public fun configure(element: Element, config: SemanticsConfiguration) {
        configs[element] = config
        ids.getOrPut(element) { SemanticsNodeId(nextId.incrementAndGet()) }
    }

    public fun commit(hit: HitTestArtifact): SemanticsChangeSet {
        val previous = artifact.nodes
        val entries = hit.entries.associateBy { it.element }
        val nodes = linkedMapOf<SemanticsNodeId, SemanticsNode>()
        fun walk(element: Element): List<SemanticsNodeId> {
            val config = configs[element] ?: element.attachment(SemanticsAttachment)
            if (config?.hidden == true) return emptyList()
            val children =
                if (config?.clearDescendants == true) emptyList()
                else element.children.flatMap(::walk)
            if (config == null) return children
            val id = ids.getOrPut(element) { SemanticsNodeId(nextId.incrementAndGet()) }
            val bounds =
                entries[element]?.let { entry ->
                    val local = entry.localBounds
                    val points =
                        listOf(
                                Point(local.x, local.y),
                                Point(local.right, local.y),
                                Point(local.right, local.bottom),
                                Point(local.x, local.bottom),
                            )
                            .map(entry.rootTransform::transform)
                    val transformed =
                        Rect(
                            points.minOf { it.x },
                            points.minOf { it.y },
                            points.maxOf { it.x } - points.minOf { it.x },
                            points.maxOf { it.y } - points.minOf { it.y },
                        )
                    entry.clip.intersect(transformed) ?: Rect(0f, 0f, 0f, 0f)
                } ?: element.geometry
            nodes[id] =
                SemanticsNode(
                    id,
                    element.id,
                    config,
                    bounds,
                    if (config.mergeDescendants) emptyList() else children,
                )
            return listOf(id)
        }
        val roots = walk(root)
        generation++
        artifact =
            SemanticsArtifact(
                generation,
                roots,
                nodes,
                accessibilityFocus?.takeIf(nodes::containsKey),
            )
        val changes =
            SemanticsChangeSet(
                nodes.keys - previous.keys,
                previous.keys - nodes.keys,
                nodes.keys.filterTo(linkedSetOf()) { previous[it] != nodes[it] },
            )
        accessibilityFocus = artifact.accessibilityFocus
        return changes
    }

    public fun perform(
        id: SemanticsNodeId,
        action: SemanticAction,
        argument: Any? = null,
    ): Boolean = artifact.nodes[id]?.config?.actions?.get(action)?.invoke(argument) ?: false

    public fun requestAccessibilityFocus(id: SemanticsNodeId): Boolean {
        if (id !in artifact.nodes) return false
        accessibilityFocus = id
        return true
    }

    public companion object {
        private val nextId = AtomicLong()
    }
}
