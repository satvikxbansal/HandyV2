# Vector Drawables — All SVG icons as Android `<vector>` resources

The HTML prototype draws icons inline as SVG. For the Kotlin app, ship these
as drawable XML resources in `app/src/main/res/drawable/`. All icons share
the same `viewport=24x24`, stroke-width 1.6, round caps.

> **Tint at runtime** with `tint = HandyColors.Accent` (or any color) on the
> Compose `Icon`. Don't hard-code colors in the drawable — use
> `?attr/colorOnSurface` or `#FFFFFFFF` and tint at the call site.

---

## ic_hand_mark.xml — the Handy logo

Used everywhere: floating widget center, overlay header, full-app header,
empty-state hero, permissions hero. **Do not** swap for an emoji or stock
icon.

```xml
<!-- res/drawable/ic_hand_mark.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path
        android:pathData="M8.5,11 V5.75 a1.25,1.25 0 0 1 2.5,0 V11
                          M11,11 V4.25 a1.25,1.25 0 0 1 2.5,0 V11
                          M13.5,11 V5 a1.25,1.25 0 0 1 2.5,0 V12
                          M16,8.75 a1.25,1.25 0 0 1 2.5,0 V14
                            c0,3.5 -2.5,6.25 -6.25,6.25
                            S6,17.5 6,14 v-1.75 a1.25,1.25 0 0 1 2.5,0"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

In Compose, use it via `painterResource(R.drawable.ic_hand_mark)` and tint at
the site. Wrap in a helper:

```kotlin
@Composable
fun HandMark(size: Dp = 24.dp, tint: Color = HandyColors.Accent) {
    Icon(
        painter = painterResource(R.drawable.ic_hand_mark),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint,
    )
}
```

---

## Other icons (24×24 viewport, 1.6 stroke, round caps)

```xml
<!-- ic_mic.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:pathData="M9,3 h6 a3,3 0 0 1 3,3 v6 a3,3 0 0 1 -3,3 h-6 a3,3 0 0 1 -3,-3 v-6 a3,3 0 0 1 3,-3 z M6,11.5 A6,6 0 0 0 18,11.5 M12,17.5 V21 M9,21 h6"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_send.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M4,12 L20,4 L17,20 L13,13 Z M13,13 L20,4"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_close.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M6,6 L18,18 M18,6 L6,18"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.7"
          android:strokeLineCap="round" />
</vector>

<!-- ic_expand.xml — corners point OUTWARD (maximise) -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M4,10 V4 h6 M20,14 v6 h-6 M4,4 l7,7 M20,20 l-7,-7"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_collapse.xml — corners point INWARD (minimise, complements expand) -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M15,4 v5 h5 M9,20 v-5 H4 M4,4 l6,6 M20,20 l-6,-6"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_brain.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M9,4.5 a2.5,2.5 0 0 1 3,0 a2.5,2.5 0 0 1 3,0 c1.7,0 3,1.3 3,3 c0,0.5 -0.1,1 -0.3,1.4 c1.3,0.5 2.3,1.8 2.3,3.3 c0,1.3 -0.7,2.4 -1.8,3 c0.5,0.5 0.8,1.2 0.8,2 c0,1.7 -1.3,3 -3,3 c-0.5,0 -1,-0.1 -1.4,-0.3 c-0.3,1 -1.2,1.8 -2.3,2 a2.5,2.5 0 0 1 -2.6,0 c-1.1,-0.2 -2,-1 -2.3,-2 c-0.4,0.2 -0.9,0.3 -1.4,0.3 c-1.7,0 -3,-1.3 -3,-3 c0,-0.8 0.3,-1.5 0.8,-2 c-1.1,-0.6 -1.8,-1.7 -1.8,-3 c0,-1.5 1,-2.8 2.3,-3.3 A3,3 0 0 1 6,7.5 c0,-1.7 1.3,-3 3,-3 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.4"
          android:strokeLineJoin="round" />
</vector>

<!-- ic_modes.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M3,5 h18 a2,2 0 0 1 2,2 v2 a2,2 0 0 1 -2,2 H3 a2,2 0 0 1 -2,-2 V7 a2,2 0 0 1 2,-2 z M3,13 h18 a2,2 0 0 1 2,2 v2 a2,2 0 0 1 -2,2 H3 a2,2 0 0 1 -2,-2 v-2 a2,2 0 0 1 2,-2 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"/>
    <path android:pathData="M7,8 m-1.3,0 a1.3,1.3 0 1 0 2.6,0 a1.3,1.3 0 1 0 -2.6,0
                            M17,16 m-1.3,0 a1.3,1.3 0 1 0 2.6,0 a1.3,1.3 0 1 0 -2.6,0"
          android:fillColor="#FFFFFF"/>
</vector>

<!-- ic_bolt.xml (triggers / lightning) -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M13,3 L5,14 h6 l-1,7 L18,10 h-6 L13,3 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"
          android:strokeLineJoin="round" />
</vector>

<!-- ic_globe.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M12,3 a9,9 0 1 0 0,18 a9,9 0 1 0 0,-18 z M3,12 h18 M12,3 a14,14 0 0 1 0,18 a14,14 0 0 1 0,-18 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"/>
</vector>

