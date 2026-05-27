// scenes-onboarding.jsx — Splash, value page (two variants), permissions.
//
// Design goals (May '26 refresh):
//   - Splash is a SHOWPIECE: concentric breathing rings + filled hand disc +
//     oversized wordmark. No copy beyond a one-line italic tagline + credit.
//   - Value page (cards) is a HORIZONTAL PAGER. Each card gets its own
//     atmosphere (amber / cobalt / emerald) with a custom hero scene built
//     from the illustration vocabulary + glow + abstract shapes — not a
//     bare icon next to text.
//   - Permissions stays a list, but the four tiles get four distinct hues
//     so the screen doesn't read as a monoblock of amber rectangles.
//
// All screens render INSIDE a Phone shell. The Phone passes the safe area;
// these scenes use ordinary block layout starting at the top-left.

function ScreenBody({ children, bg, style }) {
  const t = useTheme();
  return (
    <div style={{
      width: "100%", height: "100%",
      background: bg || t.colors.pageBg,
      color: t.colors.textPrimary,
      fontFamily: HANDY_TYPE.fontBody,
      padding: "24px 24px 32px",
      display: "flex", flexDirection: "column",
      ...style,
    }}>{children}</div>
  );
}

// ════════════════════════════════════════════════════════════════════════
//  01 · SPLASH
//
//  Layout (top → bottom):
//    1. Soft radial amber wash behind everything (bottom-half emphasis).
//    2. Three concentric breathing rings — outermost 360 dp, inner 80 dp.
//       Each ring has its own animation phase so they pulse out of sync.
//    3. Filled hand on a 76 dp accent disc, centered on the rings.
//    4. Wordmark "Handy" at 88 dp display, tight tracking.
//    5. Single-line italic tagline.
//    6. Credit pinned to bottom in muted mono.
// ════════════════════════════════════════════════════════════════════════

function SplashScreen() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%",
          background: t.colors.pageBg,
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
          position: "relative", overflow: "hidden",
          display: "flex", flexDirection: "column",
          alignItems: "center", justifyContent: "center",
          padding: "0 32px 28px",
        }}>
          {/* radial wash — sits behind the whole composition, lifts the
              centre of the screen so the rings feel lit from beneath */}
          <div style={{
            position: "absolute", inset: 0,
            background:
              "radial-gradient(80% 55% at 50% 58%, rgba(217,119,87,0.18) 0%, rgba(217,119,87,0.06) 35%, transparent 65%)",
            pointerEvents: "none",
          }} />

          {/* very faint top vignette — sells the depth */}
          <div style={{
            position: "absolute", inset: 0,
            background:
              "radial-gradient(120% 45% at 50% -10%, rgba(0,0,0,0.45) 0%, transparent 60%)",
            pointerEvents: "none",
          }} />

          {/* Rings + mark */}
          <SplashMark />

          {/* Wordmark */}
          <div style={{
            marginTop: 56,
            font: `600 76px/0.95 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.035em",
            color: t.colors.textPrimary,
            textAlign: "center",
          }}>Handy</div>

          {/* tagline — italic accent on "on-screen" pulls the eye */}
          <div style={{
            marginTop: 14,
            font: `400 17px/1.4 ${HANDY_TYPE.fontDisplay}`,
            color: t.colors.textSecondary,
            textAlign: "center",
            letterSpacing: "-0.005em",
          }}>
            Your <span style={{
              color: t.colors.accent,
              fontWeight: 500,
            }}>on-screen</span> copilot.
          </div>

          {/* credit — pinned bottom, mono so it whispers */}
          <div style={{
            position: "absolute", left: 0, right: 0, bottom: 28,
            textAlign: "center",
            font: `500 10px/1 ${HANDY_TYPE.fontMono}`,
            letterSpacing: "0.18em",
            color: t.colors.textMuted,
            textTransform: "uppercase",
          }}>Built with love by Satvik Bansal</div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

// SplashMark — concentric rings around the hand disc.
function SplashMark() {
  const t = useTheme();
  return (
    <div style={{
      position: "relative",
      width: 320, height: 320,
      display: "flex", alignItems: "center", justifyContent: "center",
    }}>
      {/* Outer ring — largest, faintest, slowest */}
      <Ring size={320} stroke={1} opacity={0.10} delay="0s"  duration="3.6s" />
      <Ring size={240} stroke={1} opacity={0.16} delay="0.6s" duration="3.6s" />
      <Ring size={170} stroke={1.5} opacity={0.30} delay="1.2s" duration="3.6s" />

      {/* Hand disc — filled accent with a glow halo */}
      <div style={{ position: "relative" }}>
        <div style={{
          position: "absolute", inset: -12, borderRadius: "50%",
          background: `radial-gradient(circle, ${t.colors.accent}55 0%, transparent 70%)`,
          filter: "blur(8px)", pointerEvents: "none",
        }} />
        <div style={{
          position: "relative",
          width: 96, height: 96, borderRadius: "50%",
          background: `linear-gradient(160deg, ${t.colors.accent} 0%, #C76547 100%)`,
          display: "flex", alignItems: "center", justifyContent: "center",
          boxShadow: `0 10px 32px -8px ${t.colors.accent}77, inset 0 1px 0 rgba(255,255,255,0.12)`,
        }}>
          <Illu name="handFill" size={56} color={t.colors.accentInk} />
        </div>
      </div>
    </div>
  );
}

