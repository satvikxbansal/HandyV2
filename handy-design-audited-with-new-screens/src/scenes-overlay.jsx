// scenes-overlay.jsx — Floating widget states + glass quick-chat overlay.

// ────────────────────────────────────────────────────────────────────────
//  WIDGET STATES — six variants laid out as a row of demonstration cards.
// ────────────────────────────────────────────────────────────────────────

function WidgetStates() {
  const t = HANDY_TOKENS.amber;
  const states = [
    { key: "idle",      label: "Idle",      meta: "Tap or long-press" },
    { key: "listening", label: "Listening", meta: "Mic active · pulse ring" },
    { key: "thinking",  label: "Thinking",  meta: "Rotating accent arc" },
    { key: "flying",    label: "Flying",    meta: "Blue tinted disc · motion trail" },
    { key: "pointing",  label: "Pointing",  meta: "Blue tinted disc · bold pointer" },
    { key: "acting",    label: "Acting",    meta: "Bolt overlay · about to tap" },
  ];
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 780, height: 520,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 32,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
      }}>
        <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
          Floating widget states
        </div>
        <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6, maxWidth: 540 }}>
          48 dp accent disc. The glyph inside morphs to match the activity. Motion is cheap on top of static shapes so the host app's UI is never obscured.
        </div>
        <div style={{
          marginTop: 28, display: "grid",
          gridTemplateColumns: "repeat(3, 1fr)", gap: 18,
        }}>
          {states.map((s) => (
            <div key={s.key} style={{
              display: "flex", flexDirection: "column", alignItems: "center", gap: 14,
              padding: "30px 16px 18px",
              background: t.colors.surface,
              border: `1px solid ${t.colors.borderSubtle}`,
              borderRadius: 18, position: "relative",
              minHeight: 160,
            }}>
              <div style={{
                position: "relative", width: 80, height: 80,
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>
                <WidgetGlyph state={s.key} />
              </div>
              <div style={{
                font: `600 11px/1 ${HANDY_TYPE.fontBody}`,
                letterSpacing: "0.16em", textTransform: "uppercase",
                color: (s.key === "pointing" || s.key === "flying") ? t.colors.point : t.colors.textSecondary,
              }}>{s.label}</div>
              <div style={{ font: `400 11px/1.4 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted, textAlign: "center" }}>{s.meta}</div>
            </div>
          ))}
        </div>
      </div>
    </ThemeProvider>
  );
}

// The actual rendered widget glyph for a given state. Re-used both in
// WidgetStates (as a demo) and inside the overlay-on-host artboards.
function WidgetGlyph({ state = "idle", size = 48 }) {
  const t = useTheme();
  if (state === "listening") {
    // Granola-style: 5 vertical bars on the accent disc — no halo rings.
    // Bars animate height in a symmetric pattern so the silhouette
    // "breathes" left→right→center.
    return (
      <span style={{
        width: size, height: size, borderRadius: "50%",
        background: t.colors.accent,
        boxShadow: `0 6px 18px -4px ${t.colors.accent}55`,
        display: "flex", alignItems: "center", justifyContent: "center",
        gap: 3,
      }}>
        {[0.12, 0.28, 0.42, 0.28, 0.12].map((delay, i) => (
          <span key={i} style={{
            width: 3, borderRadius: 2,
            background: t.colors.accentInk,
            height: 18,                          // resting (mid) height
            transformOrigin: "50% 50%",
            animation: "handy-listening-bar 0.9s ease-in-out infinite",
            animationDelay: `${delay}s`,
          }} />
        ))}
      </span>
    );
  }
  if (state === "thinking") {
    return (
      <>
        <svg width={size + 12} height={size + 12} style={{ position: "absolute", animation: "handy-spin 1.6s linear infinite" }} viewBox="0 0 100 100">
          <circle cx="50" cy="50" r="46" fill="none"
            stroke={t.colors.accent} strokeWidth="3"
            strokeDasharray="120 320" strokeLinecap="round" />
        </svg>
        <HandMark variant="open" container="disc" size={size} />
      </>
    );
  }
  if (state === "flying") {
    // Same visual language as the Pointing state — blue tinted disc with
    // the bold pointer hand — but with a motion trail to convey that the
    // widget is in transit, not yet landed. The disc is what reads as
    // "Handy is about to point at something."
    return (
      <div style={{ position: "relative", width: size, height: size }}>
        {/* motion trail behind the disc, in the same blue */}
        <span style={{
          position: "absolute", left: -22, top: "50%", marginTop: -8,
          width: 36, height: 16,
          background: `radial-gradient(ellipse at right, ${t.colors.point}99, transparent 70%)`,
          borderRadius: 999,
          filter: "blur(0.5px)",
        }} />
        <span style={{
          width: size, height: size, borderRadius: "50%",
          background: t.colors.pointSoft,
          border: `1px solid ${t.colors.pointHair}`,
          boxShadow: `0 6px 18px -4px ${t.colors.point}66`,
          display: "flex", alignItems: "center", justifyContent: "center",
          position: "relative",
        }}>
          <Illu name="handPointBold" size={Math.round(size * 0.6)} color={t.colors.point} />
        </span>
      </div>
    );
  }
  if (state === "pointing") {
    // Pointing — the one state that doesn't use the amber accent. Blue
    // tinted disc with a bold pointer hand. Pulled out as its own
    // visual moment per the spec (chart-4 blue tokens).
    return (
      <span style={{
        width: size, height: size, borderRadius: "50%",
        background: t.colors.pointSoft,
        border: `1px solid ${t.colors.pointHair}`,
        display: "flex", alignItems: "center", justifyContent: "center",
      }}>
        <Illu name="handPointBold" size={Math.round(size * 0.6)} color={t.colors.point} />
      </span>
    );
  }
  if (state === "acting") {
    return (
      <div style={{ position: "relative" }}>
        <HandMark variant="tap" container="disc" size={size} />
        <span style={{
          position: "absolute", right: -4, bottom: -4,
          width: 18, height: 18, borderRadius: "50%",
          background: t.colors.surface,
          border: `1.5px solid ${t.colors.accent}`,
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          <Illu name="bolt" size={10} color={t.colors.accent} />
        </span>
      </div>
    );
  }
  return <HandMark variant="open" container="disc" size={size} />;
}

// ────────────────────────────────────────────────────────────────────────
//  OVERLAY QUICK CHAT — glass panel above a host app's UI.
//  Three host backdrops (home / maps / photos) so designers can verify
//  contrast against very different palettes.
// ────────────────────────────────────────────────────────────────────────

function OverlayQuickChat({ host = "home" }) {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone bg={hostBg(host)} statusInk={hostStatusInk(host)} hideNav>
        <HostBackdrop host={host} />

        {/* Floating widget pinned bottom-right above the panel */}
        <div style={{
          position: "absolute", right: 18, bottom: 320, zIndex: 30,
        }}>
          <WidgetGlyph state="idle" />
        </div>

        {/* Quick chat panel — taller, opaque enough to read on any host,
            with a thin amber halo so it always identifies as Handy's. */}
        <div style={{
          position: "absolute", left: 0, right: 0, bottom: 0,
          // Near-opaque dark glass — high alpha so this reads correctly on
          // bright host UIs where backdrop blur can't paint anything.
          background: "rgba(16,17,20,0.94)",
          backdropFilter: "blur(28px) saturate(160%)",
          WebkitBackdropFilter: "blur(28px) saturate(160%)",
          borderTopLeftRadius: 28, borderTopRightRadius: 28,
          // Amber halo outline — replaces the white hairline. Reads as
          // Handy's surface on bright AND dark backdrops.
          borderTop: `1px solid ${t.colors.accentHairline}`,
          boxShadow: `0 -1px 0 ${t.colors.accent}22 inset, 0 -20px 60px -28px ${t.colors.accent}55`,
          padding: "18px 20px 28px",
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
        }}>
          {/* drag handle */}
          <div style={{
            width: 42, height: 4, borderRadius: 4,
            background: "rgba(255,255,255,0.20)",
            margin: "0 auto 16px",
          }} />

          {/* top row */}
          <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <HandyWordmark size={18} markSize={22} />
            <div style={{ display: "flex", gap: 6 }}>
              <IconButton name="expand" size={18} color={t.colors.textSecondary} />
              <IconButton name="close"  size={18} color={t.colors.textSecondary} />
            </div>
          </div>

          {/* Inline app-context line — pulls the surface into the host's
              meaning the moment Handy opens. */}
          <div style={{
            marginTop: 14,
            font: `400 14px/1.45 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textSecondary,
          }}>
            In <span style={{ color: t.colors.accent, fontWeight: 600 }}>{hostAppName(host)}</span>. What can I help you with?
          </div>

          <div style={{ marginTop: 14 }}>
            <Composer glass placeholder={contextualPlaceholder(host)} />
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function hostAppName(host) {
  return host === "maps"   ? "Google Maps"
       : host === "photos" ? "Google Photos"
       : host === "home"   ? "Google Play Store"
       : "this app";
}

// ─── Host backdrops ─────────────────────────────────────────────────────
function hostBg(host) {
  return host === "home"   ? "#FFFFFF"        // Play Store: white host
       : host === "maps"   ? "#0E1410"
       : host === "photos" ? "#0A0A0C"
       : "#0A0A0C";
}
function hostStatusInk(host) {
  return host === "home" ? "dark" : "light";
}

function HostBackdrop({ host }) {
  if (host === "home") return <HomeHost />;
  if (host === "maps") return <MapsHost />;
  if (host === "photos") return <PhotosHost />;
  return null;
}

function HomeHost() {
  // Google Play Store sign-in screen — white host backdrop. Demonstrates
  // the overlay panel sitting on a bright surface where backdrop blur
  // contributes nothing; the panel must read on its own.
  return (
    <div style={{ position: "absolute", inset: 0, background: "#FFFFFF" }}>
      {/* Center: Play logo + title + body */}
      <div style={{
        position: "absolute", left: 0, right: 0, top: "30%",
        display: "flex", flexDirection: "column", alignItems: "center", gap: 18,
      }}>
        {/* Play triangle logo */}
        <svg width="76" height="84" viewBox="0 0 76 84" fill="none">
          <path d="M4 4 L72 42 L4 80 Z" fill="url(#playGradient)" />
          <defs>
            <linearGradient id="playGradient" x1="4" y1="4" x2="72" y2="80" gradientUnits="userSpaceOnUse">
              <stop offset="0" stopColor="#00C6FF" />
              <stop offset="0.45" stopColor="#FFCC00" />
              <stop offset="1" stopColor="#FF3D3D" />
            </linearGradient>
          </defs>
        </svg>
        <div style={{
          font: `400 28px/1 'Inter', system-ui`,
          color: "#5F6368", letterSpacing: "-0.005em",
        }}>Google Play</div>
        <div style={{
          marginTop: 12,
          font: `400 14px/1.45 'Inter', system-ui`,
          color: "#3C4043", textAlign: "center",
          padding: "0 32px",
        }}>
          Sign in to find the latest Android apps,<br />games, movies, music & more
        </div>
        <button style={{
          marginTop: 18,
          padding: "12px 36px",
          borderRadius: 999, border: "none",
          background: "#1A73E8", color: "#FFFFFF",
          font: `500 15px/1 'Inter', system-ui`,
          cursor: "pointer",
        }}>Sign in</button>
      </div>
    </div>
  );
}

function MapsHost() {
  return (
    <div style={{ position: "absolute", inset: 0, overflow: "hidden" }}>
      {/* map base */}
      <div style={{ position: "absolute", inset: 0, background: "#0e1410" }} />
      {/* roads */}
      <svg style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }} viewBox="0 0 412 800" preserveAspectRatio="none">
        <path d="M-10 200 L420 240" stroke="#2a3530" strokeWidth="32" />
        <path d="M-10 400 L420 380" stroke="#2a3530" strokeWidth="28" />
        <path d="M120 -10 L160 820" stroke="#2a3530" strokeWidth="40" />
        <path d="M300 -10 L320 820" stroke="#2a3530" strokeWidth="24" />
        {/* route */}
        <path d="M40 700 Q160 600 160 380 Q160 240 280 120" stroke="#D97757"
          strokeWidth="6" fill="none" strokeLinecap="round"
          strokeDasharray="10 6" />
        {/* pin */}
        <circle cx="280" cy="120" r="10" fill="#D97757" />
        <circle cx="280" cy="120" r="18" fill="none" stroke="#D97757" strokeOpacity="0.4" />
      </svg>
      {/* search bar */}
      <div style={{
        position: "absolute", top: 14, left: 16, right: 16,
        height: 48, borderRadius: 24,
        background: "rgba(20,22,22,0.85)", backdropFilter: "blur(12px)",
        display: "flex", alignItems: "center", gap: 10, padding: "0 16px",
        color: "#A8A39B", font: `400 14px 'Inter', system-ui`,
        border: "0.5px solid rgba(255,255,255,0.08)",
      }}>
        <Illu name="search" size={16} color="#A8A39B" />
        Search here
      </div>
    </div>
  );
}

function PhotosHost() {
  // A grid of photo thumbnails (placeholder swatches).
  return (
    <div style={{ position: "absolute", inset: 0, background: "#0a0a0c", padding: "60px 4px 0" }}>
      <div style={{
        display: "grid", gridTemplateColumns: "repeat(3, 1fr)",
        gap: 3, padding: "0 4px",
      }}>
        {[
          "linear-gradient(135deg,#8a6a4a,#3a2515)",
          "linear-gradient(135deg,#4a5a4a,#1a2520)",
          "linear-gradient(135deg,#aa8a6a,#5a3a2a)",
          "linear-gradient(135deg,#3a4a6a,#152535)",
          "linear-gradient(135deg,#9a7a5a,#3a2a1a)",
          "linear-gradient(135deg,#5a4a3a,#1a1510)",
          "linear-gradient(135deg,#bababa,#5a5a5a)",
          "linear-gradient(135deg,#7a5a4a,#3a2520)",
          "linear-gradient(135deg,#5a6a4a,#1a2515)",
          "linear-gradient(135deg,#9a6a4a,#3a2010)",
          "linear-gradient(135deg,#4a4a5a,#15151a)",
          "linear-gradient(135deg,#aa9a7a,#4a3a25)",
        ].map((bg, i) => (
          <div key={i} style={{ aspectRatio: "1 / 1.3", background: bg }} />
        ))}
      </div>
    </div>
  );
}

function contextualPlaceholder(host) {
  return host === "maps"   ? "What's near here?"
       : host === "photos" ? "What's in this photo?"
       : "Ask Handy anything…";
}
function contextualChips(host) {
  if (host === "maps")
    return [
      { illu: "ask",   label: "Where am I?" },
      { illu: "bolt",  label: "Start nav" },
      { illu: "globe", label: "What's nearby?" },
      { illu: "screen", label: "Read this turn" },
    ];
  if (host === "photos")
    return [
      { illu: "eye",   label: "Describe this photo" },
      { illu: "ask",   label: "What's the date?" },
      { illu: "bolt",  label: "Share with…" },
      { illu: "globe", label: "Look this up" },
    ];
  return [
    { illu: "ask",    label: "Show me around" },
    { illu: "screen", label: "What can I do here?" },
    { illu: "bolt",   label: "Quick action" },
  ];
}

window.WidgetStates = WidgetStates;
window.WidgetGlyph = WidgetGlyph;
window.OverlayQuickChat = OverlayQuickChat;
