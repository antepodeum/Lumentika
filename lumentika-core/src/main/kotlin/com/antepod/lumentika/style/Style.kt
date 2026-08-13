package com.antepod.lumentika.style

import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UnitResolver
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.runtime.AttachmentKey
import com.antepod.lumentika.runtime.Element

/** Value accepted by a dimension style property. */
public sealed interface DimensionValue

/** Length or percentage accepted by box-edge properties. */
public sealed interface LengthPercentageValue

/** Length, percentage, or automatic value accepted by positioning properties. */
public sealed interface LengthPercentageAutoValue

/** Platform-resolvable absolute length. */
public sealed interface AbsoluteLengthValue

/** Logical core pixels. */
public data class Px(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

/** Density-independent platform units. */
public data class Dp(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

/** Font-scaled platform units. */
public data class Sp(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

/** Physical device pixels converted by the platform unit resolver. */
public data class PhysicalPx(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

/** Percentage dimension stored as a zero-to-one fraction. */
public data class Percent(val fraction: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue

/** Automatic sizing or positioning chosen by layout. */
public data object Auto : DimensionValue, LengthPercentageAutoValue

/** Linear combination of dimension values. */
public data class Calc(val terms: List<Pair<Float, DimensionValue>>) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue

/** Converts this number to logical pixels. */
public val Number.px: Px
    get() = Px(toFloat())
/** Converts this number to density-independent units. */
public val Number.dp: Dp
    get() = Dp(toFloat())
/** Converts this number to font-scaled units. */
public val Number.sp: Sp
    get() = Sp(toFloat())
/** Converts this number to physical device pixels. */
public val Number.physicalPx: PhysicalPx
    get() = PhysicalPx(toFloat())
/** Converts this percentage value to a fractional dimension. */
public val Number.percent: Percent
    get() = Percent(toFloat() / 100f)

/** Returns the automatic dimension value for function-oriented DSL usage. */
public fun auto(): Auto = Auto

/** Values for the top, right, bottom, and left edges. */
public data class Edges<T>(val top: T, val right: T, val bottom: T, val left: T)

/** Creates equal values for all four edges. */
public fun <T> edges(all: T): Edges<T> = Edges(all, all, all, all)

/** Creates edges using shared vertical and horizontal values. */
public fun <T> edges(vertical: T, horizontal: T): Edges<T> =
    Edges(vertical, horizontal, vertical, horizontal)

/** Creates independently specified edge values in top-right-bottom-left order. */
public fun <T> edges(top: T, right: T, bottom: T, left: T): Edges<T> =
    Edges(top, right, bottom, left)

/** Paint used by backgrounds and text. External backends may provide immutable implementations. */
public interface Paint

/** Solid ARGB color paint. */
public data class SolidPaint(val argb: Int) : Paint

/** ARGB color stop in a gradient. */
public data class GradientStop(val offset: Float, val color: Int)

/** Linear gradient paint. */
public data class LinearGradientPaint(val angleDegrees: Float, val stops: List<GradientStop>) :
    Paint

/** Radial gradient paint. */
public data class RadialGradientPaint(val stops: List<GradientStop>) : Paint

/** Paint backed by an adapter-resolved image identifier. */
public data class ImagePaint(val source: String) : Paint

/** Ordered composition of multiple paints. */
public data class LayeredPaint(val layers: List<Paint>) : Paint

/** Creates a solid paint from 8-bit red, green, blue, and alpha channels. */
public fun rgb(red: Int, green: Int, blue: Int, alpha: Int = 255): SolidPaint =
    SolidPaint(
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    )

/** Primary layout model or absence from layout. */
public enum class Display {
    NONE,
    BLOCK,
    FLEX,
    GRID,
}

/** Static, relative, or absolute positioning mode. */
public enum class Position {
    RELATIVE,
    ABSOLUTE,
}

/** Layout, clipping, and scrolling behavior for overflowing content. */
public enum class Overflow {
    VISIBLE,
    CLIP,
    HIDDEN,
    SCROLL,
    AUTO,
}

/** Main-axis direction of a flex container. */
public enum class FlexDirection {
    ROW,
    ROW_REVERSE,
    COLUMN,
    COLUMN_REVERSE,
}

/** Wrapping policy for flex items. */
public enum class FlexWrap {
    NO_WRAP,
    WRAP,
    WRAP_REVERSE,
}

/** Legacy compact alignment values. */
public enum class Align {
    AUTO,
    START,
    END,
    CENTER,
    STRETCH,
    SPACE_BETWEEN,
    SPACE_AROUND,
    SPACE_EVENLY,
}

/** Whether an element paints while retaining layout participation. */
public enum class Visibility {
    VISIBLE,
    HIDDEN,
}

/** Whether an element participates in hit testing. */
public enum class PointerEvents {
    AUTO,
    NONE,
}

@JvmInline
/** Bit mask describing runtime work affected by a property change. */
public value class StyleImpact(public val bits: Int) {
    public operator fun plus(other: StyleImpact): StyleImpact = StyleImpact(bits or other.bits)

    public fun contains(other: StyleImpact): Boolean = bits and other.bits != 0

    public companion object {
        public val NONE = StyleImpact(0)
        public val LAYOUT = StyleImpact(1 shl 0)
        public val INTRINSIC_MEASURE = StyleImpact(1 shl 1)
        public val PAINT = StyleImpact(1 shl 2)
        public val TRANSFORM = StyleImpact(1 shl 3)
        public val CLIP = StyleImpact(1 shl 4)
        public val EFFECT = StyleImpact(1 shl 5)
        public val STACKING = StyleImpact(1 shl 6)
        public val SCROLL = StyleImpact(1 shl 7)
        public val INTERACTION = StyleImpact(1 shl 8)
        public val SEMANTICS = StyleImpact(1 shl 9)
        public val INHERITANCE = StyleImpact(1 shl 10)
    }
}

/** Typed metadata and default value for one style property. */
public class StyleProperty<T>(
    public val id: Int,
    public val name: String,
    public val initialValue: T,
    public val inherited: Boolean = false,
    public val impact: StyleImpact,
) {
    public val mask: PropertyMask = PropertyMask(GeneratedStylePropertyCatalog.maskBits[id])
}

@JvmInline
/** Compact set of style-property identifiers. */
public value class PropertyMask(public val bits: Long) {
    public operator fun plus(other: PropertyMask): PropertyMask = PropertyMask(bits or other.bits)

    public operator fun contains(property: StyleProperty<*>): Boolean =
        bits and property.mask.bits != 0L

    public companion object {
        public val NONE: PropertyMask = PropertyMask(0)
    }
}

/** Catalog of all style properties understood by the core runtime. */
public object Properties {
    public val Display =
        StyleProperty(
            GeneratedStylePropertyCatalog.DISPLAY,
            "display",
            com.antepod.lumentika.style.Display.BLOCK,
            impact = StyleImpact.LAYOUT,
        )
    public val Width =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.WIDTH,
            "width",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val Height =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.HEIGHT,
            "height",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val MinWidth =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.MIN_WIDTH,
            "minWidth",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val MinHeight =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.MIN_HEIGHT,
            "minHeight",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val MaxWidth =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.MAX_WIDTH,
            "maxWidth",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val MaxHeight =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.MAX_HEIGHT,
            "maxHeight",
            Auto,
            impact = StyleImpact.LAYOUT,
        )
    public val Padding =
        StyleProperty(
            GeneratedStylePropertyCatalog.PADDING,
            "padding",
            edges<DimensionValue>(0.px),
            impact = StyleImpact.LAYOUT,
        )
    public val Margin =
        StyleProperty(
            GeneratedStylePropertyCatalog.MARGIN,
            "margin",
            edges<DimensionValue>(0.px),
            impact = StyleImpact.LAYOUT,
        )
    public val Gap =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.GAP,
            "gap",
            0.px,
            impact = StyleImpact.LAYOUT,
        )
    public val FlexDirection =
        StyleProperty(
            GeneratedStylePropertyCatalog.FLEX_DIRECTION,
            "flexDirection",
            com.antepod.lumentika.style.FlexDirection.ROW,
            impact = StyleImpact.LAYOUT,
        )
    public val FlexGrow =
        StyleProperty(
            GeneratedStylePropertyCatalog.FLEX_GROW,
            "flexGrow",
            0f,
            impact = StyleImpact.LAYOUT,
        )
    public val FlexShrink =
        StyleProperty(
            GeneratedStylePropertyCatalog.FLEX_SHRINK,
            "flexShrink",
            1f,
            impact = StyleImpact.LAYOUT,
        )
    public val Overflow =
        StyleProperty(
            GeneratedStylePropertyCatalog.OVERFLOW,
            "overflow",
            com.antepod.lumentika.style.Overflow.VISIBLE,
            impact = StyleImpact.LAYOUT + StyleImpact.CLIP + StyleImpact.SCROLL,
        )
    public val Background =
        StyleProperty<Paint?>(
            GeneratedStylePropertyCatalog.BACKGROUND,
            "background",
            null,
            impact = StyleImpact.PAINT,
        )
    public val Opacity =
        StyleProperty(
            GeneratedStylePropertyCatalog.OPACITY,
            "opacity",
            1f,
            impact = StyleImpact.EFFECT,
        )
    public val ZIndex =
        StyleProperty(
            GeneratedStylePropertyCatalog.Z_INDEX,
            "zIndex",
            0,
            impact = StyleImpact.STACKING,
        )
    public val Visibility =
        StyleProperty(
            GeneratedStylePropertyCatalog.VISIBILITY,
            "visibility",
            com.antepod.lumentika.style.Visibility.VISIBLE,
            true,
            StyleImpact.PAINT +
                StyleImpact.INTERACTION +
                StyleImpact.SEMANTICS +
                StyleImpact.INHERITANCE,
        )
    public val PointerEvents =
        StyleProperty(
            GeneratedStylePropertyCatalog.POINTER_EVENTS,
            "pointerEvents",
            com.antepod.lumentika.style.PointerEvents.AUTO,
            impact = StyleImpact.INTERACTION,
        )
    public val FontSize =
        StyleProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.FONT_SIZE,
            "fontSize",
            16.sp,
            true,
            StyleImpact.INTRINSIC_MEASURE +
                StyleImpact.LAYOUT +
                StyleImpact.PAINT +
                StyleImpact.INHERITANCE,
        )
    public val Color =
        StyleProperty<Paint>(
            GeneratedStylePropertyCatalog.COLOR,
            "color",
            rgb(0, 0, 0),
            true,
            StyleImpact.PAINT + StyleImpact.INHERITANCE,
        )
    public val ItemIsTable =
        layoutProperty(GeneratedStylePropertyCatalog.ITEM_IS_TABLE, "itemIsTable", false)
    public val ItemIsReplaced =
        layoutProperty(GeneratedStylePropertyCatalog.ITEM_IS_REPLACED, "itemIsReplaced", false)
    public val BoxSizing =
        layoutProperty(
            GeneratedStylePropertyCatalog.BOX_SIZING,
            "boxSizing",
            com.antepod.lumentika.style.BoxSizing.BORDER_BOX,
        )
    public val Direction =
        layoutProperty(
            GeneratedStylePropertyCatalog.DIRECTION,
            "direction",
            com.antepod.lumentika.style.Direction.LTR,
        )
    public val OverflowX =
        layoutProperty<Overflow?>(GeneratedStylePropertyCatalog.OVERFLOW_X, "overflowX", null)
    public val OverflowY =
        layoutProperty<Overflow?>(GeneratedStylePropertyCatalog.OVERFLOW_Y, "overflowY", null)
    public val ScrollbarWidth =
        layoutProperty<AbsoluteLengthValue>(
            GeneratedStylePropertyCatalog.SCROLLBAR_WIDTH,
            "scrollbarWidth",
            0.px,
        )
    public val FloatValue =
        layoutProperty(
            GeneratedStylePropertyCatalog.FLOAT_VALUE,
            "floatValue",
            FloatLayout.NONE,
        )
    public val Clear =
        layoutProperty(
            GeneratedStylePropertyCatalog.CLEAR,
            "clear",
            com.antepod.lumentika.style.Clear.NONE,
        )
    public val Position =
        layoutProperty(
            GeneratedStylePropertyCatalog.POSITION,
            "position",
            com.antepod.lumentika.style.Position.RELATIVE,
        )
    public val Inset =
        layoutProperty(
            GeneratedStylePropertyCatalog.INSET,
            "inset",
            edges<LengthPercentageAutoValue>(Auto),
        )
    public val AspectRatio =
        layoutProperty<Float?>(GeneratedStylePropertyCatalog.ASPECT_RATIO, "aspectRatio", null)
    public val Border =
        layoutProperty(
            GeneratedStylePropertyCatalog.BORDER,
            "border",
            edges<LengthPercentageValue>(0.px),
        )
    public val AlignItems =
        layoutProperty<com.antepod.lumentika.style.AlignItems?>(
            GeneratedStylePropertyCatalog.ALIGN_ITEMS,
            "alignItems",
            null,
        )
    public val AlignSelf =
        layoutProperty<com.antepod.lumentika.style.AlignItems?>(
            GeneratedStylePropertyCatalog.ALIGN_SELF,
            "alignSelf",
            null,
        )
    public val JustifyItems =
        layoutProperty<com.antepod.lumentika.style.AlignItems?>(
            GeneratedStylePropertyCatalog.JUSTIFY_ITEMS,
            "justifyItems",
            null,
        )
    public val JustifySelf =
        layoutProperty<com.antepod.lumentika.style.AlignItems?>(
            GeneratedStylePropertyCatalog.JUSTIFY_SELF,
            "justifySelf",
            null,
        )
    public val AlignContent =
        layoutProperty<com.antepod.lumentika.style.AlignContent?>(
            GeneratedStylePropertyCatalog.ALIGN_CONTENT,
            "alignContent",
            null,
        )
    public val JustifyContent =
        layoutProperty<com.antepod.lumentika.style.AlignContent?>(
            GeneratedStylePropertyCatalog.JUSTIFY_CONTENT,
            "justifyContent",
            null,
        )
    public val ColumnGap =
        layoutProperty<DimensionValue?>(
            GeneratedStylePropertyCatalog.COLUMN_GAP,
            "columnGap",
            null,
        )
    public val RowGap =
        layoutProperty<DimensionValue?>(GeneratedStylePropertyCatalog.ROW_GAP, "rowGap", null)
    public val TextAlign =
        layoutProperty(
            GeneratedStylePropertyCatalog.TEXT_ALIGN,
            "textAlign",
            com.antepod.lumentika.style.TextAlign.AUTO,
        )
    public val FlexWrap =
        layoutProperty(
            GeneratedStylePropertyCatalog.FLEX_WRAP,
            "flexWrap",
            com.antepod.lumentika.style.FlexWrap.NO_WRAP,
        )
    public val FlexBasis =
        layoutProperty<DimensionValue>(
            GeneratedStylePropertyCatalog.FLEX_BASIS,
            "flexBasis",
            Auto,
        )
    public val GridTemplateRows =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_ROWS,
            "gridTemplateRows",
            emptyList<GridTemplateComponent>(),
        )
    public val GridTemplateColumns =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_COLUMNS,
            "gridTemplateColumns",
            emptyList<GridTemplateComponent>(),
        )
    public val GridAutoRows =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_AUTO_ROWS,
            "gridAutoRows",
            emptyList<GridTrackSizing>(),
        )
    public val GridAutoColumns =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_AUTO_COLUMNS,
            "gridAutoColumns",
            emptyList<GridTrackSizing>(),
        )
    public val GridAutoFlow =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_AUTO_FLOW,
            "gridAutoFlow",
            com.antepod.lumentika.style.GridAutoFlow.ROW,
        )
    public val GridTemplateAreas =
        layoutProperty<com.antepod.lumentika.style.GridTemplateAreas?>(
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_AREAS,
            "gridTemplateAreas",
            null,
        )
    public val GridTemplateColumnNames =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_COLUMN_NAMES,
            "gridTemplateColumnNames",
            emptyList<List<String>>(),
        )
    public val GridTemplateRowNames =
        layoutProperty(
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_ROW_NAMES,
            "gridTemplateRowNames",
            emptyList<List<String>>(),
        )
    public val GridRow =
        layoutProperty(GeneratedStylePropertyCatalog.GRID_ROW, "gridRow", GridLine())
    public val GridColumn =
        layoutProperty(GeneratedStylePropertyCatalog.GRID_COLUMN, "gridColumn", GridLine())
    public val all: List<StyleProperty<*>> =
        listOf(
            Display,
            Width,
            Height,
            MinWidth,
            MinHeight,
            MaxWidth,
            MaxHeight,
            Padding,
            Margin,
            Gap,
            FlexDirection,
            FlexGrow,
            FlexShrink,
            Overflow,
            Background,
            Opacity,
            ZIndex,
            Visibility,
            PointerEvents,
            FontSize,
            Color,
            ItemIsTable,
            ItemIsReplaced,
            BoxSizing,
            Direction,
            OverflowX,
            OverflowY,
            ScrollbarWidth,
            FloatValue,
            Clear,
            Position,
            Inset,
            AspectRatio,
            Border,
            AlignItems,
            AlignSelf,
            JustifyItems,
            JustifySelf,
            AlignContent,
            JustifyContent,
            ColumnGap,
            RowGap,
            TextAlign,
            FlexWrap,
            FlexBasis,
            GridTemplateRows,
            GridTemplateColumns,
            GridAutoRows,
            GridAutoColumns,
            GridAutoFlow,
            GridTemplateAreas,
            GridTemplateColumnNames,
            GridTemplateRowNames,
            GridRow,
            GridColumn,
        )

    private fun <T> layoutProperty(id: Int, name: String, initialValue: T): StyleProperty<T> =
        StyleProperty(id, name, initialValue, impact = StyleImpact.LAYOUT)
}

