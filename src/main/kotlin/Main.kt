package org.webcam_visual

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.videoio.VideoCapture
import org.webcam_visual.algos.TemporalDenoiser
import org.webcam_visual.preproc.PreprocPipeline
import org.webcam_visual.preproc.TemporalDenoiserStep
import javax.swing.JFrame

fun main() {
    // Load the OpenCV native library.
    System.loadLibrary(Core.NATIVE_LIBRARY_NAME)

    // Open the default webcam (index 0).
    val capture = VideoCapture(0)
    if (!capture.isOpened) {
        println("Error: Could not open webcam.")
        return
    }

    // Prepare two display windows: one for the noisy original image and one for the processed image.
    val winDenoise = JFrame("Temporal Denoising Webcam")
    val winOrig = JFrame("Noisy Original Image")
    val panelDenoise = CamCapturePanel()
    val panelOrig = CamCapturePanel()

    winDenoise.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    winDenoise.contentPane.add(panelDenoise)
    winDenoise.setSize(640, 480)
    winDenoise.isVisible = true

    // Use panelOrig for the original image window.
    winOrig.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    winOrig.contentPane.add(panelOrig)
    winOrig.setSize(640, 480)
    winOrig.isVisible = true

    // Create an instance of the TemporalDenoiser.
    val denoiseStep = TemporalDenoiserStep(alpha = 0.3, threshold = 50.0)
    val preprocPipeline = PreprocPipeline(denoiseStep)

    val frame = Mat()
    while (true) {
        if (capture.read(frame)) {
            // Clone the frame and add noise to it.
            val noisyFrame = frame.clone()
            val noise = Mat(frame.size(), frame.type())
            // Generate Gaussian noise with zero mean and a standard deviation of 30.
            Core.randn(noise, 0.0,10.0)
            Core.add(noisyFrame, noise, noisyFrame)

            // Display the noisy original frame (converted from BGR to RGB).
            val origDispFrame = Mat()
            Imgproc.cvtColor(noisyFrame, origDispFrame, Imgproc.COLOR_BGR2RGB)
            panelOrig.updateImage(origDispFrame)

            // Process the noisy frame with the temporal denoiser.
            val processedFrame = preprocPipeline.process(noisyFrame)

            // Convert the processed frame from BGR to RGB for proper display.
            val displayFrame = Mat()
            Imgproc.cvtColor(processedFrame, displayFrame, Imgproc.COLOR_BGR2RGB)
            panelDenoise.updateImage(displayFrame)
        }
        // Sleep for roughly 33ms (~30 FPS).
//        Thread.sleep(33)
    }
}
