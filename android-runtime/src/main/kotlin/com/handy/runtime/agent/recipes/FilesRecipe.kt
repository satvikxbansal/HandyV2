package com.handy.runtime.agent.recipes

import com.handy.core.action.AssistantAction
import com.handy.core.action.FilePickerMode
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeInvocation
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.screen.GroundingSnapshot

object FilesRecipe : AppRecipe {
    override val id: String = "files"
    override val displayName: String = "Open Files"
    override val description: String =
        "Open Android's file picker/document UI for user-driven file search or open. Handy never reads or uploads the file."
    override val sideEffectClassification: SideEffectClassification =
        SideEffectClassification.OPENS_EXTERNAL_UI

    override fun propose(
        goal: UserGoal,
        invocation: RecipeInvocation,
        grounding: GroundingSnapshot,
    ): RecipeProposal {
        if (goal.hasFilesHardVeto(invocation)) {
            return RecipeProposal.Refused("files-mutation-blocked")
        }
        val mode = if (goal.requestedIntent == "files_search" ||
            invocation.arg("mode", "action")?.normalizeRecipeText() == "search"
        ) {
            FilePickerMode.SEARCH
        } else {
            FilePickerMode.OPEN
        }
        val name = invocation.arg("name", "file", "query")
            ?.cleanFileValue()
            ?: goal.text.extractFileName()
        val mimeType = invocation.arg("mimeType", "mime", "type")
            ?.cleanFileValue()
            ?: "*/*"

        return RecipeProposal.Proposed(
            RecipePlan(
                recipeId = id,
                displayName = displayName,
                packageName = null,
                appLabel = "Files",
                summary = when (mode) {
                    FilePickerMode.SEARCH -> "Open file picker to search${name?.let { " for \"$it\"" }.orEmpty()}"
                    FilePickerMode.OPEN -> "Open document picker${name?.let { " for \"$it\"" }.orEmpty()}"
                },
                steps = listOf(
                    RecipeStep(
                        id = when (mode) {
                            FilePickerMode.SEARCH -> "open-file-search"
                            FilePickerMode.OPEN -> "open-document-picker"
                        },
                        title = when (mode) {
                            FilePickerMode.SEARCH -> "Open file search"
                            FilePickerMode.OPEN -> "Open document picker"
                        },
                        command = RecipeCommand.NativeAction(
                            action = AssistantAction.OpenFilePicker(
                                mode = mode,
                                mimeType = mimeType,
                            ),
                            allowPackageChangeAfter = true,
                        ),
                    ),
                ),
            ).validate(),
        )
    }
}

private fun UserGoal.hasFilesHardVeto(invocation: RecipeInvocation): Boolean {
    val raw = (listOf(text) + invocation.args.flatMap { (key, value) -> listOf(key, value) })
        .joinToString(" ")
        .lowercase()
    return FILES_HARD_VETO_TERMS.any { raw.contains(it) }
}

private fun String.extractFileName(): String? {
    FILE_PATTERNS.forEach { pattern ->
        val match = pattern.find(trim()) ?: return@forEach
        return match.groupValues.getOrNull(1)?.cleanFileValue()
    }
    return null
}

private fun String.cleanFileValue(): String? =
    trim()
        .trim('"', '\'', '.', ',', ';', ':')
        .replace(Regex("""\s+"""), " ")
        .takeIf { it.isNotBlank() }

private val FILE_PATTERNS = listOf(
    Regex("""\bfind\s+file\s+(.+)$""", RegexOption.IGNORE_CASE),
    Regex("""\bopen\s+(?:file\s+)?(.+)$""", RegexOption.IGNORE_CASE),
)

private val FILES_HARD_VETO_TERMS = listOf(
    "delete",
    "remove",
    "rename",
    "move ",
    "upload",
    "share",
)