/** Marker for built-in or library-defined element states used by style conditions. */
public interface StyleState

/** Interaction and focus states maintained by the core. */
public enum class BuiltinStyleState : StyleState {
    HOVER,
    ACTIVE,
    FOCUS,
    FOCUS_VISIBLE,
    FOCUS_WITHIN,
    DISABLED,
}

/** Style state active while a pointing device hovers the element. */
public val HOVER: StyleState = BuiltinStyleState.HOVER

/** Style state active while the element is being pressed. */
public val ACTIVE: StyleState = BuiltinStyleState.ACTIVE

/** Style state active while the element owns input focus. */
public val FOCUS: StyleState = BuiltinStyleState.FOCUS

/** Focus state intended for visible keyboard-focus indication. */
public val FOCUS_VISIBLE: StyleState = BuiltinStyleState.FOCUS_VISIBLE

/** Style state active while the element or a descendant owns focus. */
public val FOCUS_WITHIN: StyleState = BuiltinStyleState.FOCUS_WITHIN

/** Style state active when interaction is disabled. */
public val DISABLED: StyleState = BuiltinStyleState.DISABLED

/** Predicate over the active style states of an element. */
public sealed interface StyleCondition {
    public val dependencies: Set<StyleState>

    public fun matches(states: Set<StyleState>): Boolean
}

