package com.team.frameai.guide

/** Local fallback for Action mode. Higher scores indicate more high-frequency detail. */
object ActionFrameScore {
    fun sharpness(luma: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3 || luma.size < width * height) return 0.0
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val at = y * width + x
                val laplacian = 4 * luma[at] - luma[at - 1] - luma[at + 1] -
                    luma[at - width] - luma[at + width]
                sum += laplacian
                sumSquares += laplacian.toDouble() * laplacian
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return (sumSquares / count - mean * mean).coerceAtLeast(0.0)
    }

    fun best(scores: List<Double>): Int =
        scores.indices.maxByOrNull { scores[it] } ?: 0
}
