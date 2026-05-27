// scenes-bubbles.jsx — Floating-widget side-bubble state board.
//
// The widget is a 48 dp accent disc that lives on top of every app. When
// something is happening, a side bubble extends from it toward the screen
// interior. This artboard lays out every state the bubble system can take
// so the design system reads as one consistent language.
//
// Design rules (applied across all states):
//   1. Surface: dark glass — rgba(18,20,24,0.78) + 28 px backdrop blur,
//      0.5 px white-12% hairline border, 18 dp radius squircle.
//   2. Glow: each bubble gets a 22 % tinted shadow in its tone color so
//      the state is glanceable over any host UI without relying on color
//      fills (which clash with other apps' chrome).
//   3. Type: 14 sp / weight 500 / tight tracking. One line by default;
//      wraps to two before truncating with an ellipsis.
//   4. Tail: subtle 6 dp triangle pointer connecting bubble to widget.
//      Auto-mirrors when the widget docks on the right edge.
//   5. Tones aren't decorative — each tone is functionally bound:
//        amber   → Handy's voice / listening / answer
//        blue    → Pointing & navigation (matches widget Pointing state)
//        emerald → action in progress / success
//        violet  → web tools (Brave / GitHub / Jina)
//        honey   → reading / fetching page
//        red     → error / blocked
//        muted   → ambient / thinking
//   6. The widget glyph next to the bubble morphs to reflect the activity
//      (listening bars, spinning arc, cursor, pointer, etc.) — same
//      WidgetGlyph component as the standalone widget-states artboard.
//
// Layout: widget on the LEFT for most rows; one row demos a RIGHT-docked
// pair to prove the system mirrors cleanly. A small-screen variant at the
// bottom proves the bubble truncates gracefully at < 320 dp wide.

// ─────────────────────────────────────────────────────────────────────
//  Side-bubble primitive
// ─────────────────────────────────────────────────────────────────────

