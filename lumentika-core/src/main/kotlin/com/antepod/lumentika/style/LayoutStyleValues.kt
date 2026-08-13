package com.antepod.lumentika.style

/** Values mirroring Taffy4J layout concepts without leaking Taffy types into public API. */
public enum class BoxSizing {
    BORDER_BOX,
    CONTENT_BOX,
}

public enum class Direction {
    LTR,
    RTL,
}

public enum class FloatLayout {
    LEFT,
    RIGHT,
    NONE,
}

public enum class Clear {
    LEFT,
    RIGHT,
    BOTH,
    NONE,
}

public enum class AlignItems {
    START,
    END,
    FLEX_START,
    FLEX_END,
    SELF_START,
    SELF_END,
    CENTER,
    BASELINE,
    STRETCH,
    SAFE_START,
    SAFE_END,
    SAFE_FLEX_START,
    SAFE_FLEX_END,
    SAFE_SELF_START,
    SAFE_SELF_END,
    SAFE_CENTER,
}

public enum class AlignContent {
    START,
    END,
    FLEX_START,
    FLEX_END,
    CENTER,
    STRETCH,
    SPACE_BETWEEN,
    SPACE_EVENLY,
    SPACE_AROUND,
    SAFE_START,
    SAFE_END,
    SAFE_FLEX_START,
    SAFE_FLEX_END,
    SAFE_CENTER,
}

public enum class TextAlign {
    AUTO,
    LEGACY_LEFT,
    LEGACY_RIGHT,
    LEGACY_CENTER,
}

public enum class GridAutoFlow {
    ROW,
    COLUMN,
    ROW_DENSE,
    COLUMN_DENSE,
}

public sealed interface GridMinTrackSizing {
    public data object Auto : GridMinTrackSizing

    public data object MinContent : GridMinTrackSizing

    public data object MaxContent : GridMinTrackSizing

    public data class Fixed(val value: LengthPercentageValue) : GridMinTrackSizing
}

public sealed interface GridMaxTrackSizing {
    public data object Auto : GridMaxTrackSizing

    public data object MinContent : GridMaxTrackSizing

    public data object MaxContent : GridMaxTrackSizing

    public data class Fixed(val value: LengthPercentageValue) : GridMaxTrackSizing

    public data class Fraction(val value: Float) : GridMaxTrackSizing {
        init {
            require(value >= 0f) { "Grid fraction must be non-negative" }
        }
    }

    public data class FitContent(val limit: LengthPercentageValue) : GridMaxTrackSizing
}

public data class GridTrackSizing(
    val min: GridMinTrackSizing,
    val max: GridMaxTrackSizing,
) {
    public companion object {
        public val Auto: GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.Auto, GridMaxTrackSizing.Auto)
        public val MinContent: GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.MinContent, GridMaxTrackSizing.MinContent)
        public val MaxContent: GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.MaxContent, GridMaxTrackSizing.MaxContent)

        public fun fixed(value: LengthPercentageValue): GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.Fixed(value), GridMaxTrackSizing.Fixed(value))

        /** CSS fr semantics: automatic minimum, fractional maximum. */
        public fun fraction(value: Number): GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.Auto, GridMaxTrackSizing.Fraction(value.toFloat()))

        /** Taffy flex() semantics: zero minimum, fractional maximum. */
        public fun flex(value: Number): GridTrackSizing =
            GridTrackSizing(
                GridMinTrackSizing.Fixed(0.px),
                GridMaxTrackSizing.Fraction(value.toFloat()),
            )

        public fun fitContent(limit: LengthPercentageValue): GridTrackSizing =
            GridTrackSizing(GridMinTrackSizing.Auto, GridMaxTrackSizing.FitContent(limit))

        public fun minmax(
            min: GridMinTrackSizing,
            max: GridMaxTrackSizing,
        ): GridTrackSizing = GridTrackSizing(min, max)
    }
}

public sealed interface GridRepetition {
    public data class Count(val count: Int) : GridRepetition {
        init {
            require(count in 0..0xffff) { "Grid repetition count must fit u16" }
        }
    }

    public data object AutoFill : GridRepetition

    public data object AutoFit : GridRepetition
}

public sealed interface GridTemplateComponent {
    public data class Single(val track: GridTrackSizing) : GridTemplateComponent

    public data class Repeat(
        val repetition: GridRepetition,
        val tracks: List<GridTrackSizing>,
        val lineNames: List<List<String>> = emptyList(),
    ) : GridTemplateComponent {
        init {
            require(tracks.isNotEmpty()) { "Grid repetition requires at least one track" }
        }
    }
}

public sealed interface GridPlacement {
    public data object Auto : GridPlacement

    public data class Line(val index: Int, val name: String? = null) : GridPlacement {
        init {
            require(index in Short.MIN_VALUE..Short.MAX_VALUE) { "Grid line index must fit i16" }
        }
    }

    public data class Span(val count: Int, val name: String? = null) : GridPlacement {
        init {
            require(count in 0..0xffff) { "Grid span must fit u16" }
        }
    }
}

public data class GridLine(
    val start: GridPlacement = GridPlacement.Auto,
    val end: GridPlacement = GridPlacement.Auto,
)

public data class GridTemplateArea(
    val name: String,
    val rowStart: Int,
    val rowEnd: Int,
    val columnStart: Int,
    val columnEnd: Int,
)

public data class GridTemplateAreas(
    val areas: List<GridTemplateArea>,
    val rowCount: Int,
    val columnCount: Int,
) {
    init {
        require(rowCount >= 0) { "Grid template row count must be non-negative" }
        require(columnCount >= 0) { "Grid template column count must be non-negative" }
    }
}
