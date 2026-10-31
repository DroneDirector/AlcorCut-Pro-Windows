# Windows: video editor settings → Performance (graphics card, encoder, processor load).
EDITS = [
    ('                if (show("sysinfo")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "sysinfo",',
     '                if (isVideo && show("perf")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "perf", "Performance", "Graphics card, encoder for export, processor load") {\n'
     '                    com.dronedirector.veyrocutpro.windows.PerformanceSettings()\n'
     '                }\n'
     '                if (show("sysinfo")) com.dronedirector.veyrocutpro.ui.SettingsGroup(k + "sysinfo",'),
    ('"System Info", "This phone\'s hardware and drivers")', '"System Info", "This PC\'s hardware and drivers")'),
]
