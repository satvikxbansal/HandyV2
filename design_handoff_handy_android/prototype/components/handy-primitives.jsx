// Handy primitives — glass card, hand mark, icons, theme provider
// Two themes: "calm" (ivory warm) and "bold" (amber warm)

const HandyThemes = {
  calm: {
    name: "Calm",
    // glass sits on a near-black with a warm brown undertone
    pageBg: "#0A0A0B",
    // glass surface (will sit over a colorful backdrop)
    glassTint: "rgba(22, 20, 24, 0.55)",
    glassHighlight: "rgba(255, 255, 255, 0.14)",
    glassBorder: "rgba(255, 255, 255, 0.18)",
    glassInnerStroke: "rgba(255, 255, 255, 0.06)",
    textPrimary: "#F3EFE8",
    textSecondary: "rgba(243, 239, 232, 0.62)",
    textMuted: "rgba(243, 239, 232, 0.42)",
    accent: "#E8C38B", // warm ivory
    accentInk: "#1C140A",
    accentSoft: "rgba(232, 195, 139, 0.16)",
    divider: "rgba(255, 255, 255, 0.08)",
    chipBg: "rgba(255, 255, 255, 0.07)",
    chipBorder: "rgba(255, 255, 255, 0.1)",
    success: "#7FD5A6",
    danger: "#E9937C",
    listening: "#E8C38B",
  },
  bold: {
    name: "Bold",
    pageBg: "#07070A",
    glassTint: "rgba(12, 10, 14, 0.58)",
    glassHighlight: "rgba(255, 220, 180, 0.22)",
    glassBorder: "rgba(255, 210, 170, 0.22)",
    glassInnerStroke: "rgba(255, 180, 120, 0.1)",
    textPrimary: "#FFF7EC",
    textSecondary: "rgba(255, 247, 236, 0.62)",
    textMuted: "rgba(255, 247, 236, 0.42)",
    accent: "#F0A868", // warm amber
    accentInk: "#2A1608",
    accentSoft: "rgba(240, 168, 104, 0.18)",
    divider: "rgba(255, 220, 180, 0.09)",
    chipBg: "rgba(240, 168, 104, 0.09)",
    chipBorder: "rgba(240, 168, 104, 0.2)",
    success: "#6FE0B3",
    danger: "#FF9A80",
    listening: "#F0A868",
  },
};

// Shared font stack
const HANDY_FONT = "'Inter', 'Inter Placeholder', -apple-system, BlinkMacSystemFont, sans-serif";

// ─────────────────────────────────────────────
// GlassCard — multi-layer blur, inner stroke, specular highlight
// Renders the iOS-liquid-glass look for overlays.
// ─────────────────────────────────────────────
function GlassCard({ theme, radius = 28, style, children, intensity = 70, sheen = true }) {
  const blur = 14 + (intensity / 100) * 20; // 14–34px
  const sat = 1.4 + (intensity / 100) * 0.4; // 1.4–1.8
  return (
    <div style={{
      position: "relative",
      borderRadius: radius,
      background: theme.glassTint,
      backdropFilter: `blur(${blur}px) saturate(${sat})`,
      WebkitBackdropFilter: `blur(${blur}px) saturate(${sat})`,
      border: `0.5px solid ${theme.glassBorder}`,
      boxShadow: `
        0 1px 0 0 ${theme.glassHighlight} inset,
        0 0 0 0.5px ${theme.glassInnerStroke} inset,
        0 24px 60px -20px rgba(0, 0, 0, 0.55),
        0 6px 16px -6px rgba(0, 0, 0, 0.4)
      `,
      overflow: "hidden",
      ...style,
    }}>
      {sheen && (
        <div style={{
          position: "absolute",
          inset: 0,
          borderRadius: "inherit",
          background: `radial-gradient(120% 60% at 30% 0%, ${theme.glassHighlight} 0%, transparent 45%)`,
          pointerEvents: "none",
          opacity: 0.6,
        }} />
      )}
      <div style={{ position: "relative", zIndex: 1 }}>{children}</div>
    </div>
  );
}

// ─────────────────────────────────────────────
// HandMark — the Handy logo. Simple raised palm with an accent dot.
// ─────────────────────────────────────────────
function HandMark({ size = 20, color = "currentColor", filled = false }) {
  // Minimal palm glyph — not a skin-tone emoji. Outlined strokes.
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none"
         style={{ display: "block" }}>
      <path
        d="M8.5 11V5.75a1.25 1.25 0 0 1 2.5 0V11M11 11V4.25a1.25 1.25 0 0 1 2.5 0V11M13.5 11V5a1.25 1.25 0 0 1 2.5 0v7M16 8.75a1.25 1.25 0 0 1 2.5 0V14c0 3.5-2.5 6.25-6.25 6.25S6 17.5 6 14v-1.75a1.25 1.25 0 0 1 2.5 0"
        stroke={color}
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
        fill={filled ? color : "none"}
        fillOpacity={filled ? 0.15 : 0}
      />
    </svg>
  );
}

