// Settings — reorganized with inline API keys per model

function SettingsScreen({ theme }) {
  const [selectedModel, setSelectedModel] = React.useState("sonnet");
  return (
    <div style={{
      position: "absolute", inset: 0,
      background: theme.pageBg,
      fontFamily: HANDY_FONT,
      color: theme.textPrimary,
      overflow: "auto",
    }}>
      <div style={{
        padding: "18px 20px 14px",
        display: "flex", alignItems: "center", gap: 12,
        borderBottom: `0.5px solid ${theme.divider}`,
        position: "sticky", top: 0,
        background: theme.pageBg,
        zIndex: 10,
      }}>
        <button style={{
          width: 34, height: 34, borderRadius: "50%",
          background: theme.chipBg,
          border: `0.5px solid ${theme.chipBorder}`,
          display: "flex", alignItems: "center", justifyContent: "center",
          cursor: "pointer",
        }}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
            <path d="m15 6-6 6 6 6" stroke={theme.textPrimary} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </button>
        <div style={{ fontSize: 20, fontWeight: 600, letterSpacing: -0.3 }}>Settings</div>
      </div>

      {/* Brain — model + inline key */}
      <Section theme={theme} icon={Icon.brain(18, theme.accent)} title="Brain" subtitle="Pick the model that powers Handy. Your key unlocks it.">
        <ModelCard theme={theme}
                   title="Claude Sonnet 4.5"
                   detail="Best reasoning · Anthropic"
                   selected={selectedModel === "sonnet"}
                   onSelect={() => setSelectedModel("sonnet")}
                   keyLabel="Anthropic API key"
                   keyValue="sk-ant-•••••••••••••••••••••••"
                   saved />
        <ModelCard theme={theme}
                   title="Claude Haiku 4.5"
                   detail="Faster · lower cost · Anthropic"
                   selected={selectedModel === "haiku"}
                   onSelect={() => setSelectedModel("haiku")}
                   keyLabel="Anthropic API key"
                   keyPlaceholder="Uses the same key as Sonnet"
                   reusesKey />
        <ModelCard theme={theme}
                   title="Gemini 2.5 Pro"
                   detail="Google · Coming soon"
                   disabled
                   keyLabel="Google AI Studio key"
                   keyPlaceholder="Paste when available" />
      </Section>

      {/* Modes */}
      <Section theme={theme} icon={Icon.modes(18, theme.accent)} title="Modes" subtitle="How Handy behaves in different situations">
        <ToggleRow theme={theme} title="Assistant" detail="General help & questions" on />
        <ToggleRow theme={theme} title="Tutor" detail="Explains as you go, nudges you" />
        <ToggleRow theme={theme} title="Focus" detail="Only reads — no actions" />
      </Section>

      {/* Triggers */}
      <Section theme={theme} icon={Icon.bolt(18, theme.accent)} title="Triggers" subtitle="When Handy wakes up">
        <ToggleRow theme={theme} title="Long-press floating widget" detail="Start voice capture" on />
        <ToggleRow theme={theme} title="Volume-down hold" detail="Global hotkey" on />
        <ToggleRow theme={theme} title='"Hey Handy"' detail="Hotword detection · uses more battery" />
      </Section>

      {/* Web tools — with nested keys */}
      <Section theme={theme} icon={Icon.globe(18, theme.accent)} title="Web Tools" subtitle="Let Handy search and fetch the open web">
        <ToggleRow theme={theme} title="Enable web search" detail="Claude can call web_search / fetch_page" on />
        <div style={{ paddingLeft: 8, marginTop: 4, display: "flex", flexDirection: "column", gap: 8 }}>
          <KeyField theme={theme} label="Brave Search" placeholder="Paste your key" />
          <KeyField theme={theme} label="Jina Reader" placeholder="Optional · raises rate limits" />
          <KeyField theme={theme} label="GitHub" placeholder="Optional · for code search" />
        </div>
      </Section>

      <div style={{ padding: "24px 20px 40px", textAlign: "center", color: theme.textMuted, fontSize: 11 }}>
        Handy · 2.0.1 · Made for Android
      </div>
    </div>
  );
}