function SideBubble({
  tone = "accent",
  label,
  prefix,                // optional small overline chip ("STEP 2 / 5")
  leading,               // optional leading icon name OR ReactNode
  trailingProgress,      // 0..1 — paints a thin progress strip at the bottom
  italic = false,        // for voice transcript copy
  // Tail removed (May '26 refresh) — read as visual noise next to the
  // already-tinted widget halo. We keep the `tail` prop only to mirror
  // the bubble's max-width side; no triangle is rendered.
  tail = "left",
  maxWidth = 280,
  small = false,
}) {
  const t = useTheme();
  const tone_color = t.colors[tone] || t.colors.accent;
  const glow = `${tone_color}38`;       // ~22% alpha

  return (
    <div style={{ position: "relative", display: "inline-flex", alignItems: "center" }}>
      {/* glow halo behind */}
      <span style={{
        position: "absolute", inset: -8,
        borderRadius: 28,
        background: `radial-gradient(60% 100% at 50% 50%, ${glow}, transparent 70%)`,
        filter: "blur(6px)",
        pointerEvents: "none", zIndex: 0,
      }} />

      <div style={{
        position: "relative", zIndex: 2,
        maxWidth,
        display: "flex", flexDirection: "column", gap: 4,
        padding: small ? "8px 12px" : "10px 14px",
        borderRadius: 18,
        background: "rgba(18,20,24,0.82)",
        backdropFilter: "blur(28px) saturate(160%)",
        WebkitBackdropFilter: "blur(28px) saturate(160%)",
        border: "0.5px solid rgba(255,255,255,0.12)",
        boxShadow: `0 10px 28px -14px ${tone_color}88, 0 1px 0 rgba(255,255,255,0.03) inset`,
      }}>
        {prefix && (
          <div style={{
            display: "inline-flex", alignSelf: "flex-start",
            font: `600 9px/1 ${HANDY_TYPE.fontBody}`,
            color: tone_color,
            letterSpacing: "0.14em", textTransform: "uppercase",
          }}>{prefix}</div>
        )}
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          {leading && (
            <span style={{
              flex: "0 0 auto", display: "inline-flex", alignItems: "center", justifyContent: "center",
              color: tone_color,
            }}>
              {typeof leading === "string"
                ? <Illu name={leading} size={14} color={tone_color} />
                : leading}
            </span>
          )}
          <span style={{
            flex: 1, minWidth: 0,
            font: `${italic ? "400" : "500"} ${small ? 12 : 13.5}px/1.35 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textPrimary,
            letterSpacing: "-0.005em",
          }}>{label}</span>
        </div>
        {typeof trailingProgress === "number" && (
          <div style={{
            height: 2, marginTop: 6,
            background: "rgba(255,255,255,0.06)",
            borderRadius: 2, overflow: "hidden",
          }}>
            <div style={{
              width: `${trailingProgress * 100}%`,
              height: "100%",
              background: tone_color,
              borderRadius: 2,
            }} />
          </div>
        )}
      </div>
    </div>
  );
}

// ─────────────────────────────────────────────────────────────────────
//  Inline mini-glyphs reused inside bubbles
// ─────────────────────────────────────────────────────────────────────

function MiniDots() {
  const t = useTheme();
  return (
    <span style={{ display: "inline-flex", gap: 3 }}>
      {[0, 0.15, 0.3].map((d, i) => (
        <span key={i} style={{
          width: 4, height: 4, borderRadius: "50%",
          background: t.colors.textSecondary,
          animation: "handy-livedot 1.1s ease-in-out infinite",
          animationDelay: `${d}s`,
        }} />
      ))}
    </span>
  );
}

function MiniBars({ color }) {
  return (
    <span style={{ display: "inline-flex", alignItems: "center", gap: 2, height: 14 }}>
      {[0.12, 0.28, 0.42, 0.28, 0.12].map((d, i) => (
        <span key={i} style={{
          width: 2.5, borderRadius: 1.5,
          height: 12,
          background: color,
          transformOrigin: "50% 50%",
          animation: "handy-listening-bar 0.9s ease-in-out infinite",
          animationDelay: `${d}s`,
        }} />
      ))}
    </span>
  );
}

// ─────────────────────────────────────────────────────────────────────
//  Bubble + widget pair — the full unit shown in the demo board
// ─────────────────────────────────────────────────────────────────────

function BubblePair({ widget, children, side = "left" }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 6,
      flexDirection: side === "left" ? "row" : "row-reverse",
    }}>
      <div style={{ flex: "0 0 auto" }}>{widget}</div>
      {children}
    </div>
  );
}

// Compact widget — same disc dimensions as the live widget, just rendered
// inline without absolute positioning.
function WidgetMini({ state }) { return <WidgetGlyph state={state} size={40} />; }

// Specialized widget glyphs for the bubble states (some are unique to the
// bubble system — e.g. the small spinning arc for "thinking inside a tool").
function WidgetWebMini({ tone = "violet" }) {
  const t = useTheme();
  const c = t.colors[tone] || t.colors.accent;
  return (
    <span style={{
      position: "relative",
      width: 40, height: 40, borderRadius: "50%",
      background: t.colors.surface,
      border: `0.5px solid ${t.colors.borderSubtle}`,
      display: "inline-flex", alignItems: "center", justifyContent: "center",
    }}>
      <svg width="48" height="48" viewBox="0 0 100 100"
        style={{ position: "absolute", animation: "handy-spin 1.6s linear infinite" }}>
        <circle cx="50" cy="50" r="46" fill="none" stroke={c} strokeWidth="3"
          strokeDasharray="120 320" strokeLinecap="round" />
      </svg>
      <Illu name="globe" size={18} color={c} />
    </span>
  );
}

// ─────────────────────────────────────────────────────────────────────
//  The board itself
// ─────────────────────────────────────────────────────────────────────

function BubbleStatesBoard() {
  const t = HANDY_TOKENS.amber;

  // Stage size — wider than the widget-states artboard since we're showing
  // bubble + widget pairs that fan to the right.
  return (
    <ThemeProvider theme="amber">
      <div style={{
        width: 980, height: 1500,
        background: t.colors.pageBg,
        borderRadius: 20, padding: 36,
        fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
        position: "relative", overflow: "hidden",
      }}>
        {/* faint ambient host-app silhouette so bubbles read in-context */}
        <div style={{
          position: "absolute", inset: 0,
          background:
            "radial-gradient(50% 35% at 30% 15%, rgba(255,255,255,0.018) 0%, transparent 70%)," +
            "radial-gradient(40% 30% at 80% 70%, rgba(255,255,255,0.012) 0%, transparent 70%)",
          pointerEvents: "none",
        }} />

        {/* Header */}
        <div style={{ position: "relative" }}>
          <div style={{ font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
            Floating overlay · bubble system
          </div>
          <div style={{ font: `400 13px/1.5 ${HANDY_TYPE.fontBody}`, color: t.colors.textSecondary, marginTop: 6, maxWidth: 640 }}>
            One bubble vocabulary across every state the widget can be in. Same dark glass surface, same 18 dp radius — the tone color carries meaning (amber = voice, blue = pointing, emerald = act, violet = web, red = blocked).
          </div>
        </div>

        {/* Sections */}
        <div style={{
          marginTop: 28, position: "relative",
          display: "grid", gridTemplateColumns: "1fr 1fr", gap: "36px 40px",
        }}>

          {/* ─────── 1. Listening & speaking ───────
              Per design rule: voice-side bubbles intentionally OMIT a
              leading icon — the widget already morphs (bars, dots) so
              an inline glyph in the bubble would be redundant noise.
              Only action / recipe / blocked rows carry an icon. */}
          <Section title="Voice & answer" caption="Amber carries Handy's own voice — listening, transcribing, speaking back. No leading icons; the widget glyph already shows the activity.">

            <StateRow caption="Idle · widget at rest, no bubble">
              <WidgetMini state="idle" />
            </StateRow>

            <StateRow caption="Live voice transcript (as user speaks)">
              <BubblePair widget={<WidgetMini state="listening" />}>
                <SideBubble
                  tone="accent"
                  label={'"What\'s the weather in Tokyo?"'}
                  italic
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Spoken assistant response">
              <BubblePair widget={<WidgetMini state="idle" />}>
                <SideBubble
                  tone="accent"
                  label="Tap 'Storage', then 'Clear Cache'."
                  maxWidth={300}
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Thinking · model processing">
              <BubblePair widget={<WidgetMini state="thinking" />}>
                <SideBubble
                  tone="muted"
                  label="Thinking…"
                  small
                />
              </BubblePair>
            </StateRow>
          </Section>

          {/* ─────── 2. Tools ───────
              Per design rule: web-tool bubbles omit leading icons too.
              The provider IS the widget glyph (spinning arc + globe /
              GitHub mark), so duplicating it inside the bubble would
              read as visual noise. */}
          <Section title="Web tools" caption="Each provider gets its own ambient tone so the user knows where the data is coming from. No leading icons — the widget already shows the provider.">

            <StateRow caption="Searching the web · Brave">
              <BubblePair widget={<WidgetWebMini tone="violet" />}>
                <SideBubble tone="violet" label="Searching the web…" />
              </BubblePair>
            </StateRow>

            <StateRow caption="GitHub search">
              <BubblePair widget={<WidgetWebMini tone="violet" />}>
                <SideBubble
                  tone="violet"
                  label="Searching GitHub…"
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Reading a page · Jina">
              <BubblePair widget={<WidgetWebMini tone="honey" />}>
                <SideBubble
                  tone="honey"
                  label="Reading anthropic.com/news…"
                  prefix="Page · Jina"
                />
              </BubblePair>
            </StateRow>
          </Section>

          {/* ─────── 3. Navigation, pointing, acting ───────
              Per design rule: flying & pointing bubbles omit leading
              icons (the widget IS the cursor/pointer). Acting bubbles
              KEEP the icon — at execution time the visible glyph
              communicates *which* action is happening (tap vs type)
              independently of widget motion. */}
          <Section title="Navigation & action" caption="Blue is reserved for guidance, emerald for execution. Action rows show the gesture icon; flying/pointing rows don't — the widget already is the cursor.">

            <StateRow caption="Flying / landing near target">
              <BubblePair widget={<WidgetMini state="flying" />}>
                <SideBubble tone="point" label='Going to "Storage" →' />
              </BubblePair>
            </StateRow>

            <StateRow caption="Pointing at target · right-docked widget">
              <BubblePair side="right" widget={<WidgetMini state="pointing" />}>
                <SideBubble tail="right" tone="point" label='Tap "Storage"' />
              </BubblePair>
            </StateRow>

            <StateRow caption="Action in progress · tap">
              <BubblePair widget={<WidgetMini state="acting" />}>
                <SideBubble tone="act" leading="handTap" label='Tapping "Clear Cache"…'
                  trailingProgress={0.6} />
              </BubblePair>
            </StateRow>

            <StateRow caption="Action in progress · type">
              <BubblePair widget={<WidgetMini state="acting" />}>
                <SideBubble tone="act" leading="keyboard" label='Typing in "Search field"…'
                  trailingProgress={0.35} />
              </BubblePair>
            </StateRow>
          </Section>

          {/* ─────── 4. Decisions, edges, errors ─────── */}
          <Section title="Decisions & safety" caption="When Handy can't act unilaterally — ambiguous matches, blocked surfaces, mistakes.">

            <StateRow caption="Ambiguous target — pick one">
              <BubblePair widget={<WidgetMini state="pointing" />}>
                <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                  <SideBubble tone="point" label="3 matches for 'Storage'" small
                    prefix="Which one?" />
                  <div style={{ display: "flex", gap: 6, paddingLeft: 8 }}>
                    {["Internal", "SD card", "Cloud"].map((o, i) => (
                      <span key={i} style={{
                        padding: "5px 10px", borderRadius: 999,
                        background: i === 0 ? `${t.colors.point}22` : t.colors.surface,
                        border: `0.5px solid ${i === 0 ? t.colors.pointHair : t.colors.borderSubtle}`,
                        color: i === 0 ? t.colors.point : t.colors.textSecondary,
                        font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
                      }}>{o}</span>
                    ))}
                  </div>
                </div>
              </BubblePair>
            </StateRow>

            <StateRow caption="Wrong target · undo & re-pick">
              <BubblePair widget={<WidgetMini state="idle" />}>
                <SideBubble
                  tone="accent"
                  leading="back"
                  label="Wrong one? Tap to undo."
                  small
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Multi-step recipe progress">
              <BubblePair widget={<WidgetMini state="acting" />}>
                <SideBubble
                  tone="accent"
                  leading="recipe"
                  prefix="Step 2 of 5"
                  label="Open Alarms tab"
                  trailingProgress={2 / 5}
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Action blocked · incognito">
              <BubblePair widget={<WidgetMini state="idle" />}>
                <SideBubble
                  tone="danger"
                  leading="warning"
                  label="Blocked · Incognito mode"
                />
              </BubblePair>
            </StateRow>

            <StateRow caption="Failed · target gone">
              <BubblePair widget={<WidgetMini state="idle" />}>
                <SideBubble
                  tone="danger"
                  leading="warning"
                  prefix="Couldn't tap"
                  label="View is no longer visible. Try again?"
                  maxWidth={300}
                />
              </BubblePair>
            </StateRow>
          </Section>
        </div>

        {/* Small-screen footer demo */}
        <div style={{
          position: "absolute", left: 36, right: 36, bottom: 32,
          padding: "18px 22px",
          borderRadius: 18,
          background: "rgba(255,255,255,0.02)",
          border: `1px solid ${t.colors.borderSubtle}`,
        }}>
          <div style={{
            font: `600 11px/1 ${HANDY_TYPE.fontBody}`,
            letterSpacing: "0.14em", textTransform: "uppercase",
            color: t.colors.textMuted, marginBottom: 12,
          }}>Responsive · truncation at &lt; 320 dp wide</div>
          <div style={{ display: "flex", alignItems: "center", gap: 24 }}>
            <BubblePair widget={<WidgetMini state="acting" />}>
              <SideBubble tone="act" leading="handTap"
                label='Tapping "Clear Cache"…'
                trailingProgress={0.5}
                maxWidth={160} />
            </BubblePair>
            <div style={{ flex: 1 }} />
            <BubblePair side="right" widget={<WidgetMini state="pointing" />}>
              <SideBubble tail="right" tone="point" leading="cursor"
                label="Storage" maxWidth={120} />
            </BubblePair>
          </div>
        </div>
      </div>
    </ThemeProvider>
  );
}

function Section({ title, caption, children }) {
  const t = useTheme();
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 18 }}>
      <div>
        <div style={{
          font: `600 13px/1 ${HANDY_TYPE.fontDisplay}`,
          color: t.colors.textPrimary,
          letterSpacing: "-0.005em",
        }}>{title}</div>
        <div style={{
          font: `400 11.5px/1.5 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textMuted, marginTop: 4,
        }}>{caption}</div>
      </div>
      <div style={{ display: "flex", flexDirection: "column", gap: 22 }}>
        {children}
      </div>
    </div>
  );
}

function StateRow({ caption, children }) {
  const t = useTheme();
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ position: "relative", paddingLeft: 4 }}>{children}</div>
      <div style={{
        font: `400 10px/1.3 ${HANDY_TYPE.fontMono}`,
        color: t.colors.textMuted,
        letterSpacing: "0.04em",
        paddingLeft: 4,
      }}>{caption}</div>
    </div>
  );
}

window.BubbleStatesBoard = BubbleStatesBoard;
window.SideBubble = SideBubble;
