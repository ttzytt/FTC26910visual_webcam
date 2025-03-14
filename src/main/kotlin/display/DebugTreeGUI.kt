package org.webcam_visual.display

import DebugImageWindow
import org.opencv.core.Mat
import org.webcam_visual.ImgDebuggable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.tree.*

/**
 * A Swing-based UI that shows a hierarchy of ImgDebuggable components in a JTree,
 * along with each component's debug-option keys.  Toggling a component node
 * hides/shows its children; toggling a debug-option node enables/disables that debug option.
 *
 * Additionally, when a debug option is toggled ON, this code will open a small
 * DebugImageWindow to display the corresponding debug image (if available). Toggling OFF closes it.
 */
class DebugTreeGUI(private val rootDebuggable: ImgDebuggable) : JFrame("Debug Controls") {

    // Tracks whether each ImgDebuggable is "expanded" (true => show children in the tree).
    private val expandedStates: MutableMap<ImgDebuggable, Boolean> = mutableMapOf()

    // JTree that displays everything.
    private val tree: JTree

    // The backing model for the JTree
    private var treeModel: DefaultTreeModel? = null

    // Keeps track of each (debugObj, optionKey) -> debug window, so we can close it if toggled OFF.
    private val debugWindows = mutableMapOf<Pair<ImgDebuggable, String>, DebugImageWindow>()

    init {
        // Default the root node to "expanded"
        expandedStates[rootDebuggable] = true

        // Create and configure the JTree
        tree = JTree().apply {
            cellRenderer = CheckBoxNodeRenderer()
            cellEditor   = CheckBoxNodeEditor()
            isEditable   = true
            showsRootHandles = true
            expandsSelectedPaths = true
        }

        refreshTree() // Build the initial tree model

        val scrollPane = JScrollPane(tree)
        contentPane.add(scrollPane, BorderLayout.CENTER)

        setSize(500, 600)
        defaultCloseOperation = EXIT_ON_CLOSE
        setLocationRelativeTo(null)
        isVisible = true
    }

    /**
     * Rebuilds the entire tree model from scratch, based on the expanded states
     * and debug options. Then sets this as the JTree's model and expands rows.
     */
    fun refreshTree() {
        val rootNode = DebuggableNode(rootDebuggable)
        treeModel = DefaultTreeModel(rootNode as TreeNode)
        tree.model = treeModel

        // Expand all rows for convenience
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Node Classes
    // ---------------------------------------------------------------------------------------

    /**
     * Represents an entire ImgDebuggable object in the tree (a parent node).
     * If 'expandedStates[debuggable]' is true, we add:
     *  - A child node for each debug-option key
     *  - Child subnodes for each child ImgDebuggable
     */
    inner class DebuggableNode(val debuggable: ImgDebuggable) : CheckBoxTreeNode() {

        init {
            userObject = debuggable
            // Add child debug-option nodes
            debuggable.availableDbgOptions.keys.forEach { key ->
                add(DebugOptionNode(debuggable, key))
            }

            // If expanded: add child ImgDebuggable nodes as well
            val showChildren = expandedStates.getOrDefault(debuggable, true)
            if (showChildren) {
                debuggable.dbgChildren.forEach { child ->
                    add(DebuggableNode(child))
                }
            }
        }

        override fun toString(): String {
            // Attempt a friendly name from class's simpleName, fallback to getClass name
            val cname = debuggable::class.simpleName ?: debuggable.javaClass.simpleName
            return cname ?: "(Unnamed Debuggable)"
        }
    }

    /**
     * Represents a single debug option within some ImgDebuggable. This is a leaf node.
     */
    inner class DebugOptionNode(val debugObj: ImgDebuggable, val optionKey: String) : CheckBoxTreeNode() {
        init {
            userObject = optionKey
            allowsChildren = false
        }

        override fun toString(): String = optionKey
    }

    // ---------------------------------------------------------------------------------------
    // Custom TreeCellRenderer that displays JCheckBoxes
    // ---------------------------------------------------------------------------------------

    private inner class CheckBoxNodeRenderer : TreeCellRenderer {
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
            if (value !is CheckBoxTreeNode) {
                // Fallback to default (should rarely happen)
                val defRenderer = DefaultTreeCellRenderer()
                return defRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            }

            when (value) {
                is DebuggableNode -> {
                    val compDbg = value.debuggable
                    checkBox.text = value.toString()
                    checkBox.isSelected = expandedStates.getOrDefault(compDbg, true)
                    // Always enabled for toggling
                    checkBox.isEnabled = true
                }
                is DebugOptionNode -> {
                    checkBox.text = value.optionKey
                    checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)

                    // If the parent is toggled OFF, disable this child
                    val parentDbg = findAncestorDebuggable(value)
                    checkBox.isEnabled = parentDbg?.let { expandedStates[it] } ?: true
                }
            }
            return checkBox
        }

