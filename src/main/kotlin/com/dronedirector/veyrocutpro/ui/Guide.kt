package com.dronedirector.veyrocutpro.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
import com.dronedirector.veyrocutpro.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/*
 * KEEP UP TO DATE: whenever a feature is added, changed or removed, update these sections,
 * all three variants below, GUIDE.md, FEATURES.md and the "Unreleased" part of CHANGELOG.md in the same change.
 */
enum class GuideTopic { CLOUD, PHONE_AI, TIPS, GENERAL }

/** Which part of the app the guide was opened from; each gets only the parts that apply to it. */
enum class GuideVariant(val title: String) { CAPTURE("Capture guide"), PHOTO("Photo editor guide"), VIDEO("Video editor guide") }

private class GuideSection(val topic: GuideTopic, val title: String, val icon: ImageVector, val intro: String?, val steps: List<String>, val numbered: Boolean)

private fun cloudSection(v: GuideVariant): GuideSection {
    val what = when (v) {
        GuideVariant.CAPTURE -> "AI upscale (AI 24 / 36 / 48 MP) uses Topaz Labs' upscaler in the cloud."
        GuideVariant.PHOTO -> "AI noise reduction on export uses Topaz Denoise in the cloud."
        GuideVariant.VIDEO -> "AI noise reduction on export uses Topaz Nyx, made for video — minutes instead of up to an hour on the phone."
    }
    val toggle = when (v) {
        GuideVariant.CAPTURE, GuideVariant.PHOTO -> "Turn on \"Photos in the cloud\"" + if (v == GuideVariant.PHOTO) " and pick the noise strength (Normal, Strong, Extreme or Max)." else "."
        GuideVariant.VIDEO -> "Turn on \"Video in the cloud\" and pick the model — Nyx Fast is quickest and cheapest (about \$0.10 per 10 s of 1080p)."
    }
    return GuideSection(
        GuideTopic.CLOUD, "Cloud AI: add your API key", Icons.Filled.Cloud,
        "Optional: runs Topaz Labs' professional AI on fast servers through your own fal.ai account. $what Without it, the AI runs on the phone.",
        listOf(
            "Open fal.ai (tap \"Open fal.ai keys\" below) and sign up or log in.",
            "Add credit to your fal.ai account (Billing). You pay fal.ai per use.",
            "Go to Dashboard → API Keys → Add key, name it (e.g. \"AlcorCut Pro\") and create it.",
            "Copy the key — it's shown only once.",
            "Back here, in this settings menu → CLOUD AI → \"Add your fal.ai key\".",
            "Paste the key and tap SAVE KEY. It's stored encrypted on this phone and only sent to fal.ai.",
            toggle,
            "Keep \"Wi-Fi only\" on to avoid using mobile data.",
            "The AI badge shows upload, processing and download; STOP cancels. If the cloud isn't available, the on-phone AI is used and you're told why.",
        ),
        numbered = true,
    )
}

