// scenes-legacy.jsx — Redesign of the three legacy surfaces still on the
// old HandyColors / HandyType tokens:
//
//   1. Manual target picker (overlay full-screen helper when the resolver
//      can't pick a target unambiguously)
//   2. Activity log (Settings → Activity log full-screen list)
//   3. Diagnostics (Settings → Diagnostics overview + timeline tabs)
//
// All three render inside the standard Phone frame and reuse our existing
// primitives (Row, Pill, SectionCard, Composer, etc.) so the visual
// vocabulary matches the rest of the canvas.

// ────────────────────────────────────────────────────────────────────────
//  1. MANUAL TARGET PICKER
//
//  Trigger: the resolver returned multiple candidates that all look like
//  plausible matches, or it returned zero confident matches. The widget
//  goes idle, a dim layer drops over the host app, and a coach card
//  surfaces at the top with the instruction "Tap the one you mean."
//
//  Two demonstrated states:
//    a) "candidates"  — 3 visible UI elements highlighted with ranked
//                       confidence chips. User taps one to confirm.
//    b) "captured"    — user has tapped; the picked element pulses
//                       amber while the action dispatches.
// ────────────────────────────────────────────────────────────────────────

function ManualTargetSelectorScreen({ state = "candidates" }) {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone bg="#FFFFFF" statusInk="dark" hideNav>
        {/* Host app — settings storage page (white, bright) */}
        <FakeStorageHost />

        {/* Dim layer */}
        <div style={{ position: "absolute", inset: 0, background: "rgba(8,9,11,0.55)" }} />

        {/* Coach card pinned to top */}
        <div style={{ position: "absolute", top: 24, left: 18, right: 18, zIndex: 30 }}>
          <CoachCard
            title={state === "captured" ? "Got it — running…" : "Tap the one you mean"}
            subtitle={state === "captured"
              ? "Confirming 'Clear cache' tap"
              : "Two of these matched. Pick the one you wanted."}
            counter={state === "captured" ? null : "2 matches"}
          />
        </div>

        {/* Highlighted target rings — drawn over the dim layer */}
        <TargetHighlights state={state} />

        {/* Floating widget bottom-right, in pointing state */}
        <div style={{ position: "absolute", right: 18, bottom: 36, zIndex: 30 }}>
          <WidgetGlyph state="pointing" />
        </div>

        {/* Cancel bar at the bottom */}
        <div style={{ position: "absolute", left: 18, right: 18, bottom: 36, zIndex: 25 }}>
          <ManualCancelBar />
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function CoachCard({ title, subtitle, counter }) {
  const t = useTheme();
  return (
    <div style={{
      borderRadius: 18,
      background: "rgba(18,20,24,0.92)",
      backdropFilter: "blur(28px) saturate(160%)",
      WebkitBackdropFilter: "blur(28px) saturate(160%)",
      border: `0.5px solid ${t.colors.pointHair}`,
      boxShadow: `0 12px 32px -12px ${t.colors.point}55`,
      padding: "14px 14px 14px 16px",
      display: "flex", alignItems: "center", gap: 12,
    }}>
      <div style={{
        width: 36, height: 36, borderRadius: "50%",
        background: t.colors.pointSoft,
        border: `0.5px solid ${t.colors.pointHair}`,
        display: "flex", alignItems: "center", justifyContent: "center",
        flex: "0 0 auto",
      }}>
        <Illu name="handPointBold" size={22} color={t.colors.point} />
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `600 14px/1.2 ${HANDY_TYPE.fontDisplay}`,
          color: t.colors.textPrimary, letterSpacing: "-0.005em",
        }}>{title}</div>
        <div style={{
          font: `400 12px/1.4 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textSecondary, marginTop: 2,
        }}>{subtitle}</div>
      </div>
      {counter && (
        <span style={{
          padding: "5px 9px", borderRadius: 999,
          background: t.colors.pointSoft, color: t.colors.point,
          font: `600 10px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.10em", textTransform: "uppercase",
          flex: "0 0 auto",
        }}>{counter}</span>
      )}
    </div>
  );
}