        /**
         * Find the nearest ancestor that is a DebuggableNode (returns its ImgDebuggable).
         */
        private fun findAncestorDebuggable(node: CheckBoxTreeNode): ImgDebuggable? {
            var parent = node.parent
            while (parent != null) {
                if (parent is DebuggableNode) return parent.debuggable
                parent = parent.parent
            }
            return null
        }
    }

    // ---------------------------------------------------------------------------------------
    // Custom TreeCellEditor that handles the toggling logic
    // ---------------------------------------------------------------------------------------

    private inner class CheckBoxNodeEditor : AbstractCellEditor(), TreeCellEditor {
        private val checkBox = JCheckBox()
        private var currentNode: CheckBoxTreeNode? = null

        init {
            checkBox.addActionListener(object : ActionListener {
                override fun actionPerformed(e: ActionEvent?) {
                    // User toggled the checkbox => commit now
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
            if (value is DebuggableNode) {
                val dbg = value.debuggable
                checkBox.text = value.toString()
                checkBox.isSelected = expandedStates.getOrDefault(dbg, true)
            } else if (value is DebugOptionNode) {
                checkBox.text = value.optionKey
                checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
            }
            return checkBox
        }

        override fun getCellEditorValue(): Any {
            currentNode?.let { node ->
                when (node) {
                    is DebuggableNode -> {
                        val dbg = node.debuggable
                        val newValue = checkBox.isSelected
                        expandedStates[dbg] = newValue
                        // Refresh the tree structure to show/hide child nodes
                        SwingUtilities.invokeLater { refreshTree() }
                    }
                    is DebugOptionNode -> {
                        val wasEnabled = node.debugObj.isDbgOptionEnabled(node.optionKey)
                        val newEnabled = checkBox.isSelected
                        node.debugObj.setDbgOption(node.optionKey, newEnabled)

                        // If turning debug option ON => open debug window. If OFF => close it.
                        if (newEnabled && !wasEnabled) {
                            openDebugWindow(node.debugObj, node.optionKey)
                        } else if (!newEnabled && wasEnabled) {
                            closeDebugWindow(node.debugObj, node.optionKey)
                        }
                    }
                }
            }
            return currentNode ?: ""
        }
    }

    // ---------------------------------------------------------------------------------------
    // Debug Window Logic
    // ---------------------------------------------------------------------------------------

    private fun openDebugWindow(debuggable: ImgDebuggable, optionKey: String) {
        val key = debuggable to optionKey
        // If already open, just bring to front
        debugWindows[key]?.let {
            it.requestFocus()
            return
        }
        // Otherwise create new window
        val frame = DebugImageWindow("$optionKey Debug",{
            debuggable.dbgData[optionKey]
        })
        debugWindows[key] = frame
    }

    private fun closeDebugWindow(debuggable: ImgDebuggable, optionKey: String) {
        val key = debuggable to optionKey
        debugWindows.remove(key)?.dispose()
    }
}

/**
 * Base class for nodes in the JTree that use checkboxes.
 * Subclasses override `toString()` or store relevant userObject data.
 */
open class CheckBoxTreeNode : DefaultMutableTreeNode() {
    override fun isLeaf(): Boolean = !allowsChildren
}