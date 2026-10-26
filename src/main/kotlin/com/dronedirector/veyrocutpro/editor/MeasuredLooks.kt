package com.dronedirector.veyrocutpro.editor

/**
 * RAW looks measured by the RAW sample test (public-domain samples from raw.pixls.us): each maker's
 * RAW files were developed with the default look and compared with the picture the camera itself
 * stored in the file; the fitted tone curve (output at inputs 0, 16 … 240, 255) and colour strength
 * were averaged per maker. Brand names only say which cameras a look was measured from.
 */
internal object MeasuredLooks {
    val ALL: List<RawDng.Look> = listOf(
        RawDng.Look("Pixel style", "Phones", intArrayOf(0, 4, 25, 51, 72, 95, 115, 132, 145, 155, 166, 178, 188, 197, 207, 219, 250), 1.495f), // 7 files
        RawDng.Look("Galaxy style", "Phones", intArrayOf(0, 7, 21, 43, 70, 97, 122, 145, 166, 184, 199, 211, 221, 228, 232, 236, 252), 1.185f), // 11 files
        RawDng.Look("iPhone style", "Phones", intArrayOf(1, 14, 41, 72, 100, 125, 144, 164, 183, 196, 206, 215, 223, 229, 233, 236, 252), 1.071f), // 4 files
        RawDng.Look("DJI camera picture", "Drones", intArrayOf(0, 8, 27, 50, 76, 103, 126, 145, 160, 175, 188, 200, 210, 221, 228, 234, 252), 1.005f), // 10 files
        RawDng.Look("Parrot style", "Drones", intArrayOf(1, 10, 23, 38, 57, 75, 100, 125, 145, 163, 180, 195, 209, 221, 232, 244, 254), 0.986f), // 3 files
        RawDng.Look("Autel style", "Drones", intArrayOf(2, 22, 49, 75, 96, 116, 135, 158, 173, 187, 202, 209, 216, 219, 222, 225, 250), 0.974f), // 2 files
        RawDng.Look("Canon style", "Cameras", intArrayOf(1, 16, 36, 56, 76, 97, 116, 134, 152, 167, 185, 196, 205, 213, 219, 224, 251), 1.024f), // 14 files
        RawDng.Look("Sony style", "Cameras", intArrayOf(0, 7, 20, 36, 53, 72, 91, 112, 130, 147, 161, 172, 183, 194, 203, 214, 250), 0.972f), // 6 files
        RawDng.Look("Nikon style", "Cameras", intArrayOf(1, 11, 28, 47, 69, 90, 113, 140, 159, 173, 184, 192, 200, 213, 219, 226, 252), 1.169f), // 4 files
        RawDng.Look("Fujifilm style", "Cameras", intArrayOf(0, 6, 18, 36, 55, 76, 96, 121, 141, 158, 173, 190, 201, 212, 223, 232, 251), 1.102f), // 4 files
        RawDng.Look("Leica style", "Cameras", intArrayOf(0, 6, 23, 49, 78, 105, 130, 151, 169, 184, 198, 209, 222, 232, 239, 245, 253), 0.878f), // 9 files
        RawDng.Look("Ricoh style", "Cameras", intArrayOf(1, 17, 39, 66, 94, 121, 144, 162, 180, 194, 210, 223, 233, 241, 245, 248, 253), 0.958f), // 12 files
        RawDng.Look("Panasonic style", "Cameras", intArrayOf(0, 4, 15, 33, 56, 81, 99, 118, 133, 148, 163, 176, 186, 194, 207, 218, 250), 0.833f), // 3 files
        RawDng.Look("Olympus / OM style", "Cameras", intArrayOf(0, 6, 18, 37, 53, 74, 96, 116, 134, 151, 167, 178, 190, 196, 202, 208, 250), 0.945f), // 2 files
        RawDng.Look("YI action cam style", "Action & 360°", intArrayOf(2, 28, 55, 83, 111, 137, 163, 191, 212, 226, 236, 239, 243, 246, 249, 252, 255), 0.977f), // 4 files
    )
}