function TargetHighlights({ state }) {
  const t = useTheme();
  // Hand-placed rectangles around mock host-app rows.
  const targets = [
    { top: 230, left: 24, w: 360, h: 56, label: "Clear cache", conf: "Best guess" },
    { top: 296, left: 24, w: 360, h: 56, label: "Clear storage", conf: "Maybe" },
  ];
  return (
    <>
      {targets.map((tg, i) => {
        const isCaptured = state === "captured" && i === 0;
        return (
          <div key={i} style={{
            position: "absolute",
            top: tg.top, left: tg.left, width: tg.w, height: tg.h,
            border: `2px solid ${isCaptured ? t.colors.accent : t.colors.point}`,
            borderRadius: 14,
            boxShadow: isCaptured
              ? `0 0 0 4px ${t.colors.accentSoft}, 0 0 28px 0 ${t.colors.accent}66`
              : `0 0 0 4px ${t.colors.pointSoft}`,
            zIndex: 20,
            pointerEvents: "none",
          }}>
            {/* Ranked chip — below the rect */}
            <span style={{
              position: "absolute", top: tg.h + 6, left: 0,
              padding: "4px 9px", borderRadius: 999,
              background: isCaptured ? t.colors.accent : t.colors.point,
              color: isCaptured ? t.colors.accentInk : "#FFFFFF",
              font: `600 9px/1 ${HANDY_TYPE.fontBody}`,
              letterSpacing: "0.10em", textTransform: "uppercase",
            }}>{isCaptured ? "Running" : tg.conf}</span>
          </div>
        );
      })}
    </>
  );
}

function ManualCancelBar() {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", alignItems: "center", justifyContent: "space-between",
      padding: "10px 14px",
      borderRadius: 999,
      background: "rgba(18,20,24,0.92)",
      backdropFilter: "blur(20px) saturate(160%)",
      border: `0.5px solid rgba(255,255,255,0.12)`,
    }}>
      <span style={{
        font: `400 12px/1 ${HANDY_TYPE.fontBody}`,
        color: t.colors.textSecondary,
      }}>Or tap anywhere outside to dismiss</span>
      <button style={{
        padding: "6px 14px", borderRadius: 999, border: "none",
        background: t.colors.surfaceElevated, color: t.colors.textPrimary,
        font: `600 11px/1 ${HANDY_TYPE.fontBody}`, cursor: "pointer",
      }}>Cancel</button>
    </div>
  );
}

function FakeStorageHost() {
  // White settings page mock — Storage screen with two adjacent action rows
  // that the resolver couldn't disambiguate.
  return (
    <div style={{ position: "absolute", inset: 0, background: "#FFFFFF",
                  fontFamily: HANDY_TYPE.fontBody, color: "#1A1A1A" }}>
      <div style={{ padding: "60px 24px 0" }}>
        <div style={{ font: `400 28px/1 'Inter', system-ui`, letterSpacing: "-0.01em" }}>Storage</div>
        <div style={{ marginTop: 6, font: `400 13px/1.5 'Inter', system-ui`, color: "#5F6368" }}>
          Free up space by clearing data
        </div>
      </div>
      <div style={{ position: "absolute", top: 230, left: 24, right: 24,
                    display: "flex", flexDirection: "column", gap: 10 }}>
        <FakeRow icon="🗑" title="Clear cache" caption="14.2 MB" />
        <FakeRow icon="🗑" title="Clear storage" caption="148 MB" />
        <FakeRow icon="📦" title="Manage space"  caption="Open app management" />
      </div>
    </div>
  );
}

