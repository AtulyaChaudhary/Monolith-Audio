# Monolith Audio — Scroll Performance, True Pitch-Black Monochrome, Per-Theme Customizers, Embedded Lyrics Cache, LRC/Plain-Text Support & Smooth Lyrics Stage

This update eliminates song-list scroll lag, restores full background controls (blur radius, black tint/dimmer, orb speed, word glow) for the Lyrics screen with a protective black tint over the blurred background so white lyrics are always legible over bright/white album covers, fixes True Monochrome mode to be strictly pitch-black (`#000000`) and white (`#FFFFFF`) with per-theme customization panels, adds persistent embedded disk + database lyrics caching with **"Clear Current Song Lyrics Cache"** and **"Clear All Lyrics Cache"** actions, adds full **LRC (Line-Synced)** and **Plain Text** lyrics support that never picks a different song's TTML over the exact song's LRC, and overhauls the Lyrics stage for butter-smooth line scrolling and word-to-word glow transitions.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> **Key Fixes & Architectural Upgrades**
> 1. **Protective Black Tint on the Lyrics Page Blur**:
>    - Adds a subtle, protective **black tint/scrim overlay** (default `~32%` black tint, customizable via the Lyrics Background Black Tint slider) directly over the blurred 9-orb album-art background on the Lyrics screen, plus automatic luminance clamping on near-white album art swatches, guaranteeing that white and dimmed lyrics remain 100% readable even when the album cover is mostly white or bright yellow/pastel.
> 2. **Zero-Lag Song List Scrolling (High Priority)**:
>    - **Decoupled 60fps Playback Clock**: The 60Hz lyrics/scrubber frame loop will no longer mutate the top-level library `MusicUiState` or trigger recomposition of `AllSongsListView`.
>    - **Off-Thread Thumbnail Cache**: `CrispAlbumArtCover` will use an in-memory `LruCache<String, ImageBitmap>` populated asynchronously on `Dispatchers.IO` with downsampled bitmaps instead of calling synchronous `BitmapFactory.decodeFile()` on the main thread during scroll.
>    - **Pre-Computed Palette & Memoized Sorting**: Track color palettes and sorted/filtered track lists are cached once per dataset/query change instead of recalculated on every frame.
> 3. **Restored Lyrics Background Settings & Per-Theme Customizations**:
>    - Restores the **Background Blur Radius Slider (`0dp–120dp`)**, **Lyrics Blur Black Tint Slider (`15%–75%`)**, **9-Orb Animation Speed Slider**, and **Word Glow Intensity Slider** directly inside both the **Lyrics Screen Settings Pop-Out Sheet** and **Main Settings**.
>    - Switching between themes (**Material You Dynamic**, **True Pitch-Black Monochrome**, and **Custom Frosted Glass**) dynamically reveals a dedicated **Theme Customization Panel** tailored to the active theme.
> 4. **True Pitch-Black & Pure White Monochrome Mode**:
>    - Removes all blue-tinted hex colors (`#0E0F14`, `#121318`, `#1C1E26`) from Monochrome mode. True Monochrome now uses strict `#000000` pitch black, pure neutral `#0D0D0D` / `#171717` elevation grays, and `#FFFFFF` pure white accents (while keeping Album Art covers and the Lyrics Stage background in full color unless toggled).
> 5. **Persistent Embedded Lyrics Cache + Cache Clearing Controls**:
>    - Every resolved lyrics payload (TTML, Spicy Lyrics JSON, LRC, or Plain Text) is stored in an embedded on-device cache (`filesDir/embedded_lyrics_cache/` + Room database) and loaded immediately offline on subsequent plays without re-fetching.
>    - Adds **"Clear Current Song Lyrics Cache"** and **"Clear All Lyrics Cache"** buttons in Settings (and in the Lyrics pop-out sheet).
> 6. **Strict Same-Song Matching + Full LRC (Line-Synced) & Plain Text Lyrics Support**:
>    - Fixes auto-lookup so it **never** selects another song's TTML over the actual song's LRC/Line or Plain Text lyrics. Candidate search results must pass strict title + artist verification for the current song first; only IDs belonging to that exact song compete on `Syllable > Word > Line (LRC) > Plain Text`.
>    - Adds full parsing and rendering for local (`.lrc`, `.txt`) and Spicy Lyrics API **Line-Synced (`Line` / LRC)** and **Plain Text (`Text` / `Static` / unsynced)** lyrics.
> 7. **Jitter-Free Line Switching & Smooth Word-to-Word Glow Transitions**:
>    - Eliminates line-switch jitter by removing conditional `Modifier.blur()` node attachment/detachment on lyric rows and replacing `LazyColumn.animateScrollToItem` jumps with a continuous, damped scroll animation to center the active line smoothly.
>    - Replaces multi-branch `Text` switching in `SyllableWordView` with a single persistent `Text` layout and an interpolated glow/fill state so the highlight and glow glide smoothly from word to word without layout invalidation.

