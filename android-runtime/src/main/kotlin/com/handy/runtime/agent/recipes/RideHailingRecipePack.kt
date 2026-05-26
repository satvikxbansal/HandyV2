package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.RecipeTarget
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object RideHailingRecipePack {
    fun defaultRecipes(): List<AppRecipe> = listOf(
        UberRideRecipe,
        OlaRideRecipe,
        RapidoRideRecipe,
    )
}

object UberRideRecipe : AppRecipe {
    override val id: String = "uber_ride"
    override val displayName: String = "Prepare Uber ride"
    override val description: String =
        "Open Uber, search the destination, and stop before final ride confirmation."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal = proposeRide(RideApp.Uber, goal, invocation)
}

object OlaRideRecipe : AppRecipe {
    override val id: String = "ola_ride"
    override val displayName: String = "Prepare Ola ride"
    override val description: String =
        "Open Ola, search the destination, and stop before final ride confirmation."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal = proposeRide(RideApp.Ola, goal, invocation)
}

object RapidoRideRecipe : AppRecipe {
    override val id: String = "rapido_ride"
    override val displayName: String = "Prepare Rapido ride"
    override val description: String =
        "Open Rapido, search the destination, and stop before final ride confirmation."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal = proposeRide(RideApp.Rapido, goal, invocation)
}

private data class RideApp(
    val recipeId: String,
    val label: String,
    val packageName: String,
) {
    companion object {
        val Uber = RideApp("uber_ride", "Uber", "com.ubercab")
        val Ola = RideApp("ola_ride", "Ola", "com.olacabs.customer")
        val Rapido = RideApp("rapido_ride", "Rapido", "com.rapido.passenger")
    }
}

private fun AppRecipe.proposeRide(
    app: RideApp,
    goal: UserGoal,
    invocation: RecipeInvocation,
): RecipeProposal {
    if (goal.hasRideHardVeto(invocation)) {
        return RecipeProposal.Refused("ride-confirmation-blocked")
    }
    val destination = invocation.arg("destination", "to", "place")
        ?.cleanRideValue()
        ?: goal.text.extractRideDestination()
        ?: return RecipeProposal.Refused("missing-destination")

    val destinationTarget = invocation.destinationFieldTarget()
    val steps = mutableListOf(
        RecipeStep(
            id = "open-${app.label.lowercase()}",
            title = "Open ${app.label}",
            command = RecipeCommand.NativeAction(
                action = AssistantAction.OpenApp(app.packageName),
                allowPackageChangeAfter = true,
            ),
        ),
        RecipeStep(
            id = "focus-destination",
            title = "Focus destination field",
            command = RecipeCommand.Tap(destinationTarget),
        ),
        RecipeStep(
            id = "type-destination",
            title = "Type destination",
            command = RecipeCommand.TypeText(
                target = destinationTarget,
                text = destination,
            ),
        ),
        RecipeStep(
            id = "open-destination-result",
            title = "Open matching destination result",
            command = RecipeCommand.Tap(invocation.destinationResultTarget(destination)),
        ),
    )

    // Safety rule: ride recipes never target Confirm, Request, Book, Choose,
    // or payment controls. The final ride confirmation remains the user's tap.
    invocation.rideClassTargetOrNull()?.let { (rideClass, target) ->
        steps += RecipeStep(
            id = "select-ride-class",
            title = "Select $rideClass card",
            command = RecipeCommand.Tap(target),
            sensitive = true,
            confirmationOverride = ConfirmationLevel.STRONG_HOLD,
        )
    }

    return RecipeProposal.Proposed(
        RecipePlan(
            recipeId = app.recipeId,
            displayName = displayName,
            packageName = app.packageName,
            appLabel = app.label,
            summary = "Prepare ${app.label} ride to $destination",
            steps = steps,
        ).validate(),
    )
}

