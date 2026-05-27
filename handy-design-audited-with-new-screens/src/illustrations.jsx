// illustrations.jsx — Handy's custom line-illustration set.
//
// Style rules (must hold across every motif):
//   - Single stroke color — accent. No fills (except where noted, e.g. the
//     filled finger-circle on the tap-for-me variant).
//   - Stroke width scales with viewbox: roughly viewBox/48 at base size so
//     all motifs read at the same visual weight.
//   - strokeLinecap="round", strokeLinejoin="round" everywhere.
//   - Very slight imperfection — paths use control points that wander 0.5-1
//     px from geometric ideal so the lines feel drawn, not generated.
//
// Sizes (per brief):
//   hero   200×200 dp   strokeWidth 2.4
//   spot    72× 72 dp   strokeWidth 1.8
//   inline  24× 24 dp   strokeWidth 1.6  (single-glyph use, simplified)

const ILLU_SIZES = { hero: 200, spot: 72, inline: 24 };
function strokeFor(size) {
  if (size <= 28)  return 1.6;
  if (size <= 96)  return 1.8;
  return 2.4;
}

// All paths drawn in a 48×48 design grid then rendered at any size.
//
// HAND MARKS — adopted from Phosphor "hand-palm" (regular + fill).
// 256×256 source viewBox; we render inside the 48×48 grid via a transform
// so it composes cleanly alongside the other motifs. Two source paths:
//
//   PH_HAND_OUTLINE  → regular weight, used for tile/disc/wordmark
//   PH_HAND_FILL     → filled version, reserved for the floating widget's
//                      active states (acting / tapping) where weight reads
//                      stronger on a small disc.
//
// Variant flavor is conveyed by transforms + small accent overlays — same
// glyph, rotated or annotated, so the brand stays one consistent shape
// across every surface.

const PH_HAND_OUTLINE = "M188 88a27.75 27.75 0 0 0-12 2.71V60a28 28 0 0 0-41.36-24.6A28 28 0 0 0 80 44v6.71A27.75 27.75 0 0 0 68 48a28 28 0 0 0-28 28v76a88 88 0 0 0 176 0v-36a28 28 0 0 0-28-28m12 64a72 72 0 0 1-144 0V76a12 12 0 0 1 24 0v44a8 8 0 0 0 16 0V44a12 12 0 0 1 24 0v68a8 8 0 0 0 16 0V60a12 12 0 0 1 24 0v68.67A48.08 48.08 0 0 0 120 176a8 8 0 0 0 16 0a32 32 0 0 1 32-32a8 8 0 0 0 8-8v-20a12 12 0 0 1 24 0Z";
const PH_HAND_FILL    = "M216 104v48a88 88 0 0 1-176 0V64a16 16 0 0 1 32 0v56a8 8 0 0 0 16 0V32a16 16 0 0 1 32 0v80a8 8 0 0 0 16 0V48a16 16 0 0 1 32 0v80.67A48.08 48.08 0 0 0 128 176a8 8 0 0 0 16 0a32 32 0 0 1 32-32a8 8 0 0 0 8-8v-32a16 16 0 0 1 32 0";

