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
function SwitchRow({ title, on, trailing, tone, last = false }) {
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
      {trailing ? trailing : <Toggle on={on} tone={tone} />}
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

// ─── Pill-select row (used for Triggers / Voice provider) ──────────────
// `tone` overrides the on-state colour so sections can keep their accent
// (e.g. Voice = honey).
function PillSelectRow({ title, options, tone, last = false }) {
  const t = useTheme();
  const onColor = tone ? (t.colors[tone] || t.colors.accent) : t.colors.accent;
  const onSoft  = tone ? (t.colors[`${tone}Soft`] || t.colors.accentSoft) : t.colors.accentSoft;
  const onHair  = tone ? (t.colors[`${tone}Hair`] || t.colors[`${tone}Hairline`] || onColor) : t.colors.accentHairline;
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
            background: o.on ? onSoft : t.colors.surfaceElevated,
            border: `1px solid ${o.on ? onHair : t.colors.borderSubtle}`,
            color: o.on ? onColor : t.colors.textSecondary,
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

// ─── Voice section ──────────────────────────────────────────────────────
//
// Sits below AI Brain as its own collapsible accordion. Honey-yellow
// accent (matches the warm family but distinct from Brain's amber).
//
// Two subsections live inside this single card:
//   1. Text-to-speech (volume-2 glyph) — how Handy speaks back. Gated by
//      the master "Speak voice replies aloud" toggle at the top.
//   2. Speech-to-text (mic-vocal glyph) — how Handy hears you. Always
//      active; no master toggle (handled by mic permission).
//
// Each subsection has its own provider picker, configuration controls,
// API key field (when applicable), and a status footer.
//
// Props:
//   tts: { provider: "system"|"sarvam", keyState: "empty"|"saved",
//          voice: "Ritu"|"Rahul"|"Simran", lang: "Auto"|... }
//   stt: { provider: "android"|"saarika", keyState: "empty"|"saved",
//          mode: "Auto"|"On-device"|"Network", lang: "System"|... }
//   speakOn: master toggle for TTS

function VoiceCard({
  expanded = true,
  speakOn = true,
  tts = { provider: "system", keyState: "empty", voice: "Ritu", lang: "Auto" },
  stt = { provider: "android", keyState: "empty", mode: "Auto", lang: "System" },
  ttsOpen = false,                  // nested TTS subsection open?
  sttOpen = false,                  // nested STT subsection open?
}) {
  const t = useTheme();
  const tone = "honey";
  const accent = t.colors[tone];

  const ttsSarvam = tts.provider === "sarvam";
  const ttsReady  = tts.provider === "system" || (ttsSarvam && tts.keyState === "saved");
  const sttSaarika = stt.provider === "saarika";
  const sttReady  = stt.provider === "android" || (sttSaarika && stt.keyState === "saved");

  // Card-level subtitle stays high-level — the subsection subheadings
  // carry the detailed state now. We just hint at provider mix.
  const ttsBit = !speakOn ? "Off"
              : tts.provider === "system" ? "System"
              : ttsReady ? "Sarvam" : "Sarvam (needs key)";
  const sttBit = stt.provider === "android" ? "Android"
              : sttReady ? "Saarika" : "Saarika (needs key)";
  const subtitle = `Speaks ${ttsBit} · hears ${sttBit}`;

  return (
    <SectionCard accent={accent} glow={expanded}>
      <SectionHead
        illu="audioLines"
        toneKey={tone}
        title="Voice"
        subtitle={subtitle}
        open={expanded}
      />

      {expanded && (
        <>
          {/* Master TTS toggle — applies only to "Handy speaks back" */}
          <SwitchRow
            title="Speak voice replies aloud"
            on={speakOn}
            tone={tone}
          />

          {/* ───────── Text-to-speech subsection ───────── */}
          <SubsectionHeader
            icon="volume2"
            tone={tone}
            title="Text-to-speech"
            subtitle={ttsSubtitle(tts, speakOn)}
            open={ttsOpen}
          />

          {ttsOpen && speakOn && (
            <>
              <PillSelectRow
                title="Voice provider"
                tone={tone}
                options={[
                  { label: "System", on: tts.provider === "system" },
                  { label: "Sarvam", on: ttsSarvam,
                    tag: ttsSarvam && tts.keyState !== "saved" ? "Add key" : null },
                ]}
              />

              {ttsSarvam && (
                <>
                  <PillSelectRow
                    title="Sarvam voice"
                    tone={tone}
                    options={[
                      { label: "Ritu",   on: tts.voice === "Ritu"   },
                      { label: "Rahul",  on: tts.voice === "Rahul"  },
                      { label: "Simran", on: tts.voice === "Simran" },
                    ]}
                  />
                  <PillSelectRow
                    title="Spoken language"
                    tone={tone}
                    options={[
                      { label: "Auto",     on: tts.lang === "Auto"     },
                      { label: "English",  on: tts.lang === "English"  },
                      { label: "Hindi",    on: tts.lang === "Hindi"    },
                      { label: "Hinglish", on: tts.lang === "Hinglish" },
                    ]}
                  />
                  <KeyFieldBlock
                    overline="Sarvam API key"
                    saved={tts.keyState === "saved"}
                    savedValue="sk-····fddd"
                    placeholder="Paste your Sarvam key"
                    helper={tts.keyState !== "saved"
                      ? "Add Sarvam key to use Bulbul. Handy falls back to System until a key is saved."
                      : null}
                  />
                  <TestVoiceRow disabled={tts.keyState !== "saved"} />
                </>
              )}

              <SubsectionStatus
                ready={ttsReady}
                emptyTone={tone}
                emptyLabel="Add Sarvam key to speak with Bulbul"
                readyLabel={tts.provider === "system" ? "Using System voice" : "Sarvam ready"}
              />
            </>
          )}
          {ttsOpen && !speakOn && (
            <SubsectionStatus
              ready={true}
              muted
              readyLabel="Replies are text-only"
            />
          )}

          {/* ───────── Speech-to-text subsection ───────── */}
          <SubsectionHeader
            icon="micVocal"
            tone={tone}
            title="Speech-to-text"
            subtitle={sttSubtitle(stt)}
            open={sttOpen}
          />

          {sttOpen && (
          <>
          <PillSelectRow
            title="Speech provider"
            tone={tone}
            options={[
              { label: "Android",        on: stt.provider === "android" },
              { label: "Sarvam Saarika", on: sttSaarika,
                tag: sttSaarika && stt.keyState !== "saved" ? "Add key" : null },
            ]}
          />

          {stt.provider === "android" && (
            <>
              <PillSelectRow
                title="STT mode"
                tone={tone}
                options={[
                  { label: "Auto",           on: stt.mode === "Auto" },
                  { label: "On-device only", on: stt.mode === "On-device" },
                  { label: "Network allowed", on: stt.mode === "Network" },
                ]}
              />
              <PillSelectRow
                title="Recognition language"
                tone={tone}
                options={[
                  { label: "System",   on: stt.lang === "System"   },
                  { label: "English",  on: stt.lang === "English"  },
                  { label: "Hindi",    on: stt.lang === "Hindi"    },
                  { label: "Hinglish", on: stt.lang === "Hinglish" },
                ]}
              />
              {stt.lang === "Hinglish" && (
                <HelperLine>
                  Hinglish enables Android's code-mix recognition where supported (Android 14+).
                </HelperLine>
              )}
            </>
          )}

          {sttSaarika && (
            <>
              <PillSelectRow
                title="Recognition language"
                tone={tone}
                options={[
                  { label: "Auto",     on: stt.lang === "Auto"     },
                  { label: "English",  on: stt.lang === "English"  },
                  { label: "Hindi",    on: stt.lang === "Hindi"    },
                  { label: "Hinglish", on: stt.lang === "Hinglish" },
                ]}
              />
              <KeyFieldBlock
                overline="Sarvam API key"
                saved={stt.keyState === "saved"}
                savedValue="sk-····fddd"
                placeholder="Paste your Sarvam key"
                helper={stt.keyState !== "saved"
                  ? "Add Sarvam key to transcribe with Saarika. Falls back to Android until saved."
                  : null}
              />
            </>
          )}

          <SubsectionStatus
            ready={sttReady}
            emptyTone={tone}
            emptyLabel="Add Sarvam key to transcribe with Saarika"
            readyLabel={stt.provider === "android" ? "Using Android speech recognition" : "Saarika ready"}
            last
          />
          </>
          )}
        </>
      )}
    </SectionCard>
  );
}

// Subtitle helpers — the inline grey caption next to each subheading.
// These compress the current state into one short phrase that the user
// can scan without expanding the subsection.
function ttsSubtitle(tts, speakOn) {
  if (!speakOn) return "Off · replies are text-only";
  if (tts.provider === "system") return "System voice";
  if (tts.keyState !== "saved")  return "Sarvam · needs key";
  return `Sarvam · ${tts.voice}`;
}
function sttSubtitle(stt) {
  if (stt.provider === "android") {
    return stt.mode === "On-device"
      ? "Android · on-device only"
      : stt.mode === "Network"
        ? "Android · network allowed"
        : "Android speech";
  }
  if (stt.keyState !== "saved") return "Sarvam Saarika · needs key";
  return "Sarvam Saarika";
}

function sttProviderName(p) {
  return p === "saarika" ? "Saarika" : "Android";
}

// ─── Subsection header — small, inline, used inside a SectionCard ──────
// Sits between rows; not a clickable accordion of its own. The icon tile
// is smaller (28 dp vs 44 dp for SectionTile) so it reads as a heading
// within the card, not a competing card.
// ─── Subsection header — small, inline, used inside a SectionCard ──────
// Renders as a tappable row with the chevron rotating to reflect open
// state. Subtitle sits inline to the right of the title in muted grey
// so the header reads as one cohesive line rather than a stack.
function SubsectionHeader({ icon, tone = "accent", title, subtitle, open = false }) {
  const t = useTheme();
  const c = t.colors[tone] || t.colors.accent;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: "14px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
      cursor: "pointer",
    }}>
      <Illu name={icon} size={16} color={c} style={{ flex: "0 0 auto" }} />
      <div style={{
        flex: 1, minWidth: 0,
        display: "flex", alignItems: "baseline", gap: 8,
        overflow: "hidden", whiteSpace: "nowrap", textOverflow: "ellipsis",
      }}>
        <span style={{
          font: `600 14px/1.2 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.008em",
          color: t.colors.textPrimary,
          flex: "0 0 auto",
        }}>{title}</span>
        {subtitle && (
          <span style={{
            font: `400 12px/1.2 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textMuted,
            overflow: "hidden", textOverflow: "ellipsis",
          }}>{subtitle}</span>
        )}
      </div>
      <div style={{
        flex: "0 0 auto",
        transform: open ? "rotate(180deg)" : "rotate(0deg)",
        transition: "transform 220ms ease-out",
      }}>
        <Illu name="chevron" size={12} color={t.colors.textMuted}
          style={{ transform: "rotate(90deg)" }} />
      </div>
    </div>
  );
}

