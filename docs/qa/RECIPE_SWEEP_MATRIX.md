# Recipe Sweep Matrix

Audit note, 2026-05-27: automated recipe contract coverage, verifier tests, app unit tests, lint, debug assemble, and emulator launch smoke passed after the deep audit. Install and Maps compatible-handler fallbacks are covered by verifier tests. P-RECIPES-2 adds YouTube, Notes, Contacts, Files, Photos, Calculator, Food Delivery, and Calendar v2 fixture coverage. The per-device pass/fail cells remain `TBD` until the manual recipe sweep below is executed on each listed device/app-account combination.

| recipe | device | Android version | app version | signed-in (Y/N) | locale | expected behavior | pass/fail | screenshot path |
|---|---|---|---|---|---|---|---|---|
| open_app | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens the requested installed app; no account required. | TBD | docs/qa/screenshots/pixel9/open_app.png |
| open_app | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens the requested installed app; no account required. | TBD | docs/qa/screenshots/s24/open_app.png |
| open_app | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens the requested installed app; no account required. | TBD | docs/qa/screenshots/api30/open_app.png |
| install_app | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Play Store listing/search only; user manually taps Install. | TBD | docs/qa/screenshots/pixel9/install_app.png |
| install_app | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Play Store listing/search only; user manually taps Install. | TBD | docs/qa/screenshots/s24/install_app.png |
| install_app | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Play Store listing/search or browser fallback; user manually installs. | TBD | docs/qa/screenshots/api30/install_app.png |
| clock_alarm | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Clock alarm UI for review; verifier sees DeskClock launch/foreground. | TBD | docs/qa/screenshots/pixel9/clock_alarm.png |
| clock_alarm | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Clock alarm UI for review; verifier sees Clock launch/foreground. | TBD | docs/qa/screenshots/s24/clock_alarm.png |
| clock_alarm | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Clock alarm UI for review; verifier sees DeskClock launch/foreground. | TBD | docs/qa/screenshots/api30/clock_alarm.png |
| set_timer | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Clock timer UI for review; does not silently start final user action. | TBD | docs/qa/screenshots/pixel9/set_timer.png |
| set_timer | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Clock timer UI for review; does not silently start final user action. | TBD | docs/qa/screenshots/s24/set_timer.png |
| set_timer | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Clock timer UI for review; does not silently start final user action. | TBD | docs/qa/screenshots/api30/set_timer.png |
| create_calendar_event_v2 | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens calendar event draft with attendee parsing; Save remains a user action with strong hold; recurring rules refused until confirmed. | TBD | docs/qa/screenshots/pixel9/create_calendar_event_v2.png |
| create_calendar_event_v2 | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens calendar event draft with attendee parsing; Save remains a user action with strong hold; recurring rules refused until confirmed. | TBD | docs/qa/screenshots/s24/create_calendar_event_v2.png |
| create_calendar_event_v2 | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens calendar event draft with attendee parsing; Save remains a user action with strong hold; recurring rules refused until confirmed. | TBD | docs/qa/screenshots/api30/create_calendar_event_v2.png |
| web_search | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens browser/search UI for the query; sensitive query is blocked by policy. | TBD | docs/qa/screenshots/pixel9/web_search.png |
| web_search | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens browser/search UI for the query; sensitive query is blocked by policy. | TBD | docs/qa/screenshots/s24/web_search.png |
| web_search | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens browser/search UI for the query or browser fallback. | TBD | docs/qa/screenshots/api30/web_search.png |
| android_settings | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens safe Settings targets; Wi-Fi/security/accessibility are denied. | TBD | docs/qa/screenshots/pixel9/android_settings.png |
| android_settings | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens safe Settings targets; Wi-Fi/security/accessibility are denied. | TBD | docs/qa/screenshots/s24/android_settings.png |
| android_settings | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens safe Settings targets; Wi-Fi/security/accessibility are denied. | TBD | docs/qa/screenshots/api30/android_settings.png |
| maps | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Maps search/navigation intent; no payment or sensitive query allowed. | TBD | docs/qa/screenshots/pixel9/maps.png |
| maps | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Maps search/navigation intent; no payment or sensitive query allowed. | TBD | docs/qa/screenshots/s24/maps.png |
| maps | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Maps search/navigation intent or compatible handler. | TBD | docs/qa/screenshots/api30/maps.png |
| gmail_compose | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens Gmail mailto draft, then requires strong hold before Send. | TBD | docs/qa/screenshots/pixel9/gmail_compose.png |
| gmail_compose | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens Gmail mailto draft, then requires strong hold before Send. | TBD | docs/qa/screenshots/s24/gmail_compose.png |
| gmail_compose | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens mail handler/chooser; fixture does not require a signed-in account. | TBD | docs/qa/screenshots/api30/gmail_compose.png |
| whatsapp_reply | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens WhatsApp draft/chat, then requires strong hold before Send. | TBD | docs/qa/screenshots/pixel9/whatsapp_reply.png |
| whatsapp_reply | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens WhatsApp draft/chat, then requires strong hold before Send. | TBD | docs/qa/screenshots/s24/whatsapp_reply.png |
| whatsapp_reply | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens WhatsApp handler/chooser where available; never auto-sends. | TBD | docs/qa/screenshots/api30/whatsapp_reply.png |
| chrome | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens URL/search in Chrome; summary requests route away from recipe. | TBD | docs/qa/screenshots/pixel9/chrome.png |
| chrome | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens URL/search in Chrome; summary requests route away from recipe. | TBD | docs/qa/screenshots/s24/chrome.png |
| chrome | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens URL/search in Chrome or compatible browser. | TBD | docs/qa/screenshots/api30/chrome.png |
| youtube | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens YouTube app/browser search or channel lookup; like/subscribe/comment refused. | TBD | docs/qa/screenshots/pixel9/youtube.png |
| youtube | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens YouTube app/browser search or channel lookup; like/subscribe/comment refused. | TBD | docs/qa/screenshots/s24/youtube.png |
| youtube | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens browser fallback if YouTube app is absent; engagement actions refused. | TBD | docs/qa/screenshots/api30/youtube.png |
| notes | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Shows system share sheet with note text; user chooses notes app/save target. | TBD | docs/qa/screenshots/pixel9/notes.png |
| notes | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Shows system share sheet with note text; user chooses notes app/save target. | TBD | docs/qa/screenshots/s24/notes.png |
| notes | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Shows chooser/share sheet with note text; no hidden note write. | TBD | docs/qa/screenshots/api30/notes.png |
| contacts | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Resolves local contact, opens contact/dialer/SMS draft; ambiguous names show chips. | TBD | docs/qa/screenshots/pixel9/contacts.png |
| contacts | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Resolves local contact, opens contact/dialer/SMS draft; ambiguous names show chips. | TBD | docs/qa/screenshots/s24/contacts.png |
| contacts | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | With seeded contacts/permission, opens drafts only; no `ACTION_CALL` or SMS send. | TBD | docs/qa/screenshots/api30/contacts.png |
| files | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Android file/document picker; file mutation requests refused. | TBD | docs/qa/screenshots/pixel9/files.png |
| files | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Android file/document picker; file mutation requests refused. | TBD | docs/qa/screenshots/s24/files.png |
| files | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens DocumentsUI picker; no automatic file read/upload/delete. | TBD | docs/qa/screenshots/api30/files.png |
| photos | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens Photos/Gallery; current-photo share only from viewer and via visible Share control. | TBD | docs/qa/screenshots/pixel9/photos.png |
| photos | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens Photos/Gallery; current-photo share only from viewer and via visible Share control. | TBD | docs/qa/screenshots/s24/photos.png |
| photos | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens gallery/image handler; delete photo/all requests refused. | TBD | docs/qa/screenshots/api30/photos.png |
| calculator | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Answers safe arithmetic in chat without execution; open calculator launches Calculator. | TBD | docs/qa/screenshots/pixel9/calculator.png |
| calculator | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Answers safe arithmetic in chat without execution; open calculator launches Calculator. | TBD | docs/qa/screenshots/s24/calculator.png |
| calculator | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Answers safe arithmetic locally; unsupported functions refused. | TBD | docs/qa/screenshots/api30/calculator.png |
| food_delivery | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens Swiggy/Zomato search or tracking; order/payment confirmation refused. | TBD | docs/qa/screenshots/pixel9/food_delivery.png |
| food_delivery | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens Swiggy/Zomato search or tracking; order/payment confirmation refused. | TBD | docs/qa/screenshots/s24/food_delivery.png |
| food_delivery | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens web fallback for food search/tracking; no ordering/payment action. | TBD | docs/qa/screenshots/api30/food_delivery.png |
| shopping_search | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Searches supported shopping app only; purchase/order/payment is refused. | TBD | docs/qa/screenshots/pixel9/shopping_search.png |
| shopping_search | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Searches supported shopping app only; purchase/order/payment is refused. | TBD | docs/qa/screenshots/s24/shopping_search.png |
| shopping_search | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Searches supported shopping app only; purchase/order/payment is refused. | TBD | docs/qa/screenshots/api30/shopping_search.png |
| shopping_find_coupons | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens visible coupons/offers affordance only; checkout/apply-payment blocked. | TBD | docs/qa/screenshots/pixel9/shopping_find_coupons.png |
| shopping_find_coupons | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens visible coupons/offers affordance only; checkout/apply-payment blocked. | TBD | docs/qa/screenshots/s24/shopping_find_coupons.png |
| shopping_find_coupons | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens visible coupons/offers affordance only; checkout/apply-payment blocked. | TBD | docs/qa/screenshots/api30/shopping_find_coupons.png |
| uber_ride | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens Uber and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/pixel9/uber_ride.png |
| uber_ride | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens Uber and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/s24/uber_ride.png |
| uber_ride | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Uber if installed or fails safely; no final ride confirmation. | TBD | docs/qa/screenshots/api30/uber_ride.png |
| ola_ride | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens Ola and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/pixel9/ola_ride.png |
| ola_ride | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens Ola and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/s24/ola_ride.png |
| ola_ride | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Ola if installed or fails safely; no final ride confirmation. | TBD | docs/qa/screenshots/api30/ola_ride.png |
| rapido_ride | Pixel 9 | Android 15 | 0.1.0 debug | Y | en-US | Opens Rapido and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/pixel9/rapido_ride.png |
| rapido_ride | Samsung S24 | Android 14 | 0.1.0 debug | Y | en-US | Opens Rapido and prepares destination; final ride confirmation remains user action. | TBD | docs/qa/screenshots/s24/rapido_ride.png |
| rapido_ride | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens Rapido if installed or fails safely; no final ride confirmation. | TBD | docs/qa/screenshots/api30/rapido_ride.png |