function FakeRow({ icon, title, caption }) {
  return (
    <div style={{
      height: 56, padding: "0 16px",
      background: "#F1F3F4", borderRadius: 14,
      display: "flex", alignItems: "center", gap: 12,
      font: `400 14px/1 'Inter', system-ui`, color: "#1A1A1A",
    }}>
      <span style={{ fontSize: 20 }}>{icon}</span>
      <div style={{ flex: 1 }}>
        <div style={{ fontWeight: 500 }}>{title}</div>
        <div style={{ fontSize: 12, color: "#5F6368", marginTop: 2 }}>{caption}</div>
      </div>
    </div>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  2. ACTIVITY LOG  (settings → Activity log)
//
//  Full-screen LazyColumn-style list. Top: back chevron + "Activity"
//  title + a session summary chip. Below: time-grouped audit events,
//  each in a card with the action label, target app pill, result pill,
//  redacted target line, and per-row destructive controls.
// ────────────────────────────────────────────────────────────────────────

function ActivityLogScreen({ empty = false } = {}) {
  const t = HANDY_TOKENS.amber;
  const today = [
    { action: "Tap", app: "Google Maps", pkg: "com.google.android.apps.maps",
      target: "Start", time: "2:14 PM", result: "Dispatched", tone: "act" },
    { action: "Type", app: "Messages", pkg: "com.google.android.apps.messaging",
      target: "Search field (redacted)", time: "1:02 PM", result: "Dispatched", tone: "act" },
    { action: "Tap", app: "Photos", pkg: "com.google.android.apps.photos",
      target: "Share button", time: "12:48 PM", result: "Cancelled", tone: "muted" },
    { action: "Tap", app: "WhatsApp", pkg: "com.whatsapp",
      target: "Continue", time: "11:30 AM", result: "Failed",
      reason: "View no longer visible", tone: "danger" },
  ];
  const yesterday = [
    { action: "Web fetch", app: "Chrome", pkg: "com.android.chrome",
      target: "anthropic.com/news", time: "8:21 PM", result: "Dispatched", tone: "act" },
  ];

  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          color: t.colors.textPrimary, fontFamily: HANDY_TYPE.fontBody,
          display: "flex", flexDirection: "column",
        }}>
          <ActivityHeader empty={empty} count={today.length + yesterday.length} />

          {empty
            ? <ActivityEmpty />
            : (
              <div style={{ flex: 1, overflowY: "hidden", padding: "18px 16px 0" }}>
                <DayHeader label="Today" />
                <div style={{ display: "flex", flexDirection: "column", gap: 10, marginBottom: 20 }}>
                  {today.map((e, i) => <ActivityRow key={i} entry={e} />)}
                </div>
                <DayHeader label="Yesterday" />
                <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                  {yesterday.map((e, i) => <ActivityRow key={i} entry={e} />)}
                </div>
              </div>
            )}
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function ActivityHeader({ empty, count }) {
  const t = useTheme();
  return (
    <div style={{ padding: "10px 18px 14px",
                  borderBottom: `1px solid ${t.colors.borderSubtle}` }}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <button style={{
          width: 36, height: 36, borderRadius: 12, border: "none",
          background: t.colors.surface, cursor: "pointer",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
        }}>
          <Illu name="back" size={16} color={t.colors.textPrimary} />
        </button>
        <div style={{ flex: 1 }}>
          <div style={{
            font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.020em",
          }}>Activity</div>
          <div style={{
            font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textSecondary, marginTop: 3,
          }}>Every action Handy took. Targets redacted.</div>
        </div>
        {!empty && (
          <span style={{
            padding: "5px 10px", borderRadius: 999,
            background: t.colors.accentSoft, color: t.colors.accent,
            font: `600 10px/1 ${HANDY_TYPE.fontBody}`,
            letterSpacing: "0.10em", textTransform: "uppercase",
          }}>{count} events</span>
        )}
      </div>
    </div>
  );
}

function ActivityEmpty() {
  const t = useTheme();
  return (
    <div style={{
      flex: 1, padding: "32px 32px 0",
      display: "flex", flexDirection: "column", alignItems: "center", textAlign: "center",
    }}>
      <Illu name="recipe" size={96} color={t.colors.accent} opacity={0.6} />
      <div style={{
        marginTop: 22,
        font: `600 22px/1.15 ${HANDY_TYPE.fontDisplay}`,
        letterSpacing: "-0.018em",
      }}>Nothing here yet</div>
      <div style={{
        marginTop: 8, maxWidth: 280,
        font: `400 13px/1.55 ${HANDY_TYPE.fontBody}`,
        color: t.colors.textSecondary,
      }}>When Handy taps, types, or fetches a page for you, the action shows up here with the target redacted.</div>
    </div>
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

function ActivityRow({ entry }) {
  const t = useTheme();
  const toneMap = {
    act:    { bg: t.colors.successSoft,            fg: t.colors.success,  label: "Done"      },
    muted:  { bg: "rgba(168,163,155,0.10)",        fg: t.colors.textMuted, label: "Cancelled" },
    danger: { bg: t.colors.dangerSoft,             fg: t.colors.danger,   label: "Failed"    },
  };
  const tone = toneMap[entry.tone] || toneMap.act;
  const illu =
    entry.action === "Web fetch" ? "globe"
    : entry.action === "Type"     ? "keyboard"
    : "handTap";

  return (
    <div style={{
      background: t.colors.surface,
      border: `1px solid ${t.colors.borderSubtle}`,
      borderRadius: 18, padding: "14px 14px",
      display: "flex", flexDirection: "column", gap: 10,
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div style={{
          width: 36, height: 36, borderRadius: 10,
          background: tone.bg,
          display: "flex", alignItems: "center", justifyContent: "center",
          flex: "0 0 auto",
        }}>
          <Illu name={illu} size={18} color={tone.fg} />
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            font: `600 14px/1.2 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textPrimary,
          }}>{entry.action} · <span style={{ color: t.colors.textSecondary, fontWeight: 400 }}>{entry.target}</span></div>
          <div style={{
            font: `400 11px/1.4 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textMuted, marginTop: 2,
          }}>{entry.app} · {entry.time}{entry.reason ? ` · ${entry.reason}` : ""}</div>
        </div>
        <span style={{
          padding: "4px 9px", borderRadius: 999,
          background: tone.bg, color: tone.fg,
          font: `600 10px/1 ${HANDY_TYPE.fontBody}`,
          letterSpacing: "0.10em", textTransform: "uppercase",
        }}>{tone.label}</span>
      </div>
      {/* per-row destructive actions */}
      <div style={{ display: "flex", gap: 8, paddingLeft: 48 }}>
        <ActionChip label="Disable in this app" tone="danger" />
        <ActionChip label="Report wrong action"  tone="muted" />
      </div>
    </div>
  );
}

function ActionChip({ label, tone = "muted" }) {
  const t = useTheme();
  const c = tone === "danger" ? t.colors.danger : t.colors.textMuted;
  const bg = tone === "danger" ? t.colors.dangerSoft : "rgba(168,163,155,0.08)";
  return (
    <span style={{
      padding: "5px 11px", borderRadius: 10,
      background: bg, border: `0.5px solid ${c}44`,
      color: c, font: `600 10px/1 ${HANDY_TYPE.fontBody}`,
      letterSpacing: "0.08em", textTransform: "uppercase",
      cursor: "pointer",
    }}>{label}</span>
  );
}

// ────────────────────────────────────────────────────────────────────────
//  3. DIAGNOSTICS  (settings → Diagnostics)
//
//  Two-tab read-only surface. Overview tab — status rows grouped by
//  capability (Accessibility, AI brain, Action gate, etc.) with green/
//  amber/red dots. Timeline tab — turn-grouped event list with per-row
//  expand for detail.
// ────────────────────────────────────────────────────────────────────────

function DiagnosticsScreen({ tab = "overview" } = {}) {
  const t = HANDY_TOKENS.amber;
  return (
    <ThemeProvider theme="amber">
      <Phone>
        <div style={{
          width: "100%", height: "100%", background: t.colors.pageBg,
          color: t.colors.textPrimary, fontFamily: HANDY_TYPE.fontBody,
          display: "flex", flexDirection: "column", overflow: "hidden",
        }}>
          <DiagHeader />
          <DiagTabs selected={tab} />
          <div style={{ flex: 1, overflowY: "hidden", padding: "14px 16px 0" }}>
            {tab === "overview" ? <DiagOverview /> : <DiagTimeline />}
          </div>
        </div>
      </Phone>
    </ThemeProvider>
  );
}

function DiagHeader() {
  const t = useTheme();
  return (
    <div style={{ padding: "10px 18px 12px",
                  borderBottom: `1px solid ${t.colors.borderSubtle}` }}>
      <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
        <button style={{
          width: 36, height: 36, borderRadius: 12, border: "none",
          background: t.colors.surface, cursor: "pointer",
          display: "inline-flex", alignItems: "center", justifyContent: "center",
        }}>
          <Illu name="back" size={16} color={t.colors.textPrimary} />
        </button>
        <div style={{ flex: 1 }}>
          <div style={{
            font: `600 22px/1 ${HANDY_TYPE.fontDisplay}`,
            letterSpacing: "-0.020em",
          }}>Diagnostics</div>
          <div style={{
            font: `400 12px/1.3 ${HANDY_TYPE.fontBody}`,
            color: t.colors.textSecondary, marginTop: 3,
          }}>What Handy sees right now. Read-only.</div>
        </div>
      </div>
    </div>
  );
}

function DiagTabs({ selected }) {
  const t = useTheme();
  return (
    <div style={{
      display: "flex", gap: 8,
      padding: "14px 16px 0",
    }}>
      {[
        { id: "overview", label: "Overview" },
        { id: "timeline", label: "Timeline" },
      ].map((tab) => {
        const active = selected === tab.id;
        return (
          <span key={tab.id} style={{
            flex: 1, textAlign: "center",
            padding: "10px 14px", borderRadius: 999,
            background: active ? t.colors.accent : t.colors.surface,
            border: active ? "none" : `1px solid ${t.colors.borderSubtle}`,
            color: active ? t.colors.accentInk : t.colors.textSecondary,
            font: `600 13px/1 ${HANDY_TYPE.fontBody}`,
            letterSpacing: "-0.005em",
          }}>{tab.label}</span>
        );
      })}
    </div>
  );
}

function DiagOverview() {
  const t = useTheme();
  const groups = [
    {
      title: "Connections",
      rows: [
        { label: "Accessibility",  value: "Connected", tone: "act" },
        { label: "Local GenAI",    value: "Available", tone: "act" },
        { label: "Cloud provider", value: "Anthropic · Sonnet 4.5", tone: "act" },
      ],
    },
    {
      title: "Voice",
      rows: [
        { label: "STT mode",     value: "Auto · on-device + network", tone: "act" },
        { label: "STT language", value: "English", tone: "muted" },
      ],
    },
    {
      title: "Action gate",
      rows: [
        { label: "Tap-for-me",          value: "Enabled", tone: "act" },
        { label: "Gestures",            value: "Allowed", tone: "act" },
        { label: "Last flight cancel",  value: "User cancel · 12 m ago", tone: "muted" },
      ],
    },
    {
      title: "Recent actions",
      rows: [
        { label: "Maps · Start",       value: "Dispatched · 2:14 PM", tone: "act" },
        { label: "WhatsApp · Continue", value: "Failed · view gone",   tone: "danger" },
      ],
    },
  ];
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14, paddingBottom: 20 }}>
      {groups.map((g, i) => (
        <div key={i}>
          <div style={{
            ...typeStyle("overline", t),
            color: t.colors.textMuted,
            padding: "0 4px 8px",
          }}>{g.title}</div>
          <div style={{
            background: t.colors.surface,
            border: `1px solid ${t.colors.borderSubtle}`,
            borderRadius: 18, overflow: "hidden",
          }}>
            {g.rows.map((r, j) => <DiagRow key={j} row={r} last={j === g.rows.length - 1} />)}
          </div>
        </div>
      ))}
    </div>
  );
}

