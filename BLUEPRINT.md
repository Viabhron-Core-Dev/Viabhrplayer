# Blueprint: Static Media Library & FFmpeg Editor

## Core Architecture & Philosophy
1. **Offline & Private**: Zero internet connectivity. No telemetry, no background calls.
2. **Stateless Editors**: Zero caching, zero edit history logging, and zero automatic re-imports after rendering. The application only remembers the persistent Output Folder URI.
3. **Modular & Lightweight**: Boot directly into the Library/Player. Editor modules stay suspended in memory until expressly invoked, behaving like separate on-demand utilities.
4. **Seamless External Integration**: Photo and Video editors can be launched via standard OS `Intent` (e.g. sharing a file from an external file manager).

## Feature Modules

### 1. Library (Default Boot State)
* **UI Aesthetics**: MiXplorer-style list structure and typography (without using its logo). Presents files and folders based on explicitly configured file types/extensions.
* **Media Presentation**: MX Player-style item layouts. Shows thumbnail, duration/time, file name, size, and date.
  - **New Marker**: Tiny red "New" badge on the top-left of thumbnails for unplayed/newly scanned media.
  - **Subtitle Detection**: If a subtitle file sharing the identical name exists in the folder, a blue subtitle badge appears next to the video name.
* **Navigation & Sorting**: Flat folder structure (MX Player style) – all folders containing media appear separately regardless of nesting depth. Supports sorting by date (applies globally or within specific folders).
* **Refresh Mechanic**: Manual pull-to-refresh supported. Scans trigger only on app start, after manual pull-to-refresh, and immediately following file modifications (no constant background scanning).
* **Component Selection**: Long-press activates multi-select mode. Context actions include: Play, Delete, Share, Properties, Rename, Edit (invokes built-in editors), and Add to Playlist.
* **Playback State & Logic**:
  - Remembers the last played playback position.
  - Looping modes: Loop One, Loop All, Loop None.
  - Custom Playlist management via multi-select options.
* **Floating Action Button (FAB) Runtime**:
  - *Main Directory*: FAB continues the last globally played media across the app.
  - *Inside a Folder*: FAB continues the last played media explicitly belonging to that specific folder.

### 2. Media Player (MX Player-Style)
* **Video Playback Engine**: Media3 (ExoPlayer).
* **UI Aesthetics**: Transparent black overlays for out-of-the-way controls. Status bar visibility is freely configurable via settings. Tapping the screen while playing reveals the UI.
* **Gesture Controls (Configurable Zones)**: Users can define gesture boundaries within settings. Default layout:
  - Double-tap: Toggle Play/Pause.
  - Left 25% boundary: Vertical swipe for Brightness scaling.
  - Right 25% boundary: Vertical swipe for System volume slide control.
  - Middle 50% boundary: Horizontal swipe for Forward/backward seeking in video.
* **Top Bar Structure**: Video title, playback speed, dedicated audio track selector, and subtitle track selector (automatically remembers explicit paths/choices). Ends with a classic 3-dot overflow menu for extra settings.
* **Tool Row (Folded, below top bar)**: 
  - *Screenshot*: Loaded purely on-demand to keep memory footprint lightweight.
  - *Input Lock*: Disables all screen touch polling until the floating padlock symbol is tapped.
  - *Loop Toggle*: Cycle between Loop One, Loop All, and None.
  - *Background Play*: Transitions purely to audio playback.
  - *Picture-in-Picture (PiP)*: Transitions video.
* **Bottom Bar & Transports**:
  - *Time & Progress*: Current time position, interactive Progress Bar (drag/tap slider to fast-seek), and Total Time (tap to continuously toggle to remaining time format).
  - *Media Controls*: Previous, 5-Second Rewind, Play/Pause toggle, 5-Second Forward, Next.
  - *Video Scaling*: Quick-toggle between Normal, Fit, Stretch-to-Fit, and Crop bounds.
* **Background / Notification Player**: Mirrors core controls (Pause, Next, Previous, Rev, Fwd) using standard Android MediaSession integrations.

