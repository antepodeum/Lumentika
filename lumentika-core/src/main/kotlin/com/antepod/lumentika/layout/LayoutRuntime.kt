package com.antepod.lumentika.layout

import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UnitResolver
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.Fragment
import com.antepod.lumentika.runtime.IntrinsicMeasurable
import com.antepod.lumentika.runtime.IntrinsicMeasureInput
import com.antepod.lumentika.style.Auto
import com.antepod.lumentika.style.Calc
import com.antepod.lumentika.style.DimensionValue
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.Percent
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.ResolvedStyle
import com.antepod.lumentika.style.resolveLength
import com.antepod.taffy.geometry.Size as TaffySize
import com.antepod.taffy.style.AvailableSpace
import com.antepod.taffy.style.Dimension as TaffyDimension
import com.antepod.taffy.style.Style as TaffyStyle
import com.antepod.taffy.tree.NodeId
import com.antepod.taffy.tree.TaffyTree

public data class LayoutSnapshot(
    val generation: Long,
    val frameTimeNanos: Long,
    val geometries: Map<Long, Rect>,
)

/** Root-owned bridge. Taffy4J remains sole sizing/positioning algorithm. */
public class LayoutRuntime(
    private val logicalRoot: Element,
    private val units: UnitResolver,
    private val resolveStyle: (Element) -> ResolvedStyle,
    rounding: Boolean = true,
) : AutoCloseable {
    private val tree = TaffyTree<Element>()
    private val nodes = LinkedHashMap<Element, NodeId>()
    private val syntheticRoot: NodeId = tree.newLeaf(TaffyStyle.DEFAULT)
    private var dirty = true
    private var lastComputedFrame = Long.MIN_VALUE
    private var generation = 0L
    public var computeCount: Long = 0
        private set

    public var snapshot: LayoutSnapshot = LayoutSnapshot(0, 0, emptyMap())
        private set

    init {
        if (rounding) tree.enableRounding() else tree.disableRounding()
    }

    public fun requestLayout() {
        dirty = true
    }

    public fun frame(frameTimeNanos: Long, environment: UiEnvironment): LayoutSnapshot {
        if (!dirty || lastComputedFrame == frameTimeNanos) return snapshot
        lastComputedFrame = frameTimeNanos
        synchronize(environment)
        tree.setStyle(
            syntheticRoot,
            TaffyStyle.builder()
                .display(com.antepod.taffy.style.Display.BLOCK)
                .size(environment.viewport.width, environment.viewport.height)
                .build(),
        )
        tree.computeLayoutWithMeasure(
            syntheticRoot,
            TaffySize(
                AvailableSpace.definite(environment.viewport.width),
                AvailableSpace.definite(environment.viewport.height),
            ),
        ) { known, available, _, context, _ ->
            val measurable = context.orElse(null)?.content as? IntrinsicMeasurable
            val measured =
                measurable?.measure(
                    IntrinsicMeasureInput(
                        knownWidth = known.width.orElse(null),
                        knownHeight = known.height.orElse(null),
                        availableWidth = available.width.intoOptional().orElse(null),
                        availableHeight = available.height.intoOptional().orElse(null),
                    )
                ) ?: com.antepod.lumentika.geometry.Size.ZERO
            TaffySize(measured.width, measured.height)
        }
        val geometries = LinkedHashMap<Long, Rect>()
        commit(logicalRoot, 0f, 0f, geometries)
        generation++
        computeCount++
        dirty = false
        snapshot = LayoutSnapshot(generation, frameTimeNanos, geometries)
        return snapshot
    }

    private fun synchronize(environment: UiEnvironment) {
        val visited = LinkedHashSet<Element>()
        val rootNode = ensureNode(logicalRoot, environment, visited)
        tree.setChildren(syntheticRoot, listOf(rootNode))
        val stale = nodes.keys.filter { it !in visited }
        stale.asReversed().forEach { element -> tree.remove(nodes.remove(element)!!) }
    }

    private fun ensureNode(
        element: Element,
        environment: UiEnvironment,
        visited: MutableSet<Element>,
    ): NodeId {
        require(element !is Fragment) { "Fragments are flattened before Taffy projection" }
        visited += element
        val style = project(resolveStyle(element), environment)
        val node =
            nodes[element] ?: tree.newLeafWithContext(style, element).also { nodes[element] = it }
        tree.setStyle(node, style)
        val children = flatten(element.children).map { ensureNode(it, environment, visited) }
        tree.setChildren(node, children)
        return node
    }

    private fun flatten(children: List<Element>): List<Element> = buildList {
        children.forEach { child ->
            if (child is Fragment) addAll(flatten(child.children)) else add(child)
        }
    }

    private fun project(style: ResolvedStyle, environment: UiEnvironment): TaffyStyle {
        val width = dimension(style[Properties.Width], environment, environment.viewport.width)
        val height = dimension(style[Properties.Height], environment, environment.viewport.height)
        return TaffyStyle.builder()
            .display(
                when (style[Properties.Display]) {
                    Display.NONE -> com.antepod.taffy.style.Display.NONE
                    Display.BLOCK -> com.antepod.taffy.style.Display.BLOCK
                    Display.FLEX -> com.antepod.taffy.style.Display.FLEX
                    Display.GRID -> com.antepod.taffy.style.Display.GRID
                }
            )
            .size(TaffySize(width, height))
            .minSize(
                TaffySize(
                    dimension(style[Properties.MinWidth], environment, environment.viewport.width),
                    dimension(
                        style[Properties.MinHeight],
                        environment,
                        environment.viewport.height,
                    ),
                )
            )
            .maxSize(
                TaffySize(
                    dimension(style[Properties.MaxWidth], environment, environment.viewport.width),
                    dimension(
                        style[Properties.MaxHeight],
                        environment,
                        environment.viewport.height,
                    ),
                )
            )
            .flexDirection(
                when (style[Properties.FlexDirection]) {
                    FlexDirection.ROW -> com.antepod.taffy.style.FlexDirection.ROW
                    FlexDirection.ROW_REVERSE -> com.antepod.taffy.style.FlexDirection.ROW_REVERSE
                    FlexDirection.COLUMN -> com.antepod.taffy.style.FlexDirection.COLUMN
                    FlexDirection.COLUMN_REVERSE ->
                        com.antepod.taffy.style.FlexDirection.COLUMN_REVERSE
                }
            )
            .flexGrow(style[Properties.FlexGrow])
            .flexShrink(style[Properties.FlexShrink])
            .build()
    }

    private fun dimension(
        value: DimensionValue,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyDimension =
        when (value) {
            Auto -> TaffyDimension.AUTO
            is Percent -> TaffyDimension.percent(value.fraction)
            is Calc -> TaffyDimension.length(resolveLength(value, environment, units, basis) ?: 0f)
            else -> TaffyDimension.length(resolveLength(value, environment, units, basis) ?: 0f)
        }

    private fun commit(
        element: Element,
        parentX: Float,
        parentY: Float,
        result: MutableMap<Long, Rect>,
    ): Rect {
        if (element is Fragment) {
            val boxes = element.children.map { commit(it, parentX, parentY, result) }
            val union =
                if (boxes.isEmpty()) Rect(parentX, parentY, 0f, 0f)
                else
                    Rect(
                        boxes.minOf { it.x },
                        boxes.minOf { it.y },
                        boxes.maxOf { it.right } - boxes.minOf { it.x },
                        boxes.maxOf { it.bottom } - boxes.minOf { it.y },
                    )
            element.geometry = union
            result[element.id] = union
            return union
        }
        val layout = tree.layout(nodes.getValue(element))
        val rect =
            Rect(
                parentX + layout.location.x,
                parentY + layout.location.y,
                layout.size.width,
                layout.size.height,
            )
        element.geometry = rect
        result[element.id] = rect
        element.children.forEach { commit(it, rect.x, rect.y, result) }
        return rect
    }

    override fun close() {
        tree.close()
    }
}