private fun sectionsFor(v: GuideVariant): List<GuideSection> = when (v) {
    GuideVariant.CAPTURE -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "AI upscale", Icons.Filled.AutoAwesome, null, listOf(
            "Settings → AI UPSCALE → turn on \"AI upscale sizes\".",
            "Pick AI 24 / 36 / 48 MP under SIZE (quick controls or Settings → Photo size).",
            "Shoot as usual — the bigger copy is made in the background and saved next to the original.",
            "Standard is faster; High keeps more real detail. Choose whether to keep the original.",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Capture tips", Icons.Filled.CameraAlt, null, listOf(
            "Search at the top of Settings finds any setting by a word (zebra, proxy, location…). Hold a button to see what it does.",
            "Settings → Appearance → One-handed: Right hand or Left hand moves the top bar down and the shutter under your thumb (phone held upright). Haptic feedback can be turned off there too.",
            "Capture opens in the mode and lens you used last. LAST in the quick bar brings back one of your last three set-ups.",
            "Deleting from the gallery goes to the Trash at once with an UNDO bar.",
            "Settings → Photo → Save location stores where each photo and video was taken. The phone asks once for permission.",
            "Settings → Photo → Focus bracketing takes 3, 4 or 6 shots focused from close to far. Use a tripod; the next time you open the Photo editor it stacks them into one sharp photo.",
            "Settings → Photo → Macro switches to the lens that focuses closest. Move to a few centimetres from the subject.",
            "Settings → Video → Anti-flicker: choose 50 Hz in Europe, Africa and most of Asia, 60 Hz in the Americas, if lights make bands in the picture.",
            "Settings → Video → Timecode adds the time of day as timecode to each video; Editing proxy makes a small copy for smooth editing; Front + back camera records both cameras into one video where the phone allows it.",
            "Keyboard: Space = shutter / record, + / − zoom, [ / ] exposure, Tab = next mode. Settings → Keyboard shortcuts changes them.",
            "? icon next to the gear: the Capture guide, quick tour, troubleshooting and proof of purchase. Settings → Audio hardware picks the microphone.",
            "Read-outs: drag the histogram / scopes, exposure read-out or audio meter anywhere. Pinch one (or hold it and drag the amber grip) to resize it; double-tap to make it small but still live.",
            "Teleprompter: tap PROMPTER in the quick bar (it also opens by itself with the selfie camera once you've typed a text). ✎ types your text and sets its look — bold, italic, underline, narrow, left / centre / right, size, text colour, background colour and opacity, scroll speed, mirror for teleprompter glass. ▶ or AUTO-SCROLL makes it scroll by itself — set the speed with the slider at the bottom of the teleprompter (or it starts with recording); ↺ goes back to the top. Drag the top bar to move it and the corner to resize it; — shrinks it to a small TELEPROMPTER bar (▢ or PROMPTER opens it again), ✕ closes it. ⇅ / ⇆ (or ✎ → Scrolling) switches between scrolling up (the default) and sideways as one line, like a news ticker.",
            "Pro read-out bar: ISO, S, F, WB, EV and ZOOM show their live values — tap one to change it. EXP shows AUTO, S-PRI (shutter priority: set S, ISO follows the light), ISO-PRI (set ISO, shutter follows) or M (both manual). REC shows resolution, frame rate and bitrate in video.",
            "Fn button (end of the read-out bar): tap it to run your favourite function, hold it to choose — zebras, peaking, false colour, next scope, AE / AWB lock, split focus / exposure, pull to A / B, frame guide, flip, torch, next lens and more.",
            "Pull points: in FOCUS, ZOOM and EV, SET A and SET B store two settings; → A / → B glide there over 1, 2, 4 or 8 s. ALL → A / ALL → B moves focus, zoom and exposure together — smooth rack focus, zoom pulls and exposure changes while recording.",
            "Separate focus and exposure (AF·AE chip or Settings): tap to focus, double-tap to set where the exposure is measured (the amber sun ring).",
            "Settings → VIDEO → 24-bit audio (WAV): the sound is recorded uncompressed and saved as a 24-bit WAV next to each clip (or in Music/AlcorCutPro), and the clip gets the same sound. Headphone monitoring pauses while recording.",
            "Settings → VIDEO → NTSC frame rates: 24, 30 and 60 fps clips become 23.976, 29.97 and 59.94 fps after recording (picture and sound slowed by 0.1 %).",
            "Pro Video → REC SHAPE (or Settings): records in the frame guide's shape — 2.76, 2.39, 1.85, 4:3, 1:1 or 9:16 — instead of only drawing the guide.",
            "Bitrate: Economy, Standard, High or Extreme (quick bar BITRATE or Settings → VIDEO). Flip picture mirrors left ↔ right for lens adapters, mirror rigs and teleprompters.",
            "Sync C1–C3 between devices: Settings → VIDEO → CREATE SYNC FILE and save it in a cloud folder (e.g. Google Drive); on your other device choose USE EXISTING and pick the same file.",
            "Capture gallery → Grade → Detail (photos): Clarity, Texture, Dehaze, Sharpening and Vignette. Light has Exposure, Contrast, Highlights, Shadows, Whites and Blacks.",
            "Settings → PHOTO → Always JPEG / Always RAW + JPEG / Always RAW only: the file format for Pro photo mode. Only one can be on, and the app remembers it.",
            "The app is now called AlcorCut Pro (formerly VeyroCut Pro). It is the same app and updates over the old one; your settings and projects stay. New photos and videos save to DCIM/AlcorCutPro, and the ones in the old DCIM/VeyroCutPro folder still show in the app's gallery.",
            "Capture settings (gear) are grouped in folded sections — Photo, Video, Audio, Viewfinder & monitoring, AI, Controls & buttons, Privacy, Film-set workflow, Remote & output, Storage & backup, App, This lens. Each shows what's inside; tap to open or close.",
            "Photo and video editor: a quick tour (same look as in Capture) opens the first time; SKIP closes it for now, \"Don't show this again\" turns it off, and gear → App → Quick tour shows it again or switches it back on.",
            "Video editor → Edit → Stabilize → Source: Gyro (motion data) uses the motion file Capture records with the clip (Settings → Video → Stabilization: Gyroflow) for sharper, steadier stabilisation; CHOOSE MOTION FILE picks one by hand.",
            "Video editor → Color → Input: for HDR clips (HLG / HDR10), HDR LEVELS shows a nits waveform with 100 / 203 / 1000-nit guides plus peak, average and how much is above 203 and 1000 nits, measured from the clip at the playhead.",
            "Video editor → Audio → Mixer → Track groups: put each track in Dialogue, Music or Effects; a group fader moves all its tracks together and M mutes the group.",
            "Photo and video editor → LUTs → EXPORT THIS LOOK AS .CUBE: saves your grade as a 33-point .cube LUT in Documents/AlcorCutPro/LUTs and adds it to My LUTs.",
            "Video editor → layer → Luma key: Remove dark (fire, sparks, smoke on black) or Remove bright, with Level and Softness.",
            "Gear → Layout & look → Fold-out sections: Remember (sections left open open again next time), Always closed, or All open — set separately for the photo and video editor.",
            "Gear → backup → Keep settings after uninstall: saves your settings, presets and LUTs to Documents/AlcorCutPro/Settings whenever you leave the app; after reinstalling, tap RESTORE MY SETTINGS on the first screen and pick AlcorCutPro-settings.vcbackup.",
            "Photo editor and video editor: every section in the tool panels is a fold-out (title, a line saying what's inside, arrow). Tap to open or close it; the tabs above work as before.",
            "Photo and video editor settings are folded into groups too (Layout & look, Saving & backup, Watermark, AI, App, and in the video editor Project archive & versions and Playback & proxies). Each line says what's inside.",
            "Watch remote: install AlcorCutPro-WATCH (from the release) on a Wear OS watch — Galaxy Watch 4 or newer, Pixel Watch. With Capture open on the phone, the watch shows the live picture and records / takes photos, switches photo ↔ video and zooms. Install steps: Guides and Tips → Installing the watch app.",
            "Capture: the Cast button on the picture (tablets) can be dragged anywhere, pinched to resize and double-tapped to make it small, like the other read-outs.",
            "Quick bar → STOP MOTION (photo modes): every photo becomes the next frame and the last one shows faintly over the viewfinder (ONION %). FRAMES opens the frame strip: play, HOLD +1, delete, 6–24 fps, MAKE VIDEO. Its bar sits on the picture: drag it, pinch to resize, double tap to make it small.",
            "With a mouse: the wheel zooms, right-click locks / unlocks exposure and focus there, and middle-click takes the shot.",
            "Use the volume keys as a shutter / record button.",
            "Settings → SLATE & SHOT LOG: set scene, shot, take and notes; they show on the viewfinder, go into photos, and every clip and photo is logged. Export the log as a .csv for Excel / Sheets; the take number goes up after each recording.",
            "Settings → COPYRIGHT: your name and copyright notice are written into every photo you take and every photo you export (Artist / Copyright in the photo's details).",
            "SMART → PORTRAIT and AI PORTRAIT: the subject stays sharp and the background is blurred — people, pets, flowers, rocks, branches and other things that stand out. PORTRAIT adds the blur a second after the shot; AI PORTRAIT also shows it live in the viewfinder while you frame (people update fastest; other subjects a few times a second, so the edge can lag a little when you move — \"no subject\" means nothing is found yet). In both, slide the BACKGROUND BLUR slider above the zoom bar (left f/1.4 = most blur, right f/16 = least) or tap a spot on it; tap AUTO to minimise it and let AlcorCut Pro pick the blur. The saved photo always gets the full-quality blur a second after the shot.",
            "SMART → PANO: tap the shutter and turn slowly in one direction, keeping the yellow line on the horizon — it finishes by itself at about 180°, or tap to finish early.",
            "While recording: a red tally border shows you're rolling, and the padlock on the left locks the screen against accidental touches (hold it to unlock). Warnings appear for low storage, low battery and heat.",
            "Settings → SAVE PHOTOS & VIDEOS TO: internal storage, an SD card / USB drive, or CHOOSE A FOLDER on any USB stick, SSD or card reader. Videos record straight onto the drive; if it's unplugged, AlcorCut Pro saves to internal storage and tells you.",
            "SMART → LONG EXP: pick LOOK (TRAILS for car lights and light painting, WATER for silky waterfalls and waves, STARS for the night sky) and TIME (2–30 s), put the phone on a tripod or something steady, and tap the shutter. The picture builds up live; tap the shutter again to finish early.",
            "Settings → Stabilization: OFF, IN-APP (CROP) — the camera steadies the picture while filming — or GYROFLOW, which records the phone's motion into a .gcsv file with the same name as each video (Documents/AlcorCutPro/Gyroflow). Put the video and its .gcsv in the same folder on a computer and open them in Gyroflow (free, gyroflow.xyz) for gimbal-smooth footage. STAB in the quick bar switches between them.",
            "Video → ND BLUR (quick bar or Settings): blends frames like an ND filter or a slower shutter, for smooth cinematic movement in bright light. ×2 is subtle, ×8 is strong. Works best with a fast shutter and a steady camera.",
            "Settings → REMOTE MONITOR: turn it on and open the address shown (it ends in a 4-digit PIN) in the browser of a second phone, tablet or computer on the same Wi-Fi. You see the live picture and can take photos, start / stop recording, zoom, switch Photo / Video / Pro Video, autofocus and lock exposure. No Wi-Fi? Use this phone's hotspot.",
            "Cast to TV: tap the cast button on the viewfinder and pick your Chromecast / Google TV on the same Wi-Fi — the live picture shows on the TV a few seconds behind. Settings → Remote & output → CAST TO TV moves the button below the picture; hide it in the layout menu.",
            "Settings → Simple layout: shows only the everyday modes and controls (Photo, Video, Portrait, Pano, Long exposure, Time-lapse, Slow-mo; fewer editor panels). Turn it off for every Pro tool — nothing is lost.",
            "Settings → BACKUP & RESTORE: SAVE BACKUP makes one .vcbackup file with all your settings, presets, LUTs, watermarks, sounds and projects. RESTORE on a new phone or after reinstalling brings it all back (the app restarts).",
            "Gallery → delete moves photos and videos to the Trash (Android 11+), where you can restore them for 30 days. EMPTY TRASH removes them for good.",
            "Shortcuts: long-press the AlcorCut Pro icon for Video, Pro photo, Photo editor and Video editor. Add the AlcorCut camera tile to Quick Settings to open the camera from the notification shade.",
            "From other apps: Share a photo or video to AlcorCut Pro (or choose Edit with) and it opens straight in the right editor.",
            "Settings → SHOW THE QUICK TOUR AGAIN replays the gesture tour.",
            "Settings → LANGUAGE · SPROG · MÁL: Phone language, English, Dansk or Føroyskt. The whole app switches at once (also in both editors' settings).",
            "C1 / C2 / C3 in the quick bar: hold one to save all your current settings (mode, lens, ISO, shutter, white balance, resolution, frame rate, profile, filter…), tap it to bring them back.",
            "Autofocus is on by default in every mode. Tap the viewfinder to focus there. Press and hold for 2 seconds (watch the ring fill) to switch to manual focus: a slim slider appears on the right edge of the picture — drag up for far (∞), down for close; the tag beside it shows the distance. Tap the small ✕ above the slider to go back to autofocus.",
            "Hold the viewfinder about half a second until it ticks, then let go before the ring closes: the exposure locks there; drag the slider under the lock to brighten or darken while it stays locked. Do the same again, or tap the small ✕, to unlock.",
            "Settings → AUDIO → Voice noise reduction (Light / Strong) cleans hiss, hum and background noise from recorded sound.",
            "Settings → PRIVACY & TITLE → Face blur pixelates faces in the viewfinder, videos and photos. The BLUR switch in the quick bar turns it on / off.",
            "Settings → PRIVACY & TITLE → Title on videos: type a title, pick lower third / centre / top and how long it shows; it is added when each clip is saved (TITLE switch in the quick bar).",
            "Settings → PHOTO → Auto-enhance saves an extra, improved copy of each photo next to the original.",
            "Panels icon (next to the settings gear): hide or show the exposure read-out, histogram, audio meter, level, Pro controls bar and tips. Presets: Full, Minimal, Video. Triple-tap the icon to put everything back as it was.",
            "Focus magnifier: in PRO / PRO VIDEO open F (focus) and turn the slider — a ×4 zoomed view appears (tap the screen first to choose where).",
            "Rack focus: in the focus control tap SET A and SET B at two distances, pick 1s / 2s / 4s, then → A / → B glides the focus smoothly while you film.",
            "Settings → Scope switches the Pro brightness graph between histogram, waveform, RGB parade and vectorscope (with a skin-tone line).",
            "Settings → VIDEO → Pre-record keeps the 3–10 seconds before you press record.",
            "Settings → AUDIO: Wind filter cuts wind rumble; USB / wired mics are used automatically; Bluetooth microphone records from a Bluetooth headset. The audio graph shows L / R meters on phones with stereo mics.",
            "Settings → MONITORING & LENS → Monitoring LUT: import a .cube LUT and see it on the viewfinder (e.g. shoot D-Log, see Rec.709). \"Record the LUT\" also burns it into videos.",
            "Shutter as angle (settings) shows the video shutter in degrees; the 180° button in the shutter control sets natural motion blur for your frame rate.",
            "Anamorphic desqueeze (settings) stretches the viewfinder back to the real shape for anamorphic lenses; desqueeze the clips later in the Video editor.",
            "PRO and PRO VIDEO give manual ISO, shutter, focus, white balance and exposure.",
            "Zebras, false colour and focus peaking help judge exposure and focus.",
            "H.265 records smaller files at the same quality; H.264 plays everywhere.",
            "D-Log / D-Log M / Flat keep more dynamic range — grade them later in the Video editor.",
            "Clean feed hides the controls for screen mirroring or HDMI.",
            "In LIVE, add your stream key once per platform; choose H.264 if your platform doesn't accept H.265.",
            "Settings → PHOTO SIZE shows what your phone's sensor allows apps to use.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "Start screen → gear → What's new lists what changed in the last three versions, for the whole app — each version in its own fold-out, the latest open. In Capture, the Photo editor and the Video editor, gear → App → ★ WHAT'S NEW shows only the changes for that part.",
            "Start screen → gear → Home-screen shortcuts: turn it on, then add separate icons for Capture, the Photo editor or the Video editor to your phone's home screen — each opens straight into that part of the app.",
            "Change the app colour under APP COLOUR.",
            "\"Always start in Capture\" opens the app straight into the camera.",
            "Choose where photos and videos are saved under SAVE PHOTOS & VIDEOS TO.",
            "Settings → Appearance: Dark, Light or Like the phone — the same choice as in the editors. On Light the menus and panels are light; the controls on the camera picture stay dark.",
        ), numbered = false),
    )
    GuideVariant.PHOTO -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "AI noise reduction", Icons.Filled.AutoAwesome, null, listOf(
            "Detail → AI NOISE REDUCTION → move Strength above Off.",
            "The preview is cleaned once by the AI; after that the slider works instantly.",
            "Exporting cleans the full-size photo (on the phone, or in the cloud if Cloud AI is on).",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "RAW (DNG) photos", Icons.Filled.Photo, null, listOf(
            "Export → CAPTION & KEYWORDS: a title, caption and keywords written into every exported JPEG / WebP (IPTC, with your name and copyright) for photo-management software, agencies and clients. Turn on the .xmp sidecar to also save your edit as Camera Raw settings in Documents/AlcorCutPro/XMP — put it next to the original to keep editing in other photo software.",
            "Start page → SKIP — OPEN THE EDITOR (under Gallery photos / Browse files) opens the editor straight away without choosing a photo; add one from inside with Gallery or Browse files. It only happens when you tap it.",
            "Start page → each photo thumbnail has a small file-type tag (DNG RAW, JPEG, PNG, HEIC…) so you can see what you're opening. The tag is only on the start page, not in the editor.",
            "Open a DNG (from AlcorCut Pro's Pro mode or any phone / camera that saves DNG) and the RAW badge appears: the editor develops the sensor data itself.",
            "Other cameras' RAW files open too: Sony ARW, Canon CR2 / CR3, Nikon NEF, Fujifilm RAF, Olympus / OM System ORF, Panasonic RW2, Pentax PEF and more (read by LibRaw). The same RAW engine as for DNGs develops them, starting at about the brightness of the camera's own picture.",
            "Light → RAW LOOK: tap \"Tap here to see different DNG looks\" at the top of Light to unfold it. Default (the app's own RAW look) and Match camera picture (copies the brightness and colour of the preview stored in this RAW file) come first; under them, Phones, Drones, Cameras and Action & 360° each unfold into small previews of your photo in looks measured from how those cameras render their own pictures (Pixel, Galaxy, iPhone, DJI, Parrot, Autel, Canon, Sony, Nikon, Fujifilm, Leica, Ricoh, Panasonic, Olympus / OM, YI). Tap a look to use it; tap a line again, or FOLD, to fold it away.",
            "Light → RAW PROFILE picks the base look: Standard, Flat (most room for grading), Vivid, Portrait, Landscape or Mono.",
            "DJI drone RAW (DNG from Mini, Air, Mavic, Avata, Phantom, Neo and Zenmuse cameras): opens with the drone's own lens corrections applied — vignetting / lens shading, distortion and colour fringing (switch: Lens corrections from the file) — and the drone's own colour profile and tone curve (RAW PROFILE → Camera). RAW INFO shows the drone, height, altitude, GPS, gimbal pitch / yaw, heading and speed. Start page → MERGE PHOTOS develops DNG AEB brackets and panorama sets from the RAW data.",
            "RAW goes further: DNGs from other cameras and apps open too — Fujifilm X-Trans and Quad Bayer sensors, black-and-white cameras, lossy, Deflate and JPEG XL compressed DNGs (newer Galaxy phones), and 16 / 24 / 32-bit floating-point HDR DNGs (e.g. HDR merges from desktop photo software).",
            "Light → RAW PROFILE → Camera uses the camera maker's own profile stored in the DNG (its colour tables and tone curve), when the file has one.",
            "Light → Lens corrections from the file: the distortion, colour fringing and vignetting fixes the phone or camera recorded in the DNG — on by default, switch off to see the uncorrected picture. RAW INFO lists the camera, lens, exposure, sensor and what the file contains.",
            "Exposure (±5 stops), Highlights, Shadows, Whites and Blacks work on the full-range data — pull Highlights down to bring back bright skies, lift Shadows without the blotches you'd get from a JPEG.",
            "Color → WHITE BALANCE: As shot, Auto, Daylight, Cloudy, Shade, Tungsten or Fluorescent, then fine-tune with Temperature and Tint.",
            "The DNG is never changed; exports are JPEG, PNG, WebP or HEIC. RAW exports take a little longer (the full-size photo is developed again at top quality).",
            "AI erase on a RAW makes a normal copy to work on; after that, the photo is edited like a JPEG.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Photo editor tips", Icons.Filled.Photo, null, listOf(
            "Swipe left / right on the whole photo for the next / previous one in the filmstrip; NEXT UNEDITED → jumps to a photo you haven't edited yet.",
            "RECENT above the panels: the sections you opened last. MERGE PHOTOS ▾ in the toolbar has every merge. A panel's RESET can be taken back with UNDO.",
            "Text → PHOTO LAYERS: + adds a photo or texture on top. Pick a blend mode (Multiply darkens, Screen lightens, Overlay / Soft light add contrast) and set opacity, size and position; Fill the photo stretches it over the whole picture.",
            "Merge → HDR keeps moving things (people, leaves, water) from one shot only, so there are no ghosts. Merge → Long exposure from a video turns a clip into a long exposure: average for silky water, Light trails for car lights and stars.",
            "SAMPLERS under the photo: tap up to four points to see their R, G, B values while you edit; tap a point again to remove it. FOCUS shows the sharpest parts in green.",
            "File menu (file icon): new, open, open recent, save as, save a copy, revert, capture, import and exit. Keyboard icon: change any shortcut.",
            "? icon: documentation, tutorials, troubleshooting and proof of purchase. Gear → Preferences and System Info.",
            "Network (NAS): + → Network (NAS) opens photos (RAW too) from your Synology or other NAS on the same Wi-Fi. A sign-in is asked for only when needed; remembering it is up to you (encrypted, never shown; gear → Network (NAS) → Remove).",
            "Pinch to zoom in on the photo and drag it; when you let go, the crop becomes what you see. Double-tap = whole photo again. In Masks, Heal and the Retouch brush two fingers only zoom the view.",
            "Crop & perspective → UPRIGHT straightens horizons and buildings for you. Heal → FIND DUST AUTOMATICALLY removes sensor dust.",
            "Export → EXPORT PRESETS, TIFF, colour space (sRGB / P3 / Adobe RGB), output sharpening and file-name templates.",
            "Export → FOR SOCIAL MEDIA: SAVE BEFORE / AFTER PICTURE puts the original and your edit in one labelled picture; PANORAMA CAROUSEL cuts a wide photo into 2–5 slides (1:1 or 4:5) that join up seamlessly when swiped through on Instagram.",
            "Masks → Object (AI): tap an object to select it. COMBINE adds, subtracts or intersects colour, brightness, depth, subject or sky selections.",
            "Effects → LENS BLUR: ANALYSE DEPTH, then PICK FOCUS and tap — a shallow depth of field with the bokeh shape you choose.",
            "Presets: 60 built-in looks with live thumbnails and an Amount slider. Import .xmp presets, or export your look as one.",
            "Color → EYEDROPPER sets white balance from a tap. POINT COLOR changes just one colour. Light → CLIPPING ⚠ shows blown highlights and crushed shadows.",
            "Pinch or double-tap the photo for a 1:1 zoom.",
            "With a mouse: the wheel over the photo goes to the next / previous filmstrip photo, right-click shows the before / after split, and double-click hides the tools.",
            "The + on the filmstrip offers Gallery, Browse files and Camera. Camera opens the full Capture screen with all its pro controls; each photo you take goes straight into the filmstrip. \"← Back to photo editor\" (or Back) returns.",
            "The filmstrip is the open catalog: each photo keeps a copy, its edit and settings, ratings and AI results (saved as you work). ⚙ → Catalog: new / open / rename / delete catalogs, how many photos to remember (10 or more, or Unlimited), photo copies on / off, and where the catalog is stored (phone, shared storage, SD card). Storage is lossless only.",
            "Hold a photo in the filmstrip → Select multiple: tap photos to tick them, hold any photo for the selection's menu (stars, flags, apply the open photo's edit, export, rename, remove), and Deselect all goes back to one photo. Holding a photo also offers Apply the open photo's edit and Export this photo.",
            "Photo editor: while photos are picked in the filmstrip, every change you make on the open photo — light, colour, detail, effects, lens, crop, masks, text and more — is made the same way on the picked photos (only the settings you touch; AI-made masks and AI-erased copies stay each photo's own).",
            "Pick many photos at once: they go in the filmstrip under the photo. Tap to switch (each keeps its own edit), hold for stars / Pick / Reject / colour label, SELECT to sync the edit, export or remove several.",
            "Switching photos is instant: a RAW shows straight away (its last render, or the camera's preview matched to the editor's colours) and the full RAW render fades in a moment later. The photos either side are prepared ahead.",
            "Hold your finger on the photo to see the original.",
            "Leave the editor with your phone's back gesture or back button (the top bar has no back arrow, so EXPORT always fits).",
            "Masks → Subject (AI) / Sky (AI) select the subject or the sky for you (on the phone, or more precisely with Cloud AI).",
            "Effects → BACKGROUND (AI): FIND SUBJECT, then blur the background (portrait look), remove it (transparent PNG) or fill it with a colour.",
            "Effects → BACKGROUND (AI) → Double exposure: pick a second picture (a forest, a city, the sea); it shows inside the subject's silhouette and the background takes a plain colour. Blend sets how strong the second picture is.",
            "Text → THUMBNAIL → MAKE A THUMBNAIL: the photo is cropped to 16:9, a big yellow title with a black outline is added and the subject pops out of a darker background with a glow (Background → Pop out). Tap the title to change it; Outline sets the edge round any text, in the photo and the video editor.",
            "Heal → AI REMOVE: TAP TO SELECT a person, car or sign (tap several), or PAINT over a wire, then REMOVE. The AI paints in what was behind at the photo's full size (on the phone with LaMa, or Bria Eraser with Cloud AI). RESET goes back to the original.",
            "Masks → People (AI): pick face skin, body skin, eye whites, irises, eyebrows, lips, teeth, hair or clothes of everyone in the photo. PORTRAIT LOOKS (Smooth skin, Whiten teeth, Brighten eyes…) set the parts and adjustments in one tap. Every mask now has Texture and Clarity too.",
            "Keyboard (Bluetooth / USB / DeX): ← → next photo, 0–5 stars, P / X / U flags, 6–9 colour labels, \\ before / after, Z zoom, Ctrl+Z undo, Ctrl+C / Ctrl+V copy / paste the look, Ctrl+E export. Press ? for the full list.",
            "Export → OUTPUT → Ultra HDR (Android 14+): JPEGs whose bright parts glow brighter on HDR screens, and look exactly as edited everywhere else.",
            "Filmstrip → SELECT → AI cull: rejects blurry shots and closed eyes, and picks the best of each burst. Nothing is deleted — the notes on the thumbnails say why.",
            "REFERENCE (top-left of the photo) shows a reference photo next to yours; MATCH LOOK matches exposure, white balance, contrast and saturation to it. Export → OUTPUT → Gamut warning marks colours a print can't show.",
            "Filmstrip → SELECT → Rename gives the files new names from a template ({name}, {date}, {time}, {seq}). Masks now have Noise reduction too.",
            "Lens → AUTO COLOUR FRINGES and LENS PROFILE fix fringes, distortion and dark corners for you. Masks → Refine edge (hair) makes AI masks follow hair and fur.",
            "Library → Stacks (bursts as one photo), People (by number of people) and Map (photos on a world map). Color → CALIBRATION → FROM A COLOUR CHART calibrates the colours from a photographed 24-patch chart.",
            "Color → Curves → HUE CURVES (photo and video editors): Hue vs Hue, Hue vs Sat, Hue vs Bright and Bright vs Sat — drag a colour's point up or down (e.g. orange leaves → red, only the greens less saturated, a darker blue sky, less colour in the shadows); double-tap a point to reset it.",
            "Photo editor → Heal → Red eye / Pet eye: tap each pupil (FIND RED EYES finds them in faces automatically) — red pupils turn dark and natural, a pet's glowing eyes become dark pupils with a small catchlight.",
            "Library → Duplicates: near-identical photos grouped, the sharpest first (✓ KEEP); reject or select the extra copies in one tap. Nothing is deleted.",
            "Library → select photos → 📍 Geotag from GPX: each photo gets the position a GPS track (GPX from a GPS logger, phone app or drone log) had when it was taken; set the photos' time zone if the camera doesn't store one. Shown on the Map and written into exports — the original files aren't changed.",
            "Export → FORMAT → AVIF (Android 14+) or JPEG XL: smaller files than JPEG at the same quality.",
            "SAVE lists your saved projects: tap one to overwrite it, or Save as new.",
            "Curve or HSL Mixer → ◎ TARGETED: put a finger on a tone or colour in the photo and drag up / down to change exactly that.",
            "Masks: add a Linear gradient, Radial or Brush mask, then use ADJUST INSIDE THE MASK. Drag the handles on the photo (or paint with the brush); red shows where the mask applies.",
            "Heal: tap dust, blemishes or small objects on the photo — a matching patch from nearby covers each one. Clone stamp: tap the source, then tap where to paint. Drag a solid circle to move a spot, drag its dashed circle to change where it copies from, hold a spot to delete it. UNDO LAST removes the latest.",
            "Lens: Distortion straightens bulging lines from wide lenses; Vignetting brightens dark corners; Colour fringes removes purple / green outlines.",
            "Crop & perspective → PERSPECTIVE: Vertical straightens buildings that lean in at the top.",
            "Crop & perspective → SMART CROP: tap 1:1, 4:5, 9:16, 16:9 or 3:2 and the photo is cropped to that shape with faces (eyes about a third from the top), or else the main subject, kept in. Pinch the photo to fine-tune.",
            "Export tab: everything for the file itself (format, quality, size, AI upscale) and the EXPORT PHOTO button are in the EXPORT fold-out; presets, colour & sharpening, file name, metadata and batch have their own.",
            "AI noise reduction is applied to the full-size photo while you edit; the export reuses it. With RAW, set exposure, white balance, highlights, shadows, whites and blacks first: changing those afterwards runs the AI again (clarity, texture, grading etc. never do).",
            "Export: JPEG (100 % = maximum quality, full colour detail), PNG, WebP or HEIC; sizes for Instagram / web / 4K or Original (full resolution); AI 2× / 4× upscale (up to 50 MP); keep or remove camera info and location; BATCH EXPORT applies your edit to up to 50 photos. Exports keep running if you leave the app or turn the screen off — pull down the notification shade to see every background task and its progress.",
            "Start page → MERGE PHOTOS: HDR merge (bracketed shots), Panorama (overlapping shots, left to right) or Focus stack — the result opens in the editor.",
            "SPLIT (top-left of the photo) shows before / after side by side — drag to move the divider.",
            "Presets → MY PRESETS → + SAVE CURRENT LOOK saves your grade; tap a preset to apply it, hold to delete. Presets also work in the Video editor.",
            "Presets → MY LUTS → + IMPORT adds your own .cube LUTs; use Strength to blend, hold a LUT to delete it.",
            "Sliders update live while you drag.",
            "COPY SETTINGS / PASTE SETTINGS moves a look to another photo — or to a video clip.",
            "SAVE keeps the edit as a project; LOAD opens it again later.",
            "Hold the divider between the photo and the controls, then drag to resize. Double-tap to reset.",
            "History panel: tap any step to go back to it; + SAVE SNAPSHOT keeps a version you can return to while the photo is open.",
            "Masks → Colour range / Brightness range: tap the photo to pick a colour or brightness; only those parts are changed (e.g. only the blue sky, only the highlights). Range and Softness fine-tune it.",
            "Panels icon (next to the settings gear): hide or show the tools panel, copy / paste bar, histogram and before / after buttons — the photo grows into the space and shrinks back when a panel returns. Presets: Full, View, Edit, Color. Triple-tap the icon to reset the whole layout.",
            "Detail → NOISE REDUCTION → Colour noise: removes blotchy colour specks (e.g. blue patches in the shadows of photos taken in dim, warm light) while keeping detail. Works on RAW and JPEG.",
            "Undo / redo are in the top bar; the histogram toggles with the chart icon.",
            "Merge photos → Star stack: 2–8 night-sky shots from a tripod are lined up on the stars (shift and turn) and averaged for a clean, detailed sky.",
            "Merge photos → Star trails: pick 10–100 night-sky shots taken in a row from a tripod (an interval timer, e.g. one every 30 s); the brightest of every shot is kept, so each star draws its arc across the sky.",
            "Crop → EXPAND (CLOUD AI): pick a shape (16:9, 3:2, 4:3, 1:1, 4:5, 9:16 or wider all round) and where the photo sits; the empty parts are painted in (Bria Expand via your fal.ai account). Optional: describe what to add.",
            "Retouch → Liquify: drag on the photo with Push, Pinch, Bloat, Twirl or Restore; Brush size and Strength set the brush. UNDO LAST takes back one stroke.",
            "Retouch → Liquify → FACE (AI): FIND FACES, then shape every face with the Eyes, Face width, Chin, Nose and Smile sliders (right = more, left = less). Subtle settings look most natural.",
            "Retouch → Skin (frequency separation): Smooth tone evens out blotchy skin, Texture keeps the pores (100% = as photographed). Pick Whole photo, Face skin or All skin (AI finds the skin).",
            "Retouch → Relight (AI): WORK OUT THE DEPTH once, then place the light in the square; Light brightens what faces it, Shadows darkens the rest, Warmth colours the light.",
            "Library → select photos → Print layout: a PDF at exact print sizes (A4, A3, Letter, 10×15, 13×18 paper; 1–6 per page or fixed 10×15 / 13×18 / 9×13 / wallet prints), borders, fill or whole photo, crop marks.",
            "Library → select 2–9 photos → Collage: Grid, Big + small, Rows, Columns or Mosaic; shape (1:1, 4:5, 9:16, 16:9, 3:2), spacing, rounded corners and background — saved as a photo.",
            "Light → CAMERA PROFILE (.dcp): IMPORT .DCP to use a DNG camera profile (e.g. one made with a colour chart) instead of the RAW file's own colour. Only profiles for this camera are listed; File's own goes back.",
            "Export → Printer profile (.icc): import the ICC profile for your printer and paper to soft-proof — the screen shows the photo as it will print. Perceptual or Relative colorimetric; Paper white & ink black shows the paper's own white.",
            "Merge photos → Noise stack (2–8 shots averaged for a clean picture), Remove passers-by (3–8 shots from the same spot; what moves is taken out) and Super resolution (4–8 hand-held shots → twice the width and height).",
            "Export → Export recipe: tick several presets (e.g. Web + Print + Archive), then EXPORT WITH N PRESETS saves the photo once per preset, each with its own size, format and file name.",
            "Crop → GUIDES: Thirds, Golden ratio, Golden spiral, Golden triangles, Diagonals or Grid over the photo while cropping; TURN ↻ flips the spiral and triangles. Only on screen.",
            "Library → select photos → Contact sheet PDF: the photos as a grid on A4 or Letter pages (3–6 per row) with file names, stars and flags — for print or to send.",
            "Effects → COLOUR SPLASH keeps one colour and turns the rest black and white; TILT-SHIFT MINIATURE blurs the top and bottom so a scene looks like a model (best from high up); SUN GLOW & RAYS adds a warm sun with soft rays where you place it.",
            "Masks → Dodge / Burn: paint to lighten (dodge) or darken (burn) parts of the photo; each one is its own mask you can change or delete.",
            "Export → METADATA: title, caption, author, copyright and keywords written into the exported photo (JPEG, PNG, WebP), kept with the edit.",
            "Photo editor → Text: TEXT EFFECTS, MORE TEXT and NAME LABELS (lower thirds) add styled text; a selected text has Font, Effect and colour.",
            "Font: every text (photo and video editor) can use about 80 fonts in five groups — Simple (incl. Arimo, the free twin of Arial, and Carlito for Calibri), Serif (Tinos for Times New Roman), Typewriter & mono (Cousine for Courier New), Script & handwritten, and Display & fun. Each font is shown in its own lettering; Effect below it adds a special look.",
            "Library → select photos → ▶ Slideshow video: the photos become clips on a new video timeline in the video editor, with seconds per photo, a slow zoom and a cross dissolve or dip between them — add music and titles there, then export.",
            "Filmstrip: hold a photo and tap a colour for a colour label (red, yellow, green, blue, purple) — with SELECT, hold any photo to label all the selected ones. It shows as a stripe along the top; the colour dots next to the filters show only that colour. Labels show in the Library too.",
            "Library → People → Find people (AI): faces are recognised on the phone (the face model is downloaded once) and grouped by person. Tap a face to see only their photos; hold it to type a name — then search by that name. Giving two groups the same name puts them together.",
            "Presets → RECOMMENDED FOR THIS PHOTO: the AI looks at what's in the photo and its light, and suggests a few fitting looks, each with a short reason. Tap one to try it (Amount and UNDO PRESET work as usual).",
            "Heal → AI REMOVE → REMOVE REFLECTIONS: for photos taken through a window — the soft reflected picture fades and the scene behind stays sharp. Strength sets how much goes; RESET goes back to the original.",
            "Heal → AI REMOVE → FIND WIRES: power lines and cables are found and selected in red; tap REMOVE to take them out, or paint / tap to add more first.",
            "Library → select photos → Keywords: type keywords separated by commas and nest them with › (Places › Faroe Islands › Tórshavn); tap suggestions from what the AI sees. The Keywords view shows the tree with counts — tap a keyword to see its photos.",
            "Filmstrip + → Tethered camera (USB): connect a camera with its USB cable (PTP / MTP), tap START; every shot you take comes straight into the filmstrip, opened with the develop preset you chose.",
            "Library → Watched folders…: add folders (a camera upload folder, a synced NAS folder…); new photos and videos that land there are copied in by themselves — when the app starts and every minute while a library is open — with a metadata preset and a develop preset.",
            "Library → select photos → Photo book: title, page size, photos a page (mixed, 1, 2 or 4), border, white or black pages, cover and page numbers — saved as a print-ready PDF.",
            "Export → CONTENT CREDENTIALS: exported photos carry a signed record (C2PA) of who made them and that they were edited in AlcorCut Pro. The certificate is made on your phone, so checkers show the signer as not verified by an authority. Android 9 and newer.",
            "Library → 📅 Calendar: tap a year, a month, then a day (each shows how many photos) to see only that time; 📅 … ✕ shows everything again.",
            "Library → select photos → a colour (red, yellow, green, blue, purple) gives them a colour label; the dots at the top filter by label. + Smart collection gathers photos by rules (stars, flag, label, date, RAW / JPEG, location, keyword, text).",
            "Library → select photos → Metadata preset: presets with your name, copyright, contact, caption and keywords, written into the photos when you export.",
            "Library → IMPORT: copy photos and videos from a memory card, USB drive or a camera on its USB cable (PTP / MTP) — new files only, renamed, a folder per day, a backup copy, a metadata preset and a develop preset.",
            "Library → select photos → Client proofing: a web gallery (.zip) for your client to mark favourites, stars and notes; paste their list under Library → Client picks… and those photos become picks.",
            "Library → select 10 or more photos → ⏱ Time-lapse video: frame rate, HD / 4K, a look that can blend into another, an exposure ramp and Deflicker. Saved in Movies/AlcorCut Pro.",
            "Presets → ADAPTIVE PRESETS (AI): Pop the subject, Darken background, Blue sky, Dramatic sky, Polished portrait and more find the subject, sky or people in each photo and adjust only those — as masks you can change.",
            "Heal → AI REMOVE → REMOVE PEOPLE selects everyone in the photo (shown in red); tap REMOVE to take them out, or tap / paint to change the selection first.",
            "Merge photos → HDR panorama: pick the bracketed shots of every position, left to right (e.g. 3 exposures each); each position is merged to HDR, then they're stitched.",
            "Export → UPLOAD AFTER EXPORT: choose a folder (Google Drive, OneDrive, your NAS's app, a USB drive) and every exported photo is also copied there.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new photo automatically.",
            "SAVE LOCATION chooses where exported photos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.",
            "Change the app colour under APP COLOUR.",
            "\"Always start in Photo editor\" opens the app straight into this editor.",
        ), numbered = false),
    )
    GuideVariant.VIDEO -> listOf(
        cloudSection(v),
        GuideSection(GuideTopic.PHONE_AI, "Catalog", Icons.Filled.AutoAwesome, null, listOf(
            "Cast to TV: tap the cast button (tablets: next to the zoom buttons below the picture; phones: Deliver → CAST) and pick your Chromecast / Google TV on the same Wi-Fi. The viewer shows on the TV a few seconds behind; tap again to stop.",
            "Media page → Camera → media list, or + Camera on the media strip: opens the full Capture screen with all its pro controls. Videos you record and photos you take (each becomes a 5 s clip) go into the media list, not onto the timeline. \"← Back to video editor\" (or Back) returns.",
            "⚙ → Catalog: the video catalog remembers each clip you use — its last settings (given back when you add it to another project) and its proxies, stabilisation analysis, waveforms, render cache, cloud AI results and transcripts.",
            "Create, name, rename, open and delete catalogs; choose how many clips to remember (10 or more, or Unlimited); keep copies of clips (off by default); move the catalog to private storage, shared storage or an SD card; Slim down frees what can be made again.",
        ), numbered = false),
        GuideSection(GuideTopic.PHONE_AI, "AI noise reduction", Icons.Filled.AutoAwesome, null, listOf(
            "Color → Detail → AI NOISE REDUCTION → move Strength above Off.",
            "It's applied when exporting; the preview shows the normal noise reduction. The first export cleans each clip once and keeps it in the video catalog — later exports reuse it and are much faster.",
            "On the phone it can take several minutes up to about an hour (tap ⓘ next to it); Cloud AI does it in minutes.",
            "Tap STOP on the AI badge to cancel.",
        ), numbered = false),
        GuideSection(GuideTopic.TIPS, "Video editor tips", Icons.Filled.Movie, null, listOf(
            "Deliver → Codec → CineForm (.mov): a pro editing file for finishing on a computer (large files; the edit is rendered, then converted). Pick the quality: Film scan 1 is a good default.",
            "While rendering, KEEP EDITING lets you carry on; the notification says when it's done and can share the file.",
            "Each project reopens with the timeline as you left it. If clips have moved, FIND THEM looks for them by name.",
            "Edit → PHOTO → ✎ EDIT IN THE PHOTO EDITOR opens the photo with all the photo tools; when you export, the new photo replaces the one on the timeline.",
            "Deliver → CLIENT REVIEW: export the video first, then make the review page. Send the .zip; your client opens index.html, adds notes at exact times and sends them back. Paste them in and they become markers.",
            "Smart bins (⚙ chips in the media strip) show only photos, drone clips, 4K and up, vertical clips, clips under 10 s or Log / HDR clips.",
            "File menu (file icon): new and open projects, several open at once (File → Open projects), save as / save a copy / revert, capture, import, new sequence, bin, offline file, captions, transcript, title, team projects, collect files, exit.",
            "Team projects: File → New team project puts the project in a shared folder on your NAS (or a shared folder); the team sees who has it open and can open it read-only meanwhile.",
            "Keyboard icon: every shortcut — tap one and press the key you want for it.",
            "Under the picture: Speed / Duration, Time interpolation (optical flow slow motion), Link / unlink tracks, Group / ungroup and the Markers menu (go to, edit, clear, export / import lists).",
            "File → Project settings: Ingest puts new media in a bin, makes proxies for heavy clips and labels them by kind; Label colours names the colours.",
            "? icon: documentation, tutorials, troubleshooting and proof of purchase. Gear → Preferences → Audio hardware picks the microphone and speaker.",
            "Network (NAS): Media → Network (NAS) finds your Synology or other NAS on the same Wi-Fi (or type its address). Pick videos, photos and audio; they're copied into the app. Sign in only if the NAS asks — then choose whether to remember it (encrypted, never shown; gear → Network (NAS) → Remove). A saved sign-in opens with the phone's fingerprint, PIN or pattern — they stay in the phone.",
            "Buttons under the picture: frame steps, in / out, marker, safe margins, playback resolution (preview only), magnifier (view only), export frame, settings. The + at the end chooses which ones you see.",
            "Pinch on the picture to zoom in and drag it into place — the export is framed exactly as seen. Double-tap = whole picture again.",
            "Video editor: the Play button effect has its own colour, apart from the scroll wheels — gear → Layout & look (or Playback) → Play button colour.",
            "Video editor: with Play button effect on, the Play button looks like the scroll wheels — their glass gradient in the wheel colours, a turning rim, and flickering \"upscale\" pixels and sparkles while playing. Gear → Layout & look (or gear → Playback) → Play button effect: on = the animated look, off = the plain amber button. The row under the picture starts with Play in the middle until you scroll it.",
            "Video editor, buttons under the picture: Play is bigger (an amber circle) and sits in the middle of the playback buttons — mute, go to in, previous clip and frame back on its left, frame forward, next clip, go to out and start on its right — and the row starts with Play centred under the picture; swipe the row for the rest.",
            "Video editor → Deliver → Resolution: 5K, 5.1K, 6K, 7K and 8K (5120×2880 up to 7680×4320 in 16:9; 5.1K = DJI's 5120×2700 with the project shape on Original), with 300 and 400 Mbps bitrates. The app checks the phone's video encoder and says whether that size works, at what highest frame rate, and when the other codec is needed (e.g. the Galaxy S24 makes 8K at up to 30 fps; most tablets stop at 4K).",
            "Video editor → Deliver → Resolution: 2.7K (2688 × 1512), 5.3K (5312 × 2988, GoPro), 5.4K (5472 × 3078, DJI Air 2S) and 5.7K (360) — a 360° video exports at 5760 × 2880 and stays 360° (marked so players, YouTube and Facebook show it as a sphere); any other video exports 5760 wide in its own shape. A 360° video exported at any 2:1 size keeps its 360° mark.",
            "Hold the middle of a clip on the timeline for its menu: Select multiple, Split at the playhead, Duplicate, Speed / Duration, Reverse, Boomerang (the clip forwards, then a backwards copy straight after it), Colour & details (the clip's colour and detail controls — exposure, contrast, highlights, shadows), Reveal in project (its file picked out in the media strip), Reveal on device (the folder its file is in), Mute sound, Delete. While selecting, tap clips to tick them; hold any clip for Deselect all, Select all, Duplicate, Mute, Nest into one clip and Delete selected. The ✕ on the timeline's SELECTED badge also ends it.",
            "Tap anything on the timeline to open its settings: a clip opens Edit, a transition opens Transitions, a layer (text, image, visualizer, progress bar, countdown, map…) opens Layers with its own settings unfolded, and the music or another audio track opens Audio at that track. Hold a layer or audio bar and drag to move it; hold near its start or end and drag to make it shorter or longer — the same as clips.",
            "Hold a file in the media strip: Append, Insert, Overwrite, Open in source viewer, Colour & details (for its clip on the timeline), Reveal in project (jumps to where it is used on the timeline), Reveal on device, stars, Reject and Remove.",
            "Color → Curves → HUE CURVES (photo and video editors): Hue vs Hue, Hue vs Sat, Hue vs Bright and Bright vs Sat — drag a colour's point up or down (e.g. orange leaves → red, only the greens less saturated, a darker blue sky, less colour in the shadows); double-tap a point to reset it.",
            "Video editor → Edit → PRIVACY BLUR: FIND FACES finds every face in the clip on the phone and blurs it as it moves; + SPOT — tap anything else (a number plate, a screen, a sign) on the frame at the playhead and it's followed to the end of the clip. Blur or pixelate, strength and extra room; in the preview and the export — the preview changes at once while you adjust it.",
            "Video editor → Audio → PITCH & VOICE: raise or lower a clip's voice up to an octave without changing its speed, and Robot, Radio, Phone or Megaphone voices — heard live in the preview and in the export.",
            "Video editor: while clips are picked (Select multiple or SELECT ALL), every change you make — colour, primaries, wheels, curves, LUTs, detail, effects, speed, audio, transitions, crop and more — is made the same way on all picked clips (only the settings you touch; each clip keeps its own media, trims and the rest of its edit).",
            "Color → Primaries: Clarity and Texture under Dehaze — clarity is broad mid-tone punch (or a softer, dreamier look below 0), texture brings out or smooths fine detail like skin, leaves and fabric.",
            "Video editor → Color → VERSIONS A / B / C: tap + to save the clip's look (colour, detail and film look) as a version, tap a version to switch back to it and compare, hold one to save over it. Saved with the project.",
            "Timeline bar: SELECT ALL / DESELECT ALL next to + VIDEO, ◆ MARKER and SNAP, and ♪ DETACH AUDIO — the selected clip's (or every picked clip's) sound becomes its own audio track below the video. Holding a clip offers Detach audio too.",
            "Hold a clip's edge and drag — the edge follows your finger. Shortening leaves a black gap and everything stays put; gear → Trimming closes the gap moves everything after it left on every track.",
            "Track headers: eye = hide, speaker = mute (video layers have their own sound switch), lock = protect from edits. IN / OUT flags on the ruler show the export range; gear → Marker effects turns the shine off.",
            "Delete a clip: tap it, then tap the red bin on top of it — CLOSE GAP moves the next clips left, LEAVE GAP keeps them in place.",
            "Media strip above the timeline: hover (mouse) or hold and slide to skim, tap to pick, double-tap to append. Stars and reject help you sort; USED marks what's already on the timeline. + Folder imports a whole folder.",
            "With a mouse: drag clips from the media strip onto the timeline, Ctrl + wheel zooms the timeline, Shift + wheel scrolls it, right-click opens a clip's tools, and double-click the player for full screen.",
            "4:2:2 footage (10-bit 4:2:2 H.264 / H.265 from Sony, Panasonic, Canon, Fujifilm, DJI…): the Media page shows a 4:2:2 badge. The editor finds a decoder on your phone that can read it, plays it directly (add it to the Proxy tab for smoother editing) and exports from the original (as 4:2:0 — phone encoders don't make 4:2:2). If no decoder on the phone can read it, the clip is marked in red.",
            "Media → MULTICAM (with 2 or more clips in the pool): pick 2–4 angles filmed at the same time — the first is the main angle for sound and timing — then SYNC BY SOUND & START. Tap an angle to start on it, press PLAY and tap angles while it plays to cut between them. DONE adds the edit to the end of the timeline.",
            "Audio → SYNC (on any audio track) or ⇆ SYNC TO THE SELECTED CLIP (music track): lines sound from an external recorder or wireless mic up with the selected clip by matching the sound. A clap at the start of the take makes it bulletproof.",
            "Layers → DRONE TELEMETRY: select the drone clip, choose what to show (altitude, speed, GPS, camera) and LOAD FLIGHT DATA (.SRT) — pick the .SRT your DJI drone saved next to the video. The read-out follows the clip's trim and speed.",
            "Edit → TRIM TOOLS: SLIP shows another part of the clip in the same slot, ROLL moves the cut to the next clip, SLIDE moves the clip between its neighbours — by 1 or 10 frames per tap. Normal trims ripple the rest of the timeline.",
            "Edit → NEST: joins the selected clip and the next ones into one clip, to grade, speed up or reframe them together. UN-NEST opens it up again.",
            "Auto-save: the editor keeps a recovery copy of your edit every 20 seconds. If the app closes unexpectedly, the start page offers RECOVER.",
            "Media → SOUND LIBRARIES: tap Soundtracks to browse the built-in music, ▶ to listen, + ADD to put a track on the timeline at the playhead. It plays in the preview and is mixed into your export, like any other music.",
            "Start page → SKIP — OPEN THE EDITOR goes straight into the editor with an empty timeline, without choosing a video first; add clips later from the Media page. It only happens when you tap it.",
            "Start page → each video thumbnail has a small file-type tag (MP4, MOV, MKV, WEBM…) so you can see what you're opening. The tag is only on the start page, not in the editor.",
            "The timeline starts at the left edge: drag one finger (or tap the ruler) to move the playhead; two fingers pinch to zoom and slide to scroll. The scissors under the player split at the playhead.",
            "The button bar under the player (split, undo, zoom, play, full screen…) slides sideways when it doesn't fit the screen — small moving arrows show the side with more buttons; swipe it left / right to reach every button.",
            "The glowing wheel in its own window on the timeline's right edge (as tall as the timeline) scrolls the tracks up and down: drag it like a scroll wheel, or tap it to jump.",
            "Video layers: tap + VIDEO on the timeline (or Layers → Video) and choose ABOVE or BELOW the main video, then pick the clip. Add as many as you like — each gets its own row: layers above the video sit above the V1 row (the top row is the topmost layer), layers below sit under V1. Above layers sit on top as picture-in-picture (Size 100% = full frame); below layers sit behind your clips and show wherever the main video doesn't cover the frame. Change it later in the layer's PLACEMENT setting, and drag a row up / down to reorder.",
            "Tap a cut on the timeline to open Transitions: VIDEO TRANSITIONS has 91 picture transitions, including the CINEMATIC group (film burn, anamorphic flare, lens warp, letterbox close, aperture, smoke, shatter, kaleidoscope, cube turn, card flip, sun rays, neon edges, ember drift, rack focus, luma fade, shine sweep, bokeh lights, prism split), the DRONE & TRAVEL group (cloud pass, warp zoom, drone roll, dolly zoom, film gate roll, heat haze), the CREATIVE group (paint splash, honeycomb, heart and star iris, checkerboard, water drop, TV switch, pixel rain, ink drop, mirror fold, overexpose and more) and three glitches (datamosh, VHS rewind, pixel sort); AUDIO TRANSITIONS sets how the sound changes (constant power, S-curve, dramatic pause, late / early swap, swell, dip to silence, plus 35 with sound effects — underwater, echo out, reverb wash, tape stop, stutter, telephone, old radio, pitch dive, riser, DJ rewind, record brake, pump, lo-fi and more). On a plain cut the sound fades out and in around the cut. SOUND EFFECTS (same page) puts a sound on the cut: 24 built-in sounds in four groups — hits (impact, boom, trailer hit, braam, drum hit, punch, thunder, heartbeat, sub drop), whooshes (whoosh, swoosh, deep whoosh, swirl, wind gust), builds (riser, reverse cymbal, clock tick build, tape stop) and stylized (glitch, record scratch, laser zap, magic shimmer, camera shutter, TV static) — tap ▶ to listen. MY SOUNDS → + IMPORT SOUND adds your own files (MP3, WAV, M4A…); choose whether each starts on, centres on or ends on the cut.",
            "The RGB histogram is a pull-out drawer on the right edge of the player: drag the small tab to the left to pull it out, drag it back (or tap the tab) to tuck it away. It never shows in full screen.",
            "Layers → AI CAPTIONS writes timed captions from the speech in your edit (Whisper via your fal.ai key); pick the language and style.",
            "Edit → AUTO-REFRAME turns the project into 9:16, 1:1 or 4:5 and makes every clip follow its subject.",
            "Edit → AI slow-mo (Cloud AI) adds 3 new frames between every pair and plays the clip at ¼ speed — smooth slow motion from normal footage (clips up to 20 s).",
            "Keyframes: select a layer → KEYFRAMES → ◆ ADD KEYFRAME AT PLAYHEAD, move the playhead, then drag / resize / fade the layer — it glides between keyframes (diamonds on the timeline).",
            "Motion tracking: select a layer, put its centre on the object at the playhead, then KEYFRAMES → ⌖ TRACK MOTION FROM PLAYHEAD — the layer (text, sticker, blur…) follows the object; tap again to stop. Works best on sharp, high-contrast spots.",
            "Audio → VOLUME KEYFRAMES rides a clip's volume over time (e.g. dip it under a voiceover).",
            "Edit → SPEED RAMP gives smooth speed curves (Montage, Hero, Bullet, Flash in / out…); the clip's length follows the curve.",
            "Edit → Freeze adds a 2 s still of the frame under the playhead; Reverse plays the clip backwards (silent, up to 60 s).",
            "Edit → Detach audio turns the clip's sound into its own track — move or extend it for J- and L-cuts.",
            "Audio → VOICEOVER records the microphone at the playhead while the edit plays (muted); it lands on its own audio track.",
            "Audio → ADD BEAT MARKERS puts a marker on every beat of the music (or tap BEATS on any audio track); with SNAP on, layers lock to them.",
            "Layers: BLEND MODE (Screen, Multiply, Overlay…) for picture, text and sticker layers; CHROMA KEY removes green / blue screens from video and image layers.",
            "◆ MARKER above the timeline drops a marker at the playhead (tap again on it to remove); tap the ruler near a marker to jump to it.",
            "SNAP makes layers and audio snap to cuts, markers, the playhead and each other while you hold-and-drag them.",
            "Audio bars and clips show real waveforms, so you can cut on beats and words.",
            "Color → LUTs → + IMPORT adds your own .cube LUTs; \"Use as input conversion\" turns a camera maker's log LUT into the clip's input transform. MY PRESETS saves and applies your looks.",
            "Deliver → PRESETS sets YouTube, Instagram, TikTok and more in one tap; RANGE exports only the part between IN and OUT; SAVE FRAME AS PHOTO saves a still.",
            "Edit → ANAMORPHIC DESQUEEZE stretches clips from anamorphic lenses back to their real shape.",
            "Deliver shows the estimated file size; it updates as you change resolution, frame rate, bitrate and audio.",
            "The buttons at the left of each timeline row mute the video track or an audio track, hide a layer (the eye: tap it and the layer disappears from the video feed and the export; tap again to show it), or lock the row so it can't be moved, trimmed, split or deleted.",
            "Deliver → Audio only saves just the sound of your edit. AUDIO FORMAT: AAC .m4a, WAV (16-bit, 24-bit or 32-bit float), AIFF 24-bit, FLAC (lossless, about half the size of WAV) or Opus .ogg (Android 10+). WAV, AIFF and FLAC are made from the finished mix itself, so they're lossless.",
            "Undo and redo sit right next to the scissors under the player and are always visible — they step back and forward through clip, layer and audio changes.",
            "Give any layer its own name (Layers → selected layer → name) — handy for telling watermarks apart; the watermark text on the video is a separate field.",
            "Hold a layer or audio bar on the timeline until it buzzes, then drag: left / right moves it in time, up / down changes the layer order.",
            "Each video clip, layer and audio bar has a handle at both ends (the bracket tab). Hold a handle and drag to lengthen or shorten it. A clip can't be made longer than its video file (the clips after it move along); a layer can't go past the start or end of the edit, and a video layer not past the end of its own clip. The new length shows while you drag.",
            "Media → Audio adds music and sound files to the media pool; ADD TO TIMELINE places them at the playhead.",
            "Edit → Crop & zoom: pinch, drag and twist on the player; Ken Burns animates start → end.",
            "Edit → KEYFRAMES: move the playhead, set zoom, position, rotation and opacity, and tap ◆ ADD KEYFRAME; add more at other moments and the clip moves smoothly between them. Tap a keyframe's time to jump there, its easing to switch Smooth / Linear / Hold, ✕ to delete. Diamonds show on the clip in the timeline.",
            "Text page: TRANSCRIBE THE EDIT turns the speech into text on this phone (download the speech model once: Fast 57 MB or Accurate 181 MB). Tap words to cut them (they turn red), REMOVE FILLERS marks um / uh / øh, then APPLY CUTS removes them from the video — undo brings them back. Hold a word to jump there.",
            "Text page → REMOVE FILLERS: choose what to find — um / uh, repeats & stutters (“I I think”, “w- we”), and if you like filler phrases (you know, like, I mean). They're marked red so you can check them before APPLY CUTS. Transcribe again after updating for the most complete result: the transcript now keeps the ums.",
            "Multicam → AUTO: WHO'S TALKING cuts to whoever is speaking, from each angle's own microphone (an interview or podcast filmed with a camera per person). Pick a Wide shot angle and it's used for cross-talk, pauses and now and then in long answers. PLAY to watch the cut; tap an angle while it plays to change a shot; DONE adds it to the timeline.",
            "Edit → AUTO HIGHLIGHTS → Reel 15 s / 30 s / 60 s / 2 min: finds the liveliest moments of your clips (laughter, cheering, a raised voice, movement, lively talk) and rebuilds the timeline as a short reel, in story order and cut between words. Transcribe first for the cleanest cuts; undo brings your clips back.",
            "Edit → SMOOTH SLOW MOTION · OPTICAL FLOW: ½, ¼ or ⅛ speed with new in-between frames drawn from the motion on the phone — no stutter. Keep my speed smooths a clip that already has a slow speed or ramp. The clip keeps its sound; undo brings the original back.",
            "Edit → MOTION BLUR: Light 90°, Natural 180° or Strong 360° — moving things get the blur a film camera's shutter gives, worked out from the motion on the phone. Great for screen recordings, drone shots, fast action and timelapses.",
            "Layers → selected layer → CORNER PIN & PLANAR TRACK: ◇ PIN TO FOUR CORNERS, then drag the four amber corners onto a screen, sign or wall — the layer (picture, video, text, shape) is drawn in perspective. ⌖ PLANAR TRACK FROM PLAYHEAD follows the surface as the camera moves.",
            "Layers → Shape: rectangle, rounded box, ellipse, line, arrow, triangle, star or heart, filled or as an outline, in any colour. Shapes animate, keyframe, blend and corner-pin like any layer.",
            "Layers → Lottie: add a Lottie animation (.json or .zip, exported from After Effects — e.g. from lottiefiles.com; check each one's licence). It loops over the layer's length and moves, scales, fades and corner-pins like any layer.",
            "Layers → OVERLAYS: Cinema bars (2.39, 2.35, 2, 1.85 or 2.76:1, still or sliding in and out), Flight path (pick the drone's .SRT for the selected clip: its route as a mini-map with a dot that follows the drone), Progress bar (fills over the video; 8 styles incl. Scroll wheel, the look of the timeline's scroll bar; top or bottom, thickness and colour), Countdown (an animated ring that empties with the seconds left in the middle; automatic = the layer's length, or manual from your number of seconds; 25 styles, incl. digital, film leader, hourglass, flip clock, radar and battery) and Lens flare (warm sun, anamorphic blue or soft glow; place it on the sun with Across / Down). Visualizer: bars, mirror bars, glow line, circular, waveform, LED equalizer, radial wave, sound rings, particles, bass orb and 20 more (dot matrix, neon bars, skyline, flames, aurora, vinyl, heartbeat, flower…), up to full size, that move with the music's frequencies and pulse on the beat (from the music track, or the selected clip's sound). All are drawn into the export; trim them on the timeline.",
            "Color → Masks → Shape (pen): a smooth curve through points you place. Drag the points on the player, tap to add one, hold one to remove it; set the Soft edge, then ⌖ TRACK THE MASK FROM THE PLAYHEAD and it follows what it covers.",
            "Color → Detail → REMOVE AN OBJECT (AI): tap a person, car, sign or wire and it disappears from the whole clip. What's behind it comes from the frames before and after along the motion (the real background); only what is never seen is painted by the AI, and kept steady. It takes a while; undo brings the original back.",
            "Color → Detail → AI DEPTH: Lens blur (a real depth of field — set the Focus and the Sharp zone), Fog / haze that thickens with distance, or Background light (darker, calmer background behind your subject). ANALYSE DEPTH works out the distance of everything in the clip on the phone; Show depth map helps set the focus.",
            "Audio → AI VOICEOVER FROM TEXT: type what should be said, pick one of 12 natural English voices and the speed, then MAKE VOICEOVER AT THE PLAYHEAD — it's added as an audio track (made on the phone; the voice model is downloaded once).",
            "Audio → VOICE NOISE REDUCTION → Studio voice (AI): DeepFilterNet3, the cleanest voice clean-up — removes every kind of noise and takes room echo down while the voice stays natural (on export). REPAIR: Remove clicks & crackle, and Remove room echo.",
            "Layers → TRANSLATE CAPTIONS: pick a language and TRANSLATE ALL CAPTIONS — every caption layer is translated on the phone (the language pack is downloaded once). Translated by Google.",
            "Audio → Voice noise reduction → Voice isolation (AI): keeps the voice and removes wind, traffic, hum, crowd and background music (on export).",
            "Audio → Clip audio → SEPARATE VOICE AND MUSIC: the selected clip's sound becomes two audio tracks in line with it — the voice and the music (everything else) — so you can turn the music down under speech, or remove the voice for a backing track. The clip is muted. Uses Demucs via your fal.ai account.",
            "Deliver: exports keep running when you switch apps or lock the screen — a notification shows the progress and tells you when it's done. RENDER QUEUE: set up an export, tap + ADD TO QUEUE, change the settings and add more (e.g. 4K, a 9:16 Reel and an audio file), then START QUEUE renders them one after another.",
            "Edit → SCENE DETECTION: select a long clip (e.g. a whole recording or a finished film) and tap DETECT SCENES & SPLIT — every camera cut is found and the clip is split into separate shots. Undo brings the clip back.",
            "Edit → SELECT CLIPS: tick several clips (or All), then delete, mute, change the speed, duplicate or copy one clip's grade to all of them at once.",
            "Audio → MUSIC TOOLS: FIT MUSIC TO VIDEO shortens or lengthens the song so it ends exactly with your video, on its real ending (a middle part is cut out or repeated on the beat). AUTO-EDIT MY CLIPS TO THE MUSIC rebuilds the timeline with a cut on every beat, every 2nd, 4th or 8th beat. Undo brings your edit back.",
            "Color → BEFORE | AFTER (on the player, Android 13+): the ungraded picture on the left of the divider, your grade on the right — drag the divider across. Tap SPLIT ✕ to close.",
            "Settings (⚙) → VERSION HISTORY: every save, and every 10 minutes while you edit, keeps a version of the project (the newest 30). Tap RESTORE to go back to one, then SAVE to keep it.",
            "Deliver → COPY YOUTUBE CHAPTERS: your timeline markers (their notes become the titles) as a chapter list for the YouTube description, starting at 0:00. YouTube needs at least 3 chapters of 10 s or more.",
            "Layers → Credits adds rolling credits over the last 12 seconds: edit the text (one name per line) and stretch the layer on the timeline — the longer the layer, the slower it rolls. Any text layer can roll: Animation → Roll (credits).",
            "Keyboard (Bluetooth / USB): Space or K play / pause · J / L back / forward 5 s (Shift: 1 s) · ← / → one frame (Shift: 10) · ↑ / ↓ previous / next cut · Home / End · S or B split · Delete removes the selected clip (ripple) · M marker · I / O export range in / out · F full screen · Ctrl+Z undo · Ctrl+Shift+Z or Ctrl+Y redo · Ctrl+S save.",
            "Color → FILM & LENS → SHOT MATCH: tap another clip and this clip's exposure, white balance, contrast and saturation are set to match it (as it looks with its grade). Fine-tune in Primaries.",
            "Color → FILM & LENS: film looks (35mm, 16mm, Super 8…) or your own mix of halation (warm glow round lights), bloom, grain (amount and size) and gate weave. LENS CORRECTION straightens wide-angle bending (action cam, drone, ultra-wide presets), removes colour fringes and brightens corners. DEFLICKER evens out flickering timelapses and lights. Film looks and lens correction change the preview at once while you drag the sliders.",
            "Color → Detail → BACKGROUND: blur, recolour or remove the background. Find: People (automatic) — or Magic Mask: tap any person, animal, car or object (tap “Not it” to leave something out, or drag a box), then TRACK THROUGH THE CLIP and the AI follows it frame by frame (on the phone; it takes a little while and stops at a scene cut). “Affect the subject (invert)” blurs or hides the object itself — a face or a number plate. Transparent lets video layers placed below the main video show through. For a video layer: Layers → REMOVE BACKGROUND (People or Magic Mask). The preview shows the cut-out as the export makes it.",
            "Media → GENERATORS: make a black, colour, gradient, noise or colour-bars clip of any length, or a film countdown leader (from 3–10), added after the selected clip at your project's size.",
            "Media → Source viewer (or SOURCE › on a file): play it, MARK IN and MARK OUT, then INSERT at the playhead (the rest moves along), OVERWRITE (replaces what's there, same total length) or APPEND to the end — three-point editing.",
            "Media → BINS: make bins (Interviews, B-roll, Drone…) and sort files into them with BIN: on each file; tap a bin to see only its files. Edit → Label gives a clip a colour stripe on the timeline. Files that went missing show RELINK — pick the file again and every clip using it is reconnected.",
            "Settings (⚙) → PROJECT ARCHIVE: SAVE ARCHIVE packs the edit and every file it uses into one .zip; OPEN ARCHIVE unpacks one as a new project (e.g. on another phone).",
            "Text page → TITLE TEMPLATES: animated lower third, big title, cinematic title, location, chapter, subscribe and end card, added at the playhead — edit the words like any text layer.",
            "Media strip: tap a clip (or SELECT several) and tap a colour to give it a colour label (red, yellow, green, blue, purple) — also in the clip's long-press menu. It shows as a stripe under the thumbnail; the colour dots next to the filters show only that colour. Labels are shared with the video library.",
            "Loop playback (the circling-arrows button under the picture, two places right of Play): the timeline plays over and over — or only the part between the in and out points when you've set them. It turns amber while on; tap again to stop.",
            "Photos in the video editor stay photos: add them from Gallery, Files, NAS, the camera, Import or the Library window (Photos) — onto the timeline or into the media strip. They're marked PHOTO / 📷; Edit → PHOTO sets how long one shows (1–60 s) and Still or Slow zoom.",
            "Library window → People → Find people (AI): people are recognised in your videos (a frame every few seconds, on the phone). Tap a face to see their videos, hold it to name them, search by name.",
            "Color → Looks → RECOMMENDED FOR THIS PHOTO: looks that suit the clip (the AI looks at a frame from its middle), plus all 60 built-in presets.",
            "Video library → Keywords (select videos) and the Keywords view: nested keywords with suggestions and counts. Watched folders…: new videos landing in a folder come into the video library and, with the editor open, the media strip.",
            "Deliver → CONTENT CREDENTIALS: exported videos carry a signed record (C2PA) of who made them and that they were edited in AlcorCut Pro (Android 9+).",
            "Media → LIBRARY & IMPORT → Video library: every video on the device with search, stars, flags, colour labels, albums, smart collections and a Calendar (year → month → day). Select videos to add them to the media strip.",
            "Media → LIBRARY & IMPORT → Import from card / camera / USB: copies footage in (new files only, renamed, a folder per day, a backup copy); the imported clips go into the media strip.",
            "Deliver → METADATA: a preset with your name, copyright, caption and keywords written into the exported video. Deliver → UPLOAD AFTER EXPORT copies each finished video to a folder you choose.",
            "Text page → LOWER THIRDS: 16 name-and-title designs (Classic bar, News, Pill, Sports, Elegant…). Tap one: it's added at the playhead and opens in Layers, where the first line is the name and the next line the title; change the design and accent colour there.",
            "Text page → TEXT EFFECTS: neon glow, gradient, rainbow, chrome, gold, 3D block, long shadow, hollow, double outline, retro 80s, glitch, fire, ice, comic pop, embossed, soft shadow, highlighter, sticker and pastel. MORE TEXT: headlines, subtitles, quotes, speech bubbles, hashtags, social handles, breaking news, sale tags, dates, call-outs and themed titles.",
            "Font: every text (photo and video editor) can use about 80 fonts in five groups — Simple (incl. Arimo, the free twin of Arial, and Carlito for Calibri), Serif (Tinos for Times New Roman), Typewriter & mono (Cousine for Courier New), Script & handwritten, and Display & fun. Each font is shown in its own lettering; Effect below it adds a special look.",
            "Layers → SPLIT SCREEN: 2 side by side, 2 stacked, 3 columns or a 2 × 2 grid from your video layers, or picture in picture. Each video layer's FRAME sets its shape (cropped to fill), rounded corners, a border and a drop shadow.",
            "Text page → WORD-BY-WORD CAPTIONS: captions from the transcript where each word lights up as it's spoken — Highlight, Box, One word or Two lines. Made on the phone; running it again replaces them.",
            "Transitions → Basic → Morph cut: for jump cuts in interviews — AlcorCut Pro analyses the two frames and draws new in-between frames, so the head glides to its new place instead of jumping or cross-fading (try 0.3–0.6 s). “Analysing morph cut…” shows while the preview is prepared; the export draws the frames at full size.",
            "The glowing wheel along the bottom of the timeline moves through the edit left and right (drag it like a wheel, or tap to jump); the one on the right moves the tracks up and down. Each sits in its own window, so neither covers a track.",
            "Settings (⚙) → App colour → Scroll wheel effects: switch off for plain-coloured scroll wheels with no animation; on for the glowing upscale look.",
            "COPY / PASTE SETTINGS copies a clip's grade, audio and more to other clips (or all clips).",
            "Proxy tab (next to Media): add the clips you want to edit smoothly (or ADD ALL MEDIA), switch Proxy ON / OFF, and pick the quality — 360p, 540p, 720p or 1080p. Proxies are made in the background with a progress bar and CANCEL; the original plays until a proxy is ready, and if a proxy is missing or fails, the original is used. Changing quality remakes the proxies (the old ones are used meanwhile). Proxies keep the original's timing, frame rate, orientation and sound, so switching never moves your edits. Exports always use the original files.",
            "If playback stalls, turn on \"Show playback info\" here and share a screenshot.",
            "Hold a divider to resize the player, timeline or controls; double-tap to reset.",
            "Layers → Adjustment: one grade over every clip in its time range (e.g. a look for a whole scene); set its length on the timeline and its strength.",
            "Deliver → SEND TO DESKTOP EDITOR: save the edit as an EDL or FCPXML to finish it in desktop editing software (copy the original clips to the computer too).",
            "Color → Masks: up to 3 masks per clip (gradient, radial, colour range, brightness range), each with its own grade; drag the handles on the player.",
            "Edit → Remove silences: cuts the pauses out of talking clips (this clip or all clips); set the silence level and minimum pause. Undo brings them back.",
            "Layers → ANIMATION: in / out animations for text and pictures — fade, slide, pop, zoom, bounce, wipe reveal, typewriter — with adjustable length.",
            "Deliver → LOUDNESS: −14 LUFS (YouTube, Instagram, Spotify), −16 (Apple, podcasts) or −23 (TV) makes every export equally loud.",
            "Audio → MIXER: one strip per track (the clips' sound, music and every extra track) with a live meter, Level, Pan, M (mute) and S (solo, for listening only), plus a MASTER level. Levels, pan and mute go into the export.",
            "Audio → LUFS meter: momentary (M), short-term (S) and integrated (I) loudness of what plays. Play the whole edit for the integrated value; RESET starts it again.",
            "Audio → DYNAMICS → Dialogue leveller: evens out speech — quiet words come up, loud ones go down; pauses are left alone.",
            "Deliver → SEND TO DESKTOP EDITOR → OTIO: OpenTimelineIO file (cuts, dissolves, speed, markers) for editing and finishing tools that read OTIO.",
            "Subtitles: Deliver saves the captions as .srt or .vtt (web players). Layers → IMPORT SUBTITLES turns an .srt or .vtt file into caption layers.",
            "Deliver → SAFE ZONES: shows on the viewer where TikTok, Reels or Shorts put their buttons and captions (approximate), or the TV action / title safe areas. Never exported.",
            "Keyframes: tap a keyframe's easing until it says Curve, then drag the two amber handles in the graph (or pick Ease in / out, Back, Anticipate…) to shape the move to the next keyframe.",
            "Audio → PARAMETRIC EQ: + BAND adds up to 8 bands (bell, shelves, low / high cut); drag a point across for frequency and up / down for boost or cut, over the live spectrum. DE-ESSER tames harsh \"s\" sounds.",
            "Deliver → REVIEW COPY: burned-in timecode, a faint watermark (e.g. DRAFT) and each marker's note on screen — for sending a cut to a client. Turn it off for the final export.",
            "Proxy page → RENDER CACHE: clips with AI noise reduction, background cut-out or AI depth effects are rendered once so they play smoothly (CACHED on the viewer). The export always uses the originals.",
            "Auto highlights → Cut on the music's beat: with a music track, the reel's cuts land on the beat. The reel now also favours sharp shots and shots with people.",
            "Layers → AI DUBBING: the captions spoken as a new audio track at their times, in a woman's or man's voice; the original voice is turned down or muted in the mixer. English is made on the phone; Danish, German, Spanish and more use Cloud AI. Translate the captions first for another language.",
            "Deliver → SMART RENDER → Fast copy: a cut-only edit (no grade, effects, layers, transitions, speed or sound changes, Source size and frame rate) is copied straight from the clips — no quality loss, much faster. Each clip then starts on the key frame at or just before its in point.",
            "Media → TIMELINES: keep several timelines in one project (e.g. a long and a short cut). NEW TIMELINE starts an empty one, DUPLICATE copies the open one, tap a name to switch. Rename the open one in its field.",
            "Panels icon (next to the settings gear): hide or show the tools panel, transport bar, histogram and audio waveforms — the player and timeline grow into the space and shrink back when a panel returns. Presets: Full, Cut, Color, Audio. Triple-tap the icon to reset the whole layout (panels and divider sizes).",
            "Deliver → WIDE + VERTICAL → EXPORT 16:9 AND 9:16: the same edit saved twice, one for YouTube and one for Reels, Shorts and TikTok (the picture fills the frame, centred).",
            "Media → EDIT TEMPLATES: Travel recap, Drone reel, Action, Calm & warm, Intro / opener or Old film. With a music track your clips are cut on its beat; every template adds transitions on the cuts, a title and, for some, an ending, cinema bars or an old-film look. Edit anything afterwards; undo brings your edit back.",
            "Layers → Flight path / route: pick a drone .SRT file, or a .GPX track from a GPS app (a hike, drive or ride) — the route is drawn as a map over the selected clip, start to finish.",
            "Layers → Text behind subject: a big title over the selected clip with the person standing in front of it — a copy of the clip with its background removed (AI) sits on top of the words. Change the words and font in the text layer. Works best on clips at normal speed without reframing.",
            "Layers → Retro look: VHS tape, Old TV, Camcorder (with a date stamp you set), Old film (dust and scratches) or Security camera over the whole video; Strength sets how strong it is. Pair it with a faded or black-and-white look on the Color page.",
            "Color → Film & lens → SKY REPLACEMENT: pick Clear blue, Golden hour, Sunset, Dramatic or Night; set Horizon just below where the sky ends and How much is sky until the whole sky changes but nothing else does. Works frame by frame, so it follows camera moves; best on clear or hazy skies.",
            "SAVE / LOAD projects; \"Save & leave\" when exiting.",
        ), numbered = false),
        GuideSection(GuideTopic.GENERAL, "General", Icons.Filled.Tune, null, listOf(
            "\"Remember watermark\" adds your watermark to every new project automatically.",
            "SAVE LOCATION chooses where exported videos go: same as capture, internal storage, an SD card / USB drive, or a folder you pick on a USB stick / SSD / SD card.",
            "Change the app colour under APP COLOUR.",
            "\"Always start in Video editor\" opens the app straight into this editor.",
        ), numbered = false),
    )
}

