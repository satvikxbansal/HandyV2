// scenes-chat.jsx — Full-app chat surfaces (Ready, Active, Reduced-mode).

function ChatTopBar({ live = true, hasBack = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", justifyContent: "space-between",
      padding: "16px 18px 14px",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        {hasBack && <Illu name="back" size={20} color={t.colors.textSecondary} />}
        <HandyWordmark size={18} markSize={22} />
        {live && (
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginLeft: 4 }}>
            <LiveDot size={6} />
            <span style={{
              font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
              color: t.colors.accent, letterSpacing: "0.06em", textTransform: "uppercase",
            }}>Live</span>
          </div>
        )}
      </div>
      <div style={{ display: "flex", alignItems: "center", gap: 4 }}>
        <IconButton name="expand"   size={18} />
        <IconButton name="settings" size={18} />
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  CHAT — READY (empty state)
//
//  Layout (top → bottom):
//    1. Hand-mark in a subtle bare disc (76 dp), centered
//    2. Title "Ready when you are" — 32 sp display
//    3. Subtitle — body secondary, two lines
//    4. 2×2 grid of color-coded quick prompts:
//         · Sparkle    (amber)   Summarize this screen
//         · Camera     (cobalt)  What's in this photo?
//         · Timer      (violet)  Set a 10-min timer
//         · Globe      (honey)   Look this up online
//    5. Composer pinned to bottom
// ────────────────────────────────────────────────────────────────────────
function ChatEmpty() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
          display: "flex", flexDirection: "column",
        }}>
          <ChatTopBar />

          {/* hero stack */}
          <div style={{
            flex: 1, padding: "8px 24px 16px",
            display: "flex", flexDirection: "column", alignItems: "center",
          }}>
            {/* Hand on a subtle bare disc — reads as the brand mark resting */}
            <div style={{
              marginTop: 16,
              width: 96, height: 96, borderRadius: "50%",
              background: t.colors.surface,
              border: `0.5px solid ${t.colors.borderSubtle}`,
              display: "flex", alignItems: "center", justifyContent: "center",
              boxShadow: `inset 0 0 24px 0 ${t.colors.accent}18`,
            }}>
              <Illu name="handOpen" size={44} color={t.colors.accent} />
            </div>

            {/* Title */}
            <div style={{
              marginTop: 28,
              textAlign: "center",
              font: `600 32px/1.06 ${HANDY_TYPE.fontDisplay}`,
              letterSpacing: "-0.026em",
              color: t.colors.textPrimary,
            }}>Ready when you are</div>

            {/* 2×2 quick prompts — each tinted differently */}
            <div style={{
              marginTop: 28, width: "100%",
              display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10,
            }}>
              <QuickPromptCard illu="sparkle" tone="accent" label="Summarize this screen" />
              <QuickPromptCard illu="camera"  tone="act"    label="What's in this photo?" />
              <QuickPromptCard illu="timer"   tone="violet" label="Set a 10-min timer" />
              <QuickPromptCard illu="globe"   tone="honey"  label="Look this up online" />
            </div>
          </div>

          <FloatingComposer />
        </div>
      </Phone>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  ContextBar — "CHATTING ABOUT <app>" pill with a Change CTA
