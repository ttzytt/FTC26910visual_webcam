package org.webcam_visual

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.webcam_visual.common.FrameCtx
import org.webcam_visual.display.DebugTreeGUI
import org.webcam_visual.preproc.BilateralFilterStep
import org.webcam_visual.preproc.PreprocPipeline
import org.webcam_visual.preproc.TemporalDenoiserStep
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // 1) Load native OpenCV library.
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    // 2) Create our pipeline with the desired steps.
    //    PreprocPipeline should implement ImgDebuggable so the DebugTreeGUI can introspect it.
    val pipeline = PreprocPipeline(
        BilateralFilterStep(11, 50.0, 2.0),
        TemporalDenoiserStep(0.7, 30.0),
    )

    // 3) Launch a debug GUI showing the entire pipeline's structure & debug toggles.
    SwingUtilities.invokeLater {
        DebugTreeGUI(pipeline)  // The tree GUI for toggling steps & debug options
    }

    // 5) Open a webcam capture & process frames in a loop.
    val cap = VideoCapture(0)
    if (!cap.isOpened) {
        println("Could not open webcam.")
        return
    }

    val frame = Mat()
    val ctx = FrameCtx(frame)
    while (true) {
        if (!cap.read(frame) || frame.empty()) {
            println("Failed to read frame.")
            break
        }
        val st = System.currentTimeMillis()
        val processed = pipeline.process(ctx)
        val ed = System.currentTimeMillis()
        ctx.prevFrame = processed.frame.clone()
        println("Processed frame in ${ed - st} ms.")
    }

    cap.release()
}
