package com.handy.app.overlay

internal enum class PanelGreetingCategory {
    SETTINGS,
    BROWSER,
    PHOTOS,
    EMAIL,
    MAPS,
    CAMERA,
    PHONE,
    SHOPPING,
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
        p.contains("settings") || p.contains("systemui") -> PanelGreetingCategory.SETTINGS
        p.contains("chrome") || p.contains("browser") || p.contains("firefox") ||
            p.contains("opera") || p.contains("brave") || p.contains("edge") ||
            p.contains("duckduckgo") || p.contains("sbrowser") -> PanelGreetingCategory.BROWSER
        p.contains("photos") || p.contains("gallery") -> PanelGreetingCategory.PHOTOS
        p.contains("gmail") || p.contains("email") || p.contains("mail") ||
            p.contains("outlook") || p.contains("yahoo.mobile") -> PanelGreetingCategory.EMAIL
        p.contains("maps") || p.contains("waze") || p.contains("navigation") ->
            PanelGreetingCategory.MAPS
        p.contains("camera") || p.contains("gcam") -> PanelGreetingCategory.CAMERA
        p.contains("dialer") || p.contains("phone") || p.contains("contacts") ||
            p.contains("incallui") -> PanelGreetingCategory.PHONE
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
