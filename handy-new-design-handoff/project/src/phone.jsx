// phone.jsx — A clean Pixel-9-shaped phone frame for canvas screens.
//
// Brief spec is Pixel 9 = 412 × 915 dp. We render at 1:1 dp so canvas
// dimensions reflect real Android values. The shell is intentionally
// minimalist (no Material color spillage) so the design inside reads.
//
// Props:
//   bg          background of the inner screen
//   time        status-bar clock string
//   statusInk   "light" (default) | "dark"
//   hideStatus  pass true for floating-widget contexts where the host app's
//               status bar would normally be visible
//   hideNav     hide the bottom gesture pill
//   children    the screen content (rendered absolutely in the safe area)

const PHONE_W = 412;
const PHONE_H = 915;
const STATUS_H = 44;     // status bar + camera cutout area
const NAV_H = 28;        // gesture handle band

function Phone({
  bg = "#08090B",
  time = "9:41",
  statusInk = "light",
  hideStatus = false,
  hideNav = false,
  children,
  outerRadius = 44,
  innerRadius = 36,
  width = PHONE_W,
  height = PHONE_H,
  style,
}) {
  const ink = statusInk === "dark" ? "#0A0A0C" : "#F4F2EE";
  return (
    <div style={{
      width, height,
      borderRadius: outerRadius,
      background: "#0A0A0C",
      padding: 8,
      boxShadow: "0 0 0 1.5px rgba(255,255,255,0.06) inset, 0 30px 80px -30px rgba(0,0,0,0.5)",
      position: "relative",
      flex: "0 0 auto",
      ...style,
    }}>
      <div style={{
        width: "100%", height: "100%",
        borderRadius: innerRadius,
        overflow: "hidden",
        position: "relative",
        background: bg,
      }}>
        {/* status bar */}
        {!hideStatus && (
          <div style={{
            position: "absolute", top: 0, left: 0, right: 0,
            height: STATUS_H,
            display: "flex", alignItems: "center", justifyContent: "space-between",
            padding: "0 24px",
            color: ink,
            font: "600 14px/1 'Söhne','Inter',system-ui,sans-serif",
            letterSpacing: "-0.01em",
            zIndex: 50,
          }}>
            <span>{time}</span>
            {/* camera punch-hole (Pixel 9 has it centered top) */}
            <div style={{
              position: "absolute", left: "50%", top: 14,
              transform: "translateX(-50%)",
              width: 12, height: 12, borderRadius: 999,
              background: "#000",
              boxShadow: "0 0 0 1px rgba(255,255,255,0.06)",
            }} />
            {/* status icons */}
            <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
              {/* signal */}
              <svg width="16" height="10" viewBox="0 0 16 10" fill={ink}>
                <rect x="0"  y="6" width="2.5" height="4" rx="0.5" />
                <rect x="4"  y="4" width="2.5" height="6" rx="0.5" />
                <rect x="8"  y="2" width="2.5" height="8" rx="0.5" opacity="0.6" />
                <rect x="12" y="0" width="2.5" height="10" rx="0.5" opacity="0.3" />
              </svg>
              {/* wifi */}
              <svg width="14" height="10" viewBox="0 0 14 10" fill="none" stroke={ink} strokeWidth="1.2" strokeLinecap="round">
                <path d="M1 4 Q7 -1 13 4" />
                <path d="M3 6 Q7 3 11 6" />
                <circle cx="7" cy="8.6" r="0.9" fill={ink} stroke="none" />
              </svg>
              {/* battery */}
              <svg width="22" height="10" viewBox="0 0 22 10" fill="none">
                <rect x="0.5" y="0.5" width="18" height="9" rx="2.2" stroke={ink} />
                <rect x="2.5" y="2.5" width="13" height="5" rx="0.8" fill={ink} />
                <rect x="20"  y="3.5" width="1.6" height="3" rx="0.6" fill={ink} />
              </svg>
            </div>
          </div>
        )}

        {/* content area */}
        <div style={{
          position: "absolute",
          top: hideStatus ? 0 : STATUS_H,
          left: 0, right: 0,
          bottom: hideNav ? 0 : NAV_H,
          overflow: "hidden",
        }}>
          {children}
        </div>

        {/* nav pill */}
        {!hideNav && (
          <div style={{
            position: "absolute", left: 0, right: 0, bottom: 0,
            height: NAV_H, display: "flex", alignItems: "center", justifyContent: "center",
            zIndex: 30,
          }}>
            <div style={{
              width: 132, height: 4, borderRadius: 4,
              background: ink, opacity: 0.95,
            }} />
          </div>
        )}
      </div>
    </div>
  );
}

window.Phone = Phone;
window.PHONE_W = PHONE_W;
window.PHONE_H = PHONE_H;
window.PHONE_STATUS_H = STATUS_H;
window.PHONE_NAV_H = NAV_H;
