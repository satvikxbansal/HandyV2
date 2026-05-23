// primitives.jsx — Reusable UI atoms for Handy.
//
// Each atom is annotated with the suggested Compose composable name (and key
// param) in a comment header — so when this canvas hits the Android agent
// the mapping is obvious. e.g.:
//
//   Atom: <PrimaryButton label="Get started" trailingIcon="chevron" />
//   Compose: @Composable fun PrimaryButton(label: String, onClick: () -> Unit,
//            trailingIcon: ImageVector? = null, enabled: Boolean = true)
//

// ───── Buttons ───────────────────────────────────────────────────────────
// Compose: PrimaryButton
function PrimaryButton({ label, trailingIcon = "chevron", disabled = false, full = true, onClick, style }) {
  const t = useTheme();
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
        width: full ? "100%" : "auto",
        height: 52, padding: "0 22px",
        borderRadius: 14, border: "none", cursor: disabled ? "default" : "pointer",
        background: disabled ? t.colors.surface : t.colors.accent,
        color: disabled ? t.colors.textMuted : t.colors.accentInk,
        boxShadow: disabled ? "none" : `0 8px 24px -10px ${t.colors.accent}66`,
        font: `600 16px/1 ${HANDY_TYPE.fontBody}`,
        letterSpacing: "-0.005em",
        ...style,
      }}>
      {label}
      {trailingIcon === "chevron" && !disabled && (
        <Illu name="chevron" size={16} color={t.colors.accentInk} />
      )}
    </button>
  );
}

// Compose: SecondaryTextButton
function SecondaryTextButton({ label, onClick, style }) {
  const t = useTheme();
  return (
    <button onClick={onClick} style={{
      background: "transparent", border: "none", cursor: "pointer",
      color: t.colors.textSecondary,
      font: `500 15px/1 ${HANDY_TYPE.fontBody}`,
      height: 48, padding: "0 12px",
      ...style,
    }}>{label}</button>
  );
}

// Compose: DestructiveButton
function DestructiveButton({ label, onClick, style }) {
  const t = useTheme();
  return (
    <button onClick={onClick} style={{
      background: "transparent",
      border: `1px solid ${t.colors.danger}55`,
      color: t.colors.danger,
      borderRadius: 12,
      font: `500 14px/1 ${HANDY_TYPE.fontBody}`,
      padding: "12px 16px",
      cursor: "pointer",
      ...style,
    }}>{label}</button>
  );
}

// ───── Status pill ────────────────────────────────────────────────────────
// Compose: StatusPill(text: String, kind: PillKind)
function Pill({ label, kind = "muted", style }) {
  const t = useTheme();
  const map = {
    success: { bg: t.colors.successSoft, fg: t.colors.success },
    accent:  { bg: t.colors.accentSoft,  fg: t.colors.accent  },
    muted:   { bg: "rgba(168,163,155,0.10)", fg: t.colors.textMuted },
    danger:  { bg: t.colors.dangerSoft,  fg: t.colors.danger  },
  };
  const c = map[kind] || map.muted;
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", gap: 4,
      height: 24, padding: "0 10px", borderRadius: 999,
      background: c.bg, color: c.fg,
      font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
      letterSpacing: "0.06em", textTransform: "uppercase",
      ...style,
    }}>{label}</span>
  );
}

// ───── Toggle switch ──────────────────────────────────────────────────────
// Compose: HandySwitch(checked: Boolean, onCheckedChange)
function Toggle({ on = false, disabled = false }) {
  const t = useTheme();
  return (
    <div style={{
      width: 44, height: 26, borderRadius: 13,
      background: on
        ? (disabled ? t.colors.accentSoft : t.colors.accent)
        : t.colors.surfaceElevated,
      border: on ? "none" : `1px solid ${t.colors.borderSubtle}`,
      position: "relative",
      transition: "background 240ms ease-out",
      opacity: disabled ? 0.55 : 1,
      flex: "0 0 auto",
    }}>
      <div style={{
        position: "absolute",
        top: 3, left: on ? 21 : 3,
        width: 20, height: 20, borderRadius: "50%",
        background: on ? "#FFFFFF" : t.colors.textMuted,
        boxShadow: on ? "0 2px 4px rgba(0,0,0,0.2)" : "none",
        transition: "left 240ms cubic-bezier(0.34, 1.2, 0.5, 1)",
      }} />
    </div>
  );
}

