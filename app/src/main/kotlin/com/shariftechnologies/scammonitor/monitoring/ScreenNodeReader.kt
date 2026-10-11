package com.shariftechnologies.scammonitor.monitoring

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.shariftechnologies.scammonitor.monitor.ScreenNode

/**
 * Reads visible text from an accessibility tree with hard limits on size and depth. Password fields and
 * invisible nodes are skipped. Editable fields are kept only as positioned nodes so the analyzer can read an
 * address bar's host; their text is never treated as message text.
 */
object ScreenNodeReader {
    class Snapshot(val nodes: List<ScreenNode>, val screenHeight: Int)

    private const val MAX_NODES = 700
    private const val MAX_DEPTH = 30
    private const val MAX_TEXT = 1500

    fun read(root: AccessibilityNodeInfo): Snapshot {
        val window = Rect()
        root.getBoundsInScreen(window)
        val out = ArrayList<ScreenNode>()
        var visited = 0
        val rect = Rect()

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (visited >= MAX_NODES || depth > MAX_DEPTH) return
            visited++
            if (!node.isVisibleToUser) return
            if (!node.isPassword) {
                val text = node.text?.toString()?.takeIf { it.isNotBlank() }
                    ?: node.contentDescription?.takeIf { !node.isClickable }?.toString()?.takeIf { it.isNotBlank() }
                if (text != null) {
                    node.getBoundsInScreen(rect)
                    out += ScreenNode(text.take(MAX_TEXT), rect.top - window.top, rect.left, node.isEditable)
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try { walk(child, depth + 1) } finally { recycle(child) }
            }
        }
        walk(root, 0)
        return Snapshot(out, window.height().coerceAtLeast(1))
    }

    @Suppress("DEPRECATION")
    fun recycle(node: AccessibilityNodeInfo) {
        try { node.recycle() } catch (e: RuntimeException) { /* already recycled or not needed on this API level */ }
    }
}
