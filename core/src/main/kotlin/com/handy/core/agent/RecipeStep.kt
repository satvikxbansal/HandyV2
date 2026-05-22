package com.handy.core.agent

import com.handy.core.action.AssistantAction
import com.handy.core.action.ActionRisk
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.ScrollDirection
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.UiNode

data class RecipeStep(
    val id: String,
    val title: String,
    val command: RecipeCommand,
    val sensitive: Boolean = command.defaultSensitive,
    val confirmationOverride: ConfirmationLevel? = null,
) {
    init {
        require(id.isNotBlank()) { "RecipeStep.id must not be blank" }
        require(title.isNotBlank()) { "RecipeStep.title must not be blank" }
    }

    fun resolveTarget(grounding: GroundingSnapshot): TapTarget? = command.target?.resolve(grounding)

    fun requiresResolvedTarget(): Boolean =
        command !is RecipeCommand.Scroll && command !is RecipeCommand.NativeAction

    fun policyAction(grounding: GroundingSnapshot): AssistantAction = when (command) {
        is RecipeCommand.NativeAction -> command.action
        is RecipeCommand.TypeText -> AssistantAction.TypeText(command.text)
        else -> AssistantAction.OpenApp(
            packageHint = grounding.screenText?.packageName
                ?: grounding.toolContext.packageName,
        )
    }

    fun policySourceTrust(): SourceTrust = when (command) {
        is RecipeCommand.NativeAction -> SourceTrust.TRUSTED_USER
        else -> SourceTrust.TRUSTED_RECIPE
    }

    fun applyConfirmationOverride(decision: PolicyDecision): PolicyDecision {
        val required = confirmationOverride ?: return decision
        if (!decision.allowed) return decision
        val confirmation = maxOf(decision.confirmation, required)
        return decision.copy(
            risk = if (confirmation.requiresHighRisk()) {
                maxOf(decision.risk, ActionRisk.HIGH)
            } else {
                decision.risk
            },
            confirmation = confirmation,
            requireFreshSnapshot = decision.requireFreshSnapshot || confirmation.requiresFreshSnapshot(),
            reason = decision.reason ?: confirmation.defaultReason(),
        )
    }

    fun deferredInitialDecision(): PolicyDecision =
        applyConfirmationOverride(
            PolicyDecision(
                allowed = true,
                risk = if (sensitive) ActionRisk.HIGH else ActionRisk.MEDIUM,
                confirmation = if (sensitive) ConfirmationLevel.NORMAL else ConfirmationLevel.NONE,
                requireFreshSnapshot = true,
                requireNodeActionOnly = true,
                allowGestureFallback = false,
                reason = "deferred-recipe-step",
            ),
        )
}

sealed class RecipeCommand {
    abstract val target: RecipeTarget?
    open val defaultSensitive: Boolean = false

    data class Tap(
        override val target: RecipeTarget,
    ) : RecipeCommand() {
        override val defaultSensitive: Boolean = target.looksSensitive()
    }

    data class LongPress(
        override val target: RecipeTarget,
    ) : RecipeCommand() {
        override val defaultSensitive: Boolean = target.looksSensitive()
    }

    data class TypeText(
        override val target: RecipeTarget,
        val text: String,
    ) : RecipeCommand() {
        override val defaultSensitive: Boolean =
            target.looksSensitive() || text.looksSensitive()
    }

    data class Scroll(
        val direction: ScrollDirection,
        override val target: RecipeTarget? = null,
    ) : RecipeCommand()

    data class NativeAction(
        val action: AssistantAction,
        val allowPackageChangeAfter: Boolean = true,
    ) : RecipeCommand() {
        override val target: RecipeTarget? = null
        override val defaultSensitive: Boolean = action.isDestructive
    }
}

sealed class RecipeTarget {
    data class Node(
        val markId: String? = null,
        val role: String? = null,
        val text: String? = null,
        val viewId: String? = null,
        val desc: String? = null,
    ) : RecipeTarget()

    data class ScreenPoint(val x: Int, val y: Int) : RecipeTarget()

    fun resolve(grounding: GroundingSnapshot): TapTarget? = when (this) {
        is ScreenPoint -> TapTarget.AtScreenPoint(x = x, y = y)
        is Node -> resolveNode(grounding)
    }

    fun displayLabel(): String = when (this) {
        is ScreenPoint -> "point $x,$y"
        is Node -> text ?: desc ?: viewId ?: markId ?: role ?: "target"
    }

    fun looksSensitive(): Boolean = when (this) {
        is ScreenPoint -> false
        is Node -> listOfNotNull(role, text, viewId, desc).joinToString(" ").looksSensitive()
    }

    private fun Node.resolveNode(grounding: GroundingSnapshot): TapTarget? {
        val packageName = grounding.screenText?.packageName
            ?: grounding.toolContext.packageName.takeIf { it.isNotBlank() }
        val mark = grounding.panelSnapshot?.marks.orEmpty().firstMatching(this)
        if (mark != null) {
            return mark.toTapTarget(
                selector = this,
                packageName = packageName,
                windowId = grounding.windowId,
                snapshotHash = grounding.rootBoundsHash,
                treeHash = grounding.treeHash,
            )
        }

        val node = grounding.screenText?.root?.firstMatching(this) ?: return null
        return TapTarget.AtNode(
            markId = node.markId ?: markId,
            role = node.role.takeIf { it.isNotBlank() } ?: role,
            text = node.text ?: text,
            viewId = node.viewIdResourceName?.substringAfterLast('/') ?: viewId,
            desc = node.contentDescription ?: desc,
            expectedPackage = packageName,
            expectedWindowId = grounding.windowId,
            snapshotHash = grounding.rootBoundsHash,
            resolverConfidence = 1f,
            treeHash = grounding.treeHash,
        )
    }

