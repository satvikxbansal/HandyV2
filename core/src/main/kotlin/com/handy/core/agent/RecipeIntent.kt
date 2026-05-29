package com.handy.core.agent

enum class RecipeIntent(val canonical: String) {
    OPEN_APP("open_app"),
    APP_SEARCH("app_search"),
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
    YOUTUBE_SEARCH("youtube_search"),
    YOUTUBE_OPEN_CHANNEL("youtube_open_channel"),
    CREATE_NOTE("create_note"),
    OPEN_CONTACT("open_contact"),
    PREPARE_CALL("prepare_call"),
    PREPARE_SMS("prepare_sms"),
    FILES_SEARCH("files_search"),
    FILES_OPEN("files_open"),
    PHOTOS_OPEN("photos_open"),
    PHOTOS_SHARE_CURRENT("photos_share_current"),
    CALCULATE("calculate"),
    FOOD_SEARCH("food_search"),
    FOOD_TRACK_ORDER("food_track_order"),
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
