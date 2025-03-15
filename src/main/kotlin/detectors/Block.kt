package org.webcam_visual.detectors
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import java.awt.Color

data class Block(
    val center: Pair<Float, Float>,
    val size: Pair<Float, Float>,
    val angle: Float,
    val color: HsvColorRange,
    val colorStd: HsvColor,
    val colorMean: HsvColor,
    val contour: MatOfPoint = MatOfPoint(), // Stores contour as a set of points
    val id : Int,
)