<!-- ic_settings.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M12,9.5 a2.5,2.5 0 1 0 0,5 a2.5,2.5 0 1 0 0,-5 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"/>
    <path android:pathData="M19.4,15 a1.7,1.7 0 0 0 0.3,1.8 l0.1,0.1 a2,2 0 1 1 -2.8,2.8 l-0.1,-0.1 a1.7,1.7 0 0 0 -1.8,-0.3 a1.7,1.7 0 0 0 -1,1.5 V21 a2,2 0 1 1 -4,0 v-0.1 a1.7,1.7 0 0 0 -1.1,-1.5 a1.7,1.7 0 0 0 -1.8,0.3 l-0.1,0.1 a2,2 0 1 1 -2.8,-2.8 l0.1,-0.1 a1.7,1.7 0 0 0 0.3,-1.8 a1.7,1.7 0 0 0 -1.5,-1 H3 a2,2 0 1 1 0,-4 h0.1 A1.7,1.7 0 0 0 4.6,9 a1.7,1.7 0 0 0 -0.3,-1.8 l-0.1,-0.1 a2,2 0 1 1 2.8,-2.8 l0.1,0.1 a1.7,1.7 0 0 0 1.8,0.3 H9 a1.7,1.7 0 0 0 1,-1.5 V3 a2,2 0 1 1 4,0 v0.1 a1.7,1.7 0 0 0 1,1.5 a1.7,1.7 0 0 0 1.8,-0.3 l0.1,-0.1 a2,2 0 1 1 2.8,2.8 l-0.1,0.1 a1.7,1.7 0 0 0 -0.3,1.8 V9 a1.7,1.7 0 0 0 1.5,1 H21 a2,2 0 1 1 0,4 h-0.1 a1.7,1.7 0 0 0 -1.5,1 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.4"/>
</vector>

<!-- ic_history.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M4,12 a8,8 0 1 0 2.5,-5.8 L4,9 M4,4 v5 h5 M12,8 v4 l3,2"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_check.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M5,12 l5,5 L20,6"
          android:strokeColor="#FFFFFF" android:strokeWidth="2"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_eye.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M2,12 c0,0 3.5,-7 10,-7 s10,7 10,7 -3.5,7 -10,7 -10,-7 -10,-7 z M12,9 a3,3 0 1 0 0,6 a3,3 0 1 0 0,-6 z"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"/>
</vector>

<!-- ic_copy.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M8,4 h12 v14 h-12 z M16,20 H6 a2,2 0 0 1 -2,-2 V8"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.5"
          android:strokeLineCap="round"/>
</vector>

<!-- ic_chevron_back.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M15,6 l-6,6 6,6"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.8"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>

<!-- ic_chevron_right.xml -->
<vector ... viewportWidth="24" viewportHeight="24">
    <path android:pathData="M9,6 l6,6 -6,6"
          android:strokeColor="#FFFFFF" android:strokeWidth="1.6"
          android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```

---

## Quick mapping — Compose `Icon` calls

```kotlin
// Use `painterResource` everywhere; tint at the call site.
Icon(painterResource(R.drawable.ic_mic),         null, tint = HandyColors.TextPrimary)
Icon(painterResource(R.drawable.ic_send),        null, tint = HandyColors.AccentInk)
Icon(painterResource(R.drawable.ic_close),       null, tint = HandyColors.TextSecondary)
Icon(painterResource(R.drawable.ic_expand),      null, tint = HandyColors.TextSecondary) // overlay → expand to full app
Icon(painterResource(R.drawable.ic_collapse),    null, tint = HandyColors.TextSecondary) // full app → minimise to overlay
Icon(painterResource(R.drawable.ic_brain),       null, tint = HandyColors.Accent)        // settings section icon
Icon(painterResource(R.drawable.ic_modes),       null, tint = HandyColors.Accent)
Icon(painterResource(R.drawable.ic_bolt),        null, tint = HandyColors.Accent)
Icon(painterResource(R.drawable.ic_globe),       null, tint = HandyColors.Accent)
Icon(painterResource(R.drawable.ic_settings),    null, tint = HandyColors.TextSecondary)
Icon(painterResource(R.drawable.ic_history),     null, tint = HandyColors.TextSecondary)
Icon(painterResource(R.drawable.ic_check),       null, tint = HandyColors.Success)
Icon(painterResource(R.drawable.ic_eye),         null, tint = HandyColors.TextSecondary)
Icon(painterResource(R.drawable.ic_copy),        null, tint = HandyColors.TextSecondary)
Icon(painterResource(R.drawable.ic_chevron_back),null, tint = HandyColors.TextPrimary)
```

---

## Sanity check

After dropping in the drawables, build the project. If a path doesn't render
correctly (paths are tricky to copy), the issue is almost always:

1. **Wrong viewport** — must be `24×24` for the values above
2. **Missing `M ... z` close** — multi-segment paths need explicit `z` between sub-paths
3. **Stroke vs fill** — these are stroke icons. Set `android:strokeColor`, NOT
   `android:fillColor`. The hand mark, mic, send, close, expand, collapse,
   chevrons, history, check, eye, copy are all stroke-only.

If anything looks off, open `prototype/components/handy-primitives.jsx` —
search for `Icon = {` — that has the original SVG path data.
