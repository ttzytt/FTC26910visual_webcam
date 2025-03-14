package org.webcam_visual

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
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
        TemporalDenoiserStep(0.3, 50.0),
        BilateralFilterStep(21, 50.0, 5.0)
    )

    // 3) Launch a debug GUI showing the entire pipeline's structure & debug toggles.
    SwingUtilities.invokeLater {
        DebugTreeGUI(pipeline)  // The tree GUI for toggling steps & debug options
    }

    // 4) Optionally, create a window to display the processed frames.
    val outputFrame = JFrame("Processed Output")
    outputFrame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    outputFrame.setSize(640, 480)
    outputFrame.isVisible = true

    // 5) Open a webcam capture & process frames in a loop.
    val cap = VideoCapture(0)
    if (!cap.isOpened) {
        println("Could not open webcam.")
        return
    }

    val frame = Mat()
    while (true) {
        if (!cap.read(frame) || frame.empty()) {
            println("Failed to read frame.")
            break
        }
        // For demonstration, optionally add synthetic noise or something.

        // 6) Process the frame through our pipeline.
        val processed = pipeline.process(frame)

        // 7) Convert the processed frame to RGB & display.
        val displayFrame = Mat()
        Imgproc.cvtColor(processed, displayFrame, Imgproc.COLOR_BGR2RGB)
        // ~ 30 fps limit
        // Thread.sleep(33)
    }

    cap.release()
}