private data class HasState(val state: StyleState) : StyleCondition {
    override val dependencies: Set<StyleState> = setOf(state)

    override fun matches(states: Set<StyleState>) = state in states
}

private data class All(val conditions: List<StyleCondition>) : StyleCondition {
    override val dependencies: Set<StyleState> =
        conditions.flatMapTo(linkedSetOf()) { it.dependencies }

    override fun matches(states: Set<StyleState>) = conditions.all { it.matches(states) }
}

private data class AnyCondition(val conditions: List<StyleCondition>) : StyleCondition {
    override val dependencies: Set<StyleState> =
        conditions.flatMapTo(linkedSetOf()) { it.dependencies }

    override fun matches(states: Set<StyleState>) = conditions.any { it.matches(states) }
}

private data class Not(val condition: StyleCondition) : StyleCondition {
    override val dependencies: Set<StyleState> = condition.dependencies

    override fun matches(states: Set<StyleState>) = !condition.matches(states)
}

/** Creates a condition that matches [state]. */
public fun condition(state: StyleState): StyleCondition = HasState(state)

/** Creates a condition requiring every supplied state. */
public fun all(vararg states: StyleState): StyleCondition = All(states.map(::HasState))

/** Creates a condition requiring at least one supplied state. */
public fun any(vararg states: StyleState): StyleCondition = AnyCondition(states.map(::HasState))

