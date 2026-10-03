# Premium BG Remover

A focused Android app that does one job: remove the background from an image and save the result as a transparent PNG.

## How it works

1. Tap **Select Image** and choose a photo.
2. The app runs ML Kit Subject Segmentation on the selected image.
3. ML Kit separates the foreground subject from the background.
4. The transparent result is shown on a checkerboard preview.
5. Tap **Save Transparent PNG** and choose where to save the PNG.

No filters, stickers, collage tools, accounts, ads, or unrelated editing tools are included.

## AI and privacy

The app uses Google ML Kit Subject Segmentation. Image segmentation runs on the device. The segmentation model is supplied through Google Play services and can require internet the first time it is downloaded. After that model is available, image processing happens locally on the phone.

Minimum Android version: Android 7.0 / API 24.

## Build the APK on GitHub

Every push to `main` triggers the **Build Android APK** GitHub Actions workflow.

Open **Actions → Build Android APK → latest successful run → Artifacts → Premium-BG-Remover-debug-apk**.

The artifact ZIP contains the installable debug APK.

## Project

- Package: `com.altaf.premiumbgremover`
- UI: Native Android
- Language: Java
- Background removal: ML Kit Subject Segmentation
- Output: Transparent PNG
