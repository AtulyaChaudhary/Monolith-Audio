# Monolith Audio

**Monolith Audio** is a modern, high-performance Android music player built with **Kotlin** and **Jetpack Compose**. It features a physics-based syllable/word/line-synced lyrics stage, automatic and manual **Spicy Lyrics API** integration, local directory & SAF scanning with co-located lyrics discovery (`.ttml`, `.lrc`, `.txt`), granular **0.1x–5.0x Speed & Pitch DSP controls**, custom `.ttf`/`.otf` font uploads, and customizable **Material You**, **True Pitch-Black Monochrome**, and **Frosted Glass Aura** themes.

---

## Key Features

### 1. Physics-Driven Synced Lyrics Stage
- **Multi-Format Lyrics Engine**:
  - **Syllable & Word-Synced TTML (`.ttml`)**: Sub-word and word-by-word horizontal gradient sweep with zero premature letter bleed, spring-damped scale/Y-offset physics (`LyricsSpring`), and smooth word-to-word glow trail decay.
  - **LRC (`.lrc`)**: Supports both standard line-synced (`[mm:ss.xx]`) and enhanced word-synced (`<mm:ss.xx>`) LRC files.
  - **Plain Text (`.txt` / Static)**: Clean, high-contrast unsynced lyrics display with manual scroll and tap-to-toggle HUD.
- **Multi-Vocal Layout**:
  - **Main Vocals**: Left-aligned bold primary vocal lines.
  - **Background Vocals (`ttm:role="x-bg"`)**: Compact sub-lines rendered at 68% scale beneath or above the active vocal.
  - **Duo / Secondary Vocals (`ttm:agent="v2"`)**: Right-aligned duet vocal lines with right-to-left gradient sweep.
- **Auto-Hiding Controls & Exclusive Lyrics Settings Pop-Out**:
  - Transport controls and the seek bar stay hidden on the lyrics stage for an unobstructed view and appear only when tapping or dragging on the screen (auto-hiding after 3.5 seconds).
  - Dedicated **Settings** button on the lyrics stage opens a compact pop-out sheet to adjust **Background Blur Radius (`0dp–120dp`)**, **Protective Black Tint (`15%–80%`)**, **9-Orb Drift Speed (`0.0x–3.0x`)**, **Active Word Glow Intensity (`0.0x–2.0x`)**, **Lyrics Font Size (`16sp–42sp`)**, and **Per-Track Lyrics Timing Offset (`ms`)**.

### 2. Spicy Lyrics API Integration & Persistent Embedded Cache
- **Secret Key Management (`$SPICY_LYRICS_SECRET_KEY`)**:
  - Configure your Spicy Lyrics Secret Key (`sl_sk_...`) in Settings to replace `$SPICY_LYRICS_SECRET_KEY` across the fetch engine, or click **Revoke Secret Key** to reset back to the default placeholder.
- **Automatic Title & Artist Lookup + Strict Same-Song Verification**:
  - Automatically queries `api.spicylyrics.org` when playing a track using its Title, Artist, Album, and Duration.
  - Strictly validates that search candidates match the active song's title and artist so another song's TTML never overrides the current song's LRC or Plain Text lyrics.
  - Evaluates all linked IDs (`spotify`, `apple_music`, `isrc`, and `variants`) and selects the highest sync quality for that exact song:
    $$\text{Syllable Sync} > \text{Word Sync} > \text{Line Sync (LRC)} > \text{Plain Text (No Sync)}$$
- **Interactive Song Search & Direct ID Fetch**:
  - Built-in search modal (`/v1/tracks/search`) displaying full song titles, artists, album art thumbnails, duration, and match tiers, plus direct Track ID / ISRC lookup.
- **Credits & Attribution Footer**:
  - Formats songwriters (`Written by ...`) and provider/community credits (`Provided by Apple Music`, `Provided by Spotify`, or `Community lyrics • Made by @username` with circular avatar) at the bottom of the lyrics scroll.
- **Persistent Embedded Storage Lyrics Cache**:
  - Resolved lyrics stay cached on device storage (`filesDir/embedded_lyrics_cache/`) and in Room across app sessions until explicitly cleared via **"Clear Current Song Lyrics Cache"** or **"Clear All Lyrics Cache"**.

### 3. Theme Studio & Per-Theme Customizations
- **Material You Dynamic**:
  - High-contrast opaque surfaces tinted by the active album art palette, with customizable **Dynamic Accent Vibrancy**, **Surface Tonal Richness**, and an optional **Bleed Album Art Colors into Song List** toggle.
- **True Pitch-Black Monochrome (`#000000` & `#FFFFFF`)**:
  - Pure `#000000` OLED pitch-black and `#FFFFFF` white surfaces with zero blue tint, customizable **Pure White Border Contrast**, while keeping album covers and the Lyrics Stage in full color.