/** Creates a condition that excludes [state]. */
public fun not(state: StyleState): StyleCondition = Not(HasState(state))

/** One conditional assignment in a compiled style program. */
public data class StyleEntry(
    val property: StyleProperty<*>,
    val value: Any?,
    val condition: StyleCondition? = null,
)

/** Environment categories that can invalidate resolved styles. */
public enum class EnvironmentDependency {
    DP_UNITS,
    SP_UNITS,
    PHYSICAL_PX_UNITS,
}

/** Immutable compiled sequence of typed style assignments. */
public data class StyleProgram(
    val assignments: List<StyleEntry>,
    val previousAssignmentForSameProperty: IntArray,
    val lastAssignmentForProperty: IntArray,
    val writtenProperties: PropertyMask,
    val stateDependencies: Map<StyleState, PropertyMask>,
    val environmentDependencies: Map<EnvironmentDependency, PropertyMask>,
) {
    internal fun winner(property: StyleProperty<*>, states: Set<StyleState>): StyleWinner {
        var index = lastAssignmentForProperty[property.id]
        while (index >= 0) {
            val entry = assignments[index]
            if (entry.condition?.matches(states) != false) return StyleWinner(true, entry.value)
            index = previousAssignmentForSameProperty[index]
        }
        return StyleWinner(false, null)
    }

    internal companion object {
        fun compile(assignments: List<StyleEntry>): StyleProgram {
            val previous = IntArray(assignments.size) { -1 }
            val last = IntArray(Properties.all.size) { -1 }
            var written = PropertyMask.NONE
            val states = mutableMapOf<StyleState, PropertyMask>()
            val environment = mutableMapOf<EnvironmentDependency, PropertyMask>()
            assignments.forEachIndexed { index, entry ->
                previous[index] = last[entry.property.id]
                last[entry.property.id] = index
                written += entry.property.mask
                entry.condition?.dependencies?.forEach { state ->
                    states[state] = (states[state] ?: PropertyMask.NONE) + entry.property.mask
                }
                dependenciesOf(entry.value).forEach { dependency ->
                    environment[dependency] =
                        (environment[dependency] ?: PropertyMask.NONE) + entry.property.mask
                }
            }
            return StyleProgram(
                assignments.toList(),
                previous,
                last,
                written,
                states.toMap(),
                environment.toMap(),
            )
        }
    }
}

internal data class StyleWinner(val found: Boolean, val value: Any?)

/** Immutable style value produced by [style]. */
public class Style internal constructor(public val program: StyleProgram)

/** Type-safe DSL builder for immutable [Style] values. */
public class StyleBuilder internal constructor(private val condition: StyleCondition? = null) {
    private val entries = mutableListOf<StyleEntry>()

    /** Copies assignments from [style] into this builder. */
    public fun include(style: Style) {
        entries +=
            style.program.assignments.map {
                if (condition == null) it else it.copy(condition = condition)
            }
    }

    /** Assigns typed [value] to [property] under the current condition. */
    public fun <T> set(property: StyleProperty<T>, value: T) {
        entries += StyleEntry(property, value, condition)
    }

    /** Adds assignments active while [state] matches. */
    public fun on(state: StyleState, block: StyleBuilder.() -> Unit) = on(condition(state), block)

    /** Adds assignments active while [condition] matches. */
    public fun on(condition: StyleCondition, block: StyleBuilder.() -> Unit) {
        entries += StyleBuilder(condition).apply(block).entries
    }

    public var display: Display
        get() = error("write-only")
        set(value) = set(Properties.Display, value)