// Phosphor `hand-pointing` — the classic index-up pointer cursor. Used for
// the floating widget's Pointing state and the Guide value-card vignette.
// Same 256-unit grid as hand-palm so PhosphorHand can host both.
const PH_POINT_OUTLINE = "M168,80a23.85,23.85,0,0,0-12,3.22V64a24,24,0,0,0-36-20.78V32a24,24,0,0,0-48,0v66.07a24,24,0,0,0-15.6,4.45c-9.84,7.51-11.86,21.5-4.62,31.85L84.91,209a8,8,0,1,0,13.09-9.18L57,141.32c-2.39-3.42-1.84-8.24,1.26-10.61a8,8,0,0,1,10.95,1.42L88,156a8,8,0,0,0,14.4-4.84L88,131.32V24a8,8,0,0,1,16,0v92a8,8,0,0,0,16,0V64a8,8,0,0,1,16,0v52a8,8,0,0,0,16,0V104a8,8,0,0,1,16,0v40a72.08,72.08,0,0,1-72,72,8,8,0,0,0,0,16,88.1,88.1,0,0,0,88-88V104A24,24,0,0,0,168,80Z";
const PH_POINT_FILL    = "M192,104v40a88.1,88.1,0,0,1-88,88,8,8,0,0,1,0-16,72.08,72.08,0,0,0,72-72V104a8,8,0,0,0-16,0v12a8,8,0,0,1-16,0V64a8,8,0,0,0-16,0v52a8,8,0,0,1-16,0V24a8,8,0,0,0-16,0V131.32a8,8,0,0,1-15.16,3.58L63.21,116.13a8,8,0,0,0-10.95-1.42c-3.1,2.37-3.65,7.19-1.26,10.61L97.06,193a8,8,0,0,1-13.09,9.18L36.16,141.32c-7.24-10.35-5.22-24.34,4.62-31.85a24,24,0,0,1,15.6-4.45V32a24,24,0,0,1,48,0V43.22A24,24,0,0,1,140,64v19.22A23.85,23.85,0,0,1,168,80,24,24,0,0,1,192,104Z";

// ─── Other Phosphor icons (mic, paper-plane, gear) used in chat composer
//     and settings header. Same 256-unit grid as hand-palm.
const PH_MIC  = "M128 176a48.05 48.05 0 0 0 48-48V64a48 48 0 0 0-96 0v64a48.05 48.05 0 0 0 48 48M96 64a32 32 0 0 1 64 0v64a32 32 0 0 1-64 0Zm40 143.6V240a8 8 0 0 1-16 0v-32.4A80.11 80.11 0 0 1 48 128a8 8 0 0 1 16 0a64 64 0 0 0 128 0a8 8 0 0 1 16 0a80.11 80.11 0 0 1-72 79.6";
const PH_SEND = "M240 127.89a16 16 0 0 1-8.18 14L63.9 237.9A16.15 16.15 0 0 1 56 240a16 16 0 0 1-15-21.33l27-79.95a4 4 0 0 1 3.72-2.72H144a8 8 0 0 0 8-8.53a8.19 8.19 0 0 0-8.26-7.47h-72a4 4 0 0 1-3.79-2.72l-27-79.94a16 16 0 0 1 22.89-19.27l168 95.89a16 16 0 0 1 8.16 13.93";
const PH_GEAR = "M128 80a48 48 0 1 0 48 48a48.05 48.05 0 0 0-48-48m0 80a32 32 0 1 1 32-32a32 32 0 0 1-32 32m109.94-52.79a8 8 0 0 0-3.89-5.4l-29.83-17l-.12-33.62a8 8 0 0 0-2.83-6.08a111.9 111.9 0 0 0-36.72-20.67a8 8 0 0 0-6.46.59L128 41.85L97.88 25a8 8 0 0 0-6.47-.6a112.1 112.1 0 0 0-36.68 20.75a8 8 0 0 0-2.83 6.07l-.15 33.65l-29.83 17a8 8 0 0 0-3.89 5.4a106.5 106.5 0 0 0 0 41.56a8 8 0 0 0 3.89 5.4l29.83 17l.12 33.62a8 8 0 0 0 2.83 6.08a111.9 111.9 0 0 0 36.72 20.67a8 8 0 0 0 6.46-.59L128 214.15L158.12 231a7.9 7.9 0 0 0 3.9 1a8.1 8.1 0 0 0 2.57-.42a112.1 112.1 0 0 0 36.68-20.73a8 8 0 0 0 2.83-6.07l.15-33.65l29.83-17a8 8 0 0 0 3.89-5.4a106.5 106.5 0 0 0-.03-41.52m-15 34.91l-28.57 16.25a8 8 0 0 0-3 3c-.58 1-1.19 2.06-1.81 3.06a7.94 7.94 0 0 0-1.22 4.21l-.15 32.25a95.9 95.9 0 0 1-25.37 14.3L134 199.13a8 8 0 0 0-3.91-1h-3.83a8.1 8.1 0 0 0-4.1 1l-28.84 16.1A96 96 0 0 1 67.88 201l-.11-32.2a8 8 0 0 0-1.22-4.22c-.62-1-1.23-2-1.8-3.06a8.1 8.1 0 0 0-3-3.06l-28.6-16.29a90.5 90.5 0 0 1 0-28.26l28.52-16.28a8 8 0 0 0 3-3c.58-1 1.19-2.06 1.81-3.06a7.94 7.94 0 0 0 1.22-4.21l.15-32.25a95.9 95.9 0 0 1 25.37-14.3L122 56.87a8 8 0 0 0 4.1 1h3.64a8.1 8.1 0 0 0 4.1-1l28.84-16.1A96 96 0 0 1 188.12 55l.11 32.2a8 8 0 0 0 1.22 4.22c.62 1 1.23 2 1.8 3.06a8.1 8.1 0 0 0 3 3.06l28.6 16.29a90.5 90.5 0 0 1 .05 28.29Z";

