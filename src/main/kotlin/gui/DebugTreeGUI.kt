package org.webcam_visual.gui

import DebugImageWindow
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.webcam_visual.common.ImgDebuggable
import org.webcam_visual.utils.mat.grayToBGR
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.tree.*

/**
 * A Swing-based debug control tree for ImgDebuggable objects.
 *
 * When started, if any debug option is already toggled on, its associated debug window will pop up.
 * Debug window titles use a recursive full path (e.g. “Preproc.BilatFilter.output”) to prevent ambiguity.
 *
 * In addition, each debug option node now shows a slider (0–100) beside the checkbox.
 * The slider adjusts the blend weight for that option. A value of 0 shows only the debug image;
 * nonzero values blend the current original image (provided via [originalImageProvider]) with the debug image.
 */
class DebugTreeGUI(
    private val rootDebuggable: ImgDebuggable,
    private val originalImageProvider: () -> Mat
) : JFrame("Debug Controls") {

    // Whether each ImgDebuggable is "expanded" in the tree view.
    private val expandedStates = mutableMapOf<ImgDebuggable, Boolean>()

    // Track open debug windows: (debuggable, optionKey) -> window.
    private val debugWindows = mutableMapOf<Pair<ImgDebuggable, String>, DebugImageWindow>()

    // Map from (ImgDebuggable, optionKey) to its full path string (e.g. "Preproc.BilatFilter.output").
    private val fullPathMap = mutableMapOf<Pair<ImgDebuggable, String>, String>()

    // Per-node overlay blending value (0–100, where 0 means no blending).
    private val overlayAlphaMap = mutableMapOf<Pair<ImgDebuggable, String>, Double>()

    private val tree: JTree
    private var treeModel: DefaultTreeModel? = null

    init {
        expandedStates[rootDebuggable] = true
        tree = JTree().apply {
            cellRenderer = CustomNodeRenderer()
            cellEditor = CustomNodeEditor()
            isEditable = true
            showsRootHandles = true
            expandsSelectedPaths = true
        }

        refreshTree()
        contentPane.add(JScrollPane(tree), BorderLayout.CENTER)
        setSize(500, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        isVisible = true

        // Compute full path mapping for every debug option.
        computeFullPaths(rootDebuggable)
        // Set default overlay alpha for each option (default 0).
        fullPathMap.keys.forEach { key -> overlayAlphaMap[key] = 0.0 }
        // At startup, if any debug option is enabled, open its debug window.
        for ((key, _) in fullPathMap) {
            val (dbg, optionKey) = key
            if (dbg.isDbgOptionEnabled(optionKey)) {
                openDebugWindow(dbg, optionKey)
            }
        }
    }

    fun refreshTree() {
        val rootNode = DebuggableNode(rootDebuggable)
        treeModel = DefaultTreeModel(rootNode as TreeNode)
        tree.model = treeModel
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    /**
     * Recursively compute a mapping from each debug option (for each ImgDebuggable)
     * to a full path string. For example, if the root debuggable’s name is “Preproc”
     * and it has a child whose class is “BilatFilter”, then an option “output” will get
     * the full title “Preproc.BilatFilter.output”.
     */
    private fun computeFullPaths(debuggable: ImgDebuggable, prefix: String = "") {
        val currentName = if (prefix.isEmpty())
            debuggable::class.simpleName ?: "root"
        else
            prefix
        for (optionKey in debuggable.availableDbgOptions.keys) {
            fullPathMap[Pair(debuggable, optionKey)] = "$currentName.$optionKey"
        }
        for (child in debuggable.dbgChildren) {
            val childName = child::class.simpleName ?: "child"
            computeFullPaths(child, "$currentName.$childName")
        }
    }

    // ---------------------------------------------------------------------------
    // Node Classes
    // ---------------------------------------------------------------------------
    inner class DebuggableNode(val debuggable: ImgDebuggable) : CheckBoxTreeNode() {
        init {
            setUserObject(debuggable)
            val showChildren = expandedStates.getOrDefault(debuggable, true)
            // Add a node for each debug option.
            debuggable.availableDbgOptions.keys.forEach { key ->
                add(DebugOptionNode(debuggable, key))
            }
            // Recursively add child ImgDebuggable nodes if expanded.
            if (showChildren) {
                debuggable.dbgChildren.forEach { child ->
                    add(DebuggableNode(child))
                }
            }
        }
        override fun toString(): String {
            val cname = debuggable::class.simpleName ?: debuggable.javaClass.simpleName
            return cname ?: "(Unnamed Debuggable)"
        }
    }

    inner class DebugOptionNode(val debugObj: ImgDebuggable, val optionKey: String) : CheckBoxTreeNode() {
        init {
            setUserObject(optionKey)
            allowsChildren = false
        }
        override fun toString(): String = optionKey
    }

    // ---------------------------------------------------------------------------
    // Renderer: returns a component for each tree cell.
    // For DebuggableNode, a simple checkbox is shown.
    // For DebugOptionNode, a panel with a checkbox and a slider is returned.
    // ---------------------------------------------------------------------------
    private inner class CustomNodeRenderer : TreeCellRenderer {
        private val checkBox = JCheckBox()
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            return when (value) {
                is DebuggableNode -> {
                    checkBox.text = value.toString()
                    val dbg = value.debuggable
                    checkBox.isSelected = areAllChildrenOn(dbg)
                    checkBox.isEnabled = true
                    checkBox
                }
                is DebugOptionNode -> {
                    // Create a panel with a checkbox and a slider.
                    val panel = JPanel(BorderLayout())
                    val cb = JCheckBox(value.optionKey)
                    cb.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
                    panel.add(cb, BorderLayout.WEST)
                    // Create a slider for blending (0 to 100).
                    val slider = JSlider(0, 100, overlayAlphaMap[Pair(value.debugObj, value.optionKey)]?.toInt() ?: 50)
                    slider.majorTickSpacing = 25
                    slider.minorTickSpacing = 5
                    slider.paintTicks = true
                    slider.paintLabels = true
                    panel.add(slider, BorderLayout.EAST)
                    panel
                }
                else -> DefaultTreeCellRenderer().getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus
                )
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Editor: allows toggling checkboxes and adjusting slider values.
    // ---------------------------------------------------------------------------
    private inner class CustomNodeEditor : AbstractCellEditor(), TreeCellEditor {
        // Panel for debug option nodes.
        private val optionPanel = JPanel(BorderLayout())
        private val checkBox = JCheckBox()
        private val slider = JSlider(0, 100, 50)
        // For non-option nodes we simply reuse a checkbox.
        private val simpleCheckBox = JCheckBox()
        private var currentNode: CheckBoxTreeNode? = null

        init {
            // When slider value changes, update the per-node blend value.
            slider.addChangeListener(object : ChangeListener {
                override fun stateChanged(e: ChangeEvent?) {
                    currentNode?.let { node ->
                        if (node is DebugOptionNode) {
                            val key = Pair(node.debugObj, node.optionKey)
                            val newVal = slider.value.toDouble()
                            overlayAlphaMap[key] = newVal
                            // If the debug window is open, force it to refresh by re-opening.
                            if (node.debugObj.isDbgOptionEnabled(node.optionKey)) {
                                openDebugWindow(node.debugObj, node.optionKey)
                            }
                        }
                    }
                }
            })
            // When checkbox is toggled, finish editing.
            checkBox.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    stopCellEditing()
                }
            })
            simpleCheckBox.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    stopCellEditing()
                }
            })
        }

        override fun getTreeCellEditorComponent(
            tree: JTree?,
            value: Any?,
            isSelected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int
        ): Component {
            currentNode = value as? CheckBoxTreeNode
            return when (value) {
                is DebuggableNode -> {
                    simpleCheckBox.text = value.toString()
                    val dbg = value.debuggable
                    simpleCheckBox.isSelected = areAllChildrenOn(dbg)
                    simpleCheckBox
                }
                is DebugOptionNode -> {
                    optionPanel.removeAll()
                    checkBox.text = value.optionKey
                    checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
                    optionPanel.add(checkBox, BorderLayout.WEST)
                    val key = Pair(value.debugObj, value.optionKey)
                    val currentAlpha = overlayAlphaMap[key]?.toInt() ?: 50
                    slider.value = currentAlpha
                    optionPanel.add(slider, BorderLayout.EAST)
                    optionPanel
                }
                else -> simpleCheckBox
            }
        }

        override fun getCellEditorValue(): Any {
            val newValue = checkBox.isSelected
            currentNode?.let { node ->
                when (node) {
                    is DebuggableNode -> {
                        toggleDebuggableRecursively(node.debuggable, newValue)
                        expandedStates[node.debuggable] = newValue
                    }
                    is DebugOptionNode -> {
                        val wasEnabled = node.debugObj.isDbgOptionEnabled(node.optionKey)
                        node.debugObj.setDbgOption(node.optionKey, newValue)
                        if (newValue && !wasEnabled) {
                            openDebugWindow(node.debugObj, node.optionKey)
                        } else if (!newValue && wasEnabled) {
                            closeDebugWindow(node.debugObj, node.optionKey)
                        }
                        syncParentStatesUp(node)
                    }
                }
                SwingUtilities.invokeLater { refreshTree() }
            }
            return currentNode ?: ""
        }
    }

    // ---------------------------------------------------------------------------
    // Recursive Toggling Helpers
    // ---------------------------------------------------------------------------
    private fun toggleDebuggableRecursively(debuggable: ImgDebuggable, newVal: Boolean) {
        for (optionKey in debuggable.availableDbgOptions.keys) {
            val wasEnabled = debuggable.isDbgOptionEnabled(optionKey)
            debuggable.setDbgOption(optionKey, newVal)
            if (newVal && !wasEnabled) {
                openDebugWindow(debuggable, optionKey)
            } else if (!newVal && wasEnabled) {
                closeDebugWindow(debuggable, optionKey)
            }
        }
        for (child in debuggable.dbgChildren) {
            toggleDebuggableRecursively(child, newVal)
        }
    }

    private fun syncParentStatesUp(childNode: CheckBoxTreeNode) {
        var parent = childNode.parent
        while (parent is DebuggableNode) {
            val dbg = parent.debuggable
            val allOn = areAllChildrenOn(dbg)
            val parentCurrentlyAllOn = areAllDebugOptionsEnabled(dbg) && areAllChildrenParentsOn(dbg)
            if (allOn != parentCurrentlyAllOn) {
                toggleDebuggableRecursively(dbg, allOn)
            }
            parent = parent.parent
        }
    }

    private fun areAllChildrenOn(debuggable: ImgDebuggable): Boolean {
        for ((_, enabled) in debuggable.availableDbgOptions) {
            if (!enabled) return false
        }
        for (child in debuggable.dbgChildren) {
            if (!areAllChildrenOn(child)) return false
        }
        return true
    }

    private fun areAllDebugOptionsEnabled(dbg: ImgDebuggable): Boolean {
        for (opt in dbg.availableDbgOptions.values) {
            if (!opt) return false
        }
        return true
    }

    private fun areAllChildrenParentsOn(dbg: ImgDebuggable): Boolean {
        for (child in dbg.dbgChildren) {
            if (!areAllChildrenOn(child)) return false
        }
        return true
    }

    // ---------------------------------------------------------------------------
    // Debug Windows
    // ---------------------------------------------------------------------------
    private fun openDebugWindow(debuggable: ImgDebuggable, optionKey: String) {
        val key = Pair(debuggable, optionKey)
        if (debugWindows.containsKey(key)) {
            debugWindows[key]?.requestFocus()
            return
        }
        val title = fullPathMap[key] ?: "$optionKey Debug"
        val window = DebugImageWindow(title, {
            var dbgImage = debuggable.dbgData[optionKey]
            val orig = originalImageProvider.invoke()
            val blendAlpha = overlayAlphaMap[key]?.div(100.0) ?: 0.5
            if (blendAlpha > 0 && dbgImage != null && validImage(orig) && validImage(dbgImage)) {
                val blended = Mat()
                // if the dbgImage is grayScale then convert to BGR
                if (dbgImage.channels() == 1){
                    Imgproc.cvtColor(dbgImage, dbgImage, Imgproc.COLOR_GRAY2BGR)
                }
                Core.addWeighted(orig, blendAlpha, dbgImage, 1.0 - blendAlpha, 0.0, blended)
                blended
            } else {
                dbgImage
            }
        })
        debugWindows[key] = window
    }

    private fun closeDebugWindow(debuggable: ImgDebuggable, optionKey: String) {
        val key = Pair(debuggable, optionKey)
        debugWindows.remove(key)?.dispose()
    }

    private fun validImage(img: Mat?): Boolean {
        return img != null && img.total() > 0 && img.rows() > 0 && img.cols() > 0
    }
}

// ---------------------------------------------------------------------------
// Base class for checkbox tree nodes.
// ---------------------------------------------------------------------------
open class CheckBoxTreeNode : DefaultMutableTreeNode() {
    override fun isLeaf(): Boolean = !allowsChildren
}
