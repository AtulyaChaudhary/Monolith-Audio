# Monolith Audio — Refined TTML Lyrics Stage, Custom Fonts & Clean Local Library Plan

This updated plan addresses every issue and feature request from your feedback and reference screenshot.

## User Review & Critical Decisions

> [!IMPORTANT]
> All six adjustments requested in your feedback have been incorporated directly into the architecture below:

1. **Zero Example / Default Songs**: All pre-seeded demo tracks (`"Tomato"`, `"Potato"`, `"Carrot"`, `"Glass Horizon"`, etc.) are completely removed and purged from local storage. The app starts with a clean, empty library ready for your own folders and audio/`.ttml` files.
2. **Opaque, Legible Settings & Modal Backdrops**: Settings, Speed/Pitch, Queue, and Glass Customizer sheets now use a high-opacity dark frosted surface (`>= 92%` opacity with a dark backdrop scrim) so background library text never bleeds through.
3. **Strict Word & Letter Highlight Timing (No Premature 1.5-Letter Bleed)**:
   - Words and letters in the `NotSung` state render strictly at a uniform dim resting opacity (`35%` white) with **zero** leading white gradient stop.
   - The white sweep highlight only begins at the exact millisecond a word's `[begin, end]` window becomes `Active`, using the word's true measured pixel width (`onSizeChanged`).
4. **Main, Smaller Background & Right-Side Duo Lyrics (Matching Reference Image)**:
   - **Main Vocals (`v1` / default `<p>`)**: Large, bold left-aligned lyrics.
   - **Background Vocals (`ttm:role="x-bg"` — both inline spans and dedicated sub-lines)**: Rendered as smaller, softer synced sub-lines directly beneath the main vocal line (e.g., `"Angel, angel"` under `"That's my part time angel"`), animated word-by-word.
   - **Duo / Secondary Vocals (`ttm:agent="v2"` or non-primary vocalist)**: Rendered aligned/indented to the right side of the lyrics stage.
5. **Custom Font Upload (`.ttf` / `.otf`) for Both Lyrics & Entire UI**:
   - Users can upload any `.ttf` or `.otf` font file from their device separately for **Entire UI Typography** and **Synced Lyrics Typography** (persisted locally in app storage with one-tap reset).
6. **Full-Color Lyrics Stage in Ultra Monochrome Mode**:
   - Even when the main library and player chrome are set to **Pure Monochrome Mode**, opening the **Lyrics Screen** preserves the vibrant, full-color blurred album-art aura and full-color album artwork shown in your reference screenshot.

---

## 1. Overview & Core Concept

- **What It Does**: Connects directly to the user's local device folders and audio files (with zero pre-installed bloat tracks), pairs audio files with co-located `.ttml` lyrics in the same directory, renders Apple Music / Spicy Lyrics-style **Main**, **Background (smaller sub-line)**, and **Duo (right-aligned)** word-by-word animations, and supports custom `.ttf`/`.otf` fonts for both the UI and lyrics.

---

## 2. User Experience & Visual Design

### Reference-Matched Lyrics Stage Layout
- **Album Art & Scrubber Column + Multi-Vocalist Lyrics Viewport**:
  - Matches the uploaded reference layout: crisp, full-color Album Art with a clean progress bar (`0:50 ─── 2:48`), bold centered Track Title, and Artist subtitle alongside the scrolling lyrics stage.
  - **Vibrant Album-Art Aura in All Modes**: Even when Ultra Monochrome Mode is active for the library UI, the Lyrics Screen retains the rich, multi-color blurred album-art backdrop and full-color cover art.
