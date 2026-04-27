// Permissions screen — first-run onboarding
// Walks user through Mic · Notifications · Overlay · Accessibility
// with clear explainers and progress.

function PermissionsScreen({ theme }) {
  const perms = [
    { id: "mic",   title: "Microphone",            detail: "For voice capture when you long-press",  status: "granted" },
    { id: "notif", title: "Notifications",         detail: "So Handy can tell you when it's ready",  status: "granted" },
    { id: "draw",  title: "Draw over other apps",  detail: "The floating widget lives above anything", status: "granted" },
    { id: "a11y",  title: "Accessibility",         detail: "Read the active screen to help in context", status: "action" },
  ];

  return (
    <div style={{
      position: "absolute", inset: 0,
      background: theme.pageBg,
      fontFamily: HANDY_FONT,
      color: theme.textPrimary,
      overflow: "auto",
      // ambient warmth
      backgroundImage: `radial-gradient(70% 40% at 50% -5%, ${theme.accent}18, transparent 60%)`,
    }}>
      {/* top spacing for status bar */}
      <div style={{ height: 52 }} />

      {/* Lens mark */}
      <div style={{ display: "flex", justifyContent: "center", marginTop: 8, marginBottom: 20 }}>
        <div style={{
          width: 72, height: 72, borderRadius: "50%",
          background: `radial-gradient(circle at 35% 25%, ${theme.glassHighlight} 0%, transparent 55%), ${theme.accentSoft}`,
          border: `1.5px solid ${theme.accent}88`,
          display: "flex", alignItems: "center", justifyContent: "center",
          boxShadow: `0 0 40px ${theme.accent}44, 0 1.5px 0 ${theme.glassHighlight} inset`,
        }}>
          <HandMark size={32} color={theme.accent} />
        </div>
      </div>

      <div style={{ padding: "0 28px" }}>
        <div style={{ fontSize: 26, fontWeight: 700, letterSpacing: -0.6, textAlign: "center", lineHeight: 1.15 }}>
          A few permissions,<br/>and you're set.
        </div>
        <div style={{ fontSize: 13.5, color: theme.textSecondary, textAlign: "center",
                      marginTop: 10, lineHeight: 1.55, maxWidth: 300, marginInline: "auto" }}>
          Handy reads your screen to help — nothing is shared with our servers.
          You always have the final say.
        </div>
      </div>

      {/* Permission rows */}
      <div style={{ padding: "28px 16px 8px", display: "flex", flexDirection: "column", gap: 10 }}>
        {perms.map(p => <PermRow key={p.id} theme={theme} {...p} />)}
      </div>

      {/* Privacy callout */}
      <div style={{
        margin: "16px 16px 0",
        padding: "12px 14px",
        borderRadius: 14,
        background: "rgba(111, 224, 179, 0.08)",
        border: "0.5px solid rgba(111, 224, 179, 0.22)",
        display: "flex", gap: 10,
        fontSize: 12, color: theme.textSecondary, lineHeight: 1.5,
      }}>
        <div style={{ flexShrink: 0, marginTop: 1, color: theme.success }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M12 3 5 6v5c0 4 3 8 7 10 4-2 7-6 7-10V6l-7-3Z" stroke="currentColor" strokeWidth="1.5" strokeLinejoin="round" />
            <path d="m9 12 2 2 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>
        <div>
          <span style={{ color: theme.textPrimary, fontWeight: 600 }}>Your data stays yours.</span>{" "}
          Handy talks directly to Anthropic using <em>your</em> API key. No servers of ours in the middle.
        </div>
      </div>

      {/* CTA */}
      <div style={{ padding: "20px 16px 24px" }}>
        <button style={{
          width: "100%", height: 52,
          borderRadius: 16,
          background: theme.accent, color: theme.accentInk,
          border: "none",
          fontFamily: HANDY_FONT, fontSize: 15, fontWeight: 600,
          cursor: "pointer",
          boxShadow: `0 10px 24px -8px ${theme.accent}88`,
          display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
        }}>
          Open Handy
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <div style={{
          textAlign: "center", marginTop: 14,
          fontSize: 11.5, color: theme.textMuted, lineHeight: 1.5,
        }}>
          Prefer reduced mode? You can skip Accessibility and still chat.
        </div>
      </div>
    </div>
  );
}

function PermRow({ theme, title, detail, status }) {
  const granted = status === "granted";
  return (
    <div style={{
      padding: "14px 16px",
      borderRadius: 16,
      background: theme.chipBg,
      border: `0.5px solid ${granted ? "rgba(111, 224, 179, 0.3)" : theme.chipBorder}`,
      display: "flex", alignItems: "center", gap: 12,
    }}>
      {/* leading icon */}
      <div style={{
        width: 36, height: 36, borderRadius: 10,
        background: granted ? "rgba(111, 224, 179, 0.12)" : theme.accentSoft,
        color: granted ? theme.success : theme.accent,
        display: "flex", alignItems: "center", justifyContent: "center",
        flexShrink: 0,
      }}>
        {granted ? (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="m5 12 5 5L20 6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : (
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="4.5" fill="currentColor" />
          </svg>
        )}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: theme.textPrimary }}>{title}</div>
        <div style={{ fontSize: 12, color: theme.textSecondary, marginTop: 1, lineHeight: 1.4 }}>{detail}</div>
      </div>
      {granted ? (
        <div style={{
          padding: "0 10px", height: 28, borderRadius: 999,
          background: "rgba(111, 224, 179, 0.14)",
          color: theme.success, fontSize: 11, fontWeight: 600,
          display: "flex", alignItems: "center", gap: 4,
        }}>
          Granted
        </div>
      ) : (
        <button style={{
          padding: "0 14px", height: 32, borderRadius: 10,
          background: theme.accent, color: theme.accentInk,
          border: "none", cursor: "pointer",
          fontFamily: HANDY_FONT, fontSize: 12, fontWeight: 600,
          boxShadow: `0 4px 10px -3px ${theme.accent}88`,
        }}>
          Enable
        </button>
      )}
    </div>
  );
}

Object.assign(window, { PermissionsScreen });
