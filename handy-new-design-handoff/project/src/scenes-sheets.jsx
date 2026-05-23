// scenes-sheets.jsx — Glass confirmation sheets + audit log.

// ────────────────────────────────────────────────────────────────────────
//  TAP-FOR-ME confirmation sheet — on top of a host app
// ────────────────────────────────────────────────────────────────────────

function TapConfirmSheet() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        {/* host app fake — WhatsApp-ish */}
        <FakeWhatsApp />

        {/* dim layer */}
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.45)" }} />

        {/* widget hovering with pointing state */}
        <div style={{ position: "absolute", right: 20, bottom: 340, zIndex: 30 }}>
          <WidgetGlyph state="pointing" />
        </div>

        {/* glass sheet */}
        <div style={{
          position: "absolute", left: 0, right: 0, bottom: 0,
          background: "rgba(20,20,22,0.72)",
          backdropFilter: "blur(28px) saturate(160%)",
          WebkitBackdropFilter: "blur(28px) saturate(160%)",
          borderTopLeftRadius: 28, borderTopRightRadius: 28,
          borderTop: "0.5px solid rgba(255,255,255,0.20)",
          padding: "12px 20px 22px",
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
        }}>
          {/* handle */}
          <div style={{ width: 38, height: 4, borderRadius: 4, background: "rgba(255,255,255,0.20)", margin: "0 auto 16px" }} />

          {/* timeout countdown */}
          <div style={{ marginBottom: 14 }}>
            <div style={{
              width: "100%", height: 2, borderRadius: 2,
              background: "rgba(255,255,255,0.10)", overflow: "hidden",
            }}>
              <div style={{ width: "62%", height: "100%", background: t.colors.accent, opacity: 0.7 }} />
            </div>
            <div style={{
              ...typeStyle("caption", t), color: t.colors.textMuted,
              marginTop: 6, display: "flex", justifyContent: "space-between",
            }}>
              <span>Confirm within</span>
              <span style={{ fontFamily: HANDY_TYPE.fontMono }}>5.0 s</span>
            </div>
          </div>

          <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12,
              background: t.colors.accentSoft, color: t.colors.accent,
              display: "flex", alignItems: "center", justifyContent: "center",
              flex: "0 0 auto",
            }}>
              <Illu name="handTap" size={26} color={t.colors.accent} />
            </div>
            <div>
              <div style={typeStyle("title", t)}>Tap <span style={{ color: t.colors.accent, fontWeight: 600 }}>Continue</span> in WhatsApp?</div>
              <div style={{ ...typeStyle("caption", t), color: t.colors.textSecondary, marginTop: 6 }}>
                Handy will tap the visible <b>Continue</b> button. Cancel anytime.
              </div>
            </div>
          </div>

          {/* the target preview — small thumbnail of the button being tapped */}
          <div style={{
            marginTop: 16, padding: "10px 12px",
            borderRadius: 12,
            background: "rgba(255,255,255,0.06)",
            border: "0.5px solid rgba(255,255,255,0.14)",
            display: "flex", alignItems: "center", gap: 10,
          }}>
            <div style={{
              width: 28, height: 28, borderRadius: 8,
              background: "#25D366", color: "#0a0a0c",
              font: `700 12px/1 ${HANDY_TYPE.fontBody}`,
              display: "flex", alignItems: "center", justifyContent: "center",
            }}>W</div>
            <div style={{ flex: 1 }}>
              <div style={{ ...typeStyle("caption", t), color: t.colors.textMuted }}>Target</div>
              <div style={{ ...typeStyle("bodyStrong", t), fontSize: 13 }}>WhatsApp → Continue button</div>
            </div>
            <div style={{ ...typeStyle("caption", t), color: t.colors.textMuted, fontFamily: HANDY_TYPE.fontMono }}>x:312 y:840</div>
          </div>

          <div style={{ marginTop: 18, display: "flex", gap: 10 }}>
            <button style={{
              flex: 1, height: 48, borderRadius: 12,
              background: "transparent", border: "1px solid rgba(255,255,255,0.14)",
              color: t.colors.textSecondary,
              font: `500 15px/1 ${HANDY_TYPE.fontBody}`,
              cursor: "pointer",
            }}>Cancel</button>
            <button style={{
              flex: 1.4, height: 48, borderRadius: 12,
              background: t.colors.accent, border: "none",
              color: t.colors.accentInk,
              font: `600 15px/1 ${HANDY_TYPE.fontBody}`,
              cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
              boxShadow: `0 8px 22px -8px ${t.colors.accent}77`,
            }}>
              <Illu name="handTap" size={16} color={t.colors.accentInk} />
              Tap for me
            </button>
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function FakeWhatsApp() {
  // A muted simulation of a WhatsApp setup screen so the confirmation has
  // context. Solid surfaces, no logo — placeholder swatches.
  return (
    <div style={{ position: "absolute", inset: 0, background: "#0f1410", color: "#F4F2EE", fontFamily: HANDY_TYPE.fontBody }}>
      <div style={{ position: "absolute", top: 30, left: 24, right: 24 }}>
        <div style={{ font: `600 24px/1.2 ${HANDY_TYPE.fontDisplay}`, letterSpacing: "-0.015em" }}>
          Verify your<br />phone number
        </div>
        <div style={{ marginTop: 12, font: `400 14px/1.5 ${HANDY_TYPE.fontBody}`, color: "#A8A39B" }}>
          We'll send a verification code by SMS.
        </div>
        <div style={{
          marginTop: 28,
          padding: "10px 12px",
          borderBottom: "1.5px solid #25D366",
          font: `400 16px/1 ${HANDY_TYPE.fontBody}`,
        }}>+1 (555) 123-4567</div>
      </div>
      {/* fake CTA - the target */}
      <div style={{
        position: "absolute", left: 24, right: 24, top: 360,
        height: 48, borderRadius: 24,
        background: "#25D366", color: "#0a1410",
        display: "flex", alignItems: "center", justifyContent: "center",
        font: `600 15px/1 ${HANDY_TYPE.fontBody}`,
        boxShadow: "0 0 0 2px #D97757, 0 0 0 4px rgba(217,119,87,0.3)",
      }}>Continue</div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  RECIPE PLAN APPROVAL — multi-step preview
// ────────────────────────────────────────────────────────────────────────

function RecipeApprovalSheet() {
  const t = HANDY_TOKENS.amber;
  const steps = [
    { label: "Open the Clock app",                    sensitive: false },
    { label: "Go to Alarms tab",                      sensitive: false },
    { label: "Tap + to create new alarm",             sensitive: false },
    { label: "Set time to 7:00 AM",                   sensitive: true  },
    { label: "Save",                                  sensitive: false },
  ];

  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{ position: "absolute", inset: 0, background: "#08090B" }} />

        {/* dim */}
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.65)" }} />

        <div style={{
          position: "absolute", left: 0, right: 0, bottom: 0,
          background: "rgba(20,20,22,0.78)",
          backdropFilter: "blur(28px) saturate(160%)",
          WebkitBackdropFilter: "blur(28px) saturate(160%)",
          borderTopLeftRadius: 28, borderTopRightRadius: 28,
          borderTop: "0.5px solid rgba(255,255,255,0.20)",
          padding: "12px 20px 22px",
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
        }}>
          <div style={{ width: 38, height: 4, borderRadius: 4, background: "rgba(255,255,255,0.20)", margin: "0 auto 14px" }} />

          <div style={{ display: "flex", alignItems: "flex-start", gap: 14 }}>
            <div style={{
              width: 44, height: 44, borderRadius: 12,
              background: t.colors.accentSoft,
              display: "flex", alignItems: "center", justifyContent: "center", flex: "0 0 auto",
            }}>
              <Illu name="recipe" size={26} color={t.colors.accent} />
            </div>
            <div>
              <div style={typeStyle("title", t)}>
                Run <span style={{ color: t.colors.accent, fontWeight: 600 }}>Set a 7:00 AM alarm</span>?
              </div>
              <div style={{ ...typeStyle("caption", t), color: t.colors.textSecondary, marginTop: 6 }}>
                Handy will run these 5 steps. You'll see each tap before it happens. Cancel anytime.
              </div>
            </div>
          </div>

          {/* steps */}
          <div style={{ marginTop: 18, display: "flex", flexDirection: "column", gap: 10 }}>
            {steps.map((s, i) => (
              <div key={i} style={{
                display: "flex", alignItems: "center", gap: 12,
                padding: "10px 12px",
                background: "rgba(255,255,255,0.04)",
                border: "0.5px solid rgba(255,255,255,0.10)",
                borderRadius: 12,
              }}>
                <div style={{
                  width: 24, height: 24, borderRadius: "50%",
                  background: t.colors.surfaceElevated,
                  border: `1px solid ${t.colors.borderSubtle}`,
                  color: t.colors.textSecondary,
                  font: `500 11px/1 ${HANDY_TYPE.fontMono}`,
                  display: "flex", alignItems: "center", justifyContent: "center", flex: "0 0 auto",
                }}>{i + 1}</div>
                <div style={{ flex: 1, ...typeStyle("body", t), fontSize: 14 }}>{s.label}</div>
                {s.sensitive && <Pill label="Sensitive" kind="accent" />}
              </div>
            ))}
          </div>

          <div style={{ marginTop: 18, display: "flex", gap: 10 }}>
            <button style={{
              flex: 1, height: 48, borderRadius: 12,
              background: "transparent", border: "1px solid rgba(255,255,255,0.14)",
              color: t.colors.textSecondary,
              font: `500 15px/1 ${HANDY_TYPE.fontBody}`,
              cursor: "pointer",
            }}>Cancel</button>
            <button style={{
              flex: 1.4, height: 48, borderRadius: 12,
              background: t.colors.accent, border: "none",
              color: t.colors.accentInk,
              font: `600 15px/1 ${HANDY_TYPE.fontBody}`,
              cursor: "pointer",
              boxShadow: `0 8px 22px -8px ${t.colors.accent}77`,
            }}>Run plan</button>
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  AUDIT — list grouped by day, with a panic banner up top
// ────────────────────────────────────────────────────────────────────────

function AuditScreen() {
  const t = HANDY_TOKENS.amber;
  const today = [
    { app: "Maps",     action: "Tap-for-me", target: "Start navigation",       result: "dispatched", time: "2:14 PM" },
    { app: "Clock",    action: "Recipe",     target: "Set 7:00 AM alarm",      result: "dispatched", time: "1:02 PM", steps: 5 },
    { app: "Photos",   action: "Tap-for-me", target: "Share button (redacted)", result: "cancelled",  time: "12:48 PM" },
    { app: "WhatsApp", action: "Tap-for-me", target: "Continue",               result: "failed",     time: "11:30 AM", reason: "View no longer visible" },
  ];
  const yesterday = [
    { app: "Chrome",   action: "Web fetch",  target: "anthropic.com/news",     result: "dispatched", time: "8:21 PM" },
    { app: "Settings", action: "Type-for-me", target: "Search field (redacted)", result: "dispatched", time: "6:04 PM" },
  ];

  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          fontFamily: HANDY_TYPE.fontBody, color: t.colors.textPrimary,
          display: "flex", flexDirection: "column", overflow: "hidden",
        }}>
          {/* Header */}
          <div style={{ padding: "8px 16px 0" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 4, marginBottom: 8 }}>
              <IconButton name="back" size={18} />
              <div style={{ flex: 1 }} />
            </div>
            <div style={typeStyle("display", t)}>Activity</div>
            <div style={{ ...typeStyle("caption", t), color: t.colors.textSecondary, marginTop: 4 }}>
              Every action Handy took, with the screen redacted.
            </div>
          </div>

          {/* panic banner */}
          <div style={{ padding: "14px 16px 0" }}>
            <div style={{
              display: "flex", alignItems: "center", gap: 12,
              padding: "12px 14px", borderRadius: 14,
              background: t.colors.dangerSoft,
              border: `0.5px solid ${t.colors.danger}50`,
            }}>
              <Illu name="warning" size={20} color={t.colors.danger} />
              <div style={{ flex: 1, ...typeStyle("caption", t), color: t.colors.textPrimary }}>
                <b>Tap-for-me paused</b> until 5:47 PM
              </div>
              <span style={{
                ...typeStyle("caption", t), color: t.colors.danger,
                textDecoration: "underline", textUnderlineOffset: 2,
              }}>End now</span>
            </div>
          </div>

          {/* list */}
          <div style={{ flex: 1, overflowY: "hidden", padding: "20px 16px 0" }}>
            <DayHeader label="Today" />
            <div style={{ display: "flex", flexDirection: "column", gap: 8, marginBottom: 18 }}>
              {today.map((e, i) => <AuditRow key={i} entry={e} />)}
            </div>

            <DayHeader label="Yesterday" />
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {yesterday.map((e, i) => <AuditRow key={i} entry={e} />)}
            </div>
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function DayHeader({ label }) {
  const t = useTheme();
  return (
    <div style={{
      ...typeStyle("overline", t),
      color: t.colors.textMuted,
      padding: "0 4px 10px",
    }}>{label}</div>
  );
}