// Phosphor `hand-pointing` (bold weight) — used by the floating widget's
// Pointing state and the Guide onboarding card. The bold weight reads
// stronger at small disc sizes than the regular outline.
const PH_POINT_BOLD = "M196 84a32 32 0 0 0-11.22 2A32 32 0 0 0 148 69V44a32 32 0 0 0-64 0v66.83A32 32 0 0 0 32.25 148l4.68 8.24C71.11 216.48 86.72 244 136 244a92.1 92.1 0 0 0 92-92v-36a32 32 0 0 0-32-32m8 68a68.08 68.08 0 0 1-68 68c-34 0-43.49-14.45-78.2-75.65l-4.69-8.28a.2.2 0 0 1 0-.07a8 8 0 0 1 13.86-8c.06.12.13.23.2.35l18.68 30A12 12 0 0 0 108 152V44a8 8 0 0 1 16 0v68a12 12 0 0 0 24 0v-12a8 8 0 0 1 16 0v20a12 12 0 0 0 24 0v-4a8 8 0 0 1 16 0Z";

// Lucide cursor (mouse-pointer-2) — 24-unit, stroke-based. The arrow form
// reads as a system cursor; we use it for the floating widget's Flying
// state (it's tighter than a hand silhouette at small sizes on a disc).
const LU_CURSOR = "M4.037 4.688a.495.495 0 0 1 .651-.651l16 6.5a.5.5 0 0 1-.063.947l-6.124 1.58a2 2 0 0 0-1.438 1.435l-1.579 6.126a.5.5 0 0 1-.947.063z";

