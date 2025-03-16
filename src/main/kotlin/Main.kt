package org.webcam_visual

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.webcam_visual.common.COLOR_DEF_ARDUCAM
import org.webcam_visual.common.COLOR_DEF_R9000P
import org.webcam_visual.common.FrameCtx
import org.webcam_visual.detectors.ColorBlockDetector
import org.webcam_visual.gui.DebugTreeGUI
import org.webcam_visual.pipeline.RobotVisionPipeline
import org.webcam_visual.preproc.BilateralFilterStep
import org.webcam_visual.preproc.PreprocPipeline
import org.webcam_visual.preproc.ScaleStep
import org.webcam_visual.preproc.TemporalDenoiserStep
import org.webcam_visual.tracker.OpticFlowBlockTracker
import org.webcam_visual.visualizer.BlockVisualizer
import javax.swing.SwingUtilities

fun main() {
    // 1) Load native OpenCV library.
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    // 2) Create our pipeline with the desired steps.
    //    PreprocPipeline should implement ImgDebuggable so the DebugTreeGUI can introspect it.

    val preprocPipeline = PreprocPipeline(
        ScaleStep(0.5, 0.5, Imgproc.INTER_AREA),
        BilateralFilterStep(11, 50.0, 2.0),
        TemporalDenoiserStep(0.8, 30.0),
    )
    val detector = ColorBlockDetector(COLOR_DEF_R9000P)
    val visualizer = BlockVisualizer()
    val tracker = OpticFlowBlockTracker()
    val trackedVisualizer = BlockVisualizer()
    val pipeline = RobotVisionPipeline(preprocPipeline, detector, tracker, visualizer)
//    pipeline.disableDebug()
    val cap = VideoCapture(0)
    if (!cap.isOpened) {
        println("Could not open webcam.")
        return
    }

    val frame = Mat()
    if (!cap.read(frame) || frame.empty()) {
        println("Failed to read frame.")
    }
    pipeline.updateFrame(frame)
    SwingUtilities.invokeLater {
        DebugTreeGUI(pipeline){
            // need to perform scaling
            if (preprocPipeline.steps.first() is ScaleStep) {
                val scaledFrame = preprocPipeline.steps.first().process(FrameCtx(frame = frame)).frame!!.clone()
                scaledFrame
            } else {
                frame
            }
        }  // The tree GUI for toggling steps & debug options
    }
    while (true) {
        if (!cap.read(frame) || frame.empty()) {
            println("Failed to read frame.")
            break
        }
        pipeline.updateFrame(frame)
        val memUse = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        println("Memory usage: ${memUse / 1024 / 1024} MB")
    }

    cap.release()
}
