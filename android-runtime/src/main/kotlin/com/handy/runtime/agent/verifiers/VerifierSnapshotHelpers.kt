package com.handy.runtime.agent.verifiers

import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.UiNode

internal fun GroundingSnapshot.foregroundPackageName(): String? =
    screenText?.packageName ?: toolContext.packageName.takeIf { it.isNotBlank() }

internal fun GroundingSnapshot.screenChangedFrom(before: GroundingSnapshot): Boolean =
    !rootBoundsHash.isNullOrBlank() &&
        !before.rootBoundsHash.isNullOrBlank() &&
        !rootBoundsHash.equals(before.rootBoundsHash, ignoreCase = true) ||
        !treeHash.isNullOrBlank() &&
        !before.treeHash.isNullOrBlank() &&
        !treeHash.equals(before.treeHash, ignoreCase = true)

internal fun GroundingSnapshot.visibleTextValues(): List<String> =
    panelSnapshot?.marks.orEmpty().flatMap { mark ->
        listOfNotNull(mark.text, mark.contentDescription, mark.viewIdSuffix)
    } + screenText?.root?.visibleTextValues().orEmpty()

private fun UiNode.visibleTextValues(): List<String> {
    val out = mutableListOf<String>()
    fun walk(node: UiNode) {
        listOfNotNull(
            node.text,
            node.contentDescription,
            node.viewIdResourceName,
        ).forEach { value ->
            if (value.isNotBlank()) out += value
        }
        node.children.forEach(::walk)
    }
    walk(this)
    return out
}

internal fun AccessibilityMark.matchesViewIdOrRole(
    viewId: String?,
    role: String?,
): Boolean {
    val viewMatches = viewId.isNullOrBlank() ||
        viewIdSuffix?.substringAfterLast('/')?.equals(viewId.substringAfterLast('/'), ignoreCase = true) == true
    val roleMatches = role.isNullOrBlank() ||
        this.role.equals(role, ignoreCase = true) ||
        this.role.replace(" ", "").equals(role.replace(" ", ""), ignoreCase = true)
    return viewMatches && roleMatches
}
