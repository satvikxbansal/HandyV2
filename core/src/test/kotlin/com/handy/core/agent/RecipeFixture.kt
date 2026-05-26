package com.handy.core.agent

import com.handy.core.action.ActionRisk
import com.handy.core.llm.ToolProvenance
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class RecipeFixture(
    val name: String,
    val app: String,
    val packageName: String?,
    val screen: String,
    val marks: List<AccessibilityMark>,
    val userGoal: String,
    val expectedRecipe: String?,
    val expectedRefusal: String?,
    val expectedRisk: ActionRisk?,
    val mustConfirm: Boolean,
    val sideEffect: SideEffectClassification,
    val provenance: ToolProvenance? = null,
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun load(recipeId: String): List<RecipeFixture> {
            val resource = RecipeFixture::class.java.classLoader
                .getResource("recipes/$recipeId")
                ?: return emptyList()
            val dir = Path.of(resource.toURI())
            return Files.list(dir).use { paths ->
                paths
                    .filter { it.extension == "json" }
                    .sorted(Comparator.comparing<Path, String> { it.name })
                    .map { path -> json.decodeFromString<RecipeFixtureJson>(path.readText()).toFixture() }
                    .toList()
            }
        }
    }
}

fun String.asUserGoal(): UserGoal = UserGoal.fromAssistantText(this)

fun RecipeFixture.toGrounding(): GroundingSnapshot {
    val packageName = packageName ?: "com.handy.test"
    val toolContext = ToolContext(packageName = packageName, appLabel = app)
    val root = UiNode(
        role = "root",
        text = screen,
        children = marks.map { mark ->
            UiNode(
                markId = mark.markId,
                role = mark.role,
                text = mark.text,
                contentDescription = mark.contentDescription,
                viewIdResourceName = mark.viewIdSuffix,
                boundsInScreen = IntRect(mark.left, mark.top, mark.right, mark.bottom),
                clickable = mark.clickable,
                scrollable = mark.scrollable,
                enabled = mark.enabled,
            )
        },
    )
    val screenText = ScreenTextSnapshot(
        packageName = packageName,
        windowTitle = screen,
        timestampEpochMs = 1L,
        root = root,
    )
    return GroundingSnapshot(
        requestId = "fixture:$name",
        source = TurnSource.TEST,
        toolContext = toolContext,
        panelSnapshot = PanelSnapshot(
            toolContext = toolContext,
            capturedAtEpochMs = 1L,
            marks = marks,
        ),
        screenText = screenText,
        windowId = 1,
        windowBounds = IntRect(0, 0, 1080, 2400),
        rootBoundsHash = GroundingSnapshot.rootBoundsHash(
            windowBounds = IntRect(0, 0, 1080, 2400),
            imeVisible = false,
            imeBounds = IntRect.ZERO,
            topmostWindowId = 1,
        ),
        treeHash = GroundingSnapshot.treeHash(marks, screenText),
    )
}

@Serializable
private data class RecipeFixtureJson(
    val name: String,
    val app: String,
    val packageName: String? = null,
    val screen: String,
    val marks: List<AccessibilityMarkJson> = emptyList(),
    val userGoal: String,
    val expectedRecipe: String? = null,
    val expectedRefusal: String? = null,
    val expectedRisk: ActionRisk? = null,
    val mustConfirm: Boolean = false,
    val sideEffect: SideEffectClassification = SideEffectClassification.NONE,
    val provenance: ToolProvenanceJson? = null,
) {
    fun toFixture(): RecipeFixture =
        RecipeFixture(
            name = name,
            app = app,
            packageName = packageName,
            screen = screen,
            marks = marks.map { it.toMark() },
            userGoal = userGoal,
            expectedRecipe = expectedRecipe,
            expectedRefusal = expectedRefusal,
            expectedRisk = expectedRisk,
            mustConfirm = mustConfirm,
            sideEffect = sideEffect,
            provenance = provenance?.toProvenance(),
        )
}

@Serializable
private data class AccessibilityMarkJson(
    val markId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewIdSuffix: String? = null,
    val role: String = "button",
    val bounds: List<Int> = listOf(0, 0, 120, 64),
    val clickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val enabled: Boolean = true,
    val isPassword: Boolean = false,
    val isChecked: Boolean? = null,
) {
    fun toMark(): AccessibilityMark =
        AccessibilityMark(
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

@Serializable
private data class ToolProvenanceJson(
    val turnId: String,
    val usedUntrustedTools: Set<String> = emptySet(),
    val untrustedDomains: List<String> = emptyList(),
    val containsActionLikeInstruction: Boolean = false,
) {
    fun toProvenance(): ToolProvenance =
        ToolProvenance(
            turnId = turnId,
            usedUntrustedTools = usedUntrustedTools,
            untrustedDomains = untrustedDomains,
            containsActionLikeInstruction = containsActionLikeInstruction,
        )
}