function AuditRow({ entry }) {
  const t = useTheme();
  const tone =
    entry.result === "dispatched" ? { bg: t.colors.successSoft, fg: t.colors.success, label: "Done" }
    : entry.result === "cancelled" ? { bg: "rgba(168,163,155,0.10)", fg: t.colors.textMuted, label: "Cancelled" }
    : { bg: t.colors.dangerSoft, fg: t.colors.danger, label: "Failed" };

  const illu =
    entry.action === "Recipe"     ? "recipe"
    : entry.action === "Web fetch" ? "globe"
    : entry.action === "Type-for-me" ? "keyboard"
    : "handTap";

  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 14px",
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 14,
    }}>
      <div style={{
        width: 32, height: 32, borderRadius: 9,
        background: tone.bg,
        display: "flex", alignItems: "center", justifyContent: "center", flex: "0 0 auto",
      }}>
        <Illu name={illu} size={18} color={tone.fg} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          ...typeStyle("bodyStrong", t), fontSize: 14,
          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
        }}>
          {entry.action} · {entry.target}
        </div>
        <div style={{ ...typeStyle("caption", t), color: t.colors.textMuted, marginTop: 2 }}>
          {entry.app} · {entry.time}{entry.reason ? ` · ${entry.reason}` : ""}{entry.steps ? ` · ${entry.steps} steps` : ""}
        </div>
      </div>
      <Pill label={tone.label} kind={
        entry.result === "dispatched" ? "success"
        : entry.result === "cancelled" ? "muted"
        : "danger"
      } />
    </div>
  );
}