    private fun List<AccessibilityMark>.firstMatching(selector: Node): AccessibilityMark? =
        firstOrNull { it.matches(selector) }

    private fun UiNode.firstMatching(selector: Node): UiNode? {
        if (matches(selector)) return this
        children.forEach { child ->
            child.firstMatching(selector)?.let { return it }
        }
        return null
    }

    private fun AccessibilityMark.matches(selector: Node): Boolean {
        selector.markId?.takeIf { it.isNotBlank() }?.let { return markId.equalsNormalized(it) }
        selector.viewId?.takeIf { it.isNotBlank() }?.let { expected ->
            return viewIdSuffix.equalsViewId(expected)
        }
        selector.desc?.takeIf { it.isNotBlank() }?.let { expected ->
            return contentDescription.equalsNormalized(expected) &&
                selector.role.matchesRole(role)
        }
        selector.text?.takeIf { it.isNotBlank() }?.let { expected ->
            return text.equalsNormalized(expected) &&
                selector.role.matchesRole(role)
        }
        return selector.role?.takeIf { it.isNotBlank() }?.let { role.equalsNormalized(it) } == true
    }

    private fun UiNode.matches(selector: Node): Boolean {
        selector.markId?.takeIf { it.isNotBlank() }?.let { return markId.equalsNormalized(it) }
        selector.viewId?.takeIf { it.isNotBlank() }?.let { expected ->
            return viewIdResourceName.equalsViewId(expected)
        }
        selector.desc?.takeIf { it.isNotBlank() }?.let { expected ->
            return contentDescription.equalsNormalized(expected) &&
                selector.role.matchesRole(role)
        }
        selector.text?.takeIf { it.isNotBlank() }?.let { expected ->
            return text.equalsNormalized(expected) &&
                selector.role.matchesRole(role)
        }
        return selector.role?.takeIf { it.isNotBlank() }?.let { role.equalsNormalized(it) } == true
    }

    private fun AccessibilityMark.toTapTarget(
        selector: Node,
        packageName: String?,
        windowId: Int?,
        snapshotHash: String?,
        treeHash: String?,
    ): TapTarget.AtNode =
        TapTarget.AtNode(
            markId = markId ?: selector.markId,
            role = role.takeIf { it.isNotBlank() } ?: selector.role,
            text = text ?: selector.text,
            viewId = viewIdSuffix ?: selector.viewId,
            desc = contentDescription ?: selector.desc,
            expectedPackage = packageName,
            expectedWindowId = windowId,
            snapshotHash = snapshotHash,
            resolverConfidence = 1f,
            treeHash = treeHash,
        )
}

internal fun String?.equalsNormalized(other: String?): Boolean =
    normalizeForRecipe() == other.normalizeForRecipe() &&
        !normalizeForRecipe().isNullOrBlank()

internal fun String?.equalsViewId(other: String?): Boolean {
    val left = normalizeForRecipe()?.substringAfterLast('/')
    val right = other.normalizeForRecipe()?.substringAfterLast('/')
    return !left.isNullOrBlank() && left == right
}

private fun String?.matchesRole(actualRole: String?): Boolean =
    isNullOrBlank() || actualRole.equalsNormalized(this)

private fun String?.normalizeForRecipe(): String? =
    this
        ?.trim()
        ?.lowercase()
        ?.replace(Regex("""\s+"""), " ")
        ?.takeIf { it.isNotBlank() }

internal fun String.looksSensitive(): Boolean {
    val normalized = lowercase()
    return SENSITIVE_RECIPE_TERMS.any { normalized.contains(it) } ||
        CARD_LIKE_REGEX.containsMatchIn(this)
}

private val SENSITIVE_RECIPE_TERMS = listOf(
    "pay",
    "payment",
    "checkout",
    "buy",
    "purchase",
    "place order",
    "delete",
    "remove",
    "transfer",
    "send money",
    "password",
    "passcode",
    "otp",
    "cvv",
    "cvc",
)

private val CARD_LIKE_REGEX = Regex("""\b(?:\d[ -]?){13,19}\b""")

private fun ConfirmationLevel.requiresHighRisk(): Boolean =
    this == ConfirmationLevel.STRONG_HOLD || this == ConfirmationLevel.TYPED_CONFIRMATION

private fun ConfirmationLevel.requiresFreshSnapshot(): Boolean =
    this == ConfirmationLevel.STRONG_HOLD || this == ConfirmationLevel.TYPED_CONFIRMATION

private fun ConfirmationLevel.defaultReason(): String? = when (this) {
    ConfirmationLevel.STRONG_HOLD -> "strong-confirmation-required"
    ConfirmationLevel.TYPED_CONFIRMATION -> "typed-confirmation-required"
    ConfirmationLevel.NORMAL,
    ConfirmationLevel.NONE -> null
}