// ───── Row — the standard card row (icon + title + caption + trailing) ────
// Compose: SettingsRow / PermissionRow / CapabilityRow (depending on context)
function Row({
  illu,             // illustration name OR custom <Illu/> element
  tileTone = "accent",  // accent | success | muted
  title, caption,
  trailing,         // ReactNode — a Pill or Toggle or chevron
  selected = false,
  onClick,
  withTile = true,
  style,
}) {
  const t = useTheme();
  const tileBg =
    tileTone === "success" ? t.colors.successSoft :
    tileTone === "muted"   ? "rgba(168,163,155,0.10)" :
    t.colors.accentSoft;
  const tileFg =
    tileTone === "success" ? t.colors.success :
    tileTone === "muted"   ? t.colors.textMuted :
    t.colors.accent;

  return (
    <div
      onClick={onClick}
      style={{
        display: "flex", alignItems: "center", gap: 14,
        padding: "14px 16px",
        background: t.colors.surface,
        border: `1px solid ${selected ? t.colors.accentHairline : t.colors.borderSubtle}`,
        borderRadius: 18,
        cursor: onClick ? "pointer" : "default",
        boxShadow: selected ? `inset 0 0 0 1px ${t.colors.accent}33` : "none",
        ...style,
      }}>
      {withTile && illu && (
        typeof illu === "string"
          ? (
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: tileBg, color: tileFg,
              display: "flex", alignItems: "center", justifyContent: "center",
              flex: "0 0 auto",
            }}>
              <Illu name={illu} size={22} color={tileFg} />
            </div>
          )
          : illu
      )}
      <div style={{ display: "flex", flexDirection: "column", gap: 3, flex: 1, minWidth: 0 }}>
        <div style={{
          color: t.colors.textPrimary,
          font: `500 15px/1.3 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "-0.005em",
        }}>{title}</div>
        {caption && (
          <div style={{
            color: t.colors.textSecondary,
            font: `400 13px/1.45 ${HANDY_TYPE.fontBody}`,
          }}>{caption}</div>
        )}
      </div>
      {trailing && <div style={{ flex: "0 0 auto" }}>{trailing}</div>}
    </div>
  );
}

// ───── Section heading (overline above grouped rows in settings) ──────────
// Compose: SectionHeading
function SectionLabel({ children, style }) {
  const t = useTheme();
  return (
    <div style={{
      color: t.colors.textMuted,
      font: `500 11px/1 ${HANDY_TYPE.fontBody}`,
      letterSpacing: "0.10em", textTransform: "uppercase",
      padding: "0 4px 10px",
      ...style,
    }}>{children}</div>
  );
}

// ───── Input field with optional trailing affordances ────────────────────
// Compose: HandyTextField
function TextField({
  placeholder, value, type = "text",
  masked = false,                  // for API keys — shows • • • •
  trailing,                        // ReactNode (e.g. eye + copy)
  style,
}) {
  const t = useTheme();
  const display = masked && value
    ? value.slice(0, 5) + "•".repeat(Math.max(0, value.length - 8)) + value.slice(-3)
    : (value || placeholder);
  const isPlaceholder = !value;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 8,
      height: 48, padding: "0 12px 0 14px",
      borderRadius: 12,
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      ...style,
    }}>
      <div style={{
        flex: 1, minWidth: 0,
        color: isPlaceholder ? t.colors.textMuted : t.colors.textPrimary,
        font: `400 14px/1 ${masked ? HANDY_TYPE.fontMono : HANDY_TYPE.fontBody}`,
        overflow: "hidden", whiteSpace: "nowrap", textOverflow: "ellipsis",
      }}>{display}</div>
      {trailing}
    </div>
  );
}

// ───── Icon button (32 dp, no background) ────────────────────────────────
function IconButton({ name, color, size = 18, onClick, style }) {
  const t = useTheme();
  return (
    <button onClick={onClick} style={{
      width: 32, height: 32, borderRadius: 8, border: "none",
      background: "transparent", cursor: "pointer", padding: 0,
      display: "inline-flex", alignItems: "center", justifyContent: "center",
      ...style,
    }}>
      <Illu name={name} size={size} color={color || t.colors.textSecondary} />
    </button>
  );
}

// ───── Progress bar ───────────────────────────────────────────────────────
// Compose: ProgressBar
function ProgressBar({ value = 0.5, height = 4, style }) {
  const t = useTheme();
  return (
    <div style={{
      width: "100%", height, borderRadius: height / 2,
      background: t.colors.surface,
      overflow: "hidden",
      ...style,
    }}>
      <div style={{
        width: `${Math.max(0, Math.min(1, value)) * 100}%`,
        height: "100%", borderRadius: height / 2,
        background: t.colors.accent,
      }} />
    </div>
  );
}

// ───── Live dot — pulsing accent indicator ────────────────────────────────
function LiveDot({ size = 8, style }) {
  const t = useTheme();
  return (
    <span style={{
      display: "inline-block", width: size, height: size, borderRadius: "50%",
      background: t.colors.accent,
      boxShadow: `0 0 0 0 ${t.colors.accent}`,
      animation: "handy-livedot 1.6s ease-in-out infinite",
      ...style,
    }} />
  );
}

// ───── Radio card (selected gets accent border) ───────────────────────────
// Compose: ModelRadioCard
function RadioCard({ selected, title, subtitle, trailing, children, onClick }) {
  const t = useTheme();
  return (
    <div onClick={onClick} style={{
      cursor: onClick ? "pointer" : "default",
      borderRadius: 18,
      background: t.colors.surface,
      border: `1px solid ${selected ? t.colors.accent : t.colors.borderSubtle}`,
      padding: 16,
      boxShadow: selected ? `inset 0 0 0 1px ${t.colors.accent}, 0 0 32px -16px ${t.colors.accent}33` : "none",
      transition: "all 200ms ease-out",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <RadioDot on={selected} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            color: t.colors.textPrimary,
            font: `500 15px/1.3 ${HANDY_TYPE.fontBody}`,
          }}>{title}</div>
          {subtitle && (
            <div style={{
              color: t.colors.textSecondary,
              font: `400 13px/1.45 ${HANDY_TYPE.fontBody}`,
              marginTop: 2,
            }}>{subtitle}</div>
          )}
        </div>
        {trailing}
      </div>
      {children}
    </div>
  );
}

function RadioDot({ on }) {
  const t = useTheme();
  return (
    <div style={{
      width: 20, height: 20, borderRadius: "50%",
      border: `1.5px solid ${on ? t.colors.accent : t.colors.borderStrong}`,
      display: "flex", alignItems: "center", justifyContent: "center",
      flex: "0 0 auto",
    }}>
      {on && <div style={{ width: 10, height: 10, borderRadius: "50%", background: useTheme().colors.accent }} />}
    </div>
  );
}

// ───── Quick-prompt chip / card ───────────────────────────────────────────
// Compose: QuickPromptCard (2x2 grid in chat empty state)
//
// `tone` picks the accent color used on the icon (and the icon's soft
// background tile). Card surface stays uniform across the 2×2 grid so the
// composition reads as one group; only the icon family varies.
function QuickPromptCard({ illu, label, sublabel, tone = "accent" }) {
  const t = useTheme();
  const accent = t.colors[tone] || t.colors.accent;
  const accentSoft = t.colors[`${tone}Soft`] || t.colors.accentSoft;
  return (
    <div style={{
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 18,
      padding: 16,
      display: "flex", flexDirection: "column", gap: 14,
      minHeight: 118,
      justifyContent: "space-between",
    }}>
      {/* icon — colored glyph, no tile bg so cards stay calm */}
      <Illu name={illu} size={22} color={accent} />
      <div>
        <div style={{
          color: t.colors.textPrimary,
          font: `600 15px/1.25 ${HANDY_TYPE.fontDisplay}`,
          letterSpacing: "-0.010em",
        }}>{label}</div>
        {sublabel && (
          <div style={{
            color: t.colors.textMuted,
            font: `400 11px/1.4 ${HANDY_TYPE.fontBody}`, marginTop: 4,
          }}>{sublabel}</div>
        )}
      </div>
    </div>
  );
}

// ───── Quick-prompt chip (horizontal scroll, overlay variant) ────────────
function QuickPromptChip({ illu, label, glass = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "inline-flex", alignItems: "center", gap: 8,
      height: 32, padding: "0 12px", borderRadius: 16,
      background: glass ? "rgba(255,255,255,0.08)" : t.colors.surfaceElevated,
      border: `0.5px solid ${glass ? "rgba(255,255,255,0.20)" : t.colors.borderSubtle}`,
      whiteSpace: "nowrap",
      backdropFilter: glass ? "blur(24px)" : "none",
    }}>
      {illu && <Illu name={illu} size={14} color={t.colors.accent} />}
      <span style={{
        color: t.colors.textPrimary,
        font: `500 12px/1 ${HANDY_TYPE.fontBody}`,
      }}>{label}</span>
    </div>
  );
}

// ───── Composer (mic | field | send) — used in chat & overlay ────────────
// Compose: ChatComposer
function Composer({ placeholder = "Ask Handy anything…", glass = false }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 10,
      padding: 8,
      borderRadius: 28,
      background: glass ? "rgba(255,255,255,0.06)" : t.colors.surfaceElevated,
      border: `1px solid ${glass ? "rgba(255,255,255,0.14)" : t.colors.borderSubtle}`,
      backdropFilter: glass ? "blur(24px)" : "none",
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
  );
}

window.PrimaryButton = PrimaryButton;
window.SecondaryTextButton = SecondaryTextButton;
window.DestructiveButton = DestructiveButton;
window.Pill = Pill;
window.Toggle = Toggle;
window.Row = Row;
window.SectionLabel = SectionLabel;
window.TextField = TextField;
window.IconButton = IconButton;
window.ProgressBar = ProgressBar;
window.LiveDot = LiveDot;
window.RadioCard = RadioCard;
window.RadioDot = RadioDot;
window.QuickPromptCard = QuickPromptCard;
window.QuickPromptChip = QuickPromptChip;
window.Composer = Composer;