    public var width: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.Width, value)

    public var height: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.Height, value)

    public var minWidth: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.MinWidth, value)

    public var minHeight: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.MinHeight, value)

    public var maxWidth: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.MaxWidth, value)

    public var maxHeight: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.MaxHeight, value)

    public var padding: Edges<DimensionValue>
        get() = error("write-only")
        set(value) = set(Properties.Padding, value)

    public var margin: Edges<DimensionValue>
        get() = error("write-only")
        set(value) = set(Properties.Margin, value)

    public var gap: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.Gap, value)

    public var flexDirection: FlexDirection
        get() = error("write-only")
        set(value) = set(Properties.FlexDirection, value)

    public var flexGrow: Float
        get() = error("write-only")
        set(value) = set(Properties.FlexGrow, value)

    public var flexShrink: Float
        get() = error("write-only")
        set(value) = set(Properties.FlexShrink, value)

    public var overflow: Overflow
        get() = error("write-only")
        set(value) = set(Properties.Overflow, value)

    public var itemIsTable: Boolean
        get() = error("write-only")
        set(value) = set(Properties.ItemIsTable, value)

    public var itemIsReplaced: Boolean
        get() = error("write-only")
        set(value) = set(Properties.ItemIsReplaced, value)

    public var boxSizing: BoxSizing
        get() = error("write-only")
        set(value) = set(Properties.BoxSizing, value)

    public var direction: Direction
        get() = error("write-only")
        set(value) = set(Properties.Direction, value)

    public var overflowX: Overflow
        get() = error("write-only")
        set(value) = set(Properties.OverflowX, value)

    public var overflowY: Overflow
        get() = error("write-only")
        set(value) = set(Properties.OverflowY, value)

    public var scrollbarWidth: AbsoluteLengthValue
        get() = error("write-only")
        set(value) = set(Properties.ScrollbarWidth, value)

    public var floatValue: FloatLayout
        get() = error("write-only")
        set(value) = set(Properties.FloatValue, value)

    public var clear: Clear
        get() = error("write-only")
        set(value) = set(Properties.Clear, value)

    public var position: Position
        get() = error("write-only")
        set(value) = set(Properties.Position, value)

    public var inset: Edges<LengthPercentageAutoValue>
        get() = error("write-only")
        set(value) = set(Properties.Inset, value)

    public var aspectRatio: Float?
        get() = error("write-only")
        set(value) {
            require(value == null || value > 0f) { "Aspect ratio must be positive" }
            set(Properties.AspectRatio, value)
        }

    public var border: Edges<LengthPercentageValue>
        get() = error("write-only")
        set(value) = set(Properties.Border, value)

    public var alignItems: AlignItems?
        get() = error("write-only")
        set(value) = set(Properties.AlignItems, value)

    public var alignSelf: AlignItems?
        get() = error("write-only")
        set(value) = set(Properties.AlignSelf, value)

    public var justifyItems: AlignItems?
        get() = error("write-only")
        set(value) = set(Properties.JustifyItems, value)

    public var justifySelf: AlignItems?
        get() = error("write-only")
        set(value) = set(Properties.JustifySelf, value)

    public var alignContent: AlignContent?
        get() = error("write-only")
        set(value) = set(Properties.AlignContent, value)

    public var justifyContent: AlignContent?
        get() = error("write-only")
        set(value) = set(Properties.JustifyContent, value)

    public var columnGap: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.ColumnGap, value)

    public var rowGap: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.RowGap, value)

    public var textAlign: TextAlign
        get() = error("write-only")
        set(value) = set(Properties.TextAlign, value)

    public var flexWrap: FlexWrap
        get() = error("write-only")
        set(value) = set(Properties.FlexWrap, value)

    public var flexBasis: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.FlexBasis, value)

    public var gridTemplateRows: List<GridTemplateComponent>
        get() = error("write-only")
        set(value) = set(Properties.GridTemplateRows, value.toList())

    public var gridTemplateColumns: List<GridTemplateComponent>
        get() = error("write-only")
        set(value) = set(Properties.GridTemplateColumns, value.toList())

    public var gridAutoRows: List<GridTrackSizing>
        get() = error("write-only")
        set(value) = set(Properties.GridAutoRows, value.toList())

    public var gridAutoColumns: List<GridTrackSizing>
        get() = error("write-only")
        set(value) = set(Properties.GridAutoColumns, value.toList())

    public var gridAutoFlow: GridAutoFlow
        get() = error("write-only")
        set(value) = set(Properties.GridAutoFlow, value)

    public var gridTemplateAreas: GridTemplateAreas?
        get() = error("write-only")
        set(value) = set(Properties.GridTemplateAreas, value)

    public var gridTemplateColumnNames: List<List<String>>
        get() = error("write-only")
        set(value) = set(Properties.GridTemplateColumnNames, value.map(List<String>::toList))

    public var gridTemplateRowNames: List<List<String>>
        get() = error("write-only")
        set(value) = set(Properties.GridTemplateRowNames, value.map(List<String>::toList))

    public var gridRow: GridLine
        get() = error("write-only")
        set(value) = set(Properties.GridRow, value)

    public var gridColumn: GridLine
        get() = error("write-only")
        set(value) = set(Properties.GridColumn, value)

    public var background: Paint?
        get() = error("write-only")
        set(value) = set(Properties.Background, value)

    public var opacity: Float
        get() = error("write-only")
        set(value) = set(Properties.Opacity, value.coerceIn(0f, 1f))

    public var zIndex: Int
        get() = error("write-only")
        set(value) = set(Properties.ZIndex, value)

    public var visibility: Visibility
        get() = error("write-only")
        set(value) = set(Properties.Visibility, value)

    public var pointerEvents: PointerEvents
        get() = error("write-only")
        set(value) = set(Properties.PointerEvents, value)

    public var fontSize: DimensionValue
        get() = error("write-only")
        set(value) = set(Properties.FontSize, value)

    public var color: Paint
        get() = error("write-only")
        set(value) = set(Properties.Color, value)

    internal fun build(): Style = Style(StyleProgram.compile(entries))
}

/** Builds an immutable style. */
public fun style(block: StyleBuilder.() -> Unit): Style = StyleBuilder().apply(block).build()

/** Typed theme variable with a fallback value. */
public class StyleVar<T>(public val default: T)

/** Creates a typed theme variable with [default]. */
public fun <T> styleVar(default: T): StyleVar<T> = StyleVar(default)

/** Stable typed component skinning token. [name] is diagnostic only; identity is token identity. */
public class StylePart<T : Any>(public val name: String)

/** Immutable overrides for style variables and component parts. */
public class Theme
internal constructor(
    internal val values: Map<StyleVar<*>, Any?>,
    internal val parts: Map<StylePart<*>, Style>,
) {
    @Suppress("UNCHECKED_CAST")
    public operator fun <T> get(variable: StyleVar<T>): T =
        values.getOrDefault(variable, variable.default) as T

    public operator fun <T : Any> get(part: StylePart<T>): Style? = parts[part]
}

/** Builder for immutable [Theme] values. */
public class ThemeBuilder {
    private val values = mutableMapOf<StyleVar<*>, Any?>()
    private val parts = mutableMapOf<StylePart<*>, Style>()

    /** Overrides [variable] with [value]. */
    public fun <T> set(variable: StyleVar<T>, value: T) {
        values[variable] = value
    }

    /** Assigns [style] to a stable component [part]. */
    public fun <T : Any> style(part: StylePart<T>, style: Style) {
        parts[part] = style
    }

    internal fun build() = Theme(values.toMap(), parts.toMap())
}

/** Builds an immutable theme. */
public fun theme(block: ThemeBuilder.() -> Unit): Theme = ThemeBuilder().apply(block).build()

