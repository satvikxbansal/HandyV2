package com.handy.core.model

/**
 * The "thinking..." vocabulary shown during request latency.
 *
 * Ported verbatim from `HandyManager.swift → loadingVerbs` (30 strings).
 * Do not change any of these strings — they are part of Handy's voice.
 * The macOS guardrail comment said "34 strings" while the Swift source
 * actually ships 30; the macOS truth wins.
 */
object LoadingVerbs {

    val ALL: List<String> = listOf(
        "Analyzing your screen...",
        "Reading the interface...",
        "Scanning for context...",
        "Processing your request...",
        "Understanding the layout...",
        "Examining the elements...",
        "Interpreting what's on screen...",
        "Studying the UI...",
        "Parsing the content...",
        "Mapping the interface...",
        "Evaluating the workspace...",
        "Inspecting the application...",
        "Reviewing the screen...",
        "Decoding the view...",
        "Assessing the context...",
        "Gathering information...",
        "Observing the display...",
        "Surveying the window...",
        "Recognizing elements...",
        "Identifying components...",
        "Synthesizing a response...",
        "Formulating guidance...",
        "Composing an answer...",
        "Piecing it together...",
        "Connecting the dots...",
        "Thinking about this...",
        "Working through it...",
        "Almost there...",
        "Digging deeper...",
        "Looking closely...",
    )

    fun random(rng: kotlin.random.Random = kotlin.random.Random.Default): String =
        ALL[rng.nextInt(ALL.size)]
}