function DiagRow({ row, last }) {
  const t = useTheme();
  const dotColor =
    row.tone === "act"    ? t.colors.success :
    row.tone === "danger" ? t.colors.danger  :
                            t.colors.textMuted;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px",
      borderBottom: last ? "none" : `1px solid ${t.colors.borderSubtle}`,
    }}>
      <span style={{
        width: 7, height: 7, borderRadius: "50%",
        background: dotColor,
        boxShadow: `0 0 10px ${dotColor}77`,
        flex: "0 0 auto",
      }} />
      <span style={{
        flex: 1, font: `500 13px/1.2 ${HANDY_TYPE.fontBody}`,
        color: t.colors.textPrimary,
      }}>{row.label}</span>
      <span style={{
        font: `400 12px/1 ${HANDY_TYPE.fontMono}`,
        color: t.colors.textSecondary, textAlign: "right",
      }}>{row.value}</span>
    </div>
  );
}

function DiagTimeline() {
  const t = useTheme();
  const turn1 = [
    { stage: "Listen",    dur: 1240, meta: "STT · android", error: null },
    { stage: "Plan",      dur:  860, meta: "Sonnet 4.5",    error: null },
    { stage: "Resolve",   dur:  140, meta: "conf 0.94",     error: null },
    { stage: "Dispatch",  dur:   62, meta: "Tap · Maps",    error: null },
  ];
  const turn2 = [
    { stage: "Listen",    dur: 1100, meta: "STT · android", error: null },
    { stage: "Plan",      dur:  920, meta: "Sonnet 4.5",    error: null },
    { stage: "Resolve",   dur:  180, meta: "conf 0.61",     error: null },
    { stage: "Dispatch",  dur:   28, meta: "Tap · WhatsApp", error: "View gone" },
  ];
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 14, paddingBottom: 20 }}>
      {/* Toolbar */}
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "0 4px",
      }}>
        <div style={{
          ...typeStyle("overline", t),
          color: t.colors.textMuted,
        }}>2 turns · 8 events</div>
        <div style={{ display: "flex", gap: 8 }}>
          <ActionChip label="Export JSON" tone="muted" />
          <ActionChip label="Clear all"   tone="danger" />
        </div>
      </div>

      <TurnGroup id="t_4ad19c" count={4} events={turn1} />
      <TurnGroup id="t_3b7e21" count={4} events={turn2} />
    </div>
  );
}

