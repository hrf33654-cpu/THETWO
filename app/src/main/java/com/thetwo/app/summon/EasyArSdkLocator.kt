package com.thetwo.app.summon

import java.io.File

data class EasyArSdkState(
    val hasAarFile: Boolean,
    val hasArchiveOnly: Boolean,
    val archiveName: String?,
)

object EasyArSdkLocator {
    private const val LIBS_DIR = "app/libs"
    private const val AAR_NAME = "EasyAR.aar"

    fun inspect(): EasyArSdkState {
        val libsDir = File(LIBS_DIR)
        val archive = libsDir.listFiles()?.firstOrNull { file ->
            file.name.startsWith("EasyAR", ignoreCase = true) && file.extension.equals("7z", ignoreCase = true)
        }
        return EasyArSdkState(
            hasAarFile = File(libsDir, AAR_NAME).exists(),
            hasArchiveOnly = archive != null,
            archiveName = archive?.name,
        )
    }

    fun hasAarFile(): Boolean = inspect().hasAarFile

    fun hasArchiveOnly(): Boolean = inspect().hasArchiveOnly
}