// ─── Key field block — shared by TTS and STT API key sections ──────────
function KeyFieldBlock({ overline, saved, savedValue, placeholder, helper }) {
  const t = useTheme();
  return (
    <div style={{
      padding: "14px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      <div style={{
        font: `500 10px/1 ${HANDY_TYPE.fontMono}`,
        letterSpacing: "0.12em", textTransform: "uppercase",
        color: t.colors.textMuted, marginBottom: 8,
      }}>{overline}</div>
      <TextField
        value={saved ? savedValue : ""}
        placeholder={saved ? "" : placeholder}
        masked={saved}
        trailing={
          <div style={{ display: "flex", gap: 2 }}>
            <IconButton name="eye"  size={16} />
            <IconButton name="copy" size={16} />
          </div>
        }
      />
      {helper && (
        <div style={{
          marginTop: 10,
          font: `400 12px/1.45 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary,
        }}>{helper}</div>
      )}
    </div>
  );
}

// Helper inline caption (no row affordance).
function HelperLine({ children }) {
  const t = useTheme();
  return (
    <div style={{
      padding: "8px 16px 14px",
      font: `400 12px/1.45 ${HANDY_TYPE.fontBody}`,
      color: t.colors.textSecondary,
    }}>{children}</div>
  );
}

// Test voice row — title + subtitle on left, Speak button on right.
function TestVoiceRow({ disabled = false }) {
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
        }}>Test voice</div>
        <div style={{
          font: `400 11px/1.4 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textMuted, marginTop: 2,
        }}>Match the device language</div>
      </div>
      <button disabled={disabled} style={{
        flex: "0 0 auto",
        padding: "8px 16px",
        borderRadius: 12, border: "none",
        cursor: disabled ? "not-allowed" : "pointer",
        background: t.colors.surfaceElevated,
        color: disabled ? t.colors.textMuted : t.colors.textPrimary,
        font: `600 13px/1 ${HANDY_TYPE.fontBody}`,
        opacity: disabled ? 0.55 : 1,
      }}>Speak</button>
    </div>
  );
}

// Subsection status footer — per-subsection state line.
// `muted=true` skips the colored dot (used for "Replies are text-only").
function SubsectionStatus({ ready, emptyLabel, readyLabel, emptyTone = "accent", muted = false, last = false }) {
  const t = useTheme();
  const empty = t.colors[emptyTone] || t.colors.accent;
  const dot = ready ? t.colors.success : empty;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: last ? "12px 16px 14px" : "12px 16px",
      borderTop: `1px solid ${t.colors.borderSubtle}`,
    }}>
      {!muted && (
        <span style={{
          width: 7, height: 7, borderRadius: "50%",
          background: dot,
          boxShadow: `0 0 10px ${dot}77`,
        }} />
      )}
      <span style={{
        font: `${ready && !muted ? 600 : 500} 12px/1 ${HANDY_TYPE.fontBody}`,
        color: muted ? t.colors.textMuted : (ready ? t.colors.success : empty),
        flex: 1,
      }}>{ready ? readyLabel : emptyLabel}</span>
    </div>
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

function SettingsAccordion({
  mode = "default",
  height,
  tts = { provider: "system", keyState: "empty", voice: "Ritu", lang: "Auto" },
  stt = { provider: "android", keyState: "empty", mode: "Auto", lang: "System" },
  speakOn = true,
  ttsOpen = false,
  sttOpen = false,
}) {
  const t = HANDY_TOKENS.amber;
  const full = mode === "full";

  // Voice card height depends on what's expanded inside it. Both
  // subsections start collapsed — only their headers + footer rows
  // contribute. When opened, each adds 200-360dp depending on provider.
  const ttsBody  = ttsOpen ? (tts.provider === "sarvam" ? 460 : 140) : 0;
  const sttBody  = sttOpen ? (stt.provider === "saarika" ? 380 : 280) : 0;
  const voiceTall = 360 + ttsBody + sttBody;
  const baseHeight = 720;
  const phoneHeight = height || (
    full ? Math.max(2150, baseHeight + voiceTall + 720)
         : baseHeight + voiceTall + 220
  );

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
            <VoiceCard
              expanded={true}
              speakOn={speakOn}
              tts={tts}
              stt={stt}
              ttsOpen={ttsOpen}
              sttOpen={sttOpen}
            />
            <CapabilitiesCard expanded={!full ? false : true} />
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
