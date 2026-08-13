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

/** An axis-aligned rectangle in logical UI coordinates. */
public data class Rect(
    public val x: Float,
    public val y: Float,
    public val width: Float,
    public val height: Float,
) {
    public val right: Float
        get() = x + width

    public val bottom: Float
        get() = y + height

    public fun contains(point: Point): Boolean =
        point.x >= x && point.x <= right && point.y >= y && point.y <= bottom

    public fun intersect(other: Rect): Rect? {
        val left = maxOf(x, other.x)
        val top = maxOf(y, other.y)
        val right = minOf(right, other.right)
        val bottom = minOf(bottom, other.bottom)
        return if (right >= left && bottom >= top) Rect(left, top, right - left, bottom - top)
        else null
    }
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
