package org.webcam_visual.preproc
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.DISOpticalFlow
import org.webcam_visual.preproc.PreprocStep
import org.webcam_visual.utils.createColorMappedDiffAndBar
import org.webcam_visual.utils.gt  // assuming you have defined the infix "gt" operator
import kotlin.math.roundToInt

/**
 * A temporal denoiser step that uses DIS optical flow and temporal blending.
 * Intermediate results are stored for debugging if the corresponding debug option is enabled.
 *
 * Note:
 * - "warp" here refers to remapping the previous frame using the computed flow (not "wrap", which is a border mode).
 */
class TemporalDenoiserStep(
    private var alpha: Double = 0.8,         // Weight for current frame in blending.
    private var threshold: Double = 30.0,      // Threshold for rejecting motion compensation.
    private val flowGridStep: Int = 20         // Grid step size for flow visualization.
) : PreprocStep("temporal_denoise") {

    private val disOptFlow: DISOpticalFlow = DISOpticalFlow.create(DISOpticalFlow.PRESET_ULTRAFAST)
    private var prevFrame: Mat? = null   // Previous frame (BGR)
    private var prevGray: Mat? = null    // Grayscale version of previous frame
    private var flow: Mat? = null        // Optical flow (CV_32FC2)

    // Coordinate grids for remapping; created once when frame size is known.
    private var coordinateGridX: Mat? = null
    private var coordinateGridY: Mat? = null

    init {
        setDbgOptions(listOf("flow", "warp", "diff", "mask", "output"), initDebug)
    }

    override fun process(image: Mat): Mat {
        // 0) Initialize coordinate grids if needed.
        if (coordinateGridX == null || coordinateGridY == null) {
            createCoordinateGrids(image.size())
        }

        // Check frame size.
        val rows = image.rows()
        val cols = image.cols()
        if (rows != coordinateGridX!!.rows() || cols != coordinateGridX!!.cols()) {
            throw IllegalArgumentException("Frame size (${rows}x${cols}) does not match stored grids (${coordinateGridX!!.rows()}x${coordinateGridX!!.cols()}).")
        }

        // 1) If no previous frame, store the current frame (and its grayscale) and return.
        if (prevFrame == null) {
            prevFrame = image.clone()
            prevGray = Mat()
            Imgproc.cvtColor(image, prevGray, Imgproc.COLOR_BGR2GRAY)
            return image
        }

        // 2) Convert current frame to grayscale (no debug for this trivial step).
        val currentGray = Mat()
        Imgproc.cvtColor(image, currentGray, Imgproc.COLOR_BGR2GRAY)

        // 3) Compute optical flow between previous and current grayscale images.
        if (flow == null) {
            flow = Mat(currentGray.size(), CvType.CV_32FC2)
        }
        disOptFlow.calc(prevGray, currentGray, flow)
        // Visualize flow as arrows if debug option "flow" is enabled.
        if (isDbgOptionEnabled("flow")) {
            val flowVis = drawFlowArrows(flow!!, flowGridStep)
            addDbgEntry("flow", flowVis)
        }

        // 4) Warp the previous frame using the flow.
        val warpedPrev = Mat(image.size(), image.type())
        val flowChannels = ArrayList<Mat>(2)
        Core.split(flow, flowChannels)  // flowChannels[0]=dx, [1]=dy
        val flowMapX = Mat()
        val flowMapY = Mat()
        Core.add(flowChannels[0], coordinateGridX, flowMapX)  // newX = x + dx
        Core.add(flowChannels[1], coordinateGridY, flowMapY)  // newY = y + dy
        // "warp" means remapping the previous frame.
        Imgproc.remap(prevFrame, warpedPrev, flowMapX, flowMapY, Imgproc.INTER_LINEAR, Core.BORDER_CONSTANT, Scalar(0.0, 0.0, 0.0))
        if (isDbgOptionEnabled("warp"))
            addDbgEntry("warp", warpedPrev.clone())

        // 5) Compute the per-pixel difference between current frame and warped previous frame.
        val diffMat = Mat()
        Core.absdiff(image, warpedPrev, diffMat)
        // Convert to floating point.
        val diffFloat = Mat()
        diffMat.convertTo(diffFloat, CvType.CV_32F)
        // Square the differences.
        val diffSquared = Mat()
        Core.multiply(diffFloat, diffFloat, diffSquared)
        // Sum across channels.
        val diffCh = ArrayList<Mat>()
        Core.split(diffSquared, diffCh)
        val sumOfSquares = Mat(diffSquared.size(), CvType.CV_32FC1)
        Core.add(diffCh[0], diffCh[1], sumOfSquares)
        Core.add(sumOfSquares, diffCh[2], sumOfSquares)
        // Compute element-wise square root.
        val distMat = Mat()
        Core.sqrt(sumOfSquares, distMat)
        if (isDbgOptionEnabled("diff")) {
            val (colorDiff, colorBar) = createColorMappedDiffAndBar(distMat)
            val roi = colorDiff.submat(
                0, colorBar.rows(),
                colorDiff.cols() - colorBar.cols(), colorDiff.cols()
            )
            colorBar.copyTo(roi)
            addDbgEntry("diff", colorDiff)
        }
        // 6) Build a mask: where the distance is greater than the threshold.
        val mask = distMat gt Scalar(threshold)
        if (isDbgOptionEnabled("mask"))
            addDbgEntry("mask", mask.clone())

        // 7) Blend the current frame and warped previous frame.
        val blended = Mat()
        Core.addWeighted(image, alpha, warpedPrev, 1.0 - alpha, 0.0, blended)
        // 8) Override output pixels with current frame where the difference is too high.
        image.copyTo(blended, mask)
        if (isDbgOptionEnabled("output"))
            addDbgEntry("output", blended.clone())

        // 9) Update previous frame references.
        prevFrame?.release()
        prevFrame = blended.clone()
        prevGray?.release()
        prevGray = currentGray

        return blended
    }

    /**
     * Creates coordinate grids (for X and Y) based on the given image size.
     */
    private fun createCoordinateGrids(size: Size) {
        val w = size.width.toInt()
        val h = size.height.toInt()
        coordinateGridX = Mat(size, CvType.CV_32FC1)
        coordinateGridY = Mat(size, CvType.CV_32FC1)
        for (row in 0 until h) {
            for (col in 0 until w) {
                coordinateGridX!!.put(row, col, col.toDouble())
                coordinateGridY!!.put(row, col, row.toDouble())
            }
        }
    }

    /**
     * Draws optical flow vectors as arrows on a blank image.
     *
     * @param flow The optical flow matrix (CV_32FC2).
     * @param step The grid step size for sampling flow vectors.
     * @return A visualization image (CV_8UC3) with arrows drawn.
     */
    private fun drawFlowArrows(flow: Mat, step: Int): Mat {
        // Create a blank image (black) with same size as flow.
        val vis = Mat.zeros(flow.size(), CvType.CV_8UC3)
        val rows = flow.rows()
        val cols = flow.cols()
        for (y in 0 until rows step step) {
            for (x in 0 until cols step step) {
                val flowAt = flow.get(y, x)
                val dx = flowAt[0]
                val dy = flowAt[1]
                val pt1 = Point(x.toDouble(), y.toDouble())
                val pt2 = Point(x + dx, y + dy)
                // Draw an arrowed line in green.
                Imgproc.arrowedLine(vis, pt1, pt2, Scalar(0.0, 255.0, 0.0), 1)
            }
        }
        return vis
    }

    fun release() {
        prevFrame?.release()
        prevGray?.release()
        flow?.release()
        coordinateGridX?.release()
        coordinateGridY?.release()
    }
}
