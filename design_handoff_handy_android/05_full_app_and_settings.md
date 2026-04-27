# Full App + Settings — Kotlin / Compose recipe

The full app appears when the user taps "expand" in the chat overlay or
opens Handy from the launcher. It's a single-activity Compose app with a
nav graph: Home (chat) ←→ Settings (Brain / Modes / Triggers / Web Tools).

This doc covers visuals only — assume you already have a `MainActivity`
hosting a `NavHost`.

---

## Full chat view

Same `ChatOverlay` body but full-screen; the header swaps the **expand**
button for a **collapse** button (`ic_collapse`) which calls back to the
floating widget.

```
┌─────────────────────────────────────┐
│  ⟵          Handy            ⌄ ⚙   │   ← collapse, settings
│  On Maps — how can I help?          │
├─────────────────────────────────────┤
│  ScrollColumn of MessageBubbles      │
│  ...                                │
├─────────────────────────────────────┤
│  💬 input field        🎤 ➤        │
└─────────────────────────────────────┘
```

Header pattern matches the chat overlay (see `02_chat_overlay.md`) — same
HandMark+title cluster, icons aligned to the title row, subtitle hangs
below at the same x-offset as the title.

```kotlin
@Composable
fun FullAppScreen(
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = HandyColors.Bg,
        topBar = {
            HandyTopBar(
                contextLabel = state.activeContext,
                trailing = {
                    IconButton(onClick = onCollapse) {
                        Icon(painterResource(R.drawable.ic_collapse), null,
                            tint = HandyColors.TextSecondary)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(painterResource(R.drawable.ic_settings), null,
                            tint = HandyColors.TextSecondary)
                    }
                }
            )
        },
        bottomBar = { ChatInputBar(onSend = viewModel::send, onMic = viewModel::startListening) }
    ) { padding ->
        ChatList(messages = state.messages, modifier = Modifier.padding(padding))
    }
}
```

`HandyTopBar` = HandMark(20dp) + "Handy" + subtitle (`On <context>`),
trailing slot for actions. Use the same alignment trick as the overlay:
icon row is `alignment = Top`, height matches the title row so it sits at
the title baseline, not centered against the stack.

---

## Settings — section + row pattern

A scrollable column of **sections**. Each section has:

- 32dp accent-tinted rounded-square icon (9dp radius, 0.5dp `chipBorder`)
- Title (16sp, weight 700) and subtitle (12.5sp, `textSecondary`)
- The icon, title, and subtitle are **one Row** locked together — the
  subtitle wraps under the title at the same indent as the title (NOT a
  bare `marginLeft: 38px` hanging in space)
- Below the header: a vertical stack of `SettingsRow` items, 8dp gap

```kotlin
@Composable
fun SettingsSection(
    icon: Painter,
    title: String,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(HandyColors.AccentSoft)
                    .border(0.5.dp, HandyColors.ChipBorder, RoundedCornerShape(9.dp))
                    .padding(top = 1.dp),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = HandyColors.Accent, modifier = Modifier.size(16.dp)) }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = HandyType.titleMedium,
                     color = HandyColors.TextPrimary, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = HandyType.bodySmall, color = HandyColors.TextSecondary)
                }
            }
        }
        content()
    }
}
```

---

## Brain section — model cards with inline API key

Each model option is a card. When **selected**, the card expands to reveal
its API key field inline. This means there's no separate "API Keys" page —
keys live next to the model they unlock.

```kotlin
@Composable
fun ModelCard(
    name: String,
    provider: String,
    description: String,
    selected: Boolean,
    apiKey: String,
    keyVisible: Boolean,
    sharedWith: String? = null,   // e.g. "Reuses Sonnet's Anthropic key"
    onSelect: () -> Unit,
    onKeyChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) HandyColors.AccentSoft else HandyColors.Surface)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) HandyColors.Accent.copy(alpha = 0.6f)
                        else HandyColors.Border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, style = HandyType.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(provider, style = HandyType.labelSmall,
                         color = HandyColors.TextSecondary)
                }
                Text(description, style = HandyType.bodySmall,
                     color = HandyColors.TextSecondary)
            }
            RadioBubble(selected)
        }

        if (selected) {
            Divider(color = HandyColors.Border, thickness = 0.5.dp)
            if (sharedWith != null) {
                Text(sharedWith, style = HandyType.labelSmall,
                     color = HandyColors.TextSecondary,
                     fontStyle = FontStyle.Italic)
            } else {
                ApiKeyField(
                    value = apiKey,
                    visible = keyVisible,
                    onChange = onKeyChange,
                    onToggleVisibility = onToggleVisibility,
                )
            }
        }
    }
}
```

`ApiKeyField` is a `OutlinedTextField` with `visualTransformation =
PasswordVisualTransformation()` toggled by `keyVisible`, plus a trailing
icon button for show/hide and a leading icon for the provider.

`RadioBubble` is a 20dp circle, accent fill + check icon when selected,
hollow border (`textTertiary`) when not.

---

## Web Tools — nested keys

A single section with three tool cards:
- **Brave Search** (web search)
- **Jina Reader** (URL → markdown)
- **GitHub** (search + read public repos)

Each card has a **toggle** (enable/disable) and an **inline API key** that
appears when enabled. Same pattern as the model cards but rectangular toggle
instead of radio.

---

## Modes & Triggers

Both are list-based. Modes are presets (Default, Coding, Travel) — the user
picks one. Triggers are rules (e.g. "double-volume-down → start listening")
— the user enables/configures each one.

Use the same `SettingsSection` header pattern. Inside, list rows with a
trailing `Switch` (Triggers) or radio (Modes).

Material 3 `Switch` defaults are fine, but recolor:

```kotlin
SwitchDefaults.colors(
    checkedThumbColor = HandyColors.AccentInk,
    checkedTrackColor = HandyColors.Accent,
    uncheckedThumbColor = HandyColors.TextTertiary,
    uncheckedTrackColor = HandyColors.Surface2,
)
```