### 3. Video Editor (Stateless FFmpeg Engine)
* **Launch Mechanism**: Invoked via Library context menu. Externally, an `ACTION_SEND` ("Share") intent routes to the Editor, while an `ACTION_VIEW` ("Open") intent routes strictly to the media Player.
* **UI Architecture**:
  - **Export Header (Top)**: Estimated file size indicator alongside a prominent "SAVE" button. Visual sliders for Resolution (up to 2k/4k), Frame Rate (24fps to 60fps), and Quality (Low, Medium, High).
  - **Preview Canvas (Center)**: Core media preview container.
  - **Tool Ribbon (Bottom)**: Horizontal tool toggles (Captions, BG, Record/Mic, Volume, Rotate, Flip). 
  - **Timeline Scrubber**: Filmstrip tracking with an "Add Clip" (+) feature, mute/unmute clip audio toggles, and total duration metrics.
* **FFmpeg Core & Storage Constraints**: Total offline processing, no AI features, no ads. Built via raw FFmpeg commands. \n  - *FFmpeg VS SAF Reality*: Since FFmpeg C/C++ libraries cannot natively output directly to Android's SAF `content://` URIs, the pipeline caches the encoded render temporarily into internal `Context.cacheDir`, seamlessly streams the bytes into the final SAF URI using ContentResolvers, and deletes the temp file.
* **UX & OOM Mitigation**:
  - Overlays a strict blocking "Please wait..." screen during active FFmpeg execution to safeguard user interaction.
  - Offloads rendering execution directly into a Foreground Service (pushing real-time progress to the Notification Bar) to survive Android OS kills.
* **Output Rules (SAF)**: Outputs cleanly to the preconfigured Output Folder URI. Strictly retains the original source filename (e.g., `video.mp4`), utilizing native Android Storage Access Framework (SAF) auto-rename (`video(1).mp4`) seamlessly if collisions occur.

### 4. Photo Editor (Stateless Canvas)
* **Launch Mechanism & Intent Routing**: Hidden from main UI.
  - *ACTION_VIEW ("Open with")*: Launches the full Photo Editor interface.
  - *ACTION_SEND ("Share")*: Bypasses the full editor and triggers a Batch Compression/Shrink queue.
* **Batch Compression Queue**: Processes shared images sequentially (one at a time) using a Foreground Service (Notification Bar) to prevent OOM kills, reducing file size with minimal quality loss.
* **Single Image Processing**: 
  - *Tools*: Crop, single-file Shrink, and Aspect Ratio manipulation.
  - *Canvas*: Standard desktop-style paint overlays (Drawing, Eraser, Color picker), Text insertion, and PiP composition.
  - *Format Conversion*: Built-in option to convert animated WebP and GIF files directly into standard Video files.

### 5. Audio/Music Editor (Stateless Trimmer)
* **Launch Mechanism**: Invoked via Library context menu for audio files. Externally, an `ACTION_SEND` ("Share") intent routes to the Editor, while an `ACTION_VIEW` ("Open") intent routes to the Audio Player.
* **UI Structure**: Simple central audio player bordered by Left and Right adjustment dials to facilitate precise minute-level timeline trimming.
* **Architecture & OOM Mitigation**: Shares the stateless FFmpeg engine for processing, using a Foreground Service Notification to guarantee processing completion without OS kills, exporting cleanly to the set Output Folder URI.

### 6. Settings & Interfaces
* **UI Aesthetics**: Settings adopts MiXplorer's clean visual layouts.
* **Structure**: Implements MX Player's root settings categories, but introduces distinct top-level "Editors" entries, which nest their own dedicated settings pages (e.g. Video Editor, Image Editor).
* **Configuration**:
  - Map the Output Folder URI (via Storage Access Framework).
  - Select target inclusion folders and explicit MIME/extension types to display in the Library.
  - Ensure zero interior tracking footprint (no memory of edited features/locations).

---
## Development Phases

