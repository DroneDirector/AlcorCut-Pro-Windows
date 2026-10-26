package android.os

import java.io.File

/** The PC's standard folders. Own code. */
object Environment {
    const val DIRECTORY_PICTURES = "Pictures"
    const val DIRECTORY_DCIM = "Pictures"
    const val DIRECTORY_MOVIES = "Videos"
    const val DIRECTORY_MUSIC = "Music"
    const val DIRECTORY_DOWNLOADS = "Downloads"
    const val DIRECTORY_DOCUMENTS = "Documents"

    fun getExternalStoragePublicDirectory(type: String): File = File(System.getProperty("user.home"), type)
    fun getExternalStorageDirectory(): File = File(System.getProperty("user.home"))
}
