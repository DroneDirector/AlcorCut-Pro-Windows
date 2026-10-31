# Changelog — AlcorCut Pro for Windows

## Unreleased
- **Capture is on Windows**, with the phone's screens, controls and wording, and a start screen (Capture · Editor) like the phone's. Desktop shortcuts straight to Capture or the Photo editor (start screen → gear).
- **PC cameras**: webcams, HDMI capture cards and cameras in webcam mode. Manual shutter, ISO (gain), focus, white balance in Kelvin, EV, zoom and anti-flicker where the camera offers them; histogram, zebras, focus peaking, false colour, filters, log profiles, monitoring LUT and live face blur.
- **Video** with the PC's own H.264 / HEVC encoders and sound (input gain, limiter, wind filter, a WAV next to each clip), time-lapse, hyperlapse, slow motion (60 fps cameras and up), motion blur, title, timecode, slate, recording in the frame guide's shape.
- **Photos**: burst, bracketing, intervals, timer, long exposure (trails, silky water, night sky), portrait blur, focus bracketing for the editor's focus stack.
- **Live streaming** to YouTube, Twitch, Facebook, TikTok or any RTMP / RTMPS server.
- **Your phone as the PC's camera** (Connect to PC in the phone app, 1.0.808 and newer): over Wi-Fi or a USB cable with USB tethering, PIN-protected; the PC shows the live picture and works the phone's camera, and every photo and video is copied to the PC at full quality.
- **Tethered pro cameras** over USB: shutter from the PC, ISO / shutter / white balance / exposure compensation (also on Canon EOS and Sony Alpha bodies, which use their own settings), live view on Nikon, Canon EOS and Sony Alpha bodies (Sony: set USB to PC Remote), and every new photo copied to the PC straight away.
- **Pre-record** (Settings → Video): the seconds before you press record are kept, so the clip starts before the moment you reacted to.
- **Voice noise reduction** (light or strong) while recording: fan hum, hiss and traffic are turned down so voices stand out.
- **NTSC frame rates** (23.976 / 29.97 / 59.94) with the sound kept in step.
- **Stabilization** on PC cameras: a steadier picture by a light crop-in, also for hyperlapse. Gyroflow mode explains how to stabilize with a camera's own motion data.
- Photos are saved without the monitoring LUT, as on the phone.
- **Cast to a TV**: the Chromecast button in Capture shows the live picture on a Chromecast or Google TV on the same network (a few seconds behind). For a projector or another screen: Windows key + K.
- **Level** (Settings → Level): on tablets and 2-in-1 PCs with a motion sensor, and with your phone as the camera (it follows the phone's tilt).
- Remote monitor from a browser, microphone and speaker choice (Audio hardware), and the Capture gallery (videos play in Windows' own player).
- **Faster and smoother**: the photo on screen is only redrawn when it changes, so sliders, zooming and the panels respond much more quickly (it was copied again for every screen update before).
- Everything new in the phone app's photo editor: photo layers with blend modes, colour samplers, focus mask, MERGE PHOTOS ▾ in the toolbar, HDR without ghosts, long exposure from a video (comes with the video editor on Windows), swipe between photos, NEXT UNEDITED →, recently used tools, recent projects next to the catalog card.
- Library: keyword tree, colour labels, calendar, people (face recognition comes with the AI update), watched folders, Pro import from an SD card or drive, geotagging from a GPX track, proofing and client picks, photo book PDF.
- File menu, keyboard shortcuts you can change, team projects in a shared folder, and network storage (NAS) sign-in like on the phone.
- Settings search, What's new, hold any button for help, undo bar for resets, safe mode after a crash at start-up.
- System Info shows how the window is drawn (graphics card or processor).
- **AI tools now on Windows**: AI noise reduction, Subject and Sky selection, People masks (whole person, face and body skin, eyes, eyebrows, lips, teeth, hair, clothes), AI upscaling, face finding for People in the library, face reshaping and smart crop. The AI models download once in the background on first start (progress shows bottom left) and are kept in the app's folder.
- **Every export format now on Windows**: HEIC (with Windows' HEVC Video Extensions), AVIF, JPEG XL and real WebP (WebP files were saved as JPEG before), and **Ultra HDR** JPEGs (Export → OUTPUT) that show bright parts brighter on HDR screens.

## 1.0.12 and newer — first Windows version
- The photo editor from the phone app, with the same screens, layout and wording: start page, library, filmstrip, all edit panels (presets, light, curve, colour, HSL mixer, grading, detail, effects, masks, heal, retouch, lens, crop & perspective, watermark, text, history), catalogs, projects and export.
- Saves to Pictures\AlcorCut Pro (or a folder you choose on any drive in the editor's settings).
- Opens photos with the Windows file dialog; "Open with → AlcorCut Pro" opens a photo straight in the editor.
- Keyboard shortcuts as on a tablet with a keyboard (Ctrl+Z / Ctrl+Y, Ctrl+C / Ctrl+V for settings, Ctrl+S, Ctrl+E, 0–5 stars, 6–9 colour labels); Esc goes back.
- Export progress shows on the app's taskbar button.
- Not on Windows yet: the camera (comes with Capture), HEIC / AVIF / JPEG XL saving, Ultra HDR, and the AI tools that use the phone's AI runtime (noise reduction, subject / sky selection, people masks, upscaling). Magic Mask and object removal run with ONNX Runtime.
