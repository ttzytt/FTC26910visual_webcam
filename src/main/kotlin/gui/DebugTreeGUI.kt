package org.webcam_visual.gui

import DebugImageWindow
import org.webcam_visual.common.ImgDebuggable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.*
import javax.swing.tree.*

/**
 * A Swing-based debug control tree for ImgDebuggable objects.
 *
 * When started, if any debug option is already toggled on, its associated debug window will pop up.
 * Debug window titles use a recursive full path (e.g. “preproc.bilatfilter.output”) to prevent ambiguity.
 */
class DebugTreeGUI(private val rootDebuggable: ImgDebuggable) : JFrame("Debug Controls") {

    // Whether each ImgDebuggable is "expanded" in the tree view (not the same as "enabled").
    private val expandedStates = mutableMapOf<ImgDebuggable, Boolean>()

    // Track open debug windows: (debuggable, optionKey) -> window.
    private val debugWindows = mutableMapOf<Pair<ImgDebuggable, String>, DebugImageWindow>()

    // Map from (ImgDebuggable, optionKey) to its full path string (e.g. "preproc.bilatfilter.output")
    private val fullPathMap = mutableMapOf<Pair<ImgDebuggable, String>, String>()

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

        // Compute full path mapping for every debug option.
        computeFullPaths(rootDebuggable)
        // Check at startup: if any debug option is enabled, pop up its window.
        for ((key, fullPath) in fullPathMap) {
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
        // Use the debuggable’s simple class name as its identifier.
        val currentName = if (prefix.isEmpty()) {
            debuggable::class.simpleName ?: "root"
        } else {
            prefix
        }
        // Map each debug option for the current debuggable.
        for (optionKey in debuggable.availableDbgOptions.keys) {
            fullPathMap[Pair(debuggable, optionKey)] = "$currentName.$optionKey"
        }
        // Recurse into child debuggables.
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
    // Renderer
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
                    val dbg = value.debuggable
                    checkBox.isSelected = areAllChildrenOn(dbg)
                    checkBox.isEnabled = true
                }
                is DebugOptionNode -> {
                    checkBox.text = value.optionKey
                    checkBox.isSelected = value.debugObj.isDbgOptionEnabled(value.optionKey)
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
    // Editor (handles toggling)
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
        // Use the computed full path title (or fall back to "$optionKey Debug")
        val title = fullPathMap[key] ?: "$optionKey Debug"
        val window = DebugImageWindow(title, {
            debuggable.dbgData[optionKey]
        })
        debugWindows[key] = window
    }

    private fun closeDebugWindow(debuggable: ImgDebuggable, optionKey: String) {
        val key = Pair(debuggable, optionKey)
        debugWindows.remove(key)?.dispose()
    }
}

// ---------------------------------------------------------------------------
// Base class for checkbox tree nodes.
// ---------------------------------------------------------------------------
open class CheckBoxTreeNode : DefaultMutableTreeNode() {
    override fun isLeaf(): Boolean = !allowsChildren
}
