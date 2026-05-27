package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
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

object PhotosRecipe : AppRecipe {
    override val id: String = "photos"
    override val displayName: String = "Open Photos"
    override val description: String =
        "Open the user's Photos/Gallery app or tap a visible share affordance only while a photo viewer is foregrounded."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.hasPhotoDeleteVeto(invocation)) {
            return RecipeProposal.Refused("photo-delete-blocked")
        }
        val wantsShare = goal.requestedIntent == "photos_share_current" ||
            invocation.arg("mode", "action")?.normalizeRecipeText() == "share" ||
            goal.text.contains("share this photo", ignoreCase = true)
        if (wantsShare) {
            if (!grounding.looksLikePhotoViewer()) {
                return RecipeProposal.Refused("not-viewing-photo")
            }
            val target = invocation.shareTarget()
            return RecipeProposal.Proposed(
                RecipePlan(
                    recipeId = id,
                    displayName = "Share current photo",
                    packageName = null,
                    appLabel = grounding.toolContext.appLabel.takeIf { it.isNotBlank() } ?: "Photos",
                    summary = "Open the system share sheet for the current photo",
                    steps = listOf(
                        RecipeStep(
                            id = "tap-photo-share",
                            title = "Open photo share sheet",
                            command = RecipeCommand.Tap(target),
                        ),
                    ),
                ).validate(),
            )
        }

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Photos",
                summary = "Open Photos or Gallery",
                steps = listOf(
                    RecipeStep(
                        id = "open-photos",
                        title = "Open Photos or Gallery",
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenPhotos,
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }
}

private fun RecipeInvocation.shareTarget(): RecipeTarget.Node =
    arg("shareMarkId", "markId")?.let { RecipeTarget.Node(markId = it) }
        ?: arg("shareViewId", "viewId")?.let { RecipeTarget.Node(viewId = it, role = "button") }
        ?: arg("shareDesc", "desc")?.let { RecipeTarget.Node(desc = it, role = "button") }
        ?: arg("shareText", "label", "text")?.let { RecipeTarget.Node(text = it, role = "button") }
        ?: RecipeTarget.Node(
            alternatives = listOf(
                RecipeTarget.Node(desc = "Share", role = "button"),
                RecipeTarget.Node(text = "Share", role = "button"),
                RecipeTarget.Node(viewIdContains = "share", role = "button"),
            ),
        )

private fun UserGoal.hasPhotoDeleteVeto(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    return raw.contains("delete this photo") ||
        raw.contains("delete all") ||
        raw.contains("delete photo") ||
        raw.contains("trash this photo")
}

private fun GroundingSnapshot.looksLikePhotoViewer(): Boolean {
    val raw = listOfNotNull(
        packageNameForRecipe(),
        toolContext.appLabel,
        windowTitleForRecipe(),
        screenText?.root?.text,
        screenText?.root?.contentDescription,
    ).joinToString(" ").normalizeRecipeText()
    return PHOTO_VIEWER_HINTS.any { raw.contains(it) }
}

private val PHOTO_VIEWER_HINTS = listOf(
    "photos",
    "gallery",
    "camera roll",
    "image viewer",
    "photo",
)
