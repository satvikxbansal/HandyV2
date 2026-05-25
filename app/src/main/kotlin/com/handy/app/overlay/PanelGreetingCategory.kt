package com.handy.app.overlay

internal enum class PanelGreetingCategory {
    SETTINGS,
    BROWSER,
    EMAIL,
    MAPS,
    CAMERA,
    PHONE,
    SHOPPING,
    PHOTOS,
    MUSIC,
    VIDEO,
    MESSAGING,
    SOCIAL,
    CALENDAR,
    NOTES,
    BANKING,
    FOOD,
    RIDE,
    FILES,
    DEFAULT,
}

internal fun panelGreetingCategoryFor(
    packageName: String?,
    siteLabel: String?,
): PanelGreetingCategory {
    if (isShoppingSiteLabel(siteLabel)) return PanelGreetingCategory.SHOPPING
    val p = packageName?.lowercase().orEmpty()
    return when {
        p.isBlank() -> PanelGreetingCategory.DEFAULT

        // Photos / gallery
        p.contains("photos") || p.contains("gallery") ||
            p.contains("snapseed") || p.contains("lightroom") ||
            p.endsWith(".gallery3d") -> PanelGreetingCategory.PHOTOS

        // Camera
        p.contains("camera") || p.contains("gcam") ||
            p.endsWith(".gallerycam") -> PanelGreetingCategory.CAMERA

        // Browser
        p.contains("chrome") || p.contains("browser") || p.contains("firefox") ||
            p.contains("opera") || p.contains("brave") || p.contains("edge") ||
            p.contains("duckduckgo") || p.contains("sbrowser") -> PanelGreetingCategory.BROWSER

        // Maps & navigation
        p.contains("maps") || p.contains("waze") ||
            p.contains("navigation") || p.contains("geo") ||
            p.contains("here.maps") -> PanelGreetingCategory.MAPS

        // Settings / system UI
        p.contains("settings") || p.contains("systemui") ||
            p.contains("setupwizard") -> PanelGreetingCategory.SETTINGS

        // Email
        p == "com.google.android.gm" ||
            p.contains("gmail") || p.contains("outlook") ||
            p.contains("email") || p.contains("yahoo.mobile") ||
            p.endsWith(".mail") || p.contains("protonmail") ||
            p.contains("fastmail") -> PanelGreetingCategory.EMAIL

        // Messaging (1:1 / group chat)
        p.contains("whatsapp") || p.contains("telegram") ||
            p.contains("signal") || p.contains("imessage") ||
            p.contains("messenger") || p.contains("messages") ||
            p.contains("rcs") -> PanelGreetingCategory.MESSAGING

        // Social
        p.contains("instagram") || p.contains("twitter") ||
            p.contains("x.android") || p.contains("threads") ||
            p.contains("facebook.katana") || p.contains("reddit") ||
            p.contains("linkedin") || p.contains("bsky") ||
            p.contains("mastodon") -> PanelGreetingCategory.SOCIAL

        // Video - must come BEFORE "video"-named music apps; tighten by exact pkgs
        p == "com.google.android.youtube" ||
            (
                p.contains("youtube") &&
                    !p.contains("youtube.music") &&
                    !p.contains("youtubemusic")
            ) ||
            p.contains("netflix") || p.contains("primevideo") ||
            p.contains("disney") || p.contains("hbomax") ||
            p.contains("hotstar") || p.contains("twitch") ||
            p.contains("vlc") -> PanelGreetingCategory.VIDEO

        // Music
        p.contains("spotify") || p.contains("music") ||
            p.contains("youtubemusic") || p.contains("apple.music") ||
            p.contains("soundcloud") || p.contains("tidal") ||
            p.contains("audible") -> PanelGreetingCategory.MUSIC

        // Calendar
        p.contains("calendar") || p.contains("fantastical") ||
            p.contains("cron") -> PanelGreetingCategory.CALENDAR

        // Notes / docs / productivity
        p.contains("notes") || p.contains("obsidian") ||
            p.contains("notion") || p.contains("evernote") ||
            p.contains("keep") || p.contains("docs") ||
            p.contains("onenote") || p.contains("bear") ||
            p.contains("standardnotes") -> PanelGreetingCategory.NOTES

        // Banking / payments / wallets / investing - broad pattern but worth tagging
        p.contains("bank") || p.contains("paytm") ||
            p.contains("phonepe") || p.contains("gpay") ||
            p.contains("googlepay") || p.contains("revolut") ||
            p.contains("chase") || p.contains("wells") ||
            p.contains("citi") || p.contains("amex") ||
            p.contains("monzo") || p.contains("n26") ||
            p.contains("hdfc") || p.contains("icicibank") ||
            p.contains("sbi.") || p.contains("groww") -> PanelGreetingCategory.BANKING

        // Food delivery
        p.contains("doordash") || p.contains("uber.eats") ||
            p.contains("ubereats") || p.contains("zomato") ||
            p.contains("swiggy") || p.contains("grubhub") ||
            p.contains("deliveroo") || p.contains("instacart") ->
            PanelGreetingCategory.FOOD

        // Ride-hailing
        p.contains("uber.android") || p == "com.ubercab" ||
            p.contains("lyft") || p.contains("ola") ||
            p.contains("rapido") || p.contains("bolt.android") ||
            p.contains("grab") || p.contains("didi") ->
            PanelGreetingCategory.RIDE

        // Phone / dialer / contacts
        p.contains("dialer") || p.contains("phone") || p.contains("contacts") ||
            p.contains("incallui") -> PanelGreetingCategory.PHONE

        // Files / file manager
        p.contains("files") || p.contains("documentsui") ||
            p.contains("filemanager") -> PanelGreetingCategory.FILES

        isShoppingPackage(p) -> PanelGreetingCategory.SHOPPING
        else -> PanelGreetingCategory.DEFAULT
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
