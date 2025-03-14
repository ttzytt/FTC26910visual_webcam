package org.webcam_visual

import org.opencv.core.Mat
import java.awt.Graphics
import java.awt.image.BufferedImage
import javax.swing.JPanel

class CamCapturePanel : JPanel(){
    private var image: BufferedImage? = null

    fun updateImage(mat: Mat) {
        image = matToBufferedImage(mat)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        image?.let { g.drawImage(it, 0, 0, width, height, null) }
    }

    private fun matToBufferedImage(mat: Mat): BufferedImage {
        val width = mat.width()
        val height = mat.height()
        val bufferSize = width * height * mat.elemSize().toInt()
        val b = ByteArray(bufferSize)
        mat.get(0, 0, b)
        val type = if (mat.channels() == 3) BufferedImage.TYPE_3BYTE_BGR else BufferedImage.TYPE_BYTE_GRAY
        val img = BufferedImage(width, height, type)
        img.raster.setDataElements(0, 0, width, height, b)
        return img
    }
}