/** Resolved style values inherited by descendants. */
public data class InheritedValues(
    val visibility: Visibility,
    val fontSize: DimensionValue,
    val color: Paint,
)

/** Resolved dimensions and box-model values. */
public data class BoxLayoutValues(
    val display: Display,
    val width: DimensionValue,
    val height: DimensionValue,
    val minWidth: DimensionValue,
    val minHeight: DimensionValue,
    val maxWidth: DimensionValue,
    val maxHeight: DimensionValue,
    val padding: Edges<DimensionValue>,
    val margin: Edges<DimensionValue>,
    val gap: DimensionValue,
    val overflow: Overflow,
)

/** Resolved common flex and grid alignment values. */
public data class FlexGridValues(
    val direction: FlexDirection,
    val grow: Float,
    val shrink: Float,
)

/** Complete resolved layout projection consumed by Taffy4J. */
public data class TaffyLayoutValues(
    val itemIsTable: Boolean,
    val itemIsReplaced: Boolean,
    val boxSizing: BoxSizing,
    val direction: Direction,
    val overflowX: Overflow?,
    val overflowY: Overflow?,
    val scrollbarWidth: AbsoluteLengthValue,
    val floatValue: FloatLayout,
    val clear: Clear,
    val position: Position,
    val inset: Edges<LengthPercentageAutoValue>,
    val aspectRatio: Float?,
    val border: Edges<LengthPercentageValue>,
    val alignItems: AlignItems?,
    val alignSelf: AlignItems?,
    val justifyItems: AlignItems?,
    val justifySelf: AlignItems?,
    val alignContent: AlignContent?,
    val justifyContent: AlignContent?,
    val columnGap: DimensionValue?,
    val rowGap: DimensionValue?,
    val textAlign: TextAlign,
    val flexWrap: FlexWrap,
    val flexBasis: DimensionValue,
    val gridTemplateRows: List<GridTemplateComponent>,
    val gridTemplateColumns: List<GridTemplateComponent>,
    val gridAutoRows: List<GridTrackSizing>,
    val gridAutoColumns: List<GridTrackSizing>,
    val gridAutoFlow: GridAutoFlow,
    val gridTemplateAreas: GridTemplateAreas?,
    val gridTemplateColumnNames: List<List<String>>,
    val gridTemplateRowNames: List<List<String>>,
    val gridRow: GridLine,
    val gridColumn: GridLine,
)

/** Resolved paint values. */
public data class PaintValues(val background: Paint?)

/** Resolved compositing and stacking values. */
public data class RenderValues(val opacity: Float, val zIndex: Int)

/** Resolved hit-testing values. */
public data class InteractionValues(val pointerEvents: PointerEvents)