// More Phosphor icons (256-unit, fill-based) — replace earlier hand-drawn
// versions of eye, eye-off, shield. The sparkle is new (used to mark AI
// moments in chat & settings).
const PH_EYE         = "M247.31 124.76c-.35-.79-8.82-19.58-27.65-38.41C194.57 61.26 162.88 48 128 48S61.43 61.26 36.34 86.35C17.51 105.18 9 124 8.69 124.76a8 8 0 0 0 0 6.5c.35.79 8.82 19.57 27.65 38.4C61.43 194.74 93.12 208 128 208s66.57-13.26 91.66-38.34c18.83-18.83 27.3-37.61 27.65-38.4a8 8 0 0 0 0-6.5M128 192c-30.78 0-57.67-11.19-79.93-33.25A133.5 133.5 0 0 1 25 128a133.3 133.3 0 0 1 23.07-30.75C70.33 75.19 97.22 64 128 64s57.67 11.19 79.93 33.25A133.5 133.5 0 0 1 231.05 128c-7.21 13.46-38.62 64-103.05 64m0-112a48 48 0 1 0 48 48a48.05 48.05 0 0 0-48-48m0 80a32 32 0 1 1 32-32a32 32 0 0 1-32 32";
const PH_EYE_CLOSED  = "M228 175a8 8 0 0 1-10.92-3l-19-33.2A123.2 123.2 0 0 1 162 155.46l5.87 35.22a8 8 0 0 1-6.58 9.21a8.4 8.4 0 0 1-1.29.11a8 8 0 0 1-7.88-6.69l-5.77-34.58a133 133 0 0 1-36.68 0l-5.77 34.58A8 8 0 0 1 96 200a8.4 8.4 0 0 1-1.32-.11a8 8 0 0 1-6.58-9.21l5.9-35.22a123.2 123.2 0 0 1-36.06-16.69L39 172a8 8 0 1 1-13.94-8l20-35a153.5 153.5 0 0 1-19.3-20a8 8 0 1 1 12.46-10c16.6 20.54 45.64 45 89.78 45s73.18-24.49 89.78-45a8 8 0 1 1 12.44 10a153.5 153.5 0 0 1-19.3 20l20 35a8 8 0 0 1-2.92 11";
const PH_SHIELD      = "M208 40H48a16 16 0 0 0-16 16v56c0 52.72 25.52 84.67 46.93 102.19c23.06 18.86 46 25.26 47 25.53a8 8 0 0 0 4.2 0c1-.27 23.91-6.67 47-25.53C198.48 196.67 224 164.72 224 112V56a16 16 0 0 0-16-16m0 72c0 37.07-13.66 67.16-40.6 89.42a129.3 129.3 0 0 1-39.4 22.2a128.3 128.3 0 0 1-38.92-21.81C61.82 179.51 48 149.3 48 112V56h160ZM82.34 141.66a8 8 0 0 1 11.32-11.32L112 148.69l50.34-50.35a8 8 0 0 1 11.32 11.32l-56 56a8 8 0 0 1-11.32 0Z";
const PH_SHIELD_FILL = "M208 40H48a16 16 0 0 0-16 16v56c0 52.72 25.52 84.67 46.93 102.19c23.06 18.86 46 25.26 47 25.53a8 8 0 0 0 4.2 0c1-.27 23.91-6.67 47-25.53C198.48 196.67 224 164.72 224 112V56a16 16 0 0 0-16-16m-34.32 69.66l-56 56a8 8 0 0 1-11.32 0l-24-24a8 8 0 0 1 11.32-11.32L112 148.69l50.34-50.35a8 8 0 0 1 11.32 11.32Z";
const PH_SPARKLE     = "M197.58 129.06L146 110l-19-51.62a15.92 15.92 0 0 0-29.88 0L78 110l-51.62 19a15.92 15.92 0 0 0 0 29.88L78 178l19 51.62a15.92 15.92 0 0 0 29.88 0L146 178l51.62-19a15.92 15.92 0 0 0 0-29.88ZM137 164.22a8 8 0 0 0-4.74 4.74L112 223.85L91.78 169a8 8 0 0 0-4.78-4.78L32.15 144L87 123.78a8 8 0 0 0 4.78-4.78L112 64.15L132.22 119a8 8 0 0 0 4.74 4.74L191.85 144ZM144 40a8 8 0 0 1 8-8h16V16a8 8 0 0 1 16 0v16h16a8 8 0 0 1 0 16h-16v16a8 8 0 0 1-16 0V48h-16a8 8 0 0 1-8-8m104 48a8 8 0 0 1-8 8h-8v8a8 8 0 0 1-16 0v-8h-8a8 8 0 0 1 0-16h8v-8a8 8 0 0 1 16 0v8h8a8 8 0 0 1 8 8";

function PhosphorIcon({ path, c, originX = 24, originY = 24 }) {
  const scale = 48 / 256;
  return (
    <g transform={`translate(${originX - 128 * scale} ${originY - 128 * scale}) scale(${scale})`}>
      <path d={path} fill={c} />
    </g>
  );
}

