package com.handy.core.overlay

/** Returns marks with stable per-snapshot IDs (`m1`, `m2`, ...). */
fun List<AccessibilityMark>.withStableMarkIds(): List<AccessibilityMark> =
    mapIndexed { index, mark ->
        mark.copy(markId = mark.markId?.takeIf { it.isNotBlank() } ?: "m${index + 1}")
    }