/** In-app guide: how to add the Cloud AI key, plus tips for every part of the app. */
@Composable
fun GuideDialog(variant: GuideVariant, open: GuideTopic? = null, onDismiss: () -> Unit) {
    val expanded = remember { mutableStateListOf<GuideTopic>().apply { add(open ?: GuideTopic.CLOUD) } }
    val uri = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(pickHere(Color(0xFF0B0C0F), Color(0xFFE9EBEE))).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(variant.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        Text("GET MORE OUT OF ALCORCUT PRO", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp, letterSpacing = 2.sp))
                    }
                    Box(Modifier.size(44.dp).clip(CircleShape).background(fgHere().copy(alpha = 0.08f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    sectionsFor(variant).forEach { s ->
                        val isOpen = s.topic in expanded
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                                .background(pickHere(Color(0xFF15171C), Color(0xFFFFFFFF)))
                                .border(1.dp, if (isOpen) Pro.Amber.copy(alpha = 0.6f) else fgHere().copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable { if (isOpen) expanded.remove(s.topic) else expanded.add(s.topic) }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(Pro.Amber.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(s.icon, contentDescription = null, tint = Pro.Amber, modifier = Modifier.size(22.dp))
                                }
                                Text(s.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                Icon(if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = Pro.Dim)
                            }
                            AnimatedVisibility(isOpen) {
                                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                    s.intro?.let { Text(it, color = Pro.Dim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                                    s.steps.forEachIndexed { i, step ->
                                        Row(Modifier.padding(vertical = 4.dp)) {
                                            Box(
                                                Modifier.size(24.dp).clip(CircleShape).background(if (s.numbered) Pro.Amber else Color.Transparent),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(if (s.numbered) "${i + 1}" else "•", color = if (s.numbered) Color.Black else Pro.Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Text(step, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
                                        }
                                    }
                                    if (s.topic == GuideTopic.CLOUD) Row(Modifier.padding(top = 8.dp)) {
                                        Text(
                                            "OPEN FAL.AI KEYS", style = Pro.Label.copy(color = Color.Black, fontSize = 11.sp),
                                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Pro.Amber).clickable { uri.openUri("https://fal.ai/dashboard/keys") }
                                                .padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                        Text(
                                            "BILLING", style = Pro.Label.copy(color = Pro.Amber, fontSize = 11.sp),
                                            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50)).border(1.dp, Pro.Amber, RoundedCornerShape(50))
                                                .clickable { uri.openUri("https://fal.ai/dashboard/billing") }.padding(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(Modifier.width(1.dp).padding(bottom = 24.dp))
                }
            }
        }
    }
}