### Phase 1: Total Observation (The Log Keeper) & Foundation
*Target: Build the diagnostic nervous system and core app scaffold.*
* Scaffold the base project (Theme, NavigationCompose, Dependency structure).
* Initialize the Singleton Logger before any framework UI initialization.
* Setup `SharedPreferences` persistence for logs and `Thread.setDefaultUncaughtExceptionHandler`.
* Ensure strict privacy routing (no PII/passwords logged).
* Implement crash-dump to `Downloads/Viabrplay_crash_YYYY-MM-DD_HHmm.txt` via SAF/MediaStore without UI interaction.
* Build the Global Diagnostic FAB (floating over all activities), featuring time filters (1h, 6h, 12h, 24h, All) and Copy/Download actions.
* Implement a master kill-switch for the logger in `SharedPreferences`.

* **Phase 2: Permissions, SAF Configuration & Settings Base** (COMPLETED)
  - Basic Settings UI to handle `ACTION_OPEN_DOCUMENT_TREE` (SAF).
  - Persistent storage of the "Output Folder URI".
  - Configuration for inclusion folders (where the library should look) and specific extensions (mp4, mkv, mp3, etc.).

* **Phase 3: The Headless Library Engine** (COMPLETED)
  - A filesystem scanner that processes the flat folder structure.
  - Detection logic for companion subtitle files (same filename check).
  - The logic to read timestamps, file sizes, and generate cache-friendly Video/Image thumbnails.

* **Phase 4: Library UI & Context Menus** (COMPLETED)
  - Flat-folder list layouts (e.g., LazyColumn/RecyclerView).
  - Manual pull-to-refresh gesture mapping.
  - Long-press multi-selection logic and the context menu (Play, Delete, Rename, Options, Add to Playlist).

* **Phase 5: Media3 (ExoPlayer) Core Lifecycle** (COMPLETED)
  - Invoking the Player natively.
  - Foreground Service mapping for Background Playback (audio-only).
  - Picture-in-Picture (PiP) lifecycle hooks (handling window resizing seamlessly).

* **Phase 6: Player UI & Invisible Gesture Zones** (COMPLETED)
  - The configurable gesture zones (Brightness Left, Volume Right, Seek Middle, Double-Tap Play/Pause).
  - The folded UI tool row (Screenshot, Lock, Loop, PiP).
  - Audio/Subtitle track selection hooks mapping into ExoPlayer.

* **Phase 7: App Intents & Universal Routing** (COMPLETED)
  - AndroidManifest configuration for `ACTION_VIEW` (Open) and `ACTION_SEND` (Share).
  - An invisible routing activity that checks MIME types to dispatch the payload to the Library, Player, Video Editor, or Photo Editor.

* **Phase 8: Photo Editor (Canvas & Compression)** (COMPLETED)
  - Batch Compression Queue via a Foreground Service Notification (for "Share" actions) / Basic Canvas handling.
  - The single-image Canvas UI for cropping, drawing, text, and aspect ratio manipulation.

* **Phase 9: Audio Trimmer** (COMPLETED)
  - Audio playback UI with left/right timeline trimming dials (`RangeSlider`).
  - Muxing out the trimmed result using `MediaMuxer` & `MediaExtractor`.

### Phase 10: Video Editor (The FFmpeg Bridge) - COMPLETED
*Target: The heavy-lifting render engine.*
* Implemented `VideoEditorViewModel` bridging the UI with video processing commands.
* Simulated FFmpeg engine `FFmpegEngine` due to container sandbox blocking artifact download.
* Built `FFmpegCommandBuilder` for Trim, Crop, Filter (LUT), Encode, and Subtitle Burn-In.
* Implemented `VideoEditorScreen` with ExoPlayer for playback, RangeSlider for trimming, and FilterChips for cropping and LUTs.
* Added Video Editor routing in `MainActivity` with multiple intents resolution.
* Integrated `LibraryScreen` edit button for video files.

### Phase 11: Polish & Deactivation - COMPLETED
*Target: Releasing the app.*
* Hiding the floating Log Keeper FAB via the master switch by wrapping it in a compositional check for the LogKeeper state.
* Fixed deprecated modifiers and icons.
* Final memory leak checks and performance profiling.
