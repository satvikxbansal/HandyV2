## DL-CH-3 — ChatBannersV2 replaces legacy ErrorBanner + BudgetWarningBanner
- ErrorBannerV2 mirrors ReducedBannerV2's shape (RoundedCornerShape(14.dp),
  soft tint + hairline border, 14h/12v padding). Danger tint used for
  errors; Honey for low budget, Danger for exhausted. This is the family
  pattern extended from ReducedBannerV2 — no separate JSX scene exists
  for it in the handoff so the spec is derived from the same primitives.
- 16/4/16 outer padding matches the existing ReducedBannerV2 placement
  so banners line up visually under the top bar.
- dispatch_action dialog migration is CH-4; theme wrapper cleanup is CH-5.

## DL-CH-4 — ConfirmActionSheetV2 replaces AlertDialog for dispatch_action
- Mirrors the PrivacyDisclosureSheet sheet family: ModalBottomSheet with
  skipPartiallyExpanded=true, CornerSheetTop=24.dp, SurfaceElevated bg,
  custom 40x4 drag handle, PrimaryButton + SecondaryTextButton stack.
- Destructive action -> PrimaryButton tinted with HandyDesign.Colors.Danger
  instead of Accent. To avoid duplicating PrimaryButton, the existing
  PrimaryButton is extended with optional container/content colour params;
  all existing call sites continue to use the Accent defaults.
- Sheet hide() is awaited via invokeOnCompletion before forwarding the
  accept/decline result so the chat behind doesn't flash.
