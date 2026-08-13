package com.antepod.lumentika.geometry

/** A point in logical UI coordinates. */
public data class Point(public val x: Float, public val y: Float) {
    public operator fun plus(other: Point): Point = Point(x + other.x, y + other.y)

    public operator fun minus(other: Point): Point = Point(x - other.x, y - other.y)
}

/** A two-dimensional logical size. */
public data class Size(public val width: Float, public val height: Float) {
    public companion object {
        public val ZERO: Size = Size(0f, 0f)
    }
}

/** Geometry that can clip rendering and hit testing in local coordinates. */
public interface ClipShape {
    /** Returns whether [point] lies inside visible shape area. */
    public fun contains(point: Point): Boolean

    /** Conservative local axis-aligned bounds. */
    public val bounds: Rect
}

/** An axis-aligned rectangle in logical UI coordinates. */
public data class Rect(
    public val x: Float,
    public val y: Float,
    public val width: Float,
    public val height: Float,
) : ClipShape {
    public val right: Float
        get() = x + width

    public val bottom: Float
        get() = y + height

    override fun contains(point: Point): Boolean =
        point.x >= x && point.x <= right && point.y >= y && point.y <= bottom

    override val bounds: Rect
        get() = this

    public fun intersect(other: Rect): Rect? {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(right, other.right)
        val bottom = minOf(bottom, other.bottom)
        return if (right >= left && bottom >= top) Rect(left, top, right - left, bottom - top)
        else null
    }
}

/** Elliptical radius of one rounded corner. */
public data class CornerRadius(public val x: Float, public val y: Float = x) {
    init {
        require(x.isFinite() && x >= 0f)
        require(y.isFinite() && y >= 0f)
    }
}

/** Radii of rectangle corners in clockwise order. */
public data class CornerRadii(
    public val topLeft: CornerRadius = CornerRadius(0f),
    public val topRight: CornerRadius = CornerRadius(0f),
    public val bottomRight: CornerRadius = CornerRadius(0f),
    public val bottomLeft: CornerRadius = CornerRadius(0f),
) {
    public constructor(
        all: Float
    ) : this(CornerRadius(all), CornerRadius(all), CornerRadius(all), CornerRadius(all))

    public val isEmpty: Boolean
        get() = listOf(topLeft, topRight, bottomRight, bottomLeft).all { it.x == 0f && it.y == 0f }
}

/** Rounded rectangle shared by drawing, clipping, and hit testing. */
public data class RoundedRect(public val rect: Rect, public val radii: CornerRadii) : ClipShape {
    override val bounds: Rect
        get() = rect

    override fun contains(point: Point): Boolean {
        if (!rect.contains(point)) return false
        val normalized = normalizedRadii(rect, radii)
        return cornerContains(point, rect.x, rect.y, normalized.topLeft, true, true) &&
            cornerContains(point, rect.right, rect.y, normalized.topRight, false, true) &&
            cornerContains(point, rect.right, rect.bottom, normalized.bottomRight, false, false) &&
            cornerContains(point, rect.x, rect.bottom, normalized.bottomLeft, true, false)
    }
}

/** Winding rule used to determine filled path interior. */
public enum class PathFillRule {
    NON_ZERO,
    EVEN_ODD,
}

/** Immutable vector-path segment. */
public sealed interface PathSegment {
    public data class MoveTo(val point: Point) : PathSegment

    public data class LineTo(val point: Point) : PathSegment

    public data class QuadraticTo(val control: Point, val end: Point) : PathSegment

    public data class CubicTo(val control1: Point, val control2: Point, val end: Point) :
        PathSegment

    public data object Close : PathSegment
}