private fun RecipeInvocation.destinationFieldTarget(): RecipeTarget.Node =
    arg("destinationMarkId", "fieldMarkId", "markId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("destinationViewId", "fieldViewId", "viewId")?.let {
            RecipeTarget.Node(viewId = it, role = "edittext")
        }
        ?: arg("destinationDesc", "fieldDesc", "desc")?.let {
            RecipeTarget.Node(desc = it, role = "edittext")
        }
        ?: RecipeTarget.Node(
            alternatives = listOf(
                RecipeTarget.Node(viewIdContains = "destination", role = "edittext"),
                RecipeTarget.Node(viewIdContains = "search", role = "edittext"),
                RecipeTarget.Node(viewIdContains = "where", role = "edittext"),
                RecipeTarget.Node(desc = "Where to?"),
                RecipeTarget.Node(desc = "Search destination"),
                RecipeTarget.Node(role = "edittext"),
            ),
        )

private fun RecipeInvocation.destinationResultTarget(destination: String): RecipeTarget.Node {
    arg("resultMarkId")?.let { return RecipeTarget.Node(markId = it) }
    arg("resultViewId")?.let { return RecipeTarget.Node(viewId = it) }
    arg("resultDesc")?.let { return RecipeTarget.Node(desc = it) }

    val alternatives = mutableListOf<RecipeTarget.Node>()
    val tokens = destination.destinationTokens()
    if (tokens.isNotEmpty()) {
        alternatives += RecipeTarget.Node(textContainsAll = tokens)
    } else {
        alternatives += RecipeTarget.Node(text = destination)
    }
    alternatives += RecipeTarget.Node(viewId = "list_item_0")
    return RecipeTarget.Node(alternatives = alternatives)
}

private fun RecipeInvocation.rideClassTargetOrNull(): Pair<String, RecipeTarget.Node>? {
    val rideClass = arg("cheapestClass", "rideClass", "class", "vehicle", "mode")
        ?.rideClassLabel()
        ?: return null
    val markId = arg("rideClassMarkId", "classMarkId")
    val viewId = arg("rideClassViewId", "classViewId")
    val desc = arg("rideClassDesc", "classDesc")
    val target = when {
        !markId.isNullOrBlank() -> RecipeTarget.Node(markId = markId, text = rideClass)
        !viewId.isNullOrBlank() -> RecipeTarget.Node(viewId = viewId, text = rideClass)
        !desc.isNullOrBlank() -> RecipeTarget.Node(desc = desc, text = rideClass)
        else -> return null
    }
    return rideClass to target
}

private fun UserGoal.hasRideHardVeto(invocation: RecipeInvocation): Boolean {
    val raw = (
        listOf(text) +
            invocation.args.flatMap { (key, value) -> listOf(key, value) }
        ).joinToString(" ")
        .lowercase()
    return RIDE_HARD_VETO_TERMS.any { raw.contains(it) }
}

private fun String.extractRideDestination(): String? {
    val normalized = trim()
    RIDE_DESTINATION_PATTERNS.forEach { pattern ->
        val match = pattern.find(normalized) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanRideValue()
    }
    return null
}

private fun String.cleanRideValue(): String? =
    trim()
        .trim('"', '\'')
        .trimEnd('.', ',', ';')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private fun String.destinationTokens(): List<String> =
    normalizeRecipeText()
        .split(' ')
        .filter { token -> token.length >= 2 && token !in DESTINATION_STOPWORDS }
        .take(5)

private fun String.rideClassLabel(): String? {
    val normalized = normalizeRecipeText().replace(" ", "")
    return when {
        normalized.contains("ubergo") -> "UberGo"
        normalized.contains("auto") -> "Auto"
        normalized.contains("mini") -> "Mini"
        normalized.contains("bike") -> "Bike"
        else -> null
    }
}

private val RIDE_DESTINATION_PATTERNS = listOf(
    Regex("""\bbook\s+(?:a\s+)?(?:cab|ride|taxi)\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\buber\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\bola\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\brapido\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\bride\s+to\s+(.+)$""", RegexOption.IGNORE_CASE),
)

private val DESTINATION_STOPWORDS = setOf(
    "a",
    "an",
    "the",
    "to",
    "near",
    "nearby",
    "please",
)

private val RIDE_HARD_VETO_TERMS = listOf(
    "confirm fare",
    "confirm ride",
    "request ride",
    "book now",
    "pay driver",
    "pay fare",
    "start trip",
)
