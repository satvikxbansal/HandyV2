// scenes-system.jsx — System artboards: palettes, color tokens, type scale,
// illustration sheet, component library.

// ────────────────────────────────────────────────────────────────────────
//  PALETTE COMPARISON  —  two themes side by side at the top of the canvas
// ────────────────────────────────────────────────────────────────────────

function PaletteHero({ themeKey }) {
  const theme = HANDY_TOKENS[themeKey];
  const isAmber = themeKey === "amber";
  return (
    <ThemeProvider theme={themeKey}>
      <div style={{
        width: 560, height: 700,
        background: theme.colors.pageBg,
        borderRadius: 20,
        padding: 36,
        position: "relative",
        overflow: "hidden",
        fontFamily: HANDY_TYPE.fontBody,
        color: theme.colors.textPrimary,
      }}>
        <div style={{
          color: theme.colors.accent,
          font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.14em", textTransform: "uppercase",
        }}>{`Handy · ${theme.name} system`}</div>

        <div style={{
          marginTop: 22, font: `600 44px/1.04 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.028em",
          maxWidth: 460,
        }}>
          {isAmber ? <>Warm amber,<br />present and<br /> inviting.</>
                   : <>Bone white,<br />cool and<br />precise.</>}
        </div>

        <div style={{
          marginTop: 16, font: `400 15px/1.55 ${HANDY_TYPE.fontBody}`,
          color: theme.colors.textSecondary, maxWidth: 460,
        }}>
          {isAmber
            ? "Near-black glass with a Claude-orange accent that echoes the hand. One typeface, varied weights. One accent — everything else neutral."
            : "Same dark surfaces, but the accent recedes to bone. Amber survives only on the hand-mark itself — the brand's one privileged color."}
        </div>

        {/* mark + swatches */}
        <div style={{
          position: "absolute", left: 36, bottom: 36, right: 36,
          display: "flex", alignItems: "flex-end", justifyContent: "space-between",
        }}>
          <HandMark variant="open" container="tile" size={84} />
          <div style={{ display: "flex", gap: 14 }}>
            {[
              { c: theme.colors.accent,          label: "Accent" },
              { c: theme.colors.markAccent || theme.colors.accent, label: "Mark"  },
              { c: theme.colors.textPrimary,     label: "Text"   },
              { c: theme.colors.textMuted,       label: "Muted"  },
              { c: theme.colors.surface,         label: "Surface", border: true },
            ].map((s, i) => (
              <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
                <div style={{
                  width: 44, height: 44, borderRadius: 10,
                  background: s.c,
                  border: s.border ? `1px solid ${theme.colors.borderSubtle}` : "none",
                }} />
                <div style={{ font: `400 11px/1 ${HANDY_TYPE.fontBody}`, color: theme.colors.textMuted }}>
                  {s.label}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* tiny tag bottom-left */}
        <div style={{
          position: "absolute", left: 36, bottom: 14,
          color: theme.colors.textMuted,
          font: `400 11px/1 ${HANDY_TYPE.fontMono}`,
          letterSpacing: "0.04em",
        }}>
          {HANDY_TYPE.fontDisplay.split(",")[0].replace(/"/g, "")} · 400 / 500 / 600
        </div>
      </div>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  COLOR TOKENS  —  dp-annotated swatch list
// ────────────────────────────────────────────────────────────────────────

function ColorTokens() {
  const tokens = [
    { name: "pageBg",          hex: "#08090B", desc: "Full-screen background" },
    { name: "surface",         hex: "#111317", desc: "Cards, rows, sections" },
    { name: "surfaceElevated", hex: "#181A1F", desc: "Nested rows, hover/press" },
    { name: "borderSubtle",    hex: "rgba(255,255,255,0.08)", desc: "All dividers" },
    { name: "borderStrong",    hex: "rgba(255,255,255,0.14)", desc: "Focused, selected" },
    { name: "textPrimary",     hex: "#F4F2EE", desc: "Titles, body" },
    { name: "textSecondary",   hex: "#A8A39B", desc: "Subtitles, descriptions" },
    { name: "textMuted",       hex: "#6E6A63", desc: "Footnotes" },
    { name: "accent",          hex: "#D97757", desc: "Primary, mark, live dot" },
    { name: "accentInk",       hex: "#1A0E07", desc: "Text on accent" },
    { name: "accentSoft",      hex: "#D97757 @ 12%", desc: "Selected tints" },
    { name: "success",         hex: "#7FB069", desc: "Granted, On" },
    { name: "danger",          hex: "#D67D6B", desc: "Stop, destructive" },
  ];
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 560, height: 700,
        background: HANDY_TOKENS.amber.colors.pageBg,
        borderRadius: 20, padding: 28,
        fontFamily: HANDY_TYPE.fontBody, color: "#F4F2EE",
      }}>
        <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>Color tokens</div>
        <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: "#A8A39B", marginTop: 6 }}>
          Map 1:1 to Compose <code style={{ font: `12px ${HANDY_TYPE.fontMono}`, color: "#D97757" }}>ColorScheme</code> entries. Names below are the Color.kt identifiers.
        </div>

        <div style={{ marginTop: 22, display: "flex", flexDirection: "column", gap: 0 }}>
          {tokens.map((t, i) => (
            <div key={i} style={{
              display: "flex", alignItems: "center", gap: 16,
              padding: "10px 0",
              borderBottom: i === tokens.length - 1 ? "none" : "1px solid rgba(255,255,255,0.06)",
            }}>
              <div style={{
                width: 36, height: 36, borderRadius: 8,
                background: t.hex.includes("rgba") ? `${t.hex.replace("@ ", "@")}, #111317` : t.hex,
                backgroundColor: t.hex.startsWith("#") ? t.hex : "#181A1F",
                border: t.hex.includes("rgba") || t.name === "surface" || t.name === "pageBg" || t.name === "surfaceElevated" ? "1px solid rgba(255,255,255,0.08)" : "none",
                flex: "0 0 auto",
              }} />
              <div style={{ flex: 1 }}>
                <div style={{ font: `500 14px/1.2 ${HANDY_TYPE.fontMono}`, color: "#F4F2EE" }}>{t.name}</div>
                <div style={{ font: `400 12px/1.4 ${HANDY_TYPE.fontBody}`, color: "#A8A39B", marginTop: 2 }}>{t.desc}</div>
              </div>
              <div style={{ font: `400 12px/1 ${HANDY_TYPE.fontMono}`, color: "#6E6A63" }}>{t.hex}</div>
            </div>
          ))}
        </div>
      </div>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  TYPE SCALE
