package com.tock

/**
 * 5×7 dot-matrix digit definitions.
 * Exactly matches the Nothing style 5x7 dot matrix from DotClock.tsx
 */
object DotMatrixFont {
    private val DIGITS: Array<Array<BooleanArray>> = arrayOf(
        // 0
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 1
        arrayOf(
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, true,  true,  false, false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 2
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, true,  false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, true,  false, false, false),
            booleanArrayOf(true,  true,  true,  true,  true)
        ),
        // 3
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, true,  true,  false),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 4
        arrayOf(
            booleanArrayOf(false, false, false, true,  false),
            booleanArrayOf(false, false, true,  true,  false),
            booleanArrayOf(false, true,  false, true,  false),
            booleanArrayOf(true,  false, false, true,  false),
            booleanArrayOf(true,  true,  true,  true,  true),
            booleanArrayOf(false, false, false, true,  false),
            booleanArrayOf(false, false, false, true,  false)
        ),
        // 5
        arrayOf(
            booleanArrayOf(true,  true,  true,  true,  true),
            booleanArrayOf(true,  false, false, false, false),
            booleanArrayOf(true,  true,  true,  true,  false),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 6
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, false),
            booleanArrayOf(true,  true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 7
        arrayOf(
            booleanArrayOf(true,  true,  true,  true,  true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(false, false, false, true,  false),
            booleanArrayOf(false, false, true,  false, false),
            booleanArrayOf(false, true,  false, false, false),
            booleanArrayOf(false, true,  false, false, false),
            booleanArrayOf(false, true,  false, false, false)
        ),
        // 8
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        ),
        // 9
        arrayOf(
            booleanArrayOf(false, true,  true,  true,  false),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  true),
            booleanArrayOf(false, false, false, false, true),
            booleanArrayOf(true,  false, false, false, true),
            booleanArrayOf(false, true,  true,  true,  false)
        )
    )

    fun getDigit(d: Int): Array<BooleanArray> = DIGITS[d.coerceIn(0, 9)]
}