/** Fully resolved style grouped by runtime consumer. */
public class ResolvedStyle
internal constructor(
    public val inherited: InheritedValues,
    public val boxLayout: BoxLayoutValues,
    public val flexGrid: FlexGridValues,
    public val taffyLayout: TaffyLayoutValues,
    public val paint: PaintValues,
    public val render: RenderValues,
    public val interaction: InteractionValues,
) {
    public operator fun <T> get(property: StyleProperty<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (property.id) {
            GeneratedStylePropertyCatalog.DISPLAY -> boxLayout.display
            GeneratedStylePropertyCatalog.WIDTH -> boxLayout.width
            GeneratedStylePropertyCatalog.HEIGHT -> boxLayout.height
            GeneratedStylePropertyCatalog.MIN_WIDTH -> boxLayout.minWidth
            GeneratedStylePropertyCatalog.MIN_HEIGHT -> boxLayout.minHeight
            GeneratedStylePropertyCatalog.MAX_WIDTH -> boxLayout.maxWidth
            GeneratedStylePropertyCatalog.MAX_HEIGHT -> boxLayout.maxHeight
            GeneratedStylePropertyCatalog.PADDING -> boxLayout.padding
            GeneratedStylePropertyCatalog.MARGIN -> boxLayout.margin
            GeneratedStylePropertyCatalog.GAP -> boxLayout.gap
            GeneratedStylePropertyCatalog.FLEX_DIRECTION -> flexGrid.direction
            GeneratedStylePropertyCatalog.FLEX_GROW -> flexGrid.grow
            GeneratedStylePropertyCatalog.FLEX_SHRINK -> flexGrid.shrink
            GeneratedStylePropertyCatalog.OVERFLOW -> boxLayout.overflow
            GeneratedStylePropertyCatalog.BACKGROUND -> paint.background
            GeneratedStylePropertyCatalog.OPACITY -> render.opacity
            GeneratedStylePropertyCatalog.Z_INDEX -> render.zIndex
            GeneratedStylePropertyCatalog.VISIBILITY -> inherited.visibility
            GeneratedStylePropertyCatalog.POINTER_EVENTS -> interaction.pointerEvents
            GeneratedStylePropertyCatalog.FONT_SIZE -> inherited.fontSize
            GeneratedStylePropertyCatalog.COLOR -> inherited.color
            GeneratedStylePropertyCatalog.ITEM_IS_TABLE -> taffyLayout.itemIsTable
            GeneratedStylePropertyCatalog.ITEM_IS_REPLACED -> taffyLayout.itemIsReplaced
            GeneratedStylePropertyCatalog.BOX_SIZING -> taffyLayout.boxSizing
            GeneratedStylePropertyCatalog.DIRECTION -> taffyLayout.direction
            GeneratedStylePropertyCatalog.OVERFLOW_X -> taffyLayout.overflowX
            GeneratedStylePropertyCatalog.OVERFLOW_Y -> taffyLayout.overflowY
            GeneratedStylePropertyCatalog.SCROLLBAR_WIDTH -> taffyLayout.scrollbarWidth
            GeneratedStylePropertyCatalog.FLOAT_VALUE -> taffyLayout.floatValue
            GeneratedStylePropertyCatalog.CLEAR -> taffyLayout.clear
            GeneratedStylePropertyCatalog.POSITION -> taffyLayout.position
            GeneratedStylePropertyCatalog.INSET -> taffyLayout.inset
            GeneratedStylePropertyCatalog.ASPECT_RATIO -> taffyLayout.aspectRatio
            GeneratedStylePropertyCatalog.BORDER -> taffyLayout.border
            GeneratedStylePropertyCatalog.ALIGN_ITEMS -> taffyLayout.alignItems
            GeneratedStylePropertyCatalog.ALIGN_SELF -> taffyLayout.alignSelf
            GeneratedStylePropertyCatalog.JUSTIFY_ITEMS -> taffyLayout.justifyItems
            GeneratedStylePropertyCatalog.JUSTIFY_SELF -> taffyLayout.justifySelf
            GeneratedStylePropertyCatalog.ALIGN_CONTENT -> taffyLayout.alignContent
            GeneratedStylePropertyCatalog.JUSTIFY_CONTENT -> taffyLayout.justifyContent
            GeneratedStylePropertyCatalog.COLUMN_GAP -> taffyLayout.columnGap
            GeneratedStylePropertyCatalog.ROW_GAP -> taffyLayout.rowGap
            GeneratedStylePropertyCatalog.TEXT_ALIGN -> taffyLayout.textAlign
            GeneratedStylePropertyCatalog.FLEX_WRAP -> taffyLayout.flexWrap
            GeneratedStylePropertyCatalog.FLEX_BASIS -> taffyLayout.flexBasis
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_ROWS -> taffyLayout.gridTemplateRows
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_COLUMNS -> taffyLayout.gridTemplateColumns
            GeneratedStylePropertyCatalog.GRID_AUTO_ROWS -> taffyLayout.gridAutoRows
            GeneratedStylePropertyCatalog.GRID_AUTO_COLUMNS -> taffyLayout.gridAutoColumns
            GeneratedStylePropertyCatalog.GRID_AUTO_FLOW -> taffyLayout.gridAutoFlow
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_AREAS -> taffyLayout.gridTemplateAreas
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_COLUMN_NAMES ->
                taffyLayout.gridTemplateColumnNames
            GeneratedStylePropertyCatalog.GRID_TEMPLATE_ROW_NAMES ->
                taffyLayout.gridTemplateRowNames
            GeneratedStylePropertyCatalog.GRID_ROW -> taffyLayout.gridRow
            GeneratedStylePropertyCatalog.GRID_COLUMN -> taffyLayout.gridColumn
            else -> error("Unknown style property ${property.name}")
        }
            as T
    }

    internal fun withUntyped(property: StyleProperty<*>, value: Any?): ResolvedStyle {
        val values =
            Properties.all.associateWith { candidate ->
                if (candidate === property) value else getUntyped(candidate)
            }
        return from(values, this)
    }

    private fun getUntyped(property: StyleProperty<*>): Any? {
        @Suppress("UNCHECKED_CAST")
        return this[property as StyleProperty<Any?>]
    }

    internal companion object {
        fun from(values: Map<StyleProperty<*>, Any?>, previous: ResolvedStyle?): ResolvedStyle {
            fun <T> value(property: StyleProperty<T>): T {
                @Suppress("UNCHECKED_CAST")
                return (if (values.containsKey(property)) values[property]
                else property.initialValue)
                    as T
            }

            fun <T> share(old: T?, next: T): T = if (old == next) old else next
            val inherited =
                share(
                    previous?.inherited,
                    InheritedValues(
                        value(Properties.Visibility),
                        value(Properties.FontSize),
                        value(Properties.Color),
                    ),
                )
            val box =
                share(
                    previous?.boxLayout,
                    BoxLayoutValues(
                        value(Properties.Display),
                        value(Properties.Width),
                        value(Properties.Height),
                        value(Properties.MinWidth),
                        value(Properties.MinHeight),
                        value(Properties.MaxWidth),
                        value(Properties.MaxHeight),
                        value(Properties.Padding),
                        value(Properties.Margin),
                        value(Properties.Gap),
                        value(Properties.Overflow),
                    ),
                )
            val flex =
                share(
                    previous?.flexGrid,
                    FlexGridValues(
                        value(Properties.FlexDirection),
                        value(Properties.FlexGrow),
                        value(Properties.FlexShrink),
                    ),
                )
            val taffy =
                share(
                    previous?.taffyLayout,
                    TaffyLayoutValues(
                        value(Properties.ItemIsTable),
                        value(Properties.ItemIsReplaced),
                        value(Properties.BoxSizing),
                        value(Properties.Direction),
                        value(Properties.OverflowX),
                        value(Properties.OverflowY),
                        value(Properties.ScrollbarWidth),
                        value(Properties.FloatValue),
                        value(Properties.Clear),
                        value(Properties.Position),
                        value(Properties.Inset),
                        value(Properties.AspectRatio),
                        value(Properties.Border),
                        value(Properties.AlignItems),
                        value(Properties.AlignSelf),
                        value(Properties.JustifyItems),
                        value(Properties.JustifySelf),
                        value(Properties.AlignContent),
                        value(Properties.JustifyContent),
                        value(Properties.ColumnGap),
                        value(Properties.RowGap),
                        value(Properties.TextAlign),
                        value(Properties.FlexWrap),
                        value(Properties.FlexBasis),
                        value(Properties.GridTemplateRows),
                        value(Properties.GridTemplateColumns),
                        value(Properties.GridAutoRows),
                        value(Properties.GridAutoColumns),
                        value(Properties.GridAutoFlow),
                        value(Properties.GridTemplateAreas),
                        value(Properties.GridTemplateColumnNames),
                        value(Properties.GridTemplateRowNames),
                        value(Properties.GridRow),
                        value(Properties.GridColumn),
                    ),
                )
            val paint = share(previous?.paint, PaintValues(value(Properties.Background)))
            val render =
                share(
                    previous?.render,
                    RenderValues(value(Properties.Opacity), value(Properties.ZIndex)),
                )
            val interaction =
                share(
                    previous?.interaction,
                    InteractionValues(value(Properties.PointerEvents)),
                )
            return ResolvedStyle(inherited, box, flex, taffy, paint, render, interaction)
        }
    }
}

/** Property and impact masks produced while resolving a style update. */
public data class StyleChangeSet(
    val impact: StyleImpact,
    val changedProperties: Set<StyleProperty<*>>,
)

private data class ElementStyleState(
    val sources: MutableList<Readable<Style>>,
    val states: MutableSet<StyleState>,
    var resolved: ResolvedStyle?,
    var part: PartStyleState? = null,
    val partOverrides: MutableMap<StylePart<*>, Readable<Style>> = mutableMapOf(),
)

private data class PartStyleState(
    val owner: Element,
    val token: StylePart<*>,
    val structural: Readable<Style>,
)

private val styleStateKey = AttachmentKey<ElementStyleState>()
private val themeKey = AttachmentKey<Readable<Theme>>()

