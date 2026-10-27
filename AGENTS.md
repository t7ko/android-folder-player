# Notes for AI coding agents: folder-player

An Android audio player that works by folders: the user adds root folders (Storage
Access Framework), browses their trees, taps a file, and the files of that folder play in
order with repeat-all (or repeat-one). Jump buttons (±30 s, 1, 5, 10, 30 min, 2 h) and a
horizontal drag over the player move through long files. Modelled on the audio player
of CX File Explorer, plus the jump buttons and several root folders.

Machine-specific notes, where they exist, are in the git-ignored `AGENTS.local.md`. Keep
paths, file names, device details and anything else about one person's setup out of
tracked files: the repository is public.

## Decisions

- Folders through SAF (`OpenDocumentTree`, persisted read grant), not
  `MANAGE_EXTERNAL_STORAGE`: no broad storage permission, and memory cards work. Android
  11+ refuses the storage root and Download in the picker.
- The whole tree of a root is read at once (`Scan`, one `query` per folder through
  `SafLister`), pruned to folders with audio below them, sorted in `NaturalOrder`, and
  cached as JSON in `files/trees/<sha1 of the tree URI>.json` (`TreeCache`), so it shows at
  once on the next start. Every root is scanned again in the background at each process
  start, when added, and on "Scan again"; a newer scan of a root cancels the older one.
  A subfolder that cannot be listed is skipped and counted; the root failing to list (lost
  grant) shows a message.
- `Library` (process-wide `StateFlow<List<Root>>`) holds the roots and trees; roots are in
  SharedPreferences (`Settings`). The browser's place is a `Location` (root URI plus folder
  names), stored on every change; after a rescan that removed part of it, the browser
  moves to the deepest folder still there (`Folder.resolve`).
- Above all roots is the root list: Up (or Back) at the top of a root goes there. Adding
  goes through the "Add folder" button on that list.
- Audio is decided by extension, then MIME type `audio/*`; playlists (m3u, pls, cue...)
  are excluded although their MIME types are audio.
- Playback: Media3 ExoPlayer in a `MediaSessionService` (`PlaybackService`), so it goes
  on in the background with the media notification (exempt from the Android 13
  notification permission), lock screen and headset controls; audio focus and "becoming
  noisy" are handled by ExoPlayer. The UI talks to it through a `MediaController`
  (`PlayerConnection`), connected while the activity is started. Swiping the app away
  stops the service only if it is not playing.
- The queue is the files of the folder where a file was tapped, as `MediaItem`s whose
  media ID is the document URI; items from a controller lose their URI on the way, so
  `onAddMediaItems` sets it again from the media ID. Tapping another file of the same
  folder only seeks within the queue.
- Repeat all is the default; the repeat button toggles repeat one (`Player.repeatMode`,
  stored in settings).
- Jumps (`Seek.target`) stay within the file: back stops at 0; forward past the end lands
  on the end, so ExoPlayer moves to the next file (or restarts it in repeat one).
- Scrubbing (`Seek.scrubDelta`): linear, 2 minutes for a drag across the whole width of
  the swipe area, whatever the file's length (the user's choice; at first it was
  proportional to the length, which made drags on long files too coarse). The target
  shows in an overlay; the seek happens on release. Only the upper part of the player is
  the swipe area, so the slider and buttons keep their own gestures.
- Saved playback (`PlaybackStore`): the queue in `files/queue.json` (written when the
  queue changes), the index and position in preferences (on play/pause, file change,
  every 10 s while playing and when the service ends). The service loads them, paused, when
  it starts; `onPlaybackResumption` gives them to the system's media controls.
