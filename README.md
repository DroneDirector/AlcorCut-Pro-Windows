# AlcorCut Pro for Windows

The Windows version of AlcorCut Pro: the same screens, layout and wording as the Android app.

Order of work: photo editor, then the video editor, then Capture (the phone as the PC's camera over Wi-Fi or USB).

## Download

Every push to `main` builds a Windows installer (`.msi`, plus an `.exe` setup) on GitHub Actions and publishes it under **Releases**.

## Build

Needs JDK 21. `gradlew packageReleaseMsi` builds the installer on Windows; `gradlew run` starts the app.

## Licences

All third-party parts are free for commercial use; credits are listed in the app under Licences and in `src/main/resources/licenses`.