- **Three-Tier Vocal Hierarchy in `.ttml`**:
  - **Main Vocal (`ttm:agent="v1"` or default)**: Full-size bold typography (`1.0x` scale), left-aligned.
  - **Background Vocal (`ttm:role="x-bg"`)**: Extracted from nested `<span ttm:role="x-bg">` or `<p ttm:role="x-bg">` tags and rendered as a smaller (`0.72x` font size) synced line directly below its parent vocal line, with its own word-by-word sweep.
  - **Duo / Second Vocalist (`ttm:agent="v2"`, `v2`, `person2`, or right-aligned agent)**: Indented and right-aligned on the lyrics stage so duet call-and-response passages are visually distinct.
- **High-Contrast Modal & Settings Readability**:
  - All bottom sheets and settings modals apply a deep `#0B0B0F` `94%` opacity frosted surface + dark dim scrim over the underlying screen so background text is completely hidden while configuring settings.

---

## 3. Technical Architecture & Fixes

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                        UPDATED MONOLITH AUDIO ENGINE                       │
│                                                                            │
│  ┌───────────────────────────────────────┐  ┌───────────────────────────┐  │
│  │        CLEAN LOCAL LIBRARY            │  │   FULL-COLOR LYRICS STAGE │  │
│  │  • Zero Pre-Seeded Demo Songs         │  │  • Always Colored Aura    │  │
│  │  • SAF Folder & File Import           │  │    (even in Mono Mode)    │  │
│  │  • Custom UI Font (.ttf/.otf)         │  │  • Custom Lyrics Font     │  │
│  │  • Opaque 94% Settings & Modals       │  │  • Main (Left, 1.0x)      │  │
│  └───────────────────────────────────────┘  │  • BG Vocal (Sub, 0.72x)  │  │
│                                             │  • Duo Vocal (Right, v2)  │  │
│                                             └─────────────┬─────────────┘  │
└───────────────────────────────────────────────────────────┼────────────────┘
                                                            ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                 FIXED WORD & LETTER SWEEP RENDERER                         │
│  • NotSung State -> 100% Uniform Dim Alpha (Zero 1.5-letter white bleed)   │
│  • Active State  -> True Pixel Width Sweep (0.0 -> 1.0 over [begin, end])  │
│  • Sung State    -> 100% Lit White                                         │
└────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Implementation Fixes
1. **Fixing the 1.5-Letter Premature Visibility Bug**:
   - In `LyricsAnimatorEngine.kt` and `LyricsStageView.kt`:
     - Previously, `NotSung` words had `currentGradientPercent = -20f`, which mapped to `((-20 + 50) / 150) = 0.20` (`20%` of the word width), causing the first ~1.5 letters of every unsung word to light up prematurely.
     - Now, `NotSung` words and letters explicitly report `fillFraction = 0f` and render with a solid, un-gradiented dim color (`Color.White.copy(alpha = 0.34f)`).
     - Only when `word.state == ElementVocalState.Active` does `fillFraction` interpolate smoothly from `0f` to `1f` across the word's actual `onSizeChanged` pixel width.
2. **Parsing Main, Background (`x-bg`), and Duo (`v2`) Vocals in `TtmlParser.kt`**:
   - Reads `ttm:agent` / `agent` on `<p>` tags: if a second distinct agent (e.g., `v2`, `m2`, or any agent different from the primary `v1` agent) is present, marks the line `isDuoLine = true` (right-aligned).
   - Reads `ttm:role="x-bg"` on both `<p>` tags and nested `<span>` tags: if a `<p>` contains both main spans and `ttm:role="x-bg"` background spans (the standard Apple Music `.ttml` structure!), `TtmlParser` splits the `x-bg` spans into a dedicated companion `isBgLine = true` sub-line positioned immediately below the main line so both animate simultaneously!
3. **Custom `.ttf` / `.otf` Font Loader (`CustomFontManager`)**:
   - Copies user-selected `.ttf` or `.otf` files via `ContentResolver` into `context.filesDir/fonts/custom_ui.ttf` and `context.filesDir/fonts/custom_lyrics.ttf`.
   - Builds `FontFamily(Font(file))` dynamically and injects them into `MyApplicationTheme` and `LyricsStageView`.