function Ring({ size, stroke = 1, opacity = 0.15, delay = "0s", duration = "3s" }) {
  const t = useTheme();
  return (
    <div style={{
      position: "absolute",
      width: size, height: size,
      borderRadius: "50%",
      border: `${stroke}px solid ${t.colors.accent}`,
      opacity,
      animation: `handy-breath ${duration} ease-in-out infinite`,
      animationDelay: delay,
    }} />
  );
}

// ════════════════════════════════════════════════════════════════════════
//  02a · VALUE PAGE — CARDS (horizontal pager)
//
//  Three large hero cards, each its own color family:
//    See  → amber  (lit phone screen)
//    Point → cobalt (hand pointing at a target ring)
//    Do   → emerald (bolt over an action button)
//
//  Layout:
//    - Slim step indicator + skip top
//    - Compact wordmark
//    - Editorial display title — "Experience your screen, reimagined."
//      with italic accent on "reimagined"
//    - Horizontal pager: card 1 at full width (288 dp), card 2 peeks
//    - Pager dots in active color
//    - Privacy strip (subtle)
//    - Primary CTA
// ════════════════════════════════════════════════════════════════════════

const USP_CARDS = [
  {
    key: "see",
    accent: "see",
    soft: "seeSoft",
    eyebrow: "See",
    title: "Understands\nany screen.",
    body: "Ask about what you're looking at — no copy-paste, no screenshots.",
    Hero: HeroSee,
  },
  {
    key: "point",
    accent: "point",
    soft: "pointSoft",
    eyebrow: "Guide",
    title: "Points to\nthe right tap.",
    body: "A hand-mark flies to the control you need. You still tap.",
    Hero: HeroPoint,
  },
  {
    key: "act",
    accent: "act",
    soft: "actSoft",
    eyebrow: "Do",
    title: "Does the\nboring bits.",
    body: "Bounded actions, always with your OK. Set a timer, open a page, tap a control.",
    Hero: HeroAct,
  },
];