function Section({ theme, icon, title, subtitle, children }) {
  return (
    <div style={{ padding: "22px 20px 4px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 4 }}>
        <div style={{
          width: 28, height: 28, borderRadius: 8,
          background: theme.accentSoft,
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>{icon}</div>
        <div style={{ fontSize: 16, fontWeight: 600, letterSpacing: -0.2 }}>{title}</div>
      </div>
      {subtitle && (
        <div style={{ fontSize: 12, color: theme.textSecondary, marginLeft: 38, marginBottom: 12, lineHeight: 1.45 }}>{subtitle}</div>
      )}
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>{children}</div>
    </div>
  );
}

// A model card — radio select + inline API key when selected
function ModelCard({ theme, title, detail, selected, disabled, onSelect, keyLabel, keyValue, keyPlaceholder, saved, reusesKey }) {
  return (
    <div style={{
      borderRadius: 16,
      background: selected ? theme.accentSoft : theme.chipBg,
      border: `0.5px solid ${selected ? theme.accent : theme.chipBorder}`,
      opacity: disabled ? 0.55 : 1,
      overflow: "hidden",
      transition: "all 200ms",
    }}>
      <div onClick={disabled ? null : onSelect}
           style={{
             padding: "14px 14px",
             display: "flex", alignItems: "center", gap: 12,
             cursor: disabled ? "not-allowed" : "pointer",
           }}>
        <div style={{
          width: 18, height: 18, borderRadius: "50%",
          border: `1.5px solid ${selected ? theme.accent : theme.textMuted}`,
          display: "flex", alignItems: "center", justifyContent: "center",
          flexShrink: 0,
        }}>
          {selected && (
            <div style={{ width: 8, height: 8, borderRadius: "50%", background: theme.accent }} />
          )}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 14, fontWeight: 600 }}>{title}</div>
          <div style={{ fontSize: 12, color: theme.textSecondary, marginTop: 1 }}>{detail}</div>
        </div>
        {saved && selected && (
          <div style={{
            padding: "0 9px", height: 24, borderRadius: 999,
            background: "rgba(111, 224, 179, 0.15)",
            color: theme.success, fontSize: 10.5, fontWeight: 600,
            display: "flex", alignItems: "center", gap: 4,
            textTransform: "uppercase", letterSpacing: 0.4,
          }}>
            {Icon.check(11, theme.success)} Ready
          </div>
        )}
      </div>

      {/* Inline key — shown when selected or if key needed */}
      {selected && !reusesKey && (
        <div style={{
          padding: "0 14px 14px",
          borderTop: `0.5px dashed ${theme.divider}`,
          paddingTop: 12,
        }}>
          <KeyField theme={theme} label={keyLabel} value={keyValue} placeholder={keyPlaceholder} saved={saved} inline />
        </div>
      )}
      {selected && reusesKey && (
        <div style={{
          padding: "10px 14px 14px",
          fontSize: 12, color: theme.textMuted,
          borderTop: `0.5px dashed ${theme.divider}`,
          display: "flex", alignItems: "center", gap: 8,
        }}>
          <span style={{ color: theme.success, display: "flex" }}>{Icon.check(12, theme.success)}</span>
          {keyPlaceholder}
        </div>
      )}
    </div>
  );
}

function ToggleRow({ theme, title, detail, on }) {
  return (
    <div style={{
      padding: "12px 14px",
      borderRadius: 14,
      background: theme.chipBg,
      border: `0.5px solid ${theme.chipBorder}`,
      display: "flex", alignItems: "center", gap: 12,
    }}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 14, fontWeight: 500 }}>{title}</div>
        <div style={{ fontSize: 12, color: theme.textSecondary, marginTop: 1 }}>{detail}</div>
      </div>
      <div style={{
        width: 40, height: 24, borderRadius: 999,
        background: on ? theme.accent : "rgba(255,255,255,0.12)",
        padding: 2, display: "flex", alignItems: "center",
        justifyContent: on ? "flex-end" : "flex-start",
        transition: "all 200ms",
      }}>
        <div style={{
          width: 20, height: 20, borderRadius: "50%",
          background: "#fff",
          boxShadow: "0 1px 2px rgba(0,0,0,0.25)",
        }} />
      </div>
    </div>
  );
}

function KeyField({ theme, label, value, placeholder, saved, inline }) {
  return (
    <div>
      {!inline && (
        <div style={{ fontSize: 12, color: theme.textSecondary, marginBottom: 6, fontWeight: 500 }}>{label}</div>
      )}
      {inline && (
        <div style={{ fontSize: 11, color: theme.textSecondary, marginBottom: 6, fontWeight: 500,
                      textTransform: "uppercase", letterSpacing: 0.4 }}>{label}</div>
      )}
      <div style={{
        padding: "0 4px 0 14px",
        height: 42,
        borderRadius: 12,
        background: "rgba(0,0,0,0.25)",
        border: `0.5px solid ${theme.chipBorder}`,
        display: "flex", alignItems: "center", gap: 6,
      }}>
        <div style={{
          flex: 1,
          fontSize: 13,
          color: value ? theme.textPrimary : theme.textMuted,
          fontFamily: value ? "ui-monospace, Menlo, monospace" : HANDY_FONT,
          overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap",
        }}>
          {value || placeholder}
        </div>
        <button style={{
          width: 30, height: 30, borderRadius: 8, border: "none",
          background: "transparent", cursor: "pointer",
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>{Icon.eye(14, theme.textSecondary)}</button>
        <button style={{
          width: 30, height: 30, borderRadius: 8, border: "none",
          background: "transparent", cursor: "pointer",
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>{Icon.copy(14, theme.textSecondary)}</button>
      </div>
    </div>
  );
}

Object.assign(window, { SettingsScreen });
