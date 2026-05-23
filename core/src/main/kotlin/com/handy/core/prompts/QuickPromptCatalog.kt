package com.handy.core.prompts

/**
 * Per-app-category quick-prompt chips for the overlay chat panel.
 *
 * Cursorbuddy recipe #9: the panel shows 3–4 contextual chips at
 * cold-start based on the foreground package. Backed by the
 * `LaunchableAppIndex` + this pure-Kotlin catalog (no Android
 * dependency — `:core` rule).
 *
 * Categorisation is package-substring based, with a narrow browser-site
 * label override for shopping domains. The package-category core was
 * reimplemented from cursorbuddy's `AppDetector` under the
 * recipes-not-source discipline documented in `DESIGN_NOTES.md`. The
 * CHIP COPY itself is **our wording** — cursorbuddy's strings are not
 * ported verbatim.
 */
object QuickPromptCatalog {

    /** Generic greeting for the panel banner above the chips. */
    const val FALLBACK_GREETING: String = "What would you like help with?"

    const val SUMMARIZE_SCREEN_TEXT: String = "Summarize this screen"

    data class QuickPrompt(
        val text: String,
        val action: Action = Action.SUBMIT_TEXT,
    )

    enum class Action {
        SUBMIT_TEXT,
        SUMMARIZE_SCREEN,
    }

    private val FALLBACK_QUICK_PROMPTS: List<QuickPrompt> = listOf(
        QuickPrompt(SUMMARIZE_SCREEN_TEXT, Action.SUMMARIZE_SCREEN),
        QuickPrompt("Show me around"),
        QuickPrompt("What can I do here?"),
        QuickPrompt("Find the settings"),
    )

    /** Fallback chips for [AppCategory.UNKNOWN] or when no package is cached. */
    val FALLBACK_PROMPTS: List<String> = FALLBACK_QUICK_PROMPTS.map { it.text }

    enum class AppCategory {
        SETTINGS,
        BROWSER,
        MESSAGING,
        SOCIAL,
        EMAIL,
        MAPS,
        CAMERA,
        PHONE,
        FILES,
        MUSIC,
        VIDEO,
        GALLERY,
        CALENDAR,
        CLOCK,
        CALCULATOR,
        SHOPPING,
        STORE,
        FINANCE,
        PRODUCTIVITY,
        FOOD,
        TRAVEL,
        UNKNOWN,
    }

    /**
     * Categorise by package-substring match plus optional browser site
     * label. Pure Kotlin — no `Context`. Order matters: more specific
     * matches go first.
     */
    fun categorize(packageName: String?, siteLabel: String? = null): AppCategory {
        if (isShoppingSiteLabel(siteLabel)) return AppCategory.SHOPPING
        if (packageName.isNullOrBlank()) return AppCategory.UNKNOWN
        val p = packageName.lowercase()
        return when {
            p.contains("settings") || p.contains("systemui") -> AppCategory.SETTINGS
            p.contains("chrome") || p.contains("browser") || p.contains("firefox") ||
                p.contains("opera") || p.contains("brave") || p.contains("edge") ||
                p.contains("duckduckgo") || p.contains("sbrowser") -> AppCategory.BROWSER
            p.contains("whatsapp") || p.contains("telegram") || p.contains("messenger") ||
                p.contains("signal") || p.contains("viber") || p.contains("wechat") ||
                p.contains("discord") || p.contains("slack") || p.contains("mms") ||
                p.contains("messaging") || p.contains("sms") -> AppCategory.MESSAGING
            p.contains("instagram") || p.contains("facebook") || p.contains("tiktok") ||
                p.contains("twitter") || p.contains("snapchat") || p.contains("reddit") ||
                p.contains("pinterest") || p.contains("linkedin") || p.contains("threads") ->
                AppCategory.SOCIAL
            p.contains("gmail") || p.contains("email") || p.contains("mail") ||
                p.contains("outlook") || p.contains("yahoo.mobile") -> AppCategory.EMAIL
            p.contains("maps") || p.contains("waze") || p.contains("navigation") -> AppCategory.MAPS
            p.contains("camera") || p.contains("gcam") -> AppCategory.CAMERA
            p.contains("dialer") || p.contains("phone") || p.contains("contacts") ||
                p.contains("incallui") -> AppCategory.PHONE
            p.contains("files") || p.contains("filemanager") ||
                p.contains("documentsui") -> AppCategory.FILES
            p.contains("spotify") || p.contains("music") || p.contains("pandora") ||
                p.contains("soundcloud") || p.contains("deezer") || p.contains("tidal") ->
                AppCategory.MUSIC
            p.contains("youtube") || p.contains("netflix") || p.contains("video") ||
                p.contains("disney") || p.contains("hulu") || p.contains("primevideo") ->
                AppCategory.VIDEO
            p.contains("gallery") || p.contains("photos") -> AppCategory.GALLERY
            p.contains("calendar") -> AppCategory.CALENDAR
            p.contains("clock") || p.contains("alarm") || p.contains("deskclock") ->
                AppCategory.CLOCK
            p.contains("calculator") || p.contains("calc") -> AppCategory.CALCULATOR
            isShoppingPackage(p) -> AppCategory.SHOPPING
            p.contains("vending") || p.contains("playstore") || p.contains("appstore") ->
                AppCategory.STORE
            p.contains("bank") || p.contains("venmo") || p.contains("cashapp") ||
                p.contains("wallet") || p.contains("finance") -> AppCategory.FINANCE
            p.contains("docs") || p.contains("sheets") || p.contains("drive") ||
                p.contains("notion") || p.contains("evernote") || p.contains("keep") ||
                p.contains("notes") || p.contains("todo") || p.contains("trello") ->
                AppCategory.PRODUCTIVITY
            p.contains("ubereats") || p.contains("doordash") || p.contains("grubhub") ||
                p.contains("deliveroo") -> AppCategory.FOOD
            p.contains("uber") || p.contains("lyft") || p.contains("booking") ||
                p.contains("airbnb") || p.contains("tripadvisor") -> AppCategory.TRAVEL
            else -> AppCategory.UNKNOWN
        }
    }

