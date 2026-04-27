// Full Handy app — chat conversation view

function FullChatApp({ theme, state = "empty" }) {
  return (
    <div style={{
      position: "absolute", inset: 0,
      background: theme.pageBg,
      display: "flex", flexDirection: "column",
      fontFamily: HANDY_FONT,
      color: theme.textPrimary,
    }}>
      {/* Header — hand aligned to the whole title+status block */}
      <div style={{
        padding: "18px 20px 14px",
        display: "flex", alignItems: "center", gap: 14,
        borderBottom: `0.5px solid ${theme.divider}`,
      }}>
        {/* Hand — vertically centers across title + status rows */}
        <HandMark size={32} color={theme.accent} />

        {/* Title + status */}
        <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: 3 }}>
          <div style={{ fontSize: 20, fontWeight: 700, letterSpacing: -0.4, lineHeight: 1.1 }}>Handy</div>
          <div style={{ fontSize: 12, color: theme.textSecondary, lineHeight: 1.2,
                        display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 6, height: 6, borderRadius: "50%", background: theme.success }} />
            Ready
          </div>
        </div>

        {/* Actions — all same size, same treatment, aligned */}
        <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
          <HeaderIconBtn theme={theme}>{Icon.history(18, theme.textSecondary)}</HeaderIconBtn>
          <HeaderIconBtn theme={theme}>{Icon.collapse(18, theme.textSecondary)}</HeaderIconBtn>
          <HeaderIconBtn theme={theme}>{Icon.settings(18, theme.textSecondary)}</HeaderIconBtn>
        </div>
      </div>

      {/* Context chip */}
      {state === "chat" && (
        <div style={{
          margin: "12px 16px 0",
          padding: "10px 14px",
          borderRadius: 14,
          background: theme.chipBg,
          border: `0.5px solid ${theme.chipBorder}`,
          display: "flex", alignItems: "center", gap: 10,
          fontSize: 12,
        }}>
          <div style={{ color: theme.accent, display: "flex" }}>{Icon.camera(14, theme.accent)}</div>
          <span style={{ color: theme.textSecondary }}>Context from</span>
          <span style={{ color: theme.textPrimary, fontWeight: 600 }}>Photos</span>
          <span style={{ flex: 1 }} />
          <span style={{ color: theme.textMuted, fontSize: 11 }}>Change</span>
        </div>
      )}

      {/* Body */}
      <div style={{ flex: 1, overflow: "hidden", position: "relative" }}>
        {state === "empty" ? <EmptyState theme={theme} /> : <Conversation theme={theme} />}
      </div>

      {/* Composer */}
      <div style={{
        padding: "10px 14px 14px",
        borderTop: `0.5px solid ${theme.divider}`,
        background: theme.pageBg,
      }}>
        <InputRow theme={theme} placeholder="Ask Handy anything…" />
      </div>
    </div>
  );
}

function EmptyState({ theme }) {
  const suggestions = [
    { icon: Icon.sparkle(14, theme.accent), text: "Summarize this screen" },
    { icon: Icon.camera(14, theme.accent), text: "What's in this photo?" },
    { icon: Icon.bolt(14, theme.accent),    text: "Set a 10-minute timer" },
    { icon: Icon.globe(14, theme.accent),   text: "Look this up online" },
  ];
  return (
    <div style={{ padding: "40px 24px", display: "flex", flexDirection: "column", gap: 24 }}>
      {/* Ambient lens */}
      <div style={{ display: "flex", justifyContent: "center", margin: "12px 0 8px" }}>
        <div style={{
          width: 72, height: 72, borderRadius: "50%",
          background: `radial-gradient(circle at 35% 25%, ${theme.glassHighlight} 0%, transparent 55%), ${theme.accentSoft}`,
          border: `0.5px solid ${theme.glassBorder}`,
          display: "flex", alignItems: "center", justifyContent: "center",
          boxShadow: `0 0 40px ${theme.accent}33, 0 1.5px 0 ${theme.glassHighlight} inset`,
        }}>
          <HandMark size={32} color={theme.accent} />
        </div>
      </div>
      <div style={{ textAlign: "center" }}>
        <div style={{ fontSize: 22, fontWeight: 600, letterSpacing: -0.4, color: theme.textPrimary }}>
          Ready when you are
        </div>
        <div style={{ fontSize: 13, color: theme.textSecondary, marginTop: 6, lineHeight: 1.5 }}>
          Ask a question, point at a button, or tell Handy<br/>to do something for you.
        </div>
      </div>

      {/* Suggestions */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginTop: 8 }}>
        {suggestions.map((s, i) => (
          <div key={i} style={{
            padding: "12px 14px",
            borderRadius: 14,
            background: theme.chipBg,
            border: `0.5px solid ${theme.chipBorder}`,
            display: "flex", alignItems: "center", gap: 10,
            fontSize: 12.5, fontWeight: 500,
            color: theme.textPrimary,
          }}>
            <span style={{ display: "flex" }}>{s.icon}</span>
            {s.text}
          </div>
        ))}
      </div>
    </div>
  );
}

