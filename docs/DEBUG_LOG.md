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

## DL-FW-1 — WidgetGlyphV2 replaces legacy WidgetContent in the floating overlay service
- All glyphs migrated to HandyDesign tokens. Layers A (colored glow) -> B (disc fill + border) -> C (per-state glyph). Outer canvas stays 64dp to preserve the existing pulse band.
- AccentGlow / PointGlow / PointTrail added to HandyDesign.Colors.
- ACTING is now a distinct widget state with a bolt corner badge (Surface fill, 1.5dp Accent border, ic_bolt at 10dp). Service mapping updated: BuddyState.ACTING -> WidgetState.ACTING.
- Flying renders the same pointer geometry as Pointing (no arrow) and adds a 36x16 dp trail BEHIND the disc, anchored 22 dp left of the disc centre, rotated by `pointerRotationRadians.toDegrees()` around the disc center so it always trails opposite to motion. Trail alpha is driven by pointerScale > 1.02f with a 120 ms fade so the trail disappears cleanly on Flying -> Pointing.
- Pointer drawable swapped from ic_pointer_hand to ic_phosphor_hand_pointing_bold. Both vectors default to finger-up; the existing `+ 90f` rotation offset is preserved.
- Pre-API-31 blur fallback uses three layered soft circles (10/18/30 % alpha) instead of Modifier.blur.
- WidgetContent.kt is retained — UnifiedBuddyContent, WidgetBubbleChip, ManualTargetFallbackChip still consume the legacy composable. They will be migrated when we touch the bubble system.