function ValueScreenCards({ activeIndex = 0 } = {}) {
  const t = HANDY_TOKENS.amber;
  // Card width 288 + 14 gap = 302 horizontal step per page.
  const offset = activeIndex * -302;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%",
          background: t.colors.pageBg,
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
          display: "flex", flexDirection: "column",
          overflow: "hidden",
        }}>
          {/* top row — wordmark + skip */}
          <div style={{
            display: "flex", alignItems: "center", justifyContent: "space-between",
            padding: "8px 24px 0",
          }}>
            <HandyWordmark size={16} markSize={22} />
            <span style={{
              font: `500 13px/1 ${HANDY_TYPE.fontBody}`,
              color: t.colors.textMuted,
            }}>Skip</span>
          </div>

          {/* Title block */}
          <div style={{ padding: "26px 24px 0" }}>
            <div style={{
              font: `600 36px/1.04 ${HANDY_TYPE.fontDisplay}`,
              letterSpacing: "-0.030em",
              color: t.colors.textPrimary,
            }}>
              Experience your screen,{" "}
              <span style={{
                color: t.colors.accent,
                fontWeight: 600,
              }}>reimagined.</span>
            </div>
          </div>

          {/* Pager — translates by activeIndex × (card width + gap) */}
          <div style={{ flex: 1, marginTop: 22, overflow: "hidden", position: "relative" }}>
            <div style={{
              display: "flex", gap: 14,
              paddingLeft: 24,
              transform: `translateX(${offset}px)`,
              transition: "transform 320ms cubic-bezier(0.34, 1.1, 0.4, 1)",
            }}>
              {USP_CARDS.map((c, i) => (
                <USPHeroCard key={c.key} card={c} active={i === activeIndex} />
              ))}
            </div>
          </div>

          {/* Pager dots — active dot adopts the card's color */}
          <div style={{
            display: "flex", justifyContent: "center", gap: 6,
            padding: "14px 0 6px",
          }}>
            {USP_CARDS.map((c, i) => (
              <div key={c.key} style={{
                height: 5, borderRadius: 3,
                width: i === activeIndex ? 22 : 5,
                background: i === activeIndex
                  ? t.colors[USP_CARDS[activeIndex].accent]
                  : t.colors.surfaceElevated,
                transition: "all 240ms ease-out",
              }} />
            ))}
          </div>

          {/* Privacy footer */}
          <div style={{
            display: "flex", gap: 10, alignItems: "center",
            padding: "10px 24px 12px",
            justifyContent: "center",
            font: `400 12px/1.4 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textMuted,
          }}>
            <Illu name="shield" size={14} color={t.colors.textMuted} />
            <span>No login, no servers of ours.{" "}
              <span style={{ color: t.colors.accent, textDecoration: "underline", textUnderlineOffset: 2 }}>
                What Handy sees
              </span>
            </span>
          </div>

          {/* CTA */}
          <div style={{ padding: "4px 20px 20px" }}>
            <PrimaryButton label="Get started" />
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

// USPHeroCard — the large card body. Width is fixed so the second card
// peeks naturally at the right edge of the pager viewport.
function USPHeroCard({ card, active = false }) {
  const t = useTheme();
  const accent = t.colors[card.accent];
  const soft = t.colors[card.soft];
  const Hero = card.Hero;
  return (
    <div style={{
      flex: "0 0 auto",
      width: 288,
      height: "100%",
      borderRadius: 22,
      background: `linear-gradient(180deg, ${soft} 0%, ${t.colors.surface} 55%)`,
      border: `1px solid ${active ? hexA(accent, 0.35) : t.colors.borderSubtle}`,
      boxShadow: active ? `0 0 60px -20px ${hexA(accent, 0.45)}` : "none",
      display: "flex", flexDirection: "column",
      overflow: "hidden",
      opacity: active ? 1 : 0.55,
    }}>
      {/* Hero scene — fills upper 2/3 */}
      <div style={{
        flex: 1, position: "relative",
        display: "flex", alignItems: "center", justifyContent: "center",
        overflow: "hidden",
      }}>
        <Hero accent={accent} />
      </div>

      {/* Text block */}
      <div style={{ padding: "20px 22px 22px" }}>
        <div style={{
          font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.16em", textTransform: "uppercase",
          color: accent, marginBottom: 8,
        }}>{card.eyebrow}</div>
        <div style={{
          font: `600 24px/1.1 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.020em",
          color: t.colors.textPrimary,
          whiteSpace: "pre-line",
        }}>{card.title}</div>
        <div style={{
          marginTop: 8,
          font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
        }}>{card.body}</div>
      </div>
    </div>
  );
}

