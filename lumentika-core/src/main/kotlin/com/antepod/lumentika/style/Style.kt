package com.antepod.lumentika.style

import com.antepod.lumentika.geometry.Insets
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.platform.UnitResolver
import com.antepod.lumentika.reactive.Readable
import com.antepod.lumentika.runtime.AttachmentKey
import com.antepod.lumentika.runtime.Element

public sealed interface DimensionValue

public sealed interface LengthPercentageValue

public sealed interface LengthPercentageAutoValue

public sealed interface AbsoluteLengthValue

public data class Px(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

public data class Dp(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

public data class Sp(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

public data class PhysicalPx(val value: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue, AbsoluteLengthValue

public data class Percent(val fraction: Float) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue

public data object Auto : DimensionValue, LengthPercentageAutoValue

public data class Calc(val terms: List<Pair<Float, DimensionValue>>) :
    DimensionValue, LengthPercentageValue, LengthPercentageAutoValue

public val Number.px: Px
    get() = Px(toFloat())
public val Number.dp: Dp
    get() = Dp(toFloat())
public val Number.sp: Sp
    get() = Sp(toFloat())
public val Number.physicalPx: PhysicalPx
    get() = PhysicalPx(toFloat())
public val Number.percent: Percent
    get() = Percent(toFloat() / 100f)

public fun auto(): Auto = Auto

public data class Edges<T>(val top: T, val right: T, val bottom: T, val left: T)

public fun <T> edges(all: T): Edges<T> = Edges(all, all, all, all)

public fun <T> edges(vertical: T, horizontal: T): Edges<T> =
    Edges(vertical, horizontal, vertical, horizontal)

public fun <T> edges(top: T, right: T, bottom: T, left: T): Edges<T> =
    Edges(top, right, bottom, left)

public sealed interface Paint

public data class SolidPaint(val argb: Int) : Paint

public data class GradientStop(val offset: Float, val color: Int)

public data class LinearGradientPaint(val angleDegrees: Float, val stops: List<GradientStop>) :
    Paint

public data class RadialGradientPaint(val stops: List<GradientStop>) : Paint

public data class ImagePaint(val source: String) : Paint

public data class LayeredPaint(val layers: List<Paint>) : Paint

public fun rgb(red: Int, green: Int, blue: Int, alpha: Int = 255): SolidPaint =
    SolidPaint(
        (alpha.coerceIn(0, 255) shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    )

public enum class Display {
    NONE,
    BLOCK,
    FLEX,
    GRID,
}

public enum class Position {
    RELATIVE,
    ABSOLUTE,
}

public enum class Overflow {
    VISIBLE,
    CLIP,
    HIDDEN,
    SCROLL,
    AUTO,
}

public enum class FlexDirection {
    ROW,
    ROW_REVERSE,
    COLUMN,
    COLUMN_REVERSE,
}

public enum class FlexWrap {
    NO_WRAP,
    WRAP,
    WRAP_REVERSE,
}

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

public enum class Visibility {
    VISIBLE,
    HIDDEN,
}

public enum class PointerEvents {
    AUTO,
    NONE,
}

@JvmInline
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
public value class PropertyMask(public val bits: Long) {
    public operator fun plus(other: PropertyMask): PropertyMask = PropertyMask(bits or other.bits)

    public operator fun contains(property: StyleProperty<*>): Boolean =
        bits and property.mask.bits != 0L

    public companion object {
        public val NONE: PropertyMask = PropertyMask(0)
    }
}

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
        )
}

public interface StyleState

public enum class BuiltinStyleState : StyleState {
    HOVER,
    ACTIVE,
    FOCUS,
    FOCUS_VISIBLE,
    FOCUS_WITHIN,
    DISABLED,
}

public val HOVER: StyleState = BuiltinStyleState.HOVER
public val ACTIVE: StyleState = BuiltinStyleState.ACTIVE
public val FOCUS: StyleState = BuiltinStyleState.FOCUS
public val FOCUS_VISIBLE: StyleState = BuiltinStyleState.FOCUS_VISIBLE
public val FOCUS_WITHIN: StyleState = BuiltinStyleState.FOCUS_WITHIN
public val DISABLED: StyleState = BuiltinStyleState.DISABLED

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

public fun condition(state: StyleState): StyleCondition = HasState(state)

public fun all(vararg states: StyleState): StyleCondition = All(states.map(::HasState))

public fun any(vararg states: StyleState): StyleCondition = AnyCondition(states.map(::HasState))

public fun not(state: StyleState): StyleCondition = Not(HasState(state))

public data class StyleEntry(
    val property: StyleProperty<*>,
    val value: Any?,
    val condition: StyleCondition? = null,
)

public enum class EnvironmentDependency {
    DP_UNITS,
    SP_UNITS,
    PHYSICAL_PX_UNITS,
}

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

public class Style internal constructor(public val program: StyleProgram)

public class StyleBuilder internal constructor(private val condition: StyleCondition? = null) {
    private val entries = mutableListOf<StyleEntry>()

    public fun include(style: Style) {
        entries +=
            style.program.assignments.map {
                if (condition == null) it else it.copy(condition = condition)
            }
    }

    public fun <T> set(property: StyleProperty<T>, value: T) {
        entries += StyleEntry(property, value, condition)
    }

    public fun on(state: StyleState, block: StyleBuilder.() -> Unit) = on(condition(state), block)

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

public fun style(block: StyleBuilder.() -> Unit): Style = StyleBuilder().apply(block).build()

public class StyleVar<T>(public val default: T)

public fun <T> styleVar(default: T): StyleVar<T> = StyleVar(default)

public class StylePart<T : Any>(public val name: String)

public class Theme
internal constructor(
    internal val values: Map<StyleVar<*>, Any?>,
    internal val parts: Map<StylePart<*>, Style>,
)

public class ThemeBuilder {
    private val values = mutableMapOf<StyleVar<*>, Any?>()
    private val parts = mutableMapOf<StylePart<*>, Style>()

    public fun <T> set(variable: StyleVar<T>, value: T) {
        values[variable] = value
    }

    public fun <T : Any> style(part: StylePart<T>, style: Style) {
        parts[part] = style
    }

    internal fun build() = Theme(values.toMap(), parts.toMap())
}

public fun theme(block: ThemeBuilder.() -> Unit): Theme = ThemeBuilder().apply(block).build()

public data class InheritedValues(
    val visibility: Visibility,
    val fontSize: DimensionValue,
    val color: Paint,
)

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

public data class FlexGridValues(
    val direction: FlexDirection,
    val grow: Float,
    val shrink: Float,
)

public data class PaintValues(val background: Paint?)

public data class RenderValues(val opacity: Float, val zIndex: Int)

public data class InteractionValues(val pointerEvents: PointerEvents)

public class ResolvedStyle
internal constructor(
    public val inherited: InheritedValues,
    public val boxLayout: BoxLayoutValues,
    public val flexGrid: FlexGridValues,
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
            return ResolvedStyle(inherited, box, flex, paint, render, interaction)
        }
    }
}

public data class StyleChangeSet(
    val impact: StyleImpact,
    val changedProperties: Set<StyleProperty<*>>,
)

private data class ElementStyleState(
    val sources: MutableList<Readable<Style>>,
    val states: MutableSet<StyleState>,
    var resolved: ResolvedStyle?,
)

private val styleStateKey = AttachmentKey<ElementStyleState>()

public class StyleRuntime {
    public fun attach(element: Element, source: Readable<Style>) {
        state(element).sources += source
    }

    public fun setState(element: Element, styleState: StyleState, enabled: Boolean) {
        if (enabled) state(element).states += styleState else state(element).states -= styleState
    }

    public fun resolve(element: Element): Pair<ResolvedStyle, StyleChangeSet> {
        val state = state(element)
        val values = mutableMapOf<StyleProperty<*>, Any?>()
        val parent = element.parent?.attachment(styleStateKey)?.resolved
        Properties.all
            .filter { it.inherited }
            .forEach { property -> parent?.let { values[property] = getUntyped(it, property) } }
        Properties.all.forEach { property ->
            state.sources
                .asReversed()
                .firstNotNullOfOrNull { source ->
                    source.value.program.winner(property, state.states).takeIf(StyleWinner::found)
                }
                ?.let { winner ->
                    values[property] = winner.value
                }
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
        else -> emptySet()
    }

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

public enum class EnvironmentInset {
    SYSTEM_BARS,
    DISPLAY_CUTOUT,
    IME,
    SYSTEM_GESTURES,
    SAFE_DRAWING,
    SAFE_GESTURES,
    SAFE_CONTENT,
}

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
