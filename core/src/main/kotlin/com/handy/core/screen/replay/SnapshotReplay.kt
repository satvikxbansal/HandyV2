package com.handy.core.screen.replay

import com.handy.core.overlay.AccessibilityMark
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.privacy.ScreenRedactor
import com.handy.core.screen.IntRect
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class SnapshotReplayFile(
    val appGroup: String,
    val packageName: String,
    val screenId: String,
    val secureWindow: Boolean = false,
    val windowId: Int? = null,
    val marks: List<ReplayMark> = emptyList(),
    val cases: List<ReplayPointerCase> = emptyList(),
) {
    fun toAccessibilityMarks(): List<AccessibilityMark> =
        marks.map { it.toAccessibilityMark() }
}

@Serializable
data class ReplayMark(
    val markId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdSuffix: String? = null,
    val role: String,
    val bounds: List<Int>,
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val isPassword: Boolean = false,
    val isChecked: Boolean? = null,
) {
    fun toAccessibilityMark(): AccessibilityMark {
        require(bounds.size == 4) { "bounds must be [left, top, right, bottom]" }
        return AccessibilityMark(
            markId = markId,
            text = text,
            contentDescription = contentDescription,
            viewIdSuffix = viewIdSuffix,
            role = role,
            bounds = bounds.toIntArray(),
            clickable = clickable,
            scrollable = scrollable,
            editable = editable,
            enabled = enabled,
            isPassword = isPassword,
            isChecked = isChecked,
        )
    }
}

@Serializable
data class ReplayPointerCase(
    val id: String,
    val userText: String = "",
    val assistantText: String,
    val expectedMarkId: String? = null,
    val expectedOutcome: ReplayExpectedOutcome = ReplayExpectedOutcome.TARGET,
    val minConfidence: Float = 0.9f,
)

@Serializable
enum class ReplayExpectedOutcome {
    @SerialName("target")
    TARGET,

    @SerialName("ambiguous")
    AMBIGUOUS,

    @SerialName("no_target")
    NO_TARGET,
}

data class ReplayCaseResult(
    val appGroup: String,
    val screenId: String,
    val caseId: String,
    val passed: Boolean,
    val expectedOutcome: ReplayExpectedOutcome,
    val expectedMarkId: String?,
    val resolved: ReplayResolvedPointTarget?,
    val message: String,
)

data class ReplayAppReport(
    val appGroup: String,
    val results: List<ReplayCaseResult>,
) {
    val passed: Int get() = results.count { it.passed }
    val total: Int get() = results.size
    val accuracy: Double get() = if (total == 0) 1.0 else passed.toDouble() / total.toDouble()
}

object SnapshotReplay {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun decode(text: String): SnapshotReplayFile =
        json.decodeFromString(text)

    fun run(screen: SnapshotReplayFile): List<ReplayCaseResult> =
        screen.cases.map { case -> runCase(screen, case) }

    fun runCase(screen: SnapshotReplayFile, case: ReplayPointerCase): ReplayCaseResult {
        val pointing = AssistantMarkupParser.parsePoint(case.assistantText)
        if (screen.secureWindow) {
            val passed = case.expectedOutcome == ReplayExpectedOutcome.NO_TARGET &&
                (pointing.isNone || !pointing.hasPointer)
            return ReplayCaseResult(
                appGroup = screen.appGroup,
                screenId = screen.screenId,
                caseId = case.id,
                passed = passed,
                expectedOutcome = case.expectedOutcome,
                expectedMarkId = case.expectedMarkId,
                resolved = null,
                message = if (passed) "secure screen did not point" else "secure screen emitted a pointer",
            )
        }

        val spec = pointing.semantic
        if (spec == null) {
            val passed = case.expectedOutcome == ReplayExpectedOutcome.NO_TARGET && pointing.isNone
            return ReplayCaseResult(
                appGroup = screen.appGroup,
                screenId = screen.screenId,
                caseId = case.id,
                passed = passed,
                expectedOutcome = case.expectedOutcome,
                expectedMarkId = case.expectedMarkId,
                resolved = null,
                message = if (passed) "model intentionally emitted POINT:none" else "no semantic pointer emitted",
            )
        }

        val resolved = ReplaySemanticPointerResolver.resolve(
            spec = spec,
            fallbackMarks = screen.toAccessibilityMarks(),
            expectedPackage = screen.packageName,
            expectedWindowId = screen.windowId,
        )

        val passed = when (case.expectedOutcome) {
            ReplayExpectedOutcome.TARGET ->
                resolved != null &&
                    resolved.failureReason == null &&
                    resolved.markId.equalsNormalized(case.expectedMarkId) &&
                    resolved.confidence >= case.minConfidence

            ReplayExpectedOutcome.AMBIGUOUS ->
                resolved?.failureReason == ReplayResolutionFailureReason.AMBIGUOUS

            ReplayExpectedOutcome.NO_TARGET ->
                resolved == null || resolved.failureReason != null
        }

        return ReplayCaseResult(
            appGroup = screen.appGroup,
            screenId = screen.screenId,
            caseId = case.id,
            passed = passed,
            expectedOutcome = case.expectedOutcome,
            expectedMarkId = case.expectedMarkId,
            resolved = resolved,
            message = resolved?.let {
                "resolved markId=${it.markId} source=${it.source} confidence=${String.format(Locale.US, "%.2f", it.confidence)} failure=${it.failureReason}"
            } ?: "no resolved target",
        )
    }
}

