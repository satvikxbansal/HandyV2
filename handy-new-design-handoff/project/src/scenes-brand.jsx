// scenes-brand.jsx — Brand mark + wordmark + launcher icon set.

function BrandSystem() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 1160, height: 520, background: t.colors.pageBg,
        borderRadius: 20, padding: 36,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
        display: "flex", gap: 36,
      }}>
        {/* Hero: the mark + wordmark */}
        <div style={{
          flex: 1.2,
          background: t.colors.surface,
          border: `1px solid ${t.colors.borderSubtle}`,
          borderRadius: 18, padding: 32,
          display: "flex", flexDirection: "column", justifyContent: "space-between",
        }}>
          <div>
            <div style={{ font: `500 11px/1 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted, letterSpacing: "0.12em", textTransform: "uppercase" }}>
              Wordmark
            </div>
            <div style={{ marginTop: 28, display: "flex", alignItems: "center", gap: 18 }}>
              <HandMark variant="open" container="bare" size={88} showWaveLines={false} />
              <span style={{
                font: `600 64px/1 ${HANDY_TYPE.fontDisplay}`,
                letterSpacing: "-0.028em",
                color: t.colors.textPrimary,
              }}>Handy</span>
            </div>
            <div style={{ marginTop: 16, font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, maxWidth: 380 }}>
              Mark sits 12 dp to the left of the wordmark. Never use the wordmark without the mark.
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 18 }}>
            <HandMark variant="open"   container="bare" size={36} showWaveLines={false} />
            <HandMark variant="wave"   container="bare" size={36} />
            <HandMark variant="point"  container="bare" size={36} />
            <Illu name="cursor" size={28} color={t.colors.accent} />
            <div style={{ font: `400 12px/1.4 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted, marginLeft: 8 }}>
              open · wave · point · cursor
            </div>
          </div>
        </div>

        {/* App icons */}
        <div style={{
          flex: 1.4,
          background: t.colors.surface,
          border: `1px solid ${t.colors.borderSubtle}`,
          borderRadius: 18, padding: 32,
          display: "flex", flexDirection: "column", gap: 22,
        }}>
          <div>
            <div style={{ font: `500 11px/1 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted, letterSpacing: "0.12em", textTransform: "uppercase" }}>
              Launcher icon
            </div>
            <div style={{ marginTop: 6, font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary }}>
              Rounded-square container (22 % corner radius) — replaces the v1 orange disc. Reads at 48 dp.
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "flex-end", gap: 32, flexWrap: "wrap" }}>
            {[
              { size: 192, label: "192 dp" },
              { size: 96,  label: "96 dp"  },
              { size: 64,  label: "64 dp"  },
              { size: 48,  label: "48 dp · launcher minimum" },
            ].map((s, i) => (
              <div key={i} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 10 }}>
                <HandMark variant="open" container="tile" size={s.size} showWaveLines={false} />
                <div style={{ font: `400 11px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>{s.label}</div>
              </div>
            ))}
          </div>

          {/* Color-on-color icon variants */}
          <div style={{ display: "flex", gap: 20, marginTop: 4, alignItems: "center" }}>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              <HandMark variant="open" container="tile" size={64} showWaveLines={false} />
              <div style={{ font: `400 10px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>standard</div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              {/* monogram: amber tile, ink glyph (light theme launcher) */}
              <HandMark variant="open" container="tile" size={64} fill={t.colors.accent} glyphColor={t.colors.accentInk} showWaveLines={false} />
              <div style={{ font: `400 10px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>monochrome accent</div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8 }}>
              {/* Themed device icon (Android 13+ themed) */}
              <HandMark variant="open" container="tile" size={64} fill="#F4F2EE" glyphColor="#08090B" border="rgba(0,0,0,0.10)" showWaveLines={false} />
              <div style={{ font: `400 10px/1 ${HANDY_TYPE.fontMono}`, color: t.colors.textMuted }}>themed-icon (system)</div>
            </div>
            <div style={{ marginLeft: "auto", textAlign: "right" }}>
              <div style={{ font: `400 11px/1.45 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted, maxWidth: 220 }}>
                Adaptive icon: foreground = hand glyph, background = surface fill. Inner padding ≤ 18 %.
              </div>
            </div>
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}

window.BrandSystem = BrandSystem;
