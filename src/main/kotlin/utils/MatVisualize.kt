package org.webcam_visual.utils

import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Applies a colormap to a floating-point difference image and creates a legend bar
 * for visualization.
 *
 * @param distMat   A CV_32F Mat holding per-pixel distances (e.g., from absdiff).
 * @param colormap  An OpenCV colormap identifier (e.g. Imgproc.COLORMAP_JET).
 * @param barHeight The height (in pixels) of the color bar.
 * @param barWidth  The width (in pixels) of the color bar (default 30).
 * @return a Pair where first = colored difference image, second = color bar legend.
 */
fun createColorMappedDiffAndBar(
    distMat: Mat,
    colormap: Int = Imgproc.COLORMAP_JET,
    barHeight: Int = 200,
    barWidth: Int = 30
): Pair<Mat, Mat> {

    // 1) Determine min/max of distMat
    val mm = Core.minMaxLoc(distMat)
    val minVal = mm.minVal
    val maxVal = mm.maxVal

    // 2) Normalize distMat to [0..255] and convert to 8U
    //    If maxVal == minVal, we artificially expand the range to avoid a divide by zero
    val norm = Mat()
    Core.normalize(distMat, norm, 0.0, 255.0, Core.NORM_MINMAX)
    norm.convertTo(norm, CvType.CV_8U)

    // 3) Apply the chosen color map
    val coloredDist = Mat()
    Imgproc.applyColorMap(norm, coloredDist, colormap)

    // 4) Create a vertical gradient from [0..255] to represent the color scale
    val gradient = Mat(barHeight, 1, CvType.CV_8U)
    for (row in 0 until barHeight) {
        // row=0 => intensity=255 at top, row=barHeight-1 => intensity=0 at bottom
        val intensity = 255 - (row * 255 / (barHeight - 1))
        gradient.put(row, 0, intensity.toDouble())
    }

    // 5) Apply the same color map to the gradient to get a color bar
    val coloredBar = Mat()
    Imgproc.applyColorMap(gradient, coloredBar, colormap)

    // 6) Resize the 1-column bar to the desired barWidth
    val colorBar = Mat()
    Imgproc.resize(coloredBar, colorBar, Size(barWidth.toDouble(), barHeight.toDouble()))

    // 7) Optional: label the min/max values on the bar
    //    We'll put them in white near the top & bottom
    //    (Because the bar is vertical, top = maxVal, bottom = minVal)
    val fontFace = Imgproc.FONT_HERSHEY_SIMPLEX
    val fontScale = 0.4
    val white = Scalar(255.0, 255.0, 255.0)

    // String formatting with 1 decimal place
    val maxText = String.format("%.1f", maxVal)
    val minText = String.format("%.1f", minVal)
    // Draw max near top, offset a bit from the left
    Imgproc.putText(colorBar, maxText, Point(2.0, 12.0), fontFace, fontScale, white)
    // Draw min near bottom
    Imgproc.putText(colorBar, minText, Point(2.0, barHeight - 5.0), fontFace, fontScale, white)

    return coloredDist to colorBar
}