function LucideIcon({ path, c, sw = 2 }) {
  const scale = 48 / 24;
  return (
    <g transform={`scale(${scale})`}>
      <path d={path} fill="none" stroke={c} strokeWidth={sw}
        strokeLinecap="round" strokeLinejoin="round" />
    </g>
  );
}

// LucideMulti — Lucide icons that compose multiple primitives (paths +
// rects + circles). Caller passes the inner children; we set the common
// stroke style + scale to fit 48×48.
function LucideMulti({ c, sw = 2, children }) {
  const scale = 48 / 24;
  return (
    <g transform={`scale(${scale})`}>
      <g fill="none" stroke={c} strokeWidth={sw}
         strokeLinecap="round" strokeLinejoin="round">
        {children}
      </g>
    </g>
  );
}

// Helper that drops the Phosphor hand inside our 48×48 grid. The source is
// 256-unit; scale = 48/256 ≈ 0.1875. We also nudge it ~2 units to optically
// centre the glyph (palm is bottom-heavy).
function PhosphorHand({ c, filled = false, rotate = 0, originX = 24, originY = 25, pointing = false }) {
  const scale = 48 / 256;
  const path = pointing
    ? (filled ? PH_POINT_FILL : PH_POINT_OUTLINE)
    : (filled ? PH_HAND_FILL  : PH_HAND_OUTLINE);
  return (
    <g transform={`rotate(${rotate} ${originX} ${originY})`}>
      <g transform={`translate(${originX - 128 * scale} ${originY - 128 * scale}) scale(${scale})`}>
        <path d={path} fill={c} />
      </g>
    </g>
  );
}

