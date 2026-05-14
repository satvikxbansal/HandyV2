package com.handy.core.screen

/**
 * Flattens a [ScreenTextSnapshot] to a YAML-ish form, one line per
 * visible node. This is the payload that goes into the
 * `<screen_ui>` block of `PromptCatalog.screenTextAddendum(...)`.
 *
 * Each line looks like:
 *
 * ```
 * [Button] "Send" (id/send_btn) @ 120,600-280,660
 * ```
 *
 * Node lines are:
 *  - `[role]` role tag (EditText, Button, TextView, …).
 *  - `"text"` (or `"contentDescription"` when text is missing) — never both,
 *     to keep the serialization token-efficient.
 *  - `(id/<suffix>)` when a `viewIdResourceName` is present; only the
 *     suffix after the `/` ships to the LLM.
 *  - `@ x1,y1-x2,y2` bounds.
 *
 * Depth / node caps are applied at read-time in `AccessibilityTreeReader`
 * so the serializer does not need to enforce them.
 */
object ScreenTextSerializer {

    fun flatten(snapshot: ScreenTextSnapshot): String {
        val out = StringBuilder()
        walk(snapshot.root, out)
        return out.toString().trimEnd('\n')
    }

    private fun walk(node: UiNode, out: StringBuilder) {
        if (shouldEmit(node)) {
            node.markId?.takeIf { it.isNotBlank() }?.let { id ->
                out.append(id)
                out.append(' ')
            }
            out.append('[')
            out.append(node.role)
            out.append(']')

            val label = node.text?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.takeIf { it.isNotBlank() }
            if (label != null) {
                out.append(' ')
                out.append('"')
                out.append(label.replace('"', '\''))
                out.append('"')
            }

            node.viewIdResourceName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { id ->
                out.append(" (id/")
                out.append(id)
                out.append(')')
            }

            val b = node.boundsInScreen
            if (b != IntRect.ZERO) {
                out.append(" @ ")
                out.append(b.left)
                out.append(',')
                out.append(b.top)
                out.append('-')
                out.append(b.right)
                out.append(',')
                out.append(b.bottom)
            }

            if (node.clickable) out.append(" clickable")
            if (node.scrollable) out.append(" scrollable")
            if (node.enabled) out.append(" enabled") else out.append(" disabled")
            out.append('\n')
        }
        node.children.forEach { walk(it, out) }
    }

    private fun shouldEmit(node: UiNode): Boolean {
        val hasLabel = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
        val hasId = !node.viewIdResourceName.isNullOrBlank()
        val interactive = node.clickable || node.scrollable
        return hasLabel || hasId || interactive
    }
}
