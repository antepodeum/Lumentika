package com.antepod.lumentika.semantics

import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.render.HitTestArtifact
import com.antepod.lumentika.runtime.AttachmentKey
import com.antepod.lumentika.runtime.Element
import java.util.concurrent.atomic.AtomicLong

/** Stable identity of a committed semantics node. */
@JvmInline public value class SemanticsNodeId(val value: Long)

/** Accessibility role of an element. */
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

/** Accessibility action that can be performed on a semantic node. */
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

/** Announcement priority for changing semantic content. */
public enum class LiveRegion {
    NONE,
    POLITE,
    ASSERTIVE,
}

/** Numeric range exposed by slider-like semantic nodes. */
public data class SemanticRange(
    val current: Float,
    val minimum: Float,
    val maximum: Float,
    val step: Float? = null,
)

/** Row, column, and hierarchy metadata for a semantic collection. */
public data class CollectionInfo(val rows: Int, val columns: Int, val hierarchical: Boolean = false)

/** Position and span metadata for an item in a semantic collection. */
public data class CollectionItemInfo(
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
)

/** Mutable-tree semantics attached to one retained element. */
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

/** Immutable semantic node committed for a platform adapter. */
public data class SemanticsNode(
    val id: SemanticsNodeId,
    val elementId: Long,
    val config: SemanticsConfiguration,
    val bounds: Rect,
    val children: List<SemanticsNodeId>,
)

/** Immutable flattened semantics tree for one committed generation. */
public data class SemanticsArtifact(
    val generation: Long,
    val roots: List<SemanticsNodeId>,
    val nodes: Map<SemanticsNodeId, SemanticsNode>,
    val accessibilityFocus: SemanticsNodeId?,
)

/** IDs added, updated, and removed since the previous semantics commit. */
public data class SemanticsChangeSet(
    val added: Set<SemanticsNodeId>,
    val removed: Set<SemanticsNodeId>,
    val changed: Set<SemanticsNodeId>,
)

/** Receives committed semantics and performs native announcements. */
public interface AccessibilityAdapter {
    /** Receives a coherent semantics artifact and its incremental changes. */
    public fun onArtifactCommitted(artifact: SemanticsArtifact, changes: SemanticsChangeSet)

    /** Announces [message] with native live-region [priority]. */
    public fun announce(message: String, priority: LiveRegion)
}

/** Element attachment containing its semantic configuration. */
public val SemanticsAttachment: AttachmentKey<SemanticsConfiguration> = AttachmentKey()

/** Builds stable semantic artifacts and routes accessibility actions. */
public class SemanticsRuntime(
    private val root: Element,
    private val announcementSink: (String, LiveRegion) -> Unit = { _, _ -> },
) {
    private val ids = mutableMapOf<Element, SemanticsNodeId>()
    private val configs = mutableMapOf<Element, SemanticsConfiguration>()
    private var generation = 0L
    public var accessibilityFocus: SemanticsNodeId? = null
        private set

    public var artifact = SemanticsArtifact(0, emptyList(), emptyMap(), null)
        private set

    /** Attaches [config] to [element] and marks semantics dirty. */
    public fun configure(element: Element, config: SemanticsConfiguration) {
        configs[element] = config
        ids.getOrPut(element) { SemanticsNodeId(nextId.incrementAndGet()) }
    }

    /** Commits semantics using geometry from the matching [hit] artifact. */
    public fun commit(hit: HitTestArtifact): SemanticsChangeSet {
        val previous = artifact.nodes
        val entries = hit.entries.associateBy { it.element }
        val nodes = linkedMapOf<SemanticsNodeId, SemanticsNode>()
        fun consume(idsToConsume: List<SemanticsNodeId>): List<SemanticsNode> =
            idsToConsume.flatMap { id ->
                val node = nodes.remove(id) ?: return@flatMap emptyList()
                listOf(node) + consume(node.children)
            }

        fun mergedText(values: List<String?>): String? =
            values.filterNotNull().filter(String::isNotBlank).distinct().joinToString(" ").ifBlank {
                null
            }

        fun walk(element: Element): List<SemanticsNodeId> {
            val config = configs[element] ?: element.attachment(SemanticsAttachment)
            if (config?.hidden == true) return emptyList()
            val children =
                if (config?.clearDescendants == true) emptyList()
                else element.children.flatMap(::walk)
            if (config == null) return children
            val id = ids.getOrPut(element) { SemanticsNodeId(nextId.incrementAndGet()) }
            var bounds =
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
            val mergedChildren = if (config.mergeDescendants) consume(children) else emptyList()
            if (mergedChildren.isNotEmpty()) {
                val allBounds = listOf(bounds) + mergedChildren.map(SemanticsNode::bounds)
                val left = allBounds.minOf(Rect::x)
                val top = allBounds.minOf(Rect::y)
                bounds =
                    Rect(
                        left,
                        top,
                        allBounds.maxOf(Rect::right) - left,
                        allBounds.maxOf(Rect::bottom) - top,
                    )
            }
            val effectiveConfig =
                if (!config.mergeDescendants) config
                else
                    config.copy(
                        label =
                            mergedText(
                                listOf(config.label) + mergedChildren.map { it.config.label }
                            ),
                        value = config.value ?: mergedText(mergedChildren.map { it.config.value }),
                        stateDescription =
                            config.stateDescription
                                ?: mergedText(mergedChildren.map { it.config.stateDescription }),
                        hint = config.hint ?: mergedText(mergedChildren.map { it.config.hint }),
                        actions =
                            mergedChildren.fold(emptyMap<SemanticAction, (Any?) -> Boolean>()) {
                                actions,
                                node ->
                                actions + node.config.actions
                            } + config.actions,
                    )
            nodes[id] =
                SemanticsNode(
                    id,
                    element.id,
                    effectiveConfig,
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
        val live = changes.added + changes.changed
        live.forEach { id ->
            val node = nodes[id] ?: return@forEach
            if (node.config.liveRegion != LiveRegion.NONE) {
                val previousNode = previous[id]
                val message = node.config.label ?: node.config.value ?: node.config.stateDescription
                val previousMessage =
                    previousNode?.config?.label
                        ?: previousNode?.config?.value
                        ?: previousNode?.config?.stateDescription
                if (message != null && message != previousMessage) {
                    announcementSink(message, node.config.liveRegion)
                }
            }
        }
        ids.keys.removeIf { !it.isMounted }
        configs.keys.removeIf { !it.isMounted }
        return changes
    }

    /** Performs [action] on [id], returning whether the node handled it. */
    public fun perform(
        id: SemanticsNodeId,
        action: SemanticAction,
        argument: Any? = null,
    ): Boolean = artifact.nodes[id]?.config?.actions?.get(action)?.invoke(argument) ?: false

    /** Moves accessibility focus to [id] when the node exists. */
    public fun requestAccessibilityFocus(id: SemanticsNodeId): Boolean {
        if (id !in artifact.nodes) return false
        accessibilityFocus = id
        return true
    }

    /** Emits a live-region announcement through the configured adapter callback. */
    public fun announce(message: String, priority: LiveRegion = LiveRegion.POLITE) {
        require(priority != LiveRegion.NONE)
        announcementSink(message, priority)
    }

    public companion object {
        private val nextId = AtomicLong()
    }
}
