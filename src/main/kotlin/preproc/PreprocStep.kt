package org.webcam_visual.preproc

import org.opencv.core.Mat
import org.webcam_visual.DefaultImgDebuggable

abstract class PreprocStep(val stepName: String, val initDebug: Boolean = false) : DefaultImgDebuggable() {
    init {
        // Optionally preconfigure the debug option "output" for this step.
        setDbgOption("output", initDebug)
    }

    abstract fun process(image: Mat): Mat
}