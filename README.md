# Smart Gallery

A photo & video gallery for **Android 13+** (Kotlin, Jetpack Compose, Media3, Coil) with
**Live Photo** support for both iPhone Live Photos and Android Motion Photos.

## Features
- Date-grouped grid of all photos and videos, with All / Photos / Videos / Live filters
- Press and hold a Live photo (or tap the LIVE chip) to play its motion clip
- Swipe viewer with full video playback
- Android 13 (`READ_MEDIA_*`) and Android 14 "selected photos" access

## How Live Photos are detected
| Source | Storage | Detection |
|---|---|---|
| iPhone Live Photo | `IMG_1234.HEIC` + `IMG_1234.MOV` | Same folder + base name, clip ≤ 6 s; the MOV is hidden from the grid |
| Android Motion Photo | MP4 appended to a JPEG | XMP header (`MotionPhoto` / `MicroVideoOffset`), verified by an `ftyp` signature at the tail |

Apple's internal content-identifier is not read, so a renamed pair won't match.

## Build
Open the folder in Android Studio (Ladybug or newer, JDK 17) and run, or:

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

## Get the APK from GitHub (no local setup)
1. Push this repo to GitHub (branch `main`).
2. Open **Actions → Build APK → latest run → Artifacts → SmartGallery-debug-apk**.
3. Or push a tag (`git tag v1.0 && git push --tags`) and the APK is attached to a **Release**.

The APK is debug-signed, which is fine for sideloading. Enable "Install unknown apps" on the phone to install it.

## Project layout
```
app/src/main/java/com/example/smartgallery/
  MainActivity.kt      permissions + theme
  GalleryScreen.kt     grid, filters, badges
  ViewerScreen.kt      pager, Live Photo overlay player, video player
  MediaRepository.kt   MediaStore query, iPhone pairing, Motion Photo detection
  GalleryViewModel.kt  state, background Motion Photo scan
  GalleryItem.kt       model
  App.kt               Coil loader with video-frame support
```
