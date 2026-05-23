package com.handy.core.agent

enum class RecipeIntent(val canonical: String) {
    OPEN_APP("open_app"),
    SET_ALARM("set_alarm"),
    SET_TIMER("set_timer"),
    WEB_SEARCH("web_search"),
    INSTALL_APP("install_app"),
    OPEN_SETTING("open_setting"),
    CREATE_CALENDAR_EVENT("create_calendar_event"),
    DRAFT_GMAIL("draft_gmail"),
    DRAFT_WHATSAPP("draft_whatsapp"),
    OPEN_CHROME_URL("open_chrome_url"),
    SHOPPING_SEARCH("shopping_search"),
    SHOPPING_FIND_COUPONS("shopping_find_coupons"),
    BOOK_RIDE("book_ride"),
    ;

    companion object {
        fun fromCanonical(value: String?): RecipeIntent? {
            val normalized = value
                ?.trim()
                ?.lowercase()
                ?: return null
            return entries.firstOrNull { it.canonical == normalized }
        }
    }
}
