// Backdrops — realistic phone content that sits UNDER the overlays
// so the glass blur has something to blur.

// ─────────────────────────────────────────────
// Pixel-style home screen with wallpaper + app grid
// ─────────────────────────────────────────────
function HomeScreenBackdrop() {
  const apps = [
    { name: "Messages", color: "#2E7CF6", glyph: "💬" },
    { name: "Phone",    color: "#34C759", glyph: "📞" },
    { name: "Chrome",   color: "#fff",    glyph: "C" },
    { name: "Gmail",    color: "#EA4335", glyph: "M" },
    { name: "Photos",   color: "linear-gradient(135deg,#FBBC05,#34A853,#4285F4,#EA4335)", glyph: "" },
    { name: "Maps",     color: "#4285F4", glyph: "📍" },
    { name: "Calendar", color: "#fff",    glyph: "24" },
    { name: "Camera",   color: "#1d1d1f", glyph: "📷" },
    { name: "YouTube",  color: "#FF0033", glyph: "▶" },
    { name: "Spotify",  color: "#1DB954", glyph: "♪" },
    { name: "Notes",    color: "#FFCC02", glyph: "📝" },
    { name: "Settings", color: "#8E8E93", glyph: "⚙" },
  ];

  return (
    <div style={{
      position: "absolute", inset: 0,
      // rich wallpaper — Pixel-style nebula
      background: `
        radial-gradient(80% 50% at 20% 15%, rgba(120, 60, 200, 0.55) 0%, transparent 60%),
        radial-gradient(70% 40% at 80% 30%, rgba(210, 90, 140, 0.45) 0%, transparent 55%),
        radial-gradient(90% 60% at 50% 90%, rgba(50, 100, 220, 0.5) 0%, transparent 60%),
        linear-gradient(180deg, #1a0f2e 0%, #0d0820 100%)
      `,
    }}>
      {/* Search pill */}
      <div style={{
        margin: "24px 16px 20px",
        height: 48,
        borderRadius: 999,
        background: "rgba(255,255,255,0.12)",
        border: "0.5px solid rgba(255,255,255,0.18)",
        display: "flex",
        alignItems: "center",
        padding: "0 18px",
        gap: 12,
        color: "rgba(255,255,255,0.85)",
        fontFamily: HANDY_FONT,
        fontSize: 14,
        backdropFilter: "blur(12px)",
      }}>
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
          <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth="1.8" />
          <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
        </svg>
        <span style={{ flex: 1 }}>Search apps, web & more</span>
        <div style={{ width: 22, height: 22, borderRadius: "50%", background: "rgba(255,255,255,0.2)" }} />
      </div>

      {/* App grid */}
      <div style={{
        padding: "12px 20px",
        display: "grid",
        gridTemplateColumns: "repeat(4, 1fr)",
        gap: 24,
      }}>
        {apps.map((a, i) => (
          <div key={i} style={{
            display: "flex", flexDirection: "column", alignItems: "center", gap: 6,
            fontFamily: HANDY_FONT, fontSize: 11, color: "#fff",
            textShadow: "0 1px 2px rgba(0,0,0,0.3)",
          }}>
            <div style={{
              width: 54, height: 54, borderRadius: 14,
              background: a.color,
              display: "flex", alignItems: "center", justifyContent: "center",
              fontSize: 22, color: "#fff", fontWeight: 700,
              boxShadow: "0 4px 12px rgba(0,0,0,0.25)",
            }}>{a.glyph}</div>
            <span>{a.name}</span>
          </div>
        ))}
      </div>

      {/* At a glance widget */}
      <div style={{
        margin: "32px 16px 0",
        padding: "14px 18px",
        borderRadius: 20,
        background: "rgba(255,255,255,0.08)",
        border: "0.5px solid rgba(255,255,255,0.14)",
        color: "#fff",
        fontFamily: HANDY_FONT,
        backdropFilter: "blur(20px)",
      }}>
        <div style={{ fontSize: 26, fontWeight: 300, letterSpacing: -0.5 }}>Fri · Apr 24</div>
        <div style={{ fontSize: 13, opacity: 0.75, marginTop: 2 }}>72° in Seattle · Mostly clear</div>
      </div>

      {/* Dock */}
      <div style={{
        position: "absolute", bottom: 20, left: 16, right: 16,
        height: 72, borderRadius: 28,
        background: "rgba(255,255,255,0.1)",
        border: "0.5px solid rgba(255,255,255,0.16)",
        backdropFilter: "blur(24px)",
        display: "flex", justifyContent: "space-around", alignItems: "center",
      }}>
        {[
          { g: "💬", c: "#2E7CF6" },
          { g: "📞", c: "#34C759" },
          { g: "C",  c: "#fff", t: "#222" },
          { g: "M",  c: "#EA4335" },
        ].map((d, i) => (
          <div key={i} style={{
            width: 48, height: 48, borderRadius: 12,
            background: d.c,
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 20, color: d.t || "#fff", fontWeight: 700,
          }}>{d.g}</div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// Photos app backdrop (colorful grid so glass blur is vivid)
// ─────────────────────────────────────────────
function PhotosAppBackdrop() {
  const tiles = [
    "linear-gradient(135deg,#ff7b7b 0%,#ff5e3a 100%)",
    "linear-gradient(135deg,#7ac6ff 0%,#3a7dff 100%)",
    "linear-gradient(135deg,#ffd07a 0%,#ffa23a 100%)",
    "linear-gradient(135deg,#b18cff 0%,#7a4dff 100%)",
    "linear-gradient(135deg,#86e6b4 0%,#3abf80 100%)",
    "linear-gradient(135deg,#ff98d0 0%,#ff5ea3 100%)",
    "linear-gradient(135deg,#9ad3ff 0%,#4aa0ff 100%)",
    "linear-gradient(135deg,#ffe69a 0%,#ffc83a 100%)",
    "linear-gradient(135deg,#a9f0d1 0%,#4ad7a1 100%)",
    "linear-gradient(135deg,#c8a2ff 0%,#8a5eff 100%)",
    "linear-gradient(135deg,#ff9b7a 0%,#ff5e3a 100%)",
    "linear-gradient(135deg,#7ae0ff 0%,#3abfff 100%)",
    "linear-gradient(135deg,#ffd07a 0%,#ff8a3a 100%)",
    "linear-gradient(135deg,#ffa0c8 0%,#ff5e8a 100%)",
    "linear-gradient(135deg,#b5ffa0 0%,#6ad97a 100%)",
  ];
  return (
    <div style={{ position: "absolute", inset: 0, background: "#0d0d12" }}>
      {/* App header */}
      <div style={{
        padding: "14px 20px 10px",
        display: "flex", alignItems: "center", justifyContent: "space-between",
        color: "#fff", fontFamily: HANDY_FONT,
      }}>
        <div style={{ fontSize: 22, fontWeight: 600, letterSpacing: -0.3 }}>Photos</div>
        <div style={{ display: "flex", gap: 12, opacity: 0.7 }}>
          <div style={{ width: 28, height: 28, borderRadius: "50%", background: "rgba(255,255,255,0.1)" }} />
          <div style={{ width: 28, height: 28, borderRadius: "50%", background: "rgba(255,255,255,0.1)" }} />
        </div>
      </div>
      <div style={{ padding: "0 20px 8px", color: "rgba(255,255,255,0.5)", fontFamily: HANDY_FONT, fontSize: 12 }}>
        TODAY · SEATTLE
      </div>
      {/* Photo grid */}
      <div style={{
        padding: "4px 2px",
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: 2,
      }}>
        {tiles.map((t, i) => (
          <div key={i} style={{ paddingBottom: "100%", position: "relative" }}>
            <div style={{ position: "absolute", inset: 0, background: t }} />
          </div>
        ))}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// Map app backdrop
// ─────────────────────────────────────────────
function MapAppBackdrop() {
  return (
    <div style={{
      position: "absolute", inset: 0,
      background: `
        radial-gradient(60% 40% at 30% 20%, #2a5f5a 0%, transparent 70%),
        radial-gradient(50% 40% at 80% 60%, #3a4a8c 0%, transparent 70%),
        linear-gradient(180deg, #0f1820 0%, #0a1015 100%)
      `,
    }}>
      {/* roads */}
      <svg width="100%" height="100%" style={{ position: "absolute", inset: 0 }}>
        <g stroke="rgba(180, 200, 230, 0.35)" fill="none" strokeLinecap="round">
          <path d="M-20 200 Q 150 250 400 180 T 800 220" strokeWidth="3" />
          <path d="M-20 400 Q 200 380 400 430 T 800 410" strokeWidth="4" />
          <path d="M50 -20 Q 120 200 90 400 T 130 800" strokeWidth="3" />
          <path d="M260 -20 Q 280 200 260 420 T 290 800" strokeWidth="3" />
          <path d="M-20 600 L 800 620" strokeWidth="2" strokeDasharray="4 6" />
        </g>
        {/* blocks */}
        <g fill="rgba(255,255,255,0.03)">
          <rect x="110" y="230" width="130" height="140" rx="6" />
          <rect x="280" y="230" width="110" height="140" rx="6" />
          <rect x="110" y="440" width="160" height="130" rx="6" />
          <rect x="300" y="440" width="100" height="130" rx="6" />
        </g>
        {/* pin */}
        <g>
          <circle cx="220" cy="340" r="24" fill="rgba(240, 168, 104, 0.2)" />
          <circle cx="220" cy="340" r="10" fill="#F0A868" />
          <circle cx="220" cy="340" r="4" fill="#fff" />
        </g>
      </svg>
      {/* nav header */}
      <div style={{
        margin: "14px 16px", padding: "12px 16px",
        borderRadius: 18,
        background: "rgba(20, 22, 28, 0.7)",
        border: "0.5px solid rgba(255,255,255,0.08)",
        backdropFilter: "blur(16px)",
        color: "#fff", fontFamily: HANDY_FONT,
        display: "flex", alignItems: "center", gap: 12,
      }}>
        <div style={{ width: 32, height: 32, borderRadius: "50%", background: "rgba(255,255,255,0.1)" }} />
        <div style={{ flex: 1, fontSize: 14, color: "rgba(255,255,255,0.7)" }}>Search here</div>
        <div style={{ width: 26, height: 26, borderRadius: "50%", background: "rgba(255,255,255,0.1)" }} />
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────
// Light app backdrop — clean white surface so we can test the
// glass overlay against a bright context (docs, reader, etc.)
// ─────────────────────────────────────────────
function LightDocAppBackdrop() {
  return (
    <div style={{
      position: "absolute", inset: 0,
      background: "#FAFAF7",
      color: "#1A1A1F",
      fontFamily: HANDY_FONT,
    }}>
      {/* App chrome */}
      <div style={{
        padding: "14px 18px",
        display: "flex", alignItems: "center", justifyContent: "space-between",
        borderBottom: "0.5px solid rgba(0,0,0,0.08)",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{
            width: 26, height: 26, borderRadius: 7,
            background: "linear-gradient(135deg,#4285F4,#34A853)",
            color: "#fff", fontWeight: 700, fontSize: 13,
            display: "flex", alignItems: "center", justifyContent: "center",
          }}>D</div>
          <div style={{ fontSize: 14, fontWeight: 600 }}>Docs</div>
        </div>
        <div style={{ display: "flex", gap: 10, opacity: 0.55 }}>
          <div style={{ width: 24, height: 24, borderRadius: "50%", background: "rgba(0,0,0,0.08)" }} />
          <div style={{ width: 24, height: 24, borderRadius: "50%", background: "rgba(0,0,0,0.08)" }} />
        </div>
      </div>

      {/* Document sheet */}
      <div style={{
        margin: "18px 16px 0",
        padding: "28px 28px 24px",
        background: "#fff",
        borderRadius: 14,
        boxShadow: "0 2px 10px rgba(0,0,0,0.04), 0 0 0 0.5px rgba(0,0,0,0.05)",
      }}>
        <div style={{
          fontSize: 11, letterSpacing: 1.5, fontWeight: 600,
          color: "#4285F4", textTransform: "uppercase",
        }}>Proposal · Draft</div>
        <div style={{ fontSize: 22, fontWeight: 700, letterSpacing: -0.5, marginTop: 6, lineHeight: 1.2 }}>
          Q3 Research Plan
        </div>
        <div style={{ fontSize: 12, color: "#6A6A72", marginTop: 4 }}>
          Last edited · 12 min ago
        </div>

        {/* Body lines */}
        <div style={{ marginTop: 22, display: "flex", flexDirection: "column", gap: 9 }}>
          <Line w="100%" />
          <Line w="94%" />
          <Line w="88%" />
          <Line w="70%" />
        </div>

        <div style={{ marginTop: 24, fontSize: 14, fontWeight: 600, color: "#1A1A1F" }}>
          Methodology
        </div>
        <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 9 }}>
          <Line w="100%" />
          <Line w="96%" />
          <Line w="82%" />
          <Line w="100%" />
          <Line w="58%" />
        </div>

        <div style={{ marginTop: 24, fontSize: 14, fontWeight: 600, color: "#1A1A1F" }}>
          Timeline
        </div>
        <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 9 }}>
          <Line w="92%" />
          <Line w="78%" />
        </div>
      </div>
    </div>
  );
}

function Line({ w }) {
  return (
    <div style={{
      height: 8, width: w,
      borderRadius: 4,
      background: "rgba(0,0,0,0.08)",
    }} />
  );
}

Object.assign(window, { HomeScreenBackdrop, PhotosAppBackdrop, MapAppBackdrop, LightDocAppBackdrop });