---

## 1. Overview & Core Concept

- **What It Does**: Delivers a lag-free local music library, customizable per-theme studio surfaces (including a true `#000000` / `#FFFFFF` Monochrome mode), a contrast-protected Lyrics blur backdrop with a built-in black tint for white album covers, an embedded offline lyrics cache with explicit cache-clearing controls, complete support for Syllable (`.ttml`), Word, Line (`.lrc`), and Plain Text (`.txt`) lyrics with accurate same-song resolution, and a 60fps jitter-free lyrics stage.
- **Key Value**: Scrolling hundreds of local songs is instant, lyrics are always readable even over bright white album art, lyrics never get replaced by a wrong song's TTML, cached lyrics persist across restarts until explicitly cleared, and word/line transitions animate smoothly.

---

## 2. User Experience & Visual Design

- **True Pitch-Black Monochrome vs. Material You vs. Custom Frosted Glass**:
  - **True Monochrome Mode**: Pure `#000000` background, `#0A0A0A` cards, `#1F1F1F` borders, and `#FFFFFF` / `#B0B0B0` text—zero blue tint. Includes customizers for *Pure OLED `#000000` Cards*, *Border Contrast*, and *Keep Lyrics Screen Colored*.
  - **Material You Dynamic Mode**: Tonal palette seeded from the active album art with customizers for *Album Art Color Bleed into Song List*, *Tonal Surface Richness*, and *Accent Vibrancy*.
  - **Custom Frosted Glass Mode**: Translucent surfaces over the single universal 9-orb backdrop with customizers for *Universal Blur Radius (`0dp–120dp`)*, *Surface Frost Opacity*, and *Specular Edge Highlight*.
- **Lyrics Screen Background Blur & Protective Black Tint**:
  - The 9-orb blurred background on the Lyrics page includes a permanent, subtle **black tint overlay** (default `0.32f` alpha, adjustable from `0.15f` to `0.75f`) plus luminance clamping on white swatches so bright/white album covers never wash out white lyric text.
  - Tapping the **Settings** button on the Lyrics screen opens the pop-out sheet containing live sliders for **Background Blur Radius (`0dp–120dp`)**, **Lyrics Blur Black Tint (`15%–75%`)**, **9-Orb Motion Speed**, **Word Glow Intensity**, **Lyrics Font Size**, **Sync Offset (`-100ms` / `+100ms`)**, and **"Clear Current Song Lyrics Cache"**.
- **Smooth Line & Word Progression**:
  - Active lines scale and brighten smoothly using hardware-accelerated `graphicsLayer` properties without changing row height or detaching render nodes.
  - Word glow smoothly ramps up as a word becomes active and gently trails off over `180ms` after the word finishes, creating a continuous baton-pass glow from word to word.
  - Line-synced (`.lrc` / `Line`) lyrics highlight the entire active line with a smooth opacity and scale transition, while Plain Text (`.txt` / `No Sync`) lyrics render in a clean, high-contrast scrollable reader view.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Protective Black Tint on Lyrics Blur Backdrop**
  - *Chosen Approach*: Render a dedicated black tint layer (`Color.Black.copy(alpha = lyricsBlackTintAlpha)`) over the blurred 9-orb canvas on the Lyrics screen and cap excessive white luminance on extracted orb colors.
  - *Why*: Ensures crisp contrast for both active white words (`100%` alpha) and unsung words (`40%` alpha) even when the album art is predominantly white.
