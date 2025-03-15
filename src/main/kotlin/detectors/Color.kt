package org.webcam_visual.detectors
import kotlin.math.sqrt

typealias HsvColor = Triple<Int, Int, Int>
typealias RgbColor = Triple<Int, Int, Int>
typealias BgrColor = Triple<Int, Int, Int>

data class HsvColorRange(
    val name : String,
    val hsvRanges : List<Pair<HsvColor, HsvColor>>,
    // it is possible for hsv to have two ranges for one range in bgr
    // red as an example
    val bgr : BgrColor
)

/**
 * Extension function on FloatArray to compute its standard deviation.
 */
fun FloatArray.stdDev(): Float {
    val mean = this.average().toFloat()
    val variance = this.fold(0f) { acc, value -> acc + (value - mean) * (value - mean) } / this.size
    return sqrt(variance.toDouble()).toFloat()
}

/**
 * Extension function on FloatArray that computes the standard deviation for hue values,
 * taking into account hue wrapping.
 *
 * It computes the standard deviation of the original values, then creates a copy where
 * every value below [flipThreshold] is increased by 180, and computes the standard deviation of that copy.
 * The function returns the smaller of the two standard deviations.
 *
 * @param flipThreshold The threshold below which hue values are "flipped" by adding 180 (default is 90.0f).
 * @return The minimum standard deviation between the original and the shifted array.
 */
fun FloatArray.computeHueStdFlip(flipThreshold: Float = 90.0f): Float {
    // Compute standard deviation directly.
    val std1 = this.stdDev()

    // Create a shifted copy where values below the threshold are increased by 180.
    val shifted = this.copyOf()
    for (i in shifted.indices) {
        if (shifted[i] < flipThreshold) {
            shifted[i] += 180.0f
        }
    }
    val std2 = shifted.stdDev()

    return minOf(std1, std2)
}
