package com.antepod.lumentika.layout

import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UnitResolver
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.Fragment
import com.antepod.lumentika.runtime.IntrinsicMeasurable
import com.antepod.lumentika.runtime.IntrinsicMeasureInput
import com.antepod.lumentika.runtime.MeasureSpace
import com.antepod.lumentika.style.Auto
import com.antepod.lumentika.style.Calc
import com.antepod.lumentika.style.DimensionValue
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.Percent
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.ResolvedStyle
import com.antepod.lumentika.style.resolveLength
import com.antepod.taffy.geometry.Rect as TaffyRect
import com.antepod.taffy.geometry.Size as TaffySize
import com.antepod.taffy.style.AvailableSpace
import com.antepod.taffy.style.Dimension as TaffyDimension
import com.antepod.taffy.style.LengthPercentage as TaffyLengthPercentage
import com.antepod.taffy.style.LengthPercentageAuto as TaffyLengthPercentageAuto
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
    private val onLayoutRequested: () -> Unit = {},
) : AutoCloseable {
    private val tree = TaffyTree<MeasureHandle>()
    private val nodes = LinkedHashMap<Element, NodeId>()
    private val measures = LinkedHashMap<Element, MeasureHandle>()
    private val syntheticRoot: NodeId = tree.newLeaf(TaffyStyle.DEFAULT)
    private var dirty = true
    private var lastComputedFrame = Long.MIN_VALUE
    private var generation = 0L
    public var computeCount: Long = 0
        private set

    public var measurementCount: Long = 0
        private set

    public var intrinsicMarkDirtyCount: Long = 0
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
            val input =
                IntrinsicMeasureInput(
                    knownWidth = known.width.orElse(null),
                    knownHeight = known.height.orElse(null),
                    availableWidth = available.width.toMeasureSpace(),
                    availableHeight = available.height.toMeasureSpace(),
                )
            val measured =
                context.orElse(null)?.measure(input) ?: com.antepod.lumentika.geometry.Size.ZERO
            TaffySize(
                finite(input.knownWidth ?: measured.width),
                finite(input.knownHeight ?: measured.height),
            )
        }
        val geometries = LinkedHashMap<Long, Rect>()
        commit(logicalRoot, 0f, 0f, geometries)
        generation++
        computeCount++
        measures.values.forEach(MeasureHandle::commit)
        dirty = false
        snapshot = LayoutSnapshot(generation, frameTimeNanos, geometries)
        return snapshot
    }

    private fun synchronize(environment: UiEnvironment) {
        val visited = LinkedHashSet<Element>()
        val rootNode = ensureNode(logicalRoot, environment, visited)
        tree.setChildren(syntheticRoot, listOf(rootNode))
        val stale = nodes.keys.filter { it !in visited }
        stale.asReversed().forEach { element ->
            tree.remove(nodes.remove(element)!!)
            measures.remove(element)?.close()
        }
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
            nodes[element]
                ?: run {
                    lateinit var created: NodeId
                    lateinit var handle: MeasureHandle
                    handle =
                        MeasureHandle(element.content) {
                            if (!handle.markedDirty) {
                                tree.markDirty(created)
                                handle.markedDirty = true
                                intrinsicMarkDirtyCount++
                            }
                            dirty = true
                            onLayoutRequested()
                        }
                    created = tree.newLeafWithContext(style, handle)
                    handle.subscription = element.onContentChanged(handle::update)
                    nodes[element] = created
                    measures[element] = handle
                    created
                }
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
        val padding = style[Properties.Padding]
        val margin = style[Properties.Margin]
        val gap = lengthPercentage(style[Properties.Gap], environment, environment.viewport.width)
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
            .padding(
                TaffyRect(
                    lengthPercentage(padding.left, environment, environment.viewport.width),
                    lengthPercentage(padding.right, environment, environment.viewport.width),
                    lengthPercentage(padding.top, environment, environment.viewport.height),
                    lengthPercentage(padding.bottom, environment, environment.viewport.height),
                )
            )
            .margin(
                TaffyRect(
                    lengthPercentageAuto(margin.left, environment, environment.viewport.width),
                    lengthPercentageAuto(margin.right, environment, environment.viewport.width),
                    lengthPercentageAuto(margin.top, environment, environment.viewport.height),
                    lengthPercentageAuto(margin.bottom, environment, environment.viewport.height),
                )
            )
            .gap(TaffySize(gap, gap))
            .overflow(
                taffyOverflow(style[Properties.Overflow]),
                taffyOverflow(style[Properties.Overflow]),
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

    private fun lengthPercentage(
        value: DimensionValue,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyLengthPercentage =
        when (value) {
            is Percent -> TaffyLengthPercentage.percent(value.fraction)
            else ->
                TaffyLengthPercentage.length(resolveLength(value, environment, units, basis) ?: 0f)
        }

    private fun taffyOverflow(
        value: com.antepod.lumentika.style.Overflow
    ): com.antepod.taffy.style.Overflow =
        when (value) {
            com.antepod.lumentika.style.Overflow.VISIBLE -> com.antepod.taffy.style.Overflow.VISIBLE
            com.antepod.lumentika.style.Overflow.CLIP -> com.antepod.taffy.style.Overflow.CLIP
            com.antepod.lumentika.style.Overflow.HIDDEN -> com.antepod.taffy.style.Overflow.HIDDEN
            com.antepod.lumentika.style.Overflow.SCROLL -> com.antepod.taffy.style.Overflow.SCROLL
            com.antepod.lumentika.style.Overflow.AUTO -> com.antepod.taffy.style.Overflow.DEFAULT
        }

    private fun lengthPercentageAuto(
        value: DimensionValue,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyLengthPercentageAuto =
        when (value) {
            Auto -> TaffyLengthPercentageAuto.AUTO
            is Percent -> TaffyLengthPercentageAuto.percent(value.fraction)
            else ->
                TaffyLengthPercentageAuto.length(
                    resolveLength(value, environment, units, basis) ?: 0f
                )
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
        measures.values.forEach(MeasureHandle::close)
        measures.clear()
        tree.close()
    }

    private inner class MeasureHandle(
        content: com.antepod.lumentika.runtime.Content?,
        private val invalidated: () -> Unit = {},
    ) : AutoCloseable {
        private var measurable = content as? IntrinsicMeasurable
        private val cache =
            mutableMapOf<IntrinsicMeasureInput, com.antepod.lumentika.geometry.Size>()
        var markedDirty = false
        var subscription: AutoCloseable? = null

        fun measure(input: IntrinsicMeasureInput): com.antepod.lumentika.geometry.Size =
            cache.getOrPut(input) {
                measurementCount++
                measurable?.measure(input) ?: com.antepod.lumentika.geometry.Size.ZERO
            }

        fun update(content: com.antepod.lumentika.runtime.Content?) {
            measurable = content as? IntrinsicMeasurable
            cache.clear()
            invalidated()
        }

        fun commit() {
            markedDirty = false
        }

        override fun close() {
            subscription?.close()
            subscription = null
            cache.clear()
        }
    }

    private fun AvailableSpace.toMeasureSpace(): MeasureSpace =
        when (kind()) {
            AvailableSpace.Kind.DEFINITE -> MeasureSpace.Definite(unwrap())
            AvailableSpace.Kind.MIN_CONTENT -> MeasureSpace.MinContent
            AvailableSpace.Kind.MAX_CONTENT -> MeasureSpace.MaxContent
        }

    private fun finite(value: Float): Float = if (value.isFinite()) value else 0f
}