function TurnGroup({ id, count, events }) {
  const t = useTheme();
  return (
    <div>
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "0 4px 8px",
      }}>
        <span style={{
          font: `500 11px/1 ${HANDY_TYPE.fontMono}`,
          color: t.colors.textSecondary, letterSpacing: "0.04em",
        }}>turn · {id}</span>
        <span style={{
          font: `400 11px/1 ${HANDY_TYPE.fontMono}`,
          color: t.colors.textMuted,
        }}>{count} events</span>
      </div>
      <div style={{
        background: t.colors.surface,
        border: `1px solid ${t.colors.borderSubtle}`,
        borderRadius: 18, overflow: "hidden",
      }}>
        {events.map((ev, i) => <TimelineRow key={i} ev={ev} last={i === events.length - 1} />)}
      </div>
    </div>
  );
}

function TimelineRow({ ev, last }) {
  const t = useTheme();
  const errored = !!ev.error;
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px",
      borderBottom: last ? "none" : `1px solid ${t.colors.borderSubtle}`,
    }}>
      <span style={{
        width: 7, height: 7, borderRadius: "50%",
        background: errored ? t.colors.danger : t.colors.accent,
        boxShadow: `0 0 10px ${errored ? t.colors.danger : t.colors.accent}77`,
        flex: "0 0 auto",
      }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{
          font: `600 13px/1.2 ${HANDY_TYPE.fontBody}`,
          color: t.colors.textPrimary,
        }}>{ev.stage}</div>
        <div style={{
          font: `400 11px/1.4 ${HANDY_TYPE.fontMono}`,
          color: errored ? t.colors.danger : t.colors.textMuted, marginTop: 2,
        }}>{ev.meta}{errored ? ` · ${ev.error}` : ""}</div>
      </div>
      <span style={{
        font: `500 11px/1 ${HANDY_TYPE.fontMono}`,
        color: t.colors.textSecondary,
      }}>{ev.dur}ms</span>
    </div>
  );
}

window.ManualTargetSelectorScreen = ManualTargetSelectorScreen;
window.ActivityLogScreen = ActivityLogScreen;
window.DiagnosticsScreen = DiagnosticsScreen;
