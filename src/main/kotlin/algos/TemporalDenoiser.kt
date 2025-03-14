package org.webcam_visual.algos

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.DISOpticalFlow
import org.webcam_visual.utils.*

class TemporalDenoiser(
    var alpha: Double = 0.8,         // Weight for the current frame in blending
    var threshold: Double = 30.0     // Threshold for rejecting motion compensation in RGB distance
) {

    private val disOptFlow: DISOpticalFlow = DISOpticalFlow.create(DISOpticalFlow.PRESET_FAST)

    private var prevFrame: Mat? = null     // Stores the previous frame (BGR)
    private var prevGray: Mat? = null      // Stores the grayscale version of the previous frame
    private var flow: Mat? = null          // Stores the optical flow result

    // Coordinate grids for remapping; created when frame size is first known
    private var coordinateGridX: Mat? = null
    private var coordinateGridY: Mat? = null

    /**
     * Processes the current frame and returns the per-pixel denoised output.
     */
    fun processFrame(currentFrame: Mat): Mat {
        // 0) If coordinate grids are not initialized, create them based on the frame size
        if (coordinateGridX == null || coordinateGridY == null) {
            createCoordinateGrids(currentFrame.size())
        }

        // Ensure the incoming frame is the same size as our grids (i.e., consistent resolution)
        val rows = currentFrame.rows()
        val cols = currentFrame.cols()
        if (rows != coordinateGridX!!.rows() || cols != coordinateGridX!!.cols()) {
            throw IllegalArgumentException(
                "Frame size (${rows}x${cols}) does not match stored grids (${coordinateGridX!!.rows()}x${coordinateGridX!!.cols()})."
            )
        }

        // 1) If there is no previous frame, store the current frame and return it
        if (prevFrame == null) {
            prevFrame = currentFrame.clone()
            prevGray = Mat()
            Imgproc.cvtColor(currentFrame, prevGray, Imgproc.COLOR_BGR2GRAY)
            return currentFrame
        }

        // 2) Convert current frame to grayscale
        val currentGray = Mat()
        Imgproc.cvtColor(currentFrame, currentGray, Imgproc.COLOR_BGR2GRAY)

        // 3) Compute optical flow if not already
        if (flow == null) {
            flow = Mat(currentGray.size(), CvType.CV_32FC2)
        }
        disOptFlow.calc(prevGray, currentGray, flow)

        // 4) Warp the previous frame using the flow vectors
        val warpedPrev = Mat(currentFrame.size(), currentFrame.type())
        val flowXY = ArrayList<Mat>(2)
        Core.split(flow, flowXY)  // flowXY[0] -> dx, flowXY[1] -> dy

        val flowMapX = Mat()
        val flowMapY = Mat()
        Core.add(flowXY[0], coordinateGridX, flowMapX)  // newX = x + dx
        Core.add(flowXY[1], coordinateGridY, flowMapY)  // newY = y + dy

        Imgproc.remap(
            prevFrame,
            warpedPrev,
            flowMapX,
            flowMapY,
            Imgproc.INTER_LINEAR,
            Core.BORDER_CONSTANT,
            Scalar(0.0, 0.0, 0.0)
        )

        // 5a) Compute absolute difference (diffMat is likely CV_8UC3)
        val diffMat = Mat()
        Core.absdiff(currentFrame, warpedPrev, diffMat)

        // Convert diffMat to a floating-point matrix (CV_32FC3)
        val diffMatFloat = Mat()
        diffMat.convertTo(diffMatFloat, CvType.CV_32F)

        // 5b) Square the differences element-wise
        val diffSquared = Mat()
        Core.multiply(diffMatFloat, diffMatFloat, diffSquared)

        // 5c) Split channels and sum them to get the sum of squares (CV_32FC1)
        val diffChannels = ArrayList<Mat>()
        Core.split(diffSquared, diffChannels)
        val sumOfSquares = Mat(diffSquared.size(), CvType.CV_32FC1)
        Core.add(diffChannels[0], diffChannels[1], sumOfSquares)
        Core.add(sumOfSquares, diffChannels[2], sumOfSquares)

        // 5d) Compute the element-wise square root using Core.sqrt.
        // Note: Core.sqrt requires the input to be a floating-point matrix.
        val distMat = Mat()
        Core.sqrt(sumOfSquares, distMat)  // Each element in distMat is sqrt(value)


        // 6) Build a mask: compare distMat with threshold
        val mask = distMat gt Scalar(threshold)

        // 7) Alpha-blend the entire frame
        val blended = Mat()
        Core.addWeighted(currentFrame, alpha, warpedPrev, 1.0 - alpha, 0.0, blended)

        // 8) Override with currentFrame where difference is too large
        currentFrame.copyTo(blended, mask)

        // 9) Update references for next iteration
        val output = blended
        prevFrame?.release()
        prevFrame = output.clone()

        prevGray?.release()
        prevGray = currentGray

        return output
    }

    /**
     * Creates and stores the X/Y coordinate grids used for remapping.
     * This only needs to be done once, based on the expected frame size.
     */
    private fun createCoordinateGrids(size: Size) {
        val (w, h) = Pair(size.width.toInt(), size.height.toInt())
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
     * Releases all allocated resources.
     */
    fun release() {
        prevFrame?.release()
        prevFrame = null
        prevGray?.release()
        prevGray = null
        flow?.release()
        flow = null

        coordinateGridX?.release()
        coordinateGridX = null
        coordinateGridY?.release()
        coordinateGridY = null
    }
}
