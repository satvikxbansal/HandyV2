// scenes-settings.jsx — Settings, single accordion variant.
//
// Design intent (May '26 refresh):
//   The old design listed all 10 capabilities as identical amber rows. That
//   read as a daunting wall of toggles. This redesign collapses every
//   setting into FOUR color-coded sections that map to user mental models:
//
//     1. AI Brain        amber    — model + API key (always-expanded card)
//     2. Capabilities    cobalt   — what Handy can see/hear/say
//     3. Automations     violet   — what Handy can do for you
//     4. Privacy & data  emerald  — controls, audit log, clearing data
//
//   Each accordion's expanded state shows ONLY actionable controls (toggles,
//   value pickers, navigation chevrons). No body copy is repeated — the
//   accordion's own subtitle already says what the section is for. A single
//   "What this does" affordance per row links to deeper detail when needed.
//
//   The full canvas artboard renders the entire page expanded so designers
//   can review every setting; the standard Pixel-9 artboard renders the
//   default first-load state (Capabilities expanded, others collapsed).

const SECTION_COLORS = {
  brain:       { key: "accent", tone: "amber"    },
  capabilities:{ key: "point",  tone: "cobalt"   },
  automations: { key: "violet", tone: "violet"   },
  privacy:     { key: "act",    tone: "emerald"  },
};

// ─── Header ─────────────────────────────────────────────────────────────

function SettingsHeader() {
  const t = useTheme();
  return (
    <div style={{
      padding: "10px 20px 16px",
      borderBottom: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <button style={{
          width: 40, height: 40, borderRadius: 12, border: "none",
          background: t.colors.surface, cursor: "pointer",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
        }}>
          <Illu name="back" size={18} color={t.colors.textPrimary} />
        </button>
        <div style={{
          font: `600 26px/1 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.022em",
          color: t.colors.textPrimary,
        }}>Settings</div>
      </div>
    </div>
  );
}

// ─── Footer ─────────────────────────────────────────────────────────────

function SettingsFooter() {
  const t = useTheme();
  return (
    <div style={{
      padding: "28px 20px 20px",
      display: "flex", flexDirection: "column", alignItems: "center", gap: 8,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, opacity: 0.45 }}>
        <Illu name="handOpen" size={18} color={t.colors.textMuted} />
        <span style={{
          font: `600 12px/1 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "0.18em", textTransform: "uppercase",
          color: t.colors.textMuted,
        }}>Handy</span>
      </div>
      <div style={{
        font: `400 10px/1 ${HANDY_TYPE.fontMono}`,
        color: t.colors.textMuted,
        letterSpacing: "0.10em", textTransform: "uppercase",
      }}>Version 0.1 · Made for Android</div>
    </div>
  );
}

// ─── Tile ───────────────────────────────────────────────────────────────
// The leading icon for each section, sized 44 dp with a tinted background.
function SectionTile({ illu, toneKey }) {
  const t = useTheme();
  const bg   = t.colors[`${toneKey}Soft`] || t.colors.accentSoft;
  const fg   = t.colors[toneKey] || t.colors.accent;
  return (
    <div style={{
      width: 44, height: 44, borderRadius: 12,
      background: bg,
      border: `0.5px solid ${fg}33`,
      display: "flex", alignItems: "center", justifyContent: "center",
      flex: "0 0 auto",
    }}>
      <Illu name={illu} size={22} color={fg} />
    </div>
  );
}

// ─── Section head ───────────────────────────────────────────────────────
// Tile + title + subtitle + chevron. Tapping toggles the accordion.
function SectionHead({ illu, toneKey, title, subtitle, open, rounded = "all" }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 14,
      padding: "16px 16px",
    }}>
      <SectionTile illu={illu} toneKey={toneKey} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `600 17px/1.2 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.012em",
          color: t.colors.textPrimary,
        }}>{title}</div>
        <div style={{
          font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary, marginTop: 2,
        }}>{subtitle}</div>
      </div>
      <div style={{
        flex: "0 0 auto",
        transform: open ? "rotate(180deg)" : "rotate(0deg)",
        transition: "transform 220ms ease-out",
      }}>
        <Illu name="chevron" size={14} color={t.colors.textMuted}
          style={{ transform: "rotate(90deg)" }} />
      </div>
    </div>
  );
}

// ─── Card shell ─────────────────────────────────────────────────────────
// Wraps a head + (optional) body. Single border, large radius.
function SectionCard({ children, accent, glow = false }) {
  const t = useTheme();
  return (
    <div style={{
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 18,
      overflow: "hidden",
      boxShadow: glow ? `0 0 0 1px ${accent}22, 0 8px 32px -16px ${accent}33` : "none",
    }}>{children}</div>
  );
}

// ─── Switch row — inside an expanded accordion ──────────────────────────
function SwitchRow({ title, on, trailing, last = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{
        flex: 1,
        font: `500 14px/1.2 ${HANDY_TYPE.fontBody}`,
        color: t.colors.textPrimary,
      }}>{title}</div>
      {trailing ? trailing : <Toggle on={on} />}
    </div>
  );
}

// ─── Navigation row — chevron-trailing, opens a sub-screen ──────────────
function NavRow({ title, value, danger = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "13px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
      cursor: "pointer",
    }}>
      <div style={{
        flex: 1,
        font: `500 14px/1.2 ${HANDY_TYPE.fontBody}`,
        color: danger ? t.colors.danger : t.colors.textPrimary,
      }}>{title}</div>
      {value && (
        <div style={{
          font: `400 13px/1 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textMuted,
        }}>{value}</div>
      )}
      <Illu name="chevron" size={12} color={t.colors.textMuted} />
    </div>
  );
}

