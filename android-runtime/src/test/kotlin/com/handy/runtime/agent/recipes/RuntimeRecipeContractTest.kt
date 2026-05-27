package com.handy.runtime.agent.recipes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.handy.core.action.ActionPolicyEngine
import com.handy.core.action.ActionRisk
import com.handy.core.action.AssistantAction
import com.handy.core.action.ConfirmationLevel
import com.handy.core.action.PolicyDecision
import com.handy.core.action.SourceTrust
import com.handy.core.action.TapTarget
import com.handy.core.agent.AppRecipe
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipePlan
import com.handy.core.agent.RecipeProposal
import com.handy.core.agent.RecipeRegistry
import com.handy.core.agent.RecipeStepPolicyCheck
import com.handy.core.agent.SideEffectClassification
import com.handy.core.agent.UserGoal
import com.handy.core.llm.ToolProvenance
import com.handy.core.model.HandySettings
import com.handy.core.overlay.AccessibilityMark
import com.handy.core.overlay.PanelSnapshot
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.ScreenTextSnapshot
import com.handy.core.screen.TurnSource
import com.handy.core.screen.UiNode
import com.handy.core.tool.ToolContext
import com.handy.runtime.action.DefaultActionPolicyEngine
import com.handy.runtime.intent.LaunchableAppIndex
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test

abstract class RuntimeRecipeContractTest {
    abstract val recipeId: String
    abstract val recipe: AppRecipe
    open val policy: ActionPolicyEngine = DefaultActionPolicyEngine(
        settingsProvider = {
            HandySettings(
                tapForMeEnabled = true,
                typeForMeEnabled = true,
                recipesEnabled = true,
                actionDisclosureVersionAccepted = 1,
            )
        },
    )

    @Test fun `has at least three fixtures`() {
        assertWithMessage(recipeId)
            .that(fixtures().size)
            .isAtLeast(3)
    }

    @Test fun `proposes correctly for all fixtures`() {
        fixtures().forEach { fixture ->
            val proposal = propose(fixture)
            if (fixture.expectedRefusal != null) {
                assertWithMessage("${recipeId}:${fixture.name}")
                    .that(proposal)
                    .isInstanceOf(RecipeProposal.Refused::class.java)
                assertWithMessage("${recipeId}:${fixture.name}")
                    .that((proposal as RecipeProposal.Refused).reason)
                    .contains(fixture.expectedRefusal)
            } else {
                assertWithMessage("${recipeId}:${fixture.name}")
                    .that(proposal)
                    .isInstanceOf(RecipeProposal.Proposed::class.java)
                assertWithMessage("${recipeId}:${fixture.name}")
                    .that((proposal as RecipeProposal.Proposed).plan.recipeId)
                    .isEqualTo(fixture.expectedRecipe ?: recipeId)
            }
        }
    }

    @Test fun `policy decision matches fixture expectations`() {
        fixtures().forEach { fixture ->
            val proposal = propose(fixture)
            if (proposal !is RecipeProposal.Proposed) return@forEach
            val checks = preflight(proposal.plan, fixture.toGrounding(), policy)
            fixture.expectedRisk?.let { expectedRisk ->
                assertWithMessage("${recipeId}:${fixture.name}")
                    .that(checks.maxOf { it.risk })
                    .isEqualTo(expectedRisk)
            }
            assertWithMessage("${recipeId}:${fixture.name}")
                .that(checks.any { it.decision.confirmation != ConfirmationLevel.NONE })
                .isEqualTo(fixture.mustConfirm)
        }
    }

    @Test fun `blocked fixtures are refused or denied by policy`() {
        fixtures().forEach { fixture ->
            if (fixture.sideEffect != SideEffectClassification.BLOCKED) return@forEach
            val proposal = propose(fixture)
            if (proposal is RecipeProposal.Refused) return@forEach

            val checks = preflight((proposal as RecipeProposal.Proposed).plan, fixture.toGrounding(), policy)
            assertWithMessage("${recipeId}:${fixture.name}")
                .that(checks.any { !it.decision.allowed })
                .isTrue()
        }
    }

    fun fixtures(): List<RuntimeRecipeFixture> = RuntimeRecipeFixture.load(recipeId)

    private fun propose(fixture: RuntimeRecipeFixture): RecipeProposal =
        RecipeRegistry(listOf(recipe)).propose(
            goal = UserGoal.fromAssistantText(fixture.userGoal),
            grounding = fixture.toGrounding(),
        )
}

class OpenAppRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "open_app"
    override val recipe: AppRecipe = OpenAppRecipe(::fakeLaunchableApps)
}

class InstallAppRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "install_app"
    override val recipe: AppRecipe = InstallAppRecipe
}

class ClockRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "clock_alarm"
    override val recipe: AppRecipe = ClockRecipe
}

class TimerRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "set_timer"
    override val recipe: AppRecipe = TimerRecipe
}

class CalendarEventRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "create_calendar_event"
    override val recipe: AppRecipe = CalendarEventRecipe
}

class WebSearchRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "web_search"
    override val recipe: AppRecipe = WebSearchRecipe
}

class AndroidSettingsRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "android_settings"
    override val recipe: AppRecipe = AndroidSettingsRecipe
}

class MapsRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "maps"
    override val recipe: AppRecipe = MapsRecipe
}

class GmailRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "gmail_compose"
    override val recipe: AppRecipe = GmailRecipe
}

class WhatsAppRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "whatsapp_reply"
    override val recipe: AppRecipe = WhatsAppRecipe
}

class ChromeRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "chrome"
    override val recipe: AppRecipe = ChromeRecipe
}

class ShoppingSearchRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "shopping_search"
    override val recipe: AppRecipe = ShoppingSearchRecipe
}

class ShoppingFindCouponsRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "shopping_find_coupons"
    override val recipe: AppRecipe = ShoppingFindCouponsRecipe
}

class UberRideRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "uber_ride"
    override val recipe: AppRecipe = UberRideRecipe
}

class OlaRideRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "ola_ride"
    override val recipe: AppRecipe = OlaRideRecipe
}

class RapidoRideRecipeContractTest : RuntimeRecipeContractTest() {
    override val recipeId: String = "rapido_ride"
    override val recipe: AppRecipe = RapidoRideRecipe
}

class RuntimeRecipeContractCoverageTest {
    @Test fun `every runtime recipe has contract fixtures`() {
        val runtimeIds = AndroidRuntimeRecipes.defaultRecipes(::fakeLaunchableApps)
            .map { it.id }
            .toSet()
        assertThat(runtimeIds).isEqualTo(contractRecipeIds)
        runtimeIds.forEach { recipeId ->
            assertWithMessage(recipeId)
                .that(RuntimeRecipeFixture.load(recipeId).size)
                .isAtLeast(3)
        }
    }
}

internal val contractRecipeIds: Set<String> = setOf(
    "open_app",
    "install_app",
    "clock_alarm",
    "set_timer",
    "create_calendar_event",
    "web_search",
    "android_settings",
    "maps",
    "gmail_compose",
    "whatsapp_reply",
    "chrome",
    "shopping_search",
    "shopping_find_coupons",
    "uber_ride",
    "ola_ride",
    "rapido_ride",
)

private fun preflight(
    plan: RecipePlan,
    grounding: GroundingSnapshot,
    policy: ActionPolicyEngine,
): List<RecipeStepPolicyCheck> {
    var deferredScreen = false
    return plan.steps.map { step ->
        if (deferredScreen && step.requiresResolvedTarget()) {
            return@map RecipeStepPolicyCheck(step, step.deferredInitialDecision())
        }
        val target = step.resolveTarget(grounding)
        val decision = if (target == null && step.requiresResolvedTarget()) {
            PolicyDecision(
                allowed = false,
                risk = ActionRisk.HIGH,
                confirmation = ConfirmationLevel.NONE,
                requireFreshSnapshot = true,
                requireNodeActionOnly = false,
                allowGestureFallback = false,
                reason = "target-not-found",
            )
        } else {
            policy.decide(
                action = step.policyAction(grounding),
                target = target,
                grounding = grounding,
                sourceTrust = step.policySourceTrust(),
            ).let(step::applyConfirmationOverride)
        }
        if ((step.command as? RecipeCommand.NativeAction)?.allowPackageChangeAfter == true) {
            deferredScreen = true
        }
        RecipeStepPolicyCheck(step, decision)
    }
}

private fun fakeLaunchableApps(query: String): List<LaunchableAppIndex.Entry> =
    when (query.trim().lowercase()) {
        "spotify" -> listOf(entry("com.spotify.music", "Spotify"))
        "maps" -> listOf(
            entry("com.google.android.apps.maps", "Maps"),
            entry("com.example.citymaps", "Maps Lite"),
        )
        else -> emptyList()
    }

private fun entry(packageName: String, label: String): LaunchableAppIndex.Entry =
    LaunchableAppIndex.Entry(
        packageName = packageName,
        label = label,
        activityComponentFlat = "$packageName/.MainActivity",
    )

data class RuntimeRecipeFixture(
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

        fun load(recipeId: String): List<RuntimeRecipeFixture> {
            val dir = fixtureRoot().resolve(recipeId)
            if (!Files.exists(dir)) return emptyList()
            return Files.list(dir).use { paths ->
                paths
                    .filter { it.extension == "json" }
                    .sorted(Comparator.comparing<Path, String> { it.name })
                    .map { path -> json.decodeFromString<RuntimeRecipeFixtureJson>(path.readText()).toFixture() }
                    .toList()
            }
        }

        private fun fixtureRoot(): Path {
            val start = Path.of("").toAbsolutePath()
            return generateSequence(start) { it.parent }
                .map { it.resolve("core/src/test/resources/recipes") }
                .firstOrNull { Files.exists(it) }
                ?: error("recipe fixture root not found from $start")
        }
    }
}

private fun RuntimeRecipeFixture.toGrounding(): GroundingSnapshot {
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
private data class RuntimeRecipeFixtureJson(
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
    fun toFixture(): RuntimeRecipeFixture =
        RuntimeRecipeFixture(
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
