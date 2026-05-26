package com.handy.core.agent

enum class SideEffectClassification {
    NONE,
    DRAFT_ONLY,
    OPENS_EXTERNAL_UI,
    REQUIRES_FINAL_USER_CONFIRMATION,
    BLOCKED,
}

internal fun SideEffectClassification.requiresStrongHoldNudge(): Boolean =
    this == SideEffectClassification.BLOCKED ||
        this == SideEffectClassification.REQUIRES_FINAL_USER_CONFIRMATION

