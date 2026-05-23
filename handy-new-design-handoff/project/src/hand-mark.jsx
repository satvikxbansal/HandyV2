// hand-mark.jsx — Brand mark variants.
//
// The brand mark is a single hand glyph. Variants change which hand pose the
// glyph adopts (open / wave / point / tap / pencil / bolt) so the floating
// widget can morph it to reflect Handy's current activity without changing
// the container.
//
// Containers:
//   tile       rounded square, surface-elevated fill, faint accent stroke
//              (app icon, hero in onboarding)
//   disc       solid accent fill — for the floating widget (the only place a
//              filled circle is allowed)
//   bare       no container — just the glyph (chat header, inline marks)

function HandMark({
  variant = "wave",     // wave | open | point | tap | pencil
  container = "bare",   // bare | tile | disc
  size = 56,
  // tile: corner radius is 22% per brief
  cornerPct = 0.22,
  // overrides
  glyphColor,
  fill,
  border,
  // disc variant uses ink-on-accent
  inkOnAccent,
  showWaveLines = true,
  style,
}) {
  const t = useTheme();
  // On a filled accent disc, the outline glyph reads thin — swap to the
  // filled Phosphor variant so the mark holds its weight against the accent
  // background. The tile + bare containers stay outlined.
  const onDisc = container === "disc";
  const glyphMap = onDisc
    ? { wave: "handFill",   open: "handFill",   point: "handPointFill",
        tap:  "handTap",    pencil: "handPencil" }
    : { wave: "handWave",   open: "handOpen",   point: "handPoint",
        tap:  "handTap",    pencil: "handPencil" };
  const glyphName = glyphMap[variant] || "handOpen";

  // size of the inner glyph
  const innerScale = container === "bare" ? 0.92 : 0.62;
  const innerSize = Math.round(size * innerScale);

  const containerStyles =
    container === "tile" ? {
      width: size, height: size,
      borderRadius: size * cornerPct,
      background: fill || t.colors.surface,
      border: `1px solid ${border || t.colors.accentHairline}`,
      display: "flex", alignItems: "center", justifyContent: "center",
    }
    : container === "disc" ? {
      width: size, height: size,
      borderRadius: "50%",
      background: fill || t.colors.accent,
      boxShadow: `0 6px 18px -4px ${t.colors.accent}55`,
      display: "flex", alignItems: "center", justifyContent: "center",
    }
    : {
      width: size, height: size,
      display: "flex", alignItems: "center", justifyContent: "center",
    };

  const color =
    glyphColor ||
    (container === "disc"
      ? (inkOnAccent || t.colors.accentInk)
      : (t.colors.markAccent || t.colors.accent));

  return (
    <div style={{ ...containerStyles, ...style }}>
      <Illu
        name={glyphName === "handWave" && !showWaveLines ? "handOpen" : glyphName}
        size={innerSize}
        color={color}
      />
    </div>
  );
}

// Wordmark = HandMark · "Handy"
function HandyWordmark({ size = 22, withMark = true, markSize = 24 }) {
  const t = useTheme();
  return (
    <div style={{ display: "inline-flex", alignItems: "center", gap: 10 }}>
      {withMark && <HandMark variant="wave" container="bare" size={markSize} showWaveLines={false} />}
      <span style={{
        font: `600 ${size}px/1 ${HANDY_TYPE.fontDisplay}`,
        letterSpacing: "-0.02em",
        color: t.colors.textPrimary,
      }}>Handy</span>
    </div>
  );
}

window.HandMark = HandMark;
window.HandyWordmark = HandyWordmark;