const ILLU = {

  // ─── 1. Hand-mark variants — all derived from the Phosphor hand-palm ────

  // Open palm — the canonical brand glyph. Used for splash, chat hero,
  // wordmark, app icon, settings rows that refer to Handy itself.
  handOpen:   ({ c }) => <PhosphorHand c={c} />,

  // Filled variant — for the floating widget's disc and the tap-for-me
  // confirmation sheet, where stronger weight reads on small surfaces.
  handFill:   ({ c }) => <PhosphorHand c={c} filled />,

  // Waving — same glyph, gently tilted. No motion lines (would compete
  // with the now-strong silhouette).
  handWave:   ({ c }) => <PhosphorHand c={c} rotate={-8} />,

  // Pointing — replaced with Phosphor's hand-pointing (index up, fingers
  // curled). Reads as the classic click-cursor instead of a tilted palm.
  handPoint:  ({ c }) => <PhosphorHand c={c} pointing />,
  handPointFill: ({ c }) => <PhosphorHand c={c} pointing filled />,
  // Bold weight — used for the actual Pointing state in the widget where
  // the glyph sits in a blue tinted disc and needs more visual weight.
  handPointBold: ({ c }) => (
    <g>
      <g transform={`translate(${24 - 128 * (48/256)} ${25 - 128 * (48/256)}) scale(${48/256})`}>
        <path d={PH_POINT_BOLD} fill={c} />
      </g>
    </g>
  ),

  // ─── Updated utility icons ───────────────────────────────────────────
  // Composer + settings + widget glyphs come straight from Phosphor /
  // Lucide. Identical render at every size, and lets the Compose codegen
  // swap in `Icons.Filled.PaperPlane` etc. The canonical names (mic, send,
  // settings, cursor) are reused below — Phosphor paths just replace the
  // hand-drawn versions in place.
  // Phosphor mic & send are now the source; the old motifs above are dead.
  // Lucide cursor — minimal pointer arrow used by the widget's Pointing
  // state (replaces the rotated hand silhouette on small discs).
  cursor: ({ c }) => <LucideIcon path={LU_CURSOR} c={c} sw={2} />,

  // Tap-for-me — just the filled palm. The previous version had a target
  // dot above the fingertips; we dropped it for visual calm.
  handTap:    ({ c }) => <PhosphorHand c={c} filled />,

  // Pencil-for-me — palm with a small pencil glyph above. We keep the
  // hand-palm as the constant and let the tool float above it.
  handPencil: ({ c, s }) => (
    <g>
      <PhosphorHand c={c} />
      <g stroke={c} strokeWidth={s * 0.9} strokeLinecap="round" strokeLinejoin="round" fill="none">
        <path d="M38 6 L44 12" />
        <path d="M32 12 L38 6 L44 12 L38 18 Z" fill={c} />
        <path d="M30 14 L36 20" />
      </g>
    </g>
  ),

  // ─── Screen + element (tablet + phone) ─ Lucide tablet-smartphone ─────────────────
  screen: ({ c }) => (
    <LucideMulti c={c}>
      <rect width="10" height="14" x="3" y="8" rx="2" />
      <path d="M5 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2h-2.4" />
      <path d="M8 18h.01" />
    </LucideMulti>
  ),

  // ─── 3. Speech bubble with question mark ───────────────────────────────
  // ─── Ask / speech bubble ─ Lucide message-circle-question-mark ──────────
  ask: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719" />
      <path d="M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3" />
      <path d="M12 17h.01" />
    </LucideMulti>
  ),

  // ─── 4. Bolt ─────────────────────────────────────────────────────────
  bolt: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z" />
    </LucideMulti>
  ),

  // ─── 5. Shield with check ─────────────────────────────────────────────
  shield:     ({ c }) => <PhosphorIcon path={PH_SHIELD}      c={c} />,
  shieldFill: ({ c }) => <PhosphorIcon path={PH_SHIELD_FILL} c={c} />,
  sparkle:    ({ c }) => <PhosphorIcon path={PH_SPARKLE}     c={c} />,

  // ─── 6. Microphone — Phosphor mic ─────────────────────────────────────
  mic: ({ c }) => <PhosphorIcon path={PH_MIC} c={c} />,

  // ─── 7. Bell — Lucide bell ────────────────────────────────────────────
  bell: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M10.268 21a2 2 0 0 0 3.464 0m-10.47-5.674A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326" />
    </LucideMulti>
  ),

  // ─── 8. Overlay square with corner arrow (Draw over other apps) ───────
  overlay: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z" />
      <path d="M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12" />
      <path d="M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17" />
    </LucideMulti>
  ),

  // ─── 9. Eye with concentric arcs (Accessibility — see the screen) ─────
  eye: ({ c }) => <PhosphorIcon path={PH_EYE} c={c} />,

  // ─── Extras used inside settings rows ──────────────────────────────────
  // Lucide CPU — a cleaner, more techy AI brain icon.
  brain: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M12 20v2m0-20v2m5 16v2m0-20v2M2 12h2m-2 5h2M2 7h2m16 5h2m-2 5h2M20 7h2M7 20v2M7 2v2" />
      <rect width="16" height="16" x="4" y="4" rx="2" />
      <rect width="8"  height="8"  x="8" y="8" rx="1" />
    </LucideMulti>
  ),

  mask: ({ s, c }) => (
    // Incognito mask
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M7 22 H41" />
      <path d="M9 22 Q9 32 17 32 Q22 32 23 25 Q23.4 23 24 23 Q24.6 23 25 25 Q26 32 31 32 Q39 32 39 22" />
    </g>
  ),

  recipe: ({ s, c }) => (
    // Book/recipe — deterministic recipes
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 10 Q10 8 12 8 H22 Q24 8 24 10 V40 Q24 38 22 38 H12 Q10 38 10 40 Z" />
      <path d="M38 10 Q38 8 36 8 H26 Q24 8 24 10 V40 Q24 38 26 38 H36 Q38 38 38 40 Z" />
      <path d="M14 16 H20" /><path d="M14 21 H20" /><path d="M14 26 H18" />
      <path d="M28 16 H34" /><path d="M28 21 H34" /><path d="M28 26 H32" />
    </g>
  ),

  globe: ({ c }) => (
    <LucideMulti c={c}>
      <circle cx="12" cy="12" r="10" />
      <path d="M12 2a14.5 14.5 0 0 0 0 20a14.5 14.5 0 0 0 0-20M2 12h20" />
    </LucideMulti>
  ),

  clipboard: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <rect x="13" y="10" width="22" height="30" rx="2.6" />
      <rect x="18" y="7" width="12" height="6" rx="1.6" />
      <path d="M18 22 H30" /><path d="M18 27 H30" /><path d="M18 32 H26" />
    </g>
  ),

  tutor: ({ s, c }) => (
    // Mortarboard
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M6 20 L24 12 L42 20 L24 28 Z" />
      <path d="M14 24 V32 Q14 36 24 36 Q34 36 34 32 V24" />
      <path d="M42 20 V28" />
    </g>
  ),

  search: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <circle cx="21" cy="21" r="10" />
      <path d="M29 29 L38 38" />
    </g>
  ),

  // Phosphor paper-plane-right-fill. Original glyph for the chat composer.
  send: ({ c }) => <PhosphorIcon path={PH_SEND} c={c} />,

  chevron: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M18 12 L30 24 L18 36" />
    </g>
  ),

  check: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 25 L20 34 L38 14" />
    </g>
  ),

  close: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 12 L36 36" />
      <path d="M36 12 L12 36" />
    </g>
  ),

  // Phosphor gear-six. Used in the chat top bar and elsewhere as a
  // settings affordance.
  settings: ({ c }) => <PhosphorIcon path={PH_GEAR} c={c} />,

  back: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M30 12 L18 24 L30 36" />
    </g>
  ),

  // ─── Expand / open-in-new ─ Lucide square-arrow-out-up-right ───────────
  expand: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M21 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h6" />
      <path d="m21 3-9 9" />
      <path d="M15 3h6v6" />
    </LucideMulti>
  ),

  eye_off: ({ c }) => <PhosphorIcon path={PH_EYE_CLOSED} c={c} />,

  // Lucide camera — for the "what's in this photo" quick prompt.
  camera: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z" />
      <circle cx="12" cy="13" r="3" />
    </LucideMulti>
  ),

  // Lucide timer — for the "set a timer" quick prompt.
  timer: ({ c }) => (
    <LucideMulti c={c}>
      <line x1="10" x2="14" y1="2" y2="2" />
      <line x1="12" x2="15" y1="14" y2="11" />
      <circle cx="12" cy="14" r="8" />
    </LucideMulti>
  ),

  // ─── Minimise — Lucide minimize-2 (chevrons pointing inward) ────────
  // Used by the chat top-bar cluster + the floating context-bar cluster.
  minimise: ({ c }) => (
    <LucideMulti c={c}>
      <polyline points="4 14 10 14 10 20" />
      <polyline points="20 10 14 10 14 4" />
      <line x1="14" x2="21" y1="10" y2="3" />
      <line x1="3" x2="10" y1="21" y2="14" />
    </LucideMulti>
  ),

  // ─── Mic vocal — Lucide mic-vocal (microphone with curved wire) ──────
  // Used as the Speech-to-text subsection glyph. Distinct from the
  // input-composer `mic` glyph (which is a standard studio mic) — this
  // one has the dangling cord that reads as "live transcription / vocal
  // capture" in context.
  micVocal: ({ c }) => (
    <LucideMulti c={c}>
      <path d="m11 7.601-5.994 8.19a1 1 0 0 0 .1 1.298l.817.818a1 1 0 0 0 1.314.087L15.09 12" />
      <path d="M16.5 21.174C15.5 20.5 14.372 20 13 20c-2.058 0-3.928 2.356-6 2-2.072-.356-2.775-3.369-1.5-4.5" />
      <circle cx="16" cy="7" r="5" />
    </LucideMulti>
  ),

  // ─── Volume-2 — Lucide volume-2 (speaker with sound waves) ──────────
  // Used as the Text-to-speech subsection glyph. The two arcs convey
  // "Handy speaks back" cleanly at small sizes.
  volume2: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z" />
      <path d="M16 9a5 5 0 0 1 0 6" />
      <path d="M19.364 18.364a9 9 0 0 0 0-12.728" />
    </LucideMulti>
  ),

  // ─── Audio-lines — Lucide audio-lines (stacked equalizer bars) ──────
  // Used as the Voice section tile. Reads as "spoken voice / TTS output"
  // distinct from `mic` (input) — six staggered vertical bars suggest a
  // waveform/output meter.
  audioLines: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M2 10v3" />
      <path d="M6 6v11" />
      <path d="M10 3v18" />
      <path d="M14 8v7" />
      <path d="M18 5v13" />
      <path d="M22 10v3" />
    </LucideMulti>
  ),

  // ─── Key (small) — Lucide key-round, used as overline accent ──────
  keyHole: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M2 18 6 14" />
      <path d="m8 12 2 2" />
      <circle cx="16.5" cy="7.5" r="5.5" />
    </LucideMulti>
  ),

  // Lucide keyboard — used for type-for-me actions in audit log.
  keyboard: ({ c }) => (
    <LucideMulti c={c}>
      <path d="M10 8h.01M12 12h.01M14 8h.01M16 12h.01M18 8h.01M6 8h.01M7 16h10m-9-4h.01" />
      <rect width="20" height="16" x="2" y="4" rx="2" />
    </LucideMulti>
  ),

  // Lucide accessibility — used on the Accessibility permission row.
  // Reads as the platform a11y glyph (matches Android's own).
  a11y: ({ c }) => (
    <LucideMulti c={c}>
      <circle cx="16" cy="4" r="1" />
      <path d="m18 19l1-7l-6 1M5 8l3-3l5.5 3l-2.36 3.5m-6.9 3a5 5 0 0 0 6.88 6" />
      <path d="M13.76 17.5a5 5 0 0 0-6.88-6" />
    </LucideMulti>
  ),

  copy: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <rect x="14" y="14" width="22" height="24" rx="2.4" />
      <path d="M12 30 V12 Q12 10 14 10 H32" />
    </g>
  ),

  plus: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M24 10 V38" />
      <path d="M10 24 H38" />
    </g>
  ),

  trash: ({ s, c }) => (
    <g fill="none" stroke={c} strokeWidth={s} strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 14 H38" />
      <path d="M14 14 V38 Q14 40 16 40 H32 Q34 40 34 38 V14" />
      <path d="M19 11 H29 Q31 11 31 13 V14 H17 V13 Q17 11 19 11 Z" />
      <path d="M20 20 V34" /><path d="M28 20 V34" />
    </g>
  ),

  // ─── Warning ─ Lucide triangle-alert ──────────────────────────────
  warning: ({ c }) => (
    <LucideMulti c={c}>
      <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3" />
      <path d="M12 9v4" />
      <path d="M12 17h.01" />
    </LucideMulti>
  ),
};

function Illu({ name, size = 48, color, opacity = 1, style }) {
  const Inner = ILLU[name];
  if (!Inner) return null;
  const s = strokeFor(size);
  return (
    <svg width={size} height={size} viewBox="0 0 48 48"
         style={{ display: "block", opacity, ...style }}>
      <Inner s={s} c={color || "#D97757"} />
    </svg>
  );
}

// Tile — illustration inside a soft accent square (used as row-leading icon).
function IlluTile({ name, size = 36, tint, fg, soft }) {
  const t = useTheme();
  const bg = soft || t.colors.accentSoft;
  const c  = fg  || t.colors.accent;
  return (
    <div style={{
      width: size, height: size, borderRadius: 10,
      background: bg, display: "flex", alignItems: "center", justifyContent: "center",
      flex: "0 0 auto",
    }}>
      <Illu name={name} size={Math.round(size * 0.62)} color={c} />
    </div>
  );
}

window.Illu = Illu;
window.IlluTile = IlluTile;
window.ILLU_SIZES = ILLU_SIZES;