- **Decision 2: Decoupling High-Frequency Playback Progress from Library State**
  - *Chosen Approach*: Move 60fps position/lyrics frame ticks out of the library state flow and cache decoded cover thumbnails in an async `LruCache`.
  - *Why*: Updating a single monolithic `MusicUiState` 60 times per second forced Compose to re-evaluate the song list and re-run list sorting while scrolling. Separating playback progress and caching bitmaps off-thread makes `LazyColumn` scrolling completely smooth.
- **Decision 3: Strict Same-Song Verification Before Sync-Type Ranking**
  - *Chosen Approach*: Filter `/v1/tracks/search` results by normalized title and artist match against the playing track before comparing sync granularity (`Syllable > Word > Line > Plain Text`).
  - *Why*: Prevents unrelated or loosely related tracks that happen to have TTML from overriding the exact song's LRC or line-synced lyrics.
- **Decision 4: Embedded On-Disk + Room Lyrics Cache**
  - *Chosen Approach*: Persist every track's resolved lyrics payload and attribution into an embedded file cache (`filesDir/embedded_lyrics_cache/`) linked in Room, with explicit **"Clear Current Song Lyrics Cache"** and **"Clear All Lyrics Cache"** buttons.
  - *Why*: Guarantees instant offline lyrics loading on every replay and gives the user full control over when cached lyrics are invalidated.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        MONOLITH AUDIO ARCHITECTURE                         │
└───────────────────────────────────┬────────────────────────────────────────┘
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        ▼                                                       ▼
┌───────────────────────────────────┐   ┌────────────────────────────────────┐
│  1. ZERO-LAG LIBRARY & THEMES     │   │  2. EMBEDDED LYRICS & SYNC ENGINE  │
│  • Decoupled Library vs 60Hz Clock│   │  • Protective Black Tint on Blur   │
│  • Async IO LruCache Cover Loader │   │  • Embedded Lyrics Storage Cache   │
│  • Memoized Track Filter & Sort   │   │    (TTML / Spicy / LRC / PlainTxt) │
│  • Per-Theme Customizer Panels:   │   │  • Clear Current / Clear All Cache │
│    - True Monochrome (#000 / #FFF)│   │  • Strict Same-Song Matcher:       │
│    - Material You Dynamic         │   │    Exact Song LRC > Other TTML     │
│    - Custom Frosted Glass         │   │  • Jitter-Free Line Scroll &       │
└───────────────────────────────────┘   │    Smooth Word-to-Word Glow Trail  │
                                        └────────────────────────────────────┘
```

### Interactive Component & State Mapping
- **Theme Switcher & Per-Theme Customizer (`SettingsAndGlassModal`)**: Selecting any theme mode immediately switches the active color scheme and expands that theme's specific sliders and toggles.
- **Lyrics Background Controls (`LyricsQuickSettingsPopoutSheet` & `SettingsModalSheet`)**: Adjusting **Background Blur (`0dp–120dp`)**, **Lyrics Blur Black Tint (`15%–75%`)**, **Orb Speed**, or **Glow Intensity** updates `PlayerSettingsState` in real time.
- **Embedded Lyrics Cache Controls (`MusicPlayerViewModel`)**:
  - `clearCurrentTrackLyricsCache()`: Deletes the embedded cache file and Room columns (`spicyLyricsJson`, `embeddedTtmlContent`, `manualTtmlContent`, `lyricsAttributionJson`) for the active song and resets the stage so the user can re-fetch or load fresh lyrics.
  - `clearAllLyricsCache()`: Purges the entire `embedded_lyrics_cache` directory and clears cached lyrics across all tracks in Room.
- **Unified Lyrics Parser (`TtmlParser`, `LrcAndPlainParser`, `SpicyLyricsClient`)**:
  - Parses `.ttml` (Syllable/Word), `.lrc` (Line-synced `[mm:ss.xx]`), and `.txt` / `Plain` (unsynced lines), flagging each line with its `LyricsSyncPriority` (`SYLLABLE`, `WORD`, `LINE`, `NO_SYNC`) so `LyricsStageView` animates syllables, words, whole lines, or static reader lines appropriately.
