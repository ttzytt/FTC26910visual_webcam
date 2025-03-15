package org.webcam_visual

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.videoio.VideoCapture
import org.webcam_visual.common.COLOR_DEF_R9000P
import org.webcam_visual.common.FrameCtx
import org.webcam_visual.common.RootImgDebuggable
import org.webcam_visual.detectors.ColorDetector
import org.webcam_visual.gui.DebugTreeGUI
import org.webcam_visual.preproc.BilateralFilterStep
import org.webcam_visual.preproc.PreprocPipeline
import org.webcam_visual.preproc.TemporalDenoiserStep
import org.webcam_visual.tracker.BlockTracker
import org.webcam_visual.visualizer.BlockVisualizer
import javax.swing.SwingUtilities

fun main() {
    // 1) Load native OpenCV library.
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    // 2) Create our pipeline with the desired steps.
    //    PreprocPipeline should implement ImgDebuggable so the DebugTreeGUI can introspect it.

    val preprocPipeline = PreprocPipeline(
        BilateralFilterStep(11, 50.0, 2.0),
        TemporalDenoiserStep(0.7, 30.0),
    )
    val detector = ColorDetector(COLOR_DEF_R9000P)
    val visualizer = BlockVisualizer()
    val tracker = BlockTracker()
    val trackedVisualizer = BlockVisualizer()
    val rootImgDbg = RootImgDebuggable(
        preprocPipeline,
        detector,
        visualizer,
        trackedVisualizer
    )

    val cap = VideoCapture(0)
    if (!cap.isOpened) {
        println("Could not open webcam.")
        return
    }

    val frame = Mat()
    val ctx = FrameCtx(frame)
    SwingUtilities.invokeLater {
        DebugTreeGUI(rootImgDbg){frame}  // The tree GUI for toggling steps & debug options
    }
    while (true) {
        if (!cap.read(frame) || frame.empty()) {
            println("Failed to read frame.")
            break
        }
        val st = System.currentTimeMillis()
        val processed = preprocPipeline.process(ctx)
        val ed = System.currentTimeMillis()
        ctx.prevFrame = processed.frame.clone()
        var blocks = detector.detectBlocks(processed)
        visualizer.visualizeBlocks(ctx.frame, blocks)
        blocks = tracker.trackBlocks(ctx, blocks)
        trackedVisualizer.visualizeBlocks(ctx.frame, blocks)
        println("Processed frame in ${ed - st} ms.")
    }

    cap.release()
}
