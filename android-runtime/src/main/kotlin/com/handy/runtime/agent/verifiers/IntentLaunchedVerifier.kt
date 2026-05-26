package com.handy.runtime.agent.verifiers

import com.handy.core.action.AssistantAction
import com.handy.core.agent.RecipeCommand
import com.handy.core.agent.RecipeStep
import com.handy.core.agent.ResultVerifier
import com.handy.core.agent.VerificationResult
import com.handy.core.screen.GroundingSnapshot

object IntentLaunchedVerifier : ResultVerifier {
    override val name: String = "IntentLaunchedVerifier"

    override suspend fun verify(
        step: RecipeStep,
        snapshotBefore: GroundingSnapshot,
        snapshotAfter: GroundingSnapshot,
    ): VerificationResult {
        val action = (step.command as? RecipeCommand.NativeAction)?.action
            ?: return VerificationResult.Inconclusive
        val beforePackage = snapshotBefore.foregroundPackageName()
        val afterPackage = snapshotAfter.foregroundPackageName()
        val expectedPackage = action.expectedPackage()
        if (expectedPackage != null) {
            return if (afterPackage.equals(expectedPackage, ignoreCase = true)) {
                VerificationResult.Verified
            } else {
                VerificationResult.Failed(
                    "intent-package-mismatch:expected=$expectedPackage actual:${afterPackage ?: "unknown"}",
                )
            }
        }

        return when {
            !beforePackage.equals(afterPackage, ignoreCase = true) -> VerificationResult.Verified
            snapshotAfter.screenChangedFrom(snapshotBefore) -> VerificationResult.Verified
            else -> VerificationResult.Failed("intent-did-not-launch")
        }
    }
}

internal fun AssistantAction.expectedPackage(): String? = when (this) {
    is AssistantAction.OpenApp -> packageHint
    is AssistantAction.InstallApp -> PLAY_STORE_PACKAGE
    is AssistantAction.OpenSettings,
    is AssistantAction.OpenAppInfo -> SETTINGS_PACKAGE
    is AssistantAction.SetAlarm,
    is AssistantAction.StartTimer -> DESKCLOCK_PACKAGE
    is AssistantAction.MapsSearch,
    is AssistantAction.StartNavigation -> MAPS_PACKAGE
    is AssistantAction.WebSearchIntent -> null
    is AssistantAction.CreateCalendarEvent -> null
    is AssistantAction.OpenUrl -> url.expectedPackageForUrl()
    else -> null
}?.takeIf { it.isNotBlank() }

private fun String.expectedPackageForUrl(): String? {
    val normalized = trim().lowercase()
    return when {
        normalized.startsWith("mailto:") -> GMAIL_PACKAGE
        normalized.startsWith("https://wa.me/") ||
            normalized.startsWith("http://wa.me/") ||
            normalized.contains("whatsapp.com") -> WHATSAPP_PACKAGE
        normalized.startsWith("market://") ||
            normalized.contains("play.google.com/store") -> PLAY_STORE_PACKAGE
        else -> null
    }
}

private const val DESKCLOCK_PACKAGE = "com.google.android.deskclock"
private const val GMAIL_PACKAGE = "com.google.android.gm"
private const val MAPS_PACKAGE = "com.google.android.apps.maps"
private const val PLAY_STORE_PACKAGE = "com.android.vending"
private const val SETTINGS_PACKAGE = "com.android.settings"
private const val WHATSAPP_PACKAGE = "com.whatsapp"