// ─────────────────────────────────────────────
// Icons — minimal, one stroke weight, square viewbox
// ─────────────────────────────────────────────
const Icon = {
  mic: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="9" y="3" width="6" height="12" rx="3" stroke={color} strokeWidth="1.6" />
      <path d="M6 11.5A6 6 0 0 0 18 11.5M12 17.5V21M9 21h6" stroke={color} strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  ),
  send: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M4 12 20 4l-3 16-4-7-9-1Z" stroke={color} strokeWidth="1.6" strokeLinejoin="round" fill={color} fillOpacity="0.0" />
      <path d="M13 13 20 4" stroke={color} strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  ),
  close: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M6 6l12 12M18 6 6 18" stroke={color} strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  ),
  expand: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M4 10V4h6M20 14v6h-6M4 4l7 7M20 20l-7-7" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  collapse: (size = 16, color = "currentColor") => (
    // Mirror of `expand`: arrows point INWARD toward the centre.
    // Corner chevrons live at the 4 corners; diagonals point to middle.
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M15 4v5h5M9 20v-5H4M4 4l6 6M20 20l-6-6"
            stroke={color} strokeWidth="1.6"
            strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  settings: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="2.5" stroke={color} strokeWidth="1.6" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z" stroke={color} strokeWidth="1.4" />
    </svg>
  ),
  sparkle: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M12 3v5M12 16v5M3 12h5M16 12h5M6 6l3 3M15 15l3 3M18 6l-3 3M9 15l-3 3" stroke={color} strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  ),
  plus: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M12 5v14M5 12h14" stroke={color} strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  ),
  chevronRight: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="m9 6 6 6-6 6" stroke={color} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  check: (size = 14, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="m5 12 5 5L20 6" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
  eye: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" stroke={color} strokeWidth="1.5" />
      <circle cx="12" cy="12" r="3" stroke={color} strokeWidth="1.5" />
    </svg>
  ),
  copy: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="8" y="4" width="12" height="14" rx="2.5" stroke={color} strokeWidth="1.5" />
      <path d="M16 20H6a2 2 0 0 1-2-2V8" stroke={color} strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  ),
  brain: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M9 4.5A2.5 2.5 0 0 1 12 4.5 2.5 2.5 0 0 1 15 4.5c1.7 0 3 1.3 3 3 0 .5-.1 1-.3 1.4 1.3.5 2.3 1.8 2.3 3.3 0 1.3-.7 2.4-1.8 3 .5.5.8 1.2.8 2 0 1.7-1.3 3-3 3-.5 0-1-.1-1.4-.3-.3 1-1.2 1.8-2.3 2a2.5 2.5 0 0 1-2.6 0c-1.1-.2-2-1-2.3-2-.4.2-.9.3-1.4.3-1.7 0-3-1.3-3-3 0-.8.3-1.5.8-2-1.1-.6-1.8-1.7-1.8-3 0-1.5 1-2.8 2.3-3.3A3 3 0 0 1 6 7.5c0-1.7 1.3-3 3-3Z" stroke={color} strokeWidth="1.4" strokeLinejoin="round" />
      <path d="M12 4v16M9 9a2 2 0 0 0 3 1M15 9a2 2 0 0 1-3 1M9 15a2 2 0 0 1 3-1M15 15a2 2 0 0 0-3-1" stroke={color} strokeWidth="1.2" strokeLinecap="round" />
    </svg>
  ),
  modes: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="3" y="5" width="18" height="6" rx="2" stroke={color} strokeWidth="1.5" />
      <rect x="3" y="13" width="18" height="6" rx="2" stroke={color} strokeWidth="1.5" />
      <circle cx="7" cy="8" r="1.3" fill={color} />
      <circle cx="17" cy="16" r="1.3" fill={color} />
    </svg>
  ),
  bolt: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="m13 3-8 11h6l-1 7 8-11h-6l1-7Z" stroke={color} strokeWidth="1.5" strokeLinejoin="round" />
    </svg>
  ),
  key: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="8" cy="15" r="4" stroke={color} strokeWidth="1.5" />
      <path d="m10.8 12.2 9-9M16 8l3 3M13.5 10.5l3 3" stroke={color} strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  ),
  globe: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="9" stroke={color} strokeWidth="1.5" />
      <path d="M3 12h18M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z" stroke={color} strokeWidth="1.5" />
    </svg>
  ),
  waveform: (size = 18, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M4 12v0M8 8v8M12 5v14M16 9v6M20 12v0" stroke={color} strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  ),
  camera: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <rect x="3" y="6" width="18" height="14" rx="3" stroke={color} strokeWidth="1.5" />
      <circle cx="12" cy="13" r="3.5" stroke={color} strokeWidth="1.5" />
      <path d="M9 6l1.5-2h3L15 6" stroke={color} strokeWidth="1.5" />
    </svg>
  ),
  history: (size = 16, color = "currentColor") => (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d="M4 12a8 8 0 1 0 2.5-5.8L4 9M4 4v5h5M12 8v4l3 2" stroke={color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  ),
};

Object.assign(window, { HandyThemes, HANDY_FONT, GlassCard, HandMark, Icon });