// hex helper for alpha-ing the accent in box-shadows / borders.
function hexA(hex, a) {
  if (hex.startsWith("rgba")) return hex;
  const c = hex.replace("#", "");
  const r = parseInt(c.substring(0, 2), 16);
  const g = parseInt(c.substring(2, 4), 16);
  const b = parseInt(c.substring(4, 6), 16);
  return `rgba(${r},${g},${b},${a})`;
}

// ─── Hero scenes — one per USP card ───────────────────────────────────────

// "See" — a phone screen at a 3D-ish angle with an amber spotlight cone
// hitting a content block.
function HeroSee({ accent }) {
  const t = useTheme();
  return (
    <svg viewBox="0 0 288 260" width="100%" height="100%" style={{ display: "block" }}>
      <defs>
        <linearGradient id="see-spot" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"   stopColor={accent} stopOpacity="0.45" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </linearGradient>
        <linearGradient id="see-screen" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%"  stopColor="#1A1D22" />
          <stop offset="100%" stopColor="#0D0F12" />
        </linearGradient>
      </defs>

      {/* spotlight cone hitting the screen */}
      <path d="M120 -20 L260 140 L150 200 L80 30 Z" fill="url(#see-spot)" />

      {/* phone, slightly tilted */}
      <g transform="translate(72 38) rotate(-8 60 110)">
        <rect x="0" y="0" width="120" height="220" rx="20"
          fill="url(#see-screen)" stroke={hexA(accent, 0.25)} strokeWidth="1" />
        {/* screen content silhouettes */}
        <rect x="14" y="20" width="44" height="6" rx="3" fill="rgba(255,255,255,0.10)" />
        <rect x="14" y="34" width="80" height="6" rx="3" fill="rgba(255,255,255,0.06)" />
        {/* the highlighted control — amber-lit */}
        <rect x="14" y="86" width="92" height="46" rx="10"
          fill={hexA(accent, 0.22)} stroke={accent} strokeWidth="1.2" />
        <rect x="22" y="100" width="56" height="6" rx="3" fill={hexA(accent, 0.85)} />
        <rect x="22" y="114" width="40" height="5" rx="2.5" fill={hexA(accent, 0.55)} />
        {/* trailing lines */}
        <rect x="14" y="148" width="92" height="5" rx="2.5" fill="rgba(255,255,255,0.06)" />
        <rect x="14" y="160" width="70" height="5" rx="2.5" fill="rgba(255,255,255,0.06)" />
        <rect x="14" y="172" width="60" height="5" rx="2.5" fill="rgba(255,255,255,0.06)" />
        {/* home bar */}
        <rect x="42" y="204" width="36" height="3" rx="1.5" fill="rgba(255,255,255,0.12)" />
      </g>

      {/* eye glyph floating in the spotlight */}
      <g transform="translate(196 62)">
        <circle r="22" fill={hexA(accent, 0.18)} />
        <g transform="translate(-14 -14)">
          <Illu name="eye" size={28} color={accent} />
        </g>
      </g>
    </svg>
  );
}