- **Frosted Glass Aura**:
  - Customizable **Glass Frost Opacity**, **Specular Rim Highlight**, **Universal Background Blur Radius**, and **9-Orb Album Art Backdrop** with a protective black tint layer so white lyrics remain legible even over bright white album covers.

### 4. Custom Typography (`.ttf` / `.otf` Font Upload)
- Upload custom `.ttf` or `.otf` font files independently for:
  1. **Entire UI Font** (headers, lists, buttons, modals, navigation)
  2. **Synced Lyrics Font** (main, duo, and background vocals)
- Includes one-tap reset to the default extra-bold typography (**Outfit** / **Plus Jakarta Sans** / **JetBrains Mono**).

### 5. Local Music Scanner, Categories & Strict Queue Engine
- **Fast Parallel Folder Scanning & Rescan**:
  - Connects local folders via Android Storage Access Framework (SAF) or imports individual audio/lyrics files (`.flac`, `.mp3`, `.wav`, `.m4a`, `.aac`, `.ogg`, `.opus`, `.ttml`, `.lrc`, `.txt`).
  - Automatically pairs co-located `<SongBaseName>.ttml`, `<SongBaseName>.lrc`, and `<SongBaseName>.txt` files in the same directory.
  - Off-thread `LruCache` cover art decoding (`RGB_565`) and throttled library state updates for 60fps zero-lag scrolling across large libraries.
- **Categories View**:
  - Browse by **Albums** (grid with prominent album art), **Folders**, **Artists**, and **Playlists** (with JSON import/export).
- **Strict Queue State Machine**:
  - Clicking a song in any view replaces the queue with that view's sorted track list.
  - Clicking **Add to Queue** inserts the track immediately after the currently playing song (`currentIndex + 1`).
- **Granular 0.1x–5.0x Speed & Pitch DSP**:
  - Independent playback speed and audio pitch sliders from `0.1x` to `5.0x` with `-0.1x` / `+0.1x` step buttons and `1.0x` reset.

---

## Project Structure

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # App entry point, edge-to-edge layout & state wiring
├── audio/
│   ├── DirectoryScanner.kt          # Parallel SAF & local directory scanner (.ttml / .lrc / .txt pairing)
│   └── MusicAudioEngine.kt          # MediaPlayer / PlaybackParams 0.1x–5.0x speed & pitch engine
├── data/
│   ├── AppDatabase.kt               # Room database & DAO (tracks, playlists, lyrics cache)
│   └── Models.kt                    # TrackEntity, QueueItem, PlaylistEntity, Album/Folder/Artist summaries
├── lyrics/
│   ├── LyricsAnimatorEngine.kt      # 60fps spring-damper & monotonic cubic spline lyrics animator
│   ├── SpicyLyricsClient.kt         # Spicy Lyrics REST client, search, multi-ID ranker & attribution parser
│   └── TtmlParser.kt                # Multi-format parser for .ttml, .lrc (line & enhanced), and .txt
├── ui/
│   ├── components/
│   │   ├── FrostedGlassComponents.kt # 9-orb album art backdrop, protective black tint, off-thread cover cache
│   │   ├── LibraryViews.kt          # Top studio bar, All Songs list, Albums/Folders/Artists/Playlists views
│   │   ├── LyricsStageView.kt       # Synced lyrics stage, auto-hide HUD, credits footer & pop-out settings
│   │   ├── PlayerAndQueueViews.kt   # Mini-player deck, 0.1x–5.0x Speed/Pitch sheet & Queue drawer
│   │   └── SettingsAndGlassModal.kt # Settings modal, Theme customizer & Spicy Lyrics search sheet
│   └── theme/
│       ├── Color.kt                 # Pitch-black monochrome & album palette color utilities
│       ├── CustomFontManager.kt     # User-uploaded .ttf / .otf font validator and loader
│       ├── Theme.kt                 # Material You, True Monochrome & Frosted Glass theme configurations
│       └── Type.kt                  # Bundled Outfit, Plus Jakarta Sans & JetBrains Mono typography
└── viewmodel/
    └── MusicPlayerViewModel.kt      # MVVM state holder, queue machine & persistent embedded lyrics cache
```

---

## Getting Started

### Prerequisites
- **Android Studio** (Ladybug or newer recommended)
- **JDK 17+**
- **Android SDK 36** (`minSdk = 24`, `targetSdk = 36`)

### Optional: Spicy Lyrics Secret Key
You can enter your Spicy Lyrics secret key (`sl_sk_...`) directly inside the app under **Settings → Spicy Lyrics Secret Key**, or configure it at build time in `.env`:

```env
SPICY_LYRICS_SECRET_KEY=sl_sk_your_secret_key_here
```

### Build & Run

1. **Clone the repository**:
   ```bash
   git clone <your-repo-url>
   cd monolith-audio
   ```

2. **Assemble the Debug APK**:
   ```bash
   gradle :app:assembleDebug
   ```

3. **Run Unit & Robolectric Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
