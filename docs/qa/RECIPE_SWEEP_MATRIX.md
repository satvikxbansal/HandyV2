# Recipe Sweep Matrix

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
| create_calendar_event | Pixel 9 | Android 15 | 0.1.0 debug | N | en-US | Opens calendar event draft; Save remains a user action with strong hold. | TBD | docs/qa/screenshots/pixel9/create_calendar_event.png |
| create_calendar_event | Samsung S24 | Android 14 | 0.1.0 debug | N | en-US | Opens calendar event draft; Save remains a user action with strong hold. | TBD | docs/qa/screenshots/s24/create_calendar_event.png |
| create_calendar_event | Emulator API 30 | Android 11 | 0.1.0 debug | N | en-US | Opens calendar event draft; Save remains a user action with strong hold. | TBD | docs/qa/screenshots/api30/create_calendar_event.png |
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