// "Point" — hand-mark hovering above a target ring on a UI control.
function HeroPoint({ accent }) {
  const t = useTheme();
  return (
    <svg viewBox="0 0 288 260" width="100%" height="100%" style={{ display: "block" }}>
      <defs>
        <radialGradient id="point-glow" cx="50%" cy="60%" r="50%">
          <stop offset="0%"  stopColor={accent} stopOpacity="0.35" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect x="0" y="0" width="288" height="260" fill="url(#point-glow)" />

      {/* surface card (simulated host control) */}
      <g transform="translate(50 130)">
        <rect x="0" y="0" width="188" height="92" rx="16"
          fill="#15171B" stroke="rgba(255,255,255,0.08)" strokeWidth="1" />
        <rect x="18" y="18" width="40" height="40" rx="10" fill={hexA(accent, 0.18)} />
        <circle cx="38" cy="38" r="6" fill={accent} />
        <rect x="68" y="22" width="84" height="7" rx="3" fill="rgba(255,255,255,0.18)" />
        <rect x="68" y="36" width="60" height="6" rx="3" fill="rgba(255,255,255,0.08)" />
        <rect x="68" y="58" width="74" height="20" rx="10"
          fill={hexA(accent, 0.18)} stroke={accent} strokeWidth="1.2" />
      </g>

      {/* concentric target rings on the highlighted button */}
      <g transform="translate(155 198)">
        <circle r="34" fill="none" stroke={accent} strokeWidth="1" strokeOpacity="0.35" />
        <circle r="22" fill="none" stroke={accent} strokeWidth="1.2" strokeOpacity="0.6" />
        <circle r="10" fill={accent} fillOpacity="0.9" />
      </g>

      {/* pointer floating above, leaning toward the target */}
      <g transform="translate(132 36) rotate(12 32 32)">
        {/* halo */}
        <circle cx="32" cy="32" r="40" fill={hexA(accent, 0.20)} />
        <circle cx="32" cy="32" r="40" fill="none" stroke={accent} strokeOpacity="0.4" />
        <g transform="translate(8 8)">
          <Illu name="handPointBold" size={48} color={accent} />
        </g>
      </g>

      {/* trailing motion dots from hand → target */}
      <g fill={accent}>
        <circle cx="170" cy="120" r="2" opacity="0.8" />
        <circle cx="166" cy="148" r="1.6" opacity="0.55" />
        <circle cx="160" cy="176" r="1.2" opacity="0.35" />
      </g>
    </svg>
  );
}

// "Do" — bolt above a row with a button being tapped, surrounded by motion
// ticks.
function HeroAct({ accent }) {
  const t = useTheme();
  return (
    <svg viewBox="0 0 288 260" width="100%" height="100%" style={{ display: "block" }}>
      <defs>
        <radialGradient id="act-glow" cx="50%" cy="40%" r="55%">
          <stop offset="0%" stopColor={accent} stopOpacity="0.32" />
          <stop offset="100%" stopColor={accent} stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect x="0" y="0" width="288" height="260" fill="url(#act-glow)" />

      {/* concentric rings around the centre */}
      <g transform="translate(144 110)">
        <circle r="68" fill="none" stroke={accent} strokeOpacity="0.20" />
        <circle r="48" fill="none" stroke={accent} strokeOpacity="0.30" />
        <circle r="28" fill="none" stroke={accent} strokeOpacity="0.45" />
        {/* bolt disc */}
        <circle r="36" fill={accent} />
        <g transform="translate(-14 -16)">
          <Illu name="bolt" size={32} color="#0D1A11" />
        </g>
      </g>

      {/* short status ticks orbiting */}
      <g stroke={accent} strokeWidth="1.6" strokeLinecap="round">
        <path d="M48 50 L60 50" opacity="0.6" />
        <path d="M232 70 L244 70" opacity="0.6" />
        <path d="M40 130 L52 130" opacity="0.5" />
        <path d="M236 150 L248 150" opacity="0.5" />
      </g>

      {/* a target row at bottom */}
      <g transform="translate(40 200)">
        <rect x="0" y="0" width="208" height="44" rx="14"
          fill="#15171B" stroke="rgba(255,255,255,0.08)" strokeWidth="1" />
        <rect x="14" y="14" width="100" height="6" rx="3" fill="rgba(255,255,255,0.20)" />
        <rect x="14" y="26" width="60" height="5" rx="2.5" fill="rgba(255,255,255,0.10)" />
        {/* confirm pill */}
        <rect x="138" y="10" width="60" height="24" rx="12" fill={accent} />
        <text x="168" y="26" textAnchor="middle"
          fontFamily="Söhne, Inter, system-ui" fontSize="11" fontWeight="600"
          fill="#0D1A11">Done</text>
      </g>
    </svg>
  );
}

// ════════════════════════════════════════════════════════════════════════
//  02b · VALUE PAGE — EDITORIAL LIST (kept as second variant)
// ════════════════════════════════════════════════════════════════════════

