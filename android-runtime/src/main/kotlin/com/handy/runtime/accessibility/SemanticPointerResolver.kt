@file:Suppress("DEPRECATION")

package com.handy.runtime.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.IntRect
import kotlin.math.max
import kotlin.math.min

/**
 * Candidate-ranking pointer resolver.
 *
 * The model should normally emit `[POINT:markId=m3]` from the prompt's
 * screen-ui block. Text/viewId/desc and fuzzy matching remain as fallback
 * grounding, with confidence and ambiguity reported to callers.
 */
class SemanticPointerResolver(
    private val service: () -> AccessibilityService?,
    private val applicationPackageName: String? = null,
) {

    data class ResolvedPointTarget(
        val bounds: IntRect,
        val node: AccessibilityNodeInfo?,
        val source: ResolutionSource,
        val confidence: Float,
        val candidateCount: Int,
        val failureReason: ResolutionFailureReason? = null,
        val debugCandidates: List<TargetCandidate> = emptyList(),
        val markId: String? = null,
        val role: String? = null,
        val text: String? = null,
        val viewId: String? = null,
        val desc: String? = null,
    )

    data class TargetCandidate(
        val markId: String?,
        val label: String?,
        val role: String?,
        val viewId: String?,
        val bounds: IntRect,
        val actionable: Boolean,
        val enabled: Boolean,
        val visible: Boolean,
        val score: Float,
        val reasons: List<String>,
    )

    enum class ResolutionSource {
        MARK_ID,
        VIEW_ID,
        TEXT_ROLE,
        DESCRIPTION_ROLE,
        BOUNDS_OVERLAP,
        FUZZY_TEXT,
        HEURISTIC,
    }

    enum class ResolutionFailureReason {
        NO_CANDIDATES,
        NO_MATCH,
        LOW_CONFIDENCE,
        AMBIGUOUS,
    }

    fun resolve(
        spec: AssistantMarkupParser.SemanticPoint,
        fallbackMarks: List<AccessibilityMark> = emptyList(),
        expectedPackage: String? = null,
        expectedWindowId: Int? = null,
    ): ResolvedPointTarget? {
        val runtimeCandidates = mutableListOf<RuntimeCandidate>()
        val ownedNodes = mutableListOf<AccessibilityNodeInfo>()
        val root = runCatching { service()?.rootInActiveWindow }.getOrNull()
        if (root != null) {
            val rootPackage = root.packageName?.toString()
            if (!applicationPackageName.isNullOrBlank() &&
                rootPackage.equals(applicationPackageName, ignoreCase = true)
            ) {
                ownedNodes += root
            } else {
                collectLiveCandidates(root, runtimeCandidates, ownedNodes)
            }
        }
        fallbackMarks.forEach { mark ->
            runtimeCandidates += RuntimeCandidate.fromMark(mark, expectedPackage, expectedWindowId)
        }

        if (runtimeCandidates.isEmpty()) {
            recycleAll(ownedNodes)
            return null
        }

        val duplicateLabels = runtimeCandidates
            .mapNotNull { normalize(it.label.orEmpty()).takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()

        val scored = runtimeCandidates
            .mapNotNull { candidate ->
                score(candidate, spec, fallbackMarks, duplicateLabels, expectedPackage, expectedWindowId)
                    ?.let { scored -> ScoredCandidate(candidate, scored) }
            }
            .sortedByDescending { it.debug.score }

        if (scored.isEmpty()) {
            recycleAll(ownedNodes)
            return null
        }

        val best = scored.first()
        val runnerUp = scored.drop(1).firstOrNull()
        val failure = when {
            best.debug.score < LOW_CONFIDENCE_SCORE -> ResolutionFailureReason.LOW_CONFIDENCE
            runnerUp != null &&
                best.debug.score - runnerUp.debug.score <= AMBIGUITY_SCORE_DELTA &&
                best.source != ResolutionSource.MARK_ID -> ResolutionFailureReason.AMBIGUOUS
            else -> null
        }
        val nodeToReturn = best.runtime.node
        recycleAll(ownedNodes.filter { it !== nodeToReturn })

        return ResolvedPointTarget(
            bounds = best.runtime.bounds,
            node = nodeToReturn,
            source = best.source,
            confidence = (best.debug.score / MARK_ID_EXACT_SCORE).coerceIn(0f, 1f),
            candidateCount = scored.size,
            failureReason = failure,
            debugCandidates = scored.take(MAX_DEBUG_CANDIDATES).map { it.debug },
            markId = best.runtime.markId,
            role = best.runtime.role,
            text = best.runtime.text,
            viewId = best.runtime.viewId,
            desc = best.runtime.contentDescription,
        )
    }

    private fun collectLiveCandidates(
        root: AccessibilityNodeInfo,
        out: MutableList<RuntimeCandidate>,
        ownedNodes: MutableList<AccessibilityNodeInfo>,
    ) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        ownedNodes += root
        var visited = 0
        var markIndex = 0
        while (queue.isNotEmpty() && visited < HARD_VISIT_CAP) {
            val node = queue.removeFirst()
            visited++
            val markId = if (isMarkableNode(node)) {
                markIndex += 1
                "m$markIndex"
            } else {
                null
            }
            RuntimeCandidate.fromNode(node, markId)?.let(out::add)
            for (i in 0 until node.childCount) {
                val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
                ownedNodes += child
                queue.addLast(child)
            }
        }
    }

    private fun isMarkableNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        if (!node.isVisibleToUser) return false
        if (!node.text.isNullOrBlank()) return true
        if (!node.contentDescription.isNullOrBlank()) return true
        return node.isClickable || node.isScrollable || node.isEditable
    }

    private fun score(
        candidate: RuntimeCandidate,
        spec: AssistantMarkupParser.SemanticPoint,
        fallbackMarks: List<AccessibilityMark>,
        duplicateLabels: Map<String, Int>,
        expectedPackage: String?,
        expectedWindowId: Int?,
    ): CandidateScore? {
        var score = 0f
        var source = ResolutionSource.HEURISTIC
        var matchedIdentifier = false
        val reasons = mutableListOf<String>()

        if (!spec.markId.isNullOrBlank() && spec.markId.equals(candidate.markId, ignoreCase = true)) {
            score += MARK_ID_EXACT_SCORE
            source = ResolutionSource.MARK_ID
            matchedIdentifier = true
            reasons += "markId exact"
        }

        spec.viewId?.takeIf { it.isNotBlank() }?.let { targetViewId ->
            val candidateViewId = candidate.viewId.orEmpty()
            when {
                candidateViewId.equals(targetViewId, ignoreCase = true) -> {
                    score += 80f
                    source = ResolutionSource.VIEW_ID
                    matchedIdentifier = true
                    reasons += "viewId exact"
                }
                candidateViewId.endsWith("/$targetViewId", ignoreCase = true) ||
                    candidateViewId.endsWith(":$targetViewId", ignoreCase = true) ||
                    candidateViewId.substringAfterLast('/').equals(targetViewId, ignoreCase = true) -> {
                    score += 70f
                    source = ResolutionSource.VIEW_ID
                    matchedIdentifier = true
                    reasons += "viewId suffix"
                }
            }
        }

        spec.text?.takeIf { it.isNotBlank() }?.let { targetText ->
            val candidateText = candidate.text.orEmpty()
            if (candidateText.equals(targetText, ignoreCase = true)) {
                val roleBonus = if (roleMatches(candidate.role, spec.role)) 20f else 0f
                score += 50f + roleBonus
                source = ResolutionSource.TEXT_ROLE
                matchedIdentifier = true
                reasons += if (roleBonus > 0f) "text+role exact" else "text exact"
            } else {
                fuzzyScore(targetText, candidateText)?.let { fuzzy ->
                    score += fuzzy
                    source = ResolutionSource.FUZZY_TEXT
                    matchedIdentifier = true
                    reasons += "fuzzy text"
                }
            }
        }

        spec.contentDescription?.takeIf { it.isNotBlank() }?.let { targetDesc ->
            val candidateDesc = candidate.contentDescription.orEmpty()
            if (candidateDesc.equals(targetDesc, ignoreCase = true)) {
                val roleBonus = if (roleMatches(candidate.role, spec.role)) 15f else 0f
                score += 50f + roleBonus
                source = ResolutionSource.DESCRIPTION_ROLE
                matchedIdentifier = true
                reasons += if (roleBonus > 0f) "desc+role exact" else "desc exact"
            }
        }

        if (overlapsMatchedMark(candidate, spec, fallbackMarks)) {
            score += 15f
            if (source == ResolutionSource.HEURISTIC) source = ResolutionSource.BOUNDS_OVERLAP
            matchedIdentifier = true
            reasons += "cached bounds overlap"
        }

        if (!matchedIdentifier) return null

        if (candidate.actionable) {
            score += 20f
            reasons += "actionable"
        }
        if (candidate.enabled) {
            score += 15f
            reasons += "enabled"
        } else {
            score -= 40f
            reasons += "disabled"
        }
        if (candidate.visible && candidate.bounds.width > 0 && candidate.bounds.height > 0) {
            score += 15f
            reasons += "visible"
        } else {
            score -= 50f
            reasons += "offscreen"
        }
        if (candidate.bounds.width < MIN_TARGET_PX || candidate.bounds.height < MIN_TARGET_PX) {
            score -= 20f
            reasons += "tiny"
        }

        expectedPackage?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = candidate.packageName
            if (!actual.isNullOrBlank() && !actual.equals(expected, ignoreCase = true)) {
                score -= 80f
                reasons += "package mismatch"
            }
        }
        expectedWindowId?.let { expected ->
            val actual = candidate.windowId
            if (actual != null && actual != expected) {
                score -= 80f
                reasons += "window mismatch"
            }
        }

        normalize(candidate.label.orEmpty()).takeIf { it.isNotBlank() }?.let { label ->
            if ((duplicateLabels[label] ?: 0) > 1) {
                score -= 20f
                reasons += "duplicate label"
            }
        }

        if (score <= 0f) return null
        return CandidateScore(
            source = source,
            debug = TargetCandidate(
                markId = candidate.markId,
                label = candidate.redactForDiagnostics(candidate.label, isPassword = candidate.isPassword),
                role = candidate.redactForDiagnostics(candidate.role),
                viewId = candidate.redactForDiagnostics(candidate.viewId?.substringAfterLast('/')),
                bounds = candidate.bounds,
                actionable = candidate.actionable,
                enabled = candidate.enabled,
                visible = candidate.visible,
                score = score,
                reasons = reasons,
            ),
        )
    }

    private fun overlapsMatchedMark(
        candidate: RuntimeCandidate,
        spec: AssistantMarkupParser.SemanticPoint,
        fallbackMarks: List<AccessibilityMark>,
    ): Boolean {
        if (fallbackMarks.isEmpty()) return false
        return fallbackMarks.any { mark ->
            val markMatchesSpec =
                (!spec.markId.isNullOrBlank() && spec.markId.equals(mark.markId, ignoreCase = true)) ||
                    (!spec.viewId.isNullOrBlank() && spec.viewId.equals(mark.viewIdSuffix, ignoreCase = true)) ||
                    (!spec.text.isNullOrBlank() && spec.text.equals(mark.text, ignoreCase = true)) ||
                    (!spec.contentDescription.isNullOrBlank() &&
                        spec.contentDescription.equals(mark.contentDescription, ignoreCase = true))
            markMatchesSpec && candidate.bounds.overlapRatio(mark.boundsRect()) >= 0.5f
        }
    }

    private fun roleMatches(role: String?, roleHint: String?): Boolean {
        val hint = normalize(roleHint.orEmpty())
        if (hint.isBlank()) return true
        val normalizedRole = normalize(role.orEmpty())
        return normalizedRole.contains(hint) ||
            (hint == "textfield" && normalizedRole.contains("edittext")) ||
            (hint == "button" && normalizedRole.contains("button"))
    }

    private fun fuzzyScore(target: String, candidate: String): Float? {
        val a = normalize(target)
        val b = normalize(candidate)
        if (a.length < 3 || b.length < 3) return null
        val distance = levenshtein(a, b)
        val maxDistance = max(2, a.length / 4)
        if (distance > maxDistance) return null
        val ratio = 1f - (distance.toFloat() / max(a.length, b.length).toFloat())
        return (ratio * 40f).coerceIn(0f, 40f)
    }

    private fun recycleAll(nodes: List<AccessibilityNodeInfo>) {
        nodes.forEach { node -> runCatching { node.recycle() } }
    }

    private data class RuntimeCandidate(
        val markId: String?,
        val text: String?,
        val contentDescription: String?,
        val label: String?,
        val viewId: String?,
        val role: String?,
        val bounds: IntRect,
        val actionable: Boolean,
        val enabled: Boolean,
        val visible: Boolean,
        val node: AccessibilityNodeInfo?,
        val packageName: String?,
        val windowId: Int?,
        val isPassword: Boolean,
    ) {
        fun redactForDiagnostics(value: String?, isPassword: Boolean = false): String? =
            ScreenRedactor.redactText(
                value = value,
                context = redactionContext,
                isPassword = isPassword,
                diagnostics = true,
            )

        private val redactionContext: String
            get() = listOfNotNull(role, viewId, contentDescription, label)
                .joinToString(" ")

        companion object {
            fun fromNode(node: AccessibilityNodeInfo, markId: String?): RuntimeCandidate? {
                val rect = Rect().also { node.getBoundsInScreen(it) }
                if (rect.width() <= 0 || rect.height() <= 0) return null
                val isPassword = node.isPassword
                val context = listOfNotNull(
                    node.className?.toString(),
                    node.viewIdResourceName,
                    node.contentDescription?.toString(),
                ).joinToString(" ")
                val text = ScreenRedactor.redactText(
                    value = node.text?.toString(),
                    context = context,
                    isPassword = isPassword,
                )
                val desc = ScreenRedactor.redactText(
                    value = node.contentDescription?.toString(),
                    context = context,
                    isPassword = isPassword,
                )
                val role = node.className?.toString()?.substringAfterLast('.').orEmpty()
                    .ifBlank { if (node.isClickable) "Button" else "Node" }
                return RuntimeCandidate(
                    markId = markId,
                    text = text,
                    contentDescription = desc,
                    label = text ?: desc,
                    viewId = node.viewIdResourceName,
                    role = role,
                    bounds = IntRect(rect.left, rect.top, rect.right, rect.bottom),
                    actionable = node.isClickable || node.isScrollable || node.isLongClickable || node.isEditable,
                    enabled = node.isEnabled,
                    visible = node.isVisibleToUser,
                    node = node,
                    packageName = node.packageName?.toString()?.takeIf { it.isNotBlank() },
                    windowId = node.windowId,
                    isPassword = isPassword,
                )
            }

            fun fromMark(
                mark: AccessibilityMark,
                expectedPackage: String?,
                expectedWindowId: Int?,
            ): RuntimeCandidate {
                val redactedMark = ScreenRedactor.redactMark(mark)
                return RuntimeCandidate(
                    markId = redactedMark.markId,
                    text = redactedMark.text,
                    contentDescription = redactedMark.contentDescription,
                    label = redactedMark.text ?: redactedMark.contentDescription ?: redactedMark.viewIdSuffix,
                    viewId = redactedMark.viewIdSuffix,
                    role = redactedMark.role,
                    bounds = IntRect(redactedMark.left, redactedMark.top, redactedMark.right, redactedMark.bottom),
                    actionable = redactedMark.clickable || redactedMark.scrollable || redactedMark.editable,
                    enabled = redactedMark.enabled,
                    visible = redactedMark.right > redactedMark.left && redactedMark.bottom > redactedMark.top,
                    node = null,
                    packageName = expectedPackage,
                    windowId = expectedWindowId,
                    isPassword = redactedMark.isPassword,
                )
            }
        }
    }

    private data class CandidateScore(
        val source: ResolutionSource,
        val debug: TargetCandidate,
    )

    private data class ScoredCandidate(
        val runtime: RuntimeCandidate,
        val result: CandidateScore,
    ) {
        val source: ResolutionSource get() = result.source
        val debug: TargetCandidate get() = result.debug
    }

    private fun AccessibilityMark.boundsRect(): IntRect =
        IntRect(left, top, right, bottom)

    private fun IntRect.overlapRatio(other: IntRect): Float {
        val left = max(left, other.left)
        val top = max(top, other.top)
        val right = min(right, other.right)
        val bottom = min(bottom, other.bottom)
        val overlapW = (right - left).coerceAtLeast(0)
        val overlapH = (bottom - top).coerceAtLeast(0)
        val overlapArea = overlapW * overlapH
        val minArea = min(width * height, other.width * other.height).coerceAtLeast(1)
        return overlapArea.toFloat() / minArea.toFloat()
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace('-', ' ')
            .replace('_', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    private companion object {
        const val MARK_ID_EXACT_SCORE = 100f
        const val LOW_CONFIDENCE_SCORE = 45f
        const val AMBIGUITY_SCORE_DELTA = 8f
        const val MAX_DEBUG_CANDIDATES = 5
        const val HARD_VISIT_CAP = 1200
        const val MIN_TARGET_PX = 12
    }
}
