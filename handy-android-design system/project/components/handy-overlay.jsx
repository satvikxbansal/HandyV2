// Chat overlay — the quick chat glass card that floats at the bottom
// of any screen. Has header, input, voice, send, context-aware chips.

function ChatOverlay({ theme, context = "home", expanded = false, tweaks = {} }) {
  const contextChips = {
    home: ["Open my calendar", "Set a timer", "What's my day like?"],
    photos: ["Find photos from Paris", "Make a collage", "Delete duplicates"],
    map: ["Nearest coffee", "Avoid tolls", "ETA to home"],
  };

  const {
    handSize = 24,
    titleSize = 18,
    subtitleSize = 13,
    headerGap = 4,
    padding = 18,
    chipCount = 2,
    showExpand = true,
    showClose = true,
  } = tweaks;

  const chips = (contextChips[context] || contextChips.home).slice(0, chipCount);
  const contextLabel = context === "home" ? "Home" : context === "photos" ? "Photos" : "Maps";

  return (
    <GlassCard theme={theme} radius={28} intensity={75}
               style={{ margin: "0 12px 12px" }}>
      <div style={{
        padding: `${padding}px ${padding}px ${padding}px`,
        fontFamily: HANDY_FONT,
        color: theme.textPrimary,
      }}>
        {/* Header row — bigger hand + title, stronger hierarchy */}
        <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: padding - 2 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, lineHeight: 1.1 }}>
              <HandMark size={handSize} color={theme.accent} />
              <span style={{ fontSize: titleSize, fontWeight: 700, letterSpacing: -0.3 }}>Handy</span>
            </div>
            <div style={{ fontSize: subtitleSize, color: theme.textSecondary, lineHeight: 1.35,
                          marginTop: headerGap,
                          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              On <span style={{ color: theme.accent, fontWeight: 500 }}>{contextLabel}</span> — how can I help?
            </div>
          </div>

          {showExpand && <BareIconBtn>{Icon.expand(16, theme.textSecondary)}</BareIconBtn>}
          {showClose && <BareIconBtn>{Icon.close(16, theme.textSecondary)}</BareIconBtn>}
        </div>

        {/* Input */}
        <InputRow theme={theme} placeholder="Ask me anything…" />

        {/* Chips */}
        {chipCount > 0 && (
          <div style={{ display: "flex", gap: 10, marginTop: padding - 2, flexWrap: "wrap" }}>
            {chips.map((c, i) => (
              <QuickChip key={i} theme={theme}>{c}</QuickChip>
            ))}
          </div>
        )}
      </div>
    </GlassCard>
  );
}

// Icon-only button, no circle — for the header affordances
function BareIconBtn({ children, onClick }) {
  return (
    <button onClick={onClick} style={{
      width: 28, height: 28, borderRadius: 8,
      background: "transparent",
      border: "none",
      display: "flex", alignItems: "center", justifyContent: "center",
      cursor: "pointer", padding: 0,
      opacity: 0.75, transition: "opacity 150ms",
    }}
    onMouseEnter={e => e.currentTarget.style.opacity = "1"}
    onMouseLeave={e => e.currentTarget.style.opacity = "0.75"}
    >{children}</button>
  );
}

function IconBtn({ theme, children, onClick }) {
  return (
    <button onClick={onClick} style={{
      width: 30, height: 30, borderRadius: "50%",
      background: theme.chipBg,
      border: `0.5px solid ${theme.chipBorder}`,
      display: "flex", alignItems: "center", justifyContent: "center",
      cursor: "pointer", padding: 0,
    }}>{children}</button>
  );
}

function InputRow({ theme, placeholder, active = false }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
      {/* Mic */}
      <button style={{
        width: 40, height: 40, borderRadius: "50%",
        background: theme.chipBg,
        border: `0.5px solid ${theme.chipBorder}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        cursor: "pointer", padding: 0, flexShrink: 0,
      }}>
        {Icon.mic(18, theme.textPrimary)}
      </button>
      {/* Text field */}
      <div style={{
        flex: 1, height: 40,
        borderRadius: 999,
        background: theme.chipBg,
        border: `0.5px solid ${active ? theme.accent : theme.chipBorder}`,
        display: "flex", alignItems: "center",
        padding: "0 16px",
        color: theme.textMuted,
        fontSize: 14, fontFamily: HANDY_FONT,
      }}>
        {placeholder}
      </div>
      {/* Send */}
      <button style={{
        width: 40, height: 40, borderRadius: "50%",
        background: theme.accent,
        border: "none",
        display: "flex", alignItems: "center", justifyContent: "center",
        cursor: "pointer", padding: 0, flexShrink: 0,
        boxShadow: `0 6px 14px -4px ${theme.accent}88`,
      }}>
        {Icon.send(17, theme.accentInk)}
      </button>
    </div>
  );
}

function QuickChip({ theme, children, icon }) {
  return (
    <div style={{
      padding: "7px 12px",
      borderRadius: 999,
      background: theme.chipBg,
      border: `0.5px solid ${theme.chipBorder}`,
      color: theme.textPrimary,
      fontSize: 12, fontWeight: 500,
      fontFamily: HANDY_FONT,
      display: "flex", alignItems: "center", gap: 6,
      whiteSpace: "nowrap",
    }}>
      {icon && <span style={{ color: theme.accent, display: "flex" }}>{icon}</span>}
      {children}
    </div>
  );
}

Object.assign(window, { ChatOverlay, IconBtn, BareIconBtn, InputRow, QuickChip });