function ValueScreenList() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <ScreenBody>
          <StepIndicator step={1} total={3} />

          <div style={{ marginTop: 28, position: "relative" }}>
            {/* hand mark anchored top-right of title */}
            <div style={{ position: "absolute", right: -8, top: -6, opacity: 0.95 }}>
              <Illu name="handPoint" size={108} color={t.colors.accent} />
            </div>
            <div style={{
              font: `600 36px/1.04 ${HANDY_TYPE.fontDisplay}`,
              letterSpacing: "-0.030em",
              color: t.colors.textPrimary,
              maxWidth: 220,
            }}>
              An on-screen{" "}
              <span style={{ color: t.colors.accent, fontWeight: 600 }}>
                copilot.
              </span>
            </div>
          </div>

          <div style={{ marginTop: 70, display: "flex", flexDirection: "column", gap: 20 }}>
            <ListItem n="01" illu="a11y"      title="Sees the screen"
              color="see"
              body="Reads visible UI through Android Accessibility — never sent to servers of ours." />
            <ListItem n="02" illu="handPoint" title="Points the way"
              color="point"
              body="A floating hand-mark flies to the button you should tap next." />
            <ListItem n="03" illu="bolt"      title="Acts, with your OK"
              color="act"
              body="Bounded tasks — set a timer, fetch a page, tap a known control." />
          </div>

          <div style={{ flex: 1 }} />

          <div style={{
            display: "flex", gap: 10, alignItems: "center", marginBottom: 16,
            ...typeStyle("caption", t), color: t.colors.textSecondary,
          }}>
            <Illu name="shield" size={16} color={t.colors.accent} />
            <span>No login, no accounts.{" "}
              <span style={{ color: t.colors.accent, textDecoration: "underline", textUnderlineOffset: 2 }}>
                What Handy sees →
              </span>
            </span>
          </div>

          <PrimaryButton label="Get started" />
        </ScreenBody>
      </Phone>
    </ThemeProvider>
  );
}