/** Immutable arbitrary vector geometry shared by drawing and clipping. */
public data class Path(
    public val segments: List<PathSegment>,
    public val fillRule: PathFillRule = PathFillRule.NON_ZERO,
) : ClipShape {
    init {
        require(segments.isNotEmpty()) { "Path requires at least one segment" }
        require(segments.first() is PathSegment.MoveTo) { "Path must begin with MoveTo" }
    }

    private val contours: List<List<Point>> by lazy { flattenContours() }

    override val bounds: Rect by lazy {
        val points = contours.flatten()
        Rect(
            points.minOf(Point::x),
            points.minOf(Point::y),
            points.maxOf(Point::x) - points.minOf(Point::x),
            points.maxOf(Point::y) - points.minOf(Point::y),
        )
    }

    override fun contains(point: Point): Boolean {
        var winding = 0
        contours.forEach { contour ->
            if (contour.size < 3) return@forEach
            contour.indices.forEach { index ->
                val a = contour[index]
                val b = contour[(index + 1) % contour.size]
                if ((a.y > point.y) != (b.y > point.y)) {
                    val intersection = (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                    if (point.x < intersection) {
                        if (fillRule == PathFillRule.EVEN_ODD) winding++
                        else winding += if (b.y > a.y) 1 else -1
                    }
                }
            }
        }
        return if (fillRule == PathFillRule.EVEN_ODD) winding % 2 != 0 else winding != 0
    }

    private fun flattenContours(): List<List<Point>> {
        val result = mutableListOf<MutableList<Point>>()
        var current = Point(0f, 0f)
        var start = current
        fun contour(): MutableList<Point> =
            result.lastOrNull() ?: mutableListOf<Point>().also(result::add)
        segments.forEach { segment ->
            when (segment) {
                is PathSegment.MoveTo -> {
                    current = segment.point
                    start = current
                    result += mutableListOf(current)
                }
                is PathSegment.LineTo -> {
                    current = segment.point
                    contour() += current
                }
                is PathSegment.QuadraticTo -> {
                    val from = current
                    (1..16).forEach { step ->
                        val t = step / 16f
                        val u = 1f - t
                        contour() +=
                            Point(
                                u * u * from.x +
                                    2f * u * t * segment.control.x +
                                    t * t * segment.end.x,
                                u * u * from.y +
                                    2f * u * t * segment.control.y +
                                    t * t * segment.end.y,
                            )
                    }
                    current = segment.end
                }
                is PathSegment.CubicTo -> {
                    val from = current
                    (1..24).forEach { step ->
                        val t = step / 24f
                        val u = 1f - t
                        contour() +=
                            Point(
                                u * u * u * from.x +
                                    3f * u * u * t * segment.control1.x +
                                    3f * u * t * t * segment.control2.x +
                                    t * t * t * segment.end.x,
                                u * u * u * from.y +
                                    3f * u * u * t * segment.control1.y +
                                    3f * u * t * t * segment.control2.y +
                                    t * t * t * segment.end.y,
                            )
                    }
                    current = segment.end
                }
                PathSegment.Close -> {
                    if (contour().lastOrNull() != start) contour() += start
                    current = start
                }
            }
        }
        return result
    }
}

private fun normalizedRadii(rect: Rect, value: CornerRadii): CornerRadii {
    val horizontal =
        maxOf(
            value.topLeft.x + value.topRight.x,
            value.bottomLeft.x + value.bottomRight.x,
        )
    val vertical =
        maxOf(
            value.topLeft.y + value.bottomLeft.y,
            value.topRight.y + value.bottomRight.y,
        )
    val scale =
        minOf(
            1f,
            if (horizontal > 0f) rect.width / horizontal else 1f,
            if (vertical > 0f) rect.height / vertical else 1f,
        )
    fun CornerRadius.scaled() = CornerRadius(x * scale, y * scale)
    return CornerRadii(
        value.topLeft.scaled(),
        value.topRight.scaled(),
        value.bottomRight.scaled(),
        value.bottomLeft.scaled(),
    )
}

private fun cornerContains(
    point: Point,
    cornerX: Float,
    cornerY: Float,
    radius: CornerRadius,
    left: Boolean,
    top: Boolean,
): Boolean {
    if (radius.x == 0f || radius.y == 0f) return true
    val centerX = cornerX + if (left) radius.x else -radius.x
    val centerY = cornerY + if (top) radius.y else -radius.y
    val inCornerX = if (left) point.x < centerX else point.x > centerX
    val inCornerY = if (top) point.y < centerY else point.y > centerY
    if (!inCornerX || !inCornerY) return true
    val dx = (point.x - centerX) / radius.x
    val dy = (point.y - centerY) / radius.y
    return dx * dx + dy * dy <= 1f
}

/** Insets measured inward from the four edges of a rectangle. */
public data class Insets(
    public val left: Float = 0f,
    public val top: Float = 0f,
    public val right: Float = 0f,
    public val bottom: Float = 0f,
) {
    public operator fun plus(other: Insets): Insets =
        Insets(
            left + other.left,
            top + other.top,
            right + other.right,
            bottom + other.bottom,
        )
}

/** An immutable 3×3 matrix used for two-dimensional affine transforms. */
public data class Matrix3(public val values: List<Float>) {
    init {
        require(values.size == 9) { "Matrix3 requires 9 values" }
    }

    public fun transform(point: Point): Point {
        val x = values[0] * point.x + values[1] * point.y + values[2]
        val y = values[3] * point.x + values[4] * point.y + values[5]
        val w = values[6] * point.x + values[7] * point.y + values[8]
        return Point(x / w, y / w)
    }

    public operator fun times(other: Matrix3): Matrix3 =
        Matrix3(
            List(9) { index ->
                val row = index / 3
                val column = index % 3
                (0..2)
                    .sumOf { values[row * 3 + it].toDouble() * other.values[it * 3 + column] }
                    .toFloat()
            }
        )

    public fun inverse(): Matrix3? {
        val a = values[0]
        val b = values[1]
        val c = values[2]
        val d = values[3]
        val e = values[4]
        val f = values[5]
        val g = values[6]
        val h = values[7]
        val i = values[8]
        val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (kotlin.math.abs(determinant) < 1e-7f) return null
        return Matrix3(
            listOf(
                (e * i - f * h) / determinant,
                (c * h - b * i) / determinant,
                (b * f - c * e) / determinant,
                (f * g - d * i) / determinant,
                (a * i - c * g) / determinant,
                (c * d - a * f) / determinant,
                (d * h - e * g) / determinant,
                (b * g - a * h) / determinant,
                (a * e - b * d) / determinant,
            )
        )
    }

    public companion object {
        public val IDENTITY: Matrix3 = Matrix3(listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        public fun translation(x: Float, y: Float): Matrix3 =
            Matrix3(listOf(1f, 0f, x, 0f, 1f, y, 0f, 0f, 1f))

        public fun scale(x: Float, y: Float = x): Matrix3 =
            Matrix3(listOf(x, 0f, 0f, 0f, y, 0f, 0f, 0f, 1f))
    }
}
