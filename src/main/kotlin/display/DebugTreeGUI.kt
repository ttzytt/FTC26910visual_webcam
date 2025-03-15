package org.webcam_visual.display

import DebugImageWindow
import org.webcam_visual.ImgDebuggable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.tree.*

class DebugTreeGUI(private val rootDebuggable: ImgDebuggable) : JFrame("Debug Controls") {

    // Whether each ImgDebuggable is "expanded" in the tree view (not the same as "enabled").
    private val expandedStates = mutableMapOf<ImgDebuggable, Boolean>()

    // Track open debug windows: (debuggable, optionKey) -> window
    private val debugWindows = mutableMapOf<Pair<ImgDebuggable, String>, DebugImageWindow>()

    private val tree: JTree
    private var treeModel: DefaultTreeModel? = null

    init {
        expandedStates[rootDebuggable] = true
        tree = JTree().apply {
            cellRenderer = CheckBoxNodeRenderer()
            cellEditor = CheckBoxNodeEditor()
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
    }

    fun refreshTree() {
        val rootNode = DebuggableNode(rootDebuggable)
        treeModel = DefaultTreeModel(rootNode as TreeNode)
        tree.model = treeModel

        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    // ---------------------------------------------------------------------------
    // Node Classes
    // ---------------------------------------------------------------------------

    inner class DebuggableNode(val debuggable: ImgDebuggable) : CheckBoxTreeNode() {

        init {
            setUserObject(debuggable)
            val showChildren = expandedStates.getOrDefault(debuggable, true)

            // Add child debug-option nodes
            debuggable.availableDbgOptions.keys.forEach { key ->
                add(DebugOptionNode(debuggable, key))
            }
            // Recursively add child ImgDebuggable nodes if expanded
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
    // RENDERER
    // ---------------------------------------------------------------------------

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
                return DefaultTreeCellRenderer().getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus
                )
            }
            when (value) {
                is DebuggableNode -> {
                    checkBox.text = value.toString()
                    // A parent node's "selected" means "all children ON"
                    val dbg = value.debuggable
                    checkBox.isSelected = areAllChildrenOn(dbg)
                    checkBox.isEnabled = true
                }
                is DebugOptionNode -> {
                    checkBox.text = value.optionKey
                    // A child node's "selected" means the actual debug option is ON
                    checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
                    // If parent is toggled OFF, optionally disable (or you can leave them clickable).
                    val parentDbg = findAncestorDebuggable(value)
                    val parentEnabled = parentDbg?.let { areAllChildrenOn(it) } ?: true
                    checkBox.isEnabled = parentEnabled
                }
            }
            return checkBox
        }
        private fun findAncestorDebuggable(node: CheckBoxTreeNode): ImgDebuggable? {
            var p = node.parent
            while (p != null) {
                if (p is DebuggableNode) return p.debuggable
                p = p.parent
            }
            return null
        }
    }

    // ---------------------------------------------------------------------------
    // EDITOR (handles toggling)
    // ---------------------------------------------------------------------------