function ListItem({ n, illu, title, body, color = "see" }) {
  const t = useTheme();
  const c = t.colors[color] || t.colors.accent;
  return (
    <div style={{ display: "flex", gap: 16, alignItems: "flex-start" }}>
      <div style={{
        font: `600 11px/1 ${HANDY_TYPE.fontMono}`,
        color: c,
        paddingTop: 6, letterSpacing: "0.08em",
        width: 22,
      }}>{n}</div>
      <div style={{ paddingTop: 2 }}>
        <Illu name={illu} size={30} color={c} />
      </div>
      <div style={{ flex: 1 }}>
        <div style={typeStyle("bodyStrong", t)}>{title}</div>
        <div style={{ ...typeStyle("caption", t), color: t.colors.textSecondary, marginTop: 2 }}>{body}</div>
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
//  03 · PERMISSIONS — color-coded tiles per permission
// ════════════════════════════════════════════════════════════════════════

function StepIndicator({ step, total }) {
  const t = useTheme();
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      {Array.from({ length: total }).map((_, i) => (
        <div key={i} style={{
          height: 4, borderRadius: 2,
          width: i + 1 === step ? 22 : 12,
          background: i + 1 <= step ? t.colors.accent : t.colors.surfaceElevated,
          transition: "all 240ms ease-out",
        }} />
      ))}
    </div>
  );
}

function PermissionsScreen({ state = "partial" }) {
  const t = HANDY_TOKENS.amber;
  const allGranted = state === "all";
  const a11yGranted = allGranted;

  // Each permission gets its own atmosphere — matches the inspiration
  // (mic-orange, bell-violet, overlay-cobalt, eye-emerald).
  const perms = [
    { key: "mic",     illu: "mic",     color: "see",    title: "Microphone",
      caption: "Voice when you long-press.", granted: true },
    { key: "notif",   illu: "bell",    color: "violet", title: "Notifications",
      caption: "So Handy can tell you when it's ready.", granted: true },
    { key: "overlay", illu: "overlay", color: "point",  title: "Draw over other apps",
      caption: "The floating mark lives above anything.", granted: true },
    { key: "a11y",    illu: "a11y",    color: "act",    title: "Accessibility",
      caption: "Read the active screen to help in context.", granted: a11yGranted },
  ];

  return (
    <ThemeProvider theme="amber">
      <Phone>
        <ScreenBody>
          <div style={{
            marginTop: 8,
            font: `600 36px/1.04 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.030em",
            color: t.colors.textPrimary,
          }}>
            One more <span style={{ color: t.colors.accent, fontWeight: 600 }}>step.</span>
          </div>
          <div style={{ marginTop: 8, ...typeStyle("body", t), color: t.colors.textSecondary }}>
            Handy needs these to work. You can disable any of them later.
          </div>

          <div style={{ marginTop: 28, display: "flex", flexDirection: "column", gap: 10 }}>
            {perms.map((p) => (
              <PermissionRow key={p.key} {...p} />
            ))}
          </div>

          <div style={{ flex: 1 }} />

          <div style={{
            display: "flex", gap: 10, alignItems: "center",
            paddingTop: 14, marginBottom: 14,
            borderTop: `1px solid ${t.colors.borderSubtle}`,
            ...typeStyle("caption", t), color: t.colors.textSecondary,
          }}>
            <Illu name="shield" size={16} color={t.colors.accent} />
            <span>Your data stays yours. Handy talks directly to your AI.</span>
          </div>

          <PrimaryButton label="Open Handy" />

          {!a11yGranted && (
            <div style={{
              ...typeStyle("caption", t),
              color: t.colors.textSecondary,
              textAlign: "center", marginTop: 12, padding: "0 8px",
            }}>
              Without accessibility, Handy can chat — but can't see your screen.{" "}
              <span style={{ color: t.colors.accent, textDecoration: "underline", textUnderlineOffset: 2 }}>
                Enable accessibility
              </span>
            </div>
          )}
        </ScreenBody>
      </Phone>
    </ThemeProvider>
  );
}

// PermissionRow — multi-color version of Row, tailored for this screen.
// Compose: PermissionRow(perm: Permission, onEnable)
function PermissionRow({ illu, color, title, caption, granted }) {
  const t = useTheme();
  const c = t.colors[color] || t.colors.accent;
  const soft = t.colors[color + "Soft"] || t.colors.accentSoft;

  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "14px 16px",
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 18,
    }}>
      {/* leading tile — color per permission */}
      <div style={{
        width: 40, height: 40, borderRadius: 11,
        background: soft,
        border: `1px solid ${hexA(c, 0.20)}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        flex: "0 0 auto",
      }}>
        <Illu name={illu} size={22} color={c} />
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          color: t.colors.textPrimary,
          font: `500 15px/1.3 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "-0.005em",
        }}>{title}</div>
        <div style={{
          color: t.colors.textSecondary,
          font: `400 13px/1.45 ${HANDY_TYPE.fontBody}`,
          marginTop: 2,
        }}>{caption}</div>
      </div>

      {/* trailing — colored chip or check, color matches the row */}
      {granted ? (
        <div style={{
          display: "inline-flex", alignItems: "center", gap: 5,
          height: 26, padding: "0 10px", borderRadius: 13,
          background: hexA(c, 0.14),
          color: c,
          font: `600 11px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.08em", textTransform: "uppercase",
        }}>
          <Illu name="check" size={11} color={c} />
          Granted
        </div>
      ) : (
        <div style={{
          height: 32, padding: "0 14px", borderRadius: 11,
          background: c, color: "#0D0F12",
          font: `600 12px/32px ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.01em",
          boxShadow: `0 6px 16px -6px ${hexA(c, 0.55)}`,
        }}>Enable</div>
      )}
    </div>
  );
}

window.SplashScreen = SplashScreen;
window.ValueScreenCards = ValueScreenCards;
window.ValueScreenList = ValueScreenList;
window.PermissionsScreen = PermissionsScreen;
window.ScreenBody = ScreenBody;
window.StepIndicator = StepIndicator;
window.hexA = hexA;