// ────────────────────────────────────────────────────────────────────────

function TypeScale() {
  const t = HANDY_TOKENS.amber;
  const samples = [
    { token: "display",    sample: "One more step", composeName: "displaySmall" },
    { token: "title",      sample: "Brain",         composeName: "titleLarge" },
    { token: "titleSmall", sample: "Claude Sonnet 4.5", composeName: "titleMedium" },
    { token: "bodyStrong", sample: "Tap-for-me", composeName: "bodyLarge (medium)" },
    { token: "body",       sample: "Handy can chat, see your screen, and act with your OK.", composeName: "bodyMedium" },
    { token: "caption",    sample: "Won't: read fields you've marked sensitive.", composeName: "bodySmall" },
    { token: "overline",   sample: "Brain", composeName: "labelSmall (uppercase)" },
  ];
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 720, height: 700,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 32,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
      }}>
        <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>Type scale</div>
        <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6 }}>
          One family — display fallbacks to Inter Tight, body to Inter. Sizes are sp; map straight to Compose Typography.
        </div>

        <div style={{ marginTop: 24, display: "flex", flexDirection: "column", gap: 18 }}>
          {samples.map((s, i) => {
            const tok = HANDY_TYPE.scale[s.token];
            return (
              <div key={i} style={{
                display: "flex", alignItems: "flex-end", gap: 24,
                paddingBottom: 16,
                borderBottom: i === samples.length - 1 ? "none" : "1px solid rgba(255,255,255,0.06)",
              }}>
                <div style={{ flex: 1, ...typeStyle(s.token, t) }}>{s.sample}</div>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 4, flex: "0 0 auto", width: 200 }}>
                  <div style={{ font: `400 11px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>
                    {tok.size}/{tok.lh}sp · w{tok.weight} · {tok.tracking >= 0 ? "+" : ""}{tok.tracking}em
                  </div>
                  <div style={{ font: `500 11px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.accent }}>
                    {s.token} → {s.composeName}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  ILLUSTRATION SHEET — all 9 brief motifs + utility glyphs
// ────────────────────────────────────────────────────────────────────────

function IllustrationSheet() {
  const t = HANDY_TOKENS.amber;
  const motifs = [
    { name: "handOpen",   label: "Hand · outline", note: "Wordmark · bare · tile" },
    { name: "handFill",   label: "Hand · fill",    note: "Widget · splash · disc" },
    { name: "handWave",   label: "Hand · wave",    note: "Splash, tilted" },
    { name: "cursor",     label: "Cursor",         note: "Widget pointing/flying" },
    { name: "eye",        label: "Eye",            note: "Read screen" },
    { name: "eye_off",    label: "Eye · closed",   note: "Reduced mode" },
    { name: "a11y",       label: "Accessibility",  note: "Platform a11y" },
    { name: "keyboard",   label: "Keyboard",       note: "Type-for-me" },
    { name: "bolt",       label: "Bolt",           note: "Quick action" },
    { name: "shield",     label: "Shield",         note: "Privacy (outline)" },
    { name: "shieldFill", label: "Shield · fill",  note: "Privacy (tile)" },
    { name: "sparkle",    label: "Sparkle",        note: "AI moment" },
    { name: "mic",        label: "Microphone",     note: "Voice" },
    { name: "bell",       label: "Bell",           note: "Notifications" },
    { name: "overlay",    label: "Overlay",        note: "Draw over apps" },
    { name: "screen",     label: "Screen + bracket", note: "Reads screen" },
    { name: "ask",        label: "Speech bubble",  note: "Ask" },
  ];
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 1160, height: 760,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 36,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
      }}>
        <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
          Illustration set
        </div>
        <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6, maxWidth: 700 }}>
          One stroke color, rounded caps, 1.6–2.4 px stroke depending on render size. Drawn on a 48×48 grid so they nest cleanly into a 36 dp leading tile. No fills (except the tap-target dot + send glyph).
        </div>

        <div style={{
          marginTop: 28, display: "grid",
          gridTemplateColumns: "repeat(7, 1fr)",
          gap: "18px 14px",
        }}>
          {motifs.map((m, i) => (
            <div key={i} style={{
              display: "flex", flexDirection: "column", alignItems: "center", gap: 8,
              padding: "14px 6px",
              background: t.colors.surface,
              border: `1px solid ${t.colors.borderSubtle}`,
              borderRadius: 14,
            }}>
              <Illu name={m.name} size={56} color={t.colors.accent} />
              <div style={{ font: `500 12px/1.2 ${HANDY_TYPE.fontBody}`, color: t.colors.textPrimary, marginTop: 4 }}>{m.label}</div>
              <div style={{ font: `400 10px/1.2 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted, textAlign: "center" }}>{m.note}</div>
            </div>
          ))}
        </div>

        {/* Size sheet for handOpen */}
        <div style={{
          marginTop: 26, display: "flex", alignItems: "flex-end", gap: 40,
          padding: 20, borderRadius: 14,
          background: t.colors.surface, border: `1px solid ${t.colors.borderSubtle}`,
        }}>
          <div style={{ font: `500 12px/1 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, alignSelf: "center" }}>
            Render sizes (Open-palm shown)
          </div>
          {[
            { size: 200, label: "Hero · 200 dp" },
            { size: 72,  label: "Spot · 72 dp" },
            { size: 36,  label: "Tile · 36 dp" },
            { size: 24,  label: "Inline · 24 dp" },
          ].map((sz, i) => (
            <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              <Illu name="handOpen" size={sz.size} color={t.colors.accent} opacity={0.96} />
              <div style={{ font: `400 11px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>{sz.label}</div>
            </div>
          ))}
        </div>
      </div>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  COMPONENT LIBRARY
// ────────────────────────────────────────────────────────────────────────

function ComponentLibrary() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 1160, height: 1080,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 36,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
      }}>
        <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
          Component library
        </div>
        <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6 }}>
          Each block annotated with its suggested Compose composable name + key params.
        </div>

        <div style={{ marginTop: 28, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 28 }}>
          {/* Buttons */}
          <Block title="PrimaryButton · SecondaryTextButton · DestructiveButton" compose="@Composable fun PrimaryButton(label, onClick, trailingIcon, enabled)">
            <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
              <PrimaryButton label="Get started" />
              <PrimaryButton label="Open Handy" disabled />
              <div style={{ display: "flex", gap: 10 }}>
                <SecondaryTextButton label="Use without app detection" />
                <DestructiveButton label="Clear chat history" />
              </div>
            </div>
          </Block>

          {/* Status pills */}
          <Block title="StatusPill" compose="enum PillKind { Success, Accent, Muted, Danger }">
            <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
              <Pill label="Granted" kind="success" />
              <Pill label="Enable" kind="accent" />
              <Pill label="On" kind="success" />
              <Pill label="Off" kind="muted" />
              <Pill label="Coming soon" kind="muted" />
              <Pill label="Limited" kind="accent" />
            </div>
          </Block>

          {/* Toggles + Radio */}
          <Block title="HandySwitch · RadioDot" compose="HandySwitch(checked, onCheckedChange)">
            <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
              <Toggle on={true} />
              <Toggle on={false} />
              <Toggle on={false} disabled />
              <span style={{ font: `400 13px/1 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted }}>Radio →</span>
              <RadioDot on />
              <RadioDot />
            </div>
          </Block>

          {/* Text field */}
          <Block title="HandyTextField" compose="HandyTextField(value, onValueChange, placeholder, masked, trailing)">
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <TextField placeholder="Search settings" trailing={<Illu name="search" size={16} color={t.colors.textMuted} />} />
              <TextField value="sk-ant-api03-aBcD…xyz" masked trailing={
                <div style={{ display: "flex", gap: 4 }}>
                  <IconButton name="eye" size={16} />
                  <IconButton name="copy" size={16} />
                </div>
              } />
            </div>
          </Block>

          {/* Row */}
          <Block title="Row" compose="Row(illu, title, caption, trailing, selected)">
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <Row illu="mic" title="Microphone" caption="For voice when you long-press." trailing={<Pill label="Granted" kind="success" />} />
              <Row illu="a11y" title="Accessibility" caption="Read the active screen to help in context." trailing={<Pill label="Enable" kind="accent" />} />
            </div>
          </Block>

          {/* Composer */}
          <Block title="ChatComposer" compose="ChatComposer(text, onTextChange, onSend, onMic, glass)">
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              <Composer />
              <Composer glass placeholder="What would you like help with?" />
            </div>
          </Block>

          {/* Chips */}
          <Block title="QuickPromptChip · QuickPromptCard" compose="QuickPromptCard(illu, label, sublabel, onClick)">
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <div style={{ display: "flex", gap: 8 }}>
                <QuickPromptChip illu="screen" label="Show me around" />
                <QuickPromptChip illu="ask" label="What can I do here?" />
                <QuickPromptChip illu="bolt" label="Quick action" />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
                <QuickPromptCard illu="eye" label="Summarize this screen" />
                <QuickPromptCard illu="bolt" label="Set a 10-minute timer" />
              </div>
            </div>
          </Block>

          {/* Hand mark variants */}
          <Block title="HandMark" compose='HandMark(variant: WAVE|OPEN|POINT|TAP|PENCIL, container: BARE|TILE|DISC)'>
            <div style={{ display: "flex", gap: 14, alignItems: "center" }}>
              <HandMark variant="wave"   container="bare" size={36} />
              <HandMark variant="open"   container="tile" size={48} />
              <HandMark variant="point"  container="tile" size={48} />
              <HandMark variant="wave"   container="disc" size={56} />
              <span style={{
                width: 56, height: 56, borderRadius: "50%",
                background: HANDY_TOKENS.amber.colors.accent,
                boxShadow: `0 6px 18px -4px ${HANDY_TOKENS.amber.colors.accent}55`,
                display: "inline-flex", alignItems: "center", justifyContent: "center",
              }}>
                <Illu name="cursor" size={28} color={HANDY_TOKENS.amber.colors.accentInk} />
              </span>
              <span style={{ font: `400 11px/1.4 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted, marginLeft: 4 }}>
                bare · tile · tile · disc · cursor-disc
              </span>
            </div>
          </Block>
        </div>
      </div>
    </ThemeProvider>
  );
}

function Block({ title, compose, children }) {
  const t = HANDY_TOKENS.amber;
  return (
    <div style={{
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 16,
      padding: 22,
      display: "flex", flexDirection: "column", gap: 14,
    }}>
      <div>
        <div style={{ font: `500 13px/1.2 ${HANDY_TYPE.fontBody}`, color: t.colors.textPrimary }}>{title}</div>
        <div style={{ font: `400 11px/1.4 ${HANDY_TYPE.fontMono}`, color: t.colors.accent, marginTop: 4 }}>
          {compose}
        </div>
      </div>
      <div style={{ borderTop: `1px solid ${t.colors.borderSubtle}`, paddingTop: 14 }}>
        {children}
      </div>
    </div>
  );
}

window.PaletteHero = PaletteHero;
window.ColorTokens = ColorTokens;
window.TypeScale = TypeScale;
window.IllustrationSheet = IllustrationSheet;
window.ComponentLibrary = ComponentLibrary;
