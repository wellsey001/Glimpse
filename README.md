# Glimpse — a local photo gallery for Android

A fast, private replacement for the Google Photos app's on-device gallery.
Backups are left to Immich; Glimpse only works with what's on the phone.

## Features

- **Photos**: timeline of every photo and video, grouped by day. Pinch the grid to change thumbnail size.
- **Albums**: one per folder (Camera and Screenshots first), plus Favourites, Videos and Trash.
- **Viewer**: swipe between items, pinch and double-tap to zoom, and play videos. Tap to hide the controls.
- **Organise**: long-press to multi-select (tap a date header to select the whole day). You can then share, favourite, move to trash, or move or copy to another album, including a new one.
- **Trash**: uses Android's own trash, so items are deleted after 30 days. You can restore items or empty the trash.
- **Details**: date, size, resolution, folder, camera, exposure settings and GPS location, with a button to open the location in a maps app.
- **Search**: by file name, album, month, year, or words like `videos`, `favourites`, `screenshots` and `camera`. Every word must match, for example `camera june 2024`.
- **Map**: all geotagged photos on an OpenStreetMap map, grouped into bubbles. Tap a bubble to see the photos taken there.
- **Edit**: crop (free or fixed ratios), rotate, flip, brightness, contrast, saturation and warmth, plus 7 filters. Press and hold the preview to compare with the original. Edits are saved as a copy and the original is never changed.
- **Default app**: Glimpse can be picked for "Open with" on any photo or video, and as the phone's gallery app.

## Build and install

1. Install [Android Studio](https://developer.android.com/studio) (any 2024 or later version).
2. **File → Open** and choose this `Glimpse` folder. Let Gradle sync. It downloads everything it needs, which takes a few minutes the first time.
3. On your phone, turn on **Developer options → USB debugging** and plug it in, or use Wireless debugging.
4. Press **Run ▶**.

If Android Studio offers to upgrade the Android Gradle Plugin or Kotlin, it's safe to accept.

### Get an APK without Android Studio

The project includes a GitHub Actions workflow (`.github/workflows/build-apk.yml`).
1. Create a new repository on GitHub and upload this folder to it, including the hidden `.github` folder.
2. Open the repository's **Actions** tab. The **Build APK** workflow runs automatically and takes about 5 minutes.
3. Open the finished run and download **Glimpse-apk** under *Artifacts*. Unzip it to get `Glimpse.apk`.
4. Copy `Glimpse.apk` to your phone and tap it to install. Allow *Install unknown apps* for your browser or file manager when Android asks.

In Android Studio you can do the same with **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

The APK is signed with a debug key so it installs straight away. Make a proper signing key before sharing it with anyone else.

### Make it your default gallery

Open a photo from any app (for example a file manager or WhatsApp), choose **Glimpse**, and then tap **Always**.
To change it later, go to **Settings → Apps → Default apps**.
You can also disable the Google Photos app (**Settings → Apps → Photos → Disable**) so it no longer appears.

## Notes

- **Requires Android 11 or later.** This allows Glimpse to use the system trash and folder moves.
- **Confirm prompts**: Android asks for confirmation before Glimpse trashes or moves files created by other apps. On Android 12 and later you can skip these prompts with **⋮ → Skip confirm prompts…**, which turns on *Media management* for Glimpse.
- **Favourites** are stored by Glimpse itself. This makes starring instant, but the favourites don't sync to Immich or Google.
- **Map**: the first time you open it, Glimpse scans your photos for GPS data. The results are cached, so later visits are instant. Map tiles come from OpenStreetMap, which needs an internet connection.
- **Edits** are saved at up to 4096 px on the longest side, in the same album as the original.

## Code layout

```
app/src/main/java/com/wellsey/glimpse/
  MainActivity.kt         permissions, navigation, system confirm dialogs
  GlimpseApp.kt             image loader + map setup
  data/                   MediaStore access, favourites, thumbnails, EXIF and location index
  ui/GalleryViewModel.kt  app state and actions
  ui/components/          photo grid, selection bar, album picker
  ui/screens/             Photos, Albums, Search, Map, Trash, permission screen
  ui/viewer/              full-screen viewer, zoom, video, details sheet
  ui/editor/              editor screen, crop frame, colour maths
```

To rename the app, change `app_name` in `res/values/strings.xml`. To change the package name, also change `applicationId` in `app/build.gradle.kts`.
