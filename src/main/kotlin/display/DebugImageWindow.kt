import org.opencv.core.Mat
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

/**
 * A JFrame that:
 *
 * 1) Starts at a default 640×480 size (you can change it).
 * 2) Polls for Mat frames (via [fetchMat]) at [initialPollInterval] ms (~30fps by default).
 * 3) Preserves the first displayed image's aspect ratio if the user tries to resize.
 * 4) Scales the displayed image to fill the interior area (no scroll bars).
 * 5) By default, does NOT close when "X" is clicked (DO_NOTHING_ON_CLOSE).
 *
 * If you want to disable polling, call [setPollInterval](-1) and manually invoke [showMat].
 */
class DebugImageWindow(
    windowTitle: String,
    private val fetchMat: () -> Mat?,  // function returning the latest Mat
    initialPollInterval: Int = 33      // default ~30 FPS
) : JFrame(windowTitle) {

    private val imageLabel = JLabel()
    private var timer: Timer? = null

    // The most recent full-resolution image we have displayed
    private var latestImage: BufferedImage? = null

    // We'll lock onto the first displayed image's aspect ratio
    private var aspectRatio: Double? = null

    init {
        // By default, ignore the "X", so it stays in sync with external toggles
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE

        layout = BorderLayout()
        add(imageLabel, BorderLayout.CENTER)

        // The "default" window size: you can change it
        setSize(640, 480)
        setLocationRelativeTo(null)

        // Listen for user-resizing to enforce aspect ratio and scale the image
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                // If we have no aspect ratio or no loaded image, do nothing
                if (aspectRatio == null || latestImage == null) return

                // Temporarily remove the listener to avoid recursion
                removeComponentListener(this)

                // 1) Enforce the aspect ratio for the new window size
                val insets = insets
                val availableW = width - insets.left - insets.right
                val availableH = height - insets.top - insets.bottom
                if (availableW <= 0 || availableH <= 0) {
                    addComponentListener(this)
                    return
                }

                val currentRatio = availableW.toDouble() / availableH
                val targetRatio = aspectRatio!!

                var newW = availableW
                var newH = availableH

                if (currentRatio > targetRatio) {
                    // Too wide => fix width based on height
                    newW = (newH * targetRatio).toInt()
                } else {
                    // Too tall => fix height based on width
                    newH = (newW / targetRatio).toInt()
                }

                val finalW = newW + insets.left + insets.right
                val finalH = newH + insets.top + insets.bottom
                setSize(finalW, finalH)

                // 2) Scale the image to match the new client area
                rescaleAndDisplayImage(newW, newH)

                // Re-add the listener
                addComponentListener(this)
            }
        })

        isVisible = true

        // Start polling if interval > 0
        setPollInterval(initialPollInterval)
    }

    /**
     * Set the polling interval (in ms).
     * If > 0, starts/restarts a Timer that calls [fetchMat] that often.
     * If -1, polling is disabled and you must call [showMat] yourself.
     */
    fun setPollInterval(intervalMs: Int) {
        timer?.stop()
        timer = null
        if (intervalMs > 0) {
            timer = Timer(intervalMs) {
                val mat = fetchMat()
                showMat(mat)
            }
            timer?.start()
        }
    }

    /**
     * Displays the given Mat in the window.
     *  - If null => clears the image.
     *  - If first non-null => store aspect ratio but do NOT call pack() (so the default
     *    window size remains as is).
     *  - Then scale to fill the current interior region.
     */
    fun showMat(mat: Mat?) {
        if (mat == null) {
            latestImage = null
            imageLabel.icon = null
            return
        }
        val buffered = matToBufferedImage(mat)
        latestImage = buffered

        // If first time we've seen a non-null image, store its aspect ratio
        if (aspectRatio == null && buffered.width > 0 && buffered.height > 0) {
            aspectRatio = buffered.width.toDouble() / buffered.height.toDouble()
        }

        // Scale the new image to the current client area
        val insets = insets
        val availW = width - insets.left - insets.right
        val availH = height - insets.top - insets.bottom
        rescaleAndDisplayImage(availW, availH)
    }

    /**
     * Rescales [latestImage] to fit the given w x h area in the label,
     * preserving the aspect ratio for the final window size, but the
     * image is "stretched" to fill it exactly.
     */
    private fun rescaleAndDisplayImage(w: Int, h: Int) {
        if (latestImage == null || w <= 0 || h <= 0) {
            imageLabel.icon = null
            return
        }
        val scaled = latestImage!!.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH)
        imageLabel.icon = ImageIcon(scaled)
    }

    // -----------------------------------------------------------------
    // Convert OpenCV Mat -> BufferedImage
    // -----------------------------------------------------------------
    private fun matToBufferedImage(mat: Mat): BufferedImage {
        val width = mat.width()
        val height = mat.height()
        val channels = mat.channels()
        val sourcePixels = ByteArray(width * height * channels)
        mat.get(0, 0, sourcePixels)

        // If 3 channels, convert BGR -> RGB in-place
        if (channels == 3) {
            for (i in sourcePixels.indices step 3) {
                val b = sourcePixels[i]
                sourcePixels[i] = sourcePixels[i + 2]
                sourcePixels[i + 2] = b
            }
        }

        val imageType = if (channels == 3) {
            BufferedImage.TYPE_3BYTE_BGR
        } else {
            BufferedImage.TYPE_BYTE_GRAY
        }
        val buffered = BufferedImage(width, height, imageType)
        buffered.raster.setDataElements(0, 0, width, height, sourcePixels)
        return buffered
    }
}