    private inner class CheckBoxNodeEditor : AbstractCellEditor(), TreeCellEditor {
        private val checkBox = JCheckBox()
        private var currentNode: CheckBoxTreeNode? = null

        init {
            checkBox.addActionListener(object : ActionListener {
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

            if (value is DebuggableNode) {
                checkBox.text = value.toString()
                val dbg = value.debuggable
                // This parent is considered "on" if all children are on
                checkBox.isSelected = areAllChildrenOn(dbg)
            } else if (value is DebugOptionNode) {
                checkBox.text = value.optionKey
                checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
            }
            return checkBox
        }

        override fun getCellEditorValue(): Any {
            val newValue = checkBox.isSelected
            currentNode?.let { node ->
                when (node) {
                    is DebuggableNode -> {
                        // Toggling a parent => recursively set all child debug options ON/OFF
                        toggleDebuggableRecursively(node.debuggable, newValue)
                        // Also update expanded state for display
                        expandedStates[node.debuggable] = newValue
                    }
                    is DebugOptionNode -> {
                        // Toggling a child => set this single debug option
                        val wasEnabled = node.debugObj.isDbgOptionEnabled(node.optionKey)
                        node.debugObj.setDbgOption(node.optionKey, newValue)
                        if (newValue && !wasEnabled) {
                            openDebugWindow(node.debugObj, node.optionKey)
                        } else if (!newValue && wasEnabled) {
                            closeDebugWindow(node.debugObj, node.optionKey)
                        }

                        // After toggling one child, we might need to update the parent(s):
                        syncParentStatesUp(node)
                    }
                }
                SwingUtilities.invokeLater {
                    refreshTree()
                }
            }
            return currentNode ?: ""
        }
    }

    // ---------------------------------------------------------------------------
    // Recursive Toggling Helpers
    // ---------------------------------------------------------------------------

    /**
     * Recursively toggles an entire debug object:
     *  - sets all debug-options in [debuggable] to [newVal].
     *  - if [newVal] is false, close windows. if [newVal] is true, open windows for all child options.
     *  - recurses into child debuggables as well.
     */
    private fun toggleDebuggableRecursively(debuggable: ImgDebuggable, newVal: Boolean) {
        // Toggle all debug options in this debuggable
        for (optionKey in debuggable.availableDbgOptions.keys) {
            val wasEnabled = debuggable.isDbgOptionEnabled(optionKey)
            debuggable.setDbgOption(optionKey, newVal)
            if (newVal && !wasEnabled) {
                openDebugWindow(debuggable, optionKey)
            } else if (!newVal && wasEnabled) {
                closeDebugWindow(debuggable, optionKey)
            }
        }
        // Recurse into child components
        for (child in debuggable.dbgChildren) {
            toggleDebuggableRecursively(child, newVal)
        }
    }

    /**
     * After toggling a single child, we climb up the tree to see if:
     *  - All siblings are ON => parent is ON
     *  - Otherwise => parent is OFF
     * Then we keep going up until the root.
     */
    private fun syncParentStatesUp(childNode: CheckBoxTreeNode) {
        var parent = childNode.parent
        while (parent is DebuggableNode) {
            val dbg = parent.debuggable
            val allOn = areAllChildrenOn(dbg)
            // If parent's state doesn't match 'allOn', flip it and recursively toggle if needed
            val parentCurrentlyAllOn = areAllDebugOptionsEnabled(dbg) && areAllChildrenParentsOn(dbg)
            if (allOn != parentCurrentlyAllOn) {
                // We flip the entire parent's toggles
                toggleDebuggableRecursively(dbg, allOn)
            }
            parent = parent.parent
        }
    }

    /**
     * Returns true if *all* debug options in [debuggable] are currently ON
     * and also all of its child debug options (recursively) are ON.
     */
    private fun areAllChildrenOn(debuggable: ImgDebuggable): Boolean {
        // 1) Check this debuggable's own debug options
        for ((optionKey, enabled) in debuggable.availableDbgOptions) {
            if (!enabled) return false
        }
        // 2) Recurse into child debuggables
        for (child in debuggable.dbgChildren) {
            if (!areAllChildrenOn(child)) return false
        }
        return true
    }

    /**
     * Returns true if *every* debugOption in this single debug object is ON.
     * This does NOT check child debugglables. (Helper used in syncParentStatesUp)
     */
    private fun areAllDebugOptionsEnabled(dbg: ImgDebuggable): Boolean {
        for (opt in dbg.availableDbgOptions.values) {
            if (!opt) return false
        }
        return true
    }

    /**
     * Returns true if all direct child debuggables are fully ON (recursively).
     */
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
        val key = debuggable to optionKey
        debugWindows[key]?.let { it.requestFocus(); return }
        val frame = DebugImageWindow("$optionKey Debug", {
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
 * Base class for checkbox tree nodes.
 */
open class CheckBoxTreeNode : DefaultMutableTreeNode() {
    override fun isLeaf(): Boolean = !allowsChildren
}