- The player screen is always dark (the user's wish, 0.1.1): `FolderPlayerTheme(dark =
  true)` around it, and `enableEdgeToEdge` with dark system bar styles while it shows, so
  the status bar icons are light; the browser follows the phone's light or dark setting.
  Checked on the emulator in light mode.
- Icons are vector drawables in `res/drawable` (Material paths), not
  `material-icons-extended`, which is large.
- English only, strings in code.

## Layout

- `app/src/main/java/t7helpers/folderplayer/`: `Tree` (`Folder`, `AudioFile`, `Location`,
  `AudioFiles`, `NaturalOrder`, `Scan`, `TreeJson`), `Saf` and `SafLister`, `Library`
  (roots, scans, `TreeCache`), `Settings`, `Seek` (jumps, scrubbing, time format),
  `PlaybackService` (with `QueueItem`, `PlaybackStore`).
- `.../ui/`: `MainActivity` (navigation state, folder picker), `BrowserScreen`
  (`RootsScreen`, `FolderScreen`, `MiniPlayer`), `PlayerScreen`, `PlayerConnection`,
  `Theme`.
- `app/src/test/`: JVM unit tests for the natural order, audio detection, scanning
  (pruning, hidden entries, unreadable folders, progress, cancelling), path resolution,
  the JSON cache, jumps, scrubbing and time formatting.

## Build and releases

Versions are in `gradle/libs.versions.toml`. The scripts are for Windows PowerShell.

- `scripts/build.ps1` runs the unit tests and writes `dist/folder-player-<version>.apk`, a
  release build signed with the key named in the git-ignored `local.properties`
  (`release.storeFile`, `release.storePasswordFile`, `release.keyAlias`); without it the
  script stops. `gradlew.bat assembleDebug` is enough for the emulator. Certificate
  SHA-256: `8a191c6fae367af0a88ccb9fa3f2c7492e438978c6ec815add73912f79e7eecd`. Every update
  must use this key, or the app has to be uninstalled and loses its data.
- A release: raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit, run
  `scripts/release.ps1`. Obtainium compares the tag with the installed versionName.
- `scripts/release.ps1` tags `v<versionName>`, pushes, and publishes the APK as a GitHub
  release at `t7ko/android-folder-player` for Obtainium, with the token file named by
  `github.tokenFile` in `local.properties`. Never print a token or password, or put one
  on a command line.
- Debug and release builds are signed with different keys: switching between them on a
  device needs `adb uninstall t7helpers.folderplayer`, which forgets the folders.

## Testing on the emulator

- Test audio: `ffmpeg -f lavfi -i "sine=frequency=440:duration=20" -ac 1 -b:a 32k "01 Intro.mp3"`;
  a 3-hour file at 8 kbps mono is about 10 MB. `adb push` them under `/sdcard/Music/...`
  (right after boot, `/sdcard` may refuse writes for a few seconds).
- `scripts/emulator-ui.py` lists texts on screen and taps them. The extended "Add folder"
  button exposes no text; tap its coordinates (it moves up when the mini player shows).
  In the picker: the device's name in the breadcrumb, the folder, "USE THIS FOLDER",
  "ALLOW".
- Player state: `adb shell dumpsys media_session`, the block with
  `package=t7helpers.folderplayer` (other apps' sessions are listed too).
- A drag: `adb shell input motionevent DOWN x y`, several `MOVE`, then `UP`; take a
  screenshot before `UP` to see the overlay.
- Background: `dumpsys activity services t7helpers.folderplayer` (`isForeground=true`),
  `dumpsys notification` (the media notification). Restore: `am force-stop`, start again.

## Tested

On 2026-10-27, version 0.1.0 (debug build) on the Android 14 emulator, with generated
files (three 20 s mp3 files and a subfolder, a 3 h mp3, m4a, flac, ogg, a jpg-only folder):

- The picker refused the storage root; Music was added with a persisted grant and scanned
  at once: the jpg-only folder hidden, counts per folder right, "Book 2" before "Book 10"
  and "02" before "10".
- Tapping the second of three files played it (2 / 3); +30s went to the third, +30s again
  to the first (repeat all); in repeat one +30s restarted the same file.
- On the 3 h file: −30s at 0:00 stayed at 0; +2h, +30m, −10m, −5m, −1m landed exactly.
- A drag showed the target ("+50:56 → 1:21:15 / 3:00:00") and seeked there on release.
  That was the first scrub mapping (proportional to the length); the 2-minute one that
  replaced it is covered by unit tests only.
- Up from the top of Music showed the root list; a second root (Podcasts) was added and
  opened; long-press → Remove took it off the list and deleted its cache; the files
  stayed.
- With the app in the background, playback went on in a foreground service with the
  media notification. After `am force-stop` and a new start, the app opened in the same
  folder with the last file paused at the saved position; Play resumed there.
- "Scan again" showed a newly added file and, after its folder was deleted, moved to the
  parent folder. Back goes up one level.

Not tried yet: a real phone (vendor battery restrictions on the service), headset buttons,
a memory card, a very large tree (scan time), audio focus with another player, Android
8-12, release builds.
