package com.antepod.lumentika.layout

import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UnitResolver
import com.antepod.lumentika.runtime.ContentInvalidation
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.Fragment
import com.antepod.lumentika.runtime.IntrinsicMeasurable
import com.antepod.lumentika.runtime.IntrinsicMeasureInput
import com.antepod.lumentika.runtime.MeasureSpace
import com.antepod.lumentika.style.AlignContent
import com.antepod.lumentika.style.AlignItems
import com.antepod.lumentika.style.Auto
import com.antepod.lumentika.style.BoxSizing
import com.antepod.lumentika.style.Calc
import com.antepod.lumentika.style.Clear
import com.antepod.lumentika.style.DimensionValue
import com.antepod.lumentika.style.Direction
import com.antepod.lumentika.style.Display
import com.antepod.lumentika.style.FlexDirection
import com.antepod.lumentika.style.FlexWrap
import com.antepod.lumentika.style.FloatLayout
import com.antepod.lumentika.style.GridAutoFlow
import com.antepod.lumentika.style.GridMaxTrackSizing
import com.antepod.lumentika.style.GridMinTrackSizing
import com.antepod.lumentika.style.GridPlacement
import com.antepod.lumentika.style.GridRepetition
import com.antepod.lumentika.style.GridTemplateComponent
import com.antepod.lumentika.style.GridTrackSizing
import com.antepod.lumentika.style.Percent
import com.antepod.lumentika.style.Position
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.ResolvedStyle
import com.antepod.lumentika.style.resolveLength
import com.antepod.taffy.geometry.Line as TaffyLine
import com.antepod.taffy.geometry.Rect as TaffyRect
import com.antepod.taffy.geometry.Size as TaffySize
import com.antepod.taffy.style.AlignContent as TaffyAlignContent
import com.antepod.taffy.style.AlignItems as TaffyAlignItems
import com.antepod.taffy.style.AvailableSpace
import com.antepod.taffy.style.Dimension as TaffyDimension
import com.antepod.taffy.style.GridPlacement as TaffyGridPlacement
import com.antepod.taffy.style.GridTemplateComponent as TaffyGridTemplateComponent
import com.antepod.taffy.style.LengthPercentage as TaffyLengthPercentage
import com.antepod.taffy.style.LengthPercentageAuto as TaffyLengthPercentageAuto
import com.antepod.taffy.style.MaxTrackSizingFunction as TaffyMaxTrackSizingFunction
import com.antepod.taffy.style.MinTrackSizingFunction as TaffyMinTrackSizingFunction
import com.antepod.taffy.style.RepetitionCount as TaffyRepetitionCount
import com.antepod.taffy.style.Style as TaffyStyle
import com.antepod.taffy.style.TrackSizingFunction as TaffyTrackSizingFunction
import com.antepod.taffy.tree.NodeId
import com.antepod.taffy.tree.TaffyTree
import java.util.Optional

/** Immutable committed geometry and scroll extent for one element. */
public data class LayoutSnapshot(
    val generation: Long,
    val frameTimeNanos: Long,
    val geometries: Map<Long, Rect>,
)