function Conversation({ theme }) {
  return (
    <div style={{ padding: "16px 16px 8px", display: "flex", flexDirection: "column", gap: 14, overflow: "auto", height: "100%" }}>
      <TimeStamp theme={theme}>Today 6:29 PM</TimeStamp>

      <UserBubble theme={theme}>What's the best shot from my weekend?</UserBubble>

      <AssistantBubble theme={theme}>
        <div style={{ marginBottom: 6 }}>
          Looking through 43 shots from Sat–Sun. Three stand out — the golden-hour portrait at Discovery Park leads on exposure and sharpness.
        </div>
        <div style={{
          display: "flex", gap: 6, marginTop: 10,
        }}>
          {["linear-gradient(135deg,#ffb48a,#ff7450)",
            "linear-gradient(135deg,#7ac6ff,#3a7dff)",
            "linear-gradient(135deg,#b18cff,#7a4dff)"].map((g, i) => (
            <div key={i} style={{
              flex: 1, height: 72, borderRadius: 10,
              background: g,
              border: `0.5px solid ${theme.chipBorder}`,
            }} />
          ))}
        </div>
      </AssistantBubble>

      <UserBubble theme={theme}>Tap the share button on the first one</UserBubble>

      <AssistantBubble theme={theme} status="acting">
        Opening the first shot and pointing at share…
      </AssistantBubble>
    </div>
  );
}

function TimeStamp({ theme, children }) {
  return (
    <div style={{ textAlign: "center", fontSize: 11, color: theme.textMuted, margin: "4px 0" }}>
      {children}
    </div>
  );
}

function UserBubble({ theme, children }) {
  return (
    <div style={{ display: "flex", justifyContent: "flex-end" }}>
      <div style={{
        maxWidth: "78%",
        padding: "10px 14px",
        borderRadius: 18,
        borderTopRightRadius: 6,
        background: theme.accentSoft,
        color: theme.textPrimary,
        fontSize: 14, lineHeight: 1.4,
        border: `0.5px solid ${theme.chipBorder}`,
      }}>{children}</div>
    </div>
  );
}

function AssistantBubble({ theme, children, status }) {
  return (
    <div style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
      <div style={{
        width: 24, height: 24, borderRadius: "50%",
        background: `radial-gradient(circle at 35% 25%, ${theme.glassHighlight}, transparent 55%), ${theme.accentSoft}`,
        border: `0.5px solid ${theme.glassBorder}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        flexShrink: 0, marginTop: 2,
      }}>
        <HandMark size={12} color={theme.accent} />
      </div>
      <div style={{ maxWidth: "82%" }}>
        <div style={{
          padding: "10px 14px",
          borderRadius: 18,
          borderTopLeftRadius: 6,
          background: theme.chipBg,
          border: `0.5px solid ${theme.chipBorder}`,
          color: theme.textPrimary,
          fontSize: 14, lineHeight: 1.45,
        }}>{children}</div>
        {status === "acting" && (
          <div style={{
            marginTop: 6, display: "inline-flex", alignItems: "center", gap: 6,
            padding: "4px 10px", borderRadius: 999,
            background: "rgba(127, 213, 166, 0.12)",
            border: "0.5px solid rgba(127, 213, 166, 0.3)",
            color: theme.success, fontSize: 11, fontWeight: 500,
          }}>
            <span style={{
              width: 6, height: 6, borderRadius: "50%",
              background: theme.success,
              animation: "handy-pulse-dot 1.2s ease-in-out infinite",
            }} />
            Working on it
          </div>
        )}
      </div>
    </div>
  );
}

Object.assign(window, { FullChatApp, EmptyState, Conversation, UserBubble, AssistantBubble });

function HeaderIconBtn({ theme, children, onClick }) {
  return (
    <button onClick={onClick} style={{
      width: 32, height: 32, borderRadius: 8,
      background: "transparent", border: "none", cursor: "pointer",
      display: "flex", alignItems: "center", justifyContent: "center",
      padding: 0, opacity: 0.72, transition: "opacity 150ms",
    }}
    onMouseEnter={e => e.currentTarget.style.opacity = "1"}
    onMouseLeave={e => e.currentTarget.style.opacity = "0.72"}
    >{children}</button>
  );
}