window.TapConfirmSheet = TapConfirmSheet;
window.RecipeApprovalSheet = RecipeApprovalSheet;
window.AuditScreen = AuditScreen;

// ═══════════════════════════════════════════════════════════════════════
//  PRIVACY DISCLOSURE bottom sheet
//
//  Triggered from the "What Handy sees" link on the onboarding value page.
//  Full-height bottom sheet (covers ~92% of the screen). Four color-coded
//  sections — each is an eyebrow + display title + body. The "Won't do"
//  section uses a danger eyebrow and bulleted-with-red-minus items so the
//  no-promises read as commitments, not aspirations.
//
//  Closes via the X button, the drag handle pull, or the dim backdrop.
// ═══════════════════════════════════════════════════════════════════════

function PrivacyDisclosureSheet() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        {/* host backdrop — the value page beneath, dimmed */}
        <FakeValuePageBackdrop />
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.55)" }} />

        <DisclosureSheet />
      </Phone>
    </ThemeProvider>
  );
}

function DisclosureSheet() {
  const t = useTheme();
  return (
    <div style={{
      position: "absolute", left: 0, right: 0, bottom: 0, top: 60,
      background: t.colors.pageBg,
      borderTopLeftRadius: 24, borderTopRightRadius: 24,
      borderTop: "0.5px solid rgba(255,255,255,0.10)",
      boxShadow: "0 -20px 60px -20px rgba(0,0,0,0.5)",
      color: t.colors.textPrimary,
      fontFamily: HANDY_TYPE.fontBody,
      display: "flex", flexDirection: "column",
      overflow: "hidden",
    }}>
      {/* drag handle */}
      <div style={{ padding: "12px 0 4px", display: "flex", justifyContent: "center" }}>
        <div style={{ width: 38, height: 4, borderRadius: 4, background: "rgba(255,255,255,0.18)" }} />
      </div>

      {/* header */}
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "10px 20px 18px",
        borderBottom: `1px solid ${t.colors.borderSubtle}`,
      }}>
        <SheetTile illu="shieldFill" toneKey="act" />
        <div style={{
          flex: 1,
          font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.020em",
        }}>Privacy Disclosure</div>
        <CloseButton />
      </div>

      {/* scroll area */}
      <div style={{
        flex: 1, overflow: "hidden",
        padding: "22px 20px 16px",
        display: "flex", flexDirection: "column", gap: 28,
      }}>
        <DisclosureSection
          eyebrow="What Handy can read"
          eyebrowColor={t.colors.act}
          title="Active screen context"
          body="Handy uses Android's Accessibility Services to parse the text and layout of your current screen. This lets it understand what you're looking at and offer help in context."
        />

        <DisclosureSection
          eyebrow="Where data goes"
          eyebrowColor={t.colors.point}
          title="Direct to AI Brain"
          body={<>Your data travels directly from your device to Anthropic's servers using <b style={{ color: t.colors.textPrimary }}>your own API key</b>. Handy's developers never see your screen context or chat history.</>}
        />

        <DisclosureSection
          eyebrow="What Handy won't do"
          eyebrowColor={t.colors.danger}
          title="No silent monitoring"
          bullets={[
            "Will not store your screen snapshots locally.",
            "Will not record audio without your active hold.",
            "Will not share your data with 3rd-party advertisers.",
          ]}
        />

        <DisclosureSection
          eyebrow="Your controls"
          eyebrowColor={t.colors.accent}
          title="Always in charge"
          body="You can revoke any permission or clear all history at any time from system settings. Handy only acts when you trigger it — never on its own."
        />
      </div>

      {/* CTA — sits in safe area */}
      <div style={{
        padding: "12px 20px 22px",
        borderTop: `1px solid ${t.colors.borderSubtle}`,
      }}>
        <button style={{
          width: "100%", height: 52, borderRadius: 14,
          border: "none", cursor: "pointer",
          background: t.colors.surfaceElevated,
          color: t.colors.textPrimary,
          font: `600 16px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "-0.005em",
        }}>I understand</button>
      </div>
    </div>
  );
}

function DisclosureSection({ eyebrow, eyebrowColor, title, body, bullets }) {
  const t = useTheme();
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{
        font: `600 11px/1 ${HANDY_TYPE.fontBody}`,
        letterSpacing: "0.16em", textTransform: "uppercase",
        color: eyebrowColor,
      }}>{eyebrow}</div>
      <div style={{
        font: `600 22px/1.15 ${HANDY_TYPE.fontDisplay}`,
        letterSpacing: "-0.020em",
        color: t.colors.textPrimary,
      }}>{title}</div>
      {body && (
        <div style={{
          font: `400 14px/1.55 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
          marginTop: 2,
        }}>{body}</div>
      )}
      {bullets && (
        <div style={{ display: "flex", flexDirection: "column", gap: 10, marginTop: 6 }}>
          {bullets.map((b, i) => (
            <div key={i} style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
              <div style={{
                width: 18, height: 18, borderRadius: "50%",
                border: `1.5px solid ${t.colors.danger}`,
                display: "flex", alignItems: "center", justifyContent: "center",
                flex: "0 0 auto", marginTop: 2,
              }}>
                <span style={{
                  width: 8, height: 1.5, background: t.colors.danger,
                  borderRadius: 1,
                }} />
              </div>
              <div style={{
                font: `400 14px/1.55 ${HANDY_TYPE.fontBody}`,
                color: t.colors.textSecondary,
              }}>{b}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Shared tile — same look as the settings section tile (44 dp tinted square).
function SheetTile({ illu, toneKey }) {
  const t = useTheme();
  const bg = t.colors[`${toneKey}Soft`] || t.colors.accentSoft;
  const fg = t.colors[toneKey]            || t.colors.accent;
  return (
    <div style={{
      width: 44, height: 44, borderRadius: 12,
      background: bg,
      border: `0.5px solid ${fg}33`,
      display: "flex", alignItems: "center", justifyContent: "center",
      flex: "0 0 auto",
    }}>
      <Illu name={illu} size={24} color={fg} />
    </div>
  );
}

// Shared X button — 38 dp circular surface, matches both sheets.
function CloseButton() {
  const t = useTheme();
  return (
    <button style={{
      width: 38, height: 38, borderRadius: "50%",
      border: "none", cursor: "pointer", padding: 0,
      background: t.colors.surface,
      color: t.colors.textSecondary,
      display: "inline-flex", alignItems: "center", justifyContent: "center",
      flex: "0 0 auto",
    }}>
      <Illu name="close" size={16} color={t.colors.textSecondary} />
    </button>
  );
}

// Cheap value-page silhouette so the sheet has context behind it.
function FakeValuePageBackdrop() {
  return (
    <div style={{ position: "absolute", inset: 0, background: "#08090B" }} />
  );
}

// ═══════════════════════════════════════════════════════════════════════
//  MODEL PICKER bottom sheet
//
//  Triggered from "Change" on the AI Brain card in Settings. Lists every
//  supported model, grouped by provider. Each model card has provider mark,
//  name, capability summary, and a status pill. The currently-selected card
//  has an accent border + radio dot; others have neutral borders.
//
//  Coming-soon models are dimmed and use a muted status pill.
// ═══════════════════════════════════════════════════════════════════════

const MODEL_GROUPS = [
  {
    provider: "Anthropic",
    color: "#D97757",
    models: [
      { id: "sonnet-4-5", name: "Claude Sonnet 4.5",
        subtitle: "Best reasoning · context 200K", ready: true, selected: true,
        ctx: "200K", tags: ["reasoning", "vision"] },
      { id: "haiku-4-5",  name: "Claude Haiku 4.5",
        subtitle: "Faster · lower cost", ready: true,
        ctx: "200K", tags: ["fast", "cheap"] },
      { id: "opus-4",     name: "Claude Opus 4",
        subtitle: "Deep reasoning · slower", ready: true,
        ctx: "200K", tags: ["deepest"] },
    ],
  },
  {
    provider: "Google",
    color: "#7AA2F7",
    models: [
      { id: "gemini-2-5",  name: "Gemini 2.5 Pro",
        subtitle: "Google · long context", coming: true,
        ctx: "1M", tags: ["long-context"] },
    ],
  },
  {
    provider: "OpenAI",
    color: "#7FB069",
    models: [
      { id: "gpt-5", name: "GPT-5",
        subtitle: "OpenAI · multimodal", coming: true,
        ctx: "1M", tags: ["multimodal"] },
    ],
  },
];

function ModelPickerSheet() {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        {/* settings page silhouette behind, dimmed */}
        <FakeSettingsBackdrop />
        <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.55)" }} />

        <ModelPickerSheetBody />
      </Phone>
    </ThemeProvider>
  );
}

function ModelPickerSheetBody() {
  const t = useTheme();
  return (
    <div style={{
      position: "absolute", left: 0, right: 0, bottom: 0, top: 60,
      background: t.colors.pageBg,
      borderTopLeftRadius: 24, borderTopRightRadius: 24,
      borderTop: "0.5px solid rgba(255,255,255,0.10)",
      boxShadow: "0 -20px 60px -20px rgba(0,0,0,0.5)",
      color: t.colors.textPrimary,
      fontFamily: HANDY_TYPE.fontBody,
      display: "flex", flexDirection: "column",
      overflow: "hidden",
    }}>
      {/* drag handle */}
      <div style={{ padding: "12px 0 4px", display: "flex", justifyContent: "center" }}>
        <div style={{ width: 38, height: 4, borderRadius: 4, background: "rgba(255,255,255,0.18)" }} />
      </div>

      {/* header */}
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "10px 20px 16px",
        borderBottom: `1px solid ${t.colors.borderSubtle}`,
      }}>
        <SheetTile illu="brain" toneKey="accent" />
        <div style={{ flex: 1 }}>
          <div style={{
            font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.020em",
          }}>Choose your brain</div>
          <div style={{
            font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textSecondary,
            marginTop: 4,
          }}>Bring your own API key · runs on-device</div>
        </div>
        <CloseButton />
      </div>

      {/* model list */}
      <div style={{
        flex: 1, overflow: "hidden",
        padding: "18px 20px 16px",
        display: "flex", flexDirection: "column", gap: 22,
      }}>
        {MODEL_GROUPS.map((group) => (
          <div key={group.provider} style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <div style={{
              font: `600 11px/1 ${HANDY_TYPE.fontBody}`,
              letterSpacing: "0.18em", textTransform: "uppercase",
              color: t.colors.textMuted,
              padding: "0 2px",
            }}>{group.provider}</div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {group.models.map((m) => (
                <ModelCard key={m.id} model={m} providerColor={group.color} />
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* footer hint */}
      <div style={{
        padding: "14px 20px 22px",
        borderTop: `1px solid ${t.colors.borderSubtle}`,
        display: "flex", alignItems: "center", gap: 10,
      }}>
        <Illu name="sparkle" size={16} color={t.colors.accent} />
        <div style={{
          flex: 1,
          font: `400 12px/1.4 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
        }}>Switch any time. Each model uses its own API key.</div>
      </div>
    </div>
  );
}

function ModelCard({ model, providerColor }) {
  const t = useTheme();
  const isSelected = model.selected;
  const isComing = model.coming;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "14px 16px",
      borderRadius: 18,
      background: t.colors.surface,
      border: `1px solid ${isSelected ? t.colors.accent : t.colors.borderSubtle}`,
      boxShadow: isSelected ? `inset 0 0 0 1px ${t.colors.accent}, 0 0 32px -16px ${t.colors.accent}33` : "none",
      opacity: isComing ? 0.55 : 1,
      cursor: isComing ? "not-allowed" : "pointer",
    }}>
      {/* provider mark — colored disc with provider initial */}
      <div style={{
        width: 38, height: 38, borderRadius: 10,
        background: `${providerColor}22`,
        border: `0.5px solid ${providerColor}55`,
        color: providerColor,
        display: "flex", alignItems: "center", justifyContent: "center",
        font: `600 14px/1 ${HANDY_TYPE.fontDisplay}`,
        flex: "0 0 auto",
      }}>{model.name[0]}</div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `600 15px/1.2 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.010em",
          color: t.colors.textPrimary,
        }}>{model.name}</div>
        <div style={{
          display: "flex", alignItems: "center", gap: 6,
          marginTop: 4,
          font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
        }}>
          <span>{model.subtitle}</span>
        </div>
      </div>

      {/* trailing — radio dot OR coming-soon pill */}
      {isComing ? (
        <span style={{
          padding: "5px 10px", borderRadius: 999,
          background: "rgba(168,163,155,0.10)",
          color: t.colors.textMuted,
          font: `500 10px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.08em", textTransform: "uppercase",
          flex: "0 0 auto",
        }}>Soon</span>
      ) : (
        <div style={{
          width: 22, height: 22, borderRadius: "50%",
          border: `1.5px solid ${isSelected ? t.colors.accent : t.colors.borderStrong}`,
          display: "flex", alignItems: "center", justifyContent: "center",
          flex: "0 0 auto",
        }}>
          {isSelected && (
            <span style={{
              width: 12, height: 12, borderRadius: "50%",
              background: t.colors.accent,
            }} />
          )}
        </div>
      )}
    </div>
  );
}

// Cheap settings-page silhouette so the picker has context behind it.
function FakeSettingsBackdrop() {
  return (
    <div style={{ position: "absolute", inset: 0, background: "#08090B" }} />
  );
}

window.PrivacyDisclosureSheet = PrivacyDisclosureSheet;
window.ModelPickerSheet = ModelPickerSheet;
