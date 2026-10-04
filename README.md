# Premium BG Remover

A focused Android background remover with two modes:

- **Free AI** — Google ML Kit subject segmentation on device.
- **Paid Pro API** — Leonardo.Ai's remove-bg model (the remove.bg API is moving to Leonardo). The app asks for your own Leonardo API key and never hardcodes a key in GitHub.

## Paid Pro pricing

Leonardo documents background removal at **USD $0.1047 per image**. The app shows the actual charge returned by the API after each successful paid removal.

## Paid Pro flow

1. Tap **Paid Pro API**.
2. Paste your Leonardo API key.
3. Tap **Select Image**.
4. The app shows a cost confirmation before any paid request is sent.
5. After confirmation, the image is sent to Leonardo's sync remove-bg API.
6. The transparent PNG result is downloaded and previewed.
7. Tap **Save Transparent PNG**.

The API key is kept only in app memory for the current session; it is not committed to GitHub or bundled into the APK.

## Free flow

1. Leave **Free AI** selected.
2. Tap **Select Image**.
3. ML Kit processes the image on device.
4. Save the transparent PNG.

## APK build

Every push to `main` triggers the GitHub Actions workflow. Open the latest successful **Build Android APK** run and download the `Premium-BG-Remover-debug-apk` artifact.
