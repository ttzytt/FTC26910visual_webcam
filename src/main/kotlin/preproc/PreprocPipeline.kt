package org.webcam_visual.preproc

import org.opencv.core.Mat
import org.webcam_visual.ImgDebuggable

class PreprocPipeline(
    vararg steps: PreprocStep
) : ImgDebuggable {
    private val steps: MutableList<PreprocStep> = steps.toMutableList()
    // Debug images are stored here if a step is set to debug.
    override val dbgChildren: MutableList<ImgDebuggable> = mutableListOf()
    override val dbgData: MutableMap<String, Mat> = mutableMapOf()
    override val availableDbgOptions: MutableMap<String, Boolean> = mutableMapOf()
    // Add a step dynamically.
    fun addStep(step: PreprocStep) {
        steps.add(step)
        dbgChildren.add(step)
    }

    // Process an image through all steps sequentially.
    fun process(image: Mat): Mat {
        var result = image
        for ((idx, step) in steps.withIndex()) {
            result = step.process(result)
        }
        return result
    }
}