/** Root-owned bridge. Taffy4J remains sole sizing/positioning algorithm. */
/** Projects the mounted element tree into Taffy4J and commits logical geometry. */
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

    /** Marks layout dirty and requests a root frame once. */
    public fun requestLayout() {
        dirty = true
    }

    /** Commits layout for [environment], reusing the previous snapshot when clean. */
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
                    handle.invalidationSubscription =
                        element.onContentInvalidated(handle::invalidate)
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

    internal fun project(style: ResolvedStyle, environment: UiEnvironment): TaffyStyle {
        val width = dimension(style[Properties.Width], environment, environment.viewport.width)
        val height = dimension(style[Properties.Height], environment, environment.viewport.height)
        val padding = style[Properties.Padding]
        val margin = style[Properties.Margin]
        val inset = style[Properties.Inset]
        val border = style[Properties.Border]
        val columnGap = style[Properties.ColumnGap] ?: style[Properties.Gap]
        val rowGap = style[Properties.RowGap] ?: style[Properties.Gap]
        return TaffyStyle.builder()
            .display(
                when (style[Properties.Display]) {
                    Display.NONE -> com.antepod.taffy.style.Display.NONE
                    Display.BLOCK -> com.antepod.taffy.style.Display.BLOCK
                    Display.FLEX -> com.antepod.taffy.style.Display.FLEX
                    Display.GRID -> com.antepod.taffy.style.Display.GRID
                }
            )
            .itemIsTable(style[Properties.ItemIsTable])
            .itemIsReplaced(style[Properties.ItemIsReplaced])
            .boxSizing(
                when (style[Properties.BoxSizing]) {
                    BoxSizing.BORDER_BOX -> com.antepod.taffy.style.BoxSizing.BORDER_BOX
                    BoxSizing.CONTENT_BOX -> com.antepod.taffy.style.BoxSizing.CONTENT_BOX
                }
            )
            .direction(
                when (style[Properties.Direction]) {
                    Direction.LTR -> com.antepod.taffy.style.Direction.LTR
                    Direction.RTL -> com.antepod.taffy.style.Direction.RTL
                }
            )
            .overflow(
                taffyOverflow(style[Properties.OverflowX] ?: style[Properties.Overflow]),
                taffyOverflow(style[Properties.OverflowY] ?: style[Properties.Overflow]),
            )
            .scrollbarWidth(
                resolveLength(
                    style[Properties.ScrollbarWidth] as DimensionValue,
                    environment,
                    units,
                ) ?: 0f
            )
            .floatValue(
                when (style[Properties.FloatValue]) {
                    FloatLayout.LEFT -> com.antepod.taffy.style.Float.LEFT
                    FloatLayout.RIGHT -> com.antepod.taffy.style.Float.RIGHT
                    FloatLayout.NONE -> com.antepod.taffy.style.Float.NONE
                }
            )
            .clear(
                when (style[Properties.Clear]) {
                    Clear.LEFT -> com.antepod.taffy.style.Clear.LEFT
                    Clear.RIGHT -> com.antepod.taffy.style.Clear.RIGHT
                    Clear.BOTH -> com.antepod.taffy.style.Clear.BOTH
                    Clear.NONE -> com.antepod.taffy.style.Clear.NONE
                }
            )
            .position(
                when (style[Properties.Position]) {
                    Position.RELATIVE -> com.antepod.taffy.style.Position.RELATIVE
                    Position.ABSOLUTE -> com.antepod.taffy.style.Position.ABSOLUTE
                }
            )
            .inset(
                TaffyRect(
                    lengthPercentageAuto(inset.left, environment, environment.viewport.width),
                    lengthPercentageAuto(inset.right, environment, environment.viewport.width),
                    lengthPercentageAuto(inset.top, environment, environment.viewport.height),
                    lengthPercentageAuto(inset.bottom, environment, environment.viewport.height),
                )
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
            .border(
                TaffyRect(
                    lengthPercentage(border.left, environment, environment.viewport.width),
                    lengthPercentage(border.right, environment, environment.viewport.width),
                    lengthPercentage(border.top, environment, environment.viewport.height),
                    lengthPercentage(border.bottom, environment, environment.viewport.height),
                )
            )
            .aspectRatio(style[Properties.AspectRatio])
            .alignItems(Optional.ofNullable(style[Properties.AlignItems]?.toTaffy()))
            .alignSelf(Optional.ofNullable(style[Properties.AlignSelf]?.toTaffy()))
            .justifyItems(Optional.ofNullable(style[Properties.JustifyItems]?.toTaffy()))
            .justifySelf(Optional.ofNullable(style[Properties.JustifySelf]?.toTaffy()))
            .alignContent(Optional.ofNullable(style[Properties.AlignContent]?.toTaffy()))
            .justifyContent(Optional.ofNullable(style[Properties.JustifyContent]?.toTaffy()))
            .margin(
                TaffyRect(
                    lengthPercentageAuto(margin.left, environment, environment.viewport.width),
                    lengthPercentageAuto(margin.right, environment, environment.viewport.width),
                    lengthPercentageAuto(margin.top, environment, environment.viewport.height),
                    lengthPercentageAuto(margin.bottom, environment, environment.viewport.height),
                )
            )
            .gap(
                TaffySize(
                    lengthPercentage(columnGap, environment, environment.viewport.width),
                    lengthPercentage(rowGap, environment, environment.viewport.height),
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
            .flexWrap(
                when (style[Properties.FlexWrap]) {
                    FlexWrap.NO_WRAP -> com.antepod.taffy.style.FlexWrap.NO_WRAP
                    FlexWrap.WRAP -> com.antepod.taffy.style.FlexWrap.WRAP
                    FlexWrap.WRAP_REVERSE -> com.antepod.taffy.style.FlexWrap.WRAP_REVERSE
                }
            )
            .flexBasis(
                dimension(style[Properties.FlexBasis], environment, environment.viewport.width)
            )
            .flexGrow(style[Properties.FlexGrow])
            .flexShrink(style[Properties.FlexShrink])
            .gridTemplateRows(
                style[Properties.GridTemplateRows].map {
                    gridTemplateComponent(it, environment, environment.viewport.height)
                }
            )
            .gridTemplateColumns(
                style[Properties.GridTemplateColumns].map {
                    gridTemplateComponent(it, environment, environment.viewport.width)
                }
            )
            .gridAutoRows(
                style[Properties.GridAutoRows].map {
                    gridTrack(it, environment, environment.viewport.height)
                }
            )
            .gridAutoColumns(
                style[Properties.GridAutoColumns].map {
                    gridTrack(it, environment, environment.viewport.width)
                }
            )
            .gridAutoFlow(
                when (style[Properties.GridAutoFlow]) {
                    GridAutoFlow.ROW -> com.antepod.taffy.style.GridAutoFlow.ROW
                    GridAutoFlow.COLUMN -> com.antepod.taffy.style.GridAutoFlow.COLUMN
                    GridAutoFlow.ROW_DENSE -> com.antepod.taffy.style.GridAutoFlow.ROW_DENSE
                    GridAutoFlow.COLUMN_DENSE -> com.antepod.taffy.style.GridAutoFlow.COLUMN_DENSE
                }
            )
            .gridTemplateAreas(
                style[Properties.GridTemplateAreas]?.let { areas ->
                    com.antepod.taffy.style.GridTemplateAreas(
                        areas.areas.map {
                            com.antepod.taffy.style.GridTemplateArea(
                                it.name,
                                it.rowStart,
                                it.rowEnd,
                                it.columnStart,
                                it.columnEnd,
                            )
                        },
                        areas.rowCount,
                        areas.columnCount,
                    )
                }
            )
            .gridTemplateColumnNames(style[Properties.GridTemplateColumnNames])
            .gridTemplateRowNames(style[Properties.GridTemplateRowNames])
            .gridRow(style[Properties.GridRow].toTaffy())
            .gridColumn(style[Properties.GridColumn].toTaffy())
            .build()
    }

    private fun AlignItems.toTaffy(): TaffyAlignItems =
        when (this) {
            AlignItems.START -> TaffyAlignItems.START
            AlignItems.END -> TaffyAlignItems.END
            AlignItems.FLEX_START -> TaffyAlignItems.FLEX_START
            AlignItems.FLEX_END -> TaffyAlignItems.FLEX_END
            AlignItems.SELF_START -> TaffyAlignItems.SELF_START
            AlignItems.SELF_END -> TaffyAlignItems.SELF_END
            AlignItems.CENTER -> TaffyAlignItems.CENTER
            AlignItems.BASELINE -> TaffyAlignItems.BASELINE
            AlignItems.STRETCH -> TaffyAlignItems.STRETCH
            AlignItems.SAFE_START -> TaffyAlignItems.SAFE_START
            AlignItems.SAFE_END -> TaffyAlignItems.SAFE_END
            AlignItems.SAFE_FLEX_START -> TaffyAlignItems.SAFE_FLEX_START
            AlignItems.SAFE_FLEX_END -> TaffyAlignItems.SAFE_FLEX_END
            AlignItems.SAFE_SELF_START -> TaffyAlignItems.SAFE_SELF_START
            AlignItems.SAFE_SELF_END -> TaffyAlignItems.SAFE_SELF_END
            AlignItems.SAFE_CENTER -> TaffyAlignItems.SAFE_CENTER
        }

    private fun AlignContent.toTaffy(): TaffyAlignContent =
        when (this) {
            AlignContent.START -> TaffyAlignContent.START
            AlignContent.END -> TaffyAlignContent.END
            AlignContent.FLEX_START -> TaffyAlignContent.FLEX_START
            AlignContent.FLEX_END -> TaffyAlignContent.FLEX_END
            AlignContent.CENTER -> TaffyAlignContent.CENTER
            AlignContent.STRETCH -> TaffyAlignContent.STRETCH
            AlignContent.SPACE_BETWEEN -> TaffyAlignContent.SPACE_BETWEEN
            AlignContent.SPACE_EVENLY -> TaffyAlignContent.SPACE_EVENLY
            AlignContent.SPACE_AROUND -> TaffyAlignContent.SPACE_AROUND
            AlignContent.SAFE_START -> TaffyAlignContent.SAFE_START
            AlignContent.SAFE_END -> TaffyAlignContent.SAFE_END
            AlignContent.SAFE_FLEX_START -> TaffyAlignContent.SAFE_FLEX_START
            AlignContent.SAFE_FLEX_END -> TaffyAlignContent.SAFE_FLEX_END
            AlignContent.SAFE_CENTER -> TaffyAlignContent.SAFE_CENTER
        }

    private fun gridTemplateComponent(
        value: GridTemplateComponent,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyGridTemplateComponent =
        when (value) {
            is GridTemplateComponent.Single ->
                TaffyGridTemplateComponent.single(gridTrack(value.track, environment, basis))
            is GridTemplateComponent.Repeat ->
                TaffyGridTemplateComponent.repeat(
                    com.antepod.taffy.style.GridTemplateRepetition(
                        when (val repetition = value.repetition) {
                            is GridRepetition.Count -> TaffyRepetitionCount.count(repetition.count)
                            GridRepetition.AutoFill -> TaffyRepetitionCount.AUTO_FILL
                            GridRepetition.AutoFit -> TaffyRepetitionCount.AUTO_FIT
                        },
                        value.tracks.map { gridTrack(it, environment, basis) },
                        value.lineNames,
                    )
                )
        }

    private fun gridTrack(
        value: GridTrackSizing,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyTrackSizingFunction =
        TaffyTrackSizingFunction(
            when (val min = value.min) {
                GridMinTrackSizing.Auto -> TaffyMinTrackSizingFunction.AUTO
                GridMinTrackSizing.MinContent -> TaffyMinTrackSizingFunction.MIN_CONTENT
                GridMinTrackSizing.MaxContent -> TaffyMinTrackSizingFunction.MAX_CONTENT
                is GridMinTrackSizing.Fixed ->
                    when (val fixed = min.value) {
                        is Percent -> TaffyMinTrackSizingFunction.percent(fixed.fraction)
                        else ->
                            TaffyMinTrackSizingFunction.length(
                                resolveLength(fixed as DimensionValue, environment, units, basis)
                                    ?: 0f
                            )
                    }
            },
            when (val max = value.max) {
                GridMaxTrackSizing.Auto -> TaffyMaxTrackSizingFunction.AUTO
                GridMaxTrackSizing.MinContent -> TaffyMaxTrackSizingFunction.MIN_CONTENT
                GridMaxTrackSizing.MaxContent -> TaffyMaxTrackSizingFunction.MAX_CONTENT
                is GridMaxTrackSizing.Fixed ->
                    when (val fixed = max.value) {
                        is Percent -> TaffyMaxTrackSizingFunction.percent(fixed.fraction)
                        else ->
                            TaffyMaxTrackSizingFunction.length(
                                resolveLength(fixed as DimensionValue, environment, units, basis)
                                    ?: 0f
                            )
                    }
                is GridMaxTrackSizing.Fraction -> TaffyMaxTrackSizingFunction.fr(max.value)
                is GridMaxTrackSizing.FitContent ->
                    when (val limit = max.limit) {
                        is Percent -> TaffyMaxTrackSizingFunction.fitContentPercent(limit.fraction)
                        else ->
                            TaffyMaxTrackSizingFunction.fitContentPx(
                                resolveLength(limit as DimensionValue, environment, units, basis)
                                    ?: 0f
                            )
                    }
            },
        )

    private fun com.antepod.lumentika.style.GridLine.toTaffy(): TaffyLine<TaffyGridPlacement> =
        TaffyLine(start.toTaffy(), end.toTaffy())

    private fun GridPlacement.toTaffy(): TaffyGridPlacement =
        when (this) {
            GridPlacement.Auto -> TaffyGridPlacement.AUTO
            is GridPlacement.Line ->
                name?.let { TaffyGridPlacement.namedLine(it, index) }
                    ?: TaffyGridPlacement.line(index)
            is GridPlacement.Span ->
                name?.let { TaffyGridPlacement.namedSpan(it, count) }
                    ?: TaffyGridPlacement.span(count)
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

    private fun lengthPercentage(
        value: com.antepod.lumentika.style.LengthPercentageValue,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyLengthPercentage = lengthPercentage(value as DimensionValue, environment, basis)

    private fun taffyOverflow(
        value: com.antepod.lumentika.style.Overflow
    ): com.antepod.taffy.style.Overflow =
        when (value) {
            com.antepod.lumentika.style.Overflow.VISIBLE -> com.antepod.taffy.style.Overflow.VISIBLE
            com.antepod.lumentika.style.Overflow.CLIP -> com.antepod.taffy.style.Overflow.CLIP
            com.antepod.lumentika.style.Overflow.HIDDEN -> com.antepod.taffy.style.Overflow.HIDDEN
            com.antepod.lumentika.style.Overflow.SCROLL -> com.antepod.taffy.style.Overflow.SCROLL
            // Taffy 0.13 has no AUTO variant. HIDDEN preserves AUTO's scroll-container
            // min-size behavior; Lumentika's scroll runtime decides whether scrolling is needed.
            com.antepod.lumentika.style.Overflow.AUTO -> com.antepod.taffy.style.Overflow.HIDDEN
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

    private fun lengthPercentageAuto(
        value: com.antepod.lumentika.style.LengthPercentageAutoValue,
        environment: UiEnvironment,
        basis: Float,
    ): TaffyLengthPercentageAuto = lengthPercentageAuto(value as DimensionValue, environment, basis)

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
        var invalidationSubscription: AutoCloseable? = null

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

        fun invalidate(invalidation: ContentInvalidation) {
            when (invalidation) {
                ContentInvalidation.PAINT -> Unit
                ContentInvalidation.INTRINSIC_MEASUREMENT -> {
                    cache.clear()
                    invalidated()
                }
                ContentInvalidation.TEXT_METRICS -> {
                    val target = measurable ?: return
                    if (cache.isEmpty()) return
                    val previous = cache.toMap()
                    val updated =
                        previous.keys.associateWith { input ->
                            measurementCount++
                            target.measure(input)
                        }
                    cache.clear()
                    cache.putAll(updated)
                    if (previous.any { (input, size) -> updated[input] != size }) invalidated()
                }
            }
        }

        fun commit() {
            markedDirty = false
        }

        override fun close() {
            subscription?.close()
            subscription = null
            invalidationSubscription?.close()
            invalidationSubscription = null
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
