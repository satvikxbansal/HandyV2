package com.handy.core.privacy

/**
 * Marks values that must never be written to logs, crash diagnostics,
 * analytics, or data-class `toString()` output.
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class Sensitive