data class ReplayResolvedPointTarget(
    val bounds: IntRect,
    val source: ReplayResolutionSource,
    val confidence: Float,
    val candidateCount: Int,
    val failureReason: ReplayResolutionFailureReason? = null,
    val debugCandidates: List<ReplayTargetCandidate> = emptyList(),
    val markId: String? = null,
    val role: String? = null,
    val text: String? = null,
    val viewId: String? = null,
    val desc: String? = null,
)

data class ReplayTargetCandidate(
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

enum class ReplayResolutionSource {
    MARK_ID,
    VIEW_ID,
    TEXT_ROLE,
    DESCRIPTION_ROLE,
    BOUNDS_OVERLAP,
    FUZZY_TEXT,
    HEURISTIC,
}

enum class ReplayResolutionFailureReason {
    NO_MATCH,
    LOW_CONFIDENCE,
    AMBIGUOUS,
}

object ReplaySemanticPointerResolver {
    fun resolve(
        spec: AssistantMarkupParser.SemanticPoint,
        fallbackMarks: List<AccessibilityMark>,
        expectedPackage: String? = null,
        expectedWindowId: Int? = null,
    ): ReplayResolvedPointTarget? {
        val candidates = fallbackMarks.map { mark ->
            ReplayRuntimeCandidate.fromMark(mark, expectedPackage, expectedWindowId)
        }
        if (candidates.isEmpty()) return null

        val duplicateLabels = candidates
            .mapNotNull { normalize(it.label.orEmpty()).takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()

        val scored = candidates
            .mapNotNull { candidate ->
                score(candidate, spec, fallbackMarks, duplicateLabels, expectedPackage, expectedWindowId)
                    ?.let { score -> ReplayScoredCandidate(candidate, score) }
            }
            .sortedByDescending { it.debug.score }

        if (scored.isEmpty()) return null

        val best = scored.first()
        val runnerUp = scored.drop(1).firstOrNull()
        val failure = when {
            best.debug.score < LOW_CONFIDENCE_SCORE ->
                ReplayResolutionFailureReason.LOW_CONFIDENCE

            runnerUp != null &&
                best.debug.score - runnerUp.debug.score <= AMBIGUITY_SCORE_DELTA &&
                best.source != ReplayResolutionSource.MARK_ID ->
                ReplayResolutionFailureReason.AMBIGUOUS

            else -> null
        }

        return ReplayResolvedPointTarget(
            bounds = best.runtime.bounds,
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

    private fun score(
        candidate: ReplayRuntimeCandidate,
        spec: AssistantMarkupParser.SemanticPoint,
        fallbackMarks: List<AccessibilityMark>,
        duplicateLabels: Map<String, Int>,
        expectedPackage: String?,
        expectedWindowId: Int?,
    ): ReplayCandidateScore? {
        var score = 0f
        var source = ReplayResolutionSource.HEURISTIC
        var matchedIdentifier = false
        val reasons = mutableListOf<String>()

        if (!spec.markId.isNullOrBlank() && spec.markId.equals(candidate.markId, ignoreCase = true)) {
            score += MARK_ID_EXACT_SCORE
            source = ReplayResolutionSource.MARK_ID
            matchedIdentifier = true
            reasons += "markId exact"
        }

        spec.viewId?.takeIf { it.isNotBlank() }?.let { targetViewId ->
            val candidateViewId = candidate.viewId.orEmpty()
            when {
                candidateViewId.equals(targetViewId, ignoreCase = true) -> {
                    score += 80f
                    source = ReplayResolutionSource.VIEW_ID
                    matchedIdentifier = true
                    reasons += "viewId exact"
                }

                candidateViewId.endsWith("/$targetViewId", ignoreCase = true) ||
                    candidateViewId.endsWith(":$targetViewId", ignoreCase = true) ||
                    candidateViewId.substringAfterLast('/').equals(targetViewId, ignoreCase = true) -> {
                    score += 70f
                    source = ReplayResolutionSource.VIEW_ID
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
                source = ReplayResolutionSource.TEXT_ROLE
                matchedIdentifier = true
                reasons += if (roleBonus > 0f) "text+role exact" else "text exact"
            } else {
                fuzzyScore(targetText, candidateText)?.let { fuzzy ->
                    score += fuzzy
                    source = ReplayResolutionSource.FUZZY_TEXT
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
                source = ReplayResolutionSource.DESCRIPTION_ROLE
                matchedIdentifier = true
                reasons += if (roleBonus > 0f) "desc+role exact" else "desc exact"
            }
        }

        if (overlapsMatchedMark(candidate, spec, fallbackMarks)) {
            score += 15f
            if (source == ReplayResolutionSource.HEURISTIC) {
                source = ReplayResolutionSource.BOUNDS_OVERLAP
            }
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
        return ReplayCandidateScore(
            source = source,
            debug = ReplayTargetCandidate(
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
        candidate: ReplayRuntimeCandidate,
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
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(
                    min(current[j - 1] + 1, previous[j] + 1),
                    previous[j - 1] + cost,
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
    }

    private const val MARK_ID_EXACT_SCORE = 100f
    private const val LOW_CONFIDENCE_SCORE = 45f
    private const val AMBIGUITY_SCORE_DELTA = 8f
    private const val MAX_DEBUG_CANDIDATES = 5
    private const val MIN_TARGET_PX = 12
}

private data class ReplayRuntimeCandidate(
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
        fun fromMark(
            mark: AccessibilityMark,
            expectedPackage: String?,
            expectedWindowId: Int?,
        ): ReplayRuntimeCandidate {
            val redacted = ScreenRedactor.redactMark(mark)
            return ReplayRuntimeCandidate(
                markId = redacted.markId,
                text = redacted.text,
                contentDescription = redacted.contentDescription,
                label = redacted.text ?: redacted.contentDescription ?: redacted.viewIdSuffix,
                viewId = redacted.viewIdSuffix,
                role = redacted.role,
                bounds = IntRect(redacted.left, redacted.top, redacted.right, redacted.bottom),
                actionable = redacted.clickable || redacted.scrollable || redacted.editable,
                enabled = redacted.enabled,
                visible = redacted.right > redacted.left && redacted.bottom > redacted.top,
                packageName = expectedPackage,
                windowId = expectedWindowId,
                isPassword = redacted.isPassword,
            )
        }
    }
}

private data class ReplayCandidateScore(
    val source: ReplayResolutionSource,
    val debug: ReplayTargetCandidate,
)

private data class ReplayScoredCandidate(
    val runtime: ReplayRuntimeCandidate,
    val result: ReplayCandidateScore,
) {
    val source: ReplayResolutionSource get() = result.source
    val debug: ReplayTargetCandidate get() = result.debug
}

private fun AccessibilityMark.boundsRect(): IntRect =
    IntRect(left, top, right, bottom)

private fun IntRect.overlapRatio(other: IntRect): Float {
    val left = max(left, other.left)
    val top = max(top, other.top)
    val right = min(right, other.right)
    val bottom = min(bottom, other.bottom)
    val overlapWidth = (right - left).coerceAtLeast(0)
    val overlapHeight = (bottom - top).coerceAtLeast(0)
    val overlapArea = overlapWidth * overlapHeight
    val minArea = min(width * height, other.width * other.height).coerceAtLeast(1)
    return overlapArea.toFloat() / minArea.toFloat()
}

private fun String?.equalsNormalized(other: String?): Boolean {
    val left = this?.trim()?.takeIf { it.isNotEmpty() } ?: return other.isNullOrBlank()
    val right = other?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    return left.equals(right, ignoreCase = true)
}