// ─── Pill-select row (used for Triggers) ────────────────────────────────
function PillSelectRow({ title, options, last = false }) {
  const t = useTheme();
  return (
    <div style={{
      padding: "12px 16px 14px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{
        font: `500 14px/1.2 ${HANDY_TYPE.fontBody}`,
        color: t.colors.textPrimary, marginBottom: 10,
      }}>{title}</div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
        {options.map((o, i) => (
          <div key={i} style={{
            display: "inline-flex", alignItems: "center", gap: 6,
            padding: "6px 12px", borderRadius: 999,
            background: o.on ? t.colors.accentSoft : t.colors.surfaceElevated,
            border: `1px solid ${o.on ? t.colors.accentHairline : t.colors.borderSubtle}`,
            color: o.on ? t.colors.accent : t.colors.textSecondary,
            font: `500 12px/1 ${HANDY_TYPE.fontBody}`,
          }}>
            {o.label}
            {o.tag && (
              <span style={{
                font: `500 9px/1 ${HANDY_TYPE.fontBody}`,
                letterSpacing: "0.08em", textTransform: "uppercase",
                color: t.colors.textMuted,
              }}>· {o.tag}</span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ─── AI Brain card — always-expanded hero ───────────────────────────────
function BrainCard() {
  const t = useTheme();
  const accent = t.colors.accent;
  return (
    <SectionCard accent={accent} glow>
      {/* Head row */}
      <div style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "16px 16px 12px",
      }}>
        <SectionTile illu="brain" toneKey="accent" />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            display: "flex", alignItems: "center", gap: 6,
            font: `600 17px/1.2 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.012em",
            color: t.colors.textPrimary,
          }}>AI Brain
            <Illu name="sparkle" size={12} color={t.colors.accent} />
          </div>
          <div style={{
            font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textSecondary, marginTop: 2,
          }}>Claude Sonnet 4.5 · Anthropic</div>
        </div>
        <div style={{
          font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
          color: t.colors.accent,
        }}>Change</div>
      </div>

      {/* API key field */}
      <div style={{ padding: "4px 16px 8px" }}>
        <div style={{
          font: `500 10px/1 ${HANDY_TYPE.fontMono}`,
          letterSpacing: "0.12em", textTransform: "uppercase",
          color: t.colors.textMuted, marginBottom: 8,
        }}>Anthropic API key</div>
        <TextField value="sk-ant-api03-aBcD…xyz" masked trailing={
          <div style={{ display: "flex", gap: 2 }}>
            <IconButton name="eye"  size={16} />
            <IconButton name="copy" size={16} />
          </div>
        } />
      </div>

      {/* Status pill */}
      <div style={{
        display: "flex", alignItems: "center", gap: 8,
        padding: "10px 16px 16px",
      }}>
        <span style={{
          width: 8, height: 8, borderRadius: "50%",
          background: t.colors.success,
          boxShadow: `0 0 12px ${t.colors.success}77`,
        }} />
        <span style={{
          font: `600 13px/1 ${HANDY_TYPE.fontBody}`,
          color: t.colors.success,
        }}>Connected & Ready</span>
        <span style={{ flex: 1 }} />
        <span style={{
          font: `400 11px/1 ${HANDY_TYPE.fontMono}`,
          color: t.colors.textMuted,
        }}>2 req · today</span>
      </div>
    </SectionCard>
  );
}

// ─── Compact key field — reused for Brave/Jina/GitHub ──────────────────
function CompactKeyField({ provider, providerColor, label, placeholder, savedMasked, optional = false }) {
  const t = useTheme();
  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
        <span style={{
          width: 18, height: 18, borderRadius: 5,
          background: `${providerColor}22`,
          border: `0.5px solid ${providerColor}44`,
          color: providerColor,
          font: `700 10px/18px ${HANDY_TYPE.fontDisplay}`,
          textAlign: "center",
        }}>{provider}</span>
        <span style={{
          font: `500 11px/1 ${HANDY_TYPE.fontMono}`,
          color: t.colors.textMuted, letterSpacing: "0.10em", textTransform: "uppercase",
        }}>{label}</span>
        {optional && (
          <span style={{
            font: `500 9px/1 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textMuted, letterSpacing: "0.08em", textTransform: "uppercase",
            padding: "2px 6px", borderRadius: 999,
            background: "rgba(168,163,155,0.10)",
          }}>Optional</span>
        )}
      </div>
      <TextField value={savedMasked || ""} placeholder={placeholder} masked={!!savedMasked} trailing={
        <div style={{ display: "flex", gap: 2 }}>
          <IconButton name="eye"  size={16} />
          <IconButton name="copy" size={16} />
        </div>
      } />
    </div>
  );
}

// ─── Web Search row — toggle + Brave/Jina/GitHub nested key fields ─────
function WebSearchRow({ on = true }) {
  const t = useTheme();
  return (
    <>
      <SwitchRow title="Web search" on={on} />
      {on && (
        <div style={{
          padding: "12px 16px 14px",
          background: "rgba(59,130,246,0.04)",
          borderTop: `1px dashed ${t.colors.borderSubtle}`,
          display: "flex", flexDirection: "column", gap: 12,
        }}>
          <CompactKeyField
            provider="B" providerColor="#FB542B"
            label="Brave Search · API key"
            placeholder="Paste your key"
            savedMasked="BSAr_x9k2…7Qm"
          />
          <CompactKeyField
            provider="J" providerColor="#1AB394"
            label="Jina Reader"
            placeholder="Paste your key (optional)"
            optional
          />
          <CompactKeyField
            provider="G" providerColor="#9B85F5"
            label="GitHub Search"
            placeholder="Paste your token (optional)"
            optional
          />
          {/* status footer */}
          <div style={{
            display: "flex", alignItems: "center", gap: 6,
            font: `400 11px/1 ${HANDY_TYPE.fontBody}`, color: t.colors.textMuted,
            paddingTop: 2,
          }}>
            <span style={{
              width: 6, height: 6, borderRadius: "50%",
              background: t.colors.success,
              boxShadow: `0 0 8px ${t.colors.success}77`,
            }} />
            <span style={{ color: t.colors.success, fontWeight: 600 }}>Brave verified</span>
            <span>· Jina + GitHub raise rate limits</span>
          </div>
        </div>
      )}
    </>
  );
}

// ─── Capabilities section ───────────────────────────────────────────────
function CapabilitiesCard({ expanded = true, webSearchOn = true }) {
  const t = useTheme();
  const accent = t.colors.point;
  return (
    <SectionCard accent={accent} glow={expanded}>
      <SectionHead
        illu="sparkle"
        toneKey="point"
        title="Capabilities"
        subtitle="Voice, vision, and intelligence"
        open={expanded}
      />
      {expanded && (
        <>
          <SwitchRow title="Screen reading"  on={true}  />
          <SwitchRow title="Voice input"     on={true}  />
          <SwitchRow title="Notifications"   on={true}  />
          <WebSearchRow on={webSearchOn} />
          <SwitchRow title="Tutor mode"      on={false} />
        </>
      )}
    </SectionCard>
  );
}

// ─── Action button row — used for panic / destructive stops ────────────
function ActionRow({ title, subtitle, actionLabel, danger = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "13px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `500 14px/1.2 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textPrimary,
        }}>{title}</div>
        {subtitle && (
          <div style={{
            font: `400 11px/1.4 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textMuted, marginTop: 2,
          }}>{subtitle}</div>
        )}
      </div>
      <button style={{
        flex: "0 0 auto",
        padding: "7px 14px",
        borderRadius: 10, border: "none", cursor: "pointer",
        background: danger ? `${t.colors.danger}22` : t.colors.surfaceElevated,
        color: danger ? t.colors.danger : t.colors.textPrimary,
        font: `600 12px/1 ${HANDY_TYPE.fontBody}`,
      }}>{actionLabel}</button>
    </div>
  );
}

// ─── Disabled apps row — shows user denylist, tap restores ─────────────
function DisabledAppsRow({ apps = [] }) {
  const t = useTheme();
  return (
    <div style={{
      padding: "12px 16px 14px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        marginBottom: apps.length ? 10 : 0,
      }}>
        <div style={{
          font: `500 14px/1.2 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textPrimary,
        }}>Disabled apps</div>
        <div style={{
          font: `400 12px/1 ${HANDY_TYPE.fontMono}`,
          color: t.colors.textMuted,
        }}>{apps.length || "None"}</div>
      </div>
      {apps.length > 0 && (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {apps.map((a, i) => (
            <div key={i} style={{
              display: "flex", alignItems: "center", gap: 10,
              padding: "8px 12px",
              borderRadius: 10,
              background: t.colors.surfaceElevated,
              border: `0.5px solid ${t.colors.borderSubtle}`,
            }}>
              <div style={{
                width: 22, height: 22, borderRadius: 6,
                background: a.color || "rgba(168,163,155,0.20)",
                color: "#0a0a0c",
                font: `700 11px/22px ${HANDY_TYPE.fontDisplay}`,
                textAlign: "center",
              }}>{a.label[0]}</div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                  font: `500 13px/1.2 ${HANDY_TYPE.fontBody}`,
                  color: t.colors.textPrimary,
                  overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
                }}>{a.label}</div>
                <div style={{
                  font: `400 11px/1.3 ${HANDY_TYPE.fontMono}`,
                  color: t.colors.textMuted,
                }}>{a.pkg}</div>
              </div>
              <span style={{
                font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
                color: t.colors.point, cursor: "pointer",
              }}>Allow again</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ─── Automations section ────────────────────────────────────────────────
function AutomationsCard({ expanded = true }) {
  const t = useTheme();
  const accent = t.colors.violet;
  return (
    <SectionCard accent={accent} glow={expanded}>
      <SectionHead
        illu="cursor"
        toneKey="violet"
        title="Automations"
        subtitle="Taps, recipes, and triggers"
        open={expanded}
      />
      {expanded && (
        <>
          <SwitchRow title="Tap-for-me"  on={true}  />
          <SwitchRow title="Type-for-me" on={true}  />
          <SwitchRow title="Recipes"     on={true}  />
          <PillSelectRow title="Triggers" options={[
            { label: "Long-press widget", on: true },
            { label: "Volume-down hold",  on: false, tag: "Soon" },
            { label: "Hey Handy",         on: false, tag: "Soon" },
          ]} />
          <ActionRow
            title="Stop Tap-for-me for 1 hour"
            subtitle="Close the action gate without changing consent"
            actionLabel="Stop 1h"
          />
          <ActionRow
            title="Stop until I turn back on"
            subtitle="Disables Tap-for-me; chat still works"
            actionLabel="Stop"
            danger
          />
          <DisabledAppsRow apps={[
            { label: "Banking",   pkg: "com.chase.sig.android", color: "#1565C0aa" },
            { label: "Wallet",    pkg: "com.google.android.apps.walletnfcrel", color: "#34A853aa" },
          ]} />
        </>
      )}
    </SectionCard>
  );
}

// ─── Privacy & data section ─────────────────────────────────────────────
function PrivacyCard({ expanded = true }) {
  const t = useTheme();
  const accent = t.colors.act;
  return (
    <SectionCard accent={accent} glow={expanded}>
      <SectionHead
        illu="shield"
        toneKey="act"
        title="Privacy & data"
        subtitle="Controls, audit, and clearing data"
        open={expanded}
      />
      {expanded && (
        <>
          <SwitchRow title="Block in Incognito" on={true} />
          <SwitchRow title="Clipboard assist"   on={true} />
          <NavRow title="Activity log" value="48 entries" />
          <NavRow title="Clear chat history" danger />
        </>
      )}
    </SectionCard>
  );
}

// ════════════════════════════════════════════════════════════════════════
//  SETTINGS — single accordion variant.
//
//  `mode` controls what's expanded on load.
//    "default" → Capabilities open, others collapsed (standard mobile state)
//    "full"    → All four expanded (full review state)
// ════════════════════════════════════════════════════════════════════════

function SettingsAccordion({ mode = "default", height }) {
  const t = HANDY_TOKENS.amber;
  const full = mode === "full";

  // Heights tuned so every row fits without visual clipping.
  // Default: AI Brain + Capabilities (web search expanded with 3 keys) +
  //   two collapsed accordions + header + footer  ≈ 1180 dp.
  // Full:    every section expanded incl. disabled apps + 3 web keys ≈ 1880 dp.
  const phoneHeight = height || (full ? 1880 : 1180);

  return (
    <ThemeProvider theme="amber">
      <Phone height={phoneHeight}>
        <div style={{
          width: "100%", height: "100%",
          background: t.colors.pageBg,
          color: t.colors.textPrimary,
          fontFamily: HANDY_TYPE.fontBody,
          display: "flex", flexDirection: "column",
        }}>
          <SettingsHeader />

          <div style={{
            flex: 1,
            padding: "18px 16px 0",
            display: "flex", flexDirection: "column", gap: 14,
          }}>
            <BrainCard />
            <CapabilitiesCard expanded={true} />
            <AutomationsCard expanded={full} />
            <PrivacyCard      expanded={full} />
          </div>

          <SettingsFooter />
        </div>
      </Phone>
    </ThemeProvider>
  );
}

window.SettingsAccordion = SettingsAccordion;
