// tokens.jsx — Single source of truth for the Handy design system.
// Two themes: "amber" (Claude-orange accent — primary) and "bone" (monochrome
// variant, accent only on the hand-mark). Everything else stays identical so
// codegen can flip a single ColorScheme value.

const HANDY_TOKENS = {
  amber: {
    name: "Amber",
    subtitle: "Warm, present, inviting",
    colors: {
      pageBg:          "#08090B",  // surface_page
      surface:         "#111317",  // surface_card
      surfaceElevated: "#181A1F",  // surface_elevated
      surfaceGlass:    "rgba(255,255,255,0.08)", // overlay only
      borderSubtle:    "rgba(255,255,255,0.08)",
      borderStrong:    "rgba(255,255,255,0.14)",
      textPrimary:     "#F4F2EE",
      textSecondary:   "#A8A39B",
      textMuted:       "#6E6A63",
      accent:          "#D97757",  // Claude orange
      accentInk:       "#1A0E07",
      accentSoft:      "rgba(217,119,87,0.12)",
      accentHairline:  "rgba(217,119,87,0.30)",
      success:         "#7FB069",
      successSoft:     "rgba(127,176,105,0.14)",
      danger:          "#D67D6B",
      dangerSoft:      "rgba(214,125,107,0.14)",
      // USP / category palette — used sparingly to give each value-prop card
      // (and the four permission tiles) their own atmosphere. Amber is
      // already the brand, so it plays the lead. Cobalt and emerald are the
      // supporting cast — desaturated, dark-friendly, never compete with the
      // accent CTAs.
      see:             "#D97757",        // amber (alias of accent)
      seeSoft:         "rgba(217,119,87,0.14)",
      point:           "#3B82F6",        // chart-4 blue — pointing / guidance
      pointSoft:       "rgba(59,130,246,0.20)",
      pointHair:       "rgba(59,130,246,0.30)",
      act:             "#7FB069",        // emerald — go, action (alias success)
      actSoft:         "rgba(127,176,105,0.14)",
      // Auxiliary tile colors used on the Permissions screen so the four
      // chips don't read as a uniform amber rectangle.
      violet:          "#B19CD9",
      violetSoft:      "rgba(177,156,217,0.14)",
      // Atmospheric supporting tones — used on splash + value vignettes so
      // the brand reads as warm/sunset rather than flat-on-black. Plum is
      // the cool shadow companion to amber (deepens gradients without
      // muddying them); honey is its warm sibling (sparkle highlights).
      plum:            "#3B1F2E",
      plumDeep:        "#1E0F19",
      plumSoft:        "rgba(140,82,118,0.22)",
      honey:           "#F0C674",
      honeySoft:       "rgba(240,198,116,0.18)",
    },
  },
  bone: {
    name: "Bone",
    subtitle: "Monochrome · accent only on the mark",
    colors: {
      pageBg:          "#0A0A0C",
      surface:         "#131316",
      surfaceElevated: "#1B1B1F",
      surfaceGlass:    "rgba(255,255,255,0.08)",
      borderSubtle:    "rgba(255,255,255,0.08)",
      borderStrong:    "rgba(255,255,255,0.16)",
      textPrimary:     "#F4F2EE",
      textSecondary:   "#A8A39B",
      textMuted:       "#6E6A63",
      accent:          "#F4F1EC",  // bone white as accent
      accentInk:       "#0A0A0C",
      accentSoft:      "rgba(244,241,236,0.10)",
      accentHairline:  "rgba(244,241,236,0.22)",
      // The hand-mark gets the Claude orange even in this theme — that's its
      // only privilege.
      markAccent:      "#D97757",
      success:         "#7FB069",
      successSoft:     "rgba(127,176,105,0.14)",
      danger:          "#D67D6B",
      dangerSoft:      "rgba(214,125,107,0.14)",
      // Same supporting palette as amber so screens swap cleanly between
      // themes — the USP cards keep their cobalt/emerald/violet identity.
      see:             "#D97757",
      seeSoft:         "rgba(217,119,87,0.14)",
      point:           "#7AA2F7",
      pointSoft:       "rgba(122,162,247,0.14)",
      act:             "#7FB069",
      actSoft:         "rgba(127,176,105,0.14)",
      violet:          "#B19CD9",
      violetSoft:      "rgba(177,156,217,0.14)",
    },
  },
};

// Resolve the active theme on a per-tree basis via React context.
const ThemeContext = React.createContext(HANDY_TOKENS.amber);
function useTheme() { return React.useContext(ThemeContext); }
function ThemeProvider({ theme = "amber", children }) {
  const value = HANDY_TOKENS[theme] || HANDY_TOKENS.amber;
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

// ───────────────────────── typography ─────────────────────────
//
// Söhne is a paid typeface; we ship a clean fallback chain that mirrors its
// metrics (Inter Tight for display + Inter for body) so the same numbers in
// Compose's Typography produce visually identical output.
const HANDY_TYPE = {
  fontDisplay: '"Söhne","Inter Tight","Inter",system-ui,-apple-system,sans-serif',
  fontBody:    '"Söhne","Inter","Inter Tight",system-ui,-apple-system,sans-serif',
  fontMono:    '"Söhne Mono","JetBrains Mono","Geist Mono",ui-monospace,monospace',
  scale: {
    display:    { size: 32, lh: 38, weight: 600, tracking: -0.022, font: "display" },
    title:      { size: 22, lh: 28, weight: 600, tracking: -0.012, font: "display" },
    titleSmall: { size: 18, lh: 24, weight: 600, tracking: -0.008, font: "display" },
    bodyStrong: { size: 15, lh: 22, weight: 500, tracking: -0.002, font: "body" },
    body:       { size: 15, lh: 22, weight: 400, tracking: 0,      font: "body" },
    caption:    { size: 13, lh: 18, weight: 400, tracking: 0,      font: "body" },
    overline:   { size: 11, lh: 14, weight: 500, tracking: 0.080,  font: "body", upper: true },
    mono:       { size: 12, lh: 18, weight: 400, tracking: 0,      font: "mono" },
  },
};

// Convenience: produce inline style for a type token.
function typeStyle(token, theme) {
  const t = HANDY_TYPE.scale[token];
  if (!t) return {};
  const fam = t.font === "display" ? HANDY_TYPE.fontDisplay
            : t.font === "mono"    ? HANDY_TYPE.fontMono
            : HANDY_TYPE.fontBody;
  return {
    fontFamily: fam,
    fontSize: t.size,
    lineHeight: t.lh + "px",
    fontWeight: t.weight,
    letterSpacing: t.tracking + "em",
    textTransform: t.upper ? "uppercase" : "none",
    color: theme ? theme.colors.textPrimary : undefined,
  };
}

window.HANDY_TOKENS = HANDY_TOKENS;
window.HANDY_TYPE = HANDY_TYPE;
window.ThemeContext = ThemeContext;
window.useTheme = useTheme;
window.ThemeProvider = ThemeProvider;
window.typeStyle = typeStyle;
