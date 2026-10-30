# Windows: phone-only settings are left out (no side buttons, motion sensors, second camera or Bluetooth headset routing on a PC).
EDITS = [
    ('                ToggleRow("Level", "Horizon line, turns green when straight", s.showLevel) { c.toggleLevel() }\n', ''),
    ('                    ToggleRow("Editing proxy",', '                    if (false) ToggleRow("Editing proxy",'),
    ('                    ToggleRow("Front + back camera",', '                    if (false) ToggleRow("Front + back camera",'),
    ('                    ChoiceRow("Pre-record", listOf(0, 3, 5, 10), x.preRecordSec,', '                    if (false) ChoiceRow("Pre-record", listOf(0, 3, 5, 10), x.preRecordSec,'),
    ('                    Text("Keeps the last few seconds before you press record,', '                    if (false) Text("Keeps the last few seconds before you press record,'),
    ('                    ToggleRow("Bluetooth microphone",', '                    if (false) ToggleRow("Bluetooth microphone",'),
    ('                    ChoiceRow("Voice noise reduction",', '                    if (false) ChoiceRow("Voice noise reduction",'),
    ('                    Text("Cleans hiss, hum and background noise', '                    if (false) Text("Cleans hiss, hum and background noise'),
    ('                    Text("USB and wired microphones are used automatically when plugged in.", color = Pro.Dim, fontSize = 12.sp)',
     '                    Text("Choose the microphone in Audio hardware (Controls & buttons).", color = Pro.Dim, fontSize = 12.sp)'),
    ('                    ToggleRow(\n                        "NTSC frame rates",', '                    if (false) ToggleRow(\n                        "NTSC frame rates",'),
    ('                run {\n                    var side by remember', '                if (false) run {\n                    var side by remember'),
]