//
//  Two variants:
//    "full"  → large card under the top bar; eyebrow + app name in display
//    "pill"  → compact chip sitting just above the floating composer
//
//  `iconTone` picks the leading icon disc color (we use cobalt by default
//  to imply "vision/context").
// ────────────────────────────────────────────────────────────────────────
function ContextBar({ app = "Play Store", variant = "full", iconTone = "point", icon = "eye" }) {
  const t = useTheme();
  const accent = t.colors[iconTone] || t.colors.point;
  const accentSoft = t.colors[`${iconTone}Soft`] || t.colors.pointSoft;

  if (variant === "pill") {
    return (
      <div style={{
        display: "inline-flex", alignItems: "center", gap: 10,
        padding: "8px 14px 8px 8px",
        borderRadius: 999,
        background: "rgba(24,26,31,0.78)",
        border: `0.5px solid rgba(255,255,255,0.12)`,
        backdropFilter: "blur(20px) saturate(160%)",
        WebkitBackdropFilter: "blur(20px) saturate(160%)",
        alignSelf: "center",
      }}>
        <div style={{
          width: 22, height: 22, borderRadius: "50%",
          background: accentSoft,
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          <Illu name={icon} size={12} color={accent} />
        </div>
        <span style={{
          font: `400 12px/1 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
        }}>Chatting about <b style={{ color: t.colors.textPrimary, fontWeight: 600 }}>{app}</b></span>
        <span style={{
          font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
          color: accent, cursor: "pointer",
        }}>Change</span>
      </div>
    );
  }

  // "full" variant — the inspiration design
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "12px 14px 12px 12px",
      borderRadius: 18,
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{
        width: 40, height: 40, borderRadius: "50%",
        background: accentSoft,
        border: `0.5px solid ${accent}33`,
        display: "flex", alignItems: "center", justifyContent: "center",
        flex: "0 0 auto",
      }}>
        <Illu name={icon} size={18} color={accent} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `600 10px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.16em", textTransform: "uppercase",
          color: t.colors.textMuted,
        }}>Chatting about</div>
        <div style={{
          marginTop: 4,
          font: `600 15px/1.2 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.010em",
          color: t.colors.textPrimary,
        }}>{app}</div>
      </div>
      <button style={{
        width: 32, height: 32, borderRadius: "50%",
        border: "none", cursor: "pointer",
        background: t.colors.surfaceElevated,
        color: t.colors.textSecondary,
        display: "inline-flex", alignItems: "center", justifyContent: "center",
        flex: "0 0 auto",
      }}>
        <Illu name="close" size={14} color={t.colors.textSecondary} />
      </button>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  FloatingComposer — translucent input pinned above the chat surface
//
//  Sits absolute-positioned at the bottom of the chat content area with
//  a backdrop blur. Messages scroll under it. A short gradient fade is
//  drawn above the composer so content visible behind it doesn't read as
//  cut-off — it dissolves cleanly.
// ────────────────────────────────────────────────────────────────────────
function FloatingComposer({ placeholder = "Ask Handy anything…", bottomChrome }) {
  const t = useTheme();
  return (
    <>
      {/* gradient fade so messages dissolve into the composer area */}
      <div style={{
        position: "absolute", left: 0, right: 0,
        bottom: bottomChrome ? 132 : 86,
        height: 36, pointerEvents: "none", zIndex: 4,
        background: `linear-gradient(to top, ${t.colors.pageBg} 0%, transparent 100%)`,
      }} />

      <div style={{
        position: "absolute", left: 16, right: 16,
        bottom: 20, zIndex: 5,
        display: "flex", flexDirection: "column", gap: 10,
        alignItems: "stretch",
      }}>
        {bottomChrome && (
          <div style={{ display: "flex", justifyContent: "center" }}>{bottomChrome}</div>
        )}
        <div style={{
          display: "flex", alignItems: "center", gap: 10,
          padding: 8,
          borderRadius: 28,
          background: "rgba(24,26,31,0.65)",
          border: "0.5px solid rgba(255,255,255,0.12)",
          backdropFilter: "blur(28px) saturate(160%)",
          WebkitBackdropFilter: "blur(28px) saturate(160%)",
          boxShadow: "0 12px 32px -16px rgba(0,0,0,0.55)",
        }}>
          <button style={{
            width: 40, height: 40, borderRadius: "50%",
            background: t.colors.accentSoft, border: "none", cursor: "pointer",
            display: "flex", alignItems: "center", justifyContent: "center", flex: "0 0 auto",
          }}>
            <Illu name="mic" size={18} color={t.colors.accent} />
          </button>
          <div style={{
            flex: 1, minWidth: 0,
            color: t.colors.textMuted,
            font: `400 15px/1 ${HANDY_TYPE.fontBody}`,
          }}>{placeholder}</div>
          <button style={{
            width: 40, height: 40, borderRadius: "50%",
            background: t.colors.accent, border: "none", cursor: "pointer",
            display: "flex", alignItems: "center", justifyContent: "center", flex: "0 0 auto",
          }}>
            <Illu name="send" size={16} color={t.colors.accentInk} />
          </button>
        </div>
      </div>
    </>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  CHAT — ACTIVE conversation
//
//  `contextBarPosition` controls where the "Chatting about <app>" indicator
//  sits — "top" pins a full card below the top bar; "bottom" floats a slim
//  pill just above the composer. Pass null to hide.
// ────────────────────────────────────────────────────────────────────────
function ChatActive({ contextBarPosition = "top", app = "Google Maps" } = {}) {
  const t = HANDY_TOKENS.amber;
  const showTop = contextBarPosition === "top";
  const showBottom = contextBarPosition === "bottom";

  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
          display: "flex", flexDirection: "column", position: "relative",
        }}>
          <ChatTopBar />

          {showTop && (
            <div style={{ padding: "0 16px 4px" }}>
              <ContextBar app={app} variant="full" />
            </div>
          )}

          {/* messages — scroll under the floating composer, last bubble has
              ~140 dp bottom padding so it doesn't hide beneath it */}
          <div style={{
            flex: 1, overflowY: "hidden",
            padding: "10px 18px 140px",
            display: "flex", flexDirection: "column", gap: 14,
          }}>
            {/* day separator */}
            <div style={{
              alignSelf: "center",
              ...typeStyle("caption", t), color: t.colors.textMuted,
              padding: "4px 10px", borderRadius: 999, background: t.colors.surface,
              border: `1px solid ${t.colors.borderSubtle}`,
            }}>Today · 2:14 PM</div>

            <Bubble side="user" text="What does this Maps screen say about the next turn?" />

            <BubbleHandy>
              <div style={{ ...typeStyle("caption", t), color: t.colors.textMuted, display: "flex", alignItems: "center", gap: 6, marginBottom: 6 }}>
                <Illu name="eye" size={12} color={t.colors.textMuted} /> read 1 screen · {app}
              </div>
              <div style={typeStyle("body", t)}>
                Turn <b>right onto Valencia St</b> in <b>0.2 mi</b>. After that, continue 1.4 mi to your destination.
              </div>
            </BubbleHandy>

            <Bubble side="user" text="Cool. Tap 'Start' for me." />

            <BubbleHandy>
              <div style={{
                display: "flex", alignItems: "center", gap: 10,
                padding: 12, borderRadius: 14,
                background: t.colors.accentSoft,
                border: `1px solid ${t.colors.accentHairline}`,
              }}>
                <div style={{
                  width: 30, height: 30, borderRadius: 8,
                  background: t.colors.surfaceElevated,
                  display: "flex", alignItems: "center", justifyContent: "center",
                }}>
                  <Illu name="handTap" size={18} color={t.colors.accent} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ ...typeStyle("bodyStrong", t), fontSize: 13 }}>Tap "Start" in Maps</div>
                  <div style={{ ...typeStyle("caption", t), fontSize: 11, color: t.colors.textMuted }}>Bounded action · expires in 8s</div>
                </div>
                <div style={{
                  padding: "6px 12px", borderRadius: 10,
                  background: t.colors.accent, color: t.colors.accentInk,
                  font: `600 12px/1 ${HANDY_TYPE.fontBody}`,
                }}>Tap for me</div>
              </div>
            </BubbleHandy>

            <Bubble side="user" text="Also remind me in 5 minutes to look at the chart." />

            <BubbleHandy>
              <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <Thinking />
                <span style={{ ...typeStyle("caption", t), color: t.colors.textSecondary }}>Thinking…</span>
              </div>
            </BubbleHandy>
          </div>

          <FloatingComposer
            placeholder="Reply to Handy…"
            bottomChrome={showBottom ? <ContextBar app={app} variant="pill" /> : null}
          />
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function Bubble({ side, text }) {
  const t = useTheme();
  const isUser = side === "user";
  return (
    <div style={{
      alignSelf: isUser ? "flex-end" : "flex-start",
      maxWidth: "82%",
      padding: "10px 14px",
      borderRadius: 18,
      background: isUser ? t.colors.surfaceElevated : "transparent",
      border: isUser ? `1px solid ${t.colors.borderSubtle}` : "none",
      color: t.colors.textPrimary,
      ...typeStyle("body", t),
      borderBottomRightRadius: isUser ? 6 : 18,
    }}>{text}</div>
  );
}

function BubbleHandy({ children }) {
  const t = useTheme();
  return (
    <div style={{ alignSelf: "flex-start", display: "flex", gap: 10, maxWidth: "90%" }}>
      <div style={{ flex: "0 0 auto", paddingTop: 2 }}>
        <HandMark variant="open" container="bare" size={20} showWaveLines={false} />
      </div>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  );
}

function Thinking() {
  return (
    <div style={{ display: "flex", gap: 4 }}>
      {[0, 1, 2].map((i) => (
        <div key={i} style={{
          width: 6, height: 6, borderRadius: "50%", background: "#D97757",
          animation: `handy-livedot 1.2s ease-in-out infinite`,
          animationDelay: `${i * 0.18}s`,
          opacity: 0.85,
        }} />
      ))}
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  CHAT — REDUCED MODE banner
// ────────────────────────────────────────────────────────────────────────
function ChatReduced() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
          display: "flex", flexDirection: "column", position: "relative",
        }}>
          <ChatTopBar live={false} />

          {/* Reduced-mode banner */}
          <div style={{ padding: "4px 16px 0" }}>
            <div style={{
              display: "flex", alignItems: "center", gap: 10,
              padding: "12px 14px", borderRadius: 14,
              background: t.colors.accentSoft,
              border: `0.5px solid ${t.colors.accentHairline}`,
            }}>
              <Illu name="eye_off" size={18} color={t.colors.accent} style={{ flex: "0 0 auto" }} />
              <div style={{ flex: 1, ...typeStyle("caption", t), color: t.colors.textPrimary }}>
                Accessibility is off. Handy can chat but can't see your screen.
              </div>
              <span style={{
                ...typeStyle("caption", t), color: t.colors.accent,
                textDecoration: "underline", textUnderlineOffset: 2, flex: "0 0 auto",
              }}>Enable</span>
            </div>
          </div>

          {/* hero, dimmed */}
          <div style={{
            flex: 1, padding: "16px 24px 140px",
            display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", textAlign: "center",
          }}>
            <Illu name="handOpen" size={120} color={t.colors.accent} opacity={0.85} />
            <div style={{ marginTop: 24, ...typeStyle("title", t), fontSize: 24, lineHeight: "28px" }}>
              I can still chat.
            </div>
            <div style={{ marginTop: 8, ...typeStyle("caption", t), color: t.colors.textSecondary, maxWidth: 280 }}>
              Without accessibility, I can't see your screen — but ask me anything and I'll help.
            </div>

            <div style={{ marginTop: 28, width: "100%", display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <QuickPromptCard illu="ask"   tone="accent" label="Ask me a question" />
              <QuickPromptCard illu="globe" tone="honey"  label="Search the web" />
            </div>
          </div>

          <FloatingComposer />
        </div>
      </Phone>
    </ThemeProvider>
  );
}

window.ChatEmpty = ChatEmpty;
window.ChatActive = ChatActive;
window.ChatReduced = ChatReduced;
window.ChatTopBar = ChatTopBar;
