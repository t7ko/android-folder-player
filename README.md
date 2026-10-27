# Folder Player

An audio player for Android 8 or newer that works by folders, made for audiobooks,
lectures and long recordings: pick folders, browse their subfolders, tap a file, and the
files of that folder play one after another. Big jump buttons (30 seconds to 2 hours) and
swiping over the player move through long files quickly.

Status: version 0.1.0.

## Install

Install the APK from the [Releases](https://github.com/t7ko/android-folder-player/releases)
page, or with [Obtainium](https://github.com/ImranR98/Obtainium) from
`https://github.com/t7ko/android-folder-player`, which also installs updates.

If Obtainium cannot install the app or an update, often with an unclear error message,
the likely cause is Google Play Protect, which may block apps that are not on Google
Play. Install that version from the Releases page by hand; when Play Protect warns about
the app, tap **More details**, then **Install anyway**. Obtainium then sees the installed
version and keeps offering updates.

## Folders

- **Add folder** opens the system folder picker; choose a folder and allow access.
  Android does not allow the top level of the storage or the Download folder itself, so
  pick a folder such as Music or Audiobooks. Folders on a memory card work too.
- The app reads the whole folder tree once and shows it at once on the next start; each
  start, and **Scan again** (the arrow at the top), reads it again in the background.
- A folder shows its subfolders, with the number of audio files in each, then its audio
  files. Folders without audio anywhere inside them are hidden, as are names starting
  with a dot.
- Going up from the top of a folder (the arrow, or Back) shows the list of all added
  folders: that is how you switch between them. Long-press one to remove it from the
  list; its files are not touched.
- Names are sorted alphabetically, ignoring case, with numbers in order: "2" before "10".
- Audio files: mp3, m4a, m4b, aac, ogg, opus, flac, wav, amr, mka, and anything else the
  storage reports as audio.

## Playing

- Tapping a file plays it and then the other files of the same folder in order (not
  those in subfolders), and starts over after the last one. The repeat button switches
  to repeating the one file, and back.
- The jump buttons move 30 s, 1 min, 5 min, 10 min, 30 min or 2 h back or forward within
  the file. Back stops at the start of the file; forward past its end goes on to the
  next file.
- Drag left or right over the upper part of the player to seek: the target time shows
  while you drag, and playback moves there when you let go. A short drag moves a few
  seconds; dragging across the whole width moves as far as twice the file's length, so
  any point can be reached. The slider below works as usual.
- Playback goes on with the app closed or the screen off, with controls in the
  notification, on the lock screen and on headsets. It pauses when headphones are
  unplugged.
- The app remembers the folder you were in, the playing folder, the file and the
  position, also after the phone restarts.

## Privacy

The app has no internet permission. It reads only the folders you add.

## Development

See `AGENTS.md` for the design and how to test on the emulator. Unit tests:
`gradlew.bat testDebugUnitTest`; a debug APK: `gradlew.bat assembleDebug`. A signed
release: raise `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then
`powershell -ExecutionPolicy Bypass -File scripts/release.ps1`.
