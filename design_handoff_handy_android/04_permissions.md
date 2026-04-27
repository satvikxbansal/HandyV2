# Permissions Screen — first-run onboarding

A single full-screen Compose screen that walks the user through the four
permissions Handy needs:

1. **Microphone** — for voice input (STT)
2. **Notifications** — for status while running in background
3. **Display over other apps** — for the floating widget (`SYSTEM_ALERT_WINDOW`)
4. **Accessibility Service** — for the widget to live above other apps and read context

Plus a privacy callout explaining that API keys talk to model providers
**directly** from the device — no Handy server in the middle.

---

## Layout

- Top: small hand mark (32dp) in an amber-tinted rounded square (44dp, 12dp radius)
- "Welcome to Handy" — `headline` size, weight 700
- Subtitle: "A few permissions and we're ready." — `bodyLarge`, `textSecondary`
- Four `PermissionCard` rows, vertical stack, 10dp gap
- Privacy callout at bottom (subtle bordered box, 16dp radius, `surface2` background)
- Bottom CTA: "Continue" — full-width pill, `accent` background, ink-on-accent text

Each `PermissionCard`:
- Icon (24dp) in a 36×36 amber-tinted rounded square
- Title (15sp, weight 600) + 1-line subtitle (13sp, `textSecondary`)
- Right side: state pill — "Granted" (success) / "Required" (accent) / "Optional" (secondary)
- Tap row → request permission via the appropriate launcher

---

## Compose recipe

```kotlin
@Composable
fun PermissionsScreen(
    onComplete: () -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    Surface(color = HandyColors.Bg, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HeroBlock()

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionCard(
                    icon = R.drawable.ic_mic,
                    title = "Microphone",
                    subtitle = "Voice input for hands-free use",
                    status = state.mic,
                    required = true,
                    onClick = { micLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                )
                PermissionCard(
                    icon = R.drawable.ic_bell,
                    title = "Notifications",
                    subtitle = "Status while running in the background",
                    status = state.notifications,
                    required = false,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
                PermissionCard(
                    icon = R.drawable.ic_layers,
                    title = "Display over other apps",
                    subtitle = "So the widget can sit on top",
                    status = state.overlay,
                    required = true,
                    onClick = {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"))
                        context.startActivity(intent)
                    }
                )
                PermissionCard(
                    icon = R.drawable.ic_accessibility,
                    title = "Accessibility",
                    subtitle = "For the widget to read app context",
                    status = state.accessibility,
                    required = true,
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )
            }

            PrivacyCallout()

            Button(
                onClick = onComplete,
                enabled = state.allRequiredGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HandyColors.Accent,
                    contentColor = HandyColors.AccentInk,
                    disabledContainerColor = HandyColors.Accent.copy(alpha = 0.4f),
                )
            ) {
                Text(
                    if (state.allRequiredGranted) "Continue" else "Grant required permissions",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HeroBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HandyColors.AccentSoft),
            contentAlignment = Alignment.Center
        ) {
            HandMark(size = 32.dp, tint = HandyColors.Accent)
        }
        Text("Welcome to Handy",
            style = HandyType.headlineLarge,
            color = HandyColors.TextPrimary)
        Text("A few permissions and we're ready.",
            style = HandyType.bodyLarge,
            color = HandyColors.TextSecondary)
    }
}

@Composable
private fun PrivacyCallout() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HandyColors.Surface2)
            .border(0.5.dp, HandyColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(painterResource(R.drawable.ic_shield), null,
            tint = HandyColors.Accent, modifier = Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your keys, your traffic.",
                style = HandyType.titleSmall, color = HandyColors.TextPrimary)
            Text("Handy talks to Anthropic, Google, and search providers " +
                 "directly from your device using API keys you provide. " +
                 "Nothing routes through our servers.",
                style = HandyType.bodySmall, color = HandyColors.TextSecondary)
        }
    }
}
```

---

## Status pill

```kotlin
@Composable
fun StatusPill(status: PermissionStatus, required: Boolean) {
    val (label, fg, bg) = when (status) {
        Granted -> Triple("Granted", HandyColors.Success, HandyColors.SuccessSoft)
        Denied  -> Triple(if (required) "Required" else "Optional",
                         HandyColors.Accent, HandyColors.AccentSoft)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, style = HandyType.labelSmall, color = fg, fontWeight = FontWeight.Medium)
    }
}
```