    /**
     * 3–4 quick-prompt chips for [category]. Copy authored fresh; no
     * cursorbuddy strings ported verbatim.
     */
    fun promptsFor(category: AppCategory): List<String> = when (category) {
        AppCategory.SETTINGS -> listOf(
            "Explain this screen",
            "Help me find a setting",
            "What does this toggle do?",
        )
        AppCategory.BROWSER -> listOf(
            "Summarize this page",
            "Bookmark this page",
            "Open a new tab",
        )
        AppCategory.MESSAGING -> listOf(
            "Draft a reply",
            "Summarize this chat",
            "Send a quick message",
        )
        AppCategory.SOCIAL -> listOf(
            "Explain this post",
            "Summarize my feed",
            "Help me write a caption",
        )
        AppCategory.EMAIL -> listOf(
            "Summarize this email",
            "Draft a reply",
            "Find an important email",
        )
        AppCategory.MAPS -> listOf(
            "Directions home",
            "Find something nearby",
            "Share my location",
        )
        AppCategory.CAMERA -> listOf(
            "Tips for this shot",
            "Switch to video",
            "Use portrait mode",
        )
        AppCategory.PHONE -> listOf(
            "Find a contact",
            "Check voicemail",
            "Recent calls",
        )
        AppCategory.FILES -> listOf(
            "Find a file",
            "Organise my downloads",
            "Free up space",
        )
        AppCategory.MUSIC -> listOf(
            "Make a playlist",
            "Find similar songs",
            "Download for offline",
        )
        AppCategory.VIDEO -> listOf(
            "Find something to watch",
            "Adjust quality",
            "Turn on subtitles",
        )
        AppCategory.GALLERY -> listOf(
            "Edit this photo",
            "Create an album",
            "Share these photos",
        )
        AppCategory.CALENDAR -> listOf(
            "Create an event",
            "What's on today?",
            "Find a meeting",
        )
        AppCategory.CLOCK -> listOf(
            "Set a timer",
            "Set an alarm",
            "World clock",
        )
        AppCategory.CALCULATOR -> listOf(
            "Explain this calculation",
            "Convert a unit",
            "Percent change",
        )
        AppCategory.SHOPPING -> listOf(
            "Returnable hai? / Is this returnable?",
            "Coupon dhoondo / Find coupons",
            "Similar se compare karo / Compare with similar",
            "Price sahi hai? / Is this a good price?",
        )
        AppCategory.STORE -> listOf(
            "Find an app",
            "Update installed apps",
            "Check my subscriptions",
        )
        AppCategory.FINANCE -> listOf(
            "Check my balance",
            "Explain this transaction",
            "Set up a transfer",
        )
        AppCategory.PRODUCTIVITY -> listOf(
            "Summarize this doc",
            "Create a new note",
            "Find my recent work",
        )
        AppCategory.FOOD -> listOf(
            "Reorder my last meal",
            "Track my order",
            "Find cheap deals",
        )
        AppCategory.TRAVEL -> listOf(
            "Check my trip",
            "Find a ride",
            "Help me book",
        )
        AppCategory.UNKNOWN -> FALLBACK_PROMPTS
    }

    fun quickPromptsFor(category: AppCategory): List<QuickPrompt> =
        when (category) {
            AppCategory.UNKNOWN -> FALLBACK_QUICK_PROMPTS
            else -> promptsFor(category).map { QuickPrompt(it) }
        }

    /**
     * App-specific greeting. Uses [appLabel] when available; falls back
     * to a generic prompt for unknown categories.
     */
    fun greetingFor(appLabel: String?, category: AppCategory): String {
        val label = appLabel?.takeIf { it.isNotBlank() && it != "Handy" }
        return when (category) {
            AppCategory.UNKNOWN -> label?.let { "I see $it. What would you like help with?" }
                ?: FALLBACK_GREETING
            AppCategory.SETTINGS -> "In Settings. What do you need?"
            AppCategory.BROWSER -> label?.let { "Browsing in $it. Need help with this page?" }
                ?: "What would you like help with?"
            AppCategory.EMAIL -> label?.let { "In $it. Need help with your email?" }
                ?: "What would you like help with?"
            AppCategory.MAPS -> "Where do you need to go?"
            AppCategory.CAMERA -> "Camera's open. Want a photography tip?"
            AppCategory.PHONE -> "In the Phone app. What do you need?"
            AppCategory.SHOPPING -> label?.let { "Shopping in $it. Compare, coupons, or returns?" }
                ?: "Shopping. What should I check?"
            else -> label?.let { "In $it. What can I help with?" } ?: FALLBACK_GREETING
        }
    }

    private fun isShoppingPackage(packageName: String): Boolean =
        packageName.contains("meesho") ||
            packageName.contains("flipkart") ||
            (
                packageName.contains("amazon.mshop") &&
                    packageName.contains("shopping")
                )

    private fun isShoppingSiteLabel(siteLabel: String?): Boolean {
        val normalized = siteLabel?.lowercase()?.trim() ?: return false
        return normalized in setOf("meesho", "amazon", "flipkart") ||
            normalized.contains("meesho.com") ||
            normalized.contains("amazon.in") ||
            normalized.contains("amazon.com") ||
            normalized.contains("flipkart.com")
    }
}
