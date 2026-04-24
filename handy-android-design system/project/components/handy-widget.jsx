// Floating widget — the draggable Handy buddy.
// 4 states: idle · hover · listening · thinking
// Sits over a backdrop so glass reads.

// ─────────────────────────────────────────────
// FloatingWidget — the lens itself. Around 60px.
// state: "idle" | "hover" | "listening" | "thinking"
// ─────────────────────────────────────────────
function FloatingWidget({ theme, state = "idle", label, size = 60 }) {
  const isIdle = state === "idle";
  const isHover = state === "hover";
  const isListening = state === "listening";
  const isThinking = state === "thinking";

  // outer halo ring color by state
  const haloColor = isListening ? theme.listening
                  : isThinking ? theme.accent
                  : "rgba(255,255,255,0.0)";

  return (
    <div style={{ position: "relative", width: size + 40, height: size + 40,
                  display: "flex", alignItems: "center", justifyContent: "center" }}>
      {/* Thinking: rotating rim */}
      {isThinking && (
        <svg style={{ position: "absolute", inset: 0, animation: "handy-spin 1.8s linear infinite" }}
             viewBox="0 0 100 100">
          <defs>
            <linearGradient id={`rim-${theme.name}`} x1="0" y1="0" x2="1" y2="1">
              <stop offset="0" stopColor={theme.accent} stopOpacity="0" />
              <stop offset="0.4" stopColor={theme.accent} stopOpacity="0.8" />
              <stop offset="1" stopColor={theme.accent} stopOpacity="0" />
            </linearGradient>
          </defs>
          <circle cx="50" cy="50" r="46" fill="none"
                  stroke={`url(#rim-${theme.name})`} strokeWidth="2" strokeLinecap="round" />
        </svg>
      )}

      {/* Listening: pulsing halos */}
      {isListening && (
        <>
          <div style={{
            position: "absolute", inset: 0, borderRadius: "50%",
            background: `radial-gradient(circle, ${theme.listening}44 0%, transparent 60%)`,
            animation: "handy-pulse 1.6s ease-out infinite",
          }} />
          <div style={{
            position: "absolute", inset: 10, borderRadius: "50%",
            background: `radial-gradient(circle, ${theme.listening}66 0%, transparent 60%)`,
            animation: "handy-pulse 1.6s ease-out infinite 0.4s",
          }} />
        </>
      )}

      {/* Hover: soft glow */}
      {isHover && (
        <div style={{
          position: "absolute", inset: 0, borderRadius: "50%",
          background: `radial-gradient(circle, ${theme.accent}33 0%, transparent 65%)`,
        }} />
      )}

      {/* The glass lens */}
      <div style={{
        position: "relative",
        width: size, height: size,
        borderRadius: "50%",
        background: theme.glassTint,
        backdropFilter: "blur(22px) saturate(1.6)",
        WebkitBackdropFilter: "blur(22px) saturate(1.6)",
        border: `1.5px solid ${isIdle ? theme.accent + "99" : theme.glassBorder}`,
        boxShadow: `
          0 1.5px 0 0 ${theme.glassHighlight} inset,
          0 0 0 1px ${isIdle ? theme.accentSoft : theme.glassInnerStroke} inset,
          0 8px 24px -6px rgba(0,0,0,0.55),
          0 2px 6px rgba(0,0,0,0.35),
          ${isHover || isThinking ? `0 0 24px ${theme.accent}55`
            : isIdle ? `0 0 18px ${theme.accent}30`
            : "0 0 0 transparent"}
        `,
        transition: "transform 180ms ease, box-shadow 200ms ease",
        transform: isHover ? "scale(1.06)" : "scale(1)",
        display: "flex", alignItems: "center", justifyContent: "center",
        overflow: "hidden",
      }}>
        {/* specular sheen */}
        <div style={{
          position: "absolute", inset: 0, borderRadius: "50%",
          background: `radial-gradient(120% 60% at 35% 15%, ${theme.glassHighlight} 0%, transparent 50%)`,
          pointerEvents: "none",
        }} />

        {/* Content per state */}
        {isListening ? (
          <Waveform theme={theme} />
        ) : (
          <HandMark size={size * 0.44} color={theme.textPrimary} />
        )}

        {/* small accent dot in thinking */}
        {isThinking && (
          <div style={{
            position: "absolute", bottom: 8, right: 8,
            width: 8, height: 8, borderRadius: "50%",
            background: theme.accent,
            boxShadow: `0 0 10px ${theme.accent}`,
          }} />
        )}
      </div>

      {/* Optional flying label bubble (used on hover/listening) */}
      {label && (
        <div style={{
          position: "absolute", top: -8, left: "100%", marginLeft: 8,
          whiteSpace: "nowrap",
          padding: "6px 12px",
          borderRadius: 999,
          background: theme.glassTint,
          backdropFilter: "blur(18px) saturate(1.5)",
          border: `0.5px solid ${theme.glassBorder}`,
          color: theme.textPrimary,
          fontFamily: HANDY_FONT, fontSize: 12, fontWeight: 500,
          boxShadow: "0 6px 16px rgba(0,0,0,0.3)",
        }}>{label}</div>
      )}
    </div>
  );
}

// Tiny animated waveform for the listening state
function Waveform({ theme }) {
  const bars = [0, 1, 2, 3, 4];
  return (
    <div style={{ display: "flex", gap: 3, alignItems: "center", height: 20 }}>
      {bars.map(i => (
        <div key={i} style={{
          width: 3, borderRadius: 2,
          background: theme.listening,
          height: 6,
          animation: `handy-wave 0.9s ease-in-out ${i * 0.1}s infinite alternate`,
        }} />
      ))}
    </div>
  );
}

// Inject animation keyframes once
function WidgetAnimations() {
  return (
    <style>{`
      @keyframes handy-spin { to { transform: rotate(360deg); } }
      @keyframes handy-pulse {
        0%   { transform: scale(0.9); opacity: 0.9; }
        100% { transform: scale(1.4); opacity: 0; }
      }
      @keyframes handy-wave {
        0%   { height: 4px; }
        100% { height: 18px; }
      }
      @keyframes handy-think-dot {
        0%, 100% { opacity: 0.3; }
        50%      { opacity: 1; }
      }
    `}</style>
  );
}

Object.assign(window, { FloatingWidget, Waveform, WidgetAnimations });