/** Attaches reactive styles, maintains states, and resolves effective target values. */
public class StyleRuntime {
    /** Attaches a reactive style [source] to [element]. */
    public fun attach(element: Element, source: Readable<Style>) {
        state(element).sources += source
    }

    /** Installs a theme boundary inherited by this element and descendants. */
    public fun attachTheme(element: Element, source: Readable<Theme>) {
        element.attach(themeKey, source)
    }

    /** Registers [element] as persistent visual [part] owned by [owner]. */
    public fun attachPart(
        owner: Element,
        element: Element,
        part: StylePart<*>,
        structuralStyle: Readable<Style>,
    ) {
        state(element).part = PartStyleState(owner, part, structuralStyle)
    }

    /** Sets component-instance styling for one typed visual [part]. */
    public fun attachPartStyle(
        owner: Element,
        part: StylePart<*>,
        source: Readable<Style>,
    ) {
        state(owner).partOverrides[part] = source
    }

    /** Enables or disables [styleState] for [element]. */
    public fun setState(element: Element, styleState: StyleState, enabled: Boolean) {
        if (enabled) state(element).states += styleState else state(element).states -= styleState
    }

    /** Resolves [element] and returns effective target values plus incremental changes. */
    public fun resolve(element: Element): Pair<ResolvedStyle, StyleChangeSet> {
        val state = state(element)
        val values = mutableMapOf<StyleProperty<*>, Any?>()
        val parent = element.parent?.attachment(styleStateKey)?.resolved
        Properties.all
            .filter { it.inherited }
            .forEach { property -> parent?.let { values[property] = getUntyped(it, property) } }
        val part = state.part
        val activeStates = part?.let { state(it.owner).states } ?: state.states
        val layeredSources = buildList {
            part?.let { metadata ->
                add(metadata.structural)
                nearestTheme(metadata.owner)?.get(metadata.token)?.let { add(fixedStyle(it)) }
                state(metadata.owner).partOverrides[metadata.token]?.let(::add)
            }
            addAll(state.sources)
        }
        Properties.all.forEach { property ->
            layeredSources
                .asReversed()
                .firstNotNullOfOrNull { source ->
                    source.value.program.winner(property, activeStates).takeIf(StyleWinner::found)
                }
                ?.let { winner -> values[property] = winner.value }
        }
        val previous = state.resolved
        val next = ResolvedStyle.from(values, previous)
        val changed =
            Properties.all.filterTo(linkedSetOf()) {
                previous == null || getUntyped(previous, it) != getUntyped(next, it)
            }
        state.resolved = next
        return next to
            StyleChangeSet(
                changed.fold(StyleImpact.NONE) { impact, property -> impact + property.impact },
                changed,
            )
    }

    private fun state(element: Element): ElementStyleState =
        element.attachment(styleStateKey)
            ?: ElementStyleState(mutableListOf(), mutableSetOf(), null).also {
                element.attach(styleStateKey, it)
            }

    private fun nearestTheme(element: Element): Theme? =
        generateSequence(element) { it.parent }
            .firstNotNullOfOrNull { it.attachment(themeKey)?.value }

    private fun fixedStyle(value: Style): Readable<Style> =
        object : Readable<Style> {
            override val value: Style = value
        }

    private fun getUntyped(style: ResolvedStyle, property: StyleProperty<*>): Any? {
        @Suppress("UNCHECKED_CAST")
        return style[property as StyleProperty<Any?>]
    }
}

private fun dependenciesOf(value: Any?): Set<EnvironmentDependency> =
    when (value) {
        is Dp -> setOf(EnvironmentDependency.DP_UNITS)
        is Sp -> setOf(EnvironmentDependency.SP_UNITS)
        is PhysicalPx -> setOf(EnvironmentDependency.PHYSICAL_PX_UNITS)
        is Calc -> value.terms.flatMapTo(linkedSetOf()) { dependenciesOf(it.second) }
        is Edges<*> ->
            listOf(value.top, value.right, value.bottom, value.left).flatMapTo(linkedSetOf()) {
                dependenciesOf(it)
            }
        is GridMinTrackSizing.Fixed -> dependenciesOf(value.value)
        is GridMaxTrackSizing.Fixed -> dependenciesOf(value.value)
        is GridMaxTrackSizing.FitContent -> dependenciesOf(value.limit)
        is GridTrackSizing -> dependenciesOf(value.min) + dependenciesOf(value.max)
        is GridTemplateComponent.Single -> dependenciesOf(value.track)
        is GridTemplateComponent.Repeat -> dependenciesOf(value.tracks)
        is Iterable<*> -> value.flatMapTo(linkedSetOf(), ::dependenciesOf)
        else -> emptySet()
    }

/** Resolves a typed dimension into logical pixels for [environment]. */
public fun resolveLength(
    value: DimensionValue,
    environment: UiEnvironment,
    units: UnitResolver,
    basis: Float = 0f,
): Float? =
    when (value) {
        is Px -> value.value
        is Dp -> units.resolveDp(value.value, environment)
        is Sp -> units.resolveSp(value.value, environment)
        is PhysicalPx -> units.resolvePhysicalPx(value.value, environment)
        is Percent -> value.fraction * basis
        is Calc ->
            value.terms
                .sumOf { (coefficient, term) ->
                    coefficient.toDouble() * (resolveLength(term, environment, units, basis) ?: 0f)
                }
                .toFloat()
        Auto -> null
    }

/** Named inset group exposed to styles and higher-level components. */
public enum class EnvironmentInset {
    SYSTEM_BARS,
    DISPLAY_CUTOUT,
    IME,
    SYSTEM_GESTURES,
    SAFE_DRAWING,
    SAFE_GESTURES,
    SAFE_CONTENT,
}

/** Returns the current inset values represented by [type]. */
public fun environmentInsets(type: EnvironmentInset, environment: UiEnvironment): Insets =
    when (type) {
        EnvironmentInset.SYSTEM_BARS -> environment.insets.systemBars
        EnvironmentInset.DISPLAY_CUTOUT -> environment.insets.displayCutout
        EnvironmentInset.IME -> environment.insets.ime
        EnvironmentInset.SYSTEM_GESTURES -> environment.insets.systemGestures
        EnvironmentInset.SAFE_DRAWING -> environment.insets.safeDrawing
        EnvironmentInset.SAFE_GESTURES -> environment.insets.safeGestures
        EnvironmentInset.SAFE_CONTENT -> environment.insets.safeContent
    }
